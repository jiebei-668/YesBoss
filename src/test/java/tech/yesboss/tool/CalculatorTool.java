package tech.yesboss.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple calculator tool for integration testing.
 *
 * <p>This tool implements basic arithmetic operations (add, subtract, multiply, divide)
 * for testing the WorkerRunner ReAct loop with real LLM integration.</p>
 */
public class CalculatorTool implements AgentTool {

    private static final Logger logger = LoggerFactory.getLogger(CalculatorTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PARAMETERS_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "operation": {
                  "type": "string",
                  "enum": ["add", "subtract", "multiply", "divide"],
                  "description": "The arithmetic operation to perform"
                },
                "a": {
                  "type": "number",
                  "description": "The first number"
                },
                "b": {
                  "type": "number",
                  "description": "The second number"
                }
              },
              "required": ["operation", "a", "b"]
            }
            """;

    @Override
    public String getName() {
        return "calculator";
    }

    @Override
    public String getDescription() {
        return "A simple calculator that can add, subtract, multiply, and divide numbers. " +
                "Provide the operation (add/subtract/multiply/divide) and two numbers (a and b).";
    }

    @Override
    public String getParametersJsonSchema() {
        return PARAMETERS_SCHEMA;
    }

    @Override
    public ToolAccessLevel getAccessLevel() {
        return ToolAccessLevel.READ_WRITE;
    }

    @Override
    public String execute(String argumentsJson) throws Exception {
        logger.debug("CalculatorTool executing with arguments: {}", argumentsJson);
        return performCalculation(argumentsJson);
    }

    @Override
    public String executeWithBypass(String argumentsJson) throws Exception {
        logger.debug("CalculatorTool executing with bypass: {}", argumentsJson);
        return performCalculation(argumentsJson);
    }

    /**
     * Perform the actual calculation.
     */
    private String performCalculation(String argumentsJson) throws Exception {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);

            if (!args.has("operation") || !args.has("a") || !args.has("b")) {
                return "Error: Missing required parameters. Required: operation, a, b";
            }

            String operation = args.get("operation").asText();
            double a = args.get("a").asDouble();
            double b = args.get("b").asDouble();

            double result;
            switch (operation.toLowerCase()) {
                case "add":
                    result = a + b;
                    break;
                case "subtract":
                    result = a - b;
                    break;
                case "multiply":
                    result = a * b;
                    break;
                case "divide":
                    if (b == 0) {
                        return "Error: Division by zero is not allowed";
                    }
                    result = a / b;
                    break;
                default:
                    return "Error: Invalid operation '" + operation + "'. Valid operations: add, subtract, multiply, divide";
            }

            String formattedResult = formatResult(result);
            logger.info("Calculator: {} {} {} = {}", a, operation, b, formattedResult);
            return formattedResult;

        } catch (Exception e) {
            logger.error("Error parsing calculator arguments: {}", argumentsJson, e);
            return "Error: Invalid JSON format for arguments. Expected: {\"operation\": \"add\", \"a\": 5, \"b\": 3}";
        }
    }

    /**
     * Format the result - remove unnecessary decimal places for whole numbers.
     */
    private String formatResult(double result) {
        if (result == (long) result) {
            return String.format("%d", (long) result);
        } else {
            return String.format("%s", result);
        }
    }
}
