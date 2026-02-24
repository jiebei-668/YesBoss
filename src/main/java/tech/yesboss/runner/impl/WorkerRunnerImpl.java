package tech.yesboss.runner.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.context.LocalStreamManager;
import tech.yesboss.context.engine.CondensationEngine;
import tech.yesboss.context.engine.InjectionEngine;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.llm.LlmClient;
import tech.yesboss.llm.impl.ModelRouter;
import tech.yesboss.runner.WorkerRunner;
import tech.yesboss.safeguard.CircuitBreaker;
import tech.yesboss.safeguard.SuspendResumeEngine;
import tech.yesboss.state.TaskManager;
import tech.yesboss.tool.SuspendExecutionException;
import tech.yesboss.tool.tracker.ToolCallTracker;
import tech.yesboss.tool.registry.ToolRegistry;
import tech.yesboss.tool.sandbox.SandboxInterceptor;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 子 Agent 执行器实现 (Worker Runner Implementation)
 *
 * <p>Worker Agent 的完整实现，负责具体任务的执行和工具调用。</p>
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
    private final CondensationEngine condensationEngine;
    private final ModelRouter modelRouter;
    private final CircuitBreaker circuitBreaker;
    private final ToolRegistry toolRegistry;
    private final SandboxInterceptor sandboxInterceptor;
    private final SuspendResumeEngine suspendResumeEngine;
    private final ToolCallTracker toolCallTracker;
    private final ExecutorService virtualThreadExecutor;

    /**
     * 构造函数
     *
     * @param taskManager         任务管理器
     * @param localStreamManager  局部流管理器
     * @param injectionEngine     上下文注入引擎
     * @param condensationEngine  上下文冷凝引擎
     * @param modelRouter         模型路由器
     * @param circuitBreaker      熔断器
     * @param toolRegistry        工具注册表
     * @param sandboxInterceptor  沙箱拦截器
     * @param suspendResumeEngine 挂起恢复引擎
     * @param toolCallTracker     工具调用追踪器
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    public WorkerRunnerImpl(
            TaskManager taskManager,
            LocalStreamManager localStreamManager,
            InjectionEngine injectionEngine,
            CondensationEngine condensationEngine,
            ModelRouter modelRouter,
            CircuitBreaker circuitBreaker,
            ToolRegistry toolRegistry,
            SandboxInterceptor sandboxInterceptor,
            SuspendResumeEngine suspendResumeEngine,
            ToolCallTracker toolCallTracker) {
        if (taskManager == null) {
            throw new IllegalArgumentException("taskManager cannot be null");
        }
        if (localStreamManager == null) {
            throw new IllegalArgumentException("localStreamManager cannot be null");
        }
        if (injectionEngine == null) {
            throw new IllegalArgumentException("injectionEngine cannot be null");
        }
        if (condensationEngine == null) {
            throw new IllegalArgumentException("condensationEngine cannot be null");
        }
        if (modelRouter == null) {
            throw new IllegalArgumentException("modelRouter cannot be null");
        }
        if (circuitBreaker == null) {
            throw new IllegalArgumentException("circuitBreaker cannot be null");
        }
        if (toolRegistry == null) {
            throw new IllegalArgumentException("toolRegistry cannot be null");
        }
        if (sandboxInterceptor == null) {
            throw new IllegalArgumentException("sandboxInterceptor cannot be null");
        }
        if (suspendResumeEngine == null) {
            throw new IllegalArgumentException("suspendResumeEngine cannot be null");
        }
        if (toolCallTracker == null) {
            throw new IllegalArgumentException("toolCallTracker cannot be null");
        }

        this.taskManager = taskManager;
        this.localStreamManager = localStreamManager;
        this.injectionEngine = injectionEngine;
        this.condensationEngine = condensationEngine;
        this.modelRouter = modelRouter;
        this.circuitBreaker = circuitBreaker;
        this.toolRegistry = toolRegistry;
        this.sandboxInterceptor = sandboxInterceptor;
        this.suspendResumeEngine = suspendResumeEngine;
        this.toolCallTracker = toolCallTracker;

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

            boolean running = true;
            int loopIteration = 0;

            while (running) {
                loopIteration++;
                logger.debug("ReAct loop iteration {} for session {}", loopIteration, sessionId);

                // 3.1 检查熔断器
                circuitBreaker.checkAndIncrement(sessionId);
                logger.debug("CircuitBreaker check passed for iteration {}", loopIteration);

                // 3.2 拉取局部流上下文
                List<UnifiedMessage> context = localStreamManager.fetchContext(sessionId);
                logger.debug("Fetched {} messages from local stream", context.size());

                // 3.3 调用 LLM 推理
                LlmClient llmClient = modelRouter.routeByRole("WORKER");
                logger.debug("Calling LLM for ReAct iteration {}", loopIteration);

                UnifiedMessage response = llmClient.chat(context, "");
                logger.info("LLM response received: hasToolCalls={}, isTextOnly={}",
                    response.hasToolCalls(), response.isTextOnly());

                // 3.4 追加 LLM 响应到局部流
                localStreamManager.appendWorkerMessage(sessionId, response);
                logger.debug("Appended LLM response to local stream");

                // 3.5 处理工具调用
                if (response.hasToolCalls()) {
                    logger.info("Processing {} tool calls", response.toolCalls().size());

                    for (UnifiedMessage.ToolCall toolCall : response.toolCalls()) {
                        logger.debug("Executing tool: {} with toolCallId: {}",
                            toolCall.name(), toolCall.id());

                        try {
                            // 3.5.1 沙箱预检
                            sandboxInterceptor.preCheck(
                                toolRegistry.getTool(toolCall.name()),
                                toolCall.argumentsJson(),
                                toolCall.id()
                            );

                            // 3.5.2 执行工具
                            tech.yesboss.tool.AgentTool tool = toolRegistry.getTool(toolCall.name());
                            String toolResult = tool.execute(toolCall.argumentsJson());

                            // 3.5.3 追踪工具调用
                            toolCallTracker.trackExecution(
                                sessionId,
                                toolCall.id(),
                                toolCall.name(),
                                toolCall.argumentsJson(),
                                toolResult,
                                false  // isIntercepted = false
                            );

                            // 3.5.4 追加工具结果到局部流
                            UnifiedMessage resultMessage = UnifiedMessage.ofToolResult(
                                toolCall.id(), toolResult, false);
                            localStreamManager.appendToolResult(sessionId, resultMessage);

                            logger.info("Tool {} executed successfully", toolCall.name());

                        } catch (SuspendExecutionException e) {
                            // 3.5.5 捕获黑名单拦截异常
                            logger.warn("Tool execution intercepted for toolCallId: {}: {}",
                                toolCall.id(), e.getMessage());

                            // 记录拦截
                            toolCallTracker.trackExecution(
                                sessionId,
                                toolCall.id(),
                                toolCall.name(),
                                toolCall.argumentsJson(),
                                e.getMessage(),
                                true  // isIntercepted = true
                            );

                            // 调用挂起引擎
                            suspendResumeEngine.suspendForApproval(
                                sessionId,
                                e.getInterceptedCommand(),
                                toolCall.id()
                            );

                            // 完全退出线程（不抛出未处理异常）
                            logger.info("Worker suspended for session {}, exiting thread gracefully", sessionId);
                            running = false;
                            break;  // 退出 ReAct 循环

                        } catch (Exception e) {
                            // 3.5.6 工具执行失败
                            logger.error("Tool {} execution failed", toolCall.name(), e);

                            // 追加错误结果
                            UnifiedMessage errorMessage = UnifiedMessage.ofToolResult(
                                toolCall.id(), "Tool execution failed: " + e.getMessage(), true);
                            localStreamManager.appendToolResult(sessionId, errorMessage);

                            // 继续循环（不退出）
                        }
                    }
                } else {
                    // 3.6 没有工具调用，任务完成
                    logger.info("No more tool calls, task completed for session {}", sessionId);

                    // 调用 CondensationEngine 压缩上下文
                    logger.debug("Calling CondensationEngine for session {}", sessionId);
                    String masterSessionId = taskManager.getParentSessionId(sessionId);
                    if (masterSessionId != null) {
                        condensationEngine.condenseAndMergeUpwards(sessionId, masterSessionId);
                    }

                    // 更新任务状态为 COMPLETED
                    taskManager.transitionState(sessionId,
                        tech.yesboss.persistence.entity.TaskSession.Status.COMPLETED);

                    logger.info("WorkerRunner completed successfully for session {}", sessionId);
                    running = false;
                }
            }

            logger.info("WorkerRunner ReAct loop completed for session {} after {} iterations",
                sessionId, loopIteration);

        } catch (tech.yesboss.safeguard.CircuitBreakerOpenException e) {
            // 熔断器触发
            logger.error("CircuitBreaker triggered for session {}, marking as FAILED", sessionId, e);

            try {
                taskManager.transitionState(sessionId,
                    tech.yesboss.persistence.entity.TaskSession.Status.FAILED);
            } catch (Exception stateError) {
                logger.error("Failed to transition state to FAILED", stateError);
            }

            throw e;  // 重新抛出熔断异常

        } catch (Exception e) {
            logger.error("WorkerRunner execution failed for session {}", sessionId, e);

            // 尝试将状态标记为 FAILED
            try {
                taskManager.transitionState(sessionId,
                    tech.yesboss.persistence.entity.TaskSession.Status.FAILED);
            } catch (Exception stateError) {
                logger.error("Failed to transition state to FAILED", stateError);
            }

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

        // 生成执行报告
        var status = taskManager.getStatus(sessionId);
        List<UnifiedMessage> context = localStreamManager.fetchContext(sessionId);

        String report = String.format("""
            Worker Execution Report
            =======================
            Session ID: %s
            Status: %s
            Total Messages: %d
            Token Count: %d
            Summary: Task %s
            """,
            sessionId,
            status,
            context.size(),
            localStreamManager.getCurrentTokenCount(sessionId),
            status == tech.yesboss.persistence.entity.TaskSession.Status.COMPLETED ? "completed successfully" : "failed"
        );

        logger.info("Execution report generated for session {}", sessionId);
        return report;
    }

    /**
     * 获取线程执行器
     *
     * @return 线程执行器
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
        logger.info("Shutting down WorkerRunner thread executor");
        virtualThreadExecutor.shutdown();
    }
}
