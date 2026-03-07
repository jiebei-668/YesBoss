package tech.yesboss.memory.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memory Cache Factory
 *
 * <p>Factory for creating MemoryCache instances with configuration support.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Singleton pattern for default cache instances</li>
 *   <li>Named cache instances for different contexts</li>
 *   <li>Configuration-based creation</li>
 *   <li>Hot reload support</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * // Get default L1 cache
 * MemoryCache cache = MemoryCacheFactory.getL1Cache();
 *
 * // Get named L1 cache
 * MemoryCache cache = MemoryCacheFactory.getL1Cache("embeddings");
 *
 * // Create with custom configuration
 * MemoryCacheConfig config = MemoryCacheConfig.builder()
 *     .maxSize(1000)
 *     .expireAfterWriteMs(300000)
 *     .build();
 * MemoryCache cache = MemoryCacheFactory.create(config);
 * </pre>
 *
 * @author YesBoss Team
 * @version 1.0
 */
public class MemoryCacheFactory {

    private static final Logger logger = LoggerFactory.getLogger(MemoryCacheFactory.class);

    private static final Map<String, MemoryCache<?, ?>> l1Instances = new ConcurrentHashMap<>();
    private static final Map<String, MemoryCache<?, ?>> l2Instances = new ConcurrentHashMap<>();
    private static volatile MemoryCache<?, ?> defaultL1Cache;
    private static volatile MemoryCache<?, ?> defaultL2Cache;
    private static volatile MemoryCacheConfig defaultL1Config;
    private static volatile MemoryCacheConfig defaultL2Config;

    // Private constructor to prevent instantiation
    private MemoryCacheFactory() {
    }

    /**
     * Initialize factory with default configurations.
     */
    public static synchronized void initialize() {
        if (defaultL1Config == null) {
            defaultL1Config = MemoryCacheConfig.defaultsL1();
            logger.info("Initialized default L1 cache config: {}", defaultL1Config);
        }
        if (defaultL2Config == null) {
            defaultL2Config = MemoryCacheConfig.defaultsL2();
            logger.info("Initialized default L2 cache config: {}", defaultL2Config);
        }
    }

    /**
     * Initialize factory with custom configurations.
     *
     * @param l1Config L1 cache configuration
     * @param l2Config L2 cache configuration
     */
    public static synchronized void initialize(MemoryCacheConfig l1Config, MemoryCacheConfig l2Config) {
        defaultL1Config = l1Config;
        defaultL2Config = l2Config;
        logger.info("Initialized with custom configs - L1: {}, L2: {}", l1Config, l2Config);
    }

    /**
     * Get the default L1 cache.
     *
     * @return Default L1 cache
     */
    @SuppressWarnings("unchecked")
    public static <K, V> MemoryCache<K, V> getL1Cache() {
        if (defaultL1Cache == null) {
            synchronized (MemoryCacheFactory.class) {
                if (defaultL1Cache == null) {
                    MemoryCacheConfig config = (defaultL1Config != null) ?
                        defaultL1Config : MemoryCacheConfig.defaultsL1();
                    defaultL1Cache = createCache(config);
                    logger.info("Created default L1 cache with config: {}", config);
                }
            }
        }
        return (MemoryCache<K, V>) defaultL1Cache;
    }

    /**
     * Get a named L1 cache.
     *
     * @param name Cache name
     * @return Named L1 cache
     */
    @SuppressWarnings("unchecked")
    public static <K, V> MemoryCache<K, V> getL1Cache(String name) {
        return (MemoryCache<K, V>) l1Instances.computeIfAbsent(name, k -> {
            MemoryCacheConfig config = (defaultL1Config != null) ?
                defaultL1Config : MemoryCacheConfig.defaultsL1();
            logger.info("Creating named L1 cache '{}' with config: {}", k, config);
            return createCache(config);
        });
    }

    /**
     * Get the default L2 cache.
     *
     * @return Default L2 cache
     */
    @SuppressWarnings("unchecked")
    public static <K, V> MemoryCache<K, V> getL2Cache() {
        if (defaultL2Cache == null) {
            synchronized (MemoryCacheFactory.class) {
                if (defaultL2Cache == null) {
                    MemoryCacheConfig config = (defaultL2Config != null) ?
                        defaultL2Config : MemoryCacheConfig.defaultsL2();
                    defaultL2Cache = createCache(config);
                    logger.info("Created default L2 cache with config: {}", config);
                }
            }
        }
        return (MemoryCache<K, V>) defaultL2Cache;
    }

    /**
     * Get a named L2 cache.
     *
     * @param name Cache name
     * @return Named L2 cache
     */
    @SuppressWarnings("unchecked")
    public static <K, V> MemoryCache<K, V> getL2Cache(String name) {
        return (MemoryCache<K, V>) l2Instances.computeIfAbsent(name, k -> {
            MemoryCacheConfig config = (defaultL2Config != null) ?
                defaultL2Config : MemoryCacheConfig.defaultsL2();
            logger.info("Creating named L2 cache '{}' with config: {}", k, config);
            return createCache(config);
        });
    }

    /**
     * Create a new cache with custom configuration.
     *
     * @param config Cache configuration
     * @return New cache instance
     */
    public static <K, V> MemoryCache<K, V> create(MemoryCacheConfig config) {
        return createCache(config);
    }

