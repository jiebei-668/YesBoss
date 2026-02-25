package tech.yesboss.llm.impl;

import com.anthropic.Anonymous;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.core.AnthropicClientException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.llm.LlmClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Claude LLM Client implementation
 *
 * <p>This is a concrete implementation of LlmClient that uses the Anthropic Claude SDK.</p>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Send chat requests to Claude API</li>
 *   <li>Summarize text content</li>
 *   <li>Handle API errors and retries</li>
 * </ul>
 */
public class ClaudeLlmClient implements LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(ClaudeLlmClient.class);

    private final AnthropicClient anthropicClient;
    private final ClaudeSdkAdapterImpl sdkAdapter;
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

        this.anthropicClient = AnthropicClient.builder()
                .apiKey(Anonymous.of(apiKey))
                .build();
        this.sdkAdapter = new ClaudeSdkAdapterImpl();
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
        this(apiKey, Model.CLAUDE_3_5_SONNET_20241022.toString(), 4096, 0.7);
    }

    @Override
    public UnifiedMessage chat(List<UnifiedMessage> messages, String systemPrompt) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Messages cannot be null or empty");
        }

        logger.debug("Sending chat request to Claude: {} messages, system prompt: {}",
                messages.size(), systemPrompt != null ? "yes" : "no");

        try {
            // Convert UnifiedMessages to MessageParams
            List<MessageParam> messageParams = new ArrayList<>();
            for (UnifiedMessage message : messages) {
                messageParams.add(sdkAdapter.toSdkRequest(message));
            }

            // Build request parameters
            MessageCreateParams.Builder builder = MessageCreateParams.builder()
                    .model(Model.of(this.model))
                    .maxTokens(maxTokens)
                    .temperature(temperature)
                    .messages(messageParams);

            // Add system prompt if provided
            if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                builder.system(systemPrompt);
            }

            // Execute request
            com.anthropic.models.messages.Message response = anthropicClient.messages()
                    .create(builder.build());

            // Convert response back to UnifiedMessage
            UnifiedMessage unifiedResponse = sdkAdapter.toUnifiedMessage(response);

            logger.info("Claude chat request completed successfully");
            return unifiedResponse;

        } catch (AnthropicClientException e) {
            logger.error("Claude API error: {}", e.getMessage(), e);
            throw new RuntimeException("Claude API error: " + e.getMessage(), e);
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
            // Create a simple chat request for summarization
            String systemPrompt = "You are a helpful assistant. Please provide a concise summary of the following content.";
            UnifiedMessage userMessage = UnifiedMessage.user(
                    "Please summarize the following content:\n\n" + content);

            UnifiedMessage response = chat(List.of(userMessage), systemPrompt);

            logger.info("Summarization completed successfully");
            return response.content();

        } catch (Exception e) {
            logger.error("Error during summarization", e);
            throw new RuntimeException("Error during summarization: " + e.getMessage(), e);
        }
    }

    /**
     * Get the Anthropic client instance.
     *
     * @return The AnthropicClient
     */
    public AnthropicClient getAnthropicClient() {
        return anthropicClient;
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
