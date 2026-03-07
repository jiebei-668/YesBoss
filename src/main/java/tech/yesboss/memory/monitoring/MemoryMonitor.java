package tech.yesboss.memory.monitoring;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Memory Monitoring and Alerting Interface
 *
 * <p>Defines system monitoring and alerting capabilities.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Real-time metrics monitoring</li>
 *   <li>Threshold-based alerting</li>
 *   <li>Alert notifications</li>
 *   <li>Health checks</li>
 *   <li>Performance tracking</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * MemoryMonitor monitor = new MemoryMonitorImpl(config);
 * monitor.registerAlert("high_error_rate", AlertType.ERROR_RATE,
 *     condition -> condition.getErrorRate() > 0.05);
 * monitor.start();
 * </pre>
 *
 * @author YesBoss Team
 * @version 1.0
 */
public interface MemoryMonitor {

    // ==================== Monitoring ====================

    /**
     * Start monitoring.
     */
    void start();

    /**
     * Stop monitoring.
     */
    void stop();

    /**
     * Check if monitoring is active.
     *
     * @return true if active
     */
    boolean isRunning();

    // ==================== Metrics Collection ====================

    /**
     * Collect current metrics.
     *
     * @return Current metrics snapshot
     */
    MetricsSnapshot collectMetrics();

    /**
     * Get metrics history.
     *
     * @param duration Duration to retrieve
     * @return Metrics history
     */
    MetricsHistory getHistory(java.time.Duration duration);

    // ==================== Alert Registration ====================

    /**
     * Register alert rule.
     *
     * @param alertId Unique alert identifier
     * @param rule Alert rule
     */
    void registerAlert(String alertId, AlertRule rule);

    /**
     * Unregister alert rule.
     *
     * @param alertId Alert identifier
     */
    void unregisterAlert(String alertId);

    /**
     * Get all registered alerts.
     *
     * @return Map of alert IDs to rules
     */
    Map<String, AlertRule> getRegisteredAlerts();

    // ==================== Health Checks ====================

    /**
     * Perform health check.
     *
     * @return Health status
     */
    HealthStatus checkHealth();

    /**
     * Perform health check for specific component.
     *
     * @param component Component name
     * @return Component health status
     */
    ComponentHealth checkComponentHealth(String component);

    /**
     * Get overall system health.
     *
     * @return System health summary
     */
    SystemHealth getSystemHealth();

    // ==================== Alert Notifications ====================

    /**
     * Send alert notification.
     *
     * @param alert Alert to send
     */
    void sendAlert(Alert alert);

    /**
     * Get active alerts.
     *
     * @return List of active alerts
     */
    java.util.List<Alert> getActiveAlerts();

    /**
     * Get alert history.
     *
     * @param duration Duration to retrieve
     * @return Alert history
     */
    java.util.List<Alert> getAlertHistory(java.time.Duration duration);

    /**
     * Acknowledge alert.
     *
     * @param alertId Alert ID
     */
    void acknowledgeAlert(String alertId);

    /**
     * Resolve alert.
     *
     * @param alertId Alert ID
     */
    void resolveAlert(String alertId);

    // ==================== Monitoring Configuration ====================

    /**
     * Get monitor configuration.
     *
     * @return Configuration
     */
    MemoryMonitorConfig getConfig();

    /**
     * Update monitor configuration.
     *
     * @param config New configuration
     */
    void updateConfig(MemoryMonitorConfig config);

    // ==================== Inner Classes ====================

    /**
     * Metrics snapshot
     */
    class MetricsSnapshot {
        private final LocalDateTime timestamp;
        private final long totalOperations;
        private final long successfulOperations;
        private final long failedOperations;
        private final double successRate;
        private final double averageResponseTimeMs;
        private final long activeConnections;
        private final long cacheSize;
        private final double cacheHitRate;
        private final double errorRate;
        private final long memoryUsageBytes;
        private final double cpuUsagePercent;

