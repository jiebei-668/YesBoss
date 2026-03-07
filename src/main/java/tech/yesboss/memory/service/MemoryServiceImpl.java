package tech.yesboss.memory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.memory.manager.MemoryManager;
import tech.yesboss.memory.model.*;
import tech.yesboss.memory.processor.ContentProcessor;
import tech.yesboss.memory.processor.ConversationSegment;
import tech.yesboss.memory.repository.PreferenceRepository;
import tech.yesboss.memory.repository.ResourceRepository;
import tech.yesboss.memory.repository.SnippetRepository;
import tech.yesboss.memory.embedding.EmbeddingService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Implementation of MemoryService that orchestrates the memory extraction pipeline.
 *
 * <p>This implementation coordinates between ContentProcessor, MemoryManager, and repositories
 * to extract, store, and manage conversational memories. It handles the complete pipeline
 * from raw messages to structured memories with preference associations.</p>
 *
 * <p>Key features:
 * <ul>
 *   <li>Conversation segmentation by topics</li>
 *   <li>Abstract generation for segments</li>
 *   <li>Structured memory extraction</li>
 *   <li>Preference association and summary updates</li>
 *   <li>Batch processing support</li>
 *   <li>Asynchronous operations</li>
 * </ul>
 */
public class MemoryServiceImpl implements MemoryService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryServiceImpl.class);
    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final ContentProcessor contentProcessor;
    private final MemoryManager memoryManager;
    private final ResourceRepository resourceRepository;
    private final SnippetRepository snippetRepository;
    private final PreferenceRepository preferenceRepository;
    private final EmbeddingService embeddingService;
    private final ExecutorService executorService;
    private final boolean available;

    /**
     * Create a new MemoryServiceImpl with all dependencies.
     *
     * @param contentProcessor Content processor for LLM-based operations
     * @param memoryManager Memory manager for three-layer coordination
     * @param resourceRepository Resource repository
     * @param snippetRepository Snippet repository
     * @param preferenceRepository Preference repository
     */
    public MemoryServiceImpl(ContentProcessor contentProcessor,
                             MemoryManager memoryManager,
                             ResourceRepository resourceRepository,
                             SnippetRepository snippetRepository,
                             PreferenceRepository preferenceRepository,
                             EmbeddingService embeddingService) {
        this.contentProcessor = contentProcessor;
        this.memoryManager = memoryManager;
        this.resourceRepository = resourceRepository;
        this.snippetRepository = snippetRepository;
        this.preferenceRepository = preferenceRepository;
        this.embeddingService = embeddingService;
        this.executorService = Executors.newFixedThreadPool(4);

        // Test availability
        this.available = testAvailability();

        logger.info("MemoryServiceImpl initialized with available: {}", this.available);
    }

    @Override
    public List<Resource> extractFromMessages(List<UnifiedMessage> messages, String conversationId, String sessionId) {
        if (messages == null || messages.isEmpty()) {
            throw new MemoryServiceException("Messages list cannot be null or empty",
                    MemoryServiceException.ERROR_INVALID_INPUT);
        }
        if (conversationId == null || conversationId.trim().isEmpty()) {
            throw new MemoryServiceException("Conversation ID cannot be null or empty",
                    MemoryServiceException.ERROR_INVALID_INPUT);
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new MemoryServiceException("Session ID cannot be null or empty",
                    MemoryServiceException.ERROR_INVALID_INPUT);
        }

        try {
            logger.info("Starting memory extraction for conversation: {}, session: {}", conversationId, sessionId);

            // Step 1: Concatenate conversation content
            String conversationContent = concatenateConversationContent(messages);
            logger.debug("Concatenated conversation content length: {}", conversationContent.length());

            // Step 2: Segment conversation
            List<ConversationSegment> segments = segmentConversation(conversationContent);
            logger.info("Segmented conversation into {} segments", segments.size());

            // Step 3: Process each segment
            List<Resource> resources = new ArrayList<>();
            Map<String, List<Snippet>> allSnippets = new HashMap<>();

            for (ConversationSegment segment : segments) {
                // Generate abstract
                String abstractText = generateSegmentAbstract(segment.getContent());

                // Build and save resource
                Resource resource = buildResource(conversationId, sessionId, segment.getContent(), abstractText);
                Resource savedResource = memoryManager.saveResource(resource);
                resources.add(savedResource);

                // Extract structured memories
                List<Snippet> snippets = extractStructuredMemories(segment.getContent());

                // Set resource ID for each snippet
                for (Snippet snippet : snippets) {
                    snippet.setResourceId(savedResource.getId());
                }

                // Save snippets
                List<Snippet> savedSnippets = memoryManager.saveSnippets(snippets);

                // Group snippets by preference
                Map<String, List<Snippet>> preferenceMap = associateWithPreferences(savedSnippets);
                allSnippets.putAll(preferenceMap);
            }

            // Step 4: Update preference summaries asynchronously
            for (Map.Entry<String, List<Snippet>> entry : allSnippets.entrySet()) {
                String preferenceId = entry.getKey();
                List<Snippet> preferenceSnippets = entry.getValue();

                CompletableFuture.runAsync(() -> {
                    try {
                        updatePreferenceSummary(preferenceId, preferenceSnippets);
                    } catch (Exception e) {
                        logger.error("Failed to update preference summary for {}: {}", preferenceId, e.getMessage());
                    }
                }, executorService);
            }

            logger.info("Completed memory extraction for conversation: {}, created {} resources",
                    conversationId, resources.size());

            return resources;

        } catch (Exception e) {
            throw new MemoryServiceException("Failed to extract memories from messages: " + e.getMessage(),
                    MemoryServiceException.ERROR_PROCESSING_FAILURE, e);
        }
    }

    @Override
    public String concatenateConversationContent(List<UnifiedMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (UnifiedMessage message : messages) {
            if (message != null && message.getDisplayContent() != null) {
                sb.append(message.getDisplayContent()).append("\n");
            }
        }

        return sb.toString().trim();
    }

    @Override
    public List<ConversationSegment> segmentConversation(String conversationContent) {
        if (conversationContent == null || conversationContent.trim().isEmpty()) {
            throw new MemoryServiceException("Conversation content cannot be null or empty",
                    MemoryServiceException.ERROR_INVALID_INPUT);
        }

        try {
            List<ConversationSegment> segments = contentProcessor.segmentConversation(conversationContent);
            logger.debug("Segmented conversation into {} segments", segments.size());
            return segments;

        } catch (Exception e) {
            throw new MemoryServiceException("Failed to segment conversation: " + e.getMessage(),
                    MemoryServiceException.ERROR_SEGMENTATION_FAILURE, e);
        }
    }

    @Override
    public String generateSegmentAbstract(String segmentContent) {
        if (segmentContent == null || segmentContent.trim().isEmpty()) {
            throw new MemoryServiceException("Segment content cannot be null or empty",
                    MemoryServiceException.ERROR_INVALID_INPUT);
        }

        try {
            String abstractText = contentProcessor.generateSegmentAbstract(segmentContent);
            logger.debug("Generated abstract for segment (length: {})", abstractText.length());
            return abstractText;

        } catch (Exception e) {
            throw new MemoryServiceException("Failed to generate segment abstract: " + e.getMessage(),
                    MemoryServiceException.ERROR_LLM_FAILURE, e);
        }
    }

    @Override
    public Resource buildResource(String conversationId, String sessionId, String segmentContent, String abstractText) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            throw new MemoryServiceException("Conversation ID cannot be null or empty",
                    MemoryServiceException.ERROR_INVALID_INPUT);
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new MemoryServiceException("Session ID cannot be null or empty",
                    MemoryServiceException.ERROR_INVALID_INPUT);
        }
        if (segmentContent == null || segmentContent.trim().isEmpty()) {
            throw new MemoryServiceException("Segment content cannot be null or empty",
                    MemoryServiceException.ERROR_INVALID_INPUT);
        }

        return Resource.builder()
                .conversationId(conversationId)
                .sessionId(sessionId)
                .content(segmentContent)
                .abstractText(abstractText)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    @Override
    public List<Snippet> extractStructuredMemories(String resourceContent) {
        if (resourceContent == null || resourceContent.trim().isEmpty()) {
            throw new MemoryServiceException("Resource content cannot be null or empty",
                    MemoryServiceException.ERROR_INVALID_INPUT);
        }

        try {
            List<Snippet> snippets = new ArrayList<>();

            // Extract memories for each type
            for (Snippet.MemoryType memoryType : Snippet.MemoryType.values()) {
                List<String> memories = extractMemoriesByType(resourceContent, memoryType);

                for (String memory : memories) {
                    Snippet snippet = new Snippet();
                    snippet.setSummary(memory);
                    snippet.setMemoryType(memoryType);
                    snippets.add(snippet);
                }
            }

            logger.debug("Extracted {} structured memories", snippets.size());
            return snippets;

        } catch (Exception e) {
            throw new MemoryServiceException("Failed to extract structured memories: " + e.getMessage(),
                    MemoryServiceException.ERROR_EXTRACTION_FAILURE, e);
        }
    }

    @Override
    public List<String> extractMemoriesByType(String resourceContent, Snippet.MemoryType memoryType) {
        if (resourceContent == null || resourceContent.trim().isEmpty()) {
            throw new MemoryServiceException("Resource content cannot be null or empty",
                    MemoryServiceException.ERROR_INVALID_INPUT);
        }
        if (memoryType == null) {
            throw new MemoryServiceException("Memory type cannot be null",
                    MemoryServiceException.ERROR_INVALID_INPUT);
        }

        try {
            List<String> memories = contentProcessor.extractStructuredMemories(resourceContent, memoryType);
            logger.debug("Extracted {} memories for type {}", memories.size(), memoryType);
            return memories;

        } catch (Exception e) {
            throw new MemoryServiceException("Failed to extract memories for type " + memoryType + ": " + e.getMessage(),
                    MemoryServiceException.ERROR_EXTRACTION_FAILURE, e);
        }
    }

    @Override
    public Map<String, List<Snippet>> associateWithPreferences(List<Snippet> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            Map<String, List<Snippet>> preferenceMap = new HashMap<>();

            for (Snippet snippet : snippets) {
                Preference preference = findOrCreatePreferenceForSnippet(snippet);

                preferenceMap.computeIfAbsent(preference.getId(), k -> new ArrayList<>()).add(snippet);
            }

            logger.debug("Associated {} snippets with preferences", snippets.size());
            return preferenceMap;

        } catch (Exception e) {
            throw new MemoryServiceException("Failed to associate snippets with preferences: " + e.getMessage(),
                    MemoryServiceException.ERROR_ASSOCIATION_FAILURE, e);
        }
    }

    @Override
    public void updatePreferenceSummary(String preferenceId, List<Snippet> snippets) {
        if (preferenceId == null || preferenceId.trim().isEmpty()) {
            throw new MemoryServiceException("Preference ID cannot be null or empty",
                    MemoryServiceException.ERROR_INVALID_INPUT);
        }
        if (snippets == null || snippets.isEmpty()) {
            logger.warn("No snippets to update for preference: {}", preferenceId);
            return;
        }

        try {
            // Find preference
            Optional<Preference> preferenceOpt = preferenceRepository.findById(preferenceId);
            if (preferenceOpt.isEmpty()) {
                throw new MemoryServiceException("Preference not found: " + preferenceId,
                        MemoryServiceException.ERROR_PREFERENCE_UPDATE_FAILURE);
            }

            Preference preference = preferenceOpt.get();

            // Generate updated summary
            String updatedSummary = generateUpdatedPreferenceSummary(preference, snippets);

            // Generate embedding for updated summary
            float[] embedding = embeddingService.generateEmbedding(updatedSummary);

            // Update preference
            memoryManager.updatePreferenceSummary(preferenceId, updatedSummary, embedding);

            logger.info("Updated preference summary for: {}", preference.getName());

        } catch (Exception e) {
            throw new MemoryServiceException("Failed to update preference summary: " + e.getMessage(),
                    MemoryServiceException.ERROR_PREFERENCE_UPDATE_FAILURE, e);
        }
    }

    @Override
    public Preference findOrCreatePreferenceForSnippet(Snippet snippet) {
        if (snippet == null) {
            throw new MemoryServiceException("Snippet cannot be null",
                    MemoryServiceException.ERROR_INVALID_INPUT);
        }

        try {
            // Try to find existing preference based on snippet content
            // This is a simplified implementation - in practice, you'd use more sophisticated matching
            String preferenceName = generatePreferenceName(snippet);

            Optional<Preference> existingPreference = preferenceRepository.findByName(preferenceName);
            if (existingPreference.isPresent()) {
                return existingPreference.get();
            }

            // Create new preference
            String summary = generateInitialPreferenceSummary(snippet);
            Preference preference = Preference.builder()
                    .name(preferenceName)
                    .summary(summary)
                    .build();

            Preference savedPreference = memoryManager.savePreference(preference);
            logger.debug("Created new preference: {}", preferenceName);

            return savedPreference;

        } catch (Exception e) {
            throw new MemoryServiceException("Failed to find or create preference: " + e.getMessage(),
                    MemoryServiceException.ERROR_ASSOCIATION_FAILURE, e);
        }
    }

    @Override
    public List<Resource> batchExtractFromMessages(List<MessageBatch> messageBatches) {
        if (messageBatches == null || messageBatches.isEmpty()) {
            throw new MemoryServiceException("Message batches list cannot be null or empty",
                    MemoryServiceException.ERROR_INVALID_INPUT);
        }

        try {
            List<Resource> allResources = new ArrayList<>();

            // Process in batches
            for (int i = 0; i < messageBatches.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, messageBatches.size());
                List<MessageBatch> batch = messageBatches.subList(i, end);

                for (MessageBatch messageBatch : batch) {
                    List<Resource> resources = extractFromMessages(
                            messageBatch.getMessages(),
                            messageBatch.getConversationId(),
                            messageBatch.getSessionId()
                    );
                    allResources.addAll(resources);
                }
            }

            logger.info("Batch extraction completed, created {} resources", allResources.size());
            return allResources;

        } catch (Exception e) {
            throw new MemoryServiceException("Failed to batch extract from messages: " + e.getMessage(),
                    MemoryServiceException.ERROR_BATCH_OPERATION_FAILURE, e);
        }
    }

    @Override
    public int processPendingResources() {
        try {
            // Find resources without snippets
            List<Resource> resourcesWithoutSnippets = resourceRepository.findResourcesWithoutEmbedding();

            int processedCount = 0;
            for (Resource resource : resourcesWithoutSnippets) {
                try {
                    List<Snippet> snippets = extractStructuredMemories(resource.getContent());
                    for (Snippet snippet : snippets) {
                        snippet.setResourceId(resource.getId());
                    }
                    memoryManager.saveSnippets(snippets);
                    processedCount++;
                } catch (Exception e) {
                    logger.error("Failed to process resource {}: {}", resource.getId(), e.getMessage());
                }
            }

            logger.info("Processed {} pending resources", processedCount);
            return processedCount;

        } catch (Exception e) {
            throw new MemoryServiceException("Failed to process pending resources: " + e.getMessage(),
                    MemoryServiceException.ERROR_PROCESSING_FAILURE, e);
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    /**
     * Shutdown the executor service.
     */

    @Override
    public BatchEmbeddingResult collectTextsForEmbedding() {
        long startTime = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();

        try {
            logger.info("Starting collection of texts for embedding");

            // Parallel collection from all three repositories
            CompletableFuture<List<Resource>> resourcesFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return resourceRepository.findResourcesWithoutEmbedding();
                } catch (Exception e) {
                    logger.error("Failed to collect resources without embedding: {}", e.getMessage());
                    errors.add("Resources: " + e.getMessage());
                    return new ArrayList<>();
                }
            }, executorService);

            CompletableFuture<List<Snippet>> snippetsFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return snippetRepository.findSnippetsWithoutEmbedding();
                } catch (Exception e) {
                    logger.error("Failed to collect snippets without embedding: {}", e.getMessage());
                    errors.add("Snippets: " + e.getMessage());
                    return new ArrayList<>();
                }
            }, executorService);

            CompletableFuture<List<Preference>> preferencesFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return preferenceRepository.findPreferencesWithoutEmbedding();
                } catch (Exception e) {
                    logger.error("Failed to collect preferences without embedding: {}", e.getMessage());
                    errors.add("Preferences: " + e.getMessage());
                    return new ArrayList<>();
                }
            }, executorService);

            // Wait for all to complete
            CompletableFuture.allOf(resourcesFuture, snippetsFuture, preferencesFuture).join();

            List<Resource> resources = resourcesFuture.get();
            List<Snippet> snippets = snippetsFuture.get();
            List<Preference> preferences = preferencesFuture.get();

            long processingTime = System.currentTimeMillis() - startTime;

            BatchEmbeddingResult result = new BatchEmbeddingResult(
                    resources.size(),
                    snippets.size(),
                    preferences.size(),
                    0,  // successCount - will be updated during processing
                    errors.size(),
                    errors,
                    processingTime
            );

            logger.info("Collected {} resources, {} snippets, {} preferences for embedding",
                    resources.size(), snippets.size(), preferences.size());

            return result;

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            errors.add("Collection failed: " + e.getMessage());
            logger.error("Failed to collect texts for embedding: {}", e.getMessage(), e);

            return new BatchEmbeddingResult(0, 0, 0, 0, errors.size(), errors, processingTime);
        }
    }

    @Override
    public BatchEmbeddingRequest prepareBatchEmbeddingRequests(List<Resource> resources,
                                                              List<Snippet> snippets,
                                                              List<Preference> preferences) {
        try {
            logger.info("Preparing batch embedding requests for {} resources, {} snippets, {} preferences",
                    resources != null ? resources.size() : 0,
                    snippets != null ? snippets.size() : 0,
                    preferences != null ? preferences.size() : 0);

            // Prepare resource abstracts
            List<String> resourceAbstracts = new ArrayList<>();
            if (resources != null && !resources.isEmpty()) {
                List<String> resourceContents = resources.stream()
                        .map(r -> r.getAbstract() != null ? r.getAbstract() : r.getContent())
                        .collect(Collectors.toList());

                resourceAbstracts = contentProcessor.batchGenerateAbstracts(resourceContents);
                logger.debug("Generated {} abstracts for resources", resourceAbstracts.size());
            }

            // Prepare snippet summaries (they already have summaries, just collect them)
            List<String> snippetSummaries = new ArrayList<>();
            if (snippets != null && !snippets.isEmpty()) {
                snippetSummaries = snippets.stream()
                        .map(Snippet::getSummary)
                        .collect(Collectors.toList());
                logger.debug("Collected {} snippet summaries", snippetSummaries.size());
            }

            // Prepare preference summaries (they already have summaries, just collect them)
            List<String> preferenceSummaries = new ArrayList<>();
            if (preferences != null && !preferences.isEmpty()) {
                preferenceSummaries = preferences.stream()
                        .map(Preference::getSummary)
                        .collect(Collectors.toList());
                logger.debug("Collected {} preference summaries", preferenceSummaries.size());
            }

            logger.info("Prepared batch embedding request successfully");

            return new BatchEmbeddingRequest(
                    resources != null ? resources : new ArrayList<>(),
                    snippets != null ? snippets : new ArrayList<>(),
                    preferences != null ? preferences : new ArrayList<>(),
                    resourceAbstracts,
                    snippetSummaries,
                    preferenceSummaries
            );

        } catch (Exception e) {
            logger.error("Failed to prepare batch embedding requests: {}", e.getMessage(), e);
            throw new MemoryServiceException("Failed to prepare batch embedding requests: " + e.getMessage(),
                    MemoryServiceException.ERROR_BATCH_OPERATION_FAILURE, e);
        }
    }

    @Override
    public BatchEmbeddingResult processBatchEmbedding() {
        long startTime = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();

        try {
            logger.info("Starting batch embedding processing");

            // Step 1: Collect items without embeddings
            BatchEmbeddingResult collectionResult = collectTextsForEmbedding();
            errors.addAll(collectionResult.getErrors());

            if (collectionResult.getTotalCount() == 0) {
                logger.info("No items found requiring embedding");
                return new BatchEmbeddingResult(0, 0, 0, 0, 0, errors,
                        System.currentTimeMillis() - startTime);
            }

            // Step 2: Fetch the actual items
            CompletableFuture<List<Resource>> resourcesFuture = CompletableFuture.supplyAsync(
                    () -> resourceRepository.findResourcesWithoutEmbedding(), executorService);
            CompletableFuture<List<Snippet>> snippetsFuture = CompletableFuture.supplyAsync(
                    () -> snippetRepository.findSnippetsWithoutEmbedding(), executorService);
            CompletableFuture<List<Preference>> preferencesFuture = CompletableFuture.supplyAsync(
                    () -> preferenceRepository.findPreferencesWithoutEmbedding(), executorService);

            CompletableFuture.allOf(resourcesFuture, snippetsFuture, preferencesFuture).join();

            List<Resource> resources = resourcesFuture.get();
            List<Snippet> snippets = snippetsFuture.get();
            List<Preference> preferences = preferencesFuture.get();

            // Step 3: Prepare batch requests (generates abstracts if needed)
            BatchEmbeddingRequest request = prepareBatchEmbeddingRequests(resources, snippets, preferences);

            // Step 4: Process embeddings in parallel using MemoryManager
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            if (!request.getResources().isEmpty()) {
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        memoryManager.batchUpdateResourceEmbeddings(request.getResources());
                        logger.info("Successfully processed {} resource embeddings", request.getResources().size());
                    } catch (Exception e) {
                        logger.error("Failed to process resource embeddings: {}", e.getMessage());
                        errors.add("Resource embeddings: " + e.getMessage());
                    }
                }, executorService));
            }

            if (!request.getSnippets().isEmpty()) {
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        memoryManager.batchUpdateSnippetEmbeddings(request.getSnippets());
                        logger.info("Successfully processed {} snippet embeddings", request.getSnippets().size());
                    } catch (Exception e) {
                        logger.error("Failed to process snippet embeddings: {}", e.getMessage());
                        errors.add("Snippet embeddings: " + e.getMessage());
                    }
                }, executorService));
            }

            if (!request.getPreferences().isEmpty()) {
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        memoryManager.batchUpdatePreferenceEmbeddings(request.getPreferences());
                        logger.info("Successfully processed {} preference embeddings", request.getPreferences().size());
                    } catch (Exception e) {
                        logger.error("Failed to process preference embeddings: {}", e.getMessage());
                        errors.add("Preference embeddings: " + e.getMessage());
                    }
                }, executorService));
            }

            // Wait for all processing to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            long processingTime = System.currentTimeMillis() - startTime;
            int successCount = resources.size() + snippets.size() + preferences.size() - errors.size();
            int failureCount = errors.size();

            BatchEmbeddingResult result = new BatchEmbeddingResult(
                    resources.size(),
                    snippets.size(),
                    preferences.size(),
                    successCount,
                    failureCount,
                    errors,
                    processingTime
            );

            logger.info("Batch embedding processing completed: {} successes, {} failures, {} ms",
                    successCount, failureCount, processingTime);

            return result;

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            errors.add("Batch processing failed: " + e.getMessage());
            logger.error("Batch embedding processing failed: {}", e.getMessage(), e);

            return new BatchEmbeddingResult(0, 0, 0, 0, errors.size(), errors, processingTime);
        }
    }

    public void shutdown() {
        executorService.shutdown();
        logger.info("MemoryServiceImpl shutdown complete");
    }

    // ========== Private Helper Methods ==========

    private boolean testAvailability() {
        try {
            return contentProcessor != null &&
                    true &&
                    memoryManager != null &&
                    memoryManager.isAvailable() &&
                    resourceRepository != null &&
                    snippetRepository != null &&
                    preferenceRepository != null;
        } catch (Exception e) {
            logger.warn("MemoryService availability test failed: {}", e.getMessage());
            return false;
        }
    }

    private String generatePreferenceName(Snippet snippet) {
        // Generate a preference name based on snippet content and type
        // This is a simplified implementation
        String prefix = snippet.getMemoryType().name().toLowerCase();
        String hash = String.valueOf(Math.abs(snippet.getSummary().hashCode()));
        return prefix + "_" + hash;
    }

    private String generateInitialPreferenceSummary(Snippet snippet) {
        // Use the snippet summary as the initial preference summary
        return snippet.getSummary();
    }

    private String generateUpdatedPreferenceSummary(Preference preference, List<Snippet> newSnippets) {
        // Merge existing summary with new snippets
        // This is a simplified implementation - in practice, you'd use LLM to generate a coherent summary
        StringBuilder sb = new StringBuilder();
        if (preference.getSummary() != null && !preference.getSummary().isEmpty()) {
            sb.append(preference.getSummary()).append("\n\n");
        }

        for (Snippet snippet : newSnippets) {
            sb.append("- ").append(snippet.getSummary()).append("\n");
        }

        return sb.toString().trim();
    }
}
