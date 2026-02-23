package tech.yesboss.tool.sandbox.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.tool.AgentTool;
import tech.yesboss.tool.SuspendExecutionException;
import tech.yesboss.tool.sandbox.SandboxInterceptor;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 安全沙箱拦截器实现
 *
 * <p>使用工具名称黑名单和参数正则表达式黑名单来拦截危险操作。</p>
 */
public class SandboxInterceptorImpl implements SandboxInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(SandboxInterceptorImpl.class);

    /**
     * 工具名黑名单（直接禁止的工具）
     *
     * <p>这些工具名称本身就被视为危险，无论参数如何都会被拦截。</p>
     */
    private static final List<String> TOOL_NAME_BLACKLIST = List.of(
            "format_disk",
            "wipe_partition",
            "delete_system",
            "destroy_data"
    );

    /**
     * 参数黑名单（正则表达式）
     *
     * <p>这些模式用于检测参数中的危险操作。</p>
     */
    private static final List<Pattern> ARGUMENT_BLACKLIST = List.of(
            Pattern.compile("rm\\s+-rf\\s+.*"),                    // rm -rf ...
            Pattern.compile("curl\\s+.*\\|\\s*(bash|sh)"),          // curl ... | bash/sh
            Pattern.compile("wget\\s+.*\\|\\s*(bash|sh)"),          // wget ... | bash/sh
            Pattern.compile("eval\\s+.*"),                         // eval ...
            Pattern.compile("exec\\s+.*"),                         // exec ...
            Pattern.compile(">\\s*/etc/(passwd|shadow|sudoers)"),  // 写入系统敏感文件
            Pattern.compile("dd\\s+.*if=/dev/zero"),               // dd with /dev/zero (disk wipe)
            Pattern.compile("mkfs(\\.|\\s).*"),                    // mkfs (format filesystem)
            Pattern.compile("fdisk\\s+.*"),                        // fdisk (partition manipulation)
            Pattern.compile("chmod\\s+.*000.*"),                   // chmod 000 (remove all permissions)
            Pattern.compile("chown\\s+-R\\s+.*"),                  // chown -R (recursive ownership change)
            Pattern.compile(":\\(\\)\\s*\\{\\s*:\\|:\\s*&\\s*\\}\\s*;"), // Fork bomb
            Pattern.compile("\\$\\(.*\\)\\s*&&.*\\|.*sh")          // Command substitution with pipe to shell
    );

    @Override
    public void preCheck(AgentTool tool, String argumentsJson, String toolCallId) throws SuspendExecutionException {
        logger.debug("Pre-checking tool: {}, arguments: {}, toolCallId: {}",
                tool.getName(), argumentsJson, toolCallId);

        // 第一步：检查工具名是否在黑名单
        if (checkBlacklist(tool.getName())) {
            String interceptedCommand = "Tool [" + tool.getName() + "] is blacklisted";
            logger.warn("Tool name blacklist triggered: {}", interceptedCommand);
            throw new SuspendExecutionException(interceptedCommand, toolCallId);
        }

        // 第二步：检查参数是否包含危险命令
        if (checkArguments(argumentsJson)) {
            String interceptedCommand = "Arguments [" + argumentsJson + "] triggered blacklist rules";
            logger.warn("Arguments blacklist triggered: {}", interceptedCommand);
            throw new SuspendExecutionException(interceptedCommand, toolCallId);
        }

        // 两者都通过，校验放行
        logger.debug("Pre-check passed for tool: {}", tool.getName());
    }

    @Override
    public boolean checkBlacklist(String toolName) {
        boolean isBlacklisted = TOOL_NAME_BLACKLIST.contains(toolName);
        if (isBlacklisted) {
            logger.debug("Tool name '{}' is in blacklist", toolName);
        }
        return isBlacklisted;
    }

    @Override
    public boolean checkArguments(String argumentsJson) {
        // 检查参数是否匹配任何一个黑名单正则表达式
        for (Pattern pattern : ARGUMENT_BLACKLIST) {
            if (pattern.matcher(argumentsJson).find()) {
                logger.debug("Arguments '{}' matched blacklist pattern: {}",
                        argumentsJson, pattern.pattern());
                return true;
            }
        }
        return false;
    }

    /**
     * 获取当前的工具名称黑名单（用于测试和调试）
     *
     * @return 工具名称黑名单列表的不可修改副本
     */
    public static List<String> getToolNameBlacklist() {
        return List.copyOf(TOOL_NAME_BLACKLIST);
    }

    /**
     * 获取当前的参数黑名单正则表达式列表（用于测试和调试）
     *
     * @return 参数黑名单正则表达式列表的不可修改副本
     */
    public static List<String> getArgumentBlacklistPatterns() {
        return ARGUMENT_BLACKLIST.stream()
                .map(Pattern::pattern)
                .toList();
    }
}
