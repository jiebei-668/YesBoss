package tech.yesboss.tool.sandbox.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.tool.AgentTool;
import tech.yesboss.tool.SuspendExecutionException;
import tech.yesboss.tool.sandbox.SandboxInterceptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 安全沙箱拦截器实现
 *
 * <p>使用工具名称黑名单和参数正则表达式黑名单来拦截危险操作。</p>
 *
 * <p><b>人机回环触发场景：</b></p>
 * <ul>
 *   <li>工具名称在黑名单中</li>
 *   <li>参数包含危险命令模式</li>
 *   <li>文件写入操作触发审批条件（覆盖已存在文件等）</li>
 * </ul>
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

    /**
     * 受保护的文件扩展名（写入这些类型需要审批）
     */
    private static final Set<String> PROTECTED_EXTENSIONS = Set.of(
            "env",      // 环境变量文件
            "pem",      // 证书文件
            "key",      // 密钥文件
            "ssh",      // SSH 配置
            "db",       // 数据库文件
            "sqlite"    // SQLite 数据库
    );

    /**
     * 受保护的目录名称
     */
    private static final Set<String> PROTECTED_DIRECTORIES = Set.of(
            ".git",
            ".ssh",
            ".aws",
            ".kube",
            "secrets",
            "credentials"
    );

    /**
     * 是否启用覆盖审批（默认启用）
     */
    private final boolean enableOverwriteApproval;

    /**
     * 默认构造函数（启用所有审批功能）
     */
    public SandboxInterceptorImpl() {
        this(true);
    }

    /**
     * 构造函数
     *
     * @param enableOverwriteApproval 是否启用覆盖审批
     */
    public SandboxInterceptorImpl(boolean enableOverwriteApproval) {
        this.enableOverwriteApproval = enableOverwriteApproval;
        logger.info("SandboxInterceptorImpl initialized with enableOverwriteApproval={}", enableOverwriteApproval);
    }

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

    @Override
    public void checkWriteOperation(String targetPath, String argumentsJson, String toolCallId, String operationType)
            throws SuspendExecutionException {
        logger.debug("Checking write operation: path={}, operation={}, toolCallId={}",
                targetPath, operationType, toolCallId);

        // 0. 删除操作强制审批
        if ("DELETE".equals(operationType) || "DELETE_RECURSIVE".equals(operationType)) {
            String operationDesc = "DELETE_RECURSIVE".equals(operationType) ? "递归删除" : "删除";
            String interceptedCommand = String.format(
                    "%s操作 '%s' 目标路径 '%s'，需要审批",
                    operationDesc, operationType, targetPath);
            logger.warn("Delete operation detected: {}", targetPath);
            throw new SuspendExecutionException(interceptedCommand, toolCallId);
        }

        // 1. 检查是否写入受保护的文件扩展名
        if (isProtectedFileExtension(targetPath)) {
            String interceptedCommand = String.format(
                    "写入操作 '%s' 目标文件 '%s' 具有受保护的扩展名，需要审批",
                    operationType, targetPath);
            logger.warn("Protected file extension detected: {}", targetPath);
            throw new SuspendExecutionException(interceptedCommand, toolCallId);
        }

        // 2. 检查是否写入受保护的目录
        if (isProtectedDirectory(targetPath)) {
            String interceptedCommand = String.format(
                    "写入操作 '%s' 目标路径 '%s' 位于受保护的目录中，需要审批",
                    operationType, targetPath);
            logger.warn("Protected directory detected: {}", targetPath);
            throw new SuspendExecutionException(interceptedCommand, toolCallId);
        }

        // 3. 检查文件覆盖（如果启用）
        if (enableOverwriteApproval && isFileOverwrite(targetPath, operationType)) {
            String interceptedCommand = String.format(
                    "写入操作 '%s' 将覆盖已存在的文件 '%s'，需要审批",
                    operationType, targetPath);
            logger.warn("File overwrite detected: {}", targetPath);
            throw new SuspendExecutionException(interceptedCommand, toolCallId);
        }

        logger.debug("Write operation check passed: path={}", targetPath);
    }

    @Override
    public boolean fileExists(String targetPath) {
        try {
            return Files.exists(Paths.get(targetPath));
        } catch (Exception e) {
            logger.warn("Failed to check if file exists: {}", targetPath, e);
            return false;
        }
    }

    /**
     * 检查文件扩展名是否受保护
     *
     * @param path 文件路径
     * @return true 如果扩展名受保护
     */
    private boolean isProtectedFileExtension(String path) {
        String extension = getFileExtension(path);
        if (extension == null) {
            return false;
        }
        return PROTECTED_EXTENSIONS.contains(extension.toLowerCase());
    }

    /**
     * 检查路径是否在受保护的目录中
     *
     * @param path 文件路径
     * @return true 如果在受保护的目录中
     */
    private boolean isProtectedDirectory(String path) {
        Path normalizedPath = Paths.get(path).normalize();

        // 检查路径的每一部分
        for (Path part : normalizedPath) {
            if (PROTECTED_DIRECTORIES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否是文件覆盖操作
     *
     * @param path 文件路径
     * @param operationType 操作类型
     * @return true 如果是覆盖操作
     */
    private boolean isFileOverwrite(String path, String operationType) {
        // 只对写入操作检查覆盖（APPEND 模式不需要审批）
        if (!"WRITE".equals(operationType)) {
            return false;
        }
        return fileExists(path);
    }

    /**
     * 获取文件扩展名
     *
     * @param path 文件路径
     * @return 扩展名（不含点号），如果没有扩展名返回 null
     */
    private String getFileExtension(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        String fileName = Paths.get(path).getFileName().toString();

        // 特殊处理：如果文件以 "." 开头（如 .env, .pem）
        // 将点后面的部分视为扩展名，因为这些文件应该受到保护
        if (fileName.startsWith(".") && fileName.length() > 1) {
            // 对于 ".env" 这样的文件，返回 "env"
            // 对于 ".gitignore" 这样的文件，返回 "gitignore"
            int firstDotIndex = fileName.indexOf('.');
            if (firstDotIndex == 0 && fileName.lastIndexOf('.') == 0) {
                // 只有一个点在开头，如 ".env"
                return fileName.substring(1).toLowerCase();
            }
        }

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }

        return null;
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

    /**
     * 获取受保护的文件扩展名列表
     *
     * @return 受保护的文件扩展名集合
     */
    public static Set<String> getProtectedExtensions() {
        return Set.copyOf(PROTECTED_EXTENSIONS);
    }

    /**
     * 获取受保护的目录名称列表
     *
     * @return 受保护的目录名称集合
     */
    public static Set<String> getProtectedDirectories() {
        return Set.copyOf(PROTECTED_DIRECTORIES);
    }

    /**
     * 是否启用覆盖审批
     *
     * @return true 如果启用覆盖审批
     */
    public boolean isOverwriteApprovalEnabled() {
        return enableOverwriteApproval;
    }
}
