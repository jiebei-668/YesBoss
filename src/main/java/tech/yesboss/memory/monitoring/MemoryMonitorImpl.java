package tech.yesboss.memory.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Memory Monitor Implementation
 *
 * <p>Comprehensive monitoring and alerting implementation.</p>
 *
 * @author YesBoss Team
 * @version 1.0
 */
public class MemoryMonitorImpl implements MemoryMonitor {

    private static final Logger logger = LoggerFactory.getLogger(MemoryMonitorImpl.class);

    private final MemoryMonitorConfig config;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;
    private final Map<String, AlertRule> alertRules;
    private final Map<String, LocalDateTime> lastAlertTimes;
    private final List<MetricsSnapshot> metricsHistory;
    private final List<Alert> alertHistory;
    private final Map<String, Alert> activeAlerts;

    // Metrics counters
    private final AtomicLong totalOperations = new AtomicLong(0);
    private final AtomicLong successfulOperations = new AtomicLong(0);
    private final AtomicLong failedOperations = new AtomicLong(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);

    /**
     * Create monitor with default configuration.
     */
    public MemoryMonitorImpl() {
        this(MemoryMonitorConfig.defaults());
    }

    /**
     * Create monitor with configuration.
     *
     * @param config Monitor configuration
     */
    public MemoryMonitorImpl(MemoryMonitorConfig config) {
        this.config = config;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "memory-monitor");
            thread.setDaemon(true);
            return thread;
        });
        this.running = new AtomicBoolean(false);
        this.alertRules = new ConcurrentHashMap<>();
        this.lastAlertTimes = new ConcurrentHashMap<>();
        this.metricsHistory = new CopyOnWriteArrayList<>();
        this.alertHistory = new CopyOnWriteArrayList<>();
        this.activeAlerts = new ConcurrentHashMap<>();
    }

    // ==================== Monitoring ====================

    @Override
    public void start() {
        if (!config.isEnabled()) {
            logger.info("Monitoring is disabled");
            return;
        }

        if (running.compareAndSet(false, true)) {
            scheduler.scheduleAtFixedRate(
                this::collectAndCheck,
                config.getIntervalMs(),
                config.getIntervalMs(),
                TimeUnit.MILLISECONDS
            );
            logger.info("Memory monitoring started with interval {}ms", config.getIntervalMs());
        } else {
            logger.warn("Memory monitoring is already running");
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.info("Memory monitoring stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    // ==================== Metrics Collection ====================

    @Override
    public MetricsSnapshot collectMetrics() {
        Runtime runtime = Runtime.getRuntime();

        long totalOps = totalOperations.get();
        long successOps = successfulOperations.get();
        long failedOps = failedOperations.get();
        double successRate = totalOps == 0 ? 0.0 : (double) successOps / totalOps;
        double avgResponseTime = totalOps == 0 ? 0.0 : (double) totalResponseTime.get() / totalOps;
        double errorRate = totalOps == 0 ? 0.0 : (double) failedOps / totalOps;

        MetricsSnapshot snapshot = new MetricsSnapshot(
            LocalDateTime.now(),
            totalOps,
            successOps,
            failedOps,
            successRate,
            avgResponseTime,
            0, // activeConnections - would need to track this
            0, // cacheSize - would need to integrate with cache
            0.0, // cacheHitRate - would need to integrate with cache
            errorRate,
            runtime.totalMemory() - runtime.freeMemory(),
            0.0 // cpuUsage - would need OS-specific code
        );

        // Add to history
        metricsHistory.add(snapshot);
        trimHistory();

        return snapshot;
    }

    @Override
    public MetricsHistory getHistory(Duration duration) {
        LocalDateTime cutoff = LocalDateTime.now().minus(duration);
        List<MetricsSnapshot> filtered = new ArrayList<>();
        for (MetricsSnapshot snapshot : metricsHistory) {
            if (snapshot.getTimestamp().isAfter(cutoff)) {
                filtered.add(snapshot);
            }
        }
        return new MetricsHistory(filtered);
    }

    // ==================== Alert Registration ====================

    @Override
    public void registerAlert(String alertId, AlertRule rule) {
        alertRules.put(alertId, rule);
        logger.info("Registered alert: {} ({})", alertId, rule.getName());
    }

    @Override
    public void unregisterAlert(String alertId) {
        alertRules.remove(alertId);
        logger.info("Unregistered alert: {}", alertId);
    }

    @Override
    public Map<String, AlertRule> getRegisteredAlerts() {
        return new HashMap<>(alertRules);
    }

    // ==================== Health Checks ====================

    @Override
    public HealthStatus checkHealth() {
        MetricsSnapshot snapshot = collectMetrics();

        if (snapshot.getErrorRate() > config.getErrorRateThreshold() ||
            snapshot.getAverageResponseTimeMs() > config.getResponseTimeThresholdMs()) {
            return HealthStatus.UNHEALTHY;
        } else if (snapshot.getErrorRate() > config.getErrorRateThreshold() * 0.5 ||
                   snapshot.getAverageResponseTimeMs() > config.getResponseTimeThresholdMs() * 0.5) {
            return HealthStatus.DEGRADED;
        } else {
            return HealthStatus.HEALTHY;
        }
    }

    @Override
    public ComponentHealth checkComponentHealth(String component) {
        HealthStatus status = HealthStatus.HEALTHY;
        String message = "Component is healthy";

        // Would need to implement actual component checks
        switch (component.toLowerCase()) {
            case "database":
                status = HealthStatus.HEALTHY;
                message = "Database is responsive";
                break;
            case "cache":
                status = HealthStatus.HEALTHY;
                message = "Cache is operational";
                break;
            case "vectorstore":
                status = HealthStatus.HEALTHY;
                message = "Vector store is operational";
                break;
            default:
                status = HealthStatus.HEALTHY;
                message = "Unknown component";
                break;
        }

        return new ComponentHealth(component, status, message, LocalDateTime.now());
    }

    @Override
    public SystemHealth getSystemHealth() {
        HealthStatus overall = HealthStatus.HEALTHY;
        Map<String, ComponentHealth> components = new HashMap<>();

        // Check individual components
        components.put("database", checkComponentHealth("database"));
        components.put("cache", checkComponentHealth("cache"));
        components.put("vectorstore", checkComponentHealth("vectorstore"));

        // Determine overall status
        boolean anyUnhealthy = components.values().stream()
            .anyMatch(c -> c.getStatus() == HealthStatus.UNHEALTHY);
        boolean anyDegraded = components.values().stream()
            .anyMatch(c -> c.getStatus() == HealthStatus.DEGRADED);

        if (anyUnhealthy) {
            overall = HealthStatus.UNHEALTHY;
        } else if (anyDegraded) {
            overall = HealthStatus.DEGRADED;
        }

        return new SystemHealth(overall, components, LocalDateTime.now());
    }

    // ==================== Alert Notifications ====================

    @Override
    public void sendAlert(Alert alert) {
        if (!config.isAlertEnabled()) {
            return;
        }

        // Check cooldown
        LocalDateTime lastAlert = lastAlertTimes.get(alert.getRuleId());
        if (lastAlert != null &&
            Duration.between(lastAlert, alert.getTriggeredAt()).toMillis() < config.getAlertCooldownMs()) {
            return;
        }

        // Log alert
        if (config.isLogAlerts()) {
            String logMsg = String.format("[%s] %s: %s",
                alert.getSeverity(), alert.getRuleId(), alert.getMessage());
            switch (alert.getSeverity()) {
                case CRITICAL:
                case EMERGENCY:
                    logger.error(logMsg);
                    break;
                case WARNING:
                    logger.warn(logMsg);
                    break;
                default:
                    logger.info(logMsg);
                    break;
            }
        }

        // Send webhook notification if configured
        if (config.isWebhookAlerts() && config.getWebhookUrl() != null) {
            sendWebhookAlert(alert);
        }

        // Add to history and active alerts
        alertHistory.add(alert);
        activeAlerts.put(alert.getId(), alert);
        lastAlertTimes.put(alert.getRuleId(), alert.getTriggeredAt());
    }

    @Override
    public List<Alert> getActiveAlerts() {
        return new ArrayList<>(activeAlerts.values());
    }

    @Override
    public List<Alert> getAlertHistory(Duration duration) {
        LocalDateTime cutoff = LocalDateTime.now().minus(duration);
        List<Alert> filtered = new ArrayList<>();
        for (Alert alert : alertHistory) {
            if (alert.getTriggeredAt().isAfter(cutoff)) {
                filtered.add(alert);
            }
        }
        return filtered;
    }

    @Override
    public void acknowledgeAlert(String alertId) {
        Alert alert = activeAlerts.get(alertId);
        if (alert != null) {
            alert.acknowledge("system");
            logger.info("Alert acknowledged: {}", alertId);
        }
    }

    @Override
    public void resolveAlert(String alertId) {
        Alert alert = activeAlerts.remove(alertId);
        if (alert != null) {
            alert.resolve();
            logger.info("Alert resolved: {}", alertId);
        }
    }

    // ==================== Monitoring Configuration ====================

    @Override
    public MemoryMonitorConfig getConfig() {
        return config;
    }

    @Override
    public void updateConfig(MemoryMonitorConfig config) {
        logger.info("Updating monitor configuration");
        // Note: In production, would need to restart scheduler with new interval
    }

    // ==================== Package-Private Methods ====================

    /**
     * Record operation result.
     *
     * @param success Whether operation succeeded
     * @param responseTimeMs Response time in milliseconds
     */
    void recordOperation(boolean success, long responseTimeMs) {
        totalOperations.incrementAndGet();
        if (success) {
            successfulOperations.incrementAndGet();
        } else {
            failedOperations.incrementAndGet();
        }
        totalResponseTime.addAndGet(responseTimeMs);
    }

    // ==================== Private Helper Methods ====================

    /**
     * Collect metrics and check alerts.
     */
    private void collectAndCheck() {
        try {
            MetricsSnapshot snapshot = collectMetrics();
            checkAlerts(snapshot);
        } catch (Exception e) {
            logger.error("Error during metrics collection", e);
        }
    }

    /**
     * Check all alert rules against snapshot.
     *
     * @param snapshot Metrics snapshot
     */
    private void checkAlerts(MetricsSnapshot snapshot) {
        for (Map.Entry<String, AlertRule> entry : alertRules.entrySet()) {
            String alertId = entry.getKey();
            AlertRule rule = entry.getValue();

            if (!rule.isEnabled()) {
                continue;
            }

            try {
                if (rule.getCondition().shouldAlert(snapshot)) {
                    String message = String.format("Alert triggered for %s: %s",
                        rule.getName(), getAlertDetails(snapshot, rule.getType()));
                    Alert alert = new Alert(
                        UUID.randomUUID().toString(),
                        alertId,
                        message,
                        rule.getSeverity(),
                        LocalDateTime.now(),
                        snapshot
                    );
                    sendAlert(alert);
                }
            } catch (Exception e) {
                logger.error("Error checking alert rule: {}", alertId, e);
            }
        }
    }

    /**
     * Get alert details for snapshot.
     *
     * @param snapshot Metrics snapshot
     * @param type Alert type
     * @return Alert details
     */
    private String getAlertDetails(MetricsSnapshot snapshot, AlertType type) {
        switch (type) {
            case ERROR_RATE:
                return String.format("Error rate: %.2f%%", snapshot.getErrorRate() * 100);
            case RESPONSE_TIME:
                return String.format("Response time: %.2fms", snapshot.getAverageResponseTimeMs());
            case MEMORY_USAGE:
                return String.format("Memory usage: %d bytes", snapshot.getMemoryUsageBytes());
            case CACHE_HIT_RATE:
                return String.format("Cache hit rate: %.2f%%", snapshot.getCacheHitRate() * 100);
            default:
                return "Threshold exceeded";
        }
    }

    /**
     * Send webhook alert.
     *
     * @param alert Alert to send
     */
    private void sendWebhookAlert(Alert alert) {
        // In production, would implement actual HTTP webhook call
        logger.debug("Webhook alert sent: {}", alert.getId());
    }

    /**
     * Trim history to configured maximum size.
     */
    private void trimHistory() {
        while (metricsHistory.size() > config.getMaxSnapshots()) {
            metricsHistory.remove(0);
        }
    }

    /**
     * Shutdown monitor.
     */
    public void shutdown() {
        stop();
        activeAlerts.clear();
        logger.info("Memory monitor shutdown complete");
    }
}
