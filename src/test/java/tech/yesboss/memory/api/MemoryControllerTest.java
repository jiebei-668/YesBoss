package tech.yesboss.memory.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.memory.model.*;
import tech.yesboss.memory.query.MemoryQueryService;
import tech.yesboss.memory.service.MemoryService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Unit Tests for MemoryController API Layer
 * 
 * Test Categories:
 * 1. Test Data Preparation (10 tests)
 * 2. Interface Contract Validation (8 tests)
 * 3. Normal Functionality Tests (12 tests)
 * 4. Performance Benchmarks (4 tests)
 */
@DisplayName("MemoryController API Layer Tests")
class MemoryControllerTest {

    @Mock
    private MemoryService memoryService;

    @Mock
    private MemoryQueryService memoryQueryService;

    @Mock
    private MemoryApiConfig config;

    private MemoryControllerImpl controller;

    private AutoCloseable closeable;

    // ==================== Test Data Preparation ====================

    @Nested
    @DisplayName("Test Data Preparation")
    class TestDataPreparation {

        @Test
        @DisplayName("Should create valid ExtractRequest with normal data")
        void shouldCreateValidExtractRequest() {
            List<UnifiedMessage> messages = createTestMessages(5);
            MemoryController.ExtractRequest request = 
                new MemoryController.ExtractRequest(messages, "conv-123", "session-456");

            assertNotNull(request);
            assertEquals(5, request.messages().size());
            assertEquals("conv-123", request.conversationId());
            assertEquals("session-456", request.sessionId());
        }

        @Test
        @DisplayName("Should create valid QueryRequest with normal data")
        void shouldCreateValidQueryRequest() {
            MemoryController.QueryRequest request = 
                new MemoryController.QueryRequest("test query", 10);

            assertNotNull(request);
            assertEquals("test query", request.query());
            assertEquals(10, request.topK());
        }

        @Test
        @DisplayName("Should reject empty messages list")
        void shouldRejectEmptyMessagesList() {
            List<UnifiedMessage> messages = List.of();
            assertThrows(IllegalArgumentException.class, () ->
                new MemoryController.ExtractRequest(messages, "conv-123", "session-456"));
        }

        @Test
        @DisplayName("Should create boundary data with single item")
        void shouldCreateBoundaryDataWithSingleItem() {
            List<UnifiedMessage> messages = createTestMessages(1);
            MemoryController.ExtractRequest request = 
                new MemoryController.ExtractRequest(messages, "conv-123", "session-456");

            assertEquals(1, request.messages().size());
        }

        @Test
        @DisplayName("Should create large dataset (1000 messages)")
        void shouldCreateLargeDataset() {
            List<UnifiedMessage> messages = createTestMessages(1000);
            MemoryController.ExtractRequest request = 
                new MemoryController.ExtractRequest(messages, "conv-123", "session-456");

            assertEquals(1000, request.messages().size());
        }

        @Test
        @DisplayName("Should create data with special characters")
        void shouldCreateDataWithSpecialCharacters() {
            MemoryController.QueryRequest request = 
                new MemoryController.QueryRequest("测试查询 with 特殊字符!@#$%", 10);

            assertEquals("测试查询 with 特殊字符!@#$%", request.query());
        }

        @Test
        @DisplayName("Should reject null messages")
        void shouldRejectNullMessages() {
            assertThrows(IllegalArgumentException.class, () ->
                new MemoryController.ExtractRequest(null, "conv-123", "session-456"));
        }

        @Test
        @DisplayName("Should reject blank conversation ID")
        void shouldRejectBlankConversationId() {
            assertThrows(IllegalArgumentException.class, () ->
                new MemoryController.ExtractRequest(createTestMessages(1), "", "session-456"));
        }

        @Test
        @DisplayName("Should reject zero topK")
        void shouldRejectZeroTopK() {
            assertThrows(IllegalArgumentException.class, () -> 
                new MemoryController.QueryRequest("test", 0));
        }

        @Test
        @DisplayName("Should reject negative topK")
        void shouldRejectNegativeTopK() {
            assertThrows(IllegalArgumentException.class, () -> 
                new MemoryController.QueryRequest("test", -1));
        }
    }

