package tech.yesboss.tool.tracker;

/**
 * 工具调用追踪器 (审计日志)
 *
 * <p>负责维护大模型生成的 tool_call_id 与实际执行工具的绑定关系。</p>
 *
 * <p><b>核心设计原则：</b></p>
 * <ul>
 *   <li>该方法内部不应直接写库，而是将数据封装为 InsertToolExecutionLogEvent，</li>
 *   <li>投递给 DbWriteEvent 异步内存无锁队列，确保高并发性能</li>
 *   <li>tool_call_id 是因果追踪的绝对纽带，必须正确维护</li>
 * </ul>
 */
public interface ToolCallTracker {

    /**
     * 记录一次完整的工具调用流水
     *
     * <p>此方法将执行信息封装为事件并投递到异步队列，实现无锁写入。</p>
     *
     * @param sessionId 任务会话 ID
     * @param toolCallId 大模型生成的因果纽带 ID
     * @param toolName 工具接口名
     * @param argumentsJson 参数 JSON
     * @param result 真实执行结果
     * @param isIntercepted 沙箱阀门标记：true = 拦截挂起，false = 安全放行
     */
    void trackExecution(String sessionId, String toolCallId, String toolName,
                        String argumentsJson, String result, boolean isIntercepted);
}
