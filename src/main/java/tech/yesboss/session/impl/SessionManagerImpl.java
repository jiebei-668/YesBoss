package tech.yesboss.session.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.persistence.db.SingleThreadDbWriter;
import tech.yesboss.persistence.entity.TaskSession;
import tech.yesboss.persistence.event.DeleteMessagesEvent;
import tech.yesboss.persistence.event.DeleteTaskSessionEvent;
import tech.yesboss.persistence.repository.TaskSessionRepository;
import tech.yesboss.session.SessionManager;
import tech.yesboss.state.TaskManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话生命周期与路由管理器实现 (Session Manager Implementation)
 *
 * <p>维护外部 IM 群聊 ID 和内部 Task ID 映射关系的桥梁。</p>
 *
 * <p><b>核心职责：</b></p>
 * <ul>
 *   <li>建立外部 IM 群聊到内部 Task Session 的路由锚点</li>
 *   <li>管理会话的创建、查询和级联删除</li>
 *   <li>提供高频查询接口用于 Webhook 路由</li>
 * </ul>
 *
 * <p><b>数据结构：</b></p>
 * <p>使用 ConcurrentHashMap 存储映射关系：</p>
 * <ul>
 *   <li>Key: imType + ":" + imGroupId (例如 "FEISHU:oc_123456")</li>
 *   <li>Value: masterSessionId (例如 "550e8400-e29b-41d4-a716-446655440000")</li>
 * </ul>
 *
 * <p><b>级联删除流程：</b></p>
 * <ol>
 *   <li>查找指定 imGroupId 对应的 masterSessionId（支持跨 IM 类型）</li>
 *   <li>查询所有 parent_id = masterSessionId 的 Worker Session</li>
 *   <li>对每个 Worker Session 触发 DeleteMessagesEvent 和 DeleteTaskSessionEvent</li>
 *   <li>对 Master Session 触发 DeleteMessagesEvent 和 DeleteTaskSessionEvent</li>
 *   <li>从内存映射中移除该绑定关系</li>
 * </ol>
 */
