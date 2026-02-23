package tech.yesboss.tool.registry;

import tech.yesboss.tool.AgentTool;

import java.util.List;

/**
 * 统一工具注册中心
 *
 * <p>统一管理原生 Tool 与 MCP 适配器 Tool，实现基于角色的访问控制（RBAC）。</p>
 *
 * <p><b>权限边界：</b></p>
 * <ul>
 *   <li><b>Master Agent</b>: 仅被授权调用只读/探索型工具（如查看目录）</li>
 *   <li><b>Worker Agent</b>: 被授权调用读写执行全量工具（受严格沙箱管控）</li>
 * </ul>
 */
public interface ToolRegistry {

    /**
     * 注册一个新的工具到系统中
     *
     * @param tool 要注册的工具实例
     * @throws IllegalArgumentException 如果工具名称已存在
     */
    void register(AgentTool tool);

    /**
     * 根据名称精确获取工具实例
     *
     * @param toolName 工具的唯一标识名称
     * @return 工具实例
     * @throws IllegalArgumentException 如果工具不存在
     */
    AgentTool getTool(String toolName);

    /**
     * 获取指定角色被授权使用的工具列表
     *
     * <p><b>权限规则：</b></p>
     * <ul>
     *   <li>MASTER 角色仅返回 READ_ONLY 级别的工具</li>
     *   <li>WORKER 角色返回所有工具（READ_ONLY + READ_WRITE）</li>
     * </ul>
     *
     * @param agentRole 角色标识，值为 "MASTER" 或 "WORKER"（不区分大小写）
     * @return 当前角色可用的工具集合，用于组装发给大模型的 Prompt
     * @throws IllegalArgumentException 如果角色不是 MASTER 或 WORKER
     */
    List<AgentTool> getAvailableTools(String agentRole);

    /**
     * 检查工具是否已注册
     *
     * @param toolName 工具的唯一标识名称
     * @return 如果工具已注册返回 true，否则返回 false
     */
    boolean isRegistered(String toolName);

    /**
     * 获取已注册工具的总数
     *
     * @return 已注册工具的数量
     */
    int getToolCount();
}
