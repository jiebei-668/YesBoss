package tech.yesboss.memory.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Memory Metrics Implementation
 *
 * <p>Comprehensive performance monitoring for memory persistence operations.</p>
 *
 * <p><b>Metrics Tracked:</b></p>
 * <ul>
 *   <li>Resource operations (save, query)</li>
 *   <li>Snippet operations (save, extract)</li>
 *   <li>Preference operations (save, update)</li>
 *   <li>Cache performance (hits, misses, evictions)</li>
 *   <li>Database operations (queries, transactions, connections)</li>
 *   <li>Vector store operations (insert, search, update, delete)</li>
 *   <li>LLM embedding calls and retries</li>
 *   <li>Error tracking by category and type</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b></p>
 * All metrics use thread-safe constructs (LongAdder, AtomicLong, ConcurrentHashMap)
 * to support concurrent access without synchronization overhead.
 *
 * @author YesBoss Team
 * @version 1.0
 */
public class MemoryMetricsImpl implements MemoryMetrics {

    private static final Logger logger = LoggerFactory.getLogger(MemoryMetricsImpl.class);

    private final boolean enabled;
    private final boolean trackPerformance;
    private final boolean trackErrors;

    // ==================== Resource Metrics ====================
    private final LongAdder resourceSaveCount;
    private final LongAdder resourceSaveSuccess;
    private final LongAdder resourceSaveFailure;
    private final AtomicLong resourceSaveMinTime;
    private final AtomicLong resourceSaveMaxTime;
    private final LongAdder resourceSaveTotalTime;

    private final LongAdder resourceQueryCount;
    private final LongAdder resourceQuerySuccess;
    private final LongAdder resourceQueryFailure;
    private final AtomicLong resourceQueryMinTime;
    private final AtomicLong resourceQueryMaxTime;
    private final LongAdder resourceQueryTotalTime;
    private final LongAdder resourceQueryResultCount;

    // ==================== Snippet Metrics ====================
    private final LongAdder snippetSaveCount;
    private final LongAdder snippetSaveSuccess;
    private final LongAdder snippetSaveFailure;
    private final AtomicLong snippetSaveMinTime;
    private final AtomicLong snippetSaveMaxTime;
    private final LongAdder snippetSaveTotalTime;

    private final LongAdder snippetExtractionCount;
    private final LongAdder snippetExtractionSuccess;
    private final LongAdder snippetExtractionFailure;
    private final AtomicLong snippetExtractionMinTime;
    private final AtomicLong snippetExtractionMaxTime;
    private final LongAdder snippetExtractionTotalTime;
    private final LongAdder snippetExtractionTotalCount;

    // ==================== Preference Metrics ====================
    private final LongAdder preferenceSaveCount;
    private final LongAdder preferenceSaveSuccess;
    private final LongAdder preferenceSaveFailure;
    private final AtomicLong preferenceSaveMinTime;
    private final AtomicLong preferenceSaveMaxTime;
    private final LongAdder preferenceSaveTotalTime;

    private final LongAdder preferenceUpdateCount;
    private final LongAdder preferenceUpdateSuccess;
    private final LongAdder preferenceUpdateFailure;
    private final AtomicLong preferenceUpdateMinTime;
    private final AtomicLong preferenceUpdateMaxTime;
    private final LongAdder preferenceUpdateTotalTime;

    // ==================== Cache Metrics ====================
    private final LongAdder cacheHits;
    private final LongAdder cacheMisses;
    private final LongAdder cacheEvictions;
    private final LongAdder cacheRefreshCount;
    private final AtomicLong cacheRefreshMinTime;
    private final AtomicLong cacheRefreshMaxTime;
    private final LongAdder cacheRefreshTotalTime;

    // ==================== Database Metrics ====================
    private final Map<String, LongAdder> dbQueryCounts;
    private final Map<String, LongAdder> dbQuerySuccess;
    private final Map<String, LongAdder> dbQueryFailure;
    private final Map<String, AtomicLong> dbQueryMinTime;
    private final Map<String, AtomicLong> dbQueryMaxTime;
    private final Map<String, LongAdder> dbQueryTotalTime;

    private final LongAdder connectionAcquired;
    private final LongAdder connectionReleased;
    private final LongAdder connectionTotalHeldTime;

    private final LongAdder transactionCount;
    private final LongAdder transactionSuccess;
    private final LongAdder transactionFailure;
    private final AtomicLong transactionMinTime;
    private final AtomicLong transactionMaxTime;
    private final LongAdder transactionTotalTime;

    // ==================== Vector Store Metrics ====================
    private final LongAdder vectorInsertCount;
    private final LongAdder vectorInsertSuccess;
    private final LongAdder vectorInsertFailure;
    private final LongAdder vectorInsertTotalCount;
    private final AtomicLong vectorInsertMinTime;
    private final AtomicLong vectorInsertMaxTime;
    private final LongAdder vectorInsertTotalTime;

    private final LongAdder vectorSearchCount;
    private final AtomicLong vectorSearchMinTime;
    private final AtomicLong vectorSearchMaxTime;
    private final LongAdder vectorSearchTotalTime;
    private final LongAdder vectorSearchTotalResults;