public class SessionManagerImpl implements SessionManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionManagerImpl.class);
    private static final String BINDING_KEY_SEPARATOR = ":";

    private final TaskManager taskManager;
    private final TaskSessionRepository taskSessionRepository;
    private final SingleThreadDbWriter dbWriter;

    /**
     * 内存映射表：key = imType + ":" + imGroupId, value = masterSessionId
     *
     * <p>使用 ConcurrentHashMap 保证线程安全。</p>
     * <p>key 格式示例："FEISHU:oc_123456", "SLACK:C0123456789"</p>
     */
    private final Map<String, String> bindingMap;

    /**
     * 构造函数
     *
     * @param taskManager 任务管理器，用于创建 Master 会话
     * @param taskSessionRepository 任务会话仓储，用于查询子会话
     * @param dbWriter 单线程数据库写入器，用于提交删除事件
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    public SessionManagerImpl(
            TaskManager taskManager,
            TaskSessionRepository taskSessionRepository,
            SingleThreadDbWriter dbWriter) {
        if (taskManager == null) {
            throw new IllegalArgumentException("taskManager cannot be null");
        }
        if (taskSessionRepository == null) {
            throw new IllegalArgumentException("taskSessionRepository cannot be null");
        }
        if (dbWriter == null) {
            throw new IllegalArgumentException("dbWriter cannot be null");
        }

        this.taskManager = taskManager;
        this.taskSessionRepository = taskSessionRepository;
        this.dbWriter = dbWriter;
        this.bindingMap = new ConcurrentHashMap<>();

        logger.info("SessionManagerImpl initialized");
    }

    @Override
    public String bindOrCreateTaskSession(String imType, String imGroupId, String topic) {
        // 参数校验
        if (imType == null || imType.trim().isEmpty()) {
            throw new IllegalArgumentException("imType cannot be null or empty");
        }
        if (imGroupId == null || imGroupId.trim().isEmpty()) {
            throw new IllegalArgumentException("imGroupId cannot be null or empty");
        }
        if (topic == null || topic.trim().isEmpty()) {
            throw new IllegalArgumentException("topic cannot be null or empty");
        }

        // 构建绑定键
        String bindingKey = buildBindingKey(imType, imGroupId);

        // 检查是否已有绑定
        String existingSessionId = bindingMap.get(bindingKey);
        if (existingSessionId != null) {
            // 验证会话仍然存在
            if (taskManager.sessionExists(existingSessionId)) {
                logger.info("Existing binding found for {}:{}, returning sessionId: {}", imType, imGroupId, existingSessionId);
                return existingSessionId;
            } else {
                // 会话已不存在，清理旧绑定
                logger.warn("Existing sessionId {} not found, removing stale binding", existingSessionId);
                bindingMap.remove(bindingKey);
            }
        }

        // 创建新的 Master 会话
        logger.info("Creating new Master session for {}:{} with topic: {}", imType, imGroupId, topic);
        String masterSessionId = taskManager.createMasterTask(imType, imGroupId, topic);

        // 存储绑定关系
        bindingMap.put(bindingKey, masterSessionId);
        logger.info("Binding created: {}:{} -> {}", imType, imGroupId, masterSessionId);

        return masterSessionId;
    }

    @Override
    public String getInternalTaskId(String imType, String imGroupId) {
        // 参数校验
        if (imType == null || imType.trim().isEmpty()) {
            throw new IllegalArgumentException("imType cannot be null or empty");
        }
        if (imGroupId == null || imGroupId.trim().isEmpty()) {
            throw new IllegalArgumentException("imGroupId cannot be null or empty");
        }

        // 查找绑定
        String bindingKey = buildBindingKey(imType, imGroupId);
        String sessionId = bindingMap.get(bindingKey);

        if (sessionId == null) {
            throw new IllegalStateException(
                "No binding found for " + imType + ":" + imGroupId + ". " +
                "Ensure bindOrCreateTaskSession() was called first."
            );
        }

        logger.debug("Retrieved internal task ID for {}:{} -> {}", imType, imGroupId, sessionId);
        return sessionId;
    }

    @Override
    public void destroySessionCascade(String imGroupId) {
        // 参数校验
        if (imGroupId == null || imGroupId.trim().isEmpty()) {
            throw new IllegalArgumentException("imGroupId cannot be null or empty");
        }

        logger.info("Starting cascade deletion for imGroupId: {}", imGroupId);

        // 查找所有匹配的绑定（可能跨多个 IM 类型）
        List<Map.Entry<String, String>> matchingBindings = bindingMap.entrySet().stream()
            .filter(entry -> entry.getKey().endsWith(BINDING_KEY_SEPARATOR + imGroupId))
            .toList();

        if (matchingBindings.isEmpty()) {
            logger.warn("No bindings found for imGroupId: {}, nothing to delete", imGroupId);
            return;
        }

        // 对每个匹配的绑定执行级联删除
        for (Map.Entry<String, String> binding : matchingBindings) {
            String bindingKey = binding.getKey();
            String masterSessionId = binding.getValue();
            String imType = extractImTypeFromKey(bindingKey);

            logger.info("Processing cascade deletion for binding {}:{} -> {}", imType, imGroupId, masterSessionId);
            destroyMasterSessionCascade(masterSessionId, imType, imGroupId);

            // 从内存映射中移除
            bindingMap.remove(bindingKey);
        }

        logger.info("Cascade deletion completed for imGroupId: {}", imGroupId);
    }

    @Override
    public boolean hasBinding(String imType, String imGroupId) {
        if (imType == null || imGroupId == null) {
            return false;
        }
        String bindingKey = buildBindingKey(imType, imGroupId);
        return bindingMap.containsKey(bindingKey);
    }

    @Override
    public int getBindingCount() {
        return bindingMap.size();
    }

    /**
     * 对 Master 会话及其所有 Worker 子会话执行级联删除
     *
     * @param masterSessionId Master 会话 ID
     * @param imType IM 类型
     * @param imGroupId 群聊 ID
     */
    private void destroyMasterSessionCascade(String masterSessionId, String imType, String imGroupId) {
        try {
            // 1. 查询所有 Worker 子会话
            List<TaskSession> workerSessions = taskSessionRepository.findByParentId(masterSessionId);
            logger.info("Found {} worker sessions under master {}", workerSessions.size(), masterSessionId);

            // 2. 删除所有 Worker 子会话的消息和会话记录
            for (TaskSession worker : workerSessions) {
                String workerSessionId = worker.id();
                logger.info("Deleting worker session: {}", workerSessionId);

                // 删除 Worker 消息
                DeleteMessagesEvent deleteWorkerMessages = new DeleteMessagesEvent(workerSessionId);
                dbWriter.submitEvent(deleteWorkerMessages);

                // 删除 Worker 会话记录
                DeleteTaskSessionEvent deleteWorkerSession = new DeleteTaskSessionEvent(workerSessionId);
                dbWriter.submitEvent(deleteWorkerSession);
            }

            // 3. 删除 Master 会话的消息
            logger.info("Deleting master session messages: {}", masterSessionId);
            DeleteMessagesEvent deleteMasterMessages = new DeleteMessagesEvent(masterSessionId);
            dbWriter.submitEvent(deleteMasterMessages);

            // 4. 删除 Master 会话记录
            logger.info("Deleting master session record: {}", masterSessionId);
            DeleteTaskSessionEvent deleteMasterSession = new DeleteTaskSessionEvent(masterSessionId);
            dbWriter.submitEvent(deleteMasterSession);

            logger.info("Cascade deletion completed for master session {}", masterSessionId);

        } catch (Exception e) {
            logger.error("Error during cascade deletion for master session {}", masterSessionId, e);
            throw new RuntimeException("Failed to destroy session cascade for " + masterSessionId, e);
        }
    }

    /**
     * 构建绑定键
     *
     * @param imType IM 类型
     * @param imGroupId 群聊 ID
     * @return 绑定键 (格式: "imType:imGroupId")
     */
    private String buildBindingKey(String imType, String imGroupId) {
        return imType + BINDING_KEY_SEPARATOR + imGroupId;
    }

    /**
     * 从绑定键中提取 IM 类型
     *
     * @param bindingKey 绑定键 (格式: "imType:imGroupId")
     * @return IM 类型
     */
    private String extractImTypeFromKey(String bindingKey) {
        int separatorIndex = bindingKey.indexOf(BINDING_KEY_SEPARATOR);
        if (separatorIndex < 0) {
            throw new IllegalArgumentException("Invalid binding key format: " + bindingKey);
        }
        return bindingKey.substring(0, separatorIndex);
    }
}
