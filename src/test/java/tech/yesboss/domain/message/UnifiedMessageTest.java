package tech.yesboss.domain.message;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UnifiedMessage domain entity.
 *
 * <p>Verifies immutability, static factory methods, and helper methods.</p>
 */
@DisplayName("UnifiedMessage Tests")
class UnifiedMessageTest {

    @Test
    @DisplayName("ofText should create text-only message with PLAIN_TEXT format")
    void testOfTextCreatesTextMessage() {
        // Act
        UnifiedMessage message = UnifiedMessage.ofText(UnifiedMessage.Role.USER, "Hello, world!");

        // Assert
        assertEquals(UnifiedMessage.Role.USER, message.role(), "Role should be USER");
        assertEquals("Hello, world!", message.content(), "Content should match");
        assertEquals(UnifiedMessage.PayloadFormat.PLAIN_TEXT, message.payloadFormat(), "Format should be PLAIN_TEXT");
        assertTrue(message.isTextOnly(), "Should be text-only");
        assertFalse(message.hasToolCalls(), "Should not have tool calls");
        assertFalse(message.hasToolResults(), "Should not have tool results");
    }

    @Test
    @DisplayName("ofText with SYSTEM role should create system message")
    void testOfTextCreatesSystemMessage() {
        // Act
        UnifiedMessage message = UnifiedMessage.ofText(UnifiedMessage.Role.SYSTEM, "You are a helpful assistant.");

        // Assert
        assertEquals(UnifiedMessage.Role.SYSTEM, message.role(), "Role should be SYSTEM");
        assertEquals("You are a helpful assistant.", message.content(), "Content should match");
        assertTrue(message.isTextOnly(), "Should be text-only");
    }

    @Test
    @DisplayName("ofToolResult should create message with tool result")
    void testOfToolResultCreatesToolResultMessage() {
        // Act
        UnifiedMessage message = UnifiedMessage.ofToolResult("call_123", "Success!", false);

        // Assert
        assertEquals(UnifiedMessage.Role.USER, message.role(), "Tool results use USER role");
        assertFalse(message.hasToolCalls(), "Should not have tool calls");
        assertTrue(message.hasToolResults(), "Should have tool results");
        assertFalse(message.isTextOnly(), "Should not be text-only");
        assertEquals(1, message.toolResults().size(), "Should have 1 tool result");
        assertEquals("call_123", message.toolResults().get(0).toolCallId(), "Tool call ID should match");
        assertEquals("Success!", message.toolResults().get(0).resultString(), "Result should match");
        assertFalse(message.toolResults().get(0).isError(), "Should not be error");
    }

    @Test
    @DisplayName("ofToolResult with isError should create error result")
    void testOfToolResultWithErrorFlag() {
        // Act
        UnifiedMessage message = UnifiedMessage.ofToolResult("call_456", "Tool failed", true);

        // Assert
        assertTrue(message.hasToolResults(), "Should have tool results");
        assertEquals("call_456", message.toolResults().get(0).toolCallId(), "Tool call ID should match");
        assertEquals("Tool failed", message.toolResults().get(0).resultString(), "Error message should match");
        assertTrue(message.toolResults().get(0).isError(), "Should be marked as error");
    }

    @Test
    @DisplayName("ofToolCalls should create message with tool calls")
    void testOfToolCallsCreatesMessageWithToolCalls() {
        // Arrange
        List<UnifiedMessage.ToolCall> calls = List.of(
                new UnifiedMessage.ToolCall("call_1", "search_web", "{\"query\": \"test\"}"),
                new UnifiedMessage.ToolCall("call_2", "write_file", "{\"path\": \"/tmp/test.txt\"}")
        );

        // Act
        UnifiedMessage message = UnifiedMessage.ofToolCalls(calls);

        // Assert
        assertEquals(UnifiedMessage.Role.ASSISTANT, message.role(), "Tool calls use ASSISTANT role");
        assertTrue(message.hasToolCalls(), "Should have tool calls");
        assertFalse(message.hasToolResults(), "Should not have tool results");
        assertFalse(message.isTextOnly(), "Should not be text-only");
        assertEquals(2, message.toolCalls().size(), "Should have 2 tool calls");
        assertEquals("ANTHROPIC_V3", message.rawPayloadFormat(), "Should use ANTHROPIC_V3 format");
    }

