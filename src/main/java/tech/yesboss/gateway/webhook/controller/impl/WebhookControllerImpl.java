package tech.yesboss.gateway.webhook.controller.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.gateway.webhook.controller.WebhookController;
import tech.yesboss.gateway.webhook.executor.WebhookEventExecutor;
import tech.yesboss.gateway.webhook.model.ImWebhookEvent;
import tech.yesboss.safeguard.SuspendResumeEngine;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

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
        if (executor == null) {
            throw new IllegalArgumentException("executor cannot be null");
        }
        if (suspendResumeEngine == null) {
            throw new IllegalArgumentException("suspendResumeEngine cannot be null");
        }

        this.executor = executor;
        this.suspendResumeEngine = suspendResumeEngine;
        this.feishuAppSecret = (feishuAppSecret != null && !feishuAppSecret.isEmpty()) ? feishuAppSecret : "";
        this.slackSigningSecret = (slackSigningSecret != null && !slackSigningSecret.isEmpty()) ? slackSigningSecret : "";
        this.objectMapper = new ObjectMapper();

        logger.info("WebhookController initialized. Feishu secret: {}, Slack secret: {}",
            !this.feishuAppSecret.isEmpty() ? "configured" : "not configured",
            !this.slackSigningSecret.isEmpty() ? "configured" : "not configured");
    }

    @Override
    public String handleFeishuEvent(String timestamp, String nonce, String signature, String body) {
        logger.debug("Received Feishu webhook event");

        try {
            // Validate inputs
            if (body == null || body.isEmpty()) {
                logger.warn("Empty Feishu webhook body");
                return HTTP_200_OK;
            }

            // Verify signature if secret is configured
            if (!feishuAppSecret.isEmpty()) {
                verifyFeishuSignature(timestamp, nonce, signature, body);
            } else {
                logger.debug("Feishu signature verification skipped (no secret configured)");
            }

            // Parse JSON payload
            JsonNode rootNode = objectMapper.readTree(body);

            // Handle URL verification challenge (Feishu webhook handshake)
            // Reference: https://open.feishu.cn/document/server-docs/webhook/event-subscription-guide
            if (rootNode.has("type") && "url_verification".equals(rootNode.get("type").asText())) {
                String challenge = rootNode.get("challenge").asText();
                logger.info("Feishu URL verification challenge received");
                // Return JSON format as required by Feishu (not plain text like Slack)
                return "{\"challenge\":\"" + challenge + "\"}";
            }

            // Extract event details from Feishu/Lark format
            String eventType = extractFeishuEventType(rootNode);
            String imGroupId = extractFeishuGroupId(rootNode);
            String userId = extractFeishuUserId(rootNode);

            // Create internal event
            ImWebhookEvent event = ImWebhookEvent.create(
                "FEISHU",
                eventType,
                imGroupId,
                userId,
                body
            );

            logger.info("Feishu event parsed: type={}, group={}, user={}", eventType, imGroupId, userId);

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
            logger.error("Body length: {}", body.length());
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
     * @param data   The data to hash
     * @param secret The secret key
     * @return Base64-encoded HMAC-SHA256 hash
     */
    private String calculateHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new SecurityException("Failed to calculate HMAC-SHA256", e);
        }
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

    private String extractFeishuGroupId(JsonNode rootNode) {
        // Feishu group chat ID location depends on event type
        // For message events: event.message.chat_id
        // For other events: event.chat.chat_id

        // Try message event path first
        JsonNode messageNode = rootNode.path("event").path("message");
        if (messageNode.has("chat_id")) {
            return messageNode.get("chat_id").asText();
        }

        // Fallback to generic chat path
        JsonNode chatNode = rootNode.path("event").path("chat");
        if (chatNode.has("chat_id")) {
            return chatNode.get("chat_id").asText();
        }

        // Log warning for debugging
        logger.warn("Unable to extract chat_id from Feishu event. Event structure: {}",
            rootNode.toPrettyString());

        // Fallback to empty string
        return "unknown-group";
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
        logger.info("Received Feishu interactive callback");

        try {
            // Validate inputs
            if (body == null || body.isEmpty()) {
                logger.warn("Empty Feishu callback body");
                return HTTP_200_OK;
            }

            // Verify signature if secret is configured
            if (!feishuAppSecret.isEmpty()) {
                verifyFeishuSignature(timestamp, nonce, signature, body);
            } else {
                logger.debug("Feishu signature verification skipped (no secret configured)");
            }

            // Parse JSON payload
            JsonNode rootNode = objectMapper.readTree(body);

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

            logger.info("Feishu callback parsed: session={}, toolCallId={}, approved={}",
                sessionId, toolCallId, isApproved);

            // Route to SuspendResumeEngine asynchronously
            final String finalSessionId = sessionId;
            final String finalToolCallId = toolCallId;
            final Boolean finalIsApproved = isApproved;
            final String finalHumanFeedback = humanFeedback;

            // Use executor's thread pool for async processing
            executor.processAsync(ImWebhookEvent.create(
                "FEISHU",
                "callback",
                finalSessionId,
                "callback-user",
                body
            ));

            // Process the resume in the async executor
            // We'll use a separate thread to avoid blocking the HTTP response
            new Thread(() -> {
                try {
                    suspendResumeEngine.resume(finalSessionId, finalToolCallId,
                        finalIsApproved, finalHumanFeedback);
                    logger.info("Successfully processed Feishu callback resume for session {}", finalSessionId);
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
     */
    private String extractFeishuCallbackSessionId(JsonNode rootNode) {
        // Feishu action callback structure: { "action": { "value": "..." } }
        JsonNode actionNode = rootNode.path("action");
        if (actionNode.has("value")) {
            String valueStr = actionNode.get("value").asText();
            try {
                JsonNode valueJson = objectMapper.readTree(valueStr);
                if (valueJson.has("session_id")) {
                    return valueJson.get("session_id").asText();
                }
            } catch (Exception e) {
                logger.debug("Failed to parse button value as JSON: {}", valueStr);
            }
        }
        return null;
    }

    /**
     * Extract tool_call_id from Feishu callback payload.
     */
    private String extractFeishuCallbackToolCallId(JsonNode rootNode) {
        JsonNode actionNode = rootNode.path("action");
        if (actionNode.has("value")) {
            String valueStr = actionNode.get("value").asText();
            try {
                JsonNode valueJson = objectMapper.readTree(valueStr);
                if (valueJson.has("tool_call_id")) {
                    return valueJson.get("tool_call_id").asText();
                }
            } catch (Exception e) {
                logger.debug("Failed to parse button value as JSON: {}", valueStr);
            }
        }
        return null;
    }

    /**
     * Extract approved flag from Feishu callback payload.
     */
    private Boolean extractFeishuCallbackApproval(JsonNode rootNode) {
        JsonNode actionNode = rootNode.path("action");
        if (actionNode.has("value")) {
            String valueStr = actionNode.get("value").asText();
            try {
                JsonNode valueJson = objectMapper.readTree(valueStr);
                if (valueJson.has("approved")) {
                    return valueJson.get("approved").asBoolean();
                }
            } catch (Exception e) {
                logger.debug("Failed to parse button value as JSON: {}", valueStr);
            }
        }
        return null;
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
