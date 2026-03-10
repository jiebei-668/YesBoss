package tech.yesboss.safeguard;

import tech.yesboss.runner.WorkerRunner;

/**
 * 挂起与恢复引擎 (Suspend and Resume Engine)
 *
 * <p>处理"人机回环 (Human-in-the-loop)"的核心组件。</p>
 *
 * <p><b>核心职责：</b></p>
 * <ul>
 *   <li>当 Worker 触发黑名单拦截时，将任务状态转为 SUSPENDED</li>
 *   <li>通过 IM 系统向用户发起审批请求</li>
 *   <li>接收用户决策（批准或拒绝）后恢复任务执行</li>
 *   <li>维护 Worker 挂起期间的现场信息</li>
 * </ul>
 *
 * <p><b>用户确认的强制性原则：</b></p>
 * <p>当 Worker 触发黑名单被拦截时，系统不得由 Master 自行决策恢复。
 * 必须通过 Master 向用户发起确认请求，只有用户本人可通过 IM 授权或拒绝。</p>
 *
 * <p><b>挂起模式（线程完全退出）：</b></p>
 * <p>Worker 触发拦截后，Worker 线程会完全退出，释放所有资源。
 * 挂起期间仅保留数据库中的持久化状态，不占用任何线程或内存资源。</p>
 *
 * @see CircuitBreaker
 * @see tech.yesboss.tool.SuspendExecutionException
 */
public interface SuspendResumeEngine {

    /**
     * 挂起当前任务等待用户审批
     *
     * <p>当底层 SandboxInterceptor 抛出黑名单拦截异常时调用。
     * 将任务状态切为 SUSPENDED，并通过 IM 系统向用户发起审批请求。</p>
     *
     * <p><b>挂起流程：</b></p>
     * <ol>
     *   <li>将任务状态从 RUNNING 转为 SUSPENDED</li>
     *   <li>存储挂起上下文到内存（toolCallId, toolName, argumentsJson, sessionId）</li>
     *   <li>伪造全局系统消息并推送审批卡片到 IM</li>
     *   <li>Worker 线程自然退出（不抛出未处理异常）</li>
     * </ol>
     *
     * <p><b>重要：</b></p>
     * <p>如果是 Worker 触发，必须先通知其父级 Master，由 Master 转发给用户。
     * Master 严禁自行做出决策，必须等待用户通过 IM 的明确回复。</p>
     *
     * @param workerSessionId    触发拦截的 Worker Session ID
     * @param interceptedCommand 被拦截的高危命令明细
     * @param toolCallId         触发拦截的大模型 tool_call_id（用于因果跟踪和上下文缝合）
     * @param toolName           被拦截的工具名称
     * @param argumentsJson      被拦截的工具调用参数 JSON
     * @throws IllegalArgumentException 如果参数为空
     * @throws IllegalStateException    如果会话不存在或状态不正确
     */
    void suspendForApproval(String workerSessionId, String interceptedCommand, String toolCallId,
                           String toolName, String argumentsJson);

    /**
     * 从挂起状态唤醒任务
     *
     * <p>接收到用户的明确授权或拒绝后，构造带有用户意图的 ToolResult，
     * 注入回 LocalStreamManager，并重新拉起 Worker 的执行循环。</p>
     *
     * <p><b>恢复流程：</b></p>
     * <ol>
     *   <li>从数据库读取 toolCallId, toolName, argumentsJson</li>
     *   <li>根据用户决策执行工具或伪造错误</li>
     *   <li>将结果注入 LocalStreamManager</li>
     *   <li>将任务状态从 SUSPENDED 转回 RUNNING</li>
     *   <li>创建新线程重新调用 WorkerRunner.run(sessionId)</li>
     * </ol>
     *
     * @param workerSessionId 被挂起的 Worker Session ID
     * @param toolCallId      需要被响应补齐的工具调用 ID
     * @param isApproved      用户是否同意继续执行
     * @param humanFeedback   用户补充的指导意见（可选）
     * @throws IllegalArgumentException 如果参数为空
     * @throws IllegalStateException    如果会话不存在或不在 SUSPENDED 状态
     */
    void resume(String workerSessionId, String toolCallId, boolean isApproved, String humanFeedback);

    /**
     * 设置 WorkerRunner 实例（用于恢复后重新启动 Worker 线程）
     *
     * <p>由于 WorkerRunner 和 SuspendResumeEngine 之间存在循环依赖，
     * 使用 setter 注入方式在 ApplicationContext 初始化时设置。</p>
     *
     * @param workerRunner WorkerRunner 实例
     */
    void setWorkerRunner(WorkerRunner workerRunner);
}
