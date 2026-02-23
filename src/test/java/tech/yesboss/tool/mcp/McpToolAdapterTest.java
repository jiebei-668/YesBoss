package tech.yesboss.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.yesboss.tool.ToolAccessLevel;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for McpToolAdapter network delegation and parsing.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>McpToolAdapter correctly implements AgentTool interface</li>
 *   <li>HTTP requests are properly formatted according to MCP protocol</li>
 *   <li>MCP responses are correctly parsed into string results</li>
 *   <li>Error handling works for various failure scenarios</li>
 * </ul>
 */
@DisplayName("McpToolAdapter Tests")
class McpToolAdapterTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private com.sun.net.httpserver.HttpServer testServer;
    private int serverPort;
    private String mcpEndpoint;
    private String lastRequestBody;
    private String lastAuthHeader;

    @BeforeEach
    void setUp() throws Exception {
        // Start a test HTTP server
        testServer = com.sun.net.httpserver.HttpServer.create();
        serverPort = 8765;
        testServer.bind(new java.net.InetSocketAddress(serverPort), 0);
        mcpEndpoint = "http://localhost:" + serverPort + "/mcp";

        // Reset captured data
        lastRequestBody = null;
        lastAuthHeader = null;

        testServer.start();
    }

    @AfterEach
    void tearDown() {
        if (testServer != null) {
            testServer.stop(0);
        }
    }

    @Test
    @DisplayName("Constructor should create adapter with valid parameters")
    void testConstructorWithValidParameters() {
        // Act
        McpToolAdapter adapter = new McpToolAdapter(
                "test_tool",
                "A test MCP tool",
                "http://example.com/mcp",
                null,
                ToolAccessLevel.READ_ONLY
        );

        // Assert
        assertEquals("test_tool", adapter.getName());
        assertEquals("A test MCP tool", adapter.getDescription());
        assertEquals(ToolAccessLevel.READ_ONLY, adapter.getAccessLevel());
        assertEquals("http://example.com/mcp", adapter.getMcpEndpoint());
        assertNotNull(adapter.getParametersJsonSchema());
        assertTrue(adapter.getParametersJsonSchema().contains("\"type\""));
    }

    @Test
    @DisplayName("Constructor should throw exception for null tool name")
    void testConstructorWithNullToolName() {
        assertThrows(IllegalArgumentException.class,
                () -> new McpToolAdapter(null, "desc", "http://example.com", null, ToolAccessLevel.READ_WRITE));
    }

    @Test
    @DisplayName("Constructor should throw exception for empty tool name")
    void testConstructorWithEmptyToolName() {
        assertThrows(IllegalArgumentException.class,
                () -> new McpToolAdapter("", "desc", "http://example.com", null, ToolAccessLevel.READ_WRITE));
    }

    @Test
    @DisplayName("Constructor should throw exception for null description")
    void testConstructorWithNullDescription() {
        assertThrows(IllegalArgumentException.class,
                () -> new McpToolAdapter("tool", null, "http://example.com", null, ToolAccessLevel.READ_WRITE));
    }

    @Test
    @DisplayName("Constructor should throw exception for null endpoint")
    void testConstructorWithNullEndpoint() {
        assertThrows(IllegalArgumentException.class,
                () -> new McpToolAdapter("tool", "desc", null, null, ToolAccessLevel.READ_WRITE));
    }

    @Test
    @DisplayName("Constructor should throw exception for null access level")
    void testConstructorWithNullAccessLevel() {
        assertThrows(IllegalArgumentException.class,
                () -> new McpToolAdapter("tool", "desc", "http://example.com", null, null));
    }

    @Test
    @DisplayName("Constructor should use READ_WRITE as default access level")
    void testConstructorDefaultAccessLevel() {
        // Act
        McpToolAdapter adapter = new McpToolAdapter(
                "test_tool",
                "A test tool",
                "http://example.com/mcp",
                "token123"
        );

        // Assert
        assertEquals(ToolAccessLevel.READ_WRITE, adapter.getAccessLevel());
    }

    @Test
    @DisplayName("execute should send properly formatted MCP request")
    void testExecuteSendsFormattedRequest() throws Exception {
        // Arrange - Setup mock response
        setupMockResponse(200, createSuccessResponse("Tool executed successfully"));

        McpToolAdapter adapter = new McpToolAdapter(
                "weather_check",
                "Check the weather",
                mcpEndpoint,
                "test-token"
        );

        // Act
        String result = adapter.execute("{\"location\":\"Tokyo\",\"unit\":\"celsius\"}");

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("Tool executed successfully"));

        // Verify request format
        assertNotNull(lastRequestBody);
        JsonNode requestJson = objectMapper.readTree(lastRequestBody);
        assertEquals("2.0", requestJson.get("jsonrpc").asText());
        assertEquals("tools/call", requestJson.get("method").asText());
        assertEquals("weather_check", requestJson.get("params").get("name").asText());
        assertEquals("Bearer test-token", lastAuthHeader);
    }

    @Test
    @DisplayName("execute should parse content array from MCP response")
    void testExecuteParsesContentArray() throws Exception {
        // Arrange
        String response = "{\n" +
                "  \"result\": {\n" +
                "    \"content\": [\n" +
                "      {\"type\": \"text\", \"text\": \"First message\"},\n" +
                "      {\"type\": \"text\", \"text\": \"Second message\"}\n" +
                "    ]\n" +
                "  }\n" +
                "}";
        setupMockResponse(200, response);

        McpToolAdapter adapter = new McpToolAdapter(
                "multi_output",
                "Multi output tool",
                mcpEndpoint,
                null
        );

        // Act
        String result = adapter.execute("{\"query\":\"test\"}");

        // Assert
        assertTrue(result.contains("First message"));
        assertTrue(result.contains("Second message"));
    }

    @Test
    @DisplayName("execute should handle single content object")
    void testExecuteWithSingleContent() throws Exception {
        // Arrange
        String response = "{\n" +
                "  \"result\": {\n" +
                "    \"content\": {\"type\": \"text\", \"text\": \"Single result\"}\n" +
                "  }\n" +
                "}";
        setupMockResponse(200, response);

        McpToolAdapter adapter = new McpToolAdapter(
                "single_output",
                "Single output tool",
                mcpEndpoint,
                null
        );

        // Act
        String result = adapter.execute("{}");

        // Assert
        assertTrue(result.contains("Single result"));
    }

    @Test
    @DisplayName("execute should handle error response from MCP service")
    void testExecuteWithError() throws Exception {
        // Arrange
        String errorResponse = "{\n" +
                "  \"error\": {\n" +
                "    \"code\": -32600,\n" +
                "    \"message\": \"Invalid Request\"\n" +
                "  }\n" +
                "}";
        setupMockResponse(200, errorResponse);

        McpToolAdapter adapter = new McpToolAdapter(
                "failing_tool",
                "A tool that fails",
                mcpEndpoint,
                null
        );

        // Act & Assert
        Exception exception = assertThrows(Exception.class,
                () -> adapter.execute("{}"));
        assertTrue(exception.getMessage().contains("Invalid Request"));
    }

    @Test
    @DisplayName("execute should handle HTTP error status")
    void testExecuteWithHttpError() throws Exception {
        // Arrange
        setupMockResponse(500, "Internal Server Error");

        McpToolAdapter adapter = new McpToolAdapter(
                "server_error_tool",
                "Server error tool",
                mcpEndpoint,
                null
        );

        // Act & Assert
        Exception exception = assertThrows(Exception.class,
                () -> adapter.execute("{}"));
        assertTrue(exception.getMessage().contains("500"));
    }

    @Test
    @DisplayName("execute should handle empty arguments")
    void testExecuteWithEmptyArguments() throws Exception {
        // Arrange
        setupMockResponse(200, createSuccessResponse("Executed with empty args"));

        McpToolAdapter adapter = new McpToolAdapter(
                "no_args_tool",
                "No args tool",
                mcpEndpoint,
                null
        );

        // Act
        String result = adapter.execute("");

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("Executed with empty args"));
    }

    @Test
    @DisplayName("execute should handle null arguments")
    void testExecuteWithNullArguments() throws Exception {
        // Arrange
        setupMockResponse(200, createSuccessResponse("Executed with null args"));

        McpToolAdapter adapter = new McpToolAdapter(
                "null_args_tool",
                "Null args tool",
                mcpEndpoint,
                null
        );

        // Act
        String result = adapter.execute(null);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("Executed with null args"));
    }

    @Test
    @DisplayName("execute should include tool name in request params")
    void testExecuteIncludesToolName() throws Exception {
        // Arrange
        setupMockResponse(200, createSuccessResponse("OK"));

        McpToolAdapter adapter = new McpToolAdapter(
                "search_database",
                "Search database",
                mcpEndpoint,
                null
        );

        // Act
        adapter.execute("{\"query\":\"SELECT * FROM users\"}");

        // Assert
        JsonNode requestJson = objectMapper.readTree(lastRequestBody);
        assertEquals("search_database", requestJson.get("params").get("name").asText());
        assertEquals("SELECT * FROM users", requestJson.get("params").get("arguments").get("query").asText());
    }

    @Test
    @DisplayName("execute should not send auth header when token is null")
    void testExecuteWithoutAuth() throws Exception {
        // Arrange
        setupMockResponse(200, createSuccessResponse("OK"));

        McpToolAdapter adapter = new McpToolAdapter(
                "no_auth_tool",
                "No auth tool",
                mcpEndpoint,
                null
        );

        // Act
        adapter.execute("{}");

        // Assert
        assertNull(lastAuthHeader, "Auth header should not be present");
    }

    @Test
    @DisplayName("executeWithBypass should call execute")
    void testExecuteWithBypass() throws Exception {
        // Arrange
        setupMockResponse(200, createSuccessResponse("Bypass executed"));

        McpToolAdapter adapter = new McpToolAdapter(
                "bypass_tool",
                "Bypass tool",
            mcpEndpoint,
                null
        );

        // Act
        String result = adapter.executeWithBypass("{\"test\":\"value\"}");

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("Bypass executed"));
    }

    @Test
    @DisplayName("execute should handle complex nested arguments")
    void testExecuteWithComplexArguments() throws Exception {
        // Arrange
        setupMockResponse(200, createSuccessResponse("Complex args processed"));

        McpToolAdapter adapter = new McpToolAdapter(
                "complex_tool",
                "Complex tool",
                mcpEndpoint,
                null
        );

        String complexArgs = "{\"query\":\"SELECT * FROM users\",\"params\":[1,2,3],\"options\":{\"timeout\":5000,\"retries\":3}}";

        // Act
        String result = adapter.execute(complexArgs);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("Complex args processed"));

        // Verify arguments were passed correctly
        JsonNode requestJson = objectMapper.readTree(lastRequestBody);
        JsonNode args = requestJson.get("params").get("arguments");
        assertEquals("SELECT * FROM users", args.get("query").asText());
        assertEquals(1, args.get("params").get(0).asInt());
        assertEquals(5000, args.get("options").get("timeout").asInt());
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    /**
     * Setup the mock HTTP server to return a specific response
     */
    private void setupMockResponse(int statusCode, String responseBody) {
        testServer.createContext("/mcp", exchange -> {
            // Capture request details
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastRequestBody = requestBody;
            lastAuthHeader = exchange.getRequestHeaders().getFirst("Authorization");

            // Send response
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, responseBody.length());
            exchange.getResponseBody().write(responseBody.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
    }

    /**
     * Create a successful MCP response
     */
    private String createSuccessResponse(String content) {
        return "{\n" +
                "  \"result\": {\n" +
                "    \"content\": [\n" +
                "      {\"type\": \"text\", \"text\": \"" + content + "\"}\n" +
                "    ]\n" +
                "  }\n" +
                "}";
    }
}
