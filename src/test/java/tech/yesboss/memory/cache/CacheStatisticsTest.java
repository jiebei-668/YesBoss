package tech.yesboss.memory.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CacheStatistics.
 */
@DisplayName("Cache Statistics Tests")
public class CacheStatisticsTest {

    private CacheStatistics statistics;

    @BeforeEach
    void setUp() {
        statistics = new CacheStatistics();
    }

    @Test
    @DisplayName("Should initialize with zero values")
    void testInitialization() {
        // Assert
        assertEquals(0, statistics.getHitCount());
        assertEquals(0, statistics.getMissCount());
        assertEquals(0, statistics.getRequestCount());
        assertEquals(0, statistics.getEvictionCount());
        assertEquals(0, statistics.getLoadSuccessCount());
        assertEquals(0, statistics.getLoadFailureCount());
    }

    @Test
    @DisplayName("Should record hits correctly")
    void testRecordHits() {
        // Act
        statistics.recordHit();
        statistics.recordHit();
        statistics.recordHit();

        // Assert
        assertEquals(3, statistics.getHitCount());
        assertEquals(3, statistics.getRequestCount());
    }

    @Test
    @DisplayName("Should record misses correctly")
    void testRecordMisses() {
        // Act
        statistics.recordMiss();
        statistics.recordMiss();

        // Assert
        assertEquals(2, statistics.getMissCount());
        assertEquals(2, statistics.getRequestCount());
    }

    @Test
    @DisplayName("Should calculate hit rate correctly")
    void testHitRateCalculation() {
        // Act
        statistics.recordHit();
        statistics.recordHit();
        statistics.recordMiss();
        statistics.recordMiss();
        statistics.recordHit();

        // Assert
        assertEquals(3, statistics.getHitCount());
        assertEquals(2, statistics.getMissCount());
        assertEquals(5, statistics.getRequestCount());
        assertEquals(0.6, statistics.getHitRate(), 0.001);
    }

    @Test
    @DisplayName("Should return 1.0 hit rate when no requests")
    void testHitRateWithNoRequests() {
        // Assert
        assertEquals(1.0, statistics.getHitRate(), 0.001);
    }

    @Test
    @DisplayName("Should return 0.0 miss rate when no requests")
    void testMissRateWithNoRequests() {
        // Assert
        assertEquals(0.0, statistics.getMissRate(), 0.001);
    }

    @Test
    @DisplayName("Should calculate miss rate correctly")
    void testMissRateCalculation() {
        // Act
        statistics.recordHit();
        statistics.recordMiss();
        statistics.recordMiss();

        // Assert
        assertEquals(1, statistics.getHitCount());
        assertEquals(2, statistics.getMissCount());
        assertEquals(0.666, statistics.getMissRate(), 0.01);
    }

    @Test
    @DisplayName("Should record load success")
    void testRecordLoadSuccess() {
        // Act
        statistics.recordLoadSuccess(1_000_000); // 1ms
        statistics.recordLoadSuccess(2_000_000); // 2ms

        // Assert
        assertEquals(2, statistics.getLoadSuccessCount());
        assertEquals(2, statistics.getLoadCount());
        assertEquals(1.5, statistics.getAverageLoadTimeMillis(), 0.01);
    }

    @Test
    @DisplayName("Should record load failure")
    void testRecordLoadFailure() {
        // Act
        statistics.recordLoadFailure(500_000); // 0.5ms
        statistics.recordLoadFailure(1_500_000); // 1.5ms

        // Assert
        assertEquals(2, statistics.getLoadFailureCount());
        assertEquals(2, statistics.getLoadCount());
        assertEquals(1.0, statistics.getAverageLoadTimeMillis(), 0.01);
    }

    @Test
    @DisplayName("Should calculate average load time correctly")
    void testAverageLoadTimeCalculation() {
        // Act
        statistics.recordLoadSuccess(1_000_000);
        statistics.recordLoadSuccess(2_000_000);
        statistics.recordLoadFailure(3_000_000);

        // Assert
        assertEquals(3, statistics.getLoadCount());
        assertEquals(2.0, statistics.getAverageLoadTimeMillis(), 0.01);
    }

    @Test
    @DisplayName("Should return 0 average load time when no loads")
    void testAverageLoadTimeWithNoLoads() {
        // Assert
        assertEquals(0.0, statistics.getAverageLoadTimeMillis(), 0.001);
        assertEquals(0.0, statistics.getAverageLoadTimeNanos(), 0.001);
    }

