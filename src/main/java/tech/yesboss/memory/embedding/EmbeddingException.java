package tech.yesboss.memory.embedding;

/**
 * Exception thrown when embedding operations fail.
 *
 * <p>This exception indicates errors during text embedding generation,
 * including API failures, invalid input, or configuration issues.</p>
 */
public class EmbeddingException extends RuntimeException {

    private final String errorCode;

    /**
     * Create a new embedding exception.
     *
     * @param message The error message
     */
    public EmbeddingException(String message) {
        super(message);
        this.errorCode = "UNKNOWN";
    }

    /**
     * Create a new embedding exception with error code.
     *
     * @param message The error message
     * @param errorCode The error code for categorization
     */
    public EmbeddingException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Create a new embedding exception with cause.
     *
     * @param message The error message
     * @param cause The underlying cause
     */
    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "UNKNOWN";
    }

    /**
     * Create a new embedding exception with all parameters.
     *
     * @param message The error message
     * @param errorCode The error code for categorization
     * @param cause The underlying cause
     */
    public EmbeddingException(String message, String errorCode, Throwable cause) {
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
    public static final String ERROR_API_FAILURE = "API_FAILURE";
    public static final String ERROR_RATE_LIMIT = "RATE_LIMIT";
    public static final String ERROR_TIMEOUT = "TIMEOUT";
    public static final String ERROR_CONFIGURATION = "CONFIGURATION";
    public static final String ERROR_SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
}
