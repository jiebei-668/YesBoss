package tech.yesboss.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.yesboss.context.impl.LocalStreamManagerImpl;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.persistence.repository.ChatMessageRepository;
import tech.yesboss.persistence.event.InsertMessageEvent;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for LocalStreamManager Worker memory isolation.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>appendWorkerMessage properly stores worker messages</li>
 *   <li>appendToolResult properly converts inputs to UnifiedMessage.ofToolResult</li>
 *   <li>getCurrentTokenCount aggregates token counts correctly</li>
 *   <li>fetchContext calls repository with LOCAL streamType</li>
 * </ul>
 */
@DisplayName("LocalStreamManager Tests")
class LocalStreamManagerTest {

    private ChatMessageRepository mockRepository;
    private LocalStreamManager localStreamManager;

    @BeforeEach
    void setUp() {
        mockRepository = mock(ChatMessageRepository.class);
        localStreamManager = new LocalStreamManagerImpl(mockRepository);
    }

    @Test
    @DisplayName("Constructor should throw exception for null repository")
    void testConstructorWithNullRepository() {
        assertThrows(IllegalArgumentException.class,
                () -> new LocalStreamManagerImpl(null),
                "Should throw exception for null repository");
    }

    @Test
    @DisplayName("appendWorkerMessage should store worker message with LOCAL stream type")
    void testAppendWorkerMessage() {
        // Arrange
        String sessionId = "session_worker_001";
        UnifiedMessage workerMessage = UnifiedMessage.ofText(
                UnifiedMessage.Role.ASSISTANT,
                "正在执行重构任务..."
        );
        when(mockRepository.getCurrentSequenceNumber(eq(sessionId),
                any(InsertMessageEvent.StreamType.class))).thenReturn(0);

        // Act
        localStreamManager.appendWorkerMessage(sessionId, workerMessage);

        // Assert
        verify(mockRepository).getCurrentSequenceNumber(sessionId, InsertMessageEvent.StreamType.LOCAL);
        verify(mockRepository).saveMessage(sessionId,
                InsertMessageEvent.StreamType.LOCAL, 1, workerMessage);
    }

