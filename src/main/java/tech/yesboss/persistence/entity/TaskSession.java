package tech.yesboss.persistence.entity;

import tech.yesboss.persistence.event.InsertTaskSessionEvent;

/**
 * Entity representing a task session row from the task_session table.
 *
 * <p>This is a read-only domain object used for returning query results
 * from the database. The entity corresponds to the task_session table
 * which manages Master/Worker Agent lifecycle.</p>
 *
 * @param id              The unique session ID
 * @param parentId        Parent session ID (null for Master sessions)
 * @param imType          IM platform type (FEISHU, SLACK, CLI)
 * @param imGroupId       The group/chat ID for IM routing
 * @param role            Agent role (MASTER or WORKER)
 * @param status          Current task status
 * @param topic           Short description of the task
 * @param executionPlan   JSON array of sub-tasks (Master only)
 * @param assignedTask    Specific task assigned to Worker
 * @param createdAt       Creation timestamp in milliseconds
 * @param updatedAt       Last update timestamp in milliseconds
 */
public record TaskSession(
        String id,
        String parentId,
        ImType imType,
        String imGroupId,
        Role role,
        Status status,
        String topic,
        String executionPlan,
        String assignedTask,
        long createdAt,
        long updatedAt
) {

    /**
     * IM platform type enum.
     */
    public enum ImType {
        FEISHU, SLACK, CLI
    }

    /**
     * Agent role enum.
     */
    public enum Role {
        MASTER, WORKER
    }

    /**
     * Task status enum matching the state machine.
     */
    public enum Status {
        PLANNING, RUNNING, SUSPENDED, COMPLETED, FAILED
    }

    /**
     * Check if this is a Master session (no parent).
     */
    public boolean isMaster() {
        return role == Role.MASTER;
    }

    /**
     * Check if this is a Worker session (has parent).
     */
    public boolean isWorker() {
        return role == Role.WORKER;
    }

    /**
     * Check if the task is in a terminal state.
     */
    public boolean isTerminal() {
        return status == Status.COMPLETED || status == Status.FAILED;
    }

    /**
     * Check if the task is currently active.
     */
    public boolean isActive() {
        return status == Status.RUNNING || status == Status.SUSPENDED || status == Status.PLANNING;
    }

    /**
     * Convert entity ImType to event ImType.
     */
    public InsertTaskSessionEvent.ImType toEventImType() {
        return InsertTaskSessionEvent.ImType.valueOf(imType.name());
    }

    /**
     * Convert entity Role to event AgentRole.
     */
    public InsertTaskSessionEvent.AgentRole toEventRole() {
        return InsertTaskSessionEvent.AgentRole.valueOf(role.name());
    }

    /**
     * Convert entity Status to event TaskStatus.
     */
    public InsertTaskSessionEvent.TaskStatus toEventStatus() {
        return InsertTaskSessionEvent.TaskStatus.valueOf(status.name());
    }

    /**
     * Convert event ImType to entity ImType.
     */
    public static ImType fromEventImType(InsertTaskSessionEvent.ImType imType) {
        return ImType.valueOf(imType.name());
    }

    /**
     * Convert event AgentRole to entity Role.
     */
    public static Role fromEventRole(InsertTaskSessionEvent.AgentRole role) {
        return Role.valueOf(role.name());
    }

    /**
     * Convert event TaskStatus to entity Status.
     */
    public static Status fromEventStatus(InsertTaskSessionEvent.TaskStatus status) {
        return Status.valueOf(status.name());
    }
}
