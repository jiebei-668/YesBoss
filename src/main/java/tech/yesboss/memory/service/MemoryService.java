package tech.yesboss.memory.service;

import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.memory.processor.ConversationSegment;
import tech.yesboss.memory.model.Preference;
import tech.yesboss.memory.model.Resource;
import tech.yesboss.memory.model.Snippet;

import java.util.List;

/**
 * MemoryService - Core memory extraction and management service.
 *
 * <p>This service orchestrates the memory extraction pipeline, coordinating between
 * ContentProcessor, MemoryManager, and repositories to extract, store, and manage
 * conversational memories.</p>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Extract memories from conversation messages</li>
 *   <li>Coordinate conversation segmentation</li>
 *   <li>Generate abstracts for segments</li>
 *   <li>Extract structured memories (snippets)</li>
 *   <li>Associate snippets with preferences</li>
 *   <li>Update preference summaries</li>
 * </ul>
 *
 * <p>This service acts as the orchestration layer in the memory management system,
 * sitting above MemoryManager and ContentProcessor.</p>
 */
public interface MemoryService {

    // ==================== Core Memory Extraction ====================

    /**
     * Extract memories from a list of conversation messages.
     *
     * <p>This is the main entry point for memory extraction. It processes
     * the messages through the following pipeline:
     * <ol>
     *   <li>Concatenate conversation content</li>
     *   <li>Segment conversation by topics</li>
     *   <li>Generate abstracts for each segment</li>
     *   <li>Create and save resources</li>
     *   <li>Extract structured memories</li>
     *   <li>Associate with preferences</li>
     *   <li>Update preference summaries</li>
     * </ol>
     *
     * @param messages List of conversation messages to process
     * @param conversationId Unique identifier for the conversation
     * @param sessionId Unique identifier for the session
     * @return List of created resources
     * @throws MemoryServiceException if extraction fails
     */
    List<Resource> extractFromMessages(List<UnifiedMessage> messages, String conversationId, String sessionId);

    // ==================== Conversation Segmentation ====================

    /**
     * Concatenate conversation content from messages.
     *
     * @param messages List of conversation messages
     * @return Concatenated conversation content
     */
    String concatenateConversationContent(List<UnifiedMessage> messages);

    /**
     * Segment conversation into topic-based segments.
     *
     * @param conversationContent Full conversation content
     * @return List of conversation segments
     * @throws MemoryServiceException if segmentation fails
     */
    List<ConversationSegment> segmentConversation(String conversationContent);

    // ==================== Resource Creation ====================

    /**
     * Generate abstract for a conversation segment.
     *
     * @param segmentContent Content of the segment
     * @return Generated abstract (1-2 sentence summary)
     * @throws MemoryServiceException if abstract generation fails
     */
    String generateSegmentAbstract(String segmentContent);

    /**
     * Build a Resource object from segment data.
     *
     * @param conversationId Conversation identifier
     * @param sessionId Session identifier
     * @param segmentContent Segment content
     * @param abstractText Generated abstract
     * @return Resource object
     */
    Resource buildResource(String conversationId, String sessionId, String segmentContent, String abstractText);

    // ==================== Structured Memory Extraction ====================

    /**
     * Extract structured memories from resource content.
     *
     * @param resourceContent Content of the resource
     * @return List of extracted snippets
     * @throws MemoryServiceException if extraction fails
     */
    List<Snippet> extractStructuredMemories(String resourceContent);

    /**
     * Extract structured memories for a specific memory type.
     *
     * @param resourceContent Content of the resource
     * @param memoryType Type of memory to extract
     * @return List of extracted snippets
     * @throws MemoryServiceException if extraction fails
     */
    List<String> extractMemoriesByType(String resourceContent, Snippet.MemoryType memoryType);

    // ==================== Preference Association ====================

    /**
     * Associate snippets with preferences.
     *
     * <p>This method analyzes snippets and finds or creates appropriate
     * preferences to associate them with.</p>
     *
     * @param snippets List of snippets to associate
     * @return Map of preference IDs to lists of associated snippets
     * @throws MemoryServiceException if association fails
     */
    java.util.Map<String, List<Snippet>> associateWithPreferences(List<Snippet> snippets);

    /**
     * Update preference summary with new snippets.
     *
     * @param preferenceId Preference identifier
     * @param snippets New snippets to merge into the summary
     * @throws MemoryServiceException if update fails
     */
    void updatePreferenceSummary(String preferenceId, List<Snippet> snippets);

    /**
     * Find or create preference for a snippet.
     *
     * @param snippet Snippet to find preference for
     * @return Associated preference
     * @throws MemoryServiceException if operation fails
     */
    Preference findOrCreatePreferenceForSnippet(Snippet snippet);

    // ==================== Batch Operations ====================

    /**
     * Batch extract memories from multiple conversations.
     *
     * @param messageBatches List of message batches, each with conversation ID and session ID
     * @return List of all created resources
     * @throws MemoryServiceException if batch extraction fails
     */
    List<Resource> batchExtractFromMessages(List<MessageBatch> messageBatches);

    /**
     * Process pending resources that need structured memory extraction.
     *
     * <p>This method finds resources without snippets and processes them
     * to extract structured memories.</p>
     *
     * @return Number of resources processed
     * @throws MemoryServiceException if processing fails
     */
    int processPendingResources();

    // ==================== Availability ====================

    /**
     * Check if the MemoryService is available and operational.
     *
     * @return true if available, false otherwise
     */
    boolean isAvailable();

    // ==================== Inner Classes ====================

    /**
     * Message batch container for batch operations.
     */
    class MessageBatch {
        private final List<UnifiedMessage> messages;
        private final String conversationId;
        private final String sessionId;

        public MessageBatch(List<UnifiedMessage> messages, String conversationId, String sessionId) {
            this.messages = messages;
            this.conversationId = conversationId;
            this.sessionId = sessionId;
        }

        public List<UnifiedMessage> getMessages() {
            return messages;
        }

        public String getConversationId() {
            return conversationId;
        }

        public String getSessionId() {
            return sessionId;
        }
    }
}
