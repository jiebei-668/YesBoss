package tech.yesboss.llm.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.llm.LlmClient;

import java.util.List;

/**
 * Claude LLM Client implementation
 *
 * <p>This is a concrete implementation of LlmClient that provides mock/stub responses
 * for integration testing. The real Claude SDK integration is implemented in
 * ClaudeSdkAdapterImpl.</p>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Send chat requests to Claude API (stubbed for now)</li>
 *   <li>Summarize text content (stubbed for now)</li>
 *   <li>Handle API errors and retries</li>
 * </ul>
 */
public class ClaudeLlmClient implements LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(ClaudeLlmClient.class);

    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final double temperature;

    /**
     * Create a new ClaudeLlmClient.
     *
     * @param apiKey    Anthropic API key
     * @param model     Model name (e.g., "claude-3-5-sonnet-20241022")
     * @param maxTokens Maximum tokens in response
     * @param temperature Temperature for response generation
     */
    public ClaudeLlmClient(String apiKey, String model, int maxTokens, double temperature) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("Model cannot be null or empty");
        }

        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;

        logger.info("ClaudeLlmClient initialized with model: {}", model);
    }

    /**
     * Create a new ClaudeLlmClient with default parameters.
     *
     * @param apiKey Anthropic API key
     */
    public ClaudeLlmClient(String apiKey) {
        this(apiKey, "claude-3-5-sonnet-20241022", 4096, 0.7);
    }

    @Override
    public UnifiedMessage chat(List<UnifiedMessage> messages, String systemPrompt) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Messages cannot be null or empty");
        }

        logger.debug("Sending chat request to Claude: {} messages, system prompt: {}",
                messages.size(), systemPrompt != null ? "yes" : "no");

        try {
            // STUB: Return a mock response for integration testing
            // TODO: Implement real Claude API call using ClaudeSdkAdapterImpl
            String lastMessage = messages.get(messages.size() - 1).content();

            // Simple stub response
            String responseContent = "I received your message: \"" +
                    (lastMessage.length() > 100 ? lastMessage.substring(0, 100) + "..." : lastMessage) +
                    "\". This is a stub response - the real Claude API integration will be implemented.";

            logger.info("Claude chat request completed (stubbed response)");
            return UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, responseContent);

        } catch (Exception e) {
            logger.error("Unexpected error during Claude chat", e);
            throw new RuntimeException("Unexpected error during Claude chat: " + e.getMessage(), e);
        }
    }

    @Override
    public String summarize(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Content cannot be null or empty");
        }

        logger.debug("Sending summarization request to Claude, content length: {}", content.length());

        try {
            // STUB: Simple summarization for integration testing
            String summary = "Summary: " +
                    (content.length() > 200 ? content.substring(0, 200) + "..." : content);

            logger.info("Summarization completed successfully (stubbed response)");
            return summary;

        } catch (Exception e) {
            logger.error("Error during summarization", e);
            throw new RuntimeException("Error during summarization: " + e.getMessage(), e);
        }
    }

    /**
     * Get the model name.
     *
     * @return The model name
     */
    public String getModel() {
        return model;
    }

    /**
     * Get max tokens setting.
     *
     * @return Max tokens
     */
    public int getMaxTokens() {
        return maxTokens;
    }

    /**
     * Get temperature setting.
     *
     * @return Temperature
     */
    public double getTemperature() {
        return temperature;
    }
}
