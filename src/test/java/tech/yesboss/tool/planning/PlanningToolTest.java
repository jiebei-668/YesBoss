package tech.yesboss.tool.planning;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.llm.LlmClient;
import tech.yesboss.tool.ToolAccessLevel;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test class for PlanningTool
 *
 * <p>Tests the planning tool's ability to break down large tasks into sub-tasks.</p>
 */
class PlanningToolTest {

    private LlmClient llmClient;
    private PlanningTool planningTool;

    private static final String VALID_TASK_JSON = """
        {"taskDescription": "Build a web application", "context": "E-commerce site", "constraints": "Use Java"}
        """;

    private static final String VALID_PLAN_RESPONSE = """
        [
          {"id": "task-1", "description": "Design database schema", "priority": "high"},
          {"id": "task-2", "description": "Implement user authentication", "priority": "high"},
          {"id": "task-3", "description": "Create product catalog", "priority": "medium"}
        ]
        """;

    private static final String PLAN_WITH_EXTRA_TEXT = """
        Here is the execution plan for your task:

        [
          {"id": "task-1", "description": "Design database schema", "priority": "high"},
          {"id": "task-2", "description": "Implement user authentication", "priority": "high"}
        ]

        This plan covers all the required aspects.
        """;

    // ==========================================
    // Constructor Tests
    // ==========================================

    @Test
    void testConstructorWithValidLlmClient() {
        LlmClient mockClient = mock(LlmClient.class);
        assertDoesNotThrow(() -> new PlanningTool(mockClient));
    }

    @Test
    void testConstructorWithNullLlmClient() {
        assertThrows(IllegalArgumentException.class, () -> new PlanningTool(null));
    }

    // ==========================================
    // AgentTool Interface Tests
    // ==========================================

    @Test
    void testGetName() {
        LlmClient mockClient = mock(LlmClient.class);
        planningTool = new PlanningTool(mockClient);

        assertEquals("planning_tool", planningTool.getName());
    }

