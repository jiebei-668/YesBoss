package tech.yesboss.memory.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.memory.vectorstore.VectorStore;
import tech.yesboss.memory.vectorstore.VectorStoreException;
import tech.yesboss.memory.vectorstore.VectorStoreFactory;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * Factory for creating vector store instances based on configuration
 *
 * Supports:
 * - Dual backend switching
 * - Configuration-based instantiation
 * - Connection pooling
 * - Error handling
 */
public class VectorStoreConfigFactory {

    private static final Logger logger = LoggerFactory.getLogger(VectorStoreConfigFactory.class);

    private final MemoryConfig config;
    private VectorStore currentVectorStore;
    private MemoryConfig.BackendType currentBackend;

    /**
     * Constructor
     */
    public VectorStoreConfigFactory() {
        this.config = MemoryConfig.getInstance();
        this.currentVectorStore = null;
        this.currentBackend = null;

        // Listen for backend changes
        config.addListener(MemoryConfig.BACKEND_TYPE, (key, oldValue, newValue) -> {
            logger.info("Backend type changed in config, vector store needs reinitialization");
        });
    }

    /**
     * Get or create vector store based on current configuration
     *
     * @param dataSource Data source (for PostgreSQL backend)
     * @return Vector store instance
     * @throws VectorStoreException if creation fails
     */
    public VectorStore getVectorStore(DataSource dataSource) throws VectorStoreException {
        MemoryConfig.BackendType backendType = config.getBackendType();

        // If backend hasn't changed, return existing instance
        if (currentVectorStore != null && currentBackend == backendType) {
            return currentVectorStore;
        }

        // Close existing vector store if present
        if (currentVectorStore != null) {
            logger.info("Closing existing vector store (backend: {})", currentBackend);
            try {
                currentVectorStore.close();
            } catch (Exception e) {
                logger.warn("Error closing vector store", e);
            }
            currentVectorStore = null;
        }

        // Create new vector store based on backend type
        logger.info("Creating vector store for backend: {}", backendType);
        currentVectorStore = createVectorStore(backendType, dataSource);
        currentBackend = backendType;

        return currentVectorStore;
    }

    /**
     * Create vector store for specific backend type
     *
     * @param backendType Backend type
     * @param dataSource Data source
     * @return Vector store instance
     * @throws VectorStoreException if creation fails
     */
    private VectorStore createVectorStore(MemoryConfig.BackendType backendType, DataSource dataSource)
            throws VectorStoreException {

        switch (backendType) {
            case SQLITE_VEC:
                return createSQLiteVecStore();

            case POSTGRESQL_PGVECTOR:
                return createPostgreSQLVectorStore(dataSource);

            default:
                throw new VectorStoreException("Unsupported backend type: " + backendType);
        }
    }

    /**
     * Create SQLite vector store
     *
     * @return SQLite vector store
     * @throws VectorStoreException if creation fails
     */
    private VectorStore createSQLiteVecStore() throws VectorStoreException {
        String sqlitePath = config.get(MemoryConfig.SQLITE_PATH);
        logger.info("Creating SQLiteVecStore with path: {}", sqlitePath);

        try {
            return VectorStoreFactory.createSQLiteVecStore(sqlitePath);
        } catch (Exception e) {
            String message = "Failed to create SQLiteVecStore: " + e.getMessage();
            logger.error(message, e);
            throw new VectorStoreException(message, e);
        }
    }

    /**
     * Create PostgreSQL vector store
     *
     * @param dataSource Data source
     * @return PostgreSQL vector store
     * @throws VectorStoreException if creation fails
     */
    private VectorStore createPostgreSQLVectorStore(DataSource dataSource) throws VectorStoreException {
        String url = config.getPostgreSQLUrl();
        logger.info("Creating PostgreSQLVectorStore with URL: {}", url);

        try {
            return VectorStoreFactory.createPostgreSQLVectorStore(dataSource);
        } catch (Exception e) {
            String message = "Failed to create PostgreSQLVectorStore: " + e.getMessage();
            logger.error(message, e);
            throw new VectorStoreException(message, e);
        }
    }

    /**
     * Switch to a different backend
     *
     * @param newBackend New backend type
     * @param dataSource Data source
     * @return New vector store instance
     * @throws VectorStoreException if switch fails
     */
    public VectorStore switchBackend(MemoryConfig.BackendType newBackend, DataSource dataSource)
            throws VectorStoreException {

        MemoryConfig.BackendType currentBackendType = config.getBackendType();

        if (currentBackendType == newBackend) {
            logger.info("Already using {} backend", newBackend);
            return currentVectorStore;
        }

        logger.info("Switching backend from {} to {}", currentBackendType, newBackend);

        // Validate configuration for new backend
        MemoryConfig.BackendType oldBackend = config.getBackendType();
        config.setBackendType(newBackend);

        if (!config.validate()) {
            logger.error("Configuration validation failed for backend: {}", newBackend);
            config.setBackendType(oldBackend);
            throw new VectorStoreException("Invalid configuration for backend: " + newBackend);
        }

        // Close current vector store
        if (currentVectorStore != null) {
            try {
                currentVectorStore.close();
            } catch (Exception e) {
                logger.warn("Error closing vector store during backend switch", e);
            }
            currentVectorStore = null;
            currentBackend = null;
        }

        // Create new vector store
        return getVectorStore(dataSource);
    }

    /**
     * Close current vector store
     */
    public void close() {
        if (currentVectorStore != null) {
            logger.info("Closing vector store");
            try {
                currentVectorStore.close();
            } catch (Exception e) {
                logger.warn("Error closing vector store", e);
            }
            currentVectorStore = null;
            currentBackend = null;
        }
    }

    /**
     * Get current backend type
     *
     * @return Current backend type
     */
    public MemoryConfig.BackendType getCurrentBackend() {
        return currentBackend;
    }

    /**
     * Check if vector store is initialized
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return currentVectorStore != null;
    }
}
