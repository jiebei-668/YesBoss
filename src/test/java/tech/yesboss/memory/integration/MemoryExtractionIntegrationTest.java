package tech.yesboss.memory.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.memory.embedding.EmbeddingService;
import tech.yesboss.memory.manager.MemoryManager;
import tech.yesboss.memory.model.Preference;
import tech.yesboss.memory.model.Resource;
import tech.yesboss.memory.model.Snippet;
import tech.yesboss.memory.processor.ConversationSegment;
import tech.yesboss.memory.processor.ContentProcessor;
import tech.yesboss.memory.repository.PreferenceRepository;
import tech.yesboss.memory.repository.ResourceRepository;
import tech.yesboss.memory.repository.SnippetRepository;
import tech.yesboss.memory.service.MemoryService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive integration tests for the complete Memory Extraction Process.
 *
 * <p>This test class verifies the end-to-end flow from message input to storage,
 * including:</p>
 * <ul>
 *   <li>Conversation content concatenation</li>
 *   <li>Conversation segmentation</li>
 *   <li>Segment abstract generation</li>
 *   <li>Resource creation and storage with embeddings</li>
 *   <li>Structured memory extraction</li>
 *   <li>Snippet creation and storage with embeddings</li>
 *   <li>Preference identification and association</li>
 * </ul>
 *
 * <p><b>Reference:</b> docs_memory/时序图3.0/01-自动触发记忆提取流程.md</p>
 */
@DisplayName("Complete Memory Extraction Process Integration Tests")
public class MemoryExtractionIntegrationTest {

    // Mock dependencies
    private ContentProcessor contentProcessor;
    private EmbeddingService embeddingService;
    private MemoryManager memoryManager;
    private ResourceRepository resourceRepository;
    private SnippetRepository snippetRepository;
    private PreferenceRepository preferenceRepository;

    // Services to test
    private MemoryService memoryService;

    // Test data
    private List<UnifiedMessage> testMessages;
    private List<ConversationSegment> testSegments;
    private List<String> testStructuredMemories;
    private float[] testEmbedding;

    @BeforeEach
    void setUp() {
        // Initialize mocks
        contentProcessor = mock(ContentProcessor.class);
        embeddingService = mock(EmbeddingService.class);
        memoryManager = mock(MemoryManager.class);
        resourceRepository = mock(ResourceRepository.class);
        snippetRepository = mock(SnippetRepository.class);
        preferenceRepository = mock(PreferenceRepository.class);

        // Create services
        memoryService = new tech.yesboss.memory.service.MemoryServiceImpl(
                contentProcessor,
                memoryManager,
                resourceRepository,
                snippetRepository,
                preferenceRepository,
                embeddingService
        );

        // Initialize test data
        testEmbedding = createTestEmbedding();
        testMessages = createTestMessages();
        testSegments = createTestSegments();
        testStructuredMemories = createTestStructuredMemories();

        // Configure default mock behaviors
        configureDefaultMockBehaviors();
    }

    @AfterEach
    void tearDown() {
        if (memoryService != null) {
            ((tech.yesboss.memory.service.MemoryServiceImpl) memoryService).shutdown();
        }
    }

    // ========== Helper Methods ==========

    private float[] createTestEmbedding() {
        float[] embedding = new float[1536];
        Arrays.fill(embedding, 0.1f);
        return embedding;
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

    private List<ConversationSegment> createTestSegments() {
        List<ConversationSegment> segments = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            ConversationSegment segment = new ConversationSegment();
            segment.setContent("Test segment content " + i);
            segment.setStartIndex(i * 100);
            segment.setEndIndex((i + 1) * 100);
            segments.add(segment);
        }
        return segments;
    }

    private List<String> createTestStructuredMemories() {
        return Arrays.asList(
                "User prefers code examples in Java",
                "User is interested in memory optimization",
                "User likes detailed explanations"
        );
    }

