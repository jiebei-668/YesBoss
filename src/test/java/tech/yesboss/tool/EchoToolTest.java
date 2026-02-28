package tech.yesboss.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for EchoTool.
 */
class EchoToolTest {

    private EchoTool echo;

    @BeforeEach
    void setUp() {
        echo = new EchoTool();
    }

    @Test
    void testGetName() {
        assertEquals("echo", echo.getName());
    }

    @Test
    void testGetDescription() {
        String description = echo.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("echo"));
    }

    @Test
    void testGetParametersJsonSchema() {
        String schema = echo.getParametersJsonSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("text"));
    }

    @Test
    void testGetAccessLevel() {
        assertEquals(ToolAccessLevel.READ_ONLY, echo.getAccessLevel());
    }

    @Test
    void testEchoSimpleText() throws Exception {
        String result = echo.execute("{\"text\": \"Hello, World!\"}");
        assertEquals("Hello, World!", result);
    }

    @Test
    void testEchoEmptyString() throws Exception {
        String result = echo.execute("{\"text\": \"\"}");
        assertEquals("", result);
    }

    @Test
    void testEchoWithSpecialCharacters() throws Exception {
        String result = echo.execute("{\"text\": \"Hello! @#$%^&*()\"}");
        assertEquals("Hello! @#$%^&*()", result);
    }

    @Test
    void testEchoWithNewlines() throws Exception {
        String result = echo.execute("{\"text\": \"Line 1\\nLine 2\\nLine 3\"}");
        assertEquals("Line 1\nLine 2\nLine 3", result);
    }

    @Test
    void testEchoWithJsonContent() throws Exception {
        String jsonContent = "{\\\"name\\\": \\\"test\\\", \\\"value\\\": 123}";
        String result = echo.execute("{\"text\": \"" + jsonContent + "\"}");
        assertEquals("{\"name\": \"test\", \"value\": 123}", result);
    }

    @Test
    void testMissingTextParameter() throws Exception {
        String result = echo.execute("{}");
        assertTrue(result.contains("Missing required parameter"));
    }

    @Test
    void testInvalidJson() throws Exception {
        String result = echo.execute("invalid json");
        assertTrue(result.contains("Invalid JSON format"));
    }

    @Test
    void testExecuteWithBypass() throws Exception {
        String result = echo.executeWithBypass("{\"text\": \"Test with bypass\"}");
        assertEquals("Test with bypass", result);
    }

    @Test
    void testEchoLongText() throws Exception {
        String longText = "A".repeat(1000);
        String result = echo.execute("{\"text\": \"" + longText + "\"}");
        assertEquals(longText, result);
    }

    @Test
    void testEchoWithUnicode() throws Exception {
        String result = echo.execute("{\"text\": \"Hello 世界 🌍\"}");
        assertEquals("Hello 世界 🌍", result);
    }
}