    private final LongAdder vectorUpdateCount;
    private final LongAdder vectorUpdateSuccess;
    private final LongAdder vectorUpdateFailure;
    private final LongAdder vectorUpdateTotalTime;

    private final LongAdder vectorDeleteCount;
    private final LongAdder vectorDeleteSuccess;
    private final LongAdder vectorDeleteFailure;
    private final LongAdder vectorDeleteTotalTime;

    // ==================== LLM Metrics ====================
    private final LongAdder embeddingCallCount;
    private final LongAdder embeddingCallSuccess;
    private final LongAdder embeddingCallFailure;
    private final LongAdder embeddingTotalTokens;
    private final LongAdder embeddingTotalTime;
    private final LongAdder embeddingRetryCount;

    // ==================== Error Metrics ====================
    private final Map<String, LongAdder> errorCounts;

    /**
     * Create memory metrics with default settings (enabled, performance and error tracking on).
     */
    public MemoryMetricsImpl() {
        this(true, true, true);
    }

    /**
     * Create memory metrics with custom settings.
     *
     * @param enabled Enable metrics collection
     * @param trackPerformance Track performance metrics
     * @param trackErrors Track error metrics
     */
    public MemoryMetricsImpl(boolean enabled, boolean trackPerformance, boolean trackErrors) {
        this.enabled = enabled;
        this.trackPerformance = trackPerformance;
        this.trackErrors = trackErrors;

        // Initialize resource metrics
        this.resourceSaveCount = new LongAdder();
        this.resourceSaveSuccess = new LongAdder();
        this.resourceSaveFailure = new LongAdder();
        this.resourceSaveMinTime = new AtomicLong(Long.MAX_VALUE);
        this.resourceSaveMaxTime = new AtomicLong(0);
        this.resourceSaveTotalTime = new LongAdder();

        this.resourceQueryCount = new LongAdder();
        this.resourceQuerySuccess = new LongAdder();
        this.resourceQueryFailure = new LongAdder();
        this.resourceQueryMinTime = new AtomicLong(Long.MAX_VALUE);
        this.resourceQueryMaxTime = new AtomicLong(0);
        this.resourceQueryTotalTime = new LongAdder();
        this.resourceQueryResultCount = new LongAdder();

        // Initialize snippet metrics
        this.snippetSaveCount = new LongAdder();
        this.snippetSaveSuccess = new LongAdder();
        this.snippetSaveFailure = new LongAdder();
        this.snippetSaveMinTime = new AtomicLong(Long.MAX_VALUE);
        this.snippetSaveMaxTime = new AtomicLong(0);
        this.snippetSaveTotalTime = new LongAdder();

        this.snippetExtractionCount = new LongAdder();
        this.snippetExtractionSuccess = new LongAdder();
        this.snippetExtractionFailure = new LongAdder();
        this.snippetExtractionMinTime = new AtomicLong(Long.MAX_VALUE);
        this.snippetExtractionMaxTime = new AtomicLong(0);
        this.snippetExtractionTotalTime = new LongAdder();
        this.snippetExtractionTotalCount = new LongAdder();

        // Initialize preference metrics
        this.preferenceSaveCount = new LongAdder();
        this.preferenceSaveSuccess = new LongAdder();
        this.preferenceSaveFailure = new LongAdder();
        this.preferenceSaveMinTime = new AtomicLong(Long.MAX_VALUE);
        this.preferenceSaveMaxTime = new AtomicLong(0);
        this.preferenceSaveTotalTime = new LongAdder();

        this.preferenceUpdateCount = new LongAdder();
        this.preferenceUpdateSuccess = new LongAdder();
        this.preferenceUpdateFailure = new LongAdder();
        this.preferenceUpdateMinTime = new AtomicLong(Long.MAX_VALUE);
        this.preferenceUpdateMaxTime = new AtomicLong(0);
        this.preferenceUpdateTotalTime = new LongAdder();

        // Initialize cache metrics
        this.cacheHits = new LongAdder();
        this.cacheMisses = new LongAdder();
        this.cacheEvictions = new LongAdder();
        this.cacheRefreshCount = new LongAdder();
        this.cacheRefreshMinTime = new AtomicLong(Long.MAX_VALUE);
        this.cacheRefreshMaxTime = new AtomicLong(0);
        this.cacheRefreshTotalTime = new LongAdder();

        // Initialize database metrics
        this.dbQueryCounts = new ConcurrentHashMap<>();
        this.dbQuerySuccess = new ConcurrentHashMap<>();
        this.dbQueryFailure = new ConcurrentHashMap<>();
        this.dbQueryMinTime = new ConcurrentHashMap<>();
        this.dbQueryMaxTime = new ConcurrentHashMap<>();
        this.dbQueryTotalTime = new ConcurrentHashMap<>();

        this.connectionAcquired = new LongAdder();
        this.connectionReleased = new LongAdder();
        this.connectionTotalHeldTime = new LongAdder();

        this.transactionCount = new LongAdder();
        this.transactionSuccess = new LongAdder();
        this.transactionFailure = new LongAdder();
        this.transactionMinTime = new AtomicLong(Long.MAX_VALUE);
        this.transactionMaxTime = new AtomicLong(0);
        this.transactionTotalTime = new LongAdder();

        // Initialize vector store metrics
        this.vectorInsertCount = new LongAdder();
        this.vectorInsertSuccess = new LongAdder();
        this.vectorInsertFailure = new LongAdder();
        this.vectorInsertTotalCount = new LongAdder();
        this.vectorInsertMinTime = new AtomicLong(Long.MAX_VALUE);
        this.vectorInsertMaxTime = new AtomicLong(0);
        this.vectorInsertTotalTime = new LongAdder();

        this.vectorSearchCount = new LongAdder();
        this.vectorSearchMinTime = new AtomicLong(Long.MAX_VALUE);
        this.vectorSearchMaxTime = new AtomicLong(0);
        this.vectorSearchTotalTime = new LongAdder();
        this.vectorSearchTotalResults = new LongAdder();

        this.vectorUpdateCount = new LongAdder();
        this.vectorUpdateSuccess = new LongAdder();
        this.vectorUpdateFailure = new LongAdder();
        this.vectorUpdateTotalTime = new LongAdder();

        this.vectorDeleteCount = new LongAdder();
        this.vectorDeleteSuccess = new LongAdder();
        this.vectorDeleteFailure = new LongAdder();
        this.vectorDeleteTotalTime = new LongAdder();

        // Initialize LLM metrics
        this.embeddingCallCount = new LongAdder();
        this.embeddingCallSuccess = new LongAdder();
        this.embeddingCallFailure = new LongAdder();
        this.embeddingTotalTokens = new LongAdder();
        this.embeddingTotalTime = new LongAdder();
        this.embeddingRetryCount = new LongAdder();

        // Initialize error metrics
        this.errorCounts = new ConcurrentHashMap<>();
    }

