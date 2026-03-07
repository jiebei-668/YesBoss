package tech.yesboss.memory.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memory Logger Implementation
 *
 * <p>Structured logging implementation using SLF4J with context support.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Integration with SLF4J logging framework</li>
 *   <li>Structured context logging with MDC</li>
 *   <li>Operation tracking with timing</li>
 *   <li>Audit logging</li>
 *   <li>Performance metrics logging</li>
 *   <li>Configurable log levels</li>
 * </ul>
 *
 * @author YesBoss Team
 * @version 1.0
 */
public class MemoryLoggerImpl implements MemoryLogger {

    private final String name;
    private final Logger slf4jLogger;
    private final MemoryLoggerConfig config;
    private final Map<String, OperationContext> activeOperations;

    /**
     * Operation tracking context
     */
    private static class OperationContext {
        final String operationId;
        final String operation;
        final Instant startTime;
        final Map<String, Object> context;

        OperationContext(String operation, Map<String, Object> context) {
            this.operationId = UUID.randomUUID().toString();
            this.operation = operation;
            this.startTime = Instant.now();
            this.context = context != null ? new ConcurrentHashMap<>(context) : new ConcurrentHashMap<>();
        }
    }

    /**
     * Create logger with name and default configuration.
     *
     * @param name Logger name
     */
    public MemoryLoggerImpl(String name) {
        this(name, MemoryLoggerConfig.defaults());
    }

    /**
     * Create logger with name and configuration.
     *
     * @param name Logger name
     * @param config Logger configuration
     */
    public MemoryLoggerImpl(String name, MemoryLoggerConfig config) {
        this.name = name;
        this.slf4jLogger = LoggerFactory.getLogger("memory." + name);
        this.config = config;
        this.activeOperations = new ConcurrentHashMap<>();
    }

    // ==================== Log Levels ====================

    @Override
    public void debug(String message) {
        if (!config.isEnabled() || !isDebugEnabled()) {
            return;
        }
        logInternal(LogLevel.DEBUG, message, null, null);
    }

    @Override
    public void debug(String message, Map<String, Object> context) {
        if (!config.isEnabled() || !isDebugEnabled()) {
            return;
        }
        logInternal(LogLevel.DEBUG, message, null, context);
    }

    @Override
    public void info(String message) {
        if (!config.isEnabled() || !isInfoEnabled()) {
            return;
        }
        logInternal(LogLevel.INFO, message, null, null);
    }

    @Override
    public void info(String message, Map<String, Object> context) {
        if (!config.isEnabled() || !isInfoEnabled()) {
            return;
        }
        logInternal(LogLevel.INFO, message, null, context);
    }

    @Override
    public void warn(String message) {
        if (!config.isEnabled() || !isWarnEnabled()) {
            return;
        }
        logInternal(LogLevel.WARN, message, null, null);
    }

    @Override
    public void warn(String message, Map<String, Object> context) {
        if (!config.isEnabled() || !isWarnEnabled()) {
            return;
        }
        logInternal(LogLevel.WARN, message, null, context);
    }

    @Override
    public void error(String message) {
        if (!config.isEnabled() || !isErrorEnabled()) {
            return;
        }
        logInternal(LogLevel.ERROR, message, null, null);
    }

    @Override
    public void error(String message, Map<String, Object> context) {
        if (!config.isEnabled() || !isErrorEnabled()) {
            return;
        }
        logInternal(LogLevel.ERROR, message, null, context);
    }

    @Override
    public void error(String message, Throwable throwable) {
        if (!config.isEnabled() || !isErrorEnabled()) {
            return;
        }
        logInternal(LogLevel.ERROR, message, throwable, null);
    }

    @Override
    public void error(String message, Throwable throwable, Map<String, Object> context) {
        if (!config.isEnabled() || !isErrorEnabled()) {
            return;
        }
        logInternal(LogLevel.ERROR, message, throwable, context);
    }

    // ==================== Operation Logging ====================

    @Override
    public String logOperationStart(String operation) {
        return logOperationStart(operation, null);
    }