    private void configureDefaultMockBehaviors() {
        // ContentProcessor mocks
        when(contentProcessor.segmentConversation(anyString()))
                .thenReturn(testSegments);
        when(contentProcessor.generateSegmentAbstract(anyString()))
                .thenReturn("Test abstract for segment");
        when(contentProcessor.extractStructuredMemories(anyString(), any()))
                .thenReturn(testStructuredMemories);
        when(contentProcessor.identifyPreferencesForSnippet(anyString(), anyList()))
                .thenReturn(Collections.emptyList());

        // EmbeddingService mocks
        when(embeddingService.generateEmbedding(anyString()))
                .thenReturn(testEmbedding);

        // MemoryManager mocks
        when(memoryManager.saveResource(any(Resource.class)))
                .thenAnswer(invocation -> {
                    Resource resource = invocation.getArgument(0);
                    if (resource.getId() == null) {
                        resource.setId("resource-" + System.currentTimeMillis());
                    }
                    return resource;
                });
        when(memoryManager.saveSnippet(any(Snippet.class)))
                .thenAnswer(invocation -> {
                    Snippet snippet = invocation.getArgument(0);
                    if (snippet.getId() == null) {
                        snippet.setId("snippet-" + System.currentTimeMillis());
                    }
                    return snippet;
                });
        when(memoryManager.isAvailable())
                .thenReturn(true);

        // Repository mocks
        when(preferenceRepository.findById(anyString()))
                .thenReturn(Optional.empty());
        when(resourceRepository.findResourcesWithoutEmbedding())
                .thenReturn(Collections.emptyList());
    }

    // ========== Module Integration Tests ==========

    @Nested
    @DisplayName("Module Integration Tests")
    class ModuleIntegrationTests {

        @Test
        @DisplayName("Complete extraction flow processes messages correctly")
        void testCompleteExtractionFlow() {
            // Act
            List<Resource> resources = memoryService.extractFromMessages(
                    testMessages,
                    "conv-1",
                    "session-1"
            );

            // Assert
            assertNotNull(resources);
            assertFalse(resources.isEmpty());

            // Verify the complete flow
            InOrder inOrder = inOrder(contentProcessor, memoryManager, embeddingService);

            // Step 1: Conversation segmentation
            inOrder.verify(contentProcessor).segmentConversation(anyString());

            // Step 2: Generate abstracts for each segment
            inOrder.verify(contentProcessor, times(testSegments.size()))
                    .generateSegmentAbstract(anyString());

            // Step 3: Save resources with embeddings
            inOrder.verify(memoryManager, times(testSegments.size()))
                    .saveResource(any(Resource.class));

            inOrder.verify(embeddingService, atLeast(testSegments.size()))
                    .generateEmbedding(anyString());

            // Step 4: Extract structured memories
            verify(contentProcessor, times(testSegments.size()))
                    .extractStructuredMemories(anyString(), eq(Snippet.MemoryType.PROFILE));
            verify(contentProcessor, times(testSegments.size()))
                    .extractStructuredMemories(anyString(), eq(Snippet.MemoryType.EVENT));
            verify(contentProcessor, times(testSegments.size()))
                    .extractStructuredMemories(anyString(), eq(Snippet.MemoryType.KNOWLEDGE));
            verify(contentProcessor, times(testSegments.size()))
                    .extractStructuredMemories(anyString(), eq(Snippet.MemoryType.BEHAVIOR));
            verify(contentProcessor, times(testSegments.size()))
                    .extractStructuredMemories(anyString(), eq(Snippet.MemoryType.SKILL));
            verify(contentProcessor, times(testSegments.size()))
                    .extractStructuredMemories(anyString(), eq(Snippet.MemoryType.TOOL));

            // Step 5: Save snippets
            verify(memoryManager, atLeast(testStructuredMemories.size()))
                    .saveSnippet(any(Snippet.class));
        }