    // ==================== Resource Metrics ====================

    @Override
    public void recordResourceSaveStart() {
        if (!enabled) return;
        resourceSaveCount.increment();
    }

    @Override
    public void recordResourceSaveSuccess(long durationMs) {
        if (!enabled) return;
        resourceSaveSuccess.increment();
        if (trackPerformance) {
            recordPerformanceTime(resourceSaveMinTime, resourceSaveMaxTime, resourceSaveTotalTime, durationMs);
        }
    }

    @Override
    public void recordResourceSaveFailure(String errorCode) {
        if (!enabled) return;
        resourceSaveFailure.increment();
        if (trackErrors) {
            recordError("resourceSave", errorCode);
        }
    }

    @Override
    public void recordResourceQueryStart() {
        if (!enabled) return;
        resourceQueryCount.increment();
    }

    @Override
    public void recordResourceQuerySuccess(long durationMs, int resultCount) {
        if (!enabled) return;
        resourceQuerySuccess.increment();
        resourceQueryResultCount.add(resultCount);
        if (trackPerformance) {
            recordPerformanceTime(resourceQueryMinTime, resourceQueryMaxTime, resourceQueryTotalTime, durationMs);
        }
    }

    @Override
    public void recordResourceQueryFailure(String errorCode) {
        if (!enabled) return;
        resourceQueryFailure.increment();
        if (trackErrors) {
            recordError("resourceQuery", errorCode);
        }
    }

    // ==================== Snippet Metrics ====================

    @Override
    public void recordSnippetSaveStart() {
        if (!enabled) return;
        snippetSaveCount.increment();
    }

    @Override
    public void recordSnippetSaveSuccess(long durationMs) {
        if (!enabled) return;
        snippetSaveSuccess.increment();
        if (trackPerformance) {
            recordPerformanceTime(snippetSaveMinTime, snippetSaveMaxTime, snippetSaveTotalTime, durationMs);
        }
    }

    @Override
    public void recordSnippetSaveFailure(String errorCode) {
        if (!enabled) return;
        snippetSaveFailure.increment();
        if (trackErrors) {
            recordError("snippetSave", errorCode);
        }
    }

    @Override
    public void recordSnippetExtractionStart() {
        if (!enabled) return;
        snippetExtractionCount.increment();
    }

    @Override
    public void recordSnippetExtractionSuccess(long durationMs, int snippetCount) {
        if (!enabled) return;
        snippetExtractionSuccess.increment();
        snippetExtractionTotalCount.add(snippetCount);
        if (trackPerformance) {
            recordPerformanceTime(snippetExtractionMinTime, snippetExtractionMaxTime,
                snippetExtractionTotalTime, durationMs);
        }
    }

    @Override
    public void recordSnippetExtractionFailure(String errorCode) {
        if (!enabled) return;
        snippetExtractionFailure.increment();
        if (trackErrors) {
            recordError("snippetExtraction", errorCode);
        }
    }

    // ==================== Preference Metrics ====================

    @Override
    public void recordPreferenceSaveStart() {
        if (!enabled) return;
        preferenceSaveCount.increment();
    }

    @Override
    public void recordPreferenceSaveSuccess(long durationMs) {
        if (!enabled) return;
        preferenceSaveSuccess.increment();
        if (trackPerformance) {
            recordPerformanceTime(preferenceSaveMinTime, preferenceSaveMaxTime, preferenceSaveTotalTime, durationMs);
        }
    }

    @Override
    public void recordPreferenceSaveFailure(String errorCode) {
        if (!enabled) return;
        preferenceSaveFailure.increment();
        if (trackErrors) {
            recordError("preferenceSave", errorCode);
        }
    }

    @Override
    public void recordPreferenceUpdateStart() {
        if (!enabled) return;
        preferenceUpdateCount.increment();
    }

