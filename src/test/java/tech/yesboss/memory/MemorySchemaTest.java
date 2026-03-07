package tech.yesboss.memory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Memory Persistence Database Schema Unit Tests
 *
 * Tests for resources, snippets, and preferences tables including:
 * - Table structure validation
 * - Foreign key constraints
 * - Index creation
 * - Boundary conditions
 * - Performance benchmarks
 */
@DisplayName("Memory Persistence Database Schema Tests")
class MemorySchemaTest {

    private static final String DB_URL = "jdbc:sqlite::memory:";
    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection(DB_URL);
        // Enable foreign key enforcement (required by SQLite)
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        initializeSchema();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    /**
     * Initialize the database schema with V2 migration script
     */
    private void initializeSchema() throws SQLException {
        // Execute V1 schema first (task_session table)
        executeSQL("""
            CREATE TABLE IF NOT EXISTS task_session (
                id TEXT PRIMARY KEY,
                parent_id TEXT,
                im_type TEXT NOT NULL CHECK(im_type IN ('FEISHU', 'SLACK', 'CLI')),
                im_group_id TEXT NOT NULL,
                role TEXT NOT NULL CHECK(role IN ('MASTER', 'WORKER')),
                status TEXT NOT NULL CHECK(status IN ('PLANNING', 'RUNNING', 'SUSPENDED', 'COMPLETED', 'FAILED')),
                topic TEXT NOT NULL,
                execution_plan TEXT,
                assigned_task TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
        """);

        // Execute V2 schema (memory tables)
        executeSQL("""
            CREATE TABLE IF NOT EXISTS resources (
                id TEXT PRIMARY KEY,
                conversation_id TEXT NOT NULL,
                session_id TEXT NOT NULL,
                content TEXT NOT NULL,
                abstract TEXT,
                embedding BLOB,
                message_count INTEGER DEFAULT 0,
                deleted INTEGER DEFAULT 0 CHECK(deleted IN (0, 1)),
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY (conversation_id) REFERENCES task_session(id) ON DELETE CASCADE,
                FOREIGN KEY (session_id) REFERENCES task_session(id) ON DELETE CASCADE
            );
        """);

        executeSQL("""
            CREATE TABLE IF NOT EXISTS snippets (
                id TEXT PRIMARY KEY,
                resource_id TEXT NOT NULL,
                summary TEXT NOT NULL,
                memory_type TEXT NOT NULL CHECK(memory_type IN ('PROFILE', 'EVENT', 'KNOWLEDGE', 'BEHAVIOR', 'SKILL', 'TOOL')),
                embedding BLOB,
                timestamp INTEGER NOT NULL,
                deleted INTEGER DEFAULT 0 CHECK(deleted IN (0, 1)),
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE CASCADE
            );
        """);

        executeSQL("""
            CREATE TABLE IF NOT EXISTS preferences (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL UNIQUE,
                summary TEXT NOT NULL,
                embedding BLOB,
                deleted INTEGER DEFAULT 0 CHECK(deleted IN (0, 1)),
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
        """);

        // Create indexes
        executeSQL("CREATE INDEX IF NOT EXISTS resource_conversation_idx ON resources(conversation_id);");
        executeSQL("CREATE INDEX IF NOT EXISTS resource_session_idx ON resources(session_id);");
        executeSQL("CREATE INDEX IF NOT EXISTS resource_embedding_null_idx ON resources(embedding) WHERE embedding IS NULL;");
        executeSQL("CREATE INDEX IF NOT EXISTS resource_created_at_idx ON resources(created_at);");
        executeSQL("CREATE INDEX IF NOT EXISTS snippet_resource_idx ON snippets(resource_id);");
        executeSQL("CREATE INDEX IF NOT EXISTS snippet_embedding_null_idx ON snippets(embedding) WHERE embedding IS NULL;");
        executeSQL("CREATE INDEX IF NOT EXISTS snippet_memory_type_idx ON snippets(memory_type);");
        executeSQL("CREATE INDEX IF NOT EXISTS snippet_timestamp_idx ON snippets(timestamp);");
        executeSQL("CREATE INDEX IF NOT EXISTS preference_name_idx ON preferences(name);");
        executeSQL("CREATE INDEX IF NOT EXISTS preference_embedding_null_idx ON preferences(embedding) WHERE embedding IS NULL;");
    }

    private void executeSQL(String sql) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    // ==========================================
    // Table Structure Validation Tests
    // ==========================================

