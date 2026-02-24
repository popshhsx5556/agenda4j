package io.agenda4j.internal.mongo;

import io.agenda4j.core.JobType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Map;

/**
 * Mongo document model for persisted scheduled jobs.
 */
@Document(collection = ScheduledJobDocument.DEFAULT_COLLECTION)
public class ScheduledJobDocument {
    public static final String DEFAULT_COLLECTION = "scheduled_jobs";

    @Id
    private String id;

    private String name;
    private String uniqueKey;
    private Map<String, Object> unique;
    private JobType type;

    @Field(write = Field.Write.ALWAYS)
    private Instant nextRunAt;

    private String repeatInterval;
    private String repeatTimezone;

    private Instant lockedAt;
    private Instant lockUntil;
    private String lockedBy;
    private Instant lastRunAt;
    private Instant lastFinishedAt;

    private int failCount;
    private Instant failedAt;

    private int priority;
    private Map<String, Object> data;

    public ScheduledJobDocument() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUniqueKey() {
        return uniqueKey;
    }

    public void setUniqueKey(String uniqueKey) {
        this.uniqueKey = uniqueKey;
    }

    public Map<String, Object> getUnique() {
        return unique;
    }

    public void setUnique(Map<String, Object> unique) {
        this.unique = unique;
    }

    public JobType getType() {
        return type;
    }

    public void setType(JobType type) {
        this.type = type;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(Instant nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public String getRepeatInterval() {
        return repeatInterval;
    }

    public void setRepeatInterval(String repeatInterval) {
        this.repeatInterval = repeatInterval;
    }

    public String getRepeatTimezone() {
        return repeatTimezone;
    }

    public void setRepeatTimezone(String repeatTimezone) {
        this.repeatTimezone = repeatTimezone;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(Instant lockedAt) {
        this.lockedAt = lockedAt;
    }

    public Instant getLockUntil() {
        return lockUntil;
    }

    public void setLockUntil(Instant lockUntil) {
        this.lockUntil = lockUntil;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy(String lockedBy) {
        this.lockedBy = lockedBy;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(Instant lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public Instant getLastFinishedAt() {
        return lastFinishedAt;
    }

    public void setLastFinishedAt(Instant lastFinishedAt) {
        this.lastFinishedAt = lastFinishedAt;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(Instant failedAt) {
        this.failedAt = failedAt;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}
