package tech.yesboss.memory.error;

/**
 * Base exception for memory persistence operations.
 *
 * <p><b>Error Types:</b></p>
 * <ul>
 *   <li>VALIDATION_ERROR: Input validation failed</li>
 *   <li>NOT_FOUND_ERROR: Requested resource not found</li>
 *   <li>CONFLICT_ERROR: Resource conflict (duplicate, version mismatch)</li>
 *   <li>CONSTRAINT_ERROR: Database constraint violation</li>
 *   <li>TIMEOUT_ERROR: Operation timed out</li>
 *   <li>CONNECTION_ERROR: Database connection error</li>
 *   <li>QUERY_ERROR: Database query error</li>
 *   <li>VECTOR_ERROR: Vector store error</li>
 *   <li>LLM_ERROR: LLM service error</li>
 *   <li>UNKNOWN_ERROR: Unknown error</li>
 * </ul>
 *
 * @author YesBoss Team
 * @version 1.0
 */
public class MemoryException extends RuntimeException {

    /**
     * Error type enumeration
     */
    public enum ErrorType {
        VALIDATION_ERROR("Input validation failed"),
        NOT_FOUND_ERROR("Requested resource not found"),
        CONFLICT_ERROR("Resource conflict"),
        CONSTRAINT_ERROR("Database constraint violation"),
        TIMEOUT_ERROR("Operation timed out"),
        CONNECTION_ERROR("Database connection error"),
        QUERY_ERROR("Database query error"),
        VECTOR_ERROR("Vector store error"),
        LLM_ERROR("LLM service error"),
        PERMISSION_ERROR("Permission denied"),
        RESOURCE_EXHAUSTED_ERROR("Resource exhausted"),
        UNKNOWN_ERROR("Unknown error");

        private final String description;

        ErrorType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    private final ErrorType errorType;
    private final boolean retryable;
    private final String errorCode;

    /**
     * Create exception with message.
     *
     * @param message Error message
     */
    public MemoryException(String message) {
        super(message);
        this.errorType = ErrorType.UNKNOWN_ERROR;
        this.retryable = false;
        this.errorCode = null;
    }

    /**
     * Create exception with message and cause.
     *
     * @param message Error message
     * @param cause Cause of the error
     */
    public MemoryException(String message, Throwable cause) {
        super(message, cause);
        this.errorType = ErrorType.UNKNOWN_ERROR;
        this.retryable = false;
        this.errorCode = null;
    }

    /**
     * Create exception with error type.
     *
     * @param errorType Type of error
     */
    public MemoryException(ErrorType errorType) {
        super(errorType.getDescription());
        this.errorType = errorType;
        this.retryable = isDefaultRetryable(errorType);
        this.errorCode = errorType.name();
    }

    /**
     * Create exception with error type and message.
     *
     * @param errorType Type of error
     * @param message Detailed error message
     */
    public MemoryException(ErrorType errorType, String message) {
        super(errorType.getDescription() + ": " + message);
        this.errorType = errorType;
        this.retryable = isDefaultRetryable(errorType);
        this.errorCode = errorType.name();
    }

    /**
     * Create exception with error type, message, and cause.
     *
     * @param errorType Type of error
     * @param message Detailed error message
     * @param cause Cause of the error
     */
    public MemoryException(ErrorType errorType, String message, Throwable cause) {
        super(errorType.getDescription() + ": " + message, cause);
        this.errorType = errorType;
        this.retryable = isDefaultRetryable(errorType);
        this.errorCode = errorType.name();
    }

    /**
     * Create exception with full details.
     *
     * @param errorType Type of error
     * @param message Detailed error message
     * @param cause Cause of the error
     * @param retryable Whether the error is retryable
     */
    public MemoryException(ErrorType errorType, String message, Throwable cause, boolean retryable) {
        super(errorType.getDescription() + ": " + message, cause);
        this.errorType = errorType;
        this.retryable = retryable;
        this.errorCode = errorType.name();
    }