    @Test
    @DisplayName("Resources table should have correct column definitions")
    void testResourcesTableStructure() throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet columns = meta.getColumns(null, null, "resources", null)) {
            List<String> columnNames = new ArrayList<>();
            while (columns.next()) {
                columnNames.add(columns.getString("COLUMN_NAME"));
            }

            assertTrue(columnNames.contains("id"), "Should have 'id' column");
            assertTrue(columnNames.contains("conversation_id"), "Should have 'conversation_id' column");
            assertTrue(columnNames.contains("session_id"), "Should have 'session_id' column");
            assertTrue(columnNames.contains("content"), "Should have 'content' column");
            assertTrue(columnNames.contains("abstract"), "Should have 'abstract' column");
            assertTrue(columnNames.contains("embedding"), "Should have 'embedding' column");
            assertTrue(columnNames.contains("message_count"), "Should have 'message_count' column");
            assertTrue(columnNames.contains("deleted"), "Should have 'deleted' column");
            assertTrue(columnNames.contains("created_at"), "Should have 'created_at' column");
            assertTrue(columnNames.contains("updated_at"), "Should have 'updated_at' column");
        }
    }

    @Test
    @DisplayName("Snippets table should have correct column definitions")
    void testSnippetsTableStructure() throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet columns = meta.getColumns(null, null, "snippets", null)) {
            List<String> columnNames = new ArrayList<>();
            while (columns.next()) {
                columnNames.add(columns.getString("COLUMN_NAME"));
            }

            assertTrue(columnNames.contains("id"), "Should have 'id' column");
            assertTrue(columnNames.contains("resource_id"), "Should have 'resource_id' column");
            assertTrue(columnNames.contains("summary"), "Should have 'summary' column");
            assertTrue(columnNames.contains("memory_type"), "Should have 'memory_type' column");
            assertTrue(columnNames.contains("embedding"), "Should have 'embedding' column");
            assertTrue(columnNames.contains("timestamp"), "Should have 'timestamp' column");
            assertTrue(columnNames.contains("deleted"), "Should have 'deleted' column");
            assertTrue(columnNames.contains("created_at"), "Should have 'created_at' column");
            assertTrue(columnNames.contains("updated_at"), "Should have 'updated_at' column");
        }
    }

    @Test
    @DisplayName("Preferences table should have correct column definitions")
    void testPreferencesTableStructure() throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet columns = meta.getColumns(null, null, "preferences", null)) {
            List<String> columnNames = new ArrayList<>();
            while (columns.next()) {
                columnNames.add(columns.getString("COLUMN_NAME"));
            }

            assertTrue(columnNames.contains("id"), "Should have 'id' column");
            assertTrue(columnNames.contains("name"), "Should have 'name' column");
            assertTrue(columnNames.contains("summary"), "Should have 'summary' column");
            assertTrue(columnNames.contains("embedding"), "Should have 'embedding' column");
            assertTrue(columnNames.contains("deleted"), "Should have 'deleted' column");
            assertTrue(columnNames.contains("created_at"), "Should have 'created_at' column");
            assertTrue(columnNames.contains("updated_at"), "Should have 'updated_at' column");
        }
    }

    // ==========================================
    // NOT NULL Constraint Tests
    // ==========================================

    @Test
    @DisplayName("Resources table should enforce NOT NULL constraints")
    void testResourcesNotNullConstraints() throws SQLException {
        // Create a valid task_session first
        executeSQL("INSERT INTO task_session (id, im_type, im_group_id, role, status, topic, created_at, updated_at) " +
                   "VALUES ('session-1', 'CLI', 'group-1', 'MASTER', 'RUNNING', 'test', 1, 1)");

        // Test inserting NULL in NOT NULL column should fail
        assertThrows(SQLException.class, () -> {
            executeSQL("INSERT INTO resources (id, conversation_id, session_id, content, created_at, updated_at) " +
                       "VALUES ('res-1', 'session-1', 'session-1', NULL, 1, 1)");
        }, "Should reject NULL content");

        assertThrows(SQLException.class, () -> {
            executeSQL("INSERT INTO resources (id, conversation_id, session_id, content, created_at, updated_at) " +
                       "VALUES ('res-2', NULL, 'session-1', 'test', 1, 1)");
        }, "Should reject NULL conversation_id");
    }

    @Test
    @DisplayName("Snippets table should enforce NOT NULL constraints")
    void testSnippetsNotNullConstraints() throws SQLException {
        // Create test data
        executeSQL("INSERT INTO task_session (id, im_type, im_group_id, role, status, topic, created_at, updated_at) " +
                   "VALUES ('session-1', 'CLI', 'group-1', 'MASTER', 'RUNNING', 'test', 1, 1)");
        executeSQL("INSERT INTO resources (id, conversation_id, session_id, content, created_at, updated_at) " +
                   "VALUES ('res-1', 'session-1', 'session-1', 'test content', 1, 1)");

        // Test inserting NULL in NOT NULL column should fail
        assertThrows(SQLException.class, () -> {
            executeSQL("INSERT INTO snippets (id, resource_id, summary, memory_type, timestamp, created_at, updated_at) " +
                       "VALUES ('snippet-1', 'res-1', NULL, 'PROFILE', 1, 1, 1)");
        }, "Should reject NULL summary");
    }

    // ==========================================
    // Foreign Key Constraint Tests
    // ==========================================

    @Test
    @DisplayName("Should enforce foreign key constraint on snippets.resource_id")
    void testSnippetForeignKeyConstraint() throws SQLException {
        // Create test data
        executeSQL("INSERT INTO task_session (id, im_type, im_group_id, role, status, topic, created_at, updated_at) " +
                   "VALUES ('session-1', 'CLI', 'group-1', 'MASTER', 'RUNNING', 'test', 1, 1)");

        // Test inserting with invalid resource_id should fail
        assertThrows(SQLException.class, () -> {
            executeSQL("INSERT INTO snippets (id, resource_id, summary, memory_type, timestamp, created_at, updated_at) " +
                       "VALUES ('snippet-1', 'nonexistent-resource', 'test summary', 'PROFILE', 1, 1, 1)");
        }, "Should reject invalid resource_id");
    }

    @Test
    @DisplayName("Should cascade delete snippets when resource is deleted")
    void testCascadeDeleteSnippet() throws SQLException {
        // Create test data
        executeSQL("INSERT INTO task_session (id, im_type, im_group_id, role, status, topic, created_at, updated_at) " +
                   "VALUES ('session-1', 'CLI', 'group-1', 'MASTER', 'RUNNING', 'test', 1, 1)");
        executeSQL("INSERT INTO resources (id, conversation_id, session_id, content, created_at, updated_at) " +
                   "VALUES ('res-1', 'session-1', 'session-1', 'test content', 1, 1)");
        executeSQL("INSERT INTO snippets (id, resource_id, summary, memory_type, timestamp, created_at, updated_at) " +
                   "VALUES ('snippet-1', 'res-1', 'test summary', 'PROFILE', 1, 1, 1)");

        // Delete the resource
        executeSQL("DELETE FROM resources WHERE id = 'res-1'");

        // Verify snippet is cascaded deleted
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM snippets WHERE id = 'snippet-1'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "Snippet should be deleted when resource is deleted");
        }
    }

    // ==========================================
    // CHECK Constraint Tests
    // ==========================================

    @Test
    @DisplayName("Should enforce CHECK constraint on memory_type")
    void testMemoryTypeCheckConstraint() throws SQLException {
        // Create test data
        executeSQL("INSERT INTO task_session (id, im_type, im_group_id, role, status, topic, created_at, updated_at) " +
                   "VALUES ('session-1', 'CLI', 'group-1', 'MASTER', 'RUNNING', 'test', 1, 1)");
        executeSQL("INSERT INTO resources (id, conversation_id, session_id, content, created_at, updated_at) " +
                   "VALUES ('res-1', 'session-1', 'session-1', 'test content', 1, 1)");

        // Test inserting invalid memory_type should fail
        assertThrows(SQLException.class, () -> {
            executeSQL("INSERT INTO snippets (id, resource_id, summary, memory_type, timestamp, created_at, updated_at) " +
                       "VALUES ('snippet-1', 'res-1', 'test summary', 'INVALID_TYPE', 1, 1, 1)");
        }, "Should reject invalid memory_type");

        // Test valid memory_types should succeed
        String[] validTypes = {"PROFILE", "EVENT", "KNOWLEDGE", "BEHAVIOR", "SKILL", "TOOL"};
        for (String type : validTypes) {
            assertDoesNotThrow(() -> {
                executeSQL(String.format(
                    "INSERT INTO snippets (id, resource_id, summary, memory_type, timestamp, created_at, updated_at) " +
                    "VALUES ('snippet-%s', 'res-1', 'test summary', '%s', 1, 1, 1)",
                    type, type
                ));
            }, "Should accept valid memory_type: " + type);
        }
    }

    @Test
    @DisplayName("Should enforce CHECK constraint on deleted field")
    void testDeletedCheckConstraint() throws SQLException {
        // Create test data
        executeSQL("INSERT INTO task_session (id, im_type, im_group_id, role, status, topic, created_at, updated_at) " +
                   "VALUES ('session-1', 'CLI', 'group-1', 'MASTER', 'RUNNING', 'test', 1, 1)");

        // Test invalid deleted values should fail
        assertThrows(SQLException.class, () -> {
            executeSQL("INSERT INTO resources (id, conversation_id, session_id, content, deleted, created_at, updated_at) " +
                       "VALUES ('res-1', 'session-1', 'session-1', 'test content', 2, 1, 1)");
        }, "Should reject invalid deleted value (2)");

        // Test valid deleted values should succeed
        assertDoesNotThrow(() -> {
            executeSQL("INSERT INTO resources (id, conversation_id, session_id, content, deleted, created_at, updated_at) " +
                       "VALUES ('res-1', 'session-1', 'session-1', 'test content', 0, 1, 1)");
        }, "Should accept deleted = 0");

        assertDoesNotThrow(() -> {
            executeSQL("INSERT INTO resources (id, conversation_id, session_id, content, deleted, created_at, updated_at) " +
                       "VALUES ('res-2', 'session-1', 'session-1', 'test content 2', 1, 1, 1)");
        }, "Should accept deleted = 1");
    }

    // ==========================================
    // UNIQUE Constraint Tests
    // ==========================================

    @Test
    @DisplayName("Should enforce UNIQUE constraint on preferences.name")
    void testPreferencesNameUniqueConstraint() throws SQLException {
        executeSQL("INSERT INTO preferences (id, name, summary, created_at, updated_at) " +
                   "VALUES ('pref-1', 'preference-1', 'test summary', 1, 1)");

        // Test inserting duplicate name should fail
        assertThrows(SQLException.class, () -> {
            executeSQL("INSERT INTO preferences (id, name, summary, created_at, updated_at) " +
                       "VALUES ('pref-2', 'preference-1', 'another summary', 1, 1)");
        }, "Should reject duplicate preference name");
    }

    // ==========================================
    // Boundary Condition Tests
    // ==========================================

    @Test
    @DisplayName("Should handle maximum content length (10000 characters)")
    void testMaximumContentLength() throws SQLException {
        // Create test data
        executeSQL("INSERT INTO task_session (id, im_type, im_group_id, role, status, topic, created_at, updated_at) " +
                   "VALUES ('session-1', 'CLI', 'group-1', 'MASTER', 'RUNNING', 'test', 1, 1)");

        String longContent = "x".repeat(10000);
        assertDoesNotThrow(() -> {
            executeSQL("INSERT INTO resources (id, conversation_id, session_id, content, created_at, updated_at) " +
                       "VALUES ('res-1', 'session-1', 'session-1', '" + longContent + "', 1, 1)");
        }, "Should accept content of 10000 characters");
    }

    @Test
    @DisplayName("Should handle embedding size (6144 bytes)")
    void testEmbeddingSize() throws SQLException {
        // Create test data
        executeSQL("INSERT INTO task_session (id, im_type, im_group_id, role, status, topic, created_at, updated_at) " +
                   "VALUES ('session-1', 'CLI', 'group-1', 'MASTER', 'RUNNING', 'test', 1, 1)");

        // Create a 6144-byte embedding (1536 dimensions * 4 bytes/float)
        byte[] embedding = new byte[6144];

        try (PreparedStatement pstmt = connection.prepareStatement(
                "INSERT INTO resources (id, conversation_id, session_id, content, embedding, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            pstmt.setString(1, "res-1");
            pstmt.setString(2, "session-1");
            pstmt.setString(3, "session-1");
            pstmt.setString(4, "test content");
            pstmt.setBytes(5, embedding);
            pstmt.setLong(6, 1);
            pstmt.setLong(7, 1);
            pstmt.executeUpdate();
        }

        // Verify embedding was stored
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT embedding FROM resources WHERE id = 'res-1'")) {
            assertTrue(rs.next());
            assertNotNull(rs.getBytes("embedding"));
            assertEquals(6144, rs.getBytes("embedding").length);
        }
    }

    // ==========================================
    // Index Tests
    // ==========================================

    @Test
    @DisplayName("Should create all required indexes")
    void testIndexesCreated() throws SQLException {
        List<String> expectedIndexes = List.of(
            "resource_conversation_idx",
            "resource_session_idx",
            "resource_embedding_null_idx",
            "resource_created_at_idx",
            "snippet_resource_idx",
            "snippet_embedding_null_idx",
            "snippet_memory_type_idx",
            "snippet_timestamp_idx",
            "preference_name_idx",
            "preference_embedding_null_idx"
        );

        // Query SQLite master table to get indexes
        List<String> actualIndexes = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE '%_idx'")) {
            while (rs.next()) {
                String indexName = rs.getString("name");
                if (indexName != null && !indexName.startsWith("sqlite_autoindex_")) {
                    actualIndexes.add(indexName);
                }
            }
        }

        for (String expectedIndex : expectedIndexes) {
            assertTrue(actualIndexes.contains(expectedIndex),
                "Should have index: " + expectedIndex + ". Actual indexes: " + actualIndexes);
        }
    }

    // ==========================================
    // Performance Benchmark Tests
    // ==========================================

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Should insert 1000 records in less than 5 seconds")
    void testInsertPerformance() throws SQLException {
        // Create test data
        executeSQL("INSERT INTO task_session (id, im_type, im_group_id, role, status, topic, created_at, updated_at) " +
                   "VALUES ('session-1', 'CLI', 'group-1', 'MASTER', 'RUNNING', 'test', 1, 1)");

        long startTime = System.currentTimeMillis();

        try (PreparedStatement pstmt = connection.prepareStatement(
                "INSERT INTO resources (id, conversation_id, session_id, content, deleted, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 0, ?, ?)")) {
            for (int i = 0; i < 1000; i++) {
                pstmt.setString(1, "res-" + i);
                pstmt.setString(2, "session-1");
                pstmt.setString(3, "session-1");
                pstmt.setString(4, "test content " + i);
                pstmt.setLong(5, 1);
                pstmt.setLong(6, 1);
                pstmt.executeUpdate();
            }
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertTrue(duration < 5000,
            "Should insert 1000 records in less than 5 seconds (actual: " + duration + "ms)");
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    @DisplayName("Should query 10000 records in less than 3 seconds")
    void testQueryPerformance() throws SQLException {
        // Create test data
        executeSQL("INSERT INTO task_session (id, im_type, im_group_id, role, status, topic, created_at, updated_at) " +
                   "VALUES ('session-1', 'CLI', 'group-1', 'MASTER', 'RUNNING', 'test', 1, 1)");

        // Insert 10000 records
        try (PreparedStatement pstmt = connection.prepareStatement(
                "INSERT INTO resources (id, conversation_id, session_id, content, deleted, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 0, ?, ?)")) {
            for (int i = 0; i < 10000; i++) {
                pstmt.setString(1, "res-" + i);
                pstmt.setString(2, "session-1");
                pstmt.setString(3, "session-1");
                pstmt.setString(4, "test content " + i);
                pstmt.setLong(5, 1);
                pstmt.setLong(6, 1);
                pstmt.executeUpdate();
            }
        }

        // Test query performance
        long startTime = System.currentTimeMillis();

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM resources WHERE conversation_id = 'session-1'")) {
            int count = 0;
            while (rs.next()) {
                count++;
            }
            assertEquals(10000, count, "Should query all 10000 records");
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertTrue(duration < 3000,
            "Should query 10000 records in less than 3 seconds (actual: " + duration + "ms)");
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    @DisplayName("Should handle concurrent queries (10 threads)")
    void testConcurrentQueries() throws InterruptedException, SQLException {
        // Create test data
        executeSQL("INSERT INTO task_session (id, im_type, im_group_id, role, status, topic, created_at, updated_at) " +
                   "VALUES ('session-1', 'CLI', 'group-1', 'MASTER', 'RUNNING', 'test', 1, 1)");

        // Insert 100 records
        try (PreparedStatement pstmt = connection.prepareStatement(
                "INSERT INTO resources (id, conversation_id, session_id, content, deleted, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 0, ?, ?)")) {
            for (int i = 0; i < 100; i++) {
                pstmt.setString(1, "res-" + i);
                pstmt.setString(2, "session-1");
                pstmt.setString(3, "session-1");
                pstmt.setString(4, "test content " + i);
                pstmt.setLong(5, 1);
                pstmt.setLong(6, 1);
                pstmt.executeUpdate();
            }
        }

        // Create 10 concurrent queries
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    try (Statement stmt = connection.createStatement();
                         ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM resources")) {
                        if (rs.next() && rs.getInt(1) >= 100) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertEquals(threadCount, successCount.get(),
            "All concurrent queries should succeed");
        assertTrue(duration < 2000,
            "Should complete 10 concurrent queries in less than 2 seconds (actual: " + duration + "ms)");
    }
}
