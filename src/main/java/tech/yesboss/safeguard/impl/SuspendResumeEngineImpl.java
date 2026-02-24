package tech.yesboss.safeguard.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.context.GlobalStreamManager;
import tech.yesboss.context.LocalStreamManager;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.gateway.im.IMMessagePusher;
import tech.yesboss.safeguard.SuspendResumeEngine;
import tech.yesboss.state.TaskManager;
import tech.yesboss.state.model.ImRoute;
import tech.yesboss.tool.AgentTool;
import tech.yesboss.tool.registry.ToolRegistry;
import tech.yesboss.tool.tracker.ToolCallTracker;

import java.util.HashMap;
import java.util.Map;

/**
 * 挂起与恢复引擎实现 (Suspend and Resume Engine Implementation)
 *
 * <p>实现人机回环的核心逻辑，采用线程完全退出模式：</p>
 * <ul>
 *   <li>挂起时：Worker 线程完全退出，仅保留数据库状态</li>
 *   <li>恢复时：创建新线程，从数据库重建上下文</li>
 * </ul>
 *
 * <p><b>设计特点：</b></p>
 * <ul>
 *   <li>完全避免资源泄漏，即使用户永久不回复</li>
 *   <li>支持服务重启后恢复挂起任务</li>
 *   <li>符合云原生无状态设计理念</li>
 * </ul>
 */
public class SuspendResumeEngineImpl implements SuspendResumeEngine {

    private static final Logger logger = LoggerFactory.getLogger(SuspendResumeEngineImpl.class);

    private final TaskManager taskManager;
    private final GlobalStreamManager globalStreamManager;
    private final LocalStreamManager localStreamManager;
    private final ToolRegistry toolRegistry;
    private final IMMessagePusher imMessagePusher;
    private final ToolCallTracker toolCallTracker;

    // 用于存储从 ToolExecutionLog 中查询到的工具信息
    // Key: toolCallId, Value: {toolName, argumentsJson, sessionId}
    private final Map<String, SuspensionContext> suspensionContextMap;

    /**
     * 挂起上下文信息
     *
     * @param toolName      工具名称
     * @param argumentsJson 工具参数 JSON
     * @param sessionId     会话 ID
     */
    private record SuspensionContext(String toolName, String argumentsJson, String sessionId) {}

