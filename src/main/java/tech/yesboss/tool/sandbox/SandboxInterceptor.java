package tech.yesboss.tool.sandbox;

import tech.yesboss.tool.AgentTool;
import tech.yesboss.tool.SuspendExecutionException;

/**
 * 安全沙箱拦截器接口
 *
 * <p>这是防止 Agent"暴走"的关键防线。当 Worker 尝试越权操作时，
 * 拦截器必须立刻阻断执行并向上层调度引擎抛出挂起信号。</p>
 */
public interface SandboxInterceptor {

    /**
     * 执行前置校验 (Pre-check)
     *
     * <p>在 Tool.execute() 执行前显式调用以校验黑名单。</p>
     *
     * @param tool 准备调用的工具实例
     * @param argumentsJson LLM 传入的执行参数
     * @param toolCallId 当前触发调用的 ID，用于透传给挂起引擎
     * @throws SuspendExecutionException 若命中黑名单则阻断，并携带 toolCallId 抛出
     *
     * <p>**抛出此异常后的处理流程：**
     * <ol>
     *   <li>SuspendResumeEngine 捕获异常并将任务状态转为 SUSPENDED</li>
     *   <li>若是 Worker 触发，通知其父级 Master</li>
     *   <li>Master 通过 IM 在群内 @ 用户展示拦截详情</li>
     *   <li>系统无限期等待用户决策（授权/拒绝/补充指令）</li>
     *   <li>只有用户本人可恢复执行，Master 严禁自行决策</li>
     * </ol>
     */
    void preCheck(AgentTool tool, String argumentsJson, String toolCallId) throws SuspendExecutionException;

    /**
     * 检查工具名称是否在黑名单中
     *
     * <p>**细粒度检查方法**：单独检查工具名层面是否被禁止。
     * 某些高危工具（如系统级格式化工具）可能直接在工具名层面就被禁止。</p>
     *
     * @param toolName 工具的唯一标识名称
     * @return true 如果工具名在黑名单中，false 否则
     */
    boolean checkBlacklist(String toolName);

    /**
     * 检查工具调用参数是否包含危险命令
     *
     * <p>**细粒度检查方法**：单独检查参数内容是否触发黑名单规则。
     * 即使工具本身是安全的，其参数可能包含危险操作（如 rm -rf、|bash 等）。</p>
     *
     * <p>**支持的检查模式：**
     * <ul>
     *   <li>精确匹配：如 "rm -rf /home"</li>
     *   <li>正则表达式：如 "curl .*\\|bash"、"eval .+"</li>
     * </ul>
     *
     * @param argumentsJson 工具调用参数的 JSON 字符串
     * @return true 如果参数匹配黑名单规则，false 否则
     */
    boolean checkArguments(String argumentsJson);
}
