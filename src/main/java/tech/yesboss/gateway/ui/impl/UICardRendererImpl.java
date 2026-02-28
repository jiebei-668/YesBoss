package tech.yesboss.gateway.ui.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.gateway.ui.UICardRenderer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * UI 卡片渲染器实现 (UI Card Renderer Implementation)
 *
 * <p>实现极简 UI 卡片渲染，支持飞书和 Slack 的卡片格式。</p>
 *
 * <p><b>支持的卡片类型：</b></p>
 * <ul>
 *   <li>进度条卡片 - 显示任务执行进度</li>
 *   <li>审批卡片 - 人机回环审批界面</li>
 *   <li>总结卡片 - 任务完成总结报告</li>
 * </ul>
 *
 * <p><b>设计原则：</b></p>
 * <ul>
 *   <li>返回通用的 JSON 结构，可适配不同 IM 平台</li>
 *   <li>极简设计，只显示必要信息</li>
 *   <li>支持交互式按钮（用于人机回环）</li>
 * </ul>
 */
public class UICardRendererImpl implements UICardRenderer {

    private static final Logger logger = LoggerFactory.getLogger(UICardRendererImpl.class);
    private final ObjectMapper objectMapper;

    // IM 平台类型常量
    private static final String IM_TYPE_FEISHU = "FEISHU";
    private static final String IM_TYPE_SLACK = "SLACK";

    public UICardRendererImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public UICardRendererImpl() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public JsonNode renderProgressBar(int totalTasks, int completedTasks, String currentTask) {
        logger.info("Rendering progress bar: {}/{} tasks, current: {}", completedTasks, totalTasks, currentTask);

        ObjectNode card = objectMapper.createObjectNode();

        // 通用字段
        card.put("card_type", "progress");
        card.put("total_tasks", totalTasks);
        card.put("completed_tasks", completedTasks);
        card.put("current_task", currentTask != null ? currentTask : "正在执行...");

        // 计算进度百分比
        int progress = totalTasks > 0 ? (completedTasks * 100 / totalTasks) : 0;
        card.put("progress_percent", progress);

        // 飞书格式的进度条 - 使用成功的直接结构
        ObjectNode feishuCard = objectMapper.createObjectNode();

        // 创建header
        ObjectNode header = objectMapper.createObjectNode();
        ObjectNode title = objectMapper.createObjectNode();
        title.put("tag", "plain_text");
        title.put("content", "任务执行进度");
        header.set("title", title);
        feishuCard.set("header", header);

        // 创建elements数组
        ArrayNode elements = objectMapper.createArrayNode();

        // 添加进度文本
        String progressText = String.format("已完成 %d/%d 个子任务 (%d%%)",
                completedTasks, totalTasks, progress);
        ObjectNode progressElement = objectMapper.createObjectNode();
        progressElement.put("tag", "div");
        ObjectNode ptext = objectMapper.createObjectNode();
        ptext.put("tag", "plain_text");
        ptext.put("content", progressText);
        progressElement.set("text", ptext);
        elements.add(progressElement);

        // 添加当前任务
        if (currentTask != null && !currentTask.isEmpty()) {
            ObjectNode currentElement = objectMapper.createObjectNode();
            currentElement.put("tag", "div");
            ObjectNode ctext = objectMapper.createObjectNode();
            ctext.put("tag", "plain_text");
            ctext.put("content", currentTask);
            currentElement.set("text", ctext);
            elements.add(currentElement);
        }

        feishuCard.set("elements", elements);

        // 将飞书card转换为JSON字符串，用于sendMessage
        try {
            String feishuCardJson = objectMapper.writeValueAsString(feishuCard);
            card.put("feishu_card_json", feishuCardJson);
        } catch (Exception e) {
            logger.error("Failed to convert Feishu card to JSON", e);
        }

        // Slack 格式的进度条
        ObjectNode slackAttachment = objectMapper.createObjectNode();
        slackAttachment.put("color", progress == 100 ? "good" : "warning");
        slackAttachment.put("title", "任务执行进度");
        slackAttachment.put("text", String.format("已完成 %d/%d 个子任务 (%d%%)\\n正在执行：%s\\n%s",
                completedTasks, totalTasks, progress,
                currentTask != null ? currentTask : "暂无",
                buildProgressBar(progress)));
        slackAttachment.put("footer", "YesBoss 任务执行中");
        slackAttachment.put("ts", System.currentTimeMillis() / 1000);

        ArrayNode slackAttachments = objectMapper.createArrayNode();
        slackAttachments.add(slackAttachment);
        card.set("slack_attachments", slackAttachments);

        return card;
    }

