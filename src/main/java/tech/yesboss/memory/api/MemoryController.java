package tech.yesboss.memory.api;

import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.memory.model.*;
import tech.yesboss.memory.service.MemoryService;

import java.util.List;

/**
 * Memory Module RESTful API Controller
 *
 * <p>This controller provides RESTful API endpoints for the memory persistence module,
 * supporting memory extraction, query, and management operations.</p>
 *
 * <p>Design Principles:</p>
 * <ul>
 *   <li>RESTful resource-oriented design</li>
 *   <li>JSON request/response format</li>
 *   <li>Comprehensive input validation</li>
 *   <li>Standardized error responses</li>
 *   <li>Performance monitoring and metrics</li>
 * </ul>
 *
 * <p>Base Path: /api/v1/memory</p>
 */
public interface MemoryController {

    // ==================== Memory Extraction Operations ====================

    /**
     * Extract memories from conversation messages.
     *
     * <p>POST /api/v1/memory/extract</p>
     *
     * @param request Extract request containing messages, conversation ID, and session ID
     * @return Extract response with created resources
     * @throws MemoryApiException if extraction fails
     */
    ExtractResponse extractFromMessages(ExtractRequest request);

    /**
     * Batch extract memories from multiple conversations.
     *
     * <p>POST /api/v1/memory/extract/batch</p>
     *
     * @param request Batch extract request
     * @return Batch extract response with all created resources
     * @throws MemoryApiException if batch extraction fails
     */
    BatchExtractResponse batchExtractFromMessages(BatchExtractRequest request);

    /**
     * Process pending resources that need structured memory extraction.
     *
     * <p>POST /api/v1/memory/process-pending</p>
     *
     * @return Processing response with count of processed resources
     * @throws MemoryApiException if processing fails
     */
    ProcessPendingResponse processPendingResources();

    // ==================== Query Operations ====================

    /**
     * AgenticRAG three-layer query.
     *
     * <p>POST /api/v1/memory/query/agentic-rag</p>
     *
     * @param request Query request with query text and topK parameter
     * @return AgenticRAG result with three-layer retrieval results
     * @throws MemoryApiException if query fails
     */
    AgenticRagResult queryAgenticRAG(QueryRequest request);

    /**
     * Find resources by query text.
     *
     * <p>POST /api/v1/memory/query/resources</p>
     *
     * @param request Query request
     * @return List of matching resources
     * @throws MemoryApiException if query fails
     */
    List<Resource> findResourcesByQuery(QueryRequest request);

    /**
     * Find snippets by query text.
     *
     * <p>POST /api/v1/memory/query/snippets</p>
     *
     * @param request Query request
     * @return List of matching snippets
     * @throws MemoryApiException if query fails
     */
    List<Snippet> findSnippetsByQuery(QueryRequest request);

    /**
     * Find preferences by query text.
     *
     * <p>POST /api/v1/memory/query/preferences</p>
     *
     * @param request Query request
     * @return List of matching preferences
     * @throws MemoryApiException if query fails
     */
    List<Preference> findPreferencesByQuery(QueryRequest request);

    /**
     * Fuzzy search snippets by keyword.
     *
     * <p>POST /api/v1/memory/search/fuzzy</p>
     *
     * @param request Fuzzy search request
     * @return List of matching snippets
     * @throws MemoryApiException if search fails
     */
    List<Snippet> fuzzySearchSnippets(FuzzySearchRequest request);

    /**
     * Semantic search with optional filters.
     *
     * <p>POST /api/v1/memory/search/semantic</p>
     *
     * @param request Semantic search request
     * @return List of matching snippets
     * @throws MemoryApiException if search fails
     */
    List<Snippet> semanticSearch(SemanticSearchRequest request);

    /**
     * Hybrid search combining keyword and semantic search.
     *
     * <p>POST /api/v1/memory/search/hybrid</p>
     *
     * @param request Hybrid search request
     * @return List of matching snippets
     * @throws MemoryApiException if search fails
     */
    List<Snippet> hybridSearch(HybridSearchRequest request);

