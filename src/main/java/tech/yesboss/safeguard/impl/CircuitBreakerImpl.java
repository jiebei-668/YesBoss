package tech.yesboss.safeguard.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.safeguard.CircuitBreaker;
import tech.yesboss.safeguard.CircuitBreakerOpenException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 死循环熔断器实现 (Circuit Breaker Implementation)
 *
 * <p>使用 ConcurrentHashMap 和 AtomicInteger 实现线程安全的循环计数。</p>
 */
public class CircuitBreakerImpl implements CircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerImpl.class);

    // Thread-safe counter map: sessionId -> loop count
    private final ConcurrentHashMap<String, AtomicInteger> counters;

    // Configurable threshold
    private int threshold;

    /**
     * 创建熔断器，使用默认阈值（20 轮）
     */
    public CircuitBreakerImpl() {
        this(DEFAULT_THRESHOLD);
    }

    /**
     * 创建熔断器，指定阈值
     *
     * @param threshold 循环次数阈值
     * @throws IllegalArgumentException if threshold is less than or equal to 0
     */
    public CircuitBreakerImpl(int threshold) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("Threshold must be positive, but was: " + threshold);
        }
        this.counters = new ConcurrentHashMap<>();
        this.threshold = threshold;
        logger.info("CircuitBreaker initialized with threshold: {}", threshold);
    }

    @Override
    public void checkAndIncrement(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId cannot be null or empty");
        }

        // Get or create counter for this session
        AtomicInteger counter = counters.computeIfAbsent(sessionId, k -> {
            logger.debug("Created new counter for session: {}", sessionId);
            return new AtomicInteger(0);
        });

        // Increment and get the new value
        int newCount = counter.incrementAndGet();

        // Check if threshold reached or exceeded
        if (newCount >= threshold) {
            String message = String.format(
                    "Circuit breaker opened for session %s after %d iterations (threshold: %d)",
                    sessionId, newCount, threshold
            );
            logger.error(message);

            // Clean up the counter to save memory
            counters.remove(sessionId);

            throw new CircuitBreakerOpenException(message, sessionId, newCount, threshold);
        }

        // Log progress
        if (newCount == threshold) {
            logger.warn("Session {} approaching circuit breaker limit: {}/{}",
                    sessionId, newCount, threshold);
        } else if (newCount % 5 == 0) {
            // Log every 5 iterations
            logger.debug("Session {} iteration count: {}", sessionId, newCount);
        }
    }

    @Override
    public void reset(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId cannot be null or empty");
        }

        AtomicInteger removed = counters.remove(sessionId);
        if (removed != null) {
            logger.info("Reset circuit breaker counter for session: {} (was at {})",
                    sessionId, removed.get());
        } else {
            logger.debug("Attempted to reset non-existent counter for session: {}", sessionId);
        }
    }

    @Override
    public int getCurrentCount(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId cannot be null or empty");
        }

        AtomicInteger counter = counters.get(sessionId);
        return counter == null ? 0 : counter.get();
    }

    @Override
    public int getThreshold() {
        return threshold;
    }

    @Override
    public void setThreshold(int threshold) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("Threshold must be positive, but was: " + threshold);
        }
        int oldThreshold = this.threshold;
        this.threshold = threshold;
        logger.info("Circuit breaker threshold updated from {} to {}", oldThreshold, threshold);
    }

    /**
     * 获取当前活跃的计数器数量（用于监控和调试）
     *
     * @return 活跃计数器数量
     */
    public int getActiveCounterCount() {
        return counters.size();
    }

    /**
     * 清空所有计数器（用于测试或系统重启）
     */
    public void clearAll() {
        int size = counters.size();
        counters.clear();
        logger.info("Cleared all circuit breaker counters (was {})", size);
    }
}
