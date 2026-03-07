package tech.yesboss.memory.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Generic cache interface for multi-level caching strategy.
 *
 * <p>This interface defines the contract for cache operations with support for:
 * <ul>
 *   <li>Synchronous and asynchronous operations</li>
 *   <li>Time-based expiration</li>
 *   <li>Cache statistics</li>
 *   <li>Multi-level cache coordination</li>
 * </ul>
 *
 * @param <K> The type of keys maintained by this cache
 * @param <V> The type of mapped values
 */
public interface Cache<K, V> {

    /**
     * Get a value from the cache.
     *
     * @param key the key whose associated value is to be returned
     * @return Optional containing the value, or empty if not present
     * @throws CacheException if the operation fails
     */
    Optional<V> get(K key);

    /**
     * Get a value asynchronously from the cache.
     *
     * @param key the key whose associated value is to be returned
     * @return CompletableFuture containing Optional with the value
     */
    CompletableFuture<Optional<V>> getAsync(K key);

    /**
     * Associate a value with the specified key in the cache.
     *
     * @param key the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key
     * @throws CacheException if the operation fails
     */
    void put(K key, V value);

    /**
     * Associate a value with the specified key asynchronously.
     *
     * @param key the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key
     * @return CompletableFuture that completes when the operation is done
     */
    CompletableFuture<Void> putAsync(K key, V value);

    /**
     * Associate a value with the specified key and expiration time.
     *
     * @param key the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key
     * @param expiration the time duration after which the entry should expire
     * @throws CacheException if the operation fails
     */
    void put(K key, V value, Duration expiration);

    /**
     * Get a value from the cache, or compute it if not present.
     *
     * @param key the key with which the specified value is to be associated
     * @param loader the function to compute the value if not present
     * @return the value associated with the key
     * @throws CacheException if the operation fails
     */
    V getOrCompute(K key, CacheLoader<K, V> loader);

    /**
     * Get a value from the cache, or compute it asynchronously if not present.
     *
     * @param key the key with which the specified value is to be associated
     * @param loader the function to compute the value if not present
     * @return CompletableFuture containing the value
     */
    CompletableFuture<V> getOrComputeAsync(K key, CacheLoader<K, V> loader);

    /**
     * Remove the entry for the specified key.
     *
     * @param key the key whose mapping is to be removed
     * @throws CacheException if the operation fails
     */
    void evict(K key);

    /**
     * Remove the entry for the specified key asynchronously.
     *
     * @param key the key whose mapping is to be removed
     * @return CompletableFuture that completes when the operation is done
     */
    CompletableFuture<Void> evictAsync(K key);

    /**
     * Remove all entries from the cache.
     *
     * @throws CacheException if the operation fails
     */
    void clear();

    /**
     * Remove all entries from the cache asynchronously.
     *
     * @return CompletableFuture that completes when the operation is done
     */
    CompletableFuture<Void> clearAsync();

    /**
     * Return the number of entries in the cache.
     *
     * @return the number of entries
     */
    long size();

    /**
     * Return cache statistics including hits, misses, and hit rate.
     *
     * @return CacheStatistics object
     */
    CacheStatistics getStatistics();

    /**
     * Reset cache statistics.
     */
    void resetStatistics();

    /**
     * Check if the cache contains an entry for the specified key.
     *
     * @param key the key whose presence is to be tested
     * @return true if the cache contains a mapping for the specified key
     */
    boolean containsKey(K key);

    /**
     * Get all keys currently in the cache.
     *
     * @return Iterable of all keys
     */
    Iterable<K> keys();

    /**
     * Get all values currently in the cache.
     *
     * @return Iterable of all values
     */
    Iterable<V> values();

    /**
     * Function to compute values on cache miss.
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     */
    @FunctionalInterface
    interface CacheLoader<K, V> {
        /**
         * Compute the value for the given key.
         *
         * @param key the key to compute the value for
         * @return the computed value
         * @throws Exception if computation fails
         */
        V load(K key) throws Exception;
    }
}