    /**
     * Determine if error type is retryable by default.
     *
     * @param errorType Error type
     * @return true if retryable
     */
    private static boolean isDefaultRetryable(ErrorType errorType) {
        switch (errorType) {
            case TIMEOUT_ERROR:
            case CONNECTION_ERROR:
            case LLM_ERROR:
            case RESOURCE_EXHAUSTED_ERROR:
                return true;
            default:
                return false;
        }
    }

    /**
     * Get the error type.
     *
     * @return Error type
     */
    public ErrorType getErrorType() {
        return errorType;
    }

    /**
     * Check if error is retryable.
     *
     * @return true if retryable
     */
    public boolean isRetryable() {
        return retryable;
    }

    /**
     * Get the error code.
     *
     * @return Error code
     */
    public String getErrorCode() {
        return errorCode;
    }

    // ==================== Static Factory Methods ====================

    public static MemoryException validationError(String message) {
        return new MemoryException(ErrorType.VALIDATION_ERROR, message);
    }

    public static MemoryException validationError(String message, Throwable cause) {
        return new MemoryException(ErrorType.VALIDATION_ERROR, message, cause);
    }

    public static MemoryException notFoundError(String message) {
        return new MemoryException(ErrorType.NOT_FOUND_ERROR, message);
    }

    public static MemoryException conflictError(String message) {
        return new MemoryException(ErrorType.CONFLICT_ERROR, message);
    }

    public static MemoryException constraintError(String message) {
        return new MemoryException(ErrorType.CONSTRAINT_ERROR, message);
    }

    public static MemoryException constraintError(String message, Throwable cause) {
        return new MemoryException(ErrorType.CONSTRAINT_ERROR, message, cause);
    }

    public static MemoryException timeoutError(String message) {
        return new MemoryException(ErrorType.TIMEOUT_ERROR, message);
    }

    public static MemoryException timeoutError(String message, Throwable cause) {
        return new MemoryException(ErrorType.TIMEOUT_ERROR, message, cause);
    }

    public static MemoryException connectionError(String message) {
        return new MemoryException(ErrorType.CONNECTION_ERROR, message);
    }

    public static MemoryException connectionError(String message, Throwable cause) {
        return new MemoryException(ErrorType.CONNECTION_ERROR, message, cause);
    }

    public static MemoryException queryError(String message) {
        return new MemoryException(ErrorType.QUERY_ERROR, message);
    }

    public static MemoryException queryError(String message, Throwable cause) {
        return new MemoryException(ErrorType.QUERY_ERROR, message, cause);
    }

    public static MemoryException vectorError(String message) {
        return new MemoryException(ErrorType.VECTOR_ERROR, message);
    }

    public static MemoryException vectorError(String message, Throwable cause) {
        return new MemoryException(ErrorType.VECTOR_ERROR, message, cause);
    }

    public static MemoryException llmError(String message) {
        return new MemoryException(ErrorType.LLM_ERROR, message);
    }

    public static MemoryException llmError(String message, Throwable cause) {
        return new MemoryException(ErrorType.LLM_ERROR, message, cause);
    }

    public static MemoryException permissionError(String message) {
        return new MemoryException(ErrorType.PERMISSION_ERROR, message);
    }

    public static MemoryException resourceExhaustedError(String message) {
        return new MemoryException(ErrorType.RESOURCE_EXHAUSTED_ERROR, message);
    }

    public static MemoryException wrap(String message, Throwable cause) {
        if (cause instanceof MemoryException) {
            return (MemoryException) cause;
        }
        return new MemoryException(message, cause);
    }

    @Override
    public String toString() {
        return "MemoryException{" +
                "errorType=" + errorType +
                ", errorCode='" + errorCode + '\'' +
                ", retryable=" + retryable +
                ", message='" + getMessage() + '\'' +
                '}';
    }
}
