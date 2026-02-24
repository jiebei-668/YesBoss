package tech.yesboss.gateway.im.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.gateway.im.IMMessagePusher;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * IM 消息推送器实现 (IM Message Pusher Implementation)
 *
 * <p>实现向飞书和 Slack 推送消息的功能。</p>
 *
 * <p><b>支持的操作：</b></p>
 * <ul>
 *   <li>推送卡片消息到 IM 群聊</li>
 *   <li>推送文本消息到 IM 群聊</li>
 *   <li>推送 UnifiedMessage 格式消息</li>
 * </ul>
 *
 * <p><b>API 端点：</b></p>
 * <ul>
 *   <li>飞书: https://open.feishu.cn/open-apis/bot/v2/hook/{hook_key}</li>
 *   <li>Slack: https://hooks.slack.com/services/{hook_key}</li>
 * </ul>
 *
 * <p><b>设计说明：</b></p>
 * <ul>
 *   <li>使用 Java 11+ HttpClient 进行 HTTP 调用</li>
 *   <li>同步阻塞调用，调用方负责异步处理</li>
 *   <li>支持环境变量配置 Webhook URL</li>
 * </ul>
 */
public class IMMessagePusherImpl implements IMMessagePusher {

    private static final Logger logger = LoggerFactory.getLogger(IMMessagePusherImpl.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // 环境变量键名
    private static final String ENV_FEISHU_WEBHOOK_PREFIX = "FEISHU_WEBHOOK_";
    private static final String ENV_SLACK_WEBHOOK_PREFIX = "SLACK_WEBHOOK_";

    // API 超时配置
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    public IMMessagePusherImpl() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public IMMessagePusherImpl(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void pushCardMessage(String imType, String imGroupId, String cardJson) throws Exception {
        logger.info("Pushing card message to {} group: {}", imType, imGroupId);

        String webhookUrl = getWebhookUrl(imType, imGroupId);
        if (webhookUrl == null) {
            throw new IllegalArgumentException(String.format(
                    "No webhook URL configured for %s group: %s. Please set environment variable: %s",
                    imType, imGroupId, getEnvKey(imType, imGroupId)));
        }

        String payload = formatCardPayload(imType, cardJson);
        executeHttpRequest(webhookUrl, payload);
    }

    @Override
    public void pushTextMessage(String imType, String imGroupId, String message) throws Exception {
        logger.info("Pushing text message to {} group: {}", imType, imGroupId);

        String webhookUrl = getWebhookUrl(imType, imGroupId);
        if (webhookUrl == null) {
            throw new IllegalArgumentException(String.format(
                    "No webhook URL configured for %s group: %s. Please set environment variable: %s",
                    imType, imGroupId, getEnvKey(imType, imGroupId)));
        }

        String payload = formatTextPayload(imType, message);
        executeHttpRequest(webhookUrl, payload);
    }

    @Override
    public void pushMessage(String imType, String imGroupId, UnifiedMessage message) throws Exception {
        logger.info("Pushing UnifiedMessage to {} group: {}", imType, imGroupId);

        // 将 UnifiedMessage 转换为文本消息推送
        // TODO: 未来可以根据消息内容智能选择推送方式（卡片或文本）
        String displayContent = message.getDisplayContent();
        pushTextMessage(imType, imGroupId, displayContent);
    }

    /**
     * 获取 Webhook URL
     *
     * @param imType IM 平台类型 (FEISHU, SLACK)
     * @param imGroupId 群聊 ID
     * @return Webhook URL，如果未配置则返回 null
     */
    private String getWebhookUrl(String imType, String imGroupId) {
        String envKey = getEnvKey(imType, imGroupId);
        String webhookUrl = System.getenv(envKey);

        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            logger.warn("Webhook URL not found for key: {}", envKey);
            return null;
        }

        return webhookUrl.trim();
    }

    /**
     * 获取环境变量键名
     *
     * @param imType IM 平台类型
     * @param imGroupId 群聊 ID
     * @return 环境变量键名
     */
    private String getEnvKey(String imType, String imGroupId) {
        // 将群 ID 转换为大写并替换特殊字符为下划线
        String sanitizedGroupId = imGroupId
                .toUpperCase()
                .replaceAll("[^A-Z0-9]", "_");

        if ("FEISHU".equalsIgnoreCase(imType)) {
            return ENV_FEISHU_WEBHOOK_PREFIX + sanitizedGroupId;
        } else if ("SLACK".equalsIgnoreCase(imType)) {
            return ENV_SLACK_WEBHOOK_PREFIX + sanitizedGroupId;
        } else {
            throw new IllegalArgumentException("Unsupported IM type: " + imType);
        }
    }

    /**
     * 格式化卡片消息负载
     *
     * @param imType IM 平台类型
     * @param cardJson 卡片的 JSON 字符串
     * @return 格式化后的 HTTP 负载
     */
    private String formatCardPayload(String imType, String cardJson) {
        try {
            JsonNode cardNode = objectMapper.readTree(cardJson);

            if ("FEISHU".equalsIgnoreCase(imType)) {
                // 飞书卡片格式
                JsonNode feishuCard = cardNode.get("feishu_card");
                if (feishuCard != null) {
                    return objectMapper.writeValueAsString(feishuCard);
                }
            } else if ("SLACK".equalsIgnoreCase(imType)) {
                // Slack 卡片格式
                JsonNode slackAttachments = cardNode.get("slack_attachments");
                if (slackAttachments != null) {
                    ObjectNode slackPayload = objectMapper.createObjectNode();
                    slackPayload.set("attachments", slackAttachments);
                    return objectMapper.writeValueAsString(slackPayload);
                }
            }

            // 如果没有特定格式，返回原始 JSON
            return cardJson;

        } catch (Exception e) {
            logger.error("Failed to format card payload for {}: {}", imType, e.getMessage(), e);
            // 格式化失败，返回原始 JSON
            return cardJson;
        }
    }

    /**
     * 格式化文本消息负载
     *
     * @param imType IM 平台类型
     * @param message 文本消息
     * @return 格式化后的 HTTP 负载
     */
    private String formatTextPayload(String imType, String message) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();

            if ("FEISHU".equalsIgnoreCase(imType)) {
                // 飞书文本消息格式
                ObjectNode content = objectMapper.createObjectNode();
                content.put("text", message);
                payload.put("msg_type", "text");
                payload.set("content", content);
            } else if ("SLACK".equalsIgnoreCase(imType)) {
                // Slack 文本消息格式
                payload.put("text", message);
            } else {
                throw new IllegalArgumentException("Unsupported IM type: " + imType);
            }

            return objectMapper.writeValueAsString(payload);

        } catch (Exception e) {
            logger.error("Failed to format text payload for {}: {}", imType, e.getMessage(), e);
            // 格式化失败，返回纯文本
            return message;
        }
    }

    /**
     * 执行 HTTP 请求
     *
     * @param webhookUrl Webhook URL
     * @param payload 请求负载
     * @throws Exception 请求失败时抛出异常
     */
    private void executeHttpRequest(String webhookUrl, String payload) throws Exception {
        try {
            logger.debug("Sending HTTP POST to: {}", webhookUrl);
            logger.debug("Payload: {}", payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String responseBody = response.body();

            logger.debug("Received response - Status: {}, Body: {}", statusCode, responseBody);

            if (statusCode >= 200 && statusCode < 300) {
                logger.info("Message pushed successfully, status: {}", statusCode);
            } else {
                throw new RuntimeException(String.format(
                        "Failed to push message. HTTP %d: %s", statusCode, responseBody));
            }

        } catch (Exception e) {
            logger.error("Failed to push message to {}: {}", webhookUrl, e.getMessage(), e);
            throw new Exception("Failed to push message: " + e.getMessage(), e);
        }
    }
}
