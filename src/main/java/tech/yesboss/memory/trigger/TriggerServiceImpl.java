package tech.yesboss.memory.trigger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.persistence.repository.ChatMessageRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementation of TriggerService that monitors conversations and triggers memory extraction.
 *
 * <p>This implementation checks trigger conditions (interval and epoch max) and
 * coordinates with MemoryService to extract memories from conversations.</p>
 *
 * <p>Key features:
 * <ul>
 *   <li>Interval-based triggering (time since last extraction)</li>
 *   <li>EpochMax-based triggering (message count threshold)</li>
 *   <li>State tracking for last extraction times</li>
 *   <li>Batch operations for multiple conversations</li>
 *   <li>Thread-safe state management</li>
 * </ul>
 */
public class TriggerServiceImpl implements TriggerService {

    private static final Logger logger = LoggerFactory.getLogger(TriggerServiceImpl.class);

    private static final long DEFAULT_INTERVAL_MS = 300000; // 5 minutes
    private static final int DEFAULT_EPOCH_MAX = 20;

    private final ChatMessageRepository chatMessageRepository;
    private final MemoryService memoryService;
    private final ExecutorService executorService;
    private final Map<String, Long> lastExtractionTimestamps;
    private final boolean available;

    private long intervalTriggerMs = DEFAULT_INTERVAL_MS;
    private int epochMaxThreshold = DEFAULT_EPOCH_MAX;

    /**
     * Create a new TriggerServiceImpl with all dependencies.
     *
     * @param chatMessageRepository Repository for chat messages
     * @param memoryService Memory service for extraction
     */
    public TriggerServiceImpl(ChatMessageRepository chatMessageRepository,
                              MemoryService memoryService) {
        this.chatMessageRepository = chatMessageRepository;
        this.memoryService = memoryService;
        this.executorService = Executors.newFixedThreadPool(2);
        this.lastExtractionTimestamps = new ConcurrentHashMap<>();

        // Test availability
        this.available = testAvailability();

        logger.info("TriggerServiceImpl initialized with available: {}", this.available);
        logger.info("Default config: interval={}ms, epochMax={}", intervalTriggerMs, epochMaxThreshold);
    }

    @Override
    public long getIntervalTriggerMs() {
        return intervalTriggerMs;
    }

    @Override
    public void setIntervalTriggerMs(long intervalMs) {
        if (intervalMs <= 0) {
            throw new TriggerServiceException("Interval must be positive",
                    TriggerServiceException.ERROR_INVALID_INPUT);
        }
        this.intervalTriggerMs = intervalMs;
        logger.info("Updated interval trigger to {}ms", intervalMs);
    }

    @Override
    public int getEpochMaxThreshold() {
        return epochMaxThreshold;
    }

    @Override
    public void setEpochMaxThreshold(int threshold) {
        if (threshold <= 0) {
            throw new TriggerServiceException("Threshold must be positive",
                    TriggerServiceException.ERROR_INVALID_INPUT);
        }
        this.epochMaxThreshold = threshold;
        logger.info("Updated epoch max threshold to {}", threshold);
    }

    @Override
    public boolean checkTriggerConditions(String conversationId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            throw new TriggerServiceException("Conversation ID cannot be null or empty",
                    TriggerServiceException.ERROR_INVALID_INPUT);
        }

