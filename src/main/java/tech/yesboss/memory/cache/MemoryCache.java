package tech.yesboss.memory.cache;

import java.util.Map;
import java.util.Set;

/**
 * Memory Cache Interface
 *
 * <p>Defines a multi-level caching mechanism for memory persistence operations.</p>
 *
 * <p><b>Cache Levels:</b></p>
 * <ul>
 *   <li>L1 (In-Memory): Fast, small cache using ConcurrentHashMap</li>
 *   <li>L2 (Persistent): Slower, larger cache with disk backing</li>
 * </ul>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>LRU eviction policy</li>
 *   <li>Time-based expiration</li>
 *   <li>Size-based eviction</li>
 *   <li>Cache statistics</li>
 *   <li>Bulk operations</li>
 *   <li>Cache warming</li>
 *   <li>Conditional updates</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * MemoryCache cache = new MemoryCacheImpl(config);
 * cache.put("key1", "value1");
 * String value = cache.get("key1");
 * cache.invalidate("key1");
 * </pre>
 *
 * @param <K> Key type
 * @param <V> Value type
 * @author YesBoss Team
 * @version 1.0
 */
public interface MemoryCache<K, V> {

    // ==================== Basic Operations ====================

    /**
     * Get value from cache.
     *
     * @param key Cache key
     * @return Cached value or null if not found
     */
    V get(K key);

    /**
     * Put value in cache.
     *
     * @param key Cache key
     * @param value Value to cache
     */
    void put(K key, V value);

    /**
     * Put value in cache with expiration.
     *
     * @param key Cache key
     * @param value Value to cache
     * @param expireAfterMs Time to expiration in milliseconds
     */
    void put(K key, V value, long expireAfterMs);

    /**
     * Get value from cache, or compute if not present.
     *
     * @param key Cache key
     * @param loader Function to compute value if not in cache
     * @return Cached or computed value
     */
    V getOrCompute(K key, CacheLoader<K, V> loader);

    /**
     * Check if key exists in cache.
     *
     * @param key Cache key
     * @return true if key exists and is not expired
     */
    boolean containsKey(K key);

    /**
     * Remove entry from cache.
     *
     * @param key Cache key
     * @return Removed value or null if not found
     */
    V remove(K key);

    /**
     * Clear all entries from cache.
     */
    void clear();

    // ==================== Bulk Operations ====================

    /**
     * Get all values for the given keys.
     *
     * @param keys Set of cache keys
     * @return Map of keys to values (only for keys found in cache)
     */
    Map<K, V> getAll(Set<K> keys);

    /**
     * Put all entries in cache.
     *
     * @param entries Map of keys to values
     */
    void putAll(Map<K, V> entries);

    /**
     * Put all entries in cache with expiration.
     *
     * @param entries Map of keys to values
     * @param expireAfterMs Time to expiration in milliseconds
     */
    void putAll(Map<K, V> entries, long expireAfterMs);

    /**
     * Remove all entries for the given keys.
     *
     * @param keys Set of cache keys
     * @return Number of entries removed
     */
    int removeAll(Set<K> keys);

    // ==================== Conditional Operations ====================

    /**
     * Put value in cache only if key is not present.
     *
     * @param key Cache key
     * @param value Value to cache
     * @return true if value was put, false if key already exists
     */
    boolean putIfAbsent(K key, V value);

    /**
     * Replace value in cache only if key is present.
     *
     * @param key Cache key
     * @param oldValue Expected current value
     * @param newValue New value to set
     * @return true if value was replaced, false otherwise
     */
    boolean replace(K key, V oldValue, V newValue);

    /**
     * Replace value in cache only if key is present.
     *
     * @param key Cache key
     * @param value New value to set
     * @return Previous value or null if key was not present
     */
    V replace(K key, V value);

    // ==================== Cache Statistics ====================

    /**
     * Get cache statistics.
     *
     * @return Cache statistics
     */
    CacheStats getStats();

    /**
     * Reset cache statistics.
     */
    void resetStats();

    // ==================== Cache Management ====================

    /**
     * Get current cache size.
     *
     * @return Current number of entries
     */
    int size();

    /**
     * Check if cache is empty.
     *
     * @return true if cache is empty
     */
    boolean isEmpty();

    /**
     * Get all keys in cache.
     *
     * @return Set of all keys
     */
    Set<K> keys();

    /**
     * Get cache configuration.
     *
     * @return Cache configuration
     */
    MemoryCacheConfig getConfig();

    /**
     * Clean up expired entries.
     *
     * @return Number of entries removed
     */
    int cleanupExpired();

    /**
     * Warm up cache with pre-loaded data.
     *
     * @param entries Map of keys to values to pre-load
     * @return Number of entries loaded
     */
    int warmUp(Map<K, V> entries);

    /**
     * Invalidate all entries matching the given predicate.
     *
     * @param predicate Predicate to test entries
     * @return Number of entries invalidated
     */
    int invalidateAll(CachePredicate<K, V> predicate);

    /**
     * Check if cache is enabled.
     *
     * @return true if cache is enabled
     */
    boolean isEnabled();

    // ==================== Inner Interfaces ====================

    /**
     * Cache loader interface for computing values on demand.
     *
     * @param <K> Key type
     * @param <V> Value type
     */
    @FunctionalInterface
    interface CacheLoader<K, V> {
        /**
         * Load value for the given key.
         *
         * @param key Cache key
         * @return Computed value
         * @throws Exception if loading fails
         */
        V load(K key) throws Exception;
    }

    /**
     * Cache predicate for conditional invalidation.
     *
     * @param <K> Key type
     * @param <V> Value type
     */
    @FunctionalInterface
    interface CachePredicate<K, V> {
        /**
         * Test if cache entry should be invalidated.
         *
         * @param key Cache key
         * @param value Cache value
         * @return true if entry should be invalidated
         */
        boolean test(K key, V value);
    }

    /**
     * Cache statistics.
     */
    class CacheStats {
        private final long hitCount;
        private final long missCount;
        private final long evictionCount;
        private final long expirationCount;
        private final long size;
        private final long maxSize;
        private final double hitRate;

        public CacheStats(long hitCount, long missCount, long evictionCount,
                         long expirationCount, long size, long maxSize) {
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.evictionCount = evictionCount;
            this.expirationCount = expirationCount;
            this.size = size;
            this.maxSize = maxSize;
            long requests = hitCount + missCount;
            this.hitRate = requests == 0 ? 0.0 : (double) hitCount / requests;
        }

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
            return hitRate;
        }

        public long getSize() {
            return size;
        }

        public long getMaxSize() {
            return maxSize;
        }

        public double getUsage() {
            return maxSize == 0 ? 0.0 : (double) size / maxSize;
        }

        @Override
        public String toString() {
            return String.format("CacheStats{hits=%d, misses=%d, evictions=%d, expirations=%d, " +
                    "size=%d/%d, hitRate=%.2f%%, usage=%.2f%%}",
                    hitCount, missCount, evictionCount, expirationCount,
                    size, maxSize, hitRate * 100, getUsage() * 100);
        }
    }
}
