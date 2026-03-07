package tech.yesboss.memory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.yesboss.memory.manager.MemoryManager;
import tech.yesboss.memory.model.Preference;
import tech.yesboss.memory.model.Resource;
import tech.yesboss.memory.model.Snippet;
import tech.yesboss.memory.processor.ContentProcessor;
import tech.yesboss.memory.repository.PreferenceRepository;
import tech.yesboss.memory.repository.ResourceRepository;
import tech.yesboss.memory.repository.SnippetRepository;
import tech.yesboss.memory.embedding.EmbeddingService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for Batch Vectorization Processing functionality.
 * 
 * This test class verifies the batch vectorization processing flow including:
 * - Interface contracts
 * - Normal functionality scenarios
 * - Boundary conditions
 * - Exception handling
 * - Performance benchmarks
 * - Concurrency scenarios
 * - Cache mechanisms
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Batch Vectorization Processing Tests")
public class BatchVectorizationProcessingTest {

    @Mock
    private ContentProcessor contentProcessor;
    
    @Mock
    private MemoryManager memoryManager;
    
    @Mock
    private ResourceRepository resourceRepository;
    
    @Mock
    private SnippetRepository snippetRepository;
    
    @Mock
    private PreferenceRepository preferenceRepository;
    
    @Mock
    private EmbeddingService embeddingService;

