package tech.yesboss.memory.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memory module configuration management system
 *
 * Supports:
 * - Dual backend switching (sqlite-vec / PostgreSQL+pgvector)
 * - Hot reload of configuration
 * - Thread-safe configuration access
 * - Configuration validation
 * - Default values and overrides
 */
public class MemoryConfig {

    private static final Logger logger = LoggerFactory.getLogger(MemoryConfig.class);

    // Backend types
    public enum BackendType {
        SQLITE_VEC,
        POSTGRESQL_PGVECTOR
    }

    // Configuration keys
    public static final String BACKEND_TYPE = "memory.backend.type";
    public static final String SQLITE_PATH = "memory.backend.sqlite.path";
    public static final String POSTGRESQL_URL = "memory.backend.postgresql.url";
    public static final String POSTGRESQL_HOST = "memory.backend.postgresql.host";
    public static final String POSTGRESQL_PORT = "memory.backend.postgresql.port";
    public static final String POSTGRESQL_DATABASE = "memory.backend.postgresql.database";
    public static final String POSTGRESQL_USER = "memory.backend.postgresql.user";
    public static final String POSTGRESQL_PASSWORD = "memory.backend.postgresql.password";
    public static final String VECTOR_DIMENSION = "memory.vector.dimension";
    public static final String SIMILARITY_THRESHOLD = "memory.vector.similarity_threshold";
    public static final String BATCH_SIZE = "memory.vector.batch_size";
    public static final String CACHE_ENABLED = "memory.cache.enabled";
    public static final String CACHE_SIZE = "memory.cache.size";
    public static final String CACHE_TTL_HOURS = "memory.cache.ttl_hours";
    public static final String HOT_RELOAD_ENABLED = "memory.hot_reload.enabled";
    public static final String HOT_RELOAD_INTERVAL_SECONDS = "memory.hot_reload.interval_seconds";

    // Default values
    private static final Map<String, Object> DEFAULTS = new HashMap<>();
    static {
        DEFAULTS.put(BACKEND_TYPE, BackendType.SQLITE_VEC);
        DEFAULTS.put(SQLITE_PATH, "data/memory.db");
        DEFAULTS.put(POSTGRESQL_HOST, "localhost");
        DEFAULTS.put(POSTGRESQL_PORT, 5432);
        DEFAULTS.put(POSTGRESQL_DATABASE, "yesboss_memory");
        DEFAULTS.put(POSTGRESQL_USER, "yesboss");
        DEFAULTS.put(POSTGRESQL_PASSWORD, "");
        DEFAULTS.put(VECTOR_DIMENSION, 1536);
        DEFAULTS.put(SIMILARITY_THRESHOLD, 0.7);
        DEFAULTS.put(BATCH_SIZE, 100);
        DEFAULTS.put(CACHE_ENABLED, true);
        DEFAULTS.put(CACHE_SIZE, 10000);
        DEFAULTS.put(CACHE_TTL_HOURS, 168); // 7 days
        DEFAULTS.put(HOT_RELOAD_ENABLED, false);
        DEFAULTS.put(HOT_RELOAD_INTERVAL_SECONDS, 60);
    }

    // Thread-safe configuration storage
    private final Map<String, Object> config;

    // Configuration change listeners
    private final Map<String, java.util.List<ConfigChangeListener>> listeners;

    /**
     * Configuration change listener interface
     */
    @FunctionalInterface
    public interface ConfigChangeListener {
        void onConfigChanged(String key, Object oldValue, Object newValue);
    }

    /**
     * Private constructor for singleton pattern
     */
    private MemoryConfig() {
        this.config = new ConcurrentHashMap<>(DEFAULTS);
        this.listeners = new ConcurrentHashMap<>();
        logger.info("MemoryConfig initialized with defaults");
    }

    /**
     * Singleton holder
     */
    private static class Holder {
        private static final MemoryConfig INSTANCE = new MemoryConfig();
    }

