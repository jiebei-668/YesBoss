package tech.yesboss.persistence.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.yesboss.domain.message.UnifiedMessage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DbWriteEvent sealed interface and its implementations.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>All event records can be instantiated with mock data</li>
 *   <li>All fields match the inputs and are immutable</li>
 *   <li>Compiler enforcement of sealed interface permits clause</li>
 *   <li>Default timestamp generation works correctly</li>
 * </ul>
 */
@DisplayName("DbWriteEvent Sealed Interface Tests")
class DbWriteEventTest {

    @Test
    @DisplayName("InsertTaskSessionEvent should create with all fields")
    void testInsertTaskSessionEvent() {
        // Arrange & Act
        InsertTaskSessionEvent event = new InsertTaskSessionEvent(
                "tsk_test123",
                null,
                InsertTaskSessionEvent.ImType.FEISHU,
                "group_456",
                InsertTaskSessionEvent.AgentRole.MASTER,
                InsertTaskSessionEvent.TaskStatus.RUNNING,
                "Test Session",
                "[{\"task\": \"do something\"}]",
                null,
                123456789L
        );

        // Assert
        assertEquals("tsk_test123", event.sessionId());
        assertNull(event.parentId());
        assertEquals(InsertTaskSessionEvent.ImType.FEISHU, event.imType());
        assertEquals("group_456", event.imGroupId());
        assertEquals(InsertTaskSessionEvent.AgentRole.MASTER, event.role());
        assertEquals(InsertTaskSessionEvent.TaskStatus.RUNNING, event.status());
        assertEquals("Test Session", event.topic());
        assertEquals("[{\"task\": \"do something\"}]", event.executionPlan());
        assertNull(event.assignedTask());
        assertEquals(123456789L, event.createdAt());
        assertEquals("InsertTaskSessionEvent", event.getEventType());
    }

    @Test
    @DisplayName("InsertTaskSessionEvent should create Worker with parent")
    void testInsertTaskSessionEventWorker() {
        // Arrange & Act
        InsertTaskSessionEvent event = new InsertTaskSessionEvent(
                "tsk_worker123",
                "tsk_master456",
                InsertTaskSessionEvent.ImType.SLACK,
                "channel_789",
                InsertTaskSessionEvent.AgentRole.WORKER,
                InsertTaskSessionEvent.TaskStatus.PLANNING,
                "Worker Task",
                null,
                "Complete the assigned task"
        );

        // Assert
        assertEquals("tsk_worker123", event.sessionId());
        assertEquals("tsk_master456", event.parentId());
        assertEquals(InsertTaskSessionEvent.AgentRole.WORKER, event.role());
        assertEquals(InsertTaskSessionEvent.TaskStatus.PLANNING, event.status());
        assertNull(event.executionPlan());
        assertEquals("Complete the assigned task", event.assignedTask());
        assertTrue(event.createdAt() > 0, "createdAt should be auto-generated");
    }

    @Test
    @DisplayName("UpdateTaskStatusEvent should create with new status")
    void testUpdateTaskStatusEvent() {
        // Arrange & Act
        UpdateTaskStatusEvent event = new UpdateTaskStatusEvent(
                "tsk_test123",
                InsertTaskSessionEvent.TaskStatus.COMPLETED,
                "[{\"task\": \"done\"}]",
                987654321L
        );

        // Assert
        assertEquals("tsk_test123", event.sessionId());
        assertEquals(InsertTaskSessionEvent.TaskStatus.COMPLETED, event.newStatus());
        assertEquals("[{\"task\": \"done\"}]", event.planJson());
        assertEquals(987654321L, event.updatedAt());
        assertEquals("UpdateTaskStatusEvent", event.getEventType());
    }

    @Test
    @DisplayName("UpdateTaskStatusEvent should create without plan")
    void testUpdateTaskStatusEventNoPlan() {
        // Arrange & Act
        UpdateTaskStatusEvent event = new UpdateTaskStatusEvent(
                "tsk_test123",
                InsertTaskSessionEvent.TaskStatus.FAILED
        );

        // Assert
        assertEquals("tsk_test123", event.sessionId());
        assertEquals(InsertTaskSessionEvent.TaskStatus.FAILED, event.newStatus());
        assertNull(event.planJson());
        assertTrue(event.updatedAt() > 0, "updatedAt should be auto-generated");
    }

