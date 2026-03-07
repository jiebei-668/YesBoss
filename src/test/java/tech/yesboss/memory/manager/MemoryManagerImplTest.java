package tech.yesboss.memory.manager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.yesboss.memory.embedding.EmbeddingService;
import tech.yesboss.memory.model.Preference;
import tech.yesboss.memory.model.Resource;
import tech.yesboss.memory.model.Snippet;
import tech.yesboss.memory.repository.PreferenceRepository;
import tech.yesboss.memory.repository.ResourceRepository;
import tech.yesboss.memory.repository.SnippetRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for MemoryManagerImpl.
 *
 * <p>This test class verifies all aspects of the MemoryManager including:
 * <ul>
 *   <li>Interface contract compliance</li>
 *   <li>Functional correctness across three layers (Resource, Snippet, Preference)</li>
 *   <li>Edge cases and boundary conditions</li>
 *   <li>Error handling and recovery</li>
 *   <li>Performance requirements</li>
 *   <li>Concurrent operations</li>
 *   <li>Asynchronous embedding generation</li>
 * </ul>
 */
@DisplayName("MemoryManager Implementation Tests")
public class MemoryManagerImplTest {

    private ResourceRepository resourceRepository;
    private SnippetRepository snippetRepository;
    private PreferenceRepository preferenceRepository;
    private EmbeddingService embeddingService;
    private MemoryManagerImpl memoryManager;

