package tech.yesboss.persistence.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.persistence.db.SingleThreadDbWriter;
import tech.yesboss.persistence.entity.ToolExecutionLog;
import tech.yesboss.persistence.event.InsertToolExecutionLogEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of ToolExecutionRepository.
 *
 * <p>Write operations are routed to SingleThreadDbWriter as events.
 * Read operations execute direct SQL queries using proper indexes.</p>
 */
public class ToolExecutionRepositoryImpl implements ToolExecutionRepository {

    private static final Logger logger = LoggerFactory.getLogger(ToolExecutionRepositoryImpl.class);

    private final Connection connection;
    private final SingleThreadDbWriter dbWriter;

    /**
     * Create a new ToolExecutionRepositoryImpl.
     *
     * @param connection The database connection for read queries
     * @param dbWriter   The SingleThreadDbWriter for write events
     */
    public ToolExecutionRepositoryImpl(Connection connection, SingleThreadDbWriter dbWriter) {
        this.connection = connection;
        this.dbWriter = dbWriter;
    }

    @Override
    public List<ToolExecutionLog> findBySessionId(String sessionId) {
        String sql = "SELECT id, session_id, tool_call_id, tool_name, arguments, " +
                "result, is_intercepted, created_at " +
                "FROM tool_execution_log " +
                "WHERE session_id = ? " +
                "ORDER BY created_at ASC";

        List<ToolExecutionLog> logs = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                logs.add(mapRowToToolExecutionLog(rs));
            }

        } catch (SQLException e) {
            logger.error("Error finding tool logs by session_id={}", sessionId, e);
            throw new RuntimeException("Failed to find tool logs by session", e);
        }

        return logs;
    }

    @Override
    public Optional<ToolExecutionLog> findByToolCallId(String toolCallId) {
        String sql = "SELECT id, session_id, tool_call_id, tool_name, arguments, " +
                "result, is_intercepted, created_at " +
                "FROM tool_execution_log WHERE tool_call_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, toolCallId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToToolExecutionLog(rs));
            }

        } catch (SQLException e) {
            logger.error("Error finding tool log by tool_call_id={}", toolCallId, e);
            throw new RuntimeException("Failed to find tool log by call ID", e);
        }

        return Optional.empty();
    }

    @Override
    public List<ToolExecutionLog> findByToolName(String toolName) {
        String sql = "SELECT id, session_id, tool_call_id, tool_name, arguments, " +
                "result, is_intercepted, created_at " +
                "FROM tool_execution_log WHERE tool_name = ? " +
                "ORDER BY created_at DESC";

        List<ToolExecutionLog> logs = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, toolName);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                logs.add(mapRowToToolExecutionLog(rs));
            }

        } catch (SQLException e) {
            logger.error("Error finding tool logs by tool_name={}", toolName, e);
            throw new RuntimeException("Failed to find tool logs by tool name", e);
        }

        return logs;
    }

    @Override
    public List<ToolExecutionLog> findInterceptedBySession(String sessionId) {
        String sql = "SELECT id, session_id, tool_call_id, tool_name, arguments, " +
                "result, is_intercepted, created_at " +
                "FROM tool_execution_log " +
                "WHERE session_id = ? AND is_intercepted = 1 " +
                "ORDER BY created_at ASC";

        List<ToolExecutionLog> logs = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                logs.add(mapRowToToolExecutionLog(rs));
            }

        } catch (SQLException e) {
            logger.error("Error finding intercepted tool logs by session_id={}", sessionId, e);
            throw new RuntimeException("Failed to find intercepted tool logs", e);
        }

        return logs;
    }

    @Override
    public void saveLog(String sessionId, String toolCallId, String toolName,
                        String arguments, String result, boolean isIntercepted) {
        InsertToolExecutionLogEvent event = new InsertToolExecutionLogEvent(
                sessionId, toolCallId, toolName, arguments, result, isIntercepted, 0
        );
        boolean submitted = dbWriter.submitEvent(event);

        if (submitted) {
            logger.debug("Submitted tool log event for session={}, tool={}", sessionId, toolName);
        } else {
            logger.warn("Failed to submit tool log event for session={}, tool={}", sessionId, toolName);
        }
    }

    /**
     * Map a database row to a ToolExecutionLog entity.
     */
    private ToolExecutionLog mapRowToToolExecutionLog(ResultSet rs) throws SQLException {
        return new ToolExecutionLog(
                rs.getString("id"),
                rs.getString("session_id"),
                rs.getString("tool_call_id"),
                rs.getString("tool_name"),
                rs.getString("arguments"),
                rs.getString("result"),
                rs.getInt("is_intercepted") == 1,
                rs.getLong("created_at")
        );
    }
}