    @Test
    @DisplayName("InsertMessageEvent should create with all fields")
    void testInsertMessageEvent() {
        // Arrange
        UnifiedMessage message = new UnifiedMessage(
                UnifiedMessage.Role.USER,
                "Hello, Agent!",
                UnifiedMessage.PayloadFormat.PLAIN_TEXT
        );

        // Act
        InsertMessageEvent event = new InsertMessageEvent(
                "tsk_test123",
                InsertMessageEvent.StreamType.GLOBAL,
                1,
                message,
                111222333L
        );

        // Assert
        assertEquals("tsk_test123", event.sessionId());
        assertEquals(InsertMessageEvent.StreamType.GLOBAL, event.streamType());
        assertEquals(1, event.sequenceNum());
        assertEquals(message, event.message());
        assertEquals(111222333L, event.createdAt());
        assertEquals("InsertMessageEvent", event.getEventType());
    }

    @Test
    @DisplayName("InsertMessageEvent should create LOCAL stream")
    void testInsertMessageEventLocal() {
        // Arrange
        UnifiedMessage message = new UnifiedMessage(
                UnifiedMessage.Role.ASSISTANT,
                "Tool result here",
                UnifiedMessage.PayloadFormat.PLAIN_TEXT
        );

        // Act
        InsertMessageEvent event = new InsertMessageEvent(
                "tsk_worker123",
                InsertMessageEvent.StreamType.LOCAL,
                5,
                message
        );

        // Assert
        assertEquals("tsk_worker123", event.sessionId());
        assertEquals(InsertMessageEvent.StreamType.LOCAL, event.streamType());
        assertEquals(5, event.sequenceNum());
        assertTrue(event.createdAt() > 0, "createdAt should be auto-generated");
    }

    @Test
    @DisplayName("InsertToolExecutionLogEvent should create successful execution")
    void testInsertToolExecutionLogEventSuccess() {
        // Arrange & Act
        InsertToolExecutionLogEvent event = new InsertToolExecutionLogEvent(
                "tsk_worker123",
                "tool_call_abc",
                "read_file",
                "{\"path\": \"/tmp/file.txt\"}",
                "File content here",
                false,
                444555666L
        );

        // Assert
        assertEquals("tsk_worker123", event.sessionId());
        assertEquals("tool_call_abc", event.toolCallId());
        assertEquals("read_file", event.toolName());
        assertEquals("{\"path\": \"/tmp/file.txt\"}", event.arguments());
        assertEquals("File content here", event.result());
        assertFalse(event.isIntercepted());
        assertEquals(444555666L, event.createdAt());
        assertEquals("InsertToolExecutionLogEvent", event.getEventType());
    }

    @Test
    @DisplayName("InsertToolExecutionLogEvent should create intercepted execution")
    void testInsertToolExecutionLogEventIntercepted() {
        // Arrange & Act
        InsertToolExecutionLogEvent event = new InsertToolExecutionLogEvent(
                "tsk_worker123",
                "tool_call_xyz",
                "delete_file",
                "{\"path\": \"/etc/passwd\"}",
                null,
                true
        );

        // Assert
        assertEquals("tsk_worker123", event.sessionId());
        assertEquals("tool_call_xyz", event.toolCallId());
        assertEquals("delete_file", event.toolName());
        assertEquals("{\"path\": \"/etc/passwd\"}", event.arguments());
        assertNull(event.result());
        assertTrue(event.isIntercepted());
        assertTrue(event.createdAt() > 0, "createdAt should be auto-generated");
    }

    @Test
    @DisplayName("InsertToolExecutionLogEvent should create without isIntercepted")
    void testInsertToolExecutionLogEventDefault() {
        // Arrange & Act
        InsertToolExecutionLogEvent event = new InsertToolExecutionLogEvent(
                "tsk_worker123",
                "tool_call_def",
                "list_dir",
                "{\"path\": \"/tmp\"}",
                "dir listing"
        );

        // Assert
        assertFalse(event.isIntercepted(), "Default should be not intercepted");
        assertEquals("list_dir", event.toolName());
    }

