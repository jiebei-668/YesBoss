package tech.yesboss.tool.filesystem.security;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 动态白名单管理器接口
 *
 * <p>支持通过人机交互动态管理路径白名单，实现灵活的安全策略。</p>
 */
public interface DynamicWhitelistManager {

    /**
     * 白名单决策类型
     */
    enum DecisionType {
        /** 允许一次（仅当前操作） */
        ALLOW_ONCE,

        /** 临时允许（当前会话） */
        ALLOW_TEMPORARY,

        /** 添加到白名单（永久） */
        ALLOW_PERMANENT,

        /** 拒绝 */
        DENY
    }

    /**
     * 检查路径是否在白名单中
     *
     * @param path 要检查的路径
     * @return true 如果路径在白名单中
     */
    boolean isPathInWhitelist(String path);

    /**
     * 请求用户决策（路径不在白名单时）
     *
     * @param path 请求访问的路径
     * @param operation 操作类型（READ/WRITE/DELETE）
     * @param toolName 工具名称
     * @param sessionId 会话 ID
     * @return 用户决策
     */
    WhitelistDecision requestUserDecision(
            String path,
            String operation,
            String toolName,
            String sessionId
    );

    /**
     * 添加路径到白名单
     *
     * @param path 路径
     * @param decisionType 决策类型
     */
    void addToWhitelist(String path, DecisionType decisionType);

    /**
     * 获取当前白名单
     *
     * @return 白名单路径集合（只读）
     */
    Set<String> getWhitelist();

    /**
     * 清除临时白名单（会话结束时）
     */
    void clearTemporaryWhitelist();

    /**
     * 持久化白名单到存储
     */
    void persistWhitelist();

    /**
     * 从存储加载白名单
     */
    void loadWhitelist();
}
