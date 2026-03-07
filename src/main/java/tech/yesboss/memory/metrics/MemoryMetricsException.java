package tech.yesboss.memory.metrics;

/**
 * Memory Metrics Exception
 *
 * <p>Exception thrown when metrics operations fail.</p>
 *
 * <p><b>Error Types:</b></p>
 * <ul>
 *   <li>CONFIGURATION_ERROR: Invalid configuration</li>
 *   <li>COLLECTION_ERROR: Error during metrics collection</li>
 *   <li>EXPORT_ERROR: Error during metrics export</li>
 *   <li>INITIALIZATION_ERROR: Error during initialization</li>
 * </ul>
 *
 * @author YesBoss Team
 * @version 1.0
 */
public class MemoryMetricsException extends RuntimeException {

    /**
     * Error type enumeration
     */
    public enum ErrorType {
        CONFIGURATION_ERROR("Invalid metrics configuration"),
        COLLECTION_ERROR("Error collecting metrics"),
        EXPORT_ERROR("Error exporting metrics"),
        INITIALIZATION_ERROR("Error initializing metrics"),
        VALIDATION_ERROR("Metrics validation failed"),
        TIMEOUT_ERROR("Metrics operation timed out");

        private final String description;

        ErrorType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    private final ErrorType errorType;

    /**
     * Create exception with message.
     *
     * @param message Error message
     */
    public MemoryMetricsException(String message) {
        super(message);
        this.errorType = null;
    }

    /**
     * Create exception with message and cause.
     *
     * @param message Error message
     * @param cause   Cause of the error
     */
    public MemoryMetricsException(String message, Throwable cause) {
        super(message, cause);
        this.errorType = null;
    }

    /**
     * Create exception with error type.
     *
     * @param errorType Type of error
     */
    public MemoryMetricsException(ErrorType errorType) {
        super(errorType.getDescription());
        this.errorType = errorType;
    }

    /**
     * Create exception with error type and message.
     *
     * @param errorType Type of error
     * @param message   Detailed error message
     */
    public MemoryMetricsException(ErrorType errorType, String message) {
        super(errorType.getDescription() + ": " + message);
        this.errorType = errorType;
    }

    /**
     * Create exception with error type, message, and cause.
     *
     * @param errorType Type of error
     * @param message   Detailed error message
     * @param cause     Cause of the error
     */
    public MemoryMetricsException(ErrorType errorType, String message, Throwable cause) {
        super(errorType.getDescription() + ": " + message, cause);
        this.errorType = errorType;
    }

    /**
     * Get the error type.
     *
     * @return Error type, or null if not set
     */
    public ErrorType getErrorType() {
        return errorType;
    }

    /**
     * Create configuration error exception.
     *
     * @param message Error message
     * @return Exception instance
     */
    public static MemoryMetricsException configurationError(String message) {
        return new MemoryMetricsException(ErrorType.CONFIGURATION_ERROR, message);
    }

    /**
     * Create configuration error exception with cause.
     *
     * @param message Error message
     * @param cause   Cause of the error
     * @return Exception instance
     */
    public static MemoryMetricsException configurationError(String message, Throwable cause) {
        return new MemoryMetricsException(ErrorType.CONFIGURATION_ERROR, message, cause);
    }

    /**
     * Create collection error exception.
     *
     * @param message Error message
     * @return Exception instance
     */
    public static MemoryMetricsException collectionError(String message) {
        return new MemoryMetricsException(ErrorType.COLLECTION_ERROR, message);
    }

    /**
     * Create collection error exception with cause.
     *
     * @param message Error message
     * @param cause   Cause of the error
     * @return Exception instance
     */
    public static MemoryMetricsException collectionError(String message, Throwable cause) {
        return new MemoryMetricsException(ErrorType.COLLECTION_ERROR, message, cause);
    }

    /**
     * Create export error exception.
     *
     * @param message Error message
     * @return Exception instance
     */
    public static MemoryMetricsException exportError(String message) {
        return new MemoryMetricsException(ErrorType.EXPORT_ERROR, message);
    }

    /**
     * Create export error exception with cause.
     *
     * @param message Error message
     * @param cause   Cause of the error
     * @return Exception instance
     */
    public static MemoryMetricsException exportError(String message, Throwable cause) {
        return new MemoryMetricsException(ErrorType.EXPORT_ERROR, message, cause);
    }

    /**
     * Create initialization error exception.
     *
     * @param message Error message
     * @return Exception instance
     */
    public static MemoryMetricsException initializationError(String message) {
        return new MemoryMetricsException(ErrorType.INITIALIZATION_ERROR, message);
    }

    /**
     * Create initialization error exception with cause.
     *
     * @param message Error message
     * @param cause   Cause of the error
     * @return Exception instance
     */
    public static MemoryMetricsException initializationError(String message, Throwable cause) {
        return new MemoryMetricsException(ErrorType.INITIALIZATION_ERROR, message, cause);
    }
}
