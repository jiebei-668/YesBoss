package tech.yesboss.tool.tracker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.yesboss.persistence.db.SingleThreadDbWriter;
import tech.yesboss.persistence.event.InsertToolExecutionLogEvent;
import tech.yesboss.tool.tracker.impl.ToolCallTrackerImpl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.argThat;
import org.mockito.ArgumentCaptor;

/**
 * Tests for ToolCallTracker event delegation.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>trackExecution() creates and submits InsertToolExecutionLogEvent</li>
 *   <li>Both successful and intercepted executions are tracked correctly</li>
 *   <li>isIntercepted flag is correctly mapped</li>
 *   <li>Validation is performed on input parameters</li>
 * </ul>
 */
@DisplayName("ToolCallTracker Tests")
class ToolCallTrackerTest {

    private SingleThreadDbWriter mockDbWriter;
    private ToolCallTracker tracker;

    @BeforeEach
    void setUp() {
        mockDbWriter = mock(SingleThreadDbWriter.class);
        tracker = new ToolCallTrackerImpl(mockDbWriter);
    }

    @Test
    @DisplayName("trackExecution should submit event for successful tool execution")
    void testTrackSuccessfulExecution() {
        // Arrange
        String sessionId = "session_123";
        String toolCallId = "call_abc";
        String toolName = "read_file";
        String argumentsJson = "{\"path\":\"/test.txt\"}";
        String result = "File content: Hello World";
        boolean isIntercepted = false;
        ArgumentCaptor<InsertToolExecutionLogEvent> eventCaptor = ArgumentCaptor.forClass(InsertToolExecutionLogEvent.class);

        // Act
        tracker.trackExecution(sessionId, toolCallId, toolName, argumentsJson, result, isIntercepted);

        // Assert
        verify(mockDbWriter, times(1)).submitEvent(eventCaptor.capture());
        InsertToolExecutionLogEvent capturedEvent = eventCaptor.getValue();

        assertEquals(sessionId, capturedEvent.sessionId());
        assertEquals(toolCallId, capturedEvent.toolCallId());
        assertEquals(toolName, capturedEvent.toolName());
        assertEquals(argumentsJson, capturedEvent.arguments());
        assertEquals(result, capturedEvent.result());
        assertFalse(capturedEvent.isIntercepted());
    }

    @Test
    @DisplayName("trackExecution should submit event with isIntercepted=true for blocked execution")
    void testTrackInterceptedExecution() {
        // Arrange
        String sessionId = "session_456";
        String toolCallId = "call_def";
        String toolName = "format_disk";
        String argumentsJson = "{\"device\":\"/dev/sda\"}";
        String result = "Operation blocked by sandbox";
        boolean isIntercepted = true;
        ArgumentCaptor<InsertToolExecutionLogEvent> eventCaptor = ArgumentCaptor.forClass(InsertToolExecutionLogEvent.class);

        // Act
        tracker.trackExecution(sessionId, toolCallId, toolName, argumentsJson, result, isIntercepted);

        // Assert
        verify(mockDbWriter, times(1)).submitEvent(eventCaptor.capture());
        InsertToolExecutionLogEvent capturedEvent = eventCaptor.getValue();

        assertEquals(sessionId, capturedEvent.sessionId());
        assertEquals(toolCallId, capturedEvent.toolCallId());
        assertEquals(toolName, capturedEvent.toolName());
        assertEquals(argumentsJson, capturedEvent.arguments());
        assertEquals(result, capturedEvent.result());
        assertTrue(capturedEvent.isIntercepted());
    }

    @Test
    @DisplayName("trackExecution should handle null arguments and result")
    void testTrackExecutionWithNullValues() {
        // Arrange
        String sessionId = "session_789";
        String toolCallId = "call_ghi";
        String toolName = "list_dir";
        String argumentsJson = null;
        String result = null;
        boolean isIntercepted = false;
        ArgumentCaptor<InsertToolExecutionLogEvent> eventCaptor = ArgumentCaptor.forClass(InsertToolExecutionLogEvent.class);

        // Act
        tracker.trackExecution(sessionId, toolCallId, toolName, argumentsJson, result, isIntercepted);

        // Assert
        verify(mockDbWriter, times(1)).submitEvent(eventCaptor.capture());
        InsertToolExecutionLogEvent capturedEvent = eventCaptor.getValue();

        assertEquals(sessionId, capturedEvent.sessionId());
        assertEquals(toolCallId, capturedEvent.toolCallId());
        assertEquals(toolName, capturedEvent.toolName());
        assertEquals("{}", capturedEvent.arguments());  // Null should become "{}"
        assertTrue(capturedEvent.result().isEmpty());  // Null should become ""
        assertFalse(capturedEvent.isIntercepted());
    }

    @Test
    @DisplayName("trackExecution should throw exception for null sessionId")
    void testTrackExecutionWithNullSessionId() {
        // Assert
        assertThrows(IllegalArgumentException.class,
                () -> tracker.trackExecution(null, "call_id", "tool_name", "{}", "result", false),
                "Should throw exception for null sessionId");
    }

    @Test
    @DisplayName("trackExecution should throw exception for empty sessionId")
    void testTrackExecutionWithEmptySessionId() {
        // Assert
        assertThrows(IllegalArgumentException.class,
                () -> tracker.trackExecution("", "call_id", "tool_name", "{}", "result", false),
                "Should throw exception for empty sessionId");
        assertThrows(IllegalArgumentException.class,
                () -> tracker.trackExecution("   ", "call_id", "tool_name", "{}", "result", false),
                "Should throw exception for whitespace-only sessionId");
    }