        @Test
        @DisplayName("Data flow validation through all components")
        void testDataFlowValidation() {
            // Act
            List<Resource> resources = memoryService.extractFromMessages(
                    testMessages,
                    "conv-1",
                    "session-1"
            );

            // Assert - Verify data flows correctly
            ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
            verify(contentProcessor).segmentConversation(contentCaptor.capture());

            String concatenatedContent = contentCaptor.getValue();
            assertNotNull(concatenatedContent);
            assertTrue(testMessages.stream()
                    .allMatch(msg -> concatenatedContent.contains(msg.getDisplayContent())));

            // Verify resources are created with correct metadata
            ArgumentCaptor<Resource> resourceCaptor = ArgumentCaptor.forClass(Resource.class);
            verify(memoryManager, atLeastOnce()).saveResource(resourceCaptor.capture());

            List<Resource> savedResources = resourceCaptor.getAllValues();
            assertTrue(savedResources.stream()
                    .allMatch(r -> "conv-1".equals(r.getConversationId())));
            assertTrue(savedResources.stream()
                    .allMatch(r -> "session-1".equals(r.getSessionId())));
        }

        @Test
        @DisplayName("Interface call sequence verification")
        void testInterfaceCallSequence() {
            // Act
            memoryService.extractFromMessages(testMessages, "conv-1", "session-1");

            // Assert - Verify correct call sequence
            InOrder inOrder = inOrder(
                    contentProcessor,
                    embeddingService,
                    memoryManager
            );

            // 1. Segment conversation
            inOrder.verify(contentProcessor).segmentConversation(anyString());

            // 2. Generate abstracts for each segment
            inOrder.verify(contentProcessor, atLeastOnce())
                    .generateSegmentAbstract(anyString());

            // 3. Generate embeddings for resources
            inOrder.verify(embeddingService, atLeastOnce())
                    .generateEmbedding(anyString());

            // 4. Save resources
            inOrder.verify(memoryManager, atLeastOnce())
                    .saveResource(any(Resource.class));

            // 5. Extract structured memories
            inOrder.verify(contentProcessor, atLeastOnce())
                    .extractStructuredMemories(anyString(), any());

            // 6. Save snippets
            inOrder.verify(memoryManager, atLeastOnce())
                    .saveSnippet(any(Snippet.class));
        }
    }

    // ========== Performance Tests ==========

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Complete extraction completes within 1s")
        void testCompleteExtractionPerformance() {
            // Act
            long startTime = System.currentTimeMillis();
            List<Resource> resources = memoryService.extractFromMessages(
                    testMessages,
                    "conv-1",
                    "session-1"
            );
            long duration = System.currentTimeMillis() - startTime;

            // Assert
            assertTrue(duration < 1000,
                    "Complete extraction should complete within 1s, took " + duration + "ms");
            assertNotNull(resources);
        }

        @Test
        @DisplayName("Throughput test - multiple extractions")
        void testThroughput() {
            // Arrange - Create many messages
            List<UnifiedMessage> messages = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                UnifiedMessage msg = mock(UnifiedMessage.class);
                when(msg.getDisplayContent()).thenReturn("Test message " + i);
                messages.add(msg);
            }

            // Act
            long startTime = System.currentTimeMillis();
            List<Resource> resources = memoryService.extractFromMessages(
                    messages,
                    "conv-1",
                    "session-1"
            );
            long duration = System.currentTimeMillis() - startTime;

