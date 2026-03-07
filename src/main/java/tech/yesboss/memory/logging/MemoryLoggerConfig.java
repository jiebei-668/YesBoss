package tech.yesboss.memory.logging;

import java.util.HashMap;
import java.util.Map;

/**
 * Memory Logger Configuration
 *
 * <p>Configuration settings for memory logger behavior.</p>
 *
 * <p><b>Configuration Properties:</b></p>
 * <ul>
 *   <li>enabled: Enable or disable logging</li>
 *   <li>level: Default log level</li>
 *   <li>includeContext: Include context data in logs</li>
 *   <li>includeStackTrace: Include stack traces in error logs</li>
 *   <li>asyncLogging: Use asynchronous logging</li>
 *   <li>auditEnabled: Enable audit logging</li>
 *   <li>performanceLoggingEnabled: Enable performance logging</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * MemoryLoggerConfig config = MemoryLoggerConfig.builder()
 *     .level(MemoryLogger.LogLevel.INFO)
 *     .includeContext(true)
 *     .asyncLogging(true)
 *     .build();
 * </pre>
 *
 * @author YesBoss Team
 * @version 1.0
 */
public class MemoryLoggerConfig {

    // Core settings
    private boolean enabled = true;
    private MemoryLogger.LogLevel level = MemoryLogger.LogLevel.INFO;

    // Format settings
    private boolean includeContext = true;
    private boolean includeStackTrace = true;
    private boolean includeTimestamp = true;
    private boolean includeLoggerName = true;
    private boolean includeThreadName = false;

    // Performance settings
    private boolean asyncLogging = false;
    private int asyncQueueSize = 1000;
    private int asyncThreadCount = 1;

    // Feature settings
    private boolean auditEnabled = true;
    private boolean performanceLoggingEnabled = true;
    private boolean operationTrackingEnabled = true;

    // Output settings
    private boolean consoleOutput = true;
    private boolean fileOutput = false;
    private String filePath;
    private boolean jsonOutput = false;

    /**
     * Private constructor - use builder
     */
    private MemoryLoggerConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.level = builder.level;
        this.includeContext = builder.includeContext;
        this.includeStackTrace = builder.includeStackTrace;
        this.includeTimestamp = builder.includeTimestamp;
        this.includeLoggerName = builder.includeLoggerName;
        this.includeThreadName = builder.includeThreadName;
        this.asyncLogging = builder.asyncLogging;
        this.asyncQueueSize = builder.asyncQueueSize;
        this.asyncThreadCount = builder.asyncThreadCount;
        this.auditEnabled = builder.auditEnabled;
        this.performanceLoggingEnabled = builder.performanceLoggingEnabled;
        this.operationTrackingEnabled = builder.operationTrackingEnabled;
        this.consoleOutput = builder.consoleOutput;
        this.fileOutput = builder.fileOutput;
        this.filePath = builder.filePath;
        this.jsonOutput = builder.jsonOutput;
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
    public static MemoryLoggerConfig defaults() {
        return builder().build();
    }

