package tech.yesboss.memory.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Memory Error Handler Implementation
 *
 * <p>Comprehensive error handling with retry, circuit breaker, and fallback.</p>
 *
 * @author YesBoss Team
 * @version 1.0
 */
public class MemoryErrorHandlerImpl implements MemoryErrorHandler {

    private static final Logger logger = LoggerFactory.getLogger(MemoryErrorHandlerImpl.class);

    private final Map<String, CircuitBreakerState> circuitBreakers;
    private final ErrorStatistics stats;
    private final Map<Class<? extends Throwable>, RecoveryAction> recoveryActions;

    /**
     * Circuit breaker state
     */
    private static class CircuitBreakerState {
        final CircuitBreakerConfig config;
        final AtomicReference<CircuitState> state;
        final AtomicLong failureCount;
        final AtomicLong successCount;
        volatile long lastFailureTime;
        volatile long lastStateChangeTime;

        CircuitBreakerState(CircuitBreakerConfig config) {
            this.config = config;
            this.state = new AtomicReference<>(CircuitState.CLOSED);
            this.failureCount = new AtomicLong(0);
            this.successCount = new AtomicLong(0);
            this.lastFailureTime = 0;
            this.lastStateChangeTime = System.currentTimeMillis();
        }

        void recordFailure() {
            failureCount.incrementAndGet();
            lastFailureTime = System.currentTimeMillis();
            checkThreshold();
        }

        void recordSuccess() {
            successCount.incrementAndGet();
            if (state.get() == CircuitState.HALF_OPEN) {
                if (successCount.get() >= config.getSuccessThreshold()) {
                    state.set(CircuitState.CLOSED);
                    resetCounts();
                }
            }
        }

        private void checkThreshold() {
            long failures = failureCount.get();
            if (failures >= config.getFailureThreshold()) {
                state.set(CircuitState.OPEN);
                lastStateChangeTime = System.currentTimeMillis();
            }
        }

        void resetCounts() {
            failureCount.set(0);
            successCount.set(0);
        }

        boolean shouldAllowRequest() {
            if (!config.isEnabled()) {
                return true;
            }

            CircuitState currentState = state.get();

            // Check if timeout has passed to transition to HALF_OPEN
            if (currentState == CircuitState.OPEN) {
                long timeSinceOpen = System.currentTimeMillis() - lastStateChangeTime;
                if (timeSinceOpen >= config.getTimeoutMs()) {
                    state.set(CircuitState.HALF_OPEN);
                    lastStateChangeTime = System.currentTimeMillis();
                    return true;
                }
                return false;
            }

            return true;
        }
    }

    /**
     * Error statistics
     */
    private static class ErrorStatistics {
        final AtomicLong totalErrors = new AtomicLong(0);
        final AtomicLong retryableErrors = new AtomicLong(0);
        final AtomicLong permanentErrors = new AtomicLong(0);
        final AtomicLong recoveredErrors = new AtomicLong(0);
        final AtomicLong totalRetries = new AtomicLong(0);
        final AtomicLong successfulRecoveries = new AtomicLong(0);

        void recordError(boolean retryable) {
            totalErrors.incrementAndGet();
            if (retryable) {
                retryableErrors.incrementAndGet();
            } else {
                permanentErrors.incrementAndGet();
            }
        }

        void recordRetry() {
            totalRetries.incrementAndGet();
        }

        void recordRecovery(boolean success) {
            recoveredErrors.incrementAndGet();
            if (success) {
                successfulRecoveries.incrementAndGet();
            }
        }

        ErrorStats toSnapshot() {
            return new ErrorStats(
                totalErrors.get(),
                retryableErrors.get(),
                permanentErrors.get(),
                recoveredErrors.get(),
                totalRetries.get(),
                successfulRecoveries.get()
            );
        }

        void reset() {
            totalErrors.set(0);
            retryableErrors.set(0);
            permanentErrors.set(0);
            recoveredErrors.set(0);
            totalRetries.set(0);
            successfulRecoveries.set(0);
        }
    }

    /**
     * Create error handler with default settings.
     */
    public MemoryErrorHandlerImpl() {
        this.circuitBreakers = new ConcurrentHashMap<>();
        this.stats = new ErrorStatistics();
        this.recoveryActions = new ConcurrentHashMap<>();
    }

    // ==================== Error Handling ====================

    @Override
    public <T> T handleWithRetry(RetryableOperation<T> operation, RetryPolicy policy) throws MemoryException {
        return handle(operation, policy, CircuitBreakerConfig.disabled(), null);
    }

    @Override
    public <T> T handleWithCircuitBreaker(RetryableOperation<T> operation,
                                          CircuitBreakerConfig circuitBreaker) throws MemoryException {
        return handle(operation, RetryPolicy.defaults(), circuitBreaker, null);
    }

