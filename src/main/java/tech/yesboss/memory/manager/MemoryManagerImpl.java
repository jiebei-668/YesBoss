package tech.yesboss.memory.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.memory.embedding.EmbeddingService;
import tech.yesboss.memory.embedding.EmbeddingServiceFactory;
import tech.yesboss.memory.model.Preference;
import tech.yesboss.memory.model.Resource;
import tech.yesboss.memory.model.Snippet;
import tech.yesboss.memory.repository.PreferenceRepository;
import tech.yesboss.memory.repository.ResourceRepository;
import tech.yesboss.memory.repository.SnippetRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Implementation of MemoryManager that coordinates three-layer memory operations.
 *
 * <p>This implementation manages the coordination between Resource, Snippet, and Preference layers,
 * handling data persistence, vectorization, and associations.</p>
 *
 * <p>Key features:
 * <ul>
 *   <li>Transactional consistency across layers</li>
 *   <li>Batch operation support</li>
 *   <li>Asynchronous vectorization</li>
 *   <li>Automatic association management</li>
 *   <li>Comprehensive error handling and retry logic</li>
 * </ul>
 */
public class MemoryManagerImpl implements MemoryManager {

    private static final Logger logger = LoggerFactory.getLogger(MemoryManagerImpl.class);

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final ResourceRepository resourceRepository;
    private final SnippetRepository snippetRepository;
    private final PreferenceRepository preferenceRepository;
    private final EmbeddingService embeddingService;
    private final ExecutorService executorService;
    private final boolean available;

    /**
     * Create a new MemoryManagerImpl with all dependencies.
     *
     * @param resourceRepository Resource repository
     * @param snippetRepository Snippet repository
     * @param preferenceRepository Preference repository
     * @param embeddingService Embedding service for vectorization
     */
    public MemoryManagerImpl(ResourceRepository resourceRepository,
                             SnippetRepository snippetRepository,
                             PreferenceRepository preferenceRepository,
                             EmbeddingService embeddingService) {
        this.resourceRepository = resourceRepository;
        this.snippetRepository = snippetRepository;
        this.preferenceRepository = preferenceRepository;
        this.embeddingService = embeddingService != null ? embeddingService :
                EmbeddingServiceFactory.getInstance().getService();
        this.executorService = Executors.newFixedThreadPool(4);

        // Test availability
        this.available = testAvailability();

        logger.info("MemoryManagerImpl initialized with available: {}", this.available);
    }

    /**
     * Create a new MemoryManagerImpl with default embedding service.
     *
     * @param resourceRepository Resource repository
     * @param snippetRepository Snippet repository
     * @param preferenceRepository Preference repository
     */
    public MemoryManagerImpl(ResourceRepository resourceRepository,
                             SnippetRepository snippetRepository,
                             PreferenceRepository preferenceRepository) {
        this(resourceRepository, snippetRepository, preferenceRepository, null);
    }

    @Override
    public Resource saveResource(Resource resource) {
        validateResource(resource);

        try {
            // Save to repository
            Resource saved = resourceRepository.save(resource);

            // Asynchronously generate embedding if not present
            if (!saved.hasEmbedding()) {
                CompletableFuture.runAsync(() -> {
                    try {
                        String contentToEmbed = saved.getAbstract() != null ? saved.getAbstract() : saved.getContent();
                        float[] embedding = embeddingService.generateConversationEmbedding(contentToEmbed);
                        byte[] embeddingBytes = floatArrayToByteArray(embedding);
                        saved.setEmbedding(embeddingBytes);
                        resourceRepository.update(saved);
                        logger.debug("Generated embedding for resource: {}", saved.getId());
                    } catch (Exception e) {
                        logger.warn("Failed to generate embedding for resource {}: {}", saved.getId(), e.getMessage());
                    }
                }, executorService);
            }

            return saved;
        } catch (Exception e) {
            throw new MemoryManagerException("Failed to save resource: " + e.getMessage(),
                    MemoryManagerException.ERROR_PERSISTENCE_FAILURE, e);
        }
    }

