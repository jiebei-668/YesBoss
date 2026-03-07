package tech.yesboss.memory.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Memory configuration manager with hot reload support
 *
 * Features:
 * - Hot reload configuration from file
 * - Schedule-based configuration checks
 * - Configuration validation before applying
 * - Rollback on validation failure
 * - Thread-safe configuration updates
 */
public class MemoryConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(MemoryConfigManager.class);

    private final MemoryConfig config;
    private final ScheduledExecutorService scheduler;
    private final Path configFilePath;
    private long lastModified;
    private volatile boolean running;

    /**
     * Constructor
     *
     * @param configFilePath Path to configuration file
     */
    public MemoryConfigManager(String configFilePath) {
        this.config = MemoryConfig.getInstance();
        this.configFilePath = Paths.get(configFilePath);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "memory-config-reloader");
            thread.setDaemon(true);
            return thread;
        });
        this.lastModified = 0;
        this.running = false;

        // Listen for backend type changes
        config.addListener(MemoryConfig.BACKEND_TYPE, (key, oldValue, newValue) -> {
            logger.info("Backend type changed from {} to {}", oldValue, newValue);
            onBackendTypeChanged(oldValue, newValue);
        });
    }

    /**
     * Start hot reload monitoring
     */
    public void start() {
        if (running) {
            logger.warn("Config manager is already running");
            return;
        }

        if (!config.get(MemoryConfig.HOT_RELOAD_ENABLED, false)) {
            logger.info("Hot reload is disabled");
            return;
        }

        running = true;
        int intervalSeconds = config.get(MemoryConfig.HOT_RELOAD_INTERVAL_SECONDS, 60);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkForUpdates();
            } catch (Exception e) {
                logger.error("Error checking for configuration updates", e);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);

        logger.info("Config manager started with {} second interval", intervalSeconds);
    }

    /**
     * Stop hot reload monitoring
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("Config manager stopped");
    }

    /**
     * Check for configuration file updates
     */
    private void checkForUpdates() {
        try {
            File file = configFilePath.toFile();
            if (!file.exists()) {
                logger.debug("Configuration file does not exist: {}", configFilePath);
                return;
            }

            long currentModified = file.lastModified();
            if (currentModified > lastModified) {
                logger.info("Configuration file modified, reloading: {}", configFilePath);
                reloadConfig();
                lastModified = currentModified;
            }
        } catch (Exception e) {
            logger.error("Error checking for configuration updates", e);
        }
    }

    /**
     * Reload configuration from file
     */
    public void reloadConfig() {
        try {
            // Create backup of current configuration
            Map<String, Object> backup = config.getAll();

            // Load new configuration
            Map<String, Object> newConfig = loadConfigFromFile(configFilePath);

            // Validate new configuration
            config.setAll(newConfig);
            if (!config.validate()) {
                logger.error("New configuration validation failed, rolling back");
                config.resetToDefaults();
                config.setAll(backup);
                return;
            }

            logger.info("Configuration reloaded successfully");
        } catch (Exception e) {
            logger.error("Failed to reload configuration", e);
        }
    }

    /**
     * Load configuration from file
     *
     * @param filePath Path to configuration file
     * @return Configuration map
     */
    private Map<String, Object> loadConfigFromFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);

        // Simple properties file parsing
        // In production, would use a proper configuration format (YAML, JSON, etc.)
        Map<String, Object> config = new java.util.HashMap<>();

        String[] lines = content.split("\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] parts = line.split("=", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();

                // Parse value type
                Object parsedValue = parseValue(value);
                config.put(key, parsedValue);
            }
        }

        return config;
    }

    /**
     * Parse configuration value
     *
     * @param value String value
     * @return Parsed value
     */
    private Object parseValue(String value) {
        // Boolean
        if ("true".equalsIgnoreCase(value)) {
            return true;
        } else if ("false".equalsIgnoreCase(value)) {
            return false;
        }

        // Integer
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // Not an integer
        }

        // Double
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            // Not a double
        }

        // String
        return value;
    }

    /**
     * Handle backend type change
     *
     * @param oldValue Old backend type
     * @param newValue New backend type
     */
    private void onBackendTypeChanged(Object oldValue, Object newValue) {
        logger.info("Backend type change detected: {} -> {}", oldValue, newValue);
        // Additional logic can be added here, such as:
        // - Reinitializing vector store
        // - Migrating data between backends
        // - Notifying dependent services
    }

    /**
     * Get current configuration
     *
     * @return Memory configuration
     */
    public MemoryConfig getConfig() {
        return config;
    }

    /**
     * Check if manager is running
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running;
    }
}
