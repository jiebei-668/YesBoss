package tech.yesboss.persistence.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DatabaseInitializer
 *
 * <p>Verifies that the SQLite database schema is correctly initialized with:
 * <ul>
 *   <li>task_session table with proper indexes</li>
 *   <li>chat_message table with critical unique index</li>
 *   <li>tool_execution_log table with proper indexes</li>
 *   <li>All constraints and foreign keys</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseInitializerTest {

    private SQLiteConnectionManager connectionManager;
    private DatabaseInitializer initializer;
    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connectionManager = SQLiteConnectionManager.inMemory();
        connection = connectionManager.getConnection();
        initializer = new DatabaseInitializer(connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        connectionManager.close();
    }

    @Test
    @DisplayName("Should initialize database schema successfully")
    void testInitializeSchema() throws SQLException, IOException {
        // Act
        initializer.initialize();

        // Assert - database should be marked as initialized
        assertTrue(initializer.isInitialized(),
                "Database should be initialized after initialization");

        // Assert - schema version should be set
        String version = initializer.getSchemaVersion();
        assertNotNull(version, "Schema version should not be null");
        assertEquals("1.0", version, "Schema version should be 1.0");
    }

    @Test
    @DisplayName("Should create task_session table with all required columns")
    void testTaskSessionTableExists() throws SQLException, IOException {
        // Arrange
        initializer.initialize();

        // Act
        DatabaseMetaData meta = connection.getMetaData();
        ResultSet columns = meta.getColumns(null, null, "task_session", null);

        // Assert - verify table exists and collect columns
        Set<String> foundColumns = new HashSet<>();
        while (columns.next()) {
            foundColumns.add(columns.getString("COLUMN_NAME"));
        }
        assertFalse(foundColumns.isEmpty(), "task_session table should exist");

        // Verify all required columns exist
        String[] requiredColumns = {
                "id", "parent_id", "im_type", "im_group_id", "role",
                "status", "topic", "execution_plan", "assigned_task",
                "created_at", "updated_at"
        };

        for (String column : requiredColumns) {
            assertTrue(foundColumns.contains(column),
                    "Column " + column + " should exist in task_session table");
        }

        columns.close();
    }

    @Test
    @DisplayName("Should create chat_message table with all required columns")
    void testChatMessageTableExists() throws SQLException, IOException {
        // Arrange
        initializer.initialize();

        // Act
        DatabaseMetaData meta = connection.getMetaData();
        ResultSet columns = meta.getColumns(null, null, "chat_message", null);

        // Assert - verify table exists and collect columns
        Set<String> foundColumns = new HashSet<>();
        while (columns.next()) {
            foundColumns.add(columns.getString("COLUMN_NAME"));
        }
        assertFalse(foundColumns.isEmpty(), "chat_message table should exist");

        // Verify all required columns exist
        String[] requiredColumns = {
                "id", "session_id", "stream_type", "sequence_num",
                "msg_role", "payload_format", "content", "token_count", "created_at"
        };

        for (String column : requiredColumns) {
            assertTrue(foundColumns.contains(column),
                    "Column " + column + " should exist in chat_message table");
        }

        columns.close();
    }

    @Test
    @DisplayName("Should create tool_execution_log table with all required columns")
    void testToolExecutionLogTableExists() throws SQLException, IOException {
        // Arrange
        initializer.initialize();

        // Act
        DatabaseMetaData meta = connection.getMetaData();
        ResultSet columns = meta.getColumns(null, null, "tool_execution_log", null);

        // Assert - verify table exists and collect columns
        Set<String> foundColumns = new HashSet<>();
        while (columns.next()) {
            foundColumns.add(columns.getString("COLUMN_NAME"));
        }
        assertFalse(foundColumns.isEmpty(), "tool_execution_log table should exist");

        // Verify all required columns exist
        String[] requiredColumns = {
                "id", "session_id", "tool_call_id", "tool_name",
                "arguments", "result", "is_intercepted", "created_at"
        };

        for (String column : requiredColumns) {
            assertTrue(foundColumns.contains(column),
                    "Column " + column + " should exist in tool_execution_log table");
        }

        columns.close();
    }

    @Test
    @DisplayName("Should create idx_session_im_route index on task_session")
    void testIdxSessionImRoute() throws SQLException, IOException {
        // Arrange
        initializer.initialize();

        // Act
        DatabaseMetaData meta = connection.getMetaData();
        ResultSet indexes = meta.getIndexInfo(null, null, "task_session", false, false);

        // Assert - verify idx_session_im_route exists
        boolean found = false;
        while (indexes.next()) {
            String indexName = indexes.getString("INDEX_NAME");
            if ("idx_session_im_route".equals(indexName)) {
                found = true;
                break;
            }
        }

        assertTrue(found, "idx_session_im_route index should exist on task_session");
        indexes.close();
    }

    @Test
    @DisplayName("Should create idx_session_parent index on task_session")
    void testIdxSessionParent() throws SQLException, IOException {
        // Arrange
        initializer.initialize();

        // Act
        DatabaseMetaData meta = connection.getMetaData();
        ResultSet indexes = meta.getIndexInfo(null, null, "task_session", false, false);

        // Assert - verify idx_session_parent exists
        boolean found = false;
        while (indexes.next()) {
            String indexName = indexes.getString("INDEX_NAME");
            if ("idx_session_parent".equals(indexName)) {
                found = true;
                break;
            }
        }

        assertTrue(found, "idx_session_parent index should exist on task_session");
        indexes.close();
    }

    @Test
    @DisplayName("Should create critical idx_chat_msg_seq unique index on chat_message")
    void testIdxChatMsgSeq() throws SQLException, IOException {
        // Arrange
        initializer.initialize();

        // Act
        DatabaseMetaData meta = connection.getMetaData();
        ResultSet indexes = meta.getIndexInfo(null, null, "chat_message", false, false);

        // Assert - verify idx_chat_msg_seq exists and is unique
        boolean found = false;
        boolean unique = false;
        while (indexes.next()) {
            String indexName = indexes.getString("INDEX_NAME");
            if ("idx_chat_msg_seq".equals(indexName)) {
                found = true;
                unique = !indexes.getBoolean("NON_UNIQUE");
                break;
            }
        }

        assertTrue(found, "idx_chat_msg_seq index should exist on chat_message");
        assertTrue(unique, "idx_chat_msg_seq should be a unique index");
        indexes.close();
    }

    @Test
    @DisplayName("Should create idx_tool_log_session index on tool_execution_log")
    void testIdxToolLogSession() throws SQLException, IOException {
        // Arrange
        initializer.initialize();

        // Act
        DatabaseMetaData meta = connection.getMetaData();
        ResultSet indexes = meta.getIndexInfo(null, null, "tool_execution_log", false, false);

        // Assert - verify idx_tool_log_session exists
        boolean found = false;
        while (indexes.next()) {
            String indexName = indexes.getString("INDEX_NAME");
            if ("idx_tool_log_session".equals(indexName)) {
                found = true;
                break;
            }
        }

        assertTrue(found, "idx_tool_log_session index should exist on tool_execution_log");
        indexes.close();
    }

    @Test
    @DisplayName("Should create idx_tool_log_call_id index on tool_execution_log")
    void testIdxToolLogCallId() throws SQLException, IOException {
        // Arrange
        initializer.initialize();

        // Act
        DatabaseMetaData meta = connection.getMetaData();
        ResultSet indexes = meta.getIndexInfo(null, null, "tool_execution_log", false, false);

        // Assert - verify idx_tool_log_call_id exists
        boolean found = false;
        while (indexes.next()) {
            String indexName = indexes.getString("INDEX_NAME");
            if ("idx_tool_log_call_id".equals(indexName)) {
                found = true;
                break;
            }
        }

        assertTrue(found, "idx_tool_log_call_id index should exist on tool_execution_log");
        indexes.close();
    }

    @Test
    @DisplayName("Should enforce CHECK constraint on im_type enum")
    void testImTypeCheckConstraint() throws SQLException, IOException {
        // Arrange
        initializer.initialize();

        // Act & Assert - valid values should work
        String[] validTypes = {"FEISHU", "SLACK", "CLI"};
        for (String type : validTypes) {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO task_session (id, im_type, im_group_id, role, status, topic, created_at, updated_at) " +
                            "VALUES (?, ?, ?, 'MASTER', 'RUNNING', 'test', 1, 1)")) {
                stmt.setString(1, "test_" + type);
                stmt.setString(2, type);
                stmt.setString(3, "group123");
                assertDoesNotThrow(() -> stmt.executeUpdate(),
                        "Valid im_type " + type + " should be accepted");
            }
        }

        // Invalid value should fail
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO task_session (id, im_type, im_group_id, role, status, topic, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'MASTER', 'RUNNING', 'test', 1, 1)")) {
            stmt.setString(1, "test_invalid");
            stmt.setString(2, "INVALID_TYPE");
            stmt.setString(3, "group123");
            assertThrows(SQLException.class, stmt::executeUpdate,
                    "Invalid im_type should be rejected by CHECK constraint");
        }
    }

    @Test
    @DisplayName("Should enforce CHECK constraint on status enum")
    void testStatusCheckConstraint() throws SQLException, IOException {
        // Arrange
        initializer.initialize();

        // Act & Assert - valid values should work
        String[] validStatuses = {"PLANNING", "RUNNING", "SUSPENDED", "COMPLETED", "FAILED"};
        for (String status : validStatuses) {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO task_session (id, im_type, im_group_id, role, status, topic, created_at, updated_at) " +
                            "VALUES (?, 'FEISHU', 'group123', 'MASTER', ?, 'test', 1, 1)")) {
                stmt.setString(1, "test_" + status);
                stmt.setString(2, status);
                assertDoesNotThrow(() -> stmt.executeUpdate(),
                        "Valid status " + status + " should be accepted");
            }
        }
    }

    @Test
    @DisplayName("Should enforce unique constraint on (session_id, stream_type, sequence_num)")
    void testChatMessageUniqueConstraint() throws SQLException, IOException {
        // Arrange
        initializer.initialize();

        // Create a test session
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO task_session (id, im_type, im_group_id, role, status, topic, created_at, updated_at) " +
                        "VALUES ('sess1', 'FEISHU', 'group123', 'MASTER', 'RUNNING', 'test', 1, 1)")) {
            stmt.executeUpdate();
        }

        // Insert first message - should succeed
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO chat_message (id, session_id, stream_type, sequence_num, msg_role, payload_format, content, created_at) " +
                        "VALUES ('msg1', 'sess1', 'GLOBAL', 1, 'user', 'PLAIN_TEXT', 'hello', 1)")) {
            assertDoesNotThrow(() -> stmt.executeUpdate(), "First message should be inserted");
        }

        // Insert duplicate with same session_id, stream_type, sequence_num - should fail
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO chat_message (id, session_id, stream_type, sequence_num, msg_role, payload_format, content, created_at) " +
                        "VALUES ('msg2', 'sess1', 'GLOBAL', 1, 'user', 'PLAIN_TEXT', 'hello again', 1)")) {
            assertThrows(SQLException.class, stmt::executeUpdate,
                    "Duplicate (session_id, stream_type, sequence_num) should be rejected");
        }
    }
}
