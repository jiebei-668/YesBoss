package tech.yesboss.context.engine.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.context.GlobalStreamManager;
import tech.yesboss.context.engine.InjectionEngine;
import tech.yesboss.domain.message.UnifiedMessage;

import java.util.List;

/**
 * 向下注入引擎实现
 *
 * <p>负责在 Master 拆分出子任务后，组装 Worker 的初始记忆。</p>
 */
public class InjectionEngineImpl implements InjectionEngine {

    private static final Logger logger = LoggerFactory.getLogger(InjectionEngineImpl.class);

    private final GlobalStreamManager globalStreamManager;

    /**
     * 创建向下注入引擎
     *
     * @param globalStreamManager 全局流管理器
     */
    public InjectionEngineImpl(GlobalStreamManager globalStreamManager) {
        if (globalStreamManager == null) {
            throw new IllegalArgumentException("GlobalStreamManager cannot be null");
        }
        this.globalStreamManager = globalStreamManager;
        logger.info("InjectionEngine initialized");
    }

    @Override
    public UnifiedMessage injectInitialContext(String masterSessionId, String assignedTask) {
        if (masterSessionId == null || masterSessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Master session ID cannot be null or empty");
        }
        if (assignedTask == null || assignedTask.trim().isEmpty()) {
            throw new IllegalArgumentException("Assigned task cannot be null or empty");
        }

        logger.info("Injecting initial context for Worker from session {}: task={}",
                masterSessionId, assignedTask);

        // Fetch global context from master session
        List<UnifiedMessage> globalContext = globalStreamManager.fetchContext(masterSessionId);

        // Build system prompt with hardcoded template
        StringBuilder systemPrompt = new StringBuilder();

        // 1. Add role definition
        systemPrompt.append("# You are a Worker Agent\n");
        systemPrompt.append("You are a specialized execution unit working under the Master Agent.\n\n");

        // 2. Add assigned task (the core directive)
        systemPrompt.append("## Your Assigned Task\n");
        systemPrompt.append("You have been assigned the following task:\n");
        systemPrompt.append("**Task:** ").append(assignedTask).append("\n\n");

        // 3. Add global rules and constraints extracted from master session
        if (!globalContext.isEmpty()) {
            systemPrompt.append("## Global Rules and Constraints\n");
            systemPrompt.append("Based on the Master Agent's planning, follow these rules:\n\n");

            // Extract and format key messages from global context
            for (UnifiedMessage msg : globalContext) {
                if (msg.role() == UnifiedMessage.Role.USER) {
                    // User requirements
                    systemPrompt.append("- User Requirement: ").append(msg.content()).append("\n");
                } else if (msg.role() == UnifiedMessage.Role.SYSTEM) {
                    // System-level rules
                    systemPrompt.append("- System Rule: ").append(msg.content()).append("\n");
                } else if (msg.role() == UnifiedMessage.Role.ASSISTANT) {
                    // Master's high-level guidance
                    String content = msg.content();
                    // Extract key points (simplified extraction)
                    if (content.length() > 200) {
                        content = content.substring(0, 200) + "...";
                    }
                    systemPrompt.append("- Master Guidance: ").append(content).append("\n");
                }
            }
            systemPrompt.append("\n");
        }

        // 4. Add execution guidelines
        systemPrompt.append("## Execution Guidelines\n");
        systemPrompt.append("- Use available tools to complete your assigned task\n");
        systemPrompt.append("- Report progress through tool results and messages\n");
        systemPrompt.append("- If you encounter obstacles, report them clearly in your responses\n");
        systemPrompt.append("- Stay focused on the assigned task, do not deviate without explicit instruction\n");

        // Create system prompt message
        UnifiedMessage systemPromptMessage = UnifiedMessage.system(systemPrompt.toString());

        logger.debug("Generated initial system prompt for Worker ({} chars)",
                systemPrompt.length());

        return systemPromptMessage;
    }

    /**
     * 提取全局上下文摘要（辅助方法）
     *
     * <p>从全局消息列表中提取关键信息，用于生成 Worker 上下文。</p>
     *
     * @param globalContext 全局上下文消息列表
     * @return 摘要文本
     */
    private String extractGlobalSummary(List<UnifiedMessage> globalContext) {
        if (globalContext == null || globalContext.isEmpty()) {
            return "No global context available.";
        }

        StringBuilder summary = new StringBuilder();
        for (UnifiedMessage msg : globalContext) {
            if (msg.role() == UnifiedMessage.Role.USER) {
                summary.append("[USER] ").append(msg.content()).append("\n");
            } else if (msg.role() == UnifiedMessage.Role.ASSISTANT) {
                String content = msg.content();
                if (content.length() > 100) {
                    content = content.substring(0, 100) + "...";
                }
                summary.append("[MASTER] ").append(content).append("\n");
            }
        }

        return summary.toString();
    }
}
