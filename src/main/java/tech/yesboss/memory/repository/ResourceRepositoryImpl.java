package tech.yesboss.memory.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.memory.model.Resource;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JDBC-based implementation of ResourceRepository.
 *
 * Features:
 * - CRUD operations with validation
 * - Batch operations (max 100 per batch)
 * - Pagination support
 * - Index-optimized queries
 * - Transaction support
 *
 * Note: Simplified implementation using JDBC (not Spring Data JPA)
 * to match the project's architecture.
 */
public class ResourceRepositoryImpl implements ResourceRepository {

    private static final Logger logger = LoggerFactory.getLogger(ResourceRepositoryImpl.class);

    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 100;

    private final DataSource dataSource;

    public ResourceRepositoryImpl(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource cannot be null");
        }
        this.dataSource = dataSource;
        logger.info("ResourceRepositoryImpl initialized");
    }

    @Override
    public Resource save(Resource resource) {
        validateResource(resource);

        // Generate ID if not present
        if (resource.getId() == null || resource.getId().isEmpty()) {
            resource.setId(UUID.randomUUID().toString());
        }

        // Set timestamps
        LocalDateTime now = LocalDateTime.now();
        if (resource.getCreatedAt() == null) {
            resource.setCreatedAt(now);
        }
        resource.setUpdatedAt(now);

        String sql = """
            INSERT INTO resources (id, conversation_id, session_id, content, abstract,
                                   embedding, message_count, deleted, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, resource.getId());
            pstmt.setString(2, resource.getConversationId());
            pstmt.setString(3, resource.getSessionId());
            pstmt.setString(4, resource.getContent());
            pstmt.setString(5, resource.getAbstract());
            pstmt.setBytes(6, resource.getEmbedding());
            pstmt.setInt(7, resource.getMessageCount());
            pstmt.setBoolean(8, resource.isDeleted());
            pstmt.setLong(9, localDateTimeToMillis(resource.getCreatedAt()));
            pstmt.setLong(10, localDateTimeToMillis(resource.getUpdatedAt()));

            pstmt.executeUpdate();
            logger.debug("Saved resource: {}", resource.getId());

        } catch (SQLException e) {
            logger.error("Failed to save resource: {}", resource.getId(), e);
            throw new RuntimeException("Failed to save resource", e);
        }

        return resource;
    }

    @Override
    public List<Resource> saveAll(List<Resource> resources) {
        if (resources == null || resources.isEmpty()) {
            return List.of();
        }

        // Split into batches if exceeds max batch size
        if (resources.size() > MAX_BATCH_SIZE) {
            return saveBatchSplit(resources);
        }

        String sql = """
            INSERT INTO resources (id, conversation_id, session_id, content, abstract,
                                   embedding, message_count, deleted, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (Resource resource : resources) {
                    validateResource(resource);

                    // Generate ID if not present
                    if (resource.getId() == null || resource.getId().isEmpty()) {
                        resource.setId(UUID.randomUUID().toString());
                    }

                    // Set timestamps
                    LocalDateTime now = LocalDateTime.now();
                    if (resource.getCreatedAt() == null) {
                        resource.setCreatedAt(now);
                    }
                    resource.setUpdatedAt(now);

                    pstmt.setString(1, resource.getId());
                    pstmt.setString(2, resource.getConversationId());
                    pstmt.setString(3, resource.getSessionId());
                    pstmt.setString(4, resource.getContent());
                    pstmt.setString(5, resource.getAbstract());
                    pstmt.setBytes(6, resource.getEmbedding());
                    pstmt.setInt(7, resource.getMessageCount());
                    pstmt.setBoolean(8, resource.isDeleted());
                    pstmt.setLong(9, localDateTimeToMillis(resource.getCreatedAt()));
                    pstmt.setLong(10, localDateTimeToMillis(resource.getUpdatedAt()));
                    pstmt.addBatch();
                }

                pstmt.executeBatch();
                conn.commit();

                logger.debug("Saved {} resources in batch", resources.size());
                return new ArrayList<>(resources);

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            logger.error("Failed to save batch of resources", e);
            throw new RuntimeException("Failed to save batch of resources", e);
        }
    }

    private List<Resource> saveBatchSplit(List<Resource> resources) {
        List<Resource> allSaved = new ArrayList<>();
        int start = 0;

        while (start < resources.size()) {
            int end = Math.min(start + MAX_BATCH_SIZE, resources.size());
            List<Resource> batch = resources.subList(start, end);
            allSaved.addAll(saveAll(batch));
            start = end;
        }

        return allSaved;
    }

    @Override
    public Optional<Resource> findById(String id) {
        if (id == null || id.isEmpty()) {
            return Optional.empty();
        }

        String sql = "SELECT * FROM resources WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToResource(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to find resource by ID: {}", id, e);
        }

        return Optional.empty();
    }

    @Override
    public List<Resource> findByConversationId(String conversationId) {
        String sql = "SELECT * FROM resources WHERE conversation_id = ? ORDER BY created_at DESC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, conversationId);
            return executeQuery(pstmt);

        } catch (SQLException e) {
            logger.error("Failed to find resources by conversation ID: {}", conversationId, e);
            return List.of();
        }
    }

    @Override
    public List<Resource> findByConversationId(String conversationId, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }

        String sql = """
            SELECT * FROM resources
            WHERE conversation_id = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, conversationId);
            pstmt.setInt(2, size);
            pstmt.setInt(3, page * size);
            return executeQuery(pstmt);

        } catch (SQLException e) {
            logger.error("Failed to find resources by conversation ID with pagination", e);
            return List.of();
        }
    }

    @Override
    public List<Resource> findBySessionId(String sessionId) {
        String sql = "SELECT * FROM resources WHERE session_id = ? ORDER BY created_at DESC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, sessionId);
            return executeQuery(pstmt);

        } catch (SQLException e) {
            logger.error("Failed to find resources by session ID: {}", sessionId, e);
            return List.of();
        }
    }

    @Override
    public List<Resource> findResourcesWithoutEmbedding() {
        String sql = """
            SELECT * FROM resources
            WHERE embedding IS NULL
            ORDER BY created_at ASC
            LIMIT 1000
            """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            return executeQuery(stmt);

        } catch (SQLException e) {
            logger.error("Failed to find resources without embedding", e);
            return List.of();
        }
    }

    @Override
    public List<Resource> findByTimeRange(long startTime, long endTime) {
        String sql = """
            SELECT * FROM resources
            WHERE created_at >= ? AND created_at <= ?
            ORDER BY created_at DESC
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, startTime);
            pstmt.setLong(2, endTime);
            return executeQuery(pstmt);

        } catch (SQLException e) {
            logger.error("Failed to find resources by time range", e);
            return List.of();
        }
    }

    @Override
    public Resource update(Resource resource) {
        validateResource(resource);

        if (resource.getId() == null || resource.getId().isEmpty()) {
            throw new IllegalArgumentException("Resource ID cannot be null or empty for update");
        }

        resource.setUpdatedAt(LocalDateTime.now());

        String sql = """
            UPDATE resources
            SET content = ?, abstract = ?, embedding = ?, message_count = ?,
                deleted = ?, updated_at = ?
            WHERE id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, resource.getContent());
            pstmt.setString(2, resource.getAbstract());
            pstmt.setBytes(3, resource.getEmbedding());
            pstmt.setInt(4, resource.getMessageCount());
            pstmt.setBoolean(5, resource.isDeleted());
            pstmt.setLong(6, localDateTimeToMillis(resource.getUpdatedAt()));
            pstmt.setString(7, resource.getId());

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows == 0) {
                logger.warn("No resource found to update: {}", resource.getId());
            }

            return resource;

        } catch (SQLException e) {
            logger.error("Failed to update resource: {}", resource.getId(), e);
            throw new RuntimeException("Failed to update resource", e);
        }
    }

    @Override
    public boolean deleteById(String id) {
        String sql = "DELETE FROM resources WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, id);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;

        } catch (SQLException e) {
            logger.error("Failed to delete resource: {}", id, e);
            return false;
        }
    }

    @Override
    public int deleteAll(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        String sql = "DELETE FROM resources WHERE id = ?";

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
            logger.error("Failed to delete resources in batch", e);
            return 0;
        }
    }

    @Override
    public int deleteBySessionId(String sessionId) {
        String sql = "DELETE FROM resources WHERE session_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, sessionId);
            return pstmt.executeUpdate();

        } catch (SQLException e) {
            logger.error("Failed to delete resources by session ID: {}", sessionId, e);
            return 0;
        }
    }

    @Override
    public boolean existsById(String id) {
        String sql = "SELECT 1 FROM resources WHERE id = ? LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            logger.error("Failed to check resource existence: {}", id, e);
            return false;
        }
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM resources";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to count resources", e);
        }

        return 0;
    }

    @Override
    public long countByConversationId(String conversationId) {
        String sql = "SELECT COUNT(*) FROM resources WHERE conversation_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, conversationId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to count resources by conversation ID", e);
        }

        return 0;
    }

    @Override
    public long countBySessionId(String sessionId) {
        String sql = "SELECT COUNT(*) FROM resources WHERE session_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, sessionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to count resources by session ID", e);
        }

        return 0;
    }

    @Override
    public void clear() {
        String sql = "DELETE FROM resources";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(sql);
            logger.info("Cleared all resources");

        } catch (SQLException e) {
            logger.error("Failed to clear resources", e);
            throw new RuntimeException("Failed to clear resources", e);
        }
    }

    // ==========================================
    // Utility Methods
    // ==========================================

    private void validateResource(Resource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("Resource cannot be null");
        }
        if (resource.getConversationId() == null || resource.getConversationId().isEmpty()) {
            throw new IllegalArgumentException("conversationId cannot be null or empty");
        }
        if (resource.getSessionId() == null || resource.getSessionId().isEmpty()) {
            throw new IllegalArgumentException("sessionId cannot be null or empty");
        }
        if (resource.getContent() == null || resource.getContent().isEmpty()) {
            throw new IllegalArgumentException("content cannot be null or empty");
        }
    }

    private Resource mapRowToResource(ResultSet rs) throws SQLException {
        Resource resource = new Resource();
        resource.setId(rs.getString("id"));
        resource.setConversationId(rs.getString("conversation_id"));
        resource.setSessionId(rs.getString("session_id"));
        resource.setContent(rs.getString("content"));
        resource.setAbstract(rs.getString("abstract"));
        resource.setEmbedding(rs.getBytes("embedding"));
        resource.setMessageCount(rs.getInt("message_count"));
        resource.setDeleted(rs.getBoolean("deleted"));
        resource.setCreatedAt(millisToLocalDateTime(rs.getLong("created_at")));
        resource.setUpdatedAt(millisToLocalDateTime(rs.getLong("updated_at")));
        return resource;
    }

    private List<Resource> executeQuery(PreparedStatement pstmt) throws SQLException {
        List<Resource> resources = new ArrayList<>();
        try (ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                resources.add(mapRowToResource(rs));
            }
        }
        return resources;
    }

    private List<Resource> executeQuery(Statement stmt, String sql) throws SQLException {
        List<Resource> resources = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                resources.add(mapRowToResource(rs));
            }
        }
        return resources;
    }

    private List<Resource> executeQuery(Statement stmt) throws SQLException {
        List<Resource> resources = new ArrayList<>();
        try (ResultSet rs = stmt.getResultSet()) {
            if (rs != null) {
                while (rs.next()) {
                    resources.add(mapRowToResource(rs));
                }
            }
        }
        return resources;
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
}