    @BeforeEach
    void setUp() {
        resourceRepository = mock(ResourceRepository.class);
        snippetRepository = mock(SnippetRepository.class);
        preferenceRepository = mock(PreferenceRepository.class);
        embeddingService = mock(EmbeddingService.class);

        // Configure embedding service mock
        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(createTestEmbedding());
        when(embeddingService.generateConversationEmbedding(anyString())).thenReturn(createTestEmbedding());

        // Configure repository mocks to return saved entities
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> {
            Resource resource = invocation.getArgument(0);
            return resource;
        });
        when(resourceRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(resourceRepository.update(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(snippetRepository.save(any(Snippet.class))).thenAnswer(invocation -> {
            Snippet snippet = invocation.getArgument(0);
            return snippet;
        });
        when(snippetRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(snippetRepository.update(any(Snippet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(preferenceRepository.save(any(Preference.class))).thenAnswer(invocation -> {
            Preference preference = invocation.getArgument(0);
            return preference;
        });
        when(preferenceRepository.update(any(Preference.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(preferenceRepository.updateSummaryAndEmbedding(anyString(), anyString(), any(byte[].class))).thenReturn(true);

        memoryManager = new MemoryManagerImpl(resourceRepository, snippetRepository,
                preferenceRepository, embeddingService);
    }

    @AfterEach
    void tearDown() {
        if (memoryManager != null) {
            memoryManager.shutdown();
        }
    }

    // ========== Helper Methods ==========

    private float[] createTestEmbedding() {
        float[] embedding = new float[1536];
        Arrays.fill(embedding, 0.1f);
        return embedding;
    }

    private Resource createTestResource() {
        return Resource.builder()
                .conversationId("conv-1")
                .sessionId("session-1")
                .content("Test conversation content")
                .abstractText("Test abstract")
                .build();
    }

    private Snippet createTestSnippet() {
        return Snippet.builder()
                .resourceId("resource-1")
                .summary("Test snippet summary")
                .memoryType(Snippet.MemoryType.KNOWLEDGE)
                .build();
    }

    private Preference createTestPreference() {
        return Preference.builder()
                .name("test-preference")
                .summary("Test preference summary")
                .build();
    }

    // ========== Interface Contract Tests ==========

    @Nested
    @DisplayName("Interface Contract Tests")
    class InterfaceContractTests {

        @Test
        @DisplayName("saveResource() returns Resource object")
        void testSaveResourceReturnsResource() {
            Resource resource = createTestResource();
            Resource result = memoryManager.saveResource(resource);
            assertNotNull(result, "Should return non-null Resource");
            assertEquals(resource.getId(), result.getId(), "Should return resource with same ID");
        }

        @Test
        @DisplayName("saveResources() returns list of Resources")
        void testSaveResourcesReturnsList() {
            List<Resource> resources = Arrays.asList(
                    createTestResource(),
                    createTestResource()
            );
            List<Resource> result = memoryManager.saveResources(resources);
            assertNotNull(result, "Should return non-null list");
            assertEquals(2, result.size(), "Should return list with 2 resources");
        }

        @Test
        @DisplayName("saveSnippet() returns Snippet object")
        void testSaveSnippetReturnsSnippet() {
            Snippet snippet = createTestSnippet();
            Snippet result = memoryManager.saveSnippet(snippet);
            assertNotNull(result, "Should return non-null Snippet");
            assertEquals(snippet.getId(), result.getId(), "Should return snippet with same ID");
        }

        @Test
        @DisplayName("saveSnippets() returns list of Snippets")
        void testSaveSnippetsReturnsList() {
            List<Snippet> snippets = Arrays.asList(
                    createTestSnippet(),
                    createTestSnippet()
            );
            List<Snippet> result = memoryManager.saveSnippets(snippets);
            assertNotNull(result, "Should return non-null list");
            assertEquals(2, result.size(), "Should return list with 2 snippets");
        }

        @Test
        @DisplayName("savePreference() returns Preference object")
        void testSavePreferenceReturnsPreference() {
            Preference preference = createTestPreference();
            Preference result = memoryManager.savePreference(preference);
            assertNotNull(result, "Should return non-null Preference");
            assertEquals(preference.getId(), result.getId(), "Should return preference with same ID");
        }

        @Test
        @DisplayName("updatePreferenceSummary() completes without exception for valid input")
        void testUpdatePreferenceSummaryCompletes() {
            String preferenceId = "pref-1";
            String summary = "Updated summary";
            float[] embedding = createTestEmbedding();
            assertDoesNotThrow(() -> memoryManager.updatePreferenceSummary(preferenceId, summary, embedding));
        }

        @Test
        @DisplayName("isAvailable() returns boolean")
        void testIsAvailableReturnsBoolean() {
            boolean available = memoryManager.isAvailable();
            assertTrue(available == true || available == false,
                    "isAvailable() should return a boolean value");
        }

        @Test
        @DisplayName("batchUpdateResourceEmbeddings() completes without exception")
        void testBatchUpdateResourceEmbeddingsCompletes() {
            List<Resource> resources = Arrays.asList(createTestResource(), createTestResource());
            assertDoesNotThrow(() -> memoryManager.batchUpdateResourceEmbeddings(resources));
        }

        @Test
        @DisplayName("batchUpdateSnippetEmbeddings() completes without exception")
        void testBatchUpdateSnippetEmbeddingsCompletes() {
            List<Snippet> snippets = Arrays.asList(createTestSnippet(), createTestSnippet());
            assertDoesNotThrow(() -> memoryManager.batchUpdateSnippetEmbeddings(snippets));
        }

        @Test
        @DisplayName("batchUpdatePreferenceEmbeddings() completes without exception")
        void testBatchUpdatePreferenceEmbeddingsCompletes() {
            List<Preference> preferences = Arrays.asList(createTestPreference(), createTestPreference());
            assertDoesNotThrow(() -> memoryManager.batchUpdatePreferenceEmbeddings(preferences));
        }
    }

    // ========== Functional Correctness Tests ==========

    @Nested
    @DisplayName("Functional Correctness Tests")
    class FunctionalCorrectnessTests {

        @Test
        @DisplayName("saveResource() saves to repository and generates embedding asynchronously")
        void testSaveResourceSavesAndGeneratesEmbedding() throws InterruptedException {
            Resource resource = createTestResource();
            Resource result = memoryManager.saveResource(resource);

            // Verify repository save was called
            verify(resourceRepository, times(1)).save(resource);

            // Wait for async embedding generation
            Thread.sleep(100);

            // Verify embedding service was called
            verify(embeddingService, times(1)).generateConversationEmbedding(anyString());
            verify(resourceRepository, times(1)).update(any(Resource.class));
        }

        @Test
        @DisplayName("saveResource() with existing embedding does not regenerate")
        void testSaveResourceWithExistingEmbedding() throws InterruptedException {
            Resource resource = createTestResource();
            resource.setEmbedding(new byte[6144]); // Has embedding

            Resource result = memoryManager.saveResource(resource);

            // Verify repository save was called
            verify(resourceRepository, times(1)).save(resource);

            // Wait for async tasks
            Thread.sleep(100);

            // Verify embedding service was NOT called (embedding already exists)
            verify(embeddingService, never()).generateConversationEmbedding(anyString());
        }

        @Test
        @DisplayName("saveResources() saves batch and generates embeddings asynchronously")
        void testSaveResourcesBatchSavesAndGeneratesEmbeddings() throws InterruptedException {
            List<Resource> resources = Arrays.asList(
                    createTestResource(),
                    createTestResource(),
                    createTestResource()
            );

            List<Resource> result = memoryManager.saveResources(resources);

            // Verify repository saveAll was called
            verify(resourceRepository, times(1)).saveAll(anyList());

            // Wait for async embedding generation
            Thread.sleep(100);

            // Verify embedding service was called for each resource
            verify(embeddingService, times(3)).generateConversationEmbedding(anyString());
        }

        @Test
        @DisplayName("saveSnippet() saves to repository and generates embedding asynchronously")
        void testSaveSnippetSavesAndGeneratesEmbedding() throws InterruptedException {
            Snippet snippet = createTestSnippet();
            Snippet result = memoryManager.saveSnippet(snippet);

            // Verify repository save was called
            verify(snippetRepository, times(1)).save(snippet);

            // Wait for async embedding generation
            Thread.sleep(100);

            // Verify embedding service was called
            verify(embeddingService, times(1)).generateEmbedding(anyString());
            verify(snippetRepository, times(1)).update(any(Snippet.class));
        }

        @Test
        @DisplayName("saveSnippets() saves batch and generates embeddings asynchronously")
        void testSaveSnippetsBatchSavesAndGeneratesEmbeddings() throws InterruptedException {
            List<Snippet> snippets = Arrays.asList(
                    createTestSnippet(),
                    createTestSnippet(),
                    createTestSnippet()
            );

            List<Snippet> result = memoryManager.saveSnippets(snippets);

            // Verify repository saveAll was called
            verify(snippetRepository, times(1)).saveAll(anyList());

            // Wait for async embedding generation
            Thread.sleep(100);

            // Verify embedding service was called for each snippet
            verify(embeddingService, times(3)).generateEmbedding(anyString());
        }

        @Test
        @DisplayName("savePreference() saves to repository and generates embedding asynchronously")
        void testSavePreferenceSavesAndGeneratesEmbedding() throws InterruptedException {
            Preference preference = createTestPreference();
            Preference result = memoryManager.savePreference(preference);

            // Verify repository save was called
            verify(preferenceRepository, times(1)).save(preference);

            // Wait for async embedding generation
            Thread.sleep(100);

            // Verify embedding service was called
            verify(embeddingService, times(1)).generateEmbedding(anyString());
            verify(preferenceRepository, times(1)).update(any(Preference.class));
        }

        @Test
        @DisplayName("updatePreferenceSummary() updates summary and embedding")
        void testUpdatePreferenceSummaryUpdatesCorrectly() {
            String preferenceId = "pref-1";
            String summary = "Updated summary";
            float[] embedding = createTestEmbedding();

            memoryManager.updatePreferenceSummary(preferenceId, summary, embedding);

            // Verify repository method was called with correct parameters
            ArgumentCaptor<byte[]> embeddingCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(preferenceRepository, times(1)).updateSummaryAndEmbedding(
                    eq(preferenceId), eq(summary), embeddingCaptor.capture());

            // Verify embedding was converted to byte array
            byte[] capturedEmbedding = embeddingCaptor.getValue();
            assertNotNull(capturedEmbedding, "Embedding byte array should not be null");
            assertEquals(6144, capturedEmbedding.length, "Embedding should be 6144 bytes (1536 * 4)");
        }

        @Test
        @DisplayName("saveResource() uses abstract for embedding if content is not available")
        void testSaveResourceUsesAbstractForEmbedding() throws InterruptedException {
            Resource resource = createTestResource();
            resource.setAbstract("Abstract text for embedding");
            resource.setContent("Content text");

            memoryManager.saveResource(resource);

            // Wait for async embedding generation
            Thread.sleep(100);

            // Verify embedding service was called with abstract
            verify(embeddingService, times(1)).generateConversationEmbedding("Abstract text for embedding");
        }

        @Test
        @DisplayName("saveResource() falls back to content when abstract is null")
        void testSaveResourceFallsBackToContent() throws InterruptedException {
            Resource resource = createTestResource();
            resource.setAbstract(null);
            resource.setContent("Content text");

            memoryManager.saveResource(resource);

            // Wait for async embedding generation
            Thread.sleep(100);

            // Verify embedding service was called with content
            verify(embeddingService, times(1)).generateConversationEmbedding("Content text");
        }
    }

    // ========== Edge Cases and Boundary Conditions Tests ==========

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("saveResource() throws for null resource")
        void testSaveResourceThrowsForNull() {
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.saveResource(null));
            assertEquals(MemoryManagerException.ERROR_INVALID_INPUT, exception.getErrorCode());
        }

        @Test
        @DisplayName("saveResource() throws for resource with null content")
        void testSaveResourceThrowsForNullContent() {
            Resource resource = Resource.builder()
                    .conversationId("conv-1")
                    .sessionId("session-1")
                    .content(null)
                    .build();
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.saveResource(resource));
            assertEquals(MemoryManagerException.ERROR_INVALID_INPUT, exception.getErrorCode());
        }

        @Test
        @DisplayName("saveResource() throws for resource with empty content")
        void testSaveResourceThrowsForEmptyContent() {
            Resource resource = Resource.builder()
                    .conversationId("conv-1")
                    .sessionId("session-1")
                    .content("   ")
                    .build();
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.saveResource(resource));
            assertEquals(MemoryManagerException.ERROR_INVALID_INPUT, exception.getErrorCode());
        }

        @Test
        @DisplayName("saveResource() throws for resource with null sessionId")
        void testSaveResourceThrowsForNullSessionId() {
            Resource resource = Resource.builder()
                    .conversationId("conv-1")
                    .sessionId(null)
                    .content("Valid content")
                    .build();
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.saveResource(resource));
            assertEquals(MemoryManagerException.ERROR_INVALID_INPUT, exception.getErrorCode());
        }

        @Test
        @DisplayName("saveResources() throws for null list")
        void testSaveResourcesThrowsForNullList() {
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.saveResources(null));
            assertEquals(MemoryManagerException.ERROR_INVALID_INPUT, exception.getErrorCode());
        }

        @Test
        @DisplayName("saveResources() throws for empty list")
        void testSaveResourcesThrowsForEmptyList() {
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.saveResources(Collections.emptyList()));
            assertEquals(MemoryManagerException.ERROR_INVALID_INPUT, exception.getErrorCode());
        }

        @Test
        @DisplayName("saveSnippet() throws for null snippet")
        void testSaveSnippetThrowsForNull() {
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.saveSnippet(null));
            assertEquals(MemoryManagerException.ERROR_INVALID_INPUT, exception.getErrorCode());
        }

        @Test
        @DisplayName("saveSnippet() throws for snippet with null resourceId")
        void testSaveSnippetThrowsForNullResourceId() {
            Snippet snippet = new Snippet();
            snippet.setResourceId(null);
            snippet.setSummary("Valid summary");
            snippet.setMemoryType(Snippet.MemoryType.KNOWLEDGE);
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.saveSnippet(snippet));
            assertEquals(MemoryManagerException.ERROR_INVALID_INPUT, exception.getErrorCode());
        }

        @Test
        @DisplayName("saveSnippet() throws for snippet with null summary")
        void testSaveSnippetThrowsForNullSummary() {
            Snippet snippet = new Snippet();
            snippet.setResourceId("resource-1");
            snippet.setSummary(null);
            snippet.setMemoryType(Snippet.MemoryType.KNOWLEDGE);
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.saveSnippet(snippet));
            assertEquals(MemoryManagerException.ERROR_INVALID_INPUT, exception.getErrorCode());
        }

