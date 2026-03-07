package tech.yesboss.memory.repository;

import tech.yesboss.memory.model.Preference;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Repository interface for Preference entities.
 *
 * Handles CRUD operations for user preferences with support for:
 * - Basic CRUD (create, read, update, delete)
 * - Query by user and session
 * - Preference value updates
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
     * Find preferences by user ID.
     *
     * @param userId User ID
     * @return List of preferences
     */
    List<Preference> findByUserId(String userId);

    /**
     * Find preference by user ID and key.
     *
     * @param userId User ID
     * @param key Preference key
     * @return Optional containing the preference, or empty if not found
     */
    Optional<Preference> findByUserIdAndKey(String userId, String key);

    /**
     * Find preferences by session ID.
     *
     * @param sessionId Session ID
     * @return List of preferences
     */
    List<Preference> findBySessionId(String sessionId);

    /**
     * Find preferences by time range.
     *
     * @param startTime Start timestamp
     * @param endTime End timestamp
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
     * Update preference value by user ID and key.
     *
     * @param userId User ID
     * @param key Preference key
     * @param value New value
     * @return true if updated, false if not found
     */
    boolean updateValue(String userId, String key, String value);

    /**
     * Delete a preference by ID.
     *
     * @param id Preference ID
     * @return true if deleted, false if not found
     */
    boolean deleteById(String id);

    /**
     * Delete multiple preferences by IDs.
     *
     * @param ids Preference IDs to delete
     * @return Number of deleted preferences
     */
    int deleteAll(List<String> ids);

    /**
     * Delete preferences by user ID.
     *
     * @param userId User ID
     * @return Number of deleted preferences
     */
    int deleteByUserId(String userId);

    /**
     * Delete preferences by session ID.
     *
     * @param sessionId Session ID
     * @return Number of deleted preferences
     */
    int deleteBySessionId(String sessionId);

    /**
     * Check if a preference exists by ID.
     *
     * @param id Preference ID
     * @return true if exists, false otherwise
     */
    boolean existsById(String id);

    /**
     * Get total preference count.
     *
     * @return Total count
     */
    long count();

    /**
     * Get preference count by user ID.
     *
     * @param userId User ID
     * @return Preference count
     */
    long countByUserId(String userId);

    /**
     * Get preference count by session ID.
     *
     * @param sessionId Session ID
     * @return Preference count
     */

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

    /**
     * Find preference by ID (non-optional version).
     *
     * @param id Preference ID
     * @return Preference or null if not found
     */

    /**
     * Update preference summary and embedding by name.
     *
     * @param name Preference name
     * @param newSummary New summary
     * @param newEmbedding New embedding
     * @return true if updated, false if not found
     */
    boolean updateSummaryAndEmbedding(String name, String newSummary, byte[] newEmbedding);

    /**
     * Delete preference by name.
     *
     * @param name Preference name
     * @return true if deleted, false if not found
     */
    boolean deleteByName(String name);

    /**
     * Check if a preference exists by name.
     *
     * @param name Preference name
     * @return true if exists, false otherwise
     */
    boolean existsByName(String name);

    default Preference find(String id) {
        return findById(id).orElse(null);
    }

    /**
     * Find preference by name.
     *
     * @param name Preference name
     * @return Optional containing the preference, or empty if not found
     */
    Optional<Preference> findByName(String name);

    /**
     * Find preferences without embeddings.
     *
     * @return List of preferences without embeddings
     */
    List<Preference> findPreferencesWithoutEmbedding();

    /**
     * Get preference count by session ID.
     *
     * @param sessionId Session ID
     * @return Preference count
     */

    /**
     * Get preference count by session ID.
     *
     * @param sessionId Session ID
     * @return Preference count
     */
    long countBySessionId(String sessionId);
}
