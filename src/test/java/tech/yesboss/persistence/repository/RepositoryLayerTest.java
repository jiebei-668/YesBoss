package tech.yesboss.persistence.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.domain.message.UnifiedMessage.PayloadFormat;
import tech.yesboss.persistence.db.DatabaseInitializer;
import tech.yesboss.persistence.db.SingleThreadDbWriter;
import tech.yesboss.persistence.db.SQLiteConnectionManager;
import tech.yesboss.persistence.entity.TaskSession;
import tech.yesboss.persistence.entity.TaskSession.ImType;
import tech.yesboss.persistence.entity.TaskSession.Status;
import tech.yesboss.persistence.entity.ToolExecutionLog;
import tech.yesboss.persistence.event.InsertMessageEvent;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Repository layer (TaskSession, ChatMessage, ToolExecution).
 *
 * <p>Tests read operations with direct SQL queries and write operations
 * that delegate to SingleThreadDbWriter via events.</p>
 */
@DisplayName("Repository Layer Tests")
class RepositoryLayerTest {

    private SQLiteConnectionManager connectionManager;
    private Connection connection;
    private SingleThreadDbWriter writer;
    private TaskSessionRepository taskSessionRepo;
    private ChatMessageRepository chatMessageRepo;
    private ToolExecutionRepository toolExecutionRepo;

    @BeforeEach
    void setUp() throws SQLException, IOException, InterruptedException {
        connectionManager = SQLiteConnectionManager.inMemory();
        connection = connectionManager.getConnection();

        // Initialize schema
        DatabaseInitializer initializer = new DatabaseInitializer(connection);
        initializer.initialize();

        // Create and start writer
        writer = new SingleThreadDbWriter(connection);
        writer.startConsumer();

        // Create repositories
        taskSessionRepo = new TaskSessionRepositoryImpl(connection, writer);
        chatMessageRepo = new ChatMessageRepositoryImpl(connection, writer);
        toolExecutionRepo = new ToolExecutionRepositoryImpl(connection, writer);
    }

    @AfterEach
    void tearDown() throws InterruptedException, SQLException {
        if (writer != null) {
            writer.stopConsumer();
        }
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        connectionManager.close();
    }

    @Test
    @DisplayName("TaskSessionRepository: saveSession should create record")
    void testTaskSessionSave() throws InterruptedException {
        // Arrange
        String sessionId = "tsk_test_001";

        // Act
        taskSessionRepo.saveSession(
                sessionId, null, ImType.CLI, "test_group",
                TaskSession.Role.MASTER, Status.RUNNING, "Test Task", null
        );

        // Wait for processing
        waitForQueueEmpty(2000);

        // Assert
        Optional<TaskSession> found = taskSessionRepo.findById(sessionId);
        assertTrue(found.isPresent(), "Session should be found");
        assertEquals("Test Task", found.get().topic(), "Topic should match");
        assertEquals(TaskSession.Role.MASTER, found.get().role(), "Role should be MASTER");
    }

    @Test
    @DisplayName("TaskSessionRepository: findByImRoute should find active session")
    void testTaskSessionFindByImRoute() throws InterruptedException {
        // Arrange
        taskSessionRepo.saveSession(
                "tsk_route_001", null, ImType.FEISHU, "group_123",
                TaskSession.Role.MASTER, Status.RUNNING, "Route Test", null
        );
        waitForQueueEmpty(2000);

        // Act
        Optional<TaskSession> found = taskSessionRepo.findByImRoute(ImType.FEISHU, "group_123");

        // Assert
        assertTrue(found.isPresent(), "Session should be found by IM route");
        assertEquals("Route Test", found.get().topic(), "Topic should match");
    }

    @Test
    @DisplayName("TaskSessionRepository: findByParentId should find children")
    void testTaskSessionFindByParentId() throws InterruptedException {
        // Arrange - Create master
        taskSessionRepo.saveSession(
                "tsk_master_001", null, ImType.SLACK, "channel_001",
                TaskSession.Role.MASTER, Status.RUNNING, "Master Task", null
        );

        // Create workers
        taskSessionRepo.saveSession(
                "tsk_worker_001", "tsk_master_001", ImType.SLACK, "channel_001",
                TaskSession.Role.WORKER, Status.RUNNING, "Worker 1", "Subtask 1"
        );
        taskSessionRepo.saveSession(
                "tsk_worker_002", "tsk_master_001", ImType.SLACK, "channel_001",
                TaskSession.Role.WORKER, Status.RUNNING, "Worker 2", "Subtask 2"
        );
        waitForQueueEmpty(2000);

        // Act
        List<TaskSession> children = taskSessionRepo.findByParentId("tsk_master_001");

        // Assert
        assertEquals(2, children.size(), "Should find 2 worker sessions");
        assertTrue(children.stream().allMatch(TaskSession::isWorker), "All should be workers");
    }

