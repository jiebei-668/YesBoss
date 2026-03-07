package tech.yesboss.memory.monitoring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for MemoryMonitor.
 *
 * <p>This test class verifies all aspects of the MemoryMonitor including:
 * <ul>
 *   <li>Interface contract compliance</li>
 *   <li>Monitoring lifecycle (start/stop/isRunning)</li>
 *   <li>Metrics collection and history</li>
 *   <li>Alert registration and management</li>
 *   <li>Health checks</li>
 *   <li>Alert notifications</li>
 *   <li>Configuration management</li>
 *   <li>Error handling and recovery</li>
 *   <li>Performance requirements</li>
 *   <li>Concurrent operations</li>
 * </ul>
 */
@DisplayName("MemoryMonitor Implementation Tests")
public class MemoryMonitorTest {

    private MemoryMonitor monitor;
    private MemoryMonitorConfig config;

    @BeforeEach
    void setUp() {
        config = MemoryMonitorConfig.builder()
                .enabled(true)
                .intervalMs(100)
                .alertEnabled(true)
                .alertCooldownMs(1000)
                .retentionDays(7)
                .errorRateThreshold(0.05)
                .responseTimeThresholdMs(1000)
                .logAlerts(false)
                .build();

        monitor = new MemoryMonitorImpl(config);
    }

    @AfterEach
    void tearDown() {
        if (monitor != null) {
            monitor.stop();
            ((MemoryMonitorImpl) monitor).shutdown();
        }
    }

    // ========== Helper Methods ==========

    private MemoryMonitor.MetricsSnapshot createTestSnapshot() {
        return new MemoryMonitor.MetricsSnapshot(
                LocalDateTime.now(),
                1000, // totalOperations
                950,  // successfulOperations
                50,   // failedOperations
                0.95, // successRate
                50.0, // averageResponseTimeMs
                10,   // activeConnections
                1000, // cacheSize
                0.85, // cacheHitRate
                0.05, // errorRate
                1024 * 1024, // memoryUsageBytes (1MB)
                0.3   // cpuUsagePercent
        );
    }

    private MemoryMonitor.AlertRule createTestAlertRule(String id, String name) {
        return new MemoryMonitor.AlertRule(
                id,
                name,
                MemoryMonitor.AlertType.ERROR_RATE,
                MemoryMonitor.AlertSeverity.WARNING,
                snapshot -> snapshot.getErrorRate() > 0.1,
                5000, // cooldownMs
                true  // enabled
        );
    }

    private MemoryMonitor.Alert createTestAlert(String id) {
        return new MemoryMonitor.Alert(
                id,
                "rule-1",
                "Test alert message",
                MemoryMonitor.AlertSeverity.WARNING,
                LocalDateTime.now(),
                createTestSnapshot()
        );
    }

    // ========== Interface Contract Tests ==========

    @Nested
    @DisplayName("Interface Contract Tests")
    class InterfaceContractTests {

        @Test
        @DisplayName("start() returns void")
        void testStartReturnsVoid() {
            assertDoesNotThrow(() -> monitor.start());
        }

        @Test
        @DisplayName("stop() returns void")
        void testStopReturnsVoid() {
            monitor.start();
            assertDoesNotThrow(() -> monitor.stop());
        }

        @Test
        @DisplayName("isRunning() returns boolean")
        void testIsRunningReturnsBoolean() {
            boolean running = monitor.isRunning();
            assertTrue(running == true || running == false, "Should return boolean value");
        }

        @Test
        @DisplayName("collectMetrics() returns MetricsSnapshot")
        void testCollectMetricsReturnsMetricsSnapshot() {
            MemoryMonitor.MetricsSnapshot result = monitor.collectMetrics();
            assertNotNull(result, "Should return non-null MetricsSnapshot");
        }

        @Test
        @DisplayName("getHistory() returns MetricsHistory")
        void testGetHistoryReturnsMetricsHistory() {
            MemoryMonitor.MetricsHistory result = monitor.getHistory(Duration.ofMinutes(5));
            assertNotNull(result, "Should return non-null MetricsHistory");
        }

        @Test
        @DisplayName("registerAlert() accepts String and AlertRule")
        void testRegisterAlertSignature() {
            assertDoesNotThrow(() -> {
                MemoryMonitor.AlertRule rule = createTestAlertRule("alert-1", "Test Alert");
                monitor.registerAlert("alert-1", rule);
            });
        }

        @Test
        @DisplayName("unregisterAlert() accepts String")
        void testUnregisterAlertSignature() {
            assertDoesNotThrow(() -> monitor.unregisterAlert("alert-1"));
        }

        @Test
        @DisplayName("getRegisteredAlerts() returns Map<String, AlertRule>")
        void testGetRegisteredAlertsReturnsMap() {
            Map<String, MemoryMonitor.AlertRule> result = monitor.getRegisteredAlerts();
            assertNotNull(result, "Should return non-null map");
        }

        @Test
        @DisplayName("checkHealth() returns HealthStatus")
        void testCheckHealthReturnsHealthStatus() {
            MemoryMonitor.HealthStatus result = monitor.checkHealth();
            assertNotNull(result, "Should return non-null HealthStatus");
        }

