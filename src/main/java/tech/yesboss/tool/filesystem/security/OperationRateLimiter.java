package tech.yesboss.tool.filesystem.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 操作频率限制器
 *
 * <p>防止文件工具被滥用，通过滑动窗口算法限制操作频率。</p>
 *
 * <p><b>核心功能：</b></p>
 * <ul>
 *   <li><b>操作计数</b>: 记录每种操作的调用次数</li>
 *   <li><b>频率限制</b>: 基于滑动窗口限制操作频率</li>
 *   <li><b>自动清理</b>: 定期清理过期的计数器</li>
 *   <li><b>统计信息</b>: 提供操作统计数据</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * OperationRateLimiter limiter = new OperationRateLimiter(100, 60000); // 每分钟100次
 *
 * // 检查是否允许操作
 * if (limiter.tryAcquire("read_file", "/path/to/file")) {
 *     // 执行操作
 * } else {
 *     // 拒绝操作
 * }
 * }</pre>
 */
public class OperationRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(OperationRateLimiter.class);

    /**
     * 默认操作限制：每分钟100次
     */
    private static final int DEFAULT_MAX_OPERATIONS_PER_MINUTE = 100;

    /**
     * 默认时间窗口：60秒
     */
    private static final long DEFAULT_WINDOW_SIZE_MS = 60_000L;

    /**
     * 每种操作类型的限制配置
     */
    private static final Map<String, Integer> OPERATION_LIMITS = Map.of(
            "read_file", 200,      // 读取操作允许更频繁
            "list_directory", 100,
            "write_file", 50,      // 写入操作限制更严格
            "create_directory", 30,
            "delete_file", 20      // 删除操作最严格
    );

    /**
     * 操作计数器（操作类型 -> 计数器）
     */
    private final Map<String, OperationCounter> operationCounters;

    /**
     * 全局最大操作次数限制
     */
    private final int globalMaxOperations;

    /**
     * 时间窗口大小（毫秒）
     */
    private final long windowSizeMs;

    /**
     * 上次清理时间
     */
    private final AtomicLong lastCleanupTime;

    /**
     * 清理间隔（毫秒）
     */
    private static final long CLEANUP_INTERVAL_MS = 300_000L; // 5分钟

    /**
     * 创建默认的频率限制器
     */
    public OperationRateLimiter() {
        this(DEFAULT_MAX_OPERATIONS_PER_MINUTE, DEFAULT_WINDOW_SIZE_MS);
    }

    /**
     * 创建自定义配置的频率限制器
     *
     * @param globalMaxOperations 全局最大操作次数限制
     * @param windowSizeMs 时间窗口大小（毫秒）
     */
    public OperationRateLimiter(int globalMaxOperations, long windowSizeMs) {
        this.globalMaxOperations = globalMaxOperations;
        this.windowSizeMs = windowSizeMs;
        this.operationCounters = new ConcurrentHashMap<>();
        this.lastCleanupTime = new AtomicLong(System.currentTimeMillis());

        logger.info("OperationRateLimiter initialized: maxOperations={}, windowMs={}",
                globalMaxOperations, windowSizeMs);
    }

    /**
     * 尝试获取操作许可
     *
     * @param operation 操作类型
     * @param path 操作路径（用于日志）
     * @return true 如果允许操作，false 如果超过限制
     */
    public boolean tryAcquire(String operation, String path) {
        // 定期清理过期计数器
        cleanupIfNeeded();

        // 获取操作类型的限制
        int limit = OPERATION_LIMITS.getOrDefault(operation, globalMaxOperations);

        // 获取或创建计数器
        OperationCounter counter = operationCounters.computeIfAbsent(
                operation,
                k -> new OperationCounter(limit, windowSizeMs)
        );

        // 尝试增加计数
        boolean allowed = counter.tryIncrement();

        if (!allowed) {
            logger.warn("Rate limit exceeded for operation: {} (limit: {}, path: {})",
                    operation, limit, path);
        } else {
            logger.debug("Operation allowed: {} (path: {})", operation, path);
        }

        return allowed;
    }

    /**
     * 检查操作是否被允许（不增加计数）
     *
     * @param operation 操作类型
     * @return true 如果操作允许
     */
    public boolean isAllowed(String operation) {
        int limit = OPERATION_LIMITS.getOrDefault(operation, globalMaxOperations);
        OperationCounter counter = operationCounters.get(operation);

        if (counter == null) {
            return true;
        }

        return counter.getCurrentCount() < limit;
    }

    /**
     * 获取操作统计信息
     *
     * @param operation 操作类型
     * @return 统计信息
     */
    public OperationStats getStats(String operation) {
        OperationCounter counter = operationCounters.get(operation);
        if (counter == null) {
            return new OperationStats(operation, 0, 0, OPERATION_LIMITS.getOrDefault(operation, globalMaxOperations));
        }

        return new OperationStats(
                operation,
                counter.getCurrentCount(),
                counter.getRejectedCount(),
                OPERATION_LIMITS.getOrDefault(operation, globalMaxOperations)
        );
    }

    /**
     * 重置指定操作的计数器
     *
     * @param operation 操作类型
     */
    public void reset(String operation) {
        operationCounters.remove(operation);
        logger.info("Rate limiter reset for operation: {}", operation);
    }

    /**
     * 重置所有计数器
     */
    public void resetAll() {
        operationCounters.clear();
        logger.info("All rate limiters reset");
    }

    /**
     * 清理过期的计数器
     */
    private void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        long lastCleanup = lastCleanupTime.get();

        if (now - lastCleanup > CLEANUP_INTERVAL_MS) {
            if (lastCleanupTime.compareAndSet(lastCleanup, now)) {
                cleanupExpiredCounters(now);
            }
        }
    }

    /**
     * 清理过期的计数器
     *
     * @param now 当前时间
     */
    private void cleanupExpiredCounters(long now) {
        int removed = 0;
        for (Map.Entry<String, OperationCounter> entry : operationCounters.entrySet()) {
            if (entry.getValue().isExpired(now)) {
                operationCounters.remove(entry.getKey());
                removed++;
            }
        }

        if (removed > 0) {
            logger.debug("Cleaned up {} expired rate limit counters", removed);
        }
    }

    /**
     * 获取操作类型的限制配置
     *
     * @return 操作限制配置（只读）
     */
    public static Map<String, Integer> getOperationLimits() {
        return Map.copyOf(OPERATION_LIMITS);
    }

    /**
     * 操作计数器
     */
    private static class OperationCounter {
        private final int limit;
        private final long windowSizeMs;
        private final AtomicInteger count;
        private final AtomicInteger rejectedCount;
        private final AtomicLong windowStartTime;

        OperationCounter(int limit, long windowSizeMs) {
            this.limit = limit;
            this.windowSizeMs = windowSizeMs;
            this.count = new AtomicInteger(0);
            this.rejectedCount = new AtomicInteger(0);
            this.windowStartTime = new AtomicLong(System.currentTimeMillis());
        }

        boolean tryIncrement() {
            long now = System.currentTimeMillis();

            // 检查是否需要重置窗口
            synchronized (this) {
                if (now - windowStartTime.get() >= windowSizeMs) {
                    windowStartTime.set(now);
                    count.set(0);
                }

                // 检查是否超过限制
                if (count.get() >= limit) {
                    rejectedCount.incrementAndGet();
                    return false;
                }

                count.incrementAndGet();
                return true;
            }
        }

        int getCurrentCount() {
            long now = System.currentTimeMillis();

            synchronized (this) {
                if (now - windowStartTime.get() >= windowSizeMs) {
                    return 0;
                }
                return count.get();
            }
        }

        int getRejectedCount() {
            return rejectedCount.get();
        }

        boolean isExpired(long now) {
            return now - windowStartTime.get() >= windowSizeMs * 2;
        }
    }

    /**
     * 操作统计信息
     *
     * @param operation 操作类型
     * @param currentCount 当前计数
     * @param rejectedCount 拒绝次数
     * @param limit 限制
     */
    public record OperationStats(
            String operation,
            int currentCount,
            int rejectedCount,
            int limit
    ) {
        /**
         * 获取使用百分比
         */
        public double getUsagePercent() {
            if (limit == 0) return 0;
            return (double) currentCount / limit * 100;
        }

        @Override
        public String toString() {
            return String.format("OperationStats{operation='%s', count=%d, rejected=%d, limit=%d, usage=%.1f%%}",
                    operation, currentCount, rejectedCount, limit, getUsagePercent());
        }
    }
}
