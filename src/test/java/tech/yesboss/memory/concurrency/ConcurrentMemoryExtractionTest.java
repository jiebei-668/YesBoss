package tech.yesboss.memory.concurrency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.memory.model.Resource;
import tech.yesboss.domain.message.UnifiedMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrent Memory Extraction Test
 *
 * Validates that the memory extraction system can handle
 * concurrent operations safely and efficiently.
 */
@SpringBootTest
@DisplayName("Concurrent Memory Extraction Tests")
class ConcurrentMemoryExtractionTest {

    @Autowired(required = false)
    private MemoryService memoryService;

    private List<UnifiedMessage> testMessages;

    @BeforeEach
    void setUp() {
        testMessages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            testMessages.add(new UnifiedMessage("user", "Test message " + i));
            testMessages.add(new UnifiedMessage("assistant", "Response " + i));
        }
    }

    @Test
    @DisplayName("Concurrent conversation concatenation - 10 threads")
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentConcatenation() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    String content = memoryService.concatenateConversationContent(testMessages);
                    if (content != null && !content.isEmpty()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount, successCount.get(),
            "All concurrent concatenations should succeed");
        assertEquals(0, errorCount.get(),
            "No errors should occur during concurrent concatenation");
    }

    @Test
    @DisplayName("Concurrent conversation segmentation - 10 threads")
    @Timeout(value = 15, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentSegmentation() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    String content = "Test conversation content for segmentation. " +
                                    "This is message 1. This is message 2. " +
                                    "This is message 3.";
                    var segments = memoryService.segmentConversation(content);
                    if (segments != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount, successCount.get(),
            "All concurrent segmentations should succeed");
    }

    @Test
    @DisplayName("Concurrent resource building - 20 threads")
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentResourceBuilding() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<Resource> resources = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    Resource resource = memoryService.buildResource(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        "Content for thread " + threadId,
                        "Abstract for thread " + threadId
                    );

                    if (resource != null) {
                        resources.add(resource);
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount, successCount.get(),
            "All concurrent resource builds should succeed");
        assertEquals(threadCount, resources.size(),
            "All resources should be created");

        // Verify all resources are unique
        long uniqueIds = resources.stream()
            .map(Resource::getId)
            .distinct()
            .count();
        assertEquals(threadCount, uniqueIds,
            "All resources should have unique IDs");
    }

    @Test
    @DisplayName("Concurrent memory extraction - 5 threads")
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentMemoryExtraction() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    String conversationId = "conv-thread-" + threadId;
                    String sessionId = "session-thread-" + threadId;

                    List<Resource> resources = memoryService.extractFromMessages(
                        testMessages,
                        conversationId,
                        sessionId
                    );

                    if (resources != null && !resources.isEmpty()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(successCount.get() > 0,
            "At least some extractions should succeed");
    }

    @Test
    @DisplayName("Concurrent abstract generation - 15 threads")
    @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentAbstractGeneration() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        int threadCount = 15;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    String content = "Content for abstract generation in thread " + threadId +
                                    ". This is a longer text to summarize.";
                    String abstractText = memoryService.generateSegmentAbstract(content);

                    if (abstractText != null && !abstractText.isEmpty()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount, successCount.get(),
            "All concurrent abstract generations should succeed");
    }

    @Test
    @DisplayName("Concurrent memory type extraction - 10 threads")
    @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentMemoryTypeExtraction() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    String content = "User John is a software engineer who prefers Python. " +
                                    "He has been working for 5 years and knows Java as well.";
                    var profiles = memoryService.extractMemoriesByType(
                        content,
                        tech.yesboss.memory.model.Snippet.MemoryType.PROFILE
                    );

                    if (profiles != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount, successCount.get(),
            "All concurrent memory type extractions should succeed");
    }

    @Test
    @DisplayName("Mixed concurrent operations - 10 threads")
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testMixedConcurrentOperations() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int operation = i % 3;  // 3 different operations
            executor.submit(() -> {
                try {
                    switch (operation) {
                        case 0:  // Concatenation
                            String content = memoryService.concatenateConversationContent(testMessages);
                            if (content != null) successCount.incrementAndGet();
                            break;

                        case 1:  // Resource building
                            Resource resource = memoryService.buildResource(
                                UUID.randomUUID().toString(),
                                UUID.randomUUID().toString(),
                                "Content", "Abstract"
                            );
                            if (resource != null) successCount.incrementAndGet();
                            break;

                        case 2:  // Segmentation
                            var segments = memoryService.segmentConversation("Test content for segmentation");
                            if (segments != null) successCount.incrementAndGet();
                            break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount, successCount.get(),
            "All mixed concurrent operations should succeed");
    }

    @Test
    @DisplayName("High load test - 50 concurrent operations")
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testHighLoadConcurrentOperations() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // Perform simple operation
                    String content = memoryService.concatenateConversationContent(
                        List.of(new UnifiedMessage("user", "Message " + threadId))
                    );

                    if (content != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(120, TimeUnit.SECONDS);
        executor.shutdown();

        long duration = System.currentTimeMillis() - startTime;

        assertEquals(threadCount, successCount.get(),
            "All high load operations should succeed");

        // Performance check: should complete within reasonable time
        assertTrue(duration < 60000,
            "High load test should complete within 60 seconds, took: " + duration + "ms");

        System.out.println("High load test completed: " + threadCount +
            " operations in " + duration + "ms");
        System.out.println("Average latency: " + (duration / threadCount) + "ms per operation");
    }

    @Test
    @DisplayName("Thread safety verification - shared state consistency")
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testThreadSafetySharedState() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ConcurrentHashMap<String, Resource> resourceMap = new ConcurrentHashMap<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // Create resources with same conversation ID but different session IDs
                    String conversationId = "shared-conversation";
                    String sessionId = "session-" + threadId;

                    Resource resource = memoryService.buildResource(
                        conversationId,
                        sessionId,
                        "Content from thread " + threadId,
                        "Abstract from thread " + threadId
                    );

                    if (resource != null) {
                        resourceMap.put(sessionId, resource);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount, resourceMap.size(),
            "All threads should create unique resources");

        // Verify conversation ID is consistent
        resourceMap.values().forEach(resource ->
            assertEquals("shared-conversation", resource.getConversationId(),
                "All resources should have the same conversation ID")
        );
    }

    @Test
    @DisplayName("Concurrent error handling - exception isolation")
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentErrorHandling() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    if (threadId % 3 == 0) {
                        // Simulate error condition
                        throw new RuntimeException("Simulated error in thread " + threadId);
                    } else {
                        // Normal operation
                        String content = memoryService.concatenateConversationContent(
                            List.of(new UnifiedMessage("user", "Message " + threadId))
                        );
                        if (content != null) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    // Error should not affect other threads
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Some threads should succeed, some should fail
        assertTrue(successCount.get() > 0,
            "Some operations should succeed despite errors in other threads");
        assertTrue(errorCount.get() > 0,
            "Some operations should fail as expected");
    }

    @Test
    @DisplayName("Concurrent availability checks")
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentAvailabilityChecks() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger consistentCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    boolean available = memoryService.isAvailable();
                    // All threads should get same result
                    if (available) {
                        consistentCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // All threads should get consistent results
        assertTrue(consistentCount.get() == threadCount || consistentCount.get() == 0,
            "All availability checks should return consistent result");
    }
}
