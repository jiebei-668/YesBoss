package tech.yesboss.persistence.event;

import java.time.Instant;

/**
 * Event for inserting a new task session record.
 *
 * <p>This event is fired when creating a new Master or Worker agent session.
 * It contains all the essential information needed to initialize a session
 * in the task_session table.</p>
 *
 * @param sessionId        Unique session identifier (e.g., "tsk_9a8b7c")
 * @param parentId         Parent session ID (null for Master, set for Worker)
 * @param imType           IM platform type (FEISHU, SLACK, CLI)
 * @param imGroupId        Group chat ID for IM routing
 * @param role             Agent role (MASTER or WORKER)
 * @param status           Initial status (PLANNING, RUNNING, etc.)
 * @param topic            Session title/summary
 * @param executionPlan    JSON array of sub-tasks (Master only)
 * @param assignedTask     Assigned task description (Worker only)
 * @param createdAt        Creation timestamp in milliseconds
 */
public record InsertTaskSessionEvent(
        String sessionId,
        String parentId,
        ImType imType,
        String imGroupId,
        AgentRole role,
        TaskStatus status,
        String topic,
        String executionPlan,
        String assignedTask,
        long createdAt
) implements DbWriteEvent {

    /**
     * Constructor with default current timestamp.
     */
    public InsertTaskSessionEvent {
        if (createdAt == 0) {
            createdAt = Instant.now().toEpochMilli();
        }
    }

    /**
     * Constructor without timestamp (uses current time).
     */
    public InsertTaskSessionEvent(
            String sessionId,
            String parentId,
            ImType imType,
            String imGroupId,
            AgentRole role,
            TaskStatus status,
            String topic,
            String executionPlan,
            String assignedTask
    ) {
        this(sessionId, parentId, imType, imGroupId, role, status, topic,
                executionPlan, assignedTask, Instant.now().toEpochMilli());
    }

    /**
     * Enum for IM platform types.
     */
    public enum ImType {
        FEISHU, SLACK, CLI
    }

    /**
     * Enum for agent roles.
     */
    public enum AgentRole {
        MASTER, WORKER
    }

    /**
     * Enum for task status values.
     */
    public enum TaskStatus {
        PLANNING, RUNNING, SUSPENDED, COMPLETED, FAILED
    }
}
