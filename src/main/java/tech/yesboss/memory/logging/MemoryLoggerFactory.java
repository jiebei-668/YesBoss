package tech.yesboss.memory.logging;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memory Logger Factory
 *
 * <p>Factory for creating MemoryLogger instances with configuration support.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Named logger instances</li>
 *   <li>Configuration-based creation</li>
 *   <li>Default configuration management</li>
 *   <li>Hot reload support</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * // Get named logger
 * MemoryLogger logger = MemoryLoggerFactory.getLogger("resource");
 *
 * // Create with custom configuration
 * MemoryLoggerConfig config = MemoryLoggerConfig.builder()
 *     .level(MemoryLogger.LogLevel.DEBUG)
 *     .includeContext(true)
 *     .build();
 * MemoryLogger logger = MemoryLoggerFactory.create("snippet", config);
 * </pre>
 *
 * @author YesBoss Team
 * @version 1.0
 */
public class MemoryLoggerFactory {

    private static final Map<String, MemoryLogger> loggers = new ConcurrentHashMap<>();
    private static volatile MemoryLoggerConfig defaultConfig;

    // Private constructor to prevent instantiation
    private MemoryLoggerFactory() {
    }

    /**
     * Initialize factory with default configuration.
     */
    public static synchronized void initialize() {
        if (defaultConfig == null) {
            defaultConfig = MemoryLoggerConfig.defaults();
        }
    }

    /**
     * Initialize factory with custom configuration.
     *
     * @param config Default configuration
     */
    public static synchronized void initialize(MemoryLoggerConfig config) {
        defaultConfig = config;
    }

    /**
     * Initialize factory from configuration map.
     *
     * @param configMap Configuration map
     */
    public static synchronized void initializeFromMap(Map<String, Object> configMap) {
        MemoryLoggerConfig config = MemoryLoggerConfig.fromMap(configMap);
        initialize(config);
    }

    /**
     * Get logger by name.
     *
     * @param name Logger name
     * @return Logger instance
     */
    public static MemoryLogger getLogger(String name) {
        return loggers.computeIfAbsent(name, k -> createLogger(k, defaultConfig));
    }

    /**
     * Get or create logger with custom configuration.
     *
     * @param name Logger name
     * @param config Logger configuration
     * @return Logger instance
     */
    public static MemoryLogger getLogger(String name, MemoryLoggerConfig config) {
        return loggers.compute(name, (k, existing) -> {
            if (existing != null) {
                return existing; // Return existing logger
            }
            return createLogger(k, config);
        });
    }

    /**
     * Create new logger instance.
     *
     * @param name Logger name
     * @return New logger instance
     */
    public static MemoryLogger create(String name) {
        return createLogger(name, defaultConfig != null ? defaultConfig : MemoryLoggerConfig.defaults());
    }

    /**
     * Create new logger instance with custom configuration.
     *
     * @param name Logger name
     * @param config Logger configuration
     * @return New logger instance
     */
    public static MemoryLogger create(String name, MemoryLoggerConfig config) {
        return createLogger(name, config);
    }

    /**
     * Get the default configuration.
     *
     * @return Default configuration, or null if not set
     */
    public static MemoryLoggerConfig getDefaultConfig() {
        return defaultConfig;
    }

    /**
     * Set the default configuration.
     *
     * @param config Default configuration
     */
    public static synchronized void setDefaultConfig(MemoryLoggerConfig config) {
        defaultConfig = config;
    }

    /**
     * Check if factory is initialized.
     *
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return defaultConfig != null;
    }

    /**
     * Clear all named loggers.
     */
    public static synchronized void clearAll() {
        loggers.clear();
    }

    /**
     * Reload configuration for all loggers.
     *
     * <p>Note: This recreates all loggers with the current default configuration.</p>
     */
    public static synchronized void reloadConfiguration() {
        if (defaultConfig == null) {
            return;
        }

        // Recreate all loggers with new configuration
        Map<String, MemoryLogger> newLoggers = new ConcurrentHashMap<>();
        loggers.keySet().forEach(name -> {
            newLoggers.put(name, createLogger(name, defaultConfig));
        });

        loggers.clear();
        loggers.putAll(newLoggers);
    }

    /**
     * Get count of active loggers.
     *
     * @return Count of loggers
     */
    public static int getLoggerCount() {
        return loggers.size();
    }

    // ==================== Private Helper Methods ====================

    /**
     * Create a logger instance with configuration.
     *
     * @param name Logger name
     * @param config Logger configuration
     * @return Logger instance
     */
    private static MemoryLogger createLogger(String name, MemoryLoggerConfig config) {
        if (config == null) {
            config = MemoryLoggerConfig.defaults();
        }
        return new MemoryLoggerImpl(name, config);
    }
}
