package tech.yesboss.memory.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.memory.model.*;
import tech.yesboss.memory.query.MemoryQueryService;
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.memory.service.MemoryServiceException;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Memory Controller Implementation
 *
 * <p>Implementation of the Memory API controller with comprehensive validation,
 * error handling, retry logic, and monitoring.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Input validation for all requests</li>
 *   <li>Comprehensive error handling with retry strategies</li>
 *   <li>Performance metrics collection</li>
 *   <li>Configuration management from application-memory.yml</li>
 * </ul>
 */
public class MemoryControllerImpl implements MemoryController {

    private static final Logger logger = LoggerFactory.getLogger(MemoryControllerImpl.class);

    private final MemoryService memoryService;
    private final MemoryQueryService memoryQueryService;
    private final MemoryApiConfig config;

    // Metrics
    private final AtomicLong totalExtractions = new AtomicLong(0);
    private final AtomicLong totalQueries = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);

    public MemoryControllerImpl(MemoryService memoryService,
                                MemoryQueryService memoryQueryService,
                                MemoryApiConfig config) {
        this.memoryService = memoryService;
        this.memoryQueryService = memoryQueryService;
        this.config = config;
    }

    // ==================== Memory Extraction Operations ====================

    @Override
    public ExtractResponse extractFromMessages(ExtractRequest request) {
        return executeWithRetry("extractFromMessages", 3, () -> {
            logger.info("Extracting memories from conversation: {}, session: {}",
                       request.conversationId(), request.sessionId());

            validateExtractRequest(request);

            List<Resource> resources = memoryService.extractFromMessages(
                request.messages(),
                request.conversationId(),
                request.sessionId()
            );

            logger.info("Extraction completed: {} resources created", resources.size());

            return new ExtractResponse(
                resources,
                resources.size(),
                0  // processingTimeMs will be set by executeWithRetry
            );
        });
    }

    @Override
    public BatchExtractResponse batchExtractFromMessages(BatchExtractRequest request) {
        return executeWithRetry("batchExtractFromMessages", 2, () -> {
            logger.info("Batch extracting memories from {} conversations", request.messageBatches().size());

            List<Resource> allResources = memoryService.batchExtractFromMessages(request.messageBatches());

            logger.info("Batch extraction completed: {} resources created", allResources.size());

            return new BatchExtractResponse(
                allResources,
                request.messageBatches().size(),
                allResources.size(),
                0
            );
        });
    }

    @Override
    public ProcessPendingResponse processPendingResources() {
        return executeWithRetry("processPendingResources", 1, () -> {
            logger.info("Processing pending resources for structured memory extraction");

            int processedCount = memoryService.processPendingResources();

            logger.info("Processed {} pending resources", processedCount);

            return new ProcessPendingResponse(processedCount, 0);
        });
    }

    // ==================== Query Operations ====================

    @Override
    public AgenticRagResult queryAgenticRAG(QueryRequest request) {
        return executeWithRetry("queryAgenticRAG", config.getMaxRetryAttempts(), () -> {
            logger.info("Executing AgenticRAG query: {} (topK: {})", request.query(), request.topK());

            validateQueryRequest(request);

            return memoryQueryService.queryMemory(request.query(), request.topK());
        });
    }

    @Override
    public List<Resource> findResourcesByQuery(QueryRequest request) {
        return executeWithRetry("findResourcesByQuery", config.getMaxRetryAttempts(), () -> {
            validateQueryRequest(request);
            return memoryQueryService.findResourcesByQuery(request.query(), request.topK());
        });
    }

    @Override
    public List<Snippet> findSnippetsByQuery(QueryRequest request) {
        return executeWithRetry("findSnippetsByQuery", config.getMaxRetryAttempts(), () -> {
            validateQueryRequest(request);
            return memoryQueryService.findSnippetsByQuery(request.query(), request.topK());
        });
    }

    @Override
    public List<Preference> findPreferencesByQuery(QueryRequest request) {
        return executeWithRetry("findPreferencesByQuery", config.getMaxRetryAttempts(), () -> {
            validateQueryRequest(request);
            return memoryQueryService.findPreferencesByQuery(request.query(), request.topK());
        });
    }

    @Override
    public List<Snippet> fuzzySearchSnippets(FuzzySearchRequest request) {
        return executeWithRetry("fuzzySearchSnippets", 1, () -> {
            if (request.preferenceId() == null || request.preferenceId().isBlank()) {
                return memoryQueryService.fuzzySearchSnippets(request.keyword(), request.topK());
            } else {
                return memoryQueryService.fuzzySearchSnippetsByPreference(
                    request.keyword(),
                    request.preferenceId(),
                    request.topK()
                );
            }
        });
    }

    @Override
    public List<Snippet> semanticSearch(SemanticSearchRequest request) {
        return executeWithRetry("semanticSearch", config.getMaxRetryAttempts(), () -> {
            if (request.startTime() != null && request.endTime() != null) {
                return memoryQueryService.semanticSearch(
                    request.query(),
                    request.preferenceId(),
                    request.startTime(),
                    request.endTime(),
                    request.topK()
                );
            } else {
                return memoryQueryService.semanticSearch(
                    request.query(),
                    request.preferenceId(),
                    request.topK()
                );
            }
        });
    }

    @Override
    public List<Snippet> hybridSearch(HybridSearchRequest request) {
        return executeWithRetry("hybridSearch", config.getMaxRetryAttempts(), () ->
            memoryQueryService.hybridSearch(
                request.query(),
                request.preferenceId(),
                request.topK()
            )
        );
    }

    @Override
    public List<Snippet> searchByTimeWindow(TimeWindowSearchRequest request) {
        return executeWithRetry("searchByTimeWindow", 1, () ->
            memoryQueryService.searchByTimeWindow(
                request.timestamp(),
                request.timeWindow(),
                request.topK()
            )
        );
    }

    @Override
    public MemoryChain findMemoryChainByPreferenceAndTime(MemoryChainRequest request) {
        return executeWithRetry("findMemoryChainByPreferenceAndTime", 1, () ->
            memoryQueryService.findMemoryChainByPreferenceAndTime(
                request.preferenceId(),
                request.startTime(),
                request.endTime()
            )
        );
    }

    @Override
    public List<MemoryChain> findMemoryChainsBySessionId(String sessionId) {
        return executeWithRetry("findMemoryChainsBySessionId", 1, () -> {
            if (sessionId == null || sessionId.isBlank()) {
                throw MemoryApiException.badRequest("Session ID cannot be null or blank");
            }
            return memoryQueryService.findMemoryChainsBySessionId(sessionId);
        });
    }

    // ==================== Aggregation Queries ====================

    @Override
    public PreferenceAggregationStats getPreferenceAggregation(String preferenceId) {
        return executeWithRetry("getPreferenceAggregation", 1, () -> {
            if (preferenceId == null || preferenceId.isBlank()) {
                throw MemoryApiException.badRequest("Preference ID cannot be null or blank");
            }
            return memoryQueryService.getPreferenceAggregation(preferenceId);
        });
    }

    @Override
    public SessionAggregationStats getSessionAggregation(String sessionId) {
        return executeWithRetry("getSessionAggregation", 1, () -> {
            if (sessionId == null || sessionId.isBlank()) {
                throw MemoryApiException.badRequest("Session ID cannot be null or blank");
            }
            return memoryQueryService.getSessionAggregation(sessionId);
        });
    }

    // ==================== Batch Operations ====================

    @Override
    public MemoryService.BatchEmbeddingResult processBatchEmbedding() {
        return executeWithRetry("processBatchEmbedding", 1, () -> {
            logger.info("Processing batch embedding for all unvectorized items");

            MemoryService.BatchEmbeddingResult result = memoryService.processBatchEmbedding();

            logger.info("Batch embedding completed: {} items processed", result.getTotalCount());

            return result;
        });
    }

    @Override
    public MemoryService.BatchEmbeddingResult collectTextsForEmbedding() {
        return executeWithRetry("collectTextsForEmbedding", 1, () ->
            memoryService.collectTextsForEmbedding()
        );
    }

    // ==================== Preference Management ====================

    @Override
    public void updatePreferenceSummary(String preferenceId, UpdatePreferenceSummaryRequest request) {
        executeWithRetry("updatePreferenceSummary", 1, () -> {
            if (preferenceId == null || preferenceId.isBlank()) {
                throw MemoryApiException.badRequest("Preference ID cannot be null or blank");
            }

            memoryService.updatePreferenceSummary(preferenceId, request.snippets());

            logger.info("Updated preference summary: {}", preferenceId);

            return null;
        });
    }

    @Override
    public Preference findOrCreatePreferenceForSnippet(Snippet snippet) {
        return executeWithRetry("findOrCreatePreferenceForSnippet", 1, () -> {
            if (snippet == null) {
                throw MemoryApiException.badRequest("Snippet cannot be null");
            }
            return memoryService.findOrCreatePreferenceForSnippet(snippet);
        });
    }

    // ==================== Health & Status ====================

    @Override
    public HealthResponse health() {
        boolean healthy = memoryService.isAvailable();
        return new HealthResponse(
            healthy,
            "Memory API",
            Instant.now().toEpochMilli()
        );
    }

    @Override
    public MetricsResponse metrics() {
        long totalOps = totalExtractions.get() + totalQueries.get();
        long successes = successCount.get();
        double successRate = totalOps > 0 ? (double) successes / totalOps : 0.0;
        double avgResponseTime = totalOps > 0 ? (double) totalResponseTime.get() / totalOps : 0.0;

        return new MetricsResponse(
            totalExtractions.get(),
            totalQueries.get(),
            successRate,
            avgResponseTime,
            Instant.now().toEpochMilli()
        );
    }

    // ==================== Validation Methods ====================

    private void validateExtractRequest(ExtractRequest request) {
        if (request.messages() == null || request.messages().isEmpty()) {
            throw MemoryApiException.validationError("Messages cannot be null or empty");
        }
        if (request.conversationId() == null || request.conversationId().isBlank()) {
            throw MemoryApiException.validationError("Conversation ID cannot be null or blank");
        }
        if (request.sessionId() == null || request.sessionId().isBlank()) {
            throw MemoryApiException.validationError("Session ID cannot be null or blank");
        }
    }

    private void validateQueryRequest(QueryRequest request) {
        if (request.query() == null || request.query().isBlank()) {
            throw MemoryApiException.validationError("Query cannot be null or blank");
        }
        if (request.topK() <= 0) {
            throw MemoryApiException.validationError("TopK must be positive");
        }
        if (request.topK() > config.getMaxTopK()) {
            throw MemoryApiException.validationError("TopK cannot exceed " + config.getMaxTopK());
        }
    }

    // ==================== Retry Logic ====================

    private <T> T executeWithRetry(String operation, int maxAttempts, java.util.concurrent.Callable<T> callable) {
        long startTime = System.currentTimeMillis();
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxAttempts) {
            attempt++;
            try {
                T result = callable.call();

                long processingTime = System.currentTimeMillis() - startTime;
                recordSuccess(operation, processingTime);

                return result;

            } catch (MemoryApiException e) {
                // Don't retry validation errors
                long processingTime = System.currentTimeMillis() - startTime;
                recordFailure(operation, processingTime, e);
                throw e;

            } catch (Exception e) {
                lastException = e;

                if (attempt < maxAttempts) {
                    long delayMs = 100L * (1L << (attempt - 1));  // Exponential backoff
                    logger.warn("Operation {} failed (attempt {}/{}), retrying in {}ms: {}",
                               operation, attempt, maxAttempts, delayMs, e.getMessage());

                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw MemoryApiException.serviceError("Operation interrupted: " + ie.getMessage(), ie);
                    }
                }
            }
        }

        long processingTime = System.currentTimeMillis() - startTime;
        recordFailure(operation, processingTime, lastException);

        throw MemoryApiException.serviceError(
            "Operation " + operation + " failed after " + maxAttempts + " attempts: " +
            (lastException != null ? lastException.getMessage() : "Unknown error"),
            lastException
        );
    }

    // ==================== Metrics Recording ====================

    private void recordSuccess(String operation, long responseTime) {
        if (operation.contains("extract") || operation.contains("process")) {
            totalExtractions.incrementAndGet();
        } else {
            totalQueries.incrementAndGet();
        }
        successCount.incrementAndGet();
        totalResponseTime.addAndGet(responseTime);

        logger.debug("Operation {} succeeded in {}ms", operation, responseTime);
    }

    private void recordFailure(String operation, long responseTime, Throwable error) {
        if (operation.contains("extract") || operation.contains("process")) {
            totalExtractions.incrementAndGet();
        } else {
            totalQueries.incrementAndGet();
        }
        failureCount.incrementAndGet();
        totalResponseTime.addAndGet(responseTime);

        logger.error("Operation {} failed in {}ms: {}", operation, responseTime,
                    error != null ? error.getMessage() : "Unknown error");
    }
}