        try {
            boolean intervalMet = checkIntervalCondition(conversationId);
            boolean epochMaxMet = checkEpochMaxCondition(conversationId);

            boolean shouldTrigger = intervalMet || epochMaxMet;

            if (shouldTrigger) {
                logger.info("Trigger conditions met for conversation: {} (interval={}, epochMax={})",
                        conversationId, intervalMet, epochMaxMet);
            }

            return shouldTrigger;

        } catch (Exception e) {
            throw new TriggerServiceException("Failed to check trigger conditions: " + e.getMessage(),
                    TriggerServiceException.ERROR_TRIGGER_CHECK_FAILED, e);
        }
    }

    @Override
    public boolean checkIntervalCondition(String conversationId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            throw new TriggerServiceException("Conversation ID cannot be null or empty",
                    TriggerServiceException.ERROR_INVALID_INPUT);
        }

        try {
            long lastExtraction = getLastExtractionTimestamp(conversationId);
            long currentTime = System.currentTimeMillis();
            long timeSinceLastExtraction = currentTime - lastExtraction;

            return timeSinceLastExtraction >= intervalTriggerMs;

        } catch (Exception e) {
            logger.error("Failed to check interval condition for conversation {}: {}",
                    conversationId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean checkEpochMaxCondition(String conversationId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            throw new TriggerServiceException("Conversation ID cannot be null or empty",
                    TriggerServiceException.ERROR_INVALID_INPUT);
        }

        try {
            List<UnifiedMessage> unprocessedMessages = findMessagesByEpochMax(conversationId);
            return unprocessedMessages.size() >= epochMaxThreshold;

        } catch (Exception e) {
            logger.error("Failed to check epoch max condition for conversation {}: {}",
                    conversationId, e.getMessage());
            return false;
        }
    }

    @Override
    public List<UnifiedMessage> findUnprocessedMessages(String conversationId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            throw new TriggerServiceException("Conversation ID cannot be null or empty",
                    TriggerServiceException.ERROR_INVALID_INPUT);
        }

        try {
            // Check both conditions and return messages if either is met
            List<UnifiedMessage> intervalMessages = findMessagesByInterval(conversationId);
            List<UnifiedMessage> epochMaxMessages = findMessagesByEpochMax(conversationId);

            // Combine and deduplicate messages
            Set<String> seenIds = new HashSet<>();
            List<UnifiedMessage> allMessages = new ArrayList<>();

            for (UnifiedMessage message : intervalMessages) {
                if (seenIds.add(getMessageId(message))) {
                    allMessages.add(message);
                }
            }

            for (UnifiedMessage message : epochMaxMessages) {
                if (seenIds.add(getMessageId(message))) {
                    allMessages.add(message);
                }
            }

            logger.debug("Found {} unprocessed messages for conversation {}", allMessages.size(), conversationId);
            return allMessages;

        } catch (Exception e) {
            throw new TriggerServiceException("Failed to find unprocessed messages: " + e.getMessage(),
                    TriggerServiceException.ERROR_MESSAGE_DISCOVERY_FAILED, e);
        }
    }

    @Override
    public List<UnifiedMessage> findMessagesByInterval(String conversationId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            throw new TriggerServiceException("Conversation ID cannot be null or empty",
                    TriggerServiceException.ERROR_INVALID_INPUT);
        }

        try {
            // In a real implementation, this would query the database for messages
            // since the last extraction timestamp. For now, we'll return an empty list.
            // This would need to be implemented based on the actual message storage structure.

            logger.debug("Finding messages by interval for conversation {}", conversationId);
            return Collections.emptyList();

        } catch (Exception e) {
            throw new TriggerServiceException("Failed to find messages by interval: " + e.getMessage(),
                    TriggerServiceException.ERROR_MESSAGE_DISCOVERY_FAILED, e);
        }
    }

    @Override
    public List<UnifiedMessage> findMessagesByEpochMax(String conversationId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            throw new TriggerServiceException("Conversation ID cannot be null or empty",
                    TriggerServiceException.ERROR_INVALID_INPUT);
        }

        try {
            // In a real implementation, this would query the database for messages
            // up to the epoch max threshold. For now, we'll return an empty list.
            // This would need to be implemented based on the actual message storage structure.

            logger.debug("Finding messages by epoch max for conversation {}", conversationId);
            return Collections.emptyList();

        } catch (Exception e) {
            throw new TriggerServiceException("Failed to find messages by epoch max: " + e.getMessage(),
                    TriggerServiceException.ERROR_MESSAGE_DISCOVERY_FAILED, e);
        }
    }

    @Override
    public int triggerMemoryExtraction(String conversationId, String sessionId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            throw new TriggerServiceException("Conversation ID cannot be null or empty",
                    TriggerServiceException.ERROR_INVALID_INPUT);
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new TriggerServiceException("Session ID cannot be null or empty",
                    TriggerServiceException.ERROR_INVALID_INPUT);
        }

        try {
            // Find unprocessed messages
            List<UnifiedMessage> messages = findUnprocessedMessages(conversationId);

            if (messages.isEmpty()) {
                logger.debug("No unprocessed messages found for conversation {}", conversationId);
                return 0;
            }

            // Trigger extraction
            int processed = triggerMemoryExtractionForMessages(messages, conversationId, sessionId);

            // Update last extraction timestamp
            updateLastExtractionTimestamp(conversationId, System.currentTimeMillis());

            return processed;

        } catch (Exception e) {
            throw new TriggerServiceException("Failed to trigger memory extraction: " + e.getMessage(),
                    TriggerServiceException.ERROR_EXTRACTION_FAILED, e);
        }
    }

    @Override
    public int triggerMemoryExtractionForMessages(List<UnifiedMessage> messages,
                                                   String conversationId, String sessionId) {
        if (messages == null || messages.isEmpty()) {
            logger.debug("No messages to process");
            return 0;
        }
        if (conversationId == null || conversationId.trim().isEmpty()) {
            throw new TriggerServiceException("Conversation ID cannot be null or empty",
                    TriggerServiceException.ERROR_INVALID_INPUT);
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new TriggerServiceException("Session ID cannot be null or empty",
                    TriggerServiceException.ERROR_INVALID_INPUT);
        }

        try {
            logger.info("Triggering memory extraction for {} messages in conversation {}",
                    messages.size(), conversationId);

            // Trigger memory extraction via MemoryService
            memoryService.extractFromMessages(messages, conversationId, sessionId);

            // Mark messages as processed
            List<String> messageIds = new ArrayList<>();
            for (UnifiedMessage message : messages) {
                messageIds.add(getMessageId(message));
            }
            markMessagesAsProcessed(messageIds);

            logger.info("Successfully processed {} messages for conversation {}",
                    messages.size(), conversationId);

            return messages.size();

        } catch (Exception e) {
            throw new TriggerServiceException("Failed to extract memories from messages: " + e.getMessage(),
                    TriggerServiceException.ERROR_EXTRACTION_FAILED, e);
        }
    }

    @Override
    public int triggerForAllConversations() {
        try {
            // In a real implementation, this would:
            // 1. Get all active conversations
            // 2. Check trigger conditions for each
            // 3. Trigger extraction for those that meet conditions

            logger.info("Triggering extraction for all conversations");

            // For now, return 0 as a placeholder
            // This would need to be implemented based on the actual conversation storage structure

            return 0;

        } catch (Exception e) {
            throw new TriggerServiceException("Failed to trigger for all conversations: " + e.getMessage(),
                    TriggerServiceException.ERROR_BATCH_OPERATION_FAILED, e);
        }
    }

    @Override
    public void markMessagesAsProcessed(List<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }

        try {
            // In a real implementation, this would update the database
            // to mark messages as processed
            logger.debug("Marking {} messages as processed", messageIds.size());

        } catch (Exception e) {
            throw new TriggerServiceException("Failed to mark messages as processed: " + e.getMessage(),
                    TriggerServiceException.ERROR_STATE_UPDATE_FAILED, e);
        }
    }

    @Override
    public long getLastExtractionTimestamp(String conversationId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            return 0;
        }

        return lastExtractionTimestamps.getOrDefault(conversationId, 0L);
    }

    @Override
    public void updateLastExtractionTimestamp(String conversationId, long timestamp) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            throw new TriggerServiceException("Conversation ID cannot be null or empty",
                    TriggerServiceException.ERROR_INVALID_INPUT);
        }

        lastExtractionTimestamps.put(conversationId, timestamp);
        logger.debug("Updated last extraction timestamp for conversation {}: {}", conversationId, timestamp);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    /**
     * Shutdown the executor service.
     */
    public void shutdown() {
        executorService.shutdown();
        logger.info("TriggerServiceImpl shutdown complete");
    }

    // ========== Private Helper Methods ==========

    private boolean testAvailability() {
        try {
            return chatMessageRepository != null &&
                    memoryService != null &&
                    memoryService.isAvailable();
        } catch (Exception e) {
            logger.warn("TriggerService availability test failed: {}", e.getMessage());
            return false;
        }
    }

    private String getMessageId(UnifiedMessage message) {
        // Generate a unique ID for the message
        // In a real implementation, this would come from the message itself
        return String.valueOf(message.hashCode());
    }
}
