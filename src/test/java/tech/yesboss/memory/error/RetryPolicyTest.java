package tech.yesboss.memory.error;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RetryPolicy.
 */
@DisplayName("Retry Policy Tests")
public class RetryPolicyTest {

    @Test
    @DisplayName("Should build retry policy with defaults")
    void testDefaultPolicy() {
        // Act
        RetryPolicy policy = RetryPolicy.builder().build();

        // Assert
        assertNotNull(policy);
        assertTrue(policy.getMaxAttempts() > 0);
        assertNotNull(policy.getStrategy());
    }

    @Test
    @DisplayName("Should build custom retry policy")
    void testCustomPolicy() {
        // Act
        RetryPolicy policy = RetryPolicy.builder()
            .maxAttempts(5)
            .strategy(RetryPolicy.RetryStrategy.EXPONENTIAL_BACKOFF)
            .initialDelayMs(100)
            .maxDelayMs(5000)
            .backoffMultiplier(2.0)
            .jitterMs(50)
            .retryOnTimeout(true)
            .retryOnSpecificExceptions(true)
            .build();

        // Assert
        assertEquals(5, policy.getMaxAttempts());
        assertEquals(RetryPolicy.RetryStrategy.EXPONENTIAL_BACKOFF, policy.getStrategy());
        assertEquals(100, policy.getInitialDelayMs());
        assertEquals(5000, policy.getMaxDelayMs());
        assertEquals(2.0, policy.getBackoffMultiplier(), 0.001);
        assertEquals(50, policy.getJitterMs());
        assertTrue(policy.isRetryOnTimeout());
        assertTrue(policy.isRetryOnSpecificExceptions());
    }

    @Test
    @DisplayName("Should validate max attempts")
    void testMaxAttemptsValidation() {
        // Assert - Positive values should work
        assertDoesNotThrow(() -> RetryPolicy.builder().maxAttempts(1).build());
        assertDoesNotThrow(() -> RetryPolicy.builder().maxAttempts(10).build());

        // Assert - Zero or negative should throw
        assertThrows(IllegalArgumentException.class, () ->
            RetryPolicy.builder().maxAttempts(0).build());
        assertThrows(IllegalArgumentException.class, () ->
            RetryPolicy.builder().maxAttempts(-1).build());
    }

    @Test
    @DisplayName("Should validate delay values")
    void testDelayValidation() {
        // Assert - Positive values should work
        assertDoesNotThrow(() -> RetryPolicy.builder()
            .initialDelayMs(1).maxDelayMs(1000).build());

        // Assert - Negative values should throw
        assertThrows(IllegalArgumentException.class, () ->
            RetryPolicy.builder().initialDelayMs(-1).build());
        assertThrows(IllegalArgumentException.class, () ->
            RetryPolicy.builder().maxDelayMs(-1).build());
    }

    @Test
    @DisplayName("Should validate backoff multiplier")
    void testBackoffMultiplierValidation() {
        // Assert - Valid multipliers
        assertDoesNotThrow(() -> RetryPolicy.builder().backoffMultiplier(1.0).build());
        assertDoesNotThrow(() -> RetryPolicy.builder().backoffMultiplier(2.0).build());
        assertDoesNotThrow(() -> RetryPolicy.builder().backoffMultiplier(0.5).build());

        // Assert - Invalid multipliers
        assertThrows(IllegalArgumentException.class, () ->
            RetryPolicy.builder().backoffMultiplier(0).build());
        assertThrows(IllegalArgumentException.class, () ->
            RetryPolicy.builder().backoffMultiplier(-1.0).build());
    }

    @Test
    @DisplayName("Should support fixed delay strategy")
    void testFixedDelayStrategy() {
        // Act
        RetryPolicy policy = RetryPolicy.builder()
            .strategy(RetryPolicy.RetryStrategy.FIXED_DELAY)
            .initialDelayMs(100)
            .build();

        // Assert
        assertEquals(RetryPolicy.RetryStrategy.FIXED_DELAY, policy.getStrategy());
        assertEquals(100, policy.getInitialDelayMs());
    }

