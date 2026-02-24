package tech.yesboss.runner;

/**
 * 子 Agent 执行器接口 (Worker Runner Interface)
 *
 * <p>Worker Agent 是执行单元，负责具体任务的执行和工具调用。</p>
 *
 * <p><b>权限边界：</b></p>
 * <ul>
 *   <li>读写全量工具（受严格沙箱管控）</li>
 *   <li>局部流读写（维护独立任务上下文）</li>
 * </ul>
 *
 * <p><b>核心职责：</b></p>
 * <ul>
 *   <li>执行分配的具体任务</li>
 *   <li>调用工具完成操作</li>
 *   <li>处理人机回环审批流程</li>
 *   <li>生成执行报告</li>
 * </ul>
 */
public interface WorkerRunner extends AgentRunner {

    /**
     * 向上汇报执行结果
     *
     * <p>历经多轮尝试完成后，生成简短的执行报告供框架收敛。</p>
     *
     * <p><b>报告内容：</b></p>
     * <ul>
     *   <li>任务完成状态（成功/失败/部分完成）</li>
     *   <li>关键输出和结果</li>
     *   <li>遇到的问题和解决方案</li>
     *   <li>建议的后续行动</li>
     * </ul>
     *
     * @param sessionId Worker 的会话 ID
     * @return 执行报告文本
     * @throws IllegalStateException 如果会话不存在
     */
    String generateExecutionReport(String sessionId);

    /**
     * 【核心规约】唤醒状态判定
     *
     * <p><b>问题背景：</b></p>
     * <p>当 Worker 触发人机回环（黑名单拦截）时，Worker 线程会完全退出释放资源。
     * 用户决策后，SuspendResumeEngine 会创建新线程调用 run(sessionId)。
     * 此时新线程内存中没有任何局部变量，如果直接执行初始化流程会导致重复注入。</p>
     *
     * <p><b>强制判定逻辑：</b></p>
     * <p>在 run() 方法入口处，必须先从 LocalStreamManager 拉取完整的局部流历史。
     * 判断最后一条消息的类型：</p>
     *
     * <ul>
     *   <li><b>如果最后一条消息是 ToolResult</b>（role=TOOL 或包含 ToolResult）：
     *     <ul>
     *       <li>→ 代表这是从 SUSPENDED 状态唤醒</li>
     *       <li>→ 必须跳过任务初始化（跳过 InjectionEngine 的 Prompt 注入）</li>
     *       <li>→ 直接基于现有上下文继续 ReAct 循环，大模型会看到 ToolResult 并继续推理</li>
     *     </ul>
     *   </li>
     *   <li><b>如果最后一条消息不是 ToolResult</b>（或局部流为空）：
     *     <ul>
     *       <li>→ 代表这是新创建的 Worker</li>
     *       <li>→ 执行完整的初始化流程（调用 InjectionEngine.injectInitialContext）</li>
     *       <li>→ 然后开始正常的 ReAct 循环</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <p><b>伪代码示例：</b></p>
     * <pre>{@code
     * void run(String sessionId) {
     *     // 1. 拉取局部流历史
     *     List<UnifiedMessage> context = localStreamManager.fetchContext(sessionId);
     *
     *     // 2. 判定是否为唤醒状态
     *     boolean isResuming = !context.isEmpty()
     *         && context.get(context.size() - 1).role() == Role.TOOL;
     *
     *     if (!isResuming) {
     *         // 新创建的 Worker：执行初始化
     *         UnifiedMessage initialPrompt = injectionEngine.injectInitialContext(...);
     *         localStreamManager.appendWorkerMessage(sessionId, initialPrompt);
     *     }
     *     // 唤醒状态：跳过初始化，直接进入 ReAct 循环
     *
     *     // 3. 开始/继续 ReAct 循环
     *     while (running) {
     *         UnifiedMessage response = llmClient.chat(context, ...);
     *         // ... 处理工具调用
     *     }
     * }
     * }</pre>
     *
     * @param sessionId Worker 的会话 ID
     * @return true 如果这是从挂起状态唤醒，false 如果这是新创建的 Worker
     * @throws IllegalArgumentException 如果 sessionId 为空
     */
    boolean isResumingFromSuspension(String sessionId);
}
