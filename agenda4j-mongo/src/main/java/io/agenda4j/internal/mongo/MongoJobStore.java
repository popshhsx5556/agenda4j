package io.agenda4j.internal.mongo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agenda4j.core.JobSpec;
import io.agenda4j.core.JobType;
import io.agenda4j.core.PersistResult;
import io.agenda4j.core.CancelQuery;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import com.mongodb.client.result.UpdateResult;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * MongoDB persistence layer for jobs.
 *
 * <p>Design A semantics:
 * <ul>
 *   <li>type=SINGLE: name-only singleton (upsert by {name,type})</li>
 *   <li>type=NORMAL: allows multiple; if uniqueKey is present, upsert by {name,type,uniqueKey}; otherwise insert</li>
 * </ul>
 */
public class MongoJobStore {

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final String collectionName;

    public MongoJobStore(MongoTemplate mongoTemplate, ObjectMapper objectMapper) {
        this(mongoTemplate, objectMapper, ScheduledJobDocument.DEFAULT_COLLECTION);
    }

    public MongoJobStore(MongoTemplate mongoTemplate, ObjectMapper objectMapper, String collectionName) {
        this.mongoTemplate = Objects.requireNonNull(mongoTemplate, "mongoTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.collectionName = isBlank(collectionName) ? ScheduledJobDocument.DEFAULT_COLLECTION : collectionName.trim();
    }

    /**
     * Persist a job spec.
     *
     * <p>Returns CREATED when the document did not exist and was inserted (upserted/inserted),
     * UPDATED when an existing document was updated.
     */
    public <T> PersistResult save(JobSpec<T> spec) {
        Objects.requireNonNull(spec, "spec must not be null");

        if (spec.type() == JobType.NORMAL && isBlank(spec.uniqueKey())) {
            ScheduledJobDocument doc = toDocument(spec);
            mongoTemplate.insert(doc, collectionName);
            return PersistResult.createdResult();
        }

        Query query = buildUpsertQuery(spec);
        Update update = buildUpsertUpdate(spec);

        UpdateResult result = mongoTemplate.upsert(query, update, ScheduledJobDocument.class, collectionName);
        return result.getUpsertedId() != null ? PersistResult.createdResult() : PersistResult.updatedResult();
    }

    private <T> Query buildUpsertQuery(JobSpec<T> spec) {
        Criteria c = Criteria.where("name").is(spec.name())
                .and("type").is(spec.type());

        if (spec.type() == JobType.NORMAL && !isBlank(spec.uniqueKey())) {
            c = c.and("uniqueKey").is(spec.uniqueKey());
        }
        return new Query(c);
    }

    private <T> Update buildUpsertUpdate(JobSpec<T> spec) {
        Update u = new Update();
        u.set("name", spec.name());
        u.set("type", spec.type());

        if (!isBlank(spec.uniqueKey())) {
            u.set("uniqueKey", spec.uniqueKey());
        } else {
            u.unset("uniqueKey");
        }

        if (spec.unique() != null && !spec.unique().isEmpty()) {
            u.set("unique", spec.unique());
        } else {
            u.unset("unique");
        }

        u.set("priority", spec.priority());
        u.set("nextRunAt", spec.nextRunAt());

        if (!isBlank(spec.repeatInterval())) {
            u.set("repeatInterval", spec.repeatInterval());
        } else {
            u.unset("repeatInterval");
        }

        if (!isBlank(spec.repeatTimezone())) {
            u.set("repeatTimezone", spec.repeatTimezone());
        } else {
            u.unset("repeatTimezone");
        }

        Map<String, Object> dataMap = spec.data() == null ? null :
                objectMapper.convertValue(spec.data(), new TypeReference<>() {
                });
        if (dataMap != null) {
            u.set("data", dataMap);
        } else {
            u.unset("data");
        }

        return u;
    }

    private <T> ScheduledJobDocument toDocument(JobSpec<T> spec) {
        ScheduledJobDocument doc = new ScheduledJobDocument();
        doc.setName(spec.name());
        doc.setType(spec.type());
        doc.setUniqueKey(spec.uniqueKey());
        doc.setUnique(spec.unique());
        doc.setPriority(spec.priority());
        doc.setNextRunAt(spec.nextRunAt());
        doc.setRepeatInterval(spec.repeatInterval());
        doc.setRepeatTimezone(spec.repeatTimezone());

        if (spec.data() != null) {
            doc.setData(
                    objectMapper.convertValue(
                            spec.data(),
                            new com.fasterxml.jackson.core.type.TypeReference<>() {
                            }
                    )
            );
        }
        return doc;
    }

    /**
     * Reverse of {@link #toDocument(JobSpec)}.
     *
     * <p>Converts a persisted {@link ScheduledJobDocument} back into an in-memory {@link JobSpec}.
     * The stored {@code data} map is converted back into the requested {@code dataClass}.
     *
     * @param doc       the Mongo document
     * @param dataClass target data class for {@code JobSpec#data()}; may be null to skip conversion
     */
    public <T> JobSpec<T> toSpec(ScheduledJobDocument doc, Class<T> dataClass) {
        Objects.requireNonNull(doc, "doc must not be null");

        Map<String, Object> raw = doc.getData();
        T data = null;
        if (raw != null && dataClass != null) {
            data = objectMapper.convertValue(raw, dataClass);
        }

        return new JobSpec<>(
                doc.getName(),
                doc.getUniqueKey(),
                doc.getUnique(),
                doc.getType(),
                doc.getNextRunAt(),
                doc.getRepeatInterval(),
                doc.getRepeatTimezone(),
                doc.getPriority(),
                data
        );
    }

    /**
     * Convenience overload: returns a {@link JobSpec} with {@code data} as raw map.
     */
    @SuppressWarnings("unchecked")
    public JobSpec<Map<String, Object>> toSpec(ScheduledJobDocument doc) {
        return toSpec(doc, (Class<Map<String, Object>>) (Class<?>) Map.class);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Hard delete job by document id.
     *
     * @return deleted count (0 or 1 normally)
     */
    public long deleteById(String id) {
        Objects.requireNonNull(id, "id must not be null");
        Query q = new Query(Criteria.where("_id").is(id));
        return mongoTemplate.remove(q, ScheduledJobDocument.class, collectionName).getDeletedCount();
    }

    /**
     * Find SINGLE job by name
     */
    public ScheduledJobDocument findSingleByName(String name) {
        Query q = new Query(Criteria.where("name").is(name).and("type").is(JobType.SINGLE));
        return mongoTemplate.findOne(q, ScheduledJobDocument.class, collectionName);
    }

    /**
     * Find NORMAL job by (name, uniqueKey)
     */
    public ScheduledJobDocument findNormalByNameAndUniqueKey(String name, String uniqueKey) {
        Query q = new Query(Criteria.where("name").is(name)
                .and("type").is(JobType.NORMAL)
                .and("uniqueKey").is(uniqueKey));
        return mongoTemplate.findOne(q, ScheduledJobDocument.class, collectionName);
    }

    /**
     * Hard delete SINGLE job by name.
     */
    public long deleteByName(String name) {
        Objects.requireNonNull(name, "name must not be null");

        Query q = new Query(
                Criteria.where("name").is(name)
                        .and("type").is(JobType.SINGLE)
        );

        return mongoTemplate.remove(q, ScheduledJobDocument.class, collectionName)
                .getDeletedCount();
    }

    /**
     * Delete NORMAL job by (name, uniqueKey)
     */
    public long deleteByName(String name, String uniqueKey) {
        if (isBlank(name)) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (isBlank(uniqueKey)) {
            throw new IllegalArgumentException("uniqueKey must not be blank for NORMAL job deletion");
        }

        Query q = new Query(
                Criteria.where("name").is(name)
                        .and("type").is(JobType.NORMAL)
                        .and("uniqueKey").is(uniqueKey)
        );
        return mongoTemplate.remove(q, ScheduledJobDocument.class, collectionName).getDeletedCount();
    }

    /**
     * Disable (cancel) NORMAL job by (name, uniqueKey).
     * Keeps the document but clears scheduling and lock fields.
     */
    public long disableByName(String name, String uniqueKey) {
        if (isBlank(name)) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (isBlank(uniqueKey)) {
            throw new IllegalArgumentException("uniqueKey must not be blank for NORMAL job disable");
        }

        Query q = new Query(
                Criteria.where("name").is(name)
                        .and("type").is(JobType.NORMAL)
                        .and("uniqueKey").is(uniqueKey)
        );

        Update u = new Update()
                .unset("nextRunAt")
                .unset("repeatInterval")
                .unset("repeatTimezone")
                .unset("lockedAt")
                .unset("lockUntil")
                .unset("lockedBy");

        UpdateResult r = mongoTemplate.updateMulti(q, u, ScheduledJobDocument.class, collectionName);
        return r.getModifiedCount();
    }

    /**
     * Delete SINGLE job by name
     */
    public long disableByName(String name) {
        Objects.requireNonNull(name, "name must not be null");

        Query q = new Query(Criteria.where("name").is(name).and("type").is(JobType.SINGLE));
        Update u = new Update()
                .unset("nextRunAt")
                .unset("repeatInterval")
                .unset("repeatTimezone")
                .unset("lockedAt")
                .unset("lockUntil")
                .unset("lockedBy");

        UpdateResult r = mongoTemplate.updateMulti(q, u, ScheduledJobDocument.class, collectionName);
        return r.getModifiedCount();
    }

    /**
     * Disable (cancel) jobs by a flexible {@link CancelQuery}.
     *
     * <p>Disable means: keep documents but clear scheduling &amp; lock fields so they won't run.
     *
     * @param query match conditions (API-layer object; translated here)
     * @param limit max number of jobs to affect; use {@code Integer.MAX_VALUE} for no limit
     * @return modified count
     */
    public long disableByQuery(CancelQuery query, int limit) {
        List<String> ids = selectIdsForQuery(query, limit);
        if (ids.isEmpty()) {
            return 0;
        }

        Query q = new Query(Criteria.where("_id").in(ids));
        Update u = disableUpdate();

        UpdateResult r = mongoTemplate.updateMulti(q, u, ScheduledJobDocument.class, collectionName);
        return r.getModifiedCount();
    }

    /**
     * Hard delete jobs by a flexible {@link CancelQuery}.
     *
     * @param query match conditions (API-layer object; translated here)
     * @param limit max number of jobs to delete; use {@code Integer.MAX_VALUE} for no limit
     * @return deleted count
     */
    public long deleteByQuery(CancelQuery query, int limit) {
        List<String> ids = selectIdsForQuery(query, limit);
        if (ids.isEmpty()) {
            return 0;
        }

        Query q = new Query(Criteria.where("_id").in(ids));
        return mongoTemplate.remove(q, ScheduledJobDocument.class, collectionName).getDeletedCount();
    }

    private List<String> selectIdsForQuery(CancelQuery query, int limit) {
        Objects.requireNonNull(query, "query must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be a positive number");
        }

        Criteria c = buildCriteria(query);
        return findIdsByCriteria(c, limit);
    }

    private static Update disableUpdate() {
        return new Update()
                .unset("nextRunAt")
                .unset("repeatInterval")
                .unset("repeatTimezone")
                .unset("lockedAt")
                .unset("lockUntil")
                .unset("lockedBy");
    }

    private List<String> findIdsByCriteria(Criteria c, int limit) {
        Query q = new Query(c);
        // stable-ish ordering: earliest nextRunAt first, then higher priority
        q.with(Sort.by(Sort.Order.asc("nextRunAt"), Sort.Order.desc("priority")));

        if (limit != Integer.MAX_VALUE) {
            q.limit(limit);
        }

        // Only need ids.
        q.fields().include("_id");

        List<ScheduledJobDocument> docs = mongoTemplate.find(q, ScheduledJobDocument.class, collectionName);
        List<String> ids = new ArrayList<>(docs.size());
        for (ScheduledJobDocument d : docs) {
            if (d != null && d.getId() != null) {
                ids.add(d.getId());
            }
        }
        return ids;
    }

    private static Criteria buildCriteria(CancelQuery query) {
        List<Criteria> parts = new ArrayList<>(8);

        if (!isBlank(query.name())) {
            parts.add(Criteria.where("name").is(query.name()));
        }

        if (!isBlank(query.uniqueKey())) {
            parts.add(Criteria.where("uniqueKey").is(query.uniqueKey()));
        }

        Map<String, Object> unique = query.unique();
        if (unique != null && !unique.isEmpty()) {
            for (var e : unique.entrySet()) {
                String k = e.getKey();
                Object v = e.getValue();
                if (isBlank(k) || v == null) {
                    continue;
                }
                parts.add(Criteria.where("unique." + k).is(v));
            }
        }

        if (parts.isEmpty()) {
            throw new IllegalArgumentException("CancelQuery must include at least one selector");
        }

        if (parts.size() == 1) {
            return parts.getFirst();
        }

        return new Criteria().andOperator(parts.toArray(new Criteria[0]));
    }

    /**
     * Atomically claims (locks) at most {@code batchSize} due jobs.
     *
     * <p>A job is considered due when:
     * <ul>
     *   <li>{@code nextRunAt <= now}</li>
     *   <li>and it is not locked, or its lock has expired: {@code lockUntil == null || lockUntil <= now}</li>
     * </ul>
     *
     * <p>This method is safe under MultiServer concurrency because each claim is performed via
     * MongoDB {@code findAndModify} (read + update atomically).
     */
    public List<ScheduledJobDocument> claimDueJobs(Instant windowEnd, int batchSize, Duration lockLifetime, String
            workerId) {
        Objects.requireNonNull(windowEnd, "windowEnd must not be null");
        Objects.requireNonNull(lockLifetime, "lockLifetime must not be null");
        if (batchSize <= 0) {
            return List.of();
        }
        if (lockLifetime.isZero() || lockLifetime.isNegative()) {
            throw new IllegalArgumentException("lockLifetime must be a positive duration");
        }
        if (isBlank(workerId)) {
            throw new IllegalArgumentException("workerId must not be blank");
        }

        Instant now = Instant.now();
        Instant lockUntil = now.plus(lockLifetime);

        Query baseQuery = new Query(
                Criteria.where("nextRunAt").ne(null).lte(windowEnd)
                        .andOperator(
                                new Criteria().orOperator(
                                        Criteria.where("lockUntil").is(null),
                                        Criteria.where("lockUntil").lte(now)
                                )
                        )
        );

        baseQuery.with(Sort.by(Sort.Order.asc("nextRunAt"), Sort.Order.desc("priority")));

        Update lockUpdate = new Update()
                .set("lockedAt", now)
                .set("lockUntil", lockUntil)
                .set("lockedBy", workerId);

        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);

        List<ScheduledJobDocument> claimed = new ArrayList<>(Math.min(batchSize, 64));
        for (int i = 0; i < batchSize; i++) {
            ScheduledJobDocument doc = mongoTemplate.findAndModify(baseQuery, lockUpdate, options, ScheduledJobDocument.class, collectionName);
            if (doc == null) {
                break;
            }
            claimed.add(doc);
        }
        return claimed;
    }