    @Override
    public void recordPreferenceUpdateSuccess(long durationMs) {
        if (!enabled) return;
        preferenceUpdateSuccess.increment();
        if (trackPerformance) {
            recordPerformanceTime(preferenceUpdateMinTime, preferenceUpdateMaxTime,
                preferenceUpdateTotalTime, durationMs);
        }
    }

    @Override
    public void recordPreferenceUpdateFailure(String errorCode) {
        if (!enabled) return;
        preferenceUpdateFailure.increment();
        if (trackErrors) {
            recordError("preferenceUpdate", errorCode);
        }
    }

    // ==================== Cache Metrics ====================

    @Override
    public void recordCacheHit() {
        if (!enabled) return;
        cacheHits.increment();
    }

    @Override
    public void recordCacheMiss() {
        if (!enabled) return;
        cacheMisses.increment();
    }

    @Override
    public void recordCacheEviction() {
        if (!enabled) return;
        cacheEvictions.increment();
    }

    @Override
    public void recordCacheRefresh(long durationMs) {
        if (!enabled) return;
        cacheRefreshCount.increment();
        if (trackPerformance) {
            recordPerformanceTime(cacheRefreshMinTime, cacheRefreshMaxTime, cacheRefreshTotalTime, durationMs);
        }
    }

    // ==================== Database Metrics ====================

    @Override
    public void recordDatabaseQuery(String queryType, long durationMs, boolean success) {
        if (!enabled) return;
        dbQueryCounts.computeIfAbsent(queryType, k -> new LongAdder()).increment();
        if (success) {
            dbQuerySuccess.computeIfAbsent(queryType, k -> new LongAdder()).increment();
        } else {
            dbQueryFailure.computeIfAbsent(queryType, k -> new LongAdder()).increment();
            if (trackErrors) {
                recordError("database", queryType);
            }
        }
        if (trackPerformance) {
            AtomicLong minTime = dbQueryMinTime.computeIfAbsent(queryType, k -> new AtomicLong(Long.MAX_VALUE));
            AtomicLong maxTime = dbQueryMaxTime.computeIfAbsent(queryType, k -> new AtomicLong(0));
            LongAdder totalTime = dbQueryTotalTime.computeIfAbsent(queryType, k -> new LongAdder());
            recordPerformanceTime(minTime, maxTime, totalTime, durationMs);
        }
    }

    @Override
    public void recordConnectionAcquired() {
        if (!enabled) return;
        connectionAcquired.increment();
    }

    @Override
    public void recordConnectionReleased(long durationMs) {
        if (!enabled) return;
        connectionReleased.increment();
        if (trackPerformance) {
            connectionTotalHeldTime.add(durationMs);
        }
    }

    @Override
    public void recordTransactionStart() {
        if (!enabled) return;
        transactionCount.increment();
    }

    @Override
    public void recordTransactionCommit(long durationMs, boolean success) {
        if (!enabled) return;
        if (success) {
            transactionSuccess.increment();
        } else {
            transactionFailure.increment();
        }
        if (trackPerformance) {
            recordPerformanceTime(transactionMinTime, transactionMaxTime, transactionTotalTime, durationMs);
        }
    }

    // ==================== Vector Store Metrics ====================

    @Override
    public void recordVectorInsert(long durationMs, int vectorCount, boolean success) {
        if (!enabled) return;
        vectorInsertCount.increment();
        vectorInsertTotalCount.add(vectorCount);
        if (success) {
            vectorInsertSuccess.increment();
        } else {
            vectorInsertFailure.increment();
            if (trackErrors) {
                recordError("vectorStore", "INSERT_FAILED");
            }
        }
        if (trackPerformance) {
            recordPerformanceTime(vectorInsertMinTime, vectorInsertMaxTime, vectorInsertTotalTime, durationMs);
        }
    }

    @Override
    public void recordVectorSearch(long durationMs, int topK, int resultCount) {
        if (!enabled) return;
        vectorSearchCount.increment();
        vectorSearchTotalResults.add(resultCount);
        if (trackPerformance) {
            recordPerformanceTime(vectorSearchMinTime, vectorSearchMaxTime, vectorSearchTotalTime, durationMs);
        }
    }

    @Override
    public void recordVectorUpdate(long durationMs, boolean success) {
        if (!enabled) return;
        vectorUpdateCount.increment();
        if (success) {
            vectorUpdateSuccess.increment();
        } else {
            vectorUpdateFailure.increment();
            if (trackErrors) {
                recordError("vectorStore", "UPDATE_FAILED");
            }
        }
        if (trackPerformance) {
            vectorUpdateTotalTime.add(durationMs);
        }
    }

    @Override
    public void recordVectorDelete(long durationMs, boolean success) {
        if (!enabled) return;
        vectorDeleteCount.increment();
        if (success) {
            vectorDeleteSuccess.increment();
        } else {
            vectorDeleteFailure.increment();
            if (trackErrors) {
                recordError("vectorStore", "DELETE_FAILED");
            }
        }
        if (trackPerformance) {
            vectorDeleteTotalTime.add(durationMs);
        }
    }

    // ==================== LLM Metrics ====================