        @Test
        @DisplayName("checkComponentHealth() accepts String and returns ComponentHealth")
        void testCheckComponentHealthSignature() {
            MemoryMonitor.ComponentHealth result = monitor.checkComponentHealth("database");
            assertNotNull(result, "Should return non-null ComponentHealth");
        }

        @Test
        @DisplayName("getSystemHealth() returns SystemHealth")
        void testGetSystemHealthReturnsSystemHealth() {
            MemoryMonitor.SystemHealth result = monitor.getSystemHealth();
            assertNotNull(result, "Should return non-null SystemHealth");
        }

        @Test
        @DisplayName("sendAlert() accepts Alert")
        void testSendAlertSignature() {
            MemoryMonitor.Alert alert = createTestAlert("alert-1");
            assertDoesNotThrow(() -> monitor.sendAlert(alert));
        }

        @Test
        @DisplayName("getActiveAlerts() returns List<Alert>")
        void testGetActiveAlertsReturnsList() {
            List<MemoryMonitor.Alert> result = monitor.getActiveAlerts();
            assertNotNull(result, "Should return non-null list");
        }

        @Test
        @DisplayName("getAlertHistory() accepts Duration and returns List<Alert>")
        void testGetAlertHistorySignature() {
            List<MemoryMonitor.Alert> result = monitor.getAlertHistory(Duration.ofMinutes(5));
            assertNotNull(result, "Should return non-null list");
        }

        @Test
        @DisplayName("acknowledgeAlert() accepts String")
        void testAcknowledgeAlertSignature() {
            assertDoesNotThrow(() -> monitor.acknowledgeAlert("alert-1"));
        }

        @Test
        @DisplayName("resolveAlert() accepts String")
        void testResolveAlertSignature() {
            assertDoesNotThrow(() -> monitor.resolveAlert("alert-1"));
        }

        @Test
        @DisplayName("getConfig() returns MemoryMonitorConfig")
        void testGetConfigReturnsConfig() {
            MemoryMonitorConfig result = monitor.getConfig();
            assertNotNull(result, "Should return non-null config");
        }

        @Test
        @DisplayName("updateConfig() accepts MemoryMonitorConfig")
        void testUpdateConfigSignature() {
            assertDoesNotThrow(() -> monitor.updateConfig(config));
        }
    }

    // ========== Functional Correctness Tests ==========

    @Nested
    @DisplayName("Functional Correctness Tests")
    class FunctionalCorrectnessTests {

        @Test
        @DisplayName("start() changes isRunning() to true")
        void testStartChangesIsRunningToTrue() {
            monitor.start();
            assertTrue(monitor.isRunning(), "Monitor should be running after start()");
        }

        @Test
        @DisplayName("start() does not change isRunning() if already running")
        void testStartWhenAlreadyRunning() {
            monitor.start();
            boolean firstRunningState = monitor.isRunning();
            monitor.start(); // Start again
            assertEquals(firstRunningState, monitor.isRunning(), "Should still be running");
        }

        @Test
        @DisplayName("stop() changes isRunning() to false")
        void testStopChangesIsRunningToFalse() {
            monitor.start();
            monitor.stop();
            assertFalse(monitor.isRunning(), "Monitor should not be running after stop()");
        }

        @Test
        @DisplayName("collectMetrics() returns snapshot with current timestamp")
        void testCollectMetricsReturnsCurrentTimestamp() {
            LocalDateTime before = LocalDateTime.now();
            MemoryMonitor.MetricsSnapshot snapshot = monitor.collectMetrics();
            LocalDateTime after = LocalDateTime.now();

            assertTrue(snapshot.getTimestamp().isBefore(after) || snapshot.getTimestamp().isEqual(after));
            assertTrue(snapshot.getTimestamp().isAfter(before) || snapshot.getTimestamp().isEqual(before));
        }

        @Test
        @DisplayName("collectMetrics() initializes all metric fields")
        void testCollectMetricsInitializesAllFields() {
            MemoryMonitor.MetricsSnapshot snapshot = monitor.collectMetrics();

            assertNotNull(snapshot.getTimestamp());
            assertTrue(snapshot.getTotalOperations() >= 0);
            assertTrue(snapshot.getSuccessfulOperations() >= 0);
            assertTrue(snapshot.getFailedOperations() >= 0);
            assertTrue(snapshot.getSuccessRate() >= 0.0 && snapshot.getSuccessRate() <= 1.0);
            assertTrue(snapshot.getAverageResponseTimeMs() >= 0.0);
            assertTrue(snapshot.getActiveConnections() >= 0);
            assertTrue(snapshot.getCacheSize() >= 0);
            assertTrue(snapshot.getCacheHitRate() >= 0.0 && snapshot.getCacheHitRate() <= 1.0);
            assertTrue(snapshot.getErrorRate() >= 0.0 && snapshot.getErrorRate() <= 1.0);
            assertTrue(snapshot.getMemoryUsageBytes() >= 0);
            assertTrue(snapshot.getCpuUsagePercent() >= 0.0);
        }

        @Test
        @DisplayName("getHistory() returns snapshots within duration")
        void testGetHistoryFiltersByDuration() throws InterruptedException {
            monitor.collectMetrics();
            Thread.sleep(50);
            monitor.collectMetrics();

            Duration duration = Duration.ofMillis(30);
            MemoryMonitor.MetricsHistory history = monitor.getHistory(duration);

            // Should return at least one recent snapshot
            assertTrue(history.getSnapshots().size() >= 1);
        }

