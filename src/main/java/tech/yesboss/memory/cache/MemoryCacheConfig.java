package tech.yesboss.memory.cache;

import java.util.HashMap;
import java.util.Map;

/**
 * Memory Cache Configuration
 *
 * <p>Configuration settings for memory cache behavior.</p>
 *
 * <p><b>Configuration Properties:</b></p>
 * <ul>
 *   <li>enabled: Enable or disable caching</li>
 *   <li>maxSize: Maximum number of entries in cache</li>
 *   <li>expireAfterWriteMs: Default time to expiration</li>
 *   <li>level: Cache level (L1 or L2)</li>
 *   <li>evictionPolicy: Eviction policy (LRU, LFU, FIFO)</li>
 *   <li>cleanupIntervalMs: Interval for cleanup task</li>
 *   <li>statisticsEnabled: Enable statistics collection</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * MemoryCacheConfig config = MemoryCacheConfig.builder()
 *     .enabled(true)
 *     .maxSize(10000)
 *     .expireAfterWriteMs(3600000)
 *     .build();
 * </pre>
 *
 * @author YesBoss Team
 * @version 1.0
 */
public class MemoryCacheConfig {

    /**
     * Cache eviction policy
     */
    public enum EvictionPolicy {
        LRU,  // Least Recently Used
        LFU,  // Least Frequently Used
        FIFO  // First In First Out
    }

    /**
     * Cache level
     */
    public enum CacheLevel {
        L1,  // In-memory cache (fast, small)
        L2   // Persistent cache (slower, larger)
    }

    // Core settings
    private boolean enabled = true;
    private int maxSize = 10000;
    private long expireAfterWriteMs = 3600000; // 1 hour
    private CacheLevel level = CacheLevel.L1;
    private EvictionPolicy evictionPolicy = EvictionPolicy.LRU;

    // Cleanup settings
    private long cleanupIntervalMs = 300000; // 5 minutes
    private boolean autoCleanup = true;

    // Statistics settings
    private boolean statisticsEnabled = true;

    // Performance settings
    private int concurrencyLevel = 4;
    private boolean recordStats = true;

