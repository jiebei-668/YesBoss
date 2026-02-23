package tech.yesboss.persistence.repository;

import tech.yesboss.persistence.entity.TaskSession;
import tech.yesboss.persistence.entity.TaskSession.ImType;
import tech.yesboss.persistence.entity.TaskSession.Role;
import tech.yesboss.persistence.entity.TaskSession.Status;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for task session operations.
 *
 * <p>This repository provides two types of operations:
 * <ul>
 *   <li><b>Read operations:</b> Direct SQL queries using indexes</li>
 *   <li><b>Write operations:</b> Asynchronous event submission to the SingleThreadDbWriter</li>
 * </ul>
 *
 * <p>Read operations use the following indexes:
 * <ul>
 *   <li>{@code idx_session_im_route}: For findByImRoute()</li>
 *   <li>{@code idx_session_parent}: For findByParentId()</li>
 * </ul>
 */
public interface TaskSessionRepository {

    /**
     * Find a task session by its unique ID.
     *
     * @param id The session ID
     * @return Optional containing the TaskSession, or empty if not found
     */
    Optional<TaskSession> findById(String id);

    /**
     * Find an active session by IM route (im_type + im_group_id).
     *
     * <p>This is used by WebhookController to route incoming IM messages
     * to the correct task session. Uses the {@code idx_session_im_route} index.</p>
     *
     * @param imType     The IM platform type (FEISHU, SLACK, CLI)
     * @param imGroupId  The group/chat ID
     * @return Optional containing the active TaskSession, or empty if not found
     */
    Optional<TaskSession> findByImRoute(ImType imType, String imGroupId);

    /**
     * Find all Worker sessions under a given Master session.
     *
     * <p>Uses the {@code idx_session_parent} index.</p>
     *
     * @param parentId The parent (Master) session ID
     * @return List of child Worker sessions
     */
    List<TaskSession> findByParentId(String parentId);

    /**
     * Find all sessions with a specific status.
     *
     * @param status The status to filter by
     * @return List of sessions with the given status
     */
    List<TaskSession> findByStatus(Status status);

    /**
     * Create a new task session asynchronously.
     *
     * <p>Creates an InsertTaskSessionEvent and submits to the queue.</p>
     *
     * @param sessionId    The unique session ID
     * @param parentId     Parent session ID (null for Master)
     * @param imType       IM platform type
     * @param imGroupId    The group/chat ID
     * @param role         Agent role (MASTER or WORKER)
     * @param status       Initial status
     * @param topic        Short description
     * @param assignedTask Assigned task text (Worker only)
     */
    void saveSession(String sessionId, String parentId, ImType imType, String imGroupId,
                     Role role, Status status, String topic, String assignedTask);

    /**
     * Update task status and optionally the execution plan.
     *
     * <p>Creates an UpdateTaskStatusEvent and submits to the queue.
     * Called by TaskManager during state transitions.</p>
     *
     * @param sessionId   The session ID
     * @param newStatus   The new status
     * @param planJson    Execution plan JSON (for Master), or null
     */
    void updateStatus(String sessionId, Status newStatus, String planJson);

    /**
     * Update the execution plan for a Master session.
     *
     * @param sessionId  The Master session ID
     * @param planJson   The JSON array of sub-tasks
     */
    void updateExecutionPlan(String sessionId, String planJson);
}
