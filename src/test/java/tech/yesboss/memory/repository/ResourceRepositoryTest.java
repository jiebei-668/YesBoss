package tech.yesboss.memory.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tech.yesboss.memory.model.Resource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ResourceRepository.
 *
 * Tests include:
 * - Repository interface contract validation
 * - CRUD operations (save, findById, update, delete)
 * - Batch operations (saveAll, deleteAll)
 * - Pagination support
 * - Conversation and session-based queries
 * - Embedding-related queries
 * - Boundary conditions and edge cases
 * - Performance benchmarks
 */
@DisplayName("ResourceRepository Unit Tests")
class ResourceRepositoryTest {

    private static final String DB_URL = "jdbc:sqlite:/tmp/test_resource.db";
    private SimpleDataSource dataSource;
    private ResourceRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = new SimpleDataSource(DB_URL);
        initializeTables();
        repository = new ResourceRepositoryImpl(dataSource);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
        // Clean up test database file
        try {
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get("/tmp/test_resource.db"));
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    private void initializeTables() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // Create resources table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS resources (
                    id TEXT PRIMARY KEY,
                    conversation_id TEXT NOT NULL,
                    session_id TEXT NOT NULL,
                    content TEXT NOT NULL,
                    abstract TEXT,
                    embedding BLOB,
                    message_count INTEGER DEFAULT 0,
                    deleted INTEGER DEFAULT 0 CHECK(deleted IN (0, 1)),
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                );
            """);
            // Create indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS resource_conversation_idx ON resources(conversation_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS resource_session_idx ON resources(session_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS resource_embedding_null_idx ON resources(embedding) WHERE embedding IS NULL;");
            stmt.execute("CREATE INDEX IF NOT EXISTS resource_created_at_idx ON resources(created_at);");
            // Enable foreign keys
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
    }

    // ==========================================
    // Test Data Preparation
    // ==========================================

    private Resource createValidResource() {
        return new Resource(
                "conversation-" + System.currentTimeMillis(),
                "session-" + System.currentTimeMillis(),
                "Test content for resource"
        );
    }

    private Resource createResourceWithConversation(String conversationId) {
        return new Resource(
                conversationId,
                "session-" + System.currentTimeMillis(),
                "Content for conversation " + conversationId
        );
    }

    private Resource createResourceWithSession(String sessionId) {
        return new Resource(
                "conversation-" + System.currentTimeMillis(),
                sessionId,
                "Content for session " + sessionId
        );
    }

    private byte[] createEmbedding() {
        float[] embedding = new float[1536];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = (float) Math.random();
        }
        // Convert to byte array
        byte[] bytes = new byte[embedding.length * 4];
        for (int i = 0; i < embedding.length; i++) {
            int bits = Float.floatToIntBits(embedding[i]);
            bytes[i * 4] = (byte) (bits >> 24);
            bytes[i * 4 + 1] = (byte) (bits >> 16);
            bytes[i * 4 + 2] = (byte) (bits >> 8);
            bytes[i * 4 + 3] = (byte) bits;
        }
        return bytes;
    }

    // ==========================================
    // Interface Contract Tests
    // ==========================================

    @Test
    @DisplayName("save() should return Resource with generated ID")
    void testSaveReturnsResourceWithGeneratedId() {
        Resource resource = createValidResource();
        Resource saved = repository.save(resource);

        assertNotNull(saved, "save() should return non-null Resource");
        assertNotNull(saved.getId(), "saved resource should have ID");
        assertTrue(saved.getId().length() > 0, "ID should not be empty");
        assertNotNull(saved.getCreatedAt(), "saved resource should have createdAt");
        assertNotNull(saved.getUpdatedAt(), "saved resource should have updatedAt");
    }

    @Test
    @DisplayName("findById() should return Optional<Resource>")
    void testFindByIdReturnsOptional() {
        Resource resource = createValidResource();
        repository.save(resource);

        Optional<Resource> result = repository.findById(resource.getId());

        assertTrue(result.isPresent(), "findById should return non-empty Optional");
        assertEquals(resource.getId(), result.get().getId(), "ID should match");
    }

    @Test
    @DisplayName("findByConversationId() should return List<Resource>")
    void testFindByConversationIdReturnsList() {
        String conversationId = "conversation-123";
        Resource resource = createResourceWithConversation(conversationId);
        repository.save(resource);

        List<Resource> results = repository.findByConversationId(conversationId);

        assertNotNull(results, "findByConversationId should return non-null list");
        assertEquals(1, results.size(), "should return 1 resource");
        assertEquals(conversationId, results.get(0).getConversationId(), "conversationId should match");
    }

    @Test
    @DisplayName("deleteById() should return boolean")
    void testDeleteByIdReturnsBoolean() {
        Resource resource = createValidResource();
        repository.save(resource);

        boolean deleted = repository.deleteById(resource.getId());

        assertTrue(deleted, "deleteById should return true on success");
        assertFalse(repository.existsById(resource.getId()), "resource should not exist after delete");
    }

    // ==========================================
    // CRUD Operation Tests
    // ==========================================

    @Test
    @DisplayName("save() should auto-generate UUID for ID")
    void testSaveAutoGeneratesId() {
        Resource resource = new Resource();
        resource.setConversationId("conversation-123");
        resource.setSessionId("session-456");
        resource.setContent("Test content");

        Resource saved = repository.save(resource);

        assertNotNull(saved.getId(), "ID should be auto-generated");
        assertTrue(saved.getId().matches("[a-f0-9\\-]{36}"), "ID should be UUID format");
    }

    @Test
    @DisplayName("save() should throw IllegalArgumentException for null resource")
    void testSaveThrowsForNullResource() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
    }

    @Test
    @DisplayName("save() should throw IllegalArgumentException for null conversationId")
    void testSaveThrowsForNullConversationId() {
        Resource resource = new Resource();
        resource.setConversationId(null);
        resource.setSessionId("session-456");
        resource.setContent("Test content");

        assertThrows(IllegalArgumentException.class, () -> repository.save(resource));
    }

    @Test
    @DisplayName("save() should throw IllegalArgumentException for null sessionId")
    void testSaveThrowsForNullSessionId() {
        Resource resource = new Resource();
        resource.setConversationId("conversation-123");
        resource.setSessionId(null);
        resource.setContent("Test content");

        assertThrows(IllegalArgumentException.class, () -> repository.save(resource));
    }

    @Test
    @DisplayName("save() should throw IllegalArgumentException for null content")
    void testSaveThrowsForNullContent() {
        Resource resource = new Resource();
        resource.setConversationId("conversation-123");
        resource.setSessionId("session-456");
        resource.setContent(null);

        assertThrows(IllegalArgumentException.class, () -> repository.save(resource));
    }

    // ==========================================
    // Pagination Tests
    // ==========================================

    @Test
    @DisplayName("findByConversationId() with pagination should respect page and size")
    void testFindByConversationIdWithPagination() {
        String conversationId = "conversation-123";
        for (int i = 0; i < 15; i++) {
            repository.save(createResourceWithConversation(conversationId));
        }

        List<Resource> page0 = repository.findByConversationId(conversationId, 0, 10);
        List<Resource> page1 = repository.findByConversationId(conversationId, 1, 10);

        assertEquals(10, page0.size(), "page 0 should have 10 results");
        assertEquals(5, page1.size(), "page 1 should have 5 results");
    }

    @Test
    @DisplayName("findByConversationId() pagination should throw for invalid page")
    void testFindByConversationIdThrowsForInvalidPage() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.findByConversationId("conversation-123", -1, 10));
    }

    @Test
    @DisplayName("findByConversationId() pagination should throw for invalid size")
    void testFindByConversationIdThrowsForInvalidSize() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.findByConversationId("conversation-123", 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> repository.findByConversationId("conversation-123", 0, 101));
    }

    // ==========================================
    // Session-based Query Tests
    // ==========================================

    @Test
    @DisplayName("findBySessionId() should retrieve resources by session")
    void testFindBySessionIdRetrievesBySession() {
        String sessionId = "session-123";
        repository.save(createResourceWithSession(sessionId));
        repository.save(createResourceWithSession(sessionId));
        repository.save(createResourceWithSession("other-session"));

        List<Resource> results = repository.findBySessionId(sessionId);

        assertEquals(2, results.size(), "should find 2 resources");
        assertTrue(results.stream().allMatch(r -> sessionId.equals(r.getSessionId())),
                "all resources should have matching sessionId");
    }

    // ==========================================
    // Embedding Query Tests
    // ==========================================

    @Test
    @DisplayName("findResourcesWithoutEmbedding() should retrieve resources without embeddings")
    void testFindResourcesWithoutEmbedding() {
        Resource withEmbedding = createValidResource();
        withEmbedding.setEmbedding(createEmbedding());
        repository.save(withEmbedding);

        Resource withoutEmbedding = createValidResource();
        repository.save(withoutEmbedding);

        List<Resource> results = repository.findResourcesWithoutEmbedding();

        assertEquals(1, results.size(), "should find 1 resource without embedding");
        assertFalse(results.get(0).hasEmbedding(), "resource should not have embedding");
    }

    // ==========================================
    // Time Range Query Tests
    // ==========================================

    @Test
    @DisplayName("findByTimeRange() should retrieve resources within time range")
    void testFindByTimeRangeRetrievesInRange() {
        long now = System.currentTimeMillis();
        long hourAgo = now - 3600000;
        long hourInFuture = now + 3600000;

        Resource resource = repository.save(createValidResource());

        List<Resource> results = repository.findByTimeRange(hourAgo, hourInFuture);

        assertEquals(1, results.size(), "should find 1 resource in time range");
        assertEquals(resource.getId(), results.get(0).getId(), "ID should match");
    }

    // ==========================================
    // Batch Operations Tests
    // ==========================================

    @Test
    @DisplayName("saveAll() should save multiple resources in batch")
    void testSaveAllSavesMultipleResources() {
        List<Resource> resources = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            resources.add(createValidResource());
        }

        List<Resource> saved = repository.saveAll(resources);

        assertEquals(10, saved.size(), "should save 10 resources");
        assertEquals(10, repository.count(), "count should be 10");
    }

    @Test
    @DisplayName("saveAll() should auto-split large batches")
    void testSaveAllAutoSplitsLargeBatches() {
        List<Resource> resources = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            resources.add(createValidResource());
        }

        List<Resource> saved = repository.saveAll(resources);

        assertEquals(250, saved.size(), "should save 250 resources");
        assertEquals(250, repository.count(), "count should be 250");
    }

    @Test
    @DisplayName("deleteBySessionId() should delete all resources for session")
    void testDeleteBySessionIdDeletesAllResources() {
        String sessionId = "session-123";
        repository.save(createResourceWithSession(sessionId));
        repository.save(createResourceWithSession(sessionId));
        repository.save(createResourceWithSession("other-session"));

        int deleted = repository.deleteBySessionId(sessionId);

        assertEquals(2, deleted, "should delete 2 resources");
        assertEquals(1, repository.count(), "count should be 1");
    }

    // ==========================================
    // Update Method Tests
    // ==========================================

    @Test
    @DisplayName("update() should update resource fields")
    void testUpdateUpdatesResourceFields() {
        Resource resource = repository.save(createValidResource());
        resource.setContent("Updated content");
        resource.setAbstract("Updated abstract");

        Resource updated = repository.update(resource);

        assertEquals("Updated content", updated.getContent(), "content should be updated");
        assertEquals("Updated abstract", updated.getAbstract(), "abstract should be updated");
    }

    @Test
    @DisplayName("update() should throw for resource without ID")
    void testUpdateThrowsForResourceWithoutId() {
        Resource resource = createValidResource();
        resource.setId(null);

        assertThrows(IllegalArgumentException.class, () -> repository.update(resource));
    }

    // ==========================================
    // Count Method Tests
    // ==========================================

    @Test
    @DisplayName("countByConversationId() should return count for conversation")
    void testCountByConversationIdReturnsCount() {
        String conversationId = "conversation-123";
        repository.save(createResourceWithConversation(conversationId));
        repository.save(createResourceWithConversation(conversationId));
        repository.save(createResourceWithConversation("other-conversation"));

        long count = repository.countByConversationId(conversationId);

        assertEquals(2L, count, "count should be 2");
    }

    @Test
    @DisplayName("countBySessionId() should return count for session")
    void testCountBySessionIdReturnsCount() {
        String sessionId = "session-123";
        repository.save(createResourceWithSession(sessionId));
        repository.save(createResourceWithSession(sessionId));
        repository.save(createResourceWithSession("other-session"));

        long count = repository.countBySessionId(sessionId);

        assertEquals(2L, count, "count should be 2");
    }

    @Test
    @DisplayName("existsById() should return true for existing resource")
    void testExistsByIdReturnsTrueForExisting() {
        Resource resource = repository.save(createValidResource());

        assertTrue(repository.existsById(resource.getId()), "should return true for existing ID");
    }

    @Test
    @DisplayName("existsById() should return false for non-existent resource")
    void testExistsByIdReturnsFalseForNonExistent() {
        assertFalse(repository.existsById("non-existent-id"), "should return false for non-existent ID");
    }

    // ==========================================
    // Boundary Conditions Tests
    // ==========================================

    @Test
    @DisplayName("should handle special characters in content")
    void testHandlesSpecialCharactersInContent() {
        Resource resource = new Resource();
        resource.setConversationId("conversation-123");
        resource.setSessionId("session-456");
        resource.setContent("Test with 特殊字符 and emoji 🎉 and \n newlines");

        Resource saved = repository.save(resource);
        Resource found = repository.findById(saved.getId()).orElseThrow();

        assertEquals(resource.getContent(), found.getContent(), "special characters should be preserved");
    }

    @Test
    @DisplayName("should handle long content text")
    void testHandlesLongContent() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            longText.append("word ");
        }

        Resource resource = new Resource();
        resource.setConversationId("conversation-123");
        resource.setSessionId("session-456");
        resource.setContent(longText.toString());

        Resource saved = repository.save(resource);
        Resource found = repository.findById(saved.getId()).orElseThrow();

        assertEquals(longText.toString(), found.getContent(), "long content should be preserved");
    }

    @Test
    @DisplayName("should handle embedding byte arrays")
    void testHandlesEmbeddingByteArrays() {
        Resource resource = createValidResource();
        byte[] embedding = createEmbedding();
        resource.setEmbedding(embedding);

        Resource saved = repository.save(resource);
        Resource found = repository.findById(saved.getId()).orElseThrow();

        assertArrayEquals(embedding, found.getEmbedding(), "embedding should be preserved");
    }

    @Test
    @DisplayName("should handle message count")
    void testHandlesMessageCount() {
        Resource resource = createValidResource();
        resource.setMessageCount(42);

        Resource saved = repository.save(resource);
        Resource found = repository.findById(saved.getId()).orElseThrow();

        assertEquals(42, found.getMessageCount(), "message count should be preserved");
    }

    @Test
    @DisplayName("should handle deleted flag")
    void testHandlesDeletedFlag() {
        Resource resource = createValidResource();
        resource.setDeleted(true);

        Resource saved = repository.save(resource);
        Resource found = repository.findById(saved.getId()).orElseThrow();

        assertTrue(found.isDeleted(), "deleted flag should be preserved");
    }

    // ==========================================
    // Performance Benchmark Tests
    // ==========================================

    @Test
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    @DisplayName("Performance: save() should complete within 100ms")
    void testPerformanceSave() {
        repository.save(createValidResource());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    @DisplayName("Performance: saveAll(250) should complete within 3s")
    void testPerformanceSaveAll250() {
        List<Resource> resources = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            resources.add(createValidResource());
        }

        repository.saveAll(resources);
        assertEquals(250, repository.count(), "should save 250 resources");
    }

    @Test
    @Timeout(value = 200, unit = TimeUnit.MILLISECONDS)
    @DisplayName("Performance: findById() should complete within 200ms")
    void testPerformanceFindById() {
        Resource resource = repository.save(createValidResource());
        repository.findById(resource.getId());
    }

    // ==========================================
    // Concurrent Access Tests
    // ==========================================

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Concurrent: should handle 10 concurrent operations")
    void testConcurrentOperations() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    repository.save(createValidResource());
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount, successCount.get(), "all operations should succeed");
    }

    // ==========================================
    // Clear Method Tests
    // ==========================================

    @Test
    @DisplayName("clear() should delete all resources")
    void testClearDeletesAllResources() {
        repository.save(createValidResource());
        repository.save(createValidResource());
        repository.save(createValidResource());

        repository.clear();

        assertEquals(0, repository.count(), "count should be 0 after clear");
    }

    // ==========================================
    // Utility Classes
    // ==========================================

    /**
     * Simple DataSource implementation for testing.
     */
    private static class SimpleDataSource implements javax.sql.DataSource {
        private final String url;
        private volatile boolean closed = false;

        SimpleDataSource(String url) {
            this.url = url;
        }

        @Override
        public Connection getConnection() throws SQLException {
            if (closed) {
                throw new SQLException("DataSource is closed");
            }
            return DriverManager.getConnection(url);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            if (closed) {
                throw new SQLException("DataSource is closed");
            }
            return DriverManager.getConnection(url, username, password);
        }

        public void close() {
            closed = true;
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return null;
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
