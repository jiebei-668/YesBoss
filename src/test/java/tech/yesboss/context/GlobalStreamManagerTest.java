package tech.yesboss.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.yesboss.context.impl.GlobalStreamManagerImpl;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.persistence.repository.ChatMessageRepository;
import tech.yesboss.persistence.event.InsertMessageEvent;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for GlobalStreamManager context append and fetch operations.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>appendHumanMessage generates correct UnifiedMessage format</li>
 *   <li>appendSystemMessage generates correct system-forged UnifiedMessage</li>
 *   <li>appendMasterMessage correctly stores the provided UnifiedMessage</li>
 *   <li>fetchContext calls repository with correct sessionId and GLOBAL streamType</li>
 * </ul>
 */
@DisplayName("GlobalStreamManager Tests")
class GlobalStreamManagerTest {

    private ChatMessageRepository mockRepository;
    private GlobalStreamManager globalStreamManager;

    @BeforeEach
    void setUp() {
        mockRepository = mock(ChatMessageRepository.class);
        globalStreamManager = new GlobalStreamManagerImpl(mockRepository);
    }

    @Test
    @DisplayName("Constructor should throw exception for null repository")
    void testConstructorWithNullRepository() {
        assertThrows(IllegalArgumentException.class,
                () -> new GlobalStreamManagerImpl(null),
                "Should throw exception for null repository");
    }

    @Test
    @DisplayName("appendHumanMessage should generate USER role UnifiedMessage")
    void testAppendHumanMessageGeneratesCorrectFormat() {
        // Arrange
        String sessionId = "session_master_001";
        String humanText = "帮我重构这个模块";
        when(mockRepository.getCurrentSequenceNumber(eq(sessionId),
                any(InsertMessageEvent.StreamType.class))).thenReturn(0);

        // Act
        globalStreamManager.appendHumanMessage(sessionId, humanText);

        // Assert
        verify(mockRepository).getCurrentSequenceNumber(sessionId, InsertMessageEvent.StreamType.GLOBAL);
        verify(mockRepository).saveMessage(eq(sessionId),
                eq(InsertMessageEvent.StreamType.GLOBAL), eq(1), any(UnifiedMessage.class));

        // Verify the message was saved with USER role
        verify(mockRepository).saveMessage(eq(sessionId),
                eq(InsertMessageEvent.StreamType.GLOBAL), eq(1),
                argThat(msg -> msg.role() == UnifiedMessage.Role.USER &&
                        msg.hasToolCalls() == false &&
                        msg.isTextOnly()));
    }