            // Assert
            assertNotNull(resources);
            assertTrue(duration < 5000,
                    "100 messages should be processed within 5s, took " + duration + "ms");
        }
    }

    // ========== Exception Scenario Tests ==========

    @Nested
    @DisplayName("Exception Scenario Tests")
    class ExceptionScenarioTests {

        @Test
        @DisplayName("Segmentation failure triggers exception")
        void testSegmentationFailure() {
            // Arrange
            when(contentProcessor.segmentConversation(anyString()))
                    .thenThrow(new RuntimeException("Segmentation failed"));

            // Act & Assert
            assertThrows(Exception.class, () ->
                    memoryService.extractFromMessages(testMessages, "conv-1", "session-1")
            );
        }

        @Test
        @DisplayName("Embedding service failure is handled")
        void testEmbeddingServiceFailure() {
            // Arrange
            when(embeddingService.generateEmbedding(anyString()))
                    .thenThrow(new RuntimeException("Embedding service unavailable"));

            // Act & Assert
            assertThrows(Exception.class, () ->
                    memoryService.extractFromMessages(testMessages, "conv-1", "session-1")
            );
        }

        @Test
        @DisplayName("Service unavailability is detected")
        void testServiceUnavailability() {
            // Arrange
            when(memoryManager.isAvailable())
                    .thenReturn(false);

            // Act - Should handle unavailability
            assertFalse(memoryService.isAvailable());
        }
    }

    // ========== Data Integrity Tests ==========

    @Nested
    @DisplayName("Data Integrity Tests")
    class DataIntegrityTests {

        @Test
        @DisplayName("Resource data correctness is maintained")
        void testResourceDataCorrectness() {
            // Act
            List<Resource> resources = memoryService.extractFromMessages(
                    testMessages,
                    "conv-1",
                    "session-1"
            );

            // Assert
            assertTrue(resources.stream().allMatch(r ->
                    r.getConversationId() != null &&
                    r.getSessionId() != null &&
                    r.getContent() != null &&
                    r.getAbstract() != null &&
                    r.getTimestamp() > 0
            ));

            assertEquals("conv-1", resources.get(0).getConversationId());
            assertEquals("session-1", resources.get(0).getSessionId());
        }

        @Test
        @DisplayName("Snippet-Resource relationship is preserved")
        void testSnippetResourceRelationship() {
            // Act
            List<Resource> resources = memoryService.extractFromMessages(
                    testMessages,
                    "conv-1",
                    "session-1"
            );

            // Assert - Verify snippets are linked to resources
            ArgumentCaptor<Snippet> snippetCaptor = ArgumentCaptor.forClass(Snippet.class);
            verify(memoryManager, atLeastOnce()).saveSnippet(snippetCaptor.capture());

            List<Snippet> snippets = snippetCaptor.getAllValues();
            assertTrue(snippets.stream().allMatch(s ->
                    s.getResourceId() != null && !s.getResourceId().isEmpty()
            ));
        }

        @Test
        @DisplayName("Embedding vectors are correctly generated")
        void testEmbeddingGeneration() {
            // Act
            memoryService.extractFromMessages(testMessages, "conv-1", "session-1");

            // Assert - Verify embeddings are generated
            ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
            verify(embeddingService, atLeastOnce())
                    .generateEmbedding(textCaptor.capture());

            List<String> textsForEmbedding = textCaptor.getAllValues();
            assertNotNull(textsForEmbedding);
            assertFalse(textsForEmbedding.isEmpty());
        }
    }

    // ========== Concurrency Tests ==========

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent extraction requests are handled safely")
        void testConcurrentExtractionRequests() throws InterruptedException {
            // Arrange
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // Act - Submit concurrent extraction requests
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        List<UnifiedMessage> messages = createTestMessages();
                        List<Resource> resources = memoryService.extractFromMessages(
                                messages,
                                "conv-" + threadId,
                                "session-" + threadId
                        );
                        if (resources != null && !resources.isEmpty()) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Assert
            assertTrue(latch.await(30, TimeUnit.SECONDS),
                    "All threads should complete within 30 seconds");

            assertEquals(threadCount, successCount.get(),
                    "All concurrent operations should succeed");

            executor.shutdown();
        }

        @Test
        @DisplayName("Thread safety of shared resources")
        void testThreadSafety() throws InterruptedException {
            // Arrange
            int threadCount = 5;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // Act - All threads use the same conversation
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        List<UnifiedMessage> messages = createTestMessages();
                        List<Resource> resources = memoryService.extractFromMessages(
                                messages,
                                "conv-1", // Same conversation
                                "session-1"
                        );
                        if (resources != null) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Assert
            assertTrue(latch.await(30, TimeUnit.SECONDS));
            assertEquals(threadCount, successCount.get());

            executor.shutdown();
        }
    }
}