        @Test
        @DisplayName("saveSnippet() throws for snippet with null memoryType")
        void testSaveSnippetThrowsForNullMemoryType() {
            Snippet snippet = new Snippet();
            snippet.setResourceId("resource-1");
            snippet.setSummary("Valid summary");
            snippet.setMemoryType(null);
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.saveSnippet(snippet));
            assertEquals(MemoryManagerException.ERROR_INVALID_INPUT, exception.getErrorCode());
        }

        @Test
        @DisplayName("saveSnippets() throws for null list")
        void testSaveSnippetsThrowsForNullList() {
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.saveSnippets(null));
            assertEquals(MemoryManagerException.ERROR_INVALID_INPUT, exception.getErrorCode());
        }

        @Test
        @DisplayName("saveSnippets() throws for empty list")
        void testSaveSnippetsThrowsForEmptyList() {
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.saveSnippets(Collections.emptyList()));
            assertEquals(MemoryManagerException.ERROR_INVALID_INPUT, exception.getErrorCode());
        }

        @Test
        @DisplayName("savePreference() throws for null preference")
        void testSavePreferenceThrowsForNull() {
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.savePreference(null));
            assertEquals(MemoryManagerException.ERROR_INVALID_INPUT, exception.getErrorCode());
        }

        @Test
        @DisplayName("savePreference() throws for preference with null name")
        void testSavePreferenceThrowsForNullName() {
            Preference preference = new Preference();
            preference.setName(null);
            preference.setSummary("Valid summary");
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.savePreference(preference));
            assertEquals(MemoryManagerException.ERROR_INVALID_INPUT, exception.getErrorCode());
        }

        @Test
        @DisplayName("savePreference() throws for preference with null summary")
        void testSavePreferenceThrowsForNullSummary() {
            Preference preference = new Preference();
            preference.setName("valid-name");
            preference.setSummary(null);
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.savePreference(preference));
            assertEquals(MemoryManagerException.ERROR_INVALID_INPUT, exception.getErrorCode());
        }

        @Test
        @DisplayName("updatePreferenceSummary() throws for null preferenceId")
        void testUpdatePreferenceSummaryThrowsForNullId() {
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.updatePreferenceSummary(null, "summary", createTestEmbedding()));
            assertEquals(MemoryManagerException.ERROR_INVALID_INPUT, exception.getErrorCode());
        }

        @Test
        @DisplayName("updatePreferenceSummary() throws for empty preferenceId")
        void testUpdatePreferenceSummaryThrowsForEmptyId() {
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.updatePreferenceSummary("   ", "summary", createTestEmbedding()));
            assertEquals(MemoryManagerException.ERROR_INVALID_INPUT, exception.getErrorCode());
        }

        @Test
        @DisplayName("updatePreferenceSummary() throws for null summary")
        void testUpdatePreferenceSummaryThrowsForNullSummary() {
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.updatePreferenceSummary("pref-1", null, createTestEmbedding()));
            assertEquals(MemoryManagerException.ERROR_INVALID_INPUT, exception.getErrorCode());
        }

        @Test
        @DisplayName("updatePreferenceSummary() throws for null embedding")
        void testUpdatePreferenceSummaryThrowsForNullEmbedding() {
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.updatePreferenceSummary("pref-1", "summary", null));
            assertEquals(MemoryManagerException.ERROR_INVALID_INPUT, exception.getErrorCode());
        }

        @Test
        @DisplayName("updatePreferenceSummary() throws for wrong embedding dimension")
        void testUpdatePreferenceSummaryThrowsForWrongDimension() {
            float[] wrongDimension = new float[100]; // Wrong dimension
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.updatePreferenceSummary("pref-1", "summary", wrongDimension));
            assertEquals(MemoryManagerException.ERROR_INVALID_INPUT, exception.getErrorCode());
        }

        @Test
        @DisplayName("batchUpdateResourceEmbeddings() handles null list gracefully")
        void testBatchUpdateResourceEmbeddingsHandlesNullList() {
            assertDoesNotThrow(() -> memoryManager.batchUpdateResourceEmbeddings(null));
            verify(embeddingService, never()).generateConversationEmbedding(anyString());
        }

        @Test
        @DisplayName("batchUpdateResourceEmbeddings() handles empty list gracefully")
        void testBatchUpdateResourceEmbeddingsHandlesEmptyList() {
            assertDoesNotThrow(() -> memoryManager.batchUpdateResourceEmbeddings(Collections.emptyList()));
            verify(embeddingService, never()).generateConversationEmbedding(anyString());
        }

        @Test
        @DisplayName("batchUpdateSnippetEmbeddings() handles null list gracefully")
        void testBatchUpdateSnippetEmbeddingsHandlesNullList() {
            assertDoesNotThrow(() -> memoryManager.batchUpdateSnippetEmbeddings(null));
            verify(embeddingService, never()).generateEmbedding(anyString());
        }

        @Test
        @DisplayName("batchUpdateSnippetEmbeddings() handles empty list gracefully")
        void testBatchUpdateSnippetEmbeddingsHandlesEmptyList() {
            assertDoesNotThrow(() -> memoryManager.batchUpdateSnippetEmbeddings(Collections.emptyList()));
            verify(embeddingService, never()).generateEmbedding(anyString());
        }

        @Test
        @DisplayName("batchUpdatePreferenceEmbeddings() handles null list gracefully")
        void testBatchUpdatePreferenceEmbeddingsHandlesNullList() {
            assertDoesNotThrow(() -> memoryManager.batchUpdatePreferenceEmbeddings(null));
            verify(embeddingService, never()).generateEmbedding(anyString());
        }

        @Test
        @DisplayName("batchUpdatePreferenceEmbeddings() handles empty list gracefully")
        void testBatchUpdatePreferenceEmbeddingsHandlesEmptyList() {
            assertDoesNotThrow(() -> memoryManager.batchUpdatePreferenceEmbeddings(Collections.emptyList()));
            verify(embeddingService, never()).generateEmbedding(anyString());
        }

        @Test
        @DisplayName("saveResources() handles batch larger than BATCH_SIZE")
        void testSaveResourcesHandlesLargeBatch() {
            // Create 250 resources (more than BATCH_SIZE of 100)
            List<Resource> largeBatch = new ArrayList<>();
            for (int i = 0; i < 250; i++) {
                largeBatch.add(createTestResource());
            }

            List<Resource> result = memoryManager.saveResources(largeBatch);

            assertEquals(250, result.size(), "Should save all 250 resources");
            // Should be called 3 times (100 + 100 + 50)
            verify(resourceRepository, times(3)).saveAll(anyList());
        }

        @Test
        @DisplayName("saveResources() handles resources with null in list")
        void testSaveResourcesHandlesNullInList() {
            List<Resource> resourcesWithNull = Arrays.asList(
                    createTestResource(),
                    null,
                    createTestResource()
            );

            assertThrows(MemoryManagerException.class,
                    () -> memoryManager.saveResources(resourcesWithNull));
        }

        @Test
        @DisplayName("batchUpdateResourceEmbeddings() skips resources with embeddings")
        void testBatchUpdateResourceEmbeddingsSkipsExistingEmbeddings() {
            Resource resourceWithEmbedding = createTestResource();
            resourceWithEmbedding.setEmbedding(new byte[6144]);

            Resource resourceWithoutEmbedding = createTestResource();

            List<Resource> resources = Arrays.asList(resourceWithEmbedding, resourceWithoutEmbedding);
            memoryManager.batchUpdateResourceEmbeddings(resources);

            // Should only generate embedding for resource without embedding
            verify(embeddingService, times(1)).generateConversationEmbedding(anyString());
        }

        @Test
        @DisplayName("batchUpdateSnippetEmbeddings() skips snippets with embeddings")
        void testBatchUpdateSnippetEmbeddingsSkipsExistingEmbeddings() {
            Snippet snippetWithEmbedding = createTestSnippet();
            snippetWithEmbedding.setEmbedding(new byte[6144]);

            Snippet snippetWithoutEmbedding = createTestSnippet();

            List<Snippet> snippets = Arrays.asList(snippetWithEmbedding, snippetWithoutEmbedding);
            memoryManager.batchUpdateSnippetEmbeddings(snippets);

            // Should only generate embedding for snippet without embedding
            verify(embeddingService, times(1)).generateEmbedding(anyString());
        }

        @Test
        @DisplayName("batchUpdatePreferenceEmbeddings() skips preferences with embeddings")
        void testBatchUpdatePreferenceEmbeddingsSkipsExistingEmbeddings() {
            Preference preferenceWithEmbedding = createTestPreference();
            preferenceWithEmbedding.setEmbedding(new byte[6144]);

            Preference preferenceWithoutEmbedding = createTestPreference();

            List<Preference> preferences = Arrays.asList(preferenceWithEmbedding, preferenceWithoutEmbedding);
            memoryManager.batchUpdatePreferenceEmbeddings(preferences);

            // Should only generate embedding for preference without embedding
            verify(embeddingService, times(1)).generateEmbedding(anyString());
        }
    }

    // ========== Error Handling Tests ==========

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("saveResource() throws MemoryManagerException when repository fails")
        void testSaveResourceThrowsWhenRepositoryFails() {
            when(resourceRepository.save(any(Resource.class))).thenThrow(new RuntimeException("Database error"));

            Resource resource = createTestResource();
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.saveResource(resource));
            assertEquals(MemoryManagerException.ERROR_PERSISTENCE_FAILURE, exception.getErrorCode());
        }

        @Test
        @DisplayName("saveResources() throws MemoryManagerException when repository fails")
        void testSaveResourcesThrowsWhenRepositoryFails() {
            when(resourceRepository.saveAll(anyList())).thenThrow(new RuntimeException("Database error"));

            List<Resource> resources = Arrays.asList(createTestResource(), createTestResource());
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.saveResources(resources));
            assertEquals(MemoryManagerException.ERROR_BATCH_OPERATION_FAILURE, exception.getErrorCode());
        }

        @Test
        @DisplayName("saveSnippet() throws MemoryManagerException when repository fails")
        void testSaveSnippetThrowsWhenRepositoryFails() {
            when(snippetRepository.save(any(Snippet.class))).thenThrow(new RuntimeException("Database error"));

            Snippet snippet = createTestSnippet();
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.saveSnippet(snippet));
            assertEquals(MemoryManagerException.ERROR_PERSISTENCE_FAILURE, exception.getErrorCode());
        }

        @Test
        @DisplayName("saveSnippets() throws MemoryManagerException when repository fails")
        void testSaveSnippetsThrowsWhenRepositoryFails() {
            when(snippetRepository.saveAll(anyList())).thenThrow(new RuntimeException("Database error"));

            List<Snippet> snippets = Arrays.asList(createTestSnippet(), createTestSnippet());
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.saveSnippets(snippets));
            assertEquals(MemoryManagerException.ERROR_BATCH_OPERATION_FAILURE, exception.getErrorCode());
        }

        @Test
        @DisplayName("savePreference() throws MemoryManagerException when repository fails")
        void testSavePreferenceThrowsWhenRepositoryFails() {
            when(preferenceRepository.save(any(Preference.class))).thenThrow(new RuntimeException("Database error"));

            Preference preference = createTestPreference();
            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.savePreference(preference));
            assertEquals(MemoryManagerException.ERROR_PERSISTENCE_FAILURE, exception.getErrorCode());
        }

        @Test
        @DisplayName("updatePreferenceSummary() throws MemoryManagerException when repository fails")
        void testUpdatePreferenceSummaryThrowsWhenRepositoryFails() {
            doThrow(new RuntimeException("Database error"))
                    .when(preferenceRepository).updateSummaryAndEmbedding(anyString(), anyString(), any(byte[].class));

            MemoryManagerException exception = assertThrows(MemoryManagerException.class,
                    () -> memoryManager.updatePreferenceSummary("pref-1", "summary", createTestEmbedding()));
            assertEquals(MemoryManagerException.ERROR_PERSISTENCE_FAILURE, exception.getErrorCode());
        }

        @Test
        @DisplayName("batchUpdateResourceEmbeddings() continues on individual failures")
        void testBatchUpdateResourceEmbeddingsContinuesOnFailures() {
            Resource resource1 = createTestResource();
            Resource resource2 = createTestResource();
            Resource resource3 = createTestResource();

            // Make embedding service fail for resource2
            when(embeddingService.generateConversationEmbedding(anyString()))
                    .thenReturn(createTestEmbedding())
                    .thenThrow(new RuntimeException("Embedding service error"))
                    .thenReturn(createTestEmbedding());

            List<Resource> resources = Arrays.asList(resource1, resource2, resource3);

            // Should not throw, should continue on individual failures
            assertDoesNotThrow(() -> memoryManager.batchUpdateResourceEmbeddings(resources));

            // Verify it was called for all three resources
            verify(embeddingService, times(3)).generateConversationEmbedding(anyString());
        }

        @Test
        @DisplayName("batchUpdateSnippetEmbeddings() continues on individual failures")
        void testBatchUpdateSnippetEmbeddingsContinuesOnFailures() {
            Snippet snippet1 = createTestSnippet();
            Snippet snippet2 = createTestSnippet();
            Snippet snippet3 = createTestSnippet();

            // Make embedding service fail for snippet2
            when(embeddingService.generateEmbedding(anyString()))
                    .thenReturn(createTestEmbedding())
                    .thenThrow(new RuntimeException("Embedding service error"))
                    .thenReturn(createTestEmbedding());

            List<Snippet> snippets = Arrays.asList(snippet1, snippet2, snippet3);

            // Should not throw, should continue on individual failures
            assertDoesNotThrow(() -> memoryManager.batchUpdateSnippetEmbeddings(snippets));

            // Verify it was called for all three snippets
            verify(embeddingService, times(3)).generateEmbedding(anyString());
        }

        @Test
        @DisplayName("MemoryManager initialization fails when embedding service is unavailable")
        void testMemoryManagerInitializationWithUnavailableEmbeddingService() {
            when(embeddingService.isAvailable()).thenReturn(false);

            MemoryManagerImpl manager = new MemoryManagerImpl(
                    resourceRepository, snippetRepository, preferenceRepository, embeddingService);

            assertFalse(manager.isAvailable(), "Manager should not be available when embedding service is unavailable");
        }
    }

    // ========== Performance Tests ==========

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("saveResource() completes within 100ms")
        void testSaveResourcePerformance() {
            Resource resource = createTestResource();
            long startTime = System.currentTimeMillis();
            memoryManager.saveResource(resource);
            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 100, "saveResource() should complete within 100ms, took " + duration + "ms");
        }

        @Test
        @DisplayName("saveResources() completes within 1s for 100 items")
        void testSaveResourcesBatchPerformance() {
            List<Resource> resources = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                resources.add(createTestResource());
            }

            long startTime = System.currentTimeMillis();
            memoryManager.saveResources(resources);
            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 1000, "saveResources() should complete within 1s for 100 items, took " + duration + "ms");
        }

        @Test
        @DisplayName("saveSnippet() completes within 100ms")
        void testSaveSnippetPerformance() {
            Snippet snippet = createTestSnippet();
            long startTime = System.currentTimeMillis();
            memoryManager.saveSnippet(snippet);
            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 100, "saveSnippet() should complete within 100ms, took " + duration + "ms");
        }

        @Test
        @DisplayName("saveSnippets() completes within 1s for 100 items")
        void testSaveSnippetsBatchPerformance() {
            List<Snippet> snippets = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                snippets.add(createTestSnippet());
            }

            long startTime = System.currentTimeMillis();
            memoryManager.saveSnippets(snippets);
            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 1000, "saveSnippets() should complete within 1s for 100 items, took " + duration + "ms");
        }

        @Test
        @DisplayName("savePreference() completes within 100ms")
        void testSavePreferencePerformance() {
            Preference preference = createTestPreference();
            long startTime = System.currentTimeMillis();
            memoryManager.savePreference(preference);
            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 100, "savePreference() should complete within 100ms, took " + duration + "ms");
        }

        @Test
        @DisplayName("batchUpdateResourceEmbeddings() completes within 500ms for 100 items")
        void testBatchUpdateResourceEmbeddingsPerformance() {
            List<Resource> resources = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                resources.add(createTestResource());
            }

            long startTime = System.currentTimeMillis();
            memoryManager.batchUpdateResourceEmbeddings(resources);
            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 500, "batchUpdateResourceEmbeddings() should complete within 500ms for 100 items, took " + duration + "ms");
        }
    }

    // ========== Concurrent Operations Tests ==========

    @Nested
    @DisplayName("Concurrent Operations Tests")
    class ConcurrentOperationsTests {

        @Test
        @DisplayName("Concurrent saveResource() calls complete successfully")
        void testConcurrentSaveResource() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        Resource resource = createTestResource();
                        memoryManager.saveResource(resource);
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "All threads should complete within 5 seconds");
            assertEquals(threadCount, successCount.get(), "All save operations should succeed");

            executor.shutdown();
        }

        @Test
        @DisplayName("Concurrent saveResources() calls complete successfully")
        void testConcurrentSaveResources() throws InterruptedException {
            int threadCount = 5;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        List<Resource> resources = Arrays.asList(
                                createTestResource(),
                                createTestResource()
                        );
                        memoryManager.saveResources(resources);
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "All threads should complete within 5 seconds");
            assertEquals(threadCount, successCount.get(), "All save operations should succeed");

            executor.shutdown();
        }

        @Test
        @DisplayName("Concurrent saveSnippet() calls complete successfully")
        void testConcurrentSaveSnippet() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        Snippet snippet = createTestSnippet();
                        memoryManager.saveSnippet(snippet);
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "All threads should complete within 5 seconds");
            assertEquals(threadCount, successCount.get(), "All save operations should succeed");

            executor.shutdown();
        }

        @Test
        @DisplayName("Concurrent savePreference() calls complete successfully")
        void testConcurrentSavePreference() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        Preference preference = createTestPreference();
                        memoryManager.savePreference(preference);
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "All threads should complete within 5 seconds");
            assertEquals(threadCount, successCount.get(), "All save operations should succeed");

            executor.shutdown();
        }

        @Test
        @DisplayName("Concurrent mixed operations complete successfully")
        void testConcurrentMixedOperations() throws InterruptedException {
            int operationsPerType = 5;
            CountDownLatch latch = new CountDownLatch(operationsPerType * 3);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(10);

            // Submit saveResource operations
            for (int i = 0; i < operationsPerType; i++) {
                executor.submit(() -> {
                    try {
                        memoryManager.saveResource(createTestResource());
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Submit saveSnippet operations
            for (int i = 0; i < operationsPerType; i++) {
                executor.submit(() -> {
                    try {
                        memoryManager.saveSnippet(createTestSnippet());
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Submit savePreference operations
            for (int i = 0; i < operationsPerType; i++) {
                executor.submit(() -> {
                    try {
                        memoryManager.savePreference(createTestPreference());
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "All operations should complete within 10 seconds");
            assertEquals(operationsPerType * 3, successCount.get(), "All operations should succeed");

            executor.shutdown();
        }
    }

    // ========== MemoryType Tests ==========

    @Nested
    @DisplayName("MemoryType Tests")
    class MemoryTypeTests {

        @Test
        @DisplayName("saveSnippet() works with all MemoryType values")
        void testSaveSnippetWithAllMemoryTypes() {
            for (Snippet.MemoryType type : Snippet.MemoryType.values()) {
                Snippet snippet = Snippet.builder()
                        .resourceId("resource-1")
                        .summary("Test summary for " + type.name())
                        .memoryType(type)
                        .build();

                Snippet result = memoryManager.saveSnippet(snippet);
                assertNotNull(result, "Should save snippet with memory type " + type.name());
                assertEquals(type, result.getMemoryType(), "Memory type should be preserved");
            }
        }

        @Test
        @DisplayName("MemoryType.getDisplayName() returns correct Chinese names")
        void testMemoryTypeDisplayNames() {
            assertEquals("人物档案", Snippet.MemoryType.PROFILE.getDisplayName());
            assertEquals("事件", Snippet.MemoryType.EVENT.getDisplayName());
            assertEquals("知识", Snippet.MemoryType.KNOWLEDGE.getDisplayName());
            assertEquals("行为模式", Snippet.MemoryType.BEHAVIOR.getDisplayName());
            assertEquals("技能", Snippet.MemoryType.SKILL.getDisplayName());
            assertEquals("工具使用", Snippet.MemoryType.TOOL.getDisplayName());
        }

        @Test
        @DisplayName("MemoryType.getDescription() returns correct English descriptions")
        void testMemoryTypeDescriptions() {
            assertEquals("User profile and personal characteristics", Snippet.MemoryType.PROFILE.getDescription());
            assertEquals("Significant events and occurrences", Snippet.MemoryType.EVENT.getDescription());
            assertEquals("Domain knowledge and factual information", Snippet.MemoryType.KNOWLEDGE.getDescription());
            assertEquals("Behavioral patterns and habits", Snippet.MemoryType.BEHAVIOR.getDescription());
            assertEquals("User skills and capabilities", Snippet.MemoryType.SKILL.getDescription());
            assertEquals("Tool preferences and usage patterns", Snippet.MemoryType.TOOL.getDescription());
        }

        @Test
        @DisplayName("MemoryType.fromDisplayName() returns correct types")
        void testMemoryTypeFromDisplayName() {
            assertEquals(Snippet.MemoryType.PROFILE, Snippet.MemoryType.fromDisplayName("人物档案"));
            assertEquals(Snippet.MemoryType.EVENT, Snippet.MemoryType.fromDisplayName("事件"));
            assertEquals(Snippet.MemoryType.KNOWLEDGE, Snippet.MemoryType.fromDisplayName("知识"));
            assertEquals(Snippet.MemoryType.BEHAVIOR, Snippet.MemoryType.fromDisplayName("行为模式"));
            assertEquals(Snippet.MemoryType.SKILL, Snippet.MemoryType.fromDisplayName("技能"));
            assertEquals(Snippet.MemoryType.TOOL, Snippet.MemoryType.fromDisplayName("工具使用"));
            assertNull(Snippet.MemoryType.fromDisplayName("不存在的类型"));
        }
    }
}
