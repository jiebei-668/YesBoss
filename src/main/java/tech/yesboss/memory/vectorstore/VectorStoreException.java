package tech.yesboss.memory.vectorstore;

/**
 * Exception thrown when vector store operations fail.
 */
public class VectorStoreException extends RuntimeException {

    private final String errorCode;

    public VectorStoreException(String message) {
        super(message);
        this.errorCode = "VECTOR_STORE_ERROR";
    }

    public VectorStoreException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "VECTOR_STORE_ERROR";
    }

    public VectorStoreException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public VectorStoreException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    // Common error codes
    public static class ErrorCodes {
        public static final String CONNECTION_FAILED = "CONNECTION_FAILED";
        public static final String INSERT_FAILED = "INSERT_FAILED";
        public static final String UPDATE_FAILED = "UPDATE_FAILED";
        public static final String SEARCH_FAILED = "SEARCH_FAILED";
        public static final String DELETE_FAILED = "DELETE_FAILED";
        public static final String INVALID_VECTOR = "INVALID_VECTOR";
        public static final String VECTOR_NOT_FOUND = "VECTOR_NOT_FOUND";
        public static final String CONFIGURATION_ERROR = "CONFIGURATION_ERROR";
        public static final String TIMEOUT = "TIMEOUT";
    }
}
