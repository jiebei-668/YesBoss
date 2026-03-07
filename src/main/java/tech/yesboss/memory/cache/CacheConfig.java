package tech.yesboss.memory.cache;

import java.time.Duration;

/**
 * Configuration for cache instances.
 *
 * <p>This class uses the Builder pattern to create cache configurations with:
 * <ul>
 *   <li>Maximum size</li>
 *   <li>Expiration times (write, access, custom)</li>
 *   <li>Statistics collection</li>
 *   <li>Removal listeners</li>
 *   <li>Concurrency level</li>
 * </ul>
 */
public class CacheConfig {

    private final long maximumSize;
    private final Duration expireAfterWrite;
    private final Duration expireAfterAccess;
    private final Duration refreshAfterWrite;
    private final boolean recordStats;
    private final RemovalListener removalListener;
    private final int concurrencyLevel;

    private CacheConfig(Builder builder) {
        this.maximumSize = builder.maximumSize;
        this.expireAfterWrite = builder.expireAfterWrite;
        this.expireAfterAccess = builder.expireAfterAccess;
        this.refreshAfterWrite = builder.refreshAfterWrite;
        this.recordStats = builder.recordStats;
        this.removalListener = builder.removalListener;
        this.concurrencyLevel = builder.concurrencyLevel;
    }

    /**
     * Get the maximum size of the cache.
     *
     * @return maximum number of entries
     */
    public long getMaximumSize() {
        return maximumSize;
    }

    /**
     * Get the expiration time after write.
     *
     * @return duration after which entries expire after being written
     */
    public Duration getExpireAfterWrite() {
        return expireAfterWrite;
    }

    /**
     * Get the expiration time after access.
     *
     * @return duration after which entries expire after being accessed
     */
    public Duration getExpireAfterAccess() {
        return expireAfterAccess;
    }

    /**
     * Get the refresh time after write.
     *
     * @return duration after which entries are refreshed after being written
     */
    public Duration getRefreshAfterWrite() {
        return refreshAfterWrite;
    }

    /**
     * Check if statistics should be recorded.
     *
     * @return true if statistics recording is enabled
     */
    public boolean isRecordStats() {
        return recordStats;
    }

    /**
     * Get the removal listener.
     *
     * @return removal listener, or null if not set
     */
    public RemovalListener getRemovalListener() {
        return removalListener;
    }

    /**
     * Get the concurrency level.
     *
     * @return concurrency level for the cache
     */
    public int getConcurrencyLevel() {
        return concurrencyLevel;
    }

    /**
     * Create a new builder for cache configuration.
     *
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a default configuration suitable for most use cases.
     *
     * @return default CacheConfig
     */
    public static CacheConfig defaults() {
        return builder()
            .maximumSize(10000)
            .expireAfterWrite(Duration.ofHours(1))
            .recordStats(true)
            .build();
    }

    /**
     * Builder for creating cache configurations.
     */
    public static class Builder {
        private long maximumSize = 10000;
        private Duration expireAfterWrite = null;
        private Duration expireAfterAccess = null;
        private Duration refreshAfterWrite = null;
        private boolean recordStats = false;
        private RemovalListener removalListener = null;
        private int concurrencyLevel = 4;

        /**
         * Set the maximum size of the cache.
         *
         * @param maximumSize maximum number of entries
         * @return this builder
         */
        public Builder maximumSize(long maximumSize) {
            if (maximumSize <= 0) {
                throw new IllegalArgumentException("Maximum size must be positive");
            }
            this.maximumSize = maximumSize;
            return this;
        }

        /**
         * Set the expiration time after write.
         *
         * @param duration duration after which entries expire
         * @return this builder
         */
        public Builder expireAfterWrite(Duration duration) {
            if (duration == null || duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException("Duration must be positive");
            }
            this.expireAfterWrite = duration;
            return this;
        }

        /**
         * Set the expiration time after access.
         *
         * @param duration duration after which entries expire after access
         * @return this builder
         */
        public Builder expireAfterAccess(Duration duration) {
            if (duration == null || duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException("Duration must be positive");
            }
            this.expireAfterAccess = duration;
            return this;
        }

        /**
         * Set the refresh time after write.
         *
         * @param duration duration after which entries are refreshed
         * @return this builder
         */
        public Builder refreshAfterWrite(Duration duration) {
            if (duration == null || duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException("Duration must be positive");
            }
            this.refreshAfterWrite = duration;
            return this;
        }

        /**
         * Enable or disable statistics recording.
         *
         * @param recordStats true to enable statistics
         * @return this builder
         */
        public Builder recordStats(boolean recordStats) {
            this.recordStats = recordStats;
            return this;
        }

        /**
         * Set the removal listener.
         *
         * @param listener listener to notify when entries are removed
         * @return this builder
         */
        public Builder removalListener(RemovalListener listener) {
            this.removalListener = listener;
            return this;
        }

        /**
         * Set the concurrency level.
         *
         * @param concurrencyLevel number of threads that can concurrently write
         * @return this builder
         */
        public Builder concurrencyLevel(int concurrencyLevel) {
            if (concurrencyLevel <= 0) {
                throw new IllegalArgumentException("Concurrency level must be positive");
            }
            this.concurrencyLevel = concurrencyLevel;
            return this;
        }

        /**
         * Build the cache configuration.
         *
         * @return CacheConfig instance
         * @throws IllegalArgumentException if configuration is invalid
         */
        public CacheConfig build() {
            // Validate configuration
            if (expireAfterWrite == null && expireAfterAccess == null && refreshAfterWrite == null) {
                // At least one expiration policy should be set for practical use
                // But we allow this for edge cases
            }
            return new CacheConfig(this);
        }
    }

    /**
     * Listener for cache entry removal events.
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     */
    @FunctionalInterface
    public interface RemovalListener<K, V> {
        /**
         * Called when an entry is removed from the cache.
         *
         * @param key the key that was removed
         * @param value the value that was removed
         * @param cause the cause for removal
         */
        void onRemoval(K key, V value, RemovalCause cause);

        /**
         * The cause for an entry's removal.
         */
        enum RemovalCause {
            /** The entry was manually removed by the user. */
            EXPLICIT,
            /** The entry was replaced by the user. */
            REPLACED,
            /** The entry was evicted due to size constraints. */
            SIZE,
            /** The entry was evicted due to expiration. */
            EXPIRED,
            /** The entry was evicted due to a collector. */
            COLLECTED
        }
    }
}
