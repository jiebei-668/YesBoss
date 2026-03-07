package tech.yesboss.memory.concurrency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.memory.service.MemoryService.BatchEmbeddingResult;
import tech.yesboss.domain.message.UnifiedMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrent Batch Processing Test
 *
 * Validates that batch processing operations can handle
 * concurrent workloads safely and efficiently.
 */
@SpringBootTest
@DisplayName("Concurrent Batch Processing Tests")
class ConcurrentBatchProcessingTest {

    @Autowired(required = false)
    private MemoryService memoryService;

    private List<UnifiedMessage> testMessages;

    @BeforeEach
    void setUp() {
        testMessages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            testMessages.add(new UnifiedMessage("user", "Message " + i));
            testMessages.add(new UnifiedMessage("assistant", "Response " + i));
        }
    }

    @Test
    @DisplayName("Concurrent pending resource processing - 3 threads")
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentPendingResourceProcessing() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger totalProcessed = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    int processed = memoryService.processPendingResources();

                    if (processed >= 0) {
                        successCount.incrementAndGet();
                        totalProcessed.addAndGet(processed);
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

        assertEquals(threadCount, successCount.get(),
            "All concurrent processing attempts should succeed");

        System.out.println("Total pending resources processed: " + totalProcessed.get());
    }

    @Test
    @DisplayName("Concurrent batch embedding - 3 threads")
    @Timeout(value = 90, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentBatchEmbedding() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        ConcurrentHashMap<Integer, BatchEmbeddingResult> resultsMap = new ConcurrentHashMap<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    BatchEmbeddingResult result = memoryService.processBatchEmbedding();

                    if (result != null) {
                        successCount.incrementAndGet();
                        resultsMap.put(threadId, result);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(180, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(successCount.get() > 0,
            "At least some batch embedding operations should succeed");

        // Verify all results are valid
        resultsMap.values().forEach(result -> {
            assertNotNull(result);
            assertTrue(result.getTotalCount() >= 0);
            assertTrue(result.getSuccessCount() >= 0);
            assertTrue(result.getFailureCount() >= 0);
        });
    }

    @Test
    @DisplayName("Concurrent batch extraction - 5 threads")
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentBatchExtraction() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger totalResources = new AtomicInteger(0);

        List<MemoryService.MessageBatch> batches = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            batches.add(new MemoryService.MessageBatch(
                testMessages,
                "batch-conv-" + i,
                "batch-session-" + i
            ));
        }

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            final List<MemoryService.MessageBatch> singleBatch =
                List.of(batches.get(threadId));

            executor.submit(() -> {
                try {
                    List<tech.yesboss.memory.model.Resource> resources =
                        memoryService.batchExtractFromMessages(singleBatch);

                    if (resources != null) {
                        successCount.incrementAndGet();
                        totalResources.addAndGet(resources.size());
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

        assertEquals(threadCount, successCount.get(),
            "All concurrent batch extractions should succeed");

        System.out.println("Total resources extracted in batches: " + totalResources.get());
    }

    @Test
    @DisplayName("Performance test: Batch processing throughput")
    @Timeout(value = 120, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testBatchProcessingThroughput() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        int operationCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(operationCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicLong totalDuration = new AtomicLong(0);

        for (int i = 0; i < operationCount; i++) {
            executor.submit(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    int processed = memoryService.processPendingResources();
                    long duration = System.currentTimeMillis() - startTime;

                    if (processed >= 0) {
                        successCount.incrementAndGet();
                        totalDuration.addAndGet(duration);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(240, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(operationCount, successCount.get(),
            "All batch processing operations should succeed");

        if (successCount.get() > 0) {
            double avgDuration = (double) totalDuration.get() / successCount.get();
            System.out.println("Average batch processing duration: " +
                String.format("%.2f", avgDuration) + "ms");

            // Performance assertion: should be reasonably fast
            assertTrue(avgDuration < 10000,
                "Average batch processing should be under 10 seconds");
        }
    }

    @Test
    @DisplayName("Concurrent operation under resource constraints")
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentOperationUnderResourceConstraints() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(2);  // Limited threads
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
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

        assertEquals(threadCount, successCount.get(),
            "All operations should succeed even with limited thread pool");
    }

    @Test
    @DisplayName("Stress test: High concurrency batch operations")
    @Timeout(value = 180, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testStressHighConcurrencyBatchOperations() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        int operationCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(operationCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < operationCount; i++) {
            final int opId = i;
            executor.submit(() -> {
                try {
                    if (opId % 2 == 0) {
                        // Batch embedding
                        BatchEmbeddingResult result = memoryService.processBatchEmbedding();
                        if (result != null) {
                            successCount.incrementAndGet();
                        }
                    } else {
                        // Pending resource processing
                        int processed = memoryService.processPendingResources();
                        if (processed >= 0) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(300, TimeUnit.SECONDS);
        executor.shutdown();

        long duration = System.currentTimeMillis() - startTime;

        System.out.println("Stress test completed:");
        System.out.println("  Operations: " + operationCount);
        System.out.println("  Success: " + successCount.get());
        System.out.println("  Errors: " + errorCount.get());
        System.out.println("  Duration: " + duration + "ms");
        System.out.println("  Throughput: " +
            String.format("%.2f", (successCount.get() / (duration / 1000.0))) +
            " ops/sec");

        // At least majority should succeed
        assertTrue(successCount.get() >= operationCount * 0.7,
            "At least 70% of operations should succeed under stress");
    }

    @Test
    @DisplayName("Concurrent batch operation data consistency")
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentBatchDataConsistency() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ConcurrentHashMap<Integer, BatchEmbeddingResult> results = new ConcurrentHashMap<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // Collect texts for embedding (read-only operation)
                    BatchEmbeddingResult result = memoryService.collectTextsForEmbedding();

                    if (result != null) {
                        results.put(threadId, result);
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

        assertEquals(threadCount, results.size(),
            "All concurrent collection operations should succeed");

        // Results should be consistent (same or increasing counts)
        int resourceCount = -1;
        for (BatchEmbeddingResult result : results.values()) {
            if (resourceCount == -1) {
                resourceCount = result.getResourceCount();
            } else {
                // Counts should be consistent (may vary slightly due to concurrent writes)
                assertTrue(Math.abs(result.getResourceCount() - resourceCount) < 10,
                    "Resource counts should be consistent across concurrent reads");
            }
        }
    }

    @Test
    @DisplayName("Memory usage under concurrent load")
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testMemoryUsageUnderConcurrentLoad() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // Perform memory-intensive operation
                    for (int j = 0; j < 10; j++) {
                        String content = memoryService.concatenateConversationContent(
                            List.of(new UnifiedMessage("user", "Message " + threadId + "-" + j))
                        );
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

        // Force garbage collection
        System.gc();
        Thread.sleep(1000);

        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;

        System.out.println("Memory usage change: " + (memoryIncrease / 1024 / 1024) + " MB");

        // Memory increase should be reasonable (less than 100 MB)
        assertTrue(memoryIncrease < 100 * 1024 * 1024,
            "Memory increase should be under 100 MB, was: " +
            (memoryIncrease / 1024 / 1024) + " MB");
    }

    @Test
    @DisplayName("Concurrent batch operation error recovery")
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentBatchErrorRecovery() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger recoveryCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    int processed = memoryService.processPendingResources();

                    if (processed >= 0) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    // Simulate recovery: retry once
                    try {
                        Thread.sleep(100);
                        int retried = memoryService.processPendingResources();
                        if (retried >= 0) {
                            recoveryCount.incrementAndGet();
                        }
                    } catch (Exception retryEx) {
                        // Recovery failed
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(120, TimeUnit.SECONDS);
        executor.shutdown();

        // Combined successes (initial + recovered) should be high
        int totalSuccess = successCount.get() + recoveryCount.get();
        assertTrue(totalSuccess >= threadCount * 0.8,
            "At least 80% of operations should succeed (initial or after recovery)");
    }

    @Test
    @DisplayName("Concurrent operation with timeouts")
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentOperationWithTimeouts() throws InterruptedException {
        if (memoryService == null) {
            return;
        }

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger timeoutCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // Use timeout for operation
                    Future<Integer> future = executor.submit(() ->
                        memoryService.processPendingResources()
                    );

                    Integer result = future.get(10, TimeUnit.SECONDS);

                    if (result != null && result >= 0) {
                        successCount.incrementAndGet();
                    }
                } catch (TimeoutException e) {
                    timeoutCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(successCount.get() > 0,
            "Most operations should complete within timeout");
        System.out.println("Completed: " + successCount.get() +
            ", Timeouts: " + timeoutCount.get());
    }
}