    /**
     * Create configuration from map.
     *
     * @param configMap Configuration map
     * @return Configuration instance
     */
    public static MemoryLoggerConfig fromMap(Map<String, Object> configMap) {
        Builder builder = builder();

        if (configMap.containsKey("enabled")) {
            builder.enabled(Boolean.parseBoolean(configMap.get("enabled").toString()));
        }
        if (configMap.containsKey("level")) {
            String levelStr = configMap.get("level").toString();
            builder.level(MemoryLogger.LogLevel.valueOf(levelStr.toUpperCase()));
        }
        if (configMap.containsKey("includeContext")) {
            builder.includeContext(Boolean.parseBoolean(configMap.get("includeContext").toString()));
        }
        if (configMap.containsKey("includeStackTrace")) {
            builder.includeStackTrace(Boolean.parseBoolean(configMap.get("includeStackTrace").toString()));
        }
        if (configMap.containsKey("asyncLogging")) {
            builder.asyncLogging(Boolean.parseBoolean(configMap.get("asyncLogging").toString()));
        }
        if (configMap.containsKey("auditEnabled")) {
            builder.auditEnabled(Boolean.parseBoolean(configMap.get("auditEnabled").toString()));
        }
        if (configMap.containsKey("performanceLoggingEnabled")) {
            builder.performanceLoggingEnabled(Boolean.parseBoolean(
                configMap.get("performanceLoggingEnabled").toString()));
        }
        if (configMap.containsKey("operationTrackingEnabled")) {
            builder.operationTrackingEnabled(Boolean.parseBoolean(
                configMap.get("operationTrackingEnabled").toString()));
        }
        if (configMap.containsKey("asyncQueueSize")) {
            builder.asyncQueueSize(Integer.parseInt(configMap.get("asyncQueueSize").toString()));
        }
        if (configMap.containsKey("asyncThreadCount")) {
            builder.asyncThreadCount(Integer.parseInt(configMap.get("asyncThreadCount").toString()));
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
        map.put("level", level.toString());
        map.put("includeContext", includeContext);
        map.put("includeStackTrace", includeStackTrace);
        map.put("includeTimestamp", includeTimestamp);
        map.put("includeLoggerName", includeLoggerName);
        map.put("includeThreadName", includeThreadName);
        map.put("asyncLogging", asyncLogging);
        map.put("asyncQueueSize", asyncQueueSize);
        map.put("asyncThreadCount", asyncThreadCount);
        map.put("auditEnabled", auditEnabled);
        map.put("performanceLoggingEnabled", performanceLoggingEnabled);
        map.put("operationTrackingEnabled", operationTrackingEnabled);
        map.put("consoleOutput", consoleOutput);
        map.put("fileOutput", fileOutput);
        map.put("filePath", filePath);
        map.put("jsonOutput", jsonOutput);
        return map;
    }

    // Getters
    public boolean isEnabled() {
        return enabled;
    }

    public MemoryLogger.LogLevel getLevel() {
        return level;
    }

    public boolean isIncludeContext() {
        return includeContext;
    }

    public boolean isIncludeStackTrace() {
        return includeStackTrace;
    }

    public boolean isIncludeTimestamp() {
        return includeTimestamp;
    }

    public boolean isIncludeLoggerName() {
        return includeLoggerName;
    }

    public boolean isIncludeThreadName() {
        return includeThreadName;
    }

    public boolean isAsyncLogging() {
        return asyncLogging;
    }

    public int getAsyncQueueSize() {
        return asyncQueueSize;
    }

    public int getAsyncThreadCount() {
        return asyncThreadCount;
    }

    public boolean isAuditEnabled() {
        return auditEnabled;
    }

    public boolean isPerformanceLoggingEnabled() {
        return performanceLoggingEnabled;
    }

    public boolean isOperationTrackingEnabled() {
        return operationTrackingEnabled;
    }

    public boolean isConsoleOutput() {
        return consoleOutput;
    }

    public boolean isFileOutput() {
        return fileOutput;
    }

    public String getFilePath() {
        return filePath;
    }

    public boolean isJsonOutput() {
        return jsonOutput;
    }

    /**
     * Builder for MemoryLoggerConfig.
     */
    public static class Builder {
        private boolean enabled = true;
        private MemoryLogger.LogLevel level = MemoryLogger.LogLevel.INFO;
        private boolean includeContext = true;
        private boolean includeStackTrace = true;
        private boolean includeTimestamp = true;
        private boolean includeLoggerName = true;
        private boolean includeThreadName = false;
        private boolean asyncLogging = false;
        private int asyncQueueSize = 1000;
        private int asyncThreadCount = 1;
        private boolean auditEnabled = true;
        private boolean performanceLoggingEnabled = true;
        private boolean operationTrackingEnabled = true;
        private boolean consoleOutput = true;
        private boolean fileOutput = false;
        private String filePath;
        private boolean jsonOutput = false;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder level(MemoryLogger.LogLevel level) {
            this.level = level;
            return this;
        }

        public Builder includeContext(boolean includeContext) {
            this.includeContext = includeContext;
            return this;
        }

        public Builder includeStackTrace(boolean includeStackTrace) {
            this.includeStackTrace = includeStackTrace;
            return this;
        }

        public Builder includeTimestamp(boolean includeTimestamp) {
            this.includeTimestamp = includeTimestamp;
            return this;
        }

        public Builder includeLoggerName(boolean includeLoggerName) {
            this.includeLoggerName = includeLoggerName;
            return this;
        }

        public Builder includeThreadName(boolean includeThreadName) {
            this.includeThreadName = includeThreadName;
            return this;
        }

        public Builder asyncLogging(boolean asyncLogging) {
            this.asyncLogging = asyncLogging;
            return this;
        }

        public Builder asyncQueueSize(int asyncQueueSize) {
            this.asyncQueueSize = asyncQueueSize;
            return this;
        }

        public Builder asyncThreadCount(int asyncThreadCount) {
            this.asyncThreadCount = asyncThreadCount;
            return this;
        }

        public Builder auditEnabled(boolean auditEnabled) {
            this.auditEnabled = auditEnabled;
            return this;
        }

        public Builder performanceLoggingEnabled(boolean performanceLoggingEnabled) {
            this.performanceLoggingEnabled = performanceLoggingEnabled;
            return this;
        }

        public Builder operationTrackingEnabled(boolean operationTrackingEnabled) {
            this.operationTrackingEnabled = operationTrackingEnabled;
            return this;
        }

        public Builder consoleOutput(boolean consoleOutput) {
            this.consoleOutput = consoleOutput;
            return this;
        }

        public Builder fileOutput(boolean fileOutput) {
            this.fileOutput = fileOutput;
            return this;
        }

        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder jsonOutput(boolean jsonOutput) {
            this.jsonOutput = jsonOutput;
            return this;
        }

        public MemoryLoggerConfig build() {
            return new MemoryLoggerConfig(this);
        }
    }

    @Override
    public String toString() {
        return "MemoryLoggerConfig{" +
                "enabled=" + enabled +
                ", level=" + level +
                ", includeContext=" + includeContext +
                ", includeStackTrace=" + includeStackTrace +
                ", asyncLogging=" + asyncLogging +
                ", auditEnabled=" + auditEnabled +
                ", performanceLoggingEnabled=" + performanceLoggingEnabled +
                ", operationTrackingEnabled=" + operationTrackingEnabled +
                '}';
    }
}