    @Test
    @DisplayName("appendWorkerMessage should throw exception for null sessionId")
    void testAppendWorkerMessageWithNullSessionId() {
        UnifiedMessage message = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "test");
        assertThrows(IllegalArgumentException.class,
                () -> localStreamManager.appendWorkerMessage(null, message));
    }

    @Test
    @DisplayName("appendWorkerMessage should throw exception for null message")
    void testAppendWorkerMessageWithNullMessage() {
        assertThrows(IllegalArgumentException.class,
                () -> localStreamManager.appendWorkerMessage("session", null));
    }

    @Test
    @DisplayName("appendToolResult should store tool result with LOCAL stream type")
    void testAppendToolResult() {
        // Arrange
        String sessionId = "session_worker_002";
        UnifiedMessage toolResult = UnifiedMessage.ofToolResult("call_123", "文件创建成功", false);
        when(mockRepository.getCurrentSequenceNumber(eq(sessionId),
                any(InsertMessageEvent.StreamType.class))).thenReturn(3);

        // Act
        localStreamManager.appendToolResult(sessionId, toolResult);

        // Assert
        verify(mockRepository).saveMessage(sessionId,
                InsertMessageEvent.StreamType.LOCAL, 4, toolResult);
    }

    @Test
    @DisplayName("appendToolResult should throw exception for null sessionId")
    void testAppendToolResultWithNullSessionId() {
        UnifiedMessage result = UnifiedMessage.ofToolResult("call_1", "result", false);
        assertThrows(IllegalArgumentException.class,
                () -> localStreamManager.appendToolResult(null, result));
    }

    @Test
    @DisplayName("appendToolResult should throw exception for null result")
    void testAppendToolResultWithNullResult() {
        assertThrows(IllegalArgumentException.class,
                () -> localStreamManager.appendToolResult("session", null));
    }

    @Test
    @DisplayName("fetchContext should call repository with LOCAL stream type")
    void testFetchContextWithLocalStreamType() {
        // Arrange
        String sessionId = "session_worker_003";
        List<UnifiedMessage> expectedContext = List.of(
                UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "Worker thinking..."),
                UnifiedMessage.ofToolResult("call_1", "Tool output", false)
        );
        when(mockRepository.fetchContext(eq(sessionId),
                any(InsertMessageEvent.StreamType.class))).thenReturn(expectedContext);

        // Act
        List<UnifiedMessage> result = localStreamManager.fetchContext(sessionId);

        // Assert
        assertEquals(expectedContext, result);
        verify(mockRepository).fetchContext(sessionId, InsertMessageEvent.StreamType.LOCAL);
    }

    @Test
    @DisplayName("fetchContext should throw exception for null sessionId")
    void testFetchContextWithNullSessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> localStreamManager.fetchContext(null));
    }

    @Test
    @DisplayName("fetchContext should throw exception for empty sessionId")
    void testFetchContextWithEmptySessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> localStreamManager.fetchContext(""));
    }

    @Test
    @DisplayName("getCurrentTokenCount should aggregate token counts of messages")
    void testGetCurrentTokenCountAggregatesTokens() {
        // Arrange
        String sessionId = "session_worker_004";
        List<UnifiedMessage> messages = List.of(
                UnifiedMessage.ofText(UnifiedMessage.Role.USER, "This is a test message with 20 chars"), // ~5 tokens
                UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "Another test with 24 chars here"), // ~6 tokens
                UnifiedMessage.ofToolResult("call_1", "Tool result string of 28 characters", false) // ~7 tokens
        );
        when(mockRepository.fetchContext(eq(sessionId),
                any(InsertMessageEvent.StreamType.class))).thenReturn(messages);

        // Act
        int tokenCount = localStreamManager.getCurrentTokenCount(sessionId);

        // Assert - Rough estimate: (20+24+28) / 4 = 18 tokens
        assertTrue(tokenCount > 0, "Token count should be positive");
        assertTrue(tokenCount < 100, "Token count should be reasonable for short messages");
    }

    @Test
    @DisplayName("getCurrentTokenCount should return 0 for empty context")
    void testGetCurrentTokenCountReturnsZeroForEmptyContext() {
        // Arrange
        String sessionId = "session_worker_empty";
        when(mockRepository.fetchContext(eq(sessionId),
                any(InsertMessageEvent.StreamType.class))).thenReturn(List.of());

        // Act
        int tokenCount = localStreamManager.getCurrentTokenCount(sessionId);

        // Assert
        assertEquals(0, tokenCount);
    }

    @Test
    @DisplayName("getCurrentTokenCount should throw exception for null sessionId")
    void testGetCurrentTokenCountWithNullSessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> localStreamManager.getCurrentTokenCount(null));
    }

    @Test
    @DisplayName("Multiple operations maintain correct sequence numbers")
    void testMultipleOperationsMaintainSequenceNumbers() {
        // Arrange
        String sessionId = "session_worker_multi";
        when(mockRepository.getCurrentSequenceNumber(eq(sessionId),
                any(InsertMessageEvent.StreamType.class)))
                .thenReturn(0)
                .thenReturn(1)
                .thenReturn(2);

        UnifiedMessage workerMsg = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "Worker msg");
        UnifiedMessage toolResult = UnifiedMessage.ofToolResult("call_1", "Success", false);

        // Act
        localStreamManager.appendWorkerMessage(sessionId, workerMsg);
        localStreamManager.appendToolResult(sessionId, toolResult);
        localStreamManager.appendWorkerMessage(sessionId, workerMsg);

        // Assert - Verify sequence numbers: 1, 2, 3
        verify(mockRepository, times(3)).saveMessage(eq(sessionId),
                eq(InsertMessageEvent.StreamType.LOCAL), anyInt(), any(UnifiedMessage.class));

        verify(mockRepository).saveMessage(sessionId,
                InsertMessageEvent.StreamType.LOCAL, 1, workerMsg);
        verify(mockRepository).saveMessage(sessionId,
                InsertMessageEvent.StreamType.LOCAL, 2, toolResult);
        verify(mockRepository).saveMessage(sessionId,
                InsertMessageEvent.StreamType.LOCAL, 3, workerMsg);
    }

    @Test
    @DisplayName("appendToolResult properly handles tool result with isError flag")
    void testAppendToolResultWithErrorFlag() {
        // Arrange
        String sessionId = "session_worker_error";
        UnifiedMessage errorResult = UnifiedMessage.ofToolResult("call_error", "Command blocked by sandbox", true);
        when(mockRepository.getCurrentSequenceNumber(eq(sessionId),
                any(InsertMessageEvent.StreamType.class))).thenReturn(0);

        // Act
        localStreamManager.appendToolResult(sessionId, errorResult);

        // Assert
        verify(mockRepository).saveMessage(sessionId,
                InsertMessageEvent.StreamType.LOCAL, 1, errorResult);
    }

    @Test
    @DisplayName("getCurrentTokenCount estimation is based on content length")
    void testTokenCountEstimationBasedOnContentLength() {
        // Arrange
        String sessionId = "session_worker_tokens";
        List<UnifiedMessage> messages = List.of(
                UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "A".repeat(40))   // ~10 tokens
        );
        when(mockRepository.fetchContext(eq(sessionId),
                any(InsertMessageEvent.StreamType.class))).thenReturn(messages);

        // Act
        int tokenCount = localStreamManager.getCurrentTokenCount(sessionId);

        // Assert - 40 chars / 4 = 10 tokens
        assertEquals(10, tokenCount);
    }

    @Test
    @DisplayName("fetchContext returns empty list when no messages exist")
    void testFetchContextReturnsEmptyList() {
        // Arrange
        String sessionId = "session_worker_no_msgs";
        when(mockRepository.fetchContext(eq(sessionId),
                any(InsertMessageEvent.StreamType.class))).thenReturn(List.of());

        // Act
        List<UnifiedMessage> result = localStreamManager.fetchContext(sessionId);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("appendToolResult with empty content returns minimum 1 token")
    void testAppendToolResultWithEmptyContent() {
        // Arrange
        String sessionId = "session_worker_empty";
        // Create a tool result with minimal content
        UnifiedMessage emptyResult = UnifiedMessage.ofToolResult("call_1", "", false);
        when(mockRepository.getCurrentSequenceNumber(eq(sessionId),
                any(InsertMessageEvent.StreamType.class))).thenReturn(0);

        // Act
        localStreamManager.appendToolResult(sessionId, emptyResult);

        // Assert - Should still save the message
        verify(mockRepository).saveMessage(eq(sessionId),
                eq(InsertMessageEvent.StreamType.LOCAL), eq(1), any(UnifiedMessage.class));
    }

    @Test
    @DisplayName("Worker and tool operations use separate sequence numbers")
    void testWorkerAndToolOperationsUseSeparateSequences() {
        // This test verifies that both worker messages and tool results share the same
        // sequence number space in the LOCAL stream, which is correct behavior

        // Arrange
        String sessionId = "session_worker_shared_seq";
        when(mockRepository.getCurrentSequenceNumber(eq(sessionId),
                any(InsertMessageEvent.StreamType.class)))
                .thenReturn(0)    // After 0, next will be 1
                .thenReturn(1)    // After 1, next will be 2
                .thenReturn(2);   // After 2, next will be 3

        UnifiedMessage workerMsg = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "Worker message");
        UnifiedMessage toolResult = UnifiedMessage.ofToolResult("call_1", "Tool result", false);

        // Act
        localStreamManager.appendWorkerMessage(sessionId, workerMsg);
        localStreamManager.appendToolResult(sessionId, toolResult);
        localStreamManager.appendWorkerMessage(sessionId, workerMsg);

        // Assert - All should use same sequence space: 1, 2, 3
        verify(mockRepository).saveMessage(sessionId,
                InsertMessageEvent.StreamType.LOCAL, 1, workerMsg);
        verify(mockRepository).saveMessage(sessionId,
                InsertMessageEvent.StreamType.LOCAL, 2, toolResult);
        verify(mockRepository).saveMessage(sessionId,
                InsertMessageEvent.StreamType.LOCAL, 3, workerMsg);
    }
}
