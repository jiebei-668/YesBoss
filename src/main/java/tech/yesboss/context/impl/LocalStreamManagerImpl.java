package tech.yesboss.context.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.persistence.event.InsertMessageEvent;
import tech.yesboss.persistence.repository.ChatMessageRepository;
import tech.yesboss.context.LocalStreamManager;

import java.util.List;

/**
 * 局部流管理器实现
 *
 * <p>负责管理 Worker 的局部上下文，包括执行思考和工具结果。</p>
 */
public class LocalStreamManagerImpl implements LocalStreamManager {

    private static final Logger logger = LoggerFactory.getLogger(LocalStreamManagerImpl.class);

    /**
     * 简单的 Token 估算系数：平均每 4 个字符约等于 1 个 Token
     */
    private static final int TOKEN_ESTIMATION_RATIO = 4;

    private final ChatMessageRepository chatMessageRepository;

    /**
     * 创建局部流管理器
     *
     * @param chatMessageRepository 聊天消息仓储
     */
    public LocalStreamManagerImpl(ChatMessageRepository chatMessageRepository) {
        if (chatMessageRepository == null) {
            throw new IllegalArgumentException("ChatMessageRepository cannot be null");
        }
        this.chatMessageRepository = chatMessageRepository;
        logger.info("LocalStreamManager initialized");
    }

    @Override
    public void appendWorkerMessage(String workerSessionId, UnifiedMessage workerMessage) {
        if (workerSessionId == null || workerSessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Worker session ID cannot be null or empty");
        }
        if (workerMessage == null) {
            throw new IllegalArgumentException("Worker message cannot be null");
        }

        // Get next sequence number
        int nextSeq = chatMessageRepository.getCurrentSequenceNumber(workerSessionId,
                InsertMessageEvent.StreamType.LOCAL) + 1;

        // Save message asynchronously
        chatMessageRepository.saveMessage(workerSessionId,
                InsertMessageEvent.StreamType.LOCAL, nextSeq, workerMessage);

        logger.debug("Appended worker message to session {}: seq={}", workerSessionId, nextSeq);
    }

    @Override
    public boolean appendWorkerMessageSync(String workerSessionId, UnifiedMessage workerMessage, long timeoutMs) throws InterruptedException {
        if (workerSessionId == null || workerSessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Worker session ID cannot be null or empty");
        }
        if (workerMessage == null) {
            throw new IllegalArgumentException("Worker message cannot be null");
        }

        // Get next sequence number
        int nextSeq = chatMessageRepository.getCurrentSequenceNumber(workerSessionId,
                InsertMessageEvent.StreamType.LOCAL) + 1;

        // Save message synchronously
        boolean success = chatMessageRepository.saveMessageSync(workerSessionId,
                InsertMessageEvent.StreamType.LOCAL, nextSeq, workerMessage, timeoutMs);

        if (success) {
            logger.debug("Appended worker message synchronously to session {}: seq={}", workerSessionId, nextSeq);
        } else {
            logger.warn("Failed to append worker message synchronously to session {}: seq={}", workerSessionId, nextSeq);
        }

        return success;
    }

    @Override
    public void appendToolResult(String workerSessionId, UnifiedMessage toolResult) {
        if (workerSessionId == null || workerSessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Worker session ID cannot be null or empty");
        }
        if (toolResult == null) {
            throw new IllegalArgumentException("Tool result cannot be null");
        }

        // Get next sequence number
        int nextSeq = chatMessageRepository.getCurrentSequenceNumber(workerSessionId,
                InsertMessageEvent.StreamType.LOCAL) + 1;

        // Save message asynchronously
        chatMessageRepository.saveMessage(workerSessionId,
                InsertMessageEvent.StreamType.LOCAL, nextSeq, toolResult);

        logger.debug("Appended tool result to session {}: seq={}", workerSessionId, nextSeq);
    }

    @Override
    public List<UnifiedMessage> fetchContext(String workerSessionId) {
        if (workerSessionId == null || workerSessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Worker session ID cannot be null or empty");
        }

        // Fetch context ordered by sequence_num
        List<UnifiedMessage> context = chatMessageRepository.fetchContext(workerSessionId,
                InsertMessageEvent.StreamType.LOCAL);

        logger.debug("Fetched {} messages for session {} from LOCAL stream",
                context.size(), workerSessionId);

        return context;
    }

    @Override
    public int getCurrentTokenCount(String workerSessionId) {
        if (workerSessionId == null || workerSessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Worker session ID cannot be null or empty");
        }

        // Fetch all messages in LOCAL stream
        List<UnifiedMessage> messages = chatMessageRepository.fetchContext(workerSessionId,
                InsertMessageEvent.StreamType.LOCAL);

        // Estimate total token count
        int totalTokens = 0;
        for (UnifiedMessage message : messages) {
            totalTokens += estimateTokenCount(message);
        }

        logger.debug("Estimated token count for session {}: {}", workerSessionId, totalTokens);

        return totalTokens;
    }

    /**
     * 估算消息的 Token 数量
     *
     * <p>这是一个简化的估算方法，实际应用中可以使用更精确的 tokenizer。</p>
     *
     * @param message 要估算的消息
     * @return 估算的 Token 数量
     */
    private int estimateTokenCount(UnifiedMessage message) {
        // 使用字符数除以系数来估算
        // 这是一个简化的方法，实际中可以使用 tiktoken 等精确计算
        String content = message.content();
        if (content == null || content.isEmpty()) {
            return 0;
        }

        // 粗略估算：每 4 个字符约等于 1 个 Token
        return Math.max(1, content.length() / TOKEN_ESTIMATION_RATIO);
    }
}
