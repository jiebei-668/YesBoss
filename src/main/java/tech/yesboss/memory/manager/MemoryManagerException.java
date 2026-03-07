package tech.yesboss.memory.manager;

/**
 * Exception thrown when MemoryManager operations fail.
 *
 * <p>This exception indicates errors during three-layer memory management,
 * including data persistence failures, vectorization errors, or association issues.</p>
 */
public class MemoryManagerException extends RuntimeException {

    private final String errorCode;

    /**
     * Create a new MemoryManager exception.
     *
     * @param message The error message
     */
    public MemoryManagerException(String message) {
        super(message);
        this.errorCode = "UNKNOWN";
    }

    /**
     * Create a new MemoryManager exception with error code.
     *
     * @param message The error message
     * @param errorCode The error code for categorization
     */
    public MemoryManagerException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Create a new MemoryManager exception with cause.
     *
     * @param message The error message
     * @param cause The underlying cause
     */
    public MemoryManagerException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "UNKNOWN";
    }

    /**
     * Create a new MemoryManager exception with all parameters.
     *
     * @param message The error message
     * @param errorCode The error code for categorization
     * @param cause The underlying cause
     */
    public MemoryManagerException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Get the error code.
     *
     * @return The error code
     */
    public String getErrorCode() {
        return errorCode;
    }

    // Common error codes
    public static final String ERROR_INVALID_INPUT = "INVALID_INPUT";
    public static final String ERROR_PERSISTENCE_FAILURE = "PERSISTENCE_FAILURE";
    public static final String ERROR_VECTORIZATION_FAILURE = "VECTORIZATION_FAILURE";
    public static final String ERROR_ASSOCIATION_FAILURE = "ASSOCIATION_FAILURE";
    public static final String ERROR_BATCH_OPERATION_FAILURE = "BATCH_OPERATION_FAILURE";
    public static final String ERROR_TIMEOUT = "TIMEOUT";
    public static final String ERROR_CONFIGURATION = "CONFIGURATION";
    public static final String ERROR_SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
}
