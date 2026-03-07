package tech.yesboss.memory.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memory Metrics Factory
 *
 * <p>Factory for creating MemoryMetrics instances with configuration support.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Singleton pattern for metrics instances</li>
 *   <li>Configuration-based creation</li>
 *   <li>Hot reload support for configuration changes</li>
 *   <li>Named metrics instances for different contexts</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * // Get default metrics instance
 * MemoryMetrics metrics = MemoryMetricsFactory.getInstance();
 *
 * // Get named metrics instance
 * MemoryMetrics cacheMetrics = MemoryMetricsFactory.getInstance("cache");
 *
 * // Create with custom configuration
 * MemoryMetricsConfig config = MemoryMetricsConfig.builder()
 *     .enabled(true)
 *     .trackPerformance(true)
 *     .build();
 * MemoryMetrics metrics = MemoryMetricsFactory.create(config);
 * </pre>
 *
 * @author YesBoss Team
 * @version 1.0
 */
public class MemoryMetricsFactory {

    private static final Logger logger = LoggerFactory.getLogger(MemoryMetricsFactory.class);

    private static final Map<String, MemoryMetrics> instances = new ConcurrentHashMap<>();
    private static volatile MemoryMetrics defaultInstance;
    private static volatile MemoryMetricsConfig globalConfig;

    // Private constructor to prevent instantiation
    private MemoryMetricsFactory() {
    }

    /**
     * Initialize the factory with global configuration.
     *
     * @param config Global configuration
     */
    public static synchronized void initialize(MemoryMetricsConfig config) {
        if (globalConfig != null) {
            logger.warn("MemoryMetricsFactory already initialized. Updating configuration.");
        }
        globalConfig = config;

        // Reinitialize default instance if it exists
        if (defaultInstance != null) {
            defaultInstance = createMetrics(globalConfig);
        }
    }

    /**
     * Initialize the factory from a configuration map.
     *
     * @param configMap Configuration map
     */
    public static synchronized void initializeFromMap(Map<String, Object> configMap) {
        MemoryMetricsConfig config = MemoryMetricsConfig.fromMap(configMap);
        initialize(config);
    }

    /**
     * Get the default metrics instance.
     *
     * <p>Creates instance with default configuration if not already initialized.</p>
     *
     * @return Default MemoryMetrics instance
     */
    public static MemoryMetrics getInstance() {
        if (defaultInstance == null) {
            synchronized (MemoryMetricsFactory.class) {
                if (defaultInstance == null) {
                    MemoryMetricsConfig config = (globalConfig != null) ? globalConfig : MemoryMetricsConfig.defaults();
                    defaultInstance = createMetrics(config);
                    logger.info("Created default MemoryMetrics instance with config: {}", config);
                }
            }
        }
        return defaultInstance;
    }

    /**
     * Get a named metrics instance.
     *
     * <p>Named instances are useful for tracking metrics in different contexts
     * (e.g., different repositories, different services).</p>
     *
     * @param name Name of the metrics instance
     * @return Named MemoryMetrics instance
     */
    public static MemoryMetrics getInstance(String name) {
        return instances.computeIfAbsent(name, k -> {
            MemoryMetricsConfig config = (globalConfig != null) ? globalConfig : MemoryMetricsConfig.defaults();
            logger.info("Creating named MemoryMetrics instance '{}' with config: {}", k, config);
            return createMetrics(config);
        });
    }

    /**
     * Create a new metrics instance with custom configuration.
     *
     * @param config Configuration for the instance
     * @return New MemoryMetrics instance
     */
    public static MemoryMetrics create(MemoryMetricsConfig config) {
        return createMetrics(config);
    }

    /**
     * Create a new metrics instance with default settings.
     *
     * @return New MemoryMetrics instance
     */
    public static MemoryMetrics create() {
        MemoryMetricsConfig config = (globalConfig != null) ? globalConfig : MemoryMetricsConfig.defaults();
        return createMetrics(config);
    }

    /**
     * Reset all metrics instances.
     *
     * <p>This resets the metrics but doesn't remove the instances.</p>
     */
    public static void resetAll() {
        if (defaultInstance != null) {
            defaultInstance.reset();
        }
        instances.values().forEach(MemoryMetrics::reset);
        logger.info("All MemoryMetrics instances reset");
    }

    /**
     * Clear all named metrics instances.
     *
     * <p>This removes all named instances but keeps the default instance.</p>
     */
    public static synchronized void clearAll() {
        instances.clear();
        logger.info("All named MemoryMetrics instances cleared");
    }

    /**
     * Reload configuration for all instances.
     *
     * <p>This recreates all instances with the current global configuration.</p>
     */
    public static synchronized void reloadConfiguration() {
        if (globalConfig == null) {
            logger.warn("Cannot reload configuration: global config not set");
            return;
        }

        // Recreate default instance
        defaultInstance = createMetrics(globalConfig);

        // Recreate all named instances
        Map<String, MemoryMetrics> newInstances = new ConcurrentHashMap<>();
        instances.keySet().forEach(name -> {
            newInstances.put(name, createMetrics(globalConfig));
        });
        instances.clear();
        instances.putAll(newInstances);

        logger.info("All MemoryMetrics instances reloaded with new configuration");
    }

    /**
     * Get the global configuration.
     *
     * @return Global configuration, or null if not set
     */
    public static MemoryMetricsConfig getGlobalConfig() {
        return globalConfig;
    }

    /**
     * Check if factory is initialized.
     *
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return globalConfig != null;
    }

    /**
     * Get count of active metrics instances.
     *
     * @return Count of instances (including default)
     */
    public static int getInstanceCount() {
        int count = (defaultInstance != null) ? 1 : 0;
        return count + instances.size();
    }

    // ==================== Private Helper Methods ====================

    /**
     * Create a metrics instance from configuration.
     *
     * @param config Configuration
     * @return MemoryMetrics instance
     */
    private static MemoryMetrics createMetrics(MemoryMetricsConfig config) {
        if (config == null) {
            config = MemoryMetricsConfig.defaults();
        }

        return new MemoryMetricsImpl(
            config.isEnabled(),
            config.isTrackPerformance(),
            config.isTrackErrors()
        );
    }
}
