package tech.yesboss.memory.metrics;

import java.util.Map;

/**
 * Memory Metrics Interface
 *
 * <p>Defines the contract for performance monitoring metrics in the memory persistence module.</p>
 *
 * <p><b>Metrics Categories:</b></p>
 * <ul>
 *   <li>Operation Metrics: Count, success rate, failure rate for all operations</li>
 *   <li>Performance Metrics: Min, max, average timing for operations</li>
 *   <li>Cache Metrics: Hit rate, miss rate, eviction count</li>
 *   <li>Database Metrics: Query time, connection pool usage</li>
 *   <li>Vector Store Metrics: Insert time, search time, index performance</li>
 *   <li>LLM Metrics: Call count, latency, token usage</li>
 *   <li>Error Metrics: Error counts by type, error rates</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * MemoryMetrics metrics = new MemoryMetricsImpl(config);
 * metrics.recordResourceSave(100, true);
 * metrics.recordCacheHit();
 * Map&lt;String, Object&gt; snapshot = metrics.getSnapshot();
 * </pre>
 *
 * @author YesBoss Team
 * @version 1.0
 */
public interface MemoryMetrics {

    // ==================== Operation Metrics ====================

    /**
     * Record a resource save operation start.
     */
    void recordResourceSaveStart();

    /**
     * Record a successful resource save operation.
     *
     * @param durationMs Operation duration in milliseconds
     */
    void recordResourceSaveSuccess(long durationMs);

    /**
     * Record a failed resource save operation.
     *
     * @param errorCode Error code indicating the failure type
     */
    void recordResourceSaveFailure(String errorCode);

    /**
     * Record a resource query operation start.
     */
    void recordResourceQueryStart();

    /**
     * Record a successful resource query operation.
     *
     * @param durationMs Operation duration in milliseconds
     * @param resultCount Number of results returned
     */
    void recordResourceQuerySuccess(long durationMs, int resultCount);

    /**
     * Record a failed resource query operation.
     *
     * @param errorCode Error code indicating the failure type
     */
    void recordResourceQueryFailure(String errorCode);

    /**
     * Record a snippet save operation start.
     */
    void recordSnippetSaveStart();

    /**
     * Record a successful snippet save operation.
     *
     * @param durationMs Operation duration in milliseconds
     */
    void recordSnippetSaveSuccess(long durationMs);

    /**
     * Record a failed snippet save operation.
     *
     * @param errorCode Error code indicating the failure type
     */
    void recordSnippetSaveFailure(String errorCode);

    /**
     * Record a snippet extraction operation start.
     */
    void recordSnippetExtractionStart();

    /**
     * Record a successful snippet extraction operation.
     *
     * @param durationMs Operation duration in milliseconds
     * @param snippetCount Number of snippets extracted
     */
    void recordSnippetExtractionSuccess(long durationMs, int snippetCount);

    /**
     * Record a failed snippet extraction operation.
     *
     * @param errorCode Error code indicating the failure type
     */
    void recordSnippetExtractionFailure(String errorCode);

    /**
     * Record a preference save operation start.
     */
    void recordPreferenceSaveStart();

    /**
     * Record a successful preference save operation.
     *
     * @param durationMs Operation duration in milliseconds
     */
    void recordPreferenceSaveSuccess(long durationMs);

    /**
     * Record a failed preference save operation.
     *
     * @param errorCode Error code indicating the failure type
     */
    void recordPreferenceSaveFailure(String errorCode);

    /**
     * Record a preference update operation start.
     */
    void recordPreferenceUpdateStart();

    /**
     * Record a successful preference update operation.
     *
     * @param durationMs Operation duration in milliseconds
     */
    void recordPreferenceUpdateSuccess(long durationMs);

    /**
     * Record a failed preference update operation.
     *
     * @param errorCode Error code indicating the failure type
     */
    void recordPreferenceUpdateFailure(String errorCode);

    // ==================== Cache Metrics ====================

    /**
     * Record a cache hit.
     */
    void recordCacheHit();

    /**
     * Record a cache miss.
     */
    void recordCacheMiss();

    /**
     * Record a cache eviction.
     */
    void recordCacheEviction();

    /**
     * Record a cache refresh.
     *
     * @param durationMs Refresh duration in milliseconds
     */
    void recordCacheRefresh(long durationMs);

    // ==================== Database Metrics ====================

    /**
     * Record a database query execution.
     *
     * @param queryType Type of query (SELECT, INSERT, UPDATE, DELETE)
     * @param durationMs Query duration in milliseconds
     * @param success Whether the query succeeded
     */
    void recordDatabaseQuery(String queryType, long durationMs, boolean success);

    /**
     * Record a database connection acquired.
     */
    void recordConnectionAcquired();

    /**
     * Record a database connection released.
     *
     * @param durationMs Duration the connection was held
     */
    void recordConnectionReleased(long durationMs);

    /**
     * Record a database transaction start.
     */
    void recordTransactionStart();

    /**
     * Record a database transaction commit.
     *
     * @param durationMs Transaction duration in milliseconds
     * @param success Whether the commit succeeded
     */
    void recordTransactionCommit(long durationMs, boolean success);

    // ==================== Vector Store Metrics ====================

    /**
     * Record a vector insert operation.
     *
     * @param durationMs Insert duration in milliseconds
     * @param vectorCount Number of vectors inserted
     * @param success Whether the insert succeeded
     */
    void recordVectorInsert(long durationMs, int vectorCount, boolean success);

    /**
     * Record a vector search operation.
     *
     * @param durationMs Search duration in milliseconds
     * @param topK Number of results requested
     * @param resultCount Number of results returned
     */
    void recordVectorSearch(long durationMs, int topK, int resultCount);

    /**
     * Record a vector update operation.
     *
     * @param durationMs Update duration in milliseconds
     * @param success Whether the update succeeded
     */
    void recordVectorUpdate(long durationMs, boolean success);

    /**
     * Record a vector delete operation.
     *
     * @param durationMs Delete duration in milliseconds
     * @param success Whether the delete succeeded
     */
    void recordVectorDelete(long durationMs, boolean success);

    // ==================== LLM Metrics ====================

    /**
     * Record an LLM embedding generation call.
     *
     * @param durationMs Call duration in milliseconds
     * @param tokenCount Number of tokens processed
     * @param success Whether the call succeeded
     */
    void recordEmbeddingCall(long durationMs, int tokenCount, boolean success);

    /**
     * Record an LLM retry attempt.
     */
    void recordEmbeddingRetry();

    // ==================== Error Metrics ====================

    /**
     * Record a custom error.
     *
     * @param category Error category (e.g., "database", "llm", "validation")
     * @param errorCode Error code
     */
    void recordError(String category, String errorCode);

    // ==================== Metric Retrieval ====================

    /**
     * Get a snapshot of all current metrics.
     *
     * @return Map of metric names to values
     */
    Map<String, Object> getSnapshot();

    /**
     * Get metrics as JSON string.
     *
     * @return JSON-formatted metrics string
     */
    String getSnapshotAsJson();

    /**
     * Get a summary of key metrics.
     *
     * @return Human-readable summary string
     */
    String getSummary();

    /**
     * Check if metrics collection is enabled.
     *
     * @return true if enabled
     */
    boolean isEnabled();

    /**
     * Reset all metrics to initial values.
     */
    void reset();

    /**
     * Check if performance tracking is enabled.
     *
     * @return true if performance tracking is enabled
     */
    boolean isPerformanceTrackingEnabled();

    /**
     * Check if error tracking is enabled.
     *
     * @return true if error tracking is enabled
     */
    boolean isErrorTrackingEnabled();
}
