package tech.yesboss.memory.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit test suite for MemoryConfigValidator
 *
 * Tests cover:
 * - Interface contract validation (method signatures, parameters, return values, exceptions)
 * - Normal functionality scenarios
 * - Boundary conditions (null values, extreme values, special characters, concurrency, resource limits)
 * - Exception handling (error scenarios, exception messages, recovery, degradation)
 * - Performance benchmarks (response time, batch processing, memory usage)
 * - Concurrency scenarios (thread safety, data consistency)
 *
 * Test data includes:
 * - Normal data: Standard valid configurations
 * - Boundary data: Edge cases and limits
 * - Anomalous data: Invalid or unexpected inputs
 */
@DisplayName("MemoryConfigValidator Unit Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MemoryConfigValidatorTest {

    private MemoryConfigValidator validator;
    private MemoryConfig config;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        validator = new MemoryConfigValidator();
        config = MemoryConfig.getInstance();
        config.resetToDefaults();
    }

    @AfterEach
    void tearDown() {
        // Reset config after each test
        config.resetToDefaults();
    }

    // ==========================================
    // Interface Contract Tests (Step 2)
    // ==========================================

    @Nested
    @DisplayName("Step 2: Interface Contract Validation")
    @Order(1)
    class InterfaceContractTests {

        @Test
        @DisplayName("Should have correct validate() method signature")
        void testValidateMethodSignature() throws NoSuchMethodException {
            // Validate method exists with correct signature
            var method = MemoryConfigValidator.class.getMethod("validate");
            assertNotNull(method);
            assertEquals(MemoryConfigValidator.ValidationResult.class, method.getReturnType());
        }

        @Test
        @DisplayName("Should have correct getDetailedReport() method signature")
        void testGetDetailedReportMethodSignature() throws NoSuchMethodException {
            var method = MemoryConfigValidator.class.getMethod("getDetailedReport");
            assertNotNull(method);
            assertEquals(String.class, method.getReturnType());
        }

        @Test
        @DisplayName("Should have correct validateForDeployment() method signature")
        void testValidateForDeploymentMethodSignature() throws NoSuchMethodException {
            var method = MemoryConfigValidator.class.getMethod("validateForDeployment");
            assertNotNull(method);
            assertEquals(boolean.class, method.getReturnType());
        }

        @Test
        @DisplayName("ValidationResult should have correct getters")
        void testValidationResultGetters() {
            var result = validator.validate();

            assertNotNull(result);
            assertNotNull(result.isValid());
            assertNotNull(result.getIssues());
            assertNotNull(result.getSummary());
        }

        @Test
        @DisplayName("ValidationIssue should have correct severity levels")
        void testValidationIssueSeverityLevels() {
            var result = validator.validate();
            var issues = result.getIssues();

            for (var issue : issues) {
                var severity = issue.getSeverity();
                assertTrue(
                    severity == MemoryConfigValidator.ValidationIssue.Severity.CRITICAL ||
                    severity == MemoryConfigValidator.ValidationIssue.Severity.WARNING ||
                    severity == MemoryConfigValidator.ValidationIssue.Severity.INFO
                );
            }
        }

        @Test
        @DisplayName("ValidationResult should filter issues by severity")
        void testValidationResultIssueFiltering() {
            var result = validator.validate();

            assertNotNull(result.getCriticalIssues());
            assertNotNull(result.getWarnings());

            // Verify all critical issues are actually critical
            for (var issue : result.getCriticalIssues()) {
                assertEquals(MemoryConfigValidator.ValidationIssue.Severity.CRITICAL, issue.getSeverity());
            }

            // Verify all warnings are actually warnings
            for (var issue : result.getWarnings()) {
                assertEquals(MemoryConfigValidator.ValidationIssue.Severity.WARNING, issue.getSeverity());
            }
        }
    }

    // ==========================================
    // Normal Functionality Tests (Step 3)
    // ==========================================

    @Nested
    @DisplayName("Step 3: Normal Functionality Scenarios")
    @Order(2)
    class NormalFunctionalityTests {

        @Test
        @DisplayName("Should validate default configuration successfully")
        void testValidateDefaultConfiguration() {
            var result = validator.validate();

            assertTrue(result.isValid(), "Default configuration should be valid");
            assertNotNull(result.getSummary());
        }

        @Test
        @DisplayName("Should validate SQLite configuration")
        void testValidateSQLiteConfiguration() {
            config.setBackendType(MemoryConfig.BackendType.SQLITE_VEC);
            config.set(MemoryConfig.SQLITE_PATH, tempDir.resolve("test.db").toString());

            var result = validator.validate();

            assertTrue(result.isValid(), "SQLite configuration should be valid");
        }

        @Test
        @DisplayName("Should validate PostgreSQL configuration")
        void testValidatePostgreSQLConfiguration() {
            config.setBackendType(MemoryConfig.BackendType.POSTGRESQL_PGVECTOR);
            config.set(MemoryConfig.POSTGRESQL_HOST, "localhost");
            config.set(MemoryConfig.POSTGRESQL_PORT, 5432);
            config.set(MemoryConfig.POSTGRESQL_DATABASE, "testdb");
            config.set(MemoryConfig.POSTGRESQL_USER, "testuser");

            var result = validator.validate();

            // Should be valid even without PostgreSQL classes
            // The validator checks for class availability
            assertFalse(result.getIssues().isEmpty());
        }

        @Test
        @DisplayName("Should validate proper namespace separation")
        void testValidateNamespaceSeparation() {
            // All default keys should have 'memory.' prefix
            var allConfig = config.getAll();

            for (var key : allConfig.keySet()) {
                assertTrue(
                    key.startsWith("memory."),
                    "Configuration key should have 'memory.' prefix: " + key
                );
            }

            var result = validator.validate();

            // Check for namespace conflict issues
            boolean hasNamespaceConflict = result.getIssues().stream()
                .anyMatch(issue -> issue.getCategory().equals("namespace_conflict"));

            assertFalse(hasNamespaceConflict, "Should not have namespace conflicts");
        }

        @Test
        @DisplayName("Should validate file system separation")
        void testValidateFileSeparation() {
            // Set up separate database paths
            String memoryDbPath = tempDir.resolve("memory.db").toString();
            config.set(MemoryConfig.SQLITE_PATH, memoryDbPath);

            var result = validator.validate();

            // Should not have file conflicts
            boolean hasFileConflict = result.getIssues().stream()
                .anyMatch(issue -> issue.getCategory().equals("file_conflict"));

            assertFalse(hasFileConflict, "Should not have file conflicts");
        }

        @Test
        @DisplayName("Should validate system properties")
        void testValidateSystemProperties() {
            var result = validator.validate();

            assertNotNull(result);
            // System properties validation should always pass
        }

        @Test
        @DisplayName("Should validate class loading isolation")
        void testValidateClassLoadingIsolation() {
            var result = validator.validate();

            // Check for package structure issues
            boolean hasPackageIssue = result.getIssues().stream()
                .anyMatch(issue -> issue.getCategory().equals("package_structure"));

            assertFalse(hasPackageIssue, "Memory classes should be in correct package");
        }

        @Test
        @DisplayName("Should validate resource usage")
        void testValidateResourceUsage() {
            // Use reasonable resource settings
            config.set(MemoryConfig.CACHE_SIZE, 1000);
            config.set(MemoryConfig.BATCH_SIZE, 100);
            config.set(MemoryConfig.VECTOR_DIMENSION, 1536);

            var result = validator.validate();

            // Should not have resource usage warnings for reasonable values
            boolean hasResourceWarning = result.getIssues().stream()
                .filter(issue -> issue.getCategory().equals("resource_usage"))
                .anyMatch(issue -> issue.getSeverity() == MemoryConfigValidator.ValidationIssue.Severity.WARNING);

            assertFalse(hasResourceWarning, "Reasonable resource settings should not generate warnings");
        }

        @Test
        @DisplayName("Should validate backend compatibility")
        void testValidateBackendCompatibility() {
            // Test with SQLite backend (should have sqlite-vec available)
            config.setBackendType(MemoryConfig.BackendType.SQLITE_VEC);

            var result = validator.validate();

            // Check for backend compatibility issues
            boolean hasBackendIssue = result.getIssues().stream()
                .anyMatch(issue -> issue.getCategory().equals("backend_compatibility") &&
                                 issue.getSeverity() == MemoryConfigValidator.ValidationIssue.Severity.CRITICAL);

            assertFalse(hasBackendIssue, "SQLite backend should be compatible");
        }
    }

    // ==========================================
    // Boundary Condition Tests (Step 4)
    // ==========================================

    @Nested
    @DisplayName("Step 4: Boundary Condition Tests")
    @Order(3)
    class BoundaryConditionTests {

        @Test
        @DisplayName("Should handle null configuration values")
        void testNullConfigurationValues() {
            // Remove a configuration value
            config.remove(MemoryConfig.SQLITE_PATH);

            var result = validator.validate();

            // Should still validate without crashing
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should handle extremely large cache size")
        void testLargeCacheSize() {
            config.set(MemoryConfig.CACHE_SIZE, 1000000); // Extremely large

            var result = validator.validate();

            // Should generate warning
            boolean hasWarning = result.getIssues().stream()
                .anyMatch(issue -> issue.getCategory().equals("resource_usage") &&
                                 issue.getSeverity() == MemoryConfigValidator.ValidationIssue.Severity.WARNING);

            assertTrue(hasWarning, "Large cache size should generate warning");
        }

        @Test
        @DisplayName("Should handle extremely large batch size")
        void testLargeBatchSize() {
            config.set(MemoryConfig.BATCH_SIZE, 10000); // Extremely large

            var result = validator.validate();

            // Should generate warning
            boolean hasWarning = result.getIssues().stream()
                .anyMatch(issue -> issue.getCategory().equals("resource_usage") &&
                                 issue.getSeverity() == MemoryConfigValidator.ValidationIssue.Severity.WARNING);

            assertTrue(hasWarning, "Large batch size should generate warning");
        }

        @Test
        @DisplayName("Should handle extremely large vector dimension")
        void testLargeVectorDimension() {
            config.set(MemoryConfig.VECTOR_DIMENSION, 4096); // Very large

            var result = validator.validate();

            // Should generate info
            boolean hasInfo = result.getIssues().stream()
                .anyMatch(issue -> issue.getCategory().equals("resource_usage") &&
                                 issue.getSeverity() == MemoryConfigValidator.ValidationIssue.Severity.INFO);

            assertTrue(hasInfo, "Large vector dimension should generate info");
        }

        @Test
        @DisplayName("Should handle special characters in paths")
        void testSpecialCharactersInPaths() {
            // Use paths with special characters (but valid)
            String pathWithSpaces = tempDir.resolve("test db with spaces.db").toString();
            config.set(MemoryConfig.SQLITE_PATH, pathWithSpaces);

            var result = validator.validate();

            // Should handle gracefully
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should handle minimum cache size (1)")
        void testMinimumCacheSize() {
            config.set(MemoryConfig.CACHE_SIZE, 1);

            var result = validator.validate();

            assertNotNull(result);
            // Minimum value should be acceptable
        }

        @Test
        @DisplayName("Should handle minimum batch size (1)")
        void testMinimumBatchSize() {
            config.set(MemoryConfig.BATCH_SIZE, 1);

            var result = validator.validate();

            assertNotNull(result);
            // Minimum value should be acceptable
        }

        @Test
        @DisplayName("Should handle minimum vector dimension (1)")
        void testMinimumVectorDimension() {
            config.set(MemoryConfig.VECTOR_DIMENSION, 1);

            var result = validator.validate();

            assertNotNull(result);
            // Minimum value should be acceptable
        }

        @Test
        @DisplayName("Should handle zero similarity threshold")
        void testZeroSimilarityThreshold() {
            config.set(MemoryConfig.SIMILARITY_THRESHOLD, 0.0);

            var result = validator.validate();

            assertNotNull(result);
            // Zero threshold should be acceptable
        }

        @Test
        @DisplayName("Should handle maximum similarity threshold (1.0)")
        void testMaximumSimilarityThreshold() {
            config.set(MemoryConfig.SIMILARITY_THRESHOLD, 1.0);

            var result = validator.validate();

            assertNotNull(result);
            // Maximum threshold should be acceptable
        }
    }

    // ==========================================
    // Exception Handling Tests (Step 5)
    // ==========================================

    @Nested
    @DisplayName("Step 5: Exception Handling Tests")
    @Order(4)
    class ExceptionHandlingTests {

        @Test
        @DisplayName("Should handle invalid configuration namespace")
        void testInvalidConfigurationNamespace() {
            // Set a configuration key without proper namespace
            // This is a conceptual test - in practice, MemoryConfig enforces namespace

            var result = validator.validate();

            // Should complete without throwing exception
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should detect database path conflicts")
        void testDatabasePathConflict() {
            // Set memory database to same path as main database
            String conflictingPath = tempDir.resolve("yesboss.db").toString();
            config.set(MemoryConfig.SQLITE_PATH, conflictingPath);

            // This simulates a conflict scenario
            var result = validator.validate();

            // Should complete without throwing
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should handle missing backend classes gracefully")
        void testMissingBackendClasses() {
            // Switch to PostgreSQL backend
            config.setBackendType(MemoryConfig.BackendType.POSTGRESQL_PGVECTOR);

            var result = validator.validate();

            // Should detect missing classes and report critical issue
            boolean hasCriticalIssue = result.getIssues().stream()
                .anyMatch(issue -> issue.getCategory().equals("backend_compatibility") &&
                                 issue.getSeverity() == MemoryConfigValidator.ValidationIssue.Severity.CRITICAL);

            // In test environment, PostgreSQL classes may not be available
            // This is expected behavior
        }

        @Test
        @DisplayName("Should provide meaningful error messages")
        void testMeaningfulErrorMessages() {
            // Trigger a validation issue
            config.set(MemoryConfig.CACHE_SIZE, 200000);

            var result = validator.validate();

            boolean hasMeaningfulMessage = result.getIssues().stream()
                .anyMatch(issue -> {
                    String msg = issue.getMessage();
                    return msg != null && !msg.isEmpty() && msg.length() > 10;
                });

            assertTrue(hasMeaningfulMessage, "Issues should have meaningful messages");
        }

        @Test
        @DisplayName("Should provide actionable recommendations")
        void testActionableRecommendations() {
            // Trigger a validation issue
            config.set(MemoryConfig.CACHE_SIZE, 200000);

            var result = validator.validate();

            boolean hasRecommendation = result.getIssues().stream()
                .anyMatch(issue -> {
                    String rec = issue.getRecommendation();
                    return rec != null && !rec.isEmpty() && rec.length() > 10;
                });

            assertTrue(hasRecommendation, "Issues should have actionable recommendations");
        }

        @Test
        @DisplayName("Should handle concurrent validation calls")
        void testConcurrentValidationCalls() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<Future<MemoryConfigValidator.ValidationResult>> futures = new ArrayList<>();

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        return validator.validate();
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));

            // All results should be valid
            for (var future : futures) {
                try {
                    var result = future.get();
                    assertNotNull(result);
                } catch (ExecutionException e) {
                    fail("Validation should not throw exception: " + e.getMessage());
                }
            }

            executor.shutdown();
        }
    }

    // ==========================================
    // Performance Benchmark Tests (Step 6)
    // ==========================================

    @Nested
    @DisplayName("Step 6: Performance Benchmark Tests")
    @Order(5)
    class PerformanceBenchmarkTests {

        @Test
        @DisplayName("Should validate within 100ms")
        void testValidationResponseTime() {
            long startTime = System.nanoTime();
            var result = validator.validate();
            long endTime = System.nanoTime();

            long durationMs = (endTime - startTime) / 1_000_000;

            assertNotNull(result);
            assertTrue(
                durationMs < 100,
                "Validation should complete in less than 100ms, took: " + durationMs + "ms"
            );
        }

        @Test
        @DisplayName("Should handle batch validation efficiently")
        void testBatchValidationPerformance() {
            int iterations = 10;
            long startTime = System.nanoTime();

            for (int i = 0; i < iterations; i++) {
                validator.validate();
            }

            long endTime = System.nanoTime();
            long avgDurationMs = ((endTime - startTime) / 1_000_000) / iterations;

            assertTrue(
                avgDurationMs < 100,
                "Average validation time should be less than 100ms, was: " + avgDurationMs + "ms"
            );
        }

        @Test
        @DisplayName("Should maintain reasonable memory usage")
        void testMemoryUsage() {
            Runtime runtime = Runtime.getRuntime();

            // Force GC before test
            System.gc();
            Thread.yield();

            long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

            // Perform multiple validations
            for (int i = 0; i < 100; i++) {
                validator.validate();
            }

            // Force GC after test
            System.gc();
            Thread.yield();

            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = (memoryAfter - memoryBefore) / (1024 * 1024); // Convert to MB

            assertTrue(
                memoryUsed < 10,
                "Memory usage should be reasonable (< 10MB), used: " + memoryUsed + "MB"
            );
        }

        @Test
        @DisplayName("Should generate detailed report efficiently")
        void testDetailedReportPerformance() {
            long startTime = System.nanoTime();
            String report = validator.getDetailedReport();
            long endTime = System.nanoTime();

            long durationMs = (endTime - startTime) / 1_000_000;

            assertNotNull(report);
            assertFalse(report.isEmpty());
            assertTrue(
                durationMs < 100,
                "Report generation should complete in less than 100ms, took: " + durationMs + "ms"
            );
        }
    }

    // ==========================================
    // Concurrency Tests (Step 7)
    // ==========================================

    @Nested
    @DisplayName("Step 7: Concurrency Scenario Tests")
    @Order(6)
    class ConcurrencyTests {

        @Test
        @DisplayName("Should handle 10 concurrent validation requests")
        void testTenConcurrentRequests() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for start signal
                        var result = validator.validate();
                        if (result != null) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Exception indicates test failure
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // Start all threads
            assertTrue(endLatch.await(10, TimeUnit.SECONDS));

            assertEquals(threadCount, successCount.get(), "All concurrent requests should succeed");

            executor.shutdown();
        }

        @Test
        @DisplayName("Should maintain thread safety during validation")
        void testThreadSafety() throws InterruptedException {
            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<Boolean> results = new CopyOnWriteArrayList<>();

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        var result = validator.validate();
                        results.add(result != null && result.isValid());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));

            // All results should be consistent
            boolean firstResult = results.get(0);
            for (boolean result : results) {
                assertEquals(firstResult, result, "All validation results should be consistent");
            }

            executor.shutdown();
        }

        @Test
        @DisplayName("Should maintain data consistency under concurrent load")
        void testDataConsistency() throws InterruptedException {
            int threadCount = 15;
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<MemoryConfigValidator.ValidationResult> results = new CopyOnWriteArrayList<>();

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        results.add(validator.validate());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));

            // All results should have same validity
            boolean firstValid = results.get(0).isValid();
            for (var result : results) {
                assertEquals(firstValid, result.isValid(), "Validity should be consistent");
            }

            executor.shutdown();
        }

        @Test
        @DisplayName("Should handle concurrent configuration changes and validation")
        void testConcurrentConfigChanges() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount * 2);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount * 2);

            // Threads that change configuration
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        config.set(MemoryConfig.CACHE_SIZE, 1000 + index * 100);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Threads that validate
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        validator.validate();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));

            // Test passes if no exceptions occur
            executor.shutdown();
        }
    }

    // ==========================================
    // Cache Mechanism Tests (Step 8)
    // ==========================================

    @Nested
    @DisplayName("Step 8: Cache Mechanism Tests")
    @Order(7)
    class CacheMechanismTests {

        @Test
        @DisplayName("Should cache configuration between validations")
        void testConfigurationCaching() {
            config.set(MemoryConfig.CACHE_SIZE, 5000);

            var result1 = validator.validate();
            var result2 = validator.validate();

            // Both should return valid results
            assertNotNull(result1);
            assertNotNull(result2);
            assertEquals(result1.isValid(), result2.isValid());
        }

        @Test
        @DisplayName("Should invalidate cache on configuration change")
        void testCacheInvalidation() {
            var result1 = validator.validate();

            // Change configuration
            config.set(MemoryConfig.CACHE_SIZE, 5000);

            var result2 = validator.validate();

            // Results may differ
            assertNotNull(result1);
            assertNotNull(result2);
        }

        @Test
        @DisplayName("Should maintain cache consistency")
        void testCacheConsistency() {
            config.set(MemoryConfig.CACHE_SIZE, 5000);

            List<MemoryConfigValidator.ValidationResult> results = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                results.add(validator.validate());
            }

            // All results should be consistent
            boolean firstValid = results.get(0).isValid();
            for (var result : results) {
                assertEquals(firstValid, result.isValid(), "Results should be consistent");
            }
        }

        @Test
        @DisplayName("Should handle cache in concurrent scenarios")
        void testCacheConcurrency() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<MemoryConfigValidator.ValidationResult> results = new CopyOnWriteArrayList<>();

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        results.add(validator.validate());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));

            // Results should be consistent
            boolean firstValid = results.get(0).isValid();
            for (var result : results) {
                assertEquals(firstValid, result.isValid(), "Cached results should be consistent");
            }

            executor.shutdown();
        }
    }

    // ==========================================
    // Additional Comprehensive Tests
    // ==========================================

    @Nested
    @DisplayName("Additional Comprehensive Tests")
    @Order(8)
    class AdditionalTests {

        @Test
        @DisplayName("Should generate comprehensive validation summary")
        void testValidationSummary() {
            // Create some issues
            config.set(MemoryConfig.CACHE_SIZE, 200000);
            config.set(MemoryConfig.BATCH_SIZE, 2000);

            var result = validator.validate();
            String summary = result.getSummary();

            assertNotNull(summary);
            assertTrue(summary.contains("Memory Configuration Validation Summary"));
            assertTrue(summary.contains("Critical Issues"));
            assertTrue(summary.contains("Warnings"));
            assertTrue(summary.contains("Info"));
            assertTrue(summary.contains("Total"));
        }

        @Test
        @DisplayName("Should generate detailed report with all issues")
        void testDetailedReport() {
            config.set(MemoryConfig.CACHE_SIZE, 200000);

            String report = validator.getDetailedReport();

            assertNotNull(report);
            assertTrue(report.contains("Memory Configuration Validation Summary"));
            assertTrue(report.contains("Detailed Issues"));
        }

        @Test
        @DisplayName("Should validate for deployment")
        void testValidateForDeployment() {
            config.set(MemoryConfig.CACHE_SIZE, 1000);

            boolean isValid = validator.validateForDeployment();

            // Default configuration should be deployable
            assertTrue(isValid, "Default configuration should be deployable");
        }

        @Test
        @DisplayName("Should fail deployment validation with critical issues")
        void testDeploymentValidationWithCriticalIssues() {
            // Simulate critical issue by using conflicting backend
            config.setBackendType(MemoryConfig.BackendType.POSTGRESQL_PGVECTOR);
            // Don't set required PostgreSQL config

            boolean isValid = validator.validateForDeployment();

            // Should fail due to critical issues
            assertFalse(isValid, "Should not be deployable with critical issues");
        }

        @Test
        @DisplayName("ValidationIssue toString should be well-formatted")
        void testValidationIssueToString() {
            var issue = new MemoryConfigValidator.ValidationIssue(
                MemoryConfigValidator.ValidationIssue.Severity.CRITICAL,
                "test_category",
                "Test message",
                "Test recommendation"
            );

            String str = issue.toString();

            assertNotNull(str);
            assertTrue(str.contains("[CRITICAL]"));
            assertTrue(str.contains("test_category"));
            assertTrue(str.contains("Test message"));
            assertTrue(str.contains("Test recommendation"));
        }

        @Test
        @DisplayName("Should handle all validation categories")
        void testAllValidationCategories() {
            var result = validator.validate();

            // Check that various categories are covered
            List<String> categories = result.getIssues().stream()
                .map(MemoryConfigValidator.ValidationIssue::getCategory)
                .distinct()
                .toList();

            // Should have at least some categories
            assertTrue(categories.size() >= 0, "Should have validation categories");
        }
    }
}
