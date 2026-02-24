package io.agenda4j.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Runtime configuration for Agenda scheduler behavior.
 */
@ConfigurationProperties(prefix = "agenda")
public class AgendaProperties {
    private int maxConcurrency = 20; // global
    private int defaultConcurrency = 5; // per job name
    private int lockLimit = 0; // global
    private int batchSize = 5;
    private int maxRetryCount = 5;
    private Duration defaultLockLifetime = Duration.ofMinutes(10);
    private Duration processEvery = Duration.ofSeconds(5);
    private boolean cleanupFinishedJobs = true;
    private String workerId;
    private boolean ensureIndexesOnStartup = false;
    private String collectionName;

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public int getDefaultConcurrency() {
        return defaultConcurrency;
    }

    public void setDefaultConcurrency(int defaultConcurrency) {
        this.defaultConcurrency = defaultConcurrency;
    }

    public int getLockLimit() {
        return lockLimit;
    }

    public void setLockLimit(int lockLimit) {
        this.lockLimit = lockLimit;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public Duration getDefaultLockLifetime() {
        return defaultLockLifetime;
    }

    public void setDefaultLockLifetime(Duration defaultLockLifetime) {
        this.defaultLockLifetime = defaultLockLifetime;
    }

    public Duration getProcessEvery() {
        return processEvery;
    }

    public void setProcessEvery(Duration processEvery) {
        this.processEvery = processEvery;
    }

    public boolean isCleanupFinishedJobs() {
        return cleanupFinishedJobs;
    }

    public void setCleanupFinishedJobs(boolean cleanupFinishedJobs) {
        this.cleanupFinishedJobs = cleanupFinishedJobs;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public boolean isEnsureIndexesOnStartup() {
        return ensureIndexesOnStartup;
    }

    public void setEnsureIndexesOnStartup(boolean ensureIndexesOnStartup) {
        this.ensureIndexesOnStartup = ensureIndexesOnStartup;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }
}