    @Override
    public String logOperationStart(String operation, Map<String, Object> context) {
        if (!config.isEnabled() || !config.isOperationTrackingEnabled()) {
            return null;
        }

        OperationContext opContext = new OperationContext(operation, context);
        activeOperations.put(opContext.operationId, opContext);

        if (isInfoEnabled()) {
            Map<String, Object> logContext = new ConcurrentHashMap<>(opContext.context);
            logContext.put("operationId", opContext.operationId);
            logContext.put("phase", "START");
            logInternal(LogLevel.INFO, "Operation started: " + operation, null, logContext);
        }

        return opContext.operationId;
    }

    @Override
    public void logOperationEnd(String operationId, boolean success) {
        logOperationEnd(operationId, success, null);
    }

    @Override
    public void logOperationEnd(String operationId, boolean success, Map<String, Object> result) {
        if (!config.isEnabled() || !config.isOperationTrackingEnabled() || operationId == null) {
            return;
        }

        OperationContext opContext = activeOperations.remove(operationId);
        if (opContext == null) {
            warn("Operation not found: " + operationId);
            return;
        }

        Instant endTime = Instant.now();
        long durationMs = Duration.between(opContext.startTime, endTime).toMillis();

        Map<String, Object> logContext = new ConcurrentHashMap<>(opContext.context);
        logContext.put("operationId", operationId);
        logContext.put("phase", "END");
        logContext.put("success", success);
        logContext.put("durationMs", durationMs);

        if (result != null) {
            logContext.putAll(result);
        }

        if (success) {
            info("Operation completed: " + opContext.operation, logContext);
        } else {
            error("Operation failed: " + opContext.operation, logContext);
        }
    }

    @Override
    public void logOperation(String operation, long durationMs, boolean success) {
        logOperation(operation, durationMs, success, null);
    }

    @Override
    public void logOperation(String operation, long durationMs, boolean success, Map<String, Object> context) {
        if (!config.isEnabled() || !config.isOperationTrackingEnabled()) {
            return;
        }

        Map<String, Object> logContext = context != null ?
            new ConcurrentHashMap<>(context) : new ConcurrentHashMap<>();
        logContext.put("operation", operation);
        logContext.put("durationMs", durationMs);
        logContext.put("success", success);

        if (success) {
            info("Operation: " + operation, logContext);
        } else {
            error("Operation failed: " + operation, logContext);
        }
    }

    // ==================== Audit Logging ====================

    @Override
    public void audit(String action, String entityType, String entityId) {
        audit(action, entityType, entityId, null);
    }

    @Override
    public void audit(String action, String entityType, String entityId, Map<String, Object> details) {
        if (!config.isEnabled() || !config.isAuditEnabled()) {
            return;
        }

        Map<String, Object> context = new ConcurrentHashMap<>();
        context.put("action", action);
        context.put("entityType", entityType);
        context.put("entityId", entityId);

        if (details != null) {
            context.putAll(details);
        }

        // Force audit logs to INFO level regardless of configured level
        if (config.getLevel().isEnabled(LogLevel.INFO)) {
            logInternal(LogLevel.INFO, "[AUDIT] " + action + " on " + entityType + ":" + entityId, null, context);
        }
    }

    // ==================== Performance Logging ====================

    @Override
    public void logMetric(String metricName, double value) {
        logMetric(metricName, value, null);
    }

    @Override
    public void logMetric(String metricName, double value, Map<String, String> tags) {
        if (!config.isEnabled() || !config.isPerformanceLoggingEnabled()) {
            return;
        }

        Map<String, Object> context = new ConcurrentHashMap<>();
        context.put("metric", metricName);
        context.put("value", value);

        if (tags != null) {
            context.putAll(tags);
        }

        if (isDebugEnabled()) {
            logInternal(LogLevel.DEBUG, "[METRIC] " + metricName + " = " + value, null, context);
        }
    }

    // ==================== Configuration ====================

    @Override
    public boolean isDebugEnabled() {
        return config.getLevel().isEnabled(LogLevel.DEBUG);
    }