    @Test
    @DisplayName("system factory should create system message")
    void testSystemFactoryMethod() {
        // Act
        UnifiedMessage message = UnifiedMessage.system("You are a coding assistant.");

        // Assert
        assertEquals(UnifiedMessage.Role.SYSTEM, message.role(), "Role should be SYSTEM");
        assertEquals("You are a coding assistant.", message.content(), "Content should match");
        assertEquals(UnifiedMessage.PayloadFormat.PLAIN_TEXT, message.payloadFormat(), "Format should be PLAIN_TEXT");
    }

    @Test
    @DisplayName("user factory should create user message")
    void testUserFactoryMethod() {
        // Act
        UnifiedMessage message = UnifiedMessage.user("Help me write code.");

        // Assert
        assertEquals(UnifiedMessage.Role.USER, message.role(), "Role should be USER");
        assertEquals("Help me write code.", message.content(), "Content should match");
        assertEquals(UnifiedMessage.PayloadFormat.PLAIN_TEXT, message.payloadFormat(), "Format should be PLAIN_TEXT");
    }

    @Test
    @DisplayName("hasToolCalls should return true only when tool calls are present")
    void testHasToolCalls() {
        // Arrange
        List<UnifiedMessage.ToolCall> calls = List.of(
                new UnifiedMessage.ToolCall("call_1", "test_tool", "{}")
        );
        UnifiedMessage withCalls = UnifiedMessage.ofToolCalls(calls);
        UnifiedMessage withoutCalls = UnifiedMessage.user("Hello");

        // Assert
        assertTrue(withCalls.hasToolCalls(), "Should have tool calls");
        assertFalse(withoutCalls.hasToolCalls(), "Should not have tool calls");
    }

    @Test
    @DisplayName("hasToolResults should return true only when tool results are present")
    void testHasToolResults() {
        // Arrange
        UnifiedMessage withResults = UnifiedMessage.ofToolResult("call_1", "result", false);
        UnifiedMessage withoutResults = UnifiedMessage.user("Hello");

        // Assert
        assertTrue(withResults.hasToolResults(), "Should have tool results");
        assertFalse(withoutResults.hasToolResults(), "Should not have tool results");
    }

    @Test
    @DisplayName("isTextOnly should return true for text messages")
    void testIsTextOnly() {
        // Arrange
        UnifiedMessage textMessage = UnifiedMessage.user("Hello");
        UnifiedMessage toolCallMessage = UnifiedMessage.ofToolCalls(List.of(
                new UnifiedMessage.ToolCall("call_1", "tool", "{}")
        ));
        UnifiedMessage toolResultMessage = UnifiedMessage.ofToolResult("call_1", "result", false);

        // Assert
        assertTrue(textMessage.isTextOnly(), "Text message should be text-only");
        assertFalse(toolCallMessage.isTextOnly(), "Tool call message should not be text-only");
        assertFalse(toolResultMessage.isTextOnly(), "Tool result message should not be text-only");
    }

    @Test
    @DisplayName("Null lists should be converted to empty lists")
    void testNullListsBecomeEmptyLists() {
        // Act - Using the canonical constructor with null lists
        UnifiedMessage message = new UnifiedMessage(
                UnifiedMessage.Role.ASSISTANT,
                "Response",
                UnifiedMessage.PayloadFormat.PLAIN_TEXT,
                null,
                null,
                "PLAIN_TEXT",
                "Response"
        );

        // Assert
        assertNotNull(message.toolCalls(), "Tool calls should not be null");
        assertNotNull(message.toolResults(), "Tool results should not be null");
        assertTrue(message.toolCalls().isEmpty(), "Tool calls should be empty");
        assertTrue(message.toolResults().isEmpty(), "Tool results should be empty");
    }

