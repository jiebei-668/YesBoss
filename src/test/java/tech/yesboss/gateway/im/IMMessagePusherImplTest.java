package tech.yesboss.gateway.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.gateway.im.impl.IMMessagePusherImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 测试 IMMessagePusherImpl 的消息推送功能
 *
 * <p>此测试使用 Mockito 模拟 FeishuApiClient，避免实际的 HTTP 调用。</p>
 */
@ExtendWith(MockitoExtension.class)
class IMMessagePusherImplTest {

    @Mock
    private FeishuApiClient mockFeishuApiClient;

    private IMMessagePusherImpl pusher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        pusher = new IMMessagePusherImpl(mockFeishuApiClient, objectMapper);
    }

    @Test
    void testConstructorWithFeishuApiClient() {
        assertNotNull(pusher);
        assertDoesNotThrow(() -> new IMMessagePusherImpl(mockFeishuApiClient));
    }

    @Test
    void testConstructorWithNullFeishuApiClientThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new IMMessagePusherImpl(null));
    }

    @Test
    void testConstructorWithAllParameters() {
        assertNotNull(pusher);
        assertDoesNotThrow(() -> new IMMessagePusherImpl(mockFeishuApiClient, objectMapper));
    }

    @Test
    void testPushFeishuTextMessageSuccess() throws Exception {
        String chatId = "test_chat_id";
        String message = "测试消息";
        String expectedMessageId = "om_1234567890";

        when(mockFeishuApiClient.sendTextMessage(chatId, "chat_id", message))
                .thenReturn(expectedMessageId);

        assertDoesNotThrow(() -> pusher.pushTextMessage("FEISHU", chatId, message));

        verify(mockFeishuApiClient, times(1)).sendTextMessage(chatId, "chat_id", message);
    }

    @Test
    void testPushFeishuCardMessageSuccess() throws Exception {
        String chatId = "test_chat_id";
        String cardJson = "{\"card\": \"content\"}";
        String expectedMessageId = "om_9876543210";

        when(mockFeishuApiClient.sendCardMessage(chatId, "chat_id", cardJson))
                .thenReturn(expectedMessageId);

        assertDoesNotThrow(() -> pusher.pushCardMessage("FEISHU", chatId, cardJson));

        verify(mockFeishuApiClient, times(1)).sendCardMessage(chatId, "chat_id", cardJson);
    }

    @Test
    void testPushFeishuTextMessagePropagatesApiException() throws Exception {
        String chatId = "test_chat_id";
        String message = "测试消息";
        FeishuApiClient.FeishuApiException apiException =
                new FeishuApiClient.FeishuApiException("API Error", 401);

        when(mockFeishuApiClient.sendTextMessage(anyString(), anyString(), anyString()))
                .thenThrow(apiException);

        FeishuApiClient.FeishuApiException thrown = assertThrows(
                FeishuApiClient.FeishuApiException.class,
                () -> pusher.pushTextMessage("FEISHU", chatId, message)
        );

        assertEquals("API Error", thrown.getMessage());
        assertEquals(401, thrown.getCode());
    }

    @Test
    void testPushFeishuCardMessagePropagatesApiException() throws Exception {
        String chatId = "test_chat_id";
        String cardJson = "{\"card\": \"content\"}";
        FeishuApiClient.FeishuApiException apiException =
                new FeishuApiClient.FeishuApiException("Card send failed", 400);

        when(mockFeishuApiClient.sendCardMessage(anyString(), anyString(), anyString()))
                .thenThrow(apiException);

        FeishuApiClient.FeishuApiException thrown = assertThrows(
                FeishuApiClient.FeishuApiException.class,
                () -> pusher.pushCardMessage("FEISHU", chatId, cardJson)
        );

        assertEquals("Card send failed", thrown.getMessage());
        assertEquals(400, thrown.getCode());
    }

    @Test
    void testPushSlackTextMessageThrowsUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> {
            pusher.pushTextMessage("SLACK", "test_chat", "message");
        });
    }

    @Test
    void testPushSlackCardMessageThrowsUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> {
            pusher.pushCardMessage("SLACK", "test_chat", "{}");
        });
    }

    @Test
    void testPushUnsupportedImTypeThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            pusher.pushTextMessage("UNSUPPORTED", "test_chat", "message");
        });
    }

    @Test
    void testPushMessageWithUnifiedMessage() throws Exception {
        String chatId = "test_chat_id";
        String expectedMessageId = "om_1111111111";
        UnifiedMessage message = UnifiedMessage.user("用户消息");

        when(mockFeishuApiClient.sendTextMessage(eq(chatId), eq("chat_id"), anyString()))
                .thenReturn(expectedMessageId);

        assertDoesNotThrow(() -> pusher.pushMessage("FEISHU", chatId, message));

        verify(mockFeishuApiClient, times(1)).sendTextMessage(eq(chatId), eq("chat_id"), anyString());
    }

    @Test
    void testPushMessageCaseInsensitiveImType() throws Exception {
        String expectedMessageId = "om_2222222222";

        when(mockFeishuApiClient.sendTextMessage(anyString(), eq("chat_id"), anyString()))
                .thenReturn(expectedMessageId);

        // Test lowercase
        assertDoesNotThrow(() -> pusher.pushTextMessage("feishu", "chat1", "msg"));

        // Test mixed case
        assertDoesNotThrow(() -> pusher.pushTextMessage("FeIsHu", "chat2", "msg"));

        verify(mockFeishuApiClient, times(2)).sendTextMessage(anyString(), eq("chat_id"), anyString());
    }

    @Test
    void testUnifiedMessageConversion() {
        // 测试 UnifiedMessage 到文本的转换
        UnifiedMessage userMessage = UnifiedMessage.user("用户消息");
        String displayContent = userMessage.getDisplayContent();

        assertNotNull(displayContent);
        assertTrue(displayContent.contains("用户") || displayContent.contains("用户消息"));
    }

    @Test
    void testUnifiedMessageWithToolResult() {
        // 测试包含工具结果的 UnifiedMessage
        UnifiedMessage toolMessage = UnifiedMessage.ofToolResult("call-123", "命令执行成功", false);
        String displayContent = toolMessage.getDisplayContent();

        assertNotNull(displayContent);
        // getDisplayContent() returns "[1 tool result]" for tool results
        assertTrue(displayContent.contains("tool result") || displayContent.contains("1"));
    }

    @Test
    void testUnifiedMessageWithSystemRole() {
        // 测试系统角色的 UnifiedMessage
        UnifiedMessage systemMessage = UnifiedMessage.system("系统提示消息");
        String displayContent = systemMessage.getDisplayContent();

        assertNotNull(displayContent);
        assertTrue(displayContent.contains("系统") || displayContent.contains("提示"));
    }

    @Test
    void testPushEmptyMessageUsesFeishuApi() throws Exception {
        String chatId = "test_chat_id";
        String emptyMessage = "";
        String expectedMessageId = "om_3333333333";

        when(mockFeishuApiClient.sendTextMessage(chatId, "chat_id", emptyMessage))
                .thenReturn(expectedMessageId);

        assertDoesNotThrow(() -> pusher.pushTextMessage("FEISHU", chatId, emptyMessage));

        verify(mockFeishuApiClient, times(1)).sendTextMessage(chatId, "chat_id", emptyMessage);
    }
}
