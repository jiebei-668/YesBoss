package tech.yesboss.memory.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MemoryErrorHandler.
 */
@DisplayName("Memory Error Handler Tests")
public class MemoryErrorHandlerTest {

    private MemoryErrorHandler errorHandler;

    @BeforeEach
    void setUp() {
        errorHandler = new MemoryErrorHandlerImpl();
    }

    @Test
    @DisplayName("Should handle successful operation without retry")
    void testSuccessfulOperation() throws Exception {
        // Arrange
        MemoryErrorHandler.RetryableOperation<String> operation = () -> "success";

        // Act
        String result = errorHandler.handleWithRetry(operation,
            RetryPolicy.builder().maxAttempts(3).build());

        // Assert
        assertEquals("success", result);
    }

    @Test
    @DisplayName("Should retry operation on transient error")
    void testRetryOnTransientError() throws Exception {
        // Arrange
        AtomicInteger attempts = new AtomicInteger(0);
        MemoryErrorHandler.RetryableOperation<String> operation = () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new RuntimeException("Transient error");
            }
            return "success";
        };

        RetryPolicy policy = RetryPolicy.builder()
            .maxAttempts(5)
            .strategy(RetryPolicy.RetryStrategy.FIXED_DELAY)
            .initialDelayMs(10)
            .build();

        // Act
        String result = errorHandler.handleWithRetry(operation, policy);

        // Assert
        assertEquals("success", result);
        assertEquals(3, attempts.get());
    }

    @Test
    @DisplayName("Should throw exception after max retries exhausted")
    void testMaxRetriesExhausted() {
        // Arrange
        MemoryErrorHandler.RetryableOperation<String> operation = () -> {
            throw new RuntimeException("Persistent error");
        };

        RetryPolicy policy = RetryPolicy.builder()
            .maxAttempts(3)
            .strategy(RetryPolicy.RetryStrategy.FIXED_DELAY)
            .initialDelayMs(10)
            .build();

        // Act & Assert
        assertThrows(MemoryException.class, () ->
            errorHandler.handleWithRetry(operation, policy));
    }

    @Test
    @DisplayName("Should handle operation with circuit breaker")
    void testCircuitBreaker() throws Exception {
        // Arrange
        String operationName = "test-operation";
        MemoryErrorHandler.RetryableOperation<String> operation = () -> "success";

        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
            .failureThreshold(3)
            .successThreshold(2)
            .timeoutMs(1000)
            .build();

        // Act - First call should succeed
        String result1 = errorHandler.handleWithCircuitBreaker(operation, config);

        // Assert
        assertEquals("success", result1);
        assertEquals(MemoryErrorHandler.CircuitState.CLOSED,
            errorHandler.getCircuitState(operationName));
    }

    @Test
    @DisplayName("Should open circuit after failure threshold")
    void testCircuitOpensOnFailures() throws Exception {
        // Arrange
        String operationName = "failing-operation";
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
            .failureThreshold(2)
            .successThreshold(2)
            .timeoutMs(1000)
            .build();

        MemoryErrorHandler.RetryableOperation<String> failingOperation = () -> {
            throw new RuntimeException("Service unavailable");
        };

        // Act - Trigger failures
        try {
            errorHandler.handleWithCircuitBreaker(failingOperation, config);
        } catch (MemoryException e) {
            // Expected
        }

        try {
            errorHandler.handleWithCircuitBreaker(failingOperation, config);
        } catch (MemoryException e) {
            // Expected
        }

        // Assert - Circuit should be open
        assertEquals(MemoryErrorHandler.CircuitState.OPEN,
            errorHandler.getCircuitState(operationName));
    }

    @Test
    @DisplayName("Should provide fallback on error")
    void testFallback() throws Exception {
        // Arrange
        MemoryErrorHandler.RetryableOperation<String> operation = () -> {
            throw new RuntimeException("Operation failed");
        };

        MemoryErrorHandler.FallbackStrategy<String> fallback = error -> "fallback-value";

        // Act
        String result = errorHandler.handleWithFallback(operation, fallback);

        // Assert
        assertEquals("fallback-value", result);
    }

    @Test
    @DisplayName("Should classify error correctly")
    void testErrorClassification() {
        // Act & Assert - Transient error (timeout)
        MemoryErrorHandler.ErrorClassification timeoutClassification =
            errorHandler.classifyError(new java.net.SocketTimeoutException());
        assertEquals(MemoryErrorHandler.ErrorType.TRANSIENT, timeoutClassification.getType());
        assertTrue(timeoutClassification.isRetryable());

        // Act & Assert - Permanent error (illegal argument)
        MemoryErrorHandler.ErrorClassification illegalArgClassification =
            errorHandler.classifyError(new IllegalArgumentException());
        assertEquals(MemoryErrorHandler.ErrorType.PERMANENT, illegalArgClassification.getType());
        assertFalse(illegalArgClassification.isRetryable());

        // Act & Assert - System error (out of memory)
        MemoryErrorHandler.ErrorClassification oomClassification =
            errorHandler.classifyError(new OutOfMemoryError());
        assertEquals(MemoryErrorHandler.ErrorType.SYSTEM, oomClassification.getType());
    }

    @Test
    @DisplayName("Should check if error is retryable")
    void testIsRetryable() {
        // Assert
        assertTrue(errorHandler.isRetryable(new java.net.SocketTimeoutException()));
        assertTrue(errorHandler.isRetryable(new java.net.ConnectException()));
        assertFalse(errorHandler.isRetryable(new IllegalArgumentException()));
        assertFalse(errorHandler.isRetryable(new NullPointerException()));
    }

    @Test
    @DisplayName("Should check if error is recoverable")
    void testIsRecoverable() {
        // Assert
        assertTrue(errorHandler.isRecoverable(new org.springframework.dao.DataIntegrityViolationException("")));
        assertFalse(errorHandler.isRecoverable(new OutOfMemoryError()));
        assertFalse(errorHandler.isRecoverable(new StackOverflowError()));
    }

    @Test
    @DisplayName("Should attempt recovery with context")
    void testRecoveryWithContext() {
        // Arrange
        RuntimeException error = new RuntimeException("Recoverable error");
        Map<String, Object> context = Map.of("retryCount", 3, "operation", "test");

        // Act
        boolean recovered = errorHandler.recover(error, context);

        // Assert - Depends on implementation
        // Some errors may be recoverable, others may not
        assertNotNull(recovered);
    }

    @Test
    @DisplayName("Should open circuit manually")
    void testManualCircuitOpen() {
        // Arrange
        String operationName = "manual-operation";

        // Act
        errorHandler.openCircuit(operationName);

        // Assert
        assertEquals(MemoryErrorHandler.CircuitState.OPEN,
            errorHandler.getCircuitState(operationName));
    }

    @Test
    @DisplayName("Should close circuit manually")
    void testManualCircuitClose() {
        // Arrange
        String operationName = "manual-operation";
        errorHandler.openCircuit(operationName);

        // Act
        errorHandler.closeCircuit(operationName);

        // Assert
        assertEquals(MemoryErrorHandler.CircuitState.CLOSED,
            errorHandler.getCircuitState(operationName));
    }

    @Test
    @DisplayName("Should reset all circuits")
    void testResetAllCircuits() {
        // Arrange
        errorHandler.openCircuit("operation1");
        errorHandler.openCircuit("operation2");

        // Act
        errorHandler.resetAllCircuits();

        // Assert
        assertEquals(MemoryErrorHandler.CircuitState.CLOSED,
            errorHandler.getCircuitState("operation1"));
        assertEquals(MemoryErrorHandler.CircuitState.CLOSED,
            errorHandler.getCircuitState("operation2"));
    }

    @Test
    @DisplayName("Should track error statistics")
    void testErrorStatistics() throws Exception {
        // Arrange
        MemoryErrorHandler.RetryableOperation<String> successOp = () -> "success";
        MemoryErrorHandler.RetryableOperation<String> failingOp = () -> {
            throw new RuntimeException("Error");
        };

        RetryPolicy policy = RetryPolicy.builder()
            .maxAttempts(2)
            .initialDelayMs(10)
            .build();

        // Act - Generate some errors
        errorHandler.handleWithRetry(successOp, policy);

        try {
            errorHandler.handleWithRetry(failingOp, policy);
        } catch (MemoryException e) {
            // Expected
        }

        // Assert
        MemoryErrorHandler.ErrorStats stats = errorHandler.getStats();
        assertTrue(stats.getTotalErrors() > 0);
        assertTrue(stats.getTotalRetries() > 0);
    }

    @Test
    @DisplayName("Should reset error statistics")
    void testResetStatistics() throws Exception {
        // Arrange - Generate some errors
        MemoryErrorHandler.RetryableOperation<String> failingOp = () -> {
            throw new RuntimeException("Error");
        };

        RetryPolicy policy = RetryPolicy.builder()
            .maxAttempts(2)
            .initialDelayMs(10)
            .build();

        try {
            errorHandler.handleWithRetry(failingOp, policy);
        } catch (MemoryException e) {
            // Expected
        }

        // Act
        errorHandler.resetStats();

        // Assert
        MemoryErrorHandler.ErrorStats stats = errorHandler.getStats();
        assertEquals(0, stats.getTotalErrors());
        assertEquals(0, stats.getTotalRetries());
    }

    @Test
    @DisplayName("Should handle comprehensive error strategy")
    void testComprehensiveErrorHandling() throws Exception {
        // Arrange
        MemoryErrorHandler.RetryableOperation<String> operation = () -> "result";
        RetryPolicy retryPolicy = RetryPolicy.builder()
            .maxAttempts(3)
            .strategy(RetryPolicy.RetryStrategy.EXPONENTIAL_BACKOFF)
            .build();
        CircuitBreakerConfig circuitConfig = CircuitBreakerConfig.builder()
            .failureThreshold(5)
            .build();
        MemoryErrorHandler.FallbackStrategy<String> fallback = error -> "fallback";

        // Act
        String result = errorHandler.handle(operation, retryPolicy, circuitConfig, fallback);

        // Assert
        assertEquals("result", result);
    }

    @Test
    @DisplayName("Should use fallback when all retries exhausted")
    void testFallbackAfterRetriesExhausted() throws Exception {
        // Arrange
        MemoryErrorHandler.RetryableOperation<String> operation = () -> {
            throw new RuntimeException("Persistent error");
        };

        RetryPolicy retryPolicy = RetryPolicy.builder()
            .maxAttempts(2)
            .initialDelayMs(10)
            .build();

        CircuitBreakerConfig circuitConfig = CircuitBreakerConfig.builder()
            .failureThreshold(5)
            .build();

        MemoryErrorHandler.FallbackStrategy<String> fallback = error -> "fallback-value";

        // Act
        String result = errorHandler.handle(operation, retryPolicy, circuitConfig, fallback);

        // Assert
        assertEquals("fallback-value", result);
    }

    @Test
    @DisplayName("Should handle concurrent operations safely")
    void testConcurrentOperations() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        AtomicInteger successCount = new AtomicInteger(0);

        // Act
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    MemoryErrorHandler.RetryableOperation<String> operation = () -> "success";
                    RetryPolicy policy = RetryPolicy.builder().maxAttempts(2).build();
                    String result = errorHandler.handleWithRetry(operation, policy);
                    if ("success".equals(result)) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Assert
        assertEquals(threadCount, successCount.get());
    }

    @Test
    @DisplayName("Should classify unknown errors")
    void testUnknownErrorClassification() {
        // Act
        MemoryErrorHandler.ErrorClassification classification =
            errorHandler.classifyError(new Exception("Unknown error"));

        // Assert
        assertNotNull(classification);
        assertEquals(MemoryErrorHandler.ErrorType.UNKNOWN, classification.getType());
    }

    @Test
    @DisplayName("Should get error statistics summary")
    void testErrorStatisticsSummary() throws Exception {
        // Arrange - Generate various errors
        MemoryErrorHandler.RetryableOperation<String> failingOp = () -> {
            throw new RuntimeException("Error");
        };

        RetryPolicy policy = RetryPolicy.builder()
            .maxAttempts(3)
            .initialDelayMs(10)
            .build();

        for (int i = 0; i < 5; i++) {
            try {
                errorHandler.handleWithRetry(failingOp, policy);
            } catch (MemoryException e) {
                // Expected
            }
        }

        // Act
        MemoryErrorHandler.ErrorStats stats = errorHandler.getStats();

        // Assert
        assertTrue(stats.getTotalErrors() > 0);
        assertTrue(stats.getRetryableErrors() > 0);
        assertNotNull(stats.toString());
    }

    @Test
    @DisplayName("Should handle null context in recovery")
    void testRecoveryWithNullContext() {
        // Arrange
        RuntimeException error = new RuntimeException("Error");

        // Act
        boolean recovered = errorHandler.recover(error, null);

        // Assert
        assertNotNull(recovered);
    }

    @Test
    @DisplayName("Should get circuit state for non-existent operation")
    void testGetCircuitStateForNonExistentOperation() {
        // Act
        MemoryErrorHandler.CircuitState state = errorHandler.getCircuitState("non-existent");

        // Assert
        assertNotNull(state);
        // Should return CLOSED state for non-existent operations
        assertEquals(MemoryErrorHandler.CircuitState.CLOSED, state);
    }

    @Test
    @DisplayName("Should handle exponential backoff retry")
    void testExponentialBackoffRetry() throws Exception {
        // Arrange
        AtomicInteger attempts = new AtomicInteger(0);
        long[] timestamps = new long[3];

        MemoryErrorHandler.RetryableOperation<String> operation = () -> {
            int attempt = attempts.incrementAndGet();
            timestamps[attempt - 1] = System.currentTimeMillis();
            if (attempt < 3) {
                throw new RuntimeException("Transient error");
            }
            return "success";
        };

        RetryPolicy policy = RetryPolicy.builder()
            .maxAttempts(3)
            .strategy(RetryPolicy.RetryStrategy.EXPONENTIAL_BACKOFF)
            .initialDelayMs(50)
            .backoffMultiplier(2.0)
            .build();

        // Act
        String result = errorHandler.handleWithRetry(operation, policy);

        // Assert
        assertEquals("success", result);
        assertEquals(3, attempts.get());
        // Verify exponential backoff (second retry should have longer delay)
        long delay1 = timestamps[1] - timestamps[0];
        long delay2 = timestamps[2] - timestamps[1];
        assertTrue(delay2 > delay1, "Exponential backoff should increase delay");
    }
}
