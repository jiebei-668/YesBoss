package tech.yesboss.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.yesboss.tool.AgentTool;
import tech.yesboss.tool.ToolAccessLevel;
import tech.yesboss.tool.filesystem.CreateDirectoryTool;
import tech.yesboss.tool.filesystem.DeleteFileTool;
import tech.yesboss.tool.filesystem.ListDirectoryTool;
import tech.yesboss.tool.filesystem.ReadFileTool;
import tech.yesboss.tool.filesystem.SearchFilesTool;
import tech.yesboss.tool.filesystem.WriteFileTool;
import tech.yesboss.tool.registry.ToolRegistry;
import tech.yesboss.tool.registry.impl.ToolRegistryImpl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * File System Tool Permission Tests
 *
 * <p>Tests for RBAC (Role-Based Access Control) configuration of filesystem tools.</p>
 *
 * <p><b>Test Coverage:</b></p>
 * <ul>
 *   <li>Verify READ_ONLY tools are correctly configured</li>
 *   <li>Verify READ_WRITE tools are correctly configured</li>
 *   <li>Verify Master Agent can only access READ_ONLY tools</li>
 *   <li>Verify Worker Agent can access all tools</li>
 *   <li>Verify ToolRegistry enforces RBAC correctly</li>
 * </ul>
 */
@DisplayName("File System Tool Permission Tests")
public class FileSystemToolPermissionTest {