    @Test
    @DisplayName("TaskSessionRepository: updateStatus should change status")
    void testTaskSessionUpdateStatus() throws InterruptedException {
        // Arrange
        String sessionId = "tsk_update_001";
        taskSessionRepo.saveSession(
                sessionId, null, ImType.CLI, "test",
                TaskSession.Role.WORKER, Status.RUNNING, "Update Test", "Task"
        );
        waitForQueueEmpty(2000);

        // Act
        taskSessionRepo.updateStatus(sessionId, Status.COMPLETED, null);
        waitForQueueEmpty(2000);

        // Assert
        Optional<TaskSession> found = taskSessionRepo.findById(sessionId);
        assertTrue(found.isPresent(), "Session should be found");
        assertEquals(Status.COMPLETED, found.get().status(), "Status should be COMPLETED");
    }

    @Test
    @DisplayName("ChatMessageRepository: saveMessage should persist message")
    void testChatMessageSave() throws InterruptedException {
        // Arrange - Create session first
        taskSessionRepo.saveSession(
                "tsk_msg_001", null, ImType.CLI, "test",
                TaskSession.Role.MASTER, Status.RUNNING, "Message Test", null
        );
        waitForQueueEmpty(2000);

        UnifiedMessage message = new UnifiedMessage(UnifiedMessage.Role.USER, "Hello, world!", PayloadFormat.PLAIN_TEXT);

        // Act
        chatMessageRepo.saveMessage("tsk_msg_001", InsertMessageEvent.StreamType.GLOBAL, 1, message);
        waitForQueueEmpty(2000);

        // Assert
        List<UnifiedMessage> context = chatMessageRepo.fetchContext("tsk_msg_001", InsertMessageEvent.StreamType.GLOBAL);
        assertEquals(1, context.size(), "Should have 1 message");
        assertEquals("Hello, world!", context.get(0).content(), "Content should match");
    }

    @Test
    @DisplayName("ChatMessageRepository: fetchContext should return ordered messages")
    void testChatMessageFetchContextOrdered() throws InterruptedException {
        // Arrange - Create session first
        taskSessionRepo.saveSession(
                "tsk_order_001", null, ImType.CLI, "test",
                TaskSession.Role.MASTER, Status.RUNNING, "Order Test", null
        );
        waitForQueueEmpty(2000);

        // Add messages in specific order
        chatMessageRepo.saveMessage("tsk_order_001", InsertMessageEvent.StreamType.LOCAL, 1,
                new UnifiedMessage(UnifiedMessage.Role.USER, "First", PayloadFormat.PLAIN_TEXT));
        chatMessageRepo.saveMessage("tsk_order_001", InsertMessageEvent.StreamType.LOCAL, 2,
                new UnifiedMessage(UnifiedMessage.Role.ASSISTANT, "Second", PayloadFormat.PLAIN_TEXT));
        chatMessageRepo.saveMessage("tsk_order_001", InsertMessageEvent.StreamType.LOCAL, 3,
                new UnifiedMessage(UnifiedMessage.Role.USER, "Third", PayloadFormat.PLAIN_TEXT));
        waitForQueueEmpty(2000);

        // Act
        List<UnifiedMessage> context = chatMessageRepo.fetchContext("tsk_order_001", InsertMessageEvent.StreamType.LOCAL);

        // Assert
        assertEquals(3, context.size(), "Should have 3 messages");
        assertEquals("First", context.get(0).content(), "First message should be first");
        assertEquals("Second", context.get(1).content(), "Second message should be second");
        assertEquals("Third", context.get(2).content(), "Third message should be third");
    }

    @Test
    @DisplayName("ChatMessageRepository: getCurrentSequenceNumber should return max seq")
    void testChatMessageGetCurrentSequenceNumber() throws InterruptedException {
        // Arrange - Create session first
        taskSessionRepo.saveSession(
                "tsk_seq_001", null, ImType.CLI, "test",
                TaskSession.Role.MASTER, Status.RUNNING, "Sequence Test", null
        );
        waitForQueueEmpty(2000);

        chatMessageRepo.saveMessage("tsk_seq_001", InsertMessageEvent.StreamType.GLOBAL, 1,
                new UnifiedMessage(UnifiedMessage.Role.USER, "Msg 1", PayloadFormat.PLAIN_TEXT));
        chatMessageRepo.saveMessage("tsk_seq_001", InsertMessageEvent.StreamType.GLOBAL, 2,
                new UnifiedMessage(UnifiedMessage.Role.ASSISTANT, "Msg 2", PayloadFormat.PLAIN_TEXT));
        chatMessageRepo.saveMessage("tsk_seq_001", InsertMessageEvent.StreamType.GLOBAL, 5,
                new UnifiedMessage(UnifiedMessage.Role.USER, "Msg 5", PayloadFormat.PLAIN_TEXT));
        waitForQueueEmpty(2000);

        // Act
        int maxSeq = chatMessageRepo.getCurrentSequenceNumber("tsk_seq_001", InsertMessageEvent.StreamType.GLOBAL);

        // Assert
        assertEquals(5, maxSeq, "Should return max sequence number");
    }