    @Override
    public <T> T handleWithFallback(RetryableOperation<T> operation, FallbackStrategy<T> fallback) {
        return handle(operation, RetryPolicy.defaults(), CircuitBreakerConfig.disabled(), fallback);
    }

    @Override
    public <T> T handle(RetryableOperation<T> operation,
                       RetryPolicy policy,
                       CircuitBreakerConfig circuitBreaker,
                       FallbackStrategy<T> fallback) {
        String operationName = operation.getClass().getSimpleName();
        MemoryException lastException = null;

        // Check circuit breaker
        if (circuitBreaker.isEnabled()) {
            CircuitBreakerState cbState = circuitBreakers.computeIfAbsent(
                operationName, k -> new CircuitBreakerState(circuitBreaker));

            if (!cbState.shouldAllowRequest()) {
                logger.warn("Circuit breaker OPEN for operation: {}", operationName);
                if (fallback != null) {
                    return fallback.getFallback(new MemoryException(
                        MemoryException.ErrorType.CONNECTION_ERROR,
                        "Circuit breaker open"));
                }
                throw new MemoryException(
                    MemoryException.ErrorType.CONNECTION_ERROR,
                    "Circuit breaker open for operation: " + operationName);
            }
        }

        // Retry loop
        for (int attempt = 0; attempt < policy.getMaxAttempts(); attempt++) {
            try {
                if (attempt > 0) {
                    stats.recordRetry();
                    long delay = policy.calculateDelay(attempt - 1);
                    logger.debug("Retrying operation {} after {}ms (attempt {})",
                        operationName, delay, attempt + 1);
                    Thread.sleep(delay);
                }

                T result = operation.execute();

                // Record success in circuit breaker
                if (circuitBreaker.isEnabled()) {
                    CircuitBreakerState cbState = circuitBreakers.get(operationName);
                    if (cbState != null) {
                        cbState.recordSuccess();
                    }
                }

                return result;

            } catch (Exception e) {
                lastException = wrapException(e);

                // Check if retryable
                if (!isRetryable(e) || attempt == policy.getMaxAttempts() - 1) {
                    break;
                }

                logger.debug("Operation {} failed (attempt {}): {}",
                    operationName, attempt + 1, e.getMessage());
            }
        }

        // All retries exhausted
        stats.recordError(lastException != null && lastException.isRetryable());

        if (circuitBreaker.isEnabled()) {
            CircuitBreakerState cbState = circuitBreakers.get(operationName);
            if (cbState != null) {
                cbState.recordFailure();
            }
        }

        // Use fallback if available
        if (fallback != null) {
            logger.info("Using fallback for operation: {}", operationName);
            return fallback.getFallback(lastException);
        }

        throw lastException != null ? lastException :
            new MemoryException(MemoryException.ErrorType.UNKNOWN_ERROR, "Operation failed");
    }

    // ==================== Error Classification ====================

    @Override
    public ErrorClassification classifyError(Throwable throwable) {
        if (throwable == null) {
            return new ErrorClassification(ErrorType.UNKNOWN, false, "null");
        }

        // Check for specific exception types
        if (throwable instanceof MemoryException) {
            MemoryException me = (MemoryException) throwable;
            return new ErrorClassification(ErrorType.valueOf(me.getErrorType().name()), me.isRetryable(), me.getErrorCode());
        }

        // Classify by exception type
        String exceptionType = throwable.getClass().getSimpleName();
        ErrorType type;
        boolean retryable;

        if (isNetworkRelated(throwable)) {
            type = ErrorType.TRANSIENT;
            retryable = true;
        } else if (isDatabaseRelated(throwable)) {
            type = ErrorType.RECOVERABLE;
            retryable = isRetryableDatabaseError(throwable);
        } else if (isValidationRelated(throwable)) {
            type = ErrorType.PERMANENT;
            retryable = false;
        } else {
            type = ErrorType.UNKNOWN;
            retryable = false;
        }

        return new ErrorClassification(type, retryable, exceptionType);
    }

    @Override
    public boolean isRetryable(Throwable throwable) {
        if (throwable instanceof MemoryException) {
            return ((MemoryException) throwable).isRetryable();
        }

        // Network and timeout errors are generally retryable
        if (isNetworkRelated(throwable)) {
            return true;
        }

        // Some database errors are retryable
        if (isDatabaseRelated(throwable)) {
            return isRetryableDatabaseError(throwable);
        }

        return false;
    }

    @Override
    public boolean isRecoverable(Throwable throwable) {
        ErrorClassification classification = classifyError(throwable);
        return classification.getType() != ErrorType.PERMANENT;
    }

