package tech.yesboss.memory.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the complete cache system.
 */
@DisplayName("Cache Integration Tests")
public class CacheIntegrationTest {

    private CacheManager cacheManager;
    private Cache<String, TestObject> cache;

    @BeforeEach
    void setUp() {
        CacheConfig config = CacheConfig.builder()
            .maximumSize(100)
            .expireAfterWrite(Duration.ofMinutes(10))
            .expireAfterAccess(Duration.ofMinutes(5))
            .recordStats(true)
            .concurrencyLevel(4)
            .build();

        cacheManager = CacheManager.builder()
            .defaultConfig(config)
            .build();

        cache = cacheManager.getCache("integration-test");
    }

    @AfterEach
    void tearDown() {
        if (cacheManager != null) {
            cacheManager.shutdown();
        }
    }

    /**
     * Test object for caching.
     */
    private static class TestObject {
        private final String id;
        private final String data;
        private final long timestamp;

        public TestObject(String id, String data) {
            this.id = id;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        public String getId() { return id; }
        public String getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }

    @Test
    @DisplayName("Should complete full cache lifecycle")
    void testFullCacheLifecycle() {
        // 1. Create and store object
        TestObject obj = new TestObject("id1", "data1");
        cache.put("key1", obj);

        // 2. Retrieve object
        Optional<TestObject> retrieved = cache.get("key1");
        assertTrue(retrieved.isPresent());
        assertEquals("id1", retrieved.get().getId());
        assertEquals("data1", retrieved.get().getData());

        // 3. Update object
        TestObject updated = new TestObject("id1", "updated-data");
        cache.put("key1", updated);

        Optional<TestObject> updatedRetrieved = cache.get("key1");
        assertTrue(updatedRetrieved.isPresent());
        assertEquals("updated-data", updatedRetrieved.get().getData());

        // 4. Evict object
        cache.evict("key1");
        assertFalse(cache.get("key1").isPresent());

        // 5. Verify statistics
        CacheStatistics stats = cache.getStatistics();
        assertTrue(stats.getRequestCount() > 0);
    }

    @Test
    @DisplayName("Should handle cache with loader correctly")
    void testCacheWithLoader() {
        // Arrange
        AtomicInteger loadCount = new AtomicInteger(0);
        Cache.CacheLoader<String, TestObject> loader = key -> {
            loadCount.incrementAndGet();
            return new TestObject(key, "loaded-data");
        };

        // Act - First call should load
        TestObject obj1 = cache.getOrCompute("load-key", loader);

        // Act - Second call should use cache
        TestObject obj2 = cache.getOrCompute("load-key", loader);

        // Assert
        assertNotNull(obj1);
        assertNotNull(obj2);
        assertEquals(obj1.getId(), obj2.getId());
        assertEquals(1, loadCount.get(), "Loader should be called only once");
    }

    @Test
    @DisplayName("Should handle complex objects")
    void testComplexObjects() {
        // Arrange
        TestObject obj1 = new TestObject("complex1", "data1");
        TestObject obj2 = new TestObject("complex2", "data2");
        TestObject obj3 = new TestObject("complex3", "data3");

        // Act
        cache.put("obj1", obj1);
        cache.put("obj2", obj2);
        cache.put("obj3", obj3);

        // Assert
        assertEquals(3, cache.size());
        assertTrue(cache.containsKey("obj1"));
        assertTrue(cache.containsKey("obj2"));
        assertTrue(cache.containsKey("obj3"));
    }

    @Test
    @DisplayName("Should handle concurrent access safely")
    void testConcurrentAccess() throws InterruptedException {
        // Arrange
        int threadCount = 20;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // Act
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "thread-" + threadId + "-key-" + j;
                        TestObject obj = new TestObject(key, "data-" + j);

                        // Put
                        cache.put(key, obj);

                        // Get
                        cache.get(key);

                        // Check contains
                        cache.containsKey(key);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for completion
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Assert
        assertTrue(cache.size() > 0, "Cache should have entries");
        CacheStatistics stats = cache.getStatistics();
        assertTrue(stats.getRequestCount() > 0, "Should have recorded requests");
    }

    @Test
    @DisplayName("Should enforce size limits correctly")
    void testSizeLimits() {
        // Arrange
        CacheConfig smallConfig = CacheConfig.builder()
            .maximumSize(10)
            .recordStats(true)
            .build();

        Cache<String, TestObject> smallCache = cacheManager.getCache("small-cache", smallConfig);

        // Act - Add more entries than max size
        for (int i = 0; i < 20; i++) {
            smallCache.put("key" + i, new TestObject("id" + i, "data" + i));
        }

        // Assert
        assertTrue(smallCache.size() <= 10, "Cache should not exceed maximum size");

        CacheStatistics stats = smallCache.getStatistics();
        assertTrue(stats.getEvictionCount() > 0, "Should have evicted entries");
    }