    @Test
    @DisplayName("trackExecution should throw exception for null toolCallId")
    void testTrackExecutionWithNullToolCallId() {
        // Assert
        assertThrows(IllegalArgumentException.class,
                () -> tracker.trackExecution("session_id", null, "tool_name", "{}", "result", false),
                "Should throw exception for null toolCallId");
    }

    @Test
    @DisplayName("trackExecution should throw exception for empty toolCallId")
    void testTrackExecutionWithEmptyToolCallId() {
        // Assert
        assertThrows(IllegalArgumentException.class,
                () -> tracker.trackExecution("session_id", "", "tool_name", "{}", "result", false),
                "Should throw exception for empty toolCallId");
    }

    @Test
    @DisplayName("trackExecution should throw exception for null toolName")
    void testTrackExecutionWithNullToolName() {
        // Assert
        assertThrows(IllegalArgumentException.class,
                () -> tracker.trackExecution("session_id", "call_id", null, "{}", "result", false),
                "Should throw exception for null toolName");
    }

    @Test
    @DisplayName("trackExecution should throw exception for empty toolName")
    void testTrackExecutionWithEmptyToolName() {
        // Assert
        assertThrows(IllegalArgumentException.class,
                () -> tracker.trackExecution("session_id", "call_id", "", "{}", "result", false),
                "Should throw exception for empty toolName");
    }

    @Test
    @DisplayName("trackExecution should handle large result strings")
    void testTrackExecutionWithLargeResult() {
        // Arrange
        String sessionId = "session_large";
        String toolCallId = "call_large";
        String toolName = "read_large_file";
        String argumentsJson = "{\"path\":\"/large/file.txt\"}";
        String largeResult = "A".repeat(10000);  // 10KB result
        boolean isIntercepted = false;
        ArgumentCaptor<InsertToolExecutionLogEvent> eventCaptor = ArgumentCaptor.forClass(InsertToolExecutionLogEvent.class);

        // Act
        tracker.trackExecution(sessionId, toolCallId, toolName, argumentsJson, largeResult, isIntercepted);

        // Assert
        verify(mockDbWriter, times(1)).submitEvent(eventCaptor.capture());
        InsertToolExecutionLogEvent capturedEvent = eventCaptor.getValue();

        assertEquals(sessionId, capturedEvent.sessionId());
        assertEquals(toolCallId, capturedEvent.toolCallId());
        assertEquals(toolName, capturedEvent.toolName());
        assertEquals(largeResult, capturedEvent.result());
        assertFalse(capturedEvent.isIntercepted());
    }

    @Test
    @DisplayName("trackExecution should handle complex JSON arguments")
    void testTrackExecutionWithComplexArguments() {
        // Arrange
        String sessionId = "session_complex";
        String toolCallId = "call_complex";
        String toolName = "execute_query";
        String complexArguments = "{\"query\":\"SELECT * FROM users WHERE id = ?\",\"params\":[1,2,3],\"options\":{\"timeout\":5000,\"retries\":3}}";
        String result = "Query returned 5 rows";
        boolean isIntercepted = false;
        ArgumentCaptor<InsertToolExecutionLogEvent> eventCaptor = ArgumentCaptor.forClass(InsertToolExecutionLogEvent.class);

        // Act
        tracker.trackExecution(sessionId, toolCallId, toolName, complexArguments, result, isIntercepted);

        // Assert
        verify(mockDbWriter, times(1)).submitEvent(eventCaptor.capture());
        InsertToolExecutionLogEvent capturedEvent = eventCaptor.getValue();

        assertEquals(sessionId, capturedEvent.sessionId());
        assertEquals(toolCallId, capturedEvent.toolCallId());
        assertEquals(toolName, capturedEvent.toolName());
        assertEquals(complexArguments, capturedEvent.arguments());
        assertEquals(result, capturedEvent.result());
        assertFalse(capturedEvent.isIntercepted());
    }

    @Test
    @DisplayName("constructor should throw exception for null dbWriter")
    void testConstructorWithNullDbWriter() {
        // Assert
        assertThrows(IllegalArgumentException.class,
                () -> new ToolCallTrackerImpl(null),
                "Should throw exception for null dbWriter");
    }

    @Test
    @DisplayName("trackExecution should track both successful and intercepted executions correctly")
    void testTrackMultipleExecutions() {
        // Arrange
        String sessionId = "session_multi";
        ArgumentCaptor<InsertToolExecutionLogEvent> eventCaptor = ArgumentCaptor.forClass(InsertToolExecutionLogEvent.class);

        // Act - Track a successful execution
        tracker.trackExecution(sessionId, "call_1", "read_file", "{}", "success", false);

        // Act - Track an intercepted execution
        tracker.trackExecution(sessionId, "call_2", "delete_file", "{}", "blocked", true);

        // Act - Track another successful execution
        tracker.trackExecution(sessionId, "call_3", "write_file", "{}", "done", false);

        // Assert
        verify(mockDbWriter, times(3)).submitEvent(eventCaptor.capture());
        List<InsertToolExecutionLogEvent> capturedEvents = eventCaptor.getAllValues();

        assertEquals(3, capturedEvents.size());
        assertEquals("call_1", capturedEvents.get(0).toolCallId());
        assertFalse(capturedEvents.get(0).isIntercepted());
        assertEquals("call_2", capturedEvents.get(1).toolCallId());
        assertTrue(capturedEvents.get(1).isIntercepted());
        assertEquals("call_3", capturedEvents.get(2).toolCallId());
        assertFalse(capturedEvents.get(2).isIntercepted());
    }
}
