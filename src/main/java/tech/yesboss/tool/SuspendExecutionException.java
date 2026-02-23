package tech.yesboss.tool;

/**
 * 高危命令安全挂起异常
 *
 * <p>这是一个业务异常，当触发拦截时，立刻向上抛出，由状态机引擎接管并进入 SUSPENDED 状态。</p>
 *
 * <p>当 Worker 尝试越权操作时，SandboxInterceptor 会抛出此异常，
 * 由 SuspendResumeEngine 捕获并处理人机回环流程。</p>
 */
public class SuspendExecutionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String interceptedCommand;
    private final String toolCallId;

    /**
     * 创建一个新的 SuspendExecutionException
     *
     * @param interceptedCommand 被拦截的命令描述
     * @param toolCallId 引发拦截的因果纽带 ID (LLM 生成的 tool_call_id)
     */
    public SuspendExecutionException(String interceptedCommand, String toolCallId) {
        super("操作触发安全沙箱黑名单，执行已挂起。");
        this.interceptedCommand = interceptedCommand;
        this.toolCallId = toolCallId;
    }

    /**
     * 获取被拦截的命令描述
     *
     * @return 被拦截的命令
     */
    public String getInterceptedCommand() {
        return interceptedCommand;
    }

    /**
     * 获取引发拦截的 tool_call_id
     *
     * <p>这个 ID 是 LLM 生成的因果纽带，用于将工具执行结果与 LLM 的工具调用请求关联起来。
     * 在人机回环流程中，这个 ID 也用于将用户的授权决策与原始工具调用关联。</p>
     *
     * @return tool_call_id
     */
    public String getToolCallId() {
        return toolCallId;
    }

    @Override
    public String toString() {
        return "SuspendExecutionException{" +
                "interceptedCommand='" + interceptedCommand + '\'' +
                ", toolCallId='" + toolCallId + '\'' +
                ", message='" + getMessage() + '\'' +
                '}';
    }
}
