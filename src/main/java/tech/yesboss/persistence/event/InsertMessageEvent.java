package tech.yesboss.persistence.event;

import tech.yesboss.domain.message.UnifiedMessage;

/**
 * Event for inserting a message into the context stream.
 *
 * <p>This event is fired when any message (human, agent, system, or tool result)
 * needs to be persisted to the chat_message table. The message is stored in the
 * appropriate stream (GLOBAL or LOCAL) for the given session.</p>
 *
 * @param sessionId     The session ID this message belongs to
 * @param streamType    The stream type (GLOBAL or LOCAL)
 * @param sequenceNum   Monotonically increasing sequence number
 * @param message       The UnifiedMessage to persist
 * @param createdAt     Creation timestamp in milliseconds
 */
public record InsertMessageEvent(
        String sessionId,
        StreamType streamType,
        int sequenceNum,
        UnifiedMessage message,
        long createdAt
) implements DbWriteEvent {

    /**
     * Constructor with default current timestamp.
     */
    public InsertMessageEvent {
        if (createdAt == 0) {
            createdAt = java.time.Instant.now().toEpochMilli();
        }
    }

    /**
     * Constructor without timestamp (uses current time).
     */
    public InsertMessageEvent(
            String sessionId,
            StreamType streamType,
            int sequenceNum,
            UnifiedMessage message
    ) {
        this(sessionId, streamType, sequenceNum, message,
                java.time.Instant.now().toEpochMilli());
    }

    /**
     * Enum for context stream types.
     *
     * <p>GLOBAL: Shared context between Master and Workers (human conversation, high-level summaries)</p>
     * <p>LOCAL: Isolated context for individual Worker (detailed execution, tool calls, errors)</p>
     */
    public enum StreamType {
        GLOBAL, LOCAL
    }
}
