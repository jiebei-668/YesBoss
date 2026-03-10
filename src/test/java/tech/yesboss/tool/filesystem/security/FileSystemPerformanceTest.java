package tech.yesboss.tool.filesystem.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件系统工具性能测试
 *
 * <p>测试大文件处理、大目录列表和并发操作性能。</p>
 */
class FileSystemPerformanceTest {

    @TempDir
    Path tempDir;

    private PerformanceMonitor performanceMonitor;
    private FilesystemSecurityContext securityContext;

    @BeforeEach
    void setUp() {
        performanceMonitor = new PerformanceMonitor();
        securityContext = new FilesystemSecurityContext(tempDir.toString());
    }

    @Test
    @DisplayName("大文件读取性能测试")
    void testLargeFilePerformance() throws IOException {
        // 创建一个较大的测试文件（1MB）
        Path largeFile = tempDir.resolve("large_file.txt");
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            content.append("Line ").append(i).append(": This is a test line with some content.\n");
        }
        Files.writeString(largeFile, content.toString());

        long fileSize = Files.size(largeFile);
        assertTrue(fileSize > 100 * 1024, "Test file should be at least 100KB");

        // 测量读取性能
        long timerId = performanceMonitor.startTimer("read_large_file");

        String readContent = Files.readString(largeFile);

        long durationMs = performanceMonitor.stopTimer(timerId, "read_large_file", fileSize);

        // 验证读取成功
        assertEquals(content.toString(), readContent);

        // 记录性能数据
        System.out.printf("Large file read: %d bytes in %d ms (%.2f MB/s)%n",
                fileSize, durationMs,
                (fileSize / 1024.0 / 1024.0) / (durationMs / 1000.0));

