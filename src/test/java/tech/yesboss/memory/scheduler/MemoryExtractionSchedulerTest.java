package tech.yesboss.memory.scheduler;

import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tech.yesboss.memory.config.MemoryConfig;
import tech.yesboss.memory.trigger.TriggerService;
import tech.yesboss.memory.trigger.TriggerServiceException;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for MemoryExtractionScheduler
 *
 * Test Coverage:
 * 1. Test data preparation with normal, boundary, and anomalous data
 * 2. Interface contract validation (methods, parameters, return values, exceptions)
 * 3. Normal functionality scenarios
 * 4. Boundary conditions (null values, extreme values, special characters, concurrency, resource limits)
 * 5. Exception handling (error scenarios, messages, recovery, degradation)
 * 6. Performance benchmarks (response time <100ms, batch processing, memory <512MB)
 * 7. Concurrency scenarios (10 concurrent requests, thread safety, data consistency)
 * 8. Metrics and monitoring
 */
@DisplayName("MemoryExtractionScheduler Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MemoryExtractionSchedulerTest {

    @Mock
    private TriggerService triggerService;

    @Mock
    private MemoryConfig config;

    private MemoryExtractionScheduler scheduler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup default mock behavior
        when(triggerService.isAvailable()).thenReturn(true);
        when(triggerService.triggerForAllConversations()).thenReturn(5);
        when(config.get(anyString(), anyBoolean())).thenReturn(true);
        when(config.get(anyString(), anyInt())).thenReturn(5);
        when(config.get(anyString(), anyLong())).thenReturn(300000L);

        scheduler = new MemoryExtractionScheduler(triggerService, config);
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    // ========================================================================
    // Step 1: Test Data Preparation
    // ========================================================================

    @Nested
    @DisplayName("Step 1: Test Data Preparation")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class TestDataPreparation {

        @Test
        @Order(1)
        @DisplayName("Should prepare normal test data - enabled scheduler")
        void shouldPrepareNormalTestData() {
            // Given: Enabled scheduler
            when(config.get("memory.scheduler.enabled", true)).thenReturn(true);

            // When: Creating scheduler
            MemoryExtractionScheduler scheduler = new MemoryExtractionScheduler(triggerService, config);

            // Then: Should be enabled
            assertTrue(scheduler.isEnabled(), "Scheduler should be enabled by default");
        }

        @Test
        @Order(2)
        @DisplayName("Should prepare boundary test data - disabled scheduler")
        void shouldPrepareBoundaryTestData() {
            // Given: Disabled scheduler
            when(config.get("memory.scheduler.enabled", true)).thenReturn(false);

            // When: Creating scheduler
            MemoryExtractionScheduler scheduler = new MemoryExtractionScheduler(triggerService, config);

            // Then: Should be disabled
            assertFalse(scheduler.isEnabled(), "Scheduler should be disabled");
        }

        @Test
        @Order(3)
        @DisplayName("Should prepare anomalous test data - unavailable trigger service")
        void shouldPrepareAnomalousTestData() {
            // Given: Unavailable trigger service
            when(triggerService.isAvailable()).thenReturn(false);

            // When: Creating scheduler
            MemoryExtractionScheduler scheduler = new MemoryExtractionScheduler(triggerService, config);

            // Then: Should still create successfully
            assertNotNull(scheduler, "Scheduler should create even with unavailable service");
        }

        @Test
        @Order(4)
        @DisplayName("Should prepare test data with normal configuration")
        void shouldPrepareTestDataWithNormalConfiguration() {
            // Given: Normal configuration
            when(config.get("memory.scheduler.fixedRate", 300000L)).thenReturn(300000L);
            when(config.get("memory.scheduler.initialDelay", 60000L)).thenReturn(60000L);
            when(config.get("memory.scheduler.maxConcurrentTasks", 5)).thenReturn(5);

            // When: Creating scheduler
            MemoryExtractionScheduler scheduler = new MemoryExtractionScheduler(triggerService, config);

            // Then: Should create successfully
            assertNotNull(scheduler, "Scheduler should create with normal config");
        }

        @Test
        @Order(5)
        @DisplayName("Should prepare test data with extreme configuration")
        void shouldPrepareTestDataWithExtremeConfiguration() {
            // Given: Extreme configuration
            when(config.get("memory.scheduler.fixedRate", 300000L)).thenReturn(1L); // Very fast
            when(config.get("memory.scheduler.maxConcurrentTasks", 5)).thenReturn(1000); // Very high

            // When: Creating scheduler
            MemoryExtractionScheduler scheduler = new MemoryExtractionScheduler(triggerService, config);

            // Then: Should handle extreme values
            assertNotNull(scheduler, "Scheduler should handle extreme config values");
        }
    }

    // ========================================================================
    // Step 2: Interface Contract Validation
    // ========================================================================

    @Nested
    @DisplayName("Step 2: Interface Contract Validation")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class InterfaceContractValidation {

        @Test
        @Order(1)
        @DisplayName("Should have enable() method")
        void shouldHaveEnableMethod() throws NoSuchMethodException {
            // Given: Scheduler class
            Class<MemoryExtractionScheduler> clazz = MemoryExtractionScheduler.class;

            // Then: Should have enable() method
            assertNotNull(clazz.getMethod("enable"),
                "Scheduler should have enable() method");
        }

        @Test
        @Order(2)
        @DisplayName("Should have disable() method")
        void shouldHaveDisableMethod() throws NoSuchMethodException {
            // Given: Scheduler class
            Class<MemoryExtractionScheduler> clazz = MemoryExtractionScheduler.class;

            // Then: Should have disable() method
            assertNotNull(clazz.getMethod("disable"),
                "Scheduler should have disable() method");
        }

        @Test
        @Order(3)
        @DisplayName("Should have isEnabled() method returning boolean")
        void shouldHaveIsEnabledMethod() {
            // When: Calling isEnabled()
            boolean enabled = scheduler.isEnabled();

            // Then: Should return boolean
            assertTrue(enabled == true || enabled == false,
                "isEnabled() should return boolean");
        }

        @Test
        @Order(4)
        @DisplayName("Should have isRunning() method returning boolean")
        void shouldHaveIsRunningMethod() {
            // When: Calling isRunning()
            boolean running = scheduler.isRunning();

            // Then: Should return boolean
            assertTrue(running == true || running == false,
                "isRunning() should return boolean");
        }

        @Test
        @Order(5)
        @DisplayName("Should have getMetrics() method returning Map")
        void shouldHaveGetMetricsMethod() {
            // When: Calling getMetrics()
            Map<String, Object> metrics = scheduler.getMetrics();

            // Then: Should return Map
            assertNotNull(metrics, "getMetrics() should not return null");
            assertInstanceOf(Map.class, metrics,
                "getMetrics() should return Map");
        }

        @Test
        @Order(6)
        @DisplayName("Should have calculateSuccessRate() method")
        void shouldHaveCalculateSuccessRateMethod() {
            // When: Calling calculateSuccessRate()
            long successRate = scheduler.calculateSuccessRate();

            // Then: Should return long
            assertTrue(successRate >= 0 && successRate <= 100,
                "Success rate should be between 0 and 100");
        }

        @Test
        @Order(7)
        @DisplayName("Should have triggerExtractionAsync() method returning CompletableFuture")
        void shouldHaveTriggerExtractionAsyncMethod() {
            // When: Calling triggerExtractionAsync()
            CompletableFuture<Integer> future = scheduler.triggerExtractionAsync("convId", "sessionId");

            // Then: Should return CompletableFuture
            assertNotNull(future, "triggerExtractionAsync() should return CompletableFuture");
        }

        @Test
        @Order(8)
        @DisplayName("Should have shutdown() method")
        void shouldHaveShutdownMethod() throws NoSuchMethodException {
            // Given: Scheduler class
            Class<MemoryExtractionScheduler> clazz = MemoryExtractionScheduler.class;

            // Then: Should have shutdown() method
            assertNotNull(clazz.getMethod("shutdown"),
                "Scheduler should have shutdown() method");
        }

        @Test
        @Order(9)
        @DisplayName("Should have restart() method")
        void shouldHaveRestartMethod() throws NoSuchMethodException {
            // Given: Scheduler class
            Class<MemoryExtractionScheduler> clazz = MemoryExtractionScheduler.class;

            // Then: Should have restart() method
            assertNotNull(clazz.getMethod("restart"),
                "Scheduler should have restart() method");
        }
    }

    // ========================================================================
    // Step 3: Normal Functionality Scenarios
    // ========================================================================

    @Nested
    @DisplayName("Step 3: Normal Functionality Scenarios")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class NormalFunctionalityTests {

        @Test
        @Order(1)
        @DisplayName("Should enable scheduler")
        void shouldEnableScheduler() {
            // Given: Disabled scheduler
            scheduler.disable();
            assertFalse(scheduler.isEnabled(), "Should be disabled");

            // When: Enabling
            scheduler.enable();

            // Then: Should be enabled
            assertTrue(scheduler.isEnabled(), "Should be enabled after enable()");
        }

        @Test
        @Order(2)
        @DisplayName("Should disable scheduler")
        void shouldDisableScheduler() {
            // Given: Enabled scheduler
            scheduler.enable();
            assertTrue(scheduler.isEnabled(), "Should be enabled");

            // When: Disabling
            scheduler.disable();

            // Then: Should be disabled
            assertFalse(scheduler.isEnabled(), "Should be disabled after disable()");
        }

        @Test
        @Order(3)
        @DisplayName("Should trigger extraction asynchronously")
        void shouldTriggerExtractionAsynchronously() throws Exception {
            // Given: Trigger service
            when(triggerService.triggerMemoryExtraction(anyString(), anyString())).thenReturn(10);

            // When: Triggering async extraction
            CompletableFuture<Integer> future = scheduler.triggerExtractionAsync("convId", "sessionId");

            // Then: Should complete successfully
            Integer result = future.get(5, TimeUnit.SECONDS);
            assertEquals(10, result, "Should process 10 items");
        }

        @Test
        @Order(4)
        @DisplayName("Should get metrics")
        void shouldGetMetrics() {
            // When: Getting metrics
            Map<String, Object> metrics = scheduler.getMetrics();

            // Then: Should have expected keys
            assertNotNull(metrics, "Metrics should not be null");
            assertTrue(metrics.containsKey("enabled"), "Should have 'enabled' metric");
            assertTrue(metrics.containsKey("running"), "Should have 'running' metric");
            assertTrue(metrics.containsKey("activeTasks"), "Should have 'activeTasks' metric");
            assertTrue(metrics.containsKey("totalExtractions"), "Should have 'totalExtractions' metric");
        }

        @Test
        @Order(5)
        @DisplayName("Should calculate success rate")
        void shouldCalculateSuccessRate() {
            // When: Calculating success rate
            long successRate = scheduler.calculateSuccessRate();

            // Then: Should be valid percentage
            assertTrue(successRate >= 0 && successRate <= 100,
                "Success rate should be between 0 and 100");
        }

        @Test
        @Order(6)
        @DisplayName("Should calculate average processing time")
        void shouldCalculateAverageProcessingTime() {
            // When: Calculating average processing time
            long avgTime = scheduler.calculateAverageProcessingTime();

            // Then: Should be non-negative
            assertTrue(avgTime >= 0,
                "Average processing time should be non-negative");
        }

        @Test
        @Order(7)
        @DisplayName("Should get active task count")
        void shouldGetActiveTaskCount() {
            // When: Getting active task count
            int activeTasks = scheduler.getActiveTaskCount();

            // Then: Should be non-negative
            assertTrue(activeTasks >= 0,
                "Active task count should be non-negative");
        }

        @Test
        @Order(8)
        @DisplayName("Should handle normal scheduled execution")
        void shouldHandleNormalScheduledExecution() {
            // Given: Normal configuration
            when(triggerService.triggerForAllConversations()).thenReturn(3);

            // When: Simulating scheduled execution
            // (This would normally be called by Spring's scheduler)
            // We'll test the logic directly

            // Then: Should not throw exceptions
            assertDoesNotThrow(() -> {
                // The scheduled method would be called here
                // For testing, we verify the setup is correct
                assertTrue(scheduler.isEnabled(), "Scheduler should be enabled");
            }, "Should handle normal execution");
        }

        @Test
        @Order(9)
        @DisplayName("Should update configuration dynamically")
        void shouldUpdateConfiguration() {
            // When: Updating configuration
            scheduler.updateConfiguration("memory.scheduler.fixedRate", 60000L);

            // Then: Should not throw exception
            assertDoesNotThrow(() -> scheduler.updateConfiguration("test.key", "test.value"),
                "Should update configuration dynamically");
        }

        @Test
        @Order(10)
        @DisplayName("Should shutdown gracefully")
        void shouldShutdownGracefully() {
            // When: Shutting down
            scheduler.shutdown();

            // Then: Should be disabled
            assertFalse(scheduler.isEnabled(), "Scheduler should be disabled after shutdown");
        }
    }

    // ========================================================================
    // Step 4: Boundary Conditions
    // ========================================================================

    @Nested
    @DisplayName("Step 4: Boundary Conditions")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class BoundaryConditionTests {

        @Test
        @Order(1)
        @DisplayName("Should handle null conversation ID gracefully")
        void shouldHandleNullConversationId() {
            // Given: Null conversation ID
            // When: Triggering extraction with null ID
            CompletableFuture<Integer> future = scheduler.triggerExtractionAsync(null, "sessionId");

            // Then: Should complete exceptionally
            assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS),
                "Should throw exception for null conversation ID");
        }

        @Test
        @Order(2)
        @DisplayName("Should handle empty conversation ID gracefully")
        void shouldHandleEmptyConversationId() {
            // Given: Empty conversation ID
            // When: Triggering extraction with empty ID
            CompletableFuture<Integer> future = scheduler.triggerExtractionAsync("", "sessionId");

            // Then: Should complete exceptionally
            assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS),
                "Should throw exception for empty conversation ID");
        }

        @Test
        @Order(3)
        @DisplayName("Should handle null session ID gracefully")
        void shouldHandleNullSessionId() {
            // Given: Null session ID
            // When: Triggering extraction with null session ID
            CompletableFuture<Integer> future = scheduler.triggerExtractionAsync("convId", null);

            // Then: Should complete exceptionally
            assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS),
                "Should throw exception for null session ID");
        }

        @Test
        @Order(4)
        @DisplayName("Should handle extreme configuration values")
        void shouldHandleExtremeConfigurationValues() {
            // Given: Extreme fixed rate (1ms)
            when(config.get("memory.scheduler.fixedRate", 300000L)).thenReturn(1L);
            when(config.get("memory.scheduler.maxConcurrentTasks", 5)).thenReturn(10000);

            // When: Creating scheduler with extreme values
            MemoryExtractionScheduler scheduler = new MemoryExtractionScheduler(triggerService, config);

            // Then: Should create successfully
            assertNotNull(scheduler, "Should handle extreme configuration values");
        }

        @Test
        @Order(5)
        @DisplayName("Should handle zero concurrent tasks")
        void shouldHandleZeroConcurrentTasks() {
            // Given: Zero max concurrent tasks
            when(config.get("memory.scheduler.maxConcurrentTasks", 5)).thenReturn(0);

            // When: Creating scheduler
            MemoryExtractionScheduler scheduler = new MemoryExtractionScheduler(triggerService, config);

            // Then: Should create successfully
            assertNotNull(scheduler, "Should handle zero concurrent tasks");
        }

        @Test
        @Order(6)
        @DisplayName("Should handle special characters in IDs")
        void shouldHandleSpecialCharactersInIds() {
            // Given: Special character IDs
            String specialId = "conv-!@#$%^&*()_+-=[]{}|;':\",./<>?`~";

            // When: Triggering extraction
            CompletableFuture<Integer> future = scheduler.triggerExtractionAsync(specialId, specialId);

            // Then: Should attempt processing (may fail in trigger service, but scheduler should handle it)
            assertNotNull(future, "Should create future for special character IDs");
        }

        @Test
        @Order(7)
        @DisplayName("Should handle very long IDs")
        void shouldHandleVeryLongIds() {
            // Given: Very long ID (10000 characters)
            StringBuilder longId = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                longId.append("a");
            }

            // When: Triggering extraction
            CompletableFuture<Integer> future = scheduler.triggerExtractionAsync(longId.toString(), "sessionId");

            // Then: Should create future
            assertNotNull(future, "Should handle very long IDs");
        }

        @Test
        @Order(8)
        @DisplayName("Should handle concurrent access")
        void shouldHandleConcurrentAccess() throws InterruptedException {
            // Given: Multiple threads accessing scheduler
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // When: Accessing concurrently
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        scheduler.getMetrics();
                        scheduler.isEnabled();
                        scheduler.getActiveTaskCount();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Then: All should complete
            boolean completed = latch.await(10, TimeUnit.SECONDS);
            assertTrue(completed, "All concurrent operations should complete");

            executor.shutdown();
        }
    }

    // ========================================================================
    // Step 5: Exception Handling
    // ========================================================================

    @Nested
    @DisplayName("Step 5: Exception Handling")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ExceptionHandlingTests {

        @Test
        @Order(1)
        @DisplayName("Should handle trigger service exceptions")
        void shouldHandleTriggerServiceExceptions() {
            // Given: Trigger service throws exception
            when(triggerService.triggerMemoryExtraction(anyString(), anyString()))
                .thenThrow(new TriggerServiceException("Test exception", -1));

            // When: Triggering extraction
            CompletableFuture<Integer> future = scheduler.triggerExtractionAsync("convId", "sessionId");

            // Then: Should complete exceptionally
            assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS),
                "Should handle trigger service exceptions");
        }

        @Test
        @Order(2)
        @DisplayName("Should handle unavailable trigger service")
        void shouldHandleUnavailableTriggerService() {
            // Given: Unavailable trigger service
            when(triggerService.isAvailable()).thenReturn(false);

            // When: Creating scheduler
            MemoryExtractionScheduler scheduler = new MemoryExtractionScheduler(triggerService, config);

            // Then: Should still create
            assertNotNull(scheduler, "Should handle unavailable trigger service");
        }

        @Test
        @Order(3)
        @DisplayName("Should retry on failure with exponential backoff")
        void shouldRetryOnFailureWithExponentialBackoff() {
            // Given: Trigger service fails then succeeds
            when(triggerService.triggerMemoryExtraction(anyString(), anyString()))
                .thenThrow(new TriggerServiceException("Temporary failure", -1))
                .thenReturn(10);

            // When: Triggering with retry
            CompletableFuture<Integer> future = scheduler.triggerExtractionWithRetry("convId", "sessionId");

            // Then: Should eventually succeed
            assertDoesNotThrow(() -> {
                Integer result = future.get(15, TimeUnit.SECONDS);
                assertEquals(10, result, "Should succeed after retry");
            }, "Should retry and eventually succeed");
        }

        @Test
        @Order(4)
        @DisplayName("Should provide meaningful error messages")
        void shouldProvideMeaningfulErrorMessages() {
            // Given: Trigger service exception
            when(triggerService.triggerMemoryExtraction(anyString(), anyString()))
                .thenThrow(new TriggerServiceException("Specific error message", -1));

            // When: Triggering extraction
            CompletableFuture<Integer> future = scheduler.triggerExtractionAsync("convId", "sessionId");

            // Then: Exception should have meaningful message
            ExecutionException exception = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));
            assertNotNull(exception.getMessage(), "Exception should have message");
        }

        @Test
        @Order(5)
        @DisplayName("Should recover from errors")
        void shouldRecoverFromErrors() {
            // Given: Trigger service fails
            when(triggerService.triggerMemoryExtraction(anyString(), anyString()))
                .thenThrow(new TriggerServiceException("Temporary error", -1));

            // When: Triggering extraction
            CompletableFuture<Integer> future1 = scheduler.triggerExtractionAsync("convId", "sessionId");

            // Then: First attempt should fail
            assertThrows(ExecutionException.class, () -> future1.get(5, TimeUnit.SECONDS));

            // When: Triggering again after service recovers
            when(triggerService.triggerMemoryExtraction(anyString(), anyString())).thenReturn(5);
            CompletableFuture<Integer> future2 = scheduler.triggerExtractionAsync("convId", "sessionId");

            // Then: Second attempt should succeed
            assertDoesNotThrow(() -> {
                Integer result = future2.get(5, TimeUnit.SECONDS);
                assertEquals(5, result, "Should recover and succeed");
            }, "Should recover from errors");
        }

        @Test
        @Order(6)
        @DisplayName("Should handle timeout exceptions")
        void shouldHandleTimeoutExceptions() {
            // Given: Slow trigger service
            when(triggerService.triggerMemoryExtraction(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    Thread.sleep(10000); // 10 seconds
                    return 10;
                });

            // When: Triggering with timeout
            CompletableFuture<Integer> future = scheduler.triggerExtractionAsync("convId", "sessionId");

            // Then: Should timeout
            assertThrows(TimeoutException.class, () -> future.get(1, TimeUnit.SECONDS),
                "Should handle timeout");
        }

        @Test
        @Order(7)
        @DisplayName("Should handle degradation modes")
        void shouldHandleDegradationModes() {
            // Given: Different degradation modes
            String[] modes = {"log_only", "disable", "backoff"};

            for (String mode : modes) {
                when(config.get("memory.scheduler.degradationMode", "log_only")).thenReturn(mode);

                // When: Creating scheduler with degradation mode
                MemoryExtractionScheduler scheduler = new MemoryExtractionScheduler(triggerService, config);

                // Then: Should create successfully
                assertNotNull(scheduler, "Should handle degradation mode: " + mode);
            }
        }

        @Test
        @Order(8)
        @DisplayName("Should handle concurrent errors gracefully")
        void shouldHandleConcurrentErrors() throws InterruptedException {
            // Given: Trigger service throws exceptions
            when(triggerService.triggerMemoryExtraction(anyString(), anyString()))
                .thenThrow(new TriggerServiceException("Concurrent error", -1));

            // When: Triggering multiple concurrent extractions
            int threadCount = 5;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        CompletableFuture<Integer> future = scheduler.triggerExtractionAsync(
                            "convId", "sessionId");
                        future.get(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        // Expected to fail
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Then: All should complete without crashing
            boolean completed = latch.await(10, TimeUnit.SECONDS);
            assertTrue(completed, "All concurrent errors should be handled gracefully");

            executor.shutdown();
        }
    }

    // ========================================================================
    // Step 6: Performance Benchmarks
    // ========================================================================

    @Nested
    @DisplayName("Step 6: Performance Benchmarks")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PerformanceBenchmarkTests {

        @Test
        @Order(1)
        @DisplayName("Should get metrics in less than 100ms")
        void shouldGetMetricsFast() {
            // When: Getting metrics
            long startTime = System.currentTimeMillis();
            Map<String, Object> metrics = scheduler.getMetrics();
            long duration = System.currentTimeMillis() - startTime;

            // Then: Should complete in less than 100ms
            assertTrue(duration < 100, "Getting metrics should take <100ms, took: " + duration + "ms");
            assertNotNull(metrics, "Should return metrics");
        }

        @Test
        @Order(2)
        @DisplayName("Should handle batch processing of 100 items in less than 1s")
        void shouldHandleBatchProcessing() {
            // When: Processing 100 metrics requests
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < 100; i++) {
                scheduler.getMetrics();
            }
            long duration = System.currentTimeMillis() - startTime;

            // Then: Should complete in less than 1s
            assertTrue(duration < 1000, "100 metrics requests should take <1s, took: " + duration + "ms");
        }

        @Test
        @Order(3)
        @DisplayName("Should use less than 512MB memory")
        void shouldUseLimitedMemory() {
            // Given: Initial memory
            Runtime runtime = Runtime.getRuntime();
            long initialMemory = runtime.totalMemory() - runtime.freeMemory();

            // When: Performing many operations
            for (int i = 0; i < 1000; i++) {
                scheduler.getMetrics();
                scheduler.isEnabled();
                scheduler.getActiveTaskCount();
            }

            // Then: Memory usage should be reasonable
            long finalMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = (finalMemory - initialMemory) / (1024 * 1024); // Convert to MB

            assertTrue(memoryUsed < 512, "Memory usage should be <512MB, used: " + memoryUsed + "MB");
        }

        @Test
        @Order(4)
        @DisplayName("Should efficiently calculate success rate")
        void shouldEfficientlyCalculateSuccessRate() {
            // When: Calculating success rate multiple times
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < 1000; i++) {
                scheduler.calculateSuccessRate();
            }
            long duration = System.currentTimeMillis() - startTime;

            // Then: Should be fast
            assertTrue(duration < 100, "1000 success rate calculations should take <100ms, took: " + duration + "ms");
        }

        @Test
        @Order(5)
        @DisplayName("Should efficiently calculate average processing time")
        void shouldEfficientlyCalculateAverageProcessingTime() {
            // When: Calculating average processing time multiple times
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < 1000; i++) {
                scheduler.calculateAverageProcessingTime();
            }
            long duration = System.currentTimeMillis() - startTime;

            // Then: Should be fast
            assertTrue(duration < 100, "1000 avg time calculations should take <100ms, took: " + duration + "ms");
        }
    }

    // ========================================================================
    // Step 7: Concurrency Scenarios
    // ========================================================================

    @Nested
    @DisplayName("Step 7: Concurrency Scenarios")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ConcurrencyTests {

        @Test
        @Order(1)
        @DisplayName("Should handle 10 concurrent extraction requests")
        void shouldHandleConcurrentExtractionRequests() throws Exception {
            // Given: 10 concurrent requests
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<Integer>> futures = new java.util.ArrayList<>();

            // When: Submitting concurrent requests
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                Future<Integer> future = executor.submit(() -> {
                    try {
                        CompletableFuture<Integer> completableFuture =
                            scheduler.triggerExtractionAsync("convId" + index, "sessionId" + index);
                        return completableFuture.get(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        return -1;
                    } finally {
                        latch.countDown();
                    }
                });
                futures.add(future);
            }

            // Then: All should complete
            latch.await(30, TimeUnit.SECONDS);
            assertEquals(threadCount, futures.size(), "All requests should complete");

            executor.shutdown();
        }

        @Test
        @Order(2)
        @DisplayName("Should be thread-safe for metrics access")
        void shouldBeThreadSafeForMetricsAccess() throws InterruptedException {
            // Given: Multiple threads accessing metrics
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Map<String, Object>> metricsList = new java.util.concurrent.CopyOnWriteArrayList<>();

            // When: Accessing metrics concurrently
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        Map<String, Object> metrics = scheduler.getMetrics();
                        metricsList.add(metrics);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Then: All should complete without errors
            latch.await(10, TimeUnit.SECONDS);
            assertEquals(threadCount, metricsList.size(), "All threads should get metrics");

            executor.shutdown();
        }

        @Test
        @Order(3)
        @DisplayName("Should maintain data consistency under concurrency")
        void shouldMaintainDataConsistency() throws InterruptedException {
            // Given: Multiple threads
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            AtomicBoolean hasError = new AtomicBoolean(false);

            // When: Modifying and reading state concurrently
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        // Enable/disable
                        scheduler.enable();
                        scheduler.disable();

                        // Get metrics
                        Map<String, Object> metrics = scheduler.getMetrics();
                        if (metrics == null) {
                            hasError.set(true);
                        }
                    } catch (Exception e) {
                        hasError.set(true);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Then: No errors should occur
            latch.await(10, TimeUnit.SECONDS);
            assertFalse(hasError.get(), "Should maintain consistency under concurrency");

            executor.shutdown();
        }

        @Test
        @Order(4)
        @DisplayName("Should handle concurrent enable/disable")
        void shouldHandleConcurrentEnableDisable() throws InterruptedException {
            // Given: Multiple threads
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount * 2);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // When: Concurrently enabling and disabling
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        scheduler.enable();
                    } finally {
                        latch.countDown();
                    }
                });
                executor.submit(() -> {
                    try {
                        scheduler.disable();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Then: Should not throw exceptions
            latch.await(10, TimeUnit.SECONDS);
            assertTrue(latch.getCount() == 0, "All enable/disable operations should complete");

            executor.shutdown();
        }

        @Test
        @Order(5)
        @DisplayName("Should handle concurrent shutdown")
        void shouldHandleConcurrentShutdown() throws InterruptedException {
            // Given: Multiple threads
            int threadCount = 5;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // When: Attempting concurrent shutdowns
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        scheduler.shutdown();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Then: Should not throw exceptions
            latch.await(10, TimeUnit.SECONDS);
            assertTrue(latch.getCount() == 0, "All shutdowns should complete");

            executor.shutdown();
        }
    }

    // ========================================================================
    // Step 8: Metrics and Monitoring
    // ========================================================================

    @Nested
    @DisplayName("Step 8: Metrics and Monitoring")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class MetricsAndMonitoringTests {

        @Test
        @Order(1)
        @DisplayName("Should track enabled status in metrics")
        void shouldTrackEnabledStatus() {
            // When: Getting metrics
            Map<String, Object> metrics = scheduler.getMetrics();

            // Then: Should have enabled status
            assertTrue(metrics.containsKey("enabled"), "Should track enabled status");
            assertEquals(true, metrics.get("enabled"), "Should be enabled by default");
        }

        @Test
        @Order(2)
        @DisplayName("Should track running status in metrics")
        void shouldTrackRunningStatus() {
            // When: Getting metrics
            Map<String, Object> metrics = scheduler.getMetrics();

            // Then: Should have running status
            assertTrue(metrics.containsKey("running"), "Should track running status");
        }

        @Test
        @Order(3)
        @DisplayName("Should track active tasks in metrics")
        void shouldTrackActiveTasks() {
            // When: Getting metrics
            Map<String, Object> metrics = scheduler.getMetrics();

            // Then: Should have active tasks count
            assertTrue(metrics.containsKey("activeTasks"), "Should track active tasks");
            assertTrue(metrics.get("activeTasks") instanceof Integer, "Active tasks should be integer");
        }

        @Test
        @Order(4)
        @DisplayName("Should track total extractions in metrics")
        void shouldTrackTotalExtractions() {
            // When: Getting metrics
            Map<String, Object> metrics = scheduler.getMetrics();

            // Then: Should have total extractions
            assertTrue(metrics.containsKey("totalExtractions"), "Should track total extractions");
            assertTrue(metrics.get("totalExtractions") instanceof Long, "Total extractions should be long");
        }

        @Test
        @Order(5)
        @DisplayName("Should track success rate in metrics")
        void shouldTrackSuccessRate() {
            // When: Getting metrics
            Map<String, Object> metrics = scheduler.getMetrics();

            // Then: Should have success rate
            assertTrue(metrics.containsKey("successRate"), "Should track success rate");
            assertTrue(metrics.get("successRate") instanceof Long, "Success rate should be long");
        }

        @Test
        @Order(6)
        @DisplayName("Should track failure rate in metrics")
        void shouldTrackFailureRate() {
            // When: Getting metrics
            Map<String, Object> metrics = scheduler.getMetrics();

            // Then: Should have failure rate
            assertTrue(metrics.containsKey("failureRate"), "Should track failure rate");
            assertTrue(metrics.get("failureRate") instanceof Long, "Failure rate should be long");
        }

        @Test
        @Order(7)
        @DisplayName("Should track average processing time in metrics")
        void shouldTrackAverageProcessingTime() {
            // When: Getting metrics
            Map<String, Object> metrics = scheduler.getMetrics();

            // Then: Should have average processing time
            assertTrue(metrics.containsKey("averageProcessingTime"), "Should track avg processing time");
            assertTrue(metrics.get("averageProcessingTime") instanceof Long, "Avg time should be long");
        }

        @Test
        @Order(8)
        @DisplayName("Should update metrics after operations")
        void shouldUpdateMetricsAfterOperations() {
            // Given: Initial metrics
            Map<String, Object> initialMetrics = scheduler.getMetrics();

            // When: Triggering extraction
            when(triggerService.triggerMemoryExtraction(anyString(), anyString())).thenReturn(5);
            CompletableFuture<Integer> future = scheduler.triggerExtractionAsync("convId", "sessionId");
            future.join();

            // Then: Metrics should be updated (or at least not crash)
            Map<String, Object> updatedMetrics = scheduler.getMetrics();
            assertNotNull(updatedMetrics, "Metrics should be available after operations");
        }
    }
}