    /**
     * Private constructor - use builder
     */
    private MemoryCacheConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.maxSize = builder.maxSize;
        this.expireAfterWriteMs = builder.expireAfterWriteMs;
        this.level = builder.level;
        this.evictionPolicy = builder.evictionPolicy;
        this.cleanupIntervalMs = builder.cleanupIntervalMs;
        this.autoCleanup = builder.autoCleanup;
        this.statisticsEnabled = builder.statisticsEnabled;
        this.concurrencyLevel = builder.concurrencyLevel;
        this.recordStats = builder.recordStats;
    }

    /**
     * Create a new builder.
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create default L1 cache configuration.
     *
     * @return Default L1 configuration
     */
    public static MemoryCacheConfig defaultsL1() {
        return builder()
                .level(CacheLevel.L1)
                .maxSize(1000)
                .expireAfterWriteMs(300000) // 5 minutes
                .build();
    }

    /**
     * Create default L2 cache configuration.
     *
     * @return Default L2 configuration
     */
    public static MemoryCacheConfig defaultsL2() {
        return builder()
                .level(CacheLevel.L2)
                .maxSize(50000)
                .expireAfterWriteMs(86400000) // 24 hours
                .build();
    }

    /**
     * Create configuration from map.
     *
     * @param configMap Configuration map
     * @return Configuration instance
     */
    public static MemoryCacheConfig fromMap(Map<String, Object> configMap) {
        Builder builder = builder();

        if (configMap.containsKey("enabled")) {
            builder.enabled(Boolean.parseBoolean(configMap.get("enabled").toString()));
        }
        if (configMap.containsKey("maxSize")) {
            builder.maxSize(Integer.parseInt(configMap.get("maxSize").toString()));
        }
        if (configMap.containsKey("expireAfterWriteMs")) {
            builder.expireAfterWriteMs(Long.parseLong(configMap.get("expireAfterWriteMs").toString()));
        }
        if (configMap.containsKey("level")) {
            String levelStr = configMap.get("level").toString();
            builder.level(CacheLevel.valueOf(levelStr.toUpperCase()));
        }
        if (configMap.containsKey("evictionPolicy")) {
            String policyStr = configMap.get("evictionPolicy").toString();
            builder.evictionPolicy(EvictionPolicy.valueOf(policyStr.toUpperCase()));
        }
        if (configMap.containsKey("cleanupIntervalMs")) {
            builder.cleanupIntervalMs(Long.parseLong(configMap.get("cleanupIntervalMs").toString()));
        }
        if (configMap.containsKey("autoCleanup")) {
            builder.autoCleanup(Boolean.parseBoolean(configMap.get("autoCleanup").toString()));
        }
        if (configMap.containsKey("statisticsEnabled")) {
            builder.statisticsEnabled(Boolean.parseBoolean(configMap.get("statisticsEnabled").toString()));
        }
        if (configMap.containsKey("concurrencyLevel")) {
            builder.concurrencyLevel(Integer.parseInt(configMap.get("concurrencyLevel").toString()));
        }
        if (configMap.containsKey("recordStats")) {
            builder.recordStats(Boolean.parseBoolean(configMap.get("recordStats").toString()));
        }

        return builder.build();
    }

    /**
     * Convert configuration to map.
     *
     * @return Configuration map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("enabled", enabled);
        map.put("maxSize", maxSize);
        map.put("expireAfterWriteMs", expireAfterWriteMs);
        map.put("level", level.toString());
        map.put("evictionPolicy", evictionPolicy.toString());
        map.put("cleanupIntervalMs", cleanupIntervalMs);
        map.put("autoCleanup", autoCleanup);
        map.put("statisticsEnabled", statisticsEnabled);
        map.put("concurrencyLevel", concurrencyLevel);
        map.put("recordStats", recordStats);
        return map;
    }

    // Getters
    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public long getExpireAfterWriteMs() {
        return expireAfterWriteMs;
    }

    public CacheLevel getLevel() {
        return level;
    }

    public EvictionPolicy getEvictionPolicy() {
        return evictionPolicy;
    }

    public long getCleanupIntervalMs() {
        return cleanupIntervalMs;
    }

    public boolean isAutoCleanup() {
        return autoCleanup;
    }

    public boolean isStatisticsEnabled() {
        return statisticsEnabled;
    }

    public int getConcurrencyLevel() {
        return concurrencyLevel;
    }

    public boolean isRecordStats() {
        return recordStats;
    }

    /**
     * Builder for MemoryCacheConfig.
     */
    public static class Builder {
        private boolean enabled = true;
        private int maxSize = 10000;
        private long expireAfterWriteMs = 3600000;
        private CacheLevel level = CacheLevel.L1;
        private EvictionPolicy evictionPolicy = EvictionPolicy.LRU;
        private long cleanupIntervalMs = 300000;
        private boolean autoCleanup = true;
        private boolean statisticsEnabled = true;
        private int concurrencyLevel = 4;
        private boolean recordStats = true;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder maxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public Builder expireAfterWriteMs(long expireAfterWriteMs) {
            this.expireAfterWriteMs = expireAfterWriteMs;
            return this;
        }

        public Builder level(CacheLevel level) {
            this.level = level;
            return this;
        }

        public Builder evictionPolicy(EvictionPolicy evictionPolicy) {
            this.evictionPolicy = evictionPolicy;
            return this;
        }

        public Builder cleanupIntervalMs(long cleanupIntervalMs) {
            this.cleanupIntervalMs = cleanupIntervalMs;
            return this;
        }

        public Builder autoCleanup(boolean autoCleanup) {
            this.autoCleanup = autoCleanup;
            return this;
        }

        public Builder statisticsEnabled(boolean statisticsEnabled) {
            this.statisticsEnabled = statisticsEnabled;
            return this;
        }

        public Builder concurrencyLevel(int concurrencyLevel) {
            this.concurrencyLevel = concurrencyLevel;
            return this;
        }

        public Builder recordStats(boolean recordStats) {
            this.recordStats = recordStats;
            return this;
        }

        public MemoryCacheConfig build() {
            return new MemoryCacheConfig(this);
        }
    }

    @Override
    public String toString() {
        return "MemoryCacheConfig{" +
                "enabled=" + enabled +
                ", maxSize=" + maxSize +
                ", expireAfterWriteMs=" + expireAfterWriteMs +
                ", level=" + level +
                ", evictionPolicy=" + evictionPolicy +
                ", cleanupIntervalMs=" + cleanupIntervalMs +
                ", autoCleanup=" + autoCleanup +
                ", statisticsEnabled=" + statisticsEnabled +
                ", concurrencyLevel=" + concurrencyLevel +
                ", recordStats=" + recordStats +
                '}';
    }
}
