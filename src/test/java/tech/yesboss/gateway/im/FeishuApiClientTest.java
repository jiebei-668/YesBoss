package tech.yesboss.gateway.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FeishuApiClient.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>Constructor validation</li>
 *   <li>Message content formatting</li>
 *   <li>Token cache management</li>
 *   <li>Configuration helper methods</li>
 * </ul>
 */
@DisplayName("FeishuApiClient Tests")
class FeishuApiClientTest {

    private static final String TEST_APP_ID = "cli_test123456";
    private static final String TEST_APP_SECRET = "test_secret_abc123";
    private static final String TEST_ACCESS_TOKEN = "test_access_token_xyz789";
    private static final String TEST_CHAT_ID = "oc_test_chat_id";
    private static final String TEST_MESSAGE_ID = "om_test_message_id";

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ==================== Constructor Tests ====================

    @Test
    @DisplayName("Constructor should create client with valid parameters")
    void testConstructor() {
        assertDoesNotThrow(() -> new FeishuApiClient(TEST_APP_ID, TEST_APP_SECRET));
        FeishuApiClient client = new FeishuApiClient(TEST_APP_ID, TEST_APP_SECRET);
        assertEquals(TEST_APP_ID, client.getAppId());
    }

    @Test
    @DisplayName("Constructor should create client with timeout")
    void testConstructorWithTimeout() {
        assertDoesNotThrow(() -> new FeishuApiClient(TEST_APP_ID, TEST_APP_SECRET, 60));
        FeishuApiClient client = new FeishuApiClient(TEST_APP_ID, TEST_APP_SECRET, 60);
        assertEquals(TEST_APP_ID, client.getAppId());
    }

    @Test
    @DisplayName("Constructor should throw exception with null appId")
    void testConstructorNullAppId() {
        assertThrows(IllegalArgumentException.class, () ->
                new FeishuApiClient(null, TEST_APP_SECRET));
    }

    @Test
    @DisplayName("Constructor should throw exception with empty appId")
    void testConstructorEmptyAppId() {
        assertThrows(IllegalArgumentException.class, () ->
                new FeishuApiClient("", TEST_APP_SECRET));
    }

    @Test
    @DisplayName("Constructor should throw exception with null appSecret")
    void testConstructorNullAppSecret() {
        assertThrows(IllegalArgumentException.class, () ->
                new FeishuApiClient(TEST_APP_ID, null));
    }

    @Test
    @DisplayName("Constructor should throw exception with empty appSecret")
    void testConstructorEmptyAppSecret() {
        assertThrows(IllegalArgumentException.class, () ->
                new FeishuApiClient(TEST_APP_ID, ""));
    }

    // ==================== Content Parsing Tests ====================

    @Test
    @DisplayName("parseContent should format text messages correctly")
    void testParseContentForText() throws Exception {
        FeishuApiClient client = new FeishuApiClient(TEST_APP_ID, TEST_APP_SECRET);
        Method method = FeishuApiClient.class.getDeclaredMethod("parseContent", String.class, String.class);
        method.setAccessible(true);

        var result = method.invoke(client, "text", "Hello, World!");
        String json = objectMapper.writeValueAsString(result);

        assertTrue(json.contains("\"text\":\"Hello, World!\""));
    }

    @Test
    @DisplayName("parseContent should format card messages correctly")
    void testParseContentForCard() throws Exception {
        FeishuApiClient client = new FeishuApiClient(TEST_APP_ID, TEST_APP_SECRET);
        Method method = FeishuApiClient.class.getDeclaredMethod("parseContent", String.class, String.class);
        method.setAccessible(true);

        String cardJson = "{\"config\":{\"wide_screen_mode\":true}}";
        var result = method.invoke(client, "interactive", cardJson);

        assertNotNull(result);
    }

    @Test
    @DisplayName("parseContent should throw exception for invalid message type")
    void testParseContentInvalidType() {
        FeishuApiClient client = new FeishuApiClient(TEST_APP_ID, TEST_APP_SECRET);
        Method method;
        try {
            method = FeishuApiClient.class.getDeclaredMethod("parseContent", String.class, String.class);
            method.setAccessible(true);

            assertThrows(Exception.class, () ->
                    method.invoke(client, "invalid_type", "test"));

        } catch (NoSuchMethodException e) {
            fail("parseContent method should exist");
        }
    }

    // ==================== Validation Tests ====================

