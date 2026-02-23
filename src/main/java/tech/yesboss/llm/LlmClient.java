package tech.yesboss.llm;

import tech.yesboss.domain.message.UnifiedMessage;

import java.util.List;

/**
 * Client interface for LLM provider abstraction.
 *
 * <p>This interface defines the contract for interacting with LLM providers
 * (Claude, GPT, etc.). It supports both chat interactions and summarization.</p>
 *
 * <p>Key methods:
 * <ul>
 *   <li>{@link #chat(List, String)} - Send a conversation and get a response</li>
 *   <li>{@link #summarize(String)} - Summarize text content</li>
 * </ul>
 */
public interface LlmClient {

    /**
     * Send a conversation to the LLM and get a response.
     *
     * @param messages The conversation history as a list of UnifiedMessages
     * @param systemPrompt Optional system prompt to guide the LLM's behavior
     * @return The LLM's response as a UnifiedMessage
     */
    UnifiedMessage chat(List<UnifiedMessage> messages, String systemPrompt);

    /**
     * Summarize the given text content.
     *
     * @param content The text content to summarize
     * @return A concise summary of the content
     */
    String summarize(String content);
}