    /**
     * Get the singleton instance
     */
    public static MemoryConfig getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Get configuration value
     *
     * @param key Configuration key
     * @return Configuration value or default if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        Object value = config.get(key);
        if (value == null) {
            value = DEFAULTS.get(key);
        }
        return (T) value;
    }

    /**
     * Get configuration value with default
     *
     * @param key Configuration key
     * @param defaultValue Default value if key not found
     * @return Configuration value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        return (T) value;
    }

    /**
     * Set configuration value
     *
     * @param key Configuration key
     * @param value Configuration value
     */
    public void set(String key, Object value) {
        Object oldValue = config.get(key);
        if (!Objects.equals(oldValue, value)) {
            config.put(key, value);
            logger.debug("Configuration updated: {} = {}", key, value);
            notifyListeners(key, oldValue, value);
        }
    }

    /**
     * Set multiple configuration values
     *
     * @param values Map of configuration keys to values
     */
    public void setAll(Map<String, Object> values) {
        values.forEach(this::set);
    }

    /**
     * Check if configuration key exists
     *
     * @param key Configuration key
     * @return true if key exists
     */
    public boolean contains(String key) {
        return config.containsKey(key) || DEFAULTS.containsKey(key);
    }

    /**
     * Remove configuration value (reverts to default)
     *
     * @param key Configuration key
     */
    public void remove(String key) {
        Object oldValue = config.remove(key);
        Object defaultValue = DEFAULTS.get(key);
        logger.debug("Configuration removed: {} (reverted to default: {})", key, defaultValue);
        notifyListeners(key, oldValue, defaultValue);
    }

    /**
     * Get all configuration values
     *
     * @return Map of all configuration keys to values
     */
    public Map<String, Object> getAll() {
        Map<String, Object> all = new HashMap<>(DEFAULTS);
        all.putAll(config);
        return all;
    }

    /**
     * Reset all configuration to defaults
     */
    public void resetToDefaults() {
        config.clear();
        logger.info("Configuration reset to defaults");
    }

    /**
     * Validate configuration
     *
     * @return true if configuration is valid
     */
    public boolean validate() {
        try {
            BackendType backendType = getBackendType();

            if (backendType == BackendType.SQLITE_VEC) {
                String path = get(SQLITE_PATH);
                if (path == null || path.trim().isEmpty()) {
                    logger.error("SQLite path is not configured");
                    return false;
                }
            } else if (backendType == BackendType.POSTGRESQL_PGVECTOR) {
                String url = get(POSTGRESQL_URL);
                String host = get(POSTGRESQL_HOST);
                Integer port = get(POSTGRESQL_PORT);
                String database = get(POSTGRESQL_DATABASE);
                String user = get(POSTGRESQL_USER);

                if (url == null && (host == null || port == null || database == null || user == null)) {
                    logger.error("PostgreSQL configuration is incomplete");
                    return false;
                }
            }

            Integer dimension = get(VECTOR_DIMENSION);
            if (dimension <= 0 || dimension > 10000) {
                logger.error("Invalid vector dimension: {}", dimension);
                return false;
            }

            Double threshold = get(SIMILARITY_THRESHOLD);
            if (threshold < 0.0 || threshold > 1.0) {
                logger.error("Invalid similarity threshold: {}", threshold);
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.error("Configuration validation failed", e);
            return false;
        }
    }

    /**
     * Get backend type
     *
     * @return Backend type
     */
    public BackendType getBackendType() {
        String type = get(BACKEND_TYPE);
        if (type != null) {
            try {
                return BackendType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid backend type: {}, using default", type);
            }
        }
        return DEFAULTS.get(BACKEND_TYPE);
    }

    /**
     * Set backend type
     *
     * @param backendType Backend type
     */
    public void setBackendType(BackendType backendType) {
        set(BACKEND_TYPE, backendType);
    }

    /**
     * Get PostgreSQL connection URL
     *
     * @return Connection URL
     */
    public String getPostgreSQLUrl() {
        String url = get(POSTGRESQL_URL);
        if (url != null && !url.trim().isEmpty()) {
            return url;
        }

        // Build URL from components
        String host = get(POSTGRESQL_HOST);
        Integer port = get(POSTGRESQL_PORT);
        String database = get(POSTGRESQL_DATABASE);

        return String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
    }

    /**
     * Switch backend
     *
     * @param newBackend New backend type
     * @return true if switch was successful
     */
    public boolean switchBackend(BackendType newBackend) {
        BackendType currentBackend = getBackendType();
        if (currentBackend == newBackend) {
            logger.info("Already using {} backend", newBackend);
            return true;
        }

        logger.info("Switching backend from {} to {}", currentBackend, newBackend);
        setBackendType(newBackend);

        // Validate new configuration
        if (!validate()) {
            logger.error("Backend switch failed: validation failed");
            setBackendType(currentBackend); // Revert
            return false;
        }

        logger.info("Backend switched successfully to {}", newBackend);
        return true;
    }

    /**
     * Add configuration change listener
     *
     * @param key Configuration key to listen for
     * @param listener Listener callback
     */
    public void addListener(String key, ConfigChangeListener listener) {
        listeners.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(listener);
    }

    /**
     * Remove configuration change listener
     *
     * @param key Configuration key
     * @param listener Listener callback
     */
    public void removeListener(String key, ConfigChangeListener listener) {
        java.util.List<ConfigChangeListener> keyListeners = listeners.get(key);
        if (keyListeners != null) {
            keyListeners.remove(listener);
        }
    }

    /**
     * Notify listeners of configuration change
     *
     * @param key Configuration key
     * @param oldValue Old value
     * @param newValue New value
     */
    private void notifyListeners(String key, Object oldValue, Object newValue) {
        java.util.List<ConfigChangeListener> keyListeners = listeners.get(key);
        if (keyListeners != null) {
            for (ConfigChangeListener listener : keyListeners) {
                try {
                    listener.onConfigChanged(key, oldValue, newValue);
                } catch (Exception e) {
                    logger.error("Error notifying config listener for key: {}", key, e);
                }
            }
        }
    }

    /**
     * Load configuration from environment variables
     */
    public void loadFromEnvironment() {
        Map<String, Object> envConfig = new HashMap<>();

        // Backend type
        String backendType = System.getenv("MEMORY_BACKEND_TYPE");
        if (backendType != null) {
            try {
                envConfig.put(BACKEND_TYPE, BackendType.valueOf(backendType.toUpperCase()));
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid MEMORY_BACKEND_TYPE: {}", backendType);
            }
        }

        // SQLite path
        String sqlitePath = System.getenv("MEMORY_SQLITE_PATH");
        if (sqlitePath != null) {
            envConfig.put(SQLITE_PATH, sqlitePath);
        }

        // PostgreSQL configuration
        String pgUrl = System.getenv("MEMORY_POSTGRESQL_URL");
        if (pgUrl != null) {
            envConfig.put(POSTGRESQL_URL, pgUrl);
        }

        String pgHost = System.getenv("MEMORY_POSTGRESQL_HOST");
        if (pgHost != null) {
            envConfig.put(POSTGRESQL_HOST, pgHost);
        }

        String pgPort = System.getenv("MEMORY_POSTGRESQL_PORT");
        if (pgPort != null) {
            try {
                envConfig.put(POSTGRESQL_PORT, Integer.parseInt(pgPort));
            } catch (NumberFormatException e) {
                logger.warn("Invalid MEMORY_POSTGRESQL_PORT: {}", pgPort);
            }
        }

        String pgDatabase = System.getenv("MEMORY_POSTGRESQL_DATABASE");
        if (pgDatabase != null) {
            envConfig.put(POSTGRESQL_DATABASE, pgDatabase);
        }

        String pgUser = System.getenv("MEMORY_POSTGRESQL_USER");
        if (pgUser != null) {
            envConfig.put(POSTGRESQL_USER, pgUser);
        }

        String pgPassword = System.getenv("MEMORY_POSTGRESQL_PASSWORD");
        if (pgPassword != null) {
            envConfig.put(POSTGRESQL_PASSWORD, pgPassword);
        }

        // Vector configuration
        String vectorDim = System.getenv("MEMORY_VECTOR_DIMENSION");
        if (vectorDim != null) {
            try {
                envConfig.put(VECTOR_DIMENSION, Integer.parseInt(vectorDim));
            } catch (NumberFormatException e) {
                logger.warn("Invalid MEMORY_VECTOR_DIMENSION: {}", vectorDim);
            }
        }

        String similarityThreshold = System.getenv("MEMORY_SIMILARITY_THRESHOLD");
        if (similarityThreshold != null) {
            try {
                envConfig.put(SIMILARITY_THRESHOLD, Double.parseDouble(similarityThreshold));
            } catch (NumberFormatException e) {
                logger.warn("Invalid MEMORY_SIMILARITY_THRESHOLD: {}", similarityThreshold);
            }
        }

        setAll(envConfig);
        logger.info("Loaded {} configuration values from environment", envConfig.size());
    }

    /**
     * Get configuration summary
     *
     * @return Configuration summary string
     */
    public String getSummary() {
        BackendType backendType = getBackendType();
        StringBuilder summary = new StringBuilder();

        summary.append("Memory Configuration:\n");
        summary.append("  Backend Type: ").append(backendType).append("\n");

        if (backendType == BackendType.SQLITE_VEC) {
            summary.append("  SQLite Path: ").append(get(SQLITE_PATH)).append("\n");
        } else {
            summary.append("  PostgreSQL URL: ").append(getPostgreSQLUrl()).append("\n");
        }

        summary.append("  Vector Dimension: ").append(get(VECTOR_DIMENSION)).append("\n");
        summary.append("  Similarity Threshold: ").append(get(SIMILARITY_THRESHOLD)).append("\n");
        summary.append("  Batch Size: ").append(get(BATCH_SIZE)).append("\n");
        summary.append("  Cache Enabled: ").append(get(CACHE_ENABLED)).append("\n");
        summary.append("  Cache Size: ").append(get(CACHE_SIZE)).append("\n");
        summary.append("  Hot Reload: ").append(get(HOT_RELOAD_ENABLED)).append("\n");

        return summary.toString();
    }
}