    @Test
    @DisplayName("isValidReceiveIdType should accept chat_id")
    void testIsValidReceiveIdTypeChatId() throws Exception {
        FeishuApiClient client = new FeishuApiClient(TEST_APP_ID, TEST_APP_SECRET);
        Method method = FeishuApiClient.class.getDeclaredMethod("isValidReceiveIdType", String.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(client, "chat_id");
        assertTrue(result);
    }

    @Test
    @DisplayName("isValidReceiveIdType should accept open_id")
    void testIsValidReceiveIdTypeOpenId() throws Exception {
        FeishuApiClient client = new FeishuApiClient(TEST_APP_ID, TEST_APP_SECRET);
        Method method = FeishuApiClient.class.getDeclaredMethod("isValidReceiveIdType", String.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(client, "open_id");
        assertTrue(result);
    }

    @Test
    @DisplayName("isValidReceiveIdType should accept user_id")
    void testIsValidReceiveIdTypeUserId() throws Exception {
        FeishuApiClient client = new FeishuApiClient(TEST_APP_ID, TEST_APP_SECRET);
        Method method = FeishuApiClient.class.getDeclaredMethod("isValidReceiveIdType", String.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(client, "user_id");
        assertTrue(result);
    }

    @Test
    @DisplayName("isValidReceiveIdType should reject invalid type")
    void testIsValidReceiveIdTypeInvalid() throws Exception {
        FeishuApiClient client = new FeishuApiClient(TEST_APP_ID, TEST_APP_SECRET);
        Method method = FeishuApiClient.class.getDeclaredMethod("isValidReceiveIdType", String.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(client, "invalid_type");
        assertFalse(result);
    }

    // ==================== Token Cache Tests ====================

    @Test
    @DisplayName("Token cache should be invalid initially")
    void testTokenNotValidInitially() {
        FeishuApiClient client = new FeishuApiClient(TEST_APP_ID, TEST_APP_SECRET);
        assertFalse(client.isTokenValid());
    }

    @Test
    @DisplayName("clearTokenCache should invalidate token")
    void testClearTokenCache() throws Exception {
        FeishuApiClient client = new FeishuApiClient(TEST_APP_ID, TEST_APP_SECRET);

        // Set a fake token in cache using reflection
        Field tokenField = FeishuApiClient.class.getDeclaredField("cachedAccessToken");
        tokenField.setAccessible(true);
        tokenField.set(client, TEST_ACCESS_TOKEN);

        Field expiryField = FeishuApiClient.class.getDeclaredField("tokenExpiryTime");
        expiryField.setAccessible(true);
        expiryField.setLong(client, System.currentTimeMillis() + 100000);

        assertTrue(client.isTokenValid());

        // Clear cache
        client.clearTokenCache();
        assertFalse(client.isTokenValid());
    }

    @Test
    @DisplayName("FeishuApiException should contain error code")
    void testFeishuApiExceptionCode() {
        FeishuApiClient.FeishuApiException exception =
                new FeishuApiClient.FeishuApiException("Test error", 9999);

        assertEquals(9999, exception.getCode());
        assertTrue(exception.getMessage().contains("Test error"));
    }

    @Test
    @DisplayName("FeishuApiException should recognize rate limit errors")
    void testFeishuApiExceptionRateLimit() {
        FeishuApiClient.FeishuApiException exception =
                new FeishuApiClient.FeishuApiException("Rate limit", 9999);

        assertTrue(exception.isRateLimitError());
        assertFalse(exception.isAuthError());
    }

    @Test
    @DisplayName("FeishuApiException should recognize auth errors")
    void testFeishuApiExceptionAuthError() {
        FeishuApiClient.FeishuApiException exception =
                new FeishuApiClient.FeishuApiException("Auth failed", 401);

        assertTrue(exception.isAuthError());
        assertFalse(exception.isRateLimitError());
    }

    @Test
    @DisplayName("FeishuApiException with cause")
    void testFeishuApiExceptionWithCause() {
        Throwable cause = new RuntimeException("Inner error");
        FeishuApiClient.FeishuApiException exception =
                new FeishuApiClient.FeishuApiException("Outer error", 500, cause);

        assertEquals(500, exception.getCode());
        assertEquals(cause, exception.getCause());
    }

    // ==================== Request Formatting Tests ====================

    @Test
    @DisplayName("sendTextMessage should throw on null receiveId")
    void testSendTextMessageNullReceiveId() {
        FeishuApiClient client = new FeishuApiClient(TEST_APP_ID, TEST_APP_SECRET);
        assertThrows(IllegalArgumentException.class, () ->
                client.sendTextMessage(null, "chat_id", "test"));
    }

    @Test
    @DisplayName("sendTextMessage should throw on empty receiveId")
    void testSendTextMessageEmptyReceiveId() {
        FeishuApiClient client = new FeishuApiClient(TEST_APP_ID, TEST_APP_SECRET);
        assertThrows(IllegalArgumentException.class, () ->
                client.sendTextMessage("", "chat_id", "test"));
    }

    @Test
    @DisplayName("sendTextMessage should throw on invalid receiveIdType")
    void testSendTextMessageInvalidReceiveIdType() {
        FeishuApiClient client = new FeishuApiClient(TEST_APP_ID, TEST_APP_SECRET);
        assertThrows(IllegalArgumentException.class, () ->
                client.sendTextMessage(TEST_CHAT_ID, "invalid_type", "test"));
    }

    @Test
    @DisplayName("sendTextMessage should throw on null text")
    void testSendTextMessageNullText() {
        FeishuApiClient client = new FeishuApiClient(TEST_APP_ID, TEST_APP_SECRET);
        assertThrows(IllegalArgumentException.class, () ->
                client.sendTextMessage(TEST_CHAT_ID, "chat_id", null));
    }

    @Test
    @DisplayName("sendTextMessage should throw on empty text")
    void testSendTextMessageEmptyText() {
        FeishuApiClient client = new FeishuApiClient(TEST_APP_ID, TEST_APP_SECRET);
        assertThrows(IllegalArgumentException.class, () ->
                client.sendTextMessage(TEST_CHAT_ID, "chat_id", ""));
    }

    @Test
    @DisplayName("sendCardMessage should throw on null cardJson")
    void testSendCardMessageNullCardJson() {
        FeishuApiClient client = new FeishuApiClient(TEST_APP_ID, TEST_APP_SECRET);
        assertThrows(IllegalArgumentException.class, () ->
                client.sendCardMessage(TEST_CHAT_ID, "chat_id", null));
    }

    @Test
    @DisplayName("sendCardMessage should throw on empty cardJson")
    void testSendCardMessageEmptyCardJson() {
        FeishuApiClient client = new FeishuApiClient(TEST_APP_ID, TEST_APP_SECRET);
        assertThrows(IllegalArgumentException.class, () ->
                client.sendCardMessage(TEST_CHAT_ID, "chat_id", ""));
    }

    // ==================== Helper Methods ====================

    @Test
    @DisplayName("buildTokenResponse creates valid JSON")
    void testBuildTokenResponse() throws Exception {
        String response = buildTokenResponse(TEST_ACCESS_TOKEN, 7200);
        var json = objectMapper.readTree(response);

        assertEquals(0, json.get("code").asInt());
        assertEquals(TEST_ACCESS_TOKEN, json.get("tenant_access_token").asText());
        assertEquals(7200, json.get("expire").asInt());
    }

    @Test
    @DisplayName("buildSendMessageResponse creates valid JSON")
    void testBuildSendMessageResponse() throws Exception {
        String response = buildSendMessageResponse(TEST_MESSAGE_ID);
        var json = objectMapper.readTree(response);

        assertEquals(0, json.get("code").asInt());
        assertEquals(TEST_MESSAGE_ID, json.get("data").get("message_id").asText());
    }

    @Test
    @DisplayName("buildErrorResponse creates valid JSON")
    void testBuildErrorResponse() throws Exception {
        String response = buildErrorResponse(9999, "Test error");
        var json = objectMapper.readTree(response);

        assertEquals(9999, json.get("code").asInt());
        assertEquals("Test error", json.get("msg").asText());
    }

    /**
     * Build a successful token response for testing.
     */
    private String buildTokenResponse(String accessToken, int expireSeconds) {
        com.fasterxml.jackson.databind.node.ObjectNode response = objectMapper.createObjectNode();
        response.put("code", 0);
        response.put("tenant_access_token", accessToken);
        response.put("expire", expireSeconds);
        return response.toString();
    }

    /**
     * Build a successful send message response for testing.
     */
    private String buildSendMessageResponse(String messageId) {
        com.fasterxml.jackson.databind.node.ObjectNode response = objectMapper.createObjectNode();
        response.put("code", 0);
        com.fasterxml.jackson.databind.node.ObjectNode data = response.putObject("data");
        data.put("message_id", messageId);
        return response.toString();
    }

    /**
     * Build an error response for testing.
     */
    private String buildErrorResponse(int code, String message) {
        com.fasterxml.jackson.databind.node.ObjectNode response = objectMapper.createObjectNode();
        response.put("code", code);
        response.put("msg", message);
        return response.toString();
    }
}
