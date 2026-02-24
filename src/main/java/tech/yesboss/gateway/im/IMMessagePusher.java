package tech.yesboss.gateway.im;

import tech.yesboss.domain.message.UnifiedMessage;

/**
 * IM Message Pusher (IM Message Pusher Interface)
 *
 * <p>负责将消息推送到外部 IM 平台（飞书、Slack 等）。</p>
 *
 * <p><b>核心职责：</b></p>
 * <ul>
 *   <li>推送卡片消息到 IM 群聊</li>
 *   <li>推送文本消息到 IM 群聊</li>
 *   <li>处理不同 IM 平台的 API 调用</li>
 * </ul>
 *
 * <p><b>注意：</b></p>
 * <p>这是接入与路由模块的核心接口，此处作为占位符接口。
 * 完整实现将在后续的接入与路由模块任务中完成。</p>
 */
public interface IMMessagePusher {

    /**
     * 推送卡片消息到 IM 群聊
     *
     * <p>用于推送人机回环审批卡片、进度卡片、总结卡片等。</p>
     *
     * @param imType    IM 平台类型 (FEISHU, SLACK)
     * @param imGroupId 群聊 ID
     * @param cardJson  卡片的 JSON 格式内容
     * @throws Exception 推送失败时抛出异常
     */
    void pushCardMessage(String imType, String imGroupId, String cardJson) throws Exception;

    /**
     * 推送文本消息到 IM 群聊
     *
     * <p>用于推送简单的文本通知。</p>
     *
     * @param imType    IM 平台类型 (FEISHU, SLACK)
     * @param imGroupId 群聊 ID
     * @param message   文本消息内容
     * @throws Exception 推送失败时抛出异常
     */
    void pushTextMessage(String imType, String imGroupId, String message) throws Exception;

    /**
     * 推送 UnifiedMessage 到 IM 群聊
     *
     * <p>统一的推送接口，根据消息类型自动选择推送方式。</p>
     *
     * @param imType    IM 平台类型 (FEISHU, SLACK)
     * @param imGroupId 群聊 ID
     * @param message   统一消息格式
     * @throws Exception 推送失败时抛出异常
     */
    void pushMessage(String imType, String imGroupId, UnifiedMessage message) throws Exception;
}
