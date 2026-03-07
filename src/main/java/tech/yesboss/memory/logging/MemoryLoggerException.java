package tech.yesboss.memory.logging;

/**
 * Memory Logger Exception
 *
 * <p>Exception thrown when logging operations fail.</p>
 *
 * <p><b>Error Types:</b></p>
 * <ul>
 *   <li>CONFIGURATION_ERROR: Invalid logger configuration</li>
 *   <li>LOG_WRITE_ERROR: Error writing log</li>
 *   <li>AUDIT_LOG_ERROR: Error writing audit log</li>
 *   <li>INITIALIZATION_ERROR: Error initializing logger</li>
 * </ul>
 *
 * @author YesBoss Team
 * @version 1.0
 */
public class MemoryLoggerException extends RuntimeException {

    /**
     * Error type enumeration
     */
    public enum ErrorType {
        CONFIGURATION_ERROR("Invalid logger configuration"),
        LOG_WRITE_ERROR("Error writing log"),
        AUDIT_LOG_ERROR("Error writing audit log"),
        INITIALIZATION_ERROR("Error initializing logger"),
        FILE_WRITE_ERROR("Error writing to log file"),
        ASYNC_QUEUE_FULL("Async logging queue full"),
        OPERATION_TRACKING_ERROR("Error tracking operation");

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
    public MemoryLoggerException(String message) {
        super(message);
        this.errorType = null;
    }

    /**
     * Create exception with message and cause.
     *
     * @param message Error message
     * @param cause   Cause of the error
     */
    public MemoryLoggerException(String message, Throwable cause) {
        super(message, cause);
        this.errorType = null;
    }

    /**
     * Create exception with error type.
     *
     * @param errorType Type of error
     */
    public MemoryLoggerException(ErrorType errorType) {
        super(errorType.getDescription());
        this.errorType = errorType;
    }

    /**
     * Create exception with error type and message.
     *
     * @param errorType Type of error
     * @param message   Detailed error message
     */
    public MemoryLoggerException(ErrorType errorType, String message) {
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
    public MemoryLoggerException(ErrorType errorType, String message, Throwable cause) {
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
    public static MemoryLoggerException configurationError(String message) {
        return new MemoryLoggerException(ErrorType.CONFIGURATION_ERROR, message);
    }

    /**
     * Create configuration error exception with cause.
     *
     * @param message Error message
     * @param cause   Cause of the error
     * @return Exception instance
     */
    public static MemoryLoggerException configurationError(String message, Throwable cause) {
        return new MemoryLoggerException(ErrorType.CONFIGURATION_ERROR, message, cause);
    }

    /**
     * Create log write error exception.
     *
     * @param message Error message
     * @return Exception instance
     */
    public static MemoryLoggerException logWriteError(String message) {
        return new MemoryLoggerException(ErrorType.LOG_WRITE_ERROR, message);
    }

    /**
     * Create log write error exception with cause.
     *
     * @param message Error message
     * @param cause   Cause of the error
     * @return Exception instance
     */
    public static MemoryLoggerException logWriteError(String message, Throwable cause) {
        return new MemoryLoggerException(ErrorType.LOG_WRITE_ERROR, message, cause);
    }

    /**
     * Create audit log error exception.
     *
     * @param message Error message
     * @return Exception instance
     */
    public static MemoryLoggerException auditLogError(String message) {
        return new MemoryLoggerException(ErrorType.AUDIT_LOG_ERROR, message);
    }

    /**
     * Create initialization error exception.
     *
     * @param message Error message
     * @return Exception instance
     */
    public static MemoryLoggerException initializationError(String message) {
        return new MemoryLoggerException(ErrorType.INITIALIZATION_ERROR, message);
    }

    /**
     * Create file write error exception.
     *
     * @param message Error message
     * @return Exception instance
     */
    public static MemoryLoggerException fileWriteError(String message) {
        return new MemoryLoggerException(ErrorType.FILE_WRITE_ERROR, message);
    }

    /**
     * Create async queue full error exception.
     *
     * @param message Error message
     * @return Exception instance
     */
    public static MemoryLoggerException asyncQueueFull(String message) {
        return new MemoryLoggerException(ErrorType.ASYNC_QUEUE_FULL, message);
    }
}
