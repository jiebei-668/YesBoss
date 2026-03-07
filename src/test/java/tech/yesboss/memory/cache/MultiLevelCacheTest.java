package tech.yesboss.memory.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MultiLevelCache.
 */
@DisplayName("Multi-Level Cache Tests")
public class MultiLevelCacheTest {

    private MultiLevelCache<String, String> multiLevelCache;
    private CacheConfig l1Config;
    private CacheConfig l2Config;

    @BeforeEach
    void setUp() {
        l1Config = CacheConfig.builder()
            .maximumSize(10)
            .expireAfterWrite(Duration.ofMinutes(5))
            .recordStats(true)
            .build();

        l2Config = CacheConfig.builder()
            .maximumSize(100)
            .expireAfterWrite(Duration.ofHours(1))
            .recordStats(true)
            .build();

        multiLevelCache = new MultiLevelCache<>(l1Config, l2Config);
    }

    @Test
    @DisplayName("Should create multi-level cache successfully")
    void testCreation() {
        // Assert
        assertNotNull(multiLevelCache);
        assertTrue(multiLevelCache.hasL2Cache());
        assertEquals(10, multiLevelCache.getL1Cache().size()); // Max size, not current size
    }

    @Test
    @DisplayName("Should create cache without L2")
    void testCreationWithoutL2() {
        // Act
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(l1Config);

        // Assert
        assertNotNull(cache);
        assertFalse(cache.hasL2Cache());
        assertNull(cache.getL2Cache());
    }

    @Test
    @DisplayName("Should get from L1 cache on hit")
    void testL1Hit() {
        // Arrange
        String key = "l1-key";
        String value = "l1-value";
        multiLevelCache.put(key, value);

        // Act
        Optional<String> result = multiLevelCache.get(key);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(value, result.get());
    }

    @Test
    @DisplayName("Should get from L2 cache on L1 miss")
    void testL2Hit() {
        // Arrange
        String key = "l2-key";
        String value = "l2-value";

        // Put directly in L2
        multiLevelCache.getL2Cache().put(key, value);

        // Act
        Optional<String> result = multiLevelCache.get(key);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(value, result.get());

        // Should be promoted to L1
        assertTrue(multiLevelCache.getL1Cache().containsKey(key));
    }

    @Test
    @DisplayName("Should return empty on miss in both levels")
    void testMissBothLevels() {
        // Act
        Optional<String> result = multiLevelCache.get("non-existent");

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should put in both L1 and L2")
    void testPutInBothLevels() {
        // Arrange
        String key = "both-key";
        String value = "both-value";

        // Act
        multiLevelCache.put(key, value);

        // Assert
        assertTrue(multiLevelCache.getL1Cache().containsKey(key));
        assertTrue(multiLevelCache.getL2Cache().containsKey(key));

        Optional<String> l1Value = multiLevelCache.getL1Cache().get(key);
        Optional<String> l2Value = multiLevelCache.getL2Cache().get(key);

        assertTrue(l1Value.isPresent());
        assertTrue(l2Value.isPresent());
        assertEquals(value, l1Value.get());
        assertEquals(value, l2Value.get());
    }

    @Test
    @DisplayName("Should evict from both levels")
    void testEvictFromBothLevels() {
        // Arrange
        String key = "evict-key";
        multiLevelCache.put(key, "value");

        // Act
        multiLevelCache.evict(key);

        // Assert
        assertFalse(multiLevelCache.getL1Cache().containsKey(key));
        assertFalse(multiLevelCache.getL2Cache().containsKey(key));
    }

    @Test
    @DisplayName("Should clear both levels")
    void testClearBothLevels() {
        // Arrange
        multiLevelCache.put("key1", "value1");
        multiLevelCache.put("key2", "value2");
        multiLevelCache.put("key3", "value3");

        // Act
        multiLevelCache.clear();

        // Assert
        assertEquals(0, multiLevelCache.getL1Cache().size());
        assertEquals(0, multiLevelCache.getL2Cache().size());
        assertEquals(0, multiLevelCache.size());
    }

    @Test
    @DisplayName("Should track statistics across levels")
    void testStatistics() {
        // Arrange
        multiLevelCache.put("key1", "value1");
        multiLevelCache.put("key2", "value2");

        // Act
        multiLevelCache.get("key1"); // L1 hit
        multiLevelCache.get("key3"); // Miss both

        // Assert
        CacheStatistics stats = multiLevelCache.getStatistics();
        assertTrue(stats.getRequestCount() > 0);
    }

    @Test
    @DisplayName("Should check if key exists in any level")
    void testContainsKey() {
        // Arrange
        multiLevelCache.put("existing-key", "value");

        // Act & Assert
        assertTrue(multiLevelCache.containsKey("existing-key"));
        assertFalse(multiLevelCache.containsKey("non-existing-key"));
    }

    @Test
    @DisplayName("Should use cache loader on miss")
    void testGetOrCompute() {
        // Arrange
        String key = "compute-key";
        AtomicInteger loadCount = new AtomicInteger(0);
        Cache.CacheLoader<String, String> loader = k -> {
            loadCount.incrementAndGet();
            return "computed-value";
        };

        // Act
        String value1 = multiLevelCache.getOrCompute(key, loader);
        String value2 = multiLevelCache.getOrCompute(key, loader);

        // Assert
        assertEquals("computed-value", value1);
        assertEquals("computed-value", value2);
        assertEquals(1, loadCount.get());
    }

    @Test
    @DisplayName("Should reset statistics in both levels")
    void testResetStatistics() {
        // Arrange
        multiLevelCache.put("key", "value");
        multiLevelCache.get("key");

        // Act
        multiLevelCache.resetStatistics();

        // Assert
        CacheStatistics l1Stats = multiLevelCache.getL1Cache().getStatistics();
        CacheStatistics l2Stats = multiLevelCache.getL2Cache().getStatistics();

        assertEquals(0, l1Stats.getHitCount());
        assertEquals(0, l1Stats.getMissCount());
        assertEquals(0, l2Stats.getHitCount());
        assertEquals(0, l2Stats.getMissCount());
    }

    @Test
    @DisplayName("Should handle concurrent operations safely")
    void testConcurrentOperations() throws InterruptedException {
        // Arrange
        int threadCount = 5;
        int operationsPerThread = 50;
        Thread[] threads = new Thread[threadCount];

        // Act
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    String key = "thread-" + threadId + "-key-" + j;
                    String value = "value-" + j;
                    multiLevelCache.put(key, value);
                    multiLevelCache.get(key);
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Assert
        assertTrue(multiLevelCache.size() > 0);
        CacheStatistics stats = multiLevelCache.getStatistics();
        assertTrue(stats.getRequestCount() > 0);
    }

    @Test
    @DisplayName("Should promote L2 hits to L1")
    void testL2PromotionToL1() {
        // Arrange
        String key = "promote-key";
        String value = "promote-value";

        // Put in L2 only
        multiLevelCache.getL2Cache().put(key, value);
        assertFalse(multiLevelCache.getL1Cache().containsKey(key));

        // Act - Get from L2
        Optional<String> result = multiLevelCache.get(key);

        // Assert - Should be in L1 now
        assertTrue(result.isPresent());
        assertTrue(multiLevelCache.getL1Cache().containsKey(key));
    }
}
