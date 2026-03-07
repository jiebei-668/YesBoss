package tech.yesboss.memory.repository;

import tech.yesboss.memory.model.Snippet;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Snippet entities.
 *
 * Handles CRUD operations for structured memory fragments with support for:
 * - Basic CRUD (create, read, update, delete)
 * - Pagination
 * - Batch operations
 * - Memory type classification queries
 * - Query optimization with indexes
 *
 * Note: This is a simplified interface using JDBC (not Spring Data JPA)
 * to match the project's architecture.
 */
public interface SnippetRepository {

    /**
     * Save a snippet.
     *
     * @param snippet Snippet to save
     * @return Saved snippet with generated ID
     */
    Snippet save(Snippet snippet);

    /**
     * Save multiple snippets in batch.
     *
     * @param snippets Snippets to save
     * @return List of saved snippets
     */
    List<Snippet> saveAll(List<Snippet> snippets);

    /**
     * Find snippet by ID.
     *
     * @param id Snippet ID
     * @return Optional containing the snippet, or empty if not found
     */
    Optional<Snippet> findById(String id);

    /**
     * Find snippets by resource ID.
     *
     * @param resourceId Resource ID
     * @return List of snippets
     */
    List<Snippet> findByResourceId(String resourceId);

    /**
     * Find snippets by resource ID with pagination.
     *
     * @param resourceId Resource ID
     * @param page Page number (0-indexed)
     * @param size Page size (max 100)
     * @return List of snippets
     */
    List<Snippet> findByResourceId(String resourceId, int page, int size);

    /**
     * Find snippets by memory type.
     *
     * @param memoryType Memory type (PROFILE, EVENT, KNOWLEDGE, BEHAVIOR, SKILL, TOOL)
     * @return List of snippets
     */
    List<Snippet> findByMemoryType(Snippet.MemoryType memoryType);

    /**
     * Find snippets by memory type with pagination.
     *
     * @param memoryType Memory type
     * @param page Page number (0-indexed)
     * @param size Page size (max 100)
     * @return List of snippets
     */
    List<Snippet> findByMemoryType(Snippet.MemoryType memoryType, int page, int size);

    /**
     * Find snippets by multiple memory types.
     *
     * @param memoryTypes List of memory types
     * @return List of snippets
     */
    List<Snippet> findByMemoryTypes(List<Snippet.MemoryType> memoryTypes);

    /**
     * Find snippets without embeddings.
     *
     * @return List of snippets without embeddings (max 1000)
     */
    List<Snippet> findSnippetsWithoutEmbedding();

    /**
     * Find snippets by time range.
     *
     * @param startTime Start timestamp (millis)
     * @param endTime End timestamp (millis)
     * @return List of snippets
     */
    List<Snippet> findByTimeRange(long startTime, long endTime);

    /**
     * Update a snippet.
     *
     * @param snippet Snippet to update
     * @return Updated snippet
     */
    Snippet update(Snippet snippet);

    /**
     * Delete a snippet by ID.
     *
     * @param id Snippet ID
     * @return true if deleted, false if not found
     */
    boolean deleteById(String id);

    /**
     * Delete multiple snippets by IDs.
     *
     * @param ids Snippet IDs to delete
     * @return Number of deleted snippets
     */
    int deleteAll(List<String> ids);

    /**
     * Delete snippets by resource ID.
     *
     * @param resourceId Resource ID
     * @return Number of deleted snippets
     */
    int deleteByResourceId(String resourceId);

    /**
     * Check if a snippet exists by ID.
     *
     * @param id Snippet ID
     * @return true if exists, false otherwise
     */
    boolean existsById(String id);

    /**
     * Get total snippet count.
     *
     * @return Total count
     */
    long count();

    /**
     * Get snippet count by resource ID.
     *
     * @param resourceId Resource ID
     * @return Snippet count
     */
    long countByResourceId(String resourceId);

    /**
     * Get snippet count by memory type.
     *
     * @param memoryType Memory type
     * @return Snippet count
     */
    long countByMemoryType(Snippet.MemoryType memoryType);

    /**
     * Clear all snippets.
     */
    void clear();

    /**
     * Find snippets by multiple IDs.
     *
     * @param ids List of snippet IDs
     * @return List of snippets
     */
    List<Snippet> findByIds(List<String> ids);

    /**
     * Find snippets by preference ID.
     *
     * @param preferenceId Preference ID
     * @return List of snippets
     */

    /**
     * Find snippets by preference ID and time range.
     *
     * @param preferenceId Preference ID
     * @param startTime Start timestamp (millis)
     * @param endTime End timestamp (millis)
     * @return List of snippets
     */

    /**
     * Find snippets by multiple resource IDs.
     *
     * @param resourceIds List of resource IDs
     * @return List of snippets
     */
    List<Snippet> findByResourceIds(List<String> resourceIds);

    /**
     * Search snippets by keyword.
     *
     * @param keyword Keyword to search for
     * @param topK Maximum number of results
     * @return List of snippets
     */
    List<Snippet> searchByKeyword(String keyword, int topK);

    /**
     * Search snippets by keyword and preference ID.
     *
     * @param keyword Keyword to search for
     * @param preferenceId Preference ID
     * @param topK Maximum number of results
     * @return List of snippets
     */

    /**
     * Find snippets by time range with limit.
     *
     * @param startTime Start timestamp (millis)
     * @param endTime End timestamp (millis)
     * @param topK Maximum number of results
     * @return List of snippets
     */
    List<Snippet> findByTimeRange(long startTime, long endTime, int topK);

    /**
    List<Snippet> findByTimeRange(long startTime, long endTime, int topK);

    /**
     * Find snippets by preference ID (STUBBED - requires schema update).
     *
     * @param preferenceId Preference ID
     * @return List of snippets
     */
    default List<Snippet> findByPreferenceId(String preferenceId) {
        // STUB: Requires preference_id field in snippets table
        return List.of();
    }

    /**
     * Find snippets by preference ID and time range (STUBBED - requires schema update).
     *
     * @param preferenceId Preference ID
     * @param startTime Start timestamp (millis)
     * @param endTime End timestamp (millis)
     * @return List of snippets
     */
    default List<Snippet> findByPreferenceIdAndTimeRange(String preferenceId, long startTime, long endTime) {
        // STUB: Requires preference_id field in snippets table
        return List.of();
    }

    /**
     * Search snippets by keyword and preference (STUBBED - requires schema update).
     *
     * @param keyword Keyword to search for
     * @param preferenceId Preference ID
     * @param topK Maximum number of results
     * @return List of snippets
     */
    default List<Snippet> searchByKeywordAndPreference(String keyword, String preferenceId, int topK) {
        // STUB: Requires preference_id field in snippets table
        return List.of();
    }
}
