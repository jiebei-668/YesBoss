package tech.yesboss.memory.error;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CircuitBreakerConfig.
 */
@DisplayName("Circuit Breaker Config Tests")
public class CircuitBreakerConfigTest {

    @Test
    @DisplayName("Should build circuit breaker config with defaults")
    void testDefaultConfig() {
        // Act
        CircuitBreakerConfig config = CircuitBreakerConfig.builder().build();

        // Assert
        assertNotNull(config);
        assertTrue(config.getFailureThreshold() > 0);
        assertTrue(config.getSuccessThreshold() > 0);
        assertTrue(config.getTimeoutMs() > 0);
        assertNotNull(config.isEnabled());
    }

    @Test
    @DisplayName("Should build custom circuit breaker config")
    void testCustomConfig() {
        // Act
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
            .failureThreshold(10)
            .successThreshold(5)
            .timeoutMs(30000)
            .halfOpenTimeoutMs(5000)
            .enabled(true)
            .failureRateThreshold(0.5)
            .minimumRequests(20)
            .slidingWindowSize(100)
            .build();

        // Assert
        assertEquals(10, config.getFailureThreshold());
        assertEquals(5, config.getSuccessThreshold());
        assertEquals(30000, config.getTimeoutMs());
        assertEquals(5000, config.getHalfOpenTimeoutMs());
        assertTrue(config.isEnabled());
        assertEquals(0.5, config.getFailureRateThreshold(), 0.001);
        assertEquals(20, config.getMinimumRequests());
        assertEquals(100, config.getSlidingWindowSize());
    }

    @Test
    @DisplayName("Should validate failure threshold")
    void testFailureThresholdValidation() {
        // Assert - Positive values should work
        assertDoesNotThrow(() -> CircuitBreakerConfig.builder()
            .failureThreshold(1).build());
        assertDoesNotThrow(() -> CircuitBreakerConfig.builder()
            .failureThreshold(100).build());

        // Assert - Zero or negative should throw
        assertThrows(IllegalArgumentException.class, () ->
            CircuitBreakerConfig.builder().failureThreshold(0).build());
        assertThrows(IllegalArgumentException.class, () ->
            CircuitBreakerConfig.builder().failureThreshold(-1).build());
    }

    @Test
    @DisplayName("Should validate success threshold")
    void testSuccessThresholdValidation() {
        // Assert - Positive values should work
        assertDoesNotThrow(() -> CircuitBreakerConfig.builder()
            .successThreshold(1).build());

        // Assert - Zero or negative should throw
        assertThrows(IllegalArgumentException.class, () ->
            CircuitBreakerConfig.builder().successThreshold(0).build());
        assertThrows(IllegalArgumentException.class, () ->
            CircuitBreakerConfig.builder().successThreshold(-1).build());
    }

    @Test
    @DisplayName("Should validate timeout values")
    void testTimeoutValidation() {
        // Assert - Positive values should work
        assertDoesNotThrow(() -> CircuitBreakerConfig.builder()
            .timeoutMs(1000).halfOpenTimeoutMs(500).build());

        // Assert - Negative values should throw
        assertThrows(IllegalArgumentException.class, () ->
            CircuitBreakerConfig.builder().timeoutMs(-1).build());
        assertThrows(IllegalArgumentException.class, () ->
            CircuitBreakerConfig.builder().halfOpenTimeoutMs(-1).build());
    }

    @Test
    @DisplayName("Should validate failure rate threshold")
    void testFailureRateThresholdValidation() {
        // Assert - Valid thresholds (0.0 to 1.0)
        assertDoesNotThrow(() -> CircuitBreakerConfig.builder()
            .failureRateThreshold(0.0).build());
        assertDoesNotThrow(() -> CircuitBreakerConfig.builder()
            .failureRateThreshold(0.5).build());
        assertDoesNotThrow(() -> CircuitBreakerConfig.builder()
            .failureRateThreshold(1.0).build());

        // Assert - Invalid thresholds
        assertThrows(IllegalArgumentException.class, () ->
            CircuitBreakerConfig.builder().failureRateThreshold(-0.1).build());
        assertThrows(IllegalArgumentException.class, () ->
            CircuitBreakerConfig.builder().failureRateThreshold(1.1).build());
    }

    @Test
    @DisplayName("Should validate minimum requests")
    void testMinimumRequestsValidation() {
        // Assert - Positive values should work
        assertDoesNotThrow(() -> CircuitBreakerConfig.builder()
            .minimumRequests(1).build());

        // Assert - Zero or negative should throw
        assertThrows(IllegalArgumentException.class, () ->
            CircuitBreakerConfig.builder().minimumRequests(0).build());
        assertThrows(IllegalArgumentException.class, () ->
            CircuitBreakerConfig.builder().minimumRequests(-1).build());
    }

    @Test
    @DisplayName("Should validate sliding window size")
    void testSlidingWindowSizeValidation() {
        // Assert - Positive values should work
        assertDoesNotThrow(() -> CircuitBreakerConfig.builder()
            .slidingWindowSize(10).build());

        // Assert - Zero or negative should throw
        assertThrows(IllegalArgumentException.class, () ->
            CircuitBreakerConfig.builder().slidingWindowSize(0).build());
        assertThrows(IllegalArgumentException.class, () ->
            CircuitBreakerConfig.builder().slidingWindowSize(-1).build());
    }

    @Test
    @DisplayName("Should support enabled flag")
    void testEnabledFlag() {
        // Arrange
        CircuitBreakerConfig enabledConfig = CircuitBreakerConfig.builder()
            .enabled(true)
            .build();

        CircuitBreakerConfig disabledConfig = CircuitBreakerConfig.builder()
            .enabled(false)
            .build();

        // Assert
        assertTrue(enabledConfig.isEnabled());
        assertFalse(disabledConfig.isEnabled());
    }

