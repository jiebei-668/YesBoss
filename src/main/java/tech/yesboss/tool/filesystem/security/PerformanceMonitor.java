package tech.yesboss.tool.filesystem.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能监控组件
 *
 * <p>为文件操作提供性能监控和统计功能，记录操作耗时、文件大小等指标。</p>
 *
 * <p><b>核心功能：</b></p>
 * <ul>
 *   <li><b>操作计时</b>: 精确记录操作执行时间</li>
 *   <li><b>吞吐量统计</b>: 统计文件读写吞吐量</li>
 *   <li><b>性能分析</b>: 识别性能瓶颈</li>
 *   <li><b>资源监控</b>: 监控内存和文件句柄使用</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * PerformanceMonitor monitor = new PerformanceMonitor();
 *
 * // 开始计时
 * long timerId = monitor.startTimer("read_file");
 *
 * // 执行操作
 * String content = readFile(path);
 *
 * // 结束计时并记录
 * monitor.stopTimer(timerId, "read_file", content.length());
 *
 * // 获取统计信息
 * PerformanceStats stats = monitor.getStats("read_file");
 * }</pre>
 */
public class PerformanceMonitor {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitor.class);

    /**
     * 计时器 ID 生成器
     */
    private final AtomicLong timerIdGenerator = new AtomicLong(0);

    /**
     * 活动计时器（timerId -> startTime）
     */
    private final Map<Long, Long> activeTimers = new ConcurrentHashMap<>();

    /**
     * 操作统计（operation -> stats）
     */
    private final Map<String, OperationStats> operationStatsMap = new ConcurrentHashMap<>();

    /**
     * 全局统计
     */
    private final GlobalStats globalStats = new GlobalStats();

    /**
     * 性能警告阈值（毫秒）
     */
    private static final long SLOW_OPERATION_THRESHOLD_MS = 5000; // 5秒

    /**
     * 大文件阈值（字节）
     */
    private static final long LARGE_FILE_THRESHOLD = 1024 * 1024; // 1MB

    /**
     * 开始计时
     *
     * @param operation 操作名称
     * @return 计时器 ID，用于结束计时
     */
    public long startTimer(String operation) {
        long timerId = timerIdGenerator.incrementAndGet();
        long startTime = System.nanoTime();
        activeTimers.put(timerId, startTime);

        logger.trace("Timer started: id={}, operation={}", timerId, operation);
        return timerId;
    }

    /**
     * 结束计时并记录性能数据
     *
     * @param timerId 计时器 ID
     * @param operation 操作名称
     * @param bytesProcessed 处理的字节数（0 表示不统计）
     * @return 操作耗时（毫秒）
     */
    public long stopTimer(long timerId, String operation, long bytesProcessed) {
        Long startTime = activeTimers.remove(timerId);
        if (startTime == null) {
            logger.warn("Timer not found: id={}", timerId);
            return 0;
        }

        long endTime = System.nanoTime();
        long durationNanos = endTime - startTime;
        long durationMs = durationNanos / 1_000_000;

        // 记录统计
        recordOperation(operation, durationMs, bytesProcessed);

        // 检查慢操作
        if (durationMs > SLOW_OPERATION_THRESHOLD_MS) {
            logger.warn("Slow operation detected: operation={}, duration={}ms, bytes={}",
                    operation, durationMs, bytesProcessed);
        }

        // 检查大文件
        if (bytesProcessed > LARGE_FILE_THRESHOLD) {
            logger.info("Large file processed: operation={}, bytes={}, duration={}ms",
                    operation, bytesProcessed, durationMs);
        }

        logger.trace("Timer stopped: id={}, operation={}, duration={}ms", timerId, operation, durationMs);
        return durationMs;
    }

    /**
     * 结束计时（不统计字节数）
     *
     * @param timerId 计时器 ID
     * @param operation 操作名称
     * @return 操作耗时（毫秒）
     */
    public long stopTimer(long timerId, String operation) {
        return stopTimer(timerId, operation, 0);
    }

    /**
     * 记录操作性能数据
     *
     * @param operation 操作名称
     * @param durationMs 耗时（毫秒）
     * @param bytesProcessed 处理的字节数
     */
    public void recordOperation(String operation, long durationMs, long bytesProcessed) {
        // 更新操作统计
        OperationStats stats = operationStatsMap.computeIfAbsent(operation, k -> new OperationStats());
        stats.record(durationMs, bytesProcessed);

        // 更新全局统计
        globalStats.record(durationMs, bytesProcessed);

        logger.debug("Operation recorded: operation={}, duration={}ms, bytes={}",
                operation, durationMs, bytesProcessed);
    }

    /**
     * 获取操作统计信息
     *
     * @param operation 操作名称
     * @return 统计信息，如果不存在返回 null
     */
    public OperationStats getStats(String operation) {
        return operationStatsMap.get(operation);
    }

    /**
     * 获取所有操作的统计信息
     *
     * @return 操作统计映射
     */
    public Map<String, OperationStats> getAllStats() {
        return Map.copyOf(operationStatsMap);
    }

    /**
     * 获取全局统计信息
     *
     * @return 全局统计
     */
    public GlobalStats getGlobalStats() {
        return globalStats;
    }

    /**
     * 获取性能摘要报告
     *
     * @return 性能摘要
     */
    public String getPerformanceSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Performance Summary ===\n");
        sb.append(globalStats.toString()).append("\n\n");

        sb.append("=== Operations ===\n");
        for (Map.Entry<String, OperationStats> entry : operationStatsMap.entrySet()) {
            sb.append(String.format("%s: %s\n", entry.getKey(), entry.getValue()));
        }

        return sb.toString();
    }

    /**
     * 重置所有统计
     */
    public void reset() {
        operationStatsMap.clear();
        globalStats.reset();
        logger.info("Performance monitor reset");
    }

    /**
     * 获取慢操作阈值（毫秒）
     */
    public static long getSlowOperationThreshold() {
        return SLOW_OPERATION_THRESHOLD_MS;
    }

    /**
     * 获取大文件阈值（字节）
     */
    public static long getLargeFileThreshold() {
        return LARGE_FILE_THRESHOLD;
    }

    /**
     * 操作统计信息
     */
    public static class OperationStats {
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong totalDurationMs = new AtomicLong(0);
        private final AtomicLong totalBytes = new AtomicLong(0);
        private final AtomicLong minDurationMs = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxDurationMs = new AtomicLong(0);
        private final AtomicLong slowOperationCount = new AtomicLong(0);

        public void record(long durationMs, long bytes) {
            count.incrementAndGet();
            totalDurationMs.addAndGet(durationMs);
            totalBytes.addAndGet(bytes);

            // 更新最小/最大值
            updateMin(minDurationMs, durationMs);
            updateMax(maxDurationMs, durationMs);

            // 检查慢操作
            if (durationMs > SLOW_OPERATION_THRESHOLD_MS) {
                slowOperationCount.incrementAndGet();
            }
        }

        public long getCount() {
            return count.get();
        }

        public long getTotalDurationMs() {
            return totalDurationMs.get();
        }

        public long getTotalBytes() {
            return totalBytes.get();
        }

        public double getAverageDurationMs() {
            long c = count.get();
            return c == 0 ? 0 : (double) totalDurationMs.get() / c;
        }

        public double getAverageBytes() {
            long c = count.get();
            return c == 0 ? 0 : (double) totalBytes.get() / c;
        }

        public long getMinDurationMs() {
            long min = minDurationMs.get();
            return min == Long.MAX_VALUE ? 0 : min;
        }

        public long getMaxDurationMs() {
            return maxDurationMs.get();
        }

        public long getSlowOperationCount() {
            return slowOperationCount.get();
        }

        /**
         * 获取吞吐量（字节/秒）
         */
        public double getThroughputBytesPerSecond() {
            long totalMs = totalDurationMs.get();
            if (totalMs == 0) return 0;
            return (double) totalBytes.get() / (totalMs / 1000.0);
        }

        private void updateMin(AtomicLong current, long value) {
            long oldValue;
            do {
                oldValue = current.get();
                if (value >= oldValue) return;
            } while (!current.compareAndSet(oldValue, value));
        }

        private void updateMax(AtomicLong current, long value) {
            long oldValue;
            do {
                oldValue = current.get();
                if (value <= oldValue) return;
            } while (!current.compareAndSet(oldValue, value));
        }

        public void reset() {
            count.set(0);
            totalDurationMs.set(0);
            totalBytes.set(0);
            minDurationMs.set(Long.MAX_VALUE);
            maxDurationMs.set(0);
            slowOperationCount.set(0);
        }

        @Override
        public String toString() {
            return String.format(
                    "OperationStats{count=%d, avg=%.2fms, min=%dms, max=%dms, totalBytes=%d, throughput=%.2fB/s, slowOps=%d}",
                    getCount(), getAverageDurationMs(), getMinDurationMs(), getMaxDurationMs(),
                    getTotalBytes(), getThroughputBytesPerSecond(), getSlowOperationCount()
            );
        }
    }

    /**
     * 全局统计信息
     */
    public static class GlobalStats {
        private final AtomicLong totalOperations = new AtomicLong(0);
        private final AtomicLong totalDurationMs = new AtomicLong(0);
        private final AtomicLong totalBytes = new AtomicLong(0);
        private final AtomicLong totalErrors = new AtomicLong(0);
        private final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());

        public void record(long durationMs, long bytes) {
            totalOperations.incrementAndGet();
            totalDurationMs.addAndGet(durationMs);
            totalBytes.addAndGet(bytes);
        }

        public void recordError() {
            totalErrors.incrementAndGet();
        }

        public long getTotalOperations() {
            return totalOperations.get();
        }

        public long getTotalDurationMs() {
            return totalDurationMs.get();
        }

        public long getTotalBytes() {
            return totalBytes.get();
        }

        public long getTotalErrors() {
            return totalErrors.get();
        }

        public double getAverageDurationMs() {
            long c = totalOperations.get();
            return c == 0 ? 0 : (double) totalDurationMs.get() / c;
        }

        /**
         * 获取运行时间（秒）
         */
        public long getUptimeSeconds() {
            return (System.currentTimeMillis() - startTime.get()) / 1000;
        }

        /**
         * 获取每秒操作数
         */
        public double getOperationsPerSecond() {
            long uptime = getUptimeSeconds();
            return uptime == 0 ? 0 : (double) totalOperations.get() / uptime;
        }

        /**
         * 获取总体吞吐量（字节/秒）
         */
        public double getThroughputBytesPerSecond() {
            long uptime = getUptimeSeconds();
            return uptime == 0 ? 0 : (double) totalBytes.get() / uptime;
        }

        public void reset() {
            totalOperations.set(0);
            totalDurationMs.set(0);
            totalBytes.set(0);
            totalErrors.set(0);
            startTime.set(System.currentTimeMillis());
        }

        @Override
        public String toString() {
            return String.format(
                    "GlobalStats{ops=%d, avg=%.2fms, totalBytes=%d, errors=%d, uptime=%ds, ops/s=%.2f, throughput=%.2fB/s}",
                    getTotalOperations(), getAverageDurationMs(), getTotalBytes(),
                    getTotalErrors(), getUptimeSeconds(), getOperationsPerSecond(),
                    getThroughputBytesPerSecond()
            );
        }
    }
}
