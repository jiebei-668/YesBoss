package tech.yesboss.memory.migration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tech.yesboss.memory.manager.MemoryManager;
import tech.yesboss.memory.model.Resource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Unit Tests for Data Migration Tool
 */
@DisplayName("DataMigrationTool Tests")
class DataMigrationToolTest {

    @Mock
    private MemoryManager memoryManager;

    private DataMigrationToolImpl tool;
    private DataMigrationTool.MigrationConfig defaultConfig;

    private AutoCloseable closeable;

    @Nested
    @DisplayName("Test Data Preparation")
    class TestDataPreparation {

        @Test
        @DisplayName("Should create valid MigrationRequest")
        void shouldCreateValidMigrationRequest() {
            var config = new DataMigrationTool.MigrationConfig(100, 3, 5000, false, true, false);
            var request = new DataMigrationTool.MigrationRequest("json", "/path/to/data.json", config, true, true);

            assertNotNull(request);
            assertEquals("json", request.sourceType());
            assertEquals("/path/to/data.json", request.sourcePath());
        }

        @Test
        @DisplayName("Should reject blank source type")
        void shouldRejectBlankSourceType() {
            var config = new DataMigrationTool.MigrationConfig(100, 3, 5000, false, true, false);
            assertThrows(IllegalArgumentException.class, () ->
                new DataMigrationTool.MigrationRequest("", "/path", config, true, true));
        }
    }

    @Nested
    @DisplayName("Normal Functionality Tests")
    class NormalFunctionalityTests {

        @BeforeEach
        void setUp() {
            closeable = MockitoAnnotations.openMocks(this);
            defaultConfig = new DataMigrationTool.MigrationConfig(100, 3, 5000, false, true, false);
            tool = new DataMigrationToolImpl(memoryManager, defaultConfig);
        }

        @Test
        @DisplayName("Should validate valid resources")
        void shouldValidateValidResources() {
            Resource resource = new Resource("conv-123", "session-456", "test content");
            resource.setId("resource-123");
            List<Resource> resources = List.of(resource);

            var result = tool.validateResources(resources);

            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
        }

        @Test
        @DisplayName("Should detect invalid resources without ID")
        void shouldDetectInvalidResourcesWithoutId() {
            Resource resource = new Resource("conv-123", "session-456", "test content");
            List<Resource> resources = List.of(resource);

            var result = tool.validateResources(resources);

            assertFalse(result.valid());
            assertFalse(result.errors().isEmpty());
        }

        @Test
        @DisplayName("Should return empty migration history initially")
        void shouldReturnEmptyMigrationHistoryInitially() {
            var history = tool.getMigrationHistory();

            assertNotNull(history);
            assertTrue(history.isEmpty());
        }

        @Test
        @DisplayName("Should verify isAvailable method")
        void shouldVerifyIsAvailable() {
            boolean available = tool.isAvailable();
            assertTrue(available);
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @BeforeEach
        void setUp() {
            closeable = MockitoAnnotations.openMocks(this);
            defaultConfig = new DataMigrationTool.MigrationConfig(100, 3, 5000, false, true, false);
            tool = new DataMigrationToolImpl(memoryManager, defaultConfig);
        }

        @Test
        @DisplayName("Should validate resources quickly")
        void shouldValidateResourcesQuickly() {
            Resource resource = new Resource("conv-123", "session-456", "content");
            resource.setId("resource-123");
            List<Resource> resources = List.of(resource);

            long startTime = System.currentTimeMillis();
            tool.validateResources(resources);
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 100, "Operation took " + duration + "ms");
        }
    }
}
