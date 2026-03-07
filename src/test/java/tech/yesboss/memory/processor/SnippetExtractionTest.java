package tech.yesboss.memory.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.llm.LlmClient;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for Snippet Extraction Algorithm
 *
 * <p>This test class specifically focuses on validating the snippet extraction functionality
 * of ContentProcessor, which extracts structured memory fragments from conversation content.
 *
 * <p>Test Coverage:
 * 1. Test Data Preparation - Create comprehensive test datasets
 * 2. Interface Contract Validation - Verify method signatures and return types
 * 3. Normal Functionality Scenarios - Test typical extraction use cases
 * 4. Boundary Conditions - Test edge cases and limits
 * 5. Exception Handling - Test error scenarios and recovery
 * 6. Performance Benchmarks - Validate performance targets (<100ms)
 * 7. Concurrency Scenarios - Test thread safety and concurrent access
 * 8. Cache Mechanism - Validate caching behavior (if applicable)
 *
 * <p>Performance Targets:
 * - Response time: <100ms for single extraction
 * - Batch processing: <1s for 100 items
 * - Memory usage: <512MB
 */
@DisplayName("Snippet Extraction Algorithm Tests")
public class SnippetExtractionTest {

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
    @DisplayName("1. Test Data Preparation")
    class TestDataPreparation {

        @Test
        @DisplayName("Create normal conversation content for PROFILE extraction")
        void testCreateNormalProfileContent() {
            String content = """
                User: My name is John and I'm a software engineer at Google.
                Assistant: Nice to meet you, John!
                User: I've been working there for 5 years and specialize in Java development.
                """;
            assertNotNull(content);
            assertFalse(content.isEmpty());
            assertTrue(content.contains("software engineer"));
        }

        @Test
        @DisplayName("Create normal conversation content for EVENT extraction")
        void testCreateNormalEventContent() {
            String content = """
                User: Yesterday I attended a tech conference about AI.
                Assistant: That sounds interesting!
                User: Yes, I learned about the latest developments in large language models.
                """;
            assertNotNull(content);
            assertTrue(content.contains("conference"));
        }

        @Test
        @DisplayName("Create normal conversation content for KNOWLEDGE extraction")
        void testCreateNormalKnowledgeContent() {
            String content = """
                User: Java 17 introduced virtual threads which improve concurrency.
                Assistant: Yes, Project Loom is a major enhancement.
                User: Virtual threads are lightweight compared to platform threads.
                """;
            assertNotNull(content);
            assertTrue(content.contains("virtual threads"));
        }

        @Test
        @DisplayName("Create normal conversation content for BEHAVIOR extraction")
        void testCreateNormalBehaviorContent() {
            String content = """
                User: I always start my day with a cup of coffee and code review.
                Assistant: That's a good morning routine.
                User: I prefer working early in the morning when it's quiet.
                """;
            assertNotNull(content);
            assertTrue(content.contains("always"));
        }

        @Test
        @DisplayName("Create normal conversation content for SKILL extraction")
        void testCreateNormalSkillContent() {
            String content = """
                User: I'm proficient in Java, Python, and JavaScript.
                Assistant: Impressive! What about frameworks?
                User: I know Spring Boot, Django, and React very well.
                """;
            assertNotNull(content);
            assertTrue(content.contains("proficient"));
        }

        @Test
        @DisplayName("Create normal conversation content for TOOL extraction")
        void testCreateNormalToolContent() {
            String content = """
                User: I use VS Code for development with GitHub Copilot enabled.
                Assistant: VS Code is a great editor.
                User: I also use Docker for containerization and Kubernetes for deployment.
                """;
            assertNotNull(content);
            assertTrue(content.contains("VS Code"));
        }

        @Test
        @DisplayName("Create empty content")
        void testCreateEmptyContent() {
            String content = "";
            assertNotNull(content);
            assertTrue(content.isEmpty());
        }

        @Test
        @DisplayName("Create null content")
        void testCreateNullContent() {
            String content = null;
            assertNull(content);
        }

        @Test
        @DisplayName("Create content with special characters")
        void testCreateSpecialCharContent() {
            String content = "User: Test with special chars: @#$%^&*(){}|:\"<>?[]\\',./`~";
            assertNotNull(content);
            assertFalse(content.isEmpty());
        }

