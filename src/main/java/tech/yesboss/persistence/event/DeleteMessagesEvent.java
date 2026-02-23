package tech.yesboss.persistence.event;

/**
 * Event for deleting all messages associated with a session.
 *
 * <p>This event is fired as part of the cascade deletion process when
 * a user deletes a group chat or when a task session is being cleaned up.
 * It physically removes all chat_message records for the given session ID.</p>
 *
 * <p>Due to the foreign key constraint with ON DELETE CASCADE, deleting
 * messages will also cascade to delete related tool_execution_log records.</p>
 *
 * @param sessionId    The session ID whose messages should be deleted
 */
public record DeleteMessagesEvent(
        String sessionId
) implements DbWriteEvent {
}
