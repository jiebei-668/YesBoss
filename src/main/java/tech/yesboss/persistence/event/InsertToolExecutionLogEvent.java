package tech.yesboss.persistence.event;

/**
 * Event for inserting a tool execution log entry.
 *
 * <p>This event is fired when a tool is executed (or intercepted) to create
 * an audit trail in the tool_execution_log table. The tool_call_id serves
 * as the causal link to the LLM's reasoning that triggered the tool call.</p>
 *
 * @param sessionId       The session ID that executed the tool
 * @param toolCallId      The LLM-generated tool call ID (causal link)
 * @param toolName        The name of the tool that was called
 * @param arguments       JSON string of tool arguments
 * @param result          The tool execution result (or error)
 * @param isIntercepted    Whether the sandbox intercepted this call
 * @param createdAt       Execution timestamp in milliseconds
 */
public record InsertToolExecutionLogEvent(
        String sessionId,
        String toolCallId,
        String toolName,
        String arguments,
        String result,
        boolean isIntercepted,
        long createdAt
) implements DbWriteEvent {

    /**
     * Constructor with default current timestamp.
     */
    public InsertToolExecutionLogEvent {
        if (createdAt == 0) {
            createdAt = java.time.Instant.now().toEpochMilli();
        }
    }

    /**
     * Constructor without timestamp (uses current time).
     */
    public InsertToolExecutionLogEvent(
            String sessionId,
            String toolCallId,
            String toolName,
            String arguments,
            String result,
            boolean isIntercepted
    ) {
        this(sessionId, toolCallId, toolName, arguments, result, isIntercepted,
                java.time.Instant.now().toEpochMilli());
    }

    /**
     * Constructor for successful execution (not intercepted).
     */
    public InsertToolExecutionLogEvent(
            String sessionId,
            String toolCallId,
            String toolName,
            String arguments,
            String result
    ) {
        this(sessionId, toolCallId, toolName, arguments, result, false,
                java.time.Instant.now().toEpochMilli());
    }
}
