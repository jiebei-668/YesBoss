package tech.yesboss.memory.e2e;

import org.junit.jupiter.api.*;
import tech.yesboss.config.ConfigurationManager;
import tech.yesboss.config.YesBossConfig;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.domain.message.UnifiedMessage.Role;
import tech.yesboss.domain.message.UnifiedMessage.PayloadFormat;
import tech.yesboss.memory.model.Resource;
import tech.yesboss.memory.model.Snippet;
import tech.yesboss.memory.repository.ResourceRepositoryImpl;
import tech.yesboss.memory.repository.SnippetRepositoryImpl;
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.memory.service.MemoryServiceImpl;
import tech.yesboss.memory.manager.MemoryManagerImpl;
import tech.yesboss.memory.processor.ContentProcessor;
import tech.yesboss.memory.processor.ZhipuContentProcessorImpl;
import tech.yesboss.memory.embedding.EmbeddingService;
import tech.yesboss.memory.embedding.EmbeddingServiceFactory;
import tech.yesboss.persistence.datasource.SimpleDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance Benchmark Test
 *
 * <p>This test verifies the system's performance under various loads,
 * including:</p>
 *
 * <ul>
 *   <li><b>Single Operation Performance:</b> Baseline performance for individual operations</li>
 *   <li><b>Bulk Operation Performance:</b> Performance for processing multiple items</li>
 *   <li><b>Concurrent Load Performance:</b> Performance under concurrent access</li>
 *   <li><b>Scalability:</b> Performance as data size increases</li>
 *   <li><b>Resource Utilization:</b> Memory and CPU usage under load</li>
 * </ul>
 *
 * <p><b>Test Frameworks:</b> JUnit 5</p>
 * <p><b>Reference:</b> docs_memory/记忆持久化模块v3.0.md</p>
 */
