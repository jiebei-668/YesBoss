package tech.yesboss.memory.service;

/**
 * Exception thrown when MemoryService operations fail.
 *
 * <p>This exception indicates errors during memory extraction, processing,
 * or management, including LLM processing failures, data validation errors,
 * or coordination issues between components.</p>
 */
public class MemoryServiceException extends RuntimeException {

    private final String errorCode;

    /**
     * Create a new MemoryService exception.
     *
     * @param message The error message
     */
    public MemoryServiceException(String message) {
        super(message);
        this.errorCode = "UNKNOWN";
    }

    /**
     * Create a new MemoryService exception with error code.
     *
     * @param message The error message
     * @param errorCode The error code for categorization
     */
    public MemoryServiceException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Create a new MemoryService exception with cause.
     *
     * @param message The error message
     * @param cause The underlying cause
     */
    public MemoryServiceException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "UNKNOWN";
    }

    /**
     * Create a new MemoryService exception with all parameters.
     *
     * @param message The error message
     * @param errorCode The error code for categorization
     * @param cause The underlying cause
     */
    public MemoryServiceException(String message, String errorCode, Throwable cause) {
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
    public static final String ERROR_PROCESSING_FAILURE = "PROCESSING_FAILURE";
    public static final String ERROR_SEGMENTATION_FAILURE = "SEGMENTATION_FAILURE";
    public static final String ERROR_EXTRACTION_FAILURE = "EXTRACTION_FAILURE";
    public static final String ERROR_ASSOCIATION_FAILURE = "ASSOCIATION_FAILURE";
    public static final String ERROR_PREFERENCE_UPDATE_FAILURE = "PREFERENCE_UPDATE_FAILURE";
    public static final String ERROR_LLM_FAILURE = "LLM_FAILURE";
    public static final String ERROR_BATCH_OPERATION_FAILURE = "BATCH_OPERATION_FAILURE";
    public static final String ERROR_SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    public static final String ERROR_TIMEOUT = "TIMEOUT";
}
