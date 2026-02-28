package tech.yesboss.llm.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.domain.message.UnifiedMessage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Zhipu GLM LLM Client implementation for real LLM integration.
 *
 * <p>This client implements the LlmClient interface for Zhipu GLM API integration.
 * It supports both chat interactions and summarization capabilities.</p>
 *
 * <p>API Documentation: https://open.bigmodel.cn/dev/api#glm-4</p>
 *
 * <p>Key features:
 * <ul>
 *   <li>Real API calls to Zhipu GLM chat completions endpoint</li>
 *   <li>Tool call support in responses</li>
 *   <li>Configurable model, max tokens, temperature, and timeouts</li>
 *   <li>Error handling for 401, 429, 500 status codes</li>
 * </ul>
 */
public class ZhipuLlmClient implements tech.yesboss.llm.LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(ZhipuLlmClient.class);

    private static final String CHAT_COMPLETIONS_ENDPOINT = "/api/paas/v4/chat/completions";
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_READ_TIMEOUT_SECONDS = 60;

    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final double temperature;
    private final int connectTimeoutSeconds;
    private final int readTimeoutSeconds;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Create a new ZhipuLlmClient with all parameters.
     *
     * @param apiKey            Zhipu API key (required)
     * @param model             Model name (e.g., "glm-4-flash", default "glm-4-flash")
     * @param maxTokens         Maximum tokens in response (default 4096)
     * @param temperature       Temperature for response generation (default 0.7)
     * @param connectTimeout    Connection timeout in seconds (default 10)
     * @param readTimeout       Read timeout in seconds (default 60)
     * @param baseUrl           Base URL for API (default https://open.bigmodel.cn)
     */
    public ZhipuLlmClient(String apiKey, String model, int maxTokens, double temperature,
                          int connectTimeout, int readTimeout, String baseUrl) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("Model cannot be null or empty");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("Max tokens must be positive");
        }
        if (temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("Temperature must be between 0 and 2");
        }

        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.connectTimeoutSeconds = connectTimeout;
        this.readTimeoutSeconds = readTimeout;
        this.baseUrl = baseUrl != null ? baseUrl : "https://open.bigmodel.cn";

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeout))
                .build();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        // Ignore unknown properties to handle API changes gracefully
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        logger.info("ZhipuLlmClient initialized with model: {}, baseUrl: {}", model, this.baseUrl);
    }

    /**
     * Create a new ZhipuLlmClient with default parameters.
     *
     * @param apiKey Zhipu API key
     */
    public ZhipuLlmClient(String apiKey) {
        this(apiKey, "glm-4-flash", 4096, 0.7,
                DEFAULT_CONNECT_TIMEOUT_SECONDS, DEFAULT_READ_TIMEOUT_SECONDS,
                "https://open.bigmodel.cn");
    }

    /**
     * Create a new ZhipuLlmClient with custom model but default other parameters.
     *
     * @param apiKey Zhipu API key
     * @param model  Model name
     */
    public ZhipuLlmClient(String apiKey, String model) {
        this(apiKey, model, 4096, 0.7,
                DEFAULT_CONNECT_TIMEOUT_SECONDS, DEFAULT_READ_TIMEOUT_SECONDS,
                "https://open.bigmodel.cn");
    }

    /**
     * Create a new ZhipuLlmClient with custom parameters.
     *
     * @param apiKey      Zhipu API key
     * @param model       Model name
     * @param maxTokens   Maximum tokens in response
     * @param temperature Temperature for response generation
     */
    public ZhipuLlmClient(String apiKey, String model, int maxTokens, double temperature) {
        this(apiKey, model, maxTokens, temperature,
                DEFAULT_CONNECT_TIMEOUT_SECONDS, DEFAULT_READ_TIMEOUT_SECONDS,
                "https://open.bigmodel.cn");
    }

    @Override
    public UnifiedMessage chat(List<UnifiedMessage> messages, String systemPrompt) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Messages cannot be null or empty");
        }

        logger.debug("Sending chat request to Zhipu GLM: {} messages, model: {}, system prompt: {}",
                messages.size(), model, systemPrompt != null ? "yes" : "no");

        try {
            // Build request body
            ChatRequest request = buildChatRequest(messages, systemPrompt);
            String requestBody = objectMapper.writeValueAsString(request);

            logger.debug("Request body: {}", requestBody);

            // Build HTTP request
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + CHAT_COMPLETIONS_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + generateJwtToken(apiKey))
                    .timeout(Duration.ofSeconds(readTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            // Send request
            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            // Handle response
            int statusCode = response.statusCode();
            String responseBody = response.body();

            logger.debug("Response status: {}, body: {}", statusCode, responseBody);

            if (statusCode == 401) {
                throw new RuntimeException("Invalid Zhipu API key - authentication failed (401)");
            } else if (statusCode == 429) {
                throw new RuntimeException("Zhipu API rate limit exceeded (429) - please retry later");
            } else if (statusCode >= 500) {
                throw new RuntimeException("Zhipu API server error (" + statusCode + ") - service unavailable");
            } else if (statusCode != 200) {
                throw new RuntimeException("Zhipu API request failed with status " + statusCode + ": " + responseBody);
            }

            // Parse response
            ChatResponse chatResponse = objectMapper.readValue(responseBody, ChatResponse.class);

            if (chatResponse.choices == null || chatResponse.choices.isEmpty()) {
                throw new RuntimeException("Empty response from Zhipu API");
            }

            ChatChoice choice = chatResponse.choices.get(0);
            ChatMessage message = choice.message;

            // Check for tool calls in response
            if (message.toolCalls != null && !message.toolCalls.isEmpty()) {
                List<UnifiedMessage.ToolCall> toolCalls = new ArrayList<>();
                for (ToolCall toolCall : message.toolCalls) {
                    toolCalls.add(new UnifiedMessage.ToolCall(
                            toolCall.id,
                            toolCall.function.name,
                            toolCall.function.arguments
                    ));
                }
                logger.info("Zhipu GLM returned {} tool calls", toolCalls.size());
                return UnifiedMessage.ofToolCalls(toolCalls);
            }

            // Return text response
            String content = message.content != null ? message.content : "";
            logger.info("Zhipu GLM chat request completed, response length: {}", content.length());
            logger.info("Token usage - prompt: {}, completion: {}, total: {}",
                    chatResponse.usage.promptTokens, chatResponse.usage.completionTokens, chatResponse.usage.totalTokens);

            return UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, content);

        } catch (JsonProcessingException e) {
            logger.error("JSON processing error during Zhipu GLM chat", e);
            throw new RuntimeException("JSON processing error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Request interrupted during Zhipu GLM chat", e);
            throw new RuntimeException("Request interrupted: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error during Zhipu GLM chat", e);
            throw new RuntimeException("Unexpected error during Zhipu GLM chat: " + e.getMessage(), e);
        }
    }

    @Override
    public String summarize(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Content cannot be null or empty");
        }

        logger.debug("Sending summarization request to Zhipu GLM, content length: {}", content.length());

        try {
            // Create summarization prompt
            List<UnifiedMessage> messages = List.of(
                    UnifiedMessage.ofText(UnifiedMessage.Role.USER,
                            "Please provide a concise summary of the following content:\n\n" + content)
            );

            // Use chat API for summarization
            UnifiedMessage response = chat(messages, null);
            String summary = response.content();

            logger.info("Summarization completed successfully, summary length: {}", summary.length());
            return summary;

        } catch (Exception e) {
            logger.error("Error during summarization", e);
            throw new RuntimeException("Error during summarization: " + e.getMessage(), e);
        }
    }

    /**
     * Build chat request from UnifiedMessage list.
     */
    private ChatRequest buildChatRequest(List<UnifiedMessage> messages, String systemPrompt) {
        List<ChatMessage> chatMessages = new ArrayList<>();

        // Add system prompt if provided
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            chatMessages.add(new ChatMessage("system", systemPrompt));
        }

        // Convert UnifiedMessages to ChatMessages
        for (UnifiedMessage msg : messages) {
            String role = convertRole(msg.role());
            String content = msg.content();

            // Handle tool results
            if (msg.hasToolResults()) {
                for (UnifiedMessage.ToolResult toolResult : msg.toolResults()) {
                    String toolContent = String.format(
                            "{\"tool_call_id\": \"%s\", \"output\": %s}",
                            toolResult.toolCallId(),
                            escapeJsonString(toolResult.resultString())
                    );
                    chatMessages.add(new ChatMessage("tool", toolContent));
                }
            }

            // Handle tool calls (for multi-turn conversations)
            if (msg.hasToolCalls()) {
                StringBuilder toolCallsContent = new StringBuilder();
                for (UnifiedMessage.ToolCall toolCall : msg.toolCalls()) {
                    toolCallsContent.append(String.format(
                            "[Tool call: %s with arguments: %s]",
                            toolCall.name(), toolCall.argumentsJson()
                    ));
                }
                chatMessages.add(new ChatMessage(role, toolCallsContent.toString()));
            }

            // Add regular content if present
            if (content != null && !content.isEmpty() && msg.toolResults().isEmpty()) {
                chatMessages.add(new ChatMessage(role, content));
            }
        }

        return new ChatRequest(model, chatMessages, maxTokens, temperature);
    }

    /**
     * Convert UnifiedMessage.Role to Zhipu role string.
     */
    private String convertRole(UnifiedMessage.Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
        };
    }

    /**
     * Generate JWT token for Zhipu API authentication.
     * Note: This is a simplified implementation. In production, use a proper JWT library.
     */
    private String generateJwtToken(String apiKey) {
        // Zhipu API key format: id.secret
        // We need to generate a JWT token signed with the secret
        // For now, we'll use the API key directly as some endpoints accept it
        // In production, implement proper JWT generation with HS256

        // Simple implementation - use API key directly
        // This works for some Zhipu endpoints
        return apiKey;
    }

    /**
     * Escape string for JSON content.
     */
    private String escapeJsonString(String input) {
        if (input == null) {
            return "\"\"";
        }
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            return "\"" + input.replace("\"", "\\\"") + "\"";
        }
    }

    // Getters for configuration
    public String getModel() {
        return model;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public String getApiKey() {
        return apiKey;
    }

    // ==========================================
    // Inner Classes for JSON Serialization
    // ==========================================

    /**
     * Chat request payload.
     */
    private static class ChatRequest {
        @JsonProperty("model")
        private final String model;

        @JsonProperty("messages")
        private final List<ChatMessage> messages;

        @JsonProperty("max_tokens")
        private final int maxTokens;

        @JsonProperty("temperature")
        private final double temperature;

        @JsonProperty("stream")
        private final boolean stream = false;

        public ChatRequest(String model, List<ChatMessage> messages, int maxTokens, double temperature) {
            this.model = model;
            this.messages = messages;
            this.maxTokens = maxTokens;
            this.temperature = temperature;
        }
    }

    /**
     * Chat message in Zhipu format.
     */
    private static class ChatMessage {
        @JsonProperty("role")
        private String role;

        @JsonProperty("content")
        private String content;

        @JsonProperty("tool_calls")
        private List<ToolCall> toolCalls;

        // Default constructor for Jackson deserialization
        public ChatMessage() {
        }

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
            this.toolCalls = null;
        }

        public void setToolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
        }
    }

    /**
     * Tool call in Zhipu response format.
     */
    private static class ToolCall {
        @JsonProperty("id")
        private String id;

        @JsonProperty("type")
        private String type = "function";

        @JsonProperty("function")
        private Function function;

        // Default constructor for Jackson deserialization
        public ToolCall() {
        }

        public ToolCall(String id, Function function) {
            this.id = id;
            this.function = function;
        }
    }

    /**
     * Function definition for tool calls.
     */
    private static class Function {
        @JsonProperty("name")
        private String name;

        @JsonProperty("arguments")
        private String arguments;

        // Default constructor for Jackson deserialization
        public Function() {
        }

        public Function(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }
    }

    /**
     * Chat response from Zhipu API.
     */
    private static class ChatResponse {
        @JsonProperty("id")
        private String id;

        @JsonProperty("request_id")
        private String requestId;

        @JsonProperty("object")
        private String object;

        @JsonProperty("created")
        private long created;

        @JsonProperty("model")
        private String model;

        @JsonProperty("choices")
        private List<ChatChoice> choices;

        @JsonProperty("usage")
        private Usage usage;

        @JsonProperty("error")
        private ErrorResponse error;
    }

    /**
     * Chat choice in response.
     */
    private static class ChatChoice {
        @JsonProperty("index")
        private int index;

        @JsonProperty("message")
        private ChatMessage message;

        @JsonProperty("finish_reason")
        private String finishReason;
    }

    /**
     * Token usage information.
     */
    private static class Usage {
        @JsonProperty("prompt_tokens")
        private int promptTokens;

        @JsonProperty("completion_tokens")
        private int completionTokens;

        @JsonProperty("total_tokens")
        private int totalTokens;
    }

    /**
     * Error response from API.
     */
    private static class ErrorResponse {
        @JsonProperty("code")
        private String code;

        @JsonProperty("message")
        private String message;

        @JsonProperty("type")
        private String type;
    }
}
