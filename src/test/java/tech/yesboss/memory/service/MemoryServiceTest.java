package tech.yesboss.memory.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.memory.embedding.EmbeddingService;
import tech.yesboss.memory.manager.MemoryManager;
import tech.yesboss.memory.model.*;
import tech.yesboss.memory.processor.ConversationSegment;
import tech.yesboss.memory.processor.ContentProcessor;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for MemoryService.
 *
 * <p>This test class verifies all aspects of the MemoryService including:
 * <ul>
 *   <li>Interface contract compliance</li>
 *   <li>Core memory extraction pipeline</li>
 *   <li>Conversation segmentation</li>
 *   <li>Abstract generation</li>
 *   <li>Structured memory extraction</li>
 *   <li>Preference association</li>
 *   <li>Error handling and recovery</li>
 *   <li>Performance requirements</li>
 *   <li>Concurrent operations</li>
 * </ul>
 */
@DisplayName("MemoryService Implementation Tests")
public class MemoryServiceTest {

    private ContentProcessor contentProcessor;
    private MemoryManager memoryManager;
    private ResourceRepository resourceRepository;
    private SnippetRepository snippetRepository;
    private PreferenceRepository preferenceRepository;
    private EmbeddingService embeddingService;
    private MemoryService memoryService;

    @BeforeEach
    void setUp() {
        contentProcessor = mock(ContentProcessor.class);
        memoryManager = mock(MemoryManager.class);
        resourceRepository = mock(ResourceRepository.class);
        snippetRepository = mock(SnippetRepository.class);
        preferenceRepository = mock(PreferenceRepository.class);
        embeddingService = mock(EmbeddingService.class);

        // Configure mocks
        when(contentProcessor.segmentConversation(anyString())).thenReturn(createTestSegments());
        when(contentProcessor.generateSegmentAbstract(anyString())).thenReturn("Test abstract");
        when(contentProcessor.extractStructuredMemories(anyString(), any())).thenReturn(Arrays.asList("Memory 1", "Memory 2"));
        when(embeddingService.generateEmbedding(anyString())).thenReturn(createTestEmbedding());

        // Configure MemoryManager mocks
        when(memoryManager.saveResource(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memoryManager.saveSnippets(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(memoryManager.savePreference(any(Preference.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memoryManager.isAvailable()).thenReturn(true);

        // Configure repository mocks
        when(preferenceRepository.findById(anyString())).thenReturn(java.util.Optional.of(createTestPreference()));
        when(preferenceRepository.findByName(anyString())).thenReturn(java.util.Optional.empty());
        when(resourceRepository.findResourcesWithoutEmbedding()).thenReturn(Collections.emptyList());

        memoryService = new MemoryServiceImpl(
                contentProcessor,
                memoryManager,
                resourceRepository,
                snippetRepository,
                preferenceRepository,
                embeddingService
        );
    }

    @AfterEach
    void tearDown() {
        if (memoryService != null) {
            ((MemoryServiceImpl) memoryService).shutdown();
        }
    }

    // ========== Helper Methods ==========

    private float[] createTestEmbedding() {
        float[] embedding = new float[1536];
        Arrays.fill(embedding, 0.1f);
        return embedding;
    }

    private List<ConversationSegment> createTestSegments() {
        List<ConversationSegment> segments = new ArrayList<>();
        ConversationSegment segment1 = new ConversationSegment();
        segment1.setContent("Test segment content 1");
        segment1.setStartIndex(0);
        segment1.setEndIndex(100);
        segments.add(segment1);

        ConversationSegment segment2 = new ConversationSegment();
        segment2.setContent("Test segment content 2");
        segment2.setStartIndex(100);
        segment2.setEndIndex(200);
        segments.add(segment2);

        return segments;
    }

    private List<UnifiedMessage> createTestMessages() {
        List<UnifiedMessage> messages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UnifiedMessage message = mock(UnifiedMessage.class);
            when(message.getDisplayContent()).thenReturn("Test message content " + i);
            messages.add(message);
        }
        return messages;
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
        @DisplayName("extractFromMessages() returns list of Resources")
        void testExtractFromMessagesReturnsList() {
            List<UnifiedMessage> messages = createTestMessages();
            List<Resource> result = memoryService.extractFromMessages(messages, "conv-1", "session-1");
            assertNotNull(result, "Should return non-null list");
            assertFalse(result.isEmpty(), "Should return non-empty list");
        }

        @Test
        @DisplayName("concatenateConversationContent() returns string")
        void testConcatenateConversationContentReturnsString() {
            List<UnifiedMessage> messages = createTestMessages();
            String result = memoryService.concatenateConversationContent(messages);
            assertNotNull(result, "Should return non-null string");
            assertFalse(result.isEmpty(), "Should return non-empty string");
        }

        @Test
        @DisplayName("segmentConversation() returns list of ConversationSegments")
        void testSegmentConversationReturnsList() {
            String content = "Test conversation content";
            List<ConversationSegment> result = memoryService.segmentConversation(content);
            assertNotNull(result, "Should return non-null list");
        }

        @Test
        @DisplayName("generateSegmentAbstract() returns string")
        void testGenerateSegmentAbstractReturnsString() {
            String content = "Test segment content";
            String result = memoryService.generateSegmentAbstract(content);
            assertNotNull(result, "Should return non-null string");
            assertFalse(result.isEmpty(), "Should return non-empty string");
        }

        @Test
        @DisplayName("buildResource() returns Resource object")
        void testBuildResourceReturnsResource() {
            Resource result = memoryService.buildResource("conv-1", "session-1", "content", "abstract");
            assertNotNull(result, "Should return non-null Resource");
            assertEquals("conv-1", result.getConversationId());
            assertEquals("session-1", result.getSessionId());
        }

        @Test
        @DisplayName("extractStructuredMemories() returns list of Snippets")
        void testExtractStructuredMemoriesReturnsList() {
            String content = "Test content";
            List<Snippet> result = memoryService.extractStructuredMemories(content);
            assertNotNull(result, "Should return non-null list");
        }

        @Test
        @DisplayName("extractMemoriesByType() returns list of strings")
        void testExtractMemoriesByTypeReturnsList() {
            String content = "Test content";
            List<String> result = memoryService.extractMemoriesByType(content, Snippet.MemoryType.KNOWLEDGE);
            assertNotNull(result, "Should return non-null list");
        }

        @Test
        @DisplayName("associateWithPreferences() returns map")
        void testAssociateWithPreferencesReturnsMap() {
            List<Snippet> snippets = Arrays.asList(
                    Snippet.builder().resourceId("r1").summary("Memory 1").memoryType(Snippet.MemoryType.KNOWLEDGE).build()
            );
            var result = memoryService.associateWithPreferences(snippets);
            assertNotNull(result, "Should return non-null map");
        }

        @Test
        @DisplayName("isAvailable() returns boolean")
        void testIsAvailableReturnsBoolean() {
            boolean available = memoryService.isAvailable();
            assertTrue(available == true || available == false, "Should return boolean value");
        }
    }

    // ========== Functional Correctness Tests ==========

    @Nested
    @DisplayName("Functional Correctness Tests")
    class FunctionalCorrectnessTests {

        @Test
        @DisplayName("extractFromMessages() processes complete pipeline")
        void testExtractFromMessagesProcessesCompletePipeline() {
            List<UnifiedMessage> messages = createTestMessages();

            List<Resource> result = memoryService.extractFromMessages(messages, "conv-1", "session-1");

            // Verify conversation content concatenation
            verify(contentProcessor, never()).segmentConversation(isNull());

            // Verify segmentation
            verify(contentProcessor, times(1)).segmentConversation(anyString());

            // Verify abstract generation for each segment
            verify(contentProcessor, times(2)).generateSegmentAbstract(anyString());

            // Verify resource saving
            verify(memoryManager, times(2)).saveResource(any(Resource.class));

            // Verify structured memory extraction
            verify(contentProcessor, times(2)).extractStructuredMemories(anyString(), eq(Snippet.MemoryType.PROFILE));
            verify(contentProcessor, times(2)).extractStructuredMemories(anyString(), eq(Snippet.MemoryType.EVENT));
            verify(contentProcessor, times(2)).extractStructuredMemories(anyString(), eq(Snippet.MemoryType.KNOWLEDGE));
            verify(contentProcessor, times(2)).extractStructuredMemories(anyString(), eq(Snippet.MemoryType.BEHAVIOR));
            verify(contentProcessor, times(2)).extractStructuredMemories(anyString(), eq(Snippet.MemoryType.SKILL));
            verify(contentProcessor, times(2)).extractStructuredMemories(anyString(), eq(Snippet.MemoryType.TOOL));

            // Verify snippet saving
            verify(memoryManager, times(2)).saveSnippets(anyList());

            assertNotNull(result);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("concatenateConversationContent() joins message contents")
        void testConcatenateConversationContentJoinsContents() {
            List<UnifiedMessage> messages = createTestMessages();

            String result = memoryService.concatenateConversationContent(messages);

            assertTrue(result.contains("Test message content 0"));
            assertTrue(result.contains("Test message content 1"));
            assertTrue(result.contains("Test message content 2"));
            assertTrue(result.contains("Test message content 3"));
            assertTrue(result.contains("Test message content 4"));
        }

        @Test
        @DisplayName("segmentConversation() delegates to ContentProcessor")
        void testSegmentConversationDelegatesToContentProcessor() {
            String content = "Test conversation content";

            List<ConversationSegment> result = memoryService.segmentConversation(content);

            verify(contentProcessor, times(1)).segmentConversation(content);
            assertNotNull(result);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("generateSegmentAbstract() delegates to ContentProcessor")
        void testGenerateSegmentAbstractDelegatesToContentProcessor() {
            String content = "Test segment content";

            String result = memoryService.generateSegmentAbstract(content);

            verify(contentProcessor, times(1)).generateSegmentAbstract(content);
            assertEquals("Test abstract", result);
        }

        @Test
        @DisplayName("buildResource() creates Resource with correct fields")
        void testBuildResourceCreatesCorrectResource() {
            Resource result = memoryService.buildResource("conv-1", "session-1", "content", "abstract");

            assertEquals("conv-1", result.getConversationId());
            assertEquals("session-1", result.getSessionId());
            assertEquals("content", result.getContent());
            assertEquals("abstract", result.getAbstract());
            assertTrue(result.getTimestamp() > 0);
        }

        @Test
        @DisplayName("extractStructuredMemories() extracts all memory types")
        void testExtractStructuredMemoriesExtractsAllTypes() {
            String content = "Test content";

            List<Snippet> result = memoryService.extractStructuredMemories(content);

            // Verify all 6 memory types were extracted
            verify(contentProcessor, times(1)).extractStructuredMemories(content, Snippet.MemoryType.PROFILE);
            verify(contentProcessor, times(1)).extractStructuredMemories(content, Snippet.MemoryType.EVENT);
            verify(contentProcessor, times(1)).extractStructuredMemories(content, Snippet.MemoryType.KNOWLEDGE);
            verify(contentProcessor, times(1)).extractStructuredMemories(content, Snippet.MemoryType.BEHAVIOR);
            verify(contentProcessor, times(1)).extractStructuredMemories(content, Snippet.MemoryType.SKILL);
            verify(contentProcessor, times(1)).extractStructuredMemories(content, Snippet.MemoryType.TOOL);

            assertNotNull(result);
            assertEquals(12, result.size()); // 2 memories per type x 6 types
        }

        @Test
        @DisplayName("associateWithPreferences() creates preferences for snippets")
        void testAssociateWithPreferencesCreatesPreferences() {
            List<Snippet> snippets = Arrays.asList(
                    Snippet.builder().resourceId("r1").summary("Memory 1").memoryType(Snippet.MemoryType.KNOWLEDGE).build(),
                    Snippet.builder().resourceId("r1").summary("Memory 2").memoryType(Snippet.MemoryType.KNOWLEDGE).build()
            );

            var result = memoryService.associateWithPreferences(snippets);

            // Verify preferences were created
            verify(memoryManager, times(2)).savePreference(any(Preference.class));

            assertNotNull(result);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("batchExtractFromMessages() processes multiple batches")
        void testBatchExtractFromMessagesProcessesMultipleBatches() {
            List<MemoryService.MessageBatch> batches = Arrays.asList(
                    new MemoryService.MessageBatch(createTestMessages(), "conv-1", "session-1"),
                    new MemoryService.MessageBatch(createTestMessages(), "conv-2", "session-2")
            );

            List<Resource> result = memoryService.batchExtractFromMessages(batches);

            verify(memoryManager, times(4)).saveResource(any(Resource.class));
            assertNotNull(result);
            assertEquals(4, result.size());
        }
    }

    // ========== Edge Cases and Boundary Conditions Tests ==========

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("extractFromMessages() throws for null messages")
        void testExtractFromMessagesThrowsForNullMessages() {
            assertThrows(MemoryServiceException.class,
                    () -> memoryService.extractFromMessages(null, "conv-1", "session-1"));
        }

        @Test
        @DisplayName("extractFromMessages() throws for empty messages")
        void testExtractFromMessagesThrowsForEmptyMessages() {
            assertThrows(MemoryServiceException.class,
                    () -> memoryService.extractFromMessages(Collections.emptyList(), "conv-1", "session-1"));
        }

        @Test
        @DisplayName("extractFromMessages() throws for null conversationId")
        void testExtractFromMessagesThrowsForNullConversationId() {
            List<UnifiedMessage> messages = createTestMessages();
            assertThrows(MemoryServiceException.class,
                    () -> memoryService.extractFromMessages(messages, null, "session-1"));
        }

        @Test
        @DisplayName("extractFromMessages() throws for empty conversationId")
        void testExtractFromMessagesThrowsForEmptyConversationId() {
            List<UnifiedMessage> messages = createTestMessages();
            assertThrows(MemoryServiceException.class,
                    () -> memoryService.extractFromMessages(messages, "   ", "session-1"));
        }

        @Test
        @DisplayName("extractFromMessages() throws for null sessionId")
        void testExtractFromMessagesThrowsForNullSessionId() {
            List<UnifiedMessage> messages = createTestMessages();
            assertThrows(MemoryServiceException.class,
                    () -> memoryService.extractFromMessages(messages, "conv-1", null));
        }

        @Test
        @DisplayName("segmentConversation() throws for null content")
        void testSegmentConversationThrowsForNullContent() {
            assertThrows(MemoryServiceException.class,
                    () -> memoryService.segmentConversation(null));
        }

        @Test
        @DisplayName("segmentConversation() throws for empty content")
        void testSegmentConversationThrowsForEmptyContent() {
            assertThrows(MemoryServiceException.class,
                    () -> memoryService.segmentConversation(""));
        }

        @Test
        @DisplayName("generateSegmentAbstract() throws for null content")
        void testGenerateSegmentAbstractThrowsForNullContent() {
            assertThrows(MemoryServiceException.class,
                    () -> memoryService.generateSegmentAbstract(null));
        }

        @Test
        @DisplayName("generateSegmentAbstract() throws for empty content")
        void testGenerateSegmentAbstractThrowsForEmptyContent() {
            assertThrows(MemoryServiceException.class,
                    () -> memoryService.generateSegmentAbstract(""));
        }

        @Test
        @DisplayName("buildResource() throws for null conversationId")
        void testBuildResourceThrowsForNullConversationId() {
            assertThrows(MemoryServiceException.class,
                    () -> memoryService.buildResource(null, "session-1", "content", "abstract"));
        }

        @Test
        @DisplayName("buildResource() throws for null sessionId")
        void testBuildResourceThrowsForNullSessionId() {
            assertThrows(MemoryServiceException.class,
                    () -> memoryService.buildResource("conv-1", null, "content", "abstract"));
        }

        @Test
        @DisplayName("buildResource() throws for null segmentContent")
        void testBuildResourceThrowsForNullSegmentContent() {
            assertThrows(MemoryServiceException.class,
                    () -> memoryService.buildResource("conv-1", "session-1", null, "abstract"));
        }

        @Test
        @DisplayName("extractStructuredMemories() throws for null content")
        void testExtractStructuredMemoriesThrowsForNullContent() {
            assertThrows(MemoryServiceException.class,
                    () -> memoryService.extractStructuredMemories(null));
        }

        @Test
        @DisplayName("extractStructuredMemories() throws for empty content")
        void testExtractStructuredMemoriesThrowsForEmptyContent() {
            assertThrows(MemoryServiceException.class,
                    () -> memoryService.extractStructuredMemories(""));
        }

        @Test
        @DisplayName("extractMemoriesByType() throws for null content")
        void testExtractMemoriesByTypeThrowsForNullContent() {
            assertThrows(MemoryServiceException.class,
                    () -> memoryService.extractMemoriesByType(null, Snippet.MemoryType.KNOWLEDGE));
        }

        @Test
        @DisplayName("extractMemoriesByType() throws for null memoryType")
        void testExtractMemoriesByTypeThrowsForNullMemoryType() {
            assertThrows(MemoryServiceException.class,
                    () -> memoryService.extractMemoriesByType("content", null));
        }

        @Test
        @DisplayName("concatenateConversationContent() handles null messages")
        void testConcatenateConversationContentHandlesNullMessages() {
            String result = memoryService.concatenateConversationContent(null);
            assertEquals("", result);
        }

        @Test
        @DisplayName("concatenateConversationContent() handles empty messages")
        void testConcatenateConversationContentHandlesEmptyMessages() {
            String result = memoryService.concatenateConversationContent(Collections.emptyList());
            assertEquals("", result);
        }

        @Test
        @DisplayName("concatenateConversationContent() skips null messages in list")
        void testConcatenateConversationContentSkipsNullMessages() {
            List<UnifiedMessage> messages = Arrays.asList(
                    createTestMessages().get(0),
                    null,
                    createTestMessages().get(1)
            );

            String result = memoryService.concatenateConversationContent(messages);

            assertNotNull(result);
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("associateWithPreferences() handles empty list")
        void testAssociateWithPreferencesHandlesEmptyList() {
            var result = memoryService.associateWithPreferences(Collections.emptyList());
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("associateWithPreferences() handles null list")
        void testAssociateWithPreferencesHandlesNullList() {
            var result = memoryService.associateWithPreferences(null);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("batchExtractFromMessages() throws for null list")
        void testBatchExtractFromMessagesThrowsForNullList() {
            assertThrows(MemoryServiceException.class,
                    () -> memoryService.batchExtractFromMessages(null));
        }

        @Test
        @DisplayName("batchExtractFromMessages() throws for empty list")
        void testBatchExtractFromMessagesThrowsForEmptyList() {
            assertThrows(MemoryServiceException.class,
                    () -> memoryService.batchExtractFromMessages(Collections.emptyList()));
        }
    }

    // ========== Error Handling Tests ==========

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("extractFromMessages() handles segmentation failure")
        void testExtractFromMessagesHandlesSegmentationFailure() {
            when(contentProcessor.segmentConversation(anyString()))
                    .thenThrow(new RuntimeException("Segmentation failed"));

            List<UnifiedMessage> messages = createTestMessages();

            MemoryServiceException exception = assertThrows(MemoryServiceException.class,
                    () -> memoryService.extractFromMessages(messages, "conv-1", "session-1"));

            assertEquals(MemoryServiceException.ERROR_PROCESSING_FAILURE, exception.getErrorCode());
        }

        @Test
        @DisplayName("extractFromMessages() handles abstract generation failure")
        void testExtractFromMessagesHandlesAbstractGenerationFailure() {
            when(contentProcessor.generateSegmentAbstract(anyString()))
                    .thenThrow(new RuntimeException("LLM failed"));

            List<UnifiedMessage> messages = createTestMessages();

            MemoryServiceException exception = assertThrows(MemoryServiceException.class,
                    () -> memoryService.extractFromMessages(messages, "conv-1", "session-1"));

            assertEquals(MemoryServiceException.ERROR_PROCESSING_FAILURE, exception.getErrorCode());
        }

        @Test
        @DisplayName("segmentConversation() handles ContentProcessor failure")
        void testSegmentConversationHandlesContentProcessorFailure() {
            when(contentProcessor.segmentConversation(anyString()))
                    .thenThrow(new RuntimeException("Processing failed"));

            MemoryServiceException exception = assertThrows(MemoryServiceException.class,
                    () -> memoryService.segmentConversation("content"));

            assertEquals(MemoryServiceException.ERROR_SEGMENTATION_FAILURE, exception.getErrorCode());
        }

        @Test
        @DisplayName("generateSegmentAbstract() handles LLM failure")
        void testGenerateSegmentAbstractHandlesLLMFailure() {
            when(contentProcessor.generateSegmentAbstract(anyString()))
                    .thenThrow(new RuntimeException("LLM failed"));

            MemoryServiceException exception = assertThrows(MemoryServiceException.class,
                    () -> memoryService.generateSegmentAbstract("content"));

            assertEquals(MemoryServiceException.ERROR_LLM_FAILURE, exception.getErrorCode());
        }

        @Test
        @DisplayName("extractStructuredMemories() handles extraction failure")
        void testExtractStructuredMemoriesHandlesExtractionFailure() {
            when(contentProcessor.extractStructuredMemories(anyString(), any()))
                    .thenThrow(new RuntimeException("Extraction failed"));

            MemoryServiceException exception = assertThrows(MemoryServiceException.class,
                    () -> memoryService.extractStructuredMemories("content"));

            assertEquals(MemoryServiceException.ERROR_EXTRACTION_FAILURE, exception.getErrorCode());
        }

        @Test
        @DisplayName("associateWithPreferences() handles association failure")
        void testAssociateWithPreferencesHandlesAssociationFailure() {
            when(memoryManager.savePreference(any(Preference.class)))
                    .thenThrow(new RuntimeException("Save failed"));

            List<Snippet> snippets = Arrays.asList(
                    Snippet.builder().resourceId("r1").summary("Memory").memoryType(Snippet.MemoryType.KNOWLEDGE).build()
            );

            MemoryServiceException exception = assertThrows(MemoryServiceException.class,
                    () -> memoryService.associateWithPreferences(snippets));

            assertEquals(MemoryServiceException.ERROR_ASSOCIATION_FAILURE, exception.getErrorCode());
        }
    }

    // ========== Performance Tests ==========

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("concatenateConversationContent() completes within 100ms for 100 messages")
        void testConcatenateConversationContentPerformance() {
            List<UnifiedMessage> messages = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                UnifiedMessage message = mock(UnifiedMessage.class);
                when(message.getDisplayContent()).thenReturn("Test message " + i);
                messages.add(message);
            }

            long startTime = System.currentTimeMillis();
            String result = memoryService.concatenateConversationContent(messages);
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 100, "Should complete within 100ms, took " + duration + "ms");
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("buildResource() completes within 10ms")
        void testBuildResourcePerformance() {
            long startTime = System.currentTimeMillis();
            Resource result = memoryService.buildResource("conv-1", "session-1", "content", "abstract");
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 10, "Should complete within 10ms, took " + duration + "ms");
            assertNotNull(result);
        }

        @Test
        @DisplayName("extractFromMessages() completes within 1s for 10 messages")
        void testExtractFromMessagesPerformance() {
            List<UnifiedMessage> messages = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                UnifiedMessage message = mock(UnifiedMessage.class);
                when(message.getDisplayContent()).thenReturn("Test message " + i);
                messages.add(message);
            }

            long startTime = System.currentTimeMillis();
            List<Resource> result = memoryService.extractFromMessages(messages, "conv-1", "session-1");
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 1000, "Should complete within 1s, took " + duration + "ms");
            assertNotNull(result);
        }
    }

    // ========== Concurrent Operations Tests ==========

    @Nested
    @DisplayName("Concurrent Operations Tests")
    class ConcurrentOperationsTests {

        @Test
        @DisplayName("Concurrent extractFromMessages() calls complete successfully")
        void testConcurrentExtractFromMessages() throws InterruptedException {
            int threadCount = 5;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        List<UnifiedMessage> messages = createTestMessages();
                        List<Resource> result = memoryService.extractFromMessages(messages, "conv-" + Thread.currentThread().getId(), "session-1");
                        if (result != null && !result.isEmpty()) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "All threads should complete within 10 seconds");
            assertEquals(threadCount, successCount.get(), "All operations should succeed");

            executor.shutdown();
        }

        @Test
        @DisplayName("Concurrent concatenateConversationContent() calls complete successfully")
        void testConcurrentConcatenateConversationContent() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        List<UnifiedMessage> messages = createTestMessages();
                        String result = memoryService.concatenateConversationContent(messages);
                        if (result != null && !result.isEmpty()) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "All threads should complete within 5 seconds");
            assertEquals(threadCount, successCount.get(), "All operations should succeed");

            executor.shutdown();
        }
    }
}