    @Override
    public JsonNode renderSuspensionCard(String sessionId, String toolCallId,
                                          String interceptedCommand, String toolName) {
        logger.info("Rendering suspension card for session: {}, tool: {}", sessionId, toolName);

        ObjectNode card = objectMapper.createObjectNode();

        // 通用字段
        card.put("card_type", "suspension");
        card.put("session_id", sessionId);
        card.put("tool_call_id", toolCallId);
        card.put("intercepted_command", interceptedCommand != null ? interceptedCommand : "未知命令");
        card.put("tool_name", toolName != null ? toolName : "未知工具");
        card.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // 飞书格式的审批卡片 - 使用成功的直接结构
        ObjectNode feishuCard = objectMapper.createObjectNode();

        // 创建header
        ObjectNode header = objectMapper.createObjectNode();
        ObjectNode title = objectMapper.createObjectNode();
        title.put("tag", "plain_text");
        title.put("content", String.format("⚠️ 高危操作拦截 - %s", toolName));
        header.set("title", title);
        feishuCard.set("header", header);

        // 创建elements数组
        ArrayNode elements = objectMapper.createArrayNode();

        // 添加命令详情
        String commandMsg = String.format("被拦截的命令: %s",
                interceptedCommand != null ? interceptedCommand : "未知");
        ObjectNode commandElement = objectMapper.createObjectNode();
        commandElement.put("tag", "div");
        ObjectNode commandText = objectMapper.createObjectNode();
        commandText.put("tag", "plain_text");
        commandText.put("content", commandMsg);
        commandElement.set("text", commandText);
        elements.add(commandElement);

        // 添加说明
        ObjectNode noticeElement = objectMapper.createObjectNode();
        noticeElement.put("tag", "div");
        ObjectNode noticeText = objectMapper.createObjectNode();
        noticeText.put("tag", "plain_text");
        noticeText.put("content", "该操作存在安全风险，需要您手动授权后才能继续执行。");
        noticeElement.set("text", noticeText);
        elements.add(noticeElement);

        // 添加操作按钮
        ObjectNode actionsElement = objectMapper.createObjectNode();
        actionsElement.put("tag", "action");
        ArrayNode actions = actionsElement.putArray("actions");

        // 批准按钮
        ObjectNode approveButton = objectMapper.createObjectNode();
        approveButton.put("tag", "button");
        approveButton.set("text", objectMapper.createObjectNode().put("tag", "plain_text").put("content", "✓ 批准执行"));
        approveButton.put("type", "primary");
        approveButton.put("value", String.format("{\"session_id\":\"%s\",\"tool_call_id\":\"%s\",\"approved\":true}",
                sessionId, toolCallId));
        actions.add(approveButton);

        // 拒绝按钮
        ObjectNode rejectButton = objectMapper.createObjectNode();
        rejectButton.put("tag", "button");
        rejectButton.set("text", objectMapper.createObjectNode().put("tag", "plain_text").put("content", "✗ 拒绝执行"));
        rejectButton.put("type", "danger");
        rejectButton.put("value", String.format("{\"session_id\":\"%s\",\"tool_call_id\":\"%s\",\"approved\":false}",
                sessionId, toolCallId));
        actions.add(rejectButton);

        elements.add(actionsElement);
        feishuCard.set("elements", elements);

        // 将飞书card转换为JSON字符串，用于sendMessage
        try {
            String feishuCardJson = objectMapper.writeValueAsString(feishuCard);
            card.put("feishu_card_json", feishuCardJson);
        } catch (Exception e) {
            logger.error("Failed to convert Feishu card to JSON", e);
        }

        // Slack 格式的审批卡片
        ObjectNode slackAttachment = objectMapper.createObjectNode();
        slackAttachment.put("color", "danger");
        slackAttachment.put("title", String.format("⚠️ 高危操作拦截 - 工具：%s", toolName));
        slackAttachment.put("text", String.format("*被拦截的命令：*\\n```%s```\\n该操作存在安全风险，请选择是否批准。",
                interceptedCommand != null ? interceptedCommand : "未知"));
        slackAttachment.put("footer", "YesBoss 安全拦截");
        slackAttachment.put("ts", System.currentTimeMillis() / 1000);

        // Slack 按钮操作
        ArrayNode slackActions = objectMapper.createArrayNode();
        ObjectNode approveAction = objectMapper.createObjectNode();
        approveAction.put("name", "approve_action");
        approveAction.put("text", "✓ 批准执行");
        approveAction.put("type", "button");
        approveAction.put("value", String.format("{\"session_id\":\"%s\",\"tool_call_id\":\"%s\",\"approved\":true}",
                sessionId, toolCallId));
        approveAction.put("style", "primary");
        slackActions.add(approveAction);

        ObjectNode rejectAction = objectMapper.createObjectNode();
        rejectAction.put("name", "reject_action");
        rejectAction.put("text", "✗ 拒绝执行");
        rejectAction.put("type", "button");
        rejectAction.put("value", String.format("{\"session_id\":\"%s\",\"tool_call_id\":\"%s\",\"approved\":false}",
                sessionId, toolCallId));
        rejectAction.put("style", "danger");
        slackActions.add(rejectAction);

        slackAttachment.set("actions", slackActions);

        ArrayNode slackAttachments = objectMapper.createArrayNode();
        slackAttachments.add(slackAttachment);
        card.set("slack_attachments", slackAttachments);

        return card;
    }