    // ==================== Interface Contract Validation ====================

    @Nested
    @DisplayName("Interface Contract Validation")
    class InterfaceContractValidation {

        @BeforeEach
        void setUpController() {
            closeable = MockitoAnnotations.openMocks(this);
            when(config.getDefaultTopK()).thenReturn(10);
            when(config.getMaxTopK()).thenReturn(100);
            when(config.getMaxRetryAttempts()).thenReturn(3);
            controller = new MemoryControllerImpl(memoryService, memoryQueryService, config);
        }

        @Test
        @DisplayName("Should verify extractFromMessages method signature")
        void shouldVerifyExtractFromMessagesSignature() {
            MemoryController.ExtractRequest request = 
                new MemoryController.ExtractRequest(createTestMessages(5), "conv-123", "session-456");
            Resource resource = new Resource("conv-123", "session-456", "content");
            List<Resource> resources = List.of(resource);
            when(memoryService.extractFromMessages(any(), any(), any())).thenReturn(resources);

            MemoryController.ExtractResponse response = controller.extractFromMessages(request);

            assertNotNull(response);
            assertEquals(1, response.resources().size());
        }

        @Test
        @DisplayName("Should verify queryAgenticRAG method signature")
        void shouldVerifyQueryAgenticRAGSignature() {
            MemoryController.QueryRequest request = 
                new MemoryController.QueryRequest("test query", 10);
            AgenticRagResult result = createTestAgenticRagResult();
            when(memoryQueryService.queryMemory(any(), anyInt())).thenReturn(result);

            AgenticRagResult response = controller.queryAgenticRAG(request);

            assertNotNull(response);
        }

        @Test
        @DisplayName("Should verify parameter types for ExtractRequest")
        void shouldVerifyExtractRequestParameterTypes() {
            List<UnifiedMessage> messages = createTestMessages(5);
            MemoryController.ExtractRequest request = 
                new MemoryController.ExtractRequest(messages, "conv-123", "session-456");

            assertInstanceOf(List.class, request.messages());
            assertInstanceOf(String.class, request.conversationId());
            assertInstanceOf(String.class, request.sessionId());
        }

        @Test
        @DisplayName("Should verify return type for ExtractResponse")
        void shouldVerifyExtractResponseReturnType() {
            MemoryController.ExtractRequest request = 
                new MemoryController.ExtractRequest(createTestMessages(5), "conv-123", "session-456");
            Resource resource = new Resource("conv-123", "session-456", "content");
            when(memoryService.extractFromMessages(any(), any(), any()))
                .thenReturn(List.of(resource));

            MemoryController.ExtractResponse response = controller.extractFromMessages(request);

            assertInstanceOf(MemoryController.ExtractResponse.class, response);
            assertInstanceOf(List.class, response.resources());
            assertInstanceOf(Integer.class, response.totalCount());
            assertInstanceOf(Long.class, response.processingTimeMs());
        }

        @Test
        @DisplayName("Should verify exception throwing for invalid input")
        void shouldVerifyExceptionThrowing() {
            MemoryController.ExtractRequest request = 
                new MemoryController.ExtractRequest(createTestMessages(5), "", "session-456");

            assertThrows(MemoryApiException.class, () -> controller.extractFromMessages(request));
        }

        @Test
        @DisplayName("Should verify health check method signature")
        void shouldVerifyHealthCheckSignature() {
            when(memoryService.isAvailable()).thenReturn(true);

            MemoryController.HealthResponse response = controller.health();

            assertNotNull(response);
            assertTrue(response.healthy());
        }

        @Test
        @DisplayName("Should verify metrics method signature")
        void shouldVerifyMetricsSignature() {
            MemoryController.MetricsResponse response = controller.metrics();

            assertNotNull(response);
            assertInstanceOf(Long.class, response.totalExtractions());
            assertInstanceOf(Long.class, response.totalQueries());
            assertInstanceOf(Double.class, response.successRate());
        }
    }

