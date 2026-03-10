package tech.yesboss.tool.filesystem.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单的动态白名单管理器（MVP 版本）
 *
 * <p>这个实现提供了一个最小可行产品（MVP），支持：
 * <ul>
 *   <li>从配置文件加载初始白名单</li>
 *   <li>运行时动态添加路径到白名单</li>
 *   <li>基本的权限决策</li>
 * </ul>
 *
 * <p>未来将扩展支持：
 * <ul>
 *   <li>临时/永久/一次性决策区分</li>
 *   <li>用户交互处理</li>
 *   <li>审计日志</li>
 *   <li>风险评估</li>
 * </ul>
 */
public class SimpleDynamicWhitelistManager implements DynamicWhitelistManager {

    private static final Logger logger = LoggerFactory.getLogger(SimpleDynamicWhitelistManager.class);

    /**
     * 白名单配置文件路径
     */
    private final String whitelistConfigPath;

    /**
     * 永久白名单（从配置文件加载 + 运行时添加）
     */
    private final Set<String> permanentWhitelist = new HashSet<>();

    /**
     * 临时白名单（仅当前会话有效）
     */
    private final Set<String> temporaryWhitelist = new HashSet<>();

    /**
     * 一次性允许列表（仅当前操作有效）
     *
     * <p>使用 ConcurrentHashMap 支持并发访问</p>
     */
    private final Set<String> onceAllowlist = ConcurrentHashMap.newKeySet();

    /**
     * 决策缓存（用于异步接收用户响应）
     */
    private final ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<WhitelistDecision>>
            pendingDecisions = new ConcurrentHashMap<>();

    /**
     * 创建简单动态白名单管理器
     *
     * @param whitelistConfigPath 白名单配置文件路径（可以是 null）
     */
    public SimpleDynamicWhitelistManager(String whitelistConfigPath) {
        this.whitelistConfigPath = whitelistConfigPath;
        loadWhitelist();
    }

    @Override
    public boolean isPathInWhitelist(String path) {
        // 规范化路径
        String normalizedPath = normalizePath(path);

        // 1. 检查一次性允许
        if (onceAllowlist.contains(normalizedPath)) {
            logger.debug("Path in once-allowlist: {}", normalizedPath);
            return true;
        }

        // 2. 检查临时白名单
        if (temporaryWhitelist.contains(normalizedPath)) {
            logger.debug("Path in temporary whitelist: {}", normalizedPath);
            return true;
        }

        // 3. 检查永久白名单
        for (String whitelistPath : permanentWhitelist) {
            if (normalizedPath.startsWith(whitelistPath)) {
                logger.debug("Path in permanent whitelist: {} (matches: {})", normalizedPath, whitelistPath);
                return true;
            }
        }

        logger.debug("Path not in whitelist: {}", normalizedPath);
        return false;
    }

    @Override
    public WhitelistDecision requestUserDecision(
            String path,
            String operation,
            String toolName,
            String sessionId) {

        // MVP 版本：直接返回一个默认决策
        // 实际实现应该通过 IM 发送请求并等待用户响应

        logger.warn("User decision requested for path: {} (MVP: auto-denied)", path);

        // MVP 版本：自动拒绝
        return new WhitelistDecision(
                DecisionType.DENY,
                path,
                "MVP version: Auto-denied. Please add path to whitelist config.",
                System.currentTimeMillis()
        );
    }

    @Override
    public void addToWhitelist(String path, DecisionType decisionType) {
        String normalizedPath = normalizePath(path);

        switch (decisionType) {
            case ALLOW_ONCE:
                // 一次性允许
                onceAllowlist.add(normalizedPath);
                logger.info("Added to once-allowlist: {}", normalizedPath);
                break;

            case ALLOW_TEMPORARY:
                // 临时允许
                temporaryWhitelist.add(normalizedPath);
                logger.info("Added to temporary whitelist: {}", normalizedPath);
                break;

            case ALLOW_PERMANENT:
                // 永久白名单
                permanentWhitelist.add(normalizedPath);
                logger.info("Added to permanent whitelist: {}", normalizedPath);
                // 持久化
                persistWhitelist();
                break;

            case DENY:
                // 拒绝（不添加到白名单）
                logger.info("Denied access to: {}", normalizedPath);
                break;
        }
    }

    @Override
    public Set<String> getWhitelist() {
        Set<String> allPaths = new HashSet<>();
        allPaths.addAll(permanentWhitelist);
        allPaths.addAll(temporaryWhitelist);
        allPaths.addAll(onceAllowlist);
        return java.util.Collections.unmodifiableSet(allPaths);
    }

