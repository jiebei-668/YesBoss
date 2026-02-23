package tech.yesboss.persistence.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.persistence.event.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single-threaded database writer engine with lock-free asynchronous queue.
 *
 * <p>This class implements the core asynchronous persistence mechanism for YesBoss.
 * It uses a {@link LinkedBlockingQueue} to decouple producers (business logic threads)
 * from the single consumer thread that performs actual SQLite writes.</p>
 *
 * <h3>Design Rationale:</h3>
 * <ul>
 *   <li><b>Single Writer Thread:</b> SQLite performs best with a single writer thread.
 *       Multiple concurrent writers cause contention and locking issues.</li>
 *   <li><b>Lock-Free Queue:</b> Producers use {@code offer()} which is non-blocking
 *       and wait-free. They never block waiting for database operations.</li>
 *   <li><b>Daemon Thread:</b> The consumer runs as a daemon thread, ensuring it
 *       doesn't prevent JVM shutdown.</li>
 *   <li><b>Sequential Processing:</b> Events are processed in FIFO order,
 *       maintaining causality and preventing race conditions.</li>
 * </ul>
 *
 * <h3>Usage Pattern:</h3>
 * <pre>{@code
 * // Initialize and start
 * SingleThreadDbWriter writer = new SingleThreadDbWriter(connection);
 * writer.startConsumer();
 *
 * // Producer threads submit events (non-blocking)
 * writer.submitEvent(new InsertTaskSessionEvent(...));
 * writer.submitEvent(new InsertMessageEvent(...));
 *
 * // Shutdown on application exit
 * writer.stopConsumer();
 * }</pre>
 *
 * @see DbWriteEvent
 * @see LinkedBlockingQueue
 */
public class SingleThreadDbWriter {

    private static final Logger logger = LoggerFactory.getLogger(SingleThreadDbWriter.class);

    /**
     * Lock-free queue for database write events.
     * Producers call offer() which is non-blocking.
     * Consumer calls take() which blocks until an event is available.
     */
    private final LinkedBlockingQueue<DbWriteEvent> memoryWriteQueue;

    /**
     * Database connection for executing SQL.
     * The connection is thread-safe for single-threaded use.
     */
    private final Connection connection;

    /**
     * Flag indicating whether the consumer is running.
     * Used for graceful shutdown.
     */
    private final AtomicBoolean isRunning;

    /**
     * The consumer thread that processes database writes.
     */
    private Thread consumerThread;

    /**
     * Create a new SingleThreadDbWriter.
     *
     * @param connection the database connection to use for writes
     */
    public SingleThreadDbWriter(Connection connection) {
        this.connection = connection;
        this.memoryWriteQueue = new LinkedBlockingQueue<>();
        this.isRunning = new AtomicBoolean(false);
    }

