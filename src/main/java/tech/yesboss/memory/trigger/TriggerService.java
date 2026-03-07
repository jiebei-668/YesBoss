package tech.yesboss.memory.trigger;

import java.util.List;

/**
 * TriggerService - Service for triggering memory extraction based on conditions.
 *
 * <p>This service monitors conversations and triggers memory extraction when
 * certain conditions are met, such as time intervals or message count thresholds.</p>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Check trigger conditions (interval and epoch max)</li>
 *   <li>Find unprocessed messages</li>
 *   <li>Trigger memory extraction via MemoryService</li>
 *   <li>Track processing state</li>
 * </ul>
 *
 * <p>Trigger conditions:
 * <ul>
 *   <li><b>Interval Trigger:</b> Trigger after a specified time interval</li>
 *   <li><b>EpochMax Trigger:</b> Trigger when conversation reaches message count threshold</li>
 * </ul>
 */
public interface TriggerService {

    // ==================== Configuration ====================

    /**
     * Get the configured interval trigger time in milliseconds.
     *
     * @return Interval time in milliseconds
     */
    long getIntervalTriggerMs();

    /**
     * Set the interval trigger time.
     *
     * @param intervalMs Interval time in milliseconds
     */
    void setIntervalTriggerMs(long intervalMs);

    /**
     * Get the configured epoch max threshold.
     *
     * @return Maximum number of messages before triggering
     */
    int getEpochMaxThreshold();

    /**
     * Set the epoch max threshold.
     *
     * @param threshold Maximum number of messages before triggering
     */
    void setEpochMaxThreshold(int threshold);

    // ==================== Trigger Conditions ====================

    /**
     * Check all trigger conditions.
     *
     * <p>This method checks both interval and epoch max conditions
     * and returns true if either condition is met.</p>
     *
     * @param conversationId Conversation ID to check
     * @return true if any trigger condition is met, false otherwise
     */
    boolean checkTriggerConditions(String conversationId);

    /**
     * Check interval trigger condition.
     *
     * <p>Returns true if the time since last extraction exceeds the configured interval.</p>
     *
     * @param conversationId Conversation ID to check
     * @return true if interval condition is met, false otherwise
     */
    boolean checkIntervalCondition(String conversationId);

    /**
     * Check epoch max trigger condition.
     *
     * <p>Returns true if the number of unprocessed messages exceeds the threshold.</p>
     *
     * @param conversationId Conversation ID to check
     * @return true if epoch max condition is met, false otherwise
     */
    boolean checkEpochMaxCondition(String conversationId);

    // ==================== Message Discovery ====================

    /**
     * Find messages that need to be processed.
     *
     * <p>This method retrieves messages that haven't been processed for memory extraction yet,
     * based on the configured trigger conditions.</p>
     *
     * @param conversationId Conversation ID
     * @return List of unprocessed messages
     */
    List<tech.yesboss.domain.message.UnifiedMessage> findUnprocessedMessages(String conversationId);

    /**
     * Find messages based on interval trigger.
     *
     * @param conversationId Conversation ID
     * @return List of messages since last extraction
     */
    List<tech.yesboss.domain.message.UnifiedMessage> findMessagesByInterval(String conversationId);

    /**
     * Find messages based on epoch max trigger.
     *
     * @param conversationId Conversation ID
     * @return List of messages up to the threshold
     */
    List<tech.yesboss.domain.message.UnifiedMessage> findMessagesByEpochMax(String conversationId);

    // ==================== Trigger Execution ====================

    /**
     * Execute memory extraction for a conversation.
     *
     * <p>This method finds unprocessed messages and triggers memory extraction.</p>
     *
     * @param conversationId Conversation ID
     * @param sessionId Session ID
     * @return Number of messages processed
     * @throws TriggerServiceException if extraction fails
     */
    int triggerMemoryExtraction(String conversationId, String sessionId);

    /**
     * Execute memory extraction for specific messages.
     *
     * @param messages Messages to process
     * @param conversationId Conversation ID
     * @param sessionId Session ID
     * @return Number of messages processed
     * @throws TriggerServiceException if extraction fails
     */
    int triggerMemoryExtractionForMessages(List<tech.yesboss.domain.message.UnifiedMessage> messages,
                                           String conversationId, String sessionId);

    // ==================== Batch Operations ====================

    /**
     * Check and trigger extraction for all active conversations.
     *
     * <p>This method iterates through all conversations and triggers extraction
     * for those that meet the trigger conditions.</p>
     *
     * @return Number of conversations processed
     * @throws TriggerServiceException if batch processing fails
     */
    int triggerForAllConversations();

    // ==================== State Management ====================

    /**
     * Mark messages as processed.
     *
     * @param messageIds List of message IDs to mark as processed
     */
    void markMessagesAsProcessed(List<String> messageIds);

    /**
     * Get the last extraction timestamp for a conversation.
     *
     * @param conversationId Conversation ID
     * @return Timestamp of last extraction, or 0 if never extracted
     */
    long getLastExtractionTimestamp(String conversationId);

    /**
     * Update the last extraction timestamp.
     *
     * @param conversationId Conversation ID
     * @param timestamp New timestamp
     */
    void updateLastExtractionTimestamp(String conversationId, long timestamp);

    // ==================== Availability ====================

    /**
     * Check if the TriggerService is available and operational.
     *
     * @return true if available, false otherwise
     */
    boolean isAvailable();
}
