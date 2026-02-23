package tech.yesboss.llm.impl;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.yesboss.domain.message.UnifiedMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClaudeSdkAdapterImpl bidirectional conversion and JSON extraction.
 *
 * <p>Verifies conversion between UnifiedMessage and Claude SDK types,
 * as well as JSON serialization/deserialization for persistence.</p>
 */
@DisplayName("ClaudeSdkAdapterImpl Tests")
class ClaudeSdkAdapterImplTest {

    private static final String SUPPORTED_FORMAT = "ANTHROPIC_V3";

    @Test
    @DisplayName("toSdkRequest should convert text-only UnifiedMessage to MessageParam with block params")
    void testToSdkRequestTextOnly() {
        // Arrange
        ClaudeSdkAdapterImpl adapter = new ClaudeSdkAdapterImpl();
        UnifiedMessage message = UnifiedMessage.user("Hello, Claude!");

        // Act
        MessageParam result = adapter.toSdkRequest(message);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals(MessageParam.Role.of("user"), result.role(), "Role should be USER");
        // Adapter always uses block params for messages with content
        assertTrue(result.content().isBlockParams(), "Content should be block params");
        List<ContentBlockParam> blocks = result.content().asBlockParams();
        assertEquals(1, blocks.size(), "Should have 1 content block");
        assertTrue(blocks.get(0).isText(), "Block should be text");
        assertEquals("Hello, Claude!", blocks.get(0).asText().text(), "Text content should match");
    }

    @Test
    @DisplayName("toSdkRequest should convert UnifiedMessage with tool calls to MessageParam")
    void testToSdkRequestWithToolCalls() {
        // Arrange
        ClaudeSdkAdapterImpl adapter = new ClaudeSdkAdapterImpl();
        List<UnifiedMessage.ToolCall> calls = List.of(
                new UnifiedMessage.ToolCall("call_123", "search_web", "{\"query\": \"test\"}")
        );
        UnifiedMessage message = UnifiedMessage.ofToolCalls(calls);

        // Act
        MessageParam result = adapter.toSdkRequest(message);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals(MessageParam.Role.of("assistant"), result.role(), "Role should be ASSISTANT");

        // The content should be ContentBlock list
        assertTrue(result.content().isBlockParams(), "Content should be block params");
        List<ContentBlockParam> blocks = result.content().asBlockParams();
        assertEquals(1, blocks.size(), "Should have 1 content block");
        assertTrue(blocks.get(0).isToolUse(), "Block should be tool use");

        ToolUseBlockParam toolUse = blocks.get(0).asToolUse();
        assertEquals("call_123", toolUse.id(), "Tool call ID should match");
        assertEquals("search_web", toolUse.name(), "Tool name should match");
    }

    @Test
    @DisplayName("toSdkRequest should convert UnifiedMessage with tool results to MessageParam")
    void testToSdkRequestWithToolResults() {
        // Arrange
        ClaudeSdkAdapterImpl adapter = new ClaudeSdkAdapterImpl();
        // ofToolResult creates a message with empty content and only tool results
        UnifiedMessage message = UnifiedMessage.ofToolResult("call_456", "Success!", false);

        // Act
        MessageParam result = adapter.toSdkRequest(message);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals(MessageParam.Role.of("user"), result.role(), "Tool results use USER role");

        assertTrue(result.content().isBlockParams(), "Content should be block params");
        List<ContentBlockParam> blocks = result.content().asBlockParams();
        // ofToolResult creates both text content (result string) and tool result block
        assertEquals(2, blocks.size(), "Should have 2 content blocks (text + tool result)");
        assertTrue(blocks.get(0).isText(), "First block should be text");
        assertTrue(blocks.get(1).isToolResult(), "Second block should be tool result");
        assertEquals("Success!", blocks.get(0).asText().text(), "Text should match result");
        assertEquals("call_456", blocks.get(1).asToolResult().toolUseId(), "Tool call ID should match");
    }