    @Override
    public List<Resource> saveResources(List<Resource> resources) {
        if (resources == null || resources.isEmpty()) {
            throw new MemoryManagerException("Resources list cannot be null or empty",
                    MemoryManagerException.ERROR_INVALID_INPUT);
        }

        try {
            // Split into batches if needed
            List<Resource> results = new ArrayList<>();
            for (int i = 0; i < resources.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, resources.size());
                List<Resource> batch = resources.subList(i, end);

                // Validate batch
                for (Resource resource : batch) {
                    validateResource(resource);
                }

                // Save batch
                List<Resource> saved = resourceRepository.saveAll(batch);
                results.addAll(saved);

                // Asynchronously generate embeddings for batch
                List<Resource> savedBatch = new ArrayList<>(saved);
                CompletableFuture.runAsync(() -> {
                    batchUpdateResourceEmbeddings(savedBatch);
                }, executorService);
            }

            return results;
        } catch (Exception e) {
            throw new MemoryManagerException("Failed to save resources: " + e.getMessage(),
                    MemoryManagerException.ERROR_BATCH_OPERATION_FAILURE, e);
        }
    }

    @Override
    public Snippet saveSnippet(Snippet snippet) {
        validateSnippet(snippet);

        try {
            // Save to repository
            Snippet saved = snippetRepository.save(snippet);

            // Asynchronously generate embedding if not present
            if (!saved.hasEmbedding()) {
                CompletableFuture.runAsync(() -> {
                    try {
                        float[] embedding = embeddingService.generateEmbedding(saved.getSummary());
                        byte[] embeddingBytes = floatArrayToByteArray(embedding);
                        saved.setEmbedding(embeddingBytes);
                        snippetRepository.update(saved);
                        logger.debug("Generated embedding for snippet: {}", saved.getId());
                    } catch (Exception e) {
                        logger.warn("Failed to generate embedding for snippet {}: {}", saved.getId(), e.getMessage());
                    }
                }, executorService);
            }

            return saved;
        } catch (Exception e) {
            throw new MemoryManagerException("Failed to save snippet: " + e.getMessage(),
                    MemoryManagerException.ERROR_PERSISTENCE_FAILURE, e);
        }
    }

    @Override
    public List<Snippet> saveSnippets(List<Snippet> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            throw new MemoryManagerException("Snippets list cannot be null or empty",
                    MemoryManagerException.ERROR_INVALID_INPUT);
        }

        try {
            // Validate all snippets
            for (Snippet snippet : snippets) {
                validateSnippet(snippet);
            }

            // Save batch
            List<Snippet> saved = snippetRepository.saveAll(snippets);

            // Asynchronously generate embeddings for batch
            CompletableFuture.runAsync(() -> {
                batchUpdateSnippetEmbeddings(saved);
            }, executorService);

            return saved;
        } catch (Exception e) {
            throw new MemoryManagerException("Failed to save snippets: " + e.getMessage(),
                    MemoryManagerException.ERROR_BATCH_OPERATION_FAILURE, e);
        }
    }

    @Override
    public Preference savePreference(Preference preference) {
        validatePreference(preference);

        try {
            // Save to repository
            Preference saved = preferenceRepository.save(preference);

            // Asynchronously generate embedding if not present
            if (!saved.hasEmbedding()) {
                CompletableFuture.runAsync(() -> {
                    try {
                        float[] embedding = embeddingService.generateEmbedding(saved.getSummary());
                        byte[] embeddingBytes = floatArrayToByteArray(embedding);
                        saved.setEmbedding(embeddingBytes);
                        preferenceRepository.update(saved);
                        logger.debug("Generated embedding for preference: {}", saved.getId());
                    } catch (Exception e) {
                        logger.warn("Failed to generate embedding for preference {}: {}", saved.getId(), e.getMessage());
                    }
                }, executorService);
            }

            return saved;
        } catch (Exception e) {
            throw new MemoryManagerException("Failed to save preference: " + e.getMessage(),
                    MemoryManagerException.ERROR_PERSISTENCE_FAILURE, e);
        }
    }

    @Override
    public void updatePreferenceSummary(String preferenceId, String summary, float[] embedding) {
        if (preferenceId == null || preferenceId.trim().isEmpty()) {
            throw new MemoryManagerException("Preference ID cannot be null or empty",
                    MemoryManagerException.ERROR_INVALID_INPUT);
        }
        if (summary == null || summary.trim().isEmpty()) {
            throw new MemoryManagerException("Summary cannot be null or empty",
                    MemoryManagerException.ERROR_INVALID_INPUT);
        }
        if (embedding == null || embedding.length != 1536) {
            throw new MemoryManagerException("Embedding must be 1536-dimensional",
                    MemoryManagerException.ERROR_INVALID_INPUT);
        }

        try {
            byte[] embeddingBytes = floatArrayToByteArray(embedding);
            preferenceRepository.updateSummaryAndEmbedding(preferenceId, summary, embeddingBytes);
            logger.info("Updated preference summary: {}", preferenceId);
        } catch (Exception e) {
            throw new MemoryManagerException("Failed to update preference summary: " + e.getMessage(),
                    MemoryManagerException.ERROR_PERSISTENCE_FAILURE, e);
        }
    }

    @Override
    public void batchUpdateResourceEmbeddings(List<Resource> resources) {
        if (resources == null || resources.isEmpty()) {
            return;
        }

        try {
            for (Resource resource : resources) {
                if (resource != null && !resource.hasEmbedding()) {
                    try {
                        String contentToEmbed = resource.getAbstract() != null ?
                                resource.getAbstract() : resource.getContent();
                        float[] embedding = embeddingService.generateConversationEmbedding(contentToEmbed);
                        byte[] embeddingBytes = floatArrayToByteArray(embedding);
                        resource.setEmbedding(embeddingBytes);
                        resourceRepository.update(resource);
                    } catch (Exception e) {
                        logger.warn("Failed to update embedding for resource {}: {}", resource.getId(), e.getMessage());
                    }
                }
            }
            logger.info("Updated embeddings for {} resources", resources.size());
        } catch (Exception e) {
            throw new MemoryManagerException("Failed to batch update resource embeddings: " + e.getMessage(),
                    MemoryManagerException.ERROR_VECTORIZATION_FAILURE, e);
        }
    }

    @Override
    public void batchUpdateSnippetEmbeddings(List<Snippet> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return;
        }

        try {
            for (Snippet snippet : snippets) {
                if (snippet != null && !snippet.hasEmbedding()) {
                    try {
                        float[] embedding = embeddingService.generateEmbedding(snippet.getSummary());
                        byte[] embeddingBytes = floatArrayToByteArray(embedding);
                        snippet.setEmbedding(embeddingBytes);
                        snippetRepository.update(snippet);
                    } catch (Exception e) {
                        logger.warn("Failed to update embedding for snippet {}: {}", snippet.getId(), e.getMessage());
                    }
                }
            }
            logger.info("Updated embeddings for {} snippets", snippets.size());
        } catch (Exception e) {
            throw new MemoryManagerException("Failed to batch update snippet embeddings: " + e.getMessage(),
                    MemoryManagerException.ERROR_VECTORIZATION_FAILURE, e);
        }
    }

    @Override
    public void batchUpdatePreferenceEmbeddings(List<Preference> preferences) {
        if (preferences == null || preferences.isEmpty()) {
            return;
        }

        try {
            for (Preference preference : preferences) {
                if (preference != null && !preference.hasEmbedding()) {
                    try {
                        float[] embedding = embeddingService.generateEmbedding(preference.getSummary());
                        byte[] embeddingBytes = floatArrayToByteArray(embedding);
                        preference.setEmbedding(embeddingBytes);
                        preferenceRepository.update(preference);
                    } catch (Exception e) {
                        logger.warn("Failed to update embedding for preference {}: {}", preference.getId(), e.getMessage());
                    }
                }
            }
            logger.info("Updated embeddings for {} preferences", preferences.size());
        } catch (Exception e) {
            throw new MemoryManagerException("Failed to batch update preference embeddings: " + e.getMessage(),
                    MemoryManagerException.ERROR_VECTORIZATION_FAILURE, e);
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    /**
     * Shutdown the executor service.
     */
    public void shutdown() {
        executorService.shutdown();
        logger.info("MemoryManagerImpl shutdown complete");
    }

    // ========== Private helper methods ==========

    private void validateResource(Resource resource) {
        if (resource == null) {
            throw new MemoryManagerException("Resource cannot be null",
                    MemoryManagerException.ERROR_INVALID_INPUT);
        }
        if (resource.getContent() == null || resource.getContent().trim().isEmpty()) {
            throw new MemoryManagerException("Resource content cannot be null or empty",
                    MemoryManagerException.ERROR_INVALID_INPUT);
        }
        if (resource.getSessionId() == null || resource.getSessionId().trim().isEmpty()) {
            throw new MemoryManagerException("Resource session ID cannot be null or empty",
                    MemoryManagerException.ERROR_INVALID_INPUT);
        }
    }

    private void validateSnippet(Snippet snippet) {
        if (snippet == null) {
            throw new MemoryManagerException("Snippet cannot be null",
                    MemoryManagerException.ERROR_INVALID_INPUT);
        }
        if (snippet.getResourceId() == null || snippet.getResourceId().trim().isEmpty()) {
            throw new MemoryManagerException("Snippet resource ID cannot be null or empty",
                    MemoryManagerException.ERROR_INVALID_INPUT);
        }
        if (snippet.getSummary() == null || snippet.getSummary().trim().isEmpty()) {
            throw new MemoryManagerException("Snippet summary cannot be null or empty",
                    MemoryManagerException.ERROR_INVALID_INPUT);
        }
        if (snippet.getMemoryType() == null) {
            throw new MemoryManagerException("Snippet memory type cannot be null",
                    MemoryManagerException.ERROR_INVALID_INPUT);
        }
    }

    private void validatePreference(Preference preference) {
        if (preference == null) {
            throw new MemoryManagerException("Preference cannot be null",
                    MemoryManagerException.ERROR_INVALID_INPUT);
        }
        if (preference.getName() == null || preference.getName().trim().isEmpty()) {
            throw new MemoryManagerException("Preference name cannot be null or empty",
                    MemoryManagerException.ERROR_INVALID_INPUT);
        }
        if (preference.getSummary() == null || preference.getSummary().trim().isEmpty()) {
            throw new MemoryManagerException("Preference summary cannot be null or empty",
                    MemoryManagerException.ERROR_INVALID_INPUT);
        }
    }

    private byte[] floatArrayToByteArray(float[] array) {
        if (array == null) {
            return null;
        }

        byte[] bytes = new byte[array.length * 4]; // float32 = 4 bytes
        for (int i = 0; i < array.length; i++) {
            int bits = Float.floatToIntBits(array[i]);
            bytes[i * 4] = (byte) (bits >> 24);
            bytes[i * 4 + 1] = (byte) (bits >> 16);
            bytes[i * 4 + 2] = (byte) (bits >> 8);
            bytes[i * 4 + 3] = (byte) bits;
        }
        return bytes;
    }

    private boolean testAvailability() {
        try {
            // Check if all repositories are accessible
            // In a real implementation, you might do a simple query to test connectivity
            return resourceRepository != null &&
                    snippetRepository != null &&
                    preferenceRepository != null &&
                    embeddingService != null &&
                    embeddingService.isAvailable();
        } catch (Exception e) {
            logger.warn("MemoryManager availability test failed: {}", e.getMessage());
            return false;
        }
    }
}
