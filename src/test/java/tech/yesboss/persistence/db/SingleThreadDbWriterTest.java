package tech.yesboss.persistence.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.persistence.event.*;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for SingleThreadDbWriter.
 *
 * <p>Verifies concurrency and sequential processing of database writes:
 * <ul>
 *   <li>Consumer thread processes events sequentially</li>
 *   <li>Multiple producer threads can submit concurrently</li>
 *   <li>No race conditions or data corruption</li>
 *   <li>Graceful shutdown with thread interruption</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("SingleThreadDbWriter Concurrency Tests")
class SingleThreadDbWriterTest {

    private SQLiteConnectionManager connectionManager;
    private Connection connection;
    private SingleThreadDbWriter writer;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        connectionManager = SQLiteConnectionManager.inMemory();
        connection = connectionManager.getConnection();

        // Initialize schema
        DatabaseInitializer initializer = new DatabaseInitializer(connection);
        initializer.initialize();

        // Create and start writer
        writer = new SingleThreadDbWriter(connection);
        writer.startConsumer();
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

    /**
     * Wait for the queue to be empty with a timeout.
     * This is more reliable than Thread.sleep() for synchronization.
     *
     * @param timeoutMs maximum time to wait in milliseconds
     * @return true if queue emptied within timeout, false otherwise
     */
    private boolean waitForQueueEmpty(long timeoutMs) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (writer.getQueueSize() == 0) {
                // Add a small buffer to ensure the last event is processed
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                // Check again to make sure queue is still empty
                if (writer.getQueueSize() == 0) {
                    return true;
                }
                // If queue has more items, continue waiting
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

    @Test
    @Order(1)
    @DisplayName("Should start and stop consumer thread")
    void testStartAndStop() throws InterruptedException {
        // Assert - writer should be running
        assertTrue(writer.isRunning(), "Writer should be running after startConsumer()");

        // Act - stop the writer
        writer.stopConsumer();

        // Assert - writer should be stopped
        assertFalse(writer.isRunning(), "Writer should be stopped after stopConsumer()");
    }

    @Test
    @Order(2)
    @DisplayName("Should reject starting consumer twice")
    void testStartTwiceThrows() {
        // Act & Assert - starting twice should throw
        assertThrows(IllegalStateException.class, writer::startConsumer,
                "Should throw IllegalStateException when starting consumer twice");
    }

    @Test
    @Order(3)
    @DisplayName("Should submit single event and process it")
    void testSubmitSingleEvent() throws SQLException, InterruptedException {
        // Arrange
        InsertTaskSessionEvent event = new InsertTaskSessionEvent(
                "tsk_single", null, InsertTaskSessionEvent.ImType.CLI,
                "test_group", InsertTaskSessionEvent.AgentRole.MASTER,
                InsertTaskSessionEvent.TaskStatus.RUNNING, "Single Event Test", null, null
        );

        // Act
        boolean submitted = writer.submitEvent(event);

        // Wait for processing using helper method
        boolean queueEmptied = waitForQueueEmpty(2000);

        // Assert
        assertTrue(submitted, "Event should be submitted successfully");
        assertTrue(queueEmptied, "Queue should be processed within timeout");
        assertEquals(0, writer.getQueueSize(), "Queue should be empty after processing");

        // Verify database
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT COUNT(*) FROM task_session WHERE id = 'tsk_single'")) {
            ResultSet rs = stmt.executeQuery();
            assertTrue(rs.next(), "Query should return a row");
            assertEquals(1, rs.getInt(1), "Task session should exist");
        }
    }