    @Test
    @DisplayName("DeleteMessagesEvent should create with sessionId")
    void testDeleteMessagesEvent() {
        // Arrange & Act
        DeleteMessagesEvent event = new DeleteMessagesEvent("tsk_test123");

        // Assert
        assertEquals("tsk_test123", event.sessionId());
        assertEquals("DeleteMessagesEvent", event.getEventType());
    }

    @Test
    @DisplayName("DeleteTaskSessionEvent should create with sessionId")
    void testDeleteTaskSessionEvent() {
        // Arrange & Act
        DeleteTaskSessionEvent event = new DeleteTaskSessionEvent("tsk_test123");

        // Assert
        assertEquals("tsk_test123", event.sessionId());
        assertEquals("DeleteTaskSessionEvent", event.getEventType());
    }

    @Test
    @DisplayName("Records should be immutable")
    void testRecordImmutability() {
        // Arrange
        InsertTaskSessionEvent event = new InsertTaskSessionEvent(
                "tsk_test", null, InsertTaskSessionEvent.ImType.CLI,
                "cli_group", InsertTaskSessionEvent.AgentRole.MASTER,
                InsertTaskSessionEvent.TaskStatus.RUNNING, "CLI Task", null, null
        );

        // Assert - records are immutable by design
        // Attempting to reassign fields would cause compilation error
        assertEquals("tsk_test", event.sessionId());
        assertEquals(InsertTaskSessionEvent.ImType.CLI, event.imType());

        // Verify each call returns the same value (no internal mutation)
        assertEquals(event.sessionId(), event.sessionId());
        assertEquals(event.topic(), event.topic());
    }

    @Test
    @DisplayName("All permitted types should implement DbWriteEvent")
    void testSealedInterfacePermits() {
        // Arrange & Act
        DbWriteEvent insertEvent = new InsertTaskSessionEvent(
                "tsk_1", null, InsertTaskSessionEvent.ImType.FEISHU,
                "g1", InsertTaskSessionEvent.AgentRole.MASTER,
                InsertTaskSessionEvent.TaskStatus.PLANNING, "T", null, null
        );
        DbWriteEvent updateEvent = new UpdateTaskStatusEvent(
                "tsk_1", InsertTaskSessionEvent.TaskStatus.RUNNING
        );
        DbWriteEvent messageEvent = new InsertMessageEvent(
                "tsk_1", InsertMessageEvent.StreamType.GLOBAL, 1,
                new UnifiedMessage(UnifiedMessage.Role.USER, "Hi",
                        UnifiedMessage.PayloadFormat.PLAIN_TEXT)
        );
        DbWriteEvent toolEvent = new InsertToolExecutionLogEvent(
                "tsk_1", "call_1", "test", "{}", "ok", false
        );
        DbWriteEvent deleteMsgEvent = new DeleteMessagesEvent("tsk_1");
        DbWriteEvent deleteSessionEvent = new DeleteTaskSessionEvent("tsk_1");

        // Assert - all should be instances of DbWriteEvent
        assertTrue(insertEvent instanceof DbWriteEvent);
        assertTrue(updateEvent instanceof DbWriteEvent);
        assertTrue(messageEvent instanceof DbWriteEvent);
        assertTrue(toolEvent instanceof DbWriteEvent);
        assertTrue(deleteMsgEvent instanceof DbWriteEvent);
        assertTrue(deleteSessionEvent instanceof DbWriteEvent);

        // Verify getEventType works for all
        assertEquals("InsertTaskSessionEvent", insertEvent.getEventType());
        assertEquals("UpdateTaskStatusEvent", updateEvent.getEventType());
        assertEquals("InsertMessageEvent", messageEvent.getEventType());
        assertEquals("InsertToolExecutionLogEvent", toolEvent.getEventType());
        assertEquals("DeleteMessagesEvent", deleteMsgEvent.getEventType());
        assertEquals("DeleteTaskSessionEvent", deleteSessionEvent.getEventType());
    }
}
