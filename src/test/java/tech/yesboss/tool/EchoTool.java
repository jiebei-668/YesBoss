package tech.yesboss.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple echo tool for integration testing.
 *
 * <p>This tool returns the input text exactly as received, useful for testing
 * the WorkerRunner ReAct loop with real LLM integration.</p>
 */
public class EchoTool implements AgentTool {

    private static final Logger logger = LoggerFactory.getLogger(EchoTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PARAMETERS_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "text": {
                  "type": "string",
                  "description": "The text to echo back"
                }
              },
              "required": ["text"]
            }
            """;

    @Override
    public String getName() {
        return "echo";
    }

    @Override
    public String getDescription() {
        return "A simple echo tool that returns the input text exactly as received. " +
                "Useful for testing and debugging.";
    }

    @Override
    public String getParametersJsonSchema() {
        return PARAMETERS_SCHEMA;
    }

    @Override
    public ToolAccessLevel getAccessLevel() {
        return ToolAccessLevel.READ_ONLY;
    }

    @Override
    public String execute(String argumentsJson) throws Exception {
        logger.debug("EchoTool executing with arguments: {}", argumentsJson);
        return performEcho(argumentsJson);
    }

    @Override
    public String executeWithBypass(String argumentsJson) throws Exception {
        logger.debug("EchoTool executing with bypass: {}", argumentsJson);
        return performEcho(argumentsJson);
    }

    /**
     * Perform the actual echo.
     */
    private String performEcho(String argumentsJson) throws Exception {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);

            if (!args.has("text")) {
                return "Error: Missing required parameter 'text'";
            }

            String text = args.get("text").asText();
            logger.info("Echo: {}", text);
            return text;

        } catch (Exception e) {
            logger.error("Error parsing echo arguments: {}", argumentsJson, e);
            return "Error: Invalid JSON format for arguments. Expected: {\"text\": \"your message\"}";
        }
    }
}
