package tech.yesboss.tool.filesystem.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 性能监控器测试
 */
class PerformanceMonitorTest {

    private PerformanceMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new PerformanceMonitor();
    }

    @Test
    @DisplayName("应该正确开始和结束计时器")
    void testStartStopTimer() throws InterruptedException {
        long timerId = monitor.startTimer("read_file");
        assertTrue(timerId > 0);

        // 模拟一些工作
        Thread.sleep(10);

        long durationMs = monitor.stopTimer(timerId, "read_file", 1024);

        assertTrue(durationMs >= 10, "Duration should be at least 10ms");
    }

    @Test
    @DisplayName("应该正确统计操作")
    void testRecordOperation() {
        monitor.recordOperation("read_file", 100, 1024);
        monitor.recordOperation("read_file", 200, 2048);
        monitor.recordOperation("read_file", 150, 512);

        PerformanceMonitor.OperationStats stats = monitor.getStats("read_file");

        assertNotNull(stats);
        assertEquals(3, stats.getCount());
        assertEquals(150.0, stats.getAverageDurationMs(), 0.1); // (100+200+150)/3
        assertEquals(3584, stats.getTotalBytes()); // 1024+2048+512
        assertEquals(100, stats.getMinDurationMs());
        assertEquals(200, stats.getMaxDurationMs());
    }

    @Test
    @DisplayName("应该正确计算吞吐量")
    void testThroughput() {
        // 记录 1 秒内处理 1MB 数据的操作
        monitor.recordOperation("read_file", 1000, 1024 * 1024);

        PerformanceMonitor.OperationStats stats = monitor.getStats("read_file");

        // 吞吐量应该是约 1MB/s
        assertTrue(stats.getThroughputBytesPerSecond() > 1000 * 1000);
    }

    @Test
    @DisplayName("应该正确检测慢操作")
    void testSlowOperationDetection() throws InterruptedException {
        long slowThreshold = PerformanceMonitor.getSlowOperationThreshold();

        // 执行一个较快的操作
        long timerId1 = monitor.startTimer("read_file");
        Thread.sleep(5);
        monitor.stopTimer(timerId1, "read_file", 100);

        PerformanceMonitor.OperationStats stats = monitor.getStats("read_file");
        assertEquals(0, stats.getSlowOperationCount());

        // 执行一个慢操作（模拟）
        monitor.recordOperation("read_file", slowThreshold + 1000, 100);

        stats = monitor.getStats("read_file");
        assertTrue(stats.getSlowOperationCount() >= 1);
    }

    @Test
    @DisplayName("应该正确统计全局数据")
    void testGlobalStats() {
        monitor.recordOperation("read_file", 100, 1024);
        monitor.recordOperation("write_file", 200, 2048);
        monitor.recordOperation("list_directory", 50, 0);

        PerformanceMonitor.GlobalStats globalStats = monitor.getGlobalStats();

        assertEquals(3, globalStats.getTotalOperations());
        assertEquals(350, globalStats.getTotalDurationMs());
        assertEquals(3072, globalStats.getTotalBytes());
        assertEquals(116.67, globalStats.getAverageDurationMs(), 0.5);
    }

    @Test
    @DisplayName("应该正确获取所有操作统计")
    void testGetAllStats() {
        monitor.recordOperation("read_file", 100, 1024);
        monitor.recordOperation("write_file", 200, 2048);

        var allStats = monitor.getAllStats();

        assertEquals(2, allStats.size());
        assertTrue(allStats.containsKey("read_file"));
        assertTrue(allStats.containsKey("write_file"));
    }

    @Test
    @DisplayName("reset 应该清除所有统计")
    void testReset() {
        monitor.recordOperation("read_file", 100, 1024);
        monitor.recordOperation("write_file", 200, 2048);

        monitor.reset();

        assertNull(monitor.getStats("read_file"));
        assertNull(monitor.getStats("write_file"));

        PerformanceMonitor.GlobalStats globalStats = monitor.getGlobalStats();
        assertEquals(0, globalStats.getTotalOperations());
    }

    @Test
    @DisplayName("应该正确处理不存在的计时器")
    void testInvalidTimerId() {
        long durationMs = monitor.stopTimer(99999, "read_file", 100);
        assertEquals(0, durationMs);
    }

    @Test
    @DisplayName("应该正确处理没有字节数的操作")
    void testOperationWithoutBytes() throws InterruptedException {
        long timerId = monitor.startTimer("list_directory");
        Thread.sleep(10);
        monitor.stopTimer(timerId, "list_directory");

        PerformanceMonitor.OperationStats stats = monitor.getStats("list_directory");

        assertEquals(1, stats.getCount());
        assertEquals(0, stats.getTotalBytes());
    }

    @Test
    @DisplayName("getPerformanceSummary 应该返回有效的摘要")
    void testGetPerformanceSummary() {
        monitor.recordOperation("read_file", 100, 1024);
        monitor.recordOperation("write_file", 200, 2048);

        String summary = monitor.getPerformanceSummary();

        assertNotNull(summary);
        assertTrue(summary.contains("Performance Summary"));
        assertTrue(summary.contains("Operations"));
    }

    @Test
    @DisplayName("OperationStats 应该正确处理并发更新")
    void testConcurrentUpdates() throws InterruptedException {
        Thread[] threads = new Thread[10];

        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    monitor.recordOperation("read_file", j, j * 100);
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        PerformanceMonitor.OperationStats stats = monitor.getStats("read_file");

        assertEquals(1000, stats.getCount()); // 10 threads * 100 operations
    }

    @Test
    @DisplayName("OperationStats 应该正确格式化输出")
    void testOperationStatsToString() {
        monitor.recordOperation("read_file", 100, 1024);

        PerformanceMonitor.OperationStats stats = monitor.getStats("read_file");
        String str = stats.toString();

        assertTrue(str.contains("count=1"));
        assertTrue(str.contains("avg="));
        assertTrue(str.contains("totalBytes=1024"));
    }

    @Test
    @DisplayName("GlobalStats 应该正确格式化输出")
    void testGlobalStatsToString() {
        monitor.recordOperation("read_file", 100, 1024);

        PerformanceMonitor.GlobalStats stats = monitor.getGlobalStats();
        String str = stats.toString();

        assertTrue(str.contains("ops=1"));
        assertTrue(str.contains("totalBytes=1024"));
    }

    @Test
    @DisplayName("GlobalStats 应该正确计算每秒操作数")
    void testOperationsPerSecond() {
        // 记录多个操作
        for (int i = 0; i < 100; i++) {
            monitor.recordOperation("read_file", 10, 1024);
        }

        PerformanceMonitor.GlobalStats stats = monitor.getGlobalStats();

        // 应该有正值的每秒操作数（取决于运行时间）
        assertTrue(stats.getOperationsPerSecond() >= 0);
        assertTrue(stats.getThroughputBytesPerSecond() >= 0);
    }

    @Test
    @DisplayName("应该正确获取阈值常量")
    void testThresholds() {
        assertTrue(PerformanceMonitor.getSlowOperationThreshold() > 0);
        assertTrue(PerformanceMonitor.getLargeFileThreshold() > 0);
    }

    @Test
    @DisplayName("OperationStats reset 应该重置所有值")
    void testOperationStatsReset() {
        PerformanceMonitor.OperationStats stats = new PerformanceMonitor.OperationStats();

        stats.record(100, 1024);
        stats.record(200, 2048);

        stats.reset();

        assertEquals(0, stats.getCount());
        assertEquals(0, stats.getTotalBytes());
        assertEquals(0, stats.getMinDurationMs());
        assertEquals(0, stats.getMaxDurationMs());
    }
}