    @Override
    public JsonNode renderSummaryCard(String sessionId, String summaryText, boolean success) {
        logger.info("Rendering summary card for session: {}, success: {}", sessionId, success);

        ObjectNode card = objectMapper.createObjectNode();

        // 通用字段
        card.put("card_type", "summary");
        card.put("session_id", sessionId);
        card.put("success", success);
        card.put("summary_text", summaryText != null ? summaryText : "任务已完成");
        card.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // 状态标签
        String statusLabel = success ? "✓ 任务完成" : "✗ 任务失败";
        String statusColor = success ? "green" : "red";

        // 飞书格式的总结卡片 - 使用官方示例格式
        // 参考：https://open.feishu.cn/document/common-capabilities/message-card/message-cards-content
        ObjectNode feishuCard = objectMapper.createObjectNode();

        // 创建header
        ObjectNode header = objectMapper.createObjectNode();
        ObjectNode title = objectMapper.createObjectNode();
        title.put("tag", "plain_text");
        title.put("content", statusLabel);
        header.set("title", title);
        feishuCard.set("header", header);

        // 创建elements数组
        ArrayNode elements = objectMapper.createArrayNode();

        // 添加文本元素
        ObjectNode divElement = objectMapper.createObjectNode();
        divElement.put("tag", "div");
        ObjectNode textObj = objectMapper.createObjectNode();
        textObj.put("tag", "plain_text");
        textObj.put("content", summaryText != null ? summaryText : "任务已完成");
        divElement.set("text", textObj);
        elements.add(divElement);

        feishuCard.set("elements", elements);

        // 将飞书card转换为JSON字符串，用于sendMessage
        try {
            String feishuCardJson = objectMapper.writeValueAsString(feishuCard);
            card.put("feishu_card_json", feishuCardJson);
        } catch (Exception e) {
            logger.error("Failed to convert Feishu card to JSON", e);
        }  // Store as string for later use

        // Slack 格式的总结卡片
        ObjectNode slackAttachment = objectMapper.createObjectNode();
        slackAttachment.put("color", success ? "good" : "danger");
        slackAttachment.put("title", statusLabel);
        slackAttachment.put("text", summaryText != null ? summaryText : "任务已完成");
        slackAttachment.put("footer", "YesBoss 任务报告");
        slackAttachment.put("ts", System.currentTimeMillis() / 1000);

        ArrayNode slackAttachments = objectMapper.createArrayNode();
        slackAttachments.add(slackAttachment);
        card.set("slack_attachments", slackAttachments);

        return card;
    }

