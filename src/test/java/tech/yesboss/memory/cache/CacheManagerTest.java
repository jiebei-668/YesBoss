package tech.yesboss.memory.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CacheManager.
 */
@DisplayName("Cache Manager Tests")
public class CacheManagerTest {

    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        CacheConfig config = CacheConfig.builder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofHours(1))
            .recordStats(true)
            .build();

        cacheManager = CacheManager.builder()
            .defaultConfig(config)
            .build();
    }

    @AfterEach
    void tearDown() {
        if (cacheManager != null) {
            cacheManager.shutdown();
        }
    }

    @Test
    @DisplayName("Should create cache manager with default config")
    void testCreation() {
        // Assert
        assertNotNull(cacheManager);
        assertEquals(0, cacheManager.getCacheCount());
    }

    @Test
    @DisplayName("Should get or create cache with default config")
    void testGetOrCreateCache() {
        // Act
        Cache<String, String> cache1 = cacheManager.getCache("test-cache");
        Cache<String, String> cache2 = cacheManager.getCache("test-cache");

        // Assert
        assertNotNull(cache1);
        assertSame(cache1, cache2, "Should return same instance");
        assertEquals(1, cacheManager.getCacheCount());
    }

    @Test
    @DisplayName("Should create multiple caches with different names")
    void testMultipleCaches() {
        // Act
        Cache<String, String> cache1 = cacheManager.getCache("cache1");
        Cache<String, String> cache2 = cacheManager.getCache("cache2");
        Cache<String, String> cache3 = cacheManager.getCache("cache3");

        // Assert
        assertNotNull(cache1);
        assertNotNull(cache2);
        assertNotNull(cache3);
        assertNotSame(cache1, cache2);
        assertNotSame(cache2, cache3);
        assertEquals(3, cacheManager.getCacheCount());
    }

    @Test
    @DisplayName("Should create cache with custom config")
    void testGetCacheWithCustomConfig() {
        // Arrange
        CacheConfig customConfig = CacheConfig.builder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(30))
            .recordStats(true)
            .build();

        // Act
        Cache<String, String> cache = cacheManager.getCache("custom-cache", customConfig);

        // Assert
        assertNotNull(cache);
        assertEquals(1, cacheManager.getCacheCount());
        assertTrue(cacheManager.hasCache("custom-cache"));
    }

    @Test
    @DisplayName("Should check if cache exists")
    void testHasCache() {
        // Arrange
        cacheManager.getCache("existing-cache");

        // Act & Assert
        assertTrue(cacheManager.hasCache("existing-cache"));
        assertFalse(cacheManager.hasCache("non-existing-cache"));
    }

    @Test
    @DisplayName("Should remove cache from registry")
    void testRemoveCache() {
        // Arrange
        cacheManager.getCache("temp-cache");

        // Act
        boolean removed = cacheManager.removeCache("temp-cache");

        // Assert
        assertTrue(removed);
        assertFalse(cacheManager.hasCache("temp-cache"));
        assertEquals(0, cacheManager.getCacheCount());
    }

    @Test
    @DisplayName("Should return false when removing non-existent cache")
    void testRemoveNonExistentCache() {
        // Act
        boolean removed = cacheManager.removeCache("non-existent");

        // Assert
        assertFalse(removed);
    }

    @Test
    @DisplayName("Should get all cache names")
    void testGetCacheNames() {
        // Arrange
        cacheManager.getCache("cache1");
        cacheManager.getCache("cache2");
        cacheManager.getCache("cache3");

        // Act
        String[] names = cacheManager.getCacheNames();

        // Assert
        assertNotNull(names);
        assertEquals(3, names.length);
        // Note: order is not guaranteed
    }

    @Test
    @DisplayName("Should shutdown all caches")
    void testShutdown() {
        // Arrange
        cacheManager.getCache("cache1");
        cacheManager.getCache("cache2");

        // Act
        cacheManager.shutdown();

        // Assert
        assertEquals(0, cacheManager.getCacheCount());
    }

    @Test
    @DisplayName("Should support multi-level cache via builder")
    void testMultiLevelCacheBuilder() {
        // Arrange
        CacheConfig l2Config = CacheConfig.builder()
            .maximumSize(10000)
            .expireAfterWrite(Duration.ofHours(24))
            .build();

        // Act
        CacheManager multiLevelManager = CacheManager.builder()
            .defaultConfig(CacheConfig.defaults())
            .l2Config(l2Config)
            .build();

        Cache<String, String> cache = multiLevelManager.getCache("ml-cache");

        // Assert
        assertNotNull(cache);
        assertTrue(cache instanceof MultiLevelCache);
        assertTrue(((MultiLevelCache<?, ?>) cache).hasL2Cache());

        // Cleanup
        multiLevelManager.shutdown();
    }

    @Test
    @DisplayName("Should use singleton default instance")
    void testDefaultSingleton() {
        // Act
        CacheManager default1 = CacheManager.getDefault();
        CacheManager default2 = CacheManager.getDefault();

        // Assert
        assertSame(default1, default2, "Should return same instance");
    }

    @Test
    @DisplayName("Should handle cache operations independently")
    void testIndependentCacheOperations() {
        // Arrange
        Cache<String, String> cache1 = cacheManager.getCache("cache1");
        Cache<String, String> cache2 = cacheManager.getCache("cache2");

        // Act
        cache1.put("key", "value1");
        cache2.put("key", "value2");

        // Assert
        assertEquals("value1", cache1.get("key").orElse(null));
        assertEquals("value2", cache2.get("key").orElse(null));
    }

    @Test
    @DisplayName("Should create cache with custom supplier")
    void testGetCacheWithSupplier() {
        // Act
        Cache<String, String> cache = cacheManager.getCache("supplier-cache",
            () -> new InMemoryCache<>(CacheConfig.defaults()));

        // Assert
        assertNotNull(cache);
        assertTrue(cacheManager.hasCache("supplier-cache"));
        assertEquals(1, cacheManager.getCacheCount());
    }

    @Test
    @DisplayName("Should handle concurrent cache creation")
    void testConcurrentCacheCreation() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        // Act
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                cacheManager.getCache("concurrent-cache-" + index);
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Assert
        assertEquals(threadCount, cacheManager.getCacheCount());
    }

    @Test
    @DisplayName("Should handle cache with same name from multiple threads")
    void testConcurrentSameCacheAccess() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        // Act
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                Cache<String, String> cache = cacheManager.getCache("shared-cache");
                cache.put("key", "value");
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Assert
        assertEquals(1, cacheManager.getCacheCount());
        Cache<String, String> cache = cacheManager.getCache("shared-cache");
        assertTrue(cache.get("key").isPresent());
    }
}