    // ==================== Normal Functionality Tests ====================

    @Nested
    @DisplayName("Normal Functionality Tests")
    class NormalFunctionalityTests {

        @BeforeEach
        void setUpController() {
            closeable = MockitoAnnotations.openMocks(this);
            when(config.getDefaultTopK()).thenReturn(10);
            when(config.getMaxTopK()).thenReturn(100);
            when(config.getMaxRetryAttempts()).thenReturn(3);
            controller = new MemoryControllerImpl(memoryService, memoryQueryService, config);
        }

        @Test
        @DisplayName("Should extract memories from messages successfully")
        void shouldExtractMemoriesSuccessfully() {
            MemoryController.ExtractRequest request = 
                new MemoryController.ExtractRequest(createTestMessages(5), "conv-123", "session-456");
            
            List<Resource> resources = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                resources.add(new Resource("conv-123", "session-456", "content-" + i));
            }
            
            when(memoryService.extractFromMessages(any(), any(), any())).thenReturn(resources);

            MemoryController.ExtractResponse response = controller.extractFromMessages(request);

            assertEquals(3, response.totalCount());
            verify(memoryService, times(1)).extractFromMessages(any(), any(), any());
        }

        @Test
        @DisplayName("Should batch extract memories successfully")
        void shouldBatchExtractMemoriesSuccessfully() {
            List<MemoryService.MessageBatch> batches = List.of(
                new MemoryService.MessageBatch(createTestMessages(5), "conv-1", "session-1"),
                new MemoryService.MessageBatch(createTestMessages(5), "conv-2", "session-2")
            );
            MemoryController.BatchExtractRequest request = 
                new MemoryController.BatchExtractRequest(batches);
            
            List<Resource> resources = List.of(
                new Resource("conv-1", "session-1", "content-1"),
                new Resource("conv-2", "session-2", "content-2")
            );
            
            when(memoryService.batchExtractFromMessages(any())).thenReturn(resources);

            MemoryController.BatchExtractResponse response = controller.batchExtractFromMessages(request);

            assertNotNull(response);
            assertEquals(2, response.batchCount());
        }

        @Test
        @DisplayName("Should process pending resources successfully")
        void shouldProcessPendingResourcesSuccessfully() {
            when(memoryService.processPendingResources()).thenReturn(5);

            MemoryController.ProcessPendingResponse response = controller.processPendingResources();

            assertEquals(5, response.processedCount());
        }

        @Test
        @DisplayName("Should query AgenticRAG successfully")
        void shouldQueryAgenticRAGSuccessfully() {
            MemoryController.QueryRequest request = 
                new MemoryController.QueryRequest("test query", 10);
            AgenticRagResult result = createTestAgenticRagResult();
            when(memoryQueryService.queryMemory(any(), anyInt())).thenReturn(result);

            AgenticRagResult response = controller.queryAgenticRAG(request);

            assertNotNull(response);
            verify(memoryQueryService, times(1)).queryMemory("test query", 10);
        }

        @Test
        @DisplayName("Should find resources by query successfully")
        void shouldFindResourcesByQuerySuccessfully() {
            MemoryController.QueryRequest request = 
                new MemoryController.QueryRequest("test query", 10);
            Resource resource = new Resource("conv-123", "session-456", "content");
            List<Resource> resources = List.of(resource);
            when(memoryQueryService.findResourcesByQuery(any(), anyInt())).thenReturn(resources);

            List<Resource> response = controller.findResourcesByQuery(request);

            assertNotNull(response);
            assertEquals(1, response.size());
        }

        @Test
        @DisplayName("Should find snippets by query successfully")
        void shouldFindSnippetsByQuerySuccessfully() {
            MemoryController.QueryRequest request = 
                new MemoryController.QueryRequest("test query", 10);
            
            Snippet snippet = new Snippet();
            snippet.setResourceUuid("resource-123");
            snippet.setSummary("Test snippet");
            snippet.setMemoryType(Snippet.MemoryType.PROFILE);
            List<Snippet> snippets = List.of(snippet);
            
            when(memoryQueryService.findSnippetsByQuery(any(), anyInt())).thenReturn(snippets);

            List<Snippet> response = controller.findSnippetsByQuery(request);

            assertNotNull(response);
            assertEquals(1, response.size());
        }

