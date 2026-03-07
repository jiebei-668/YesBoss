package tech.yesboss.memory.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-level cache implementation with L1 (in-memory) and L2 (future distributed) support.
 *
 * <p>This cache provides a hierarchical caching strategy:
 * <ul>
 *   <li>L1 Cache: Fast in-memory cache (primary)</li>
 *   <li>L2 Cache: Optional slower but larger cache (currently placeholder)</li>
 * </ul>
 *
 * <p>Read operations check L1 first, then L2 if not found.
 * Write operations propagate to all levels.
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of mapped values
 */
public class MultiLevelCache<K, V> implements Cache<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(MultiLevelCache.class);

    private final Cache<K, V> l1Cache;
    private final Cache<K, V> l2Cache;
    private final boolean hasL2Cache;
    private final MultiLevelCacheStatistics statistics;

    /**
     * Create a multi-level cache with only L1 cache.
     *
     * @param l1Config configuration for L1 cache
     * @throws CacheException if initialization fails
     */
    public MultiLevelCache(CacheConfig l1Config) {
        this(l1Config, null);
    }

    /**
     * Create a multi-level cache with L1 and optional L2 cache.
     *
     * @param l1Config configuration for L1 cache
     * @param l2Config configuration for L2 cache, or null for no L2 cache
     * @throws CacheException if initialization fails
     */
    public MultiLevelCache(CacheConfig l1Config, CacheConfig l2Config) {
        this.l1Cache = new InMemoryCache<>(l1Config);
        this.l2Cache = l2Config != null ? new InMemoryCache<>(l2Config) : null;
        this.hasL2Cache = l2Cache != null;
        this.statistics = new MultiLevelCacheStatistics();

        logger.info("MultiLevelCache initialized: L1={}, L2={}",
            l1Config.getMaximumSize(), hasL2Cache ? l2Config.getMaximumSize() : "disabled");
    }

    @Override
    public Optional<V> get(K key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        // Try L1 cache first
        Optional<V> value = l1Cache.get(key);
        if (value.isPresent()) {
            statistics.recordL1Hit();
            return value;
        }

        statistics.recordL1Miss();

        // Try L2 cache if available
        if (hasL2Cache) {
            value = l2Cache.get(key);
            if (value.isPresent()) {
                statistics.recordL2Hit();
                // Promote to L1 cache
                l1Cache.put(key, value.get());
                logger.debug("L2 hit, promoted to L1: key={}", key);
                return value;
            }
            statistics.recordL2Miss();
        }

        return Optional.empty();
    }

    @Override
    public CompletableFuture<Optional<V>> getAsync(K key) {
        return CompletableFuture.supplyAsync(() -> get(key));
    }

    @Override
    public void put(K key, V value) {
        put(key, value, null);
    }

    @Override
    public CompletableFuture<Void> putAsync(K key, V value) {
        return CompletableFuture.runAsync(() -> put(key, value));
    }

    @Override
    public void put(K key, V value, Duration expiration) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }

        // Always put in L1
        l1Cache.put(key, value, expiration);

        // Also put in L2 if available
        if (hasL2Cache) {
            l2Cache.put(key, value, expiration);
        }
    }

    @Override
    public V getOrCompute(K key, CacheLoader<K, V> loader) {
        Optional<V> value = get(key);
        if (value.isPresent()) {
            return value.get();
        }

        long startTime = System.nanoTime();
        try {
            V computedValue = loader.load(key);
            long loadTime = System.nanoTime() - startTime;

            statistics.recordLoadSuccess(loadTime);

            // Put in all cache levels
            put(key, computedValue);

            return computedValue;
        } catch (Exception e) {
            long loadTime = System.nanoTime() - startTime;

            statistics.recordLoadFailure(loadTime);

            throw new CacheException("Failed to load value for key: " + key, e, CacheException.ERROR_BACKEND);
        }
    }

    @Override
    public CompletableFuture<V> getOrComputeAsync(K key, CacheLoader<K, V> loader) {
        return CompletableFuture.supplyAsync(() -> getOrCompute(key, loader));
    }

    @Override
    public void evict(K key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        // Evict from all levels
        l1Cache.evict(key);
        if (hasL2Cache) {
            l2Cache.evict(key);
        }
    }

    @Override
    public CompletableFuture<Void> evictAsync(K key) {
        return CompletableFuture.runAsync(() -> evict(key));
    }

    @Override
    public void clear() {
        // Clear all levels
        l1Cache.clear();
        if (hasL2Cache) {
            l2Cache.clear();
        }
    }

    @Override
    public CompletableFuture<Void> clearAsync() {
        return CompletableFuture.runAsync(this::clear);
    }

    @Override
    public long size() {
        // Return L1 size as primary
        return l1Cache.size();
    }

    @Override
    public CacheStatistics getStatistics() {
        return statistics;
    }

    @Override
    public void resetStatistics() {
        statistics.reset();
        l1Cache.resetStatistics();
        if (hasL2Cache) {
            l2Cache.resetStatistics();
        }
    }

    @Override
    public boolean containsKey(K key) {
        // Check L1 first
        if (l1Cache.containsKey(key)) {
            return true;
        }

        // Check L2 if available
        return hasL2Cache && l2Cache.containsKey(key);
    }

    @Override
    public Iterable<K> keys() {
        // Return L1 keys as primary
        return l1Cache.keys();
    }

    @Override
    public Iterable<V> values() {
        // Return L1 values as primary
        return l1Cache.values();
    }

    /**
     * Get the L1 cache directly.
     *
     * @return L1 cache instance
     */
    public Cache<K, V> getL1Cache() {
        return l1Cache;
    }

    /**
     * Get the L2 cache (may be null).
     *
     * @return L2 cache instance, or null if not configured
     */
    public Cache<K, V> getL2Cache() {
        return l2Cache;
    }

    /**
     * Check if L2 cache is enabled.
     *
     * @return true if L2 cache is enabled
     */
    public boolean hasL2Cache() {
        return hasL2Cache;
    }

    /**
     * Shutdown all cache levels.
     */
    public void shutdown() {
        if (l1Cache instanceof InMemoryCache) {
            ((InMemoryCache<?, ?>) l1Cache).shutdown();
        }
        if (hasL2Cache && l2Cache instanceof InMemoryCache) {
            ((InMemoryCache<?, ?>) l2Cache).shutdown();
        }
        logger.info("MultiLevelCache shutdown complete");
    }

    /**
     * Statistics for multi-level cache operations.
     */
    private static class MultiLevelCacheStatistics extends CacheStatistics {
        private final AtomicLong l1Hits = new AtomicLong(0);
        private final AtomicLong l1Misses = new AtomicLong(0);
        private final AtomicLong l2Hits = new AtomicLong(0);
        private final AtomicLong l2Misses = new AtomicLong(0);

        public void recordL1Hit() {
            l1Hits.incrementAndGet();
        }

        public void recordL1Miss() {
            l1Misses.incrementAndGet();
        }

        public void recordL2Hit() {
            l2Hits.incrementAndGet();
        }

        public void recordL2Miss() {
            l2Misses.incrementAndGet();
        }

        public long getL1Hits() {
            return l1Hits.get();
        }

        public long getL1Misses() {
            return l1Misses.get();
        }

        public long getL2Hits() {
            return l2Hits.get();
        }

        public long getL2Misses() {
            return l2Misses.get();
        }

        public long getTotalL1Requests() {
            return l1Hits.get() + l1Misses.get();
        }

        public long getTotalL2Requests() {
            return l2Hits.get() + l2Misses.get();
        }

        public double getL1HitRate() {
            long total = getTotalL1Requests();
            return total == 0 ? 1.0 : (double) l1Hits.get() / total;
        }

        public double getL2HitRate() {
            long total = getTotalL2Requests();
            return total == 0 ? 1.0 : (double) l2Hits.get() / total;
        }

        @Override
        public void reset() {
            super.reset();
            l1Hits.set(0);
            l1Misses.set(0);
            l2Hits.set(0);
            l2Misses.set(0);
        }

        @Override
        public String toString() {
            return String.format(
                "MultiLevelCacheStatistics{l1Hits=%d, l1Misses=%d, l1HitRate=%.2f%%, l2Hits=%d, l2Misses=%d, l2HitRate=%.2f%%}",
                getL1Hits(), getL1Misses(), getL1HitRate() * 100,
                getL2Hits(), getL2Misses(), getL2HitRate() * 100
            );
        }
    }
}
