package tech.yesboss.memory.trigger;

/**
 * Exception thrown when TriggerService operations fail.
 *
 * <p>This exception indicates errors during trigger condition checking,
 * message discovery, or memory extraction triggering.</p>
 */
public class TriggerServiceException extends RuntimeException {

    private final String errorCode;

    /**
     * Create a new TriggerService exception.
     *
     * @param message The error message
     */
    public TriggerServiceException(String message) {
        super(message);
        this.errorCode = "UNKNOWN";
    }

    /**
     * Create a new TriggerService exception with error code.
     *
     * @param message The error message
     * @param errorCode The error code for categorization
     */
    public TriggerServiceException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Create a new TriggerService exception with cause.
     *
     * @param message The error message
     * @param cause The underlying cause
     */
    public TriggerServiceException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "UNKNOWN";
    }

    /**
     * Create a new TriggerService exception with all parameters.
     *
     * @param message The error message
     * @param errorCode The error code for categorization
     * @param cause The underlying cause
     */
    public TriggerServiceException(String message, String errorCode, Throwable cause) {
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
    public static final String ERROR_TRIGGER_CHECK_FAILED = "TRIGGER_CHECK_FAILED";
    public static final String ERROR_MESSAGE_DISCOVERY_FAILED = "MESSAGE_DISCOVERY_FAILED";
    public static final String ERROR_EXTRACTION_FAILED = "EXTRACTION_FAILED";
    public static final String ERROR_BATCH_OPERATION_FAILED = "BATCH_OPERATION_FAILED";
    public static final String ERROR_STATE_UPDATE_FAILED = "STATE_UPDATE_FAILED";
    public static final String ERROR_SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    public static final String ERROR_TIMEOUT = "TIMEOUT";
}