        @Test
        @DisplayName("Should find preferences by query successfully")
        void shouldFindPreferencesByQuerySuccessfully() {
            MemoryController.QueryRequest request = 
                new MemoryController.QueryRequest("test query", 10);
            
            Preference preference = new Preference();
            preference.setName("Test Preference");
            preference.setSummary("Test summary");
            List<Preference> preferences = List.of(preference);
            
            when(memoryQueryService.findPreferencesByQuery(any(), anyInt())).thenReturn(preferences);

            List<Preference> response = controller.findPreferencesByQuery(request);

            assertNotNull(response);
            assertEquals(1, response.size());
        }

        @Test
        @DisplayName("Should semantic search successfully")
        void shouldSemanticSearchSuccessfully() {
            MemoryController.SemanticSearchRequest request = 
                new MemoryController.SemanticSearchRequest("test query", null, null, null, 10);
            
            Snippet snippet = new Snippet();
            snippet.setResourceUuid("resource-123");
            snippet.setSummary("Test snippet");
            snippet.setMemoryType(Snippet.MemoryType.PROFILE);
            List<Snippet> snippets = List.of(snippet);
            
            when(memoryQueryService.semanticSearch(any(), any(), anyInt())).thenReturn(snippets);

            List<Snippet> response = controller.semanticSearch(request);

            assertNotNull(response);
        }

        @Test
        @DisplayName("Should hybrid search successfully")
        void shouldHybridSearchSuccessfully() {
            MemoryController.HybridSearchRequest request = 
                new MemoryController.HybridSearchRequest("test query", null, 10);
            
            Snippet snippet = new Snippet();
            snippet.setResourceUuid("resource-123");
            snippet.setSummary("Test snippet");
            snippet.setMemoryType(Snippet.MemoryType.PROFILE);
            List<Snippet> snippets = List.of(snippet);
            
            when(memoryQueryService.hybridSearch(any(), any(), anyInt())).thenReturn(snippets);

            List<Snippet> response = controller.hybridSearch(request);

            assertNotNull(response);
        }

        @Test
        @DisplayName("Should process batch embedding successfully")
        void shouldProcessBatchEmbeddingSuccessfully() {
            MemoryService.BatchEmbeddingResult result = new MemoryService.BatchEmbeddingResult(
                10, 20, 5, 35, 0, List.of(), 100
            );
            when(memoryService.processBatchEmbedding()).thenReturn(result);

            MemoryService.BatchEmbeddingResult response = controller.processBatchEmbedding();

            assertNotNull(response);
            assertEquals(35, response.getTotalCount());
        }

