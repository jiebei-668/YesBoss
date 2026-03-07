package tech.yesboss.memory.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.memory.model.Snippet;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JDBC-based implementation of SnippetRepository.
 *
 * Features:
 * - CRUD operations with validation
 * - Batch operations (max 100 per batch)
 * - Pagination support
 * - Index-optimized queries
 * - Memory type classification queries
 * - Transaction support
 *
 * Note: Simplified implementation using JDBC (not Spring Data JPA)
 * to match the project's architecture.
 */
public class SnippetRepositoryImpl implements SnippetRepository {

    private static final Logger logger = LoggerFactory.getLogger(SnippetRepositoryImpl.class);

    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 100;

    private final DataSource dataSource;

    public SnippetRepositoryImpl(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource cannot be null");
        }
        this.dataSource = dataSource;
        logger.info("SnippetRepositoryImpl initialized");
    }

    @Override
    public Snippet save(Snippet snippet) {
        validateSnippet(snippet);

        // Generate ID if not present
        if (snippet.getId() == null || snippet.getId().isEmpty()) {
            snippet.setId(UUID.randomUUID().toString());
        }

        // Set timestamps
        LocalDateTime now = LocalDateTime.now();
        if (snippet.getCreatedAt() == null) {
            snippet.setCreatedAt(now);
        }
        snippet.setUpdatedAt(now);

        String sql = """
            INSERT INTO snippets (id, resource_id, summary, memory_type, embedding,
                                 timestamp, deleted, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, snippet.getId());
            pstmt.setString(2, snippet.getResourceId());
            pstmt.setString(3, snippet.getSummary());
            pstmt.setString(4, snippet.getMemoryTypeString());
            pstmt.setBytes(5, snippet.getEmbedding());
            pstmt.setLong(6, snippet.getTimestamp());
            pstmt.setInt(7, snippet.isDeleted() ? 1 : 0);
            pstmt.setLong(8, localDateTimeToMillis(snippet.getCreatedAt()));
            pstmt.setLong(9, localDateTimeToMillis(snippet.getUpdatedAt()));

            pstmt.executeUpdate();
            logger.debug("Saved snippet: {}", snippet.getId());

        } catch (SQLException e) {
            logger.error("Failed to save snippet: {}", snippet.getId(), e);
            throw new RuntimeException("Failed to save snippet", e);
        }

        return snippet;
    }

    @Override
    public List<Snippet> saveAll(List<Snippet> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return List.of();
        }

        // Split into batches if exceeds max batch size
        if (snippets.size() > MAX_BATCH_SIZE) {
            return saveBatchSplit(snippets);
        }

        String sql = """
            INSERT INTO snippets (id, resource_id, summary, memory_type, embedding,
                                 timestamp, deleted, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (Snippet snippet : snippets) {
                    validateSnippet(snippet);

                    // Generate ID if not present
                    if (snippet.getId() == null || snippet.getId().isEmpty()) {
                        snippet.setId(UUID.randomUUID().toString());
                    }

                    // Set timestamps
                    LocalDateTime now = LocalDateTime.now();
                    if (snippet.getCreatedAt() == null) {
                        snippet.setCreatedAt(now);
                    }
                    snippet.setUpdatedAt(now);

                    pstmt.setString(1, snippet.getId());
                    pstmt.setString(2, snippet.getResourceId());
                    pstmt.setString(3, snippet.getSummary());
                    pstmt.setString(4, snippet.getMemoryTypeString());
                    pstmt.setBytes(5, snippet.getEmbedding());
                    pstmt.setLong(6, snippet.getTimestamp());
                    pstmt.setInt(7, snippet.isDeleted() ? 1 : 0);
                    pstmt.setLong(8, localDateTimeToMillis(snippet.getCreatedAt()));
                    pstmt.setLong(9, localDateTimeToMillis(snippet.getUpdatedAt()));
                    pstmt.addBatch();
                }

                pstmt.executeBatch();
                conn.commit();

                logger.debug("Saved {} snippets in batch", snippets.size());
                return new ArrayList<>(snippets);

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            logger.error("Failed to save batch of snippets", e);
            throw new RuntimeException("Failed to save batch of snippets", e);
        }
    }

    private List<Snippet> saveBatchSplit(List<Snippet> snippets) {
        List<Snippet> allSaved = new ArrayList<>();
        int start = 0;

        while (start < snippets.size()) {
            int end = Math.min(start + MAX_BATCH_SIZE, snippets.size());
            List<Snippet> batch = snippets.subList(start, end);
            allSaved.addAll(saveAll(batch));
            start = end;
        }

        return allSaved;
    }

    @Override
    public Optional<Snippet> findById(String id) {
        if (id == null || id.isEmpty()) {
            return Optional.empty();
        }

        String sql = "SELECT * FROM snippets WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToSnippet(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to find snippet by ID: {}", id, e);
        }

        return Optional.empty();
    }

    @Override
    public List<Snippet> findByResourceId(String resourceId) {
        String sql = "SELECT * FROM snippets WHERE resource_id = ? ORDER BY created_at DESC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, resourceId);
            return executeQuery(pstmt);

        } catch (SQLException e) {
            logger.error("Failed to find snippets by resource ID: {}", resourceId, e);
            return List.of();
        }
    }

    @Override
    public List<Snippet> findByResourceId(String resourceId, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }

        String sql = """
            SELECT * FROM snippets
            WHERE resource_id = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, resourceId);
            pstmt.setInt(2, size);
            pstmt.setInt(3, page * size);
            return executeQuery(pstmt);

        } catch (SQLException e) {
            logger.error("Failed to find snippets by resource ID with pagination", e);
            return List.of();
        }
    }

    @Override
    public List<Snippet> findByMemoryType(Snippet.MemoryType memoryType) {
        String sql = "SELECT * FROM snippets WHERE memory_type = ? ORDER BY created_at DESC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, memoryType.name());
            return executeQuery(pstmt);

        } catch (SQLException e) {
            logger.error("Failed to find snippets by memory type: {}", memoryType, e);
            return List.of();
        }
    }

    @Override
    public List<Snippet> findByMemoryType(Snippet.MemoryType memoryType, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }

        String sql = """
            SELECT * FROM snippets
            WHERE memory_type = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, memoryType.name());
            pstmt.setInt(2, size);
            pstmt.setInt(3, page * size);
            return executeQuery(pstmt);

        } catch (SQLException e) {
            logger.error("Failed to find snippets by memory type with pagination", e);
            return List.of();
        }
    }

    @Override
    public List<Snippet> findByMemoryTypes(List<Snippet.MemoryType> memoryTypes) {
        if (memoryTypes == null || memoryTypes.isEmpty()) {
            return List.of();
        }

        String placeholders = memoryTypes.stream()
                .map(type -> "?")
                .collect(Collectors.joining(","));

        String sql = "SELECT * FROM snippets WHERE memory_type IN (" + placeholders + ") ORDER BY created_at DESC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            int index = 1;
            for (Snippet.MemoryType type : memoryTypes) {
                pstmt.setString(index++, type.name());
            }
            return executeQuery(pstmt);

        } catch (SQLException e) {
            logger.error("Failed to find snippets by memory types", e);
            return List.of();
        }
    }

    @Override
    public List<Snippet> findSnippetsWithoutEmbedding() {
        String sql = """
            SELECT * FROM snippets
            WHERE embedding IS NULL
            ORDER BY created_at ASC
            LIMIT 1000
            """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            return executeQuery(stmt);

        } catch (SQLException e) {
            logger.error("Failed to find snippets without embedding", e);
            return List.of();
        }
    }

    @Override
    public List<Snippet> findByTimeRange(long startTime, long endTime) {
        String sql = """
            SELECT * FROM snippets
            WHERE created_at >= ? AND created_at <= ?
            ORDER BY created_at DESC
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, startTime);
            pstmt.setLong(2, endTime);
            return executeQuery(pstmt);

        } catch (SQLException e) {
            logger.error("Failed to find snippets by time range", e);
            return List.of();
        }
    }

    @Override
    public Snippet update(Snippet snippet) {
        validateSnippet(snippet);

        if (snippet.getId() == null || snippet.getId().isEmpty()) {
            throw new IllegalArgumentException("Snippet ID cannot be null or empty for update");
        }

        snippet.setUpdatedAt(LocalDateTime.now());

        String sql = """
            UPDATE snippets
            SET resource_id = ?, summary = ?, memory_type = ?, embedding = ?,
                deleted = ?, updated_at = ?
            WHERE id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, snippet.getResourceId());
            pstmt.setString(2, snippet.getSummary());
            pstmt.setString(3, snippet.getMemoryTypeString());
            pstmt.setBytes(4, snippet.getEmbedding());
            pstmt.setInt(5, snippet.isDeleted() ? 1 : 0);
            pstmt.setLong(6, localDateTimeToMillis(snippet.getUpdatedAt()));
            pstmt.setString(7, snippet.getId());

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows == 0) {
                logger.warn("No snippet found to update: {}", snippet.getId());
            }

            return snippet;

        } catch (SQLException e) {
            logger.error("Failed to update snippet: {}", snippet.getId(), e);
            throw new RuntimeException("Failed to update snippet", e);
        }
    }

    @Override
    public boolean deleteById(String id) {
        String sql = "DELETE FROM snippets WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, id);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;

        } catch (SQLException e) {
            logger.error("Failed to delete snippet: {}", id, e);
            return false;
        }
    }

    @Override
    public int deleteAll(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        String sql = "DELETE FROM snippets WHERE id = ?";

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
            logger.error("Failed to delete snippets in batch", e);
            return 0;
        }
    }

    @Override
    public int deleteByResourceId(String resourceId) {
        String sql = "DELETE FROM snippets WHERE resource_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, resourceId);
            return pstmt.executeUpdate();

        } catch (SQLException e) {
            logger.error("Failed to delete snippets by resource ID: {}", resourceId, e);
            return 0;
        }
    }

    @Override
    public boolean existsById(String id) {
        String sql = "SELECT 1 FROM snippets WHERE id = ? LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            logger.error("Failed to check snippet existence: {}", id, e);
            return false;
        }
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM snippets";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to count snippets", e);
        }

        return 0;
    }

    @Override
    public long countByResourceId(String resourceId) {
        String sql = "SELECT COUNT(*) FROM snippets WHERE resource_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, resourceId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to count snippets by resource ID", e);
        }

        return 0;
    }

    @Override
    public long countByMemoryType(Snippet.MemoryType memoryType) {
        String sql = "SELECT COUNT(*) FROM snippets WHERE memory_type = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, memoryType.name());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to count snippets by memory type", e);
        }

        return 0;
    }

    @Override
    public void clear() {
        String sql = "DELETE FROM snippets";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(sql);
            logger.info("Cleared all snippets");

        } catch (SQLException e) {
            logger.error("Failed to clear snippets", e);
            throw new RuntimeException("Failed to clear snippets", e);
        }
    }

    // ==========================================
    // Utility Methods
    // ==========================================

    private void validateSnippet(Snippet snippet) {
        if (snippet == null) {
            throw new IllegalArgumentException("Snippet cannot be null");
        }
        if (snippet.getResourceId() == null || snippet.getResourceId().isEmpty()) {
            throw new IllegalArgumentException("resourceId cannot be null or empty");
        }
        if (snippet.getSummary() == null || snippet.getSummary().isEmpty()) {
            throw new IllegalArgumentException("summary cannot be null or empty");
        }
        if (snippet.getMemoryType() == null) {
            throw new IllegalArgumentException("memoryType cannot be null");
        }
    }

    private Snippet mapRowToSnippet(ResultSet rs) throws SQLException {
        Snippet snippet = new Snippet();
        snippet.setId(rs.getString("id"));
        snippet.setResourceId(rs.getString("resource_id"));
        snippet.setSummary(rs.getString("summary"));
        snippet.setMemoryTypeFromString(rs.getString("memory_type"));
        snippet.setEmbedding(rs.getBytes("embedding"));
        snippet.setTimestamp(rs.getLong("timestamp"));
        snippet.setDeleted(rs.getInt("deleted") == 1);
        snippet.setCreatedAt(millisToLocalDateTime(rs.getLong("created_at")));
        snippet.setUpdatedAt(millisToLocalDateTime(rs.getLong("updated_at")));
        return snippet;
    }

    private List<Snippet> executeQuery(PreparedStatement pstmt) throws SQLException {
        List<Snippet> snippets = new ArrayList<>();
        try (ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                snippets.add(mapRowToSnippet(rs));
            }
        }
        return snippets;
    }

    private List<Snippet> executeQuery(Statement stmt, String sql) throws SQLException {
        List<Snippet> snippets = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                snippets.add(mapRowToSnippet(rs));
            }
        }
        return snippets;
    }

    private List<Snippet> executeQuery(Statement stmt) throws SQLException {
        List<Snippet> snippets = new ArrayList<>();
        try (ResultSet rs = stmt.getResultSet()) {
            if (rs != null) {
                while (rs.next()) {
                    snippets.add(mapRowToSnippet(rs));
                }
            }
        }
        return snippets;
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
    public List<Snippet> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT * FROM snippets WHERE id IN (" + placeholders + ") AND deleted = 0";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                pstmt.setString(i + 1, ids.get(i));
            }
            return executeQuery(pstmt);
        } catch (SQLException e) {
            logger.error("Failed to find snippets by IDs", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Snippet> findByPreferenceId(String preferenceId) {
        String sql = "SELECT * FROM snippets WHERE preference_id = ? AND deleted = 0 ORDER BY created_at DESC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, preferenceId);
            return executeQuery(pstmt);
        } catch (SQLException e) {
            logger.error("Failed to find snippets by preference ID", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Snippet> findByPreferenceIdAndTimeRange(String preferenceId, long startTime, long endTime) {
        String sql = "SELECT * FROM snippets WHERE preference_id = ? AND deleted = 0 " +
                     "AND created_at >= ? AND created_at <= ? ORDER BY created_at DESC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, preferenceId);
            pstmt.setLong(2, startTime);
            pstmt.setLong(3, endTime);
            return executeQuery(pstmt);
        } catch (SQLException e) {
            logger.error("Failed to find snippets by preference ID and time range", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Snippet> findByResourceIds(List<String> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Collections.emptyList();
        }

        String placeholders = String.join(",", Collections.nCopies(resourceIds.size(), "?"));
        String sql = "SELECT * FROM snippets WHERE resource_id IN (" + placeholders + ") AND deleted = 0";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < resourceIds.size(); i++) {
                pstmt.setString(i + 1, resourceIds.get(i));
            }
            return executeQuery(pstmt);
        } catch (SQLException e) {
            logger.error("Failed to find snippets by resource IDs", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Snippet> searchByKeyword(String keyword, int topK) {
        String sql = "SELECT * FROM snippets WHERE (summary LIKE ? OR content LIKE ?) AND deleted = 0 " +
                     "ORDER BY created_at DESC LIMIT ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            String searchTerm = "%" + keyword + "%";
            pstmt.setString(1, searchTerm);
            pstmt.setString(2, searchTerm);
            pstmt.setInt(3, topK);
            return executeQuery(pstmt);
        } catch (SQLException e) {
            logger.error("Failed to search snippets by keyword", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Snippet> searchByKeywordAndPreference(String keyword, String preferenceId, int topK) {
        String sql = "SELECT * FROM snippets WHERE preference_id = ? AND " +
                     "(summary LIKE ? OR content LIKE ?) AND deleted = 0 " +
                     "ORDER BY created_at DESC LIMIT ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            String searchTerm = "%" + keyword + "%";
            pstmt.setString(1, preferenceId);
            pstmt.setString(2, searchTerm);
            pstmt.setString(3, searchTerm);
            pstmt.setInt(4, topK);
            return executeQuery(pstmt);
        } catch (SQLException e) {
            logger.error("Failed to search snippets by keyword and preference", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Snippet> findByTimeRange(long startTime, long endTime, int topK) {
        String sql = "SELECT * FROM snippets WHERE deleted = 0 " +
                     "AND created_at >= ? AND created_at <= ? " +
                     "ORDER BY created_at DESC LIMIT ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, startTime);
            pstmt.setLong(2, endTime);
            pstmt.setInt(3, topK);
            return executeQuery(pstmt);
        } catch (SQLException e) {
            logger.error("Failed to find snippets by time range", e);
            return Collections.emptyList();
        }
    }
}
