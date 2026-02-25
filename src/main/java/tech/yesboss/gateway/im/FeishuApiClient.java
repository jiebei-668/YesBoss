package tech.yesboss.gateway.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Feishu API Client for token management and message sending.
 *
 * <p>This client handles:</p>
 * <ul>
 *   <li>OAuth2 tenant access token retrieval and caching</li>
 *   <li>Sending messages to Feishu chats (card and text formats)</li>
 *   <li>Automatic retry logic with exponential backoff</li>
 *   <li>Comprehensive error handling</li>
 * </ul>
 *
 * <p><b>API Endpoints:</b></p>
 * <ul>
 *   <li>Auth: https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal</li>
 *   <li>Message: https://open.feishu.cn/open-apis/im/v1/messages</li>
 * </ul>
 *
 * <p><b>Receive ID Types:</b></p>
 * <ul>
 *   <li>chat_id - Group chat ID</li>
 *   <li>open_id - User open ID</li>
 *   <li>user_id - User ID</li>
 * </ul>
 *
 * @see <a href="https://open.feishu.cn/document/server-docs/im-v1/message/create">Feishu Message API</a>
 */
public class FeishuApiClient {

    private static final Logger logger = LoggerFactory.getLogger(FeishuApiClient.class);

    // API Endpoints
    private static final String AUTH_URL = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
    private static final String MESSAGE_URL = "https://open.feishu.cn/open-apis/im/v1/messages";

    // HTTP Timeouts
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    // Token expiry buffer (refresh 5 minutes before actual expiry)
    private static final long TOKEN_EXPIRY_BUFFER_SECONDS = 300;

    // Retry configuration
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;

    // Feishu configuration
    private final String appId;
    private final String appSecret;
    private final int timeoutSeconds;

    // HTTP client
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Token cache (thread-safe)
    private volatile String cachedAccessToken;
    private volatile long tokenExpiryTime;
    private final ReentrantLock tokenLock = new ReentrantLock();

    /**
     * Creates a new FeishuApiClient with default configuration.
     *
     * @param appId          Feishu App ID
     * @param appSecret      Feishu App Secret
     * @param timeoutSeconds Request timeout in seconds
     */
    public FeishuApiClient(String appId, String appSecret, int timeoutSeconds) {
        if (appId == null || appId.isEmpty()) {
            throw new IllegalArgumentException("appId cannot be null or empty");
        }
        if (appSecret == null || appSecret.isEmpty()) {
            throw new IllegalArgumentException("appSecret cannot be null or empty");
        }

        this.appId = appId;
        this.appSecret = appSecret;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();

        logger.info("FeishuApiClient initialized with app_id: {}", appId);
    }

    /**
     * Creates a new FeishuApiClient with default timeout (30 seconds).
     *
     * @param appId     Feishu App ID
     * @param appSecret Feishu App Secret
     */
    public FeishuApiClient(String appId, String appSecret) {
        this(appId, appSecret, 30);
    }

    /**
     * Test constructor with dependency injection (for testing).
     */
    FeishuApiClient(String appId, String appSecret, int timeoutSeconds,
                    HttpClient httpClient, ObjectMapper objectMapper) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    // ==================== Access Token Management ====================

    /**
     * Get tenant access token, with automatic caching and refresh.
     *
     * <p>This method:</p>
     * <ul>
     *   <li>Returns cached token if still valid</li>
     *   <li>Fetches new token if cache is expired</li>
     *   <li>Thread-safe with double-checked locking</li>
     * </ul>
     *
     * @return Valid tenant access token
     * @throws FeishuApiException if token retrieval fails
     */
    public String getAccessToken() throws FeishuApiException {
        // Fast path: check if cached token is still valid
        if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpiryTime) {
            logger.debug("Using cached access token");
            return cachedAccessToken;
        }

        // Slow path: acquire lock and fetch new token
        tokenLock.lock();
        try {
            // Double-check: another thread might have refreshed the token
            if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpiryTime) {
                return cachedAccessToken;
            }

            // Fetch new token
            String newToken = fetchAccessTokenInternal();
            cachedAccessToken = newToken;

            logger.info("Successfully refreshed Feishu access token");

            return cachedAccessToken;

        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * Internal method to fetch access token from Feishu API.
     *
     * @return New access token
     * @throws FeishuApiException if API call fails
     */
    private String fetchAccessTokenInternal() throws FeishuApiException {
        try {
            // Build request payload
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("app_id", appId);
            payload.put("app_secret", appSecret);

            String jsonPayload = objectMapper.writeValueAsString(payload);

            logger.debug("Fetching access token from Feishu");

            // Build HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(AUTH_URL))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            // Execute with retry logic
            HttpResponse<String> response = executeWithRetry(request, "getAccessToken");

            // Parse response
            JsonNode rootNode = objectMapper.readTree(response.body());

            int code = rootNode.path("code").asInt();
            if (code != 0) {
                String errorMsg = rootNode.path("msg").asText("Unknown error");
                throw new FeishuApiException("Failed to get access token: " + errorMsg, code);
            }

            String accessToken = rootNode.path("tenant_access_token").asText();
            int expireSeconds = rootNode.path("expire").asInt(7200); // Default 2 hours

            // Calculate expiry time with buffer
            tokenExpiryTime = System.currentTimeMillis()
                    + (expireSeconds - TOKEN_EXPIRY_BUFFER_SECONDS) * 1000L;

            logger.debug("Access token expires in {} seconds (with buffer)", expireSeconds);

            return accessToken;

        } catch (FeishuApiException e) {
            throw e;
        } catch (Exception e) {
            throw new FeishuApiException("Failed to fetch access token: " + e.getMessage(), e);
        }
    }

