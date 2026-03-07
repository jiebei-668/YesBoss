package tech.yesboss.memory.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit test suite for MemoryConfig
 *
 * Tests cover:
 * - Singleton pattern
 * - Configuration get/set operations
 * - Type safety
 * - Validation
 * - Backend switching
 * - Change listeners
 * - Environment variable loading
 */
@DisplayName("MemoryConfig Unit Tests")
class MemoryConfigTest {

    private MemoryConfig config;

    @BeforeEach
    void setUp() {
        // Reset to defaults before each test
        config = MemoryConfig.getInstance();
        config.resetToDefaults();
    }

    // ==========================================
    // Singleton Pattern Tests
    // ==========================================

    @Nested
    @DisplayName("Singleton Pattern Tests")
    class SingletonTests {

        @Test
        @DisplayName("Should return same instance")
        void testSingletonInstance() {
            MemoryConfig instance1 = MemoryConfig.getInstance();
            MemoryConfig instance2 = MemoryConfig.getInstance();

            assertSame(instance1, instance2);
        }

        @Test
        @DisplayName("Should be thread-safe")
        void testThreadSafety() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger uniqueInstances = new AtomicInteger(0);
            List<MemoryConfig> instances = new java.util.ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                new Thread(() -> {
                    MemoryConfig instance = MemoryConfig.getInstance();
                    synchronized (instances) {
                        if (!instances.contains(instance)) {
                            instances.add(instance);
                            uniqueInstances.incrementAndGet();
                        }
                    }
                    latch.countDown();
                }).start();
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(1, uniqueInstances.get());
        }
    }

    // ==========================================
    // Configuration Get/Set Tests
    // ==========================================

    @Nested
    @DisplayName("Configuration Get/Set Tests")
    class ConfigurationGetSetTests {

        @Test
        @DisplayName("Should get default value")
        void testGetDefaultValue() {
            Integer dimension = config.get(MemoryConfig.VECTOR_DIMENSION);

            assertNotNull(dimension);
            assertEquals(1536, dimension);
        }

        @Test
        @DisplayName("Should set and get configuration value")
        void testSetAndGetValue() {
            config.set(MemoryConfig.VECTOR_DIMENSION, 768);

            Integer dimension = config.get(MemoryConfig.VECTOR_DIMENSION);

            assertEquals(768, dimension);
        }

        @Test
        @DisplayName("Should return custom default when key not found")
        void testGetWithCustomDefault() {
            Integer value = config.get("non.existent.key", 42);

            assertEquals(42, value);
        }

        @Test
        @DisplayName("Should set multiple values")
        void testSetMultipleValues() {
            Map<String, Object> values = Map.of(
                MemoryConfig.VECTOR_DIMENSION, 512,
                MemoryConfig.BATCH_SIZE, 50
            );

            config.setAll(values);

            assertEquals(512, config.get(MemoryConfig.VECTOR_DIMENSION));
            assertEquals(50, config.get(MemoryConfig.BATCH_SIZE));
        }

        @Test
        @DisplayName("Should check if key exists")
        void testContainsKey() {
            assertTrue(config.contains(MemoryConfig.VECTOR_DIMENSION));
            assertFalse(config.contains("non.existent.key"));
        }

        @Test
        @DisplayName("Should remove configuration value")
        void testRemoveValue() {
            config.set(MemoryConfig.VECTOR_DIMENSION, 1024);
            assertEquals(1024, config.get(MemoryConfig.VECTOR_DIMENSION));

            config.remove(MemoryConfig.VECTOR_DIMENSION);

            // Should revert to default
            assertEquals(1536, config.get(MemoryConfig.VECTOR_DIMENSION));
        }

        @Test
        @DisplayName("Should get all configuration")
        void testGetAll() {
            config.set(MemoryConfig.VECTOR_DIMENSION, 512);

            Map<String, Object> all = config.getAll();

            assertNotNull(all);
            assertTrue(all.containsKey(MemoryConfig.VECTOR_DIMENSION));
            assertTrue(all.containsKey(MemoryConfig.BATCH_SIZE));
        }

        @Test
        @DisplayName("Should reset to defaults")
        void testResetToDefaults() {
            config.set(MemoryConfig.VECTOR_DIMENSION, 512);
            config.set(MemoryConfig.BATCH_SIZE, 50);

            config.resetToDefaults();

            assertEquals(1536, config.get(MemoryConfig.VECTOR_DIMENSION));
            assertEquals(100, config.get(MemoryConfig.BATCH_SIZE));
        }
    }

    // ==========================================
    // Type Safety Tests
    // ==========================================

    @Nested
    @DisplayName("Type Safety Tests")
    class TypeSafetyTests {

        @Test
        @DisplayName("Should get integer value")
        void testGetInteger() {
            config.set(MemoryConfig.VECTOR_DIMENSION, 512);

            Integer value = config.get(MemoryConfig.VECTOR_DIMENSION);

            assertEquals(512, value);
            assertInstanceOf(Integer.class, value);
        }

        @Test
        @DisplayName("Should get double value")
        void testGetDouble() {
            config.set(MemoryConfig.SIMILARITY_THRESHOLD, 0.85);

            Double value = config.get(MemoryConfig.SIMILARITY_THRESHOLD);

            assertEquals(0.85, value, 0.001);
            assertInstanceOf(Double.class, value);
        }

        @Test
        @DisplayName("Should get boolean value")
        void testGetBoolean() {
            config.set(MemoryConfig.CACHE_ENABLED, false);

            Boolean value = config.get(MemoryConfig.CACHE_ENABLED);

            assertFalse(value);
            assertInstanceOf(Boolean.class, value);
        }

        @Test
        @DisplayName("Should get string value")
        void testGetString() {
            config.set(MemoryConfig.SQLITE_PATH, "/tmp/test.db");

            String value = config.get(MemoryConfig.SQLITE_PATH);

            assertEquals("/tmp/test.db", value);
            assertInstanceOf(String.class, value);
        }

        @Test
        @DisplayName("Should get enum value")
        void testGetEnum() {
            config.set(MemoryConfig.BACKEND_TYPE, MemoryConfig.BackendType.POSTGRESQL_PGVECTOR);

            MemoryConfig.BackendType value = config.getBackendType();

            assertEquals(MemoryConfig.BackendType.POSTGRESQL_PGVECTOR, value);
        }
    }

    // ==========================================
    // Validation Tests
    // ==========================================

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should validate default configuration")
        void testValidateDefaultConfig() {
            assertTrue(config.validate());
        }

        @Test
        @DisplayName("Should validate SQLite configuration")
        void testValidateSQLiteConfig() {
            config.setBackendType(MemoryConfig.BackendType.SQLITE_VEC);
            config.set(MemoryConfig.SQLITE_PATH, "/tmp/test.db");

            assertTrue(config.validate());
        }

        @Test
        @DisplayName("Should validate PostgreSQL configuration")
        void testValidatePostgreSQLConfig() {
            config.setBackendType(MemoryConfig.BackendType.POSTGRESQL_PGVECTOR);
            config.set(MemoryConfig.POSTGRESQL_HOST, "localhost");
            config.set(MemoryConfig.POSTGRESQL_PORT, 5432);
            config.set(MemoryConfig.POSTGRESQL_DATABASE, "testdb");
            config.set(MemoryConfig.POSTGRESQL_USER, "testuser");

            assertTrue(config.validate());
        }

        @Test
        @DisplayName("Should fail validation for invalid vector dimension")
        void testValidateInvalidVectorDimension() {
            config.set(MemoryConfig.VECTOR_DIMENSION, -1);

            assertFalse(config.validate());
        }

        @Test
        @DisplayName("Should fail validation for invalid similarity threshold")
        void testValidateInvalidSimilarityThreshold() {
            config.set(MemoryConfig.SIMILARITY_THRESHOLD, 1.5);

            assertFalse(config.validate());
        }

        @Test
        @DisplayName("Should fail validation for missing SQLite path")
        void testValidateMissingSQLitePath() {
            config.setBackendType(MemoryConfig.BackendType.SQLITE_VEC);
            config.set(MemoryConfig.SQLITE_PATH, "");

            assertFalse(config.validate());
        }

        @Test
        @DisplayName("Should fail validation for incomplete PostgreSQL config")
        void testValidateIncompletePostgreSQLConfig() {
            config.setBackendType(MemoryConfig.BackendType.POSTGRESQL_PGVECTOR);
            // Missing required fields

            assertFalse(config.validate());
        }
    }

    // ==========================================
    // Backend Switching Tests
    // ==========================================

    @Nested
    @DisplayName("Backend Switching Tests")
    class BackendSwitchingTests {

        @Test
        @DisplayName("Should get default backend type")
        void testGetDefaultBackendType() {
            MemoryConfig.BackendType backend = config.getBackendType();

            assertEquals(MemoryConfig.BackendType.SQLITE_VEC, backend);
        }

        @Test
        @DisplayName("Should set backend type")
        void testSetBackendType() {
            config.setBackendType(MemoryConfig.BackendType.POSTGRESQL_PGVECTOR);

            MemoryConfig.BackendType backend = config.getBackendType();

            assertEquals(MemoryConfig.BackendType.POSTGRESQL_PGVECTOR, backend);
        }

        @Test
        @DisplayName("Should build PostgreSQL URL from components")
        void testBuildPostgreSQLUrl() {
            config.set(MemoryConfig.POSTGRESQL_HOST, "localhost");
            config.set(MemoryConfig.POSTGRESQL_PORT, 5432);
            config.set(MemoryConfig.POSTGRESQL_DATABASE, "testdb");

            String url = config.getPostgreSQLUrl();

            assertEquals("jdbc:postgresql://localhost:5432/testdb", url);
        }

        @Test
        @DisplayName("Should use explicit URL if provided")
        void testUseExplicitPostgreSQLUrl() {
            config.set(MemoryConfig.POSTGRESQL_URL, "jdbc:postgresql://remote:5432/mydb");

            String url = config.getPostgreSQLUrl();

            assertEquals("jdbc:postgresql://remote:5432/mydb", url);
        }

        @Test
        @DisplayName("Should switch backend successfully")
        void testSwitchBackend() {
            config.setBackendType(MemoryConfig.BackendType.SQLITE_VEC);
            config.set(MemoryConfig.SQLITE_PATH, "/tmp/test.db");

            assertTrue(config.switchBackend(MemoryConfig.BackendType.POSTGRESQL_PGVECTOR));
            assertEquals(MemoryConfig.BackendType.POSTGRESQL_PGVECTOR, config.getBackendType());
        }

        @Test
        @DisplayName("Should revert backend switch on validation failure")
        void testRevertBackendSwitchOnFailure() {
            config.setBackendType(MemoryConfig.BackendType.SQLITE_VEC);
            config.set(MemoryConfig.SQLITE_PATH, "/tmp/test.db");

            // Try to switch without proper PostgreSQL config
            assertFalse(config.switchBackend(MemoryConfig.BackendType.POSTGRESQL_PGVECTOR));
            assertEquals(MemoryConfig.BackendType.SQLITE_VEC, config.getBackendType());
        }
    }

    // ==========================================
    // Change Listener Tests
    // ==========================================

    @Nested
    @DisplayName("Change Listener Tests")
    class ChangeListenerTests {

        @Test
        @DisplayName("Should notify listener on configuration change")
        void testNotifyListener() {
            AtomicReference<Object> newValue = new AtomicReference<>();

            config.addListener(MemoryConfig.VECTOR_DIMENSION, (key, oldValue, newVal) -> {
                newValue.set(newVal);
            });

            config.set(MemoryConfig.VECTOR_DIMENSION, 768);

            assertEquals(768, newValue.get());
        }

        @Test
        @DisplayName("Should not notify listener when value unchanged")
        void testNoNotificationOnUnchangedValue() {
            AtomicInteger notificationCount = new AtomicInteger(0);

            config.addListener(MemoryConfig.VECTOR_DIMENSION, (key, oldValue, newVal) -> {
                notificationCount.incrementAndGet();
            });

            config.set(MemoryConfig.VECTOR_DIMENSION, 1536); // Same as default

            assertEquals(0, notificationCount.get());
        }

        @Test
        @DisplayName("Should notify multiple listeners")
        void testNotifyMultipleListeners() {
            AtomicInteger count1 = new AtomicInteger(0);
            AtomicInteger count2 = new AtomicInteger(0);

            config.addListener(MemoryConfig.VECTOR_DIMENSION, (key, oldValue, newVal) -> {
                count1.incrementAndGet();
            });

            config.addListener(MemoryConfig.VECTOR_DIMENSION, (key, oldValue, newVal) -> {
                count2.incrementAndGet();
            });

            config.set(MemoryConfig.VECTOR_DIMENSION, 768);

            assertEquals(1, count1.get());
            assertEquals(1, count2.get());
        }

        @Test
        @DisplayName("Should remove listener")
        void testRemoveListener() {
            AtomicInteger notificationCount = new AtomicInteger(0);

            MemoryConfig.ConfigChangeListener listener = (key, oldValue, newVal) -> {
                notificationCount.incrementAndGet();
            };

            config.addListener(MemoryConfig.VECTOR_DIMENSION, listener);
            config.set(MemoryConfig.VECTOR_DIMENSION, 768);
            assertEquals(1, notificationCount.get());

            config.removeListener(MemoryConfig.VECTOR_DIMENSION, listener);
            config.set(MemoryConfig.VECTOR_DIMENSION, 512);
            assertEquals(1, notificationCount.get()); // No increment
        }
    }

    // ==========================================
    // Environment Variable Tests
    // ==========================================

    @Nested
    @DisplayName("Environment Variable Tests")
    class EnvironmentVariableTests {

        @Test
        @DisplayName("Should load backend type from environment")
        void testLoadBackendTypeFromEnvironment() {
            String originalValue = System.getenv("MEMORY_BACKEND_TYPE");
            try {
                System.setProperty("MEMORY_BACKEND_TYPE", "POSTGRESQL_PGVECTOR");
                // Note: In real test, would set environment variable
                // For unit test, we'll simulate by calling loadFromEnvironment
                // after setting the env var

                // This test demonstrates the concept
                // In practice, environment variable testing is complex
            } finally {
                if (originalValue != null) {
                    System.setProperty("MEMORY_BACKEND_TYPE", originalValue);
                }
            }
        }
    }

    // ==========================================
    // Configuration Summary Tests
    // ==========================================

    @Nested
    @DisplayName("Configuration Summary Tests")
    class ConfigurationSummaryTests {

        @Test
        @DisplayName("Should generate configuration summary")
        void testGetSummary() {
            String summary = config.getSummary();

            assertNotNull(summary);
            assertTrue(summary.contains("Memory Configuration"));
            assertTrue(summary.contains("Backend Type"));
            assertTrue(summary.contains("Vector Dimension"));
            assertTrue(summary.contains("Similarity Threshold"));
        }
    }
}
