package tech.yesboss.memory.migration;

/**
 * Data Migration Exception
 *
 * <p>Exception thrown when data migration operations fail.</p>
 *
 * <p>Error Types:</p>
 * <ul>
 *   <li>INVALID_SOURCE - Source data is invalid or inaccessible</li>
 *   <li>VALIDATION_FAILED - Data validation failed</li>
 *   <li>MIGRATION_FAILED - Migration operation failed</li>
 *   <li>ROLLBACK_FAILED - Rollback operation failed</li>
 *   <li>TIMEOUT - Operation timed out</li>
 * </ul>
 */
public class DataMigrationException extends RuntimeException {

    private final ErrorType errorType;

    public enum ErrorType {
        INVALID_SOURCE("INVALID_SOURCE", 400),
        VALIDATION_FAILED("VALIDATION_FAILED", 400),
        MIGRATION_FAILED("MIGRATION_FAILED", 500),
        ROLLBACK_FAILED("ROLLBACK_FAILED", 500),
        TIMEOUT("TIMEOUT", 504);

        private final String code;
        private final int httpStatus;

        ErrorType(String code, int httpStatus) {
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

    public DataMigrationException(String message, ErrorType errorType) {
        super(message);
        this.errorType = errorType;
    }

    public DataMigrationException(String message, ErrorType errorType, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    // Factory methods for common errors
    public static DataMigrationException invalidSource(String message) {
        return new DataMigrationException(message, ErrorType.INVALID_SOURCE);
    }

    public static DataMigrationException validationFailed(String message) {
        return new DataMigrationException(message, ErrorType.VALIDATION_FAILED);
    }

    public static DataMigrationException migrationFailed(String message, Throwable cause) {
        return new DataMigrationException(message, ErrorType.MIGRATION_FAILED, cause);
    }

    public static DataMigrationException rollbackFailed(String message, Throwable cause) {
        return new DataMigrationException(message, ErrorType.ROLLBACK_FAILED, cause);
    }

    public static DataMigrationException timeout(String message) {
        return new DataMigrationException(message, ErrorType.TIMEOUT);
    }
}
