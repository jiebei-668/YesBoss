package tech.yesboss.persistence.repository;

import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.persistence.event.InsertMessageEvent;
import tech.yesboss.persistence.event.InsertMessageEvent.StreamType;

import java.util.List;

/**
 * Repository interface for chat message operations.
 *
 * <p>This repository provides two types of operations:
 * <ul>
 *   <li><b>Read operations:</b> Direct SQL queries for high-concurrency access</li>
 *   <li><b>Write operations:</b> Asynchronous event submission to the SingleThreadDbWriter</li>
 * </ul>
 *
 * <p>Read operations use the {@code idx_chat_msg_seq} index for optimal performance
 * and return messages strictly ordered by {@code sequence_num} to prevent LLM hallucination.</p>
 */
public interface ChatMessageRepository {

    /**
     * Fetch the complete context for a specific session and stream type.
     *
     * <p>This method is used for LLM prompt assembly. It retrieves all messages
     * in the specified stream (GLOBAL or LOCAL) ordered by sequence_num.
     * The strict ordering is critical to prevent out-of-context LLM responses.</p>
     *
     * @param sessionId  The session ID to fetch messages for
     * @param streamType The stream type (GLOBAL or LOCAL)
     * @return List of UnifiedMessage objects ordered by sequence_num (ascending)
     */
    List<UnifiedMessage> fetchContext(String sessionId, StreamType streamType);

    /**
     * Asynchronously save a single message record.
     *
     * <p>This method creates an InsertMessageEvent and submits it to the
     * SingleThreadDbWriter queue. The actual database write happens
     * asynchronously in the single consumer thread.</p>
     *
     * <p>Called by GlobalStreamManager and LocalStreamManager.</p>
     *
     * @param sessionId   The session ID this message belongs to
     * @param streamType  The stream type (GLOBAL or LOCAL)
     * @param sequenceNum The monotonically increasing sequence number
     * @param message     The UnifiedMessage to persist
     */
    void saveMessage(String sessionId, StreamType streamType, int sequenceNum, UnifiedMessage message);

    /**
     * Delete all messages for a specific session.
     *
     * <p>This creates a DeleteMessagesEvent and submits it to the queue.
     * Used for cascade deletion when a session is destroyed.</p>
     *
     * @param sessionId The session ID whose messages should be deleted
     */
    void deleteBySession(String sessionId);

    /**
     * Get the current sequence number for a session/stream.
     *
     * <p>This is useful for determining the next sequence number when
     * appending new messages.</p>
     *
     * @param sessionId  The session ID
     * @param streamType The stream type
     * @return The highest sequence number, or 0 if no messages exist
     */
    int getCurrentSequenceNumber(String sessionId, StreamType streamType);
}