    public UpdateResult markSuccess(String id, String workerId, Instant startedAt, Instant finishedAt, Instant
            nextRunAtOrNull) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(workerId, "workerId must not be null");
        Objects.requireNonNull(finishedAt, "finishedAt must not be null");

        Query q = new Query(
                Criteria.where("_id").is(id)
                        // Prevent stale write-back if another worker already re-claimed this job.
                        .and("lockedBy").is(workerId)
        );

        Update u = new Update()
                .set("lastRunAt", startedAt != null ? startedAt : finishedAt)
                .set("lastFinishedAt", finishedAt)
                .unset("lockedAt")
                .unset("lockUntil")
                .unset("lockedBy")
                .unset("failedAt")
                .set("failCount", 0);

        if (nextRunAtOrNull != null) {
            u.set("nextRunAt", nextRunAtOrNull);
        } else {
            u.unset("nextRunAt");
        }

        return mongoTemplate.updateFirst(q, u, ScheduledJobDocument.class, collectionName);
    }

    public UpdateResult markFailure(String id, String workerId, Instant failedAt, Instant nextRunAtOrNull) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(workerId, "workerId must not be null");
        Objects.requireNonNull(failedAt, "failedAt must not be null");

        Query q = new Query(
                Criteria.where("_id").is(id)
                        .and("lockedBy").is(workerId)
        );

        Update u = new Update()
                .inc("failCount", 1)
                .set("failedAt", failedAt)
                .unset("lockedAt")
                .unset("lockUntil")
                .unset("lockedBy");

        if (nextRunAtOrNull != null) {
            u.set("nextRunAt", nextRunAtOrNull);
        } else {
            u.unset("nextRunAt");
        }

        return mongoTemplate.updateFirst(q, u, ScheduledJobDocument.class, collectionName);
    }
}