    @Test
    @DisplayName("deserializeToRequest should rebuild MessageParam from JSON")
    void testDeserializeToRequest() {
        // Arrange
        ClaudeSdkAdapterImpl adapter = new ClaudeSdkAdapterImpl();

        // Create JSON representation of a text block
        String jsonContent = "[{\"type\":\"text\",\"text\":\"Deserialized message\"}]";

        // Act
        MessageParam result = adapter.deserializeToRequest(jsonContent, "user");

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals(MessageParam.Role.of("user"), result.role(), "Role should be USER");
        assertTrue(result.content().isBlockParams(), "Content should be block params");
        List<ContentBlockParam> blocks = result.content().asBlockParams();
        assertEquals("Deserialized message", blocks.get(0).asText().text(), "Text should match");
    }

    @Test
    @DisplayName("deserializeToRequest should rebuild MessageParam with tool use from JSON")
    void testDeserializeToRequestWithToolUse() {
        // Arrange
        ClaudeSdkAdapterImpl adapter = new ClaudeSdkAdapterImpl();

        // JSON representation of a tool use block
        String jsonContent = "[{\"type\":\"tool_use\",\"id\":\"call_999\",\"name\":\"test_tool\",\"input\":{\"key\":\"value\"}}]";

        // Act
        MessageParam result = adapter.deserializeToRequest(jsonContent, "assistant");

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals(MessageParam.Role.of("assistant"), result.role(), "Role should be ASSISTANT");

        assertTrue(result.content().isBlockParams(), "Content should be block params");
        List<ContentBlockParam> blocks = result.content().asBlockParams();
        assertEquals(1, blocks.size(), "Should have 1 content block");
        assertTrue(blocks.get(0).isToolUse(), "Block should be tool use");

        ToolUseBlockParam toolUse = blocks.get(0).asToolUse();
        assertEquals("call_999", toolUse.id(), "Tool call ID should match");
        assertEquals("test_tool", toolUse.name(), "Tool name should match");
    }

    @Test
    @DisplayName("deserializeToRequest should rebuild MessageParam with tool result from JSON")
    void testDeserializeToRequestWithToolResult() {
        // Arrange
        ClaudeSdkAdapterImpl adapter = new ClaudeSdkAdapterImpl();

        // JSON representation of a tool result block
        String jsonContent = "[{\"type\":\"tool_result\",\"tool_use_id\":\"call_888\",\"content\":\"Tool output\",\"is_error\":false}]";

        // Act
        MessageParam result = adapter.deserializeToRequest(jsonContent, "user");

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals(MessageParam.Role.of("user"), result.role(), "Role should be USER");

        assertTrue(result.content().isBlockParams(), "Content should be block params");
        List<ContentBlockParam> blocks = result.content().asBlockParams();
        assertEquals(1, blocks.size(), "Should have 1 content block");
        assertTrue(blocks.get(0).isToolResult(), "Block should be tool result");
    }

    @Test
    @DisplayName("getSupportedFormat should return ANTHROPIC_V3")
    void testGetSupportedFormat() {
        // Arrange
        ClaudeSdkAdapterImpl adapter = new ClaudeSdkAdapterImpl();

        // Act
        String format = adapter.getSupportedFormat();

        // Assert
        assertEquals(SUPPORTED_FORMAT, format, "Supported format should be ANTHROPIC_V3");
    }

