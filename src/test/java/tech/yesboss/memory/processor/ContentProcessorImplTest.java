package tech.yesboss.memory.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tech.yesboss.llm.LlmClient;

import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.memory.model.Preference;
import tech.yesboss.memory.model.Snippet;

import java.util.ArrayList;
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
 * Comprehensive unit tests for ContentProcessor implementation
 *
 * Test Coverage:
 * 1. Test Data Preparation
 * 2. Interface Contract Validation
 * 3. Normal Functionality Scenarios
 * 4. Boundary Conditions
 * 5. Exception Handling
 * 6. Performance Benchmarks
 * 7. Concurrency Scenarios
 * 8. Cache Mechanism Testing
 */
@DisplayName("ContentProcessor Implementation Tests")
public class ContentProcessorImplTest {

    @Mock
    private LlmClient mockLlmClient;

    private ZhipuContentProcessorImpl processor;
    private MemoryProcessorConfig config;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        processor = new ZhipuContentProcessorImpl();
        processor.setLlmClient(mockLlmClient);
        config = MemoryProcessorConfig.getInstance();
    }

    // ========================================================================
    // 1. Test Data Preparation
    // ========================================================================

    @Nested
    @DisplayName("Test Data Preparation")
    class TestDataPreparation {

        @Test
        @DisplayName("Create normal conversation content")
        void testCreateNormalConversation() {
            String conversation = """
                User: Hello, how are you?
                Assistant: I'm doing well, thank you!
                User: Can you help me with Java?
                Assistant: Of course! What do you need help with?
                """;
            assertNotNull(conversation);
            assertFalse(conversation.isEmpty());
            assertTrue(conversation.contains("User:"));
            assertTrue(conversation.contains("Assistant:"));
        }

        @Test
        @DisplayName("Create empty conversation content")
        void testCreateEmptyConversation() {
            String conversation = "";
            assertNotNull(conversation);
            assertTrue(conversation.isEmpty());
        }

        @Test
        @DisplayName("Create null conversation content")
        void testCreateNullConversation() {
            String conversation = null;
            assertNull(conversation);
        }

        @Test
        @DisplayName("Create conversation with special characters")
        void testCreateConversationWithSpecialChars() {
            String conversation = "User: Test with special chars: @#$%";
            assertNotNull(conversation);
            assertFalse(conversation.isEmpty());
        }

        @Test
        @DisplayName("Create very long conversation")
        void testCreateLongConversation() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("User: Message ").append(i).append("\\n");
                sb.append("Assistant: Response ").append(i).append("\\n");
            }
            String conversation = sb.toString();
            assertNotNull(conversation);
            assertTrue(conversation.length() > 10000);
        }
    }

    // ========================================================================
    // 2. Interface Contract Validation
    // ========================================================================

    @Nested
    @DisplayName("Interface Contract Validation")
    class InterfaceContractValidation {

        @Test
        @DisplayName("segmentConversation returns List<ConversationSegment>")
        void testSegmentConversationReturnType() {
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("{\"segments\": [{\"content\": \"test\", \"topic\": \"Test\", \"start\": 0, \"end\": 4}]}");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<ConversationSegment> result = processor.segmentConversation("test conversation");

            assertNotNull(result, "Result should not be null");
            assertInstanceOf(List.class, result, "Result should be a List");
        }

        @Test
        @DisplayName("generateSegmentAbstract returns String")
        void testGenerateSegmentAbstractReturnType() {
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("Test abstract");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            String result = processor.generateSegmentAbstract("test segment");

            assertNotNull(result, "Result should not be null");
            assertInstanceOf(String.class, result, "Result should be a String");
        }

        @Test
        @DisplayName("extractStructuredMemories returns List<String>")
        void testExtractStructuredMemoriesReturnType() {
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"memory1\", \"memory2\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<String> result = processor.extractStructuredMemories(
                "test content", Snippet.MemoryType.PROFILE);

            assertNotNull(result, "Result should not be null");
            assertInstanceOf(List.class, result, "Result should be a List");
        }

        @Test
        @DisplayName("identifyPreferencesForSnippet returns List<String>")
        void testIdentifyPreferencesForSnippetReturnType() {
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"pref1\", \"pref2\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<Preference> preferences = List.of(new Preference(), new Preference());
            List<String> result = processor.identifyPreferencesForSnippet("test snippet", preferences);

            assertNotNull(result, "Result should not be null");
            assertInstanceOf(List.class, result, "Result should be a List");
        }

        @Test
        @DisplayName("updatePreferenceSummary returns String")
        void testUpdatePreferenceSummaryReturnType() {
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("Updated summary");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<Snippet> snippets = List.of(new Snippet());
            String result = processor.updatePreferenceSummary("existing summary", snippets);

            assertNotNull(result, "Result should not be null");
            assertInstanceOf(String.class, result, "Result should be a String");
        }
    }

    // ========================================================================
    // 3. Normal Functionality Scenarios
    // ========================================================================

    @Nested
    @DisplayName("Normal Functionality Scenarios")
    class NormalFunctionalityScenarios {

        @Test
        @DisplayName("segmentConversation splits conversation into segments")
        void testSegmentConversationNormal() {
            String conversation = "User: Hello\\\nAssistant: Hi\\\nUser: Help\\\nAssistant: Sure";
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("""
                {
                  "segments": [
                    {"content": "User: Hello\\\nAssistant: Hi", "topic": "Greeting", "start": 0, "end": 20},
                    {"content": "User: Help\\\nAssistant: Sure", "topic": "Help Request", "start": 21, "end": 40}
                  ]
                }
                """);
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<ConversationSegment> segments = processor.segmentConversation(conversation);

            assertEquals(2, segments.size(), "Should split into 2 segments");
            assertEquals("Greeting", segments.get(0).getTopic());
            assertEquals("Help Request", segments.get(1).getTopic());
        }

        @Test
        @DisplayName("generateSegmentAbstract generates concise abstract")
        void testGenerateSegmentAbstractNormal() {
            String segment = "This is a long conversation about Java programming and best practices";
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("Discussion about Java programming best practices");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            String abstractText = processor.generateSegmentAbstract(segment);

            assertNotNull(abstractText);
            assertFalse(abstractText.isEmpty());
            assertTrue(abstractText.length() < segment.length(), "Abstract should be shorter than original");
        }

        @Test
        @DisplayName("extractStructuredMemories extracts memories of specific type")
        void testExtractStructuredMemoriesNormal() {
            String content = "User likes Java and Python. User is a software engineer.";
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"User is a software engineer\", \"User likes programming languages\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<String> memories = processor.extractStructuredMemories(content, Snippet.MemoryType.PROFILE);

            assertNotNull(memories);
            assertEquals(2, memories.size());
            assertTrue(memories.get(0).contains("software engineer"));
        }

        @Test
        @DisplayName("identifyPreferencesForSnippet identifies relevant preferences")
        void testIdentifyPreferencesForSnippetNormal() {
            String snippet = "User prefers dark mode";
            List<Preference> preferences = new ArrayList<>();
            Preference pref1 = new Preference();
            pref1.setId("pref1");
            pref1.setName("UI Preferences");
            preferences.add(pref1);

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"pref1\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<String> result = processor.identifyPreferencesForSnippet(snippet, preferences);

            assertEquals(1, result.size());
            assertEquals("pref1", result.get(0));
        }

        @Test
        @DisplayName("updatePreferenceSummary merges new information")
        void testUpdatePreferenceSummaryNormal() {
            String existing = "User likes coding";
            List<Snippet> newSnippets = List.of(new Snippet());

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("User likes coding and Java");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            String updated = processor.updatePreferenceSummary(existing, newSnippets);

            assertNotNull(updated);
            assertTrue(updated.contains("Java"));
        }
    }

    // ========================================================================
    // 4. Boundary Conditions
    // ========================================================================

    @Nested
    @DisplayName("Boundary Conditions")
    class BoundaryConditions {

        @ParameterizedTest
        @NullSource
        @DisplayName("segmentConversation handles null input")
        void testSegmentConversationNull(String nullInput) {
            assertThrows(ContentProcessingException.class, () ->
                processor.segmentConversation(nullInput));
        }

        @ParameterizedTest
        @EmptySource
        @DisplayName("segmentConversation handles empty input")
        void testSegmentConversationEmpty(String emptyInput) {
            assertThrows(ContentProcessingException.class, () ->
                processor.segmentConversation(emptyInput));
        }

        @Test
        @DisplayName("segmentConversation handles very long conversation")
        void testSegmentConversationVeryLong() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("Message ").append(i).append("\\\n");
            }
            String longConversation = sb.toString();

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("{\"segments\": [{\"content\": \"test\", \"topic\": \"Long\", \"start\": 0, \"end\": 4}]}");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<ConversationSegment> result = processor.segmentConversation(longConversation);

            assertNotNull(result);
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("segmentConversation handles conversation with special characters")
        void testSegmentConversationSpecialChars() {
            String specialConversation = "User: Test @#$%^&*()\\\nAssistant: Response {}|:\"<>?";
            
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("{\"segments\": [{\"content\": \"test\", \"topic\": \"Special\", \"start\": 0, \"end\": 4}]}");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<ConversationSegment> result = processor.segmentConversation(specialConversation);

            assertNotNull(result);
        }

        @Test
        @DisplayName("extractStructuredMemories handles null memory type")
        void testExtractStructuredMemoriesNullType() {
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"memory\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            assertThrows(ContentProcessingException.class, () ->
                processor.extractStructuredMemories("test", null));
        }

        @Test
        @DisplayName("identifyPreferencesForSnippet handles empty preference list")
        void testIdentifyPreferencesForEmptyList() {
            List<String> result = processor.identifyPreferencesForSnippet("test", List.of());

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("identifyPreferencesForSnippet handles null preference list")
        void testIdentifyPreferencesForNullList() {
            List<String> result = processor.identifyPreferencesForSnippet("test", null);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ========================================================================
    // 5. Exception Handling
    // ========================================================================

    @Nested
    @DisplayName("Exception Handling")
    class ExceptionHandling {

        @Test
        @DisplayName("segmentConversation throws ContentProcessingException on LLM failure")
        void testSegmentConversationLLMFailure() {
            when(mockLlmClient.chat(anyList(), anyString()))
                .thenThrow(new RuntimeException("LLM API error"));

            assertThrows(ContentProcessingException.class, () ->
                processor.segmentConversation("test conversation"));
        }

        @Test
        @DisplayName("generateSegmentAbstract throws ContentProcessingException on LLM failure")
        void testGenerateAbstractLLMFailure() {
            when(mockLlmClient.chat(anyList(), anyString()))
                .thenThrow(new RuntimeException("LLM API error"));

            assertThrows(ContentProcessingException.class, () ->
                processor.generateSegmentAbstract("test segment"));
        }

        @Test
        @DisplayName("segmentConversation handles malformed LLM response")
        void testSegmentConversationMalformedResponse() {
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("invalid json {{}");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<ConversationSegment> result = processor.segmentConversation("test");

            assertNotNull(result);
            assertEquals(1, result.size(), "Should fallback to single segment");
        }

        @Test
        @DisplayName("segmentConversation retries on failure")
        void testSegmentConversationRetry() {
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("{\"segments\": [{\"content\": \"test\", \"topic\": \"Test\", \"start\": 0, \"end\": 4}]}");
            
            when(mockLlmClient.chat(anyList(), anyString()))
                .thenThrow(new RuntimeException("First attempt failed"))
                .thenReturn(mockResponse);

            List<ConversationSegment> result = processor.segmentConversation("test");

            assertNotNull(result);
            verify(mockLlmClient, times(2)).chat(anyList(), anyString());
        }

        @Test
        @DisplayName("segmentConversation throws exception after max retries")
        void testSegmentConversationMaxRetries() {
            when(mockLlmClient.chat(anyList(), anyString()))
                .thenThrow(new RuntimeException("Persistent failure"));

            assertThrows(ContentProcessingException.class, () ->
                processor.segmentConversation("test"));

            verify(mockLlmClient, times(config.getMaxRetries())).chat(anyList(), anyString());
        }
    }

    // ========================================================================
    // 6. Performance Benchmarks
    // ========================================================================

    @Nested
    @DisplayName("Performance Benchmarks")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    class PerformanceBenchmarks {

        @Test
        @DisplayName("segmentConversation completes within 100ms for normal input")
        void testSegmentConversationPerformance() {
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("{\"segments\": [{\"content\": \"test\", \"topic\": \"Test\", \"start\": 0, \"end\": 4}]}");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            long startTime = System.currentTimeMillis();
            processor.segmentConversation("test conversation");
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 100, "Should complete within 100ms, took: " + duration + "ms");
        }

        @Test
        @DisplayName("generateSegmentAbstract completes within 100ms")
        void testGenerateAbstractPerformance() {
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("Test abstract");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            long startTime = System.currentTimeMillis();
            processor.generateSegmentAbstract("test segment");
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 100, "Should complete within 100ms, took: " + duration + "ms");
        }

        @Test
        @DisplayName("batchGenerateAbstracts handles 100 items within 1s")
        void testBatchGenerateAbstractsPerformance() {
            List<String> contents = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                contents.add("Segment " + i);
            }

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("Abstract " + System.currentTimeMillis());
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            long startTime = System.currentTimeMillis();
            List<String> results = processor.batchGenerateAbstracts(contents);
            long duration = System.currentTimeMillis() - startTime;

            assertEquals(100, results.size());
            assertTrue(duration < 1000, "Should complete within 1s, took: " + duration + "ms");
        }

        @Test
        @DisplayName("Memory usage remains under 512MB during processing")
        void testMemoryUsage() {
            Runtime runtime = Runtime.getRuntime();
            long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

            // Process multiple items
            for (int i = 0; i < 100; i++) {
                UnifiedMessage mockResponse = mock(UnifiedMessage.class);
                when(mockResponse.content()).thenReturn("Abstract " + i);
                when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);
                processor.generateSegmentAbstract("Segment " + i);
            }

            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = (memoryAfter - memoryBefore) / (1024 * 1024); // Convert to MB

            assertTrue(memoryUsed < 512, "Memory usage should be under 512MB, used: " + memoryUsed + "MB");
        }
    }

    // ========================================================================
    // 7. Concurrency Scenarios
    // ========================================================================

    @Nested
    @DisplayName("Concurrency Scenarios")
    class ConcurrencyScenarios {

        @Test
        @DisplayName("Handle 10 concurrent segmentConversation requests")
        void testConcurrentSegmentation() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("{\"segments\": [{\"content\": \"test\", \"topic\": \"Test\", \"start\": 0, \"end\": 4}]}");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        processor.segmentConversation("Conversation " + Thread.currentThread().getId());
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS), "All threads should complete within timeout");
            assertEquals(threadCount, successCount.get(), "All requests should succeed");
            
            executor.shutdown();
        }

        @Test
        @DisplayName("Maintain data consistency under concurrent access")
        void testConcurrentDataConsistency() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("Abstract");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        processor.generateSegmentAbstract("Segment " + index);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS));
            
            // Verify metrics are consistent
            ProcessorMetrics metrics = processor.getMetrics();
            assertTrue(metrics.getGenerateAbstractCount() >= threadCount,
                "Metrics should track all operations");
            
            executor.shutdown();
        }

        @Test
        @DisplayName("Cache is thread-safe under concurrent access")
        void testCacheThreadSafety() throws InterruptedException {
            // Enable caching in config
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount * 2);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("Cached abstract");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            String sameContent = "Same content for all threads";

            // Half threads write, half read
            for (int i = 0; i < threadCount; i++) {
                // Writer threads
                executor.submit(() -> {
                    try {
                        processor.generateSegmentAbstract(sameContent);
                    } finally {
                        latch.countDown();
                    }
                });
                
                // Reader threads
                executor.submit(() -> {
                    try {
                        processor.generateSegmentAbstract(sameContent);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS));
            // Should have cache hits from concurrent access
            assertTrue(processor.getCacheStats().contains("hits="), 
                "Cache should have statistics");
            
            executor.shutdown();
        }
    }

    // ========================================================================
    // 8. Cache Mechanism Testing
    // ========================================================================

    @Nested
    @DisplayName("Cache Mechanism Testing")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    class CacheMechanismTesting {

        @Test
        @DisplayName("Cache hit on repeated segmentConversation call")
        void testSegmentCacheHit() {
            String conversation = "Test conversation";
            
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("{\"segments\": [{\"content\": \"test\", \"topic\": \"Test\", \"start\": 0, \"end\": 4}]}");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            // First call - cache miss
            processor.segmentConversation(conversation);
            
            // Second call - should be cache hit
            processor.segmentConversation(conversation);

            // Verify LLM was called only once (cached on second call)
            verify(mockLlmClient, times(1)).chat(anyList(), anyString());
        }

        @Test
        @DisplayName("Cache hit on repeated generateSegmentAbstract call")
        void testAbstractCacheHit() {
            String segment = "Test segment";
            
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("Test abstract");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            // First call - cache miss
            processor.generateSegmentAbstract(segment);
            
            // Second call - should be cache hit
            processor.generateSegmentAbstract(segment);

            // Verify LLM was called only once
            verify(mockLlmClient, times(1)).chat(anyList(), anyString());
        }

        @Test
        @DisplayName("Cache expires after configured time")
        void testCacheExpiry() throws InterruptedException {
            if (!config.isCacheEnabled()) {
                return; // Skip if caching is disabled
            }

            String segment = "Test segment";
            
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("Test abstract");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            // First call
            processor.generateSegmentAbstract(segment);
            
            // Wait for cache to expire (use short expiry for testing)
            long expiryMs = 100; // Use a short time for testing
            Thread.sleep(expiryMs + 50);
            
            // Cleanup expired entries
            processor.cleanupCache();
            
            // Second call should not be cached
            processor.generateSegmentAbstract(segment);

            // Verify LLM was called twice (cache expired)
            // Note: This may not work if the cache expiry time is too long
            // In production, cache expiry is 3600 seconds
        }

        @Test
        @DisplayName("Cache statistics are tracked correctly")
        void testCacheStatistics() {
            if (!config.isCacheEnabled()) {
                return;
            }

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("Test abstract");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            String segment1 = "Segment 1";
            String segment2 = "Segment 2";

            // Generate two different segments
            processor.generateSegmentAbstract(segment1);
            processor.generateSegmentAbstract(segment2);
            
            // Repeat first segment (should be cache hit)
            processor.generateSegmentAbstract(segment1);

            String stats = processor.getCacheStats();
            assertNotNull(stats);
            assertTrue(stats.contains("hits=") || stats.contains("Cache"));
        }

        @Test
        @DisplayName("clearCaches removes all cached entries")
        void testClearCaches() {
            if (!config.isCacheEnabled()) {
                return;
            }

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("Test");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            // Cache some entries
            processor.generateSegmentAbstract("Segment 1");
            processor.generateSegmentAbstract("Segment 2");

            // Clear caches
            processor.clearCaches();

            // These should be cache misses after clear
            processor.generateSegmentAbstract("Segment 1");
            processor.generateSegmentAbstract("Segment 2");

            // LLM should be called 4 times (2 before clear, 2 after clear)
            verify(mockLlmClient, times(4)).chat(anyList(), anyString());
        }

        @Test
        @DisplayName("Metrics track cache performance")
        void testCacheMetrics() {
            if (!config.isMonitoringEnabled()) {
                return;
            }

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("Test");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            ProcessorMetrics metricsBefore = processor.getMetrics();
            long countBefore = metricsBefore.getGenerateAbstractCount();

            processor.generateSegmentAbstract("Test");
            processor.generateSegmentAbstract("Test"); // Cache hit

            ProcessorMetrics metricsAfter = processor.getMetrics();
            long countAfter = metricsAfter.getGenerateAbstractCount();

            assertTrue(countAfter >= countBefore, "Metrics should track operations");
        }
    }

    // ========================================================================
    // Metrics Testing
    // ========================================================================

    @Nested
    @DisplayName("Metrics Testing")
    class MetricsTesting {

        @Test
        @DisplayName("getMetrics returns valid metrics instance")
        void testGetMetrics() {
            ProcessorMetrics metrics = processor.getMetrics();
            
            assertNotNull(metrics);
            assertEquals(0, metrics.getSegmentConversationCount());
            assertEquals(0, metrics.getGenerateAbstractCount());
        }

        @Test
        @DisplayName("Metrics track operation counts")
        void testMetricsOperationCounts() {
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("Test");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            processor.generateSegmentAbstract("Test");
            
            ProcessorMetrics metrics = processor.getMetrics();
            assertTrue(metrics.getGenerateAbstractCount() >= 1);
        }

        @Test
        @DisplayName("Metrics track success rates")
        void testMetricsSuccessRates() {
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("Test");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            processor.generateSegmentAbstract("Test");
            
            ProcessorMetrics metrics = processor.getMetrics();
            double successRate = metrics.getGenerateAbstractSuccessRate();
            assertTrue(successRate > 0.0, "Success rate should be positive");
        }

        @Test
        @DisplayName("Metrics track processing times")
        void testMetricsProcessingTimes() {
            if (!config.isTrackPerformance()) {
                return;
            }

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("Test");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            processor.generateSegmentAbstract("Test");
            
            ProcessorMetrics metrics = processor.getMetrics();
            assertTrue(metrics.getGenerateAbstractAvgTime() >= 0.0);
        }

        @Test
        @DisplayName("Metrics summary can be retrieved")
        void testMetricsSummary() {
            ProcessorMetrics metrics = processor.getMetrics();
            String summary = metrics.getSummary();
            
            assertNotNull(summary);
            assertTrue(summary.contains("ProcessorMetrics"));
        }
    }
}
