package tech.yesboss.memory.cache;

/**
 * Memory Cache Exception
 *
 * <p>Exception thrown when cache operations fail.</p>
 *
 * <p><b>Error Types:</b></p>
 * <ul>
 *   <li>CONFIGURATION_ERROR: Invalid cache configuration</li>
 *   <li>LOAD_ERROR: Error loading value into cache</li>
 *   <li>EVICTION_ERROR: Error during cache eviction</li>
 *   <li>INVALIDATION_ERROR: Error during cache invalidation</li>
 * </ul>
 *
 * @author YesBoss Team
 * @version 1.0
 */
public class MemoryCacheException extends RuntimeException {

    /**
     * Error type enumeration
     */
    public enum ErrorType {
        CONFIGURATION_ERROR("Invalid cache configuration"),
        LOAD_ERROR("Error loading value into cache"),
        EVICTION_ERROR("Error during cache eviction"),
        INVALIDATION_ERROR("Error during cache invalidation"),
        INITIALIZATION_ERROR("Error initializing cache"),
        TIMEOUT_ERROR("Cache operation timed out"),
        CAPACITY_ERROR("Cache capacity exceeded");

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
    public MemoryCacheException(String message) {
        super(message);
        this.errorType = null;
    }

    /**
     * Create exception with message and cause.
     *
     * @param message Error message
     * @param cause   Cause of the error
     */
    public MemoryCacheException(String message, Throwable cause) {
        super(message, cause);
        this.errorType = null;
    }

    /**
     * Create exception with error type.
     *
     * @param errorType Type of error
     */
    public MemoryCacheException(ErrorType errorType) {
        super(errorType.getDescription());
        this.errorType = errorType;
    }

    /**
     * Create exception with error type and message.
     *
     * @param errorType Type of error
     * @param message   Detailed error message
     */
    public MemoryCacheException(ErrorType errorType, String message) {
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
    public MemoryCacheException(ErrorType errorType, String message, Throwable cause) {
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
    public static MemoryCacheException configurationError(String message) {
        return new MemoryCacheException(ErrorType.CONFIGURATION_ERROR, message);
    }

    /**
     * Create configuration error exception with cause.
     *
     * @param message Error message
     * @param cause   Cause of the error
     * @return Exception instance
     */
    public static MemoryCacheException configurationError(String message, Throwable cause) {
        return new MemoryCacheException(ErrorType.CONFIGURATION_ERROR, message, cause);
    }

    /**
     * Create load error exception.
     *
     * @param message Error message
     * @return Exception instance
     */
    public static MemoryCacheException loadError(String message) {
        return new MemoryCacheException(ErrorType.LOAD_ERROR, message);
    }

    /**
     * Create load error exception with cause.
     *
     * @param message Error message
     * @param cause   Cause of the error
     * @return Exception instance
     */
    public static MemoryCacheException loadError(String message, Throwable cause) {
        return new MemoryCacheException(ErrorType.LOAD_ERROR, message, cause);
    }

    /**
     * Create eviction error exception.
     *
     * @param message Error message
     * @return Exception instance
     */
    public static MemoryCacheException evictionError(String message) {
        return new MemoryCacheException(ErrorType.EVICTION_ERROR, message);
    }

    /**
     * Create initialization error exception.
     *
     * @param message Error message
     * @return Exception instance
     */
    public static MemoryCacheException initializationError(String message) {
        return new MemoryCacheException(ErrorType.INITIALIZATION_ERROR, message);
    }

    /**
     * Create timeout error exception.
     *
     * @param message Error message
     * @return Exception instance
     */
    public static MemoryCacheException timeoutError(String message) {
        return new MemoryCacheException(ErrorType.TIMEOUT_ERROR, message);
    }

    /**
     * Create capacity error exception.
     *
     * @param message Error message
     * @return Exception instance
     */
    public static MemoryCacheException capacityError(String message) {
        return new MemoryCacheException(ErrorType.CAPACITY_ERROR, message);
    }
}
