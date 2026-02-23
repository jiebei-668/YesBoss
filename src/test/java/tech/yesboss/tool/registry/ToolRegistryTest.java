package tech.yesboss.tool.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.yesboss.tool.AgentTool;
import tech.yesboss.tool.SuspendExecutionException;
import tech.yesboss.tool.ToolAccessLevel;
import tech.yesboss.tool.registry.impl.ToolRegistryImpl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ToolRegistry registration and role-based filtering.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Tools can be registered and retrieved</li>
 *   <li>getAvailableTools filters correctly by role</li>
 *   <li>MASTER role only gets READ_ONLY tools</li>
 *   <li>WORKER role gets all tools</li>
 *   <li>Exceptions are thrown for invalid operations</li>
 * </ul>
 */
@DisplayName("ToolRegistry Tests")
class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistryImpl();
    }

    @Test
    @DisplayName("register should add tool to registry")
    void testRegisterTool() {
        // Arrange
        AgentTool tool = createMockTool("read_file", ToolAccessLevel.READ_ONLY);

        // Act
        registry.register(tool);

        // Assert
        assertTrue(registry.isRegistered("read_file"),
                "Tool should be registered");
        assertEquals(1, registry.getToolCount(),
                "Tool count should be 1");
    }

    @Test
    @DisplayName("register should throw exception for duplicate tool name")
    void testRegisterDuplicateToolName() {
        // Arrange
        AgentTool tool1 = createMockTool("write_file", ToolAccessLevel.READ_WRITE);
        AgentTool tool2 = createMockTool("write_file", ToolAccessLevel.READ_WRITE);

        // Act
        registry.register(tool1);

        // Assert
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(tool2),
                "Should throw exception for duplicate tool name");
    }

    @Test
    @DisplayName("register should throw exception for null tool")
    void testRegisterNullTool() {
        // Assert
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(null),
                "Should throw exception for null tool");
    }

    @Test
    @DisplayName("getTool should return registered tool")
    void testGetTool() {
        // Arrange
        AgentTool tool = createMockTool("list_dir", ToolAccessLevel.READ_ONLY);
        registry.register(tool);

        // Act
        AgentTool retrieved = registry.getTool("list_dir");

        // Assert
        assertSame(tool, retrieved,
                "Should return the same tool instance");
    }

    @Test
    @DisplayName("getTool should throw exception for non-existent tool")
    void testGetNonExistentTool() {
        // Assert
        assertThrows(IllegalArgumentException.class,
                () -> registry.getTool("non_existent_tool"),
                "Should throw exception for non-existent tool");
    }

    @Test
    @DisplayName("getAvailableTools for MASTER should only return READ_ONLY tools")
    void testGetAvailableToolsForMaster() {
        // Arrange
        AgentTool readOnlyTool1 = createMockTool("read_file", ToolAccessLevel.READ_ONLY);
        AgentTool readOnlyTool2 = createMockTool("list_dir", ToolAccessLevel.READ_ONLY);
        AgentTool readWriteTool1 = createMockTool("write_file", ToolAccessLevel.READ_WRITE);
        AgentTool readWriteTool2 = createMockTool("delete_file", ToolAccessLevel.READ_WRITE);

        registry.register(readOnlyTool1);
        registry.register(readOnlyTool2);
        registry.register(readWriteTool1);
        registry.register(readWriteTool2);

        // Act
        List<AgentTool> masterTools = registry.getAvailableTools("MASTER");

        // Assert
        assertEquals(2, masterTools.size(),
                "MASTER should only get READ_ONLY tools");
        assertTrue(masterTools.contains(readOnlyTool1),
                "Should contain read_file");
        assertTrue(masterTools.contains(readOnlyTool2),
                "Should contain list_dir");
        assertFalse(masterTools.contains(readWriteTool1),
                "Should not contain write_file");
        assertFalse(masterTools.contains(readWriteTool2),
                "Should not contain delete_file");
    }

    @Test
    @DisplayName("getAvailableTools for WORKER should return all tools")
    void testGetAvailableToolsForWorker() {
        // Arrange
        AgentTool readOnlyTool = createMockTool("read_config", ToolAccessLevel.READ_ONLY);
        AgentTool readWriteTool1 = createMockTool("write_config", ToolAccessLevel.READ_WRITE);
        AgentTool readWriteTool2 = createMockTool("execute_command", ToolAccessLevel.READ_WRITE);

        registry.register(readOnlyTool);
        registry.register(readWriteTool1);
        registry.register(readWriteTool2);

        // Act
        List<AgentTool> workerTools = registry.getAvailableTools("WORKER");

        // Assert
        assertEquals(3, workerTools.size(),
                "WORKER should get all tools");
        assertTrue(workerTools.contains(readOnlyTool),
                "Should contain read_config");
        assertTrue(workerTools.contains(readWriteTool1),
                "Should contain write_config");
        assertTrue(workerTools.contains(readWriteTool2),
                "Should contain execute_command");
    }

    @Test
    @DisplayName("getAvailableTools should be case-insensitive for role")
    void testGetAvailableToolsCaseInsensitive() {
        // Arrange
        AgentTool tool = createMockTool("search_code", ToolAccessLevel.READ_ONLY);
        registry.register(tool);

        // Act & Assert
        assertEquals(1, registry.getAvailableTools("MASTER").size(),
                "MASTER (uppercase) should work");
        assertEquals(1, registry.getAvailableTools("master").size(),
                "master (lowercase) should work");
        assertEquals(1, registry.getAvailableTools("Master").size(),
                "Master (mixed case) should work");
        assertEquals(1, registry.getAvailableTools("MaStEr").size(),
                "MaStEr (weird case) should work");
    }

    @Test
    @DisplayName("getAvailableTools should throw exception for invalid role")
    void testGetAvailableToolsInvalidRole() {
        // Arrange
        AgentTool tool = createMockTool("some_tool", ToolAccessLevel.READ_ONLY);
        registry.register(tool);

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> registry.getAvailableTools("INVALID_ROLE"),
                "Should throw exception for invalid role");
        assertThrows(IllegalArgumentException.class,
                () -> registry.getAvailableTools(""),
                "Should throw exception for empty role");
        assertThrows(IllegalArgumentException.class,
                () -> registry.getAvailableTools(null),
                "Should throw exception for null role");
    }

    @Test
    @DisplayName("isRegistered should return true for registered tools")
    void testIsRegistered() {
        // Arrange
        AgentTool tool = createMockTool("grep_search", ToolAccessLevel.READ_ONLY);
        registry.register(tool);

        // Act & Assert
        assertTrue(registry.isRegistered("grep_search"),
                "Should return true for registered tool");
        assertFalse(registry.isRegistered("non_existent"),
                "Should return false for non-existent tool");
        assertFalse(registry.isRegistered(""),
                "Should return false for empty string");
        assertFalse(registry.isRegistered(null),
                "Should return false for null");
    }

    @Test
    @DisplayName("getToolCount should return correct count")
    void testGetToolCount() {
        // Arrange
        assertEquals(0, registry.getToolCount(),
                "Initial count should be 0");

        AgentTool tool1 = createMockTool("tool1", ToolAccessLevel.READ_ONLY);
        AgentTool tool2 = createMockTool("tool2", ToolAccessLevel.READ_WRITE);

        // Act
        registry.register(tool1);
        assertEquals(1, registry.getToolCount(),
                "Count should be 1 after first registration");

        registry.register(tool2);
        assertEquals(2, registry.getToolCount(),
                "Count should be 2 after second registration");
    }

    @Test
    @DisplayName("getAvailableTools should return unmodifiable list")
    void testGetAvailableToolsReturnsUnmodifiableList() {
        // Arrange
        AgentTool tool = createMockTool("readme_tool", ToolAccessLevel.READ_ONLY);
        registry.register(tool);

        // Act
        List<AgentTool> tools = registry.getAvailableTools("MASTER");

        // Assert
        assertThrows(UnsupportedOperationException.class,
                () -> tools.add(createMockTool("new_tool", ToolAccessLevel.READ_ONLY)),
                "Should not allow modification to returned list");
    }

    @Test
    @DisplayName("registry should handle multiple tools correctly")
    void testMultipleToolsIntegration() {
        // Arrange - Register various tools
        registry.register(createMockTool("read_file", ToolAccessLevel.READ_ONLY));
        registry.register(createMockTool("list_dir", ToolAccessLevel.READ_ONLY));
        registry.register(createMockTool("search_code", ToolAccessLevel.READ_ONLY));
        registry.register(createMockTool("write_file", ToolAccessLevel.READ_WRITE));
        registry.register(createMockTool("delete_file", ToolAccessLevel.READ_WRITE));
        registry.register(createMockTool("execute_cmd", ToolAccessLevel.READ_WRITE));

        // Act & Assert
        assertEquals(6, registry.getToolCount(),
                "Should have 6 tools registered");

        List<AgentTool> masterTools = registry.getAvailableTools("MASTER");
        assertEquals(3, masterTools.size(),
                "MASTER should have 3 READ_ONLY tools");

        List<AgentTool> workerTools = registry.getAvailableTools("WORKER");
        assertEquals(6, workerTools.size(),
                "WORKER should have all 6 tools");
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    /**
     * Create a mock AgentTool for testing.
     *
     * @param name The tool name
     * @param accessLevel The tool's access level
     * @return A mock AgentTool
     */
    private AgentTool createMockTool(String name, ToolAccessLevel accessLevel) {
        return new AgentTool() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return "Mock tool: " + name + " (" + accessLevel + ")";
            }

            @Override
            public String getParametersJsonSchema() {
                return "{\"type\":\"object\"}";
            }

            @Override
            public ToolAccessLevel getAccessLevel() {
                return accessLevel;
            }

            @Override
            public String execute(String argumentsJson) throws Exception {
                return "Executed: " + name + " with " + argumentsJson;
            }

            @Override
            public String executeWithBypass(String argumentsJson) throws Exception {
                return "Bypass executed: " + name + " with " + argumentsJson;
            }
        };
    }
}
