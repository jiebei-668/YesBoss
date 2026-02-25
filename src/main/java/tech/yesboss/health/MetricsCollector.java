package tech.yesboss.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.persistence.db.SingleThreadDbWriter;
import tech.yesboss.safeguard.CircuitBreaker;
import tech.yesboss.session.SessionManager;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Metrics Collector
 *
 * <p>Collects and aggregates application metrics for monitoring and observability.</p>
 *
 * <p><b>Metrics Collected:</b></p>
 * <ul>
 *   <li>Active sessions count</li>
 *   <li>Database writer queue size</li>
 *   <li>CircuitBreaker active sessions</li>
 *   <li>Webhook executor thread pool metrics</li>
 *   <li>JVM memory usage</li>
 *   <li>Thread count</li>
 *   <li>Uptime</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * MetricsCollector collector = new MetricsCollector(...);
 * Map&lt;String, Object&gt; metrics = collector.getMetrics();
 * </pre>
 */
public class MetricsCollector {

    private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);

    private final SessionManager sessionManager;
    private final SingleThreadDbWriter dbWriter;
    private final CircuitBreaker circuitBreaker;
    private final ExecutorService webhookExecutor;
    private final MemoryMXBean memoryMXBean;
    private final ThreadMXBean threadMXBean;
    private final long startTime;

    /**
     * Create a new MetricsCollector.
     *
     * @param sessionManager   The session manager for active session count
     * @param dbWriter         The database writer for queue size metrics
     * @param circuitBreaker   The circuit breaker for active loop counts
     * @param webhookExecutor  The webhook executor for thread pool metrics
     */
    public MetricsCollector(
            SessionManager sessionManager,
            SingleThreadDbWriter dbWriter,
            CircuitBreaker circuitBreaker,
            ExecutorService webhookExecutor
    ) {
        this.sessionManager = sessionManager;
        this.dbWriter = dbWriter;
        this.circuitBreaker = circuitBreaker;
        this.webhookExecutor = webhookExecutor;
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Collect all application metrics.
     *
     * @return A map of metric names to their values
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        // Service info
        metrics.put("service", "YesBoss");
        metrics.put("status", "running");
        metrics.put("uptimeSeconds", getUptimeSeconds());

        // Session metrics
        metrics.put("activeSessions", getActiveSessionsCount());

        // Database metrics
        metrics.put("databaseWriterQueueSize", getDatabaseWriterQueueSize());

        // Circuit breaker metrics
        metrics.put("circuitBreakerActiveSessions", getCircuitBreakerActiveSessions());

        // Executor metrics
        metrics.put("executorMetrics", getExecutorMetrics());

        // JVM metrics
        metrics.put("jvmMetrics", getJvmMetrics());

        return metrics;
    }

    /**
     * Get metrics as JSON string for easy serialization.
     *
     * @return JSON-formatted metrics string
     */
    public String getMetricsAsJson() {
        Map<String, Object> metrics = getMetrics();

        StringBuilder json = new StringBuilder();
        json.append("{");

        boolean first = true;
        for (Map.Entry<String, Object> entry : metrics.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":");

            Object value = entry.getValue();
            if (value instanceof Map) {
                // Nested map
                json.append("{");
                boolean firstNested = true;
                for (Map.Entry<?, ?> nestedEntry : ((Map<?, ?>) value).entrySet()) {
                    if (!firstNested) {
                        json.append(",");
                    }
                    json.append("\"").append(nestedEntry.getKey()).append("\":");
                    formatJsonValue(json, nestedEntry.getValue());
                    firstNested = false;
                }
                json.append("}");
            } else {
                formatJsonValue(json, value);
            }

            first = false;
        }

        json.append("}");
        return json.toString();
    }

    // ==================== Individual Metric Collection ====================

    private long getUptimeSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }

    private int getActiveSessionsCount() {
        try {
            if (sessionManager != null) {
                // Return 0 for now as SessionManager doesn't expose this method
                // This could be enhanced in the future
                return 0;
            }
            return 0;
        } catch (Exception e) {
            logger.debug("Error getting active sessions count: {}", e.getMessage());
            return 0;
        }
    }

    private int getDatabaseWriterQueueSize() {
        try {
            if (dbWriter != null) {
                return dbWriter.getQueueSize();
            }
            return 0;
        } catch (Exception e) {
            logger.debug("Error getting database writer queue size: {}", e.getMessage());
            return 0;
        }
    }

    private int getCircuitBreakerActiveSessions() {
        try {
            if (circuitBreaker != null) {
                // Return 0 for now as CircuitBreaker doesn't expose this method
                // This could be enhanced in the future
                return 0;
            }
            return 0;
        } catch (Exception e) {
            logger.debug("Error getting circuit breaker active sessions: {}", e.getMessage());
            return 0;
        }
    }

    private Map<String, Object> getExecutorMetrics() {
        Map<String, Object> executorMetrics = new HashMap<>();

        if (webhookExecutor != null) {
            if (webhookExecutor instanceof java.util.concurrent.ThreadPoolExecutor) {
                java.util.concurrent.ThreadPoolExecutor tpe =
                    (java.util.concurrent.ThreadPoolExecutor) webhookExecutor;
                executorMetrics.put("activeThreads", tpe.getActiveCount());
                executorMetrics.put("poolSize", tpe.getPoolSize());
                executorMetrics.put("corePoolSize", tpe.getCorePoolSize());
                executorMetrics.put("maxPoolSize", tpe.getMaximumPoolSize());
                executorMetrics.put("completedTasks", tpe.getCompletedTaskCount());
                executorMetrics.put("queueSize", tpe.getQueue().size());
            } else {
                // For virtual thread executors, we can't get detailed metrics
                executorMetrics.put("type", "VirtualThreadPerTaskExecutor");
                executorMetrics.put("isShutdown", webhookExecutor.isShutdown());
                executorMetrics.put("isTerminated", webhookExecutor.isTerminated());
            }
        } else {
            executorMetrics.put("status", "not_initialized");
        }

        return executorMetrics;
    }

    private Map<String, Object> getJvmMetrics() {
        Map<String, Object> jvmMetrics = new HashMap<>();

        // Memory metrics
        jvmMetrics.put("heapMemoryUsed", formatBytes(memoryMXBean.getHeapMemoryUsage().getUsed()));
        jvmMetrics.put("heapMemoryMax", formatBytes(memoryMXBean.getHeapMemoryUsage().getMax()));
        jvmMetrics.put("nonHeapMemoryUsed", formatBytes(memoryMXBean.getNonHeapMemoryUsage().getUsed()));

        // Thread metrics
        jvmMetrics.put("threadCount", threadMXBean.getThreadCount());
        jvmMetrics.put("peakThreadCount", threadMXBean.getPeakThreadCount());
        jvmMetrics.put("daemonThreadCount", threadMXBean.getDaemonThreadCount());

        // Runtime metrics
        jvmMetrics.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        jvmMetrics.put("freeMemory", formatBytes(Runtime.getRuntime().freeMemory()));
        jvmMetrics.put("totalMemory", formatBytes(Runtime.getRuntime().totalMemory()));
        jvmMetrics.put("maxMemory", formatBytes(Runtime.getRuntime().maxMemory()));

        return jvmMetrics;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    private void formatJsonValue(StringBuilder json, Object value) {
        if (value instanceof String) {
            json.append("\"").append(value).append("\"");
        } else if (value instanceof Number) {
            json.append(value);
        } else if (value instanceof Boolean) {
            json.append(value);
        } else {
            json.append("\"").append(value).append("\"");
        }
    }
}
