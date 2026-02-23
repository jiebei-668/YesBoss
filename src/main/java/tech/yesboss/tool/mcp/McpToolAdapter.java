package tech.yesboss.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.tool.AgentTool;
import tech.yesboss.tool.ToolAccessLevel;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * MCP (Model Context Protocol) 工具适配器
 *
 * <p>将外部 MCP 服务适配为标准的 AgentTool 接口，使系统能够调用
 * 通过 MCP 协议暴露的外部工具和服务。</p>
 *
 * <p><b>MCP 协议支持：</b></p>
 * <ul>
 *   <li>HTTP/HTTPS 调用外部 MCP 服务</li>
 *   <li>JSON Schema 参数转换</li>
 *   <li>响应解析为标准字符串格式</li>
 *   <li>可配置超时和认证</li>
 * </ul>
 */
public class McpToolAdapter implements AgentTool {

    private static final Logger logger = LoggerFactory.getLogger(McpToolAdapter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final String toolName;
    private final String description;
    private final String parametersJsonSchema;
    private final String mcpEndpoint;
    private final String authToken;
    private final HttpClient httpClient;
    private final ToolAccessLevel accessLevel;

    /**
     * 创建一个新的 MCP 工具适配器
     *
     * @param toolName 工具名称
     * @param description 工具描述
     * @param mcpEndpoint MCP 服务端点 URL
     * @param authToken 认证令牌（可选，可为 null）
     * @param accessLevel 工具访问级别
     */
    public McpToolAdapter(String toolName, String description, String mcpEndpoint,
                          String authToken, ToolAccessLevel accessLevel) {
        if (toolName == null || toolName.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool name cannot be null or empty");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description cannot be null or empty");
        }
        if (mcpEndpoint == null || mcpEndpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("MCP endpoint cannot be null or empty");
        }
        if (accessLevel == null) {
            throw new IllegalArgumentException("Access level cannot be null");
        }

        this.toolName = toolName;
        this.description = description;
        this.mcpEndpoint = mcpEndpoint;
        this.authToken = authToken;
        this.accessLevel = accessLevel;

        // Create HTTP client with timeout
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .build();

        // Generate parameters JSON schema from MCP endpoint
        this.parametersJsonSchema = generateSchemaFromEndpoint();

        logger.info("Created MCP tool adapter: {} -> {}", toolName, mcpEndpoint);
    }

    /**
     * 创建 MCP 工具适配器（默认 READ_WRITE 访问级别）
     */
    public McpToolAdapter(String toolName, String description, String mcpEndpoint, String authToken) {
        this(toolName, description, mcpEndpoint, authToken, ToolAccessLevel.READ_WRITE);
    }

    @Override
    public String getName() {
        return toolName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getParametersJsonSchema() {
        return parametersJsonSchema;
    }

    @Override
    public ToolAccessLevel getAccessLevel() {
        return accessLevel;
    }

    @Override
    public String execute(String argumentsJson) throws Exception {
        logger.debug("Executing MCP tool: {} with arguments: {}", toolName, argumentsJson);

        try {
            // Validate and parse arguments
            if (argumentsJson == null || argumentsJson.trim().isEmpty()) {
                argumentsJson = "{}";
            }

            JsonNode argsNode = objectMapper.readTree(argumentsJson);

            // Build MCP protocol request
            ObjectNode mcpRequest = objectMapper.createObjectNode();
            mcpRequest.put("jsonrpc", "2.0");
            mcpRequest.put("id", System.currentTimeMillis());
            mcpRequest.put("method", "tools/call");

            ObjectNode params = mcpRequest.putObject("params");
            params.put("name", toolName);
            params.set("arguments", argsNode);

            String requestBody = objectMapper.writeValueAsString(mcpRequest);
            logger.debug("MCP request: {}", requestBody);

            // Build HTTP request
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(mcpEndpoint))
                    .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));

            // Add auth token if provided
            if (authToken != null && !authToken.trim().isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + authToken);
            }

            HttpRequest httpRequest = requestBuilder.build();

            // Execute HTTP call
            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            String responseBody = response.body();
            logger.debug("MCP response status: {}, body: {}", response.statusCode(), responseBody);

            // Parse MCP response
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode responseJson = objectMapper.readTree(responseBody);

                // Extract result content from MCP response
                if (responseJson.has("result")) {
                    JsonNode result = responseJson.get("result");

                    // Handle different MCP result formats
                    if (result.has("content")) {
                        JsonNode content = result.get("content");
                        if (content.isArray()) {
                            // Format array of content items
                            StringBuilder sb = new StringBuilder();
                            content.forEach(item -> {
                                if (item.has("text")) {
                                    sb.append(item.get("text").asText());
                                } else if (item.has("type")) {
                                    sb.append("[MCP Content: ").append(item.get("type").asText()).append("]");
                                }
                            });
                            return sb.toString();
                        } else {
                            return content.toString();
                        }
                    } else {
                        return result.toString();
                    }
                } else if (responseJson.has("error")) {
                    JsonNode error = responseJson.get("error");
                    String errorMsg = error.has("message") ? error.get("message").asText() : error.toString();
                    throw new Exception("MCP service error: " + errorMsg);
                } else {
                    return responseBody;
                }
            } else {
                throw new Exception("MCP service returned error status: " + response.statusCode() +
                        ", body: " + responseBody);
            }

        } catch (Exception e) {
            logger.error("Failed to execute MCP tool: {}", toolName, e);
            throw new Exception("MCP tool execution failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String executeWithBypass(String argumentsJson) throws Exception {
        // For MCP tools, bypass mode is same as normal execution
        // The sandbox check is done before calling this method
        logger.info("Executing MCP tool with bypass: {}", toolName);
        return execute(argumentsJson);
    }

    /**
     * 从 MCP 端点生成参数 JSON Schema
     *
     * <p>在实际部署中，这会调用 MCP 服务的 schema 端点。
     * 这里我们生成一个通用的 schema。</p>
     *
     * @return JSON Schema 格式的参数定义
     */
    private String generateSchemaFromEndpoint() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        // Add common MCP properties
        ObjectNode inputProp = properties.putObject("input");
        inputProp.put("type", "string");
        inputProp.put("description", "Tool input arguments");

        schema.putObject("additionalProperties").put("additionalProperties", true);

        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            logger.warn("Failed to generate schema, using default", e);
            return "{\"type\":\"object\",\"properties\":{}}";
        }
    }

    /**
     * 获取 MCP 端点 URL
     *
     * @return MCP 服务端点
     */
    public String getMcpEndpoint() {
        return mcpEndpoint;
    }

    /**
     * 获取认证令牌
     *
     * @return 认证令牌（可能为 null）
     */
    public String getAuthToken() {
        return authToken;
    }
}