    /**
     * Search snippets by time window.
     *
     * <p>POST /api/v1/memory/search/time-window</p>
     *
     * @param request Time window search request
     * @return List of matching snippets
     * @throws MemoryApiException if search fails
     */
    List<Snippet> searchByTimeWindow(TimeWindowSearchRequest request);

    /**
     * Find memory chain by preference and time range.
     *
     * <p>POST /api/v1/memory/query/memory-chain</p>
     *
     * @param request Memory chain request
     * @return Memory chain with linked resources, snippets, and preferences
     * @throws MemoryApiException if query fails
     */
    MemoryChain findMemoryChainByPreferenceAndTime(MemoryChainRequest request);

    /**
     * Find memory chains by session ID.
     *
     * <p>GET /api/v1/memory/query/memory-chains?sessionId={sessionId}</p>
     *
     * @param sessionId Session identifier
     * @return List of memory chains for the session
     * @throws MemoryApiException if query fails
     */
    List<MemoryChain> findMemoryChainsBySessionId(String sessionId);

    // ==================== Aggregation Queries ====================

    /**
     * Get preference aggregation statistics.
     *
     * <p>GET /api/v1/memory/aggregation/preference?preferenceId={preferenceId}</p>
     *
     * @param preferenceId Preference identifier
     * @return Preference aggregation statistics
     * @throws MemoryApiException if query fails
     */
    PreferenceAggregationStats getPreferenceAggregation(String preferenceId);

    /**
     * Get session aggregation statistics.
     *
     * <p>GET /api/v1/memory/aggregation/session?sessionId={sessionId}</p>
     *
     * @param sessionId Session identifier
     * @return Session aggregation statistics
     * @throws MemoryApiException if query fails
     */
    SessionAggregationStats getSessionAggregation(String sessionId);

    // ==================== Batch Operations ====================

    /**
     * Process batch embedding for all items that need vectorization.
     *
     * <p>POST /api/v1/memory/batch/embedding</p>
     *
     * @return Batch embedding result with processing statistics
     * @throws MemoryApiException if batch processing fails
     */
    MemoryService.BatchEmbeddingResult processBatchEmbedding();

    /**
     * Collect texts for embedding.
     *
     * <p>GET /api/v1/memory/batch/collect-embedding</p>
     *
     * @return Batch embedding result with collected items
     * @throws MemoryApiException if collection fails
     */
    MemoryService.BatchEmbeddingResult collectTextsForEmbedding();

    // ==================== Preference Management ====================

    /**
     * Update preference summary with new snippets.
     *
     * <p>PUT /api/v1/memory/preference/{preferenceId}/summary</p>
     *
     * @param preferenceId Preference identifier
     * @param request Update preference summary request
     * @throws MemoryApiException if update fails
     */
    void updatePreferenceSummary(String preferenceId, UpdatePreferenceSummaryRequest request);

    /**
     * Find or create preference for a snippet.
     *
     * <p>POST /api/v1/memory/preference/find-or-create</p>
     *
     * @param snippet Snippet to find preference for
     * @return Associated preference
     * @throws MemoryApiException if operation fails
     */
    Preference findOrCreatePreferenceForSnippet(Snippet snippet);

    // ==================== Health & Status ====================

    /**
     * Check if the memory service is available.
     *
     * <p>GET /api/v1/memory/health</p>
     *
     * @return Health status
     */
    HealthResponse health();

    /**
     * Get memory service metrics.
     *
     * <p>GET /api/v1/memory/metrics</p>
     *
     * @return Memory service metrics
     */
    MetricsResponse metrics();

    // ==================== Request/Response DTOs ====================

    /**
     * Request for memory extraction.
     */
    record ExtractRequest(
        List<UnifiedMessage> messages,
        String conversationId,
        String sessionId
    ) {
        public ExtractRequest {
            if (messages == null || messages.isEmpty()) {
                throw new IllegalArgumentException("Messages cannot be null or empty");
            }
            if (conversationId == null || conversationId.isBlank()) {
                throw new IllegalArgumentException("Conversation ID cannot be null or blank");
            }
            if (sessionId == null || sessionId.isBlank()) {
                throw new IllegalArgumentException("Session ID cannot be null or blank");
            }
        }
    }

