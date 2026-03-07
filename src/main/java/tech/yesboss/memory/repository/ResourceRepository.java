package tech.yesboss.memory.repository;

import tech.yesboss.memory.model.Resource;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Resource entities.
 *
 * Handles CRUD operations for conversation resources with support for:
 * - Basic CRUD (create, read, update, delete)
 * - Pagination
 * - Batch operations
 * - Query optimization with indexes
 *
 * Note: This is a simplified interface using JDBC (not Spring Data JPA)
 * to match the project's architecture.
 */
public interface ResourceRepository {

    /**
     * Save a resource.
     *
     * @param resource Resource to save
     * @return Saved resource with generated ID
     */
    Resource save(Resource resource);

    /**
     * Save multiple resources in batch.
     *
     * @param resources Resources to save
     * @return List of saved resources
     */
    List<Resource> saveAll(List<Resource> resources);

    /**
     * Find resource by ID.
     *
     * @param id Resource ID
     * @return Optional containing the resource, or empty if not found
     */
    Optional<Resource> findById(String id);

    /**
     * Find resources by conversation ID.
     *
     * @param conversationId Conversation ID
     * @return List of resources
     */
    List<Resource> findByConversationId(String conversationId);

    /**
     * Find resources by conversation ID with pagination.
     *
     * @param conversationId Conversation ID
     * @param page Page number (0-indexed)
     * @param size Page size
     * @return List of resources
     */
    List<Resource> findByConversationId(String conversationId, int page, int size);

    /**
     * Find resources by session ID.
     *
     * @param sessionId Session ID
     * @return List of resources
     */
    List<Resource> findBySessionId(String sessionId);

    /**
     * Find resources without embeddings.
     *
     * @return List of resources without embeddings (max 1000)
     */
    List<Resource> findResourcesWithoutEmbedding();

    /**
     * Find resources by time range.
     *
     * @param startTime Start timestamp
     * @param endTime End timestamp
     * @return List of resources
     */
    List<Resource> findByTimeRange(long startTime, long endTime);

    /**
     * Update a resource.
     *
     * @param resource Resource to update
     * @return Updated resource
     */
    Resource update(Resource resource);

    /**
     * Delete a resource by ID.
     *
     * @param id Resource ID
     * @return true if deleted, false if not found
     */
    boolean deleteById(String id);

    /**
     * Delete multiple resources by IDs.
     *
     * @param ids Resource IDs to delete
     * @return Number of deleted resources
     */
    int deleteAll(List<String> ids);

    /**
     * Delete resources by session ID.
     *
     * @param sessionId Session ID
     * @return Number of deleted resources
     */
    int deleteBySessionId(String sessionId);

    /**
     * Check if a resource exists by ID.
     *
     * @param id Resource ID
     * @return true if exists, false otherwise
     */
    boolean existsById(String id);

    /**
     * Get total resource count.
     *
     * @return Total count
     */
    long count();

    /**
     * Get resource count by conversation ID.
     *
     * @param conversationId Conversation ID
     * @return Resource count
     */
    long countByConversationId(String conversationId);

    /**
     * Get resource count by session ID.
     *
     * @param sessionId Session ID
     * @return Resource count
     */
    long countBySessionId(String sessionId);

    /**
     * Clear all resources.
     */
    void clear();

    /**
     * Find resources by multiple IDs.
     *
     * @param ids List of resource IDs
     * @return List of resources
     */
    List<Resource> findByIds(List<String> ids);

    /**
     * Find resource by ID (non-optional version).
     *
     * @param id Resource ID
     * @return Resource or null if not found
     */

    /**
     * Find resource by ID (non-optional version).
     *
     * @param id Resource ID
     * @return Resource or null if not found
     */
    default Resource find(String id) {
        return findById(id).orElse(null);
    }
}

    /**
     * Find resources by multiple IDs.
     *
     * @param ids List of resource IDs
     * @return List of resources
     */
    default List<Resource> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .map(this::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }
