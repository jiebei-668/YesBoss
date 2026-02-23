package tech.yesboss.llm.impl;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.llm.VendorSdkAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Claude SDK adapter implementation for Anthropic Claude API.
 *
 * <p>This adapter handles bidirectional conversion between the system's
 * {@link UnifiedMessage} format and Claude's {@link MessageParam}/{@link Message} types.
 * It uses Java 21 pattern matching for ContentBlock processing.</p>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Convert UnifiedMessage to Claude MessageParam for API requests</li>
 *   <li>Convert Claude Message responses to UnifiedMessage</li>
 *   <li>Extract and deserialize raw JSON for persistence and resume</li>
 * </ul>
 */
public class ClaudeSdkAdapterImpl implements VendorSdkAdapter<MessageParam, Message> {

    private static final Logger logger = LoggerFactory.getLogger(ClaudeSdkAdapterImpl.class);
    private static final String SUPPORTED_FORMAT = "ANTHROPIC_V3";

    private final ObjectMapper objectMapper;

    /**
     * Create a new ClaudeSdkAdapterImpl.
     */
    public ClaudeSdkAdapterImpl() {
        this.objectMapper = new ObjectMapper();
        // Configure ObjectMapper for Claude SDK compatibility
        this.objectMapper.findAndRegisterModules();
    }

    /**
     * Create a new ClaudeSdkAdapterImpl with a custom ObjectMapper.
     *
     * @param objectMapper The configured ObjectMapper
     */
    public ClaudeSdkAdapterImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getSupportedFormat() {
        return SUPPORTED_FORMAT;
    }

    @Override
    public MessageParam toSdkRequest(UnifiedMessage unifiedMessage) {
        logger.debug("Converting UnifiedMessage to Claude MessageParam, role={}", unifiedMessage.role());

        List<ContentBlockParam> blocks = new ArrayList<>();

        // 1. Handle text content
        if (unifiedMessage.content() != null && !unifiedMessage.content().isEmpty()) {
            blocks.add(ContentBlockParam.ofText(
                    TextBlockParam.builder().text(unifiedMessage.content()).build()
            ));
        }

        // 2. Handle tool calls (Assistant wants to use tools)
        if (unifiedMessage.hasToolCalls()) {
            for (UnifiedMessage.ToolCall call : unifiedMessage.toolCalls()) {
                ToolUseBlockParam.Input input = parseJsonToInput(call.argumentsJson());

                blocks.add(ContentBlockParam.ofToolUse(
                        ToolUseBlockParam.builder()
                                .id(call.id())
                                .name(call.name())
                                .input(input)
                                .build()
                ));
            }
        }

        // 3. Handle tool results (User returns tool execution results)
        if (unifiedMessage.hasToolResults()) {
            for (UnifiedMessage.ToolResult result : unifiedMessage.toolResults()) {
                blocks.add(ContentBlockParam.ofToolResult(
                        ToolResultBlockParam.builder()
                                .toolUseId(result.toolCallId())
                                .content(result.resultString())
                                .isError(result.isError())
                                .build()
                ));
            }
        }

        // Build the MessageParam
        MessageParam.Builder builder = MessageParam.builder()
                .role(MessageParam.Role.of(unifiedMessage.role().name().toLowerCase()));

        if (!blocks.isEmpty()) {
            builder.contentOfBlockParams(blocks);
        } else if (unifiedMessage.content() != null) {
            // Fallback to simple text content if no blocks
            builder.content(unifiedMessage.content());
        }

        return builder.build();
    }

    @Override
    public UnifiedMessage toUnifiedMessage(Message claudeResponse) {
        logger.debug("Converting Claude Message to UnifiedMessage");

        StringBuilder textBuilder = new StringBuilder();
        List<UnifiedMessage.ToolCall> toolCalls = new ArrayList<>();
        List<UnifiedMessage.ToolResult> toolResults = new ArrayList<>();

        // Process content blocks using the new API with is*()/as*() pattern
        for (ContentBlock block : claudeResponse.content()) {
            if (block.isText()) {
                TextBlock textBlock = block.asText();
                textBuilder.append(textBlock.text());
            } else if (block.isToolUse()) {
                ToolUseBlock toolUse = block.asToolUse();
                // Get the input as JsonValue and convert to String
                String inputJson = toolUse._input().toString();
                toolCalls.add(new UnifiedMessage.ToolCall(
                        toolUse.id(),
                        toolUse.name(),
                        inputJson
                ));
            }
            // ToolResultBlock typically appears in user messages, not assistant responses
            // Ignoring ThinkingBlock and other internal blocks
        }

        // Extract raw JSON for persistence
        String rawJson = extractRawContentJson(claudeResponse);

        return new UnifiedMessage(
                UnifiedMessage.Role.ASSISTANT, // Claude responses are always from assistant
                textBuilder.toString(),
                UnifiedMessage.PayloadFormat.ANTHROPIC_BLOCKS,
                toolCalls,
                toolResults,
                SUPPORTED_FORMAT,
                rawJson
        );
    }

    @Override
    public String extractRawContentJson(Message claudeResponse) {
        try {
            return objectMapper.writeValueAsString(claudeResponse.content());
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize Claude ContentBlocks to JSON", e);
            throw new RuntimeException("Failed to serialize Claude ContentBlocks to JSON", e);
        }
    }

    @Override
    public MessageParam deserializeToRequest(String jsonContent, String msgRole) {
        logger.debug("Deserializing JSON to Claude MessageParam, role={}", msgRole);

        try {
            // Deserialize JSON to List<ContentBlockParam>
            List<ContentBlockParam> blocks = objectMapper.readValue(
                    jsonContent,
                    new TypeReference<List<ContentBlockParam>>() {}
            );

            return MessageParam.builder()
                    .role(MessageParam.Role.of(msgRole.toLowerCase()))
                    .contentOfBlockParams(blocks)
                    .build();
        } catch (Exception e) {
            logger.error("Failed to deserialize JSON to Claude MessageParam. JSON: {}", jsonContent, e);
            throw new RuntimeException("Resume failed: could not deserialize Claude context. JSON: " + jsonContent, e);
        }
    }

    // ==========================================
    // Private Helper Methods
    // ==========================================

    /**
     * Parse JSON string to ToolUseBlockParam.Input.
     *
     * @param argumentsJson The JSON string to parse
     * @return The Input object
     */
    private ToolUseBlockParam.Input parseJsonToInput(String argumentsJson) {
        try {
            Map<String, Object> properties = objectMapper.readValue(
                    argumentsJson,
                    new TypeReference<Map<String, Object>>() {}
            );

            ToolUseBlockParam.Input.Builder builder = ToolUseBlockParam.Input.builder();
            properties.forEach((key, value) -> builder.putAdditionalProperty(key, JsonValue.from(value)));

            return builder.build();
        } catch (Exception e) {
            logger.error("Failed to parse tool arguments JSON: {}", argumentsJson, e);
            throw new RuntimeException("Failed to parse tool arguments JSON: " + argumentsJson, e);
        }
    }
}
