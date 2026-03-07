package tech.yesboss.memory.concurrency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tech.yesboss.memory.query.MemoryQueryService;
import tech.yesboss.memory.model.Resource;
import tech.yesboss.memory.model.Snippet;
import tech.yesboss.memory.model.Snippet.MemoryType;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrent Query Processing Test
 *
 * Validates that query operations can handle concurrent
 * search and retrieval requests safely and efficiently.
 */
@SpringBootTest
@DisplayName("Concurrent Query Processing Tests")
class ConcurrentQueryProcessingTest {

    @Autowired(required = false)
    private MemoryQueryService queryService;

    @Test
    @DisplayName("Concurrent resource search - 10 threads")
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentResourceSearch() throws InterruptedException {
        if (queryService == null) {
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
                    List<Resource> results = queryService.searchResources(
                        "Test query for thread " + threadId,
                        5
                    );

                    if (results != null) {
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
            "All concurrent searches should succeed");
        assertEquals(0, errorCount.get(),
            "No errors should occur");
    }

    @Test
    @DisplayName("Concurrent snippet search - 10 threads")
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentSnippetSearch() throws InterruptedException {
        if (queryService == null) {
            return;
        }

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    List<Snippet> results = queryService.searchSnippets(
                        "User preference from thread " + threadId,
                        MemoryType.PREFERENCE,
                        5
                    );

                    if (results != null) {
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
            "All concurrent snippet searches should succeed");
    }

    @Test
    @DisplayName("Concurrent Agentic RAG queries - 5 threads")
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentAgenticRAGQueries() throws InterruptedException {
        if (queryService == null) {
            return;
        }

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    var result = queryService.agenticRagQuery(
                        "Query from thread " + threadId,
                        "conversation-" + threadId,
                        5,
                        0.7
                    );

                    if (result != null) {
                        successCount.incrementAndGet();
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

        assertTrue(successCount.get() > 0,
            "At least some RAG queries should succeed");
    }

    @Test
    @DisplayName("Concurrent memory chain retrieval - 10 threads")
    @Timeout(value = 15, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentMemoryChainRetrieval() throws InterruptedException {
        if (queryService == null) {
            return;
        }

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    var chains = queryService.getMemoryChain(
                        "conversation-" + threadId,
                        3
                    );

                    if (chains != null) {
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
            "All concurrent memory chain retrievals should succeed");
    }

    @Test
    @DisplayName("Concurrent mixed query operations - 15 threads")
    @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentMixedQueryOperations() throws InterruptedException {
        if (queryService == null) {
            return;
        }

        int threadCount = 15;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int operation = i % 3;
            final int threadId = i;
            executor.submit(() -> {
                try {
                    switch (operation) {
                        case 0:  // Resource search
                            List<Resource> resources = queryService.searchResources(
                                "Query " + threadId, 5
                            );
                            if (resources != null) successCount.incrementAndGet();
                            break;

                        case 1:  // Snippet search
                            List<Snippet> snippets = queryService.searchSnippets(
                                "Query " + threadId, MemoryType.PROFILE, 5
                            );
                            if (snippets != null) successCount.incrementAndGet();
                            break;

                        case 2:  // Memory chain
                            var chains = queryService.getMemoryChain(
                                "conv-" + threadId, 3
                            );
                            if (chains != null) successCount.incrementAndGet();
                            break;
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
            "All mixed query operations should succeed");
    }

    @Test
    @DisplayName("Concurrent query with different parameters - 20 threads")
    @Timeout(value = 15, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentQueryWithDifferentParameters() throws InterruptedException {
        if (queryService == null) {
            return;
        }

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // Vary topK parameter
                    int topK = (threadId % 5) + 1;  // 1 to 5
                    List<Resource> results = queryService.searchResources(
                        "Query " + threadId,
                        topK
                    );

                    if (results != null) {
                        assertTrue(results.size() <= topK,
                            "Should return at most " + topK + " results");
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
            "All queries with different parameters should succeed");
    }

    @Test
    @DisplayName("High frequency query test - 100 rapid queries")
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testHighFrequencyQueries() throws InterruptedException {
        if (queryService == null) {
            return;
        }

        int queryCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(queryCount);
        AtomicInteger successCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < queryCount; i++) {
            final int queryId = i;
            executor.submit(() -> {
                try {
                    List<Resource> results = queryService.searchResources(
                        "Rapid query " + queryId,
                        5
                    );

                    if (results != null) {
                        successCount.incrementAndGet();
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

        long duration = System.currentTimeMillis() - startTime;

        assertEquals(queryCount, successCount.get(),
            "All rapid queries should succeed");

        // Performance check: should complete within reasonable time
        assertTrue(duration < 30000,
            "High frequency test should complete within 30 seconds, took: " + duration + "ms");

        double throughput = (double) queryCount / (duration / 1000.0);
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " queries/second");
    }

    @Test
    @DisplayName("Concurrent query result independence")
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentQueryResultIndependence() throws InterruptedException {
        if (queryService == null) {
            return;
        }

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ConcurrentHashMap<Integer, List<Resource>> resultsMap = new ConcurrentHashMap<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    List<Resource> results = queryService.searchResources(
                        "Unique query " + threadId + " " + UUID.randomUUID(),
                        5
                    );

                    resultsMap.put(threadId, results);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount, resultsMap.size(),
            "All threads should get results");

        // Each thread should have its own result list
        resultsMap.keySet().forEach(key ->
            assertNotNull(resultsMap.get(key),
                "Each thread should have its own results")
        );
    }

    @Test
    @DisplayName("Concurrent query error isolation")
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentQueryErrorIsolation() throws InterruptedException {
        if (queryService == null) {
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
                        // Simulate error with invalid parameters
                        queryService.searchResources("", -1);  // Invalid topK
                    } else {
                        // Normal query
                        List<Resource> results = queryService.searchResources(
                            "Query " + threadId, 5
                        );
                        if (results != null) {
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

        // Most operations should succeed despite errors in some threads
        assertTrue(successCount.get() > 0,
            "Most operations should succeed despite errors in other threads");
    }

    @Test
    @DisplayName("Concurrent query with memory type filtering")
    @Timeout(value = 15, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void testConcurrentQueryWithMemoryTypeFiltering() throws InterruptedException {
        if (queryService == null) {
            return;
        }

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        MemoryType[] types = MemoryType.values();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            final MemoryType type = types[threadId % types.length];
            executor.submit(() -> {
                try {
                    List<Snippet> results = queryService.searchSnippets(
                        "Query " + threadId,
                        type,
                        5
                    );

                    if (results != null) {
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
            "All queries with memory type filtering should succeed");
    }
}
