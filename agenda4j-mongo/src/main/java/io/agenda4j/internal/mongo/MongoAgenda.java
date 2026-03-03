package io.agenda4j.internal.mongo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agenda4j.Agenda;
import io.agenda4j.JobHandler;
import io.agenda4j.config.AgendaProperties;
import io.agenda4j.core.CancelQuery;
import io.agenda4j.core.CancelResult;
import io.agenda4j.JobBuilder;
import io.agenda4j.core.JobHandlerRegistry;
import io.agenda4j.core.PersistResult;
import io.agenda4j.internal.SimpleJobBuilder;
import io.agenda4j.utils.IntervalParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agenda is a Mongo-backed job scheduler &amp; runner.
 *
 * <p>Core capabilities:
 * <ul>
 *   <li>One-time jobs (run at a specific Instant/ISOString)</li>
 *   <li>Recurring jobs (cron expression / repeatEvery interval)</li>
 *   <li>Distributed-safe execution via atomic claim/lock</li>
 * </ul>
 *
 * <p>Typical usage:
 * <pre>{@code
 * agenda.start();
 *
 * agenda.create("sub-to-channel", data)
 *       .unique(Map.of("sourceId", "xxx", "key", "yyy"))
 *       .schedule("2026-01-20T09:30:00.000Z")
 *       .save();
 *
 * agenda.now("sync-something", data).save();
 * agenda.stop();
 * }</pre>
 */
public class MongoAgenda implements Agenda {
    private static final Logger log = LoggerFactory.getLogger(MongoAgenda.class);
    private final AgendaProperties props;
    private final MongoJobStore jobStore;
    private final JobHandlerRegistry jobRegistry;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private ExecutorService workerPool;

    private Thread pollerThread;
    private Thread dispatcherThread;

    private final DelayQueue<DelayedJob> queue = new DelayQueue<>();
    private final ConcurrentHashMap<String, Boolean> enqueued = new ConcurrentHashMap<>();

    private final Semaphore refillSignal = new Semaphore(0);

    private final Semaphore globalSem;
    private final ConcurrentHashMap<String, Semaphore> perNameSem = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private int systemErrorCount = 0;

    private final String workerId;

    private static final class DelayedJob implements Delayed {
        private final ScheduledJobDocument doc;
        private final Instant runAt;

        private DelayedJob(ScheduledJobDocument doc) {
            this.doc = doc;
            this.runAt = doc.getNextRunAt();
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long ms = Duration.between(Instant.now(), runAt).toMillis();
            return unit.convert(ms, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            if (other == this) return 0;
            if (other instanceof DelayedJob o) {
                return this.runAt.compareTo(o.runAt);
            }
            long d1 = this.getDelay(TimeUnit.MILLISECONDS);
            long d2 = other.getDelay(TimeUnit.MILLISECONDS);
            return Long.compare(d1, d2);
        }
    }

    private Semaphore semForName(String name) {
        return perNameSem.computeIfAbsent(name, n -> new Semaphore(props.getDefaultConcurrency()));
    }

    public MongoAgenda(AgendaProperties props, MongoJobStore jobStore, JobHandlerRegistry jobRegistry, ObjectMapper objectMapper) {
        this.props = Objects.requireNonNull(props, "props must not be null");
        this.jobStore = Objects.requireNonNull(jobStore, "jobStore must not be null");
        this.jobRegistry = Objects.requireNonNull(jobRegistry, "jobRegistry must not be null");
        this.globalSem = new Semaphore(props.getMaxConcurrency());
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.workerId = resolveWorkerId(props.getWorkerId());
    }

    /**
     * Start polling and executing due jobs. Should be idempotent.
     */
    @Override
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        Duration interval = Objects.requireNonNull(props.getProcessEvery(), "agenda.processEvery must not be null");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("agenda.processEvery must be a positive duration");
        }

