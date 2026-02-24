package tech.yesboss.context.engine.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.context.GlobalStreamManager;
import tech.yesboss.context.LocalStreamManager;
import tech.yesboss.context.engine.CondensationEngine;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.llm.LlmClient;
import tech.yesboss.llm.impl.ModelRouter;

import java.util.List;

/**
 * 向上收敛引擎实现
 *
 * <p>负责解决上下文超载，提纯脏数据。</p>
 */
public class CondensationEngineImpl implements CondensationEngine {

    private static final Logger logger = LoggerFactory.getLogger(CondensationEngineImpl.class);

    private static final int DEFAULT_TOKEN_THRESHOLD = 12000; // Default threshold for token compaction

    private final LocalStreamManager localStreamManager;
    private final GlobalStreamManager globalStreamManager;
    private final ModelRouter modelRouter;

    private int tokenThreshold;

    /**
     * 创建向上收敛引擎
     *
     * @param localStreamManager 局部流管理器
     * @param globalStreamManager 全局流管理器
     * @param modelRouter 模型路由器
     */
    public CondensationEngineImpl(LocalStreamManager localStreamManager,
                                   GlobalStreamManager globalStreamManager,
                                   ModelRouter modelRouter) {
        if (localStreamManager == null) {
            throw new IllegalArgumentException("LocalStreamManager cannot be null");
        }
        if (globalStreamManager == null) {
            throw new IllegalArgumentException("GlobalStreamManager cannot be null");
        }
        if (modelRouter == null) {
            throw new IllegalArgumentException("ModelRouter cannot be null");
        }

        this.localStreamManager = localStreamManager;
        this.globalStreamManager = globalStreamManager;
        this.modelRouter = modelRouter;
        this.tokenThreshold = DEFAULT_TOKEN_THRESHOLD;

        logger.info("CondensationEngine initialized with token threshold: {}", tokenThreshold);
    }

    @Override
    public String condenseAndMergeUpwards(String workerSessionId, String masterSessionId) {
        if (workerSessionId == null || workerSessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Worker session ID cannot be null or empty");
        }
        if (masterSessionId == null || masterSessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Master session ID cannot be null or empty");
        }

        logger.info("Condensing local context from Worker {} to Master {}",
                workerSessionId, masterSessionId);

        // 1. Fetch local context from worker
        List<UnifiedMessage> localContext = localStreamManager.fetchContext(workerSessionId);

        if (localContext.isEmpty()) {
            logger.warn("Worker {} has no local context to condense", workerSessionId);
            return "No execution history to summarize.";
        }

        // 2. Convert local context to a single text string for summarization
        String localContextText = convertLocalContextToText(localContext);

        // 3. Call LLM to summarize
        LlmClient summarizer = modelRouter.getSummarizer();
        String summary = summarizer.summarize(localContextText);

        // 4. Append summary to Master's global stream as a Master message
        String masterReport = String.format("[Worker Report - Session: %s]\n%s", workerSessionId, summary);
        UnifiedMessage masterMessage = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, masterReport);

        globalStreamManager.appendMasterMessage(masterSessionId, masterMessage);

        logger.info("Successfully condensed {} messages into summary ({} chars)",
                localContext.size(), summary.length());

        return summary;
    }

    @Override
    public boolean checkAndCompactIfNeeded(String workerSessionId) {
        if (workerSessionId == null || workerSessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Worker session ID cannot be null or empty");
        }

        int currentTokenCount = localStreamManager.getCurrentTokenCount(workerSessionId);
        boolean needsCompaction = currentTokenCount > tokenThreshold;

        if (needsCompaction) {
            logger.info("Worker {} token count {} exceeds threshold {}, compaction needed",
                    workerSessionId, currentTokenCount, tokenThreshold);
        } else {
            logger.debug("Worker {} token count {} within threshold {}",
                    workerSessionId, currentTokenCount, tokenThreshold);
        }

        return needsCompaction;
    }

    @Override
    public int getTokenThreshold() {
        return tokenThreshold;
    }

    @Override
    public void setTokenThreshold(int threshold) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("Token threshold must be positive");
        }
        this.tokenThreshold = threshold;
        logger.info("Token threshold updated to: {}", threshold);
    }

    /**
     * Convert local context messages to a single text string for summarization.
     *
     * @param localContext The local context messages
     * @return A formatted text representation of the local context
     */
    private String convertLocalContextToText(List<UnifiedMessage> localContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Worker Execution History\n\n");

        for (UnifiedMessage msg : localContext) {
            sb.append("[").append(msg.role()).append("] ");

            if (msg.hasToolCalls()) {
                sb.append("Tool Calls: ");
                for (UnifiedMessage.ToolCall toolCall : msg.toolCalls()) {
                    sb.append(toolCall.name()).append("(").append(toolCall.argumentsJson()).append(") ");
                }
            } else if (msg.hasToolResults()) {
                sb.append("Tool Results: ");
                for (UnifiedMessage.ToolResult result : msg.toolResults()) {
                    if (result.isError()) {
                        sb.append("[ERROR] ");
                    }
                    sb.append(result.resultString()).append(" ");
                }
            } else {
                // Text message
                String content = msg.content();
                if (content != null && !content.isEmpty()) {
                    sb.append(content);
                } else {
                    sb.append("[empty message]");
                }
            }
            sb.append("\n\n");
        }

        return sb.toString();
    }
}