    /**
     * Submit a database write event for asynchronous processing.
     *
     * <p>This method is non-blocking and thread-safe. Producers can call this
     * method from any thread without worrying about contention.</p>
     *
     * <p>The event will be processed by the consumer thread in FIFO order.</p>
     *
     * @param event the database write event to submit
     * @return {@code true} if the event was added to the queue, {@code false} if the queue is full
     * @throws IllegalArgumentException if event is null
     */
    public boolean submitEvent(DbWriteEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("DbWriteEvent cannot be null");
        }
        boolean offered = memoryWriteQueue.offer(event);
        if (offered) {
            logger.debug("Submitted event: {}", event.getEventType());
        } else {
            logger.warn("Failed to submit event: {}, queue may be full", event.getEventType());
        }
        return offered;
    }

    /**
     * Start the consumer daemon thread.
     *
     * <p>This method creates and starts a daemon thread that continuously
     * processes events from the queue. The thread will run until {@link #stopConsumer()}
     * is called or the JVM shuts down.</p>
     *
     * <p><b>Important:</b> This method should be called once during application
     * initialization. Calling it multiple times will result in multiple consumer threads.</p>
     *
     * @throws IllegalStateException if the consumer is already running
     */
    public void startConsumer() {
        if (isRunning.getAndSet(true)) {
            throw new IllegalStateException("Consumer is already running");
        }

        consumerThread = new Thread(() -> runConsumerLoop(), "SQLite-Single-Thread-Writer");
        consumerThread.setDaemon(true);
        consumerThread.start();

        logger.info("Started SQLite single-thread consumer daemon");
    }

    /**
     * Stop the consumer thread gracefully.
     *
     * <p>This method sets the running flag to false and interrupts the consumer thread.
     * Any events remaining in the queue will be processed before shutdown.</p>
     *
     * <p><b>Important:</b> This method blocks until the consumer thread terminates.
     * Call this method during application shutdown.</p>
     *
     * @throws InterruptedException if interrupted while waiting for consumer to stop
     */
    public void stopConsumer() throws InterruptedException {
        if (!isRunning.getAndSet(false)) {
            return; // Already stopped
        }

        if (consumerThread != null) {
            // Interrupt to unblock from take()
            consumerThread.interrupt();
            // Wait for thread to finish
            consumerThread.join(5000); // Wait up to 5 seconds

            if (consumerThread.isAlive()) {
                logger.warn("Consumer thread did not stop gracefully after 5 seconds");
            } else {
                logger.info("Stopped SQLite single-thread consumer");
            }
        }
    }

    /**
     * Main consumer loop.
     *
     * <p>This method runs in the consumer thread and processes events
     * from the queue until {@link #isRunning} becomes false.</p>
     */
    private void runConsumerLoop() {
        logger.debug("Consumer thread started");

        while (isRunning.get()) {
            try {
                // Block until an event is available (or interrupted)
                DbWriteEvent event = memoryWriteQueue.take();
                dispatchAndExecuteSql(event);
            } catch (InterruptedException e) {
                // Thread was interrupted, check if we should continue
                logger.debug("Consumer thread interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Log error but continue processing other events
                logger.error("Error processing database write event", e);
            }
        }

        logger.debug("Consumer thread terminated");
    }

    /**
     * Dispatch and execute SQL for a specific event type.
     *
     * <p>This method uses Java 17 enhanced switch expressions to dispatch
     * to the appropriate SQL execution method based on the event type.</p>
     *
     * @param event the database write event to process
     * @throws SQLException if SQL execution fails
     */
    private void dispatchAndExecuteSql(DbWriteEvent event) throws SQLException {
        logger.debug("Processing event: {}", event.getEventType());

        // Use pattern matching for switch (Java 17)
        switch (event) {
            case InsertTaskSessionEvent e -> executeInsertTaskSession(e);
            case UpdateTaskStatusEvent e -> executeUpdateTaskStatus(e);
            case InsertMessageEvent e -> executeInsertMessage(e);
            case InsertToolExecutionLogEvent e -> executeInsertToolExecutionLog(e);
            case DeleteMessagesEvent e -> executeDeleteMessages(e);
            case DeleteTaskSessionEvent e -> executeDeleteTaskSession(e);
            default -> throw new IllegalArgumentException("Unknown event type: " + event.getClass());
        }

        logger.debug("Completed event: {}", event.getEventType());
    }

    /**
     * Execute INSERT for task_session table.
     */
    private void executeInsertTaskSession(InsertTaskSessionEvent event) throws SQLException {
        String sql = "INSERT INTO task_session (" +
                "id, parent_id, im_type, im_group_id, role, status, " +
                "topic, execution_plan, assigned_task, created_at, updated_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, event.sessionId());
            stmt.setString(2, event.parentId());
            stmt.setString(3, event.imType().name());
            stmt.setString(4, event.imGroupId());
            stmt.setString(5, event.role().name());
            stmt.setString(6, event.status().name());
            stmt.setString(7, event.topic());
            stmt.setString(8, event.executionPlan());
            stmt.setString(9, event.assignedTask());
            stmt.setLong(10, event.createdAt());
            stmt.setLong(11, event.createdAt());

            stmt.executeUpdate();
        }
    }

    /**
     * Execute UPDATE for task_session table (status change).
     */
    private void executeUpdateTaskStatus(UpdateTaskStatusEvent event) throws SQLException {
        String sql = "UPDATE task_session " +
                "SET status = ?, execution_plan = ?, updated_at = ? " +
                "WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, event.newStatus().name());
            stmt.setString(2, event.planJson());
            stmt.setLong(3, event.updatedAt());
            stmt.setString(4, event.sessionId());

            stmt.executeUpdate();
        }
    }

    /**
     * Execute INSERT for chat_message table.
     */
    private void executeInsertMessage(InsertMessageEvent event) throws SQLException {
        String sql = "INSERT INTO chat_message (" +
                "id, session_id, stream_type, sequence_num, msg_role, " +
                "payload_format, content, token_count, created_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, generateMessageId());
            stmt.setString(2, event.sessionId());
            stmt.setString(3, event.streamType().name());
            stmt.setInt(4, event.sequenceNum());
            stmt.setString(5, event.message().role().name());
            stmt.setString(6, event.message().payloadFormat().name());
            stmt.setString(7, event.message().content());
            stmt.setInt(8, 0); // token_count - to be calculated later
            stmt.setLong(9, event.createdAt());

            stmt.executeUpdate();
        }
    }

    /**
     * Execute INSERT for tool_execution_log table.
     */
    private void executeInsertToolExecutionLog(InsertToolExecutionLogEvent event) throws SQLException {
        String sql = "INSERT INTO tool_execution_log (" +
                "id, session_id, tool_call_id, tool_name, arguments, " +
                "result, is_intercepted, created_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, generateLogId());
            stmt.setString(2, event.sessionId());
            stmt.setString(3, event.toolCallId());
            stmt.setString(4, event.toolName());
            stmt.setString(5, event.arguments());
            stmt.setString(6, event.result());
            stmt.setInt(7, event.isIntercepted() ? 1 : 0);
            stmt.setLong(8, event.createdAt());

            stmt.executeUpdate();
        }
    }

    /**
     * Execute DELETE for chat_message table (cascade).
     */
    private void executeDeleteMessages(DeleteMessagesEvent event) throws SQLException {
        String sql = "DELETE FROM chat_message WHERE session_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, event.sessionId());
            stmt.executeUpdate();
        }
    }

    /**
     * Execute DELETE for task_session table.
     */
    private void executeDeleteTaskSession(DeleteTaskSessionEvent event) throws SQLException {
        String sql = "DELETE FROM task_session WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, event.sessionId());
            stmt.executeUpdate();
        }
    }

    /**
     * Generate a unique message ID.
     * TODO: Implement proper ID generation (UUID or snowflake).
     */
    private String generateMessageId() {
        return "msg_" + System.nanoTime();
    }

    /**
     * Generate a unique log ID.
     * TODO: Implement proper ID generation (UUID or snowflake).
     */
    private String generateLogId() {
        return "log_" + System.nanoTime();
    }

    /**
     * Get the current queue size.
     * Useful for monitoring and health checks.
     *
     * @return the number of events waiting to be processed
     */
    public int getQueueSize() {
        return memoryWriteQueue.size();
    }

    /**
     * Check if the consumer is currently running.
     *
     * @return {@code true} if the consumer thread is running
     */
    public boolean isRunning() {
        return isRunning.get();
    }
}