        @Test
        @DisplayName("Should update preference summary successfully")
        void shouldUpdatePreferenceSummarySuccessfully() {
            String preferenceId = "pref-123";
            
            Snippet snippet = new Snippet();
            snippet.setResourceUuid("resource-123");
            snippet.setSummary("Test snippet");
            snippet.setMemoryType(Snippet.MemoryType.PROFILE);
            List<Snippet> snippets = List.of(snippet);
            
            MemoryController.UpdatePreferenceSummaryRequest request = 
                new MemoryController.UpdatePreferenceSummaryRequest(snippets);
            doNothing().when(memoryService).updatePreferenceSummary(any(), any());

            assertDoesNotThrow(() -> controller.updatePreferenceSummary(preferenceId, request));

            verify(memoryService, times(1)).updatePreferenceSummary(eq(preferenceId), eq(snippets));
        }
    }

    // ==================== Performance Benchmark Tests ====================

    @Nested
    @DisplayName("Performance Benchmark Tests")
    class PerformanceBenchmarkTests {

        @BeforeEach
        void setUpController() {
            closeable = MockitoAnnotations.openMocks(this);
            when(config.getDefaultTopK()).thenReturn(10);
            when(config.getMaxTopK()).thenReturn(100);
            when(config.getMaxRetryAttempts()).thenReturn(3);
            controller = new MemoryControllerImpl(memoryService, memoryQueryService, config);
        }

        @Test
        @DisplayName("Should complete extractFromMessages within 100ms")
        void shouldCompleteExtractFromMessagesWithin100ms() {
            MemoryController.ExtractRequest request = 
                new MemoryController.ExtractRequest(createTestMessages(5), "conv-123", "session-456");
            Resource resource = new Resource("conv-123", "session-456", "content");
            when(memoryService.extractFromMessages(any(), any(), any()))
                .thenReturn(List.of(resource));

            long startTime = System.currentTimeMillis();
            controller.extractFromMessages(request);
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 100, "Operation took " + duration + "ms, expected <100ms");
        }

        @Test
        @DisplayName("Should complete queryAgenticRAG within 100ms")
        void shouldCompleteQueryAgenticRAGWithin100ms() {
            MemoryController.QueryRequest request = 
                new MemoryController.QueryRequest("test", 10);
            when(memoryQueryService.queryMemory(any(), anyInt()))
                .thenReturn(createTestAgenticRagResult());

            long startTime = System.currentTimeMillis();
            controller.queryAgenticRAG(request);
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 100, "Operation took " + duration + "ms, expected <100ms");
        }

        @Test
        @DisplayName("Should complete batch processing within 1s for 100 items")
        void shouldCompleteBatchProcessingWithin1sFor100Items() {
            List<MemoryService.MessageBatch> batches = List.of(
                new MemoryService.MessageBatch(createTestMessages(100), "conv-1", "session-1")
            );
            MemoryController.BatchExtractRequest request = 
                new MemoryController.BatchExtractRequest(batches);
            
            List<Resource> resources = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                resources.add(new Resource("conv-1", "session-1", "content-" + i));
            }
            
            when(memoryService.batchExtractFromMessages(any())).thenReturn(resources);

            long startTime = System.currentTimeMillis();
            controller.batchExtractFromMessages(request);
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 1000, "Operation took " + duration + "ms, expected <1000ms");
        }

        @Test
        @DisplayName("Should use less than 512MB memory")
        void shouldUseLessThan512MBMemory() {
            Runtime runtime = Runtime.getRuntime();
            long initialMemory = runtime.totalMemory() - runtime.freeMemory();

            // Perform multiple operations
            for (int i = 0; i < 100; i++) {
                MemoryController.ExtractRequest request = new MemoryController.ExtractRequest(
                    createTestMessages(5), "conv-" + i, "session-" + i
                );
                Resource resource = new Resource("conv-" + i, "session-" + i, "content");
                when(memoryService.extractFromMessages(any(), any(), any()))
                    .thenReturn(List.of(resource));
                controller.extractFromMessages(request);
            }

            long finalMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = (finalMemory - initialMemory) / (1024 * 1024);

            assertTrue(memoryUsed < 512, "Memory used: " + memoryUsed + "MB, expected <512MB");
        }
    }

    // ==================== Helper Methods ====================

    private List<UnifiedMessage> createTestMessages(int count) {
        List<UnifiedMessage> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            messages.add(new UnifiedMessage(
                "user-" + i,
                "test message content " + i,
                LocalDateTime.now(),
                "conv-123",
                i
            ));
        }
        return messages;
    }

    private AgenticRagResult createTestAgenticRagResult() {
        Preference pref = new Preference();
        pref.setName("Test Pref");
        pref.setSummary("Test summary");
        
        Snippet snippet = new Snippet();
        snippet.setResourceUuid("resource-123");
        snippet.setSummary("Test snippet");
        snippet.setMemoryType(Snippet.MemoryType.PROFILE);
        
        Resource resource = new Resource("conv-123", "session-456", "content");
        
        DecisionLog decision = new DecisionLog();
        decision.setQuery("test-query");
        decision.setShouldContinue(true);
        decision.setReason("test-reason");
        
        return new AgenticRagResult(
            List.of(pref),
            List.of(snippet),
            List.of(resource),
            List.of(decision),
            List.of("step1", "step2")
        );
    }
}