    // ==================== Message Sending ====================

    /**
     * Send a message to a Feishu chat.
     *
     * @param receiveId     The target ID (chat_id, open_id, or user_id)
     * @param receiveIdType Type of receive_id: "chat_id", "open_id", or "user_id"
     * @param messageType   Type of message: "text" or "interactive"
     * @param content       Message content (JSON string for card, plain text for text)
     * @return Message ID from Feishu API
     * @throws FeishuApiException if sending fails
     */
    public String sendMessage(String receiveId, String receiveIdType, String messageType, String content)
            throws FeishuApiException {

        if (receiveId == null || receiveId.isEmpty()) {
            throw new IllegalArgumentException("receiveId cannot be null or empty");
        }
        if (receiveIdType == null || receiveIdType.isEmpty()) {
            throw new IllegalArgumentException("receiveIdType cannot be null or empty");
        }
        if (!isValidReceiveIdType(receiveIdType)) {
            throw new IllegalArgumentException("Invalid receiveIdType: " + receiveIdType);
        }
        if (messageType == null || messageType.isEmpty()) {
            throw new IllegalArgumentException("messageType cannot be null or empty");
        }
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("content cannot be null or empty");
        }

        try {
            // Get fresh access token
            String accessToken = getAccessToken();

            // Build request payload
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("receive_id_type", receiveIdType);
            payload.put("receive_id", receiveId);
            payload.put("msg_type", messageType);
            payload.set("content", parseContent(messageType, content));

            String jsonPayload = objectMapper.writeValueAsString(payload);

            logger.info("Sending {} message to {}: {}", messageType, receiveIdType, receiveId);
            logger.debug("Message payload: {}", jsonPayload);

            // Build HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MESSAGE_URL))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            // Execute with retry logic
            HttpResponse<String> response = executeWithRetry(request, "sendMessage");

            // Parse response
            return parseSendMessageResponse(response.body());

        } catch (FeishuApiException e) {
            throw e;
        } catch (Exception e) {
            throw new FeishuApiException("Failed to send message: " + e.getMessage(), e);
        }
    }

    /**
     * Send a text message to a Feishu chat.
     *
     * @param receiveId     The target ID (chat_id, open_id, or user_id)
     * @param receiveIdType Type of receive_id: "chat_id", "open_id", or "user_id"
     * @param text          Plain text content
     * @return Message ID from Feishu API
     * @throws FeishuApiException if sending fails
     */
    public String sendTextMessage(String receiveId, String receiveIdType, String text)
            throws FeishuApiException {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("text cannot be null or empty");
        }
        return sendMessage(receiveId, receiveIdType, "text", text);
    }

    /**
     * Send a card message to a Feishu chat.
     *
     * @param receiveId     The target ID (chat_id, open_id, or user_id)
     * @param receiveIdType Type of receive_id: "chat_id", "open_id", or "user_id"
     * @param cardJson      Card content as JSON string
     * @return Message ID from Feishu API
     * @throws FeishuApiException if sending fails
     */
    public String sendCardMessage(String receiveId, String receiveIdType, String cardJson)
            throws FeishuApiException {
        if (cardJson == null || cardJson.isEmpty()) {
            throw new IllegalArgumentException("cardJson cannot be null or empty");
        }
        return sendMessage(receiveId, receiveIdType, "interactive", cardJson);
    }

    // ==================== Helper Methods ====================

    /**
     * Validate receive_id_type parameter.
     */
    private boolean isValidReceiveIdType(String receiveIdType) {
        return "chat_id".equals(receiveIdType)
                || "open_id".equals(receiveIdType)
                || "user_id".equals(receiveIdType);
    }

    /**
     * Parse content based on message type.
     * For text: wrap in JSON object with "text" key.
     * For interactive: parse as JSON.
     */
    private JsonNode parseContent(String messageType, String content) throws Exception {
        if ("text".equals(messageType)) {
            // Text messages need: {"text": "content"}
            ObjectNode textContent = objectMapper.createObjectNode();
            textContent.put("text", content);
            return textContent;
        } else if ("interactive".equals(messageType)) {
            // Card messages are already JSON
            return objectMapper.readTree(content);
        } else {
            throw new IllegalArgumentException("Unsupported message type: " + messageType);
        }
    }

    /**
     * Parse send message API response and extract message ID.
     */
    private String parseSendMessageResponse(String responseBody) throws FeishuApiException {
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);

            int code = rootNode.path("code").asInt();
            if (code != 0) {
                String errorMsg = rootNode.path("msg").asText("Unknown error");
                throw new FeishuApiException("Failed to send message: " + errorMsg, code);
            }

            String messageId = rootNode.path("data").path("message_id").asText();
            if (messageId.isEmpty()) {
                throw new FeishuApiException("No message_id in response");
            }

            logger.info("Message sent successfully, message_id: {}", messageId);
            return messageId;

        } catch (FeishuApiException e) {
            throw e;
        } catch (Exception e) {
            throw new FeishuApiException("Failed to parse send message response: " + e.getMessage(), e);
        }
    }

    /**
     * Execute HTTP request with retry logic and exponential backoff.
     *
     * @param request    HTTP request to execute
     * @param operation  Operation name for logging
     * @return HTTP response
     * @throws FeishuApiException if all retries fail
     */
    private HttpResponse<String> executeWithRetry(HttpRequest request, String operation)
            throws FeishuApiException {

        Exception lastException = null;
        long retryDelay = INITIAL_RETRY_DELAY_MS;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                int statusCode = response.statusCode();

                // Check for rate limit (429) or server errors (5xx)
                if (statusCode == 429 || (statusCode >= 500 && statusCode < 600)) {
                    String errorBody = response.body();
                    logger.warn("{} failed with status {} on attempt {}/{}. Retrying in {}ms...",
                            operation, statusCode, attempt, MAX_RETRIES, retryDelay);

                    // Sleep before retry (exponential backoff)
                    if (attempt < MAX_RETRIES) {
                        try {
                            Thread.sleep(retryDelay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new FeishuApiException("Retry interrupted", ie);
                        }
                        retryDelay *= 2; // Exponential backoff
                        continue;
                    }
                }

                // Check for client errors (4xx except 429)
                if (statusCode >= 400 && statusCode < 500 && statusCode != 429) {
                    String errorBody = response.body();
                    logger.error("{} failed with client error status {}: {}",
                            operation, statusCode, errorBody);

                    // Parse Feishu error code
                    try {
                        JsonNode errorJson = objectMapper.readTree(errorBody);
                        int code = errorJson.path("code").asInt();
                        String msg = errorJson.path("msg").asText();
                        throw new FeishuApiException(
                                String.format("%s failed: %s (HTTP %d, code %d)",
                                        operation, msg, statusCode, code), code);
                    } catch (Exception e) {
                        throw new FeishuApiException(
                                String.format("%s failed with HTTP %d: %s", operation, statusCode, errorBody),
                                statusCode);
                    }
                }

                // Success (2xx)
                if (statusCode >= 200 && statusCode < 300) {
                    logger.debug("{} succeeded with status {}", operation, statusCode);
                    return response;
                }

                // Unexpected status code
                throw new FeishuApiException(
                        String.format("%s failed with unexpected status %d", operation, statusCode),
                        statusCode);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FeishuApiException(operation + " interrupted", e);
            } catch (FeishuApiException e) {
                // Don't retry Feishu API errors (they're not transient)
                throw e;
            } catch (Exception e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    logger.warn("{} failed on attempt {}/{} with exception: {}. Retrying in {}ms...",
                            operation, attempt, MAX_RETRIES, e.getMessage(), retryDelay);

                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new FeishuApiException("Retry interrupted", ie);
                    }
                    retryDelay *= 2;
                }
            }
        }

        // All retries exhausted
        throw new FeishuApiException(
                String.format("%s failed after %d attempts", operation, MAX_RETRIES),
                lastException);
    }

    // ==================== Getters ====================

    /**
     * Get the app ID used by this client.
     */
    public String getAppId() {
        return appId;
    }

    /**
     * Check if the current access token is still valid.
     *
     * @return true if token is valid and not expired
     */
    public boolean isTokenValid() {
        return cachedAccessToken != null && System.currentTimeMillis() < tokenExpiryTime;
    }

    /**
     * Clear the cached access token (force refresh on next call).
     */
    public void clearTokenCache() {
        tokenLock.lock();
        try {
            cachedAccessToken = null;
            tokenExpiryTime = 0;
            logger.info("Token cache cleared");
        } finally {
            tokenLock.unlock();
        }
    }

    // ==================== Exception Classes ====================

    /**
     * Exception thrown when Feishu API operations fail.
     */
    public static class FeishuApiException extends Exception {
        private final int code;

        public FeishuApiException(String message) {
            this(message, -1);
        }

        public FeishuApiException(String message, int code) {
            super(message);
            this.code = code;
        }

        public FeishuApiException(String message, Throwable cause) {
            this(message, -1, cause);
        }

        public FeishuApiException(String message, int code, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        /**
         * Get the Feishu API error code.
         *
         * @return Error code from Feishu API, or -1 if not available
         */
        public int getCode() {
            return code;
        }

        /**
         * Check if this is a rate limit error (HTTP 429).
         */
        public boolean isRateLimitError() {
            return code == 9999 || code == 429;
        }

        /**
         * Check if this is an authentication error (invalid token).
         */
        public boolean isAuthError() {
            return code == 401 || code == 403;
        }
    }
}