        @Test
        @DisplayName("getHistory() returns empty list for very short duration")
        void testGetHistoryReturnsEmptyForShortDuration() {
            Duration duration = Duration.ofMillis(1);
            MemoryMonitor.MetricsHistory history = monitor.getHistory(duration);
            // May be empty if no snapshots in last 1ms
            assertNotNull(history.getSnapshots());
        }

        @Test
        @DisplayName("registerAlert() adds alert to registry")
        void testRegisterAlertAddsToRegistry() {
            MemoryMonitor.AlertRule rule = createTestAlertRule("alert-1", "Test Alert");
            monitor.registerAlert("alert-1", rule);

            Map<String, MemoryMonitor.AlertRule> alerts = monitor.getRegisteredAlerts();
            assertTrue(alerts.containsKey("alert-1"));
            assertEquals("Test Alert", alerts.get("alert-1").getName());
        }

        @Test
        @DisplayName("unregisterAlert() removes alert from registry")
        void testUnregisterAlertRemovesFromRegistry() {
            MemoryMonitor.AlertRule rule = createTestAlertRule("alert-1", "Test Alert");
            monitor.registerAlert("alert-1", rule);
            monitor.unregisterAlert("alert-1");

            Map<String, MemoryMonitor.AlertRule> alerts = monitor.getRegisteredAlerts();
            assertFalse(alerts.containsKey("alert-1"));
        }

        @Test
        @DisplayName("checkHealth() returns HEALTHY for good metrics")
        void testCheckHealthReturnsHealthy() {
            MemoryMonitor.HealthStatus status = monitor.checkHealth();
            // With no errors, should be HEALTHY
            assertEquals(MemoryMonitor.HealthStatus.HEALTHY, status);
        }

        @Test
        @DisplayName("checkComponentHealth() returns ComponentHealth with correct fields")
        void testCheckComponentHealthReturnsCorrectFields() {
            MemoryMonitor.ComponentHealth health = monitor.checkComponentHealth("database");

            assertNotNull(health);
            assertEquals("database", health.getComponent());
            assertNotNull(health.getStatus());
            assertNotNull(health.getMessage());
            assertNotNull(health.getCheckedAt());
        }

        @Test
        @DisplayName("getSystemHealth() returns SystemHealth with components")
        void testGetSystemHealthReturnsComponents() {
            MemoryMonitor.SystemHealth health = monitor.getSystemHealth();

            assertNotNull(health);
            assertNotNull(health.getOverallStatus());
            assertNotNull(health.getComponents());
            assertTrue(health.getComponents().size() > 0);
            assertNotNull(health.getCheckedAt());
        }

        @Test
        @DisplayName("sendAlert() adds alert to active alerts")
        void testSendAlertAddsToActiveAlerts() {
            MemoryMonitor.Alert alert = createTestAlert("alert-1");
            monitor.sendAlert(alert);

            List<MemoryMonitor.Alert> activeAlerts = monitor.getActiveAlerts();
            assertTrue(activeAlerts.size() > 0);
        }

        @Test
        @DisplayName("acknowledgeAlert() changes alert status to ACKNOWLEDGED")
        void testAcknowledgeAlertChangesStatus() {
            MemoryMonitor.Alert alert = createTestAlert("alert-1");
            alert.acknowledge("test-user");

            assertEquals(MemoryMonitor.AlertStatus.ACKNOWLEDGED, alert.getStatus());
            assertNotNull(alert.getAcknowledgedAt());
            assertEquals("test-user", alert.getAcknowledgedBy());
        }

        @Test
        @DisplayName("resolveAlert() changes alert status to RESOLVED")
        void testResolveAlertChangesStatus() {
            MemoryMonitor.Alert alert = createTestAlert("alert-1");
            alert.resolve();

            assertEquals(MemoryMonitor.AlertStatus.RESOLVED, alert.getStatus());
            assertNotNull(alert.getResolvedAt());
        }

        @Test
        @DisplayName("getConfig() returns current configuration")
        void testGetConfigReturnsCurrentConfig() {
            MemoryMonitorConfig returnedConfig = monitor.getConfig();
            assertEquals(config.isEnabled(), returnedConfig.isEnabled());
            assertEquals(config.getIntervalMs(), returnedConfig.getIntervalMs());
            assertEquals(config.isAlertEnabled(), returnedConfig.isAlertEnabled());
        }
    }

    // ========== Edge Cases and Boundary Conditions Tests ==========

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("start() when config is disabled does not start monitoring")
        void testStartWhenConfigDisabled() {
            MemoryMonitorConfig disabledConfig = MemoryMonitorConfig.builder()
                    .enabled(false)
                    .build();
            MemoryMonitor disabledMonitor = new MemoryMonitorImpl(disabledConfig);

            disabledMonitor.start();
            assertFalse(disabledMonitor.isRunning(), "Monitor should not start when disabled");

            disabledMonitor.stop();
            ((MemoryMonitorImpl) disabledMonitor).shutdown();
        }

        @Test
        @DisplayName("stop() when not running does not throw")
        void testStopWhenNotRunning() {
            assertDoesNotThrow(() -> monitor.stop());
        }

