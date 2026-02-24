package tech.yesboss.state;

import tech.yesboss.state.model.ImRoute;
import tech.yesboss.persistence.entity.TaskSession.Status;

/**
 * 任务中枢管理器 (Task Manager)
 *
 * <p>负责维护任务的状态机流转，并管理父子任务的生命周期。</p>
 *
 * <p><b>核心职责：</b></p>
 * <ul>
 *   <li>创建 Master 任务（总任务）和 Worker 任务（子任务）</li>
 *   <li>管理任务状态转换（状态机）</li>
 *   <li>维护父子任务关系</li>
 *   <li>提供 IM 路由信息查询</li>
 * </ul>
 */
public interface TaskManager {

    /**
     * 开启一个全新的总任务 (Master 会话)
     *
     * <p>创建一个新的 Master 任务会话，绑定到指定的 IM 群聊。</p>
     *
     * @param imType    渠道类型 (如 FEISHU, SLACK, CLI)
     * @param imGroupId 绑定的群聊 ID
     * @param topic     任务主题
     * @return 全局唯一的 Master Session ID
     * @throws IllegalArgumentException if any parameter is null or empty
     */
    String createMasterTask(String imType, String imGroupId, String topic);

    /**
     * 拆分并下发一个子任务 (Worker 会话)
     *
     * <p>为 Master 任务创建一个 Worker 子任务，继承 Master 的 IM 路由信息。</p>
     *
     * @param parentSessionId Master 的 Session ID
     * @param assignedTask    给 Worker 下发的明确任务文本
     * @return 全局唯一的 Worker Session ID
     * @throws IllegalArgumentException if any parameter is null or empty
     * @throws IllegalStateException    if parent session is not found or not a Master
     */
    String createWorkerTask(String parentSessionId, String assignedTask);

    /**
     * 核心状态机流转方法
     *
     * <p>内部必须做严格的状态校验 (例如 FAILED 不能直接转 RUNNING)。</p>
     *
     * <p><b>有效状态转换：</b></p>
     * <ul>
     *   <li>PLANNING → RUNNING</li>
     *   <li>PLANNING → FAILED</li>
     *   <li>RUNNING → SUSPENDED</li>
     *   <li>RUNNING → COMPLETED</li>
     *   <li>RUNNING → FAILED</li>
     *   <li>SUSPENDED → RUNNING (resume)</li>
     *   <li>SUSPENDED → FAILED</li>
     * </ul>
     *
     * @param sessionId 会话 ID
     * @param newStatus 新状态
     * @throws IllegalArgumentException if sessionId is null/empty or newStatus is null
     * @throws IllegalStateException    if status transition is invalid
     */
    void transitionState(String sessionId, Status newStatus);

    /**
     * 获取当前任务状态
     *
     * @param sessionId 会话 ID
     * @return 当前状态
     * @throws IllegalArgumentException if sessionId is null/empty
     * @throws IllegalStateException    if session not found
     */
    Status getStatus(String sessionId);

    /**
     * 获取父级会话 ID
     *
     * <p>用于人机回环流程中，Worker 触发拦截时需要找到其父级 Master，
     * 以便向 Master 的全局流插入系统消息。</p>
     *
     * @param sessionId 当前会话 ID（Worker 或 Master）
     * @return 父级会话 ID（Master 的 parent 为 null），若当前已是 Master 则返回 null
     * @throws IllegalArgumentException if sessionId is null/empty
     * @throws IllegalStateException    if session not found
     */
    String getParentSessionId(String sessionId);

    /**
     * 获取 IM 路由信息
     *
     * <p>用于人机回环流程中，SuspendResumeEngine 需要通过 Master SessionId
     * 获取对应的 imType 和 imGroupId，以便推送消息到正确的群聊。</p>
     *
     * @param masterSessionId Master 的会话 ID
     * @return 包含 imType 和 imGroupId 的路由对象
     * @throws IllegalArgumentException if masterSessionId is null/empty
     * @throws IllegalStateException    if session not found or not a Master
     */
    ImRoute getImRoute(String masterSessionId);

    /**
     * Check if a session exists.
     *
     * @param sessionId The session ID to check
     * @return true if the session exists, false otherwise
     */
    boolean sessionExists(String sessionId);

    /**
     * Check if a session is a Master session.
     *
     * @param sessionId The session ID to check
     * @return true if the session is a Master, false otherwise
     */
    boolean isMasterSession(String sessionId);

    /**
     * Check if a session is a Worker session.
     *
     * @param sessionId The session ID to check
     * @return true if the session is a Worker, false otherwise
     */
    boolean isWorkerSession(String sessionId);
}
