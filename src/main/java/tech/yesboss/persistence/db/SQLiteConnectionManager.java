package tech.yesboss.persistence.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * SQLite Connection Manager
 *
 * <p>Manages SQLite database connections with support for both file-based
 * and in-memory databases. Implements connection pooling for efficient resource usage.</p>
 *
 * <p>SQLite Connection Configuration:</p>
 * <ul>
 *   <li>Journal mode: WAL (Write-Ahead Logging) for better concurrency</li>
 *   <li>Synchronous mode: NORMAL for balanced performance and safety</li>
 *   <li>Foreign keys: ENABLED for data integrity</li>
 *   <li>Cache size: -64000 (64MB) for improved performance</li>
 * </ul>
 */
public class SQLiteConnectionManager {

    private static final String JDBC_PREFIX = "jdbc:sqlite:";
    private static final String MEMORY_DB = JDBC_PREFIX + ":memory:";

    private static final ConcurrentMap<String, SQLiteConnectionManager> instances = new ConcurrentHashMap<>();

    private final String connectionString;
    private final Path dbPath;
    private volatile Connection connection;

    /**
     * Get or create connection manager for in-memory database.
     *
     * <p>Useful for testing and development.</p>
     *
     * @return connection manager for in-memory database
     */
    public static SQLiteConnectionManager inMemory() {
        return instances.computeIfAbsent(":memory:",
                key -> new SQLiteConnectionManager(MEMORY_DB, null));
    }

    /**
     * Get or create connection manager for file-based database.
     *
     * @param dbPath path to SQLite database file
     * @return connection manager for the specified database
     */
    public static SQLiteConnectionManager forFile(Path dbPath) {
        String key = dbPath.toAbsolutePath().toString();
        return instances.computeIfAbsent(key,
                k -> new SQLiteConnectionManager(JDBC_PREFIX + dbPath, dbPath));
    }

    private SQLiteConnectionManager(String connectionString, Path dbPath) {
        this.connectionString = connectionString;
        this.dbPath = dbPath;
    }

    /**
     * Get database connection, creating if necessary.
     *
     * <p>Applies performance and safety optimizations:</p>
     * <ul>
     *   <li>WAL mode for better read/write concurrency</li>
     *   <li>Foreign key constraints enabled</li>
     *   <li>Optimized cache and page size</li>
     * </ul>
     *
     * @return database connection
     * @throws SQLException if connection fails
     */
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            synchronized (this) {
                if (connection == null || connection.isClosed()) {
                    connection = createConnection();
                }
            }
        }
        return connection;
    }

    /**
     * Create new database connection with optimizations.
     *
     * @return new connection
     * @throws SQLException if connection fails
     */
    private Connection createConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(connectionString);

        // Enable foreign key constraints
        try (var stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }

        // Set WAL mode for better concurrency (multiple readers + one writer)
        try (var stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL");
        }

        // Set synchronous mode to NORMAL for balanced performance
        try (var stmt = conn.createStatement()) {
            stmt.execute("PRAGMA synchronous = NORMAL");
        }

        // Increase cache size to 64MB for better performance
        try (var stmt = conn.createStatement()) {
            stmt.execute("PRAGMA cache_size = -64000");
        }

        // Set page size to 4096 bytes
        try (var stmt = conn.createStatement()) {
            stmt.execute("PRAGMA page_size = 4096");
        }

        // Set busy timeout to 5 seconds
        try (var stmt = conn.createStatement()) {
            stmt.execute("PRAGMA busy_timeout = 5000");
        }

        return conn;
    }

    /**
     * Close database connection.
     *
     * @throws SQLException if closing fails
     */
    public void close() throws SQLException {
        synchronized (this) {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                connection = null;
            }
        }
    }

    /**
     * Get database path (null for in-memory database).
     *
     * @return database file path or null
     */
    public Path getDbPath() {
        return dbPath;
    }

    /**
     * Check if this is an in-memory database.
     *
     * @return true if in-memory, false if file-based
     */
    public boolean isInMemory() {
        return dbPath == null;
    }

    /**
     * Close all connection managers.
     *
     * <p>Useful for application shutdown.</p>
     */
    public static void closeAll() {
        instances.values().forEach(manager -> {
            try {
                manager.close();
            } catch (SQLException e) {
                // Log error but continue closing others
                System.err.println("Error closing connection: " + e.getMessage());
            }
        });
        instances.clear();
    }
}
