package tech.yesboss.memory.vectorstore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VectorStore abstraction layer.
 *
 * Tests include:
 * - Interface contract validation
 * - SQLiteVecStore implementation
 * - VectorStoreFactory
 * - Boundary conditions
 * - Performance benchmarks
 */
@DisplayName("VectorStore Abstraction Layer Tests")
class VectorStoreTest {

    private static final String DB_URL = "jdbc:sqlite:/tmp/test_vector.db";
    private SimpleDataSource dataSource;
    private VectorStore vectorStore;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = new SimpleDataSource(DB_URL);
        initializeVectorTable();
        vectorStore = new SQLiteVecStore(dataSource, "test_vectors");
    }

    @AfterEach
    void tearDown() {
        if (vectorStore != null) {
            vectorStore.close();
        }
        if (dataSource != null) {
            dataSource.close();
        }
        // Clean up test database file
        try {
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get("/tmp/test_vector.db"));
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    private void initializeVectorTable() throws SQLException {
        // Table will be created by SQLiteVecStore, we just need to ensure it exists
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS test_vectors (
                    id TEXT PRIMARY KEY,
                    vector BLOB
                );
            """);
            // Enable foreign keys
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
    }

    // ==========================================
    // Test Data Preparation
    // ==========================================

    private float[] createNormalVector() {
        float[] vector = new float[1536];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) Math.random();
        }
        return vector;
    }

    private float[] createZeroVector() {
        return new float[1536];
    }

    private float[] createLargeVector(int dimensions) {
        return new float[dimensions];
    }

    // ==========================================
    // Interface Contract Tests
    // ==========================================

    @Test
    @DisplayName("Should insert vector and return true on success")
    void testInsertReturnsTrueOnSuccess() {
        float[] vector = createNormalVector();
        boolean result = vectorStore.insert("vec-1", vector);
        assertTrue(result, "insert should return true on success");
    }

    @Test
    @DisplayName("Should update existing vector and return true")
    void testUpdateReturnsTrueOnSuccess() {
        float[] vector1 = createNormalVector();
        float[] vector2 = createNormalVector();

        vectorStore.insert("vec-1", vector1);
        boolean result = vectorStore.update("vec-1", vector2);

        assertTrue(result, "update should return true for existing vector");
    }

    @Test
    @DisplayName("Should return false when updating non-existent vector")
    void testUpdateReturnsFalseForNonExistent() {
        float[] vector = createNormalVector();
        boolean result = vectorStore.update("non-existent", vector);
        assertFalse(result, "update should return false for non-existent vector");
    }

    @Test
    @DisplayName("Should search and return List<SearchResult>")
    void testSearchReturnsListOfResults() {
        float[] queryVector = createNormalVector();
        List<VectorStore.SearchResult> results = vectorStore.search(queryVector, 5);

        assertNotNull(results, "search should return non-null list");
        assertTrue(results.size() <= 5, "search should return at most topK results");
    }

    @Test
    @DisplayName("Should delete vector and return true")
    void testDeleteReturnsTrueOnSuccess() {
        float[] vector = createNormalVector();
        vectorStore.insert("vec-1", vector);

        boolean result = vectorStore.delete("vec-1");
        assertTrue(result, "delete should return true on success");
    }

    @Test
    @DisplayName("Should return false when deleting non-existent vector")
    void testDeleteReturnsFalseForNonExistent() {
        boolean result = vectorStore.delete("non-existent");
        assertFalse(result, "delete should return false for non-existent vector");
    }

    // ==========================================
    // SQLiteVecStore Implementation Tests
    // ==========================================

    @Test
    @DisplayName("Should insert single vector")
    void testInsertSingleVector() {
        float[] vector = createNormalVector();
        boolean result = vectorStore.insert("vec-1", vector);

        assertTrue(result);
        assertTrue(vectorStore.exists("vec-1"));
    }

    @Test
    @DisplayName("Should insert batch of 100 vectors")
    void testInsertBatch100() {
        Map<String, float[]> vectors = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            vectors.put("vec-" + i, createNormalVector());
        }

        int count = vectorStore.insertBatch(vectors);
        assertEquals(100, count, "Should insert all 100 vectors");
        assertEquals(100, vectorStore.count());
    }

    @Test
    @DisplayName("Should insert batch of 1000 vectors")
    void testInsertBatch1000() {
        Map<String, float[]> vectors = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            vectors.put("vec-" + i, createNormalVector());
        }

        int count = vectorStore.insertBatch(vectors);
        assertEquals(1000, count, "Should insert all 1000 vectors");
        assertEquals(1000, vectorStore.count());
    }

    @Test
    @DisplayName("Should calculate cosine similarity correctly")
    void testCosineSimilarity() {
        // Create two identical vectors
        float[] vector1 = createNormalVector();
        float[] vector2 = vector1.clone();

        vectorStore.insert("vec-1", vector1);
        vectorStore.insert("vec-2", vector2);

        List<VectorStore.SearchResult> results = vectorStore.search(vector1, 1);

        assertFalse(results.isEmpty());
        // Cosine similarity of identical vectors should be close to 1.0
        assertTrue(results.get(0).score() > 0.99f, "Similarity should be > 0.99 for identical vectors");
    }

    @Test
    @DisplayName("Should return topK results in order")
    void testSearchReturnsTopKOrdered() {
        // Insert multiple vectors
        float[] queryVector = createNormalVector();
        for (int i = 0; i < 10; i++) {
            vectorStore.insert("vec-" + i, createNormalVector());
        }
        // Insert a vector identical to query
        vectorStore.insert("vec-match", queryVector);

        List<VectorStore.SearchResult> results = vectorStore.search(queryVector, 5);

        assertEquals(5, results.size());
        // Results should be sorted by score (highest first)
        for (int i = 0; i < results.size() - 1; i++) {
            assertTrue(results.get(i).score() >= results.get(i + 1).score(),
                "Results should be sorted by score descending");
        }
    }

    // ==========================================
    // Boundary Condition Tests
    // ==========================================

    @Test
    @DisplayName("Should reject null vector")
    void testRejectNullVector() {
        assertThrows(VectorStoreException.class, () -> {
            vectorStore.insert("vec-null", null);
        });
    }

    @Test
    @DisplayName("Should reject vector with wrong dimensions")
    void testRejectWrongDimensions() {
        float[] vector = new float[100]; // Wrong dimensions
        assertThrows(VectorStoreException.class, () -> {
            vectorStore.insert("vec-wrong", vector);
        });
    }

    @Test
    @DisplayName("Should accept zero vector")
    void testAcceptZeroVector() {
        float[] vector = createZeroVector();
        boolean result = vectorStore.insert("vec-zero", vector);
        assertTrue(result, "Should accept zero vector");
    }

    @Test
    @DisplayName("Should throw exception on duplicate vector IDs")
    void testHandleDuplicateIds() {
        float[] vector1 = createNormalVector();
        float[] vector2 = createNormalVector();

        vectorStore.insert("vec-dup", vector1);
        // Second insert with same ID should throw exception
        assertThrows(VectorStoreException.class, () -> {
            vectorStore.insert("vec-dup", vector2);
        });
    }

    @Test
    @DisplayName("Should handle empty batch")
    void testHandleEmptyBatch() {
        int count = vectorStore.insertBatch(Map.of());
        assertEquals(0, count);
    }

    @Test
    @DisplayName("Should handle null batch")
    void testHandleNullBatch() {
        int count = vectorStore.insertBatch(null);
        assertEquals(0, count);
    }

    // ==========================================
    // Factory Tests
    // ==========================================

    @Test
    @DisplayName("Should create SQLite vector store via factory")
    void testFactoryCreatesSQLiteStore() {
        VectorStoreFactory factory = VectorStoreFactory.getInstance(dataSource);
        VectorStore store = factory.getVectorStore("factory_test");

        assertNotNull(store);
        assertTrue(store instanceof SQLiteVecStore);
    }

    @Test
    @DisplayName("Should return same instance for same table")
    void testFactoryReturnsSingleton() {
        VectorStoreFactory factory = VectorStoreFactory.getInstance(dataSource);
        VectorStore store1 = factory.getVectorStore("singleton_test");
        VectorStore store2 = factory.getVectorStore("singleton_test");

        assertSame(store1, store2, "Should return same instance");
    }

    @Test
    @DisplayName("Should fallback to SQLite for unknown type")
    void testFactoryFallbacksToSQLite() {
        VectorStoreFactory factory = VectorStoreFactory.getInstance(dataSource);
        VectorStore store = factory.getVectorStore("fallback_test", VectorStoreFactory.VectorStoreType.POSTGRESQL);

        assertNotNull(store);
        assertTrue(store instanceof SQLiteVecStore, "Should fallback to SQLite");
    }

    // ==========================================
    // Performance Benchmark Tests
    // ==========================================

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    @DisplayName("Should insert 1000 vectors in < 2 seconds")
    void testInsert1000Performance() {
        Map<String, float[]> vectors = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            vectors.put("vec-" + i, createNormalVector());
        }

        long startTime = System.currentTimeMillis();
        int count = vectorStore.insertBatch(vectors);
        long duration = System.currentTimeMillis() - startTime;

        assertEquals(1000, count);
        assertTrue(duration < 2000, "Should insert 1000 vectors in < 2s (actual: " + duration + "ms)");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Should query 10000 vectors in < 5 seconds")
    void testQuery10000Performance() {
        // Insert 10000 vectors
        Map<String, float[]> vectors = new HashMap<>();
        for (int i = 0; i < 10000; i++) {
            vectors.put("vec-" + i, createNormalVector());
        }
        vectorStore.insertBatch(vectors);

        float[] queryVector = createNormalVector();

        long startTime = System.currentTimeMillis();
        List<VectorStore.SearchResult> results = vectorStore.search(queryVector, 10);
        long duration = System.currentTimeMillis() - startTime;

        assertNotNull(results);
        assertEquals(10, results.size());
        assertTrue(duration < 5000, "Should query 10000 vectors in < 5s (actual: " + duration + "ms)");
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    @DisplayName("Should handle 10 concurrent queries")
    void testConcurrentQueries() throws InterruptedException {
        // Insert 100 vectors
        Map<String, float[]> vectors = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            vectors.put("vec-" + i, createNormalVector());
        }
        vectorStore.insertBatch(vectors);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    float[] queryVector = createNormalVector();
                    List<VectorStore.SearchResult> results = vectorStore.search(queryVector, 5);
                    if (results.size() <= 5) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        long duration = System.currentTimeMillis() - startTime;

        assertEquals(threadCount, successCount.get(), "All queries should succeed");
        assertTrue(duration < 3000, "Should complete 10 concurrent queries in < 3s (actual: " + duration + "ms)");
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

        // Other required methods (not used in tests)
        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public java.util.logging.Logger getParentLogger() { return null; }
        @Override public <T> T unwrap(Class<T> iface) { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }
}