        public MetricsSnapshot(LocalDateTime timestamp, long totalOperations,
                              long successfulOperations, long failedOperations,
                              double successRate, double averageResponseTimeMs,
                              long activeConnections, long cacheSize,
                              double cacheHitRate, double errorRate,
                              long memoryUsageBytes, double cpuUsagePercent) {
            this.timestamp = timestamp;
            this.totalOperations = totalOperations;
            this.successfulOperations = successfulOperations;
            this.failedOperations = failedOperations;
            this.successRate = successRate;
            this.averageResponseTimeMs = averageResponseTimeMs;
            this.activeConnections = activeConnections;
            this.cacheSize = cacheSize;
            this.cacheHitRate = cacheHitRate;
            this.errorRate = errorRate;
            this.memoryUsageBytes = memoryUsageBytes;
            this.cpuUsagePercent = cpuUsagePercent;
        }

        public LocalDateTime getTimestamp() { return timestamp; }
        public long getTotalOperations() { return totalOperations; }
        public long getSuccessfulOperations() { return successfulOperations; }
        public long getFailedOperations() { return failedOperations; }
        public double getSuccessRate() { return successRate; }
        public double getAverageResponseTimeMs() { return averageResponseTimeMs; }
        public long getActiveConnections() { return activeConnections; }
        public long getCacheSize() { return cacheSize; }
        public double getCacheHitRate() { return cacheHitRate; }
        public double getErrorRate() { return errorRate; }
        public long getMemoryUsageBytes() { return memoryUsageBytes; }
        public double getCpuUsagePercent() { return cpuUsagePercent; }
    }

    /**
     * Metrics history
     */
    class MetricsHistory {
        private final java.util.List<MetricsSnapshot> snapshots;

        public MetricsHistory(java.util.List<MetricsSnapshot> snapshots) {
            this.snapshots = snapshots;
        }

        public java.util.List<MetricsSnapshot> getSnapshots() {
            return snapshots;
        }

        public MetricsSnapshot getAverage() {
            if (snapshots.isEmpty()) {
                return null;
            }

            long totalOps = 0, successOps = 0, failedOps = 0;
            double avgResponseTime = 0, successRate = 0, cacheHitRate = 0, errorRate = 0;
            long activeConns = 0, cacheSize = 0, memUsage = 0;
            double cpuUsage = 0;

            for (MetricsSnapshot snapshot : snapshots) {
                totalOps += snapshot.getTotalOperations();
                successOps += snapshot.getSuccessfulOperations();
                failedOps += snapshot.getFailedOperations();
                avgResponseTime += snapshot.getAverageResponseTimeMs();
                successRate += snapshot.getSuccessRate();
                activeConns += snapshot.getActiveConnections();
                cacheSize += snapshot.getCacheSize();
                cacheHitRate += snapshot.getCacheHitRate();
                errorRate += snapshot.getErrorRate();
                memUsage += snapshot.getMemoryUsageBytes();
                cpuUsage += snapshot.getCpuUsagePercent();
            }

            int count = snapshots.size();
            return new MetricsSnapshot(
                LocalDateTime.now(),
                totalOps / count,
                successOps / count,
                failedOps / count,
                successRate / count,
                avgResponseTime / count,
                activeConns / count,
                cacheSize / count,
                cacheHitRate / count,
                errorRate / count,
                memUsage / count,
                cpuUsage / count
            );
        }
    }

    /**
     * Alert rule
     */
    class AlertRule {
        private final String id;
        private final String name;
        private final AlertType type;
        private final AlertSeverity severity;
        private final AlertCondition condition;
        private final long cooldownMs;
        private final boolean enabled;

        public AlertRule(String id, String name, AlertType type,
                       AlertSeverity severity, AlertCondition condition,
                       long cooldownMs, boolean enabled) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.severity = severity;
            this.condition = condition;
            this.cooldownMs = cooldownMs;
            this.enabled = enabled;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public AlertType getType() { return type; }
        public AlertSeverity getSeverity() { return severity; }
        public AlertCondition getCondition() { return condition; }
        public long getCooldownMs() { return cooldownMs; }
        public boolean isEnabled() { return enabled; }
    }

