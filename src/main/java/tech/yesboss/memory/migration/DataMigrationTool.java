package tech.yesboss.memory.migration;

import tech.yesboss.memory.model.Resource;
import tech.yesboss.memory.model.Snippet;
import tech.yesboss.memory.model.Preference;

import java.util.List;

/**
 * Data Migration Tool Interface
 *
 * <p>This interface provides methods for migrating historical data into the memory
 * persistence system. It supports migration from various sources and formats.</p>
 *
 * <p>Key Features:</p>
 * <ul>
 *   <li>Migration from legacy data sources</li>
 *   <li>Data validation and cleaning</li>
 *   <li>Batch processing for large datasets</li>
 *   <li>Rollback support</li>
 *   <li>Progress tracking and monitoring</li>
 * </ul>
 */
public interface DataMigrationTool {

    // ==================== Migration Operations ====================

    /**
     * Migrate data from a legacy data source.
     *
     * @param request Migration request with source configuration
     * @return Migration result with statistics
     * @throws DataMigrationException if migration fails
     */
    MigrationResult migrateFromLegacySource(MigrationRequest request);

    /**
     * Migrate resources from JSON file.
     *
     * @param jsonFilePath Path to JSON file containing resources
     * @return Migration result with statistics
     * @throws DataMigrationException if migration fails
     */
    MigrationResult migrateResourcesFromJson(String jsonFilePath);

    /**
     * Migrate snippets from JSON file.
     *
     * @param jsonFilePath Path to JSON file containing snippets
     * @return Migration result with statistics
     * @throws DataMigrationException if migration fails
     */
    MigrationResult migrateSnippetsFromJson(String jsonFilePath);

    /**
     * Migrate preferences from JSON file.
     *
     * @param jsonFilePath Path to JSON file containing preferences
     * @return Migration result with statistics
     * @throws DataMigrationException if migration fails
     */
    MigrationResult migratePreferencesFromJson(String jsonFilePath);

    /**
     * Migrate all data from a directory.
     *
     * @param directoryPath Path to directory containing data files
     * @return Migration result with statistics
     * @throws DataMigrationException if migration fails
     */
    MigrationResult migrateFromDirectory(String directoryPath);

    // ==================== Batch Migration ====================

    /**
     * Batch migrate data with chunking.
     *
     * @param request Batch migration request
     * @return Batch migration result with statistics
     * @throws DataMigrationException if migration fails
     */
    BatchMigrationResult batchMigrate(BatchMigrationRequest request);

    // ==================== Validation ====================

    /**
     * Validate data before migration.
     *
     * @param data Data to validate
     * @return Validation result with issues found
     */
    ValidationResult validateResources(List<Resource> data);

    /**
     * Validate snippets before migration.
     *
     * @param data Snippets to validate
     * @return Validation result with issues found
     */
    ValidationResult validateSnippets(List<Snippet> data);

    /**
     * Validate preferences before migration.
     *
     * @param data Preferences to validate
     * @return Validation result with issues found
     */
    ValidationResult validatePreferences(List<Preference> data);

    // ==================== Rollback ====================

    /**
     * Rollback a migration.
     *
     * @param migrationId Migration ID to rollback
     * @return Rollback result
     * @throws DataMigrationException if rollback fails
     */
    RollbackResult rollbackMigration(String migrationId);

    /**
     * Get migration history.
     *
     * @return List of past migrations
     */
    List<MigrationRecord> getMigrationHistory();

    // ==================== Progress Tracking ====================

    /**
     * Get migration progress.
     *
     * @param migrationId Migration ID
     * @return Migration progress
     */
    MigrationProgress getMigrationProgress(String migrationId);

    // ==================== Health & Status ====================

    /**
     * Check if migration tool is available.
     *
     * @return true if available, false otherwise
     */
    boolean isAvailable();

    // ==================== Request/Response DTOs ====================

    /**
     * Migration request
     */
    record MigrationRequest(
        String sourceType,
        String sourcePath,
        MigrationConfig config,
        boolean validateBeforeMigration,
        boolean enableRollback
    ) {
        public MigrationRequest {
            if (sourceType == null || sourceType.isBlank()) {
                throw new IllegalArgumentException("Source type cannot be null or blank");
            }
            if (sourcePath == null || sourcePath.isBlank()) {
                throw new IllegalArgumentException("Source path cannot be null or blank");
            }
        }
    }

    /**
     * Migration configuration
     */
    record MigrationConfig(
        int batchSize,
        int maxRetries,
        long timeoutMs,
        boolean continueOnError,
        boolean generateEmbeddings,
        boolean dryRun
    ) {
        public MigrationConfig {
            if (batchSize <= 0) {
                throw new IllegalArgumentException("Batch size must be positive");
            }
            if (maxRetries < 0) {
                throw new IllegalArgumentException("Max retries cannot be negative");
            }
        }
    }

    /**
     * Migration result
     */
    record MigrationResult(
        String migrationId,
        int totalCount,
        int successCount,
        int failureCount,
        List<String> errors,
        long processingTimeMs,
        MigrationStatus status
    ) {}

    /**
     * Batch migration request
     */
    record BatchMigrationRequest(
        List<MigrationRequest> requests,
        int parallelism
    ) {
        public BatchMigrationRequest {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Requests cannot be null or empty");
            }
            if (parallelism <= 0) {
                throw new IllegalArgumentException("Parallelism must be positive");
            }
        }
    }

    /**
     * Batch migration result
     */
    record BatchMigrationResult(
        List<MigrationResult> results,
        int totalRequests,
        int successCount,
        int failureCount,
        long totalProcessingTimeMs
    ) {}

    /**
     * Validation result
     */
    record ValidationResult(
        boolean valid,
        List<ValidationError> errors,
        List<ValidationWarning> warnings
    ) {}

    /**
     * Validation error
     */
    record ValidationError(
        String field,
        String message,
        String itemId
    ) {}

    /**
     * Validation warning
     */
    record ValidationWarning(
        String field,
        String message,
        String itemId
    ) {}

    /**
     * Rollback result
     */
    record RollbackResult(
        String migrationId,
        boolean success,
        int itemsRolledBack,
        List<String> errors,
        long processingTimeMs
    ) {}

    /**
     * Migration record
     */
    record MigrationRecord(
        String migrationId,
        String sourceType,
        String sourcePath,
        long timestamp,
        int totalCount,
        int successCount,
        int failureCount,
        MigrationStatus status
    ) {}

    /**
     * Migration progress
     */
    record MigrationProgress(
        String migrationId,
        int currentStep,
        int totalSteps,
        int itemsProcessed,
        int totalItems,
        double progressPercentage,
        String currentOperation
    ) {}

    /**
     * Migration status
     */
    enum MigrationStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        ROLLED_BACK
    }
}
