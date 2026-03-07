package tech.yesboss.memory.metrics;

import java.util.HashMap;
import java.util.Map;

/**
 * Memory Metrics Configuration
 *
 * <p>Configuration settings for memory performance monitoring metrics.</p>
 *
 * <p><b>Configuration Properties:</b></p>
 * <ul>
 *   <li>enabled: Enable or disable metrics collection</li>
 *   <li>trackPerformance: Track performance metrics (timing, throughput)</li>
 *   <li>trackErrors: Track error metrics (error counts, error rates)</li>
 *   <li>collectionIntervalSeconds: Interval for periodic metrics collection</li>
 *   <li>retentionHours: How long to retain metrics data</li>
 *   <li>exportEnabled: Enable metrics export to external systems</li>
 *   <li>exportFormat: Export format (JSON, Prometheus, etc.)</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * MemoryMetricsConfig config = MemoryMetricsConfig.builder()
 *     .enabled(true)
 *     .trackPerformance(true)
 *     .trackErrors(true)
 *     .collectionIntervalSeconds(60)
 *     .build();
 * </pre>
 *
 * @author YesBoss Team
 * @version 1.0
 */
public class MemoryMetricsConfig {

    // Core settings
    private boolean enabled = true;
    private boolean trackPerformance = true;
    private boolean trackErrors = true;

    // Collection settings
    private int collectionIntervalSeconds = 60;
    private int retentionHours = 24;

    // Export settings
    private boolean exportEnabled = false;
    private String exportFormat = "JSON";
    private String exportEndpoint;

    // Performance thresholds (for alerting)
    private long slowOperationThresholdMs = 1000;
    private double highErrorRateThreshold = 0.05; // 5%
    private double lowCacheHitRateThreshold = 0.5; // 50%

    // Batch processing settings
    private int batchSize = 100;
    private long batchTimeoutMs = 5000;

    /**
     * Private constructor - use builder
     */
    private MemoryMetricsConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.trackPerformance = builder.trackPerformance;
        this.trackErrors = builder.trackErrors;
        this.collectionIntervalSeconds = builder.collectionIntervalSeconds;
        this.retentionHours = builder.retentionHours;
        this.exportEnabled = builder.exportEnabled;
        this.exportFormat = builder.exportFormat;
        this.exportEndpoint = builder.exportEndpoint;
        this.slowOperationThresholdMs = builder.slowOperationThresholdMs;
        this.highErrorRateThreshold = builder.highErrorRateThreshold;
        this.lowCacheHitRateThreshold = builder.lowCacheHitRateThreshold;
        this.batchSize = builder.batchSize;
        this.batchTimeoutMs = builder.batchTimeoutMs;
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
    public static MemoryMetricsConfig defaults() {
        return builder().build();
    }