    @Override
    public boolean isInfoEnabled() {
        return config.getLevel().isEnabled(LogLevel.INFO);
    }

    @Override
    public boolean isWarnEnabled() {
        return config.getLevel().isEnabled(LogLevel.WARN);
    }

    @Override
    public boolean isErrorEnabled() {
        return config.getLevel().isEnabled(LogLevel.ERROR);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setLevel(LogLevel level) {
        // Note: This doesn't change SLF4J's level, just our internal check
        // In production, would need to reconfigure SLF4J
        // Note: Config level is immutable after creation
    }

    @Override
    public LogLevel getLevel() {
        return config.getLevel();
    }

    // ==================== Private Helper Methods ====================

    /**
     * Internal logging method with context support.
     *
     * @param level Log level
     * @param message Log message
     * @param throwable Throwable (optional)
     * @param context Context data (optional)
     */
    private void logInternal(LogLevel level, String message, Throwable throwable, Map<String, Object> context) {
        try {
            // Set MDC context
            if (config.isIncludeContext() && context != null && !context.isEmpty()) {
                for (Map.Entry<String, Object> entry : context.entrySet()) {
                    MDC.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }

            // Add logger name to MDC
            if (config.isIncludeLoggerName()) {
                MDC.put("logger", name);
            }

            // Add timestamp to MDC
            if (config.isIncludeTimestamp()) {
                MDC.put("timestamp", Instant.now().toString());
            }

            // Add thread name to MDC
            if (config.isIncludeThreadName()) {
                MDC.put("thread", Thread.currentThread().getName());
            }

            // Log to SLF4J
            String formattedMessage = formatMessage(message, context);
            logToSlf4j(level, formattedMessage, throwable);

        } finally {
            // Clear MDC
            MDC.clear();
        }
    }

    /**
     * Format message with context.
     *
     * @param message Base message
     * @param context Context data
     * @return Formatted message
     */
    private String formatMessage(String message, Map<String, Object> context) {
        if (!config.isIncludeContext() || context == null || context.isEmpty()) {
            return message;
        }

        StringBuilder sb = new StringBuilder(message);
        if (config.isJsonOutput()) {
            sb.append(" | ").append(contextToJson(context));
        } else {
            sb.append(" ").append(contextToString(context));
        }
        return sb.toString();
    }

    /**
     * Convert context to string.
     *
     * @param context Context data
     * @return String representation
     */
    private String contextToString(Map<String, Object> context) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Convert context to JSON string.
     *
     * @param context Context data
     * @return JSON string
     */
    private String contextToJson(Map<String, Object> context) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(entry.getKey()).append("\":");

            Object value = entry.getValue();
            if (value instanceof Number) {
                sb.append(value);
            } else if (value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append("\"").append(value).append("\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Log to SLF4J based on level.
     *
     * @param level Log level
     * @param message Formatted message
     * @param throwable Throwable (optional)
     */
    private void logToSlf4j(LogLevel level, String message, Throwable throwable) {
        switch (level) {
            case TRACE:
            case DEBUG:
                if (throwable != null) {
                    slf4jLogger.debug(message, throwable);
                } else {
                    slf4jLogger.debug(message);
                }
                break;
            case INFO:
                if (throwable != null) {
                    slf4jLogger.info(message, throwable);
                } else {
                    slf4jLogger.info(message);
                }
                break;
            case WARN:
                if (throwable != null) {
                    slf4jLogger.warn(message, throwable);
                } else {
                    slf4jLogger.warn(message);
                }
                break;
            case ERROR:
                if (throwable != null) {
                    if (config.isIncludeStackTrace()) {
                        slf4jLogger.error(message, throwable);
                    } else {
                        slf4jLogger.error(message + ": " + throwable.getMessage());
                    }
                } else {
                    slf4jLogger.error(message);
                }
                break;
        }
    }

    /**
     * Clean up any lingering operations.
     */
    public void cleanup() {
        if (!activeOperations.isEmpty()) {
            warn("Cleaning up " + activeOperations.size() + " active operations");
            activeOperations.clear();
        }
    }
}
