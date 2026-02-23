package tech.yesboss.tool;

/**
 * 统一工具抽象接口
 * 所有的原生能力与外部 MCP 服务在注册入网时，均被包装为此标准接口。
 */
public interface AgentTool {

    /**
     * 工具的唯一标识名称 (供 LLM 调用，如 "write_file", "mcp_list_dir")
     *
     * @return 工具名称
     */
    String getName();

    /**
     * 工具的详细描述 (极其重要，直接影响 LLM 的 Prompt 理解)
     *
     * @return 工具描述
     */
    String getDescription();

    /**
     * 工具入参的 JSON Schema 定义
     * 用于向大模型声明该工具需要什么参数 (如 Claude 的 input_schema)
     *
     * @return JSON Schema 格式的参数定义
     */
    String getParametersJsonSchema();

    /**
     * 获取工具的访问级别
     *
     * <p>用于实现基于角色的访问控制（RBAC）。
     * Master Agent 只能访问 READ_ONLY 级别的工具，
     * Worker Agent 可以访问所有级别的工具。</p>
     *
     * @return 工具的访问级别
     */
    ToolAccessLevel getAccessLevel();

    /**
     * 核心执行逻辑
     *
     * @param argumentsJson LLM 生成并传入的参数 JSON 字符串
     * @return 工具执行的真实结果，或者是底层的异常报错堆栈
     * @throws Exception 执行过程中的任何异常
     */
    String execute(String argumentsJson) throws Exception;

    /**
     * 绕过沙箱检查的执行逻辑（专用于人机回环用户授权后）
     *
     * <p>**核心场景：**
     * 当 Worker 触发黑名单拦截，用户明确授权后，系统需要绕过 SandboxInterceptor，
     * 直接执行该工具。此方法仅在被 SuspendResumeEngine.resume() 调用时使用。</p>
     *
     * @param argumentsJson LLM 生成并传入的参数 JSON 字符串
     * @return 工具执行的真实结果，或者是底层的异常报错堆栈
     * @throws Exception 执行过程中的任何异常
     */
    String executeWithBypass(String argumentsJson) throws Exception;
}