    /**
     * Create configuration from map.
     *
     * @param config Configuration map
     * @return Configuration instance
     */
    public static MemoryMetricsConfig fromMap(Map<String, Object> config) {
        Builder builder = builder();

        if (config.containsKey("enabled")) {
            builder.enabled = Boolean.parseBoolean(config.get("enabled").toString());
        }
        if (config.containsKey("trackPerformance")) {
            builder.trackPerformance = Boolean.parseBoolean(config.get("trackPerformance").toString());
        }
        if (config.containsKey("trackErrors")) {
            builder.trackErrors = Boolean.parseBoolean(config.get("trackErrors").toString());
        }
        if (config.containsKey("collectionIntervalSeconds")) {
            builder.collectionIntervalSeconds = Integer.parseInt(config.get("collectionIntervalSeconds").toString());
        }
        if (config.containsKey("retentionHours")) {
            builder.retentionHours = Integer.parseInt(config.get("retentionHours").toString());
        }
        if (config.containsKey("exportEnabled")) {
            builder.exportEnabled = Boolean.parseBoolean(config.get("exportEnabled").toString());
        }
        if (config.containsKey("exportFormat")) {
            builder.exportFormat = config.get("exportFormat").toString();
        }
        if (config.containsKey("exportEndpoint")) {
            builder.exportEndpoint = config.get("exportEndpoint").toString();
        }
        if (config.containsKey("slowOperationThresholdMs")) {
            builder.slowOperationThresholdMs = Long.parseLong(config.get("slowOperationThresholdMs").toString());
        }
        if (config.containsKey("highErrorRateThreshold")) {
            builder.highErrorRateThreshold = Double.parseDouble(config.get("highErrorRateThreshold").toString());
        }
        if (config.containsKey("lowCacheHitRateThreshold")) {
            builder.lowCacheHitRateThreshold = Double.parseDouble(config.get("lowCacheHitRateThreshold").toString());
        }
        if (config.containsKey("batchSize")) {
            builder.batchSize = Integer.parseInt(config.get("batchSize").toString());
        }
        if (config.containsKey("batchTimeoutMs")) {
            builder.batchTimeoutMs = Long.parseLong(config.get("batchTimeoutMs").toString());
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
        map.put("trackPerformance", trackPerformance);
        map.put("trackErrors", trackErrors);
        map.put("collectionIntervalSeconds", collectionIntervalSeconds);
        map.put("retentionHours", retentionHours);
        map.put("exportEnabled", exportEnabled);
        map.put("exportFormat", exportFormat);
        map.put("exportEndpoint", exportEndpoint);
        map.put("slowOperationThresholdMs", slowOperationThresholdMs);
        map.put("highErrorRateThreshold", highErrorRateThreshold);
        map.put("lowCacheHitRateThreshold", lowCacheHitRateThreshold);
        map.put("batchSize", batchSize);
        map.put("batchTimeoutMs", batchTimeoutMs);
        return map;
    }

    // Getters
    public boolean isEnabled() {
        return enabled;
    }

    public boolean isTrackPerformance() {
        return trackPerformance;
    }

    public boolean isTrackErrors() {
        return trackErrors;
    }

    public int getCollectionIntervalSeconds() {
        return collectionIntervalSeconds;
    }

    public int getRetentionHours() {
        return retentionHours;
    }

    public boolean isExportEnabled() {
        return exportEnabled;
    }

    public String getExportFormat() {
        return exportFormat;
    }

    public String getExportEndpoint() {
        return exportEndpoint;
    }

    public long getSlowOperationThresholdMs() {
        return slowOperationThresholdMs;
    }

    public double getHighErrorRateThreshold() {
        return highErrorRateThreshold;
    }

    public double getLowCacheHitRateThreshold() {
        return lowCacheHitRateThreshold;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public long getBatchTimeoutMs() {
        return batchTimeoutMs;
    }

    /**
     * Builder for MemoryMetricsConfig.
     */
    public static class Builder {
        private boolean enabled = true;
        private boolean trackPerformance = true;
        private boolean trackErrors = true;
        private int collectionIntervalSeconds = 60;
        private int retentionHours = 24;
        private boolean exportEnabled = false;
        private String exportFormat = "JSON";
        private String exportEndpoint;
        private long slowOperationThresholdMs = 1000;
        private double highErrorRateThreshold = 0.05;
        private double lowCacheHitRateThreshold = 0.5;
        private int batchSize = 100;
        private long batchTimeoutMs = 5000;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder trackPerformance(boolean trackPerformance) {
            this.trackPerformance = trackPerformance;
            return this;
        }

        public Builder trackErrors(boolean trackErrors) {
            this.trackErrors = trackErrors;
            return this;
        }

        public Builder collectionIntervalSeconds(int collectionIntervalSeconds) {
            this.collectionIntervalSeconds = collectionIntervalSeconds;
            return this;
        }

        public Builder retentionHours(int retentionHours) {
            this.retentionHours = retentionHours;
            return this;
        }

        public Builder exportEnabled(boolean exportEnabled) {
            this.exportEnabled = exportEnabled;
            return this;
        }

        public Builder exportFormat(String exportFormat) {
            this.exportFormat = exportFormat;
            return this;
        }

        public Builder exportEndpoint(String exportEndpoint) {
            this.exportEndpoint = exportEndpoint;
            return this;
        }

        public Builder slowOperationThresholdMs(long slowOperationThresholdMs) {
            this.slowOperationThresholdMs = slowOperationThresholdMs;
            return this;
        }

        public Builder highErrorRateThreshold(double highErrorRateThreshold) {
            this.highErrorRateThreshold = highErrorRateThreshold;
            return this;
        }

        public Builder lowCacheHitRateThreshold(double lowCacheHitRateThreshold) {
            this.lowCacheHitRateThreshold = lowCacheHitRateThreshold;
            return this;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder batchTimeoutMs(long batchTimeoutMs) {
            this.batchTimeoutMs = batchTimeoutMs;
            return this;
        }

        public MemoryMetricsConfig build() {
            return new MemoryMetricsConfig(this);
        }
    }

    @Override
    public String toString() {
        return "MemoryMetricsConfig{" +
                "enabled=" + enabled +
                ", trackPerformance=" + trackPerformance +
                ", trackErrors=" + trackErrors +
                ", collectionIntervalSeconds=" + collectionIntervalSeconds +
                ", retentionHours=" + retentionHours +
                ", exportEnabled=" + exportEnabled +
                ", exportFormat='" + exportFormat + '\'' +
                ", exportEndpoint='" + exportEndpoint + '\'' +
                ", slowOperationThresholdMs=" + slowOperationThresholdMs +
                ", highErrorRateThreshold=" + highErrorRateThreshold +
                ", lowCacheHitRateThreshold=" + lowCacheHitRateThreshold +
                ", batchSize=" + batchSize +
                ", batchTimeoutMs=" + batchTimeoutMs +
                '}';
    }
}
