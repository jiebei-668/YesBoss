package tech.yesboss.memory.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tech.yesboss.memory.model.Preference;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PreferenceRepository Unit Tests")
class PreferenceRepositoryTest {

    private static final String DB_URL = "jdbc:sqlite:/tmp/test_preference.db";
    private SimpleDataSource dataSource;
    private PreferenceRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = new SimpleDataSource(DB_URL);
        initializeTables();
        repository = new PreferenceRepositoryImpl(dataSource);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
        try {
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get("/tmp/test_preference.db"));
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    private void initializeTables() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS preferences (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE,
                    summary TEXT NOT NULL,
                    embedding BLOB,
                    deleted INTEGER DEFAULT 0 CHECK(deleted IN (0, 1)),
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                );
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS preference_name_idx ON preferences(name);");
            stmt.execute("CREATE INDEX IF NOT EXISTS preference_embedding_null_idx ON preferences(embedding) WHERE embedding IS NULL;");
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
    }

    private Preference createValidPreference() {
        String uniqueName = "pref-" + UUID.randomUUID().toString().substring(0, 8);
        return new Preference(uniqueName, "Test summary");
    }

    private Preference createPreferenceWithName(String name) {
        return new Preference(name, "Summary for " + name);
    }

    private byte[] createEmbedding() {
        float[] embedding = new float[1536];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = (float) Math.random();
        }
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

    // Interface Contract Tests
    @Test
    @DisplayName("save() should return Preference with generated ID")
    void testSaveReturnsPreferenceWithGeneratedId() {
        Preference preference = createValidPreference();
        Preference saved = repository.save(preference);

        assertNotNull(saved, "save() should return non-null Preference");
        assertNotNull(saved.getId(), "saved preference should have ID");
        assertNotNull(saved.getCreatedAt(), "saved preference should have createdAt");
        assertNotNull(saved.getUpdatedAt(), "saved preference should have updatedAt");
    }

    @Test
    @DisplayName("findById() should return Optional<Preference>")
    void testFindByIdReturnsOptional() {
        Preference preference = createValidPreference();
        repository.save(preference);

        Optional<Preference> result = repository.findById(preference.getId());

        assertTrue(result.isPresent(), "findById should return non-empty Optional");
        assertEquals(preference.getId(), result.get().getId(), "ID should match");
    }

    @Test
    @DisplayName("findByName() should return Optional<Preference>")
    void testFindByNameReturnsOptional() {
        String name = "preference-test";
        Preference preference = createPreferenceWithName(name);
        repository.save(preference);

        Optional<Preference> result = repository.findByName(name);

        assertTrue(result.isPresent(), "findByName should return non-empty Optional");
        assertEquals(name, result.get().getName(), "name should match");
    }

    @Test
    @DisplayName("deleteById() should return boolean")
    void testDeleteByIdReturnsBoolean() {
        Preference preference = createValidPreference();
        repository.save(preference);

        boolean deleted = repository.deleteById(preference.getId());

        assertTrue(deleted, "deleteById should return true on success");
        assertFalse(repository.existsById(preference.getId()), "preference should not exist after delete");
    }

    // CRUD Operation Tests
    @Test
    @DisplayName("save() should auto-generate UUID for ID")
    void testSaveAutoGeneratesId() {
        Preference preference = new Preference();
        preference.setName("test-preference");
        preference.setSummary("Test summary");

        Preference saved = repository.save(preference);

        assertNotNull(saved.getId(), "ID should be auto-generated");
        assertTrue(saved.getId().matches("[a-f0-9\\-]{36}"), "ID should be UUID format");
    }

    @Test
    @DisplayName("save() should throw IllegalArgumentException for null preference")
    void testSaveThrowsForNullPreference() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
    }

    @Test
    @DisplayName("save() should throw IllegalArgumentException for null name")
    void testSaveThrowsForNullName() {
        Preference preference = new Preference();
        preference.setName(null);
        preference.setSummary("Test summary");

        assertThrows(IllegalArgumentException.class, () -> repository.save(preference));
    }

    @Test
    @DisplayName("save() should throw IllegalArgumentException for empty name")
    void testSaveThrowsForEmptyName() {
        Preference preference = new Preference();
        preference.setName("");
        preference.setSummary("Test summary");

        assertThrows(IllegalArgumentException.class, () -> repository.save(preference));
    }

    @Test
    @DisplayName("save() should throw IllegalArgumentException for name > 100 characters")
    void testSaveThrowsForLongName() {
        Preference preference = new Preference();
        preference.setName("a".repeat(101));
        preference.setSummary("Test summary");

        assertThrows(IllegalArgumentException.class, () -> repository.save(preference));
    }

    // Name-based Query Tests
    @Test
    @DisplayName("findByName() should retrieve preference by name")
    void testFindByNameRetrievesByName() {
        String name = "preference-unique";
        Preference preference = createPreferenceWithName(name);
        repository.save(preference);

        Optional<Preference> result = repository.findByName(name);

        assertTrue(result.isPresent(), "should find preference by name");
        assertEquals(name, result.get().getName(), "name should match");
    }

    @Test
    @DisplayName("findByName() should return empty for non-existent name")
    void testFindByNameReturnsEmptyForNonExistent() {
        Optional<Preference> result = repository.findByName("non-existent");
        assertFalse(result.isPresent(), "should return empty Optional");
    }

    // Smart Update Tests
    @Test
    @DisplayName("updateSummaryAndEmbedding() should update by name")
    void testUpdateSummaryAndEmbedding() {
        String name = "preference-update";
        Preference preference = createPreferenceWithName(name);
        repository.save(preference);

        byte[] newEmbedding = createEmbedding();
        boolean updated = repository.updateSummaryAndEmbedding(name, "Updated summary", newEmbedding);

        assertTrue(updated, "update should succeed");

        Optional<Preference> found = repository.findByName(name);
        assertTrue(found.isPresent(), "should find updated preference");
        assertEquals("Updated summary", found.get().getSummary(), "summary should be updated");
        assertArrayEquals(newEmbedding, found.get().getEmbedding(), "embedding should be updated");
    }

    @Test
    @DisplayName("updateSummaryAndEmbedding() should return false for non-existent name")
    void testUpdateSummaryAndEmbeddingReturnsFalseForNonExistent() {
        boolean updated = repository.updateSummaryAndEmbedding("non-existent", "Summary", null);
        assertFalse(updated, "should return false for non-existent name");
    }

    // Embedding Query Tests
    @Test
    @DisplayName("findPreferencesWithoutEmbedding() should retrieve preferences without embeddings")
    void testFindPreferencesWithoutEmbedding() {
        Preference withEmbedding = createValidPreference();
        withEmbedding.setEmbedding(createEmbedding());
        repository.save(withEmbedding);

        Preference withoutEmbedding = createValidPreference();
        repository.save(withoutEmbedding);

        List<Preference> results = repository.findPreferencesWithoutEmbedding();

        assertEquals(1, results.size(), "should find 1 preference without embedding");
        assertFalse(results.get(0).hasEmbedding(), "preference should not have embedding");
    }

    // Batch Operations Tests
    @Test
    @DisplayName("saveAll() should save multiple preferences in batch")
    void testSaveAllSavesMultiplePreferences() {
        List<Preference> preferences = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            preferences.add(createValidPreference());
        }

        List<Preference> saved = repository.saveAll(preferences);

        assertEquals(10, saved.size(), "should save 10 preferences");
        assertEquals(10, repository.count(), "count should be 10");
    }

    @Test
    @DisplayName("saveAll() should auto-split large batches")
    void testSaveAllAutoSplitsLargeBatches() {
        List<Preference> preferences = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            preferences.add(createValidPreference());
        }

        List<Preference> saved = repository.saveAll(preferences);

        assertEquals(250, saved.size(), "should save 250 preferences");
        assertEquals(250, repository.count(), "count should be 250");
    }

    @Test
    @DisplayName("deleteByName() should delete preference by name")
    void testDeleteByNameDeletesPreference() {
        String name = "preference-delete";
        repository.save(createPreferenceWithName(name));
        repository.save(createValidPreference());

        boolean deleted = repository.deleteByName(name);

        assertTrue(deleted, "should delete preference");
        assertEquals(1, repository.count(), "count should be 1");
    }

    // Update Method Tests
    @Test
    @DisplayName("update() should update preference fields")
    void testUpdateUpdatesPreferenceFields() {
        Preference preference = repository.save(createValidPreference());
        preference.setSummary("Updated summary");

        Preference updated = repository.update(preference);

        assertEquals("Updated summary", updated.getSummary(), "summary should be updated");
    }

    @Test
    @DisplayName("update() should throw for preference without ID")
    void testUpdateThrowsForPreferenceWithoutId() {
        Preference preference = createValidPreference();
        preference.setId(null);

        assertThrows(IllegalArgumentException.class, () -> repository.update(preference));
    }

    // Count Method Tests
    @Test
    @DisplayName("existsById() should return true for existing preference")
    void testExistsByIdReturnsTrueForExisting() {
        Preference preference = repository.save(createValidPreference());

        assertTrue(repository.existsById(preference.getId()), "should return true for existing ID");
    }

    @Test
    @DisplayName("existsByName() should return true for existing name")
    void testExistsByNameReturnsTrueForExisting() {
        String name = "preference-exists";
        repository.save(createPreferenceWithName(name));

        assertTrue(repository.existsByName(name), "should return true for existing name");
    }

    // Boundary Conditions Tests
    @Test
    @DisplayName("should handle special characters in name and summary")
    void testHandlesSpecialCharacters() {
        Preference preference = new Preference();
        preference.setName("preference-特殊-🎉");
        preference.setSummary("Summary with 特殊, emoji 🎉 and \n newlines");

        Preference saved = repository.save(preference);
        Preference found = repository.findById(saved.getId()).orElseThrow();

        assertEquals(saved.getName(), found.getName(), "special characters should be preserved in name");
        assertEquals(saved.getSummary(), found.getSummary(), "special characters should be preserved in summary");
    }

    @Test
    @DisplayName("should handle embedding byte arrays")
    void testHandlesEmbeddingByteArrays() {
        Preference preference = createValidPreference();
        byte[] embedding = createEmbedding();
        preference.setEmbedding(embedding);

        Preference saved = repository.save(preference);
        Preference found = repository.findById(saved.getId()).orElseThrow();

        assertArrayEquals(embedding, found.getEmbedding(), "embedding should be preserved");
    }

    // Performance Tests
    @Test
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    @DisplayName("Performance: save() should complete within 100ms")
    void testPerformanceSave() {
        repository.save(createValidPreference());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    @DisplayName("Performance: saveAll(250) should complete within 3s")
    void testPerformanceSaveAll250() {
        List<Preference> preferences = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            preferences.add(createValidPreference());
        }

        repository.saveAll(preferences);
        assertEquals(250, repository.count(), "should save 250 preferences");
    }

    // Concurrent Access Tests
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
                    repository.save(createValidPreference());
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

    // Clear Method Tests
    @Test
    @DisplayName("clear() should delete all preferences")
    void testClearDeletesAllPreferences() {
        repository.save(createValidPreference());
        repository.save(createValidPreference());
        repository.save(createValidPreference());

        repository.clear();

        assertEquals(0, repository.count(), "count should be 0 after clear");
    }

    // Utility Classes
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
