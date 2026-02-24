package tech.yesboss.runner.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.context.GlobalStreamManager;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.gateway.im.IMMessagePusher;
import tech.yesboss.gateway.ui.UICardRenderer;
import tech.yesboss.llm.LlmClient;
import tech.yesboss.llm.impl.ModelRouter;
import tech.yesboss.runner.MasterRunner;
import tech.yesboss.runner.WorkerRunner;
import tech.yesboss.state.TaskManager;
import tech.yesboss.state.model.ImRoute;
import tech.yesboss.tool.AgentTool;
import tech.yesboss.tool.planning.PlanningTool;
import tech.yesboss.tool.registry.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

/**
 * 总 Agent 执行器实现 (Master Runner Implementation)
 *
 * <p>Master Agent 的骨架实现，负责整体任务规划和子任务调度。</p>
 *
 * <p><b>执行流程：</b></p>
 * <ol>
 *   <li>需求澄清（通过 IM 交互）</li>
 *   <li>环境探索（使用只读工具）</li>
 *   <li>制定执行计划（调用 PlanningTool）</li>
 *   <li>创建 Worker 子任务</li>
 *   <li>监控 Worker 执行</li>
 *   <li>生成最终总结</li>
 * </ol>
 */
public class MasterRunnerImpl implements MasterRunner {

    private static final Logger logger = LoggerFactory.getLogger(MasterRunnerImpl.class);

    private final TaskManager taskManager;
    private final GlobalStreamManager globalStreamManager;
    private final ModelRouter modelRouter;
    private final ToolRegistry toolRegistry;
    private final WorkerRunner workerRunner;
    private final PlanningTool planningTool;
    private final UICardRenderer uiCardRenderer;
    private final IMMessagePusher imMessagePusher;
    private final ExecutorService virtualThreadExecutor;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数
     *
     * @param taskManager         任务管理器
     * @param globalStreamManager 全局流管理器
     * @param modelRouter         模型路由器
     * @param toolRegistry        工具注册表
     * @param workerRunner        Worker 执行器
     * @param planningTool        规划工具
     * @param uiCardRenderer      UI 卡片渲染器
     * @param imMessagePusher     IM 消息推送器
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    public MasterRunnerImpl(
            TaskManager taskManager,
            GlobalStreamManager globalStreamManager,
            ModelRouter modelRouter,
            ToolRegistry toolRegistry,
            WorkerRunner workerRunner,
            PlanningTool planningTool,
            UICardRenderer uiCardRenderer,
            IMMessagePusher imMessagePusher) {
        if (taskManager == null) {
            throw new IllegalArgumentException("taskManager cannot be null");
        }
        if (globalStreamManager == null) {
            throw new IllegalArgumentException("globalStreamManager cannot be null");
        }
        if (modelRouter == null) {
            throw new IllegalArgumentException("modelRouter cannot be null");
        }
        if (toolRegistry == null) {
            throw new IllegalArgumentException("toolRegistry cannot be null");
        }
        if (workerRunner == null) {
            throw new IllegalArgumentException("workerRunner cannot be null");
        }
        if (planningTool == null) {
            throw new IllegalArgumentException("planningTool cannot be null");
        }
        if (uiCardRenderer == null) {
            throw new IllegalArgumentException("uiCardRenderer cannot be null");
        }
        if (imMessagePusher == null) {
            throw new IllegalArgumentException("imMessagePusher cannot be null");
        }

        this.taskManager = taskManager;
        this.globalStreamManager = globalStreamManager;
        this.modelRouter = modelRouter;
        this.toolRegistry = toolRegistry;
        this.workerRunner = workerRunner;
        this.planningTool = planningTool;
        this.uiCardRenderer = uiCardRenderer;
        this.imMessagePusher = imMessagePusher;
        this.objectMapper = new ObjectMapper();

        // 创建线程池用于并发执行
        // Note: Using cached thread pool for Java 17 compatibility
        // When upgraded to Java 21+, use Executors.newVirtualThreadPerTaskExecutor()
        this.virtualThreadExecutor = Executors.newCachedThreadPool();

        logger.info("MasterRunnerImpl initialized with all dependencies");
    }

