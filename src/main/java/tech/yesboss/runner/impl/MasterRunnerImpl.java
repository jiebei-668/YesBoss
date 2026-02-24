package tech.yesboss.runner.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.context.GlobalStreamManager;
import tech.yesboss.llm.LlmClient;
import tech.yesboss.llm.impl.ModelRouter;
import tech.yesboss.runner.MasterRunner;
import tech.yesboss.state.TaskManager;
import tech.yesboss.state.model.ImRoute;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final ExecutorService virtualThreadExecutor;

    /**
     * 构造函数
     *
     * @param taskManager         任务管理器
     * @param globalStreamManager 全局流管理器
     * @param modelRouter         模型路由器
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    public MasterRunnerImpl(
            TaskManager taskManager,
            GlobalStreamManager globalStreamManager,
            ModelRouter modelRouter) {
        if (taskManager == null) {
            throw new IllegalArgumentException("taskManager cannot be null");
        }
        if (globalStreamManager == null) {
            throw new IllegalArgumentException("globalStreamManager cannot be null");
        }
        if (modelRouter == null) {
            throw new IllegalArgumentException("modelRouter cannot be null");
        }

        this.taskManager = taskManager;
        this.globalStreamManager = globalStreamManager;
        this.modelRouter = modelRouter;

        // 创建线程池用于并发执行
        // Note: Using cached thread pool for Java 17 compatibility
        // When upgraded to Java 21+, use Executors.newVirtualThreadPerTaskExecutor()
        this.virtualThreadExecutor = Executors.newCachedThreadPool();

        logger.info("MasterRunnerImpl initialized with thread pool executor");
    }

    @Override
    public void run(String sessionId) {
        logger.info("MasterRunner starting for session {}", sessionId);

        // 参数校验
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId cannot be null or empty");
        }

        try {
            // TODO: 完整的 MasterRunner 执行流程将在后续任务中实现
            // 当前是骨架实现，包含基本结构和状态转换

            logger.info("MasterRunner execution started for session {}", sessionId);

            // 检查会话状态
            if (!taskManager.sessionExists(sessionId)) {
                throw new IllegalStateException("Session not found: " + sessionId);
            }

            var status = taskManager.getStatus(sessionId);
            logger.info("Session {} current status: {}", sessionId, status);

            // 1. 需求澄清阶段（骨架：占位符）
            logger.debug("Step 1: Requirement clarification (placeholder)");

            // 2. 环境探索阶段（骨架：占位符）
            logger.debug("Step 2: Environment exploration (placeholder)");

            // 3. 生成执行计划
            String executionPlan = generateExecutionPlan(sessionId);
            logger.info("Generated execution plan: {}", executionPlan);

            // 4. 创建 Worker 子任务（骨架：占位符）
            logger.debug("Step 4: Create worker tasks (placeholder)");

            // 5. 监控 Worker 执行（骨架：占位符）
            logger.debug("Step 5: Monitor worker execution (placeholder)");

            // 6. 生成最终总结（骨架：占位符）
            logger.debug("Step 6: Generate final summary (placeholder)");

            logger.info("MasterRunner execution completed for session {}", sessionId);

        } catch (Exception e) {
            logger.error("MasterRunner execution failed for session {}", sessionId, e);
            throw e;
        }
    }

    @Override
    public String generateExecutionPlan(String sessionId) {
        logger.info("Generating execution plan for session {}", sessionId);

        // 检查会话状态
        if (!taskManager.sessionExists(sessionId)) {
            throw new IllegalStateException("Session not found: " + sessionId);
        }

        var status = taskManager.getStatus(sessionId);
        if (status != tech.yesboss.persistence.entity.TaskSession.Status.PLANNING) {
            throw new IllegalStateException(
                "Session is not in PLANNING status, current: " + status);
        }

        // TODO: 完整的执行计划生成将在后续任务中实现
        // 当前返回一个示例 JSON 格式的计划

        String plan = """
            [
              {
                "id": "task-1",
                "description": "Analyze requirements and gather context",
                "priority": "high"
              },
              {
                "id": "task-2",
                "description": "Design solution architecture",
                "priority": "high"
              },
              {
                "id": "task-3",
                "description": "Implement core functionality",
                "priority": "medium"
              }
            ]
            """;

        logger.info("Execution plan generated for session {}: {}", sessionId, plan);
        return plan;
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
