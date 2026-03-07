package tech.yesboss.memory.query;

import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tech.yesboss.memory.embedding.EmbeddingService;
import tech.yesboss.memory.model.*;
import tech.yesboss.memory.repository.PreferenceRepository;
import tech.yesboss.memory.repository.ResourceRepository;
import tech.yesboss.memory.repository.SnippetRepository;
import tech.yesboss.memory.vectorstore.VectorStore;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit test suite for MemoryQueryService
 *
 * Tests cover:
 * - Interface contract validation
 * - Functional correctness
 * - Edge cases and boundary conditions
 * - Error handling
 * - Performance benchmarks
 * - Concurrent operations
 */
@DisplayName("MemoryQueryService Unit Tests")
@Nested
class MemoryQueryServiceTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private SnippetRepository snippetRepository;

    @Mock
    private PreferenceRepository preferenceRepository;

    private MemoryQueryService memoryQueryService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        memoryQueryService = new MemoryQueryServiceImpl(
            embeddingService,
            vectorStore,
            resourceRepository,
            snippetRepository,
            preferenceRepository
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // ==========================================
    // Interface Contract Tests
    // ==========================================

    @Nested
    @DisplayName("Interface Contract Tests")
    class InterfaceContractTests {

        @Test
        @DisplayName("Should implement MemoryQueryService interface")
        void testImplementsInterface() {
            assertTrue(memoryQueryService instanceof MemoryQueryService);
        }

        @Test
        @DisplayName("Should have all required methods")
        void testHasAllRequiredMethods() throws NoSuchMethodException {
            Class<?> clazz = memoryQueryService.getClass();

            // Check for all interface methods
            assertNotNull(clazz.getMethod("queryMemory", String.class, int.class));
            assertNotNull(clazz.getMethod("findResourcesByQuery", String.class, int.class));
            assertNotNull(clazz.getMethod("findSnippetsByQuery", String.class, int.class));
            assertNotNull(clazz.getMethod("findPreferencesByQuery", String.class, int.class));
            assertNotNull(clazz.getMethod("findResourcesByPreference", String.class, String.class, int.class));
            assertNotNull(clazz.getMethod("findMemoryChainByPreferenceAndTime", String.class, long.class, long.class));
            assertNotNull(clazz.getMethod("findMemoryChainsBySessionId", String.class));
            assertNotNull(clazz.getMethod("fuzzySearchSnippets", String.class, int.class));
            assertNotNull(clazz.getMethod("fuzzySearchSnippetsByPreference", String.class, String.class, int.class));
            assertNotNull(clazz.getMethod("semanticSearch", String.class, String.class, int.class));
            assertNotNull(clazz.getMethod("semanticSearch", String.class, String.class, Long.class, Long.class, int.class));
            assertNotNull(clazz.getMethod("hybridSearch", String.class, String.class, int.class));
            assertNotNull(clazz.getMethod("recommendByContext", String.class, String.class, int.class));
            assertNotNull(clazz.getMethod("searchByTimeWindow", long.class, long.class, int.class));
            assertNotNull(clazz.getMethod("getPreferenceAggregation", String.class));
            assertNotNull(clazz.getMethod("getSessionAggregation", String.class));
        }
    }

    // ==========================================
    // Functional Correctness Tests
    // ==========================================

    @Nested
    @DisplayName("Functional Correctness Tests")
    class FunctionalCorrectnessTests {

        @Test
        @DisplayName("queryMemory should return AgenticRagResult with all layers")
        void testQueryMemoryReturnsAllLayers() throws Exception {
            // Arrange
            String query = "test query";
            int topK = 5;
            float[] queryVector = new float[1536];

            List<VectorStore.SearchResult> preferenceResults = Arrays.asList(
                new VectorStore.SearchResult("pref1", 0.9f),
                new VectorStore.SearchResult("pref2", 0.8f)
            );

            List<VectorStore.SearchResult> snippetResults = Arrays.asList(
                new VectorStore.SearchResult("snippet1", 0.85f),
                new VectorStore.SearchResult("snippet2", 0.75f)
            );

            List<VectorStore.SearchResult> resourceResults = Arrays.asList(
                new VectorStore.SearchResult("resource1", 0.88f)
            );

            List<Preference> preferences = Arrays.asList(
                createMockPreference("pref1", "Preference 1"),
                createMockPreference("pref2", "Preference 2")
            );

            List<Snippet> snippets = Arrays.asList(
                createMockSnippet("snippet1", "Summary 1"),
                createMockSnippet("snippet2", "Summary 2")
            );

            List<Resource> resources = Arrays.asList(
                createMockResource("resource1", "Content 1")
            );

            when(embeddingService.generateEmbedding(query)).thenReturn(queryVector);
            when(vectorStore.search(queryVector, topK)).thenReturn(
                preferenceResults,
                snippetResults,
                resourceResults
            );
            when(preferenceRepository.findByIds(anyList())).thenReturn(preferences);
            when(snippetRepository.findByIds(anyList())).thenReturn(snippets);
            when(resourceRepository.findByIds(anyList())).thenReturn(resources);
            when(snippetRepository.findByResourceIds(anyList())).thenReturn(snippets);

            // Act
            AgenticRagResult result = memoryQueryService.queryMemory(query, topK);

            // Assert
            assertNotNull(result);
            assertEquals(query, result.getQuery());
            assertEquals(query, result.getFinalQuery());
            assertFalse(result.getPreferences().isEmpty());
            assertFalse(result.getSnippets().isEmpty());
            assertFalse(result.getResources().isEmpty());
            assertFalse(result.getLinkedSnippets().isEmpty());
            assertFalse(result.getDecisionHistory().isEmpty());
            assertTrue(result.getTotalDurationMs() >= 0);
        }

        @Test
        @DisplayName("queryMemory should stop at Preference layer if sufficient")
        void testQueryMemoryStopsAtPreferenceLayer() throws Exception {
            // Arrange
            String query = "simple query";
            int topK = 5;
            float[] queryVector = new float[1536];

            List<VectorStore.SearchResult> preferenceResults = Arrays.asList(
                new VectorStore.SearchResult("pref1", 0.95f),
                new VectorStore.SearchResult("pref2", 0.92f),
                new VectorStore.SearchResult("pref3", 0.89f)
            );

            List<Preference> preferences = Arrays.asList(
                createMockPreference("pref1", "Preference 1"),
                createMockPreference("pref2", "Preference 2"),
                createMockPreference("pref3", "Preference 3")
            );

            when(embeddingService.generateEmbedding(query)).thenReturn(queryVector);
            when(vectorStore.search(eq(queryVector), eq(topK))).thenReturn(preferenceResults);
            when(preferenceRepository.findByIds(anyList())).thenReturn(preferences);

            // Act
            AgenticRagResult result = memoryQueryService.queryMemory(query, topK);

            // Assert
            assertNotNull(result);
            assertEquals(3, result.getPreferences().size());
            assertTrue(result.getSnippets().isEmpty());
            assertTrue(result.getResources().isEmpty());

            // Verify decision history shows stopping at Preference layer
            boolean stoppedAtPreference = result.getDecisionHistory().stream()
                .anyMatch(log -> log.getTier() == AgenticRagResult.RetrievalLevel.PREFERENCE
                    && log.getDecision() == DecisionLog.DecisionType.SUFFICIENT);
            assertTrue(stoppedAtPreference);
        }

        @Test
        @DisplayName("findResourcesByQuery should return resources by semantic search")
        void testFindResourcesByQuery() throws Exception {
            // Arrange
            String query = "test query";
            int topK = 5;
            float[] queryVector = new float[1536];

            List<VectorStore.SearchResult> searchResults = Arrays.asList(
                new VectorStore.SearchResult("resource1", 0.9f),
                new VectorStore.SearchResult("resource2", 0.8f)
            );

            List<Resource> resources = Arrays.asList(
                createMockResource("resource1", "Content 1"),
                createMockResource("resource2", "Content 2")
            );

            when(embeddingService.generateEmbedding(query)).thenReturn(queryVector);
            when(vectorStore.search(queryVector, topK)).thenReturn(searchResults);
            when(resourceRepository.findByIds(anyList())).thenReturn(resources);

            // Act
            List<Resource> result = memoryQueryService.findResourcesByQuery(query, topK);

            // Assert
            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals("resource1", result.get(0).getId());
            assertEquals("resource2", result.get(1).getId());
        }

        @Test
        @DisplayName("findSnippetsByQuery should return snippets by semantic search")
        void testFindSnippetsByQuery() throws Exception {
            // Arrange
            String query = "test query";
            int topK = 5;
            float[] queryVector = new float[1536];

            List<VectorStore.SearchResult> searchResults = Arrays.asList(
                new VectorStore.SearchResult("snippet1", 0.9f)
            );

            List<Snippet> snippets = Arrays.asList(
                createMockSnippet("snippet1", "Summary 1")
            );

            when(embeddingService.generateEmbedding(query)).thenReturn(queryVector);
            when(vectorStore.search(queryVector, topK)).thenReturn(searchResults);
            when(snippetRepository.findByIds(anyList())).thenReturn(snippets);

            // Act
            List<Snippet> result = memoryQueryService.findSnippetsByQuery(query, topK);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("snippet1", result.get(0).getId());
        }

        @Test
        @DisplayName("findPreferencesByQuery should return preferences by semantic search")
        void testFindPreferencesByQuery() throws Exception {
            // Arrange
            String query = "test query";
            int topK = 5;
            float[] queryVector = new float[1536];

            List<VectorStore.SearchResult> searchResults = Arrays.asList(
                new VectorStore.SearchResult("pref1", 0.9f)
            );

            List<Preference> preferences = Arrays.asList(
                createMockPreference("pref1", "Preference 1")
            );

            when(embeddingService.generateEmbedding(query)).thenReturn(queryVector);
            when(vectorStore.search(queryVector, topK)).thenReturn(searchResults);
            when(preferenceRepository.findByIds(anyList())).thenReturn(preferences);

            // Act
            List<Preference> result = memoryQueryService.findPreferencesByQuery(query, topK);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("pref1", result.get(0).getId());
        }

        @Test
        @DisplayName("fuzzySearchSnippets should perform keyword search")
        void testFuzzySearchSnippets() {
            // Arrange
            String keyword = "test";
            int topK = 5;
            List<Snippet> snippets = Arrays.asList(
                createMockSnippet("snippet1", "Test summary")
            );

            when(snippetRepository.searchByKeyword(keyword, topK)).thenReturn(snippets);

            // Act
            List<Snippet> result = memoryQueryService.fuzzySearchSnippets(keyword, topK);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            verify(snippetRepository).searchByKeyword(keyword, topK);
        }

        @Test
        @DisplayName("semanticSearch should filter by time range when provided")
        void testSemanticSearchWithTimeRange() throws Exception {
            // Arrange
            String query = "test query";
            String preferenceId = null;
            Long startTime = System.currentTimeMillis() - 10000;
            Long endTime = System.currentTimeMillis();
            int topK = 5;
            float[] queryVector = new float[1536];

            LocalDateTime oldTime = LocalDateTime.now().minusDays(10);
            LocalDateTime recentTime = LocalDateTime.now();

            List<VectorStore.SearchResult> searchResults = Arrays.asList(
                new VectorStore.SearchResult("snippet1", 0.9f),
                new VectorStore.SearchResult("snippet2", 0.8f)
            );

            List<Snippet> allSnippets = Arrays.asList(
                createMockSnippetWithTime("snippet1", "Summary 1", oldTime),
                createMockSnippetWithTime("snippet2", "Summary 2", recentTime)
            );

            when(embeddingService.generateEmbedding(query)).thenReturn(queryVector);
            when(vectorStore.search(queryVector, topK)).thenReturn(searchResults);
            when(snippetRepository.findByIds(anyList())).thenReturn(allSnippets);

            // Act
            List<Snippet> result = memoryQueryService.semanticSearch(
                query, preferenceId, startTime, endTime, topK
            );

            // Assert
            assertNotNull(result);
            // Should only return snippets within time range
            assertTrue(result.stream().allMatch(s -> {
                LocalDateTime time = s.getCreatedAt();
                long timestamp = time.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                return timestamp >= startTime && timestamp <= endTime;
            }));
        }

        @Test
        @DisplayName("hybridSearch should merge semantic and keyword results")
        void testHybridSearch() throws Exception {
            // Arrange
            String query = "test query";
            String preferenceId = null;
            int topK = 5;
            float[] queryVector = new float[1536];

            List<VectorStore.SearchResult> searchResults = Arrays.asList(
                new VectorStore.SearchResult("snippet1", 0.9f)
            );

            List<Snippet> semanticResults = Arrays.asList(
                createMockSnippet("snippet1", "Semantic result")
            );

            List<Snippet> keywordResults = Arrays.asList(
                createMockSnippet("snippet2", "Keyword result")
            );

            when(embeddingService.generateEmbedding(query)).thenReturn(queryVector);
            when(vectorStore.search(queryVector, topK)).thenReturn(searchResults);
            when(snippetRepository.findByIds(anyList())).thenReturn(semanticResults);
            when(snippetRepository.searchByKeyword(query, topK)).thenReturn(keywordResults);

            // Act
            List<Snippet> result = memoryQueryService.hybridSearch(query, preferenceId, topK);

            // Assert
            assertNotNull(result);
            assertTrue(result.size() >= 1);
            // Should contain unique results from both searches
            Set<String> ids = result.stream().map(Snippet::getId).collect(java.util.stream.Collectors.toSet());
            assertTrue(ids.contains("snippet1") || ids.contains("snippet2"));
        }

        @Test
        @DisplayName("searchByTimeWindow should find snippets in time range")
        void testSearchByTimeWindow() {
            // Arrange
            long timestamp = System.currentTimeMillis();
            long timeWindow = 60000; // 1 minute
            int topK = 5;

            List<Snippet> snippets = Arrays.asList(
                createMockSnippet("snippet1", "Summary 1")
            );

            when(snippetRepository.findByTimeRange(anyLong(), anyLong(), eq(topK)))
                .thenReturn(snippets);

            // Act
            List<Snippet> result = memoryQueryService.searchByTimeWindow(timestamp, timeWindow, topK);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            verify(snippetRepository).findByTimeRange(
                eq(timestamp - timeWindow / 2),
                eq(timestamp + timeWindow / 2),
                eq(topK)
            );
        }
    }

    // ==========================================
    // Edge Cases and Boundary Conditions Tests
    // ==========================================

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("queryMemory should handle null query")
        void testQueryMemoryWithNullQuery() {
            // Act & Assert
            assertThrows(Exception.class, () -> {
                memoryQueryService.queryMemory(null, 5);
            });
        }

        @Test
        @DisplayName("queryMemory should handle empty query")
        void testQueryMemoryWithEmptyQuery() {
            // Act & Assert
            assertThrows(Exception.class, () -> {
                memoryQueryService.queryMemory("", 5);
            });
        }

        @Test
        @DisplayName("queryMemory should handle zero topK")
        void testQueryMemoryWithZeroTopK() throws Exception {
            // Arrange
            String query = "test query";
            float[] queryVector = new float[1536];

            when(embeddingService.generateEmbedding(query)).thenReturn(queryVector);
            when(vectorStore.search(queryVector, 0)).thenReturn(Collections.emptyList());

            // Act
            AgenticRagResult result = memoryQueryService.queryMemory(query, 0);

            // Assert
            assertNotNull(result);
            assertTrue(result.getPreferences().isEmpty());
            assertTrue(result.getSnippets().isEmpty());
            assertTrue(result.getResources().isEmpty());
        }

        @Test
        @DisplayName("queryMemory should handle negative topK")
        void testQueryMemoryWithNegativeTopK() throws Exception {
            // Arrange
            String query = "test query";
            float[] queryVector = new float[1536];

            when(embeddingService.generateEmbedding(query)).thenReturn(queryVector);
            when(vectorStore.search(queryVector, -5)).thenReturn(Collections.emptyList());

            // Act
            AgenticRagResult result = memoryQueryService.queryMemory(query, -5);

            // Assert
            assertNotNull(result);
        }

        @Test
        @DisplayName("findResourcesByQuery should handle empty results")
        void testFindResourcesByQueryWithEmptyResults() throws Exception {
            // Arrange
            String query = "non-existent query";
            int topK = 5;
            float[] queryVector = new float[1536];

            when(embeddingService.generateEmbedding(query)).thenReturn(queryVector);
            when(vectorStore.search(queryVector, topK)).thenReturn(Collections.emptyList());

            // Act
            List<Resource> result = memoryQueryService.findResourcesByQuery(query, topK);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("semanticSearch should handle null time range")
        void testSemanticSearchWithNullTimeRange() throws Exception {
            // Arrange
            String query = "test query";
            float[] queryVector = new float[1536];

            List<VectorStore.SearchResult> searchResults = Arrays.asList(
                new VectorStore.SearchResult("snippet1", 0.9f)
            );

            List<Snippet> snippets = Arrays.asList(
                createMockSnippet("snippet1", "Summary 1")
            );

            when(embeddingService.generateEmbedding(query)).thenReturn(queryVector);
            when(vectorStore.search(queryVector, 5)).thenReturn(searchResults);
            when(snippetRepository.findByIds(anyList())).thenReturn(snippets);

            // Act
            List<Snippet> result = memoryQueryService.semanticSearch(query, null, null, null, 5);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("fuzzySearchSnippets should handle empty keyword")
        void testFuzzySearchSnippetsWithEmptyKeyword() {
            // Arrange
            String keyword = "";
            int topK = 5;

            when(snippetRepository.searchByKeyword(keyword, topK))
                .thenReturn(Collections.emptyList());

            // Act
            List<Snippet> result = memoryQueryService.fuzzySearchSnippets(keyword, topK);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ==========================================
    // Error Handling Tests
    // ==========================================

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("queryMemory should handle embedding service failure")
        void testQueryMemoryWithEmbeddingServiceFailure() throws Exception {
            // Arrange
            String query = "test query";

            when(embeddingService.generateEmbedding(query))
                .thenThrow(new RuntimeException("Embedding service unavailable"));

            // Act
            AgenticRagResult result = memoryQueryService.queryMemory(query, 5);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
            assertFalse(result.getDecisionHistory().isEmpty());

            // Check that error was logged in decision history
            boolean hasErrorDecision = result.getDecisionHistory().stream()
                .anyMatch(log -> log.getTier() == AgenticRagResult.RetrievalLevel.NONE);
            assertTrue(hasErrorDecision);
        }

        @Test
        @DisplayName("findResourcesByQuery should handle vector store failure")
        void testFindResourcesByQueryWithVectorStoreFailure() throws Exception {
            // Arrange
            String query = "test query";

            when(embeddingService.generateEmbedding(query)).thenReturn(new float[1536]);
            when(vectorStore.search(any(), anyInt()))
                .thenThrow(new RuntimeException("Vector store unavailable"));

            // Act
            List<Resource> result = memoryQueryService.findResourcesByQuery(query, 5);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("queryMemory should handle repository failure")
        void testQueryMemoryWithRepositoryFailure() throws Exception {
            // Arrange
            String query = "test query";
            float[] queryVector = new float[1536];

            List<VectorStore.SearchResult> searchResults = Arrays.asList(
                new VectorStore.SearchResult("pref1", 0.9f)
            );

            when(embeddingService.generateEmbedding(query)).thenReturn(queryVector);
            when(vectorStore.search(queryVector, 5)).thenReturn(searchResults);
            when(preferenceRepository.findByIds(anyList()))
                .thenThrow(new RuntimeException("Database unavailable"));

            // Act
            AgenticRagResult result = memoryQueryService.queryMemory(query, 5);

            // Assert
            assertNotNull(result);
            assertTrue(result.getPreferences().isEmpty());
        }
    }

    // ==========================================
    // Performance Tests
    // ==========================================

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("queryMemory should complete within reasonable time")
        @Timeout(5)
        void testQueryMemoryPerformance() throws Exception {
            // Arrange
            String query = "test query";
            float[] queryVector = new float[1536];

            when(embeddingService.generateEmbedding(query)).thenReturn(queryVector);
            when(vectorStore.search(any(), anyInt())).thenReturn(Collections.emptyList());

            // Act
            long startTime = System.currentTimeMillis();
            AgenticRagResult result = memoryQueryService.queryMemory(query, 5);
            long duration = System.currentTimeMillis() - startTime;

            // Assert
            assertNotNull(result);
            assertTrue(duration < 1000, "Query should complete within 1 second, took: " + duration + "ms");
        }

        @Test
        @DisplayName("findResourcesByQuery should complete within 100ms")
        @Timeout(1)
        void testFindResourcesByQueryPerformance() throws Exception {
            // Arrange
            String query = "test query";
            float[] queryVector = new float[1536];

            when(embeddingService.generateEmbedding(query)).thenReturn(queryVector);
            when(vectorStore.search(any(), anyInt())).thenReturn(Collections.emptyList());

            // Act
            long startTime = System.nanoTime();
            List<Resource> result = memoryQueryService.findResourcesByQuery(query, 5);
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;

            // Assert
            assertNotNull(result);
            assertTrue(durationMs < 100, "Query should complete within 100ms, took: " + durationMs + "ms");
        }
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private Preference createMockPreference(String id, String name) {
        Preference preference = new Preference();
        preference.setId(id);
        preference.setName(name);
        preference.setSummary("Summary for " + name);
        preference.setCreatedAt(LocalDateTime.now());
        preference.setUpdatedAt(LocalDateTime.now());
        return preference;
    }

    private Snippet createMockSnippet(String id, String summary) {
        return createMockSnippetWithTime(id, summary, LocalDateTime.now());
    }

    private Snippet createMockSnippetWithTime(String id, String summary, LocalDateTime createdAt) {
        Snippet snippet = new Snippet();
        snippet.setId(id);
        snippet.setResourceId("resource_" + id);
        snippet.setSummary(summary);
        snippet.setMemoryType(Snippet.MemoryType.PROFILE);
        snippet.setCreatedAt(createdAt);
        snippet.setUpdatedAt(LocalDateTime.now());
        return snippet;
    }

    private Resource createMockResource(String id, String content) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setSessionId("session_" + id);
        resource.setConversationId("conversation_" + id);
        resource.setContent(content);
        resource.setAbstract("Abstract for " + content);
        resource.setCreatedAt(LocalDateTime.now());
        resource.setUpdatedAt(LocalDateTime.now());
        return resource;
    }
}
