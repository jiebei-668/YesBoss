package tech.yesboss.memory.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tech.yesboss.memory.model.Snippet;

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
 * Comprehensive unit tests for SnippetRepository.
 *
 * Tests include:
 * - Repository interface contract validation
 * - CRUD operations (save, findById, update, delete)
 * - Batch operations (saveAll, deleteAll)
 * - Pagination support
 * - Memory type classification queries
 * - Three-tier architecture association
 * - Boundary conditions and edge cases
 * - Performance benchmarks
 */
@DisplayName("SnippetRepository Unit Tests")
class SnippetRepositoryTest {

    private static final String DB_URL = "jdbc:sqlite:/tmp/test_snippet.db";
    private SimpleDataSource dataSource;
    private SnippetRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = new SimpleDataSource(DB_URL);
        initializeTables();
        repository = new SnippetRepositoryImpl(dataSource);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
        // Clean up test database file
        try {
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get("/tmp/test_snippet.db"));
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    private void initializeTables() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // Create snippets table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS snippets (
                    id TEXT PRIMARY KEY,
                    resource_id TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    memory_type TEXT NOT NULL CHECK(memory_type IN ('PROFILE', 'EVENT', 'KNOWLEDGE', 'BEHAVIOR', 'SKILL', 'TOOL')),
                    embedding BLOB,
                    timestamp INTEGER NOT NULL,
                    deleted INTEGER DEFAULT 0 CHECK(deleted IN (0, 1)),
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                );
            """);
            // Create indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS snippet_resource_idx ON snippets(resource_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS snippet_memory_type_idx ON snippets(memory_type);");
            stmt.execute("CREATE INDEX IF NOT EXISTS snippet_embedding_null_idx ON snippets(embedding) WHERE embedding IS NULL;");
            stmt.execute("CREATE INDEX IF NOT EXISTS snippet_timestamp_idx ON snippets(timestamp);");
            // Enable foreign keys
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
    }

    // ==========================================
    // Test Data Preparation
    // ==========================================

    private Snippet createValidSnippet() {
        return new Snippet(
                "resource-" + System.currentTimeMillis(),
                "Test summary for snippet",
                Snippet.MemoryType.KNOWLEDGE
        );
    }

    private Snippet createSnippetWithType(Snippet.MemoryType type) {
        return new Snippet(
                "resource-" + System.currentTimeMillis(),
                "Summary for " + type + " snippet",
                type
        );
    }

    private Snippet createSnippetWithResource(String resourceId) {
        return new Snippet(
                resourceId,
                "Summary for resource " + resourceId,
                Snippet.MemoryType.EVENT
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
    @DisplayName("save() should return Snippet with generated ID")
    void testSaveReturnsSnippetWithGeneratedId() {
        Snippet snippet = createValidSnippet();
        Snippet saved = repository.save(snippet);

        assertNotNull(saved, "save() should return non-null Snippet");
        assertNotNull(saved.getId(), "saved snippet should have ID");
        assertTrue(saved.getId().length() > 0, "ID should not be empty");
        assertNotNull(saved.getCreatedAt(), "saved snippet should have createdAt");
        assertNotNull(saved.getUpdatedAt(), "saved snippet should have updatedAt");
    }

    @Test
    @DisplayName("findById() should return Optional<Snippet>")
    void testFindByIdReturnsOptional() {
        Snippet snippet = createValidSnippet();
        repository.save(snippet);

        Optional<Snippet> result = repository.findById(snippet.getId());

        assertTrue(result.isPresent(), "findById should return non-empty Optional");
        assertEquals(snippet.getId(), result.get().getId(), "ID should match");
    }

    @Test
    @DisplayName("findByResourceId() should return List<Snippet>")
    void testFindByResourceIdReturnsList() {
        String resourceId = "resource-123";
        Snippet snippet = createSnippetWithResource(resourceId);
        repository.save(snippet);

        List<Snippet> results = repository.findByResourceId(resourceId);

        assertNotNull(results, "findByResourceId should return non-null list");
        assertEquals(1, results.size(), "should return 1 snippet");
        assertEquals(resourceId, results.get(0).getResourceId(), "resourceId should match");
    }

    @Test
    @DisplayName("deleteById() should return boolean")
    void testDeleteByIdReturnsBoolean() {
        Snippet snippet = createValidSnippet();
        repository.save(snippet);

        boolean deleted = repository.deleteById(snippet.getId());

        assertTrue(deleted, "deleteById should return true on success");
        assertFalse(repository.existsById(snippet.getId()), "snippet should not exist after delete");
    }

    // ==========================================
    // CRUD Operation Tests
    // ==========================================

    @Test
    @DisplayName("save() should auto-generate UUID for ID")
    void testSaveAutoGeneratesId() {
        Snippet snippet = new Snippet();
        snippet.setResourceId("resource-123");
        snippet.setSummary("Test summary");
        snippet.setMemoryType(Snippet.MemoryType.KNOWLEDGE);

        Snippet saved = repository.save(snippet);

        assertNotNull(saved.getId(), "ID should be auto-generated");
        assertTrue(saved.getId().matches("[a-f0-9\\-]{36}"), "ID should be UUID format");
    }

    @Test
    @DisplayName("save() should throw IllegalArgumentException for null snippet")
    void testSaveThrowsForNullSnippet() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
    }

    @Test
    @DisplayName("save() should throw IllegalArgumentException for null resourceId")
    void testSaveThrowsForNullResourceId() {
        Snippet snippet = new Snippet();
        snippet.setResourceId(null);
        snippet.setSummary("Test");
        snippet.setMemoryType(Snippet.MemoryType.KNOWLEDGE);

        assertThrows(IllegalArgumentException.class, () -> repository.save(snippet));
    }

    // ==========================================
    // Pagination Tests
    // ==========================================

    @Test
    @DisplayName("findByResourceId() with pagination should respect page and size")
    void testFindByResourceIdWithPagination() {
        String resourceId = "resource-123";
        for (int i = 0; i < 15; i++) {
            repository.save(createSnippetWithResource(resourceId));
        }

        List<Snippet> page0 = repository.findByResourceId(resourceId, 0, 10);
        List<Snippet> page1 = repository.findByResourceId(resourceId, 1, 10);

        assertEquals(10, page0.size(), "page 0 should have 10 results");
        assertEquals(5, page1.size(), "page 1 should have 5 results");
    }

    @Test
    @DisplayName("findByMemoryType() with pagination should respect page and size")
    void testFindByMemoryTypeWithPagination() {
        for (int i = 0; i < 15; i++) {
            repository.save(createSnippetWithType(Snippet.MemoryType.EVENT));
        }

        List<Snippet> page0 = repository.findByMemoryType(Snippet.MemoryType.EVENT, 0, 10);
        List<Snippet> page1 = repository.findByMemoryType(Snippet.MemoryType.EVENT, 1, 10);

        assertEquals(10, page0.size(), "page 0 should have 10 results");
        assertEquals(5, page1.size(), "page 1 should have 5 results");
    }

    // ==========================================
    // Memory Type Classification Tests
    // ==========================================

    @Test
    @DisplayName("findByMemoryType() should retrieve snippets by type")
    void testFindByMemoryTypeRetrievesByType() {
        repository.save(createSnippetWithType(Snippet.MemoryType.PROFILE));
        repository.save(createSnippetWithType(Snippet.MemoryType.PROFILE));
        repository.save(createSnippetWithType(Snippet.MemoryType.KNOWLEDGE));

        List<Snippet> profiles = repository.findByMemoryType(Snippet.MemoryType.PROFILE);
        List<Snippet> knowledge = repository.findByMemoryType(Snippet.MemoryType.KNOWLEDGE);

        assertEquals(2, profiles.size(), "should find 2 PROFILE snippets");
        assertEquals(1, knowledge.size(), "should find 1 KNOWLEDGE snippet");
    }

    @Test
    @DisplayName("findByMemoryTypes() should retrieve snippets by multiple types")
    void testFindByMemoryTypesRetrievesByMultipleTypes() {
        repository.save(createSnippetWithType(Snippet.MemoryType.PROFILE));
        repository.save(createSnippetWithType(Snippet.MemoryType.KNOWLEDGE));
        repository.save(createSnippetWithType(Snippet.MemoryType.BEHAVIOR));
        repository.save(createSnippetWithType(Snippet.MemoryType.SKILL));

        List<Snippet> results = repository.findByMemoryTypes(
                List.of(Snippet.MemoryType.PROFILE, Snippet.MemoryType.KNOWLEDGE, Snippet.MemoryType.BEHAVIOR)
        );

        assertEquals(3, results.size(), "should find 3 matching snippets");
    }

    // ==========================================
    // Batch Operations Tests
    // ==========================================

    @Test
    @DisplayName("saveAll() should save multiple snippets in batch")
    void testSaveAllSavesMultipleSnippets() {
        List<Snippet> snippets = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            snippets.add(createValidSnippet());
        }

        List<Snippet> saved = repository.saveAll(snippets);

        assertEquals(10, saved.size(), "should save 10 snippets");
        assertEquals(10, repository.count(), "count should be 10");
    }

    @Test
    @DisplayName("saveAll() should auto-split large batches")
    void testSaveAllAutoSplitsLargeBatches() {
        List<Snippet> snippets = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            snippets.add(createValidSnippet());
        }

        List<Snippet> saved = repository.saveAll(snippets);

        assertEquals(250, saved.size(), "should save 250 snippets");
        assertEquals(250, repository.count(), "count should be 250");
    }

    @Test
    @DisplayName("deleteByResourceId() should delete all snippets for resource")
    void testDeleteByResourceIdDeletesAllSnippets() {
        String resourceId = "resource-123";
        repository.save(createSnippetWithResource(resourceId));
        repository.save(createSnippetWithResource(resourceId));
        repository.save(createSnippetWithResource("other-resource"));

        int deleted = repository.deleteByResourceId(resourceId);

        assertEquals(2, deleted, "should delete 2 snippets");
        assertEquals(1, repository.count(), "count should be 1");
    }

    // ==========================================
    // Three-Tier Association Tests
    // ==========================================

    @Test
    @DisplayName("should maintain association with resource via resourceId")
    void testMaintainsResourceAssociation() {
        String resourceId = "resource-123";
        Snippet snippet = repository.save(createSnippetWithResource(resourceId));

        List<Snippet> found = repository.findByResourceId(resourceId);

        assertEquals(1, found.size(), "should find snippet by resource ID");
        assertEquals(resourceId, found.get(0).getResourceId(), "resourceId should match");
    }

    // ==========================================
    // Boundary Conditions Tests
    // ==========================================

    @Test
    @DisplayName("should handle special characters in summary")
    void testHandlesSpecialCharactersInSummary() {
        Snippet snippet = new Snippet();
        snippet.setResourceId("resource-123");
        snippet.setSummary("Test with 特殊字符 and emoji 🎉 and \n newlines");
        snippet.setMemoryType(Snippet.MemoryType.KNOWLEDGE);

        Snippet saved = repository.save(snippet);
        Snippet found = repository.findById(saved.getId()).orElseThrow();

        assertEquals(snippet.getSummary(), found.getSummary(), "special characters should be preserved");
    }

    @Test
    @DisplayName("should handle all memory types")
    void testHandlesAllMemoryTypes() {
        for (Snippet.MemoryType type : Snippet.MemoryType.values()) {
            Snippet snippet = createSnippetWithType(type);
            repository.save(snippet);
        }

        assertEquals(6, repository.count(), "should save all 6 memory types");
    }

    @Test
    @DisplayName("should handle embedding byte arrays")
    void testHandlesEmbeddingByteArrays() {
        Snippet snippet = createValidSnippet();
        byte[] embedding = createEmbedding();
        snippet.setEmbedding(embedding);

        Snippet saved = repository.save(snippet);
        Snippet found = repository.findById(saved.getId()).orElseThrow();

        assertArrayEquals(embedding, found.getEmbedding(), "embedding should be preserved");
    }

    // ==========================================
    // Performance Benchmark Tests
    // ==========================================

    @Test
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    @DisplayName("Performance: save() should complete within 100ms")
    void testPerformanceSave() {
        repository.save(createValidSnippet());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    @DisplayName("Performance: saveAll(250) should complete within 3s")
    void testPerformanceSaveAll250() {
        List<Snippet> snippets = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            snippets.add(createValidSnippet());
        }

        repository.saveAll(snippets);
        assertEquals(250, repository.count(), "should save 250 snippets");
    }

    @Test
    @Timeout(value = 200, unit = TimeUnit.MILLISECONDS)
    @DisplayName("Performance: findById() should complete within 200ms")
    void testPerformanceFindById() {
        Snippet snippet = repository.save(createValidSnippet());
        repository.findById(snippet.getId());
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
                    repository.save(createValidSnippet());
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
    @DisplayName("clear() should delete all snippets")
    void testClearDeletesAllSnippets() {
        repository.save(createValidSnippet());
        repository.save(createValidSnippet());
        repository.save(createValidSnippet());

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