        Duration lockLifetime = Objects.requireNonNull(props.getDefaultLockLifetime(), "agenda.defaultLockLifetime must not be null");
        if (lockLifetime.isZero() || lockLifetime.isNegative()) {
            throw new IllegalArgumentException("agenda.defaultLockLifetime must be a positive duration");
        }

        log.info("Agenda starting with processEvery={}, defaultLockLifetime={}, workerId={}, maxConcurrency={}, lockLimit={}, batchSize={}",
                props.getProcessEvery(),
                props.getDefaultLockLifetime(),
                workerId,
                props.getMaxConcurrency(),
                props.getLockLimit(),
                props.getBatchSize());

        if (workerPool == null) {
            workerPool = Executors.newFixedThreadPool(props.getMaxConcurrency(), r -> {
                Thread t = new Thread(r);
                t.setName("agenda.workerPool");
                t.setDaemon(true);
                return t;
            });
        }

        if (dispatcherThread == null) {
            dispatcherThread = new Thread(this::dispatchLoop);
            dispatcherThread.setName("agenda.dispatcher");
            dispatcherThread.setDaemon(true);
            dispatcherThread.start();
        }

        if (pollerThread == null) {
            pollerThread = new Thread(this::pollerLoop);
            pollerThread.setName("agenda.poller");
            pollerThread.setDaemon(true);
            pollerThread.start();
        }
        log.info("Agenda started successfully.");
    }

    /**
     * Stop polling and executing. Should be idempotent.
     */
    @Override
    public void stop() {
        if (!started.compareAndSet(true, false)) {
            return;
        }

        log.info("Agenda stopping...");

        if (pollerThread != null) {
            pollerThread.interrupt();
            pollerThread = null;
        }

        if (workerPool != null) {
            workerPool.shutdown();
            try {
                if (!workerPool.awaitTermination(props.getDefaultLockLifetime().toSeconds(), TimeUnit.SECONDS)) {
                    workerPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                workerPool.shutdownNow();
            } finally {
                workerPool = null;
            }
        }

        if (dispatcherThread != null) {
            dispatcherThread.interrupt();
            dispatcherThread = null;
        }

        queue.clear();
        enqueued.clear();
        refillSignal.drainPermits();
        log.info("Agenda stopped successfully.");
    }

    /**
     * Create a job builder. This does not persist until save() is called.
     */
    @Override
    public <T> JobBuilder<T> create(String name, T data) {
        return new SimpleJobBuilder<>(name, data, jobStore::save);
    }

    /**
     * Create a job builder without data.
     */
    @Override
    public JobBuilder<Void> create(String name) {
        return new SimpleJobBuilder<>(name, null, jobStore::save);
    }

    /**
     * Create and persist a job that runs immediately (nextRunAt = now).
     * Callers do not need to call {@code save()}.
     */
    @Override
    public <T> PersistResult now(String name, T data) {
        return this.create(name, data)
                .schedule(this.nowInstant())
                .save();
    }

    /**
     * Create and persist a job that runs immediately (nextRunAt = now) without data.
     * Callers do not need to call {@code save()}.
     */
    @Override
    public PersistResult now(String name) {
        return this.create(name)
                .schedule(this.nowInstant())
                .save();
    }

    @Override
    public CancelResult cancel(CancelQuery query) {
        return cancel(query, CancelOptions.defaults());
    }

    @Override
    public CancelResult cancel(CancelQuery query, CancelOptions options) {
        Objects.requireNonNull(query, "query must not be null");
        if (options == null) {
            options = CancelOptions.defaults();
        }
        if (options.limit() <= 0) {
            throw new IllegalArgumentException("limit must be a positive number");
        }
        if (options.mode() == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (query.isEmpty()) {
            throw new IllegalArgumentException("CancelQuery must include at least 'name' or non-empty 'unique'");
        }

        return switch (options.mode()) {
            case DISABLE -> {
                long modified = jobStore.disableByQuery(query, options.limit());
                yield new CancelResult(modified, modified, 0);
            }
            case DELETE -> {
                long deleted = jobStore.deleteByQuery(query, options.limit());
                yield new CancelResult(deleted, 0, deleted);
            }
        };
    }

    @Override
    public <T> JobBuilder<T> schedule(String name, Instant time, T data) {
        return this.create(name, data)
                .schedule(time);
    }

    @Override
    public JobBuilder<Void> schedule(String name, Instant time) {
        return this.create(name)
                .schedule(time);
    }

    @Override
    public <T> PersistResult every(String name, String interval, T data, JobBuilder.RepeatOptions options) {
        JobBuilder<T> b = this.create(name, data).single();
        if (options != null) {
            b.repeatEvery(interval, options);
        } else {
            b.repeatEvery(interval);
        }
        return b.save();
    }

    @Override
    public PersistResult every(String name, String interval, JobBuilder.RepeatOptions options) {
        return this.every(name, interval, null, options);
    }

    @Override
    public <T> PersistResult every(String name, Number interval, T data, JobBuilder.RepeatOptions options) {
        JobBuilder<T> b = this.create(name, data).single();
        if (options != null) {
            b.repeatEvery(interval, options);
        } else {
            b.repeatEvery(interval);
        }
        return b.save();
    }

    @Override
    public PersistResult every(String name, Number interval, JobBuilder.RepeatOptions options) {
        return this.every(name, interval, null, options);
    }

    /**
     * Utility: current scheduler time source (useful for tests).
     */
    protected Instant nowInstant() {
        return Instant.now();
    }

    private String resolveWorkerId(String configuredWorkerId) {
        if (configuredWorkerId != null && !configuredWorkerId.isBlank()) {
            return configuredWorkerId;
        }

        String host = "agenda4j";
        try {
            host = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
        }

        String pid = "unknown";
        try {
            pid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        } catch (Exception ignored) {
        }

        String generated = host + "-" + pid + "-" + java.util.UUID.randomUUID();
        if (generated.length() > 128) {
            return generated.substring(0, 128);
        }
        return generated;
    }

    private void pollerLoop() {
        while (started.get()) {
            boolean backlog;
            try {
                backlog = pollOnce();
                systemErrorCount = 0;
            } catch (Exception e) {
                systemErrorCount++;
                log.error("agenda pollOnce failed msg={}", e.getMessage(), e);
                if (systemErrorCount >= 30) {
                    log.error("Agenda stopped due to repeated system failures...");
                    stop();
                    break;
                }

                try {
                    Duration sleep = (systemErrorCount >= 10)
                            ? Duration.ofSeconds(60)
                            : backoff(systemErrorCount);

                    Thread.sleep(sleep.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            if (!started.get()) {
                break;
            }

            try {
                Duration idleWait = backlog ? Duration.ofMillis(200) : props.getProcessEvery();
                refillSignal.tryAcquire(idleWait.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // Exponential backoff for repeated poll-loop failures.
    private Duration backoff(int failCount) {
        int exp = Math.max(0, Math.min(failCount, 15));
        long ms = Math.min(1000L * (1L << exp), 60_000L);
        return Duration.ofMillis(ms);
    }

    /**
     * Job retry delay for handler failures.
     * attempt starts from 1 (first failure).
     * Default: 10s, 20s, 40s, 80s, 160s... capped at 10 minutes.
     */
    private Duration retryDelay(int attempt) {
        int exp = Math.max(0, attempt - 1);
        exp = Math.min(exp, 20); // avoid overflow
        long ms = Math.min(10_000L * (1L << exp), 600_000L);
        return Duration.ofMillis(ms);
    }

    private boolean pollOnce() {
        Instant windowEnd = Instant.now().plus(props.getProcessEvery());

        int running = props.getMaxConcurrency() - globalSem.availablePermits();
        int inFlight = enqueued.size() + Math.max(0, running);

        int lockLimit = props.getLockLimit();
        int remaining;
        if (lockLimit <= 0) {
            remaining = Integer.MAX_VALUE;
        } else {
            remaining = Math.max(0, lockLimit - inFlight);
        }

        if (remaining == 0) {
            return true;
        }

        int batchSize = Math.max(1, props.getBatchSize());
        boolean backlog = false;

        while (remaining > 0) {
            int take = Math.min(batchSize, remaining);

            var jobs = jobStore.claimDueJobs(
                    windowEnd,
                    take,
                    props.getDefaultLockLifetime(),
                    workerId
            );

            log.debug("Agenda polled jobs count={} windowEnd={} remaining={}", jobs.size(), windowEnd, remaining);

            for (var job : jobs) {
                if (enqueued.putIfAbsent(job.getId(), Boolean.TRUE) == null) {
                    queue.offer(new DelayedJob(job));
                    remaining--;
                    if (remaining == 0) {
                        break;
                    }
                }
            }

            if (jobs.size() < take) {
                break;
            }

            if (remaining == 0) {
                backlog = true;
                break;
            }
        }

        return backlog;
    }

    private void dispatchLoop() {
        while (started.get()) {
            try {
                DelayedJob dj = queue.take();
                ScheduledJobDocument job = dj.doc;

                enqueued.remove(job.getId());
                submitToWorker(job);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("agenda dispatcher failed msg={}", e.getMessage(), e);
            }
        }
    }

    private void submitToWorker(ScheduledJobDocument job) {
        final String name = job.getName();

        final Semaphore nameSem = semForName(name);

        globalSem.acquireUninterruptibly();
        nameSem.acquireUninterruptibly();

        workerPool.submit(() -> {
            try {
                var handler = jobRegistry.getRequired(name);
                var spec = jobStore.toSpec(job, handler.dataClass());

                Instant startedAt = Instant.now();
                log.debug("Agenda job started name={} id={} At={}", name, job.getId(), startedAt);
                executeHandler(handler, spec.data());
                Instant finishedAt = Instant.now();

                log.debug("Agenda job succeeded name={} id={} At={}", name, job.getId(), finishedAt);

                var nextRunCalc = IntervalParser.computeNextRunAt(
                        spec.repeatInterval(),
                        spec.repeatTimezone(),
                        spec.nextRunAt(),
                        finishedAt
                );

                if (nextRunCalc == null && props.isCleanupFinishedJobs()) {
                    jobStore.deleteById(job.getId());
                } else {
                    jobStore.markSuccess(
                            job.getId(),
                            workerId,
                            startedAt,
                            finishedAt,
                            nextRunCalc
                    );
                }
            } catch (Exception e) {
                log.error("agenda job failed name={} id={} msg={}", name, job.getId(), e.getMessage(), e);

                Instant failedAt = Instant.now();
                int nextAttempt = job.getFailCount() + 1;

                Instant nextRunAt;
                int maxRetry = props.getMaxRetryCount();
                if (maxRetry > 0 && nextAttempt >= maxRetry) {
                    nextRunAt = null;
                    log.warn("agenda job reached max retries; disabling further runs name={} id={} attempts={} maxRetry={}",
                            name, job.getId(), nextAttempt, maxRetry);
                } else {
                    nextRunAt = failedAt.plus(retryDelay(nextAttempt));
                }

                try {
                    jobStore.markFailure(job.getId(), workerId, failedAt, nextRunAt);
                } catch (Exception storeEx) {
                    log.error("agenda markFailure failed name={} id={} msg={}", name, job.getId(), storeEx.getMessage(), storeEx);
                }
            } finally {
                nameSem.release();
                globalSem.release();
                refillSignal.release();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private <T> void executeHandler(Object handler, Object rawData) throws Exception {
        var h = (JobHandler<T>) handler;
        T data = (rawData == null) ? null : objectMapper.convertValue(rawData, h.dataClass());
        h.execute(data);
    }

}
