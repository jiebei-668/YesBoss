package tech.yesboss.memory.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CacheConfig.
 */
@DisplayName("Cache Configuration Tests")
public class CacheConfigTest {

    @Test
    @DisplayName("Should build default configuration")
    void testDefaultConfiguration() {
        // Act
        CacheConfig config = CacheConfig.defaults();

        // Assert
        assertNotNull(config);
        assertEquals(10000, config.getMaximumSize());
        assertTrue(config.isRecordStats());
        assertNotNull(config.getExpireAfterWrite());
    }

    @Test
    @DisplayName("Should build configuration with builder")
    void testBuilderConfiguration() {
        // Act
        CacheConfig config = CacheConfig.builder()
            .maximumSize(5000)
            .expireAfterWrite(Duration.ofHours(2))
            .expireAfterAccess(Duration.ofMinutes(30))
            .recordStats(true)
            .concurrencyLevel(8)
            .build();

        // Assert
        assertEquals(5000, config.getMaximumSize());
        assertEquals(Duration.ofHours(2), config.getExpireAfterWrite());
        assertEquals(Duration.ofMinutes(30), config.getExpireAfterAccess());
        assertTrue(config.isRecordStats());
        assertEquals(8, config.getConcurrencyLevel());
    }

    @Test
    @DisplayName("Should throw exception for invalid maximum size")
    void testInvalidMaximumSize() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            CacheConfig.builder().maximumSize(0)
        );

        assertThrows(IllegalArgumentException.class, () ->
            CacheConfig.builder().maximumSize(-100)
        );
    }

    @Test
    @DisplayName("Should throw exception for invalid duration")
    void testInvalidDuration() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            CacheConfig.builder().expireAfterWrite(Duration.ZERO)
        );

        assertThrows(IllegalArgumentException.class, () ->
            CacheConfig.builder().expireAfterWrite(Duration.ofMillis(-1))
        );

        assertThrows(IllegalArgumentException.class, () ->
            CacheConfig.builder().expireAfterAccess(null)
        );
    }

    @Test
    @DisplayName("Should throw exception for invalid concurrency level")
    void testInvalidConcurrencyLevel() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            CacheConfig.builder().concurrencyLevel(0)
        );

        assertThrows(IllegalArgumentException.class, () ->
            CacheConfig.builder().concurrencyLevel(-4)
        );
    }

    @Test
    @DisplayName("Should support removal listener")
    void testRemovalListener() {
        // Arrange
        CacheConfig.RemovalListener<String, String> listener = (key, value, cause) -> {
            // Do nothing
        };

        // Act
        CacheConfig config = CacheConfig.builder()
            .removalListener(listener)
            .build();

        // Assert
        assertNotNull(config.getRemovalListener());
        assertSame(listener, config.getRemovalListener());
    }

    @Test
    @DisplayName("Should build configuration without expiration")
    void testConfigurationWithoutExpiration() {
        // Act
        CacheConfig config = CacheConfig.builder()
            .maximumSize(1000)
            .recordStats(false)
            .build();

        // Assert
        assertEquals(1000, config.getMaximumSize());
        assertFalse(config.isRecordStats());
        assertNull(config.getExpireAfterWrite());
        assertNull(config.getExpireAfterAccess());
        assertNull(config.getRefreshAfterWrite());
    }
}