    @Test
    void testGetDescription() {
        LlmClient mockClient = mock(LlmClient.class);
        planningTool = new PlanningTool(mockClient);

        String description = planningTool.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("子任务"));
        assertTrue(description.contains("JSON"));
    }

    @Test
    void testGetParametersJsonSchema() {
        LlmClient mockClient = mock(LlmClient.class);
        planningTool = new PlanningTool(mockClient);

        String schema = planningTool.getParametersJsonSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("array"));
        assertTrue(schema.contains("id"));
        assertTrue(schema.contains("description"));
        assertTrue(schema.contains("priority"));
    }

    @Test
    void testGetAccessLevel() {
        LlmClient mockClient = mock(LlmClient.class);
        planningTool = new PlanningTool(mockClient);

        assertEquals(ToolAccessLevel.READ_ONLY, planningTool.getAccessLevel());
    }

    // ==========================================
    // Execute Method Tests
    // ==========================================

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        planningTool = new PlanningTool(llmClient);
    }

    @Test
    void testExecuteWithValidInputAndCleanJsonResponse() throws Exception {
        // Setup
        UnifiedMessage mockResponse = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, VALID_PLAN_RESPONSE);
        when(llmClient.chat(any(List.class), any(String.class))).thenReturn(mockResponse);

        // Execute
        String result = planningTool.execute(VALID_TASK_JSON);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("task-1"));
        assertTrue(result.contains("Design database schema"));
        assertTrue(result.contains("high"));

        verify(llmClient).chat(any(List.class), any(String.class));
    }

    @Test
    void testExecuteWithJsonEmbeddedInText() throws Exception {
        // Setup
        UnifiedMessage mockResponse = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, PLAN_WITH_EXTRA_TEXT);
        when(llmClient.chat(any(List.class), any(String.class))).thenReturn(mockResponse);

        // Execute
        String result = planningTool.execute(VALID_TASK_JSON);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("task-1"));
        assertTrue(result.contains("Design database schema"));
        // Should be extracted without the extra text
        assertFalse(result.contains("Here is the execution plan"));

        verify(llmClient).chat(any(List.class), any(String.class));
    }

    @Test
    void testExecuteWithNullArguments() {
        assertThrows(Exception.class, () -> planningTool.execute(null));
    }

    @Test
    void testExecuteWithInvalidJson() {
        String invalidJson = "{invalid json";

        assertThrows(Exception.class, () -> planningTool.execute(invalidJson));
    }

    @Test
    void testExecuteWithLlmReturningNonJson() throws Exception {
        // Setup
        UnifiedMessage mockResponse = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "This is just plain text, no JSON here");
        when(llmClient.chat(any(List.class), any(String.class))).thenReturn(mockResponse);

        // Execute & Verify
        assertThrows(Exception.class, () -> planningTool.execute(VALID_TASK_JSON));
    }

    @Test
    void testExecuteWithLlmReturningJsonObjectInsteadOfArray() throws Exception {
        // Setup
        String invalidResponse = """
            {"task": "task-1", "description": "Single task"}
            """;
        UnifiedMessage mockResponse = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, invalidResponse);
        when(llmClient.chat(any(List.class), any(String.class))).thenReturn(mockResponse);

        // Execute & Verify
        assertThrows(Exception.class, () -> planningTool.execute(VALID_TASK_JSON));
    }

    @Test
    void testExecuteWithMinimalTaskDescription() throws Exception {
        // Setup
        String minimalJson = """
            {"taskDescription": "Simple task"}
            """;
        UnifiedMessage mockResponse = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, VALID_PLAN_RESPONSE);
        when(llmClient.chat(any(List.class), any(String.class))).thenReturn(mockResponse);

        // Execute
        String result = planningTool.execute(minimalJson);

        // Verify
        assertNotNull(result);
        verify(llmClient).chat(any(List.class), any(String.class));
    }

    @Test
    void testExecuteWithFullContext() throws Exception {
        // Setup
        String fullContextJson = """
            {
              "taskDescription": "Build API",
              "context": "RESTful API for user management",
              "constraints": "Must use Spring Boot"
            }
            """;
        UnifiedMessage mockResponse = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, VALID_PLAN_RESPONSE);
        when(llmClient.chat(any(List.class), any(String.class))).thenReturn(mockResponse);

        // Execute
        String result = planningTool.execute(fullContextJson);

        // Verify
        assertNotNull(result);
        verify(llmClient).chat(any(List.class), any(String.class));

        // Verify the prompt was constructed correctly with all context
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        var stringCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(llmClient).chat(captor.capture(), stringCaptor.capture());

        List<UnifiedMessage> messages = captor.getValue();
        assertEquals(1, messages.size()); // Only user prompt, system prompt is passed as second param

        UnifiedMessage userPrompt = messages.get(0);
        String content = userPrompt.content();
        assertTrue(content.contains("Build API"));
        assertTrue(content.contains("RESTful API for user management"));
        assertTrue(content.contains("Must use Spring Boot"));
    }

    // ==========================================
    // ExecuteWithBypass Tests
    // ==========================================

    @Test
    void testExecuteWithBypassCallsExecute() throws Exception {
        // Setup
        UnifiedMessage mockResponse = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, VALID_PLAN_RESPONSE);
        when(llmClient.chat(any(List.class), any(String.class))).thenReturn(mockResponse);

        // Execute
        String result = planningTool.executeWithBypass(VALID_TASK_JSON);

        // Verify
        assertNotNull(result);
        verify(llmClient).chat(any(List.class), any(String.class));
    }

    // ==========================================
    // Edge Case Tests
    // ==========================================

    @Test
    void testExecuteWithEmptyTaskDescription() throws Exception {
        // Setup
        String emptyTaskJson = """
            {"taskDescription": ""}
            """;
        UnifiedMessage mockResponse = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, VALID_PLAN_RESPONSE);
        when(llmClient.chat(any(List.class), any(String.class))).thenReturn(mockResponse);

        // Execute - should still work (LLM will handle empty description)
        String result = planningTool.execute(emptyTaskJson);

        // Verify
        assertNotNull(result);
        verify(llmClient).chat(any(List.class), any(String.class));
    }

    @Test
    void testExecuteWithMalformedJsonInResponse() throws Exception {
        // Setup
        String malformedJson = """
            [
              {"id": "task-1", "description": "Task 1", "priority": "high"
            ]
            """;  // Missing closing brace
        UnifiedMessage mockResponse = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, malformedJson);
        when(llmClient.chat(any(List.class), any(String.class))).thenReturn(mockResponse);

        // Execute & Verify
        assertThrows(Exception.class, () -> planningTool.execute(VALID_TASK_JSON));
    }

    @Test
    void testExecuteWithVeryLargePlan() throws Exception {
        // Setup
        StringBuilder largePlan = new StringBuilder("[\n");
        for (int i = 1; i <= 20; i++) {
            if (i > 1) largePlan.append(",\n");
            largePlan.append(String.format(
                "  {\"id\": \"task-%d\", \"description\": \"Sub task %d\", \"priority\": \"medium\"}",
                i, i
            ));
        }
        largePlan.append("\n]");

        UnifiedMessage mockResponse = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, largePlan.toString());
        when(llmClient.chat(any(List.class), any(String.class))).thenReturn(mockResponse);

        // Execute
        String result = planningTool.execute(VALID_TASK_JSON);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("task-1"));
        assertTrue(result.contains("task-20"));

        verify(llmClient).chat(any(List.class), any(String.class));
    }

    @Test
    void testExecuteWithLlmException() throws Exception {
        // Setup
        when(llmClient.chat(any(List.class), any(String.class))).thenThrow(new RuntimeException("LLM service unavailable"));

        // Execute & Verify
        assertThrows(Exception.class, () -> planningTool.execute(VALID_TASK_JSON));
    }

    // ==========================================
    // Schema Validation Tests (Task 18)
    // ==========================================

    @Test
    void testExecuteReturnsValidJsonArrayMatchingSchema() throws Exception {
        // Setup
        UnifiedMessage mockResponse = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, VALID_PLAN_RESPONSE);
        when(llmClient.chat(any(List.class), any(String.class))).thenReturn(mockResponse);

        // Execute
        String result = planningTool.execute(VALID_TASK_JSON);

        // Parse JSON and validate schema
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        List<Map<String, Object>> planArray = mapper.readValue(result,
            new TypeReference<List<Map<String, Object>>>() {});

        // Verify it's an array
        assertNotNull(planArray);
        assertFalse(planArray.isEmpty(), "Plan array should not be empty");

        // Validate each task object matches the schema
        for (Map<String, Object> task : planArray) {
            // Verify required fields exist
            assertTrue(task.containsKey("id"), "Task must have 'id' field");
            assertTrue(task.containsKey("description"), "Task must have 'description' field");
            assertTrue(task.containsKey("priority"), "Task must have 'priority' field");

            // Verify field types
            Object id = task.get("id");
            Object description = task.get("description");
            Object priority = task.get("priority");

            assertTrue(id instanceof String, "Task 'id' must be a string");
            assertTrue(description instanceof String, "Task 'description' must be a string");
            assertTrue(priority instanceof String, "Task 'priority' must be a string");

            // Verify priority is one of the allowed enum values
            String priorityValue = (String) priority;
            assertTrue(java.util.List.of("high", "medium", "low").contains(priorityValue),
                "Task 'priority' must be one of: high, medium, low");

            // Verify id and description are not empty
            assertFalse(((String) id).trim().isEmpty(), "Task 'id' cannot be empty");
            assertFalse(((String) description).trim().isEmpty(), "Task 'description' cannot be empty");
        }

        verify(llmClient).chat(any(List.class), any(String.class));
    }

    @Test
    void testExecuteWithComplexUserRequirementGeneratesValidPlan() throws Exception {
        // Setup - complex requirement
        String complexRequirement = """
            {
              "taskDescription": "Build a complete e-commerce platform with user authentication, product catalog, shopping cart, payment integration, and order management",
              "context": "Startup company, needs MVP in 3 months",
              "constraints": "Must use Java, Spring Boot, and PostgreSQL"
            }
            """;

        String complexPlanResponse = """
            [
              {"id": "task-1", "description": "Design database schema for users, products, and orders", "priority": "high"},
              {"id": "task-2", "description": "Implement user registration and authentication", "priority": "high"},
              {"id": "task-3", "description": "Create product catalog API with search and filtering", "priority": "high"},
              {"id": "task-4", "description": "Build shopping cart functionality", "priority": "medium"},
              {"id": "task-5", "description": "Integrate payment gateway", "priority": "high"},
              {"id": "task-6", "description": "Implement order management system", "priority": "medium"}
            ]
            """;

        UnifiedMessage mockResponse = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, complexPlanResponse);
        when(llmClient.chat(any(List.class), any(String.class))).thenReturn(mockResponse);

        // Execute
        String result = planningTool.execute(complexRequirement);

        // Validate schema
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        List<Map<String, Object>> planArray = mapper.readValue(result,
            new TypeReference<List<Map<String, Object>>>() {});

        // Verify we got 6 tasks
        assertEquals(6, planArray.size(), "Complex requirement should generate multiple sub-tasks");

        // Verify all tasks follow the schema
        for (java.util.Map<String, Object> task : planArray) {
            assertEquals(3, task.size(), "Task should have exactly 3 fields");
            assertTrue(task.containsKey("id"));
            assertTrue(task.containsKey("description"));
            assertTrue(task.containsKey("priority"));
        }

        verify(llmClient).chat(any(List.class), any(String.class));
    }
}