    private ToolRegistry toolRegistry;
    private static final String PROJECT_ROOT = System.getProperty("user.dir");

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistryImpl();
        registerAllFilesystemTools();
    }

    /**
     * Register all filesystem tools to the registry
     */
    private void registerAllFilesystemTools() {
        // READ_ONLY tools
        toolRegistry.register(new ReadFileTool(PROJECT_ROOT));
        toolRegistry.register(new ListDirectoryTool(PROJECT_ROOT));
        toolRegistry.register(new SearchFilesTool(PROJECT_ROOT));

        // READ_WRITE tools
        toolRegistry.register(new WriteFileTool(PROJECT_ROOT));
        toolRegistry.register(new CreateDirectoryTool(PROJECT_ROOT));
        toolRegistry.register(new DeleteFileTool(PROJECT_ROOT));
    }

    @Test
    @DisplayName("READ_ONLY tools should have correct access level")
    void testReadOnlyToolsAccessLevel() {
        // Given
        AgentTool readFileTool = toolRegistry.getTool("read_file");
        AgentTool listDirectoryTool = toolRegistry.getTool("list_directory");
        AgentTool searchFilesTool = toolRegistry.getTool("search_files");

        // Then
        assertEquals(ToolAccessLevel.READ_ONLY, readFileTool.getAccessLevel(),
                "read_file should be READ_ONLY");
        assertEquals(ToolAccessLevel.READ_ONLY, listDirectoryTool.getAccessLevel(),
                "list_directory should be READ_ONLY");
        assertEquals(ToolAccessLevel.READ_ONLY, searchFilesTool.getAccessLevel(),
                "search_files should be READ_ONLY");
    }

    @Test
    @DisplayName("READ_WRITE tools should have correct access level")
    void testReadWriteToolsAccessLevel() {
        // Given
        AgentTool writeFileTool = toolRegistry.getTool("write_file");
        AgentTool createDirectoryTool = toolRegistry.getTool("create_directory");
        AgentTool deleteFileTool = toolRegistry.getTool("delete_file");

        // Then
        assertEquals(ToolAccessLevel.READ_WRITE, writeFileTool.getAccessLevel(),
                "write_file should be READ_WRITE");
        assertEquals(ToolAccessLevel.READ_WRITE, createDirectoryTool.getAccessLevel(),
                "create_directory should be READ_WRITE");
        assertEquals(ToolAccessLevel.READ_WRITE, deleteFileTool.getAccessLevel(),
                "delete_file should be READ_WRITE");
    }

    @Test
    @DisplayName("Master Agent should only access READ_ONLY tools")
    void testMasterAgentAccess() {
        // When
        List<AgentTool> masterTools = toolRegistry.getAvailableTools("MASTER");

        // Then
        assertNotNull(masterTools, "Master tools list should not be null");
        assertEquals(3, masterTools.size(),
                "Master should have access to exactly 3 READ_ONLY tools");

        // Verify all tools are READ_ONLY
        for (AgentTool tool : masterTools) {
            assertEquals(ToolAccessLevel.READ_ONLY, tool.getAccessLevel(),
                    "Master should only access READ_ONLY tools, but found: " + tool.getName());
        }

        // Verify specific tools are available
        assertTrue(masterTools.stream().anyMatch(t -> t.getName().equals("read_file")),
                "Master should have access to read_file");
        assertTrue(masterTools.stream().anyMatch(t -> t.getName().equals("list_directory")),
                "Master should have access to list_directory");
        assertTrue(masterTools.stream().anyMatch(t -> t.getName().equals("search_files")),
                "Master should have access to search_files");

        // Verify write tools are NOT available
        assertFalse(masterTools.stream().anyMatch(t -> t.getName().equals("write_file")),
                "Master should NOT have access to write_file");
        assertFalse(masterTools.stream().anyMatch(t -> t.getName().equals("create_directory")),
                "Master should NOT have access to create_directory");
        assertFalse(masterTools.stream().anyMatch(t -> t.getName().equals("delete_file")),
                "Master should NOT have access to delete_file");
    }

    @Test
    @DisplayName("Worker Agent should access all tools")
    void testWorkerAgentAccess() {
        // When
        List<AgentTool> workerTools = toolRegistry.getAvailableTools("WORKER");

        // Then
        assertNotNull(workerTools, "Worker tools list should not be null");
        assertEquals(6, workerTools.size(),
                "Worker should have access to all 6 tools (3 READ_ONLY + 3 READ_WRITE)");

        // Verify READ_ONLY tools are available
        assertTrue(workerTools.stream().anyMatch(t -> t.getName().equals("read_file")),
                "Worker should have access to read_file");
        assertTrue(workerTools.stream().anyMatch(t -> t.getName().equals("list_directory")),
                "Worker should have access to list_directory");
        assertTrue(workerTools.stream().anyMatch(t -> t.getName().equals("search_files")),
                "Worker should have access to search_files");

        // Verify READ_WRITE tools are available
        assertTrue(workerTools.stream().anyMatch(t -> t.getName().equals("write_file")),
                "Worker should have access to write_file");
        assertTrue(workerTools.stream().anyMatch(t -> t.getName().equals("create_directory")),
                "Worker should have access to create_directory");
        assertTrue(workerTools.stream().anyMatch(t -> t.getName().equals("delete_file")),
                "Worker should have access to delete_file");
    }

    @Test
    @DisplayName("ToolRegistry should enforce role validation")
    void testToolRegistryRoleValidation() {
        // Test invalid role
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            toolRegistry.getAvailableTools("INVALID_ROLE");
        });

        assertTrue(exception.getMessage().contains("Invalid agent role"),
                "Should throw exception for invalid role");
    }

    @Test
    @DisplayName("ToolRegistry should be case-insensitive for roles")
    void testToolRegistryCaseInsensitive() {
        // When
        List<AgentTool> master1 = toolRegistry.getAvailableTools("MASTER");
        List<AgentTool> master2 = toolRegistry.getAvailableTools("master");
        List<AgentTool> master3 = toolRegistry.getAvailableTools("Master");

        List<AgentTool> worker1 = toolRegistry.getAvailableTools("WORKER");
        List<AgentTool> worker2 = toolRegistry.getAvailableTools("worker");
        List<AgentTool> worker3 = toolRegistry.getAvailableTools("Worker");

        // Then
        assertEquals(master1.size(), master2.size(),
                "MASTER and master should return same tools");
        assertEquals(master2.size(), master3.size(),
                "master and Master should return same tools");

        assertEquals(worker1.size(), worker2.size(),
                "WORKER and worker should return same tools");
        assertEquals(worker2.size(), worker3.size(),
                "worker and Worker should return same tools");
    }

    @Test
    @DisplayName("READ_ONLY tools count should match expected")
    void testReadOnlyToolsCount() {
        // When
        List<AgentTool> masterTools = toolRegistry.getAvailableTools("MASTER");

        // Then
        long readOnlyCount = masterTools.stream()
                .filter(t -> t.getAccessLevel() == ToolAccessLevel.READ_ONLY)
                .count();

        assertEquals(3, readOnlyCount,
                "Should have exactly 3 READ_ONLY tools");
    }

    @Test
    @DisplayName("READ_WRITE tools count should match expected")
    void testReadWriteToolsCount() {
        // When
        List<AgentTool> workerTools = toolRegistry.getAvailableTools("WORKER");

        // Then
        long readWriteCount = workerTools.stream()
                .filter(t -> t.getAccessLevel() == ToolAccessLevel.READ_WRITE)
                .count();

        assertEquals(3, readWriteCount,
                "Should have exactly 3 READ_WRITE tools");
    }

    @Test
    @DisplayName("All filesystem tools should be registered")
    void testAllToolsRegistered() {
        // Then
        assertTrue(toolRegistry.isRegistered("read_file"),
                "read_file should be registered");
        assertTrue(toolRegistry.isRegistered("list_directory"),
                "list_directory should be registered");
        assertTrue(toolRegistry.isRegistered("search_files"),
                "search_files should be registered");
        assertTrue(toolRegistry.isRegistered("write_file"),
                "write_file should be registered");
        assertTrue(toolRegistry.isRegistered("create_directory"),
                "create_directory should be registered");
        assertTrue(toolRegistry.isRegistered("delete_file"),
                "delete_file should be registered");

        assertEquals(6, toolRegistry.getToolCount(),
                "Total registered tools should be 6");
    }

    @Test
    @DisplayName("Master Agent permission boundary should be enforced")
    void testMasterPermissionBoundary() {
        // When
        List<AgentTool> masterTools = toolRegistry.getAvailableTools("MASTER");

        // Then
        // Verify Master cannot access any READ_WRITE tools
        for (AgentTool tool : masterTools) {
            assertNotEquals(ToolAccessLevel.READ_WRITE, tool.getAccessLevel(),
                    "Master should not have access to READ_WRITE tool: " + tool.getName());
        }

        // Verify permission boundary count
        int totalReadTools = 3; // read_file, list_directory, search_files
        int totalWriteTools = 3; // write_file, create_directory, delete_file

        assertEquals(totalReadTools, masterTools.size(),
                "Master should only access " + totalReadTools + " read-only tools");
        assertNotEquals(totalReadTools + totalWriteTools, masterTools.size(),
                "Master should NOT access all tools");
    }

    @Test
    @DisplayName("Worker Agent should have complete tool access")
    void testWorkerCompleteAccess() {
        // When
        List<AgentTool> workerTools = toolRegistry.getAvailableTools("WORKER");

        // Then
        // Verify Worker has access to both READ_ONLY and READ_WRITE tools
        long readOnlyCount = workerTools.stream()
                .filter(t -> t.getAccessLevel() == ToolAccessLevel.READ_ONLY)
                .count();

        long readWriteCount = workerTools.stream()
                .filter(t -> t.getAccessLevel() == ToolAccessLevel.READ_WRITE)
                .count();

        assertTrue(readOnlyCount > 0,
                "Worker should have access to READ_ONLY tools");
        assertTrue(readWriteCount > 0,
                "Worker should have access to READ_WRITE tools");
        assertEquals(3, readOnlyCount,
                "Worker should access all 3 READ_ONLY tools");
        assertEquals(3, readWriteCount,
                "Worker should access all 3 READ_WRITE tools");
    }
}