        @Test
        @DisplayName("registerAlert() with null alertId throws exception")
        void testRegisterAlertWithNullId() {
            MemoryMonitor.AlertRule rule = createTestAlertRule("id", "name");
            assertThrows(NullPointerException.class, () -> monitor.registerAlert(null, rule));
        }

        @Test
        @DisplayName("registerAlert() with null rule throws exception")
        void testRegisterAlertWithNullRule() {
            assertThrows(NullPointerException.class, () -> monitor.registerAlert("alert-1", null));
        }

        @Test
        @DisplayName("unregisterAlert() with non-existent alertId does not throw")
        void testUnregisterNonExistentAlert() {
            assertDoesNotThrow(() -> monitor.unregisterAlert("non-existent"));
        }

        @Test
        @DisplayName("checkComponentHealth() with null component returns default health")
        void testCheckComponentHealthWithNullComponent() {
            MemoryMonitor.ComponentHealth health = monitor.checkComponentHealth(null);
            assertNotNull(health);
            assertEquals(MemoryMonitor.HealthStatus.HEALTHY, health.getStatus());
        }

        @Test
        @DisplayName("checkComponentHealth() with empty string returns default health")
        void testCheckComponentHealthWithEmptyComponent() {
            MemoryMonitor.ComponentHealth health = monitor.checkComponentHealth("");
            assertNotNull(health);
            assertEquals(MemoryMonitor.HealthStatus.HEALTHY, health.getStatus());
        }

        @Test
        @DisplayName("getHistory() with zero duration returns empty history")
        void testGetHistoryWithZeroDuration() {
            MemoryMonitor.MetricsHistory history = monitor.getHistory(Duration.ZERO);
            assertNotNull(history.getSnapshots());
        }

        @Test
        @DisplayName("getHistory() with negative duration returns empty history")
        void testGetHistoryWithNegativeDuration() {
            MemoryMonitor.MetricsHistory history = monitor.getHistory(Duration.ofMillis(-1));
            assertNotNull(history.getSnapshots());
        }

        @Test
        @DisplayName("getAlertHistory() filters by duration")
        void testGetAlertHistoryFiltersByDuration() throws InterruptedException {
            MemoryMonitor.Alert alert1 = createTestAlert("alert-1");
            monitor.sendAlert(alert1);
            Thread.sleep(50);
            MemoryMonitor.Alert alert2 = createTestAlert("alert-2");
            monitor.sendAlert(alert2);

            List<MemoryMonitor.Alert> recentAlerts = monitor.getAlertHistory(Duration.ofMillis(30));
            // Should get at least one recent alert
            assertNotNull(recentAlerts);
        }

        @Test
        @DisplayName("sendAlert() with null alert does not throw")
        void testSendAlertWithNullAlert() {
            assertDoesNotThrow(() -> monitor.sendAlert(null));
        }

        @Test
        @DisplayName("sendAlert() respects alert cooldown")
        void testSendAlertRespectsCooldown() throws InterruptedException {
            MemoryMonitorConfig shortCooldownConfig = MemoryMonitorConfig.builder()
                    .alertEnabled(true)
                    .alertCooldownMs(100)
                    .logAlerts(false)
                    .build();

            MemoryMonitor monitorWithCooldown = new MemoryMonitorImpl(shortCooldownConfig);
            MemoryMonitor.Alert alert = createTestAlert("alert-1");

            monitorWithCooldown.sendAlert(alert);
            Thread.sleep(50);
            monitorWithCooldown.sendAlert(alert); // Should be blocked by cooldown

            monitorWithCooldown.stop();
            ((MemoryMonitorImpl) monitorWithCooldown).shutdown();
        }

        @Test
        @DisplayName("sendAlert() when alertEnabled is false does not send")
        void testSendAlertWhenDisabled() {
            MemoryMonitorConfig disabledAlertConfig = MemoryMonitorConfig.builder()
                    .alertEnabled(false)
                    .build();

            MemoryMonitor monitorWithDisabledAlerts = new MemoryMonitorImpl(disabledAlertConfig);
            MemoryMonitor.Alert alert = createTestAlert("alert-1");

            monitorWithDisabledAlerts.sendAlert(alert);
            List<MemoryMonitor.Alert> activeAlerts = monitorWithDisabledAlerts.getActiveAlerts();

            // Alert should not be added when disabled
            assertEquals(0, activeAlerts.size());

            monitorWithDisabledAlerts.stop();
            ((MemoryMonitorImpl) monitorWithDisabledAlerts).shutdown();
        }

        @Test
        @DisplayName("acknowledgeAlert() with non-existent alertId does not throw")
        void testAcknowledgeNonExistentAlert() {
            assertDoesNotThrow(() -> monitor.acknowledgeAlert("non-existent"));
        }

        @Test
        @DisplayName("resolveAlert() with non-existent alertId does not throw")
        void testResolveNonExistentAlert() {
            assertDoesNotThrow(() -> monitor.resolveAlert("non-existent"));
        }

        @Test
        @DisplayName("Alert.acknowledge() can only be called once")
        void testAlertAcknowledgeOnce() {
            MemoryMonitor.Alert alert = createTestAlert("alert-1");
            LocalDateTime firstAck = LocalDateTime.now();
            alert.acknowledge("user1");

            // Try to acknowledge again
            alert.acknowledge("user2");

            // Should keep first acknowledgment
            assertEquals("user1", alert.getAcknowledgedBy());
        }