    @Override
    public void run(String sessionId) {
        logger.info("MasterRunner starting for session {}", sessionId);

        // 参数校验
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId cannot be null or empty");
        }

        try {
            // 0. 检查会话状态
            if (!taskManager.sessionExists(sessionId)) {
                throw new IllegalStateException("Session not found: " + sessionId);
            }

            var status = taskManager.getStatus(sessionId);
            logger.info("Session {} current status: {}", sessionId, status);

            // 1. 需求澄清阶段
            if (!clarifyRequirements(sessionId)) {
                // 需求不清晰，已推送交互问题，退出等待用户响应
                logger.info("Requirements clarification needed, yielding for user response");
                return;
            }

            // 2. 环境探索阶段（使用只读工具）
            String explorationContext = exploreEnvironment(sessionId);
            logger.info("Environment exploration completed for session {}", sessionId);

            // 3. 生成执行计划
            List<Map<String, String>> executionPlan = generateExecutionPlan(sessionId, explorationContext);
            logger.info("Generated execution plan with {} tasks for session {}", executionPlan.size(), sessionId);

            // 4. 转换状态为 RUNNING
            taskManager.transitionState(sessionId, tech.yesboss.persistence.entity.TaskSession.Status.RUNNING);

            // 5. 创建 Worker 子任务并执行
            List<String> workerSessionIds = createAndExecuteWorkerTasks(sessionId, executionPlan);

            // 6. 监控 Worker 执行并等待完成
            boolean allWorkersCompleted = waitForWorkersCompletion(sessionId, workerSessionIds);

            // 7. 生成最终总结
            if (allWorkersCompleted) {
                generateFinalSummary(sessionId, workerSessionIds);
                taskManager.transitionState(sessionId, tech.yesboss.persistence.entity.TaskSession.Status.COMPLETED);
                logger.info("MasterRunner completed successfully for session {}", sessionId);
            } else {
                logger.error("Some workers failed for session {}", sessionId);
                taskManager.transitionState(sessionId, tech.yesboss.persistence.entity.TaskSession.Status.FAILED);
            }

        } catch (Exception e) {
            logger.error("MasterRunner execution failed for session {}", sessionId, e);
            try {
                taskManager.transitionState(sessionId, tech.yesboss.persistence.entity.TaskSession.Status.FAILED);
            } catch (Exception stateException) {
                logger.error("Failed to transition session to FAILED state", stateException);
            }
            throw e;
        }
    }

    /**
     * 需求澄清
     *
     * @param sessionId 会话 ID
     * @return true 如果需求清晰，false 如果需要等待用户响应
     */
    private boolean clarifyRequirements(String sessionId) {
        logger.info("Step 1: Requirement clarification for session {}", sessionId);

        try {
            // 获取全局上下文
            List<UnifiedMessage> context = globalStreamManager.fetchContext(sessionId);

            // 如果上下文为空或只有初始消息，需要澄清
            if (context.isEmpty() || context.size() < 2) {
                logger.info("Context insufficient, initiating clarification for session {}", sessionId);
                pushClarificationQuestion(sessionId);
                return false;
            }

            // 调用 LLM 判断需求是否清晰
            LlmClient llmClient = modelRouter.routeByRole("MASTER");
            String systemPrompt = "分析以下对话历史，判断用户需求是否清晰明确。如果清晰，返回 'CLEAR'。如果不清晰或需要更多信息，返回 'UNCLEAR: ' 后面跟随具体问题。";

            UnifiedMessage response = llmClient.chat(context, systemPrompt);
            String responseText = response.content().trim();

            if (responseText.startsWith("UNCLEAR:")) {
                String question = responseText.substring("UNCLEAR:".length()).trim();
                logger.info("Requirements unclear, pushing question: {}", question);
                pushClarificationQuestion(sessionId, question);
                return false;
            }

            logger.info("Requirements clarified for session {}", sessionId);
            return true;

        } catch (Exception e) {
            logger.error("Error during requirement clarification for session {}", sessionId, e);
            // 发生错误时，继续执行（乐观策略）
            return true;
        }
    }

    /**
     * 环境探索（使用只读工具）
     *
     * @param sessionId 会话 ID
     * @return 探索结果摘要
     */
    private String exploreEnvironment(String sessionId) {
        logger.info("Step 2: Environment exploration for session {}", sessionId);

        try {
            // 获取只读工具
            List<AgentTool> readOnlyTools = toolRegistry.getAvailableTools("MASTER");

            if (readOnlyTools.isEmpty()) {
                logger.info("No read-only tools available for exploration");
                return "No exploration tools available";
            }

            StringBuilder explorationResult = new StringBuilder();
            explorationResult.append("Environment Exploration Results:\n\n");

            // 获取全局上下文
            List<UnifiedMessage> context = globalStreamManager.fetchContext(sessionId);

            // 调用 LLM 决定是否需要探索以及如何探索
            LlmClient llmClient = modelRouter.routeByRole("MASTER");

            // 构建系统提示词，引导 LLM 进行环境探索
            String systemPrompt = """
                你是一个环境探索专家。你有以下只读工具可以使用：
                %s

                请分析当前任务，判断是否需要进行环境探索。
                如果不需要，返回 "NO_EXPLORATION_NEEDED"。
                如果需要，返回具体的探索指令（使用哪些工具，查找什么信息）。
                """.formatted(readOnlyTools.toString());

            UnifiedMessage response = llmClient.chat(context, systemPrompt);
            String responseText = response.content().trim();

            if (responseText.contains("NO_EXPLORATION_NEEDED")) {
                logger.info("No exploration needed for session {}", sessionId);
                return "No exploration performed";
            }

            // 记录探索决策
            explorationResult.append("Exploration Decision: ").append(responseText).append("\n\n");

            // 简化实现：记录探索意图，实际工具执行在后续迭代中实现
            // TODO: 在后续版本中，可以在这里实际调用只读工具
            logger.info("Environment exploration decision: {}", responseText);

            return explorationResult.toString();

        } catch (Exception e) {
            logger.error("Error during environment exploration for session {}", sessionId, e);
            return "Exploration failed: " + e.getMessage();
        }
    }

    /**
     * 生成执行计划（使用 PlanningTool）
     *
     * @param sessionId 会话 ID
     * @param explorationContext 探索上下文
     * @return 执行计划（任务列表）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, String>> generateExecutionPlan(String sessionId, String explorationContext) {
        logger.info("Step 3: Generating execution plan for session {}", sessionId);

        try {
            // 获取全局上下文
            List<UnifiedMessage> context = globalStreamManager.fetchContext(sessionId);

            // 从上下文中提取原始任务描述
            String taskDescription = extractTaskDescription(context);

            // 构建 PlanningTool 参数
            Map<String, String> planningRequest = new ConcurrentHashMap<>();
            planningRequest.put("taskDescription", taskDescription);
            planningRequest.put("context", explorationContext);
            planningRequest.put("constraints", "Use available tools and complete tasks efficiently");

            String requestJson = objectMapper.writeValueAsString(planningRequest);

            // 调用 PlanningTool
            String planJson = planningTool.execute(requestJson);
            logger.info("PlanningTool returned: {}", planJson);

            // 解析 JSON 数组
            List<Map<String, String>> plan = objectMapper.readValue(
                planJson,
                new TypeReference<List<Map<String, String>>>() {}
            );

            logger.info("Parsed execution plan with {} tasks", plan.size());
            return plan;

        } catch (Exception e) {
            logger.error("Error generating execution plan for session {}", sessionId, e);
            throw new RuntimeException("Failed to generate execution plan", e);
        }
    }

    /**
     * 创建并执行 Worker 任务
     *
     * @param masterSessionId Master 会话 ID
     * @param executionPlan 执行计划
     * @return Worker 会话 ID 列表
     */
    private List<String> createAndExecuteWorkerTasks(String masterSessionId, List<Map<String, String>> executionPlan) {
        logger.info("Step 4-5: Creating and executing {} worker tasks for session {}", executionPlan.size(), masterSessionId);

        List<String> workerSessionIds = new ArrayList<>();

        try {
            for (Map<String, String> task : executionPlan) {
                String taskId = task.get("id");
                String description = task.get("description");
                String priority = task.get("priority");

                logger.info("Creating worker task: id={}, description={}, priority={}", taskId, description, priority);

                // 创建 Worker 任务
                String workerSessionId = taskManager.createWorkerTask(masterSessionId, description);
                workerSessionIds.add(workerSessionId);

                logger.info("Created worker session {} for task {}", workerSessionId, taskId);

                // 异步执行 Worker 任务
                final String currentWorkerSessionId = workerSessionId;
                virtualThreadExecutor.submit(() -> {
                    try {
                        logger.info("Starting worker execution for session {}", currentWorkerSessionId);
                        workerRunner.run(currentWorkerSessionId);
                    } catch (Exception e) {
                        logger.error("Worker execution failed for session {}", currentWorkerSessionId, e);
                    }
                });
            }

            logger.info("All {} worker tasks created and started", workerSessionIds.size());
            return workerSessionIds;

        } catch (Exception e) {
            logger.error("Error creating worker tasks for session {}", masterSessionId, e);
            throw new RuntimeException("Failed to create worker tasks", e);
        }
    }

    /**
     * 等待所有 Worker 完成
     *
     * @param masterSessionId Master 会话 ID
     * @param workerSessionIds Worker 会话 ID 列表
     * @return true 如果所有 Worker 都成功完成，false 如果有任何 Worker 失败
     */
    private boolean waitForWorkersCompletion(String masterSessionId, List<String> workerSessionIds) {
        logger.info("Step 6: Monitoring {} workers for session {}", workerSessionIds.size(), masterSessionId);

        try {
            int totalTasks = workerSessionIds.size();
            int completedCount = 0;
            int failedCount = 0;
            long maxWaitTime = 60 * 60 * 1000; // 最多等待 1 小时
            long startTime = System.currentTimeMillis();

            while (completedCount + failedCount < totalTasks) {
                // 检查超时
                if (System.currentTimeMillis() - startTime > maxWaitTime) {
                    logger.warn("Worker monitoring timeout for session {}", masterSessionId);
                    return false;
                }

                completedCount = 0;
                failedCount = 0;

                for (String workerSessionId : workerSessionIds) {
                    var status = taskManager.getStatus(workerSessionId);
                    if (status == tech.yesboss.persistence.entity.TaskSession.Status.COMPLETED) {
                        completedCount++;
                    } else if (status == tech.yesboss.persistence.entity.TaskSession.Status.FAILED) {
                        failedCount++;
                    }
                }

                // 更新进度条
                int totalCompleted = completedCount + failedCount;
                if (totalCompleted > 0 && totalCompleted <= totalTasks) {
                    updateProgress(masterSessionId, totalTasks, totalCompleted);
                }

                // 等待一段时间再检查
                Thread.sleep(2000); // 每 2 秒检查一次
            }

            logger.info("Workers monitoring completed: {} completed, {} failed for session {}",
                        completedCount, failedCount, masterSessionId);

            return failedCount == 0;

        } catch (InterruptedException e) {
            logger.error("Worker monitoring interrupted for session {}", masterSessionId, e);
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            logger.error("Error monitoring workers for session {}", masterSessionId, e);
            return false;
        }
    }

    /**
     * 生成最终总结
     *
     * @param masterSessionId Master 会话 ID
     * @param workerSessionIds Worker 会话 ID 列表
     */
    private void generateFinalSummary(String masterSessionId, List<String> workerSessionIds) {
        logger.info("Step 7: Generating final summary for session {}", masterSessionId);

        try {
            StringBuilder summary = new StringBuilder();
            summary.append("# Task Execution Summary\n\n");

            // 收集所有 Worker 的执行报告
            for (String workerSessionId : workerSessionIds) {
                try {
                    String report = workerRunner.generateExecutionReport(workerSessionId);
                    summary.append("## Worker Report\n").append(report).append("\n\n");
                } catch (Exception e) {
                    logger.warn("Failed to get report from worker {}", workerSessionId, e);
                    summary.append("## Worker Report\n").append("Report unavailable\n\n");
                }
            }

            String summaryText = summary.toString();
            logger.info("Generated summary: {}", summaryText);

            // 渲染总结卡片
            var summaryCard = uiCardRenderer.renderSummaryCard(masterSessionId, summaryText, true);

            // 推送总结卡片
            ImRoute imRoute = taskManager.getImRoute(masterSessionId);
            imMessagePusher.pushCardMessage(imRoute.imType(), imRoute.imGroupId(), summaryCard.toString());

            // 将总结追加到全局流
            globalStreamManager.appendMasterMessage(masterSessionId, UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, summaryText));

            logger.info("Final summary generated and pushed for session {}", masterSessionId);

        } catch (Exception e) {
            logger.error("Error generating final summary for session {}", masterSessionId, e);
        }
    }

    /**
     * 从上下文中提取任务描述
     *
     * @param context 上下文列表
     * @return 任务描述
     */
    private String extractTaskDescription(List<UnifiedMessage> context) {
        if (context.isEmpty()) {
            return "Unknown task";
        }

        // 查找第一条用户消息作为任务描述
        for (UnifiedMessage message : context) {
            if (message.role() == UnifiedMessage.Role.USER) {
                return message.content();
            }
        }

        // 如果没有用户消息，返回第一条消息的内容
        return context.get(0).content();
    }

    /**
     * 推送澄清问题
     *
     * @param sessionId 会话 ID
     */
    private void pushClarificationQuestion(String sessionId) {
        pushClarificationQuestion(sessionId, "Please provide more details about your requirement.");
    }

    /**
     * 推送澄清问题
     *
     * @param sessionId 会话 ID
     * @param question 问题文本
     */
    private void pushClarificationQuestion(String sessionId, String question) {
        try {
            ImRoute imRoute = taskManager.getImRoute(sessionId);
            var card = uiCardRenderer.renderSummaryCard(sessionId, "Question: " + question, false);
            imMessagePusher.pushCardMessage(imRoute.imType(), imRoute.imGroupId(), card.toString());

            // 追加到全局流
            globalStreamManager.appendSystemMessage(sessionId, "Clarification question sent to user: " + question);

            logger.info("Clarification question pushed for session {}", sessionId);
        } catch (Exception e) {
            logger.error("Error pushing clarification question for session {}", sessionId, e);
        }
    }

    /**
     * 更新进度条
     *
     * @param sessionId 会话 ID
     * @param totalTasks 总任务数
     * @param completedTasks 已完成任务数
     */
    private void updateProgress(String sessionId, int totalTasks, int completedTasks) {
        try {
            String currentTask = String.format("Progress: %d/%d tasks completed", completedTasks, totalTasks);
            var progressBar = uiCardRenderer.renderProgressBar(totalTasks, completedTasks, currentTask);

            ImRoute imRoute = taskManager.getImRoute(sessionId);
            imMessagePusher.pushCardMessage(imRoute.imType(), imRoute.imGroupId(), progressBar.toString());

            logger.debug("Progress updated for session {}: {}/{}", sessionId, completedTasks, totalTasks);
        } catch (Exception e) {
            logger.warn("Error updating progress for session {}", sessionId, e);
        }
    }

    @Override
    public String generateExecutionPlan(String sessionId) {
        logger.info("Generating execution plan for session {} (legacy method)", sessionId);

        // 检查会话状态
        if (!taskManager.sessionExists(sessionId)) {
            throw new IllegalStateException("Session not found: " + sessionId);
        }

        var status = taskManager.getStatus(sessionId);
        if (status != tech.yesboss.persistence.entity.TaskSession.Status.PLANNING) {
            throw new IllegalStateException(
                "Session is not in PLANNING status, current: " + status);
        }

        // 使用新的规划逻辑
        List<Map<String, String>> plan = generateExecutionPlan(sessionId, "");

        try {
            return objectMapper.writeValueAsString(plan);
        } catch (Exception e) {
            logger.error("Error converting plan to JSON", e);
            return "[]";
        }
    }

    /**
     * 获取虚拟线程执行器
     *
     * <p>用于在虚拟线程中并发执行多个 Worker 任务。</p>
     *
     * @return 虚拟线程执行器
     */
    public ExecutorService getVirtualThreadExecutor() {
        return virtualThreadExecutor;
    }

    /**
     * 关闭执行器
     *
     * <p>在应用关闭时调用，释放资源。</p>
     */
    public void shutdown() {
        logger.info("Shutting down MasterRunner virtual thread executor");
        virtualThreadExecutor.shutdown();
    }
}
