package tech.yesboss.tool.registry.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.tool.AgentTool;
import tech.yesboss.tool.ToolAccessLevel;
import tech.yesboss.tool.registry.ToolRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一工具注册中心实现
 *
 * <p>使用 ConcurrentHashMap 实现线程安全的工具注册与检索。</p>
 *
 * <p><b>RBAC 规则：</b></p>
 * <ul>
 *   <li>MASTER: 只能访问 READ_ONLY 工具</li>
 *   <li>WORKER: 可以访问所有工具</li>
 * </ul>
 */
public class ToolRegistryImpl implements ToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ToolRegistryImpl.class);

    /**
     * 工具注册表，使用线程安全的 ConcurrentHashMap
     */
    private final Map<String, AgentTool> tools;

    /**
     * 创建一个新的工具注册中心
     */
    public ToolRegistryImpl() {
        this.tools = new ConcurrentHashMap<>();
        logger.info("ToolRegistry initialized");
    }

    @Override
    public void register(AgentTool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("Tool cannot be null");
        }

        String toolName = tool.getName();
        if (toolName == null || toolName.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool name cannot be null or empty");
        }

        if (tools.containsKey(toolName)) {
            throw new IllegalArgumentException("Tool '" + toolName + "' is already registered");
        }

        tools.put(toolName, tool);
        logger.info("Registered tool: {} (access level: {})",
                toolName, tool.getAccessLevel());
    }

    @Override
    public AgentTool getTool(String toolName) {
        if (toolName == null || toolName.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool name cannot be null or empty");
        }

        AgentTool tool = tools.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Tool '" + toolName + "' is not registered");
        }

        return tool;
    }

    @Override
    public List<AgentTool> getAvailableTools(String agentRole) {
        if (agentRole == null || agentRole.trim().isEmpty()) {
            throw new IllegalArgumentException("Agent role cannot be null or empty");
        }

        String normalizedRole = agentRole.trim().toUpperCase();

        if (!normalizedRole.equals("MASTER") && !normalizedRole.equals("WORKER")) {
            throw new IllegalArgumentException(
                    "Invalid agent role: '" + agentRole + "'. Must be 'MASTER' or 'WORKER'");
        }

        List<AgentTool> availableTools = new ArrayList<>();

        for (AgentTool tool : tools.values()) {
            if (normalizedRole.equals("MASTER")) {
                // Master 只能访问 READ_ONLY 工具
                if (tool.getAccessLevel() == ToolAccessLevel.READ_ONLY) {
                    availableTools.add(tool);
                }
            } else {
                // Worker 可以访问所有工具
                availableTools.add(tool);
            }
        }

        logger.debug("Retrieved {} tools for role {}",
                availableTools.size(), normalizedRole);

        return Collections.unmodifiableList(availableTools);
    }

    @Override
    public boolean isRegistered(String toolName) {
        if (toolName == null || toolName.trim().isEmpty()) {
            return false;
        }
        return tools.containsKey(toolName);
    }

    @Override
    public int getToolCount() {
        return tools.size();
    }
}
