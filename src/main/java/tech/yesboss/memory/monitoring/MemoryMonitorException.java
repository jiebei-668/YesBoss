package tech.yesboss.memory.monitoring;

/**
 * Memory Monitor Exception
 *
 * <p>Exception thrown when monitoring operations fail.</p>
 *
 * <p><b>Error Types:</b></p>
 * <ul>
 *   <li>CONFIGURATION_ERROR: Invalid monitor configuration</li>
 *   <li>METRICS_COLLECTION_ERROR: Error collecting metrics</li>
 *   <li>ALERT_ERROR: Error sending alert</li>
 *   <li>INITIALIZATION_ERROR: Error initializing monitor</li>
 * </ul>
 *
 * @author YesBoss Team
 * @version 1.0
 */
public class MemoryMonitorException extends RuntimeException {

    /**
     * Error type enumeration
     */
    public enum ErrorType {
        CONFIGURATION_ERROR("Invalid monitor configuration"),
        METRICS_COLLECTION_ERROR("Error collecting metrics"),
        ALERT_ERROR("Error sending alert"),
        INITIALIZATION_ERROR("Error initializing monitor"),
        WEBHOOK_ERROR("Error sending webhook"),
        THRESHOLD_ERROR("Invalid threshold configuration");

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
    public MemoryMonitorException(String message) {
        super(message);
        this.errorType = null;
    }

    /**
     * Create exception with message and cause.
     *
     * @param message Error message
     * @param cause Cause of the error
     */
    public MemoryMonitorException(String message, Throwable cause) {
        super(message, cause);
        this.errorType = null;
    }

    /**
     * Create exception with error type.
     *
     * @param errorType Type of error
     */
    public MemoryMonitorException(ErrorType errorType) {
        super(errorType.getDescription());
        this.errorType = errorType;
    }

    /**
     * Create exception with error type and message.
     *
     * @param errorType Type of error
     * @param message Detailed error message
     */
    public MemoryMonitorException(ErrorType errorType, String message) {
        super(errorType.getDescription() + ": " + message);
        this.errorType = errorType;
    }

    /**
     * Create exception with error type, message, and cause.
     *
     * @param errorType Type of error
     * @param message Detailed error message
     * @param cause Cause of the error
     */
    public MemoryMonitorException(ErrorType errorType, String message, Throwable cause) {
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
    public static MemoryMonitorException configurationError(String message) {
        return new MemoryMonitorException(ErrorType.CONFIGURATION_ERROR, message);
    }

    /**
     * Create metrics collection error exception.
     *
     * @param message Error message
     * @return Exception instance
     */
    public static MemoryMonitorException metricsCollectionError(String message) {
        return new MemoryMonitorException(ErrorType.METRICS_COLLECTION_ERROR, message);
    }

    /**
     * Create alert error exception.
     *
     * @param message Error message
     * @return Exception instance
     */
    public static MemoryMonitorException alertError(String message) {
        return new MemoryMonitorException(ErrorType.ALERT_ERROR, message);
    }

    /**
     * Create initialization error exception.
     *
     * @param message Error message
     * @return Exception instance
     */
    public static MemoryMonitorException initializationError(String message) {
        return new MemoryMonitorException(ErrorType.INITIALIZATION_ERROR, message);
    }
}
