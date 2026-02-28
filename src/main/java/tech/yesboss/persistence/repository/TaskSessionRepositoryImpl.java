package tech.yesboss.persistence.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.persistence.db.SingleThreadDbWriter;
import tech.yesboss.persistence.entity.TaskSession;
import tech.yesboss.persistence.entity.TaskSession.ImType;
import tech.yesboss.persistence.entity.TaskSession.Role;
import tech.yesboss.persistence.entity.TaskSession.Status;
import tech.yesboss.persistence.event.InsertTaskSessionEvent;
import tech.yesboss.persistence.event.UpdateTaskStatusEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of TaskSessionRepository.
 *
 * <p>Write operations are routed to SingleThreadDbWriter as events.
 * Read operations execute direct SQL queries using proper indexes.</p>
 */
public class TaskSessionRepositoryImpl implements TaskSessionRepository {

    private static final Logger logger = LoggerFactory.getLogger(TaskSessionRepositoryImpl.class);

    private final Connection connection;
    private final SingleThreadDbWriter dbWriter;

    /**
     * Create a new TaskSessionRepositoryImpl.
     *
     * @param connection The database connection for read queries
     * @param dbWriter   The SingleThreadDbWriter for write events
     */
    public TaskSessionRepositoryImpl(Connection connection, SingleThreadDbWriter dbWriter) {
        this.connection = connection;
        this.dbWriter = dbWriter;
    }

    @Override
    public Optional<TaskSession> findById(String id) {
        String sql = "SELECT id, parent_id, im_type, im_group_id, role, status, " +
                "topic, execution_plan, assigned_task, created_at, updated_at " +
                "FROM task_session WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToTaskSession(rs));
            }

        } catch (SQLException e) {
            logger.error("Error finding session by id={}", id, e);
            throw new RuntimeException("Failed to find session by id", e);
        }

        return Optional.empty();
    }

    @Override
    public Optional<TaskSession> findByImRoute(ImType imType, String imGroupId) {
        String sql = "SELECT id, parent_id, im_type, im_group_id, role, status, " +
                "topic, execution_plan, assigned_task, created_at, updated_at " +
                "FROM task_session " +
                "WHERE im_type = ? AND im_group_id = ? AND status != 'COMPLETED' AND status != 'FAILED' " +
                "ORDER BY created_at DESC " +
                "LIMIT 1";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, imType.name());
            stmt.setString(2, imGroupId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToTaskSession(rs));
            }

        } catch (SQLException e) {
            logger.error("Error finding session by im_route: type={}, group={}", imType, imGroupId, e);
            throw new RuntimeException("Failed to find session by IM route", e);
        }

        return Optional.empty();
    }

    @Override
    public List<TaskSession> findByParentId(String parentId) {
        String sql = "SELECT id, parent_id, im_type, im_group_id, role, status, " +
                "topic, execution_plan, assigned_task, created_at, updated_at " +
                "FROM task_session WHERE parent_id = ? " +
                "ORDER BY created_at ASC";

        List<TaskSession> sessions = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, parentId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                sessions.add(mapRowToTaskSession(rs));
            }

        } catch (SQLException e) {
            logger.error("Error finding sessions by parent_id={}", parentId, e);
            throw new RuntimeException("Failed to find sessions by parent", e);
        }

        return sessions;
    }

    @Override
    public List<TaskSession> findByStatus(Status status) {
        String sql = "SELECT id, parent_id, im_type, im_group_id, role, status, " +
                "topic, execution_plan, assigned_task, created_at, updated_at " +
                "FROM task_session WHERE status = ? " +
                "ORDER BY created_at ASC";

        List<TaskSession> sessions = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                sessions.add(mapRowToTaskSession(rs));
            }

        } catch (SQLException e) {
            logger.error("Error finding sessions by status={}", status, e);
            throw new RuntimeException("Failed to find sessions by status", e);
        }

        return sessions;
    }

    @Override
    public void saveSession(String sessionId, String parentId, ImType imType, String imGroupId,
                            Role role, Status status, String topic, String assignedTask) {
        InsertTaskSessionEvent event = new InsertTaskSessionEvent(
                sessionId, parentId,
                InsertTaskSessionEvent.ImType.valueOf(imType.name()),
                imGroupId,
                InsertTaskSessionEvent.AgentRole.valueOf(role.name()),
                InsertTaskSessionEvent.TaskStatus.valueOf(status.name()),
                topic, null, assignedTask, 0
        );

        // Use synchronous write to ensure session is persisted before returning
        // This fixes the race condition where MasterRunner queries before the async write completes
        try {
            boolean processed = dbWriter.submitEventAndWait(event, 5000);  // Wait up to 5 seconds
            if (!processed) {
                throw new RuntimeException("Failed to persist session within timeout: " + sessionId);
            }
            logger.debug("Session saved synchronously: {}", sessionId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while saving session: " + sessionId, e);
        }
    }

    @Override
    public void updateStatus(String sessionId, Status newStatus, String planJson) {
        UpdateTaskStatusEvent event = new UpdateTaskStatusEvent(
                sessionId,
                InsertTaskSessionEvent.TaskStatus.valueOf(newStatus.name()),
                planJson
        );
        boolean submitted = dbWriter.submitEvent(event);

        if (submitted) {
            logger.debug("Submitted status update event for id={}, status={}", sessionId, newStatus);
        } else {
            logger.warn("Failed to submit status update event for id={}, status={}", sessionId, newStatus);
        }
    }

    @Override
    public void updateExecutionPlan(String sessionId, String planJson) {
        UpdateTaskStatusEvent event = new UpdateTaskStatusEvent(sessionId, null, planJson);
        boolean submitted = dbWriter.submitEvent(event);

        if (submitted) {
            logger.debug("Submitted execution plan update event for id={}", sessionId);
        } else {
            logger.warn("Failed to submit execution plan update event for id={}", sessionId);
        }
    }

    /**
     * Map a database row to a TaskSession entity.
     */
    private TaskSession mapRowToTaskSession(ResultSet rs) throws SQLException {
        return new TaskSession(
                rs.getString("id"),
                rs.getString("parent_id"),
                ImType.valueOf(rs.getString("im_type")),
                rs.getString("im_group_id"),
                Role.valueOf(rs.getString("role")),
                Status.valueOf(rs.getString("status")),
                rs.getString("topic"),
                rs.getString("execution_plan"),
                rs.getString("assigned_task"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
