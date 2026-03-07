package tech.yesboss.memory.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Central cache manager for creating and managing cache instances.
 *
 * <p>This manager provides:
 * <ul>
 *   <li>Centralized cache creation and configuration</li>
 *   <li>Named cache registry for easy access</li>
 *   <li>Default configuration management</li>
 *   <li>Cache lifecycle management (initialization, shutdown)</li>
 *   <li>Statistics aggregation across all caches</li>
 * </ul>
 */
public class CacheManager {

    private static final Logger logger = LoggerFactory.getLogger(CacheManager.class);

    private final Map<String, Cache<?, ?>> cacheRegistry;
    private final CacheConfig defaultConfig;
    private final boolean useMultiLevelCache;
    private final CacheConfig l2Config;

    private CacheManager(Builder builder) {
        this.cacheRegistry = new ConcurrentHashMap<>();
        this.defaultConfig = builder.defaultConfig;
        this.useMultiLevelCache = builder.useMultiLevelCache;
        this.l2Config = builder.l2Config;

        logger.info("CacheManager initialized: multiLevel={}, defaultConfig={}",
            useMultiLevelCache, defaultConfig.getMaximumSize());
    }

    /**
     * Get or create a cache with the specified name.
     *
     * @param name the cache name
     * @param <K> the type of keys
     * @param <V> the type of values
     * @return existing or new cache instance
     * @throws CacheException if cache creation fails
     */
    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> getCache(String name) {
        return getCache(name, () -> createCache(name));
    }

    /**
     * Get or create a cache with the specified name and configuration.
     *
     * @param name the cache name
     * @param config the cache configuration
     * @param <K> the type of keys
     * @param <V> the type of values
     * @return existing or new cache instance
     * @throws CacheException if cache creation fails
     */
    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> getCache(String name, CacheConfig config) {
        return getCache(name, () -> createCache(name, config));
    }

    /**
     * Get or create a cache using a custom supplier.
     *
     * @param name the cache name
     * @param supplier the cache supplier
     * @param <K> the type of keys
     * @param <V> the type of values
     * @return existing or new cache instance
     */
    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> getCache(String name, Supplier<Cache<K, V>> supplier) {
        return (Cache<K, V>) cacheRegistry.computeIfAbsent(name, k -> {
            Cache<K, V> cache = supplier.get();
            logger.info("Created cache: {}, type: {}", name, cache.getClass().getSimpleName());
            return cache;
        });
    }

    /**
     * Create a new cache with default configuration.
     *
     * @param name the cache name
     * @param <K> the type of keys
     * @param <V> the type of values
     * @return new cache instance
     * @throws CacheException if creation fails
     */
    private <K, V> Cache<K, V> createCache(String name) {
        return createCache(name, defaultConfig);
    }

    /**
     * Create a new cache with the specified configuration.
     *
     * @param name the cache name
     * @param config the cache configuration
     * @param <K> the type of keys
     * @param <V> the type of values
     * @return new cache instance
     * @throws CacheException if creation fails
     */
    private <K, V> Cache<K, V> createCache(String name, CacheConfig config) {
        try {
            if (useMultiLevelCache) {
                return new MultiLevelCache<>(config, l2Config);
            } else {
                return new InMemoryCache<>(config);
            }
        } catch (Exception e) {
            throw CacheException.initializationError("Failed to create cache: " + name, e);
        }
    }

    /**
     * Check if a cache with the specified name exists.
     *
     * @param name the cache name
     * @return true if the cache exists
     */
    public boolean hasCache(String name) {
        return cacheRegistry.containsKey(name);
    }

    /**
     * Remove a cache from the registry.
     *
     * @param name the cache name
     * @return true if the cache was removed
     */
    public boolean removeCache(String name) {
        Cache<?, ?> cache = cacheRegistry.remove(name);
        if (cache != null) {
            if (cache instanceof InMemoryCache) {
                ((InMemoryCache<?, ?>) cache).shutdown();
            } else if (cache instanceof MultiLevelCache) {
                ((MultiLevelCache<?, ?>) cache).shutdown();
            }
            logger.info("Removed cache: {}", name);
            return true;
        }
        return false;
    }

    /**
     * Get the number of registered caches.
     *
     * @return cache count
     */
    public int getCacheCount() {
        return cacheRegistry.size();
    }

    /**
     * Get all registered cache names.
     *
     * @return array of cache names
     */
    public String[] getCacheNames() {
        return cacheRegistry.keySet().toArray(new String[0]);
    }

    /**
     * Shutdown all caches and clear the registry.
     */
    public void shutdown() {
        logger.info("Shutting down CacheManager with {} caches", cacheRegistry.size());

        cacheRegistry.forEach((name, cache) -> {
            try {
                if (cache instanceof InMemoryCache) {
                    ((InMemoryCache<?, ?>) cache).shutdown();
                } else if (cache instanceof MultiLevelCache) {
                    ((MultiLevelCache<?, ?>) cache).shutdown();
                }
            } catch (Exception e) {
                logger.warn("Failed to shutdown cache: {}", name, e);
            }
        });

        cacheRegistry.clear();
        logger.info("CacheManager shutdown complete");
    }

    /**
     * Create a new builder for cache manager configuration.
     *
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating cache manager instances.
     */
    public static class Builder {
        private CacheConfig defaultConfig = CacheConfig.defaults();
        private boolean useMultiLevelCache = false;
        private CacheConfig l2Config = null;

        /**
         * Set the default cache configuration.
         *
         * @param config default configuration
         * @return this builder
         */
        public Builder defaultConfig(CacheConfig config) {
            this.defaultConfig = config;
            return this;
        }

        /**
         * Enable multi-level caching.
         *
         * @return this builder
         */
        public Builder enableMultiLevelCache() {
            this.useMultiLevelCache = true;
            return this;
        }

        /**
         * Set the L2 cache configuration.
         *
         * @param config L2 cache configuration
         * @return this builder
         */
        public Builder l2Config(CacheConfig config) {
            this.l2Config = config;
            this.useMultiLevelCache = true;
            return this;
        }

        /**
         * Build the cache manager.
         *
         * @return CacheManager instance
         */
        public CacheManager build() {
            return new CacheManager(this);
        }
    }

    /**
     * Get or create the default singleton cache manager.
     *
     * @return default CacheManager instance
     */
    public static CacheManager getDefault() {
        return DefaultCacheManagerHolder.INSTANCE;
    }

    /**
     * Holder for the default singleton cache manager.
     */
    private static class DefaultCacheManagerHolder {
        private static final CacheManager INSTANCE = CacheManager.builder()
            .defaultConfig(CacheConfig.defaults())
            .build();
    }
}