@DisplayName("Performance Benchmark Test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PerformanceBenchmarkTest {

    private static DataSource dataSource;
    private static ResourceRepositoryImpl resourceRepository;
    private static SnippetRepositoryImpl snippetRepository;

    private MemoryService memoryService;

    @BeforeAll
    static void setUpOnce() throws Exception {
        System.out.println("========================================");
        System.out.println("Setting up Performance Benchmark Test");
        System.out.println("========================================");

        dataSource = new SimpleDataSource("jdbc:sqlite::memory:");
        resourceRepository = new ResourceRepositoryImpl(dataSource);
        snippetRepository = new SnippetRepositoryImpl(dataSource);

        System.out.println("Database initialized successfully");
        System.out.println("========================================");
    }

    @AfterAll
    static void tearDownOnce() {
        System.out.println("========================================");
        System.out.println("Tearing down Performance Benchmark Test");
        System.out.println("========================================");

        if (dataSource instanceof SimpleDataSource) {
            ((SimpleDataSource) dataSource).close();
        }

        System.out.println("Teardown completed");
        System.out.println("========================================");
    }

    @BeforeEach
    void setUp() {
        try {
            MemoryManagerImpl memoryManager = new MemoryManagerImpl(
                    resourceRepository,
                    snippetRepository,
                    null
            );

            EmbeddingService embeddingService = EmbeddingServiceFactory.getInstance(null);
            ContentProcessor contentProcessor = new ZhipuContentProcessorImpl(null);

            memoryService = new MemoryServiceImpl(
                    contentProcessor,
                    memoryManager,
                    resourceRepository,
                    snippetRepository,
                    null,
                    embeddingService
            );
        } catch (Exception e) {
            System.err.println("Warning: Could not initialize all components: " + e.getMessage());
        }
    }

    // ==================== Test 1: Baseline Performance ====================

    @Test
    @Order(1)
    @DisplayName("Test 1: Baseline single operation performance")
    void testBaselineSingleOperationPerformance() {
        System.out.println("\n========================================");
        System.out.println("Test 1: Baseline Single Operation Performance");
        System.out.println("========================================");

        List<UnifiedMessage> messages = createTestMessages(5);
        String convId = "perf-test-conv-" + System.currentTimeMillis();
        String sessionId = "perf-test-session-" + System.currentTimeMillis();

        // Measure memory extraction time
        long startTime = System.nanoTime();
        List<Resource> resources = memoryService.extractFromMessages(messages, convId, sessionId);
        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        System.out.println("Single extraction time: " + durationMs + "ms");
        System.out.println("Resources extracted: " + resources.size());

        assertTrue(durationMs < 1000,
                "Single operation should complete within 1s, took: " + durationMs + "ms");
        assertNotNull(resources);
        assertFalse(resources.isEmpty());

        System.out.println("✓ Baseline performance test PASSED");
        System.out.println("========================================");
    }

    // ==================== Test 2: Bulk Operation Performance ====================

    @Test
    @Order(2)
    @DisplayName("Test 2: Bulk operation performance")
    void testBulkOperationPerformance() {
        System.out.println("\n========================================");
        System.out.println("Test 2: Bulk Operation Performance");
        System.out.println("========================================");

        int batchSize = 20;
        List<Long> durations = new ArrayList<>();

        for (int i = 0; i < batchSize; i++) {
            List<UnifiedMessage> messages = createTestMessages(5);
            String convId = "perf-bulk-" + i + "-" + System.currentTimeMillis();
            String sessionId = "perf-session-" + i + "-" + System.currentTimeMillis();

            long startTime = System.nanoTime();
            memoryService.extractFromMessages(messages, convId, sessionId);
            long endTime = System.nanoTime();
            durations.add((endTime - startTime) / 1_000_000);
        }

        long avgDuration = durations.stream().mapToLong(Long::longValue).sum() / batchSize;
        long maxDuration = durations.stream().mapToLong(Long::longValue).max().orElse(0);
        long minDuration = durations.stream().mapToLong(Long::longValue).min().orElse(0);

        System.out.println("Average operation time: " + avgDuration + "ms");
        System.out.println("Max operation time: " + maxDuration + "ms");
        System.out.println("Min operation time: " + minDuration + "ms");

        assertTrue(avgDuration < 1000,
                "Average operation time should be < 1s, got: " + avgDuration + "ms");
        assertTrue(maxDuration < 2000,
                "Max operation time should be < 2s, got: " + maxDuration + "ms");

        System.out.println("✓ Bulk operation performance test PASSED");
        System.out.println("========================================");
    }

    // ==================== Test 3: Concurrent Load Performance ====================

    @Test
    @Order(3)
    @DisplayName("Test 3: Concurrent load performance")
    void testConcurrentLoadPerformance() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("Test 3: Concurrent Load Performance");
        System.out.println("========================================");

        int threadCount = 10;
        int operationsPerThread = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong totalDuration = new AtomicLong(0);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        long testStartTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        List<UnifiedMessage> messages = createTestMessages(5);
                        String convId = "perf-concurrent-" + threadId + "-" + j + "-" + System.currentTimeMillis();
                        String sessionId = "perf-session-" + threadId + "-" + j;

                        long startTime = System.nanoTime();
                        memoryService.extractFromMessages(messages, convId, sessionId);
                        long endTime = System.nanoTime();
                        totalDuration.addAndGet((endTime - startTime) / 1_000_000);

                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("Error in concurrent operation: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(60, TimeUnit.SECONDS),
                "All concurrent operations should complete within 60s");

        long testEndTime = System.currentTimeMillis();
        long totalTestDuration = testEndTime - testStartTime;

        executor.shutdown();

        long avgOperationTime = totalDuration.get() / (threadCount * operationsPerThread);
        double throughput = (double) (threadCount * operationsPerThread) / (totalTestDuration / 1000.0);

        System.out.println("Total test duration: " + totalTestDuration + "ms");
        System.out.println("Average operation time: " + avgOperationTime + "ms");
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " ops/second");
        System.out.println("Successful operations: " + successCount.get() + "/" + (threadCount * operationsPerThread));

        assertEquals(threadCount * operationsPerThread, successCount.get(),
                "All operations should succeed");
        assertTrue(throughput > 5,
                "Throughput should be > 5 ops/second, got: " + String.format("%.2f", throughput));

        System.out.println("✓ Concurrent load performance test PASSED");
        System.out.println("========================================");
    }

    // ==================== Test 4: Scalability Performance ====================

    @Test
    @Order(4)
    @DisplayName("Test 4: Scalability with increasing data size")
    void testScalabilityPerformance() {
        System.out.println("\n========================================");
        System.out.println("Test 4: Scalability Performance");
        System.out.println("========================================");

        int[] dataSizes = {10, 50, 100};
        List<Long> durations = new ArrayList<>();

        for (int size : dataSizes) {
            List<UnifiedMessage> messages = createTestMessages(size);
            String convId = "perf-scale-" + size + "-" + System.currentTimeMillis();
            String sessionId = "perf-session-" + size;

            long startTime = System.nanoTime();
            memoryService.extractFromMessages(messages, convId, sessionId);
            long endTime = System.nanoTime();
            long duration = (endTime - startTime) / 1_000_000;

            durations.add(duration);
            System.out.println("Data size: " + size + " messages, Time: " + duration + "ms");
        }

        // Verify linear or better scalability
        // Time for 100 messages should be less than 10x time for 10 messages
        assertTrue(durations.get(2) < durations.get(0) * 10,
                "Scalability should be near-linear");

        System.out.println("✓ Scalability performance test PASSED");
        System.out.println("========================================");
    }

    // ==================== Test 5: Memory Efficiency ====================

    @Test
    @Order(5)
    @DisplayName("Test 5: Memory efficiency under load")
    void testMemoryEfficiency() {
        System.out.println("\n========================================");
        System.out.println("Test 5: Memory Efficiency");
        System.out.println("========================================");

        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // Create a large batch of operations
        int operationCount = 50;
        for (int i = 0; i < operationCount; i++) {
            List<UnifiedMessage> messages = createTestMessages(10);
            String convId = "perf-memory-" + i + "-" + System.currentTimeMillis();
            String sessionId = "perf-session-" + i;
            memoryService.extractFromMessages(messages, convId, sessionId);
        }

        runtime.gc();
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = (memoryAfter - memoryBefore) / (1024 * 1024); // Convert to MB

        System.out.println("Memory used: " + memoryUsed + "MB");
        System.out.println("Operations: " + operationCount);
        System.out.println("Memory per operation: " + String.format("%.2f", (double) memoryUsed / operationCount) + "MB");

        // Memory usage should be reasonable (< 500MB for 50 operations)
        assertTrue(memoryUsed < 500,
                "Memory usage should be < 500MB for " + operationCount + " operations, got: " + memoryUsed + "MB");

        System.out.println("✓ Memory efficiency test PASSED");
        System.out.println("========================================");
    }

    // ==================== Helper Methods ====================

    private List<UnifiedMessage> createTestMessages(int count) {
        List<UnifiedMessage> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Role role = (i % 2 == 0) ? Role.USER : Role.ASSISTANT;
            String content = "Performance test message " + i + " with some content to process.";
            messages.add(new UnifiedMessage(role, content, PayloadFormat.TEXT));
        }
        return messages;
    }
}
