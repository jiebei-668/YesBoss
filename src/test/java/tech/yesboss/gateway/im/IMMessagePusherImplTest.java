package tech.yesboss.gateway.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.gateway.im.impl.IMMessagePusherImpl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 IMMessagePusherImpl 的消息推送功能
 *
 * <p>注意：此测试使用反射来测试内部方法，避免需要实际的环境变量和 HTTP 服务器。</p>
 */
class IMMessagePusherImplTest {

    private IMMessagePusherImpl pusher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        pusher = new IMMessagePusherImpl();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testConstructorDefault() {
        assertNotNull(pusher);
        assertDoesNotThrow(() -> new IMMessagePusherImpl());
    }

    @Test
    void testConstructorWithParameters() {
        HttpClient customClient = HttpClient.newHttpClient();
        assertDoesNotThrow(() -> new IMMessagePusherImpl(customClient, objectMapper));
    }

    @Test
    void testPushTextMessageWithoutWebhookUrlThrowsException() {
        // 不设置环境变量，应该抛出异常
        assertThrows(Exception.class, () -> {
            pusher.pushTextMessage("FEISHU", "NONEXISTENT_GROUP", "测试消息");
        });
    }

    @Test
    void testPushCardMessageWithoutWebhookUrlThrowsException() {
        // 不设置环境变量，应该抛出异常
        String cardJson = "{}";
        assertThrows(Exception.class, () -> {
            pusher.pushCardMessage("FEISHU", "NONEXISTENT_GROUP", cardJson);
        });
    }

    @Test
    void testPushMessageWithoutWebhookUrlThrowsException() {
        // 不设置环境变量，应该抛出异常
        UnifiedMessage message = UnifiedMessage.user("测试消息");
        assertThrows(Exception.class, () -> {
            pusher.pushMessage("FEISHU", "NONEXISTENT_GROUP", message);
        });
    }

