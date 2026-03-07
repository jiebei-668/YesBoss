package tech.yesboss.memory.cache;

/**
 * Exception thrown when cache operations fail.
 *
 * <p>This exception indicates errors during cache operations such as:
 * <ul>
 *   <li>Cache initialization failures</li>
 *   <li>Serialization/deserialization errors</li>
 *   <li>Cache backend connectivity issues</li>
 *   <li>Invalid cache configurations</li>
 * </ul>
 */
public class CacheException extends RuntimeException {

    /** Error code for cache initialization failures */
    public static final String ERROR_INITIALIZATION = "CACHE_INITIALIZATION_ERROR";

    /** Error code for serialization errors */
    public static final String ERROR_SERIALIZATION = "CACHE_SERIALIZATION_ERROR";

    /** Error code for backend connectivity issues */
    public static final String ERROR_BACKEND = "CACHE_BACKEND_ERROR";

    /** Error code for invalid configuration */
    public static final String ERROR_CONFIGURATION = "CACHE_CONFIGURATION_ERROR";

    /** Error code for operation timeout */
    public static final String ERROR_TIMEOUT = "CACHE_TIMEOUT_ERROR";

    /** Error code for eviction failures */
    public static final String ERROR_EVICTION = "CACHE_EVICTION_ERROR";

    private final String errorCode;

    /**
     * Create a new cache exception with a message.
     *
     * @param message the error message
     */
    public CacheException(String message) {
        super(message);
        this.errorCode = "UNKNOWN_ERROR";
    }

    /**
     * Create a new cache exception with a message and error code.
     *
     * @param message the error message
     * @param errorCode the error code
     */
    public CacheException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Create a new cache exception with a message and cause.
     *
     * @param message the error message
     * @param cause the cause of the exception
     */
    public CacheException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "UNKNOWN_ERROR";
    }

    /**
     * Create a new cache exception with a message, cause, and error code.
     *
     * @param message the error message
     * @param cause the cause of the exception
     * @param errorCode the error code
     */
    public CacheException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Get the error code.
     *
     * @return the error code
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Create a cache initialization exception.
     *
     * @param message the error message
     * @return CacheException with initialization error code
     */
    public static CacheException initializationError(String message) {
        return new CacheException(message, ERROR_INITIALIZATION);
    }

    /**
     * Create a cache initialization exception with cause.
     *
     * @param message the error message
     * @param cause the cause of the exception
     * @return CacheException with initialization error code
     */
    public static CacheException initializationError(String message, Throwable cause) {
        return new CacheException(message, cause, ERROR_INITIALIZATION);
    }

    /**
     * Create a serialization exception.
     *
     * @param message the error message
     * @return CacheException with serialization error code
     */
    public static CacheException serializationError(String message) {
        return new CacheException(message, ERROR_SERIALIZATION);
    }

    /**
     * Create a serialization exception with cause.
     *
     * @param message the error message
     * @param cause the cause of the exception
     * @return CacheException with serialization error code
     */
    public static CacheException serializationError(String message, Throwable cause) {
        return new CacheException(message, cause, ERROR_SERIALIZATION);
    }

    /**
     * Create a backend error exception.
     *
     * @param message the error message
     * @return CacheException with backend error code
     */
    public static CacheException backendError(String message) {
        return new CacheException(message, ERROR_BACKEND);
    }

    /**
     * Create a backend error exception with cause.
     *
     * @param message the error message
     * @param cause the cause of the exception
     * @return CacheException with backend error code
     */
    public static CacheException backendError(String message, Throwable cause) {
        return new CacheException(message, cause, ERROR_BACKEND);
    }

    /**
     * Create a configuration error exception.
     *
     * @param message the error message
     * @return CacheException with configuration error code
     */
    public static CacheException configurationError(String message) {
        return new CacheException(message, ERROR_CONFIGURATION);
    }

    /**
     * Create a timeout error exception.
     *
     * @param message the error message
     * @return CacheException with timeout error code
     */
    public static CacheException timeoutError(String message) {
        return new CacheException(message, ERROR_TIMEOUT);
    }

    /**
     * Create an eviction error exception.
     *
     * @param message the error message
     * @return CacheException with eviction error code
     */
    public static CacheException evictionError(String message) {
        return new CacheException(message, ERROR_EVICTION);
    }

    /**
     * Create an eviction error exception with cause.
     *
     * @param message the error message
     * @param cause the cause of the exception
     * @return CacheException with eviction error code
     */
    public static CacheException evictionError(String message, Throwable cause) {
        return new CacheException(message, cause, ERROR_EVICTION);
    }
}
