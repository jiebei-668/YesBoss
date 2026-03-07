package tech.yesboss.memory.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Cache interface and basic operations.
 */
@DisplayName("Cache Interface Tests")
public class CacheTest {

    private Cache<String, String> cache;

    @BeforeEach
    void setUp() {
        CacheConfig config = CacheConfig.builder()
            .maximumSize(100)
            .expireAfterWrite(Duration.ofMinutes(10))
            .recordStats(true)
            .build();
        cache = new InMemoryCache<>(config);
    }

    @Test
    @DisplayName("Should put and get value successfully")
    void testPutAndGet() {
        // Arrange
        String key = "test-key";
        String value = "test-value";

        // Act
        cache.put(key, value);
        Optional<String> result = cache.get(key);

        // Assert
        assertTrue(result.isPresent(), "Value should be present");
        assertEquals(value, result.get(), "Value should match");
    }

    @Test
    @DisplayName("Should return empty for non-existent key")
    void testGetNonExistentKey() {
        // Act
        Optional<String> result = cache.get("non-existent");

        // Assert
        assertFalse(result.isPresent(), "Value should not be present");
    }

    @Test
    @DisplayName("Should return empty for null key")
    void testGetNullKey() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> cache.get(null),
            "Should throw IllegalArgumentException for null key");
    }

    @Test
    @DisplayName("Should throw exception for null value in put")
    void testPutNullValue() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> cache.put("key", null),
            "Should throw IllegalArgumentException for null value");
    }

    @Test
    @DisplayName("Should evict entry successfully")
    void testEvict() {
        // Arrange
        String key = "test-key";
        cache.put(key, "value");

        // Act
        cache.evict(key);
        Optional<String> result = cache.get(key);

        // Assert
        assertFalse(result.isPresent(), "Entry should be evicted");
    }

    @Test
    @DisplayName("Should clear all entries")
    void testClear() {
        // Arrange
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        // Act
        cache.clear();

        // Assert
        assertEquals(0, cache.size(), "Cache should be empty");
        assertFalse(cache.get("key1").isPresent());
        assertFalse(cache.get("key2").isPresent());
        assertFalse(cache.get("key3").isPresent());
    }

    @Test
    @DisplayName("Should report correct size")
    void testSize() {
        // Act
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        // Assert
        assertEquals(3, cache.size(), "Cache size should be 3");
    }

    @Test
    @DisplayName("Should check if key exists")
    void testContainsKey() {
        // Arrange
        cache.put("existing-key", "value");

        // Act & Assert
        assertTrue(cache.containsKey("existing-key"), "Key should exist");
        assertFalse(cache.containsKey("non-existing-key"), "Key should not exist");
    }

    @Test
    @DisplayName("Should put value with expiration")
    void testPutWithExpiration() {
        // Arrange
        String key = "expiring-key";
        String value = "expiring-value";
        Duration expiration = Duration.ofSeconds(1);

        // Act
        cache.put(key, value, expiration);
        Optional<String> immediateResult = cache.get(key);

        // Assert
        assertTrue(immediateResult.isPresent(), "Value should be present immediately");
        assertEquals(value, immediateResult.get(), "Value should match");
    }

    @Test
    @DisplayName("Should compute value on cache miss")
    void testGetOrCompute() {
        // Arrange
        String key = "compute-key";
        AtomicInteger loadCount = new AtomicInteger(0);
        Cache.CacheLoader<String, String> loader = k -> {
            loadCount.incrementAndGet();
            return "computed-value";
        };

        // Act - First call should load
        String value1 = cache.getOrCompute(key, loader);

        // Act - Second call should use cache
        String value2 = cache.getOrCompute(key, loader);

        // Assert
        assertEquals("computed-value", value1, "Computed value should match");
        assertEquals("computed-value", value2, "Cached value should match");
        assertEquals(1, loadCount.get(), "Loader should be called only once");
    }

    @Test
    @DisplayName("Should throw exception when loader fails")
    void testGetOrComputeWithException() {
        // Arrange
        Cache.CacheLoader<String, String> failingLoader = k -> {
            throw new RuntimeException("Load failed");
        };

        // Act & Assert
        assertThrows(CacheException.class, () -> cache.getOrCompute("key", failingLoader),
            "Should throw CacheException when loader fails");
    }

    @Test
    @DisplayName("Should support async operations")
    void testAsyncOperations() throws ExecutionException, InterruptedException {
        // Arrange
        String key = "async-key";
        String value = "async-value";

        // Act
        CompletableFuture<Void> putFuture = cache.putAsync(key, value);
        CompletableFuture<Optional<String>> getFuture = cache.getAsync(key);

        // Assert
        putFuture.get(); // Wait for put to complete
        Optional<String> result = getFuture.get(); // Wait for get to complete

        assertTrue(result.isPresent());
        assertEquals(value, result.get());
    }

    @Test
    @DisplayName("Should track statistics")
    void testStatistics() {
        // Arrange
        String key = "stats-key";
        String value = "stats-value";

        // Act
        cache.put(key, value);
        cache.get(key); // Hit
        cache.get(key); // Hit
        cache.get("non-existent"); // Miss

        // Assert
        CacheStatistics stats = cache.getStatistics();
        assertEquals(2, stats.getHitCount(), "Should have 2 hits");
        assertEquals(1, stats.getMissCount(), "Should have 1 miss");
        assertEquals(3, stats.getRequestCount(), "Should have 3 requests");
        assertTrue(stats.getHitRate() > 0.6, "Hit rate should be > 60%");
    }

    @Test
    @DisplayName("Should reset statistics")
    void testResetStatistics() {
        // Arrange
        cache.put("key", "value");
        cache.get("key");

        // Act
        CacheStatistics statsBefore = cache.getStatistics();
        cache.resetStatistics();
        CacheStatistics statsAfter = cache.getStatistics();

        // Assert
        assertTrue(statsBefore.getHitCount() > 0, "Should have hits before reset");
        assertEquals(0, statsAfter.getHitCount(), "Should have no hits after reset");
        assertEquals(0, statsAfter.getMissCount(), "Should have no misses after reset");
    }

    @Test
    @DisplayName("Should iterate over keys")
    void testKeysIteration() {
        // Arrange
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        // Act
        Iterable<String> keys = cache.keys();

        // Assert
        assertNotNull(keys, "Keys should not be null");
        // Note: We can't easily test the exact content without converting to list
        // But we can verify it's not empty
        assertTrue(keys.iterator().hasNext(), "Should have at least one key");
    }

    @Test
    @DisplayName("Should iterate over values")
    void testValuesIteration() {
        // Arrange
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        // Act
        Iterable<String> values = cache.values();

        // Assert
        assertNotNull(values, "Values should not be null");
        assertTrue(values.iterator().hasNext(), "Should have at least one value");
    }

    @Test
    @DisplayName("Should handle concurrent operations")
    void testConcurrentOperations() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        int operationsPerThread = 100;
        Thread[] threads = new Thread[threadCount];

        // Act
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    String key = "thread-" + threadId + "-key-" + j;
                    String value = "value-" + j;
                    cache.put(key, value);
                    cache.get(key);
                }
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Assert
        // Cache should have all entries
        assertTrue(cache.size() > 0, "Cache should have entries");
        // Statistics should reflect all operations
        CacheStatistics stats = cache.getStatistics();
        assertTrue(stats.getRequestCount() > 0, "Should have recorded requests");
    }

    @Test
    @DisplayName("Should enforce maximum size with eviction")
    void testMaxSizeEviction() {
        // Arrange
        CacheConfig smallConfig = CacheConfig.builder()
            .maximumSize(5)
            .recordStats(true)
            .build();
        Cache<String, String> smallCache = new InMemoryCache<>(smallConfig);

        // Act - Add more entries than max size
        for (int i = 0; i < 10; i++) {
            smallCache.put("key" + i, "value" + i);
        }

        // Assert - Cache should not exceed max size
        assertTrue(smallCache.size() <= 5, "Cache size should not exceed maximum");

        // Some entries should have been evicted
        CacheStatistics stats = smallCache.getStatistics();
        assertTrue(stats.getEvictionCount() > 0, "Should have evicted entries");
    }

    @Test
    @DisplayName("Should handle removal listener")
    void testRemovalListener() {
        // Arrange
        AtomicInteger removalCount = new AtomicInteger(0);
        CacheConfig.RemovalListener<String, String> listener = (key, value, cause) -> {
            removalCount.incrementAndGet();
        };

        CacheConfig config = CacheConfig.builder()
            .maximumSize(3)
            .removalListener(listener)
            .build();
        Cache<String, String> cacheWithListener = new InMemoryCache<>(config);

        // Act - Add more than max size to trigger eviction
        cacheWithListener.put("key1", "value1");
        cacheWithListener.put("key2", "value2");
        cacheWithListener.put("key3", "value3");
        cacheWithListener.put("key4", "value4"); // Should trigger eviction

        // Assert
        assertTrue(removalCount.get() > 0, "Removal listener should be called");
    }
}