    @Test
    void testUnsupportedImTypeThrowsException() {
        String groupName = "UNSUPPORTED_GROUP";
        // 对于不支持的 IM 类型，formatTextPayload 方法会捕获异常并返回原始文本
        // 所以这个测试验证的是 getEnvKey 会抛出异常
        assertThrows(Exception.class, () -> {
            try {
                Method method = IMMessagePusherImpl.class.getDeclaredMethod("getEnvKey", String.class, String.class);
                method.setAccessible(true);
                method.invoke(pusher, "UNSUPPORTED", "test-group");
            } catch (Exception e) {
                if (e.getCause() instanceof IllegalArgumentException) {
                    throw (IllegalArgumentException) e.getCause();
                }
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testFormatFeishuTextPayload() throws Exception {
        // 使用反射测试 formatTextPayload 方法
        Method method = IMMessagePusherImpl.class.getDeclaredMethod("formatTextPayload", String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(pusher, "FEISHU", "这是一条测试消息");

        assertNotNull(result);
        JsonNode json = objectMapper.readTree(result);
        assertEquals("text", json.get("msg_type").asText());
        assertEquals("这是一条测试消息", json.get("content").get("text").asText());
    }

    @Test
    void testFormatSlackTextPayload() throws Exception {
        // 使用反射测试 formatTextPayload 方法
        Method method = IMMessagePusherImpl.class.getDeclaredMethod("formatTextPayload", String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(pusher, "SLACK", "This is a test message");

        assertNotNull(result);
        JsonNode json = objectMapper.readTree(result);
        assertEquals("This is a test message", json.get("text").asText());
    }

    @Test
    void testFormatFeishuCardPayload() throws Exception {
        // 构建飞书卡片 JSON
        ObjectNode wrapper = objectMapper.createObjectNode();
        ObjectNode feishuCard = objectMapper.createObjectNode();
        feishuCard.put("msg_type", "interactive");
        ObjectNode feishuContent = objectMapper.createObjectNode();
        feishuContent.put("title", "测试卡片");
        feishuCard.set("content", feishuContent);
        wrapper.set("feishu_card", feishuCard);

        String cardJson = objectMapper.writeValueAsString(wrapper);

        // 使用反射测试 formatCardPayload 方法
        Method method = IMMessagePusherImpl.class.getDeclaredMethod("formatCardPayload", String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(pusher, "FEISHU", cardJson);

        assertNotNull(result);
        JsonNode json = objectMapper.readTree(result);
        assertEquals("interactive", json.get("msg_type").asText());
        assertTrue(json.has("content"));
    }

    @Test
    void testFormatSlackCardPayload() throws Exception {
        // 构建 Slack 卡片 JSON
        ObjectNode wrapper = objectMapper.createObjectNode();
        ObjectNode attachment = objectMapper.createObjectNode();
        attachment.put("title", "测试卡片");
        attachment.put("text", "这是一张测试卡片");
        attachment.put("color", "good");
        wrapper.set("slack_attachments", objectMapper.createArrayNode().add(attachment));

        String cardJson = objectMapper.writeValueAsString(wrapper);

        // 使用反射测试 formatCardPayload 方法
        Method method = IMMessagePusherImpl.class.getDeclaredMethod("formatCardPayload", String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(pusher, "SLACK", cardJson);

        assertNotNull(result);
        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.has("attachments"));
        assertTrue(json.get("attachments").isArray());
    }

    @Test
    void testFormatCardPayloadWithInvalidJson() throws Exception {
        // 测试无效的 JSON 输入
        String invalidJson = "{invalid json}";

        // 使用反射测试 formatCardPayload 方法
        Method method = IMMessagePusherImpl.class.getDeclaredMethod("formatCardPayload", String.class, String.class);
        method.setAccessible(true);

        // 应该返回原始 JSON，而不是抛出异常
        String result = (String) method.invoke(pusher, "FEISHU", invalidJson);

        assertNotNull(result);
        assertEquals(invalidJson, result);
    }

    @Test
    void testGetEnvKeyForFeishu() throws Exception {
        // 使用反射测试 getEnvKey 方法
        Method method = IMMessagePusherImpl.class.getDeclaredMethod("getEnvKey", String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(pusher, "FEISHU", "test-group-123");

        assertEquals("FEISHU_WEBHOOK_TEST_GROUP_123", result);
    }

    @Test
    void testGetEnvKeyForSlack() throws Exception {
        // 使用反射测试 getEnvKey 方法
        Method method = IMMessagePusherImpl.class.getDeclaredMethod("getEnvKey", String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(pusher, "SLACK", "test.group@special");

        assertEquals("SLACK_WEBHOOK_TEST_GROUP_SPECIAL", result);
    }

    @Test
    void testGetEnvKeyForSpecialCharacters() throws Exception {
        // 使用反射测试 getEnvKey 方法
        Method method = IMMessagePusherImpl.class.getDeclaredMethod("getEnvKey", String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(pusher, "FEISHU", "my.group-123@id");

        assertEquals("FEISHU_WEBHOOK_MY_GROUP_123_ID", result);
    }

    @Test
    void testGetEnvKeyForUnsupportedImType() throws Exception {
        // 使用反射测试 getEnvKey 方法
        Method method = IMMessagePusherImpl.class.getDeclaredMethod("getEnvKey", String.class, String.class);
        method.setAccessible(true);

        assertThrows(Exception.class, () -> {
            try {
                method.invoke(pusher, "UNSUPPORTED", "test-group");
            } catch (Exception e) {
                if (e.getCause() instanceof IllegalArgumentException) {
                    throw (IllegalArgumentException) e.getCause();
                }
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testGetWebhookUrlReturnsNullWhenNotSet() throws Exception {
        // 使用反射测试 getWebhookUrl 方法
        Method method = IMMessagePusherImpl.class.getDeclaredMethod("getWebhookUrl", String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(pusher, "FEISHU", "NONEXISTENT_GROUP");

        assertNull(result);
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
    void testFeishuTextPayloadStructure() throws Exception {
        // 测试飞书文本消息负载结构
        Method method = IMMessagePusherImpl.class.getDeclaredMethod("formatTextPayload", String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(pusher, "FEISHU", "测试消息");

        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.has("msg_type"));
        assertTrue(json.has("content"));
        assertTrue(json.get("content").isObject());
        assertTrue(json.get("content").has("text"));
    }

    @Test
    void testSlackTextPayloadStructure() throws Exception {
        // 测试 Slack 文本消息负载结构
        Method method = IMMessagePusherImpl.class.getDeclaredMethod("formatTextPayload", String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(pusher, "SLACK", "Test message");

        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.has("text"));
        assertEquals("Test message", json.get("text").asText());
    }

    @Test
    void testFormatTextPayloadErrorHandling() throws Exception {
        // 测试格式化文本消息时的错误处理
        // 对于不支持的 IM 类型，formatTextPayload 会捕获异常并返回原始文本
        Method method = IMMessagePusherImpl.class.getDeclaredMethod("formatTextPayload", String.class, String.class);
        method.setAccessible(true);

        // 应该返回原始文本，而不是抛出异常（内部有 try-catch）
        String result = (String) method.invoke(pusher, "UNSUPPORTED", "测试");
        assertNotNull(result);
        // 当格式化失败时，返回原始文本
        assertEquals("测试", result);
    }
}
