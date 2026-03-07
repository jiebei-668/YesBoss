package tech.yesboss.memory.monitoring;

import java.util.HashMap;
import java.util.Map;

/**
 * Memory Monitor Configuration
 *
 * <p>Configuration settings for monitoring and alerting.</p>
 *
 * <p><b>Configuration Properties:</b></p>
 * <ul>
 *   <li>enabled: Enable or disable monitoring</li>
 *   <li>intervalMs: Metrics collection interval</li>
 *   <li>alertEnabled: Enable alert notifications</li>
 *   <li>alertCooldownMs: Minimum time between alerts</li>
 *   <li>retentionDays: Metrics retention period</li>
 * </ul>
 *
 * @author YesBoss Team
 * @version 1.0
 */
public class MemoryMonitorConfig {

    // Core settings
    private boolean enabled = true;
    private long intervalMs = 60000; // 1 minute

    // Alert settings
    private boolean alertEnabled = true;
    private long alertCooldownMs = 300000; // 5 minutes

    // Retention settings
    private int retentionDays = 7;
    private int maxSnapshots = 10000;

    // Threshold settings
    private double errorRateThreshold = 0.05; // 5%
    private double responseTimeThresholdMs = 1000;
    private double memoryUsageThreshold = 0.8; // 80%
    private double cpuUsageThreshold = 0.8; // 80%
    private double cacheHitRateThreshold = 0.5; // 50%

    // Notification settings
    private boolean logAlerts = true;
    private boolean webhookAlerts = false;
    private String webhookUrl;