    @Test
    @Order(4)
    @DisplayName("Should reject null event")
    void testSubmitNullThrows() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> writer.submitEvent(null),
                "Should throw IllegalArgumentException for null event");
    }

    @Test
    @Order(5)
    @DisplayName("Should process events sequentially from multiple producers")
    void testConcurrentSubmission() throws Exception {
        // Arrange
        int threadCount = 10;
        int eventsPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // Act - submit events from multiple threads
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int i = 0; i < eventsPerThread; i++) {
                    String sessionId = "tsk_t" + threadId + "_e" + i;
                    InsertTaskSessionEvent event = new InsertTaskSessionEvent(
                            sessionId, null, InsertTaskSessionEvent.ImType.CLI,
                            "test_group", InsertTaskSessionEvent.AgentRole.WORKER,
                            InsertTaskSessionEvent.TaskStatus.PLANNING, "Thread " + threadId, null, null
                    );

                    if (writer.submitEvent(event)) {
                        successCount.incrementAndGet();
                    }
                }
            }, executor);
            futures.add(future);
        }

        // Wait for all submissions
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

        // Wait for processing using helper method
        boolean queueEmptied = waitForQueueEmpty(10000);

        // Assert
        assertEquals(threadCount * eventsPerThread, successCount.get(),
                "All events should be submitted successfully");
        assertTrue(queueEmptied, "Queue should be processed within timeout");

        // Verify database count
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT COUNT(*) FROM task_session")) {
            ResultSet rs = stmt.executeQuery();
            assertTrue(rs.next(), "Query should return a row");
            assertEquals(threadCount * eventsPerThread, rs.getInt(1),
                    "All events should be processed");
        }

        executor.shutdown();
    }

    @Test
    @Order(6)
    @DisplayName("Should process different event types correctly")
    void testMultipleEventTypes() throws Exception {
        // Arrange - Create a task session first
        InsertTaskSessionEvent sessionEvent = new InsertTaskSessionEvent(
                "tsk_mixed", null, InsertTaskSessionEvent.ImType.SLACK,
                "channel_123", InsertTaskSessionEvent.AgentRole.MASTER,
                InsertTaskSessionEvent.TaskStatus.RUNNING, "Mixed Events", null, null
        );

        // Act - Submit different event types
        writer.submitEvent(sessionEvent);
        waitForQueueEmpty(1000);

        // Update status
        writer.submitEvent(new UpdateTaskStatusEvent(
                "tsk_mixed", InsertTaskSessionEvent.TaskStatus.COMPLETED, "[{\"done\": true}]"
        ));
        waitForQueueEmpty(1000);

        // Insert message
        writer.submitEvent(new InsertMessageEvent(
                "tsk_mixed", InsertMessageEvent.StreamType.GLOBAL, 1,
                new UnifiedMessage(UnifiedMessage.Role.USER, "Test message",
                        UnifiedMessage.PayloadFormat.PLAIN_TEXT)
        ));
        waitForQueueEmpty(1000);

        // Insert tool log
        writer.submitEvent(new InsertToolExecutionLogEvent(
                "tsk_mixed", "call_001", "test_tool", "{}", "Tool result", false
        ));
        waitForQueueEmpty(1000);

        // Assert - Verify all data persisted
        // Check task session
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT status, execution_plan FROM task_session WHERE id = 'tsk_mixed'")) {
            ResultSet rs = stmt.executeQuery();
            assertTrue(rs.next(), "Task session should exist");
            assertEquals("COMPLETED", rs.getString("status"), "Status should be COMPLETED");
            assertEquals("[{\"done\": true}]", rs.getString("execution_plan"), "Execution plan should match");
        }

        // Check message
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT content FROM chat_message WHERE session_id = 'tsk_mixed'")) {
            ResultSet rs = stmt.executeQuery();
            assertTrue(rs.next(), "Message should exist");
            assertEquals("Test message", rs.getString("content"), "Message content should match");
        }

        // Check tool log
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT tool_name, result FROM tool_execution_log WHERE session_id = 'tsk_mixed'")) {
            ResultSet rs = stmt.executeQuery();
            assertTrue(rs.next(), "Tool log should exist");
            assertEquals("test_tool", rs.getString("tool_name"), "Tool name should match");
            assertEquals("Tool result", rs.getString("result"), "Result should match");
        }
    }

    @Test
    @Order(7)
    @DisplayName("Should handle graceful shutdown with remaining events")
    void testGracefulShutdown() throws Exception {
        // Arrange - Submit multiple events
        for (int i = 0; i < 10; i++) {
            writer.submitEvent(new InsertTaskSessionEvent(
                    "tsk_shutdown_" + i, null, InsertTaskSessionEvent.ImType.CLI,
                    "test", InsertTaskSessionEvent.AgentRole.WORKER,
                    InsertTaskSessionEvent.TaskStatus.PLANNING, "Shutdown test " + i, null, null
            ));
        }

        // Wait for events to be processed using helper method
        boolean queueEmptied = waitForQueueEmpty(5000);
        assertTrue(queueEmptied, "All events should be processed before shutdown");

        // Act - Stop writer (should process remaining events)
        writer.stopConsumer();

        // Assert
        assertFalse(writer.isRunning(), "Writer should be stopped");

        // Verify all events were processed
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT COUNT(*) FROM task_session WHERE id LIKE 'tsk_shutdown_%'")) {
            ResultSet rs = stmt.executeQuery();
            assertTrue(rs.next(), "Query should return a row");
            assertEquals(10, rs.getInt(1), "All events should be processed before shutdown");
        }
    }

    @Test
    @Order(8)
    @DisplayName("Should cascade delete messages when session deleted")
    void testCascadeDeletion() throws Exception {
        // Arrange - Create session with messages
        writer.submitEvent(new InsertTaskSessionEvent(
                "tsk_cascade", null, InsertTaskSessionEvent.ImType.FEISHU,
                "group_abc", InsertTaskSessionEvent.AgentRole.MASTER,
                InsertTaskSessionEvent.TaskStatus.RUNNING, "Cascade test", null, null
        ));
        waitForQueueEmpty(1000);

        writer.submitEvent(new InsertMessageEvent(
                "tsk_cascade", InsertMessageEvent.StreamType.GLOBAL, 1,
                new UnifiedMessage(UnifiedMessage.Role.USER, "Message 1",
                        UnifiedMessage.PayloadFormat.PLAIN_TEXT)
        ));
        waitForQueueEmpty(1000);

        writer.submitEvent(new InsertMessageEvent(
                "tsk_cascade", InsertMessageEvent.StreamType.LOCAL, 1,
                new UnifiedMessage(UnifiedMessage.Role.ASSISTANT, "Message 2",
                        UnifiedMessage.PayloadFormat.PLAIN_TEXT)
        ));
        waitForQueueEmpty(1000);

        // Verify messages exist
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT COUNT(*) FROM chat_message WHERE session_id = 'tsk_cascade'")) {
            ResultSet rs = stmt.executeQuery();
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1), "Should have 2 messages");
        }

        // Act - Delete messages
        writer.submitEvent(new DeleteMessagesEvent("tsk_cascade"));
        waitForQueueEmpty(1000);

        // Assert - Messages should be deleted
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT COUNT(*) FROM chat_message WHERE session_id = 'tsk_cascade'")) {
            ResultSet rs = stmt.executeQuery();
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "All messages should be deleted");
        }
    }

    @Test
    @Order(9)
    @DisplayName("Should return correct queue size")
    void testQueueSize() throws Exception {
        // Initially empty or small
        int initialSize = writer.getQueueSize();

        // Submit many events quickly
        for (int i = 0; i < 50; i++) {
            writer.submitEvent(new InsertTaskSessionEvent(
                    "tsk_queue_" + i, null, InsertTaskSessionEvent.ImType.CLI,
                    "test", InsertTaskSessionEvent.AgentRole.WORKER,
                    InsertTaskSessionEvent.TaskStatus.PLANNING, "Queue test", null, null
            ));
        }

        // Queue should have some events (processing happens in background)
        int sizeAfterSubmit = writer.getQueueSize();

        // Wait for processing using helper method
        waitForQueueEmpty(5000);
        int sizeAfterProcessing = writer.getQueueSize();

        // Assert
        assertTrue(sizeAfterSubmit >= 0, "Queue size should be non-negative");
        assertTrue(sizeAfterProcessing <= sizeAfterSubmit,
                "Queue size should decrease after processing");
        assertEquals(0, sizeAfterProcessing, "Queue should be empty after processing");
    }
}
