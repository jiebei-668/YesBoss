package tech.yesboss.memory.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tech.yesboss.memory.config.MemoryConfig;
import tech.yesboss.memory.trigger.TriggerService;
import tech.yesboss.memory.trigger.TriggerServiceException;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Scheduled task component for memory extraction integration with Spring Boot scheduling system.
 *
 * <p>This scheduler provides:
 * <ul>
 *   <li>Periodic memory extraction checks (configurable interval)</li>
 *   <li>Batch processing of multiple conversations</li>
 *   <li>Retry mechanism with exponential backoff</li>
 *   <li>Performance monitoring and metrics</li>
 *   <li>Graceful degradation on errors</li>
 *   <li>Configurable concurrency control</li>
 * </ul>
 *
 * <p>Scheduling Configuration (from application-memory.yml):
 * <pre>
 * memory:
 *   scheduler:
 *     enabled: true
 *     fixedRate: 300000  # 5 minutes
 *     initialDelay: 60000  # 1 minute
 *     maxConcurrentTasks: 5
 *     retryMaxAttempts: 3
 *     retryInitialDelay: 1000  # 1 second
 * </pre>
 */
@Component
public class MemoryExtractionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MemoryExtractionScheduler.class);

    private final TriggerService triggerService;
    private final MemoryConfig config;
    private final ExecutorService executorService;
    private final ScheduledExecutorService retryExecutorService;

    // Performance metrics
    private final AtomicLong totalExtractions = new AtomicLong(0);
    private final AtomicLong successfulExtractions = new AtomicLong(0);
    private final AtomicLong failedExtractions = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);
    private final AtomicLong lastExecutionTime = new AtomicLong(0);
    private final AtomicInteger activeTasks = new AtomicInteger(0);

    // State management
    private volatile boolean running = false;
    private volatile boolean enabled = true;
    private volatile long lastExecutionTimestamp = 0;

    /**
     * Constructor with dependency injection.
     *
     * @param triggerService Trigger service for memory extraction
     * @param config Memory configuration
     */
    public MemoryExtractionScheduler(TriggerService triggerService, MemoryConfig config) {
        this.triggerService = triggerService;
        this.config = config;
        this.executorService = createExecutorService();
        this.retryExecutorService = Executors.newSingleThreadScheduledExecutor();

        // Load configuration
        this.enabled = config.get("memory.scheduler.enabled", true);
        this.running = this.enabled;

        logger.info("MemoryExtractionScheduler initialized with enabled: {}", this.enabled);
    }

    // ========================================================================
    // Scheduled Tasks
    // ========================================================================

    /**
     * Scheduled task for periodic memory extraction.
     * Runs at a fixed rate configured in application-memory.yml.
     *
     * Default: Every 5 minutes (300000ms)
     */
    @Scheduled(fixedRateString = "${memory.scheduler.fixedRate:300000}",
               initialDelayString = "${memory.scheduler.initialDelay:60000}")
    public void scheduledMemoryExtraction() {
        if (!enabled || !triggerService.isAvailable()) {
            logger.debug("Memory extraction scheduler is disabled or trigger service unavailable");
            return;
        }

        long startTime = System.currentTimeMillis();
        lastExecutionTimestamp = startTime;

        try {
            running = true;
            logger.info("Starting scheduled memory extraction");

            // Execute extraction for all conversations
            int processedCount = executeExtractionForAllConversations();

            // Update metrics
            totalExtractions.incrementAndGet();
            if (processedCount > 0) {
                successfulExtractions.incrementAndGet();
            }

            long duration = System.currentTimeMillis() - startTime;
            totalProcessingTimeMs.addAndGet(duration);
            lastExecutionTime.set(duration);

            logger.info("Scheduled memory extraction completed: {} conversations processed in {}ms",
                       processedCount, duration);

        } catch (Exception e) {
            logger.error("Error during scheduled memory extraction: {}", e.getMessage(), e);
            failedExtractions.incrementAndGet();
            handleExtractionError(e);
        } finally {
            running = false;
        }
    }

    /**
     * Scheduled task for health check and metrics reporting.
     * Runs every minute.
     */
    @Scheduled(fixedRate = 60000, initialDelay = 30000)
    public void scheduledHealthCheck() {
        if (!enabled) {
            return;
        }

        try {
            logHealthMetrics();
            checkAndRecoverFromErrors();
        } catch (Exception e) {
            logger.warn("Error during health check: {}", e.getMessage());
        }
    }

    // ========================================================================
    // Core Execution Logic
    // ========================================================================

    /**
     * Execute memory extraction for all active conversations.
     *
     * @return Number of conversations processed
     */
    private int executeExtractionForAllConversations() {
        int maxConcurrent = config.get("memory.scheduler.maxConcurrentTasks", 5);
        int activeCount = activeTasks.get();

        if (activeCount >= maxConcurrent) {
            logger.warn("Maximum concurrent tasks reached: {}", activeCount);
            return 0;
        }

        try {
            activeTasks.incrementAndGet();
            return triggerService.triggerForAllConversations();
        } finally {
            activeTasks.decrementAndGet();
        }
    }

    /**
     * Trigger memory extraction for a specific conversation.
     *
     * @param conversationId Conversation ID
     * @param sessionId Session ID
     * @return Future for async operation
     */
    public CompletableFuture<Integer> triggerExtractionAsync(String conversationId, String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Triggering extraction for conversation: {}", conversationId);
                return triggerService.triggerMemoryExtraction(conversationId, sessionId);
            } catch (Exception e) {
                logger.error("Error triggering extraction for conversation {}: {}",
                           conversationId, e.getMessage());
                throw new CompletionException(e);
            }
        }, executorService);
    }

    /**
     * Trigger memory extraction with retry mechanism.
     *
     * @param conversationId Conversation ID
     * @param sessionId Session ID
     * @return Future for async operation with retry
     */
    public CompletableFuture<Integer> triggerExtractionWithRetry(String conversationId, String sessionId) {
        int maxAttempts = config.get("memory.scheduler.retryMaxAttempts", 3);
        long initialDelay = config.get("memory.scheduler.retryInitialDelay", 1000);

        return CompletableFuture.supplyAsync(() -> {
            int attempts = 0;
            Exception lastException = null;

            while (attempts < maxAttempts) {
                try {
                    attempts++;
                    logger.debug("Extraction attempt {} for conversation: {}", attempts, conversationId);
                    return triggerService.triggerMemoryExtraction(conversationId, sessionId);
                } catch (Exception e) {
                    lastException = e;
                    if (attempts < maxAttempts) {
                        long delay = initialDelay * (1L << (attempts - 1)); // Exponential backoff
                        logger.warn("Extraction attempt {} failed for conversation {}, retrying in {}ms: {}",
                                   attempts, conversationId, delay, e.getMessage());
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new TriggerServiceException("Retry interrupted", -1, ie);
                        }
                    }
                }
            }

            throw new TriggerServiceException(
                String.format("Extraction failed after %d attempts: %s", maxAttempts, lastException.getMessage()),
                -1, lastException);
        }, executorService);
    }

    // ========================================================================
    // Error Handling and Recovery
    // ========================================================================

    /**
     * Handle extraction error with degradation strategy.
     *
     * @param error The error that occurred
     */
    private void handleExtractionError(Exception error) {
        // Log error details
        logger.error("Memory extraction error: {}", error.getMessage());

        // Implement degradation strategy
        String degradationMode = config.get("memory.scheduler.degradationMode", "log_only");

        switch (degradationMode) {
            case "disable":
                logger.warn("Disabling scheduler due to errors");
                enabled = false;
                break;
            case "backoff":
                logger.warn("Backing off scheduler execution");
                scheduleBackoff();
                break;
            case "log_only":
            default:
                // Just log the error, continue normal operation
                break;
        }
    }

    /**
     * Schedule exponential backoff for recovery.
     */
    private void scheduleBackoff() {
        long backoffDelay = config.get("memory.scheduler.backoffDelay", 60000); // 1 minute default

        retryExecutorService.schedule(() -> {
            logger.info("Recovering from backoff, re-enabling scheduler");
            enabled = true;
        }, backoffDelay, TimeUnit.MILLISECONDS);
    }

    /**
     * Check and recover from errors.
     */
    private void checkAndRecoverFromErrors() {
        long failureRate = calculateFailureRate();
        long threshold = config.get("memory.scheduler.failureRateThreshold", 50); // 50%

        if (failureRate > threshold) {
            logger.warn("High failure rate detected: {}%", failureRate);
            handleExtractionError(new Exception("High failure rate: " + failureRate + "%"));
        }
    }

    /**
     * Calculate failure rate as percentage.
     *
     * @return Failure rate (0-100)
     */
    private long calculateFailureRate() {
        long total = totalExtractions.get();
        if (total == 0) {
            return 0;
        }
        long failed = failedExtractions.get();
        return (failed * 100) / total;
    }

    // ========================================================================
    // Metrics and Monitoring
    // ========================================================================

    /**
     * Log health metrics.
     */
    private void logHealthMetrics() {
        logger.info("MemoryExtractionScheduler Health Metrics:");
        logger.info("  Enabled: {}", enabled);
        logger.info("  Running: {}", running);
        logger.info("  Active Tasks: {}/{}", activeTasks.get(), config.get("memory.scheduler.maxConcurrentTasks", 5));
        logger.info("  Total Extractions: {}", totalExtractions.get());
        logger.info("  Successful: {} ({}%)", successfulExtractions.get(),
                   calculateSuccessRate());
        logger.info("  Failed: {} ({}%)", failedExtractions.get(),
                   calculateFailureRate());
        logger.info("  Avg Processing Time: {}ms", calculateAverageProcessingTime());
        logger.info("  Last Execution Time: {}ms", lastExecutionTime.get());
    }

    /**
     * Calculate success rate as percentage.
     *
     * @return Success rate (0-100)
     */
    public long calculateSuccessRate() {
        long total = totalExtractions.get();
        if (total == 0) {
            return 0;
        }
        long successful = successfulExtractions.get();
        return (successful * 100) / total;
    }

    /**
     * Calculate average processing time.
     *
     * @return Average time in milliseconds
     */
    public long calculateAverageProcessingTime() {
        long total = totalExtractions.get();
        if (total == 0) {
            return 0;
        }
        return totalProcessingTimeMs.get() / total;
    }

    /**
     * Get comprehensive metrics.
     *
     * @return Metrics map
     */
    public java.util.Map<String, Object> getMetrics() {
        java.util.Map<String, Object> metrics = new ConcurrentHashMap<>();
        metrics.put("enabled", enabled);
        metrics.put("running", running);
        metrics.put("activeTasks", activeTasks.get());
        metrics.put("totalExtractions", totalExtractions.get());
        metrics.put("successfulExtractions", successfulExtractions.get());
        metrics.put("failedExtractions", failedExtractions.get());
        metrics.put("successRate", calculateSuccessRate());
        metrics.put("failureRate", calculateFailureRate());
        metrics.put("averageProcessingTime", calculateAverageProcessingTime());
        metrics.put("lastExecutionTime", lastExecutionTime.get());
        metrics.put("lastExecutionTimestamp", lastExecutionTimestamp);
        return metrics;
    }

    // ========================================================================
    // Configuration Management
    // ========================================================================

    /**
     * Enable scheduler.
     */
    public void enable() {
        this.enabled = true;
        logger.info("Memory extraction scheduler enabled");
    }

    /**
     * Disable scheduler.
     */
    public void disable() {
        this.enabled = false;
        logger.info("Memory extraction scheduler disabled");
    }

    /**
     * Check if scheduler is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Check if scheduler is currently running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get active task count.
     *
     * @return Active task count
     */
    public int getActiveTaskCount() {
        return activeTasks.get();
    }

    /**
     * Update configuration dynamically.
     *
     * @param key Configuration key
     * @param value Configuration value
     */
    public void updateConfiguration(String key, Object value) {
        config.set(key, value);
        logger.info("Updated configuration: {} = {}", key, value);
    }

    // ========================================================================
    // Lifecycle Management
    // ========================================================================

    /**
     * Shutdown scheduler gracefully.
     */
    public void shutdown() {
        logger.info("Shutting down MemoryExtractionScheduler");

        enabled = false;

        try {
            // Shutdown executor services
            executorService.shutdown();
            retryExecutorService.shutdown();

            // Wait for active tasks to complete
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("Executor service did not terminate in time, forcing shutdown");
                executorService.shutdownNow();
            }

            if (!retryExecutorService.awaitTermination(10, TimeUnit.SECONDS)) {
                retryExecutorService.shutdownNow();
            }

            logger.info("MemoryExtractionScheduler shutdown complete");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Shutdown interrupted", e);
        }
    }

    /**
     * Restart scheduler.
     */
    public void restart() {
        logger.info("Restarting MemoryExtractionScheduler");
        shutdown();
        enabled = true;
        logger.info("MemoryExtractionScheduler restarted");
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Create executor service with appropriate configuration.
     *
     * @return Configured executor service
     */
    private ExecutorService createExecutorService() {
        int maxConcurrent = config.get("memory.scheduler.maxConcurrentTasks", 5);
        return new ThreadPoolExecutor(
            1, // Core pool size
            maxConcurrent, // Maximum pool size
            60L, TimeUnit.SECONDS, // Keep-alive time
            new LinkedBlockingQueue<>(100), // Queue capacity
            new ThreadPoolExecutor.CallerRunsPolicy() // Rejection policy
        );
    }
}