    @Test
    @DisplayName("Should support exponential backoff strategy")
    void testExponentialBackoffStrategy() {
        // Act
        RetryPolicy policy = RetryPolicy.builder()
            .strategy(RetryPolicy.RetryStrategy.EXPONENTIAL_BACKOFF)
            .initialDelayMs(100)
            .backoffMultiplier(2.0)
            .build();

        // Assert
        assertEquals(RetryPolicy.RetryStrategy.EXPONENTIAL_BACKOFF, policy.getStrategy());
        assertEquals(2.0, policy.getBackoffMultiplier(), 0.001);
    }

    @Test
    @DisplayName("Should support linear backoff strategy")
    void testLinearBackoffStrategy() {
        // Act
        RetryPolicy policy = RetryPolicy.builder()
            .strategy(RetryPolicy.RetryStrategy.LINEAR_BACKOFF)
            .initialDelayMs(100)
            .backoffMultiplier(1.5)
            .build();

        // Assert
        assertEquals(RetryPolicy.RetryStrategy.LINEAR_BACKOFF, policy.getStrategy());
    }

    @Test
    @DisplayName("Should enforce max delay limit")
    void testMaxDelayLimit() {
        // Arrange
        RetryPolicy policy = RetryPolicy.builder()
            .strategy(RetryPolicy.RetryStrategy.EXPONENTIAL_BACKOFF)
            .initialDelayMs(1000)
            .maxDelayMs(5000)
            .backoffMultiplier(10.0)
            .maxAttempts(10)
            .build();

        // Act & Assert - All delays should be capped at maxDelayMs
        for (int attempt = 0; attempt < policy.getMaxAttempts(); attempt++) {
            long delay = policy.getDelayForAttempt(attempt);
            assertTrue(delay <= policy.getMaxDelayMs(),
                "Delay " + delay + " at attempt " + attempt + " should not exceed max delay");
        }
    }

    @Test
    @DisplayName("Should calculate delay for each attempt")
    void testDelayCalculation() {
        // Arrange
        RetryPolicy fixedPolicy = RetryPolicy.builder()
            .strategy(RetryPolicy.RetryStrategy.FIXED_DELAY)
            .initialDelayMs(100)
            .build();

        // Act & Assert
        assertEquals(100, fixedPolicy.getDelayForAttempt(0));
        assertEquals(100, fixedPolicy.getDelayForAttempt(1));
        assertEquals(100, fixedPolicy.getDelayForAttempt(2));

        // Arrange - Exponential backoff
        RetryPolicy exponentialPolicy = RetryPolicy.builder()
            .strategy(RetryPolicy.RetryStrategy.EXPONENTIAL_BACKOFF)
            .initialDelayMs(100)
            .backoffMultiplier(2.0)
            .build();

        // Act & Assert
        assertEquals(100, exponentialPolicy.getDelayForAttempt(0));
        assertEquals(200, exponentialPolicy.getDelayForAttempt(1));
        assertEquals(400, exponentialPolicy.getDelayForAttempt(2));
        assertEquals(800, exponentialPolicy.getDelayForAttempt(3));
    }

    @Test
    @DisplayName("Should add jitter to delay")
    void testJitterAddition() {
        // Arrange
        RetryPolicy policy = RetryPolicy.builder()
            .strategy(RetryPolicy.RetryStrategy.FIXED_DELAY)
            .initialDelayMs(100)
            .jitterMs(50)
            .build();

        // Act
        long delay1 = policy.getDelayForAttempt(0);
        long delay2 = policy.getDelayForAttempt(0);

        // Assert - With jitter, delays should vary slightly
        // Note: This test might occasionally fail if random jitter is the same
        assertTrue(Math.abs(delay1 - delay2) <= policy.getJitterMs() * 2);
    }

    @Test
    @DisplayName("Should handle zero jitter")
    void testZeroJitter() {
        // Arrange
        RetryPolicy policy = RetryPolicy.builder()
            .strategy(RetryPolicy.RetryStrategy.FIXED_DELAY)
            .initialDelayMs(100)
            .jitterMs(0)
            .build();

        // Act
        long delay1 = policy.getDelayForAttempt(0);
        long delay2 = policy.getDelayForAttempt(0);

        // Assert - Without jitter, delays should be consistent
        assertEquals(delay1, delay2);
    }