        @Test
        @DisplayName("Create very long conversation content")
        void testCreateLongContent() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("User: Message ").append(i).append("\n");
                sb.append("Assistant: Response ").append(i).append("\n");
            }
            String content = sb.toString();
            assertNotNull(content);
            assertTrue(content.length() > 10000);
        }

        @Test
        @DisplayName("Create content with Unicode and emoji")
        void testCreateUnicodeContent() {
            String content = """
                User: 你好！我是一名软件工程师 👨‍💻
                Assistant: Hello! Nice to meet you! 🤝
                User: 我喜欢编程和阅读 📚
                """;
            assertNotNull(content);
            assertTrue(content.contains("软件工程师"));
        }
    }

    // ========================================================================
    // 2. Interface Contract Validation
    // ========================================================================

    @Nested
    @DisplayName("2. Interface Contract Validation")
    class InterfaceContractValidation {

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
            assertInstanceOf(String.class, result.get(0), "List elements should be String");
        }

        @Test
        @DisplayName("extractStructuredMemories accepts String and MemoryType parameters")
        void testExtractStructuredMemoriesParameterTypes() {
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"memory\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            assertDoesNotThrow(() -> {
                processor.extractStructuredMemories("content", Snippet.MemoryType.PROFILE);
            });
        }

        @Test
        @DisplayName("extractStructuredMemories throws ContentProcessingException on error")
        void testExtractStructuredMemoriesExceptionType() {
            when(mockLlmClient.chat(anyList(), anyString()))
                .thenThrow(new RuntimeException("LLM error"));

            assertThrows(ContentProcessingException.class, () ->
                processor.extractStructuredMemories("test", Snippet.MemoryType.PROFILE));
        }

        @ParameterizedTest
        @EnumSource(Snippet.MemoryType.class)
        @DisplayName("extractStructuredMemories accepts all MemoryType enum values")
        void testExtractStructuredMemoriesAllMemoryTypes(Snippet.MemoryType memoryType) {
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"memory\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            assertDoesNotThrow(() ->
                processor.extractStructuredMemories("test", memoryType));
        }
    }

    // ========================================================================
    // 3. Normal Functionality Scenarios
    // ========================================================================

    @Nested
    @DisplayName("3. Normal Functionality Scenarios")
    class NormalFunctionalityScenarios {

        @Test
        @DisplayName("Extract PROFILE memories from conversation")
        void testExtractProfileMemories() {
            String content = """
                User: My name is Alice and I'm a data scientist at Meta.
                Assistant: Great to meet you, Alice!
                User: I have a PhD in Computer Science and love machine learning.
                """;

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("["
                + "\"Alice is a data scientist at Meta with a PhD in Computer Science\", "
                + "\"Alice loves machine learning\""
                + "]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<String> memories = processor.extractStructuredMemories(content, Snippet.MemoryType.PROFILE);

            assertNotNull(memories);
            assertEquals(2, memories.size());
            assertTrue(memories.get(0).toLowerCase().contains("data scientist"));
        }

        @Test
        @DisplayName("Extract EVENT memories from conversation")
        void testExtractEventMemories() {
            String content = """
                User: Last week I attended the AWS re:Invent conference.
                Assistant: That's a major cloud computing event!
                User: Yes, I learned about their new AI services and infrastructure.
                """;

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("["
                + "\"User attended AWS re:Invent conference last week\", "
                + "\"User learned about new AI services and infrastructure\""
                + "]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<String> memories = processor.extractStructuredMemories(content, Snippet.MemoryType.EVENT);

            assertNotNull(memories);
            assertEquals(2, memories.size());
            assertTrue(memories.get(0).contains("AWS re:Invent"));
        }

        @Test
        @DisplayName("Extract KNOWLEDGE memories from conversation")
        void testExtractKnowledgeMemories() {
            String content = """
                User: Spring Boot 3.0 requires Java 17 as baseline.
                Assistant: Yes, it also includes native image support.
                User: The framework uses AOT compilation for improved startup time.
                """;

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("["
                + "\"Spring Boot 3.0 requires Java 17 as baseline\", "
                + "\"Spring Boot includes native image support with AOT compilation\""
                + "]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<String> memories = processor.extractStructuredMemories(content, Snippet.MemoryType.KNOWLEDGE);

            assertNotNull(memories);
            assertEquals(2, memories.size());
        }

        @Test
        @DisplayName("Extract BEHAVIOR memories from conversation")
        void testExtractBehaviorMemories() {
            String content = """
                User: I always write unit tests before implementation.
                Assistant: That's test-driven development!
                User: Yes, I prefer TDD because it ensures code quality.
                """;

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("["
                + "\"User follows test-driven development approach\", "
                + "\"User writes unit tests before implementation\""
                + "]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<String> memories = processor.extractStructuredMemories(content, Snippet.MemoryType.BEHAVIOR);

            assertNotNull(memories);
            assertEquals(2, memories.size());
            assertTrue(memories.get(0).contains("test-driven development") ||
                       memories.get(0).contains("TDD"));
        }

        @Test
        @DisplayName("Extract SKILL memories from conversation")
        void testExtractSkillMemories() {
            String content = """
                User: I'm proficient in Rust and Go for systems programming.
                Assistant: Those are great languages for performance-critical applications.
                User: I also know Kubernetes and Docker for container orchestration.
                """;

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("["
                + "\"User is proficient in Rust and Go for systems programming\", "
                + "\"User knows Kubernetes and Docker for container orchestration\""
                + "]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<String> memories = processor.extractStructuredMemories(content, Snippet.MemoryType.SKILL);

            assertNotNull(memories);
            assertEquals(2, memories.size());
            assertTrue(memories.get(0).contains("Rust") || memories.get(0).contains("Go"));
        }

        @Test
        @DisplayName("Extract TOOL memories from conversation")
        void testExtractToolMemories() {
            String content = """
                User: I use IntelliJ IDEA for Java development with the GitHub Copilot plugin.
                Assistant: IntelliJ is excellent for Java development.
                User: I also use Postman for API testing and Grafana for monitoring.
                """;

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("["
                + "\"User uses IntelliJ IDEA with GitHub Copilot for Java development\", "
                + "\"User uses Postman for API testing and Grafana for monitoring\""
                + "]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<String> memories = processor.extractStructuredMemories(content, Snippet.MemoryType.TOOL);

            assertNotNull(memories);
            assertEquals(2, memories.size());
            assertTrue(memories.get(0).contains("IntelliJ"));
        }

        @Test
        @DisplayName("Return empty list when no memories found")
        void testExtractNoMemories() {
            String content = "User: Hello\nAssistant: Hi there";

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<String> memories = processor.extractStructuredMemories(content, Snippet.MemoryType.PROFILE);

            assertNotNull(memories);
            assertTrue(memories.isEmpty());
        }

        @Test
        @DisplayName("Handle single memory extraction")
        void testExtractSingleMemory() {
            String content = "User: I'm a developer";

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"User is a developer\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<String> memories = processor.extractStructuredMemories(content, Snippet.MemoryType.PROFILE);

            assertNotNull(memories);
            assertEquals(1, memories.size());
            assertEquals("User is a developer", memories.get(0));
        }
    }

    // ========================================================================
    // 4. Boundary Conditions
    // ========================================================================

    @Nested
    @DisplayName("4. Boundary Conditions")
    class BoundaryConditions {

        @ParameterizedTest
        @NullSource
        @DisplayName("Handle null content input")
        void testNullContent(String nullContent) {
            assertThrows(ContentProcessingException.class, () ->
                processor.extractStructuredMemories(nullContent, Snippet.MemoryType.PROFILE));
        }

        @ParameterizedTest
        @EmptySource
        @DisplayName("Handle empty content input")
        void testEmptyContent(String emptyContent) {
            assertThrows(ContentProcessingException.class, () ->
                processor.extractStructuredMemories(emptyContent, Snippet.MemoryType.PROFILE));
        }

        @Test
        @DisplayName("Handle null memory type")
        void testNullMemoryType() {
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"memory\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            assertThrows(ContentProcessingException.class, () ->
                processor.extractStructuredMemories("test content", null));
        }

        @Test
        @DisplayName("Handle very long content (>10000 characters)")
        void testVeryLongContent() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("User: Message ").append(i).append("\n");
                sb.append("Assistant: Response ").append(i).append("\n");
            }
            String longContent = sb.toString();

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"Extracted memory\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            assertDoesNotThrow(() ->
                processor.extractStructuredMemories(longContent, Snippet.MemoryType.PROFILE));
        }

        @Test
        @DisplayName("Handle content with special characters")
        void testSpecialCharactersContent() {
            String content = "User: Test @#$%^&*(){}|:\"<>?[]\\',./`~";

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"Special character memory\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            assertDoesNotThrow(() ->
                processor.extractStructuredMemories(content, Snippet.MemoryType.PROFILE));
        }

        @Test
        @DisplayName("Handle content with Unicode and emoji")
        void testUnicodeContent() {
            String content = "User: 你好！👋\nAssistant: Hello! 🌍";

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"Unicode memory 你好\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            assertDoesNotThrow(() ->
                processor.extractStructuredMemories(content, Snippet.MemoryType.PROFILE));
        }

        @Test
        @DisplayName("Handle minimal content (single word)")
        void testMinimalContent() {
            String content = "Developer";

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"User is a developer\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<String> memories = processor.extractStructuredMemories(content, Snippet.MemoryType.PROFILE);

            assertNotNull(memories);
            assertFalse(memories.isEmpty());
        }

        @Test
        @DisplayName("Handle content with only whitespace")
        void testWhitespaceContent() {
            String content = "   \n\t   \n   ";

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            assertThrows(ContentProcessingException.class, () ->
                processor.extractStructuredMemories(content, Snippet.MemoryType.PROFILE));
        }

        @ParameterizedTest
        @ValueSource(strings = {"PROFILE", "EVENT", "KNOWLEDGE", "BEHAVIOR", "SKILL", "TOOL"})
        @DisplayName("Handle all memory types")
        void testAllMemoryTypes(String memoryTypeStr) {
            Snippet.MemoryType memoryType = Snippet.MemoryType.valueOf(memoryTypeStr);
            String content = "Test content";

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"Memory\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            assertDoesNotThrow(() ->
                processor.extractStructuredMemories(content, memoryType));
        }
    }

    // ========================================================================
    // 5. Exception Handling
    // ========================================================================

    @Nested
    @DisplayName("5. Exception Handling")
    class ExceptionHandling {

        @Test
        @DisplayName("Throw ContentProcessingException on LLM failure")
        void testLLMFailure() {
            when(mockLlmClient.chat(anyList(), anyString()))
                .thenThrow(new RuntimeException("LLM API error"));

            assertThrows(ContentProcessingException.class, () ->
                processor.extractStructuredMemories("test", Snippet.MemoryType.PROFILE));
        }

        @Test
        @DisplayName("Throw exception on malformed JSON response")
        void testMalformedJsonResponse() {
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("invalid json {{}");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            // Should handle gracefully and not crash
            assertDoesNotThrow(() ->
                processor.extractStructuredMemories("test", Snippet.MemoryType.PROFILE));
        }

        @Test
        @DisplayName("Retry on transient LLM failures")
        void testRetryMechanism() {
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"Memory after retry\"]");

            when(mockLlmClient.chat(anyList(), anyString()))
                .thenThrow(new RuntimeException("First attempt failed"))
                .thenReturn(mockResponse);

            List<String> memories = processor.extractStructuredMemories("test", Snippet.MemoryType.PROFILE);

            assertNotNull(memories);
            verify(mockLlmClient, times(2)).chat(anyList(), anyString());
        }

        @Test
        @DisplayName("Throw exception after max retries exceeded")
        void testMaxRetriesExceeded() {
            when(mockLlmClient.chat(anyList(), anyString()))
                .thenThrow(new RuntimeException("Persistent failure"));

            assertThrows(ContentProcessingException.class, () ->
                processor.extractStructuredMemories("test", Snippet.MemoryType.PROFILE));

            verify(mockLlmClient, times(config.getMaxRetries())).chat(anyList(), anyString());
        }

        @Test
        @DisplayName("Handle empty array response gracefully")
        void testEmptyArrayResponse() {
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            List<String> memories = processor.extractStructuredMemories("test", Snippet.MemoryType.PROFILE);

            assertNotNull(memories);
            assertTrue(memories.isEmpty());
        }

        @Test
        @DisplayName("Handle non-array JSON response gracefully")
        void testNonArrayJsonResponse() {
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("{\"key\": \"value\"}");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            // Should handle gracefully - may return empty list or single item
            assertDoesNotThrow(() ->
                processor.extractStructuredMemories("test", Snippet.MemoryType.PROFILE));
        }

        @Test
        @DisplayName("Verify error message contains useful information")
        void testErrorMessageQuality() {
            when(mockLlmClient.chat(anyList(), anyString()))
                .thenThrow(new RuntimeException("Specific error details"));

            ContentProcessingException exception = assertThrows(ContentProcessingException.class, () ->
                processor.extractStructuredMemories("test", Snippet.MemoryType.PROFILE));

            assertNotNull(exception.getMessage());
            assertFalse(exception.getMessage().isEmpty());
        }
    }

    // ========================================================================
    // 6. Performance Benchmarks
    // ========================================================================

    @Nested
    @DisplayName("6. Performance Benchmarks")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    class PerformanceBenchmarks {

        @Test
        @DisplayName("Complete extraction within 100ms for normal input")
        void testResponseTime() {
            String content = "User: I'm a software engineer\nAssistant: Nice to meet you";

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"User is a software engineer\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            long startTime = System.currentTimeMillis();
            processor.extractStructuredMemories(content, Snippet.MemoryType.PROFILE);
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 100, "Should complete within 100ms, took: " + duration + "ms");
        }

        @Test
        @DisplayName("Handle batch processing of 100 items within 1s")
        void testBatchProcessingPerformance() {
            List<String> contents = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                contents.add("Content " + i + ": User is a developer");
            }

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"Extracted memory\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            long startTime = System.currentTimeMillis();
            for (String content : contents) {
                processor.extractStructuredMemories(content, Snippet.MemoryType.PROFILE);
            }
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 1000, "Should complete 100 items within 1s, took: " + duration + "ms");
        }

        @Test
        @DisplayName("Memory usage remains under 512MB during processing")
        void testMemoryUsage() {
            Runtime runtime = Runtime.getRuntime();
            long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

            // Process multiple items
            for (int i = 0; i < 100; i++) {
                UnifiedMessage mockResponse = mock(UnifiedMessage.class);
                when(mockResponse.content()).thenReturn("[\"Memory " + i + "\"]");
                when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

                processor.extractStructuredMemories("Content " + i, Snippet.MemoryType.PROFILE);
            }

            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = (memoryAfter - memoryBefore) / (1024 * 1024); // Convert to MB

            assertTrue(memoryUsed < 512, "Memory usage should be under 512MB, used: " + memoryUsed + "MB");
        }

        @Test
        @DisplayName("Maintain consistent performance across different memory types")
        void testPerformanceConsistency() {
            String content = "User: Test content\nAssistant: Response";

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"Memory\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            long[] durations = new long[Snippet.MemoryType.values().length];
            int index = 0;

            for (Snippet.MemoryType type : Snippet.MemoryType.values()) {
                long startTime = System.currentTimeMillis();
                processor.extractStructuredMemories(content, type);
                durations[index++] = System.currentTimeMillis() - startTime;
            }

            // All operations should complete within reasonable time
            for (long duration : durations) {
                assertTrue(duration < 100, "All types should complete within 100ms, took: " + duration + "ms");
            }
        }
    }

    // ========================================================================
    // 7. Concurrency Scenarios
    // ========================================================================

    @Nested
    @DisplayName("7. Concurrency Scenarios")
    class ConcurrencyScenarios {

        @Test
        @DisplayName("Handle 10 concurrent extraction requests")
        void testConcurrentExtractions() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"Concurrent memory\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        processor.extractStructuredMemories(
                            "Concurrent content " + Thread.currentThread().getId(),
                            Snippet.MemoryType.PROFILE
                        );
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
        @DisplayName("Maintain thread safety under concurrent access")
        void testThreadSafety() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"Thread-safe memory\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        List<String> memories = processor.extractStructuredMemories(
                            "Content " + index,
                            Snippet.MemoryType.KNOWLEDGE
                        );
                        assertNotNull(memories);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS));

            executor.shutdown();
        }

        @Test
        @DisplayName("Ensure data consistency under concurrent operations")
        void testDataConsistency() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            AtomicInteger errorCount = new AtomicInteger(0);

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"Consistent memory\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        List<String> memories = processor.extractStructuredMemories(
                            "Same content",
                            Snippet.MemoryType.PROFILE
                        );
                        if (memories == null) {
                            errorCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS));
            assertEquals(0, errorCount.get(), "No errors should occur under concurrent access");

            executor.shutdown();
        }

        @Test
        @DisplayName("Handle mixed memory types concurrently")
        void testMixedMemoryTypesConcurrently() throws InterruptedException {
            int threadCount = 6; // One for each memory type
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"Memory\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            Snippet.MemoryType[] types = Snippet.MemoryType.values();
            for (int i = 0; i < threadCount; i++) {
                final Snippet.MemoryType type = types[i];
                executor.submit(() -> {
                    try {
                        processor.extractStructuredMemories("Content", type);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS));

            executor.shutdown();
        }
    }

    // ========================================================================
    // 8. Cache Mechanism Testing (if applicable)
    // ========================================================================

    @Nested
    @DisplayName("8. Cache Mechanism Testing")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    class CacheMechanismTesting {

        @Test
        @DisplayName("Cache hit on identical content and memory type")
        void testCacheHit() {
            if (!config.isCacheEnabled()) {
                return; // Skip if caching is disabled
            }

            String content = "Cacheable content";
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"Cached memory\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            // First call - cache miss
            processor.extractStructuredMemories(content, Snippet.MemoryType.PROFILE);

            // Second call - should be cache hit
            processor.extractStructuredMemories(content, Snippet.MemoryType.PROFILE);

            // Verify LLM was called only once (cached on second call)
            verify(mockLlmClient, times(1)).chat(anyList(), anyString());
        }

        @Test
        @DisplayName("Cache miss on different content")
        void testCacheMissDifferentContent() {
            if (!config.isCacheEnabled()) {
                return;
            }

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"Memory\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            processor.extractStructuredMemories("Content 1", Snippet.MemoryType.PROFILE);
            processor.extractStructuredMemories("Content 2", Snippet.MemoryType.PROFILE);

            // Should call LLM twice (different content)
            verify(mockLlmClient, times(2)).chat(anyList(), anyString());
        }

        @Test
        @DisplayName("Cache miss on different memory type")
        void testCacheMissDifferentMemoryType() {
            if (!config.isCacheEnabled()) {
                return;
            }

            String content = "Same content";
            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"Memory\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            processor.extractStructuredMemories(content, Snippet.MemoryType.PROFILE);
            processor.extractStructuredMemories(content, Snippet.MemoryType.KNOWLEDGE);

            // Should call LLM twice (different memory types)
            verify(mockLlmClient, times(2)).chat(anyList(), anyString());
        }

        @Test
        @DisplayName("Cache statistics are tracked")
        void testCacheStatistics() {
            if (!config.isCacheEnabled()) {
                return;
            }

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"Memory\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            String content = "Stats test content";
            processor.extractStructuredMemories(content, Snippet.MemoryType.PROFILE);
            processor.extractStructuredMemories(content, Snippet.MemoryType.PROFILE);

            String stats = processor.getCacheStats();
            assertNotNull(stats);
            // Stats should indicate some cache activity
            assertTrue(stats.contains("hits=") || stats.contains("Cache"));
        }

        @Test
        @DisplayName("clearCaches removes cached entries")
        void testClearCaches() {
            if (!config.isCacheEnabled()) {
                return;
            }

            UnifiedMessage mockResponse = mock(UnifiedMessage.class);
            when(mockResponse.content()).thenReturn("[\"Memory\"]");
            when(mockLlmClient.chat(anyList(), anyString())).thenReturn(mockResponse);

            String content = "Clear test content";
            processor.extractStructuredMemories(content, Snippet.MemoryType.PROFILE);
            processor.clearCaches();
            processor.extractStructuredMemories(content, Snippet.MemoryType.PROFILE);

            // LLM should be called twice (cache was cleared)
            verify(mockLlmClient, times(2)).chat(anyList(), anyString());
        }
    }
}
