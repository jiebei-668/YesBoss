package tech.yesboss.memory.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.memory.model.Preference;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * JDBC-based implementation of PreferenceRepository.
 *
 * Features:
 * - CRUD operations with validation
 * - Batch operations (max 100 per batch)
 * - Name-based queries (unique constraint)
 * - Embedding-related queries
 * - Smart update mechanisms
 * - Transaction support
 *
 * Note: Simplified implementation using JDBC (not Spring Data JPA)
 * to match the project's architecture.
 */
public class PreferenceRepositoryImpl implements PreferenceRepository {

    private static final Logger logger = LoggerFactory.getLogger(PreferenceRepositoryImpl.class);

    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_NAME_LENGTH = 100;

    private final DataSource dataSource;

    public PreferenceRepositoryImpl(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource cannot be null");
        }
        this.dataSource = dataSource;
        logger.info("PreferenceRepositoryImpl initialized");
    }

    @Override
    public Preference save(Preference preference) {
        validatePreference(preference);

        // Generate ID if not present
        if (preference.getId() == null || preference.getId().isEmpty()) {
            preference.setId(UUID.randomUUID().toString());
        }

        // Set timestamps
        LocalDateTime now = LocalDateTime.now();
        if (preference.getCreatedAt() == null) {
            preference.setCreatedAt(now);
        }
        preference.setUpdatedAt(now);

        String sql = """
            INSERT INTO preferences (id, name, summary, embedding, deleted, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, preference.getId());
            pstmt.setString(2, preference.getName());
            pstmt.setString(3, preference.getSummary());
            pstmt.setBytes(4, preference.getEmbedding());
            pstmt.setInt(5, preference.isDeleted() ? 1 : 0);
            pstmt.setLong(6, localDateTimeToMillis(preference.getCreatedAt()));
            pstmt.setLong(7, localDateTimeToMillis(preference.getUpdatedAt()));

            pstmt.executeUpdate();
            logger.debug("Saved preference: {}", preference.getName());

        } catch (SQLException e) {
            logger.error("Failed to save preference: {}", preference.getName(), e);
            throw new RuntimeException("Failed to save preference", e);
        }

        return preference;
    }

    @Override
    public List<Preference> saveAll(List<Preference> preferences) {
        if (preferences == null || preferences.isEmpty()) {
            return List.of();
        }

        // Split into batches if exceeds max batch size
        if (preferences.size() > MAX_BATCH_SIZE) {
            return saveBatchSplit(preferences);
        }

        String sql = """
            INSERT INTO preferences (id, name, summary, embedding, deleted, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (Preference preference : preferences) {
                    validatePreference(preference);

                    // Generate ID if not present
                    if (preference.getId() == null || preference.getId().isEmpty()) {
                        preference.setId(UUID.randomUUID().toString());
                    }

                    // Set timestamps
                    LocalDateTime now = LocalDateTime.now();
                    if (preference.getCreatedAt() == null) {
                        preference.setCreatedAt(now);
                    }
                    preference.setUpdatedAt(now);

                    pstmt.setString(1, preference.getId());
                    pstmt.setString(2, preference.getName());
                    pstmt.setString(3, preference.getSummary());
                    pstmt.setBytes(4, preference.getEmbedding());
                    pstmt.setInt(5, preference.isDeleted() ? 1 : 0);
                    pstmt.setLong(6, localDateTimeToMillis(preference.getCreatedAt()));
                    pstmt.setLong(7, localDateTimeToMillis(preference.getUpdatedAt()));
                    pstmt.addBatch();
                }

                pstmt.executeBatch();
                conn.commit();

                logger.debug("Saved {} preferences in batch", preferences.size());
                return new ArrayList<>(preferences);

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            logger.error("Failed to save batch of preferences", e);
            throw new RuntimeException("Failed to save batch of preferences", e);
        }
    }

    private List<Preference> saveBatchSplit(List<Preference> preferences) {
        List<Preference> allSaved = new ArrayList<>();
        int start = 0;

        while (start < preferences.size()) {
            int end = Math.min(start + MAX_BATCH_SIZE, preferences.size());
            List<Preference> batch = preferences.subList(start, end);
            allSaved.addAll(saveAll(batch));
            start = end;
        }

        return allSaved;
    }

    @Override
    public Optional<Preference> findById(String id) {
        if (id == null || id.isEmpty()) {
            return Optional.empty();
        }

        String sql = "SELECT * FROM preferences WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToPreference(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to find preference by ID: {}", id, e);
        }

        return Optional.empty();
    }

    @Override
    public Optional<Preference> findByName(String name) {
        if (name == null || name.isEmpty()) {
            return Optional.empty();
        }

        String sql = "SELECT * FROM preferences WHERE name = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToPreference(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to find preference by name: {}", name, e);
        }

        return Optional.empty();
    }

    @Override
    public List<Preference> findPreferencesWithoutEmbedding() {
        String sql = """
            SELECT * FROM preferences
            WHERE embedding IS NULL
            ORDER BY created_at ASC
            LIMIT 1000
            """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(sql);
            return executeQuery(stmt);

        } catch (SQLException e) {
            logger.error("Failed to find preferences without embedding", e);
            return List.of();
        }
    }

    @Override
    public List<Preference> findBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return List.of();
        }
        // Preferences don't have session_id in their schema, return empty list
        // This method exists for interface compatibility
        logger.debug("findBySessionId called on PreferenceRepository, returning empty list (preferences are not session-scoped)");
        return List.of();
    }

    @Override
    public List<Preference> findByUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            return List.of();
        }
        // Preferences don't have user_id in their schema, return empty list
        // This method exists for interface compatibility
        logger.debug("findByUserId called on PreferenceRepository, returning empty list (preferences are not user-scoped)");
        return List.of();
    }

    @Override
    public Optional<Preference> findByUserIdAndKey(String userId, String key) {
        if (userId == null || userId.isEmpty() || key == null || key.isEmpty()) {
            return Optional.empty();
        }
        // Preferences don't have user_id and key in their schema, return empty
        // This method exists for interface compatibility
        logger.debug("findByUserIdAndKey called on PreferenceRepository, returning empty (preferences are not user-key scoped)");
        return Optional.empty();
    }


    @Override
    public List<Preference> findByTimeRange(long startTime, long endTime) {
        String sql = """
            SELECT * FROM preferences
            WHERE created_at >= ? AND created_at <= ?
            ORDER BY created_at DESC
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, startTime);
            pstmt.setLong(2, endTime);
            return executeQuery(pstmt);

        } catch (SQLException e) {
            logger.error("Failed to find preferences by time range", e);
            return List.of();
        }
    }

    @Override
    public Preference update(Preference preference) {
        validatePreference(preference);

        if (preference.getId() == null || preference.getId().isEmpty()) {
            throw new IllegalArgumentException("Preference ID cannot be null or empty for update");
        }

        preference.setUpdatedAt(LocalDateTime.now());

        String sql = """
            UPDATE preferences
            SET name = ?, summary = ?, embedding = ?, deleted = ?, updated_at = ?
            WHERE id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, preference.getName());
            pstmt.setString(2, preference.getSummary());
            pstmt.setBytes(3, preference.getEmbedding());
            pstmt.setInt(4, preference.isDeleted() ? 1 : 0);
            pstmt.setLong(5, localDateTimeToMillis(preference.getUpdatedAt()));
            pstmt.setString(6, preference.getId());

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows == 0) {
                logger.warn("No preference found to update: {}", preference.getId());
            }

            return preference;

        } catch (SQLException e) {
            logger.error("Failed to update preference: {}", preference.getId(), e);
            throw new RuntimeException("Failed to update preference", e);
        }
    }

    @Override
    public boolean updateSummaryAndEmbedding(String name, String newSummary, byte[] newEmbedding) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name cannot be null or empty");
        }
        if (newSummary == null || newSummary.isEmpty()) {
            throw new IllegalArgumentException("newSummary cannot be null or empty");
        }

        String sql = """
            UPDATE preferences
            SET summary = ?, embedding = ?, updated_at = ?
            WHERE name = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newSummary);
            pstmt.setBytes(2, newEmbedding);
            pstmt.setLong(3, System.currentTimeMillis());
            pstmt.setString(4, name);

            int affectedRows = pstmt.executeUpdate();
            logger.debug("Updated preference summary and embedding: {} (rows: {})", name, affectedRows);

            return affectedRows > 0;

        } catch (SQLException e) {
            logger.error("Failed to update preference summary and embedding: {}", name, e);
            return false;
        }
    }

    @Override
    public boolean deleteById(String id) {
        String sql = "DELETE FROM preferences WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, id);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;

        } catch (SQLException e) {
            logger.error("Failed to delete preference: {}", id, e);
            return false;
        }
    }

    @Override
    public boolean deleteByName(String name) {
        String sql = "DELETE FROM preferences WHERE name = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;

        } catch (SQLException e) {
            logger.error("Failed to delete preference: {}", name, e);
            return false;
        }
    }

    @Override
    public int deleteAll(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        String sql = "DELETE FROM preferences WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            int totalDeleted = 0;
            for (String id : ids) {
                pstmt.setString(1, id);
                pstmt.addBatch();
            }

            int[] results = pstmt.executeBatch();
            for (int result : results) {
                totalDeleted += result;
            }

            return totalDeleted;

        } catch (SQLException e) {
            logger.error("Failed to delete preferences in batch", e);
            return 0;
        }
    }

    @Override
    public boolean existsById(String id) {
        String sql = "SELECT 1 FROM preferences WHERE id = ? LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            logger.error("Failed to check preference existence: {}", id, e);
            return false;
        }
    }

    @Override
    public boolean existsByName(String name) {
        String sql = "SELECT 1 FROM preferences WHERE name = ? LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            logger.error("Failed to check preference existence: {}", name, e);
            return false;
        }
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM preferences";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to count preferences", e);
        }

        return 0;
    }

    @Override
    public void clear() {
        String sql = "DELETE FROM preferences";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(sql);
            logger.info("Cleared all preferences");

        } catch (SQLException e) {
            logger.error("Failed to clear preferences", e);
            throw new RuntimeException("Failed to clear preferences", e);
        }
    }

    // ==========================================
    // Utility Methods
    // ==========================================

    private void validatePreference(Preference preference) {
        if (preference == null) {
            throw new IllegalArgumentException("Preference cannot be null");
        }
        if (preference.getName() == null || preference.getName().isEmpty()) {
            throw new IllegalArgumentException("name cannot be null or empty");
        }
        if (preference.getName().length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("name length cannot exceed " + MAX_NAME_LENGTH + " characters");
        }
        if (preference.getSummary() == null || preference.getSummary().isEmpty()) {
            throw new IllegalArgumentException("summary cannot be null or empty");
        }
    }

    private Preference mapRowToPreference(ResultSet rs) throws SQLException {
        Preference preference = new Preference();
        preference.setId(rs.getString("id"));
        preference.setName(rs.getString("name"));
        preference.setSummary(rs.getString("summary"));
        preference.setEmbedding(rs.getBytes("embedding"));
        preference.setDeleted(rs.getInt("deleted") == 1);
        preference.setCreatedAt(millisToLocalDateTime(rs.getLong("created_at")));
        preference.setUpdatedAt(millisToLocalDateTime(rs.getLong("updated_at")));
        return preference;
    }

    private List<Preference> executeQuery(PreparedStatement pstmt) throws SQLException {
        List<Preference> preferences = new ArrayList<>();
        try (ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                preferences.add(mapRowToPreference(rs));
            }
        }
        return preferences;
    }

    private List<Preference> executeQuery(Statement stmt) throws SQLException {
        List<Preference> preferences = new ArrayList<>();
        try (ResultSet rs = stmt.getResultSet()) {
            if (rs != null) {
                while (rs.next()) {
                    preferences.add(mapRowToPreference(rs));
                }
            }
        }
        return preferences;
    }

    private long localDateTimeToMillis(LocalDateTime dateTime) {
        return dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private LocalDateTime millisToLocalDateTime(long millis) {
        return LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(millis),
            java.time.ZoneId.systemDefault()
        );
    }

    @Override
    public long countBySessionId(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return 0;
        }

        String sql = "SELECT COUNT(*) FROM memory_preferences WHERE session_id = ? AND deleted = 0";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, sessionId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            logger.error("Error counting preferences by session_id: {}", sessionId, e);
            return 0;
        }
    }

    @Override
    public long countByUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return 0;
        }

        String sql = "SELECT COUNT(*) FROM memory_preferences WHERE user_id = ? AND deleted = 0";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            logger.error("Error counting preferences by user_id: {}", userId, e);
            return 0;
        }
    }

    @Override
    public int deleteBySessionId(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return 0;
        }

        String sql = "UPDATE memory_preferences SET deleted = 1, updated_at = ? WHERE session_id = ? AND deleted = 0";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, localDateTimeToMillis(LocalDateTime.now()));
            pstmt.setString(2, sessionId);

            return pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error deleting preferences by session_id: {}", sessionId, e);
            return 0;
        }
    }

    @Override
    public int deleteByUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return 0;
        }

        String sql = "UPDATE memory_preferences SET deleted = 1, updated_at = ? WHERE user_id = ? AND deleted = 0";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, localDateTimeToMillis(LocalDateTime.now()));
            pstmt.setString(2, userId);

            return pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error deleting preferences by user_id: {}", userId, e);
            return 0;
        }
    }

    @Override
    public boolean updateValue(String userId, String key, String value) {
        // Stub implementation
        logger.warn("updateValue not implemented");
        return false;
    }
}
