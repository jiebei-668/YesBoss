package tech.yesboss.persistence.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Database Schema Initializer
 *
 * <p>Responsible for executing DDL scripts to initialize SQLite database schema.
 * Reads SQL migration files from classpath and executes them atomically.</p>
 *
 * <p>This class ensures:</p>
 * <ul>
 *   <li>task_session table with im_route and parent_id indexes</li>
 *   <li>chat_message table with critical idx_chat_msg_seq unique index</li>
 *   <li>tool_execution_log table with session and call_id indexes</li>
 * </ul>
 *
 * @see <a href="../../../../../resources/db/migration/V1__init_schema.sql">V1__init_schema.sql</a>
 */
public class DatabaseInitializer {

    private static final String MIGRATION_PATH = "/db/migration/V1__init_schema.sql";

    private final Connection connection;

    public DatabaseInitializer(Connection connection) {
        this.connection = connection;
    }

    /**
     * Initialize database schema by executing DDL migration scripts.
     *
     * <p>This method:</p>
     * <ol>
     *   <li>Reads the SQL migration file from classpath</li>
     *   <li>Executes each statement atomically</li>
     *   <li>Creates all tables, indexes, and constraints</li>
     * </ol>
     *
     * @throws SQLException if database initialization fails
     * @throws IOException if migration file cannot be read
     */
    public void initialize() throws SQLException, IOException {
        // Ensure auto-commit is enabled for DDL statements
        boolean originalAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(true);
            String sqlScript = readMigrationScript();
            executeSqlScript(sqlScript);
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    /**
     * Check if database schema has been initialized.
     *
     * @return true if db_metadata table exists and contains schema version
     */
    public boolean isInitialized() throws SQLException {
        try (var stmt = connection.prepareStatement(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='db_metadata'")) {
            var rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /**
     * Get current schema version from database.
     *
     * @return schema version string, or null if not initialized
     */
    public String getSchemaVersion() throws SQLException {
        try (var stmt = connection.prepareStatement(
                "SELECT value FROM db_metadata WHERE key = 'schema_version'")) {
            var rs = stmt.executeQuery();
            return rs.next() ? rs.getString("value") : null;
        }
    }

    /**
     * Read SQL migration script from classpath resource.
     *
     * @return SQL script content
     * @throws IOException if resource cannot be read
     */
    private String readMigrationScript() throws IOException {
        try (InputStream inputStream = DatabaseInitializer.class.getResourceAsStream(MIGRATION_PATH)) {
            if (inputStream == null) {
                throw new IOException("Migration script not found: " + MIGRATION_PATH);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Execute SQL script by splitting into individual statements.
     *
     * <p>Handles SQLite's script format with statement termination by semicolon.</p>
     *
     * @param sqlScript the SQL script to execute
     * @throws SQLException if any statement fails
     */
    private void executeSqlScript(String sqlScript) throws SQLException {
        // First remove full-line comments
        StringBuilder cleanedScript = new StringBuilder();
        String[] lines = sqlScript.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            // Skip full-line comments but keep other lines
            if (!trimmed.startsWith("--")) {
                // Remove inline comments
                int commentIndex = trimmed.indexOf("--");
                if (commentIndex >= 0) {
                    cleanedScript.append(trimmed.substring(0, commentIndex));
                } else {
                    cleanedScript.append(line);
                }
                cleanedScript.append("\n");
            }
        }

        // Split by semicolon and execute each statement
        String[] statements = cleanedScript.toString().split(";");

        try (Statement stmt = connection.createStatement()) {
            for (String statement : statements) {
                String trimmed = statement.trim();

                // Skip empty statements
                if (trimmed.isEmpty()) {
                    continue;
                }

                // Execute each statement individually
                stmt.execute(trimmed);
            }
        }
    }

    /**
     * Drop all tables (for testing purposes only).
     *
     * <p>WARNING: This will delete all data. Use only in test environments.</p>
     *
     * @throws SQLException if drop operation fails
     */
    public void dropAllTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Drop tables in reverse order of dependencies
            stmt.execute("DROP TABLE IF EXISTS tool_execution_log");
            stmt.execute("DROP TABLE IF EXISTS chat_message");
            stmt.execute("DROP TABLE IF EXISTS task_session");
            stmt.execute("DROP TABLE IF EXISTS db_metadata");
        }
    }
}
