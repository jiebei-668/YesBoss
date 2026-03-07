package tech.yesboss.memory.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collection and monitoring for cache operations.
 *
 * <p>This class provides comprehensive metrics tracking:
 * <ul>
 *   <li>Operation counts (get, put, evict, etc.)</li>
 *   <li>Performance metrics (response time, throughput)</li>
 *   <li>Cache effectiveness (hit rate, miss rate)</li>
 *   <li>Resource utilization (memory, size)</li>
 *   <li>Alert evaluation based on thresholds</li>
 * </ul>
 */
public class CacheMetrics {

    private final String cacheName;
    private final Cache<?, ?> cache;
    private final Instant startTime;

    // Operation counters
    private final AtomicLong readOperations = new AtomicLong(0);
    private final AtomicLong writeOperations = new AtomicLong(0);
    private final AtomicLong deleteOperations = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);

    // Performance metrics
    private final AtomicLong totalResponseTimeNanos = new AtomicLong(0);
    private final AtomicLong slowOperations = new AtomicLong(0);

    // Alert thresholds
    private volatile long slowOperationThresholdNanos = Duration.ofMillis(100).toNanos();
    private volatile double alertHitRateThreshold = 0.8;
    private volatile int alertSizeThreshold = 0;

    // Alert history
    private final List<Alert> alertHistory = new ArrayList<>();
    private final Map<String, Alert> activeAlerts = new ConcurrentHashMap<>();

    /**
     * Create a new cache metrics instance.
     *
     * @param cacheName the name of the cache
     * @param cache the cache instance
     */
    public CacheMetrics(String cacheName, Cache<?, ?> cache) {
        this.cacheName = cacheName;
        this.cache = cache;
        this.startTime = Instant.now();
    }

    // ==================== Record Operations ====================

    /**
     * Record a read operation.
     *
     * @param hit true if the read was a cache hit
     * @param responseTimeNanos the response time in nanoseconds
     */
    public void recordRead(boolean hit, long responseTimeNanos) {
        readOperations.incrementAndGet();
        if (hit) {
            cacheHits.incrementAndGet();
        } else {
            cacheMisses.incrementAndGet();
        }

        totalResponseTimeNanos.addAndGet(responseTimeNanos);

        if (responseTimeNanos > slowOperationThresholdNanos) {
            slowOperations.incrementAndGet();
        }

        // Check for alerts
        evaluateAlerts();
    }

    /**
     * Record a write operation.
     *
     * @param responseTimeNanos the response time in nanoseconds
     */
    public void recordWrite(long responseTimeNanos) {
        writeOperations.incrementAndGet();
        totalResponseTimeNanos.addAndGet(responseTimeNanos);

        if (responseTimeNanos > slowOperationThresholdNanos) {
            slowOperations.incrementAndGet();
        }
    }

    /**
     * Record a delete operation.
     *
     * @param responseTimeNanos the response time in nanoseconds
     */
    public void recordDelete(long responseTimeNanos) {
        deleteOperations.incrementAndGet();
        totalResponseTimeNanos.addAndGet(responseTimeNanos);

        if (responseTimeNanos > slowOperationThresholdNanos) {
            slowOperations.incrementAndGet();
        }
    }

    // ==================== Metrics Getters ====================

    /**
     * Get the cache name.
     */
    public String getCacheName() {
        return cacheName;
    }

    /**
     * Get the total number of operations.
     */
    public long getTotalOperations() {
        return readOperations.get() + writeOperations.get() + deleteOperations.get();
    }

    /**
     * Get the number of read operations.
     */
    public long getReadOperations() {
        return readOperations.get();
    }

    /**
     * Get the number of write operations.
     */
    public long getWriteOperations() {
        return writeOperations.get();
    }

    /**
     * Get the number of delete operations.
     */
    public long getDeleteOperations() {
        return deleteOperations.get();
    }

    /**
     * Get the number of cache hits.
     */
    public long getCacheHits() {
        return cacheHits.get();
    }

    /**
     * Get the number of cache misses.
     */
    public long getCacheMisses() {
        return cacheMisses.get();
    }

    /**
     * Get the cache hit rate.
     */
    public double getHitRate() {
        long totalReads = readOperations.get();
        return totalReads == 0 ? 0.0 : (double) cacheHits.get() / totalReads;
    }

    /**
     * Get the average response time in nanoseconds.
     */
    public double getAverageResponseTimeNanos() {
        long totalOps = getTotalOperations();
        return totalOps == 0 ? 0.0 : (double) totalResponseTimeNanos.get() / totalOps;
    }

    /**
     * Get the average response time in milliseconds.
     */
    public double getAverageResponseTimeMillis() {
        return getAverageResponseTimeNanos() / 1_000_000.0;
    }

    /**
     * Get the number of slow operations.
     */
    public long getSlowOperations() {
        return slowOperations.get();
    }

    /**
     * Get the slow operation percentage.
     */
    public double getSlowOperationPercentage() {
        long totalOps = getTotalOperations();
        return totalOps == 0 ? 0.0 : (double) slowOperations.get() / totalOps;
    }

    /**
     * Get the cache size.
     */
    public long getCacheSize() {
        return cache.size();
    }

    /**
     * Get the uptime duration.
     */
    public Duration getUptime() {
        return Duration.between(startTime, Instant.now());
    }

    /**
     * Get operations per second.
     */
    public double getOperationsPerSecond() {
        long uptimeSeconds = getUptime().getSeconds();
        return uptimeSeconds == 0 ? 0.0 : (double) getTotalOperations() / uptimeSeconds;
    }

    // ==================== Alert Management ====================

    /**
     * Set the slow operation threshold.
     *
     * @param threshold the threshold in milliseconds
     */
    public void setSlowOperationThreshold(long threshold) {
        this.slowOperationThresholdNanos = Duration.ofMillis(threshold).toNanos();
    }

    /**
     * Set the hit rate alert threshold.
     *
     * @param threshold the threshold (0.0 to 1.0)
     */
    public void setHitRateAlertThreshold(double threshold) {
        this.alertHitRateThreshold = threshold;
    }

    /**
     * Set the size alert threshold.
     *
     * @param threshold the threshold
     */
    public void setSizeAlertThreshold(int threshold) {
        this.alertSizeThreshold = threshold;
    }

    /**
     * Evaluate alerts based on current metrics.
     */
    private void evaluateAlerts() {
        // Check hit rate
        if (getHitRate() < alertHitRateThreshold && getTotalOperations() > 100) {
            createOrUpdateAlert("LOW_HIT_RATE",
                String.format("Cache hit rate %.2f%% is below threshold %.2f%%",
                    getHitRate() * 100, alertHitRateThreshold * 100),
                Alert.Severity.WARNING);
        } else {
            resolveAlert("LOW_HIT_RATE");
        }

        // Check cache size
        if (alertSizeThreshold > 0 && getCacheSize() >= alertSizeThreshold) {
            createOrUpdateAlert("HIGH_CACHE_SIZE",
                String.format("Cache size %d is at or above threshold %d",
                    getCacheSize(), alertSizeThreshold),
                Alert.Severity.WARNING);
        } else {
            resolveAlert("HIGH_CACHE_SIZE");
        }

        // Check slow operations
        if (getSlowOperationPercentage() > 0.1) {
            createOrUpdateAlert("HIGH_SLOW_OPERATIONS",
                String.format("Slow operation percentage %.2f%% is above 0.1%%",
                    getSlowOperationPercentage() * 100),
                Alert.Severity.WARNING);
        } else {
            resolveAlert("HIGH_SLOW_OPERATIONS");
        }
    }

    /**
     * Create or update an alert.
     */
    private void createOrUpdateAlert(String id, String message, Alert.Severity severity) {
        Alert alert = activeAlerts.computeIfAbsent(id, k -> {
            Alert newAlert = new Alert(id, message, severity);
            alertHistory.add(newAlert);
            return newAlert;
        });
        alert.updateMessage(message);
        alert.updateSeverity(severity);
    }

    /**
     * Resolve an alert.
     */
    private void resolveAlert(String id) {
        Alert alert = activeAlerts.remove(id);
        if (alert != null) {
            alert.resolve();
        }
    }

    /**
     * Get active alerts.
     */
    public List<Alert> getActiveAlerts() {
        return new ArrayList<>(activeAlerts.values());
    }

    /**
     * Get alert history.
     */
    public List<Alert> getAlertHistory() {
        return new ArrayList<>(alertHistory);
    }

    // ==================== Summary ====================

    /**
     * Get a summary of all metrics.
     */
    public CacheMetricsSummary getSummary() {
        return new CacheMetricsSummary(
            cacheName,
            getTotalOperations(),
            getReadOperations(),
            getWriteOperations(),
            getDeleteOperations(),
            getCacheHits(),
            getCacheMisses(),
            getHitRate(),
            getAverageResponseTimeMillis(),
            getSlowOperations(),
            getSlowOperationPercentage(),
            getCacheSize(),
            getOperationsPerSecond(),
            getUptime(),
            getActiveAlerts().size()
        );
    }

    @Override
    public String toString() {
        return String.format(
            "CacheMetrics{name='%s', operations=%d, hits=%d, misses=%d, hitRate=%.2f%%, avgResponse=%.2fms, size=%d}",
            cacheName, getTotalOperations(), getCacheHits(), getCacheMisses(),
            getHitRate() * 100, getAverageResponseTimeMillis(), getCacheSize()
        );
    }

    /**
     * Alert class for cache metrics.
     */
    public static class Alert {
        private final String id;
        private final Instant created;
        private String message;
        private Severity severity;
        private Instant resolved;
        private volatile boolean acknowledged;

        public enum Severity {
            INFO, WARNING, ERROR, CRITICAL
        }

        public Alert(String id, String message, Severity severity) {
            this.id = id;
            this.message = message;
            this.severity = severity;
            this.created = Instant.now();
            this.acknowledged = false;
        }

        public String getId() { return id; }
        public String getMessage() { return message; }
        public Severity getSeverity() { return severity; }
        public Instant getCreated() { return created; }
        public Instant getResolved() { return resolved; }
        public boolean isResolved() { return resolved != null; }
        public boolean isAcknowledged() { return acknowledged; }

        public void updateMessage(String message) {
            this.message = message;
        }

        public void updateSeverity(Severity severity) {
            this.severity = severity;
        }

        public void resolve() {
            this.resolved = Instant.now();
        }

        public void acknowledge() {
            this.acknowledged = true;
        }
    }

    /**
     * Summary of cache metrics.
     */
    public static class CacheMetricsSummary {
        private final String cacheName;
        private final long totalOperations;
        private final long readOperations;
        private final long writeOperations;
        private final long deleteOperations;
        private final long cacheHits;
        private final long cacheMisses;
        private final double hitRate;
        private final double averageResponseTimeMillis;
        private final long slowOperations;
        private final double slowOperationPercentage;
        private final long cacheSize;
        private final double operationsPerSecond;
        private final Duration uptime;
        private final int activeAlerts;

        public CacheMetricsSummary(String cacheName, long totalOperations, long readOperations,
                                  long writeOperations, long deleteOperations, long cacheHits,
                                  long cacheMisses, double hitRate, double averageResponseTimeMillis,
                                  long slowOperations, double slowOperationPercentage, long cacheSize,
                                  double operationsPerSecond, Duration uptime, int activeAlerts) {
            this.cacheName = cacheName;
            this.totalOperations = totalOperations;
            this.readOperations = readOperations;
            this.writeOperations = writeOperations;
            this.deleteOperations = deleteOperations;
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.hitRate = hitRate;
            this.averageResponseTimeMillis = averageResponseTimeMillis;
            this.slowOperations = slowOperations;
            this.slowOperationPercentage = slowOperationPercentage;
            this.cacheSize = cacheSize;
            this.operationsPerSecond = operationsPerSecond;
            this.uptime = uptime;
            this.activeAlerts = activeAlerts;
        }

        // Getters
        public String getCacheName() { return cacheName; }
        public long getTotalOperations() { return totalOperations; }
        public long getReadOperations() { return readOperations; }
        public long getWriteOperations() { return writeOperations; }
        public long getDeleteOperations() { return deleteOperations; }
        public long getCacheHits() { return cacheHits; }
        public long getCacheMisses() { return cacheMisses; }
        public double getHitRate() { return hitRate; }
        public double getAverageResponseTimeMillis() { return averageResponseTimeMillis; }
        public long getSlowOperations() { return slowOperations; }
        public double getSlowOperationPercentage() { return slowOperationPercentage; }
        public long getCacheSize() { return cacheSize; }
        public double getOperationsPerSecond() { return operationsPerSecond; }
        public Duration getUptime() { return uptime; }
        public int getActiveAlerts() { return activeAlerts; }

        @Override
        public String toString() {
            return String.format(
                "CacheMetricsSummary{cache='%s', ops=%d, hitRate=%.2f%%, avgResp=%.2fms, size=%d, alerts=%d}",
                cacheName, totalOperations, hitRate * 100, averageResponseTimeMillis, cacheSize, activeAlerts
            );
        }
    }
}