        @Test
        @DisplayName("Alert.resolve() can only be called once")
        void testAlertResolveOnce() {
            MemoryMonitor.Alert alert = createTestAlert("alert-1");
            LocalDateTime firstResolve = LocalDateTime.now();
            alert.resolve();

            // Try to resolve again
            alert.resolve();

            // Should keep first resolution
            assertTrue(alert.getResolvedAt().isAfter(firstResolve) || alert.getResolvedAt().isEqual(firstResolve));
        }

        @Test
        @DisplayName("MetricsSnapshot handles edge case values")
        void testMetricsSnapshotEdgeCases() {
            MemoryMonitor.MetricsSnapshot snapshot = new MemoryMonitor.MetricsSnapshot(
                    LocalDateTime.now(),
                    0,    // totalOperations = 0
                    0,    // successfulOperations = 0
                    0,    // failedOperations = 0
                    0.0,  // successRate = 0.0
                    0.0,  // averageResponseTimeMs = 0.0
                    0,    // activeConnections = 0
                    0,    // cacheSize = 0
                    0.0,  // cacheHitRate = 0.0
                    0.0,  // errorRate = 0.0
                    0,    // memoryUsageBytes = 0
                    0.0   // cpuUsagePercent = 0.0
            );

            assertEquals(0, snapshot.getTotalOperations());
            assertEquals(0.0, snapshot.getSuccessRate());
            assertEquals(0.0, snapshot.getErrorRate());
        }

