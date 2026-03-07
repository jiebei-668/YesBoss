package tech.yesboss.memory.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

/**
 * Loader for cache configuration from application-memory.yml or application-memory.properties.
 *
 * <p>This class loads cache configuration with support for:
 * <ul>
 *   <li>Properties file format</li>
 *   <li>Environment variable override</li>
 *   <li>Default values for missing properties</li>
 *   <li>Hot-reload support</li>
 * </ul>
 */
public class CacheConfigurationLoader {

    private static final Logger logger = LoggerFactory.getLogger(CacheConfigurationLoader.class);

    private static final String DEFAULT_CONFIG_FILE = "application-memory.properties";
    private static final String ENV_PREFIX = "YESBOSS_CACHE_";

    // Configuration keys
    private static final String KEY_MAX_SIZE = "cache.maxSize";
    private static final String KEY_EXPIRE_AFTER_WRITE = "cache.expireAfterWrite";
    private static final String KEY_EXPIRE_AFTER_ACCESS = "cache.expireAfterAccess";
    private static final String KEY_REFRESH_AFTER_WRITE = "cache.refreshAfterWrite";
    private static final String KEY_RECORD_STATS = "cache.recordStats";
    private static final String KEY_CONCURRENCY_LEVEL = "cache.concurrencyLevel";
    private static final String KEY_MULTI_LEVEL_ENABLED = "cache.multiLevel.enabled";
    private static final String KEY_L2_MAX_SIZE = "cache.multiLevel.l2.maxSize";
    private static final String KEY_L2_EXPIRE_AFTER_WRITE = "cache.multiLevel.l2.expireAfterWrite";

    private Properties properties;
    private volatile CacheConfig cachedConfig;
    private volatile CacheConfig cachedL2Config;
    private volatile Boolean cachedMultiLevelEnabled;

    /**
     * Create a new configuration loader.
     */
    public CacheConfigurationLoader() {
        this.properties = new Properties();
        loadConfiguration();
    }

    /**
     * Load configuration from file and environment.
     */
    private void loadConfiguration() {
        // Load from properties file
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(DEFAULT_CONFIG_FILE)) {
            if (is != null) {
                properties.load(is);
                logger.info("Loaded cache configuration from {}", DEFAULT_CONFIG_FILE);
            } else {
                logger.debug("No cache configuration file found, using defaults");
            }
        } catch (IOException e) {
            logger.warn("Failed to load cache configuration from {}, using defaults", DEFAULT_CONFIG_FILE, e);
        }

        // Override with environment variables
        loadEnvironmentVariables();

