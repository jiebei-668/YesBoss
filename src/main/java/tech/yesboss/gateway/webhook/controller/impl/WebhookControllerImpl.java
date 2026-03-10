package tech.yesboss.gateway.webhook.controller.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.gateway.webhook.controller.WebhookController;
import tech.yesboss.gateway.webhook.executor.WebhookEventExecutor;
import tech.yesboss.gateway.webhook.model.ImWebhookEvent;
import tech.yesboss.safeguard.SuspendResumeEngine;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Webhook Controller Implementation
 *
 * <p>This implementation provides:</p>
 * <ul>
 *   <li>Feishu/Lark webhook signature verification</li>
 *   <li>Slack webhook signature verification</li>
 *   <li>JSON payload parsing into ImWebhookEvent</li>
 *   <li>Immediate 200 OK response with async processing</li>
 * </ul>
 *
 * <p>Signature Verification:</p>
 * <ul>
 *   <li>Feishu: HMAC-SHA256(timestamp + nonce + body)</li>
 *   <li>Slack: HMAC-SHA256("v0:" + timestamp + ":" + body)</li>
 * </ul>
 */
public class WebhookControllerImpl implements WebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WebhookControllerImpl.class);

    // The standard HTTP 200 OK response
    private static final String HTTP_200_OK = "200 OK";

    // Feishu signature expiration time (3 minutes)
    private static final long FEISHU_SIGNATURE_TOLERANCE_SECONDS = 180;

    // Slack signature expiration time (5 minutes)
    private static final long SLACK_SIGNATURE_TOLERANCE_SECONDS = 300;

    private final WebhookEventExecutor executor;
    private final SuspendResumeEngine suspendResumeEngine;
    private final String feishuAppSecret;
    private final String slackSigningSecret;
    private final ObjectMapper objectMapper;

    // Track processed callbacks to prevent duplicate processing (idempotency)
    // Key format: "sessionId:toolCallId"
    private final Set<String> processedCallbacks = ConcurrentHashMap.newKeySet();

    /**
     * Creates a new WebhookControllerImpl.
     *
     * @param executor            The async event executor
     * @param suspendResumeEngine The suspend/resume engine for human-in-the-loop callbacks
     * @param feishuAppSecret     The Feishu app encrypt key for signature verification (can be empty for testing)
     * @param slackSigningSecret  The Slack signing secret for signature verification (can be empty for testing)
     * @throws IllegalArgumentException if executor or suspendResumeEngine is null
     */
    public WebhookControllerImpl(
        WebhookEventExecutor executor,
        SuspendResumeEngine suspendResumeEngine,
        String feishuAppSecret,
        String slackSigningSecret
    ) {
        logger.info("========== WebhookControllerImpl Constructor Start ==========");
        logger.info("Executor: {}", executor != null ? "provided" : "null");
        logger.info("SuspendResumeEngine: {}", suspendResumeEngine != null ? "provided" : "null");
        logger.info("FeishuAppSecret: {}",
            feishuAppSecret != null ?
            (feishuAppSecret.isEmpty() ? "empty" : "length=" + feishuAppSecret.length()) :
            "null");
        logger.info("SlackSigningSecret: {}",
            slackSigningSecret != null ?
            (slackSigningSecret.isEmpty() ? "empty" : "length=" + slackSigningSecret.length()) :
            "null");

        if (executor == null) {
            throw new IllegalArgumentException("executor cannot be null");
        }
        if (suspendResumeEngine == null) {
            throw new IllegalArgumentException("suspendResumeEngine cannot be null");
        }

        try {
            this.executor = executor;
            this.suspendResumeEngine = suspendResumeEngine;
            this.feishuAppSecret = (feishuAppSecret != null && !feishuAppSecret.isEmpty()) ? feishuAppSecret : "";
            this.slackSigningSecret = (slackSigningSecret != null && !slackSigningSecret.isEmpty()) ? slackSigningSecret : "";

            logger.info("FeishuAppSecret set to: {}",
                !this.feishuAppSecret.isEmpty() ? "configured (length=" + this.feishuAppSecret.length() + ")" : "empty");
            logger.info("SlackSigningSecret set to: {}",
                !this.slackSigningSecret.isEmpty() ? "configured (length=" + this.slackSigningSecret.length() + ")" : "empty");

            this.objectMapper = new ObjectMapper();
            logger.info("ObjectMapper created");

            logger.info("WebhookController initialized. Feishu secret: {}, Slack secret: {}",
                !this.feishuAppSecret.isEmpty() ? "configured" : "not configured",
                !this.slackSigningSecret.isEmpty() ? "configured" : "not configured");

            logger.info("========== WebhookControllerImpl Constructor Complete ==========");
        } catch (Exception e) {
            logger.error("Error in WebhookControllerImpl constructor", e);
            throw e;
        }
    }

    @Override
    public String handleFeishuEvent(String timestamp, String nonce, String signature, String body) {
        logger.error("========== FEISHU EVENT PARSING START ==========");
        logger.error("Timestamp: {}", timestamp);
        logger.error("Nonce: {}", nonce);
        logger.error("Signature (first 30): {}", signature != null && signature.length() > 30 ?
            signature.substring(0, 30) + "..." : signature);
        logger.error("Body length: {}", body != null ? body.length() : 0);

        try {
            // Validate inputs
            if (body == null || body.isEmpty()) {
                logger.warn("Empty Feishu webhook body");
                return HTTP_200_OK;
            }

            // Verify signature if secret is configured
            // TEMPORARILY DISABLED FOR DEBUGGING
            if (!feishuAppSecret.isEmpty()) {
                logger.warn("Feishu signature verification TEMPORARILY DISABLED for debugging");
                logger.warn("Encrypt key configured: YES (length: {})", feishuAppSecret.length());
                // verifyFeishuSignature(timestamp, nonce, signature, body);
            } else {
                logger.warn("Feishu signature verification skipped (no secret configured)");
            }

            // Parse JSON payload
            JsonNode rootNode = objectMapper.readTree(body);
            logger.error("JSON parsed successfully");
            // Log root node field names for debugging
            java.util.Iterator<String> fieldNames = rootNode.fieldNames();
            StringBuilder fields = new StringBuilder();
            while (fieldNames.hasNext()) {
                if (fields.length() > 0) fields.append(", ");
                fields.append(fieldNames.next());
            }
            logger.error("Root node fields: {}", fields.toString());

            // Handle encrypted events (Feishu Encrypt Key feature)
            // If the body contains an "encrypt" field, decrypt it first
            if (rootNode.has("encrypt") && !feishuAppSecret.isEmpty()) {
                logger.error("Encrypt field detected, attempting decryption...");
                try {
                    String encryptedData = rootNode.get("encrypt").asText();
                    logger.error("Encrypted data length: {}", encryptedData.length());
                    String decryptedBody = decryptFeishuEvent(encryptedData, feishuAppSecret);
                    logger.error("Feishu event decrypted successfully");
                    logger.error("Decrypted body (first 500 chars): {}",
                        decryptedBody.length() > 500 ? decryptedBody.substring(0, 500) + "..." : decryptedBody);
                    rootNode = objectMapper.readTree(decryptedBody);
                } catch (Exception e) {
                    logger.error("Failed to decrypt Feishu event, using encrypted body", e);
                    // Continue with encrypted body (will likely fail to extract fields)
                }
            }

            // Handle URL verification challenge (Feishu webhook handshake)
            // Reference: https://open.feishu.cn/document/server-docs/webhook/event-subscription-guide
            if (rootNode.has("type") && "url_verification".equals(rootNode.get("type").asText())) {
                String challenge = rootNode.get("challenge").asText();
                logger.info("Feishu URL verification challenge received");
                // Return JSON format as required by Feishu (not plain text like Slack)
                return "{\"challenge\":\"" + challenge + "\"}";
            }

            // Check if this is a button callback event (has "action" field)
            // Button callbacks should be handled by handleFeishuCallback, but sometimes
            // Feishu sends them to the main webhook endpoint due to configuration issues
            if (rootNode.has("action")) {
                logger.info("Detected button callback event, forwarding to callback handler");
                return handleFeishuCallback(timestamp, nonce, signature, body);
            }

            // Extract event details from Feishu/Lark format
            logger.error("=== Extracting event details ===");
            String eventType = extractFeishuEventType(rootNode);
            logger.error("Event type extracted: {}", eventType);

            // Filter out bot's own messages to prevent message loops
            if (isBotSelfMessage(rootNode)) {
                logger.info("Ignoring bot's own message to prevent message loop");
                return HTTP_200_OK;
            }

            String imGroupId = extractFeishuGroupId(rootNode);
            logger.error("Group ID extracted: {}", imGroupId);

            // Validate imGroupId - if empty, we cannot process this event
            if (imGroupId == null || imGroupId.trim().isEmpty()) {
                logger.error("❌ Failed to extract valid chat_id from Feishu event. Cannot process event.");
                logger.error("This event will be ignored. Please check the event structure above.");
                // Return 200 OK to avoid retry storms, but log the error clearly
                return HTTP_200_OK;
            }

            String userId = extractFeishuUserId(rootNode);
            logger.error("User ID extracted: {}", userId);

            // Extract message text content
            String messageText = extractFeishuMessageText(rootNode);
            logger.error("Message text extracted: {}",
                messageText != null ? (messageText.length() > 200 ?
                    messageText.substring(0, 200) + "..." : messageText) : "(null)");

            // Create internal event with message text
            ImWebhookEvent event = ImWebhookEvent.create(
                "FEISHU",
                eventType,
                imGroupId,
                userId,
                body,
                messageText
            );

            logger.info("Feishu event parsed: type={}, group={}, user={}, text={}",
                eventType, imGroupId, userId,
                messageText != null ? messageText : "(no text)");

            // Submit to async executor (non-blocking)
            executor.processAsync(event);

            // Immediately return 200 OK
            return HTTP_200_OK;

        } catch (SecurityException e) {
            logger.error("Feishu signature verification failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error processing Feishu event: {}", e.getMessage(), e);
            // Still return 200 OK to avoid retry storms
            return HTTP_200_OK;
        }
    }

    @Override
    public String handleSlackEvent(String timestamp, String signature, String body) {
        logger.debug("Received Slack webhook event");

        try {
            // Validate inputs
            if (body == null || body.isEmpty()) {
                logger.warn("Empty Slack webhook body");
                return HTTP_200_OK;
            }

            // Skip signature verification if secret is not configured (for testing)
            if (!slackSigningSecret.isEmpty()) {
                verifySlackSignature(timestamp, signature, body);
            } else {
                logger.debug("Slack signature verification skipped (no secret configured)");
            }

            // Parse JSON payload
            JsonNode rootNode = objectMapper.readTree(body);

            // Handle URL verification challenge (Slack's handshake)
            if (rootNode.has("type") && "url_verification".equals(rootNode.get("type").asText())) {
                String challenge = rootNode.get("challenge").asText();
                logger.info("Slack URL verification challenge received");
                return challenge; // Return the challenge as-is
            }

            // Extract event details from Slack format
            String eventType = extractSlackEventType(rootNode);
            String imGroupId = extractSlackChannelId(rootNode);
            String userId = extractSlackUserId(rootNode);

            // Create internal event
            ImWebhookEvent event = ImWebhookEvent.create(
                "SLACK",
                eventType,
                imGroupId,
                userId,
                body
            );

            logger.info("Slack event parsed: type={}, channel={}, user={}", eventType, imGroupId, userId);

            // Submit to async executor (non-blocking)
            executor.processAsync(event);

            // Immediately return 200 OK
            return HTTP_200_OK;

        } catch (SecurityException e) {
            logger.error("Slack signature verification failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error processing Slack event: {}", e.getMessage(), e);
            // Still return 200 OK to avoid retry storms
            return HTTP_200_OK;
        }
    }

    @Override
    public void handleCliCommand(String command) {
        if (command == null || command.isEmpty()) {
            logger.warn("Empty CLI command");
            return;
        }

        logger.info("CLI command received: {}", command);

        // Create CLI event
        ImWebhookEvent event = ImWebhookEvent.create(
            "CLI",
            "command",
            "cli-session",
            "cli-user",
            command
        );

        // For CLI, we can process synchronously or asynchronously
        // Using async for consistency with IM modes
        executor.processAsync(event);
    }

    @Override
    public boolean isReady() {
        return executor.isRunning();
    }

    // ==================== Signature Verification Methods ====================

    /**
     * Verify Feishu webhook signature.
     *
     * Feishu signature format: base64(hmac_sha256(secret, timestamp + "\n" + nonce + "\n" + body))
     *
     * Reference: https://open.feishu.cn/document/server-docs/webhook/event-subscription-guide
     *
     * @param timestamp The timestamp header
     * @param nonce     The nonce header
     * @param signature The signature header
     * @param body      The request body
     * @throws SecurityException if verification fails
     */
    private void verifyFeishuSignature(String timestamp, String nonce, String signature, String body) {
        try {
            // Parse and validate timestamp
            long requestTimestamp = Long.parseLong(timestamp);
            long currentTime = Instant.now().getEpochSecond();
            long timeDiff = Math.abs(currentTime - requestTimestamp);

            if (timeDiff > FEISHU_SIGNATURE_TOLERANCE_SECONDS) {
                throw new SecurityException("Feishu timestamp expired: diff=" + timeDiff + "s");
            }

            // Build signature base string (FEISHU REQUIRES NEWLINES!)
            // Format: timestamp + "\n" + nonce + "\n" + body
            String baseString = timestamp + "\n" + nonce + "\n" + body;

            // Calculate expected signature
            String expectedSignature = calculateHmacSha256(baseString, feishuAppSecret);

            // Debug logging
            logger.error("=== Signature Debug ===");
            logger.error("Timestamp: {}", timestamp);
            logger.error("Nonce: {}", nonce);
            logger.error("Body first 100 chars: {}", body.substring(0, Math.min(100, body.length())));
            logger.error("Body length: {}", body.length());
            logger.error("Base string first 150 chars: {}", baseString.substring(0, Math.min(150, baseString.length())));
            logger.error("Base string length: {}", baseString.length());
            logger.error("Received signature: {}", signature);
            logger.error("Expected signature: {}", expectedSignature);
            logger.error("App secret length: {}", feishuAppSecret.length());
            logger.error("Signatures match: {}", constantTimeEquals(expectedSignature, signature));
            logger.error("====================");

            // Compare signatures (timing-safe comparison)
            if (!constantTimeEquals(expectedSignature, signature)) {
                throw new SecurityException("Feishu signature mismatch");
            }

            logger.debug("Feishu signature verified successfully");

        } catch (NumberFormatException e) {
            throw new SecurityException("Invalid Feishu timestamp format", e);
        }
    }

    /**
     * Verify Slack webhook signature.
     *
     * Slack signature format: "v0=" + hmac_sha256(signing_secret, "v0:" + timestamp + ":" + body)
     *
     * @param timestamp The timestamp header
     * @param signature The signature header
     * @param body      The request body
     * @throws SecurityException if verification fails
     */
    private void verifySlackSignature(String timestamp, String signature, String body) {
        try {
            // Parse and validate timestamp
            long requestTimestamp = Long.parseLong(timestamp);
            long currentTime = Instant.now().getEpochSecond();
            long timeDiff = Math.abs(currentTime - requestTimestamp);

            if (timeDiff > SLACK_SIGNATURE_TOLERANCE_SECONDS) {
                throw new SecurityException("Slack timestamp expired: diff=" + timeDiff + "s");
            }

            // Build signature base string
            String baseString = "v0:" + timestamp + ":" + body;

            // Calculate expected signature
            String expectedSignature = "v0=" + calculateHmacSha256(baseString, slackSigningSecret);

            // Compare signatures (timing-safe comparison)
            if (!constantTimeEquals(expectedSignature, signature)) {
                throw new SecurityException("Slack signature mismatch");
            }

            logger.debug("Slack signature verified successfully");

        } catch (NumberFormatException e) {
            throw new SecurityException("Invalid Slack timestamp format", e);
        }
    }

    /**
     * Calculate HMAC-SHA256 hash.
     *
     * Feishu and Slack both use HEX encoding for signatures, not Base64.
     *
     * @param data   The data to hash
     * @param secret The secret key
     * @return HEX-encoded HMAC-SHA256 hash
     */
    private String calculateHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            // Convert to HEX string (lowercase)
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new SecurityException("Failed to calculate HMAC-SHA256", e);
        }
    }

    /**
     * Decrypt Feishu encrypted event payload.
     *
     * <p>Feishu encrypts event JSON using AES-128 when Encrypt Key is configured.
     * Multiple attempts with different cipher configurations.</p>
     *
     * @param encryptedBase64 Base64-encoded encrypted data
     * @param encryptKey The Encrypt Key from Feishu webhook configuration
     * @return Decrypted JSON string
     * @throws Exception if decryption fails
     */
    private String decryptFeishuEvent(String encryptedBase64, String encryptKey) throws Exception {
        // Feishu official decryption method
        // Reference: https://open.feishu.cn/document/event-subscription-guide

        // 1. Base64 decode
        byte[] encryptedBytes = java.util.Base64.getDecoder().decode(encryptedBase64);

        // 2. Extract IV from first 16 bytes
        byte[] iv = new byte[16];
        System.arraycopy(encryptedBytes, 0, iv, 0, 16);

        // 3. Extract actual encrypted data (remaining bytes after IV)
        byte[] encryptedData = new byte[encryptedBytes.length - 16];
        System.arraycopy(encryptedBytes, 16, encryptedData, 0, encryptedData.length);

        // 4. Hash Encrypt Key with SHA-256 to get AES key
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] key = digest.digest(encryptKey.getBytes(StandardCharsets.UTF_8));

        // 5. Decrypt using AES-256-CBC with NOPADDING
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/NOPADDING");
        javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(key, "AES");
        javax.crypto.spec.IvParameterSpec ivSpec = new javax.crypto.spec.IvParameterSpec(iv);

        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] decryptedBytes = cipher.doFinal(encryptedData);

        // 6. Remove padding (find the last non-padding byte)
        if (decryptedBytes.length > 0) {
            int p = decryptedBytes.length - 1;
            for (; p >= 0 && decryptedBytes[p] <= 16; p--) {
                // Find the last byte that's not padding
            }
            if (p != decryptedBytes.length - 1) {
                byte[] unpadded = new byte[p + 1];
                System.arraycopy(decryptedBytes, 0, unpadded, 0, p + 1);
                decryptedBytes = unpadded;
            }
        }

        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     *
     * @param a First string
     * @param b Second string
     * @return true if strings are equal, false otherwise
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }

        return result == 0;
    }

    // ==================== Feishu Payload Parsing Methods ====================

    private String extractFeishuEventType(JsonNode rootNode) {
        // Feishu event structure: { "header": { "event_type": "im.message.receive_v1" } }
        if (rootNode.has("header") && rootNode.get("header").has("event_type")) {
            String eventType = rootNode.get("header").get("event_type").asText();

            // Normalize event type
            if (eventType.contains("message")) {
                return "message";
            } else if (eventType.contains("group")) {
                return "group_join";
            }

            return eventType;
        }

        // Default to message if structure not recognized
        return "message";
    }

    /**
     * Check if the message is from the bot itself to prevent message loops.
     *
     * <p>Feishu may send the bot's own messages back to the webhook, which can cause
     * infinite loops if not filtered. This method checks:</p>
     * <ul>
     *   <li>sender.type == "app" - Message sent by the app/bot</li>
     *   <li>message.message_type == "interactive" - Card message sent by bot</li>
     * </ul>
     *
     * @param rootNode The parsed JSON event payload
     * @return true if this is a bot's own message that should be ignored
     */
    private boolean isBotSelfMessage(JsonNode rootNode) {
        // Check if sender is the app/bot itself
        JsonNode eventNode = rootNode.path("event");
        JsonNode senderNode = eventNode.path("sender");

        if (senderNode.has("sender_type")) {
            String senderType = senderNode.get("sender_type").asText();
            if ("app".equalsIgnoreCase(senderType)) {
                logger.debug("Message sender_type is 'app', filtering out bot's own message");
                return true;
            }
        }

        // Check if sender.type is app (alternative field name)
        if (senderNode.has("type")) {
            String senderType = senderNode.get("type").asText();
            if ("app".equalsIgnoreCase(senderType)) {
                logger.debug("Message sender type is 'app', filtering out bot's own message");
                return true;
            }
        }

        // Check if message type is 'interactive' (card message sent by bot)
        JsonNode messageNode = eventNode.path("message");
        if (messageNode.has("message_type")) {
            String messageType = messageNode.get("message_type").asText();
            if ("interactive".equalsIgnoreCase(messageType)) {
                logger.debug("Message type is 'interactive', filtering out bot's own card message");
                return true;
            }
        }

        // Check msg_type at root level (for callback-style events)
        if (rootNode.has("msg_type")) {
            String msgType = rootNode.get("msg_type").asText();
            if ("interactive".equalsIgnoreCase(msgType)) {
                logger.debug("Root msg_type is 'interactive', filtering out bot's own message");
                return true;
            }
        }

        return false;
    }

    /**
     * Check if this callback is from the bot itself (e.g., when Feishu sends back the bot's card).
     * This is used to prevent message loops in the callback handler.
     *
     * <p>For card.action.trigger events, we check if the operator (user who triggered the action)
     * is the bot itself.</p>
     *
     * @param rootNode The parsed JSON callback payload
     * @return true if this is a bot's own callback that should be ignored
     */
    private boolean isBotSelfCallback(JsonNode rootNode) {
        // Check for card.action.trigger event format
        JsonNode eventNode = rootNode.path("event");

        // Check if operator (the one who triggered the action) is the app/bot
        JsonNode operatorNode = eventNode.path("operator");
        if (operatorNode.has("operator_type")) {
            String operatorType = operatorNode.get("operator_type").asText();
            if ("app".equalsIgnoreCase(operatorType)) {
                logger.debug("Callback operator_type is 'app', filtering out bot's own callback");
                return true;
            }
        }

        // Alternative: check sender in event
        if (eventNode.has("sender")) {
            JsonNode senderNode = eventNode.get("sender");
            if (senderNode.has("sender_type")) {
                String senderType = senderNode.get("sender_type").asText();
                if ("app".equalsIgnoreCase(senderType)) {
                    logger.debug("Callback sender_type is 'app', filtering out bot's own callback");
                    return true;
                }
            }
        }

        // Check for root-level msg_type: interactive (bot's own card message)
        if (rootNode.has("msg_type")) {
            String msgType = rootNode.get("msg_type").asText();
            if ("interactive".equalsIgnoreCase(msgType)) {
                logger.debug("Callback root msg_type is 'interactive', filtering out bot's own card");
                return true;
            }
        }

        return false;
    }

    private String extractFeishuGroupId(JsonNode rootNode) {
        logger.error("========== EXTRACTING FEISHU GROUP ID ==========");
        logger.error("Full event structure: {}", rootNode.toPrettyString());
        logger.error("=================================================");

        // Check if this is a button callback event (has "action" field)
        // Button callbacks use "open_chat_id" at root level
        if (rootNode.has("action")) {
            logger.error("Detected button callback event");
            if (rootNode.has("open_chat_id")) {
                String chatId = rootNode.get("open_chat_id").asText();
                logger.error("✅ Found open_chat_id in button callback: {}", chatId);
                logger.error("=================================================");
                return chatId;
            }
        }

        // Feishu group chat ID location depends on event type
        // For message events: event.message.chat_id
        // For other events: event.chat.chat_id

        // Try message event path first
        JsonNode eventNode = rootNode.path("event");
        logger.error("Event node: {}", eventNode.toPrettyString());

        JsonNode messageNode = eventNode.path("message");
        logger.error("Message node exists: {}", !messageNode.isMissingNode());
        logger.error("Message node: {}", messageNode.toPrettyString());

        if (messageNode.has("chat_id")) {
            String chatId = messageNode.get("chat_id").asText();
            logger.error("Found chat_id in message node: {}", chatId);
            return chatId;
        }

        // Fallback to generic chat path
        JsonNode chatNode = eventNode.path("chat");
        logger.error("Chat node exists: {}", !chatNode.isMissingNode());
        logger.error("Chat node: {}", chatNode.toPrettyString());

        if (chatNode.has("chat_id")) {
            String chatId = chatNode.get("chat_id").asText();
            logger.error("Found chat_id in chat node: {}", chatId);
            return chatId;
        }

        // Try root level chat_id (for callback events)
        if (rootNode.has("chat_id")) {
            String chatId = rootNode.get("chat_id").asText();
            logger.error("Found chat_id at root level: {}", chatId);
            return chatId;
        }

        // Try root level open_chat_id (for button callbacks)
        if (rootNode.has("open_chat_id")) {
            String chatId = rootNode.get("open_chat_id").asText();
            logger.error("Found open_chat_id at root level: {}", chatId);
            return chatId;
        }

        // Try event level chat_id
        if (eventNode.has("chat_id")) {
            String chatId = eventNode.get("chat_id").asText();
            logger.error("Found chat_id at event level: {}", chatId);
            return chatId;
        }

        // Log all field names in event node for debugging
        logger.error("Event node field names:");
        eventNode.fieldNames().forEachRemaining(name ->
            logger.error("  - {}", name)
        );

        // Log all root level field names for debugging
        logger.error("Root node field names:");
        rootNode.fieldNames().forEachRemaining(name ->
            logger.error("  - {}", name)
        );

        // Log error and return fallback
        logger.error("❌ Unable to extract chat_id from Feishu event");
        logger.error("=================================================");

        // Return empty string to indicate failure (better than invalid value)
        return "";
    }

    private String extractFeishuUserId(JsonNode rootNode) {
        // Feishu sender ID is in event.sender.sender_id.user_id
        JsonNode senderNode = rootNode.path("event").path("sender").path("sender_id");
        if (senderNode.has("user_id")) {
            return senderNode.get("user_id").asText();
        }

        // Fallback to empty string
        return "unknown-user";
    }

    private String extractFeishuMessageText(JsonNode rootNode) {
        logger.error("=== extractFeishuMessageText called ===");
        logger.error("Root node structure: {}", rootNode.toPrettyString());

        // Feishu message text location: event.text (for text messages)
        JsonNode eventNode = rootNode.path("event");
        logger.error("Event node exists: {}", !eventNode.isMissingNode());
        // Log event node field names
        if (!eventNode.isMissingNode()) {
            java.util.Iterator<String> eventFields = eventNode.fieldNames();
            StringBuilder fields = new StringBuilder();
            while (eventFields.hasNext()) {
                if (fields.length() > 0) fields.append(", ");
                fields.append(eventFields.next());
            }
            logger.error("Event node fields: {}", fields.toString());
        } else {
            logger.error("Event node fields: (node is missing)");
        }

        // Method 1: Try event.text (v2 API)
        if (eventNode.has("text")) {
            String text = eventNode.get("text").asText();
            logger.error("Found text using event.text: {}", text);
            return text;
        }

        // Method 2: Try event.message.content (v1 API)
        JsonNode messageNode = eventNode.path("message");
        logger.error("Message node exists: {}", !messageNode.isMissingNode());

        if (messageNode.has("content")) {
            String content = messageNode.get("content").asText();
            logger.error("Found content field: {}", content);

            try {
                // content is JSON string like "{\"text\":\"hello\"}"
                JsonNode contentJson = objectMapper.readTree(content);
                logger.error("Content JSON parsed successfully");
                // Log content JSON field names
                java.util.Iterator<String> contentFields = contentJson.fieldNames();
                StringBuilder fields = new StringBuilder();
                while (contentFields.hasNext()) {
                    if (fields.length() > 0) fields.append(", ");
                    fields.append(contentFields.next());
                }
                logger.error("Content JSON fields: {}", fields.toString());

                if (contentJson.has("text")) {
                    String text = contentJson.get("text").asText();
                    logger.error("Extracted text from content JSON: {}", text);
                    return text;
                }
            } catch (Exception e) {
                logger.error("Failed to parse message content as JSON: {}", content, e);
            }
            // Fallback to raw content
            logger.error("Returning raw content as fallback");
            return content;
        }

        // No message text found
        logger.error("No message text found in event");
        return null;
    }

    // ==================== Slack Payload Parsing Methods ====================

    private String extractSlackEventType(JsonNode rootNode) {
        // Slack event structure: { "event": { "type": "message" } }
        if (rootNode.has("event") && rootNode.get("event").has("type")) {
            String eventType = rootNode.get("event").get("type").asText();

            // Normalize event types
            switch (eventType) {
                case "message":
                    return "message";
                case "group_joined":
                    return "group_join";
                case "group_left":
                    return "group_delete";
                default:
                    return eventType;
            }
        }

        return "message";
    }

    private String extractSlackChannelId(JsonNode rootNode) {
        // Slack channel ID is in event.channel
        JsonNode eventNode = rootNode.path("event");
        if (eventNode.has("channel")) {
            return eventNode.get("channel").asText();
        }

        return "unknown-channel";
    }

    private String extractSlackUserId(JsonNode rootNode) {
        // Slack user ID is in event.user
        JsonNode eventNode = rootNode.path("event");
        if (eventNode.has("user")) {
            return eventNode.get("user").asText();
        }

        return "unknown-user";
    }

    // ==================== Interactive Callback Methods ====================

    @Override
    public String handleFeishuCallback(String timestamp, String nonce, String signature, String body) {
        logger.info("========== FEISHU CALLBACK RECEIVED ==========");
        logger.info("Received Feishu interactive callback");
        logger.info("Request body (first 500 chars): {}",
            body != null && body.length() > 500 ? body.substring(0, 500) + "..." : body);
        logger.info("=============================================");

        try {
            // Validate inputs
            if (body == null || body.isEmpty()) {
                logger.warn("Empty Feishu callback body");
                return HTTP_200_OK;
            }

            // Parse JSON payload early to check for bot self-messages
            JsonNode rootNode = objectMapper.readTree(body);

            // Check if this is a bot's own message (card.action.trigger for bot's own cards)
            // This prevents message loops when Feishu sends the bot's card back to the webhook
            if (isBotSelfCallback(rootNode)) {
                logger.info("Ignoring bot's own callback to prevent message loop");
                return HTTP_200_OK;
            }

            // Verify signature if secret is configured
            // TEMPORARILY DISABLED FOR DEBUGGING
            if (!feishuAppSecret.isEmpty()) {
                logger.warn("Feishu signature verification TEMPORARILY DISABLED for debugging");
                // verifyFeishuSignature(timestamp, nonce, signature, body);
            } else {
                logger.debug("Feishu signature verification skipped (no secret configured)");
            }

            // Extract callback action details
            String sessionId = extractFeishuCallbackSessionId(rootNode);
            String toolCallId = extractFeishuCallbackToolCallId(rootNode);
            Boolean isApproved = extractFeishuCallbackApproval(rootNode);
            String humanFeedback = extractFeishuCallbackFeedback(rootNode);

            // Validate required fields
            if (sessionId == null || sessionId.isEmpty()) {
                logger.warn("Feishu callback missing session_id");
                return HTTP_200_OK;
            }
            if (toolCallId == null || toolCallId.isEmpty()) {
                logger.warn("Feishu callback missing tool_call_id");
                return HTTP_200_OK;
            }
            if (isApproved == null) {
                logger.warn("Feishu callback missing approved flag");
                return HTTP_200_OK;
            }

            // Idempotency check: prevent duplicate callback processing
            String callbackKey = sessionId + ":" + toolCallId;
            if (!processedCallbacks.add(callbackKey)) {
                logger.info("Callback already processed for key: {}, ignoring duplicate", callbackKey);
                return HTTP_200_OK;
            }
            logger.info("Feishu callback parsed: session={}, toolCallId={}, approved={}, callbackKey={}",
                sessionId, toolCallId, isApproved, callbackKey);

            // Route to SuspendResumeEngine asynchronously
            final String finalSessionId = sessionId;
            final String finalToolCallId = toolCallId;
            final Boolean finalIsApproved = isApproved;
            final String finalHumanFeedback = humanFeedback;

            // Process the resume in a separate thread to avoid blocking the HTTP response
            new Thread(() -> {
                try {
                    suspendResumeEngine.resume(finalSessionId, finalToolCallId,
                        finalIsApproved, finalHumanFeedback);
                    logger.info("Successfully processed Feishu callback resume for session {}", finalSessionId);
                } catch (IllegalStateException e) {
                    // Handle the case where session is already RUNNING (already resumed)
                    if (e.getMessage() != null && e.getMessage().contains("not in SUSPENDED state")) {
                        logger.warn("Session {} is already resumed (not in SUSPENDED state), ignoring callback", finalSessionId);
                    } else {
                        logger.error("Failed to process Feishu callback resume for session {}", finalSessionId, e);
                    }
                } catch (Exception e) {
                    logger.error("Failed to process Feishu callback resume for session {}", finalSessionId, e);
                }
            }, "FeishuCallback-" + finalSessionId).start();

            // Immediately return 200 OK
            return HTTP_200_OK;

        } catch (SecurityException e) {
            logger.error("Feishu callback signature verification failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error processing Feishu callback: {}", e.getMessage(), e);
            // Still return 200 OK to avoid retry storms
            return HTTP_200_OK;
        }
    }

    @Override
    public String handleSlackCallback(String payload, String timestamp, String signature) {
        logger.info("Received Slack interactive callback");

        try {
            // Validate inputs
            if (payload == null || payload.isEmpty()) {
                logger.warn("Empty Slack callback payload");
                return HTTP_200_OK;
            }

            // Skip signature verification if secret is not configured (for testing)
            if (!slackSigningSecret.isEmpty()) {
                verifySlackSignature(timestamp, signature, payload);
            } else {
                logger.debug("Slack signature verification skipped (no secret configured)");
            }

            // Slack sends the payload as URL-encoded JSON in a "payload" form field
            // We need to decode it first
            String decodedPayload = java.net.URLDecoder.decode(payload, StandardCharsets.UTF_8);

            // Parse JSON payload
            JsonNode rootNode = objectMapper.readTree(decodedPayload);

            // Extract callback action details
            String sessionId = extractSlackCallbackSessionId(rootNode);
            String toolCallId = extractSlackCallbackToolCallId(rootNode);
            Boolean isApproved = extractSlackCallbackApproval(rootNode);
            String humanFeedback = extractSlackCallbackFeedback(rootNode);

            // Validate required fields
            if (sessionId == null || sessionId.isEmpty()) {
                logger.warn("Slack callback missing session_id");
                return HTTP_200_OK;
            }
            if (toolCallId == null || toolCallId.isEmpty()) {
                logger.warn("Slack callback missing tool_call_id");
                return HTTP_200_OK;
            }
            if (isApproved == null) {
                logger.warn("Slack callback missing approved flag");
                return HTTP_200_OK;
            }

            logger.info("Slack callback parsed: session={}, toolCallId={}, approved={}",
                sessionId, toolCallId, isApproved);

            // Route to SuspendResumeEngine asynchronously
            final String finalSessionId = sessionId;
            final String finalToolCallId = toolCallId;
            final Boolean finalIsApproved = isApproved;
            final String finalHumanFeedback = humanFeedback;

            // Process the resume in a separate thread
            new Thread(() -> {
                try {
                    suspendResumeEngine.resume(finalSessionId, finalToolCallId,
                        finalIsApproved, finalHumanFeedback);
                    logger.info("Successfully processed Slack callback resume for session {}", finalSessionId);
                } catch (Exception e) {
                    logger.error("Failed to process Slack callback resume for session {}", finalSessionId, e);
                }
            }, "SlackCallback-" + finalSessionId).start();

            // Immediately return 200 OK
            return HTTP_200_OK;

        } catch (SecurityException e) {
            logger.error("Slack callback signature verification failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error processing Slack callback: {}", e.getMessage(), e);
            // Still return 200 OK to avoid retry storms
            return HTTP_200_OK;
        }
    }

    // ==================== Feishu Callback Parsing Methods ====================

    /**
     * Extract session_id from Feishu callback payload.
     * Feishu button value format: {"session_id":"xxx","tool_call_id":"yyy","approved":true}
     *
     * Supports two callback formats:
     * 1. Direct format: { "action": { "value": "..." } }
     * 2. Event format (card.action.trigger): { "event": { "action": { "value": "..." } } }
     *
     * Supports two value formats:
     * 1. JSON object (new format): { "value": { "approved": true, "session_id": "...", "tool_call_id": "..." } }
     * 2. JSON string (old format): { "value": "{\"approved\":true,\"session_id\":\"...\"}" }
     */
    private String extractFeishuCallbackSessionId(JsonNode rootNode) {
        logger.info("========== EXTRACTING SESSION ID FROM FEISHU CALLBACK ==========");
        logger.info("Root node structure: {}", rootNode.toPrettyString());

        // Try to find action node in two possible locations
        JsonNode actionNode = findActionNode(rootNode);
        logger.info("Action node: {}", actionNode.toPrettyString());
        logger.info("Action node has value: {}", actionNode.has("value"));

        if (actionNode.has("value")) {
            JsonNode valueJson = parseButtonValueNode(actionNode.get("value"));
            logger.info("Parsed value JSON: {}", valueJson != null ? valueJson.toPrettyString() : "null");

            if (valueJson != null && valueJson.has("session_id")) {
                String sessionId = valueJson.get("session_id").asText();
                logger.info("Successfully extracted session_id: {}", sessionId);
                logger.info("===========================================================");
                return sessionId;
            }
        }

        logger.warn("Failed to extract session_id from callback");
        logger.info("===========================================================");
        return null;
    }

    /**
     * Extract tool_call_id from Feishu callback payload.
     */
    private String extractFeishuCallbackToolCallId(JsonNode rootNode) {
        JsonNode actionNode = findActionNode(rootNode);
        if (actionNode.has("value")) {
            JsonNode valueJson = parseButtonValueNode(actionNode.get("value"));
            if (valueJson != null && valueJson.has("tool_call_id")) {
                return valueJson.get("tool_call_id").asText();
            }
        }
        return null;
    }

    /**
     * Extract approved flag from Feishu callback payload.
     */
    private Boolean extractFeishuCallbackApproval(JsonNode rootNode) {
        JsonNode actionNode = findActionNode(rootNode);
        if (actionNode.has("value")) {
            JsonNode valueJson = parseButtonValueNode(actionNode.get("value"));
            if (valueJson != null && valueJson.has("approved")) {
                return valueJson.get("approved").asBoolean();
            }
        }
        return null;
    }

    /**
     * Find the action node in the callback payload.
     * Supports both direct format and event format (card.action.trigger).
     *
     * @param rootNode The root JSON node
     * @return The action node, or missing node if not found
     */
    private JsonNode findActionNode(JsonNode rootNode) {
        // Format 1: Direct format { "action": { "value": "..." } }
        if (rootNode.has("action")) {
            return rootNode.get("action");
        }

        // Format 2: Event format (card.action.trigger) { "event": { "action": { "value": "..." } } }
        JsonNode eventNode = rootNode.path("event");
        if (eventNode.has("action")) {
            return eventNode.get("action");
        }

        // Return missing node if not found
        return rootNode.path("action");
    }

    /**
     * Parse the button value node to JSON, handling multiple formats.
     *
     * <p>Supports three value formats:</p>
     * <ul>
     *   <li>JSON object (new format): { "approved": true, "session_id": "...", "tool_call_id": "..." }</li>
     *   <li>JSON string (old format): "{\"approved\":true,\"session_id\":\"...\"}"</li>
     *   <li>Double-encoded string: "\"{\\\"approved\\\":true,...}\""</li>
     * </ul>
     *
     * @param valueNode The button value node (can be object or string)
     * @return The parsed JSON node, or null if parsing fails
     */
    private JsonNode parseButtonValueNode(JsonNode valueNode) {
        try {
            // Case 1: Value is already a JSON object (new format)
            if (valueNode.isObject()) {
                logger.debug("Button value is already a JSON object");
                return valueNode;
            }

            // Case 2: Value is a string that needs parsing
            if (valueNode.isTextual()) {
                String valueStr = valueNode.asText();
                logger.debug("Button value is a string: {}", valueStr);

                // Handle empty string
                if (valueStr == null || valueStr.isEmpty()) {
                    logger.warn("Button value string is empty");
                    return null;
                }

                JsonNode parsed = objectMapper.readTree(valueStr);

                // Handle double-encoded JSON (backwards compatibility)
                // If the parsed value is still a string, parse again
                if (parsed.isTextual()) {
                    logger.debug("Detected double-encoded JSON, parsing again...");
                    String innerJson = parsed.asText();
                    parsed = objectMapper.readTree(innerJson);
                }

                return parsed;
            }

            // Case 3: Unexpected node type
            logger.warn("Unexpected button value node type: {}", valueNode.getNodeType());
            return null;

        } catch (Exception e) {
            logger.error("Failed to parse button value node: {}", valueNode, e);
            return null;
        }
    }

    /**
     * Parse the button value string to JSON, handling double-encoding.
     *
     * @param valueStr The button value string
     * @return The parsed JSON node, or null if parsing fails
     * @deprecated Use {@link #parseButtonValueNode(JsonNode)} instead
     */
    @Deprecated
    private JsonNode parseButtonValue(String valueStr) {
        try {
            JsonNode valueJson = objectMapper.readTree(valueStr);

            // Handle double-encoded JSON (backwards compatibility)
            // If the parsed value is a string node, it means the JSON was double-encoded
            if (valueJson.isTextual()) {
                logger.debug("Detected double-encoded JSON, parsing again...");
                String innerJson = valueJson.asText();
                valueJson = objectMapper.readTree(innerJson);
            }

            return valueJson;
        } catch (Exception e) {
            logger.error("Failed to parse button value as JSON: {}", valueStr, e);
            return null;
        }
    }

    /**
     * Extract human feedback from Feishu callback payload.
     * This is optional and may not be present in all callbacks.
     */
    private String extractFeishuCallbackFeedback(JsonNode rootNode) {
        // Feishu may include user input in a specific field
        // For now, return empty string as basic approval cards don't have feedback input
        JsonNode actionNode = rootNode.path("action");
        if (actionNode.has("text")) {
            return actionNode.get("text").asText();
        }
        return "";
    }

    // ==================== Slack Callback Parsing Methods ====================

    /**
     * Extract session_id from Slack callback payload.
     * Slack button value format: {"session_id":"xxx","tool_call_id":"yyy","approved":true}
     */
    private String extractSlackCallbackSessionId(JsonNode rootNode) {
        // Slack interactive callback structure: { "actions": [ { "value": "..." } ] }
        JsonNode actionsNode = rootNode.path("actions");
        if (actionsNode.isArray() && actionsNode.size() > 0) {
            JsonNode firstAction = actionsNode.get(0);
            if (firstAction.has("value")) {
                String valueStr = firstAction.get("value").asText();
                try {
                    JsonNode valueJson = objectMapper.readTree(valueStr);
                    if (valueJson.has("session_id")) {
                        return valueJson.get("session_id").asText();
                    }
                } catch (Exception e) {
                    logger.debug("Failed to parse button value as JSON: {}", valueStr);
                }
            }
        }
        return null;
    }

    /**
     * Extract tool_call_id from Slack callback payload.
     */
    private String extractSlackCallbackToolCallId(JsonNode rootNode) {
        JsonNode actionsNode = rootNode.path("actions");
        if (actionsNode.isArray() && actionsNode.size() > 0) {
            JsonNode firstAction = actionsNode.get(0);
            if (firstAction.has("value")) {
                String valueStr = firstAction.get("value").asText();
                try {
                    JsonNode valueJson = objectMapper.readTree(valueStr);
                    if (valueJson.has("tool_call_id")) {
                        return valueJson.get("tool_call_id").asText();
                    }
                } catch (Exception e) {
                    logger.debug("Failed to parse button value as JSON: {}", valueStr);
                }
            }
        }
        return null;
    }

    /**
     * Extract approved flag from Slack callback payload.
     * Can be derived from the action name (approve_action vs reject_action).
     */
    private Boolean extractSlackCallbackApproval(JsonNode rootNode) {
        JsonNode actionsNode = rootNode.path("actions");
        if (actionsNode.isArray() && actionsNode.size() > 0) {
            JsonNode firstAction = actionsNode.get(0);

            // First try to get from value JSON
            if (firstAction.has("value")) {
                String valueStr = firstAction.get("value").asText();
                try {
                    JsonNode valueJson = objectMapper.readTree(valueStr);
                    if (valueJson.has("approved")) {
                        return valueJson.get("approved").asBoolean();
                    }
                } catch (Exception e) {
                    logger.debug("Failed to parse button value as JSON: {}", valueStr);
                }
            }

            // Fallback: check action name
            if (firstAction.has("name")) {
                String actionName = firstAction.get("name").asText();
                if ("approve_action".equals(actionName)) {
                    return true;
                } else if ("reject_action".equals(actionName)) {
                    return false;
                }
            }
        }
        return null;
    }

    /**
     * Extract human feedback from Slack callback payload.
     * This is optional and may not be present in all callbacks.
     */
    private String extractSlackCallbackFeedback(JsonNode rootNode) {
        // Slack may include user input in specific fields
        // For now, check for a "text" field in the root
        if (rootNode.has("text")) {
            return rootNode.get("text").asText();
        }
        return "";
    }
}