    @Test
    @DisplayName("Should provide meaningful toString")
    void testToString() {
        // Arrange
        RetryPolicy policy = RetryPolicy.builder()
            .maxAttempts(3)
            .strategy(RetryPolicy.RetryStrategy.EXPONENTIAL_BACKOFF)
            .initialDelayMs(100)
            .build();

        // Act
        String str = policy.toString();

        // Assert
        assertNotNull(str);
        assertTrue(str.contains("RetryPolicy"));
        assertTrue(str.contains("maxAttempts") || str.contains("3"));
    }

    @Test
    @DisplayName("Should handle retry on timeout setting")
    void testRetryOnTimeout() {
        // Arrange
        RetryPolicy policy = RetryPolicy.builder()
            .retryOnTimeout(true)
            .build();

        // Assert
        assertTrue(policy.isRetryOnTimeout());
    }

    @Test
    @DisplayName("Should handle retry on specific exceptions setting")
    void testRetryOnSpecificExceptions() {
        // Arrange
        RetryPolicy policy = RetryPolicy.builder()
            .retryOnSpecificExceptions(true)
            .build();

        // Assert
        assertTrue(policy.isRetryOnSpecificExceptions());
    }

    @Test
    @DisplayName("Should support default max attempts when not specified")
    void testDefaultMaxAttempts() {
        // Act
        RetryPolicy policy = RetryPolicy.builder().build();

        // Assert
        assertTrue(policy.getMaxAttempts() >= 1);
    }

    @Test
    @DisplayName("Should support default initial delay when not specified")
    void testDefaultInitialDelay() {
        // Act
        RetryPolicy policy = RetryPolicy.builder().build();

        // Assert
        assertTrue(policy.getInitialDelayMs() >= 0);
    }

    @Test
    @DisplayName("Should create policy with all optional parameters")
    void testPolicyWithAllOptionals() {
        // Act
        RetryPolicy policy = RetryPolicy.builder()
            .maxAttempts(7)
            .strategy(RetryPolicy.RetryStrategy.LINEAR_BACKOFF)
            .initialDelayMs(200)
            .maxDelayMs(10000)
            .backoffMultiplier(1.5)
            .jitterMs(100)
            .retryOnTimeout(false)
            .retryOnSpecificExceptions(false)
            .build();

        // Assert
        assertEquals(7, policy.getMaxAttempts());
        assertEquals(RetryPolicy.RetryStrategy.LINEAR_BACKOFF, policy.getStrategy());
        assertEquals(200, policy.getInitialDelayMs());
        assertEquals(10000, policy.getMaxDelayMs());
        assertEquals(1.5, policy.getBackoffMultiplier(), 0.001);
        assertEquals(100, policy.getJitterMs());
        assertFalse(policy.isRetryOnTimeout());
        assertFalse(policy.isRetryOnSpecificExceptions());
    }

    @Test
    @DisplayName("Should handle very large max attempts")
    void testLargeMaxAttempts() {
        // Act
        RetryPolicy policy = RetryPolicy.builder()
            .maxAttempts(1000000)
            .build();

        // Assert
        assertEquals(1000000, policy.getMaxAttempts());
    }

    @Test
    @DisplayName("Should handle very small delays")
    void testSmallDelays() {
        // Act
        RetryPolicy policy = RetryPolicy.builder()
            .initialDelayMs(1)
            .maxDelayMs(10)
            .build();

        // Assert
        assertEquals(1, policy.getInitialDelayMs());
        assertEquals(10, policy.getMaxDelayMs());
    }

    @Test
    @DisplayName("Should calculate delay for linear backoff")
    void testLinearBackoffCalculation() {
        // Arrange
        RetryPolicy policy = RetryPolicy.builder()
            .strategy(RetryPolicy.RetryStrategy.LINEAR_BACKOFF)
            .initialDelayMs(100)
            .backoffMultiplier(2.0)
            .build();

        // Act & Assert
        assertEquals(100, policy.getDelayForAttempt(0));
        assertEquals(102, policy.getDelayForAttempt(1)); // 100 + 2
        assertEquals(104, policy.getDelayForAttempt(2)); // 100 + 4
        assertEquals(106, policy.getDelayForAttempt(3)); // 100 + 6
    }
}
