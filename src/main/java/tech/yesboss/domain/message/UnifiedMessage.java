package tech.yesboss.domain.message;

/**
 * Unified message format for all LLM interactions.
 *
 * <p>This is a placeholder implementation. The complete implementation
 * will be created in the LLM路由网关模块 task.</p>
 *
 * <p>For now, this class serves as a compile-time dependency for the
 * persistence layer events.</p>
 */
public record UnifiedMessage(
        Role role,
        String content,
        PayloadFormat payloadFormat
) {
    /**
     * Message role enum following OpenAI/Claude standards.
     */
    public enum Role {
        SYSTEM, USER, ASSISTANT, TOOL
    }

    /**
     * Payload format enum for content serialization.
     */
    public enum PayloadFormat {
        PLAIN_TEXT, ANTHROPIC_BLOCKS, OPENAI_CHATS
    }
}