    @Override
    public void recordEmbeddingCall(long durationMs, int tokenCount, boolean success) {
        if (!enabled) return;
        embeddingCallCount.increment();
        embeddingTotalTokens.add(tokenCount);
        if (success) {
            embeddingCallSuccess.increment();
        } else {
            embeddingCallFailure.increment();
        }
        if (trackPerformance) {
            embeddingTotalTime.add(durationMs);
        }
    }

    @Override
    public void recordEmbeddingRetry() {
        if (!enabled) return;
        embeddingRetryCount.increment();
    }

    // ==================== Error Metrics ====================

    @Override
    public void recordError(String category, String errorCode) {
        if (!enabled || !trackErrors) return;
        String key = category + ":" + errorCode;
        errorCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    // ==================== Helper Methods ====================

    private void recordPerformanceTime(AtomicLong minTime, AtomicLong maxTime,
                                      LongAdder totalTime, long durationMs) {
        // Update min
        long currentMin = minTime.get();
        while (durationMs < currentMin && !minTime.compareAndSet(currentMin, durationMs)) {
            currentMin = minTime.get();
        }

        // Update max
        long currentMax = maxTime.get();
        while (durationMs > currentMax && !maxTime.compareAndSet(currentMax, durationMs)) {
            currentMax = maxTime.get();
        }

        // Add to total
        totalTime.add(durationMs);
    }

    private long getSafeLong(AtomicLong atomic) {
        long value = atomic.get();
        return value == Long.MAX_VALUE ? 0 : value;
    }

    private double getAverage(LongAdder count, LongAdder total) {
        long cnt = count.sum();
        return cnt == 0 ? 0.0 : (double) total.sum() / cnt;
    }

    private double getSuccessRate(LongAdder success, LongAdder total) {
        long ttl = total.sum();
        return ttl == 0 ? 0.0 : (double) success.sum() / ttl;
    }

    // ==================== Metric Retrieval ====================

    @Override
    public Map<String, Object> getSnapshot() {
        Map<String, Object> snapshot = new ConcurrentHashMap<>();

        snapshot.put("enabled", enabled);
        snapshot.put("trackPerformance", trackPerformance);
        snapshot.put("trackErrors", trackErrors);

        // Resource metrics
        Map<String, Object> resourceMetrics = new ConcurrentHashMap<>();
        resourceMetrics.put("save", getResourceSaveMetrics());
        resourceMetrics.put("query", getResourceQueryMetrics());
        snapshot.put("resources", resourceMetrics);

        // Snippet metrics
        Map<String, Object> snippetMetrics = new ConcurrentHashMap<>();
        snippetMetrics.put("save", getSnippetSaveMetrics());
        snippetMetrics.put("extraction", getSnippetExtractionMetrics());
        snapshot.put("snippets", snippetMetrics);

        // Preference metrics
        Map<String, Object> preferenceMetrics = new ConcurrentHashMap<>();
        preferenceMetrics.put("save", getPreferenceSaveMetrics());
        preferenceMetrics.put("update", getPreferenceUpdateMetrics());
        snapshot.put("preferences", preferenceMetrics);

        // Cache metrics
        snapshot.put("cache", getCacheMetrics());

        // Database metrics
        snapshot.put("database", getDatabaseMetrics());

        // Vector store metrics
        snapshot.put("vectorStore", getVectorStoreMetrics());

        // LLM metrics
        snapshot.put("llm", getLLMMetrics());

        // Error metrics
        if (trackErrors) {
            snapshot.put("errors", getErrorMetrics());
        }

        return snapshot;
    }

    private Map<String, Object> getResourceSaveMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        metrics.put("count", resourceSaveCount.sum());
        metrics.put("success", resourceSaveSuccess.sum());
        metrics.put("failure", resourceSaveFailure.sum());
        metrics.put("successRate", getSuccessRate(resourceSaveSuccess, resourceSaveCount));
        if (trackPerformance) {
            metrics.put("minTimeMs", getSafeLong(resourceSaveMinTime));
            metrics.put("maxTimeMs", resourceSaveMaxTime.get());
            metrics.put("avgTimeMs", getAverage(resourceSaveSuccess, resourceSaveTotalTime));
        }
        return metrics;
    }

