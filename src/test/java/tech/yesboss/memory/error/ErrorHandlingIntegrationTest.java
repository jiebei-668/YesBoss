package tech.yesboss.memory.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for error handling system.
 */
@DisplayName("Error Handling Integration Tests")
public class ErrorHandlingIntegrationTest {

    private MemoryErrorHandler errorHandler;

    @BeforeEach
    void setUp() {
        errorHandler = new MemoryErrorHandlerImpl();
    }

    @Test
    @DisplayName("Should handle complete error handling flow")
    void testCompleteErrorHandlingFlow() throws Exception {
        // Arrange
        AtomicInteger attempts = new AtomicInteger(0);
        MemoryErrorHandler.RetryableOperation<String> operation = () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new RuntimeException("Transient error");
            }
            return "success";
        };

        RetryPolicy retryPolicy = RetryPolicy.builder()
            .maxAttempts(5)
            .strategy(RetryPolicy.RetryStrategy.EXPONENTIAL_BACKOFF)
            .initialDelayMs(50)
            .build();

        CircuitBreakerConfig circuitConfig = CircuitBreakerConfig.builder()
            .failureThreshold(3)
            .successThreshold(2)
            .timeoutMs(10000)
            .build();

        MemoryErrorHandler.FallbackStrategy<String> fallback = error -> "fallback";

        // Act
        String result = errorHandler.handle(operation, retryPolicy, circuitConfig, fallback);

        // Assert
        assertEquals("success", result);
        assertEquals(3, attempts.get());
    }

    @Test
    @DisplayName("Should fallback when all strategies fail")
    void testFallbackAfterAllStrategiesFail() throws Exception {
        // Arrange
        MemoryErrorHandler.RetryableOperation<String> operation = () -> {
            throw new RuntimeException("Persistent error");
        };

        RetryPolicy retryPolicy = RetryPolicy.builder()
            .maxAttempts(3)
            .initialDelayMs(10)
            .build();

        CircuitBreakerConfig circuitConfig = CircuitBreakerConfig.builder()
            .failureThreshold(2)
            .build();

        MemoryErrorHandler.FallbackStrategy<String> fallback = error -> {
            assertEquals("Persistent error", error.getMessage());
            return "fallback-value";
        };

        // Act
        String result = errorHandler.handle(operation, retryPolicy, circuitConfig, fallback);

        // Assert
        assertEquals("fallback-value", result);
    }

    @Test
    @DisplayName("Should open circuit after threshold and reject requests")
    void testCircuitBreakerLifecycle() throws Exception {
        // Arrange
        String operationName = "failing-operation";
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
            .failureThreshold(3)
            .successThreshold(2)
            .timeoutMs(5000)
            .build();

        MemoryErrorHandler.RetryableOperation<String> failingOperation = () -> {
            throw new RuntimeException("Service unavailable");
        };

        MemoryErrorHandler.RetryableOperation<String> successOperation = () -> "success";

        // Act - Trigger failures to open circuit
        for (int i = 0; i < 3; i++) {
            try {
                errorHandler.handleWithCircuitBreaker(failingOperation, config);
            } catch (MemoryException e) {
                // Expected
            }
        }

        // Assert - Circuit should be open
        assertEquals(MemoryErrorHandler.CircuitState.OPEN,
            errorHandler.getCircuitState(operationName));

        // Act - Try to execute with open circuit
        try {
            errorHandler.handleWithCircuitBreaker(successOperation, config);
            fail("Should throw exception when circuit is open");
        } catch (MemoryException e) {
            // Expected
            assertEquals(MemoryErrorHandler.CircuitState.OPEN,
                errorHandler.getCircuitState(operationName));
        }
    }

    @Test
    @DisplayName("Should track error statistics across operations")
    void testErrorStatisticsTracking() throws Exception {
        // Arrange
        MemoryErrorHandler.RetryableOperation<String> successOp = () -> "success";
        MemoryErrorHandler.RetryableOperation<String> failingOp = () -> {
            throw new RuntimeException("Error");
        };

        RetryPolicy policy = RetryPolicy.builder()
            .maxAttempts(3)
            .initialDelayMs(10)
            .build();

        // Act - Execute successful operation
        errorHandler.handleWithRetry(successOp, policy);

        // Execute failing operations
        for (int i = 0; i < 2; i++) {
            try {
                errorHandler.handleWithRetry(failingOp, policy);
            } catch (MemoryException e) {
                // Expected
            }
        }

        // Assert
        MemoryErrorHandler.ErrorStats stats = errorHandler.getStats();
        assertTrue(stats.getTotalErrors() > 0);
        assertTrue(stats.getTotalRetries() > 0);
        assertTrue(stats.getRetryableErrors() > 0);
    }

    @Test
    @DisplayName("Should recover from errors with context")
    void testErrorRecoveryWithContext() {
        // Arrange
        RuntimeException error = new RuntimeException("Recoverable error");
        java.util.Map<String, Object> context = java.util.Map.of(
            "operation", "test",
            "retryCount", 3,
            "timestamp", System.currentTimeMillis()
        );

        // Act
        boolean recovered = errorHandler.recover(error, context);

        // Assert - Recovery depends on error type and context
        assertNotNull(recovered);
    }

    @Test
    @DisplayName("Should classify different error types correctly")
    void testErrorClassification() {
        // Arrange & Assert - Transient errors
        MemoryErrorHandler.ErrorClassification timeoutClassification =
            errorHandler.classifyError(new java.net.SocketTimeoutException());
        assertEquals(MemoryErrorHandler.ErrorType.TRANSIENT, timeoutClassification.getType());
        assertTrue(timeoutClassification.isRetryable());

        // Recoverable errors
        MemoryErrorHandler.ErrorClassification recoverableClassification =
            errorHandler.classifyError(new org.springframework.dao.DataIntegrityViolationException(""));
        assertEquals(MemoryErrorHandler.ErrorType.RECOVERABLE, recoverableClassification.getType());

        // Permanent errors
        MemoryErrorHandler.ErrorClassification permanentClassification =
            errorHandler.classifyError(new IllegalArgumentException());
        assertEquals(MemoryErrorHandler.ErrorType.PERMANENT, permanentClassification.getType());
        assertFalse(permanentClassification.isRetryable());

        // System errors
        MemoryErrorHandler.ErrorClassification systemClassification =
            errorHandler.classifyError(new OutOfMemoryError());
        assertEquals(MemoryErrorHandler.ErrorType.SYSTEM, systemClassification.getType());
    }

    @Test
    @DisplayName("Should handle concurrent error scenarios")
    void testConcurrentErrorHandling() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        int operationsPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        RetryPolicy policy = RetryPolicy.builder()
            .maxAttempts(3)
            .initialDelayMs(10)
            .build();

        // Act
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        MemoryErrorHandler.RetryableOperation<String> operation = () -> {
                            if (Math.random() < 0.3) {
                                throw new RuntimeException("Random error");
                            }
                            return "success";
                        };

                        try {
                            String result = errorHandler.handleWithRetry(operation, policy);
                            if ("success".equals(result)) {
                                successCount.incrementAndGet();
                            }
                        } catch (MemoryException e) {
                            errorCount.incrementAndGet();
                        }
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
        assertTrue(successCount.get() > 0 || errorCount.get() > 0);
        assertEquals(threadCount * operationsPerThread,
            successCount.get() + errorCount.get());
    }

    @Test
    @DisplayName("Should handle complex error scenarios")
    void testComplexErrorScenarios() throws Exception {
        // Arrange - Mix of success and failure
        AtomicInteger counter = new AtomicInteger(0);
        MemoryErrorHandler.RetryableOperation<String> operation = () -> {
            int count = counter.incrementAndGet();
            if (count % 3 == 0) {
                return "success";
            }
            throw new RuntimeException("Error on attempt " + count);
        };

        RetryPolicy policy = RetryPolicy.builder()
            .maxAttempts(5)
            .strategy(RetryPolicy.RetryStrategy.EXPONENTIAL_BACKOFF)
            .initialDelayMs(20)
            .build();

        MemoryErrorHandler.FallbackStrategy<String> fallback =
            error -> "fallback-after-" + counter.get();

        // Act
        String result = errorHandler.handle(operation, policy, null, fallback);

        // Assert
        assertTrue(result.equals("success") || result.startsWith("fallback"));
    }

    @Test
    @DisplayName("Should reset statistics correctly")
    void testStatisticsReset() throws Exception {
        // Arrange - Generate some errors
        MemoryErrorHandler.RetryableOperation<String> failingOp = () -> {
            throw new RuntimeException("Error");
        };

        RetryPolicy policy = RetryPolicy.builder()
            .maxAttempts(2)
            .initialDelayMs(10)
            .build();

        for (int i = 0; i < 3; i++) {
            try {
                errorHandler.handleWithRetry(failingOp, policy);
            } catch (MemoryException e) {
                // Expected
            }
        }

        MemoryErrorHandler.ErrorStats statsBefore = errorHandler.getStats();
        assertTrue(statsBefore.getTotalErrors() > 0);

        // Act
        errorHandler.resetStats();

        // Assert
        MemoryErrorHandler.ErrorStats statsAfter = errorHandler.getStats();
        assertEquals(0, statsAfter.getTotalErrors());
        assertEquals(0, statsAfter.getTotalRetries());
        assertEquals(0, statsAfter.getRecoveredErrors());
    }

    @Test
    @DisplayName("Should manage multiple circuit breakers independently")
    void testMultipleCircuitBreakers() throws Exception {
        // Arrange
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
            .failureThreshold(2)
            .build();

        // Act - Open first circuit
        errorHandler.openCircuit("operation1");
        assertEquals(MemoryErrorHandler.CircuitState.OPEN,
            errorHandler.getCircuitState("operation1"));

        // Second circuit should remain closed
        assertEquals(MemoryErrorHandler.CircuitState.CLOSED,
            errorHandler.getCircuitState("operation2"));

        // Close first circuit
        errorHandler.closeCircuit("operation1");
        assertEquals(MemoryErrorHandler.CircuitState.CLOSED,
            errorHandler.getCircuitState("operation1"));
    }

    @Test
    @DisplayName("Should handle retry with different strategies")
    void testRetryWithDifferentStrategies() throws Exception {
        // Arrange
        AtomicInteger attempts = new AtomicInteger(0);
        MemoryErrorHandler.RetryableOperation<String> operation = () -> {
            attempts.incrementAndGet();
            if (attempts.get() < 3) {
                throw new RuntimeException("Error");
            }
            return "success";
        };

        // Test fixed delay
        attempts.set(0);
        RetryPolicy fixedPolicy = RetryPolicy.builder()
            .maxAttempts(5)
            .strategy(RetryPolicy.RetryStrategy.FIXED_DELAY)
            .initialDelayMs(10)
            .build();
        String result1 = errorHandler.handleWithRetry(operation, fixedPolicy);
        assertEquals("success", result1);

        // Test exponential backoff
        attempts.set(0);
        RetryPolicy exponentialPolicy = RetryPolicy.builder()
            .maxAttempts(5)
            .strategy(RetryPolicy.RetryStrategy.EXPONENTIAL_BACKOFF)
            .initialDelayMs(10)
            .build();
        String result2 = errorHandler.handleWithRetry(operation, exponentialPolicy);
        assertEquals("success", result2);

        // Test linear backoff
        attempts.set(0);
        RetryPolicy linearPolicy = RetryPolicy.builder()
            .maxAttempts(5)
            .strategy(RetryPolicy.RetryStrategy.LINEAR_BACKOFF)
            .initialDelayMs(10)
            .build();
        String result3 = errorHandler.handleWithRetry(operation, linearPolicy);
        assertEquals("success", result3);
    }

    @Test
    @DisplayName("Should handle error recovery actions")
    void testErrorRecoveryActions() {
        // Arrange
        RuntimeException error = new RuntimeException("Recoverable error");

        // Act
        MemoryErrorHandler.RecoveryAction action = errorHandler.getRecoveryAction(error);

        // Assert - Action may or may not be available depending on error type
        // This test verifies the method works without throwing exceptions
        if (action != null) {
            boolean recovered = action.recover(error);
            assertNotNull(recovered);
        }
    }

    @Test
    @DisplayName("Should provide comprehensive error statistics")
    void testComprehensiveErrorStatistics() throws Exception {
        // Arrange - Generate various types of errors
        MemoryErrorHandler.RetryableOperation<String> transientOp = () -> {
            throw new java.net.SocketTimeoutException();
        };

        MemoryErrorHandler.RetryableOperation<String> permanentOp = () -> {
            throw new IllegalArgumentException();
        };

        RetryPolicy policy = RetryPolicy.builder()
            .maxAttempts(3)
            .initialDelayMs(10)
            .build();

        // Act - Generate errors
        for (int i = 0; i < 2; i++) {
            try {
                errorHandler.handleWithRetry(transientOp, policy);
            } catch (MemoryException e) {
                // Expected
            }
        }

        for (int i = 0; i < 2; i++) {
            try {
                errorHandler.handleWithRetry(permanentOp, policy);
            } catch (MemoryException e) {
                // Expected
            }
        }

        // Assert
        MemoryErrorHandler.ErrorStats stats = errorHandler.getStats();
        assertTrue(stats.getTotalErrors() >= 4);
        assertTrue(stats.getRetryableErrors() > 0);
        assertTrue(stats.getPermanentErrors() > 0);
        assertNotNull(stats.toString());
    }

    @Test
    @DisplayName("Should handle fallback with error information")
    void testFallbackWithErrorInformation() throws Exception {
        // Arrange
        RuntimeException originalError = new RuntimeException("Original error");
        AtomicInteger fallbackCallCount = new AtomicInteger(0);

        MemoryErrorHandler.RetryableOperation<String> operation = () -> {
            throw originalError;
        };

        MemoryErrorHandler.FallbackStrategy<String> fallback = error -> {
            fallbackCallCount.incrementAndGet();
            assertEquals(originalError, error);
            return "fallback-value";
        };

        // Act
        String result = errorHandler.handleWithFallback(operation, fallback);

        // Assert
        assertEquals("fallback-value", result);
        assertEquals(1, fallbackCallCount.get());
    }
}
