package tech.yesboss.memory.cache;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Statistics for cache operations.
 *
 * <p>This class tracks various cache metrics including:
 * <ul>
 *   <li>Hit count and rate</li>
 *   <li>Miss count and rate</li>
 *   <li>Eviction count</li>
 *   <li>Load success and failure counts</li>
 *   <li>Total load time</li>
 *   <li>Request count</li>
 * </ul>
 *
 * <p>All statistics are thread-safe and can be accessed concurrently.
 */
public class CacheStatistics {

    private final LongAdder hitCount = new LongAdder();
    private final LongAdder missCount = new LongAdder();
    private final LongAdder loadSuccessCount = new LongAdder();
    private final LongAdder loadFailureCount = new LongAdder();
    private final LongAdder totalLoadTime = new LongAdder();
    private final LongAdder evictionCount = new LongAdder();
    private final LongAdder requestCount = new LongAdder();

    private final LocalDateTime creationTime;

    /**
     * Create a new cache statistics instance.
     */
    public CacheStatistics() {
        this.creationTime = LocalDateTime.now();
    }

    /**
     * Record a cache hit.
     */
    public void recordHit() {
        hitCount.increment();
        requestCount.increment();
    }

    /**
     * Record a cache miss.
     */
    public void recordMiss() {
        missCount.increment();
        requestCount.increment();
    }

    /**
     * Record a successful load operation.
     *
     * @param loadTimeNanos the time taken to load the value in nanoseconds
     */
    public void recordLoadSuccess(long loadTimeNanos) {
        loadSuccessCount.increment();
        totalLoadTime.add(loadTimeNanos);
    }

    /**
     * Record a failed load operation.
     *
     * @param loadTimeNanos the time taken for the failed load in nanoseconds
     */
    public void recordLoadFailure(long loadTimeNanos) {
        loadFailureCount.increment();
        totalLoadTime.add(loadTimeNanos);
    }

    /**
     * Record an eviction.
     */
    public void recordEviction() {
        evictionCount.increment();
    }

    /**
     * Get the number of cache hits.
     *
     * @return hit count
     */
    public long getHitCount() {
        return hitCount.sum();
    }

    /**
     * Get the number of cache misses.
     *
     * @return miss count
     */
    public long getMissCount() {
        return missCount.sum();
    }

    /**
     * Get the total number of requests (hits + misses).
     *
     * @return total request count
     */
    public long getRequestCount() {
        return requestCount.sum();
    }

    /**
     * Get the cache hit rate as a percentage.
     *
     * @return hit rate (0.0 to 1.0)
     */
    public double getHitRate() {
        long total = getRequestCount();
        return total == 0 ? 1.0 : (double) getHitCount() / total;
    }

    /**
     * Get the cache miss rate as a percentage.
     *
     * @return miss rate (0.0 to 1.0)
     */
    public double getMissRate() {
        long total = getRequestCount();
        return total == 0 ? 0.0 : (double) getMissCount() / total;
    }

    /**
     * Get the number of successful load operations.
     *
     * @return load success count
     */
    public long getLoadSuccessCount() {
        return loadSuccessCount.sum();
    }

    /**
     * Get the number of failed load operations.
     *
     * @return load failure count
     */
    public long getLoadFailureCount() {
        return loadFailureCount.sum();
    }

    /**
     * Get the total number of load operations.
     *
     * @return total load count
     */
    public long getLoadCount() {
        return getLoadSuccessCount() + getLoadFailureCount();
    }

    /**
     * Get the average load time in nanoseconds.
     *
     * @return average load time, or 0 if no loads have been performed
     */
    public double getAverageLoadTimeNanos() {
        long count = getLoadCount();
        return count == 0 ? 0.0 : (double) totalLoadTime.sum() / count;
    }

    /**
     * Get the average load time in milliseconds.
     *
     * @return average load time in milliseconds
     */
    public double getAverageLoadTimeMillis() {
        return getAverageLoadTimeNanos() / 1_000_000.0;
    }

    /**
     * Get the eviction count.
     *
     * @return eviction count
     */
    public long getEvictionCount() {
        return evictionCount.sum();
    }

    /**
     * Get the creation time of these statistics.
     *
     * @return creation time
     */
    public LocalDateTime getCreationTime() {
        return creationTime;
    }

    /**
     * Reset all statistics to zero.
     */
    public void reset() {
        hitCount.reset();
        missCount.reset();
        loadSuccessCount.reset();
        loadFailureCount.reset();
        totalLoadTime.reset();
        evictionCount.reset();
        requestCount.reset();
    }

    @Override
    public String toString() {
        return String.format(
            "CacheStatistics{hitCount=%d, missCount=%d, hitRate=%.2f%%, " +
            "loadSuccessCount=%d, loadFailureCount=%d, evictionCount=%d, " +
            "averageLoadTime=%.2fms}",
            getHitCount(),
            getMissCount(),
            getHitRate() * 100,
            getLoadSuccessCount(),
            getLoadFailureCount(),
            getEvictionCount(),
            getAverageLoadTimeMillis()
        );
    }

    /**
     * Create a snapshot of the current statistics.
     *
     * @return CacheStatisticsSnapshot containing current values
     */
    public CacheStatisticsSnapshot snapshot() {
        return new CacheStatisticsSnapshot(
            getHitCount(),
            getMissCount(),
            getHitRate(),
            getLoadSuccessCount(),
            getLoadFailureCount(),
            getEvictionCount(),
            getAverageLoadTimeMillis(),
            Duration.between(creationTime, LocalDateTime.now())
        );
    }

    /**
     * Immutable snapshot of cache statistics.
     */
    public static class CacheStatisticsSnapshot {
        private final long hitCount;
        private final long missCount;
        private final double hitRate;
        private final long loadSuccessCount;
        private final long loadFailureCount;
        private final long evictionCount;
        private final double averageLoadTimeMillis;
        private final Duration uptime;

        public CacheStatisticsSnapshot(long hitCount, long missCount, double hitRate,
                                      long loadSuccessCount, long loadFailureCount,
                                      long evictionCount, double averageLoadTimeMillis,
                                      Duration uptime) {
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.hitRate = hitRate;
            this.loadSuccessCount = loadSuccessCount;
            this.loadFailureCount = loadFailureCount;
            this.evictionCount = evictionCount;
            this.averageLoadTimeMillis = averageLoadTimeMillis;
            this.uptime = uptime;
        }

        public long getHitCount() { return hitCount; }
        public long getMissCount() { return missCount; }
        public double getHitRate() { return hitRate; }
        public long getLoadSuccessCount() { return loadSuccessCount; }
        public long getLoadFailureCount() { return loadFailureCount; }
        public long getEvictionCount() { return evictionCount; }
        public double getAverageLoadTimeMillis() { return averageLoadTimeMillis; }
        public Duration getUptime() { return uptime; }
    }
}
