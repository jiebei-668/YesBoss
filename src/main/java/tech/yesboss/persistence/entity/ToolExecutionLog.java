package tech.yesboss.persistence.entity;

/**
 * Entity representing a tool execution log row from the tool_execution_log table.
 *
 * <p>This is a read-only domain object used for returning query results
 * from the database. The entity corresponds to the tool_execution_log table
 * which records all tool calls for audit and UI rendering purposes.</p>
 *
 * @param id              The unique log entry ID
 * @param sessionId       The session that initiated this tool call
 * @param toolCallId      The LLM-generated tool call ID (links to chat_message)
 * @param toolName        The unified tool interface name
 * @param arguments       JSON string of arguments passed to the tool
 * @param result          The tool execution result or error stack trace
 * @param isIntercepted   Whether the sandbox intercepted this call (1 = intercepted, 0 = allowed)
 * @param createdAt       Execution timestamp in milliseconds
 */
public record ToolExecutionLog(
        String id,
        String sessionId,
        String toolCallId,
        String toolName,
        String arguments,
        String result,
        boolean isIntercepted,
        long createdAt
) {

    /**
     * Check if this tool execution was successful.
     */
    public boolean isSuccessful() {
        return !isIntercepted && result != null && !result.isEmpty();
    }

    /**
     * Check if this tool execution was blocked by the sandbox.
     */
    public boolean wasBlocked() {
        return isIntercepted;
    }

    /**
     * Get a human-readable status description.
     */
    public String getStatusDescription() {
        if (isIntercepted) {
            return "BLOCKED by sandbox";
        } else if (result == null || result.isEmpty()) {
            return "No result";
        } else {
            return "Success";
        }
    }
}