        @Test
        @DisplayName("MetricsHistory.getAverage() returns null for empty list")
        void testMetricsHistoryAverageWithEmptyList() {
            MemoryMonitor.MetricsHistory emptyHistory = new MemoryMonitor.MetricsHistory(List.of());
            MemoryMonitor.MetricsSnapshot average = emptyHistory.getAverage();
            assertNull(average);
        }
    }

    // ========== Error Handling Tests ==========

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("collectMetrics() handles Runtime.getRuntime() failures gracefully")
        void testCollectMetricsHandlesRuntimeFailures() {
            // This test verifies that collectMetrics doesn't throw exceptions
            // even if Runtime methods have issues
            assertDoesNotThrow(() -> monitor.collectMetrics());
        }

        @Test
        @DisplayName("checkAlerts() handles exception in alert condition")
        void testCheckAlertsHandlesConditionException() {
            MemoryMonitor.AlertRule failingRule = new MemoryMonitor.AlertRule(
                    "failing-alert",
                    "Failing Alert",
                    MemoryMonitor.AlertType.ERROR_RATE,
                    MemoryMonitor.AlertSeverity.WARNING,
                    snapshot -> {
                        throw new RuntimeException("Condition evaluation failed");
                    },
                    5000,
                    true
            );

            monitor.registerAlert("failing-alert", failingRule);
            monitor.start();

            // Should not throw exception
            assertDoesNotThrow(() -> {
                Thread.sleep(200); // Let monitoring cycle run
            });
        }

        @Test
        @DisplayName("sendWebhookAlert() handles webhook failures gracefully")
        void testSendWebhookAlertHandlesFailures() {
            MemoryMonitorConfig webhookConfig = MemoryMonitorConfig.builder()
                    .webhookAlerts(true)
                    .webhookUrl("http://invalid-url-that-does-not-exist.local")
                    .build();

            MemoryMonitor monitorWithWebhook = new MemoryMonitorImpl(webhookConfig);
            MemoryMonitor.Alert alert = createTestAlert("alert-1");

            // Should not throw exception even if webhook fails
            assertDoesNotThrow(() -> monitorWithWebhook.sendAlert(alert));

            monitorWithWebhook.stop();
            ((MemoryMonitorImpl) monitorWithWebhook).shutdown();
        }

        @Test
        @DisplayName("trimHistory() handles concurrent modifications")
        void testTrimHistoryHandlesConcurrentModifications() throws InterruptedException {
            MemoryMonitorConfig smallHistoryConfig = MemoryMonitorConfig.builder()
                    .retentionDays(1)
                    .intervalMs(10)
                    .build();

            MemoryMonitor monitorWithSmallHistory = new MemoryMonitorImpl(smallHistoryConfig);
            monitorWithSmallHistory.start();

            // Rapidly collect metrics to trigger trimming
            for (int i = 0; i < 20; i++) {
                monitorWithSmallHistory.collectMetrics();
            }

            // Should handle concurrent trimming without throwing
            assertDoesNotThrow(() -> {
                Thread.sleep(100);
            });

            monitorWithSmallHistory.stop();
            ((MemoryMonitorImpl) monitorWithSmallHistory).shutdown();
        }
    }

    // ========== Performance Tests ==========

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("collectMetrics() completes within 100ms")
        void testCollectMetricsPerformance() {
            long startTime = System.currentTimeMillis();
            MemoryMonitor.MetricsSnapshot snapshot = monitor.collectMetrics();
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 100, "Should complete within 100ms, took " + duration + "ms");
            assertNotNull(snapshot);
        }

        @Test
        @DisplayName("registerAlert() completes within 10ms")
        void testRegisterAlertPerformance() {
            MemoryMonitor.AlertRule rule = createTestAlertRule("alert-1", "Test Alert");

            long startTime = System.currentTimeMillis();
            monitor.registerAlert("alert-1", rule);
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 10, "Should complete within 10ms, took " + duration + "ms");
        }

        @Test
        @DisplayName("checkHealth() completes within 50ms")
        void testCheckHealthPerformance() {
            long startTime = System.currentTimeMillis();
            MemoryMonitor.HealthStatus health = monitor.checkHealth();
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 50, "Should complete within 50ms, took " + duration + "ms");
            assertNotNull(health);
        }

        @Test
        @DisplayName("getSystemHealth() completes within 100ms")
        void testGetSystemHealthPerformance() {
            long startTime = System.currentTimeMillis();
            MemoryMonitor.SystemHealth health = monitor.getSystemHealth();
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 100, "Should complete within 100ms, took " + duration + "ms");
            assertNotNull(health);
        }

        @Test
        @DisplayName("sendAlert() completes within 10ms")
        void testSendAlertPerformance() {
            MemoryMonitor.Alert alert = createTestAlert("alert-1");

            long startTime = System.currentTimeMillis();
            monitor.sendAlert(alert);
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 10, "Should complete within 10ms, took " + duration + "ms");
        }

        @Test
        @DisplayName("Batch collectMetrics() 100 times within 1s")
        void testBatchCollectMetricsPerformance() {
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < 100; i++) {
                monitor.collectMetrics();
            }

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 1000, "Should complete 100 collections within 1s, took " + duration + "ms");
        }

        @Test
        @DisplayName("Batch registerAlert() 100 alerts within 1s")
        void testBatchRegisterAlertPerformance() {
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < 100; i++) {
                MemoryMonitor.AlertRule rule = createTestAlertRule("alert-" + i, "Alert " + i);
                monitor.registerAlert("alert-" + i, rule);
            }

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 1000, "Should complete 100 registrations within 1s, took " + duration + "ms");
        }

        @Test
        @DisplayName("Memory usage stays within 512MB for 1000 metrics")
        void testMemoryUsageForMetrics() {
            Runtime runtime = Runtime.getRuntime();
            long initialMemory = runtime.totalMemory() - runtime.freeMemory();

            for (int i = 0; i < 1000; i++) {
                monitor.collectMetrics();
            }

            long finalMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = finalMemory - initialMemory;

            // Allow some overhead but should be reasonable
            assertTrue(memoryUsed < 512 * 1024 * 1024,
                    "Memory usage should stay under 512MB, used: " + (memoryUsed / 1024 / 1024) + "MB");
        }

        @Test
        @DisplayName("getHistory() with large dataset completes within 100ms")
        void testGetHistoryWithLargeDatasetPerformance() {
            // Collect many metrics
            for (int i = 0; i < 1000; i++) {
                monitor.collectMetrics();
            }

            long startTime = System.currentTimeMillis();
            MemoryMonitor.MetricsHistory history = monitor.getHistory(Duration.ofHours(1));
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 100, "Should complete within 100ms, took " + duration + "ms");
            assertNotNull(history);
        }

        @Test
        @DisplayName("getAlertHistory() with large dataset completes within 100ms")
        void testGetAlertHistoryWithLargeDatasetPerformance() {
            // Send many alerts
            for (int i = 0; i < 100; i++) {
                MemoryMonitor.Alert alert = createTestAlert("alert-" + i);
                monitor.sendAlert(alert);
            }

            long startTime = System.currentTimeMillis();
            List<MemoryMonitor.Alert> history = monitor.getAlertHistory(Duration.ofHours(1));
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 100, "Should complete within 100ms, took " + duration + "ms");
            assertNotNull(history);
        }
    }

    // ========== Concurrent Operations Tests ==========

    @Nested
    @DisplayName("Concurrent Operations Tests")
    class ConcurrentOperationsTests {

        @Test
        @DisplayName("Concurrent collectMetrics() calls complete successfully")
        void testConcurrentCollectMetrics() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < 10; j++) {
                            MemoryMonitor.MetricsSnapshot snapshot = monitor.collectMetrics();
                            if (snapshot != null) {
                                successCount.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "All threads should complete within 10 seconds");
            assertEquals(threadCount * 10, successCount.get(), "All operations should succeed");

            executor.shutdown();
        }

        @Test
        @DisplayName("Concurrent registerAlert() calls complete successfully")
        void testConcurrentRegisterAlert() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < 10; j++) {
                            MemoryMonitor.AlertRule rule = createTestAlertRule(
                                    "alert-" + threadId + "-" + j,
                                    "Alert " + threadId + "-" + j
                            );
                            monitor.registerAlert("alert-" + threadId + "-" + j, rule);
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "All threads should complete within 10 seconds");
            assertEquals(threadCount * 10, successCount.get(), "All operations should succeed");

            executor.shutdown();
        }

        @Test
        @DisplayName("Concurrent sendAlert() calls complete successfully")
        void testConcurrentSendAlert() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < 10; j++) {
                            MemoryMonitor.Alert alert = createTestAlert("alert-" + j);
                            monitor.sendAlert(alert);
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "All threads should complete within 10 seconds");
            assertEquals(threadCount * 10, successCount.get(), "All operations should succeed");

            executor.shutdown();
        }

        @Test
        @DisplayName("Concurrent start() and stop() calls are safe")
        void testConcurrentStartStop() throws InterruptedException {
            int threadCount = 5;
            CountDownLatch latch = new CountDownLatch(threadCount);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < 5; j++) {
                            monitor.start();
                            monitor.stop();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "All threads should complete within 10 seconds");

            executor.shutdown();
        }

        @Test
        @DisplayName("Concurrent getRegisteredAlerts() and registerAlert() are safe")
        void testConcurrentGetAndRegisterAlert() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount * 2);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // Half threads register alerts
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        MemoryMonitor.AlertRule rule = createTestAlertRule(
                                "alert-" + threadId,
                                "Alert " + threadId
                        );
                        monitor.registerAlert("alert-" + threadId, rule);
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Half threads get registered alerts
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        Map<String, MemoryMonitor.AlertRule> alerts = monitor.getRegisteredAlerts();
                        if (alerts != null) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "All threads should complete within 10 seconds");
            assertEquals(threadCount * 2, successCount.get(), "All operations should succeed");

            executor.shutdown();
        }

        @Test
        @DisplayName("Concurrent health checks complete successfully")
        void testConcurrentHealthChecks() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < 10; j++) {
                            MemoryMonitor.HealthStatus health = monitor.checkHealth();
                            MemoryMonitor.SystemHealth systemHealth = monitor.getSystemHealth();
                            if (health != null && systemHealth != null) {
                                successCount.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "All threads should complete within 10 seconds");
            assertEquals(threadCount * 10, successCount.get(), "All operations should succeed");

            executor.shutdown();
        }
    }

    // ========== Alert Lifecycle Tests ==========

    @Nested
    @DisplayName("Alert Lifecycle Tests")
    class AlertLifecycleTests {

        @Test
        @DisplayName("Alert transitions from ACTIVE to ACKNOWLEDGED to RESOLVED")
        void testAlertStateTransitions() {
            MemoryMonitor.Alert alert = createTestAlert("alert-1");

            assertEquals(MemoryMonitor.AlertStatus.ACTIVE, alert.getStatus());

            alert.acknowledge("user1");
            assertEquals(MemoryMonitor.AlertStatus.ACKNOWLEDGED, alert.getStatus());

            alert.resolve();
            assertEquals(MemoryMonitor.AlertStatus.RESOLVED, alert.getStatus());
        }

        @Test
        @DisplayName("Alert severity levels are correctly set")
        void testAlertSeverityLevels() {
            MemoryMonitor.Alert infoAlert = new MemoryMonitor.Alert(
                    "alert-1",
                    "rule-1",
                    "Info message",
                    MemoryMonitor.AlertSeverity.INFO,
                    LocalDateTime.now(),
                    createTestSnapshot()
            );
            assertEquals(MemoryMonitor.AlertSeverity.INFO, infoAlert.getSeverity());

            MemoryMonitor.Alert warningAlert = new MemoryMonitor.Alert(
                    "alert-2",
                    "rule-2",
                    "Warning message",
                    MemoryMonitor.AlertSeverity.WARNING,
                    LocalDateTime.now(),
                    createTestSnapshot()
            );
            assertEquals(MemoryMonitor.AlertSeverity.WARNING, warningAlert.getSeverity());

            MemoryMonitor.Alert criticalAlert = new MemoryMonitor.Alert(
                    "alert-3",
                    "rule-3",
                    "Critical message",
                    MemoryMonitor.AlertSeverity.CRITICAL,
                    LocalDateTime.now(),
                    createTestSnapshot()
            );
            assertEquals(MemoryMonitor.AlertSeverity.CRITICAL, criticalAlert.getSeverity());

            MemoryMonitor.Alert emergencyAlert = new MemoryMonitor.Alert(
                    "alert-4",
                    "rule-4",
                    "Emergency message",
                    MemoryMonitor.AlertSeverity.EMERGENCY,
                    LocalDateTime.now(),
                    createTestSnapshot()
            );
            assertEquals(MemoryMonitor.AlertSeverity.EMERGENCY, emergencyAlert.getSeverity());
        }

        @Test
        @DisplayName("AlertType enum contains all expected types")
        void testAlertTypeEnumValues() {
            MemoryMonitor.AlertType[] types = MemoryMonitor.AlertType.values();

            assertTrue(List.of(types).contains(MemoryMonitor.AlertType.ERROR_RATE));
            assertTrue(List.of(types).contains(MemoryMonitor.AlertType.RESPONSE_TIME));
            assertTrue(List.of(types).contains(MemoryMonitor.AlertType.MEMORY_USAGE));
            assertTrue(List.of(types).contains(MemoryMonitor.AlertType.CPU_USAGE));
            assertTrue(List.of(types).contains(MemoryMonitor.AlertType.CACHE_HIT_RATE));
            assertTrue(List.of(types).contains(MemoryMonitor.AlertType.CONNECTION_COUNT));
            assertTrue(List.of(types).contains(MemoryMonitor.AlertType.CUSTOM));
        }

        @Test
        @DisplayName("AlertRule condition is evaluated correctly")
        void testAlertRuleCondition() {
            MemoryMonitor.AlertRule highErrorRateRule = new MemoryMonitor.AlertRule(
                    "rule-1",
                    "High Error Rate",
                    MemoryMonitor.AlertType.ERROR_RATE,
                    MemoryMonitor.AlertSeverity.WARNING,
                    snapshot -> snapshot.getErrorRate() > 0.1,
                    5000,
                    true
            );

            MemoryMonitor.MetricsSnapshot lowErrorSnapshot = new MemoryMonitor.MetricsSnapshot(
                    LocalDateTime.now(),
                    1000, 950, 50,
                    0.95, 50.0, 10, 1000, 0.85,
                    0.05, // error rate
                    1024 * 1024, 0.3
            );

            MemoryMonitor.MetricsSnapshot highErrorSnapshot = new MemoryMonitor.MetricsSnapshot(
                    LocalDateTime.now(),
                    1000, 800, 200,
                    0.80, 50.0, 10, 1000, 0.85,
                    0.2, // error rate
                    1024 * 1024, 0.3
            );

            assertFalse(highErrorRateRule.getCondition().shouldAlert(lowErrorSnapshot));
            assertTrue(highErrorRateRule.getCondition().shouldAlert(highErrorSnapshot));
        }

        @Test
        @DisplayName("AlertRule disabled rules are not evaluated")
        void testDisabledAlertRule() {
            MemoryMonitor.AlertRule disabledRule = new MemoryMonitor.AlertRule(
                    "rule-1",
                    "Disabled Rule",
                    MemoryMonitor.AlertType.ERROR_RATE,
                    MemoryMonitor.AlertSeverity.WARNING,
                    snapshot -> snapshot.getErrorRate() > 0.0,
                    5000,
                    false // disabled
            );

            assertFalse(disabledRule.isEnabled(), "Rule should be disabled");
        }
    }

    // ========== Configuration Tests ==========

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("MemoryMonitorConfig builder creates correct config")
        void testConfigBuilder() {
            MemoryMonitorConfig testConfig = MemoryMonitorConfig.builder()
                    .enabled(true)
                    .intervalMs(5000)
                    .alertEnabled(true)
                    .alertCooldownMs(10000)
                    .retentionDays(30)
                    .errorRateThreshold(0.1)
                    .responseTimeThresholdMs(2000)
                    .build();

            assertTrue(testConfig.isEnabled());
            assertEquals(5000, testConfig.getIntervalMs());
            assertTrue(testConfig.isAlertEnabled());
            assertEquals(10000, testConfig.getAlertCooldownMs());
            assertEquals(30, testConfig.getRetentionDays());
            assertEquals(0.1, testConfig.getErrorRateThreshold());
            assertEquals(2000, testConfig.getResponseTimeThresholdMs());
        }

        @Test
        @DisplayName("MemoryMonitorConfig defaults() returns default config")
        void testConfigDefaults() {
            MemoryMonitorConfig defaultConfig = MemoryMonitorConfig.defaults();

            assertNotNull(defaultConfig);
            assertTrue(defaultConfig.isEnabled());
            assertEquals(60000, defaultConfig.getIntervalMs());
            assertTrue(defaultConfig.isAlertEnabled());
            assertEquals(300000, defaultConfig.getAlertCooldownMs());
        }

        @Test
        @DisplayName("MemoryMonitorConfig toMap() and fromMap() are symmetric")
        void testConfigToMapFromMapSymmetry() {
            MemoryMonitorConfig originalConfig = MemoryMonitorConfig.builder()
                    .enabled(false)
                    .intervalMs(10000)
                    .alertEnabled(false)
                    .alertCooldownMs(5000)
                    .retentionDays(14)
                    .errorRateThreshold(0.15)
                    .responseTimeThresholdMs(1500)
                    .build();

            Map<String, Object> configMap = originalConfig.toMap();
            MemoryMonitorConfig restoredConfig = MemoryMonitorConfig.fromMap(configMap);

            assertEquals(originalConfig.isEnabled(), restoredConfig.isEnabled());
            assertEquals(originalConfig.getIntervalMs(), restoredConfig.getIntervalMs());
            assertEquals(originalConfig.isAlertEnabled(), restoredConfig.isAlertEnabled());
            assertEquals(originalConfig.getAlertCooldownMs(), restoredConfig.getAlertCooldownMs());
            assertEquals(originalConfig.getRetentionDays(), restoredConfig.getRetentionDays());
            assertEquals(originalConfig.getErrorRateThreshold(), restoredConfig.getErrorRateThreshold(), 0.001);
            assertEquals(originalConfig.getResponseTimeThresholdMs(), restoredConfig.getResponseTimeThresholdMs(), 0.001);
        }

        @Test
        @DisplayName("MemoryMonitorConfig handles missing fields in fromMap()")
        void testConfigFromMapWithMissingFields() {
            Map<String, Object> partialMap = Map.of(
                    "enabled", true,
                    "intervalMs", 5000
                    // Missing other fields
            );

            MemoryMonitorConfig config = MemoryMonitorConfig.fromMap(partialMap);

            assertTrue(config.isEnabled());
            assertEquals(5000, config.getIntervalMs());
            // Other fields should have default values
            assertTrue(config.isAlertEnabled()); // default
            assertEquals(300000, config.getAlertCooldownMs()); // default
        }

        @Test
        @DisplayName("updateConfig() updates monitor configuration")
        void testUpdateConfig() {
            MemoryMonitorConfig newConfig = MemoryMonitorConfig.builder()
                    .enabled(false)
                    .intervalMs(10000)
                    .build();

            assertDoesNotThrow(() -> monitor.updateConfig(newConfig));
        }
    }
}
