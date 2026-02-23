package tech.yesboss.persistence.event;

/**
 * Sealed interface for all database write events.
 *
 * <p>This interface strictly defines the permitted types of write operations
 * that can be performed on the database. By using a sealed interface,
 * the compiler enforces that only the permitted record types can implement
 * this interface, providing type safety and preventing unauthorized write operations.</p>
 *
 * <p>This design ensures that all database writes go through the single-threaded
 * writer engine ({@link tech.yesboss.persistence.db.SingleThreadDbWriter})
 * and prevents direct database access from business logic.</p>
 *
 * <h3>Permitted Event Types:</h3>
 * <ul>
 *   <li>{@link InsertTaskSessionEvent} - Create new task session (Master/Worker)</li>
 *   <li>{@link UpdateTaskStatusEvent} - Update task status and execution plan</li>
 *   <li>{@link InsertMessageEvent} - Append message to context stream</li>
 *   <li>{@link InsertToolExecutionLogEvent} - Record tool execution</li>
 *   <li>{@link DeleteMessagesEvent} - Cascade delete messages for a session</li>
 *   <li>{@link DeleteTaskSessionEvent} - Delete task session record</li>
 * </ul>
 *
 * @see tech.yesboss.persistence.db.SingleThreadDbWriter
 */
public sealed interface DbWriteEvent permits
        InsertTaskSessionEvent,
        UpdateTaskStatusEvent,
        InsertMessageEvent,
        InsertToolExecutionLogEvent,
        DeleteMessagesEvent,
        DeleteTaskSessionEvent {

    /**
     * Get the event type identifier for logging and routing purposes.
     *
     * @return the event type name
     */
    default String getEventType() {
        return this.getClass().getSimpleName();
    }
}
