package tech.yesboss.tool.tracker.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.persistence.db.SingleThreadDbWriter;
import tech.yesboss.persistence.event.InsertToolExecutionLogEvent;
import tech.yesboss.tool.tracker.ToolCallTracker;

/**
 * 工具调用追踪器实现
 *
 * <p>将工具执行信息封装为 InsertToolExecutionLogEvent 并投递到异步队列。</p>
 */
public class ToolCallTrackerImpl implements ToolCallTracker {

    private static final Logger logger = LoggerFactory.getLogger(ToolCallTrackerImpl.class);

    private final SingleThreadDbWriter dbWriter;

    /**
     * 创建一个新的工具调用追踪器
     *
     * @param dbWriter 单线程数据库写入器，用于异步写入
     */
    public ToolCallTrackerImpl(SingleThreadDbWriter dbWriter) {
        if (dbWriter == null) {
            throw new IllegalArgumentException("SingleThreadDbWriter cannot be null");
        }
        this.dbWriter = dbWriter;
        logger.info("ToolCallTracker initialized");
    }

    @Override
    public void trackExecution(String sessionId, String toolCallId, String toolName,
                               String argumentsJson, String result, boolean isIntercepted) {
        // 参数校验
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Session ID cannot be null or empty");
        }
        if (toolCallId == null || toolCallId.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool Call ID cannot be null or empty");
        }
        if (toolName == null || toolName.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool name cannot be null or empty");
        }

        // 创建事件对象
        InsertToolExecutionLogEvent event = new InsertToolExecutionLogEvent(
                sessionId,
                toolCallId,
                toolName,
                argumentsJson != null ? argumentsJson : "{}",  // field: arguments
                result != null ? result : "",                   // field: result
                isIntercepted
        );

        // 投递到异步队列
        dbWriter.submitEvent(event);

        logger.debug("Tracked tool execution: sessionId={}, toolCallId={}, toolName={}, isIntercepted={}",
                sessionId, toolCallId, toolName, isIntercepted);

        if (isIntercepted) {
            logger.warn("Tool execution was intercepted: sessionId={}, toolCallId={}, toolName={}",
                    sessionId, toolCallId, toolName);
        }
    }
}