    @Override
    public void clearTemporaryWhitelist() {
        temporaryWhitelist.clear();
        onceAllowlist.clear();
        logger.info("Cleared temporary and once-allowlists");
    }

    @Override
    public void persistWhitelist() {
        if (whitelistConfigPath == null || whitelistConfigPath.isEmpty()) {
            logger.debug("No whitelist config path specified, skipping persistence");
            return;
        }

        try {
            // 构建简单的 YAML 格式
            StringBuilder yaml = new StringBuilder();
            yaml.append("# Dynamic Whitelist Configuration\n");
            yaml.append("# Generated by YesBoss\n");
            yaml.append("# Do not edit manually\n");
            yaml.append("\n");
            yaml.append("whitelist:\n");

            for (String path : permanentWhitelist) {
                yaml.append("  - \"").append(escapeYaml(path)).append("\"\n");
            }

            // 写入文件
            Files.writeString(Paths.get(whitelistConfigPath), yaml);

            logger.info("Whitelist persisted to: {}", whitelistConfigPath);

        } catch (Exception e) {
            logger.error("Failed to persist whitelist to: {}", whitelistConfigPath, e);
        }
    }

    @Override
    public void loadWhitelist() {
        if (whitelistConfigPath == null || whitelistConfigPath.isEmpty()) {
            logger.debug("No whitelist config path specified, starting with empty whitelist");
            return;
        }

        try {
            File configFile = new File(whitelistConfigPath);
            if (!configFile.exists()) {
                logger.info("Whitelist config file does not exist: {}", whitelistConfigPath);
                logger.info("Creating initial whitelist config...");
                persistWhitelist();
                return;
            }

            // 读取并解析简单的 YAML 格式
            String content = Files.readString(configFile.toPath());
            parseWhitelistConfig(content);

            logger.info("Loaded {} paths from whitelist config: {}",
                    permanentWhitelist.size(), whitelistConfigPath);

        } catch (Exception e) {
            logger.error("Failed to load whitelist from: {}", whitelistConfigPath, e);
            logger.warn("Starting with empty whitelist");
        }
    }

    /**
     * 解析白名单配置
     *
     * <p>支持简单的 YAML 格式：</p>
     * <pre>
     * whitelist:
     *   - "path1"
     *   - "path2"
     * </pre>
     */
    private void parseWhitelistConfig(String content) {
        permanentWhitelist.clear();

        String[] lines = content.split("\n");
        boolean inWhitelistSection = false;

        for (String line : lines) {
            line = line.trim();

            if (line.equals("whitelist:")) {
                inWhitelistSection = true;
                continue;
            }

            if (inWhitelistSection && line.startsWith("- \"")) {
                // 提取路径（例如：- "D:\Projects\External"）
                int start = 3; // "- \"".length()
                int end = line.lastIndexOf("\"");
                if (end > start) {
                    String path = line.substring(start, end);
                    permanentWhitelist.add(path);
                }
            } else if (inWhitelistSection && line.isEmpty()) {
                // 空行表示白名单部分结束
                break;
            }
        }
    }

    /**
     * 规范化路径
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }

        try {
            return Paths.get(path).toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            logger.warn("Failed to normalize path: {}", path, e);
            return path;
        }
    }

    /**
     * 转义 YAML 特殊字符
     */
    private String escapeYaml(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 手动添加路径到白名单（便捷方法）
     *
     * @param path 路径
     */
    public void addPath(String path) {
        addToWhitelist(path, DecisionType.ALLOW_PERMANENT);
    }

    /**
     * 手动添加多个路径到白名单（便捷方法）
     *
     * @param paths 路径数组
     */
    public void addPaths(String... paths) {
        for (String path : paths) {
            addPath(path);
        }
    }

    /**
     * 检查白名单是否为空
     *
     * @return true 如果所有白名单都为空
     */
    public boolean isEmpty() {
        return permanentWhitelist.isEmpty() &&
               temporaryWhitelist.isEmpty() &&
               onceAllowlist.isEmpty();
    }

    /**
     * 获取白名单统计信息
     *
     * @return 统计信息字符串
     */
    public String getStatistics() {
        return String.format(
                "Whitelist Statistics:\n" +
                "  Permanent: %d paths\n" +
                "  Temporary: %d paths\n" +
                "  Once: %d paths\n" +
                "  Total: %d paths",
                permanentWhitelist.size(),
                temporaryWhitelist.size(),
                onceAllowlist.size(),
                permanentWhitelist.size() + temporaryWhitelist.size() + onceAllowlist.size()
        );
    }
}