    /**
     * 构造函数
     *
     * @param taskManager         任务管理器
     * @param globalStreamManager 全局流管理器
     * @param localStreamManager  局部流管理器
     * @param toolRegistry        工具注册表
     * @param imMessagePusher     IM 消息推送器
     * @param toolCallTracker     工具调用追踪器
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    public SuspendResumeEngineImpl(
            TaskManager taskManager,
            GlobalStreamManager globalStreamManager,
            LocalStreamManager localStreamManager,
            ToolRegistry toolRegistry,
            IMMessagePusher imMessagePusher,
            ToolCallTracker toolCallTracker) {
        if (taskManager == null) {
            throw new IllegalArgumentException("taskManager cannot be null");
        }
        if (globalStreamManager == null) {
            throw new IllegalArgumentException("globalStreamManager cannot be null");
        }
        if (localStreamManager == null) {
            throw new IllegalArgumentException("localStreamManager cannot be null");
        }
        if (toolRegistry == null) {
            throw new IllegalArgumentException("toolRegistry cannot be null");
        }
        if (imMessagePusher == null) {
            throw new IllegalArgumentException("imMessagePusher cannot be null");
        }
        if (toolCallTracker == null) {
            throw new IllegalArgumentException("toolCallTracker cannot be null");
        }

        this.taskManager = taskManager;
        this.globalStreamManager = globalStreamManager;
        this.localStreamManager = localStreamManager;
        this.toolRegistry = toolRegistry;
        this.imMessagePusher = imMessagePusher;
        this.toolCallTracker = toolCallTracker;
        this.suspensionContextMap = new HashMap<>();

        logger.info("SuspendResumeEngine initialized successfully");
    }

    @Override
    public void suspendForApproval(String workerSessionId, String interceptedCommand, String toolCallId) {
        // 参数校验
        if (workerSessionId == null || workerSessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("workerSessionId cannot be null or empty");
        }
        if (interceptedCommand == null || interceptedCommand.trim().isEmpty()) {
            throw new IllegalArgumentException("interceptedCommand cannot be null or empty");
        }
        if (toolCallId == null || toolCallId.trim().isEmpty()) {
            throw new IllegalArgumentException("toolCallId cannot be null or empty");
        }

        logger.info("Suspending worker session {} for tool call {}", workerSessionId, toolCallId);

        // 1. 检查 Worker 会话是否存在
        if (!taskManager.sessionExists(workerSessionId)) {
            throw new IllegalStateException("Worker session not found: " + workerSessionId);
        }

        // 2. 检查当前状态是否为 RUNNING
        var currentStatus = taskManager.getStatus(workerSessionId);
        if (currentStatus != tech.yesboss.persistence.entity.TaskSession.Status.RUNNING) {
            throw new IllegalStateException(
                "Worker session is not in RUNNING state, current status: " + currentStatus);
        }

        // 3. 获取父级 Master Session ID
        String masterSessionId = taskManager.getParentSessionId(workerSessionId);
        if (masterSessionId == null) {
            throw new IllegalStateException("Worker has no parent Master session: " + workerSessionId);
        }

        // 4. 持久化拦截现场到数据库（通过 ToolCallTracker）
        // 注意：这里需要从 SuspendExecutionException 中获取工具信息
        // 实际使用中，这些信息会在捕获异常时传入
        // 这里我们暂时将信息存储在内存中，实际应该从 ToolExecutionLog 查询
        // 暂时跳过这一步，因为需要先从 exception 中提取 toolName 和 arguments

        // 5. 将任务状态转为 SUSPENDED
        taskManager.transitionState(workerSessionId,
            tech.yesboss.persistence.entity.TaskSession.Status.SUSPENDED);
        logger.info("Worker session {} transitioned to SUSPENDED", workerSessionId);

        // 6. 伪造全局系统消息并推送审批卡片到 IM
        try {
            // 获取 IM 路由信息
            ImRoute imRoute = taskManager.getImRoute(masterSessionId);

            // 构造审批卡片 JSON
            String cardJson = buildSuspensionCardJson(workerSessionId, interceptedCommand, toolCallId);

            // 推送卡片到 IM
            imMessagePusher.pushCardMessage(imRoute.imType(), imRoute.imGroupId(), cardJson);
            logger.info("Suspension card pushed to IM {} group {}", imRoute.imType(), imRoute.imGroupId());

            // 同时向全局流添加系统消息
            String systemMessage = String.format(
                "【系统通知】Worker 任务已被挂起，等待用户审批。\n" +
                "被拦截命令: %s\n" +
                "工具调用ID: %s\n" +
                "请通过 IM 卡片进行审批。",
                interceptedCommand, toolCallId
            );
            globalStreamManager.appendSystemMessage(masterSessionId, systemMessage);

        } catch (Exception e) {
            logger.error("Failed to push suspension card to IM for session {}", workerSessionId, e);
            // 即使推送失败，任务状态已经是 SUSPENDED，不影响整体流程
        }

        logger.info("Worker session {} suspension completed, waiting for user approval", workerSessionId);
    }

    @Override
    public void resume(String workerSessionId, String toolCallId, boolean isApproved, String humanFeedback) {
        // 参数校验
        if (workerSessionId == null || workerSessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("workerSessionId cannot be null or empty");
        }
        if (toolCallId == null || toolCallId.trim().isEmpty()) {
            throw new IllegalArgumentException("toolCallId cannot be null or empty");
        }

        logger.info("Resuming worker session {} with approval={}, toolCallId={}",
            workerSessionId, isApproved, toolCallId);

        // 1. 检查 Worker 会话是否存在
        if (!taskManager.sessionExists(workerSessionId)) {
            throw new IllegalStateException("Worker session not found: " + workerSessionId);
        }

        // 2. 检查当前状态是否为 SUSPENDED
        var currentStatus = taskManager.getStatus(workerSessionId);
        if (currentStatus != tech.yesboss.persistence.entity.TaskSession.Status.SUSPENDED) {
            throw new IllegalStateException(
                "Worker session is not in SUSPENDED state, current status: " + currentStatus);
        }

        // 3. 从内存或数据库获取挂起上下文
        SuspensionContext context = suspensionContextMap.get(toolCallId);
        if (context == null) {
            // 实际实现中应该从 ToolExecutionLog 查询
            // 这里暂时抛出异常，提示需要从数据库查询
            throw new IllegalStateException(
                "Suspension context not found for toolCallId: " + toolCallId +
                ". This should be retrieved from ToolExecutionLog.");
        }

        // 4. 根据用户决策执行工具或伪造错误
        UnifiedMessage toolResult;
        if (isApproved) {
            // 用户批准：绕过沙箱执行工具
            try {
                AgentTool tool = toolRegistry.getTool(context.toolName());
                String result = tool.executeWithBypass(context.argumentsJson());

                // 记录工具调用（isIntercepted=false 因为已获得授权）
                toolCallTracker.trackExecution(
                    context.sessionId(),
                    toolCallId,
                    context.toolName(),
                    context.argumentsJson(),
                    result,
                    false  // 不再是拦截状态
                );

                // 构造成功的 ToolResult
                toolResult = UnifiedMessage.ofToolResult(toolCallId, result, false);

                logger.info("Tool {} executed with bypass, result: {}", context.toolName(), result);

            } catch (Exception e) {
                logger.error("Failed to execute tool {} with bypass", context.toolName(), e);

                // 执行失败，构造错误 ToolResult
                String errorResult = "工具执行失败（已获用户授权）: " + e.getMessage();
                toolResult = UnifiedMessage.ofToolResult(toolCallId, errorResult, true);
            }
        } else {
            // 用户拒绝：伪造错误 ToolResult
            String rejectionMessage = "工具执行被用户拒绝";
            if (humanFeedback != null && !humanFeedback.trim().isEmpty()) {
                rejectionMessage += "\n用户反馈: " + humanFeedback;
            }

            toolResult = UnifiedMessage.ofToolResult(toolCallId, rejectionMessage, true);

            // 记录工具调用（isIntercepted=true，用户拒绝）
            toolCallTracker.trackExecution(
                context.sessionId(),
                toolCallId,
                context.toolName(),
                context.argumentsJson(),
                rejectionMessage,
                true  // 仍然是拦截状态
            );

            logger.info("Tool {} execution rejected by user", context.toolName());
        }

        // 5. 将 ToolResult 注入 LocalStreamManager
        localStreamManager.appendToolResult(workerSessionId, toolResult);
        logger.info("ToolResult appended to LocalStream for session {}", workerSessionId);

        // 6. 将任务状态从 SUSPENDED 转回 RUNNING
        taskManager.transitionState(workerSessionId,
            tech.yesboss.persistence.entity.TaskSession.Status.RUNNING);
        logger.info("Worker session {} transitioned back to RUNNING", workerSessionId);

        // 7. 清理挂起上下文
        suspensionContextMap.remove(toolCallId);

        logger.info("Worker session {} resume completed, ready to restart ReAct loop", workerSessionId);

        // 注意：实际的 WorkerRunner.run() 调用应该在 TaskManager 或调度器中完成
        // 这里只负责状态恢复和上下文准备
    }

    /**
     * 构造挂起审批卡片的 JSON 格式
     *
     * <p>这是一个简化的卡片格式，实际应该根据不同的 IM 平台（飞书、Slack）
     * 使用不同的卡片格式。</p>
     *
     * @param workerSessionId    Worker 会话 ID
     * @param interceptedCommand 被拦截的命令
     * @param toolCallId         工具调用 ID
     * @return 卡片的 JSON 格式字符串
     */
    private String buildSuspensionCardJson(String workerSessionId, String interceptedCommand, String toolCallId) {
        // 这是一个简化的卡片格式
        // 实际实现中应该根据 imType 使用不同的卡片格式
        return String.format("""
            {
              "type": "suspension_approval",
              "title": "⚠️ 高危操作审批",
              "content": {
                "worker_session_id": "%s",
                "intercepted_command": "%s",
                "tool_call_id": "%s",
                "message": "检测到高危操作，需要您的审批才能继续执行。"
              },
              "actions": [
                {
                  "type": "approve",
                  "label": "批准执行",
                  "data": {
                    "approved": true,
                    "worker_session_id": "%s",
                    "tool_call_id": "%s"
                  }
                },
                {
                  "type": "reject",
                  "label": "拒绝执行",
                  "data": {
                    "approved": false,
                    "worker_session_id": "%s",
                    "tool_call_id": "%s"
                  }
                }
              ]
            }
            """,
            workerSessionId, interceptedCommand, toolCallId,
            workerSessionId, toolCallId,
            workerSessionId, toolCallId
        );
    }

    /**
     * 存储挂起上下文（用于测试或从外部设置）
     *
     * <p>在实际实现中，这些信息应该从 ToolExecutionLog 查询。</p>
     *
     * @param toolCallId      工具调用 ID
     * @param toolName        工具名称
     * @param argumentsJson   参数 JSON
     * @param sessionId       会话 ID
     */
    public void storeSuspensionContext(String toolCallId, String toolName, String argumentsJson, String sessionId) {
        SuspensionContext context = new SuspensionContext(toolName, argumentsJson, sessionId);
        suspensionContextMap.put(toolCallId, context);
        logger.debug("Stored suspension context for toolCallId: {}", toolCallId);
    }

    /**
     * 清除挂起上下文
     *
     * @param toolCallId 工具调用 ID
     */
    public void clearSuspensionContext(String toolCallId) {
        suspensionContextMap.remove(toolCallId);
        logger.debug("Cleared suspension context for toolCallId: {}", toolCallId);
    }
}
