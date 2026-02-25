package tech.yesboss.gateway.im.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.gateway.im.FeishuApiClient;
import tech.yesboss.gateway.im.IMMessagePusher;

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
 * <p><b>设计说明：</b></p>
 * <ul>
 *   <li>使用 FeishuApiClient 进行飞书消息推送（OAuth2 认证）</li>
 *   <li>同步阻塞调用，调用方负责异步处理</li>
 *   <li>支持 receive_id_type 为 chat_id 的消息发送</li>
 *   <li>FeishuApiClient 为可选配置，未配置时消息推送为空操作</li>
 * </ul>
 */
public class IMMessagePusherImpl implements IMMessagePusher {

    private static final Logger logger = LoggerFactory.getLogger(IMMessagePusherImpl.class);

    private static final String RECEIVE_ID_TYPE_CHAT_ID = "chat_id";
    private static final String RECEIVE_ID_TYPE_OPEN_ID = "open_id";
    private static final String RECEIVE_ID_TYPE_USER_ID = "user_id";

    private final FeishuApiClient feishuApiClient;
    private final ObjectMapper objectMapper;

    /**
     * Default constructor with FeishuApiClient injection (nullable).
     *
     * @param feishuApiClient Feishu API client for message sending (optional)
     */
    public IMMessagePusherImpl(FeishuApiClient feishuApiClient) {
        this.feishuApiClient = feishuApiClient;
        this.objectMapper = new ObjectMapper();
        if (feishuApiClient == null) {
            logger.warn("IMMessagePusherImpl initialized without FeishuApiClient - message pushing will be disabled");
        }
    }

    /**
     * Test constructor with dependency injection.
     *
     * @param feishuApiClient Feishu API client
     * @param objectMapper    ObjectMapper for JSON processing
     */
    public IMMessagePusherImpl(FeishuApiClient feishuApiClient, ObjectMapper objectMapper) {
        this.feishuApiClient = feishuApiClient;
        this.objectMapper = objectMapper;
        if (feishuApiClient == null && objectMapper == null) {
            logger.warn("IMMessagePusherImpl initialized without FeishuApiClient - message pushing will be disabled");
        }
    }

    @Override
    public void pushCardMessage(String imType, String imGroupId, String cardJson) throws Exception {
        logger.info("Pushing card message to {} group: {}", imType, imGroupId);

        if ("FEISHU".equalsIgnoreCase(imType)) {
            pushFeishuCardMessage(imGroupId, cardJson);
        } else if ("SLACK".equalsIgnoreCase(imType)) {
            throw new UnsupportedOperationException("Slack integration not yet implemented");
        } else {
            throw new IllegalArgumentException("Unsupported IM type: " + imType);
        }
    }

    @Override
    public void pushTextMessage(String imType, String imGroupId, String message) throws Exception {
        logger.info("Pushing text message to {} group: {}", imType, imGroupId);

        if ("FEISHU".equalsIgnoreCase(imType)) {
            pushFeishuTextMessage(imGroupId, message);
        } else if ("SLACK".equalsIgnoreCase(imType)) {
            throw new UnsupportedOperationException("Slack integration not yet implemented");
        } else {
            throw new IllegalArgumentException("Unsupported IM type: " + imType);
        }
    }

    @Override
    public void pushMessage(String imType, String imGroupId, UnifiedMessage message) throws Exception {
        logger.info("Pushing UnifiedMessage to {} group: {}", imType, imGroupId);

        // Convert UnifiedMessage to text message for push
        // TODO: Future enhancement - intelligently choose card vs text based on message content
        String displayContent = message.getDisplayContent();
        pushTextMessage(imType, imGroupId, displayContent);
    }

    // ==================== Private Helper Methods ====================

    /**
     * Push card message to Feishu using FeishuApiClient.
     *
     * @param chatId   Feishu group chat ID
     * @param cardJson Card JSON content
     * @throws FeishuApiClient.FeishuApiException if sending fails
     */
    private void pushFeishuCardMessage(String chatId, String cardJson) throws FeishuApiClient.FeishuApiException {
        if (feishuApiClient == null) {
            logger.warn("FeishuApiClient not configured, skipping card message to chat_id: {}", chatId);
            return;
        }
        try {
            String messageId = feishuApiClient.sendCardMessage(chatId, RECEIVE_ID_TYPE_CHAT_ID, cardJson);
            logger.info("Card message sent successfully to Feishu chat_id: {}, message_id: {}", chatId, messageId);
        } catch (FeishuApiClient.FeishuApiException e) {
            logger.error("Failed to send card message to Feishu chat_id: {}, error: {}", chatId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Push text message to Feishu using FeishuApiClient.
     *
     * @param chatId  Feishu group chat ID
     * @param message Text message content
     * @throws FeishuApiClient.FeishuApiException if sending fails
     */
    private void pushFeishuTextMessage(String chatId, String message) throws FeishuApiClient.FeishuApiException {
        if (feishuApiClient == null) {
            logger.warn("FeishuApiClient not configured, skipping text message to chat_id: {}", chatId);
            return;
        }
        try {
            String messageId = feishuApiClient.sendTextMessage(chatId, RECEIVE_ID_TYPE_CHAT_ID, message);
            logger.info("Text message sent successfully to Feishu chat_id: {}, message_id: {}", chatId, messageId);
        } catch (FeishuApiClient.FeishuApiException e) {
            logger.error("Failed to send text message to Feishu chat_id: {}, error: {}", chatId, e.getMessage(), e);
            throw e;
        }
    }
}
