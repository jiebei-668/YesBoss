package tech.yesboss.memory.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * In-memory cache implementation with thread-safe operations and statistics.
 *
 * <p>This implementation provides:
 * <ul>
 *   <li>Thread-safe operations using ReadWriteLock</li>
 *   <li>Time-based expiration (write and access based)</li>
 *   <li>LRU eviction policy when size limit is reached</li>
 *   <li>Cache statistics tracking</li>
 *   <li>Async operations using CompletableFuture</li>
 *   <li>Removal listener support</li>
 * </ul>
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of mapped values
 */
public class InMemoryCache<K, V> implements Cache<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryCache.class);

    private final ConcurrentHashMap<K, CacheEntry<V>> cache;
    private final ConcurrentLinkedQueue<K> accessQueue;
    private final CacheConfig config;
    private final CacheStatistics statistics;
    private final ScheduledExecutorService maintenanceExecutor;
    private final ExecutorService asyncExecutor;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Internal cache entry with expiration tracking.
     *
     * @param <V> the type of value
     */
    private static class CacheEntry<V> {
        private final V value;
        private final Instant writeTime;
        private volatile Instant accessTime;
        private volatile long accessCount;

        CacheEntry(V value, Duration expireAfterWrite, Duration expireAfterAccess) {
            this.value = value;
            this.writeTime = Instant.now();
            this.accessTime = Instant.now();
            this.accessCount = 0;
        }

        V getValue() {
            return value;
        }

        void recordAccess() {
            this.accessTime = Instant.now();
            this.accessCount++;
        }

        boolean isExpired(Duration expireAfterWrite, Duration expireAfterAccess) {
            Instant now = Instant.now();
            if (expireAfterWrite != null) {
                if (writeTime.plus(expireAfterWrite).isBefore(now)) {
                    return true;
                }
            }
            if (expireAfterAccess != null) {
                if (accessTime.plus(expireAfterAccess).isBefore(now)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Create a new in-memory cache with the specified configuration.
     *
     * @param config cache configuration
     * @throws CacheException if initialization fails
     */
    public InMemoryCache(CacheConfig config) {
        if (config == null) {
            throw CacheException.configurationError("Cache configuration cannot be null");
        }

        this.config = config;
        this.cache = new ConcurrentHashMap<>(16, 0.75f, config.getConcurrencyLevel());
        this.accessQueue = new ConcurrentLinkedQueue<>();
        this.statistics = config.isRecordStats() ? new CacheStatistics() : null;
        this.maintenanceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "cache-maintenance");
            thread.setDaemon(true);
            return thread;
        });
        this.asyncExecutor = Executors.newFixedThreadPool(
            Math.max(2, config.getConcurrencyLevel()),
            r -> {
                Thread thread = new Thread(r, "cache-async");
                thread.setDaemon(true);
                return thread;
            }
        );

        // Start maintenance task
        startMaintenanceTask();

        logger.info("InMemoryCache initialized with config: maxSize={}, expireAfterWrite={}, expireAfterAccess={}",
            config.getMaximumSize(), config.getExpireAfterWrite(), config.getExpireAfterAccess());
    }

    /**
     * Start the maintenance task to clean up expired entries.
     */
    private void startMaintenanceTask() {
        Duration checkInterval = Duration.ofMinutes(5);
        maintenanceExecutor.scheduleAtFixedRate(
            this::performMaintenance,
            checkInterval.toMinutes(),
            checkInterval.toMinutes(),
            TimeUnit.MINUTES
        );
    }

    /**
     * Perform maintenance: clean expired entries and enforce size limit.
     */
    private void performMaintenance() {
        try {
            cleanExpiredEntries();
            enforceSizeLimit();
        } catch (Exception e) {
            logger.warn("Maintenance task failed", e);
        }
    }

    /**
     * Remove expired entries from the cache.
     */
    private void cleanExpiredEntries() {
        if (config.getExpireAfterWrite() == null && config.getExpireAfterAccess() == null) {
            return;
        }

        List<K> expiredKeys = cache.entrySet().stream()
            .filter(entry -> entry.getValue().isExpired(config.getExpireAfterWrite(), config.getExpireAfterAccess()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        for (K key : expiredKeys) {
            evictInternal(key, CacheConfig.RemovalListener.RemovalCause.EXPIRED);
        }

        if (!expiredKeys.isEmpty()) {
            logger.debug("Cleaned {} expired entries", expiredKeys.size());
        }
    }

    /**
     * Enforce the maximum size limit using LRU eviction.
     */
    private void enforceSizeLimit() {
        while (cache.size() > config.getMaximumSize()) {
            K lruKey = accessQueue.poll();
            if (lruKey == null) {
                // Fallback: remove any entry
                K firstKey = cache.keys().nextElement();
                if (firstKey != null) {
                    evictInternal(firstKey, CacheConfig.RemovalListener.RemovalCause.SIZE);
                }
                break;
            }
            evictInternal(lruKey, CacheConfig.RemovalListener.RemovalCause.SIZE);
        }
    }

    @Override
    public Optional<V> get(K key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        try {
            lock.readLock().lock();
            CacheEntry<V> entry = cache.get(key);

            if (entry == null) {
                if (statistics != null) {
                    statistics.recordMiss();
                }
                return Optional.empty();
            }

            // Check expiration
            if (entry.isExpired(config.getExpireAfterWrite(), config.getExpireAfterAccess())) {
                evictInternal(key, CacheConfig.RemovalListener.RemovalCause.EXPIRED);
                if (statistics != null) {
                    statistics.recordMiss();
                }
                return Optional.empty();
            }

            // Record access
            entry.recordAccess();
            accessQueue.remove(key);
            accessQueue.offer(key);

            if (statistics != null) {
                statistics.recordHit();
            }

            return Optional.of(entry.getValue());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public CompletableFuture<Optional<V>> getAsync(K key) {
        return CompletableFuture.supplyAsync(() -> get(key), asyncExecutor);
    }

    @Override
    public void put(K key, V value) {
        put(key, value, config.getExpireAfterWrite());
    }

    @Override
    public CompletableFuture<Void> putAsync(K key, V value) {
        return CompletableFuture.runAsync(() -> put(key, value), asyncExecutor);
    }

    @Override
    public void put(K key, V value, Duration expiration) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }

        try {
            lock.writeLock().lock();
            boolean replaced = cache.containsKey(key);
            CacheEntry<V> entry = new CacheEntry<>(value, expiration, config.getExpireAfterAccess());
            cache.put(key, entry);

            // Update access queue
            accessQueue.remove(key);
            accessQueue.offer(key);

            // Enforce size limit
            enforceSizeLimit();

            if (replaced && config.getRemovalListener() != null) {
                config.getRemovalListener().onRemoval(key, value, CacheConfig.RemovalListener.RemovalCause.REPLACED);
            }

            logger.debug("Put entry in cache: key={}, replaced={}", key, replaced);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public V getOrCompute(K key, CacheLoader<K, V> loader) {
        Optional<V> cached = get(key);
        if (cached.isPresent()) {
            return cached.get();
        }

        long startTime = System.nanoTime();
        try {
            V value = loader.load(key);
            long loadTime = System.nanoTime() - startTime;

            if (statistics != null) {
                statistics.recordLoadSuccess(loadTime);
            }

            put(key, value);
            return value;
        } catch (Exception e) {
            long loadTime = System.nanoTime() - startTime;

            if (statistics != null) {
                statistics.recordLoadFailure(loadTime);
            }

            throw new CacheException("Failed to load value for key: " + key, e, CacheException.ERROR_BACKEND);
        }
    }

    @Override
    public CompletableFuture<V> getOrComputeAsync(K key, CacheLoader<K, V> loader) {
        return CompletableFuture.supplyAsync(() -> getOrCompute(key, loader), asyncExecutor);
    }

    @Override
    public void evict(K key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        try {
            lock.writeLock().lock();
            evictInternal(key, CacheConfig.RemovalListener.RemovalCause.EXPLICIT);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public CompletableFuture<Void> evictAsync(K key) {
        return CompletableFuture.runAsync(() -> evict(key), asyncExecutor);
    }

    /**
     * Internal eviction method without locking.
     */
    private void evictInternal(K key, CacheConfig.RemovalListener.RemovalCause cause) {
        CacheEntry<V> entry = cache.remove(key);
        if (entry != null) {
            accessQueue.remove(key);
            if (statistics != null) {
                statistics.recordEviction();
            }
            if (config.getRemovalListener() != null) {
                config.getRemovalListener().onRemoval(key, entry.getValue(), cause);
            }
        }
    }

    @Override
    public void clear() {
        try {
            lock.writeLock().lock();
            cache.clear();
            accessQueue.clear();
            logger.debug("Cache cleared");
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public CompletableFuture<Void> clearAsync() {
        return CompletableFuture.runAsync(this::clear, asyncExecutor);
    }

    @Override
    public long size() {
        return cache.size();
    }

    @Override
    public CacheStatistics getStatistics() {
        if (statistics == null) {
            throw new IllegalStateException("Statistics recording is not enabled for this cache");
        }
        return statistics;
    }

    @Override
    public void resetStatistics() {
        if (statistics == null) {
            throw new IllegalStateException("Statistics recording is not enabled for this cache");
        }
        statistics.reset();
    }

    @Override
    public boolean containsKey(K key) {
        if (key == null) {
            return false;
        }

        CacheEntry<V> entry = cache.get(key);
        if (entry == null) {
            return false;
        }

        if (entry.isExpired(config.getExpireAfterWrite(), config.getExpireAfterAccess())) {
            return false;
        }

        return true;
    }

    @Override
    public Iterable<K> keys() {
        return new ArrayList<>(cache.keySet());
    }

    @Override
    public Iterable<V> values() {
        return cache.values().stream()
            .map(CacheEntry::getValue)
            .collect(Collectors.toList());
    }

    /**
     * Shutdown the cache and release resources.
     */
    public void shutdown() {
        maintenanceExecutor.shutdown();
        asyncExecutor.shutdown();
        try {
            if (!maintenanceExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                maintenanceExecutor.shutdownNow();
            }
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logger.info("InMemoryCache shutdown complete");
    }
}