    /**
     * Alert condition
     */
    @FunctionalInterface
    interface AlertCondition {
        /**
         * Check if condition is met.
         *
         * @param snapshot Metrics snapshot
         * @return true if alert should be triggered
         */
        boolean shouldAlert(MetricsSnapshot snapshot);
    }

    /**
     * Alert type enumeration
     */
    enum AlertType {
        ERROR_RATE,
        RESPONSE_TIME,
        MEMORY_USAGE,
        CPU_USAGE,
        CACHE_HIT_RATE,
        CONNECTION_COUNT,
        CUSTOM
    }

    /**
     * Alert severity enumeration
     */
    enum AlertSeverity {
        INFO,
        WARNING,
        CRITICAL,
        EMERGENCY
    }

    /**
     * Alert
     */
    class Alert {
        private final String id;
        private final String ruleId;
        private final String message;
        private final AlertSeverity severity;
        private final LocalDateTime triggeredAt;
        private final MetricsSnapshot snapshot;
        private volatile LocalDateTime acknowledgedAt;
        private volatile LocalDateTime resolvedAt;
        private volatile String acknowledgedBy;
        private volatile AlertStatus status;

        public Alert(String id, String ruleId, String message,
                   AlertSeverity severity, LocalDateTime triggeredAt,
                   MetricsSnapshot snapshot) {
            this.id = id;
            this.ruleId = ruleId;
            this.message = message;
            this.severity = severity;
            this.triggeredAt = triggeredAt;
            this.snapshot = snapshot;
            this.status = AlertStatus.ACTIVE;
        }

        public String getId() { return id; }
        public String getRuleId() { return ruleId; }
        public String getMessage() { return message; }
        public AlertSeverity getSeverity() { return severity; }
        public LocalDateTime getTriggeredAt() { return triggeredAt; }
        public MetricsSnapshot getSnapshot() { return snapshot; }
        public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
        public LocalDateTime getResolvedAt() { return resolvedAt; }
        public String getAcknowledgedBy() { return acknowledgedBy; }
        public AlertStatus getStatus() { return status; }

        public void acknowledge(String user) {
            this.acknowledgedAt = LocalDateTime.now();
            this.acknowledgedBy = user;
            this.status = AlertStatus.ACKNOWLEDGED;
        }

        public void resolve() {
            this.resolvedAt = LocalDateTime.now();
            this.status = AlertStatus.RESOLVED;
        }
    }

    /**
     * Alert status enumeration
     */
    enum AlertStatus {
        ACTIVE,
        ACKNOWLEDGED,
        RESOLVED
    }

    /**
     * Health status enumeration
     */
    enum HealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY
    }

    /**
     * Component health
     */
    class ComponentHealth {
        private final String component;
        private final HealthStatus status;
        private final String message;
        private final LocalDateTime checkedAt;

        public ComponentHealth(String component, HealthStatus status,
                             String message, LocalDateTime checkedAt) {
            this.component = component;
            this.status = status;
            this.message = message;
            this.checkedAt = checkedAt;
        }

        public String getComponent() { return component; }
        public HealthStatus getStatus() { return status; }
        public String getMessage() { return message; }
        public LocalDateTime getCheckedAt() { return checkedAt; }
    }

    /**
     * System health
     */
    class SystemHealth {
        private final HealthStatus overallStatus;
        private final Map<String, ComponentHealth> components;
        private final LocalDateTime checkedAt;

        public SystemHealth(HealthStatus overallStatus,
                          Map<String, ComponentHealth> components,
                          LocalDateTime checkedAt) {
            this.overallStatus = overallStatus;
            this.components = components;
            this.checkedAt = checkedAt;
        }

        public HealthStatus getOverallStatus() { return overallStatus; }
        public Map<String, ComponentHealth> getComponents() { return components; }
        public LocalDateTime getCheckedAt() { return checkedAt; }
    }
}
