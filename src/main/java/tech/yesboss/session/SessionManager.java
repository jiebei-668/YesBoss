package tech.yesboss.session;

/**
 * 会话生命周期与路由管理器 (Session Lifecycle and Routing Manager)
 *
 * <p>维护"群聊 ID+渠道类型"与"内部 Task ID"的联合绑定关系。</p>
 *
 * <p><b>核心职责：</b></p>
 * <ul>
 *   <li>建立外部 IM 群聊到内部 Task Session 的路由锚点</li>
 *   <li>管理会话的创建、查询和级联删除</li>
 *   <li>提供高频查询接口用于 Webhook 路由</li>
 * </ul>
 *
 * <p><b>关键设计：</b></p>
 * <p>系统规定"一次完整的任务即为一次会话"，并且与"新建一个专属群聊"强绑定。</p>
 * <p>这个组件就是维护外部群聊 ID 和内部 Task ID 映射关系的桥梁。</p>
 *
 * @see SessionManagerImpl
 */
public interface SessionManager {

    /**
     * 建立路由锚点：当用户在群内下达新任务时，创建或获取绑定的内部 Master Task ID
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>检查 (imType, imGroupId) 组合是否已有绑定</li>
     *   <li>如果已存在，直接返回绑定的 masterSessionId</li>
     *   <li>如果不存在，调用 TaskManager.createMasterTask() 创建新会话</li>
     *   <li>将 (imType + imGroupId) → masterSessionId 的映射存入内存</li>
     * </ol>
     *
     * @param imType 接入渠道 (如 FEISHU, SLACK)
     * @param imGroupId 外部办公软件的真实群聊 ID
     * @param topic 任务主题
     * @return 内部全局唯一的 Task Session ID
     * @throws IllegalArgumentException 如果任何参数为 null 或空
     */
    String bindOrCreateTaskSession(String imType, String imGroupId, String topic);

    /**
     * 精准路由：通过外部群聊 ID 找到对应的内部任务 ID (高频查询)
     *
     * <p>用于 Webhook 事件处理时快速定位目标会话。</p>
     *
     * @param imType 接入渠道 (如 FEISHU, SLACK)
     * @param imGroupId 外部办公软件的真实群聊 ID
     * @return 内部绑定的 Master Session ID
     * @throws IllegalArgumentException 如果参数为 null 或空
     * @throws IllegalStateException 如果未找到绑定的会话
     */
    String getInternalTaskId(String imType, String imGroupId);

    /**
     * 物理销毁：处理群聊解散事件
     *
     * <p>当用户删除该群聊时，触发级联数据物理销毁：</p>
     * <ol>
     *   <li>查找 (imType, imGroupId) 对应的 masterSessionId</li>
     *   <li>查询所有 parent_id = masterSessionId 的 Worker Session</li>
     *   <li>触发 DeleteMessagesEvent 删除所有 Worker 和 Master 的消息</li>
     *   <li>触发 DeleteTaskSessionEvent 删除所有 Worker 和 Master 会话记录</li>
     *   <li>从内存映射中移除该绑定关系</li>
     * </ol>
     *
     * @param imGroupId 解散的群聊 ID
     * @throws IllegalArgumentException 如果参数为 null 或空
     */
    void destroySessionCascade(String imGroupId);

    /**
     * 检查指定群聊是否已有绑定的会话
     *
     * @param imType 接入渠道
     * @param imGroupId 群聊 ID
     * @return true 如果存在绑定，false 否则
     */
    boolean hasBinding(String imType, String imGroupId);

    /**
     * 获取当前绑定的会话数量
     *
     * @return 绑定关系总数
     */
    int getBindingCount();
}
