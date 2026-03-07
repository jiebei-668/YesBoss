package tech.yesboss.memory.api;

/**
 * Memory API Exception
 *
 * <p>Exception thrown by the Memory API layer for API-related errors.
 * This includes validation errors, not found errors, and service integration errors.</p>
 *
 * <p>Error Codes:</p>
 * <ul>
 *   <li>400: Bad Request - Invalid input parameters</li>
 *   <li>404: Not Found - Resource not found</li>
 *   *   <li>500: Internal Server Error - Service error</li>
 *   <li>503: Service Unavailable - Service not available</li>
 * </ul>
 */
public class MemoryApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final int httpStatusCode;

    public enum ErrorCode {
        BAD_REQUEST("BAD_REQUEST", 400),
        NOT_FOUND("NOT_FOUND", 404),
        SERVICE_ERROR("SERVICE_ERROR", 500),
        SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", 503),
        VALIDATION_ERROR("VALIDATION_ERROR", 400),
        TIMEOUT("TIMEOUT", 504);

        private final String code;
        private final int httpStatus;

        ErrorCode(String code, int httpStatus) {
            this.code = code;
            this.httpStatus = httpStatus;
        }

        public String getCode() {
            return code;
        }

        public int getHttpStatus() {
            return httpStatus;
        }
    }

    public MemoryApiException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatusCode = errorCode.getHttpStatus();
    }

    public MemoryApiException(String message, ErrorCode errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatusCode = errorCode.getHttpStatus();
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public static MemoryApiException badRequest(String message) {
        return new MemoryApiException(message, ErrorCode.BAD_REQUEST);
    }

    public static MemoryApiException notFound(String message) {
        return new MemoryApiException(message, ErrorCode.NOT_FOUND);
    }

    public static MemoryApiException serviceError(String message, Throwable cause) {
        return new MemoryApiException(message, ErrorCode.SERVICE_ERROR, cause);
    }

    public static MemoryApiException serviceUnavailable(String message) {
        return new MemoryApiException(message, ErrorCode.SERVICE_UNAVAILABLE);
    }

    public static MemoryApiException validationError(String message) {
        return new MemoryApiException(message, ErrorCode.VALIDATION_ERROR);
    }

    public static MemoryApiException timeout(String message) {
        return new MemoryApiException(message, ErrorCode.TIMEOUT);
    }
}
