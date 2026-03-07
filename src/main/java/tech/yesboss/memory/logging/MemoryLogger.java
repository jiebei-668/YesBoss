package tech.yesboss.memory.logging;

import java.util.Map;

/**
 * Memory Logger Interface
 *
 * <p>Structured logging system for memory persistence operations.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Structured logging with context</li>
 *   <li>Multiple log levels (DEBUG, INFO, WARN, ERROR)</li>
 *   <li>Performance tracking</li>
 *   <li>Audit logging</li>
 *   <li>Error logging with stack traces</li>
 *   <li>Context-aware logging</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * MemoryLogger logger = MemoryLoggerFactory.getLogger("resource");
 * logger.info("Resource saved", Map.of("resourceId", id, "size", size));
 * logger.error("Failed to save resource", exception, Map.of("resourceId", id));
 * logger.logOperation("save", startTime, endTime, Map.of("success", true));
 * </pre>
 *
 * @author YesBoss Team
 * @version 1.0
 */
public interface MemoryLogger {

    // ==================== Log Levels ====================

    /**
     * Log debug message.
     *
     * @param message Log message
     */
    void debug(String message);

    /**
     * Log debug message with context.
     *
     * @param message Log message
     * @param context Context data
     */
    void debug(String message, Map<String, Object> context);

    /**
     * Log info message.
     *
     * @param message Log message
     */
    void info(String message);

    /**
     * Log info message with context.
     *
     * @param message Log message
     * @param context Context data
     */
    void info(String message, Map<String, Object> context);

    /**
     * Log warning message.
     *
     * @param message Log message
     */
    void warn(String message);

    /**
     * Log warning message with context.
     *
     * @param message Log message
     * @param context Context data
     */
    void warn(String message, Map<String, Object> context);

    /**
     * Log error message.
     *
     * @param message Log message
     */
    void error(String message);

    /**
     * Log error message with context.
     *
     * @param message Log message
     * @param context Context data
     */
    void error(String message, Map<String, Object> context);

    /**
     * Log error message with exception.
     *
     * @param message Log message
     * @param throwable Exception or error
     */
    void error(String message, Throwable throwable);

    /**
     * Log error message with exception and context.
     *
     * @param message Log message
     * @param throwable Exception or error
     * @param context Context data
     */
    void error(String message, Throwable throwable, Map<String, Object> context);

    // ==================== Operation Logging ====================

    /**
     * Log operation start.
     *
     * @param operation Operation name
     * @return Operation ID for tracking
     */
    String logOperationStart(String operation);

    /**
     * Log operation start with context.
     *
     * @param operation Operation name
     * @param context Context data
     * @return Operation ID for tracking
     */
    String logOperationStart(String operation, Map<String, Object> context);

    /**
     * Log operation end.
     *
     * @param operationId Operation ID
     * @param success Whether operation succeeded
     */
    void logOperationEnd(String operationId, boolean success);

    /**
     * Log operation end with result.
     *
     * @param operationId Operation ID
     * @param success Whether operation succeeded
     * @param result Result data
     */
    void logOperationEnd(String operationId, boolean success, Map<String, Object> result);

    /**
     * Log operation with timing.
     *
     * @param operation Operation name
     * @param durationMs Operation duration in milliseconds
     * @param success Whether operation succeeded
     */
    void logOperation(String operation, long durationMs, boolean success);

    /**
     * Log operation with timing and context.
     *
     * @param operation Operation name
     * @param durationMs Operation duration in milliseconds
     * @param success Whether operation succeeded
     * @param context Context data
     */
    void logOperation(String operation, long durationMs, boolean success, Map<String, Object> context);

    // ==================== Audit Logging ====================

    /**
     * Log audit event.
     *
     * @param action Action performed
     * @param entityType Type of entity affected
     * @param entityId ID of entity affected
     */
    void audit(String action, String entityType, String entityId);

    /**
     * Log audit event with details.
     *
     * @param action Action performed
     * @param entityType Type of entity affected
     * @param entityId ID of entity affected
     * @param details Additional details
     */
    void audit(String action, String entityType, String entityId, Map<String, Object> details);

    // ==================== Performance Logging ====================

    /**
     * Log performance metric.
     *
     * @param metricName Metric name
     * @param value Metric value
     */
    void logMetric(String metricName, double value);

    /**
     * Log performance metric with tags.
     *
     * @param metricName Metric name
     * @param value Metric value
     * @param tags Metric tags
     */
    void logMetric(String metricName, double value, Map<String, String> tags);

    // ==================== Configuration ====================

    /**
     * Check if debug logging is enabled.
     *
     * @return true if debug enabled
     */
    boolean isDebugEnabled();

    /**
     * Check if info logging is enabled.
     *
     * @return true if info enabled
     */
    boolean isInfoEnabled();

    /**
     * Check if warn logging is enabled.
     *
     * @return true if warn enabled
     */
    boolean isWarnEnabled();

    /**
     * Check if error logging is enabled.
     *
     * @return true if error enabled
     */
    boolean isErrorEnabled();

    /**
     * Get logger name.
     *
     * @return Logger name
     */
    String getName();

    /**
     * Set log level.
     *
     * @param level Log level
     */
    void setLevel(LogLevel level);

    /**
     * Get current log level.
     *
     * @return Current log level
     */
    LogLevel getLevel();

    // ==================== Inner Classes ====================

    /**
     * Log level enumeration
     */
    enum LogLevel {
        TRACE(0),
        DEBUG(1),
        INFO(2),
        WARN(3),
        ERROR(4);

        private final int value;

        LogLevel(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public boolean isEnabled(LogLevel configuredLevel) {
            return this.value >= configuredLevel.value;
        }
    }
}
