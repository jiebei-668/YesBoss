package tech.yesboss.persistence.event;

/**
 * Event for updating task session status.
 *
 * <p>This event is fired when the state machine transitions a task to a new status.
 * It can also optionally update the execution plan JSON for Master agents.</p>
 *
 * @param sessionId      The session ID to update
 * @param newStatus      The new status value
 * @param planJson       Optional updated execution plan (Master only)
 * @param updatedAt      Update timestamp in milliseconds
 */
public record UpdateTaskStatusEvent(
        String sessionId,
        InsertTaskSessionEvent.TaskStatus newStatus,
        String planJson,
        long updatedAt
) implements DbWriteEvent {

    /**
     * Constructor with default current timestamp.
     */
    public UpdateTaskStatusEvent {
        if (updatedAt == 0) {
            updatedAt = java.time.Instant.now().toEpochMilli();
        }
    }

    /**
     * Constructor without timestamp (uses current time).
     */
    public UpdateTaskStatusEvent(
            String sessionId,
            InsertTaskSessionEvent.TaskStatus newStatus,
            String planJson
    ) {
        this(sessionId, newStatus, planJson, java.time.Instant.now().toEpochMilli());
    }

    /**
     * Constructor for status-only update (no plan change).
     */
    public UpdateTaskStatusEvent(
            String sessionId,
            InsertTaskSessionEvent.TaskStatus newStatus
    ) {
        this(sessionId, newStatus, null, java.time.Instant.now().toEpochMilli());
    }
}