    @Test
    @DisplayName("ChatMessageRepository: deleteBySession should remove all messages")
    void testChatMessageDeleteBySession() throws InterruptedException {
        // Arrange - Create session first
        taskSessionRepo.saveSession(
                "tsk_del_001", null, ImType.CLI, "test",
                TaskSession.Role.MASTER, Status.RUNNING, "Delete Test", null
        );
        waitForQueueEmpty(2000);

        chatMessageRepo.saveMessage("tsk_del_001", InsertMessageEvent.StreamType.GLOBAL, 1,
                new UnifiedMessage(UnifiedMessage.Role.USER, "To be deleted", PayloadFormat.PLAIN_TEXT));
        waitForQueueEmpty(2000);

        // Verify messages exist
        assertEquals(1, chatMessageRepo.fetchContext("tsk_del_001", InsertMessageEvent.StreamType.GLOBAL).size());

        // Act
        chatMessageRepo.deleteBySession("tsk_del_001");
        waitForQueueEmpty(2000);

        // Assert
        List<UnifiedMessage> context = chatMessageRepo.fetchContext("tsk_del_001", InsertMessageEvent.StreamType.GLOBAL);
        assertTrue(context.isEmpty(), "All messages should be deleted");
    }

    @Test
    @DisplayName("ToolExecutionRepository: saveLog should persist tool execution")
    void testToolExecutionSaveLog() throws InterruptedException {
        // Arrange - Create session first
        taskSessionRepo.saveSession(
                "tsk_tool_001", null, ImType.CLI, "test",
                TaskSession.Role.WORKER, Status.RUNNING, "Tool Test", "Run tool"
        );
        waitForQueueEmpty(2000);

        // Act
        toolExecutionRepo.saveLog(
                "tsk_tool_001", "call_001", "test_tool",
                "{\"arg\": \"value\"}", "Success result", false
        );
        waitForQueueEmpty(2000);

        // Assert
        Optional<ToolExecutionLog> found = toolExecutionRepo.findByToolCallId("call_001");
        assertTrue(found.isPresent(), "Tool log should be found");
        assertEquals("test_tool", found.get().toolName(), "Tool name should match");
        assertFalse(found.get().isIntercepted(), "Should not be intercepted");
    }

    @Test
    @DisplayName("ToolExecutionRepository: findBySessionId should return logs in order")
    void testToolExecutionFindBySessionId() throws InterruptedException {
        // Arrange - Create session first
        taskSessionRepo.saveSession(
                "tsk_tools_001", null, ImType.CLI, "test",
                TaskSession.Role.WORKER, Status.RUNNING, "Tools Test", "Run tools"
        );
        waitForQueueEmpty(2000);

        toolExecutionRepo.saveLog("tsk_tools_001", "call_001", "tool_a", "{}", "Result A", false);
        toolExecutionRepo.saveLog("tsk_tools_001", "call_002", "tool_b", "{}", "Result B", false);
        toolExecutionRepo.saveLog("tsk_tools_001", "call_003", "tool_c", "{}", "Result C", false);
        waitForQueueEmpty(2000);

        // Act
        List<ToolExecutionLog> logs = toolExecutionRepo.findBySessionId("tsk_tools_001");

        // Assert
        assertEquals(3, logs.size(), "Should find 3 tool logs");
        assertEquals("tool_a", logs.get(0).toolName(), "Should be ordered by time");
        assertEquals("tool_b", logs.get(1).toolName(), "Should be ordered by time");
        assertEquals("tool_c", logs.get(2).toolName(), "Should be ordered by time");
    }

    @Test
    @DisplayName("ToolExecutionRepository: findInterceptedBySession should return only blocked")
    void testToolExecutionFindIntercepted() throws InterruptedException {
        // Arrange - Create session first
        taskSessionRepo.saveSession(
                "tsk_block_001", null, ImType.CLI, "test",
                TaskSession.Role.WORKER, Status.RUNNING, "Block Test", "Run blocked"
        );
        waitForQueueEmpty(2000);

        toolExecutionRepo.saveLog("tsk_block_001", "call_safe", "safe_tool", "{}", "OK", false);
        toolExecutionRepo.saveLog("tsk_block_001", "call_block", "danger_tool", "{}", "Blocked", true);
        toolExecutionRepo.saveLog("tsk_block_001", "call_safe2", "safe_tool2", "{}", "OK", false);
        waitForQueueEmpty(2000);

        // Act
        List<ToolExecutionLog> intercepted = toolExecutionRepo.findInterceptedBySession("tsk_block_001");

        // Assert
        assertEquals(1, intercepted.size(), "Should find 1 intercepted log");
        assertEquals("danger_tool", intercepted.get(0).toolName(), "Should be the blocked tool");
        assertTrue(intercepted.get(0).wasBlocked(), "Should be marked as blocked");
    }

    /**
     * Wait for the queue to be empty with a timeout.
     */
    private boolean waitForQueueEmpty(long timeoutMs) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (writer.getQueueSize() == 0) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                if (writer.getQueueSize() == 0) {
                    return true;
                }
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
