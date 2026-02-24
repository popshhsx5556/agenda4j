package io.agenda4j.config;

import io.agenda4j.internal.mongo.ScheduledJobDocument;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import java.util.Collection;
import java.util.Objects;

/**
 * MongoDB index definitions for the Agenda module.
 *
 * <p><b>Important:</b> This module does <b>NOT</b> automatically create indexes at application startup.
 * In production, indexes are usually managed by DB migrations / ops scripts (e.g., Compass, mongosh, CI/CD).
 *
 * <p>This class exists to:
 * <ul>
 *   <li>Document which indexes are required for correctness/performance</li>
 *   <li>Provide a manual {@link #ensureIndexes()} entrypoint if you explicitly choose to run it</li>
 *   <li>Provide helpers for building custom unique indexes for user-defined "unique" keys</li>
 * </ul>
 *
 * <h3>Required indexes (collection: {@code scheduled_jobs})</h3>
 * <ul>
 *   <li><b>idx_due_claim</b>: { nextRunAt: 1, lockUntil: 1, priority: -1 }
 *       <br/>Used by polling/claiming due jobs with lock filtering and priority ordering.</li>
 *   <li><b>idx_name_uniqueKey</b>: { name: 1, uniqueKey: 1 }
 *       <br/>Used by NORMAL job lookup/cancel (name + uniqueKey).</li>
 *   <li><b>ux_single_name</b> (unique + partial): { name: 1 } with partialFilterExpression { type: "SINGLE" }
 *       <br/>Enforces SINGLE job uniqueness by name.</li>
 * </ul>
 *
 * <h3>Example mongosh script</h3>
 * <pre>
 * db.scheduled_jobs.createIndex({ nextRunAt: 1, lockUntil: 1, priority: -1 }, { name: "idx_due_claim" });
 * db.scheduled_jobs.createIndex({ name: 1, uniqueKey: 1 }, { name: "idx_name_uniqueKey" });
 * db.scheduled_jobs.createIndex(
 *   { name: 1 },
 *   { name: "ux_single_name", unique: true, partialFilterExpression: { type: "SINGLE" } }
 * );
 * </pre>
 */
public class AgendaMongoIndexConfig {

    public static final String IDX_DUE_CLAIM = "idx_due_claim";
    public static final String IDX_NAME_UNIQUE_KEY = "idx_name_uniqueKey";
    public static final String UX_SINGLE_NAME = "ux_single_name";

    private final MongoTemplate mongoTemplate;
    private final String collectionName;

    public AgendaMongoIndexConfig(MongoTemplate mongoTemplate) {
        this(mongoTemplate, ScheduledJobDocument.DEFAULT_COLLECTION);
    }

    public AgendaMongoIndexConfig(MongoTemplate mongoTemplate, String collectionName) {
        this.mongoTemplate = mongoTemplate;
        this.collectionName = (collectionName == null || collectionName.isBlank())
                ? ScheduledJobDocument.DEFAULT_COLLECTION
                : collectionName.trim();
    }

    /**
     * Manually ensure required indexes for Agenda.
     * <p>
     * This method is intentionally <b>NOT</b> annotated with {@code @PostConstruct}.
     * Call it explicitly if you want auto-creation in a controlled environment.
     */
    public void ensureIndexes() {
        mongoTemplate.indexOps(collectionName).createIndex(dueClaimIndex());
        mongoTemplate.indexOps(collectionName).createIndex(nameUniqueKeyIndex());
        mongoTemplate.indexOps(collectionName).createIndex(singleNameUniqueIndex());
    }

    /**
     * Index for polling/claiming due jobs.
     * Keys: nextRunAt ASC, lockUntil ASC, priority DESC
     */
    public static Index dueClaimIndex() {
        return new Index()
                .on("nextRunAt", Sort.Direction.ASC)
                .on("lockUntil", Sort.Direction.ASC)
                .on("priority", Sort.Direction.DESC)
                .named(IDX_DUE_CLAIM);
    }

    /**
     * Index for NORMAL job lookup/cancel.
     * Keys: name ASC, uniqueKey ASC
     */
    public static Index nameUniqueKeyIndex() {
        return new Index()
                .on("name", Sort.Direction.ASC)
                .on("uniqueKey", Sort.Direction.ASC)
                .named(IDX_NAME_UNIQUE_KEY);
    }

    /**
     * Unique index for SINGLE jobs.
     * Keys: name ASC
     * Options: unique + partialFilterExpression { type: "SINGLE" }
     */
    public static Index singleNameUniqueIndex() {
        return new Index()
                .on("name", Sort.Direction.ASC)
                .unique()
                .partial(PartialIndexFilter.of(new Document("type", "SINGLE")))
                .named(UX_SINGLE_NAME);
    }

    /**
     * Build a custom <b>unique</b> index for user-defined "unique" keys stored under a nested field.
     *
     * <p>Assumption: your document contains a field like:
     * <pre>
     *   unique: { guildId: "...", sourceId: "..." }
     * </pre>
     *
     * <p>Given uniqueKeys ["guildId", "sourceId"], the index keys become:
     * <pre>
     *   { "unique.guildId": 1, "unique.sourceId": 1 }
     * </pre>
     *
     * <p><b>Note:</b> MongoDB indexes require a fixed set of fields. If keys vary per job, you must decide
     * which combinations you want to support and create an index per combination.
     *
     * @param indexName  index name
     * @param uniqueKeys keys under the nested field (e.g. "guildId", "sourceId")
     * @param partialFilter optional partial filter (pass null for none)
     */
    public static Index customUniqueIndex(String indexName,
                                         Collection<String> uniqueKeys,
                                         Document partialFilter) {
        Objects.requireNonNull(indexName, "indexName must not be null");
        Objects.requireNonNull(uniqueKeys, "uniqueKeys must not be null");
        if (uniqueKeys.isEmpty()) {
            throw new IllegalArgumentException("uniqueKeys must not be empty");
        }

        Index idx = new Index();
        for (String k : uniqueKeys) {
            if (k == null || k.isBlank()) {
                throw new IllegalArgumentException("uniqueKeys must not contain blank values");
            }
            idx = idx.on("unique." + k, Sort.Direction.ASC);
        }

        idx = idx.unique().named(indexName);
        if (partialFilter != null) {
            idx = idx.partial(PartialIndexFilter.of(partialFilter));
        }
        return idx;
    }
}
