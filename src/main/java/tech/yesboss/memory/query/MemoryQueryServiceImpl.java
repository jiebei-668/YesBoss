package tech.yesboss.memory.query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.memory.embedding.EmbeddingService;
import tech.yesboss.memory.model.*;
import tech.yesboss.memory.repository.PreferenceRepository;
import tech.yesboss.memory.repository.ResourceRepository;
import tech.yesboss.memory.repository.SnippetRepository;
import tech.yesboss.memory.vectorstore.VectorStore;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MemoryQueryService implementation
 *
 * Provides unified memory query interface with AgenticRAG three-layer retrieval support
 *
 * NOTE: Some methods are stubbed or simplified due to schema limitations.
 * Methods requiring preference_id or session_id fields in snippets table are marked.
 */
public class MemoryQueryServiceImpl implements MemoryQueryService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryQueryServiceImpl.class);

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final ResourceRepository resourceRepository;
    private final SnippetRepository snippetRepository;
    private final PreferenceRepository preferenceRepository;

    // Similarity threshold for considering results relevant
    private static final float DEFAULT_SIMILARITY_THRESHOLD = 0.7f;

    public MemoryQueryServiceImpl(
            EmbeddingService embeddingService,
            VectorStore vectorStore,
            ResourceRepository resourceRepository,
            SnippetRepository snippetRepository,
            PreferenceRepository preferenceRepository) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.resourceRepository = resourceRepository;
        this.snippetRepository = snippetRepository;
        this.preferenceRepository = preferenceRepository;
    }

    @Override
    public AgenticRagResult queryMemory(String query, int topK) {
        logger.info("Starting AgenticRAG query: query='{}', topK={}", query, topK);

        long startTime = System.currentTimeMillis();
        AgenticRagResult result = new AgenticRagResult();
        result.setQuery(query);
        result.setFinalQuery(query);

        try {
            // Generate query embedding
            float[] queryVector = embeddingService.generateEmbedding(query);

            // Layer 1: Preference Retrieval
            List<VectorStore.SearchResult> preferenceResults = vectorStore.search(queryVector, topK);

            if (!preferenceResults.isEmpty()) {
                // Filter by similarity threshold
                List<String> preferenceIds = preferenceResults.stream()
                        .filter(r -> r.score() >= DEFAULT_SIMILARITY_THRESHOLD)
                        .map(VectorStore.SearchResult::vectorId)
                        .collect(Collectors.toList());

                if (!preferenceIds.isEmpty()) {
                    List<Preference> preferences = preferenceRepository.findByIds(preferenceIds);
                    result.setPreferences(preferences);

                    // Log layer 1 decision
                    DecisionLog layer1Log = new DecisionLog(
                            AgenticRagResult.RetrievalLevel.PREFERENCE,
                            preferences.isEmpty() ? DecisionLog.DecisionType.NO_RESULTS : DecisionLog.DecisionType.SUFFICIENT,
                            preferences.isEmpty() ? "No preferences found" : "Found relevant preferences"
                    );
                    layer1Log.setCandidateCount(preferences.size());
                    layer1Log.setAverageSimilarity(calculateAverageSimilarity(preferenceResults));
                    result.addDecisionLog(layer1Log);

                    // LLM Decision: Check if Preference layer is sufficient
                    if (isPreferenceLayerSufficient(preferences, query)) {
                        logger.info("Preference layer results sufficient, stopping retrieval");
                        result.addDecisionLog(new DecisionLog(
                                AgenticRagResult.RetrievalLevel.PREFERENCE,
                                DecisionLog.DecisionType.SUFFICIENT,
                                "LLM determined preferences are sufficient for the query"
                        ));
                        result.setTotalDurationMs(System.currentTimeMillis() - startTime);
                        return result;
                    }
                }
            }

            // Layer 2: Snippet Retrieval
            List<VectorStore.SearchResult> snippetResults = vectorStore.search(queryVector, topK);

            if (!snippetResults.isEmpty()) {
                List<String> snippetIds = snippetResults.stream()
                        .filter(r -> r.score() >= DEFAULT_SIMILARITY_THRESHOLD)
                        .map(VectorStore.SearchResult::vectorId)
                        .collect(Collectors.toList());

                if (!snippetIds.isEmpty()) {
                    List<Snippet> snippets = snippetRepository.findByIds(snippetIds);
                    result.setSnippets(snippets);

                    // Log layer 2 decision
                    DecisionLog layer2Log = new DecisionLog(
                            AgenticRagResult.RetrievalLevel.SNIPPET,
                            snippets.isEmpty() ? DecisionLog.DecisionType.NO_RESULTS : DecisionLog.DecisionType.SUFFICIENT,
                            snippets.isEmpty() ? "No snippets found" : "Found relevant snippets"
                    );
                    layer2Log.setCandidateCount(snippets.size());
                    layer2Log.setAverageSimilarity(calculateAverageSimilarity(snippetResults));
                    result.addDecisionLog(layer2Log);

                    // LLM Decision: Check if Snippet layer is sufficient
                    if (isSnippetLayerSufficient(snippets, query)) {
                        logger.info("Snippet layer results sufficient, stopping retrieval");
                        result.addDecisionLog(new DecisionLog(
                                AgenticRagResult.RetrievalLevel.SNIPPET,
                                DecisionLog.DecisionType.SUFFICIENT,
                                "LLM determined snippets are sufficient for the query"
                        ));
                        result.setTotalDurationMs(System.currentTimeMillis() - startTime);
                        return result;
                    }
                }
            }

            // Layer 3: Resource Retrieval
            List<VectorStore.SearchResult> resourceResults = vectorStore.search(queryVector, topK);

            if (!resourceResults.isEmpty()) {
                List<String> resourceIds = resourceResults.stream()
                        .filter(r -> r.score() >= DEFAULT_SIMILARITY_THRESHOLD)
                        .map(VectorStore.SearchResult::vectorId)
                        .collect(Collectors.toList());

                if (!resourceIds.isEmpty()) {
                    List<Resource> resources = resourceRepository.findByIds(resourceIds);
                    result.setResources(resources);

                    // Fetch linked snippets for resources
                    if (!resources.isEmpty()) {
                        List<Snippet> linkedSnippets = snippetRepository.findByResourceIds(resourceIds);
                        result.setLinkedSnippets(linkedSnippets);
                    }

                    // Log layer 3 decision
                    DecisionLog layer3Log = new DecisionLog(
                            AgenticRagResult.RetrievalLevel.RESOURCE,
                            resources.isEmpty() ? DecisionLog.DecisionType.NO_RESULTS : DecisionLog.DecisionType.SUFFICIENT,
                            resources.isEmpty() ? "No resources found" : "Found relevant resources"
                    );
                    layer3Log.setCandidateCount(resources.size());
                    layer3Log.setAverageSimilarity(calculateAverageSimilarity(resourceResults));
                    result.addDecisionLog(layer3Log);
                }
            }

            result.addDecisionLog(new DecisionLog(
                    AgenticRagResult.RetrievalLevel.RESOURCE,
                    DecisionLog.DecisionType.SUFFICIENT,
                    "Completed all three layers of retrieval"
            ));

        } catch (Exception e) {
            logger.error("Error during AgenticRAG query: {}", e.getMessage(), e);
            result.addDecisionLog(new DecisionLog(
                    AgenticRagResult.RetrievalLevel.NONE,
                    DecisionLog.DecisionType.NO_RESULTS,
                    "Error during retrieval: " + e.getMessage()
            ));
        }

        result.setTotalDurationMs(System.currentTimeMillis() - startTime);
        logger.info("AgenticRAG query completed: finalLevel={}, totalResults={}, duration={}ms",
                result.getFinalLevel(), result.getTotalResultCount(), result.getTotalDurationMs());

        return result;
    }

    @Override
    public List<Resource> findResourcesByQuery(String query, int topK) {
        logger.debug("Finding resources by query: query='{}', topK={}", query, topK);

        try {
            float[] queryVector = embeddingService.generateEmbedding(query);
            List<VectorStore.SearchResult> searchResults = vectorStore.search(queryVector, topK);

            List<String> resourceIds = searchResults.stream()
                    .filter(r -> r.score() >= DEFAULT_SIMILARITY_THRESHOLD)
                    .map(VectorStore.SearchResult::vectorId)
                    .collect(Collectors.toList());

            return resourceRepository.findByIds(resourceIds);
        } catch (Exception e) {
            logger.error("Error finding resources by query: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Snippet> findSnippetsByQuery(String query, int topK) {
        logger.debug("Finding snippets by query: query='{}', topK={}", query, topK);

        try {
            float[] queryVector = embeddingService.generateEmbedding(query);
            List<VectorStore.SearchResult> searchResults = vectorStore.search(queryVector, topK);

            List<String> snippetIds = searchResults.stream()
                    .filter(r -> r.score() >= DEFAULT_SIMILARITY_THRESHOLD)
                    .map(VectorStore.SearchResult::vectorId)
                    .collect(Collectors.toList());

            return snippetRepository.findByIds(snippetIds);
        } catch (Exception e) {
            logger.error("Error finding snippets by query: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Preference> findPreferencesByQuery(String query, int topK) {
        logger.debug("Finding preferences by query: query='{}', topK={}", query, topK);

        try {
            float[] queryVector = embeddingService.generateEmbedding(query);
            List<VectorStore.SearchResult> searchResults = vectorStore.search(queryVector, topK);

            List<String> preferenceIds = searchResults.stream()
                    .filter(r -> r.score() >= DEFAULT_SIMILARITY_THRESHOLD)
                    .map(VectorStore.SearchResult::vectorId)
                    .collect(Collectors.toList());

            return preferenceRepository.findByIds(preferenceIds);
        } catch (Exception e) {
            logger.error("Error finding preferences by query: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Resource> findResourcesByPreference(String preferenceId, String query, int topK) {
        logger.debug("Finding resources by preference: preferenceId={}, query='{}', topK={}",
                preferenceId, query, topK);

        // STUB: Requires preference_id field in snippets table
        // TODO: Implement once schema is updated
        logger.warn("findResourcesByPreference is stubbed - requires schema updates");
        return Collections.emptyList();
    }

    @Override
    public MemoryChain findMemoryChainByPreferenceAndTime(String preferenceId, long startTime, long endTime) {
        logger.debug("Finding memory chain by preference and time: preferenceId={}, startTime={}, endTime={}",
                preferenceId, startTime, endTime);

        // STUB: Requires preference_id field in snippets table
        // TODO: Implement once schema is updated
        logger.warn("findMemoryChainByPreferenceAndTime is stubbed - requires schema updates");
        return null;
    }

    @Override
    public List<MemoryChain> findMemoryChainsBySessionId(String sessionId) {
        logger.debug("Finding memory chains by session ID: sessionId={}", sessionId);

        // STUB: Requires session_id field in preferences table
        // TODO: Implement once schema is updated
        logger.warn("findMemoryChainsBySessionId is stubbed - requires schema updates");
        return Collections.emptyList();
    }

    @Override
    public List<Snippet> fuzzySearchSnippets(String keyword, int topK) {
        logger.debug("Fuzzy searching snippets: keyword='{}', topK={}", keyword, topK);

        try {
            return snippetRepository.searchByKeyword(keyword, topK);
        } catch (Exception e) {
            logger.error("Error during fuzzy search: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Snippet> fuzzySearchSnippetsByPreference(String keyword, String preferenceId, int topK) {
        logger.debug("Fuzzy searching snippets by preference: keyword='{}', preferenceId={}, topK={}",
                keyword, preferenceId, topK);

        // STUB: Requires preference_id field in snippets table
        // TODO: Implement once schema is updated
        logger.warn("fuzzySearchSnippetsByPreference is stubbed - requires schema updates");
        return Collections.emptyList();
    }

    @Override
    public List<Snippet> semanticSearch(String query, String preferenceId, int topK) {
        return semanticSearch(query, preferenceId, null, null, topK);
    }

    @Override
    public List<Snippet> semanticSearch(String query, String preferenceId, Long startTime, Long endTime, int topK) {
        logger.debug("Semantic search: query='{}', preferenceId={}, startTime={}, endTime={}, topK={}",
                query, preferenceId, startTime, endTime, topK);

        try {
            float[] queryVector = embeddingService.generateEmbedding(query);
            List<VectorStore.SearchResult> searchResults = vectorStore.search(queryVector, topK);

            List<String> snippetIds = searchResults.stream()
                    .filter(r -> r.score() >= DEFAULT_SIMILARITY_THRESHOLD)
                    .map(VectorStore.SearchResult::vectorId)
                    .collect(Collectors.toList());

            List<Snippet> allSnippets = snippetRepository.findByIds(snippetIds);

            // Filter by time range if specified (preference filter skipped - requires schema)
            if (startTime != null && endTime != null) {
                allSnippets = allSnippets.stream()
                        .filter(s -> {
                            LocalDateTime snippetTime = s.getCreatedAt();
                            long timestamp = snippetTime != null ?
                                    snippetTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : 0;
                            return timestamp >= startTime && timestamp <= endTime;
                        })
                        .collect(Collectors.toList());
            }

            return allSnippets.stream()
                    .limit(topK)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Error during semantic search: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Snippet> hybridSearch(String query, String preferenceId, int topK) {
        logger.debug("Hybrid search: query='{}', preferenceId={}, topK={}", query, preferenceId, topK);

        try {
            // Combine semantic and keyword search
            List<Snippet> semanticResults = semanticSearch(query, preferenceId, topK);
            List<Snippet> keywordResults = fuzzySearchSnippets(query, topK);

            // Merge and deduplicate
            Set<String> seenIds = new HashSet<>();
            List<Snippet> mergedResults = new ArrayList<>();

            // Add semantic results first (higher priority)
            for (Snippet snippet : semanticResults) {
                if (seenIds.add(snippet.getId())) {
                    mergedResults.add(snippet);
                }
            }

            // Add keyword results
            for (Snippet snippet : keywordResults) {
                if (seenIds.add(snippet.getId())) {
                    mergedResults.add(snippet);
                }
            }

            return mergedResults.stream()
                    .limit(topK)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Error during hybrid search: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Snippet> recommendByContext(String context, String sessionId, int topK) {
        logger.debug("Recommend by context: sessionId={}, topK={}", sessionId, topK);

        try {
            // Use semantic search with context as query
            float[] contextVector = embeddingService.generateEmbedding(context);
            List<VectorStore.SearchResult> searchResults = vectorStore.search(contextVector, topK);

            List<String> snippetIds = searchResults.stream()
                    .filter(r -> r.score() >= DEFAULT_SIMILARITY_THRESHOLD)
                    .map(VectorStore.SearchResult::vectorId)
                    .collect(Collectors.toList());

            List<Snippet> snippets = snippetRepository.findByIds(snippetIds);

            // Session filter skipped - requires session_id in schema
            return snippets.stream()
                    .limit(topK)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Error during context-based recommendation: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Snippet> searchByTimeWindow(long timestamp, long timeWindow, int topK) {
        logger.debug("Search by time window: timestamp={}, window={}ms, topK={}", timestamp, timeWindow, topK);

        try {
            long startTime = timestamp - timeWindow / 2;
            long endTime = timestamp + timeWindow / 2;

            return snippetRepository.findByTimeRange(startTime, endTime, topK);
        } catch (Exception e) {
            logger.error("Error during time window search: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public PreferenceAggregationStats getPreferenceAggregation(String preferenceId) {
        logger.debug("Getting preference aggregation: preferenceId={}", preferenceId);

        // STUB: Requires preference_id field in snippets table
        // TODO: Implement once schema is updated
        logger.warn("getPreferenceAggregation is stubbed - requires schema updates");
        return null;
    }

    @Override
    public SessionAggregationStats getSessionAggregation(String sessionId) {
        logger.debug("Getting session aggregation: sessionId={}", sessionId);

        // STUB: Requires session_id fields in preferences/snippets tables
        // TODO: Implement once schema is updated
        logger.warn("getSessionAggregation is stubbed - requires schema updates");
        return null;
    }

    // Helper methods

    /**
     * LLM decision: Check if Preference layer results are sufficient
     *
     * This is a simplified implementation. In production, should call LLM API for decision.
     */
    private boolean isPreferenceLayerSufficient(List<Preference> preferences, String query) {
        if (preferences.isEmpty()) {
            return false;
        }

        // Simplified: if we have 3+ preferences, consider it sufficient
        if (preferences.size() >= 3) {
            return true;
        }

        // If only one preference and query is short, might need deeper retrieval
        return preferences.size() > 1 || query.length() > 50;
    }

    /**
     * LLM decision: Check if Snippet layer results are sufficient
     *
     * This is a simplified implementation. In production, should call LLM API for decision.
     */
    private boolean isSnippetLayerSufficient(List<Snippet> snippets, String query) {
        if (snippets.isEmpty()) {
            return false;
        }

        // Simplified: if we have 5+ snippets, consider it sufficient
        return snippets.size() >= 5;
    }

    /**
     * Calculate average similarity from search results
     */
    private double calculateAverageSimilarity(List<VectorStore.SearchResult> results) {
        if (results.isEmpty()) {
            return 0.0;
        }

        return results.stream()
                .map(VectorStore.SearchResult::score)
                .mapToDouble(Float::doubleValue)
                .average()
                .orElse(0.0);
    }
}
