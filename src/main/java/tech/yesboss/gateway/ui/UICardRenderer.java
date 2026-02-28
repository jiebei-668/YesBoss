package tech.yesboss.gateway.ui;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * UI 卡片渲染器接口 (UI Card Renderer Interface)
 *
 * <p>负责渲染各种 UI 卡片的通用接口，用于 IM 消息推送。</p>
 *
 * <p><b>支持卡片类型：</b></p>
 * <ul>
 *   <li>进度条卡片 - 显示任务执行进度</li>
 *   <li>审批卡片 - 人机回环审批界面</li>
 *   <li>总结卡片 - 任务完成总结报告</li>
 * </ul>
 *
 * <p><b>实现说明：</b></p>
 * <p>此接口作为 Access & Routing 模块的占位符。
 * 完整的 IM 协议适配和卡片渲染将在后续模块中实现。</p>
 *
 * @see tech.yesboss.gateway.im.IMMessagePusher
 */
public interface UICardRenderer {

    /**
     * 渲染进度条卡片
     *
     * @param totalTasks 总任务数
     * @param completedTasks 已完成任务数
     * @param currentTask 当前执行的任务描述
     * @return 进度条卡片的 JSON 结构
     */
    JsonNode renderProgressBar(int totalTasks, int completedTasks, String currentTask);

    /**
     * 渲染审批卡片（人机回环）
     *
     * @param sessionId 会话 ID
     * @param toolCallId 工具调用 ID
     * @param interceptedCommand 被拦截的命令
     * @param toolName 工具名称
     * @return 审批卡片的 JSON 结构
     */
    JsonNode renderSuspensionCard(String sessionId, String toolCallId,
                                   String interceptedCommand, String toolName);

    /**
     * 渲染任务完成总结卡片
     *
     * @param sessionId 会话 ID
     * @param summaryText 总结文本
     * @param success 是否成功
     * @return 总结卡片的 JSON 结构
     */
    JsonNode renderSummaryCard(String sessionId, String summaryText, boolean success);

    /**
     * 渲染需求澄清问题卡片
     *
     * @param sessionId 会话 ID
     * @param question 澄清问题
     * @return 澄清问题卡片的 JSON 结构
     */
    JsonNode renderClarificationCard(String sessionId, String question);
}