    @Test
    @DisplayName("appendHumanMessage should throw exception for null sessionId")
    void testAppendHumanMessageWithNullSessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> globalStreamManager.appendHumanMessage(null, "test"),
                "Should throw exception for null sessionId");
    }

    @Test
    @DisplayName("appendHumanMessage should throw exception for empty sessionId")
    void testAppendHumanMessageWithEmptySessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> globalStreamManager.appendHumanMessage("", "test"),
                "Should throw exception for empty sessionId");
    }

    @Test
    @DisplayName("appendHumanMessage should throw exception for null text")
    void testAppendHumanMessageWithNullText() {
        assertThrows(IllegalArgumentException.class,
                () -> globalStreamManager.appendHumanMessage("session", null),
                "Should throw exception for null text");
    }

    @Test
    @DisplayName("appendHumanMessage should throw exception for empty text")
    void testAppendHumanMessageWithEmptyText() {
        assertThrows(IllegalArgumentException.class,
                () -> globalStreamManager.appendHumanMessage("session", ""),
                "Should throw exception for empty text");
    }

    @Test
    @DisplayName("appendMasterMessage should store provided UnifiedMessage")
    void testAppendMasterMessageStoresProvidedMessage() {
        // Arrange
        String sessionId = "session_master_002";
        UnifiedMessage masterMessage = UnifiedMessage.ofText(
                UnifiedMessage.Role.ASSISTANT,
                "我将把这个任务分解为三个子任务"
        );
        when(mockRepository.getCurrentSequenceNumber(eq(sessionId),
                any(InsertMessageEvent.StreamType.class))).thenReturn(5);

        // Act
        globalStreamManager.appendMasterMessage(sessionId, masterMessage);

        // Assert
        verify(mockRepository).saveMessage(sessionId,
                InsertMessageEvent.StreamType.GLOBAL, 6, masterMessage);
    }

    @Test
    @DisplayName("appendMasterMessage should throw exception for null sessionId")
    void testAppendMasterMessageWithNullSessionId() {
        UnifiedMessage message = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT,"test");
        assertThrows(IllegalArgumentException.class,
                () -> globalStreamManager.appendMasterMessage(null, message));
    }

    @Test
    @DisplayName("appendMasterMessage should throw exception for null message")
    void testAppendMasterMessageWithNullMessage() {
        assertThrows(IllegalArgumentException.class,
                () -> globalStreamManager.appendMasterMessage("session", null));
    }

    @Test
    @DisplayName("appendSystemMessage generates SYSTEM role UnifiedMessage")
    void testAppendSystemMessageGeneratesSystemMessage() {
        // Arrange
        String sessionId = "session_master_003";
        String systemText = "Worker 触发了黑名单拦截，需要您的授权";
        when(mockRepository.getCurrentSequenceNumber(eq(sessionId),
                any(InsertMessageEvent.StreamType.class))).thenReturn(2);

        // Act
        globalStreamManager.appendSystemMessage(sessionId, systemText);

        // Assert
        verify(mockRepository).saveMessage(eq(sessionId),
                eq(InsertMessageEvent.StreamType.GLOBAL), eq(3), any(UnifiedMessage.class));

        // Verify the message was saved with SYSTEM role
        verify(mockRepository).saveMessage(eq(sessionId),
                eq(InsertMessageEvent.StreamType.GLOBAL), eq(3),
                argThat(msg -> msg.role() == UnifiedMessage.Role.SYSTEM &&
                        msg.isTextOnly()));
    }

    @Test
    @DisplayName("appendSystemMessage should throw exception for null sessionId")
    void testAppendSystemMessageWithNullSessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> globalStreamManager.appendSystemMessage(null, "test"));
    }

    @Test
    @DisplayName("appendSystemMessage should throw exception for null text")
    void testAppendSystemMessageWithNullText() {
        assertThrows(IllegalArgumentException.class,
                () -> globalStreamManager.appendSystemMessage("session", null));
    }

    @Test
    @DisplayName("fetchContext calls repository with correct parameters")
    void testFetchContextCallsRepositoryCorrectly() {
        // Arrange
        String sessionId = "session_master_004";
        List<UnifiedMessage> expectedContext = List.of(
                UnifiedMessage.user("初始需求"),
                UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT,"我理解了，开始规划")
        );
        when(mockRepository.fetchContext(eq(sessionId),
                any(InsertMessageEvent.StreamType.class))).thenReturn(expectedContext);

        // Act
        List<UnifiedMessage> result = globalStreamManager.fetchContext(sessionId);

        // Assert
        assertEquals(expectedContext, result);
        verify(mockRepository).fetchContext(sessionId, InsertMessageEvent.StreamType.GLOBAL);
    }

    @Test
    @DisplayName("fetchContext should throw exception for null sessionId")
    void testFetchContextWithNullSessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> globalStreamManager.fetchContext(null));
    }

    @Test
    @DisplayName("fetchContext should throw exception for empty sessionId")
    void testFetchContextWithEmptySessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> globalStreamManager.fetchContext(""));
    }

    @Test
    @DisplayName("fetchContext returns empty list when no messages exist")
    void testFetchContextReturnsEmptyList() {
        // Arrange
        String sessionId = "session_master_empty";
        when(mockRepository.fetchContext(eq(sessionId),
                any(InsertMessageEvent.StreamType.class))).thenReturn(List.of());

        // Act
        List<UnifiedMessage> result = globalStreamManager.fetchContext(sessionId);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Multiple operations maintain correct sequence numbers")
    void testMultipleOperationsMaintainSequenceNumbers() {
        // Arrange
        String sessionId = "session_master_multi";
        when(mockRepository.getCurrentSequenceNumber(eq(sessionId),
                any(InsertMessageEvent.StreamType.class)))
                .thenReturn(0)    // Initial: 0, next will be 1
                .thenReturn(1)    // After first: 1, next will be 2
                .thenReturn(2)    // After second: 2, next will be 3
                .thenReturn(3);   // After third: 3, next will be 4

        // Act
        globalStreamManager.appendHumanMessage(sessionId, "任务1");
        globalStreamManager.appendMasterMessage(sessionId, UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT,"回复1"));
        globalStreamManager.appendSystemMessage(sessionId, "系统消息");
        globalStreamManager.appendHumanMessage(sessionId, "任务2");

        // Assert - Verify sequence numbers were incremented correctly
        verify(mockRepository).saveMessage(eq(sessionId),
                eq(InsertMessageEvent.StreamType.GLOBAL), eq(1), any(UnifiedMessage.class));
        verify(mockRepository).saveMessage(eq(sessionId),
                eq(InsertMessageEvent.StreamType.GLOBAL), eq(2), any(UnifiedMessage.class));
        verify(mockRepository).saveMessage(eq(sessionId),
                eq(InsertMessageEvent.StreamType.GLOBAL), eq(3), any(UnifiedMessage.class));
        verify(mockRepository).saveMessage(eq(sessionId),
                eq(InsertMessageEvent.StreamType.GLOBAL), eq(4), any(UnifiedMessage.class));
    }
}