        // Invalidate cached configuration
        invalidateCache();
    }

    /**
     * Load configuration from environment variables.
     */
    private void loadEnvironmentVariables() {
        System.getenv().entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(ENV_PREFIX))
            .forEach(entry -> {
                String key = entry.getKey().substring(ENV_PREFIX.length()).toLowerCase();
                String value = entry.getValue();
                properties.setProperty(key, value);
                logger.debug("Environment override: {}={}", key, value);
            });
    }

    /**
     * Invalidate cached configuration to force reload on next access.
     */
    public void invalidateCache() {
        this.cachedConfig = null;
        this.cachedL2Config = null;
        this.cachedMultiLevelEnabled = null;
        logger.debug("Configuration cache invalidated");
    }

    /**
     * Reload configuration from file and environment.
     */
    public void reload() {
        properties.clear();
        loadConfiguration();
        logger.info("Cache configuration reloaded");
    }

    /**
     * Get the cache configuration.
     *
     * @return CacheConfig instance
     */
    public CacheConfig getCacheConfig() {
        if (cachedConfig == null) {
            synchronized (this) {
                if (cachedConfig == null) {
                    cachedConfig = buildCacheConfig();
                }
            }
        }
        return cachedConfig;
    }

    /**
     * Get the L2 cache configuration.
     *
     * @return CacheConfig instance for L2, or null if not configured
     */
    public CacheConfig getL2CacheConfig() {
        if (cachedL2Config == null) {
            synchronized (this) {
                if (cachedL2Config == null) {
                    cachedL2Config = buildL2CacheConfig();
                }
            }
        }
        return cachedL2Config;
    }

    /**
     * Check if multi-level caching is enabled.
     *
     * @return true if multi-level caching is enabled
     */
    public boolean isMultiLevelEnabled() {
        if (cachedMultiLevelEnabled == null) {
            synchronized (this) {
                if (cachedMultiLevelEnabled == null) {
                    cachedMultiLevelEnabled = getBoolean(KEY_MULTI_LEVEL_ENABLED, false);
                }
            }
        }
        return cachedMultiLevelEnabled;
    }

    /**
     * Build the cache configuration from properties.
     */
    private CacheConfig buildCacheConfig() {
        CacheConfig.Builder builder = CacheConfig.builder();

        // Maximum size
        long maxSize = getLong(KEY_MAX_SIZE, 10000);
        builder.maximumSize(maxSize);

        // Expiration times
        String expireAfterWriteStr = properties.getProperty(KEY_EXPIRE_AFTER_WRITE);
        if (expireAfterWriteStr != null) {
            Duration expireAfterWrite = parseDuration(expireAfterWriteStr);
            if (expireAfterWrite != null) {
                builder.expireAfterWrite(expireAfterWrite);
            }
        }

        String expireAfterAccessStr = properties.getProperty(KEY_EXPIRE_AFTER_ACCESS);
        if (expireAfterAccessStr != null) {
            Duration expireAfterAccess = parseDuration(expireAfterAccessStr);
            if (expireAfterAccess != null) {
                builder.expireAfterAccess(expireAfterAccess);
            }
        }

        String refreshAfterWriteStr = properties.getProperty(KEY_REFRESH_AFTER_WRITE);
        if (refreshAfterWriteStr != null) {
            Duration refreshAfterWrite = parseDuration(refreshAfterWriteStr);
            if (refreshAfterWrite != null) {
                builder.refreshAfterWrite(refreshAfterWrite);
            }
        }

        // Statistics
        boolean recordStats = getBoolean(KEY_RECORD_STATS, true);
        builder.recordStats(recordStats);

        // Concurrency level
        int concurrencyLevel = getInteger(KEY_CONCURRENCY_LEVEL, 4);
        builder.concurrencyLevel(concurrencyLevel);

        CacheConfig config = builder.build();

        logger.info("Built cache configuration: maxSize={}, recordStats={}, concurrencyLevel={}",
            maxSize, recordStats, concurrencyLevel);

        return config;
    }

    /**
     * Build the L2 cache configuration from properties.
     */
    private CacheConfig buildL2CacheConfig() {
        if (!isMultiLevelEnabled()) {
            return null;
        }

        CacheConfig.Builder builder = CacheConfig.builder();

        // Maximum size
        long maxSize = getLong(KEY_L2_MAX_SIZE, 100000);
        builder.maximumSize(maxSize);

        // Expiration time
        String expireAfterWriteStr = properties.getProperty(KEY_L2_EXPIRE_AFTER_WRITE);
        if (expireAfterWriteStr != null) {
            Duration expireAfterWrite = parseDuration(expireAfterWriteStr);
            if (expireAfterWrite != null) {
                builder.expireAfterWrite(expireAfterWrite);
            }
        } else {
            // Default: 24 hours
            builder.expireAfterWrite(Duration.ofHours(24));
        }

        // Statistics
        builder.recordStats(true);

        // Concurrency level
        builder.concurrencyLevel(getInteger(KEY_CONCURRENCY_LEVEL, 4));

        CacheConfig config = builder.build();

        logger.info("Built L2 cache configuration: maxSize={}", maxSize);

        return config;
    }

    /**
     * Parse a duration string.
     *
     * @param durationStr the duration string (e.g., "1h", "30m", "60s")
     * @return Duration instance, or null if parsing fails
     */
    private Duration parseDuration(String durationStr) {
        if (durationStr == null || durationStr.trim().isEmpty()) {
            return null;
        }

        durationStr = durationStr.trim().toLowerCase();

        try {
            // Try parsing as number + unit
            long value = Long.parseLong(durationStr.replaceAll("[^0-9]", ""));

            if (durationStr.contains("h") || durationStr.contains("hour")) {
                return Duration.ofHours(value);
            } else if (durationStr.contains("m") || durationStr.contains("min")) {
                return Duration.ofMinutes(value);
            } else if (durationStr.contains("s") || durationStr.contains("sec")) {
                return Duration.ofSeconds(value);
            } else if (durationStr.contains("ms") || durationStr.contains("milli")) {
                return Duration.ofMillis(value);
            } else if (durationStr.contains("d") || durationStr.contains("day")) {
                return Duration.ofDays(value);
            } else {
                logger.warn("Unknown duration unit in: {}", durationStr);
                return null;
            }
        } catch (NumberFormatException e) {
            logger.warn("Failed to parse duration: {}", durationStr, e);
            return null;
        }
    }

    /**
     * Get a string property.
     */
    private String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Get an integer property.
     */
    private int getInteger(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for key {}: {}", key, value);
            return defaultValue;
        }
    }

    /**
     * Get a long property.
     */
    private long getLong(String key, long defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid long value for key {}: {}", key, value);
            return defaultValue;
        }
    }

    /**
     * Get a boolean property.
     */
    private boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * Get the configuration properties.
     *
     * @return Properties copy
     */
    public Properties getProperties() {
        Properties copy = new Properties();
        copy.putAll(properties);
        return copy;
    }

    /**
     * Get a property value.
     *
     * @param key the property key
     * @return the property value, or null if not found
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    /**
     * Set a property value programmatically.
     *
     * @param key the property key
     * @param value the property value
     */
    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
        invalidateCache();
        logger.debug("Set property: {}={}", key, value);
    }

    /**
     * Remove a property.
     *
     * @param key the property key
     */
    public void removeProperty(String key) {
        properties.remove(key);
        invalidateCache();
        logger.debug("Removed property: {}", key);
    }
}
