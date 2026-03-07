package tech.yesboss.memory.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Memory Cache Implementation
 *
 * <p>Thread-safe multi-level cache with LRU eviction and time-based expiration.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Concurrent access using ReadWriteLock</li>
 *   <li>LRU eviction policy</li>
 *   <li>Time-based expiration</li>
 *   <li>Size-based eviction</li>
 *   <li>Cache statistics</li>
 *   <li>Bulk operations</li>
 *   <li>Conditional updates</li>
 *   <li>Automatic cleanup</li>
 * </ul>
 *
 * @param <K> Key type
 * @param <V> Value type
 * @author YesBoss Team
 * @version 1.0
 */
public class MemoryCacheImpl<K, V> implements MemoryCache<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(MemoryCacheImpl.class);

    private final MemoryCacheConfig config;
    private final Map<K, CacheEntry> cache;
    private final ReadWriteLock lock;
    private final ScheduledExecutorService cleanupExecutor;
    private final CacheStatistics stats;

    /**
     * Internal cache entry with value and metadata
     */
    private class CacheEntry {
        final V value;
        final long createdAt;
        volatile long lastAccessed;
        volatile long accessCount;
        final long expireAfterMs;

        CacheEntry(V value, long expireAfterMs) {
            this.value = value;
            this.createdAt = System.currentTimeMillis();
            this.lastAccessed = this.createdAt;
            this.accessCount = 0;
            this.expireAfterMs = expireAfterMs;
        }

        boolean isExpired() {
            if (expireAfterMs <= 0) {
                return false; // No expiration
            }
            return System.currentTimeMillis() - createdAt > expireAfterMs;
        }

        void recordAccess() {
            this.lastAccessed = System.currentTimeMillis();
            this.accessCount++;
        }

        long getAge() {
            return System.currentTimeMillis() - createdAt;
        }

        long getIdleTime() {
            return System.currentTimeMillis() - lastAccessed;
        }
    }

    /**
     * Cache statistics
     */
    private class CacheStatistics {
        private final AtomicLong hitCount = new AtomicLong(0);
        private final AtomicLong missCount = new AtomicLong(0);
        private final AtomicLong evictionCount = new AtomicLong(0);
        private final AtomicLong expirationCount = new AtomicLong(0);
        private final AtomicLong loadSuccessCount = new AtomicLong(0);
        private final AtomicLong loadFailureCount = new AtomicLong(0);

        void recordHit() {
            if (config.isRecordStats()) {
                hitCount.incrementAndGet();
            }
        }

        void recordMiss() {
            if (config.isRecordStats()) {
                missCount.incrementAndGet();
            }
        }

        void recordEviction() {
            if (config.isRecordStats()) {
                evictionCount.incrementAndGet();
            }
        }

        void recordExpiration() {
            if (config.isRecordStats()) {
                expirationCount.incrementAndGet();
            }
        }

        void recordLoadSuccess() {
            if (config.isRecordStats()) {
                loadSuccessCount.incrementAndGet();
            }
        }

        void recordLoadFailure() {
            if (config.isRecordStats()) {
                loadFailureCount.incrementAndGet();
            }
        }

        void reset() {
            hitCount.set(0);
            missCount.set(0);
            evictionCount.set(0);
            expirationCount.set(0);
            loadSuccessCount.set(0);
            loadFailureCount.set(0);
        }

        CacheStats toSnapshot() {
            return new CacheStats(
                hitCount.get(),
                missCount.get(),
                evictionCount.get(),
                expirationCount.get(),
                cache.size(),
                config.getMaxSize()
            );
        }
    }

    /**
     * Create cache with configuration.
     *
     * @param config Cache configuration
     */
    public MemoryCacheImpl(MemoryCacheConfig config) {
        this.config = config;
        this.lock = new ReentrantReadWriteLock();
        this.stats = new CacheStatistics();

        // Create concurrent cache with LRU eviction
        this.cache = new LinkedHashMap<K, CacheEntry>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry> eldest) {
                boolean removed = size() > config.getMaxSize();
                if (removed) {
                    stats.recordEviction();
                }
                return removed;
            }
        };

        // Start cleanup executor if auto cleanup is enabled
        if (config.isAutoCleanup() && config.isEnabled()) {
            this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "memory-cache-cleanup");
                thread.setDaemon(true);
                return thread;
            });
            this.cleanupExecutor.scheduleAtFixedRate(
                this::cleanupExpired,
                config.getCleanupIntervalMs(),
                config.getCleanupIntervalMs(),
                TimeUnit.MILLISECONDS
            );
        } else {
            this.cleanupExecutor = null;
        }

        logger.info("MemoryCache initialized: {}", config);
    }

    /**
     * Create cache with default L1 configuration.
     */
    public MemoryCacheImpl() {
        this(MemoryCacheConfig.defaultsL1());
    }

    // ==================== Basic Operations ====================

    @Override
    public V get(K key) {
        if (!config.isEnabled() || key == null) {
            return null;
        }

        lock.readLock().lock();
        try {
            CacheEntry entry = cache.get(key);
            if (entry == null) {
                stats.recordMiss();
                return null;
            }

            // Check expiration
            if (entry.isExpired()) {
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    // Double-check after acquiring write lock
                    entry = cache.get(key);
                    if (entry != null && entry.isExpired()) {
                        cache.remove(key);
                        stats.recordExpiration();
                        stats.recordMiss();
                        return null;
                    }
                } finally {
                    // Downgrade to read lock
                    lock.readLock().lock();
                    lock.writeLock().unlock();
                }
            }

            entry.recordAccess();
            stats.recordHit();
            return entry.value;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void put(K key, V value) {
        put(key, value, config.getExpireAfterWriteMs());
    }

    @Override
    public void put(K key, V value, long expireAfterMs) {
        if (!config.isEnabled() || key == null || value == null) {
            return;
        }

        lock.writeLock().lock();
        try {
            cache.put(key, new CacheEntry(value, expireAfterMs));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public V getOrCompute(K key, CacheLoader<K, V> loader) {
        if (!config.isEnabled() || key == null) {
            try {
                return loader.load(key);
            } catch (Exception e) {
                logger.error("Error loading value for key: {}", key, e);
                return null;
            }
        }

        V value = get(key);
        if (value != null) {
            return value;
        }

        lock.writeLock().lock();
        try {
            // Double-check pattern
            value = get(key);
            if (value != null) {
                return value;
            }

            try {
                value = loader.load(key);
                if (value != null) {
                    put(key, value);
                    stats.recordLoadSuccess();
                } else {
                    stats.recordLoadFailure();
                }
            } catch (Exception e) {
                logger.error("Error loading value for key: {}", key, e);
                stats.recordLoadFailure();
            }
            return value;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean containsKey(K key) {
        if (!config.isEnabled() || key == null) {
            return false;
        }

        lock.readLock().lock();
        try {
            CacheEntry entry = cache.get(key);
            if (entry == null) {
                return false;
            }
            return !entry.isExpired();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public V remove(K key) {
        if (!config.isEnabled() || key == null) {
            return null;
        }

        lock.writeLock().lock();
        try {
            CacheEntry entry = cache.remove(key);
            return entry != null ? entry.value : null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void clear() {
        if (!config.isEnabled()) {
            return;
        }

        lock.writeLock().lock();
        try {
            cache.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ==================== Bulk Operations ====================

    @Override
    public Map<K, V> getAll(Set<K> keys) {
        if (!config.isEnabled() || keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<K, V> result = new HashMap<>();
        lock.readLock().lock();
        try {
            for (K key : keys) {
                if (key == null) continue;
                CacheEntry entry = cache.get(key);
                if (entry != null && !entry.isExpired()) {
                    entry.recordAccess();
                    result.put(key, entry.value);
                    stats.recordHit();
                } else {
                    stats.recordMiss();
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        return result;
    }

    @Override
    public void putAll(Map<K, V> entries) {
        putAll(entries, config.getExpireAfterWriteMs());
    }

    @Override
    public void putAll(Map<K, V> entries, long expireAfterMs) {
        if (!config.isEnabled() || entries == null || entries.isEmpty()) {
            return;
        }

        lock.writeLock().lock();
        try {
            for (Map.Entry<K, V> entry : entries.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    cache.put(entry.getKey(), new CacheEntry(entry.getValue(), expireAfterMs));
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int removeAll(Set<K> keys) {
        if (!config.isEnabled() || keys == null || keys.isEmpty()) {
            return 0;
        }

        lock.writeLock().lock();
        try {
            int count = 0;
            for (K key : keys) {
                if (key != null && cache.remove(key) != null) {
                    count++;
                }
            }
            return count;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ==================== Conditional Operations ====================

    @Override
    public boolean putIfAbsent(K key, V value) {
        if (!config.isEnabled() || key == null || value == null) {
            return false;
        }

        lock.writeLock().lock();
        try {
            if (!containsKey(key)) {
                put(key, value);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        if (!config.isEnabled() || key == null || oldValue == null || newValue == null) {
            return false;
        }

        lock.writeLock().lock();
        try {
            CacheEntry entry = cache.get(key);
            if (entry != null && !entry.isExpired() &&
                Objects.equals(entry.value, oldValue)) {
                put(key, newValue);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public V replace(K key, V value) {
        if (!config.isEnabled() || key == null || value == null) {
            return null;
        }

        lock.writeLock().lock();
        try {
            CacheEntry entry = cache.get(key);
            if (entry != null && !entry.isExpired()) {
                V oldValue = entry.value;
                put(key, value);
                return oldValue;
            }
            return null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ==================== Cache Statistics ====================

    @Override
    public CacheStats getStats() {
        return stats.toSnapshot();
    }

    @Override
    public void resetStats() {
        stats.reset();
    }

    // ==================== Cache Management ====================

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return cache.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        lock.readLock().lock();
        try {
            return cache.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Set<K> keys() {
        lock.readLock().lock();
        try {
            return new HashSet<>(cache.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public MemoryCacheConfig getConfig() {
        return config;
    }

    @Override
    public int cleanupExpired() {
        if (!config.isEnabled()) {
            return 0;
        }

        lock.writeLock().lock();
        try {
            int removed = 0;
            Iterator<Map.Entry<K, CacheEntry>> iterator = cache.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<K, CacheEntry> entry = iterator.next();
                if (entry.getValue().isExpired()) {
                    iterator.remove();
                    removed++;
                    stats.recordExpiration();
                }
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int warmUp(Map<K, V> entries) {
        if (!config.isEnabled() || entries == null || entries.isEmpty()) {
            return 0;
        }

        lock.writeLock().lock();
        try {
            int count = 0;
            for (Map.Entry<K, V> entry : entries.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    if (!cache.containsKey(entry.getKey())) {
                        cache.put(entry.getKey(), new CacheEntry(entry.getValue(),
                            config.getExpireAfterWriteMs()));
                        count++;
                    }
                }
            }
            logger.info("Cache warmed up with {} entries", count);
            return count;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int invalidateAll(CachePredicate<K, V> predicate) {
        if (!config.isEnabled() || predicate == null) {
            return 0;
        }

        lock.writeLock().lock();
        try {
            int removed = 0;
            Iterator<Map.Entry<K, CacheEntry>> iterator = cache.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<K, CacheEntry> entry = iterator.next();
                if (predicate.test(entry.getKey(), entry.getValue().value)) {
                    iterator.remove();
                    removed++;
                }
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    /**
     * Shutdown cache and cleanup resources.
     */
    public void shutdown() {
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        clear();
        logger.info("MemoryCache shutdown complete");
    }
}