    /**
     * Response for memory extraction.
     */
    record ExtractResponse(
        List<Resource> resources,
        int totalCount,
        long processingTimeMs
    ) {}

    /**
     * Request for batch memory extraction.
     */
    record BatchExtractRequest(
        List<MemoryService.MessageBatch> messageBatches
    ) {
        public BatchExtractRequest {
            if (messageBatches == null || messageBatches.isEmpty()) {
                throw new IllegalArgumentException("Message batches cannot be null or empty");
            }
        }
    }

    /**
     * Response for batch memory extraction.
     */
    record BatchExtractResponse(
        List<Resource> resources,
        int batchCount,
        int totalCount,
        long processingTimeMs
    ) {}

    /**
     * Response for processing pending resources.
     */
    record ProcessPendingResponse(
        int processedCount,
        long processingTimeMs
    ) {}

    /**
     * Request for query operations.
     */
    record QueryRequest(
        String query,
        int topK
    ) {
        public QueryRequest {
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("Query cannot be null or blank");
            }
            if (topK <= 0) {
                throw new IllegalArgumentException("TopK must be positive");
            }
        }
    }

    /**
     * Request for fuzzy search.
     */
    record FuzzySearchRequest(
        String keyword,
        String preferenceId,
        int topK
    ) {
        public FuzzySearchRequest {
            if (keyword == null || keyword.isBlank()) {
                throw new IllegalArgumentException("Keyword cannot be null or blank");
            }
            if (topK <= 0) {
                throw new IllegalArgumentException("TopK must be positive");
            }
        }
    }

    /**
     * Request for semantic search.
     */
    record SemanticSearchRequest(
        String query,
        String preferenceId,
        Long startTime,
        Long endTime,
        int topK
    ) {
        public SemanticSearchRequest {
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("Query cannot be null or blank");
            }
            if (topK <= 0) {
                throw new IllegalArgumentException("TopK must be positive");
            }
        }
    }

    /**
     * Request for hybrid search.
     */
    record HybridSearchRequest(
        String query,
        String preferenceId,
        int topK
    ) {
        public HybridSearchRequest {
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("Query cannot be null or blank");
            }
            if (topK <= 0) {
                throw new IllegalArgumentException("TopK must be positive");
            }
        }
    }

    /**
     * Request for time window search.
     */
    record TimeWindowSearchRequest(
        long timestamp,
        long timeWindow,
        int topK
    ) {
        public TimeWindowSearchRequest {
            if (timeWindow <= 0) {
                throw new IllegalArgumentException("Time window must be positive");
            }
            if (topK <= 0) {
                throw new IllegalArgumentException("TopK must be positive");
            }
        }
    }

    /**
     * Request for memory chain query.
     */
    record MemoryChainRequest(
        String preferenceId,
        long startTime,
        long endTime
    ) {
        public MemoryChainRequest {
            if (preferenceId == null || preferenceId.isBlank()) {
                throw new IllegalArgumentException("Preference ID cannot be null or blank");
            }
        }
    }

    /**
     * Request for updating preference summary.
     */
    record UpdatePreferenceSummaryRequest(
        List<Snippet> snippets
    ) {
        public UpdatePreferenceSummaryRequest {
            if (snippets == null || snippets.isEmpty()) {
                throw new IllegalArgumentException("Snippets cannot be null or empty");
            }
        }
    }

    /**
     * Health check response.
     */
    record HealthResponse(
        boolean healthy,
        String service,
        long timestamp
    ) {}

    /**
     * Metrics response.
     */
    record MetricsResponse(
        long totalExtractions,
        long totalQueries,
        double successRate,
        double avgResponseTimeMs,
        long timestamp
    ) {}
}