    @Override
    public JsonNode renderClarificationCard(String sessionId, String question) {
        logger.info("Rendering clarification card for session: {}, question: {}", sessionId, question);

        ObjectNode card = objectMapper.createObjectNode();

        // 通用字段
        card.put("card_type", "clarification");
        card.put("session_id", sessionId);
        card.put("question", question != null ? question : "请提供更多需求细节");
        card.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // 飞书格式的澄清问题卡片
        ObjectNode feishuCard = objectMapper.createObjectNode();

        // 创建header
        ObjectNode header = objectMapper.createObjectNode();
        ObjectNode title = objectMapper.createObjectNode();
        title.put("tag", "plain_text");
        title.put("content", "📋 需求澄清");
        header.set("title", title);
        feishuCard.set("header", header);

        // 创建elements数组
        ArrayNode elements = objectMapper.createArrayNode();

        // 添加问题文本
        ObjectNode divElement = objectMapper.createObjectNode();
        divElement.put("tag", "div");
        ObjectNode textObj = objectMapper.createObjectNode();
        textObj.put("tag", "plain_text");
        textObj.put("content", question != null ? question : "请提供更多需求细节");
        divElement.set("text", textObj);
        elements.add(divElement);

        // 添加分隔线
        ObjectNode hrElement = objectMapper.createObjectNode();
        hrElement.put("tag", "hr");
        elements.add(hrElement);

        // 添加提示文本
        ObjectNode tipElement = objectMapper.createObjectNode();
        tipElement.put("tag", "div");
        ObjectNode tipText = objectMapper.createObjectNode();
        tipText.put("tag", "plain_text");
        tipText.put("content", "💬 请在群聊中回复，帮助我更好地理解您的需求");
        tipElement.set("text", tipText);
        elements.add(tipElement);

        feishuCard.set("elements", elements);

        // 将飞书card转换为JSON字符串
        try {
            String feishuCardJson = objectMapper.writeValueAsString(feishuCard);
            card.put("feishu_card_json", feishuCardJson);
        } catch (Exception e) {
            logger.error("Failed to convert Feishu card to JSON", e);
        }

        // Slack 格式的澄清问题卡片
        ObjectNode slackAttachment = objectMapper.createObjectNode();
        slackAttachment.put("color", "warning"); // 黄色表示等待
        slackAttachment.put("title", "📋 需求澄清");
        slackAttachment.put("text", question != null ? question : "请提供更多需求细节");
        slackAttachment.put("footer", "YesBoss 智能助手");
        slackAttachment.put("ts", System.currentTimeMillis() / 1000);

        ArrayNode slackAttachments = objectMapper.createArrayNode();
        slackAttachments.add(slackAttachment);
        card.set("slack_attachments", slackAttachments);

        return card;
    }

    /**
     * 构建文本进度条
     *
     * @param progress 进度百分比 (0-100)
     * @return 文本进度条字符串
     */
    private String buildProgressBar(int progress) {
        int totalBars = 20;
        int filledBars = (progress * totalBars) / 100;
        StringBuilder bar = new StringBuilder("```\\n[");
        for (int i = 0; i < totalBars; i++) {
            if (i < filledBars) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }
        bar.append("]\\n```");
        return bar.toString();
    }
}