    @Test
    @DisplayName("Should work with multi-level cache")
    void testMultiLevelCacheIntegration() {
        // Arrange
        CacheConfig l1Config = CacheConfig.builder()
            .maximumSize(5)
            .expireAfterWrite(Duration.ofMinutes(1))
            .recordStats(true)
            .build();

        CacheConfig l2Config = CacheConfig.builder()
            .maximumSize(20)
            .expireAfterWrite(Duration.ofHours(1))
            .recordStats(true)
            .build();

        MultiLevelCache<String, TestObject> mlCache =
            new MultiLevelCache<>(l1Config, l2Config);

        // Act
        TestObject obj = new TestObject("ml-id", "ml-data");
        mlCache.put("ml-key", obj);

        Optional<TestObject> retrieved = mlCache.get("ml-key");

        // Assert
        assertTrue(retrieved.isPresent());
        assertEquals("ml-id", retrieved.get().getId());

        // Should be in both L1 and L2
        assertTrue(mlCache.getL1Cache().containsKey("ml-key"));
        assertTrue(mlCache.getL2Cache().containsKey("ml-key"));
    }

    @Test
    @DisplayName("Should track statistics accurately")
    void testStatisticsTracking() {
        // Arrange
        cache.put("hit1", new TestObject("id1", "data1"));
        cache.put("hit2", new TestObject("id2", "data2"));
        cache.put("hit3", new TestObject("id3", "data3"));

        // Act - Generate hits and misses
        cache.get("hit1"); // Hit
        cache.get("hit2"); // Hit
        cache.get("miss1"); // Miss
        cache.get("miss2"); // Miss
        cache.get("hit3"); // Hit

        // Assert
        CacheStatistics stats = cache.getStatistics();
        assertEquals(3, stats.getHitCount());
        assertEquals(2, stats.getMissCount());
        assertEquals(5, stats.getRequestCount());
        assertEquals(0.6, stats.getHitRate(), 0.01);
    }

    @Test
    @DisplayName("Should handle cache removal listener")
    void testRemovalListener() {
        // Arrange
        AtomicInteger removalCount = new AtomicInteger(0);
        CacheConfig.RemovalListener<String, TestObject> listener = (key, value, cause) -> {
            removalCount.incrementAndGet();
        };

        CacheConfig configWithListener = CacheConfig.builder()
            .maximumSize(3)
            .removalListener(listener)
            .build();

        Cache<String, TestObject> cacheWithListener =
            cacheManager.getCache("listener-cache", configWithListener);

        // Act - Add more than max size to trigger eviction
        cacheWithListener.put("key1", new TestObject("id1", "data1"));
        cacheWithListener.put("key2", new TestObject("id2", "data2"));
        cacheWithListener.put("key3", new TestObject("id3", "data3"));
        cacheWithListener.put("key4", new TestObject("id4", "data4")); // Should trigger eviction

        // Assert
        assertTrue(removalCount.get() > 0, "Removal listener should be called");
    }

    @Test
    @DisplayName("Should handle async operations correctly")
    void testAsyncOperations() throws Exception {
        // Arrange
        TestObject obj = new TestObject("async-id", "async-data");

        // Act
        cache.putAsync("async-key", obj).get(); // Wait for completion
        java.util.concurrent.CompletableFuture<Optional<TestObject>> future =
            cache.getAsync("async-key");
        Optional<TestObject> result = future.get(); // Wait for completion

        // Assert
        assertTrue(result.isPresent());
        assertEquals("async-id", result.get().getId());
    }

    @Test
    @DisplayName("Should handle cache invalidation")
    void testCacheInvalidation() {
        // Arrange
        cache.put("key1", new TestObject("id1", "data1"));
        cache.put("key2", new TestObject("id2", "data2"));

        // Act
        cache.evict("key1");
        cache.clear();

        // Assert
        assertEquals(0, cache.size());
        assertFalse(cache.containsKey("key1"));
        assertFalse(cache.containsKey("key2"));
    }

    @Test
    @DisplayName("Should maintain consistency across multiple cache instances")
    void testMultipleCacheInstances() {
        // Arrange
        Cache<String, TestObject> cache1 = cacheManager.getCache("instance1");
        Cache<String, TestObject> cache2 = cacheManager.getCache("instance2");

        TestObject obj1 = new TestObject("id1", "data1");
        TestObject obj2 = new TestObject("id2", "data2");

        // Act
        cache1.put("key", obj1);
        cache2.put("key", obj2);

        // Assert - Should be independent
        Optional<TestObject> result1 = cache1.get("key");
        Optional<TestObject> result2 = cache2.get("key");

        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertEquals("id1", result1.get().getId());
        assertEquals("id2", result2.get().getId());
    }

    @Test
    @DisplayName("Should handle null and edge cases")
    void testEdgeCases() {
        // Act & Assert - Null key
        assertThrows(IllegalArgumentException.class, () -> cache.get(null));

        // Act & Assert - Null value
        assertThrows(IllegalArgumentException.class, () -> cache.put("key", null));

        // Act & Assert - Empty cache operations
        assertFalse(cache.get("non-existent").isPresent());
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("Should work with different data types")
    void testDifferentDataTypes() {
        // Arrange
        Cache<String, Integer> intCache = cacheManager.getCache("int-cache");
        Cache<String, String> stringCache = cacheManager.getCache("string-cache");

        // Act
        intCache.put("number", 42);
        stringCache.put("text", "hello");

        // Assert
        assertEquals(42, intCache.get("number").orElse(0));
        assertEquals("hello", stringCache.get("text").orElse(""));
    }
}