    private Map<String, Object> getResourceQueryMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        metrics.put("count", resourceQueryCount.sum());
        metrics.put("success", resourceQuerySuccess.sum());
        metrics.put("failure", resourceQueryFailure.sum());
        metrics.put("successRate", getSuccessRate(resourceQuerySuccess, resourceQueryCount));
        metrics.put("totalResults", resourceQueryResultCount.sum());
        if (trackPerformance) {
            metrics.put("minTimeMs", getSafeLong(resourceQueryMinTime));
            metrics.put("maxTimeMs", resourceQueryMaxTime.get());
            metrics.put("avgTimeMs", getAverage(resourceQuerySuccess, resourceQueryTotalTime));
        }
        return metrics;
    }

    private Map<String, Object> getSnippetSaveMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        metrics.put("count", snippetSaveCount.sum());
        metrics.put("success", snippetSaveSuccess.sum());
        metrics.put("failure", snippetSaveFailure.sum());
        metrics.put("successRate", getSuccessRate(snippetSaveSuccess, snippetSaveCount));
        if (trackPerformance) {
            metrics.put("minTimeMs", getSafeLong(snippetSaveMinTime));
            metrics.put("maxTimeMs", snippetSaveMaxTime.get());
            metrics.put("avgTimeMs", getAverage(snippetSaveSuccess, snippetSaveTotalTime));
        }
        return metrics;
    }

    private Map<String, Object> getSnippetExtractionMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        metrics.put("count", snippetExtractionCount.sum());
        metrics.put("success", snippetExtractionSuccess.sum());
        metrics.put("failure", snippetExtractionFailure.sum());
        metrics.put("successRate", getSuccessRate(snippetExtractionSuccess, snippetExtractionCount));
        metrics.put("totalSnippets", snippetExtractionTotalCount.sum());
        if (trackPerformance) {
            metrics.put("minTimeMs", getSafeLong(snippetExtractionMinTime));
            metrics.put("maxTimeMs", snippetExtractionMaxTime.get());
            metrics.put("avgTimeMs", getAverage(snippetExtractionSuccess, snippetExtractionTotalTime));
        }
        return metrics;
    }

    private Map<String, Object> getPreferenceSaveMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        metrics.put("count", preferenceSaveCount.sum());
        metrics.put("success", preferenceSaveSuccess.sum());
        metrics.put("failure", preferenceSaveFailure.sum());
        metrics.put("successRate", getSuccessRate(preferenceSaveSuccess, preferenceSaveCount));
        if (trackPerformance) {
            metrics.put("minTimeMs", getSafeLong(preferenceSaveMinTime));
            metrics.put("maxTimeMs", preferenceSaveMaxTime.get());
            metrics.put("avgTimeMs", getAverage(preferenceSaveSuccess, preferenceSaveTotalTime));
        }
        return metrics;
    }

    private Map<String, Object> getPreferenceUpdateMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        metrics.put("count", preferenceUpdateCount.sum());
        metrics.put("success", preferenceUpdateSuccess.sum());
        metrics.put("failure", preferenceUpdateFailure.sum());
        metrics.put("successRate", getSuccessRate(preferenceUpdateSuccess, preferenceUpdateCount));
        if (trackPerformance) {
            metrics.put("minTimeMs", getSafeLong(preferenceUpdateMinTime));
            metrics.put("maxTimeMs", preferenceUpdateMaxTime.get());
            metrics.put("avgTimeMs", getAverage(preferenceUpdateSuccess, preferenceUpdateTotalTime));
        }
        return metrics;
    }

    private Map<String, Object> getCacheMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        long hits = cacheHits.sum();
        long misses = cacheMisses.sum();
        long total = hits + misses;

        metrics.put("hits", hits);
        metrics.put("misses", misses);
        metrics.put("evictions", cacheEvictions.sum());
        metrics.put("hitRate", total == 0 ? 0.0 : (double) hits / total);

        if (trackPerformance) {
            metrics.put("refreshCount", cacheRefreshCount.sum());
            metrics.put("refreshMinTimeMs", getSafeLong(cacheRefreshMinTime));
            metrics.put("refreshMaxTimeMs", cacheRefreshMaxTime.get());
            metrics.put("refreshAvgTimeMs", getAverage(cacheRefreshCount, cacheRefreshTotalTime));
        }

        return metrics;
    }

    private Map<String, Object> getDatabaseMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();

        // Query metrics by type
        Map<String, Object> queryMetrics = new ConcurrentHashMap<>();
        for (String queryType : dbQueryCounts.keySet()) {
            Map<String, Object> typeMetrics = new ConcurrentHashMap<>();
            LongAdder count = dbQueryCounts.get(queryType);
            LongAdder success = dbQuerySuccess.getOrDefault(queryType, new LongAdder());
            LongAdder failure = dbQueryFailure.getOrDefault(queryType, new LongAdder());

            typeMetrics.put("count", count.sum());
            typeMetrics.put("success", success.sum());
            typeMetrics.put("failure", failure.sum());
            typeMetrics.put("successRate", getSuccessRate(success, count));

            if (trackPerformance) {
                AtomicLong minTime = dbQueryMinTime.get(queryType);
                AtomicLong maxTime = dbQueryMaxTime.get(queryType);
                LongAdder totalTime = dbQueryTotalTime.get(queryType);

                if (minTime != null) {
                    typeMetrics.put("minTimeMs", getSafeLong(minTime));
                    typeMetrics.put("maxTimeMs", maxTime != null ? maxTime.get() : 0);
                    typeMetrics.put("avgTimeMs", getAverage(success, totalTime != null ? totalTime : new LongAdder()));
                }
            }

            queryMetrics.put(queryType, typeMetrics);
        }
        metrics.put("queries", queryMetrics);

        // Connection metrics
        Map<String, Object> connectionMetrics = new ConcurrentHashMap<>();
        connectionMetrics.put("acquired", connectionAcquired.sum());
        connectionMetrics.put("released", connectionReleased.sum());
        connectionMetrics.put("avgHeldTimeMs", getAverage(connectionReleased, connectionTotalHeldTime));
        metrics.put("connections", connectionMetrics);

        // Transaction metrics
        Map<String, Object> transactionMetrics = new ConcurrentHashMap<>();
        transactionMetrics.put("count", transactionCount.sum());
        transactionMetrics.put("success", transactionSuccess.sum());
        transactionMetrics.put("failure", transactionFailure.sum());
        transactionMetrics.put("successRate", getSuccessRate(transactionSuccess, transactionCount));
        if (trackPerformance) {
            transactionMetrics.put("minTimeMs", getSafeLong(transactionMinTime));
            transactionMetrics.put("maxTimeMs", transactionMaxTime.get());
            transactionMetrics.put("avgTimeMs", getAverage(transactionSuccess, transactionTotalTime));
        }
        metrics.put("transactions", transactionMetrics);

        return metrics;
    }

    private Map<String, Object> getVectorStoreMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();

        // Insert metrics
        Map<String, Object> insertMetrics = new ConcurrentHashMap<>();
        insertMetrics.put("count", vectorInsertCount.sum());
        insertMetrics.put("success", vectorInsertSuccess.sum());
        insertMetrics.put("failure", vectorInsertFailure.sum());
        insertMetrics.put("totalVectors", vectorInsertTotalCount.sum());
        insertMetrics.put("successRate", getSuccessRate(vectorInsertSuccess, vectorInsertCount));
        if (trackPerformance) {
            insertMetrics.put("minTimeMs", getSafeLong(vectorInsertMinTime));
            insertMetrics.put("maxTimeMs", vectorInsertMaxTime.get());
            insertMetrics.put("avgTimeMs", getAverage(vectorInsertSuccess, vectorInsertTotalTime));
        }
        metrics.put("insert", insertMetrics);

        // Search metrics
        Map<String, Object> searchMetrics = new ConcurrentHashMap<>();
        searchMetrics.put("count", vectorSearchCount.sum());
        searchMetrics.put("totalResults", vectorSearchTotalResults.sum());
        if (trackPerformance) {
            searchMetrics.put("minTimeMs", getSafeLong(vectorSearchMinTime));
            searchMetrics.put("maxTimeMs", vectorSearchMaxTime.get());
            searchMetrics.put("avgTimeMs", getAverage(vectorSearchCount, vectorSearchTotalTime));
        }
        metrics.put("search", searchMetrics);

        // Update metrics
        Map<String, Object> updateMetrics = new ConcurrentHashMap<>();
        updateMetrics.put("count", vectorUpdateCount.sum());
        updateMetrics.put("success", vectorUpdateSuccess.sum());
        updateMetrics.put("failure", vectorUpdateFailure.sum());
        updateMetrics.put("successRate", getSuccessRate(vectorUpdateSuccess, vectorUpdateCount));
        if (trackPerformance) {
            updateMetrics.put("avgTimeMs", getAverage(vectorUpdateSuccess, vectorUpdateTotalTime));
        }
        metrics.put("update", updateMetrics);

        // Delete metrics
        Map<String, Object> deleteMetrics = new ConcurrentHashMap<>();
        deleteMetrics.put("count", vectorDeleteCount.sum());
        deleteMetrics.put("success", vectorDeleteSuccess.sum());
        deleteMetrics.put("failure", vectorDeleteFailure.sum());
        deleteMetrics.put("successRate", getSuccessRate(vectorDeleteSuccess, vectorDeleteCount));
        if (trackPerformance) {
            deleteMetrics.put("avgTimeMs", getAverage(vectorDeleteSuccess, vectorDeleteTotalTime));
        }
        metrics.put("delete", deleteMetrics);

        return metrics;
    }

    private Map<String, Object> getLLMMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        metrics.put("callCount", embeddingCallCount.sum());
        metrics.put("success", embeddingCallSuccess.sum());
        metrics.put("failure", embeddingCallFailure.sum());
        metrics.put("totalTokens", embeddingTotalTokens.sum());
        metrics.put("retries", embeddingRetryCount.sum());
        metrics.put("successRate", getSuccessRate(embeddingCallSuccess, embeddingCallCount));
        if (trackPerformance) {
            metrics.put("avgTimeMs", getAverage(embeddingCallSuccess, embeddingTotalTime));
        }
        return metrics;
    }

    private Map<String, Long> getErrorMetrics() {
        Map<String, Long> errors = new ConcurrentHashMap<>();
        errorCounts.forEach((key, adder) -> errors.put(key, adder.sum()));
        return errors;
    }

    @Override
    public String getSnapshotAsJson() {
        Map<String, Object> snapshot = getSnapshot();
        return toJson(snapshot);
    }

    private String toJson(Map<String, Object> data) {
        StringBuilder json = new StringBuilder();
        json.append("{");

        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":");

            Object value = entry.getValue();
            if (value instanceof Map) {
                json.append(toJson((Map<String, Object>) value));
            } else if (value instanceof Number) {
                json.append(value);
            } else if (value instanceof Boolean) {
                json.append(value);
            } else {
                json.append("\"").append(value).append("\"");
            }

            first = false;
        }

        json.append("}");
        return json.toString();
    }

    @Override
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("MemoryMetrics{\n");
        sb.append("  enabled=").append(enabled).append("\n");
        sb.append("  resources:\n");
        sb.append("    save: count=").append(resourceSaveCount.sum())
          .append(", successRate=").append(String.format("%.2f%%",
              getSuccessRate(resourceSaveSuccess, resourceSaveCount) * 100)).append("\n");
        sb.append("    query: count=").append(resourceQueryCount.sum())
          .append(", successRate=").append(String.format("%.2f%%",
              getSuccessRate(resourceQuerySuccess, resourceQueryCount) * 100)).append("\n");
        sb.append("  cache: hitRate=").append(String.format("%.2f%%",
            (cacheHits.sum() * 100.0 / Math.max(1, cacheHits.sum() + cacheMisses.sum())))).append("\n");
        sb.append("  vectorStore:\n");
        sb.append("    insert: count=").append(vectorInsertCount.sum())
          .append(", successRate=").append(String.format("%.2f%%",
              getSuccessRate(vectorInsertSuccess, vectorInsertCount) * 100)).append("\n");
        sb.append("    search: count=").append(vectorSearchCount.sum())
          .append(", avgTime=").append(String.format("%.2fms",
              getAverage(vectorSearchCount, vectorSearchTotalTime))).append("\n");
        sb.append("  llm: calls=").append(embeddingCallCount.sum())
          .append(", retries=").append(embeddingRetryCount.sum())
          .append(", avgTime=").append(String.format("%.2fms",
              getAverage(embeddingCallSuccess, embeddingTotalTime))).append("\n");
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isPerformanceTrackingEnabled() {
        return trackPerformance;
    }

    @Override
    public boolean isErrorTrackingEnabled() {
        return trackErrors;
    }

    @Override
    public void reset() {
        // Reset resource metrics
        resourceSaveCount.reset();
        resourceSaveSuccess.reset();
        resourceSaveFailure.reset();
        resourceSaveMinTime.set(Long.MAX_VALUE);
        resourceSaveMaxTime.set(0);
        resourceSaveTotalTime.reset();

        resourceQueryCount.reset();
        resourceQuerySuccess.reset();
        resourceQueryFailure.reset();
        resourceQueryMinTime.set(Long.MAX_VALUE);
        resourceQueryMaxTime.set(0);
        resourceQueryTotalTime.reset();
        resourceQueryResultCount.reset();

        // Reset snippet metrics
        snippetSaveCount.reset();
        snippetSaveSuccess.reset();
        snippetSaveFailure.reset();
        snippetSaveMinTime.set(Long.MAX_VALUE);
        snippetSaveMaxTime.set(0);
        snippetSaveTotalTime.reset();

        snippetExtractionCount.reset();
        snippetExtractionSuccess.reset();
        snippetExtractionFailure.reset();
        snippetExtractionMinTime.set(Long.MAX_VALUE);
        snippetExtractionMaxTime.set(0);
        snippetExtractionTotalTime.reset();
        snippetExtractionTotalCount.reset();

        // Reset preference metrics
        preferenceSaveCount.reset();
        preferenceSaveSuccess.reset();
        preferenceSaveFailure.reset();
        preferenceSaveMinTime.set(Long.MAX_VALUE);
        preferenceSaveMaxTime.set(0);
        preferenceSaveTotalTime.reset();

        preferenceUpdateCount.reset();
        preferenceUpdateSuccess.reset();
        preferenceUpdateFailure.reset();
        preferenceUpdateMinTime.set(Long.MAX_VALUE);
        preferenceUpdateMaxTime.set(0);
        preferenceUpdateTotalTime.reset();

        // Reset cache metrics
        cacheHits.reset();
        cacheMisses.reset();
        cacheEvictions.reset();
        cacheRefreshCount.reset();
        cacheRefreshMinTime.set(Long.MAX_VALUE);
        cacheRefreshMaxTime.set(0);
        cacheRefreshTotalTime.reset();

        // Reset database metrics
        dbQueryCounts.clear();
        dbQuerySuccess.clear();
        dbQueryFailure.clear();
        dbQueryMinTime.clear();
        dbQueryMaxTime.clear();
        dbQueryTotalTime.clear();

        connectionAcquired.reset();
        connectionReleased.reset();
        connectionTotalHeldTime.reset();

        transactionCount.reset();
        transactionSuccess.reset();
        transactionFailure.reset();
        transactionMinTime.set(Long.MAX_VALUE);
        transactionMaxTime.set(0);
        transactionTotalTime.reset();

        // Reset vector store metrics
        vectorInsertCount.reset();
        vectorInsertSuccess.reset();
        vectorInsertFailure.reset();
        vectorInsertTotalCount.reset();
        vectorInsertMinTime.set(Long.MAX_VALUE);
        vectorInsertMaxTime.set(0);
        vectorInsertTotalTime.reset();

        vectorSearchCount.reset();
        vectorSearchMinTime.set(Long.MAX_VALUE);
        vectorSearchMaxTime.set(0);
        vectorSearchTotalTime.reset();
        vectorSearchTotalResults.reset();

        vectorUpdateCount.reset();
        vectorUpdateSuccess.reset();
        vectorUpdateFailure.reset();
        vectorUpdateTotalTime.reset();

        vectorDeleteCount.reset();
        vectorDeleteSuccess.reset();
        vectorDeleteFailure.reset();
        vectorDeleteTotalTime.reset();

        // Reset LLM metrics
        embeddingCallCount.reset();
        embeddingCallSuccess.reset();
        embeddingCallFailure.reset();
        embeddingTotalTokens.reset();
        embeddingTotalTime.reset();
        embeddingRetryCount.reset();

        // Reset error metrics
        errorCounts.clear();
    }
}
