package tech.yesboss.runner.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.context.LocalStreamManager;
import tech.yesboss.context.engine.InjectionEngine;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.llm.LlmClient;
import tech.yesboss.llm.impl.ModelRouter;
import tech.yesboss.runner.WorkerRunner;
import tech.yesboss.safeguard.CircuitBreaker;
import tech.yesboss.state.TaskManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 子 Agent 执行器实现 (Worker Runner Implementation)
 *
 * <p>Worker Agent 的骨架实现，负责具体任务的执行和工具调用。</p>
 *
 * <p><b>核心特性：</b></p>
 * <ul>
 *   <li>支持从挂起状态唤醒（通过 isResumingFromSuspension 判断）</li>
 *   <li>ReAct 循环执行（推理 + 行动）</li>
 *   <li>熔断器保护（防止无限循环）</li>
 *   <li>人机回环支持（黑名单拦截）</li>
 * </ul>
 */
public class WorkerRunnerImpl implements WorkerRunner {

    private static final Logger logger = LoggerFactory.getLogger(WorkerRunnerImpl.class);

    private final TaskManager taskManager;
    private final LocalStreamManager localStreamManager;
    private final InjectionEngine injectionEngine;
    private final ModelRouter modelRouter;
    private final CircuitBreaker circuitBreaker;
    private final ExecutorService virtualThreadExecutor;

    /**
     * 构造函数
     *
     * @param taskManager         任务管理器
     * @param localStreamManager  局部流管理器
     * @param injectionEngine     上下文注入引擎
     * @param modelRouter         模型路由器
     * @param circuitBreaker      熔断器
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    public WorkerRunnerImpl(
            TaskManager taskManager,
            LocalStreamManager localStreamManager,
            InjectionEngine injectionEngine,
            ModelRouter modelRouter,
            CircuitBreaker circuitBreaker) {
        if (taskManager == null) {
            throw new IllegalArgumentException("taskManager cannot be null");
        }
        if (localStreamManager == null) {
            throw new IllegalArgumentException("localStreamManager cannot be null");
        }
        if (injectionEngine == null) {
            throw new IllegalArgumentException("injectionEngine cannot be null");
        }
        if (modelRouter == null) {
            throw new IllegalArgumentException("modelRouter cannot be null");
        }
        if (circuitBreaker == null) {
            throw new IllegalArgumentException("circuitBreaker cannot be null");
        }

        this.taskManager = taskManager;
        this.localStreamManager = localStreamManager;
        this.injectionEngine = injectionEngine;
        this.modelRouter = modelRouter;
        this.circuitBreaker = circuitBreaker;

        // 创建线程池
        // Note: Using cached thread pool for Java 17 compatibility
        // When upgraded to Java 21+, use Executors.newVirtualThreadPerTaskExecutor()
        this.virtualThreadExecutor = Executors.newCachedThreadPool();

        logger.info("WorkerRunnerImpl initialized with thread pool executor");
    }

    @Override
    public void run(String sessionId) {
        logger.info("WorkerRunner starting for session {}", sessionId);

        // 参数校验
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId cannot be null or empty");
        }

        try {
            // 检查会话状态
            if (!taskManager.sessionExists(sessionId)) {
                throw new IllegalStateException("Session not found: " + sessionId);
            }

            // 1. 【核心规约】判定是否为唤醒状态
            boolean isResuming = isResumingFromSuspension(sessionId);
            logger.info("WorkerRunner for session {} is resuming from suspension: {}", sessionId, isResuming);

            // 2. 如果不是唤醒状态，执行初始化流程
            if (!isResuming) {
                logger.info("New worker session, performing initialization");

                // 获取父级 Master Session ID
                String masterSessionId = taskManager.getParentSessionId(sessionId);
                if (masterSessionId == null) {
                    throw new IllegalStateException("Worker has no parent Master session");
                }

                // 获取分配的任务（从 TaskSession 的 plan_json 字段中）
                // TODO: 在完整实现中，这里应该从数据库读取分配的任务
                String assignedTask = "Assigned task placeholder";  // 骨架实现

                // 注入初始上下文
                UnifiedMessage initialPrompt = injectionEngine.injectInitialContext(
                    masterSessionId, assignedTask);

                // 追加到局部流
                localStreamManager.appendWorkerMessage(sessionId, initialPrompt);

                logger.info("Initial context injected for session {}", sessionId);
            } else {
                logger.info("Resuming from suspension, skipping initialization");
            }

            // 3. 开始/继续 ReAct 循环
            logger.info("Starting ReAct loop for session {}", sessionId);

            // TODO: 完整的 ReAct 循环将在后续任务中实现
            // 当前是骨架实现，包含基本结构

            // 拉取局部流上下文
            List<UnifiedMessage> context = localStreamManager.fetchContext(sessionId);
            logger.info("Fetched {} messages from local stream for session {}", context.size(), sessionId);

            // ReAct 循环占位符
            // while (running) {
            //     // 1. 检查熔断器
            //     circuitBreaker.checkAndIncrement(sessionId);
            //
            //     // 2. 调用 LLM 推理
            //     LlmClient llmClient = modelRouter.routeByRole("WORKER");
            //     UnifiedMessage response = llmClient.chat(context);
            //
            //     // 3. 处理工具调用
            //     if (response.hasToolCalls()) {
            //         // 执行工具
            //     }
            //
            //     // 4. 更新上下文
            // }

            logger.info("WorkerRunner ReAct loop completed for session {}", sessionId);

        } catch (Exception e) {
            logger.error("WorkerRunner execution failed for session {}", sessionId, e);
            throw e;
        }
    }

    @Override
    public boolean isResumingFromSuspension(String sessionId) {
        logger.debug("Checking if session {} is resuming from suspension", sessionId);

        // 参数校验
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId cannot be null or empty");
        }

        // 拉取局部流历史
        List<UnifiedMessage> context = localStreamManager.fetchContext(sessionId);

        // 判定逻辑：
        // - 如果上下文为空 → 新创建的 Worker → 返回 false
        // - 如果最后一条消息是 ToolResult → 从挂起状态唤醒 → 返回 true
        // - 否则 → 新创建的 Worker → 返回 false

        if (context.isEmpty()) {
            logger.debug("Local stream is empty for session {}, treating as new worker", sessionId);
            return false;
        }

        UnifiedMessage lastMessage = context.get(context.size() - 1);

        // 检查最后一条消息是否包含 ToolResult
        // 根据 UnifiedMessage 的设计，ToolResult 体现在 toolResults 列表中
        boolean hasToolResult = !lastMessage.toolResults().isEmpty();

        if (hasToolResult) {
            logger.info("Last message contains ToolResult, session {} is resuming from suspension", sessionId);
            return true;
        }

        logger.debug("Last message does not contain ToolResult, treating session {} as new worker", sessionId);
        return false;
    }

    @Override
    public String generateExecutionReport(String sessionId) {
        logger.info("Generating execution report for session {}", sessionId);

        // 检查会话状态
        if (!taskManager.sessionExists(sessionId)) {
            throw new IllegalStateException("Session not found: " + sessionId);
        }

        // TODO: 完整的执行报告生成将在后续任务中实现
        // 当前返回一个示例报告

        String report = String.format("""
            Worker Execution Report
            =======================
            Session ID: %s
            Status: COMPLETED
            Summary: Task executed successfully
            """, sessionId);

        logger.info("Execution report generated for session {}", sessionId);
        return report;
    }

    /**
     * 获取虚拟线程执行器
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
        logger.info("Shutting down WorkerRunner virtual thread executor");
        virtualThreadExecutor.shutdown();
    }
}
