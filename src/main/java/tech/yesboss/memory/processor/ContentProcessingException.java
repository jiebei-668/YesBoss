package tech.yesboss.memory.processor;

/**
 * Exception thrown when content processing operations fail.
 *
 * <p>This exception indicates errors during conversation processing,
 * memory extraction, or content analysis.</p>
 */
public class ContentProcessingException extends RuntimeException {

    private final String errorCode;

    /**
     * Create a new content processing exception.
     *
     * @param message The error message
     */
    public ContentProcessingException(String message) {
        super(message);
        this.errorCode = "UNKNOWN";
    }

    /**
     * Create a new content processing exception with error code.
     *
     * @param message The error message
     * @param errorCode The error code for categorization
     */
    public ContentProcessingException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Create a new content processing exception with cause.
     *
     * @param message The error message
     * @param cause The underlying cause
     */
    public ContentProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "UNKNOWN";
    }

    /**
     * Create a new content processing exception with all parameters.
     *
     * @param message The error message
     * @param errorCode The error code for categorization
     * @param cause The underlying cause
     */
    public ContentProcessingException(String message, String errorCode, Throwable cause) {
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
    public static final String ERROR_LLM_FAILURE = "LLM_FAILURE";
    public static final String ERROR_PARSING_FAILURE = "PARSING_FAILURE";
    public static final String ERROR_SEGMENTATION_FAILURE = "SEGMENTATION_FAILURE";
    public static final String ERROR_EXTRACTION_FAILURE = "EXTRACTION_FAILURE";
    public static final String ERROR_CLASSIFICATION_FAILURE = "CLASSIFICATION_FAILURE";
    public static final String ERROR_TIMEOUT = "TIMEOUT";
    public static final String ERROR_RATE_LIMIT = "RATE_LIMIT";
}
