package tech.yesboss.memory.error;

import java.util.Map;

/**
 * Memory Error Handler Interface
 *
 * <p>Defines error handling strategies for memory persistence operations.</p>
 *
 * <p><b>Error Handling Features:</b></p>
 * <ul>
 *   <li>Automatic retry with exponential backoff</li>
 *   <li>Circuit breaker pattern</li>
 *   <li>Fallback/degradation strategies</li>
 *   <li>Error classification and routing</li>
 *   <li>Error recovery mechanisms</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * MemoryErrorHandler handler = new MemoryErrorHandlerImpl(config);
 * Result result = handler.handle(() -> riskyOperation(),
 *     RetryPolicy exponentialBackoff(),
 *     FallbackStrategy.defaultValue()
 * );
 * </pre>
 *
 * @author YesBoss Team
 * @version 1.0
 */
public interface MemoryErrorHandler {

    // ==================== Error Handling ====================

    /**
     * Handle error with retry policy.
     *
     * @param operation Operation to execute
     * @param policy Retry policy
     * @param <T> Result type
     * @return Operation result
     * @throws MemoryException if all retries exhausted
     */
    <T> T handleWithRetry(RetryableOperation<T> operation, RetryPolicy policy) throws MemoryException;

    /**
     * Handle error with circuit breaker.
     *
     * @param operation Operation to execute
     * @param circuitBreaker Circuit breaker configuration
     * @param <T> Result type
     * @return Operation result
     * @throws MemoryException if circuit is open
     */
    <T> T handleWithCircuitBreaker(RetryableOperation<T> operation,
                                   CircuitBreakerConfig circuitBreaker) throws MemoryException;

    /**
     * Handle error with fallback.
     *
     * @param operation Operation to execute
     * @param fallback Fallback strategy
     * @param <T> Result type
     * @return Operation result or fallback value
     */
    <T> T handleWithFallback(RetryableOperation<T> operation, FallbackStrategy<T> fallback);

    /**
     * Handle error with comprehensive strategy.
     *
     * @param operation Operation to execute
     * @param policy Retry policy
     * @param circuitBreaker Circuit breaker configuration
     * @param fallback Fallback strategy
     * @param <T> Result type
     * @return Operation result or fallback value
     */
    <T> T handle(RetryableOperation<T> operation,
                RetryPolicy policy,
                CircuitBreakerConfig circuitBreaker,
                FallbackStrategy<T> fallback);

    // ==================== Error Classification ====================

    /**
     * Classify error type.
     *
     * @param throwable Error/exception
     * @return Error classification
     */
    ErrorClassification classifyError(Throwable throwable);

    /**
     * Check if error is retryable.
     *
     * @param throwable Error/exception
     * @return true if retryable
     */
    boolean isRetryable(Throwable throwable);

    /**
     * Check if error is recoverable.
     *
     * @param throwable Error/exception
     * @return true if recoverable
     */
    boolean isRecoverable(Throwable throwable);

    // ==================== Error Recovery ====================

    /**
     * Attempt to recover from error.
     *
     * @param throwable Error/exception
     * @param context Error context
     * @return true if recovery successful
     */
    boolean recover(Throwable throwable, Map<String, Object> context);

    /**
     * Get recovery action for error.
     *
     * @param throwable Error/exception
     * @return Recovery action, or null if none available
     */
    RecoveryAction getRecoveryAction(Throwable throwable);

    // ==================== Circuit Breaker Management ====================

    /**
     * Open circuit breaker for specific operation.
     *
     * @param operationName Operation name
     */
    void openCircuit(String operationName);

    /**
     * Close circuit breaker for specific operation.
     *
     * @param operationName Operation name
     */
    void closeCircuit(String operationName);

    /**
     * Get circuit breaker state.
     *
     * @param operationName Operation name
     * @return Circuit state
     */
    CircuitState getCircuitState(String operationName);

    /**
     * Reset all circuit breakers.
     */
    void resetAllCircuits();

    // ==================== Error Statistics ====================

    /**
     * Get error statistics.
     *
     * @return Error statistics
     */
    ErrorStats getStats();

    /**
     * Reset error statistics.
     */
    void resetStats();

    // ==================== Inner Interfaces ====================

    /**
     * Retryable operation interface
     *
     * @param <T> Result type
     */
    @FunctionalInterface
    interface RetryableOperation<T> {
        /**
         * Execute operation.
         *
         * @return Operation result
         * @throws Exception if operation fails
         */
        T execute() throws Exception;
    }

    /**
     * Fallback strategy interface
     *
     * @param <T> Result type
     */
    @FunctionalInterface
    interface FallbackStrategy<T> {
        /**
         * Get fallback value.
         *
         * @param error Error that occurred
         * @return Fallback value
         */
        T getFallback(Throwable error);
    }

    /**
     * Recovery action interface
     */
    @FunctionalInterface
    interface RecoveryAction {
        /**
         * Execute recovery action.
         *
         * @param error Error to recover from
         * @return true if recovery successful
         */
        boolean recover(Throwable error);
    }

    /**
     * Error classification
     */
    enum ErrorType {
        TRANSIENT,           // Temporary errors (network, timeout)
        RECOVERABLE,         // Recoverable errors (constraint violations)
        PERMANENT,           // Permanent errors (invalid data)
        SYSTEM,              // System errors (out of memory)
        UNKNOWN              // Unknown errors
    }

    /**
     * Error classification
     */
    class ErrorClassification {
        private final ErrorType type;
        private final boolean retryable;
        private final String category;

        public ErrorClassification(ErrorType type, boolean retryable, String category) {
            this.type = type;
            this.retryable = retryable;
            this.category = category;
        }

        public ErrorType getType() {
            return type;
        }

        public boolean isRetryable() {
            return retryable;
        }

        public String getCategory() {
            return category;
        }
    }

    /**
     * Circuit state
     */
    enum CircuitState {
        CLOSED,    // Normal operation
        OPEN,      // Circuit open, reject requests
        HALF_OPEN  // Testing if service recovered
    }

    /**
     * Error statistics
     */
    class ErrorStats {
        private final long totalErrors;
        private final long retryableErrors;
        private final long permanentErrors;
        private final long recoveredErrors;
        private final long totalRetries;
        private final long successfulRecoveries;

        public ErrorStats(long totalErrors, long retryableErrors, long permanentErrors,
                         long recoveredErrors, long totalRetries, long successfulRecoveries) {
            this.totalErrors = totalErrors;
            this.retryableErrors = retryableErrors;
            this.permanentErrors = permanentErrors;
            this.recoveredErrors = recoveredErrors;
            this.totalRetries = totalRetries;
            this.successfulRecoveries = successfulRecoveries;
        }

        public long getTotalErrors() {
            return totalErrors;
        }

        public long getRetryableErrors() {
            return retryableErrors;
        }

        public long getPermanentErrors() {
            return permanentErrors;
        }

        public long getRecoveredErrors() {
            return recoveredErrors;
        }

        public long getTotalRetries() {
            return totalRetries;
        }

        public long getSuccessfulRecoveries() {
            return successfulRecoveries;
        }

        public double getRecoveryRate() {
            return totalErrors == 0 ? 0.0 : (double) recoveredErrors / totalErrors;
        }

        @Override
        public String toString() {
            return String.format("ErrorStats{total=%d, retryable=%d, permanent=%d, " +
                    "recovered=%d, retries=%d, recoveryRate=%.2f%%}",
                    totalErrors, retryableErrors, permanentErrors,
                    recoveredErrors, totalRetries, getRecoveryRate() * 100);
        }
    }
}
