package tech.yesboss.memory.repository;

import tech.yesboss.memory.model.Preference;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Preference entities.
 *
 * Handles CRUD operations for user preference topics with support for:
 * - Basic CRUD (create, read, update, delete)
 * - Name-based queries
 * - Batch operations
 * - Embedding-related queries
 * - Smart update mechanisms
 *
 * Note: This is a simplified interface using JDBC (not Spring Data JPA)
 * to match the project's architecture.
 */
public interface PreferenceRepository {

    /**
     * Save a preference.
     *
     * @param preference Preference to save
     * @return Saved preference with generated ID
     */
    Preference save(Preference preference);

    /**
     * Save multiple preferences in batch.
     *
     * @param preferences Preferences to save
     * @return List of saved preferences
     */
    List<Preference> saveAll(List<Preference> preferences);

    /**
     * Find preference by ID.
     *
     * @param id Preference ID
     * @return Optional containing the preference, or empty if not found
     */
    Optional<Preference> findById(String id);

    /**
     * Find preference by name.
     *
     * @param name Preference name (unique)
     * @return Optional containing the preference, or empty if not found
     */
    Optional<Preference> findByName(String name);

    /**
     * Find preferences without embeddings.
     *
     * @return List of preferences without embeddings (max 1000)
     */
    List<Preference> findPreferencesWithoutEmbedding();

    /**
     * Find preferences by time range.
     *
     * @param startTime Start timestamp (millis)
     * @param endTime End timestamp (millis)
     * @return List of preferences
     */
    List<Preference> findByTimeRange(long startTime, long endTime);

    /**
     * Update a preference.
     *
     * @param preference Preference to update
     * @return Updated preference
     */
    Preference update(Preference preference);

    /**
     * Update summary and embedding by name.
     *
     * @param name Preference name
     * @param newSummary New summary text
     * @param newEmbedding New embedding (can be null to remove)
     * @return true if updated, false if not found
     */
    boolean updateSummaryAndEmbedding(String name, String newSummary, byte[] newEmbedding);

    /**
     * Delete a preference by ID.
     *
     * @param id Preference ID
     * @return true if deleted, false if not found
     */
    boolean deleteById(String id);

    /**
     * Delete a preference by name.
     *
     * @param name Preference name
     * @return true if deleted, false if not found
     */
    boolean deleteByName(String name);

    /**
     * Delete multiple preferences by IDs.
     *
     * @param ids Preference IDs to delete
     * @return Number of deleted preferences
     */
    int deleteAll(List<String> ids);

    /**
     * Check if a preference exists by ID.
     *
     * @param id Preference ID
     * @return true if exists, false otherwise
     */
    boolean existsById(String id);

    /**
     * Check if a preference exists by name.
     *
     * @param name Preference name
     * @return true if exists, false otherwise
     */
    boolean existsByName(String name);

    /**
     * Get total preference count.
     *
     * @return Total count
     */
    long count();

    /**
     * Clear all preferences.
     */
    void clear();

    /**
     * Find preferences by multiple IDs.
     *
     * @param ids List of preference IDs
     * @return List of preferences
     */
    List<Preference> findByIds(List<String> ids);

    /**
     * Find preference by ID (non-optional version).
     *
     * @param id Preference ID
     * @return Preference or null if not found
     */

    /**
     * Find preferences by session ID (STUBBED - requires schema update).
     *
     * @param sessionId Session ID
     * @return List of preferences
     */
    default List<Preference> findBySessionId(String sessionId) {
        // STUB: Requires session_id field in preferences table
        return List.of();
    }

    /**
     * Find preference by ID (non-optional version).
     *
     * @param id Preference ID
     * @return Preference or null if not found
     */
    default Preference find(String id) {
        return findById(id).orElse(null);
    }
}

    /**
     * Find preferences by multiple IDs.
     *
     * @param ids List of preference IDs
     * @return List of preferences
     */
    default List<Preference> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .map(this::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }
