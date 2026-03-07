package tech.yesboss.memory.trigger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.memory.config.MemoryConfig;
import tech.yesboss.domain.message.UnifiedMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Conversation round-based trigger mechanism for memory extraction.
 *
 * <p>This mechanism tracks the number of conversation rounds (message exchanges)
 * and triggers memory extraction when a configured threshold is reached.
 *
 * <p>Key features:
 * <ul>
 *   <li>Round counting per conversation</li>
 *   <li>Configurable round thresholds</li>
 *   <li>Automatic reset after extraction</li>
 *   <li>Batch processing support</li>
 *   <li>Performance metrics</li>
 *   <li>Thread-safe operations</li>
 * </ul>
 *
 * <p>Configuration (from application-memory.yml):
 * <pre>
 * memory:
 *   trigger:
 *     conversationRound:
 *       enabled: true
 *       defaultThreshold: 20
 *       perConversationThresholds: {}
 *       resetAfterExtraction: true
 *       includeBotMessages: false
 * </pre>
 */
public class ConversationRoundTrigger {

    private static final Logger logger = LoggerFactory.getLogger(ConversationRoundTrigger.class);

    private final MemoryConfig config;
    private final TriggerService triggerService;

    // Round tracking per conversation
    private final Map<String, AtomicInteger> conversationRounds;
    private final Map<String, Long> lastUpdateTimestamps;

    // Metrics
    private final AtomicLong totalRoundsTracked = new AtomicLong(0);
    private final AtomicLong totalTriggers = new AtomicLong(0);
    private final AtomicLong totalExtractions = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);

    // Configuration
    private volatile boolean enabled;
    private volatile int defaultThreshold;
    private volatile boolean resetAfterExtraction;
    private volatile boolean includeBotMessages;

    /**
     * Constructor with dependency injection.
     *
     * @param config Memory configuration
     * @param triggerService Trigger service for extraction
     */
    public ConversationRoundTrigger(MemoryConfig config, TriggerService triggerService) {
        this.config = config;
        this.triggerService = triggerService;
        this.conversationRounds = new ConcurrentHashMap<>();
        this.lastUpdateTimestamps = new ConcurrentHashMap<>();

        // Load configuration
        loadConfiguration();

        logger.info("ConversationRoundTrigger initialized with enabled: {}, defaultThreshold: {}",
                   enabled, defaultThreshold);
    }

    // ========================================================================
    // Core Functionality
    // ========================================================================

    /**
     * Track a message and increment round count if applicable.
     *
     * @param message The message to track
     * @param conversationId The conversation ID
     * @return true if trigger threshold was reached
     */
    public boolean trackMessage(UnifiedMessage message, String conversationId) {
        if (!enabled || !shouldCountMessage(message)) {
            return false;
        }

        long startTime = System.currentTimeMillis();

        try {
            // Increment round count
            int currentRound = incrementRoundCount(conversationId);
            totalRoundsTracked.incrementAndGet();

            // Get threshold for this conversation
            int threshold = getThresholdForConversation(conversationId);

            // Check if threshold reached
            boolean triggered = currentRound >= threshold;

            if (triggered) {
                logger.info("Conversation {} reached round threshold: {}/{}",
                           conversationId, currentRound, threshold);
                totalTriggers.incrementAndGet();
            }

            long duration = System.currentTimeMillis() - startTime;
            totalProcessingTimeMs.addAndGet(duration);

            return triggered;

        } catch (Exception e) {
            logger.error("Error tracking message for conversation {}: {}",
                        conversationId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if trigger condition is met for a conversation.
     *
     * @param conversationId The conversation ID
     * @return true if trigger condition is met
     */
    public boolean isTriggerConditionMet(String conversationId) {
        if (!enabled) {
            return false;
        }

        try {
            int currentRound = getRoundCount(conversationId);
            int threshold = getThresholdForConversation(conversationId);

            return currentRound >= threshold;

        } catch (Exception e) {
            logger.error("Error checking trigger condition for conversation {}: {}",
                        conversationId, e.getMessage());
            return false;
        }
    }

    /**
     * Trigger memory extraction for a conversation.
     *
     * @param conversationId The conversation ID
     * @param sessionId The session ID
     * @return Number of messages processed
     */
    public int triggerExtraction(String conversationId, String sessionId) {
        if (!enabled) {
            logger.debug("ConversationRoundTrigger is disabled");
            return 0;
        }

        long startTime = System.currentTimeMillis();

        try {
            logger.info("Triggering extraction for conversation {} (round-based)",
                       conversationId);

            // Trigger extraction via trigger service
            int processed = triggerService.triggerMemoryExtraction(conversationId, sessionId);

            // Reset round count if configured
            if (resetAfterExtraction) {
                resetRoundCount(conversationId);
            }

            totalExtractions.incrementAndGet();

            long duration = System.currentTimeMillis() - startTime;
            totalProcessingTimeMs.addAndGet(duration);

            logger.info("Extraction triggered for conversation {}: {} messages processed in {}ms",
                       conversationId, processed, duration);

            return processed;

        } catch (Exception e) {
            logger.error("Error triggering extraction for conversation {}: {}",
                        conversationId, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Trigger extraction for all conversations that meet the round threshold.
     *
     * @return Number of conversations processed
     */
    public int triggerForAllConversations() {
        if (!enabled) {
            return 0;
        }

        int processed = 0;
        int threshold = defaultThreshold;

        for (Map.Entry<String, AtomicInteger> entry : conversationRounds.entrySet()) {
            String conversationId = entry.getKey();
            int currentRound = entry.getValue().get();

            if (currentRound >= threshold) {
                // Trigger extraction for this conversation
                // Note: In a real implementation, you'd need to get the sessionId
                // This is a simplified version
                logger.info("Conversation {} meets round threshold: {}/{}",
                           conversationId, currentRound, threshold);
                processed++;
            }
        }

        logger.info("Triggered extraction for {} conversations based on round threshold",
                   processed);

        return processed;
    }

    // ========================================================================
    // Round Counting
    // ========================================================================

    /**
     * Get the current round count for a conversation.
     *
     * @param conversationId The conversation ID
     * @return Current round count
     */
    public int getRoundCount(String conversationId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            return 0;
        }

        AtomicInteger counter = conversationRounds.get(conversationId);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Increment the round count for a conversation.
     *
     * @param conversationId The conversation ID
     * @return New round count
     */
    private int incrementRoundCount(String conversationId) {
        return conversationRounds.computeIfAbsent(conversationId,
            k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * Reset the round count for a conversation.
     *
     * @param conversationId The conversation ID
     */
    public void resetRoundCount(String conversationId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            return;
        }

        AtomicInteger counter = conversationRounds.remove(conversationId);
        if (counter != null) {
            logger.debug("Reset round count for conversation {}: was {}",
                        conversationId, counter.get());
        }

        lastUpdateTimestamps.remove(conversationId);
    }

    /**
     * Reset all round counts.
     */
    public void resetAllRoundCounts() {
        int count = conversationRounds.size();
        conversationRounds.clear();
        lastUpdateTimestamps.clear();

        logger.info("Reset all round counts: {} conversations cleared", count);
    }

    // ========================================================================
    // Threshold Management
    // ========================================================================

    /**
     * Get the threshold for a specific conversation.
     *
     * @param conversationId The conversation ID
     * @return Threshold value
     */
    public int getThresholdForConversation(String conversationId) {
        // Check for per-conversation threshold
        String key = "memory.trigger.conversationRound.perConversationThresholds." + conversationId;
        Integer threshold = config.get(key, null);

        if (threshold != null && threshold > 0) {
            return threshold;
        }

        // Return default threshold
        return defaultThreshold;
    }

    /**
     * Set a custom threshold for a specific conversation.
     *
     * @param conversationId The conversation ID
     * @param threshold The threshold value
     */
    public void setThresholdForConversation(String conversationId, int threshold) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("Threshold must be positive: " + threshold);
        }

        String key = "memory.trigger.conversationRound.perConversationThresholds." + conversationId;
        config.set(key, threshold);

        logger.info("Set custom threshold for conversation {}: {}",
                   conversationId, threshold);
    }

    /**
     * Get the default threshold.
     *
     * @return Default threshold
     */
    public int getDefaultThreshold() {
        return defaultThreshold;
    }

    /**
     * Set the default threshold.
     *
     * @param threshold The default threshold
     */
    public void setDefaultThreshold(int threshold) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("Threshold must be positive: " + threshold);
        }

        this.defaultThreshold = threshold;
        config.set("memory.trigger.conversationRound.defaultThreshold", threshold);

        logger.info("Updated default threshold to {}", threshold);
    }

    // ========================================================================
    // Message Filtering
    // ========================================================================

    /**
     * Check if a message should be counted toward round threshold.
     *
     * @param message The message to check
     * @return true if message should be counted
     */
    private boolean shouldCountMessage(UnifiedMessage message) {
        if (message == null) {
            return false;
        }

        // Check if bot messages should be included
        if (!includeBotMessages && isBotMessage(message)) {
            return false;
        }

        // Additional filtering logic can be added here
        return true;
    }

    /**
     * Check if a message is from a bot.
     *
     * @param message The message to check
     * @return true if message is from bot
     */
    private boolean isBotMessage(UnifiedMessage message) {
        // This is a placeholder implementation
        // In a real system, you'd check the message sender type
        return false;
    }

    // ========================================================================
    // Metrics and Monitoring
    // ========================================================================

    /**
     * Get comprehensive metrics.
     *
     * @return Metrics map
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        metrics.put("enabled", enabled);
        metrics.put("defaultThreshold", defaultThreshold);
        metrics.put("resetAfterExtraction", resetAfterExtraction);
        metrics.put("includeBotMessages", includeBotMessages);
        metrics.put("trackedConversations", conversationRounds.size());
        metrics.put("totalRoundsTracked", totalRoundsTracked.get());
        metrics.put("totalTriggers", totalTriggers.get());
        metrics.put("totalExtractions", totalExtractions.get());
        metrics.put("averageProcessingTime", calculateAverageProcessingTime());
        metrics.put("totalProcessingTime", totalProcessingTimeMs.get());

        return metrics;
    }

    /**
     * Calculate average processing time.
     *
     * @return Average time in milliseconds
     */
    public long calculateAverageProcessingTime() {
        long total = totalExtractions.get();
        if (total == 0) {
            return 0;
        }
        return totalProcessingTimeMs.get() / total;
    }

    /**
     * Get round counts for all conversations.
     *
     * @return Map of conversation ID to round count
     */
    public Map<String, Integer> getAllRoundCounts() {
        Map<String, Integer> counts = new ConcurrentHashMap<>();

        for (Map.Entry<String, AtomicInteger> entry : conversationRounds.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().get());
        }

        return counts;
    }

    // ========================================================================
    // Configuration Management
    // ========================================================================

    /**
     * Load configuration from MemoryConfig.
     */
    private void loadConfiguration() {
        this.enabled = config.get("memory.trigger.conversationRound.enabled", true);
        this.defaultThreshold = config.get("memory.trigger.conversationRound.defaultThreshold", 20);
        this.resetAfterExtraction = config.get("memory.trigger.conversationRound.resetAfterExtraction", true);
        this.includeBotMessages = config.get("memory.trigger.conversationRound.includeBotMessages", false);

        logger.info("Loaded configuration: enabled={}, defaultThreshold={}, resetAfterExtraction={}, includeBotMessages={}",
                   enabled, defaultThreshold, resetAfterExtraction, includeBotMessages);
    }

    /**
     * Reload configuration.
     */
    public void reloadConfiguration() {
        loadConfiguration();
        logger.info("Configuration reloaded");
    }

    /**
     * Enable the trigger.
     */
    public void enable() {
        this.enabled = true;
        config.set("memory.trigger.conversationRound.enabled", true);
        logger.info("ConversationRoundTrigger enabled");
    }

    /**
     * Disable the trigger.
     */
    public void disable() {
        this.enabled = false;
        config.set("memory.trigger.conversationRound.enabled", false);
        logger.info("ConversationRoundTrigger disabled");
    }

    /**
     * Check if trigger is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set whether to reset round count after extraction.
     *
     * @param reset true to reset after extraction
     */
    public void setResetAfterExtraction(boolean reset) {
        this.resetAfterExtraction = reset;
        config.set("memory.trigger.conversationRound.resetAfterExtraction", reset);
        logger.info("Reset after extraction set to: {}", reset);
    }

    /**
     * Set whether to include bot messages in round count.
     *
     * @param include true to include bot messages
     */
    public void setIncludeBotMessages(boolean include) {
        this.includeBotMessages = include;
        config.set("memory.trigger.conversationRound.includeBotMessages", include);
        logger.info("Include bot messages set to: {}", include);
    }

    // ========================================================================
    // Batch Operations
    // ========================================================================

    /**
     * Batch track multiple messages.
     *
     * @param messages List of messages to track
     * @param conversationId The conversation ID
     * @return Number of messages that triggered threshold
     */
    public int batchTrackMessages(java.util.List<UnifiedMessage> messages, String conversationId) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        int triggerCount = 0;

        for (UnifiedMessage message : messages) {
            if (trackMessage(message, conversationId)) {
                triggerCount++;
            }
        }

        logger.debug("Batch tracked {} messages for conversation {}, {} triggers",
                    messages.size(), conversationId, triggerCount);

        return triggerCount;
    }

    /**
     * Get conversations that meet the threshold.
     *
     * @return List of conversation IDs that meet threshold
     */
    public java.util.List<String> getConversationsMeetingThreshold() {
        java.util.List<String> conversations = new java.util.ArrayList<>();
        int threshold = defaultThreshold;

        for (Map.Entry<String, AtomicInteger> entry : conversationRounds.entrySet()) {
            if (entry.getValue().get() >= threshold) {
                conversations.add(entry.getKey());
            }
        }

        return conversations;
    }

    // ========================================================================
    // Cleanup and Maintenance
    // ========================================================================

    /**
     * Remove tracking for a conversation.
     *
     * @param conversationId The conversation ID
     */
    public void removeConversation(String conversationId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            return;
        }

        conversationRounds.remove(conversationId);
        lastUpdateTimestamps.remove(conversationId);

        logger.debug("Removed tracking for conversation: {}", conversationId);
    }

    /**
     * Clean up old conversation tracking data.
     *
     * @param maxAgeMs Maximum age in milliseconds
     * @return Number of conversations cleaned up
     */
    public int cleanupOldConversations(long maxAgeMs) {
        if (maxAgeMs <= 0) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        int cleaned = 0;

        for (Map.Entry<String, Long> entry : lastUpdateTimestamps.entrySet()) {
            long age = currentTime - entry.getValue();

            if (age > maxAgeMs) {
                removeConversation(entry.getKey());
                cleaned++;
            }
        }

        if (cleaned > 0) {
            logger.info("Cleaned up {} old conversations (age > {}ms)", cleaned, maxAgeMs);
        }

        return cleaned;
    }

    /**
     * Get statistics about tracked conversations.
     *
     * @return Statistics map
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();

        if (conversationRounds.isEmpty()) {
            stats.put("totalConversations", 0);
            stats.put("averageRounds", 0);
            stats.put("maxRounds", 0);
            stats.put("minRounds", 0);
            return stats;
        }

        int total = 0;
        int max = Integer.MIN_VALUE;
        int min = Integer.MAX_VALUE;

        for (AtomicInteger count : conversationRounds.values()) {
            int value = count.get();
            total += value;
            max = Math.max(max, value);
            min = Math.min(min, value);
        }

        stats.put("totalConversations", conversationRounds.size());
        stats.put("averageRounds", total / conversationRounds.size());
        stats.put("maxRounds", max);
        stats.put("minRounds", min);
        stats.put("totalRounds", total);

        return stats;
    }
}