    // ==================== Error Recovery ====================

    @Override
    public boolean recover(Throwable throwable, Map<String, Object> context) {
        RecoveryAction action = getRecoveryAction(throwable);
        if (action != null) {
            try {
                boolean recovered = action.recover(throwable);
                stats.recordRecovery(recovered);
                return recovered;
            } catch (Exception e) {
                logger.warn("Recovery action failed: {}", e.getMessage());
                stats.recordRecovery(false);
                return false;
            }
        }
        return false;
    }

    @Override
    public RecoveryAction getRecoveryAction(Throwable throwable) {
        Class<? extends Throwable> throwableClass = throwable.getClass();
        return recoveryActions.get(throwableClass);
    }

    /**
     * Register recovery action for specific exception type.
     *
     * @param exceptionClass Exception class
     * @param action Recovery action
     */
    public <T extends Throwable> void registerRecoveryAction(Class<T> exceptionClass, RecoveryAction action) {
        recoveryActions.put(exceptionClass, action);
    }

    // ==================== Circuit Breaker Management ====================

    @Override
    public void openCircuit(String operationName) {
        CircuitBreakerState state = circuitBreakers.get(operationName);
        if (state != null) {
            state.state.set(CircuitState.OPEN);
            state.lastStateChangeTime = System.currentTimeMillis();
            logger.info("Circuit opened for operation: {}", operationName);
        }
    }

    @Override
    public void closeCircuit(String operationName) {
        CircuitBreakerState state = circuitBreakers.get(operationName);
        if (state != null) {
            state.state.set(CircuitState.CLOSED);
            state.resetCounts();
            state.lastStateChangeTime = System.currentTimeMillis();
            logger.info("Circuit closed for operation: {}", operationName);
        }
    }

    @Override
    public CircuitState getCircuitState(String operationName) {
        CircuitBreakerState state = circuitBreakers.get(operationName);
        return state != null ? state.state.get() : CircuitState.CLOSED;
    }

    @Override
    public void resetAllCircuits() {
        circuitBreakers.clear();
        logger.info("All circuit breakers reset");
    }

    // ==================== Error Statistics ====================

    @Override
    public ErrorStats getStats() {
        return stats.toSnapshot();
    }

    @Override
    public void resetStats() {
        stats.reset();
    }

    // ==================== Private Helper Methods ====================

    /**
     * Wrap exception in MemoryException if needed.
     *
     * @param e Original exception
     * @return Wrapped or original exception
     */
    private MemoryException wrapException(Exception e) {
        if (e instanceof MemoryException) {
            return (MemoryException) e;
        }

        // Map common exceptions to MemoryException types
        String message = e.getMessage();
        if (isNetworkRelated(e)) {
            return new MemoryException(MemoryException.ErrorType.CONNECTION_ERROR,
                message != null ? message : "Connection error", e, true);
        } else if (isDatabaseRelated(e)) {
            return new MemoryException(MemoryException.ErrorType.QUERY_ERROR,
                message != null ? message : "Database error", e, isRetryableDatabaseError(e));
        } else {
            return new MemoryException(MemoryException.ErrorType.UNKNOWN_ERROR,
                message != null ? message : "Unknown error", e, false);
        }
    }

    /**
     * Check if exception is network-related.
     *
     * @param throwable Exception to check
     * @return true if network-related
     */
    private boolean isNetworkRelated(Throwable throwable) {
        String className = throwable.getClass().getName();
        return className.contains("Timeout") ||
               className.contains("Connect") ||
               className.contains("Socket") ||
               className.contains("Network") ||
               className.contains("Http");
    }

    /**
     * Check if exception is database-related.
     *
     * @param throwable Exception to check
     * @return true if database-related
     */
    private boolean isDatabaseRelated(Throwable throwable) {
        String className = throwable.getClass().getName();
        return className.contains("SQL") ||
               className.contains("Database") ||
               className.contains("Connection") ||
               throwable instanceof java.sql.SQLException;
    }

    /**
     * Check if exception is validation-related.
     *
     * @param throwable Exception to check
     * @return true if validation-related
     */
    private boolean isValidationRelated(Throwable throwable) {
        String className = throwable.getClass().getName();
        return className.contains("Validation") ||
               className.contains("IllegalArgument") ||
               throwable instanceof IllegalArgumentException;
    }

    /**
     * Check if database error is retryable.
     *
     * @param throwable Exception to check
     * @return true if retryable
     */
    private boolean isRetryableDatabaseError(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null) {
            return true;
        }
        // Connection deadlocks and timeouts are retryable
        return message.toLowerCase().contains("timeout") ||
               message.toLowerCase().contains("deadlock") ||
               message.toLowerCase().contains("connection");
    }
}
