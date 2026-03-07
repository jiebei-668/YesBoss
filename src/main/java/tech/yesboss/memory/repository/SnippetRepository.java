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
}
