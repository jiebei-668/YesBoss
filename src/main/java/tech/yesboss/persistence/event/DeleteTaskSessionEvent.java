package tech.yesboss.persistence.event;

/**
 * Event for deleting a task session record.
 *
 * <p>This event is fired when a user deletes a group chat or when
 * performing cascade deletion of a Master session (which includes
 * all its Worker child sessions).</p>
 *
 * <p>Due to foreign key constraints with ON DELETE CASCADE, this
 * operation will cascade to delete:</p>
 * <ul>
 *   <li>All chat_message records for this session</li>
 *   <li>All tool_execution_log records for this session</li>
 * </ul>
 *
 * <p><strong>Important:</strong> When deleting a Master session, all
 * Worker child sessions should be deleted first via individual
 * DeleteTaskSessionEvent instances to maintain referential integrity.</p>
 *
 * @param sessionId    The session ID to delete
 */
public record DeleteTaskSessionEvent(
        String sessionId
) implements DbWriteEvent {
}
