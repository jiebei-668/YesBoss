package tech.yesboss.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for CalculatorTool.
 */
class CalculatorToolTest {

    private CalculatorTool calculator;

    @BeforeEach
    void setUp() {
        calculator = new CalculatorTool();
    }

    @Test
    void testGetName() {
        assertEquals("calculator", calculator.getName());
    }

    @Test
    void testGetDescription() {
        String description = calculator.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("calculator"));
        assertTrue(description.contains("add"));
        assertTrue(description.contains("subtract"));
        assertTrue(description.contains("multiply"));
        assertTrue(description.contains("divide"));
    }

    @Test
    void testGetParametersJsonSchema() {
        String schema = calculator.getParametersJsonSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("operation"));
        assertTrue(schema.contains("a"));
        assertTrue(schema.contains("b"));
        assertTrue(schema.contains("add"));
        assertTrue(schema.contains("subtract"));
        assertTrue(schema.contains("multiply"));
        assertTrue(schema.contains("divide"));
    }

    @Test
    void testGetAccessLevel() {
        assertEquals(ToolAccessLevel.READ_WRITE, calculator.getAccessLevel());
    }

    @Test
    void testAddOperation() throws Exception {
        String result = calculator.execute("{\"operation\": \"add\", \"a\": 15, \"b\": 27}");
        assertEquals("42", result);
    }

    @Test
    void testSubtractOperation() throws Exception {
        String result = calculator.execute("{\"operation\": \"subtract\", \"a\": 50, \"b\": 23}");
        assertEquals("27", result);
    }

    @Test
    void testMultiplyOperation() throws Exception {
        String result = calculator.execute("{\"operation\": \"multiply\", \"a\": 6, \"b\": 7}");
        assertEquals("42", result);
    }

    @Test
    void testDivideOperation() throws Exception {
        String result = calculator.execute("{\"operation\": \"divide\", \"a\": 84, \"b\": 2}");
        assertEquals("42", result);
    }

    @Test
    void testDivideWithDecimalResult() throws Exception {
        String result = calculator.execute("{\"operation\": \"divide\", \"a\": 10, \"b\": 3}");
        assertEquals("3.3333333333333335", result);
    }

    @Test
    void testDivisionByZero() throws Exception {
        String result = calculator.execute("{\"operation\": \"divide\", \"a\": 10, \"b\": 0}");
        assertTrue(result.contains("Division by zero"));
    }

    @Test
    void testInvalidOperation() throws Exception {
        String result = calculator.execute("{\"operation\": \"modulo\", \"a\": 10, \"b\": 3}");
        assertTrue(result.contains("Invalid operation"));
    }

    @Test
    void testMissingParameters() throws Exception {
        String result = calculator.execute("{\"operation\": \"add\"}");
        assertTrue(result.contains("Missing required parameters"));
    }

    @Test
    void testInvalidJson() throws Exception {
        String result = calculator.execute("invalid json");
        assertTrue(result.contains("Invalid JSON format"));
    }

    @Test
    void testExecuteWithBypass() throws Exception {
        String result = calculator.executeWithBypass("{\"operation\": \"add\", \"a\": 5, \"b\": 3}");
        assertEquals("8", result);
    }

    @Test
    void testNegativeNumbers() throws Exception {
        String result = calculator.execute("{\"operation\": \"add\", \"a\": -10, \"b\": 5}");
        assertEquals("-5", result);
    }

    @Test
    void testDecimalNumbers() throws Exception {
        String result = calculator.execute("{\"operation\": \"multiply\", \"a\": 2.5, \"b\": 4}");
        assertEquals("10", result);  // formatResult removes .0 for whole numbers
    }
}