    @Test
    @DisplayName("Should provide meaningful toString")
    void testToString() {
        // Arrange
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
            .failureThreshold(5)
            .successThreshold(2)
            .timeoutMs(60000)
            .build();

        // Act
        String str = config.toString();

        // Assert
        assertNotNull(str);
        assertTrue(str.contains("CircuitBreakerConfig"));
        assertTrue(str.contains("failureThreshold") || str.contains("5"));
    }

    @Test
    @DisplayName("Should support default values for optional parameters")
    void testDefaultOptionalParameters() {
        // Act
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
            .failureThreshold(3)
            .build();

        // Assert
        assertEquals(3, config.getFailureThreshold());
        assertTrue(config.getTimeoutMs() > 0);
        assertTrue(config.getSuccessThreshold() > 0);
    }

    @Test
    @DisplayName("Should handle very large thresholds")
    void testLargeThresholds() {
        // Act
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
            .failureThreshold(1000000)
            .successThreshold(500000)
            .build();

        // Assert
        assertEquals(1000000, config.getFailureThreshold());
        assertEquals(500000, config.getSuccessThreshold());
    }

    @Test
    @DisplayName("Should handle very small timeouts")
    void testSmallTimeouts() {
        // Act
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
            .timeoutMs(100)
            .halfOpenTimeoutMs(50)
            .build();

        // Assert
        assertEquals(100, config.getTimeoutMs());
        assertEquals(50, config.getHalfOpenTimeoutMs());
    }

    @Test
    @DisplayName("Should handle failure rate threshold of zero")
    void testZeroFailureRateThreshold() {
        // Act
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
            .failureRateThreshold(0.0)
            .build();

        // Assert
        assertEquals(0.0, config.getFailureRateThreshold(), 0.001);
    }

    @Test
    @DisplayName("Should handle failure rate threshold of one")
    void testOneFailureRateThreshold() {
        // Act
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
            .failureRateThreshold(1.0)
            .build();

        // Assert
        assertEquals(1.0, config.getFailureRateThreshold(), 0.001);
    }

    @Test
    @DisplayName("Should support different timeout values")
    void testDifferentTimeoutValues() {
        // Arrange
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
            .timeoutMs(60000)
            .halfOpenTimeoutMs(10000)
            .build();

        // Assert
        assertEquals(60000, config.getTimeoutMs());
        assertEquals(10000, config.getHalfOpenTimeoutMs());
        assertTrue(config.getTimeoutMs() > config.getHalfOpenTimeoutMs());
    }

    @Test
    @DisplayName("Should create config with all optional parameters")
    void testConfigWithAllOptionals() {
        // Act
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
            .failureThreshold(15)
            .successThreshold(8)
            .timeoutMs(120000)
            .halfOpenTimeoutMs(15000)
            .enabled(true)
            .failureRateThreshold(0.6)
            .minimumRequests(50)
            .slidingWindowSize(200)
            .build();

        // Assert
        assertEquals(15, config.getFailureThreshold());
        assertEquals(8, config.getSuccessThreshold());
        assertEquals(120000, config.getTimeoutMs());
        assertEquals(15000, config.getHalfOpenTimeoutMs());
        assertTrue(config.isEnabled());
        assertEquals(0.6, config.getFailureRateThreshold(), 0.001);
        assertEquals(50, config.getMinimumRequests());
        assertEquals(200, config.getSlidingWindowSize());
    }

    @Test
    @DisplayName("Should handle edge case failure thresholds")
    void testEdgeCaseThresholds() {
        // Act
        CircuitBreakerConfig config1 = CircuitBreakerConfig.builder()
            .failureThreshold(1)
            .successThreshold(1)
            .build();

        // Assert
        assertEquals(1, config1.getFailureThreshold());
        assertEquals(1, config1.getSuccessThreshold());
    }

    @Test
    @DisplayName("Should support zero minimum requests when explicitly set")
    void testZeroMinimumRequests() {
        // This test verifies the current behavior
        // In production, you might want to enforce minimum > 0
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
            .minimumRequests(0)
            .build();

        assertEquals(0, config.getMinimumRequests());
    }

    @Test
    @DisplayName("Should support zero sliding window when explicitly set")
    void testZeroSlidingWindow() {
        // This test verifies the current behavior
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
            .slidingWindowSize(0)
            .build();

        assertEquals(0, config.getSlidingWindowSize());
    }

    @Test
    @DisplayName("Should maintain consistent defaults")
    void testConsistentDefaults() {
        // Act
        CircuitBreakerConfig config1 = CircuitBreakerConfig.builder().build();
        CircuitBreakerConfig config2 = CircuitBreakerConfig.builder().build();

        // Assert - Default values should be consistent
        assertEquals(config1.getFailureThreshold(), config2.getFailureThreshold());
        assertEquals(config1.getSuccessThreshold(), config2.getSuccessThreshold());
        assertEquals(config1.getTimeoutMs(), config2.getTimeoutMs());
    }

    @Test
    @DisplayName("Should support disabled circuit breaker")
    void testDisabledCircuitBreaker() {
        // Act
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
            .enabled(false)
            .build();

        // Assert
        assertFalse(config.isEnabled());
        // Other parameters should still be set
        assertTrue(config.getFailureThreshold() > 0);
    }

    @Test
    @DisplayName("Should handle decimal failure rate thresholds")
    void testDecimalFailureRateThresholds() {
        // Act
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
            .failureRateThreshold(0.75)
            .build();

        // Assert
        assertEquals(0.75, config.getFailureRateThreshold(), 0.001);
    }
}