    @Test
    @DisplayName("Lists should be immutable")
    void testListsAreImmutable() {
        // Arrange
        List<UnifiedMessage.ToolCall> originalCalls = List.of(
                new UnifiedMessage.ToolCall("call_1", "tool", "{}")
        );
        UnifiedMessage message = new UnifiedMessage(
                UnifiedMessage.Role.ASSISTANT,
                "",
                UnifiedMessage.PayloadFormat.ANTHROPIC_BLOCKS,
                originalCalls,
                List.of(),
                "ANTHROPIC_V3",
                null
        );

        // Assert - Lists should be unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> {
            message.toolCalls().add(new UnifiedMessage.ToolCall("call_2", "tool", "{}"));
        }, "Tool calls list should be immutable");

        assertThrows(UnsupportedOperationException.class, () -> {
            message.toolResults().add(new UnifiedMessage.ToolResult("call_1", "result", false));
        }, "Tool results list should be immutable");
    }

    @Test
    @DisplayName("getDisplayContent should return summary for tool calls")
    void testGetDisplayContentForToolCalls() {
        // Arrange
        List<UnifiedMessage.ToolCall> calls = List.of(
                new UnifiedMessage.ToolCall("call_1", "tool_a", "{}"),
                new UnifiedMessage.ToolCall("call_2", "tool_b", "{}")
        );
        UnifiedMessage message = UnifiedMessage.ofToolCalls(calls);

        // Act
        String display = message.getDisplayContent();

        // Assert
        assertEquals("[Calling 2 tools]", display, "Should show tool call summary");
    }

    @Test
    @DisplayName("getDisplayContent should return summary for tool results")
    void testGetDisplayContentForToolResults() {
        // Arrange
        UnifiedMessage message = UnifiedMessage.ofToolResult("call_1", "result", false);

        // Act
        String display = message.getDisplayContent();

        // Assert
        assertEquals("[1 tool result]", display, "Should show tool result summary");
    }

    @Test
    @DisplayName("getDisplayContent should return content for text messages")
    void testGetDisplayContentForTextMessages() {
        // Arrange
        UnifiedMessage message = UnifiedMessage.user("Hello, world!");

        // Act
        String display = message.getDisplayContent();

        // Assert
        assertEquals("Hello, world!", display, "Should return text content");
    }

    @Test
    @DisplayName("ToolCall record should store all fields")
    void testToolCallRecord() {
        // Arrange
        UnifiedMessage.ToolCall call = new UnifiedMessage.ToolCall("call_123", "search", "{\"q\": \"test\"}");

        // Assert
        assertEquals("call_123", call.id(), "ID should match");
        assertEquals("search", call.name(), "Name should match");
        assertEquals("{\"q\": \"test\"}", call.argumentsJson(), "Arguments JSON should match");
    }

    @Test
    @DisplayName("ToolResult record should store all fields")
    void testToolResultRecord() {
        // Arrange
        UnifiedMessage.ToolResult result = new UnifiedMessage.ToolResult("call_456", "Output here", false);

        // Assert
        assertEquals("call_456", result.toolCallId(), "Tool call ID should match");
        assertEquals("Output here", result.resultString(), "Result string should match");
        assertFalse(result.isError(), "Should not be error");
    }

    @Test
    @DisplayName("Simplified constructor should maintain backward compatibility")
    void testSimplifiedConstructorBackwardCompatibility() {
        // Act - Using the old 3-parameter constructor
        UnifiedMessage message = new UnifiedMessage(
                UnifiedMessage.Role.USER,
                "Test content",
                UnifiedMessage.PayloadFormat.PLAIN_TEXT
        );

        // Assert
        assertEquals(UnifiedMessage.Role.USER, message.role(), "Role should match");
        assertEquals("Test content", message.content(), "Content should match");
        assertEquals(UnifiedMessage.PayloadFormat.PLAIN_TEXT, message.payloadFormat(), "Format should match");
        assertTrue(message.toolCalls().isEmpty(), "Tool calls should be empty");
        assertTrue(message.toolResults().isEmpty(), "Tool results should be empty");
    }
}