    @Test
    @DisplayName("Should record evictions")
    void testRecordEvictions() {
        // Act
        statistics.recordEviction();
        statistics.recordEviction();
        statistics.recordEviction();

        // Assert
        assertEquals(3, statistics.getEvictionCount());
    }

    @Test
    @DisplayName("Should reset all statistics")
    void testReset() {
        // Arrange
        statistics.recordHit();
        statistics.recordMiss();
        statistics.recordLoadSuccess(1_000_000);
        statistics.recordEviction();

        // Act
        statistics.reset();

        // Assert
        assertEquals(0, statistics.getHitCount());
        assertEquals(0, statistics.getMissCount());
        assertEquals(0, statistics.getRequestCount());
        assertEquals(0, statistics.getLoadSuccessCount());
        assertEquals(0, statistics.getLoadFailureCount());
        assertEquals(0, statistics.getEvictionCount());
    }

    @Test
    @DisplayName("Should create snapshot")
    void testSnapshot() {
        // Arrange
        statistics.recordHit();
        statistics.recordMiss();
        statistics.recordLoadSuccess(1_000_000);
        statistics.recordEviction();

        // Act
        CacheStatistics.CacheStatisticsSnapshot snapshot = statistics.snapshot();

        // Assert
        assertNotNull(snapshot);
        assertEquals(1, snapshot.getHitCount());
        assertEquals(1, snapshot.getMissCount());
        assertEquals(1, snapshot.getLoadSuccessCount());
        assertEquals(1, snapshot.getEvictionCount());
        assertNotNull(snapshot.getUptime());
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
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    statistics.recordHit();
                    statistics.recordMiss();
                    statistics.recordLoadSuccess(1_000_000);
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Assert
        assertEquals(threadCount * operationsPerThread, statistics.getHitCount());
        assertEquals(threadCount * operationsPerThread, statistics.getMissCount());
        assertEquals(threadCount * operationsPerThread, statistics.getLoadSuccessCount());
    }

    @Test
    @DisplayName("Should track creation time")
    void testCreationTime() {
        // Act
        java.time.LocalDateTime creationTime = statistics.getCreationTime();

        // Assert
        assertNotNull(creationTime);
        assertFalse(creationTime.isAfter(java.time.LocalDateTime.now()));
    }

    @Test
    @DisplayName("Should calculate hit and miss rates correctly together")
    void testHitAndMissRates() {
        // Arrange
        // 7 hits, 3 misses = 70% hit rate, 30% miss rate
        for (int i = 0; i < 7; i++) {
            statistics.recordHit();
        }
        for (int i = 0; i < 3; i++) {
            statistics.recordMiss();
        }

        // Assert
        assertEquals(0.7, statistics.getHitRate(), 0.001);
        assertEquals(0.3, statistics.getMissRate(), 0.001);
        assertEquals(1.0, statistics.getHitRate() + statistics.getMissRate(), 0.001);
    }

    @Test
    @DisplayName("Should handle mixed success and failure loads")
    void testMixedLoadResults() {
        // Act
        statistics.recordLoadSuccess(1_000_000);
        statistics.recordLoadFailure(500_000);
        statistics.recordLoadSuccess(2_000_000);

        // Assert
        assertEquals(2, statistics.getLoadSuccessCount());
        assertEquals(1, statistics.getLoadFailureCount());
        assertEquals(3, statistics.getLoadCount());
        assertEquals(1.166, statistics.getAverageLoadTimeMillis(), 0.01);
    }

    @Test
    @DisplayName("Should convert nanoseconds to milliseconds correctly")
    void testNanoToMilliConversion() {
        // Act
        statistics.recordLoadSuccess(5_000_000); // 5,000,000 ns = 5 ms

        // Assert
        assertEquals(5.0, statistics.getAverageLoadTimeMillis(), 0.001);
    }

    @Test
    @DisplayName("Should have meaningful toString")
    void testToString() {
        // Arrange
        statistics.recordHit();
        statistics.recordMiss();
        statistics.recordEviction();

        // Act
        String str = statistics.toString();

        // Assert
        assertNotNull(str);
        assertTrue(str.contains("hitCount"));
        assertTrue(str.contains("missCount"));
        assertTrue(str.contains("evictionCount"));
    }
}
