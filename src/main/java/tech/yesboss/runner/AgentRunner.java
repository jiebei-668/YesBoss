package tech.yesboss.runner;

/**
 * 智能体执行器统一接口 (Agent Runner Interface)
 *
 * <p>封装了 Agent 的动态 ReAct 循环。</p>
 *
 * <p><b>核心职责：</b></p>
 * <ul>
 *   <li>定义 Agent 执行循环的统一入口</li>
 *   <li>支持在虚拟线程中运行以实现高并发</li>
 *   <li>为 MasterRunner 和 WorkerRunner 提供统一抽象</li>
 * </ul>
 *
 * <p><b>使用方式：</b></p>
 * <p>建议在底层实现时，将其丢入 {@code Executors.newVirtualThreadPerTaskExecutor()} 中运行。</p>
 */
public interface AgentRunner {

    /**
     * 启动 Agent 的执行循环
     *
     * <p>这是 Agent 的主入口方法，封装了完整的 ReAct (Reason + Act) 循环逻辑。</p>
     *
     * <p><b>执行流程：</b></p>
     * <ol>
     *   <li>拉取会话历史上下文</li>
     *   <li>调用 LLM 进行推理</li>
     *   <li>根据 LLM 响应执行工具调用或生成文本</li>
     *   <li>循环执行直到任务完成或触发熔断</li>
     * </ol>
     *
     * @param sessionId 当前任务的 Session ID
     * @throws IllegalStateException 如果会话不存在或状态不正确
     */
    void run(String sessionId);
}