    /**
     * Private constructor - use builder
     */
    private MemoryMonitorConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.intervalMs = builder.intervalMs;
        this.alertEnabled = builder.alertEnabled;
        this.alertCooldownMs = builder.alertCooldownMs;
        this.retentionDays = builder.retentionDays;
        this.maxSnapshots = builder.maxSnapshots;
        this.errorRateThreshold = builder.errorRateThreshold;
        this.responseTimeThresholdMs = builder.responseTimeThresholdMs;
        this.memoryUsageThreshold = builder.memoryUsageThreshold;
        this.cpuUsageThreshold = builder.cpuUsageThreshold;
        this.cacheHitRateThreshold = builder.cacheHitRateThreshold;
        this.logAlerts = builder.logAlerts;
        this.webhookAlerts = builder.webhookAlerts;
        this.webhookUrl = builder.webhookUrl;
    }

    /**
     * Create a new builder.
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create default configuration.
     *
     * @return Default configuration
     */
    public static MemoryMonitorConfig defaults() {
        return builder().build();
    }

    /**
     * Create configuration from map.
     *
     * @param configMap Configuration map
     * @return Configuration instance
     */
    public static MemoryMonitorConfig fromMap(Map<String, Object> configMap) {
        Builder builder = builder();

        if (configMap.containsKey("enabled")) {
            builder.enabled(Boolean.parseBoolean(configMap.get("enabled").toString()));
        }
        if (configMap.containsKey("intervalMs")) {
            builder.intervalMs(Long.parseLong(configMap.get("intervalMs").toString()));
        }
        if (configMap.containsKey("alertEnabled")) {
            builder.alertEnabled(Boolean.parseBoolean(configMap.get("alertEnabled").toString()));
        }
        if (configMap.containsKey("alertCooldownMs")) {
            builder.alertCooldownMs(Long.parseLong(configMap.get("alertCooldownMs").toString()));
        }
        if (configMap.containsKey("retentionDays")) {
            builder.retentionDays(Integer.parseInt(configMap.get("retentionDays").toString()));
        }
        if (configMap.containsKey("errorRateThreshold")) {
            builder.errorRateThreshold(Double.parseDouble(configMap.get("errorRateThreshold").toString()));
        }
        if (configMap.containsKey("responseTimeThresholdMs")) {
            builder.responseTimeThresholdMs(Double.parseDouble(configMap.get("responseTimeThresholdMs").toString()));
        }

        return builder.build();
    }

    /**
     * Convert configuration to map.
     *
     * @return Configuration map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("enabled", enabled);
        map.put("intervalMs", intervalMs);
        map.put("alertEnabled", alertEnabled);
        map.put("alertCooldownMs", alertCooldownMs);
        map.put("retentionDays", retentionDays);
        map.put("maxSnapshots", maxSnapshots);
        map.put("errorRateThreshold", errorRateThreshold);
        map.put("responseTimeThresholdMs", responseTimeThresholdMs);
        map.put("memoryUsageThreshold", memoryUsageThreshold);
        map.put("cpuUsageThreshold", cpuUsageThreshold);
        map.put("cacheHitRateThreshold", cacheHitRateThreshold);
        map.put("logAlerts", logAlerts);
        map.put("webhookAlerts", webhookAlerts);
        map.put("webhookUrl", webhookUrl);
        return map;
    }

    // Getters
    public boolean isEnabled() { return enabled; }
    public long getIntervalMs() { return intervalMs; }
    public boolean isAlertEnabled() { return alertEnabled; }
    public long getAlertCooldownMs() { return alertCooldownMs; }
    public int getRetentionDays() { return retentionDays; }
    public int getMaxSnapshots() { return maxSnapshots; }
    public double getErrorRateThreshold() { return errorRateThreshold; }
    public double getResponseTimeThresholdMs() { return responseTimeThresholdMs; }
    public double getMemoryUsageThreshold() { return memoryUsageThreshold; }
    public double getCpuUsageThreshold() { return cpuUsageThreshold; }
    public double getCacheHitRateThreshold() { return cacheHitRateThreshold; }
    public boolean isLogAlerts() { return logAlerts; }
    public boolean isWebhookAlerts() { return webhookAlerts; }
    public String getWebhookUrl() { return webhookUrl; }

    /**
     * Builder for MemoryMonitorConfig
     */
    public static class Builder {
        private boolean enabled = true;
        private long intervalMs = 60000;
        private boolean alertEnabled = true;
        private long alertCooldownMs = 300000;
        private int retentionDays = 7;
        private int maxSnapshots = 10000;
        private double errorRateThreshold = 0.05;
        private double responseTimeThresholdMs = 1000;
        private double memoryUsageThreshold = 0.8;
        private double cpuUsageThreshold = 0.8;
        private double cacheHitRateThreshold = 0.5;
        private boolean logAlerts = true;
        private boolean webhookAlerts = false;
        private String webhookUrl;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder intervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
            return this;
        }

        public Builder alertEnabled(boolean alertEnabled) {
            this.alertEnabled = alertEnabled;
            return this;
        }

        public Builder alertCooldownMs(long alertCooldownMs) {
            this.alertCooldownMs = alertCooldownMs;
            return this;
        }

        public Builder retentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
            return this;
        }

        public Builder errorRateThreshold(double errorRateThreshold) {
            this.errorRateThreshold = errorRateThreshold;
            return this;
        }

        public Builder responseTimeThresholdMs(double responseTimeThresholdMs) {
            this.responseTimeThresholdMs = responseTimeThresholdMs;
            return this;
        }

        public Builder logAlerts(boolean logAlerts) {
            this.logAlerts = logAlerts;
            return this;
        }

        public Builder webhookAlerts(boolean webhookAlerts) {
            this.webhookAlerts = webhookAlerts;
            return this;
        }

        public Builder webhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
            return this;
        }

        public MemoryMonitorConfig build() {
            return new MemoryMonitorConfig(this);
        }
    }

    @Override
    public String toString() {
        return "MemoryMonitorConfig{" +
                "enabled=" + enabled +
                ", intervalMs=" + intervalMs +
                ", alertEnabled=" + alertEnabled +
                ", alertCooldownMs=" + alertCooldownMs +
                ", retentionDays=" + retentionDays +
                ", errorRateThreshold=" + errorRateThreshold +
                ", responseTimeThresholdMs=" + responseTimeThresholdMs +
                '}';
    }
}
