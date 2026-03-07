package tech.yesboss.memory.processor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Simple thread-safe LRU cache for ContentProcessor
 *
 * <p>This cache provides:
 * <ul>
 *   <li>LRU eviction policy when cache is full</li>
 *   <li>Thread-safe operations</li>
 *   <li>Time-based expiration</li>
 *   <li>Cache statistics</li>
 * </ul>
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public class ProcessorCache<K, V> {

    private final int maxSize;
    private final long expireAfterWriteMillis;
    private final LinkedHashMap<K, CacheEntry<V>> cache;
    private final CacheStats stats;

    /**
     * Internal cache entry with timestamp
     */
    private static class CacheEntry<V> {
        final V value;
        final long timestamp;

        CacheEntry(V value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired(long expireAfterWriteMillis) {
            return System.currentTimeMillis() - timestamp > expireAfterWriteMillis;
        }
    }

    /**
     * Cache statistics
     */
    public static class CacheStats {
        private volatile long hitCount;
        private volatile long missCount;
        private volatile long evictionCount;
        private volatile long expirationCount;

        public long getHitCount() {
            return hitCount;
        }

        public long getMissCount() {
            return missCount;
        }

        public long getEvictionCount() {
            return evictionCount;
        }

        public long getExpirationCount() {
            return expirationCount;
        }

        public long getRequestCount() {
            return hitCount + missCount;
        }

        public double getHitRate() {
            long requests = getRequestCount();
            return requests == 0 ? 0.0 : (double) hitCount / requests;
        }

        public void recordHit() {
            hitCount++;
        }

        public void recordMiss() {
            missCount++;
        }

        public void recordEviction() {
            evictionCount++;
        }

        public void recordExpiration() {
            expirationCount++;
        }

        public void reset() {
            hitCount = 0;
            missCount = 0;
            evictionCount = 0;
            expirationCount = 0;
        }

        @Override
        public String toString() {
            return String.format("CacheStats{hits=%d, misses=%d, evictions=%d, expirations=%d, hitRate=%.2f%%}",
                    hitCount, missCount, evictionCount, expirationCount, getHitRate() * 100);
        }
    }

    /**
     * Create a new cache with specified size and expiration
     *
     * @param maxSize Maximum number of entries
     * @param expireAfterWriteMillis Time to expiration in milliseconds
     */
    public ProcessorCache(int maxSize, long expireAfterWriteMillis) {
        this.maxSize = maxSize;
        this.expireAfterWriteMillis = expireAfterWriteMillis;
        this.stats = new CacheStats();

        // Create LinkedHashMap with access order and removeEldestEntry
        this.cache = new LinkedHashMap<K, CacheEntry<V>>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
                boolean removed = size() > maxSize;
                if (removed) {
                    stats.recordEviction();
                }
                return removed;
            }
        };
    }

    /**
     * Get value from cache
     *
     * @param key Cache key
     * @return Cached value or null if not found or expired
     */
    public synchronized V get(K key) {
        CacheEntry<V> entry = cache.get(key);
        if (entry == null) {
            stats.recordMiss();
            return null;
        }

        // Check expiration
        if (entry.isExpired(expireAfterWriteMillis)) {
            cache.remove(key);
            stats.recordExpiration();
            stats.recordMiss();
            return null;
        }

        stats.recordHit();
        return entry.value;
    }

    /**
     * Put value in cache
     *
     * @param key Cache key
     * @param value Value to cache
     */
    public synchronized void put(K key, V value) {
        cache.put(key, new CacheEntry<>(value));
    }

    /**
     * Get value from cache, or compute if not present
     *
     * @param key Cache key
     * @param loadingFunction Function to compute value if not in cache
     * @return Cached or computed value
     */
    public synchronized V getOrCompute(K key, Function<K, V> loadingFunction) {
        V value = get(key);
        if (value != null) {
            return value;
        }

        value = loadingFunction.apply(key);
        if (value != null) {
            put(key, value);
        }
        return value;
    }

    /**
     * Check if key exists in cache and is not expired
     *
     * @param key Cache key
     * @return true if key exists and is not expired
     */
    public synchronized boolean containsKey(K key) {
        CacheEntry<V> entry = cache.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired(expireAfterWriteMillis)) {
            cache.remove(key);
            return false;
        }
        return true;
    }

    /**
     * Remove entry from cache
     *
     * @param key Cache key
     * @return Removed value or null if not found
     */
    public synchronized V remove(K key) {
        CacheEntry<V> entry = cache.remove(key);
        return entry != null ? entry.value : null;
    }

    /**
     * Clear all entries from cache
     */
    public synchronized void clear() {
        cache.clear();
        stats.reset();
    }

    /**
     * Get current cache size
     *
     * @return Current number of entries
     */
    public synchronized int size() {
        return cache.size();
    }

    /**
     * Check if cache is empty
     *
     * @return true if cache is empty
     */
    public synchronized boolean isEmpty() {
        return cache.isEmpty();
    }

    /**
     * Get cache statistics
     *
     * @return Cache statistics
     */
    public CacheStats getStats() {
        return stats;
    }

    /**
     * Clean up expired entries
     *
     * @return Number of entries removed
     */
    public synchronized int cleanupExpired() {
        int removed = 0;
        var iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().isExpired(expireAfterWriteMillis)) {
                iterator.remove();
                removed++;
                stats.recordExpiration();
            }
        }
        return removed;
    }

    @Override
    public String toString() {
        return String.format("ProcessorCache{size=%d/%d, expireAfterWrite=%dms, %s}",
                size(), maxSize, expireAfterWriteMillis, stats);
    }
}
