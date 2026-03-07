package tech.yesboss.memory.processor;

import tech.yesboss.memory.model.Snippet.MemoryType;
import tech.yesboss.memory.model.Preference;
import tech.yesboss.memory.model.Snippet;

import java.util.List;

/**
 * Content processing service for analyzing and processing conversation content.
 *
 * <p>This service provides capabilities for:
 * <ul>
 *   <li>Conversation segmentation by topic</li>
 *   <li>Abstract/summary generation for segments</li>
 *   <li>Structured memory extraction from resources</li>
 *   <li>Preference identification for snippets</li>
 *   <li>Preference summary updates</li>
 * </ul>
 */
public interface ContentProcessor {

    /**
     * Analyze and segment conversation content by topic.
     *
     * <p>Uses LLM to analyze complete conversation content and automatically
     * split it into multiple segments based on topic changes.</p>
     *
     * @param conversationContent Complete conversation content (formatted, one message per line)
     * @return List of conversation segments in chronological order
     * @throws ContentProcessingException if processing fails
     */
    List<ConversationSegment> segmentConversation(String conversationContent);

    /**
     * Generate abstract summary for a conversation segment.
     *
     * <p>Generates a 1-2 sentence brief abstract for a single conversation segment.</p>
     *
     * @param segmentContent Segment content
     * @return Segment abstract/summary
     * @throws ContentProcessingException if generation fails
     */
    String generateSegmentAbstract(String segmentContent);

    /**
     * Generate conversation description.
     *
     * @param content Conversation content
     * @return Conversation description
     * @deprecated Use {@link #segmentConversation(String)} and {@link #generateSegmentAbstract(String)} instead
     */
    @Deprecated
    String generateAbstract(String content);

    /**
     * Extract structured memories from resource content.
     *
     * <p>Uses LLM to extract specific types of structured memory fragments from
     * the complete Resource content. Each fragment is an independent, meaningful
     * memory unit (such as profile, event, knowledge, etc.).</p>
     *
     * @param resourceContent Complete resource content (conversation segment, document, etc.)
     * @param memoryType Memory type (PROFILE, EVENT, KNOWLEDGE, BEHAVIOR, SKILL, TOOL)
     * @return List of extracted structured memory fragments
     * @throws ContentProcessingException if extraction fails
     */
    List<String> extractStructuredMemories(String resourceContent, MemoryType memoryType);

    /**
     * Identify which preferences a snippet belongs to.
     *
     * <p>Uses LLM to analyze snippet content and determine which existing,
     * hardcoded preference topics it should be associated with. A snippet can
     * be associated with multiple preferences (many-to-many relationship).</p>
     *
     * <p>Note: Preferences are hardcoded and not dynamically created.</p>
     *
     * @param snippetSummary Snippet summary content
     * @param existingPreferences List of existing, hardcoded preferences (including id, name, summary)
     * @return List of preference IDs (may be empty, indicating no existing preference association)
     * @throws ContentProcessingException if identification fails
     */
    List<String> identifyPreferencesForSnippet(String snippetSummary, List<Preference> existingPreferences);

    /**
     * Update preference summary.
     *
     * <p>Uses existing preference summary and new snippets to generate an updated summary.
     * The LLM performs a merge operation: preserves historical information, adds new information.</p>
     *
     * <p>Note: This method only uses newly created snippets to update the preference,
     * not all snippets.</p>
     *
     * @param existingSummary Current preference summary
     * @param newSnippets Newly created snippets that should be associated with this preference (usually only 1)
     * @return Updated summary
     * @throws ContentProcessingException if update fails
     */
    String updatePreferenceSummary(String existingSummary, List<Snippet> newSnippets);

    /**
     * Batch generate conversation descriptions.
     *
     * @param contents List of conversation contents
     * @return List of conversation descriptions
     * @throws ContentProcessingException if batch generation fails
     */
    List<String> batchGenerateAbstracts(List<String> contents);

    /**
     * Batch generate summaries.
     *
     * @param contents List of contents
     * @param memoryType Memory type
     * @return List of summaries
     * @throws ContentProcessingException if batch generation fails
     */
    List<String> batchGenerateSummaries(List<String> contents, MemoryType memoryType);
}
