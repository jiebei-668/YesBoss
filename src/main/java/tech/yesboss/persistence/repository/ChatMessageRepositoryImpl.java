package tech.yesboss.persistence.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.domain.message.UnifiedMessage.PayloadFormat;
import tech.yesboss.domain.message.UnifiedMessage.Role;
import tech.yesboss.persistence.db.SingleThreadDbWriter;
import tech.yesboss.persistence.event.DeleteMessagesEvent;
import tech.yesboss.persistence.event.InsertMessageEvent;
import tech.yesboss.persistence.event.InsertMessageEvent.StreamType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of ChatMessageRepository.
 *
 * <p>Write operations are routed to SingleThreadDbWriter as events.
 * Read operations execute direct SQL queries using proper indexes.</p>
 */
public class ChatMessageRepositoryImpl implements ChatMessageRepository {

    private static final Logger logger = LoggerFactory.getLogger(ChatMessageRepositoryImpl.class);

    private final Connection connection;
    private final SingleThreadDbWriter dbWriter;

    /**
     * Create a new ChatMessageRepositoryImpl.
     *
     * @param connection The database connection for read queries
     * @param dbWriter   The SingleThreadDbWriter for write events
     */
    public ChatMessageRepositoryImpl(Connection connection, SingleThreadDbWriter dbWriter) {
        this.connection = connection;
        this.dbWriter = dbWriter;
    }

    @Override
    public List<UnifiedMessage> fetchContext(String sessionId, StreamType streamType) {
        String sql = "SELECT msg_role, payload_format, content " +
                "FROM chat_message " +
                "WHERE session_id = ? AND stream_type = ? " +
                "ORDER BY sequence_num ASC";

        List<UnifiedMessage> messages = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            stmt.setString(2, streamType.name());

            logger.debug("Fetching context for session={}, stream={}", sessionId, streamType);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String roleStr = rs.getString("msg_role");
                String formatStr = rs.getString("payload_format");
                String content = rs.getString("content");

                Role role = Role.valueOf(roleStr.toUpperCase());
                PayloadFormat format = PayloadFormat.valueOf(formatStr);

                messages.add(new UnifiedMessage(role, content, format));
            }

            logger.debug("Fetched {} messages for session={}, stream={}", messages.size(), sessionId, streamType);

        } catch (SQLException e) {
            logger.error("Error fetching context for session={}, stream={}", sessionId, streamType, e);
            throw new RuntimeException("Failed to fetch context", e);
        }

        return messages;
    }

    @Override
    public void saveMessage(String sessionId, StreamType streamType, int sequenceNum, UnifiedMessage message) {
        InsertMessageEvent event = new InsertMessageEvent(sessionId, streamType, sequenceNum, message);
        boolean submitted = dbWriter.submitEvent(event);

        if (submitted) {
            logger.debug("Submitted message event for session={}, stream={}, seq={}", sessionId, streamType, sequenceNum);
        } else {
            logger.warn("Failed to submit message event for session={}, stream={}, seq={}", sessionId, streamType, sequenceNum);
        }
    }

    @Override
    public void deleteBySession(String sessionId) {
        DeleteMessagesEvent event = new DeleteMessagesEvent(sessionId);
        boolean submitted = dbWriter.submitEvent(event);

        if (submitted) {
            logger.debug("Submitted delete messages event for session={}", sessionId);
        } else {
            logger.warn("Failed to submit delete messages event for session={}", sessionId);
        }
    }

    @Override
    public int getCurrentSequenceNumber(String sessionId, StreamType streamType) {
        String sql = "SELECT COALESCE(MAX(sequence_num), 0) as max_seq " +
                "FROM chat_message " +
                "WHERE session_id = ? AND stream_type = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            stmt.setString(2, streamType.name());

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("max_seq");
            }

        } catch (SQLException e) {
            logger.error("Error getting current sequence number for session={}, stream={}", sessionId, streamType, e);
        }

        return 0;
    }
}
