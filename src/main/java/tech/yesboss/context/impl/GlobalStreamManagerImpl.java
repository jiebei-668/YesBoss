package tech.yesboss.context.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.persistence.event.InsertMessageEvent;
import tech.yesboss.persistence.repository.ChatMessageRepository;
import tech.yesboss.context.GlobalStreamManager;

import java.util.List;

/**
 * 全局流管理器实现
 *
 * <p>负责管理 Master 的全局上下文，包括人类对话和 Master 的高维抽象。</p>
 */
public class GlobalStreamManagerImpl implements GlobalStreamManager {

    private static final Logger logger = LoggerFactory.getLogger(GlobalStreamManagerImpl.class);

    private final ChatMessageRepository chatMessageRepository;

    /**
     * 创建全局流管理器
     *
     * @param chatMessageRepository 聊天消息仓储
     */
    public GlobalStreamManagerImpl(ChatMessageRepository chatMessageRepository) {
        if (chatMessageRepository == null) {
            throw new IllegalArgumentException("ChatMessageRepository cannot be null");
        }
        this.chatMessageRepository = chatMessageRepository;
        logger.info("GlobalStreamManager initialized");
    }

    @Override
    public void appendHumanMessage(String masterSessionId, String humanText) {
        if (masterSessionId == null || masterSessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Master session ID cannot be null or empty");
        }
        if (humanText == null || humanText.trim().isEmpty()) {
            throw new IllegalArgumentException("Human text cannot be null or empty");
        }

        // Get next sequence number
        int nextSeq = chatMessageRepository.getCurrentSequenceNumber(masterSessionId,
                InsertMessageEvent.StreamType.GLOBAL) + 1;

        // Create UnifiedMessage for human input
        UnifiedMessage message = UnifiedMessage.user(humanText);

        // Save message asynchronously
        chatMessageRepository.saveMessage(masterSessionId,
                InsertMessageEvent.StreamType.GLOBAL, nextSeq, message);

        logger.debug("Appended human message to session {}: seq={}", masterSessionId, nextSeq);
    }

    @Override
    public void appendMasterMessage(String masterSessionId, UnifiedMessage masterMessage) {
        if (masterSessionId == null || masterSessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Master session ID cannot be null or empty");
        }
        if (masterMessage == null) {
            throw new IllegalArgumentException("Master message cannot be null");
        }

        // Get next sequence number
        int nextSeq = chatMessageRepository.getCurrentSequenceNumber(masterSessionId,
                InsertMessageEvent.StreamType.GLOBAL) + 1;

        // Save message asynchronously
        chatMessageRepository.saveMessage(masterSessionId,
                InsertMessageEvent.StreamType.GLOBAL, nextSeq, masterMessage);

        logger.debug("Appended master message to session {}: seq={}", masterSessionId, nextSeq);
    }

    @Override
    public List<UnifiedMessage> fetchContext(String masterSessionId) {
        if (masterSessionId == null || masterSessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Master session ID cannot be null or empty");
        }

        // Fetch context ordered by sequence_num
        List<UnifiedMessage> context = chatMessageRepository.fetchContext(masterSessionId,
                InsertMessageEvent.StreamType.GLOBAL);

        logger.debug("Fetched {} messages for session {} from GLOBAL stream",
                context.size(), masterSessionId);

        return context;
    }

    @Override
    public void appendSystemMessage(String masterSessionId, String systemText) {
        if (masterSessionId == null || masterSessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Master session ID cannot be null or empty");
        }
        if (systemText == null || systemText.trim().isEmpty()) {
            throw new IllegalArgumentException("System text cannot be null or empty");
        }

        // Get next sequence number
        int nextSeq = chatMessageRepository.getCurrentSequenceNumber(masterSessionId,
                InsertMessageEvent.StreamType.GLOBAL) + 1;

        // Create UnifiedMessage for system message
        UnifiedMessage message = UnifiedMessage.system(systemText);

        // Save message asynchronously
        chatMessageRepository.saveMessage(masterSessionId,
                InsertMessageEvent.StreamType.GLOBAL, nextSeq, message);

        logger.info("Appended system message to session {}: seq={}", masterSessionId, nextSeq);
    }
}
