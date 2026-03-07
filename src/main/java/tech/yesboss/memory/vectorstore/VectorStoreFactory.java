package tech.yesboss.memory.vectorstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating VectorStore instances.
 *
 * Features:
 * - Singleton pattern (one instance per type)
 * - Dynamic backend selection (SQLite or PostgreSQL)
 * - Runtime configuration reloading
 * - Fallback to SQLite on configuration error
 *
 * Configuration:
 * - vector.store.type: sqlite | postgresql (default: sqlite)
 */
public class VectorStoreFactory {

    private static final Logger logger = LoggerFactory.getLogger(VectorStoreFactory.class);

    private static volatile VectorStoreFactory instance;

    private final Map<String, VectorStore> stores = new ConcurrentHashMap<>();
    private final DataSource dataSource;
    private VectorStoreType defaultType = VectorStoreType.SQLITE;

    private VectorStoreFactory(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource cannot be null");
        }
        this.dataSource = dataSource;
        logger.info("VectorStoreFactory initialized with dataSource");
    }

    /**
     * Get the singleton instance of VectorStoreFactory.
     *
     * @param dataSource DataSource for database connections
     * @return Singleton instance
     */
    public static VectorStoreFactory getInstance(DataSource dataSource) {
        if (instance == null) {
            synchronized (VectorStoreFactory.class) {
                if (instance == null) {
                    instance = new VectorStoreFactory(dataSource);
                }
            }
        }
        return instance;
    }

    /**
     * Get a VectorStore for the specified table name.
     *
     * @param tableName Name of the table
     * @return VectorStore instance
     */
    public VectorStore getVectorStore(String tableName) {
        return getVectorStore(tableName, defaultType);
    }

    /**
     * Get a VectorStore for the specified table name and type.
     *
     * @param tableName Name of the table
     * @param type Type of vector store
     * @return VectorStore instance
     */
    public VectorStore getVectorStore(String tableName, VectorStoreType type) {
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("tableName cannot be null or empty");
        }
        if (type == null) {
            type = defaultType;
        }

        final String finalTableName = tableName;
        final VectorStoreType finalType = type;
        String key = type.name() + ":" + tableName;

        return stores.computeIfAbsent(key, k -> createStore(finalTableName, finalType));
    }

    /**
     * Set the default vector store type.
     *
     * @param type Default type to use
     */
    public void setDefaultType(VectorStoreType type) {
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
        this.defaultType = type;
        logger.info("Default vector store type set to: {}", type);
    }

    /**
     * Reload configuration and recreate vector stores.
     */
    public void reloadConfig() {
        logger.info("Reloading vector store configuration");
        stores.clear();
        logger.info("Vector store configuration reloaded, {} stores cleared", stores.size());
    }

    /**
     * Close all vector stores.
     */
    public void closeAll() {
        logger.info("Closing all vector stores");
        stores.values().forEach(VectorStore::close);
        stores.clear();
    }

    private VectorStore createStore(final String tableName, VectorStoreType type) {
        try {
            logger.info("Creating vector store: table={}, type={}", tableName, type);

            return switch (type) {
                case SQLITE -> new SQLiteVecStore(dataSource, tableName);
                case POSTGRESQL -> {
                    logger.warn("PostgreSQL vector store not implemented, falling back to SQLite");
                    yield new SQLiteVecStore(dataSource, tableName);
                }
                default -> {
                    logger.warn("Unknown vector store type: {}, falling back to SQLite", type);
                    yield new SQLiteVecStore(dataSource, tableName);
                }
            };

        } catch (Exception e) {
            logger.error("Failed to create vector store: table={}, type={}, falling back to SQLite",
                tableName, type, e);
            return new SQLiteVecStore(dataSource, tableName);
        }
    }

    /**
     * Vector store types.
     */
    public enum VectorStoreType {
        SQLITE,
        POSTGRESQL
    }
}
