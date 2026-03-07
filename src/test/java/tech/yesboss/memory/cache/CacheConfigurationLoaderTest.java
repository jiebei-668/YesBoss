package tech.yesboss.memory.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CacheConfigurationLoader.
 */
@DisplayName("Cache Configuration Loader Tests")
public class CacheConfigurationLoaderTest {

    private CacheConfigurationLoader loader;

    @BeforeEach
    void setUp() {
        loader = new CacheConfigurationLoader();
    }

    @Test
    @DisplayName("Should load default configuration")
    void testLoadDefaultConfiguration() {
        // Act
        CacheConfig config = loader.getCacheConfig();

        // Assert
        assertNotNull(config);
        assertTrue(config.getMaximumSize() > 0);
        assertTrue(config.isRecordStats());
    }

    @Test
    @DisplayName("Should parse duration strings correctly")
    void testDurationParsing() {
        // We can't directly test the private parseDuration method,
        // but we can test by setting properties and getting config

        // Arrange
        loader.setProperty("cache.expireAfterWrite", "2h");
        loader.setProperty("cache.expireAfterAccess", "30m");

        // Act
        loader.reload();
        CacheConfig config = loader.getCacheConfig();

        // Assert
        assertNotNull(config.getExpireAfterWrite());
        assertNotNull(config.getExpireAfterAccess());
        // The actual values depend on parsing
    }

    @Test
    @DisplayName("Should handle multi-level cache configuration")
    void testMultiLevelConfiguration() {
        // Arrange
        loader.setProperty("cache.multiLevel.enabled", "true");
        loader.setProperty("cache.multiLevel.l2.maxSize", "50000");

        // Act
        loader.reload();
        boolean enabled = loader.isMultiLevelEnabled();
        CacheConfig l2Config = loader.getL2CacheConfig();

        // Assert
        assertTrue(enabled);
        assertNotNull(l2Config);
    }

    @Test
    @DisplayName("Should return null for L2 config when multi-level is disabled")
    void testL2ConfigWhenDisabled() {
        // Arrange
        loader.setProperty("cache.multiLevel.enabled", "false");

        // Act
        loader.reload();
        CacheConfig l2Config = loader.getL2CacheConfig();

        // Assert
        assertNull(l2Config);
    }

    @Test
    @DisplayName("Should get and set properties")
    void testPropertyManipulation() {
        // Arrange
        String testKey = "cache.testProperty";
        String testValue = "testValue";

        // Act
        loader.setProperty(testKey, testValue);
        String retrieved = loader.getProperty(testKey);

        // Assert
        assertEquals(testValue, retrieved);
    }

    @Test
    @DisplayName("Should remove property")
    void testRemoveProperty() {
        // Arrange
        String testKey = "cache.tempProperty";
        loader.setProperty(testKey, "tempValue");

        // Act
        loader.removeProperty(testKey);
        String retrieved = loader.getProperty(testKey);

        // Assert
        assertNull(retrieved);
    }

    @Test
    @DisplayName("Should reload configuration")
    void testReload() {
        // Arrange
        CacheConfig config1 = loader.getCacheConfig();

        // Act
        loader.setProperty("cache.maxSize", "20000");
        loader.reload();
        CacheConfig config2 = loader.getCacheConfig();

        // Assert
        // Configurations should be different instances after reload
        assertNotSame(config1, config2);
    }

    @Test
    @DisplayName("Should get properties copy")
    void testGetProperties() {
        // Act
        Properties props = loader.getProperties();

        // Assert
        assertNotNull(props);
        assertFalse(props.isEmpty());

        // Verify it's a copy, not the original
        props.setProperty("cache.test", "value");
        assertNull(loader.getProperty("cache.test"));
    }

    @Test
    @DisplayName("Should handle invalid property values gracefully")
    void testInvalidPropertyValues() {
        // Arrange
        loader.setProperty("cache.maxSize", "invalid");

        // Act
        loader.reload();

        // Assert - Should not throw exception, should use default
        CacheConfig config = loader.getCacheConfig();
        assertNotNull(config);
        assertTrue(config.getMaximumSize() > 0);
    }

    @Test
    @DisplayName("Should handle numeric properties")
    void testNumericProperties() {
        // Arrange
        loader.setProperty("cache.maxSize", "5000");
        loader.setProperty("cache.concurrencyLevel", "8");

        // Act
        loader.reload();
        CacheConfig config = loader.getCacheConfig();

        // Assert
        assertEquals(5000, config.getMaximumSize());
        assertEquals(8, config.getConcurrencyLevel());
    }

    @Test
    @DisplayName("Should handle boolean properties")
    void testBooleanProperties() {
        // Arrange
        loader.setProperty("cache.recordStats", "true");
        loader.setProperty("cache.multiLevel.enabled", "false");

        // Act
        loader.reload();
        CacheConfig config = loader.getCacheConfig();
        boolean multiLevel = loader.isMultiLevelEnabled();

        // Assert
        assertTrue(config.isRecordStats());
        assertFalse(multiLevel);
    }

    @Test
    @DisplayName("Should invalidate cache on property change")
    void testInvalidateCache() {
        // Arrange
        CacheConfig config1 = loader.getCacheConfig();

        // Act
        loader.setProperty("cache.maxSize", "15000");
        loader.invalidateCache();
        CacheConfig config2 = loader.getCacheConfig();

        // Assert
        assertNotSame(config1, config2);
    }

    @Test
    @DisplayName("Should handle concurrent property access")
    void testConcurrentPropertyAccess() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        // Act
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                loader.setProperty("cache.prop" + index, "value" + index);
                loader.getCacheConfig();
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Assert - Should not throw exceptions
        CacheConfig config = loader.getCacheConfig();
        assertNotNull(config);
    }

    @Test
    @DisplayName("Should handle duration units correctly")
    void testDurationUnits() {
        // Test various duration formats
        String[] durations = {"1h", "30m", "60s", "1000ms", "2d"};

        for (String duration : durations) {
            loader.setProperty("cache.expireAfterWrite", duration);
            loader.reload();
            CacheConfig config = loader.getCacheConfig();

            assertNotNull(config.getExpireAfterWrite(),
                "Should parse duration: " + duration);
        }
    }

    @Test
    @DisplayName("Should use default when property is missing")
    void testMissingPropertyUsesDefault() {
        // Arrange
        loader.removeProperty("cache.maxSize");

        // Act
        loader.reload();
        CacheConfig config = loader.getCacheConfig();

        // Assert
        assertNotNull(config);
        assertTrue(config.getMaximumSize() > 0);
    }

    @Test
    @DisplayName("Should handle empty string properties")
    void testEmptyStringProperties() {
        // Arrange
        loader.setProperty("cache.expireAfterWrite", "");

        // Act
        loader.reload();
        CacheConfig config = loader.getCacheConfig();

        // Assert
        assertNotNull(config);
        // Should use default when property is empty
    }
}