    /**
     * Create a new cache with default L1 configuration.
     *
     * @return New cache instance
     */
    public static <K, V> MemoryCache<K, V> createL1() {
        MemoryCacheConfig config = (defaultL1Config != null) ?
            defaultL1Config : MemoryCacheConfig.defaultsL1();
        return createCache(config);
    }

    /**
     * Create a new cache with default L2 configuration.
     *
     * @return New cache instance
     */
    public static <K, V> MemoryCache<K, V> createL2() {
        MemoryCacheConfig config = (defaultL2Config != null) ?
            defaultL2Config : MemoryCacheConfig.defaultsL2();
        return createCache(config);
    }

    /**
     * Create cache from configuration map.
     *
     * @param configMap Configuration map
     * @return New cache instance
     */
    public static <K, V> MemoryCache<K, V> createFromMap(Map<String, Object> configMap) {
        MemoryCacheConfig config = MemoryCacheConfig.fromMap(configMap);
        return createCache(config);
    }

    /**
     * Clear all named caches.
     */
    public static synchronized void clearAll() {
        l1Instances.values().forEach(cache -> {
            if (cache instanceof MemoryCacheImpl) {
                ((MemoryCacheImpl<?, ?>) cache).shutdown();
            }
        });
        l1Instances.clear();

        l2Instances.values().forEach(cache -> {
            if (cache instanceof MemoryCacheImpl) {
                ((MemoryCacheImpl<?, ?>) cache).shutdown();
            }
        });
        l2Instances.clear();

        if (defaultL1Cache instanceof MemoryCacheImpl) {
            ((MemoryCacheImpl<?, ?>) defaultL1Cache).shutdown();
        }
        defaultL1Cache = null;

        if (defaultL2Cache instanceof MemoryCacheImpl) {
            ((MemoryCacheImpl<?, ?>) defaultL2Cache).shutdown();
        }
        defaultL2Cache = null;

        logger.info("All caches cleared and shut down");
    }

    /**
     * Reload configuration for all caches.
     */
    public static synchronized void reloadConfiguration() {
        // Recreate default caches
        if (defaultL1Config != null) {
            defaultL1Cache = createCache(defaultL1Config);
        }
        if (defaultL2Config != null) {
            defaultL2Cache = createCache(defaultL2Config);
        }

        // Recreate all named caches
        Map<String, MemoryCache<?, ?>> newL1Instances = new ConcurrentHashMap<>();
        l1Instances.keySet().forEach(name -> {
            MemoryCacheConfig config = (defaultL1Config != null) ?
                defaultL1Config : MemoryCacheConfig.defaultsL1();
            newL1Instances.put(name, createCache(config));
        });

        Map<String, MemoryCache<?, ?>> newL2Instances = new ConcurrentHashMap<>();
        l2Instances.keySet().forEach(name -> {
            MemoryCacheConfig config = (defaultL2Config != null) ?
                defaultL2Config : MemoryCacheConfig.defaultsL2();
            newL2Instances.put(name, createCache(config));
        });

        // Shutdown old caches
        l1Instances.values().forEach(cache -> {
            if (cache instanceof MemoryCacheImpl) {
                ((MemoryCacheImpl<?, ?>) cache).shutdown();
            }
        });
        l2Instances.values().forEach(cache -> {
            if (cache instanceof MemoryCacheImpl) {
                ((MemoryCacheImpl<?, ?>) cache).shutdown();
            }
        });

        l1Instances.clear();
        l2Instances.clear();

        l1Instances.putAll(newL1Instances);
        l2Instances.putAll(newL2Instances);

        logger.info("All caches reloaded with new configuration");
    }

    /**
     * Get the default L1 configuration.
     *
     * @return Default L1 configuration
     */
    public static MemoryCacheConfig getDefaultL1Config() {
        return defaultL1Config;
    }

    /**
     * Get the default L2 configuration.
     *
     * @return Default L2 configuration
     */
    public static MemoryCacheConfig getDefaultL2Config() {
        return defaultL2Config;
    }

    /**
     * Set the default L1 configuration.
     *
     * @param config L1 configuration
     */
    public static synchronized void setDefaultL1Config(MemoryCacheConfig config) {
        defaultL1Config = config;
        logger.info("Updated default L1 config: {}", config);
    }

    /**
     * Set the default L2 configuration.
     *
     * @param config L2 configuration
     */
    public static synchronized void setDefaultL2Config(MemoryCacheConfig config) {
        defaultL2Config = config;
        logger.info("Updated default L2 config: {}", config);
    }

    /**
     * Get count of active L1 cache instances.
     *
     * @return Count of L1 instances
     */
    public static int getL1InstanceCount() {
        int count = (defaultL1Cache != null) ? 1 : 0;
        return count + l1Instances.size();
    }

    /**
     * Get count of active L2 cache instances.
     *
     * @return Count of L2 instances
     */
    public static int getL2InstanceCount() {
        int count = (defaultL2Cache != null) ? 1 : 0;
        return count + l2Instances.size();
    }

    // ==================== Private Helper Methods ====================

    /**
     * Create a cache instance from configuration.
     *
     * @param config Cache configuration
     * @return Cache instance
     */
    private static <K, V> MemoryCache<K, V> createCache(MemoryCacheConfig config) {
        if (config == null) {
            config = MemoryCacheConfig.defaultsL1();
        }
        return new MemoryCacheImpl<>(config);
    }
}