    @Test
    @DisplayName("deserializeToRequest with invalid JSON should throw RuntimeException")
    void testDeserializeToRequestWithInvalidJson() {
        // Arrange
        ClaudeSdkAdapterImpl adapter = new ClaudeSdkAdapterImpl();
        String invalidJson = "{invalid json content}";

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            adapter.deserializeToRequest(invalidJson, "user");
        }, "Should throw RuntimeException for invalid JSON");
    }

    @Test
    @DisplayName("toSdkRequest should handle mixed content with text and tool calls")
    void testToSdkRequestMixedContent() {
        // Arrange
        ClaudeSdkAdapterImpl adapter = new ClaudeSdkAdapterImpl();

        // Create a message with both text and tool calls
        UnifiedMessage message = new UnifiedMessage(
                UnifiedMessage.Role.ASSISTANT,
                "Thinking about using a tool...",
                UnifiedMessage.PayloadFormat.ANTHROPIC_BLOCKS,
                List.of(new UnifiedMessage.ToolCall("call_mix", "test_tool", "{}")),
                List.of(),
                SUPPORTED_FORMAT,
                null
        );

        // Act
        MessageParam result = adapter.toSdkRequest(message);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals(MessageParam.Role.of("assistant"), result.role(), "Role should be ASSISTANT");
        assertTrue(result.content().isBlockParams(), "Content should be block params");

        List<ContentBlockParam> blocks = result.content().asBlockParams();
        // Should have both text and tool use blocks (2 blocks)
        assertEquals(2, blocks.size(), "Should have 2 blocks (text + tool use)");
        assertTrue(blocks.get(0).isText(), "First block should be text");
        assertTrue(blocks.get(1).isToolUse(), "Second block should be tool use");
    }

    @Test
    @DisplayName("Round-trip: UnifiedMessage -> MessageParam -> JSON -> MessageParam should preserve structure")
    void testRoundTripThroughJson() {
        // Arrange
        ClaudeSdkAdapterImpl adapter = new ClaudeSdkAdapterImpl();
        UnifiedMessage original = UnifiedMessage.user("Round-trip test");

        // Act - Convert to SDK
        MessageParam sdkRequest = adapter.toSdkRequest(original);

        // Serialize to JSON manually (simulating what would be stored)
        assertTrue(sdkRequest.content().isBlockParams(), "Content should be block params");
        String jsonContent = "[{\"type\":\"text\",\"text\":\"Round-trip test\"}]";

        // Deserialize back
        MessageParam restored = adapter.deserializeToRequest(jsonContent, "user");

        // Assert
        assertNotNull(restored, "Restored message should not be null");
        assertTrue(restored.content().isBlockParams(), "Restored content should be block params");
        assertEquals("Round-trip test", restored.content().asBlockParams().get(0).asText().text(),
                "Text should be preserved through round-trip");
    }

    @Test
    @DisplayName("deserializeToRequest should handle complex tool arguments JSON")
    void testDeserializeToRequestWithComplexToolArgs() {
        // Arrange
        ClaudeSdkAdapterImpl adapter = new ClaudeSdkAdapterImpl();

        // JSON with nested object arguments
        String jsonContent = "[{\"type\":\"tool_use\",\"id\":\"call_complex\",\"name\":\"complex_tool\",\"input\":{\"nested\":{\"key\":\"value\"},\"array\":[1,2,3]}}]";

        // Act
        MessageParam result = adapter.deserializeToRequest(jsonContent, "assistant");

        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(result.content().isBlockParams(), "Content should be block params");
        List<ContentBlockParam> blocks = result.content().asBlockParams();
        assertEquals(1, blocks.size(), "Should have 1 content block");
        assertTrue(blocks.get(0).isToolUse(), "Block should be tool use");

        ToolUseBlockParam toolUse = blocks.get(0).asToolUse();
        assertEquals("call_complex", toolUse.id(), "Tool call ID should match");
        assertEquals("complex_tool", toolUse.name(), "Tool name should match");
        assertNotNull(toolUse.input(), "Input should not be null");
    }

    @Test
    @DisplayName("toSdkRequest should handle empty content message")
    void testToSdkRequestEmptyContent() {
        // Arrange
        ClaudeSdkAdapterImpl adapter = new ClaudeSdkAdapterImpl();
        UnifiedMessage message = new UnifiedMessage(
                UnifiedMessage.Role.ASSISTANT,
                "",
                UnifiedMessage.PayloadFormat.PLAIN_TEXT,
                List.of(),
                List.of(),
                SUPPORTED_FORMAT,
                null
        );

        // Act
        MessageParam result = adapter.toSdkRequest(message);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals(MessageParam.Role.of("assistant"), result.role(), "Role should be ASSISTANT");
        // Empty content with no tool calls should result in string content
        assertTrue(result.content().isString(), "Empty content should be string type");
        assertEquals("", result.content().asString(), "Content should be empty string");
    }
}