        // 读取应该在合理时间内完成（< 5秒）
        assertTrue(durationMs < 5000, "Large file read should complete within 5 seconds");
    }

    @Test
    @DisplayName("大目录列表性能测试")
    void testLargeDirectoryListing() throws IOException {
        // 创建包含大量文件的目录
        Path largeDir = tempDir.resolve("large_directory");
        Files.createDirectories(largeDir);

        int fileCount = 100;
        for (int i = 0; i < fileCount; i++) {
            Files.writeString(largeDir.resolve("file_" + i + ".txt"), "Content " + i);
        }

        // 测量列表性能
        long timerId = performanceMonitor.startTimer("list_large_directory");

        try (var stream = Files.list(largeDir)) {
            long count = stream.count();
            assertEquals(fileCount, count);
        }

        long durationMs = performanceMonitor.stopTimer(timerId, "list_large_directory", 0);

        System.out.printf("Large directory list: %d files in %d ms%n", fileCount, durationMs);

        // 列表应该在合理时间内完成
        assertTrue(durationMs < 1000, "Directory listing should complete within 1 second");
    }

    @Test
    @DisplayName("递归目录列表性能测试")
    void testRecursiveDirectoryListing() throws IOException {
        // 创建嵌套目录结构
        Path rootDir = tempDir.resolve("recursive_test");
        Files.createDirectories(rootDir);

        int depth = 5;
        int filesPerDir = 10;
        createNestedDirectories(rootDir, depth, filesPerDir);

        // 测量递归列表性能
        long timerId = performanceMonitor.startTimer("list_recursive");

        try (var stream = Files.walk(rootDir)) {
            long count = stream.count();
            System.out.println("Recursive walk found " + count + " items");
            assertTrue(count > depth * filesPerDir);
        }

        long durationMs = performanceMonitor.stopTimer(timerId, "list_recursive", 0);

        System.out.printf("Recursive directory walk in %d ms%n", durationMs);

        assertTrue(durationMs < 2000, "Recursive walk should complete within 2 seconds");
    }

    @Test
    @DisplayName("并发文件操作测试")
    void testConcurrentOperations() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 20;
        int[] successCount = {0};
        int[] errorCount = {0};

        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    try {
                        Path testFile = tempDir.resolve("concurrent_" + threadIndex + "_" + j + ".txt");
                        long timerId = performanceMonitor.startTimer("concurrent_write");
                        Files.writeString(testFile, "Concurrent content " + j);
                        performanceMonitor.stopTimer(timerId, "concurrent_write", 20);

                        synchronized (successCount) {
                            successCount[0]++;
                        }
                    } catch (Exception e) {
                        synchronized (errorCount) {
                            errorCount[0]++;
                        }
                    }
                }
            });
        }

        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        System.out.printf("Concurrent test: %d success, %d errors%n", successCount[0], errorCount[0]);

        // 所有操作应该成功
        assertEquals(threadCount * operationsPerThread, successCount[0]);
        assertEquals(0, errorCount[0]);

        // 验证性能统计
        PerformanceMonitor.OperationStats stats = performanceMonitor.getStats("concurrent_write");
        assertNotNull(stats);
        assertEquals(threadCount * operationsPerThread, stats.getCount());
    }

    @Test
    @DisplayName("文件写入性能测试")
    void testWritePerformance() throws IOException {
        // 创建不同大小的测试内容
        int[] sizes = {1024, 10 * 1024, 100 * 1024}; // 1KB, 10KB, 100KB

        for (int size : sizes) {
            String content = "x".repeat(size);
            Path testFile = tempDir.resolve("perf_test_" + size + ".txt");

            long timerId = performanceMonitor.startTimer("write_" + size);

            Files.writeString(testFile, content);

            long durationMs = performanceMonitor.stopTimer(timerId, "write_" + size, size);

            System.out.printf("Write %d bytes: %d ms (%.2f MB/s)%n",
                    size, durationMs,
                    (size / 1024.0 / 1024.0) / (durationMs / 1000.0));

            assertTrue(Files.exists(testFile));
            assertEquals(size, Files.size(testFile));
        }
    }

    @Test
    @DisplayName("文件删除性能测试")
    void testDeletePerformance() throws IOException {
        // 创建多个文件
        int fileCount = 50;
        Path deleteDir = tempDir.resolve("delete_test");
        Files.createDirectories(deleteDir);

        for (int i = 0; i < fileCount; i++) {
            Files.writeString(deleteDir.resolve("file_" + i + ".txt"), "Content " + i);
        }

        // 测量删除性能
        long timerId = performanceMonitor.startTimer("delete_files");

        try (var stream = Files.walk(deleteDir)) {
            stream.sorted((a, b) -> -a.compareTo(b)) // 先删除文件，再删除目录
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        long durationMs = performanceMonitor.stopTimer(timerId, "delete_files", 0);

        System.out.printf("Delete %d files in %d ms%n", fileCount, durationMs);

        assertFalse(Files.exists(deleteDir));
    }

    @Test
    @DisplayName("性能监控统计验证")
    void testPerformanceMonitorStats() {
        // 记录多个操作
        for (int i = 0; i < 100; i++) {
            performanceMonitor.recordOperation("test_op", i, i * 100);
        }

        PerformanceMonitor.OperationStats stats = performanceMonitor.getStats("test_op");

        assertEquals(100, stats.getCount());
        assertEquals(4950, stats.getTotalDurationMs()); // sum(0..99)
        assertEquals(495000, stats.getTotalBytes()); // sum(0..99) * 100
        assertEquals(49.5, stats.getAverageDurationMs(), 0.1);
        assertEquals(0, stats.getMinDurationMs());
        assertEquals(99, stats.getMaxDurationMs());
    }

    @Test
    @DisplayName("全局统计测试")
    void testGlobalStats() {
        // 记录不同类型的操作
        performanceMonitor.recordOperation("read", 100, 1000);
        performanceMonitor.recordOperation("write", 200, 2000);
        performanceMonitor.recordOperation("delete", 50, 0);

        PerformanceMonitor.GlobalStats globalStats = performanceMonitor.getGlobalStats();

        assertEquals(3, globalStats.getTotalOperations());
        assertEquals(350, globalStats.getTotalDurationMs());
        assertEquals(3000, globalStats.getTotalBytes());
    }

    @Test
    @DisplayName("安全上下文性能测试")
    void testSecurityContextPerformance() throws Exception {
        int iterations = 100;

        long totalTime = 0;

        for (int i = 0; i < iterations; i++) {
            long timerId = securityContext.beforeOperation(
                    "read_file",
                    tempDir.resolve("test" + i + ".txt").toString(),
                    "{}",
                    "call-" + i
            );

            securityContext.afterSuccess("read_file", tempDir.resolve("test" + i + ".txt").toString(), timerId, 100);

            totalTime += securityContext.getPerformanceMonitor().getStats("read_file").getTotalDurationMs();
        }

        // 验证审计统计
        var auditStats = securityContext.getAuditStatistics();
        assertEquals(iterations, auditStats.totalSuccess());

        System.out.println("Security context performance summary:");
        System.out.println(securityContext.getPerformanceSummary());
    }

    // 辅助方法：创建嵌套目录结构
    private void createNestedDirectories(Path parent, int depth, int filesPerLevel) throws IOException {
        if (depth <= 0) return;

        // 在当前目录创建文件
        for (int i = 0; i < filesPerLevel; i++) {
            Files.writeString(parent.resolve("file_" + i + ".txt"), "Content");
        }

        // 创建子目录
        Path childDir = parent.resolve("subdir");
        Files.createDirectories(childDir);
        createNestedDirectories(childDir, depth - 1, filesPerLevel);
    }
}