    private MemoryServiceImpl memoryService;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(4);
        memoryService = new MemoryServiceImpl(
            contentProcessor,
            memoryManager,
            resourceRepository,
            snippetRepository,
            preferenceRepository,
            embeddingService
        );
    }

    // ==================== Test Data Preparation ====================

    private List<Resource> createTestResources(int count) {
        List<Resource> resources = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Resource resource = new Resource();
            resource.setId("resource-" + i);
            resource.setContent("Test content " + i);
            resource.setAbstract("Test abstract " + i);
            resource.setSessionId("session-1");
            resource.setConversationId("conversation-1");
            resource.setCreatedAt(System.currentTimeMillis());
            resources.add(resource);
        }
        return resources;
    }

    private List<Snippet> createTestSnippets(int count) {
        List<Snippet> snippets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Snippet snippet = new Snippet();
            snippet.setId("snippet-" + i);
            snippet.setResourceId("resource-1");
            snippet.setSummary("Test summary " + i);
            snippet.setMemoryType(Snippet.MemoryType.PROFILE);
            snippet.setCreatedAt(System.currentTimeMillis());
            snippets.add(snippet);
        }
        return snippets;
    }

    private List<Preference> createTestPreferences(int count) {
        List<Preference> preferences = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Preference preference = new Preference();
            preference.setId("preference-" + i);
            preference.setName("Test Preference " + i);
            preference.setSummary("Test preference summary " + i);
            preference.setCreatedAt(System.currentTimeMillis());
            preferences.add(preference);
        }
        return preferences;
    }

    // ==================== 1. Interface Contract Tests ====================

    @Nested
    @DisplayName("1. Interface Contract Tests")
    class InterfaceContractTests {

        @Test
        @DisplayName("collectTextsForEmbedding() returns BatchEmbeddingResult")
        void testCollectTextsForEmbeddingReturnType() {
            // Arrange
            when(resourceRepository.findResourcesWithoutEmbedding()).thenReturn(Collections.emptyList());
            when(snippetRepository.findSnippetsWithoutEmbedding()).thenReturn(Collections.emptyList());
            when(preferenceRepository.findPreferencesWithoutEmbedding()).thenReturn(Collections.emptyList());

            // Act
            MemoryService.BatchEmbeddingResult result = memoryService.collectTextsForEmbedding();

            // Assert
            assertNotNull(result, "Result should not be null");
            assertInstanceOf(MemoryService.BatchEmbeddingResult.class, result);
        }

        @Test
        @DisplayName("prepareBatchEmbeddingRequests() accepts three lists")
        void testPrepareBatchEmbeddingRequestParameters() {
            // Arrange
            List<Resource> resources = createTestResources(1);
            List<Snippet> snippets = createTestSnippets(1);
            List<Preference> preferences = createTestPreferences(1);
            when(contentProcessor.batchGenerateAbstracts(anyList())).thenReturn(Arrays.asList("abstract"));

            // Act
            MemoryService.BatchEmbeddingRequest request = memoryService.prepareBatchEmbeddingRequests(
                resources, snippets, preferences
            );

            // Assert
            assertNotNull(request, "Request should not be null");
            assertNotNull(request.getResources(), "Resources should not be null");
            assertNotNull(request.getSnippets(), "Snippets should not be null");
            assertNotNull(request.getPreferences(), "Preferences should not be null");
        }

        @Test
        @DisplayName("processBatchEmbedding() returns BatchEmbeddingResult")
        void testProcessBatchEmbeddingReturnType() {
            // Arrange
            when(resourceRepository.findResourcesWithoutEmbedding()).thenReturn(Collections.emptyList());
            when(snippetRepository.findSnippetsWithoutEmbedding()).thenReturn(Collections.emptyList());
            when(preferenceRepository.findPreferencesWithoutEmbedding()).thenReturn(Collections.emptyList());

            // Act
            MemoryService.BatchEmbeddingResult result = memoryService.processBatchEmbedding();

            // Assert
            assertNotNull(result, "Result should not be null");
            assertInstanceOf(MemoryService.BatchEmbeddingResult.class, result);
        }

        @Test
        @DisplayName("BatchEmbeddingResult contains all required fields")
        void testBatchEmbeddingResultFields() {
            // Arrange
            when(resourceRepository.findResourcesWithoutEmbedding()).thenReturn(Collections.emptyList());
            when(snippetRepository.findSnippetsWithoutEmbedding()).thenReturn(Collections.emptyList());
            when(preferenceRepository.findPreferencesWithoutEmbedding()).thenReturn(Collections.emptyList());

            // Act
            MemoryService.BatchEmbeddingResult result = memoryService.collectTextsForEmbedding();

            // Assert
            assertDoesNotThrow(() -> {
                result.getResourceCount();
                result.getSnippetCount();
                result.getPreferenceCount();
                result.getTotalCount();
                result.getSuccessCount();
                result.getFailureCount();
                result.getErrors();
                result.getProcessingTimeMs();
            });
        }

        @Test
        @DisplayName("BatchEmbeddingRequest contains all required fields")
        void testBatchEmbeddingRequestFields() {
            // Arrange
            List<Resource> resources = createTestResources(1);
            List<Snippet> snippets = createTestSnippets(1);
            List<Preference> preferences = createTestPreferences(1);
            when(contentProcessor.batchGenerateAbstracts(anyList())).thenReturn(Arrays.asList("abstract"));

            // Act
            MemoryService.BatchEmbeddingRequest request = memoryService.prepareBatchEmbeddingRequests(
                resources, snippets, preferences
            );

            // Assert
            assertDoesNotThrow(() -> {
                request.getResources();
                request.getSnippets();
                request.getPreferences();
                request.getResourceAbstracts();
                request.getSnippetSummaries();
                request.getPreferenceSummaries();
            });
        }
    }

    // ==================== 2. Normal Functionality Tests ====================

    @Nested
    @DisplayName("2. Normal Functionality Tests")
    class NormalFunctionalityTests {

        @Test
        @DisplayName("collectTextsForEmbedding() collects from all repositories")
        void testCollectFromAllRepositories() {
            // Arrange
            List<Resource> resources = createTestResources(5);
            List<Snippet> snippets = createTestSnippets(3);
            List<Preference> preferences = createTestPreferences(2);
            
            when(resourceRepository.findResourcesWithoutEmbedding()).thenReturn(resources);
            when(snippetRepository.findSnippetsWithoutEmbedding()).thenReturn(snippets);
            when(preferenceRepository.findPreferencesWithoutEmbedding()).thenReturn(preferences);

            // Act
            MemoryService.BatchEmbeddingResult result = memoryService.collectTextsForEmbedding();

            // Assert
            assertEquals(5, result.getResourceCount());
            assertEquals(3, result.getSnippetCount());
            assertEquals(2, result.getPreferenceCount());
            assertEquals(10, result.getTotalCount());
        }

        @Test
        @DisplayName("prepareBatchEmbeddingRequests() generates abstracts for resources")
        void testPrepareBatchGeneratesAbstracts() {
            // Arrange
            List<Resource> resources = createTestResources(3);
            List<String> expectedAbstracts = Arrays.asList("abstract-1", "abstract-2", "abstract-3");
            
            when(contentProcessor.batchGenerateAbstracts(anyList())).thenReturn(expectedAbstracts);

            // Act
            MemoryService.BatchEmbeddingRequest request = memoryService.prepareBatchEmbeddingRequests(
                resources, Collections.emptyList(), Collections.emptyList()
            );

            // Assert
            assertEquals(3, request.getResourceAbstracts().size());
            assertEquals("abstract-1", request.getResourceAbstracts().get(0));
            assertEquals("abstract-2", request.getResourceAbstracts().get(1));
            assertEquals("abstract-3", request.getResourceAbstracts().get(2));
            verify(contentProcessor, times(1)).batchGenerateAbstracts(anyList());
        }

        @Test
        @DisplayName("prepareBatchEmbeddingRequests() collects snippet summaries")
        void testPrepareBatchCollectsSnippetSummaries() {
            // Arrange
            List<Snippet> snippets = createTestSnippets(3);

            // Act
            MemoryService.BatchEmbeddingRequest request = memoryService.prepareBatchEmbeddingRequests(
                Collections.emptyList(), snippets, Collections.emptyList()
            );

            // Assert
            assertEquals(3, request.getSnippetSummaries().size());
            assertEquals("Test summary 0", request.getSnippetSummaries().get(0));
            assertEquals("Test summary 1", request.getSnippetSummaries().get(1));
            assertEquals("Test summary 2", request.getSnippetSummaries().get(2));
        }

        @Test
        @DisplayName("processBatchEmbedding() orchestrates complete flow")
        void testProcessBatchEmbeddingCompleteFlow() {
            // Arrange
            List<Resource> resources = createTestResources(2);
            List<Snippet> snippets = createTestSnippets(2);
            List<Preference> preferences = createTestPreferences(1);
            
            when(resourceRepository.findResourcesWithoutEmbedding()).thenReturn(resources);
            when(snippetRepository.findSnippetsWithoutEmbedding()).thenReturn(snippets);
            when(preferenceRepository.findPreferencesWithoutEmbedding()).thenReturn(preferences);
            when(contentProcessor.batchGenerateAbstracts(anyList())).thenReturn(Arrays.asList("a1", "a2"));
            doNothing().when(memoryManager).batchUpdateResourceEmbeddings(anyList());
            doNothing().when(memoryManager).batchUpdateSnippetEmbeddings(anyList());
            doNothing().when(memoryManager).batchUpdatePreferenceEmbeddings(anyList());

            // Act
            MemoryService.BatchEmbeddingResult result = memoryService.processBatchEmbedding();

            // Assert
            assertEquals(2, result.getResourceCount());
            assertEquals(2, result.getSnippetCount());
            assertEquals(1, result.getPreferenceCount());
            assertTrue(result.getSuccessCount() > 0);
            verify(memoryManager, times(1)).batchUpdateResourceEmbeddings(anyList());
            verify(memoryManager, times(1)).batchUpdateSnippetEmbeddings(anyList());
            verify(memoryManager, times(1)).batchUpdatePreferenceEmbeddings(anyList());
        }
    }

    // ==================== 3. Boundary Condition Tests ====================

    @Nested
    @DisplayName("3. Boundary Condition Tests")
    class BoundaryConditionTests {

        @Test
        @DisplayName("collectTextsForEmbedding() handles empty collections")
        void testCollectHandlesEmptyCollections() {
            // Arrange
            when(resourceRepository.findResourcesWithoutEmbedding()).thenReturn(Collections.emptyList());
            when(snippetRepository.findSnippetsWithoutEmbedding()).thenReturn(Collections.emptyList());
            when(preferenceRepository.findPreferencesWithoutEmbedding()).thenReturn(Collections.emptyList());

            // Act
            MemoryService.BatchEmbeddingResult result = memoryService.collectTextsForEmbedding();

            // Assert
            assertEquals(0, result.getResourceCount());
            assertEquals(0, result.getSnippetCount());
            assertEquals(0, result.getPreferenceCount());
            assertEquals(0, result.getTotalCount());
        }

        @Test
        @DisplayName("prepareBatchEmbeddingRequests() handles null parameters")
        void testPrepareHandlesNullParameters() {
            // Act & Assert
            assertDoesNotThrow(() -> {
                MemoryService.BatchEmbeddingRequest request = memoryService.prepareBatchEmbeddingRequests(
                    null, null, null
                );
                assertNotNull(request);
                assertTrue(request.getResources().isEmpty());
                assertTrue(request.getSnippets().isEmpty());
                assertTrue(request.getPreferences().isEmpty());
            });
        }

        @Test
        @DisplayName("prepareBatchEmbeddingRequests() handles empty lists")
        void testPrepareHandlesEmptyLists() {
            // Act & Assert
            assertDoesNotThrow(() -> {
                MemoryService.BatchEmbeddingRequest request = memoryService.prepareBatchEmbeddingRequests(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
                );
                assertNotNull(request);
                assertTrue(request.getResources().isEmpty());
                assertTrue(request.getSnippets().isEmpty());
                assertTrue(request.getPreferences().isEmpty());
                assertTrue(request.getResourceAbstracts().isEmpty());
                assertTrue(request.getSnippetSummaries().isEmpty());
                assertTrue(request.getPreferenceSummaries().isEmpty());
            });
        }

        @Test
        @DisplayName("processBatchEmbedding() handles no items to process")
        void testProcessHandlesNoItems() {
            // Arrange
            when(resourceRepository.findResourcesWithoutEmbedding()).thenReturn(Collections.emptyList());
            when(snippetRepository.findSnippetsWithoutEmbedding()).thenReturn(Collections.emptyList());
            when(preferenceRepository.findPreferencesWithoutEmbedding()).thenReturn(Collections.emptyList());

            // Act
            MemoryService.BatchEmbeddingResult result = memoryService.processBatchEmbedding();

            // Assert
            assertEquals(0, result.getTotalCount());
            assertEquals(0, result.getFailureCount());
            assertTrue(result.getErrors().isEmpty() || result.getErrors().size() == 0);
        }

        @Test
        @DisplayName("collectTextsForEmbedding() handles large collections")
        void testCollectHandlesLargeCollections() {
            // Arrange
            List<Resource> resources = createTestResources(500);
            List<Snippet> snippets = createTestSnippets(500);
            List<Preference> preferences = createTestPreferences(500);
            
            when(resourceRepository.findResourcesWithoutEmbedding()).thenReturn(resources);
            when(snippetRepository.findSnippetsWithoutEmbedding()).thenReturn(snippets);
            when(preferenceRepository.findPreferencesWithoutEmbedding()).thenReturn(preferences);

            // Act
            MemoryService.BatchEmbeddingResult result = memoryService.collectTextsForEmbedding();

            // Assert
            assertEquals(500, result.getResourceCount());
            assertEquals(500, result.getSnippetCount());
            assertEquals(500, result.getPreferenceCount());
            assertEquals(1500, result.getTotalCount());
        }
    }

    // ==================== 4. Exception Handling Tests ====================

    @Nested
    @DisplayName("4. Exception Handling Tests")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("collectTextsForEmbedding() handles repository exceptions")
        void testCollectHandlesRepositoryExceptions() {
            // Arrange
            when(resourceRepository.findResourcesWithoutEmbedding())
                .thenThrow(new RuntimeException("Resource repository error"));
            when(snippetRepository.findSnippetsWithoutEmbedding()).thenReturn(Collections.emptyList());
            when(preferenceRepository.findPreferencesWithoutEmbedding()).thenReturn(Collections.emptyList());

            // Act
            MemoryService.BatchEmbeddingResult result = memoryService.collectTextsForEmbedding();

            // Assert
            assertFalse(result.getErrors().isEmpty());
            assertTrue(result.getErrors().get(0).contains("Resources"));
        }

        @Test
        @DisplayName("prepareBatchEmbeddingRequests() handles processor exceptions")
        void testPrepareHandlesProcessorExceptions() {
            // Arrange
            List<Resource> resources = createTestResources(3);
            when(contentProcessor.batchGenerateAbstracts(anyList()))
                .thenThrow(new RuntimeException("Processor error"));

            // Act & Assert
            assertThrows(MemoryServiceException.class, () -> {
                memoryService.prepareBatchEmbeddingRequests(
                    resources, Collections.emptyList(), Collections.emptyList()
                );
            });
        }

        @Test
        @DisplayName("processBatchEmbedding() handles memory manager exceptions")
        void testProcessHandlesMemoryManagerExceptions() {
            // Arrange
            List<Resource> resources = createTestResources(2);
            List<Snippet> snippets = createTestSnippets(2);
            List<Preference> preferences = createTestPreferences(1);
            
            when(resourceRepository.findResourcesWithoutEmbedding()).thenReturn(resources);
            when(snippetRepository.findSnippetsWithoutEmbedding()).thenReturn(snippets);
            when(preferenceRepository.findPreferencesWithoutEmbedding()).thenReturn(preferences);
            when(contentProcessor.batchGenerateAbstracts(anyList())).thenReturn(Arrays.asList("a1", "a2"));
            doThrow(new RuntimeException("Memory manager error"))
                .when(memoryManager).batchUpdateResourceEmbeddings(anyList());

            // Act
            MemoryService.BatchEmbeddingResult result = memoryService.processBatchEmbedding();

            // Assert
            assertTrue(result.getFailureCount() > 0);
            assertFalse(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("collectTextsForEmbedding() aggregates multiple errors")
        void testCollectAggregatesMultipleErrors() {
            // Arrange
            when(resourceRepository.findResourcesWithoutEmbedding())
                .thenThrow(new RuntimeException("Resource error"));
            when(snippetRepository.findSnippetsWithoutEmbedding())
                .thenThrow(new RuntimeException("Snippet error"));
            when(preferenceRepository.findPreferencesWithoutEmbedding())
                .thenThrow(new RuntimeException("Preference error"));

            // Act
            MemoryService.BatchEmbeddingResult result = memoryService.collectTextsForEmbedding();

            // Assert
            assertTrue(result.getErrors().size() >= 3);
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Resource")));
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Snippet")));
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Preference")));
        }

        @Test
        @DisplayName("processBatchEmbedding() continues on partial failures")
        void testProcessContinuesOnPartialFailures() {
            // Arrange
            List<Resource> resources = createTestResources(2);
            List<Snippet> snippets = createTestSnippets(2);
            List<Preference> preferences = createTestPreferences(1);
            
            when(resourceRepository.findResourcesWithoutEmbedding()).thenReturn(resources);
            when(snippetRepository.findSnippetsWithoutEmbedding()).thenReturn(snippets);
            when(preferenceRepository.findPreferencesWithoutEmbedding()).thenReturn(preferences);
            when(contentProcessor.batchGenerateAbstracts(anyList())).thenReturn(Arrays.asList("a1", "a2"));
            doThrow(new RuntimeException("Resource update error"))
                .when(memoryManager).batchUpdateResourceEmbeddings(anyList());
            doNothing().when(memoryManager).batchUpdateSnippetEmbeddings(anyList());
            doNothing().when(memoryManager).batchUpdatePreferenceEmbeddings(anyList());

            // Act
            MemoryService.BatchEmbeddingResult result = memoryService.processBatchEmbedding();

            // Assert
            assertTrue(result.getFailureCount() > 0);
            // Snippets and preferences should still be processed
            verify(memoryManager, times(1)).batchUpdateSnippetEmbeddings(anyList());
            verify(memoryManager, times(1)).batchUpdatePreferenceEmbeddings(anyList());
        }
    }

    // ==================== 5. Performance Benchmark Tests ====================

    @Nested
    @DisplayName("5. Performance Benchmark Tests")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    class PerformanceBenchmarkTests {

        @Test
        @DisplayName("collectTextsForEmbedding() completes within 100ms")
        void testCollectCompletesWithin100ms() {
            // Arrange
            List<Resource> resources = createTestResources(10);
            List<Snippet> snippets = createTestSnippets(10);
            List<Preference> preferences = createTestPreferences(10);
            
            when(resourceRepository.findResourcesWithoutEmbedding()).thenReturn(resources);
            when(snippetRepository.findSnippetsWithoutEmbedding()).thenReturn(snippets);
            when(preferenceRepository.findPreferencesWithoutEmbedding()).thenReturn(preferences);

            // Act
            long startTime = System.currentTimeMillis();
            MemoryService.BatchEmbeddingResult result = memoryService.collectTextsForEmbedding();
            long duration = System.currentTimeMillis() - startTime;

            // Assert
            assertTrue(duration < 100, "Collection should complete within 100ms, took " + duration + "ms");
        }

        @Test
        @DisplayName("prepareBatchEmbeddingRequests() completes within 100ms")
        void testPrepareCompletesWithin100ms() {
            // Arrange
            List<Resource> resources = createTestResources(10);
            List<Snippet> snippets = createTestSnippets(10);
            List<Preference> preferences = createTestPreferences(10);
            
            when(contentProcessor.batchGenerateAbstracts(anyList()))
                .thenReturn(Collections.nCopies(10, "abstract"));

            // Act
            long startTime = System.currentTimeMillis();
            MemoryService.BatchEmbeddingRequest request = memoryService.prepareBatchEmbeddingRequests(
                resources, snippets, preferences
            );
            long duration = System.currentTimeMillis() - startTime;

            // Assert
            assertTrue(duration < 100, "Preparation should complete within 100ms, took " + duration + "ms");
        }

        @Test
        @DisplayName("processBatchEmbedding() handles 100 items within 1s")
        void testProcessHandles100ItemsWithin1s() {
            // Arrange
            List<Resource> resources = createTestResources(34);
            List<Snippet> snippets = createTestSnippets(33);
            List<Preference> preferences = createTestPreferences(33);
            
            when(resourceRepository.findResourcesWithoutEmbedding()).thenReturn(resources);
            when(snippetRepository.findSnippetsWithoutEmbedding()).thenReturn(snippets);
            when(preferenceRepository.findPreferencesWithoutEmbedding()).thenReturn(preferences);
            when(contentProcessor.batchGenerateAbstracts(anyList()))
                .thenReturn(Collections.nCopies(34, "abstract"));
            doNothing().when(memoryManager).batchUpdateResourceEmbeddings(anyList());
            doNothing().when(memoryManager).batchUpdateSnippetEmbeddings(anyList());
            doNothing().when(memoryManager).batchUpdatePreferenceEmbeddings(anyList());

            // Act
            long startTime = System.currentTimeMillis();
            MemoryService.BatchEmbeddingResult result = memoryService.processBatchEmbedding();
            long duration = System.currentTimeMillis() - startTime;

            // Assert
            assertTrue(duration < 1000, "Processing 100 items should complete within 1s, took " + duration + "ms");
            assertEquals(100, result.getTotalCount());
        }
    }

    // ==================== 6. Concurrency Scenario Tests ====================

    @Nested
    @DisplayName("6. Concurrency Scenario Tests")
    class ConcurrencyScenarioTests {

        @Test
        @DisplayName("Concurrent collectTextsForEmbedding() calls succeed")
        void testConcurrentCollectCalls() throws InterruptedException {
            // Arrange
            List<Resource> resources = createTestResources(10);
            List<Snippet> snippets = createTestSnippets(10);
            List<Preference> preferences = createTestPreferences(10);
            
            when(resourceRepository.findResourcesWithoutEmbedding()).thenReturn(resources);
            when(snippetRepository.findSnippetsWithoutEmbedding()).thenReturn(snippets);
            when(preferenceRepository.findPreferencesWithoutEmbedding()).thenReturn(preferences);

            // Act
            int threadCount = 10;
            List<CompletableFuture<MemoryService.BatchEmbeddingResult>> futures = new ArrayList<>();
            
            for (int i = 0; i < threadCount; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> 
                    memoryService.collectTextsForEmbedding(), executorService));
            }
            
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Assert
            for (CompletableFuture<MemoryService.BatchEmbeddingResult> future : futures) {
                MemoryService.BatchEmbeddingResult result = future.join();
                assertEquals(30, result.getTotalCount());
            }
        }

        @Test
        @DisplayName("Concurrent processBatchEmbedding() calls succeed")
        void testConcurrentProcessCalls() throws InterruptedException {
            // Arrange
            List<Resource> resources = createTestResources(5);
            List<Snippet> snippets = createTestSnippets(5);
            List<Preference> preferences = createTestPreferences(5);
            
            when(resourceRepository.findResourcesWithoutEmbedding()).thenReturn(resources);
            when(snippetRepository.findSnippetsWithoutEmbedding()).thenReturn(snippets);
            when(preferenceRepository.findPreferencesWithoutEmbedding()).thenReturn(preferences);
            when(contentProcessor.batchGenerateAbstracts(anyList()))
                .thenReturn(Collections.nCopies(5, "abstract"));
            doNothing().when(memoryManager).batchUpdateResourceEmbeddings(anyList());
            doNothing().when(memoryManager).batchUpdateSnippetEmbeddings(anyList());
            doNothing().when(memoryManager).batchUpdatePreferenceEmbeddings(anyList());

            // Act
            int threadCount = 10;
            List<CompletableFuture<MemoryService.BatchEmbeddingResult>> futures = new ArrayList<>();
            
            for (int i = 0; i < threadCount; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> 
                    memoryService.processBatchEmbedding(), executorService));
            }
            
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Assert
            for (CompletableFuture<MemoryService.BatchEmbeddingResult> future : futures) {
                MemoryService.BatchEmbeddingResult result = future.join();
                assertEquals(15, result.getTotalCount());
            }
        }

        @Test
        @DisplayName("Concurrent operations maintain data consistency")
        void testConcurrentMaintainsConsistency() throws InterruptedException {
            // Arrange
            AtomicInteger successCount = new AtomicInteger(0);
            List<Resource> resources = createTestResources(3);
            List<Snippet> snippets = createTestSnippets(3);
            List<Preference> preferences = createTestPreferences(3);
            
            when(resourceRepository.findResourcesWithoutEmbedding()).thenReturn(resources);
            when(snippetRepository.findSnippetsWithoutEmbedding()).thenReturn(snippets);
            when(preferenceRepository.findPreferencesWithoutEmbedding()).thenReturn(preferences);
            when(contentProcessor.batchGenerateAbstracts(anyList()))
                .thenReturn(Collections.nCopies(3, "abstract"));
            doAnswer(invocation -> {
                successCount.incrementAndGet();
                return null;
            }).when(memoryManager).batchUpdateResourceEmbeddings(anyList());
            doAnswer(invocation -> {
                successCount.incrementAndGet();
                return null;
            }).when(memoryManager).batchUpdateSnippetEmbeddings(anyList());
            doAnswer(invocation -> {
                successCount.incrementAndGet();
                return null;
            }).when(memoryManager).batchUpdatePreferenceEmbeddings(anyList());

            // Act
            int threadCount = 10;
            List<CompletableFuture<MemoryService.BatchEmbeddingResult>> futures = new ArrayList<>();
            
            for (int i = 0; i < threadCount; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> 
                    memoryService.processBatchEmbedding(), executorService));
            }
            
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Assert
            // Each thread should process 3 types (resources, snippets, preferences)
            assertEquals(threadCount * 3, successCount.get());
        }
    }

    // ==================== 7. Additional Validation Tests ====================

    @Nested
    @DisplayName("7. Additional Validation Tests")
    class AdditionalValidationTests {

        @Test
        @DisplayName("BatchEmbeddingResult.getTotalCount() returns sum")
        void testTotalCountCalculation() {
            // Arrange
            List<Resource> resources = createTestResources(5);
            List<Snippet> snippets = createTestSnippets(3);
            List<Preference> preferences = createTestPreferences(2);
            
            when(resourceRepository.findResourcesWithoutEmbedding()).thenReturn(resources);
            when(snippetRepository.findSnippetsWithoutEmbedding()).thenReturn(snippets);
            when(preferenceRepository.findPreferencesWithoutEmbedding()).thenReturn(preferences);

            // Act
            MemoryService.BatchEmbeddingResult result = memoryService.collectTextsForEmbedding();

            // Assert
            assertEquals(5, result.getResourceCount());
            assertEquals(3, result.getSnippetCount());
            assertEquals(2, result.getPreferenceCount());
            assertEquals(10, result.getTotalCount());
        }

        @Test
        @DisplayName("BatchEmbeddingResult tracks processing time")
        void testProcessingTimeTracking() {
            // Arrange
            when(resourceRepository.findResourcesWithoutEmbedding()).thenReturn(Collections.emptyList());
            when(snippetRepository.findSnippetsWithoutEmbedding()).thenReturn(Collections.emptyList());
            when(preferenceRepository.findPreferencesWithoutEmbedding()).thenReturn(Collections.emptyList());

            // Act
            MemoryService.BatchEmbeddingResult result = memoryService.collectTextsForEmbedding();

            // Assert
            assertTrue(result.getProcessingTimeMs() >= 0, 
                "Processing time should be non-negative");
        }

        @Test
        @DisplayName("prepareBatchEmbeddingRequests() uses resource content when abstract is null")
        void testPrepareUsesContentWhenAbstractNull() {
            // Arrange
            Resource resourceWithoutAbstract = new Resource();
            resourceWithoutAbstract.setId("resource-1");
            resourceWithoutAbstract.setContent("Full content");
            resourceWithoutAbstract.setAbstract(null);
            resourceWithoutAbstract.setSessionId("session-1");
            resourceWithoutAbstract.setConversationId("conversation-1");
            resourceWithoutAbstract.setCreatedAt(System.currentTimeMillis());
            
            when(contentProcessor.batchGenerateAbstracts(anyList())).thenReturn(Arrays.asList("generated-abstract"));

            // Act
            MemoryService.BatchEmbeddingRequest request = memoryService.prepareBatchEmbeddingRequests(
                Arrays.asList(resourceWithoutAbstract), Collections.emptyList(), Collections.emptyList()
            );

            // Assert
            assertEquals(1, request.getResourceAbstracts().size());
            assertEquals("generated-abstract", request.getResourceAbstracts().get(0));
            verify(contentProcessor, times(1)).batchGenerateAbstracts(anyList());
        }
    }
}
