package tech.yesboss.tool.filesystem.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.tool.filesystem.exception.FileSecurityException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 文件操作安全验证器
 *
 * <p>提供统一的文件操作安全验证机制，防止路径遍历和敏感文件访问。</p>
 *
 * <p><b>核心安全策略：</b></p>
 * <ul>
 *   <li><b>路径白名单</b>: 只允许访问项目目录和 /tmp 目录</li>
 *   <li><b>路径黑名单</b>: 禁止访问敏感文件和目录（如 /etc/passwd, ~/.ssh）</li>
 *   <li><b>文件类型限制</b>: 只允许操作文本格式文件</li>
 *   <li><b>文件大小限制</b>: 默认限制 10MB</li>
 *   <li><b>路径规范化</b>: 防止 ../ 路径遍历攻击</li>
 * </ul>
 *
 * <p><b>典型使用流程：</b></p>
 * <pre>{@code
 * FileSecurityValidator validator = new FileSecurityValidator("/project/root");
 *
 * // 验证读取操作
 * validator.validateReadAccess("/project/root/src/Main.java");
 *
 * // 验证写入操作
 * validator.validateWriteAccess("/project/root/src/output.txt", 1024);
 *
 * // 仅验证路径
 * validator.validatePath("/project/root/src");
 * }</pre>
 */
public class FileSecurityValidator {

    private static final Logger logger = LoggerFactory.getLogger(FileSecurityValidator.class);

    /**
     * 默认文件大小限制：10MB
     */
    private static final long DEFAULT_MAX_FILE_SIZE = 10L * 1024 * 1024; // 10MB

    /**
     * 最大路径深度限制
     */
    private static final int MAX_PATH_DEPTH = 20;

    /**
     * 最小磁盘空间要求：100MB
     */
    private static final long MIN_DISK_SPACE = 100L * 1024 * 1024; // 100MB

    /**
     * 是否启用覆盖保护（默认启用）
     */
    private static final boolean DEFAULT_OVERWRITE_PROTECTION = true;

    /**
     * 路径非法字符正则表达式
     */
    private static final Pattern ILLEGAL_CHARACTERS_PATTERN = Pattern.compile("[\0<>:\"|?*\u0000-\u001f]");

    /**
     * 项目根目录（绝对路径）
     */
    private final String projectRoot;

    /**
     * 最大文件大小限制（字节）
     */
    private final long maxFileSize;

    /**
     * 路径黑名单（敏感路径）
     */
    private final Set<String> pathBlacklist;

    /**
     * 允许的文件扩展名（文本格式）
     */
    private final Set<String> allowedExtensions;

    /**
     * 受保护的文件列表（禁止写入）
     */
    private final Set<String> protectedFiles;

    /**
     * 最小磁盘空间要求（字节）
     */
    private final long minDiskSpace;

    /**
     * 是否启用覆盖保护
     */
    private final boolean overwriteProtection;

    /**
     * 动态白名单管理器（可选）
     */
    private final DynamicWhitelistManager dynamicWhitelistManager;

    /**
     * 创建一个文件安全验证器
     *
     * @param projectRoot 项目根目录（必需）
     */
    public FileSecurityValidator(String projectRoot) {
        this(projectRoot, DEFAULT_MAX_FILE_SIZE);
    }

    /**
     * 创建一个文件安全验证器（自定义文件大小限制）
     *
     * @param projectRoot 项目根目录（必需）
     * @param maxFileSize 最大文件大小（字节）
     * @throws IllegalArgumentException 如果 projectRoot 为空或无效
     */
    public FileSecurityValidator(String projectRoot, long maxFileSize) {
        this(projectRoot, maxFileSize, MIN_DISK_SPACE, DEFAULT_OVERWRITE_PROTECTION);
    }

    /**
     * 创建一个文件安全验证器（完全自定义）
     *
     * @param projectRoot 项目根目录（必需）
     * @param maxFileSize 最大文件大小（字节）
     * @param minDiskSpace 最小磁盘空间要求（字节）
     * @param overwriteProtection 是否启用覆盖保护
     * @throws IllegalArgumentException 如果 projectRoot 为空或无效
     */
    public FileSecurityValidator(String projectRoot, long maxFileSize, long minDiskSpace, boolean overwriteProtection) {
        this(projectRoot, maxFileSize, minDiskSpace, overwriteProtection, null);
    }

    /**
     * 创建一个文件安全验证器（支持动态白名单）
     *
     * @param projectRoot 项目根目录（必需）
     * @param maxFileSize 最大文件大小（字节）
     * @param minDiskSpace 最小磁盘空间要求（字节）
     * @param overwriteProtection 是否启用覆盖保护
     * @param dynamicWhitelistManager 动态白名单管理器（可选）
     * @throws IllegalArgumentException 如果 projectRoot 为空或无效
     */
    public FileSecurityValidator(String projectRoot, long maxFileSize, long minDiskSpace, boolean overwriteProtection,
                                DynamicWhitelistManager dynamicWhitelistManager) {
        if (projectRoot == null || projectRoot.trim().isEmpty()) {
            throw new IllegalArgumentException("Project root cannot be null or empty");
        }

        // 规范化项目根目录
        this.projectRoot = normalizePath(projectRoot);
        this.maxFileSize = maxFileSize;
        this.minDiskSpace = minDiskSpace;
        this.overwriteProtection = overwriteProtection;
        this.dynamicWhitelistManager = dynamicWhitelistManager;

        // 初始化路径黑名单
        this.pathBlacklist = initializePathBlacklist();

        // 初始化允许的文件扩展名
        this.allowedExtensions = initializeAllowedExtensions();

        // 初始化受保护的文件列表
        this.protectedFiles = initializeProtectedFiles();

        logger.info("FileSecurityValidator initialized with projectRoot={}, maxFileSize={} bytes, minDiskSpace={} bytes, overwriteProtection={}",
                this.projectRoot, this.maxFileSize, this.minDiskSpace, this.overwriteProtection);
    }

    /**
     * 验证路径是否安全（不区分操作类型）
     *
     * <p>此方法执行基础的安全检查：</p>
     * <ul>
     *   <li>路径不为空</li>
     *   <li>路径不包含非法字符</li>
     *   <li>路径规范化成功</li>
     *   <li>路径不遍历攻击（../）</li>
     *   <li>路径不在黑名单中</li>
     *   <li>路径在白名单中（项目目录或 /tmp）</li>
     * </ul>
     *
     * @param path 要验证的路径
     * @throws FileSecurityException 如果路径不安全
     * @throws IllegalArgumentException 如果路径为空
     */
    public void validatePath(String path) throws FileSecurityException {
        logger.debug("Validating path: {}", path);

        // 1. 检查路径是否为空
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        // 2. 检查路径是否包含非法字符
        checkIllegalCharacters("VALIDATE_PATH", path);

        // 3. 规范化路径
        String normalizedPath;
        try {
            normalizedPath = normalizePath(path);
            logger.debug("Normalized path: {} -> {}", path, normalizedPath);
        } catch (Exception e) {
            logger.warn("Failed to normalize path: {}", path, e);
            throw FileSecurityException.illegalCharacters("VALIDATE_PATH", path);
        }

        // 4. 检查路径遍历攻击
        checkPathTraversal("VALIDATE_PATH", path, normalizedPath);

        // 5. 检查路径深度限制
        checkPathDepth("VALIDATE_PATH", normalizedPath);

        // 6. 检查路径黑名单
        checkBlacklist("VALIDATE_PATH", normalizedPath);

        // 7. 检查路径白名单
        checkWhitelist("VALIDATE_PATH", normalizedPath);

        logger.debug("Path validation passed: {}", normalizedPath);
    }

    /**
     * 验证读取访问权限
     *
     * <p>除了基础路径验证外，还检查：</p>
     * <ul>
     *   <li>路径是否存在</li>
     *   <li>文件是否可读</li>
     *   <li>文件类型是否允许</li>
     *   <li>文件大小是否超限</li>
     * </ul>
     *
     * @param path 要读取的文件路径
     * @throws FileSecurityException 如果安全验证失败
     * @throws IllegalArgumentException 如果路径为空
     */
    public void validateReadAccess(String path) throws FileSecurityException {
        logger.debug("Validating read access for: {}", path);

        // 先进行基础路径验证
        validatePath(path);

        // 规范化路径
        String normalizedPath = normalizePath(path);

        // 检查文件是否存在
        File file = new File(normalizedPath);
        if (!file.exists()) {
            logger.debug("File does not exist: {}", normalizedPath);
            // 对于读取操作，文件不存在不是安全问题，而是业务问题
            // 这里我们只记录日志，不抛出异常
            return;
        }

        // 检查是否可读
        if (!file.canRead()) {
            logger.warn("File is not readable: {}", normalizedPath);
            throw FileSecurityException.insufficientPermissions("READ", normalizedPath);
        }

        // 如果是文件，检查文件类型和大小
        if (file.isFile()) {
            // 检查文件类型
            checkFileType("READ", normalizedPath);

            // 检查文件大小
            checkFileSize("READ", normalizedPath, file.length());
        }

        logger.debug("Read access validation passed: {}", normalizedPath);
    }

    /**
     * 验证写入访问权限
     *
     * <p>除了基础路径验证外，还检查：</p>
     * <ul>
     *   <li>文件类型是否允许</li>
     *   <li>文件大小是否超限（如果文件已存在）</li>
     *   <li>父目录是否存在且可写</li>
     *   <li>文件是否在受保护列表中</li>
     *   <li>磁盘空间是否充足</li>
     *   <li>是否允许覆盖已存在的文件</li>
     * </ul>
     *
     * @param path 要写入的文件路径
     * @param fileSize 要写入的文件大小（字节，0 表示未知）
     * @throws FileSecurityException 如果安全验证失败
     * @throws IllegalArgumentException 如果路径为空
     */
    public void validateWriteAccess(String path, long fileSize) throws FileSecurityException {
        logger.debug("Validating write access for: {}, size: {} bytes", path, fileSize);

        // 先进行基础路径验证
        validatePath(path);

        // 规范化路径
        String normalizedPath = normalizePath(path);

        // 检查文件是否在受保护列表中（优先检查）
        checkProtectedFile("WRITE", normalizedPath);

        // 检查文件类型
        checkFileType("WRITE", normalizedPath);

        // 检查文件大小（如果提供了大小）
        if (fileSize > 0) {
            checkFileSize("WRITE", normalizedPath, fileSize);
        }

        // 如果文件已存在，检查当前大小
        File file = new File(normalizedPath);
        if (file.exists() && file.isFile()) {
            checkFileSize("WRITE", normalizedPath, file.length());
        }

        // 检查父目录是否可写
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.canWrite()) {
            logger.warn("Parent directory is not writable: {}", parentDir.getAbsolutePath());
            throw FileSecurityException.insufficientPermissions("WRITE", normalizedPath);
        }

        // 检查磁盘空间是否充足
        checkDiskSpace("WRITE", normalizedPath, fileSize);

        // 检查覆盖保护
        if (overwriteProtection && file.exists() && file.isFile()) {
            logger.warn("File already exists and overwrite protection is enabled: {}", normalizedPath);
            throw FileSecurityException.overwriteRejected("WRITE", normalizedPath);
        }

        logger.debug("Write access validation passed: {}", normalizedPath);
    }

    /**
     * 验证写入访问权限（文件大小未知）
     *
     * @param path 要写入的文件路径
     * @throws FileSecurityException 如果安全验证失败
     */
    public void validateWriteAccess(String path) throws FileSecurityException {
        validateWriteAccess(path, 0);
    }

    // ==================== 私有验证方法 ====================

    /**
     * 检查路径是否包含非法字符
     *
     * <p>注意：Windows 路径中的驱动器号（D:）和路径分隔符（\）是合法的。
     * 此方法会跳过路径中的驱动器号和分隔符，只检查文件名部分。</p>
     */
    private void checkIllegalCharacters(String operation, String path) throws FileSecurityException {
        // 分离路径和文件名
        // Windows 路径可能包含驱动器号（D:）和反斜杠分隔符
        // 我们只检查文件名部分是否包含非法字符

        // 提取文件名（最后一个路径分隔符之后的部分）
        String fileName;
        int lastSeparator = Math.max(
                path.lastIndexOf('/'),
                path.lastIndexOf(java.io.File.separatorChar)
        );

        if (lastSeparator >= 0 && lastSeparator < path.length() - 1) {
            fileName = path.substring(lastSeparator + 1);
        } else {
            // 如果没有分隔符，整个路径就是文件名
            fileName = path;
        }

        // 检查文件名是否包含非法字符
        if (ILLEGAL_CHARACTERS_PATTERN.matcher(fileName).find()) {
            logger.warn("Path contains illegal characters: {}", path);
            throw FileSecurityException.illegalCharacters(operation, path);
        }
    }

    /**
     * 检查路径遍历攻击
     */
    private void checkPathTraversal(String operation, String originalPath, String normalizedPath) throws FileSecurityException {
        // 检查原始路径中是否包含 ..
        if (originalPath.contains("..") || originalPath.contains("." + File.separator)) {
            logger.warn("Path contains traversal patterns (..): {}", originalPath);
            throw FileSecurityException.pathTraversal(operation, originalPath);
        }

        // 注意：不再在此方法中检查白名单
        // 白名单检查由 checkWhitelist 方法单独处理
        // 这样可以区分"路径遍历攻击"和"不在白名单内的路径"两种不同的情况
    }

    /**
     * 检查路径深度限制
     */
    private void checkPathDepth(String operation, String path) throws FileSecurityException {
        int depth = path.split(File.separator.equals("/") ? "/" : File.separator.equals("\\") ? "\\\\" : Pattern.quote(File.separator)).length;
        if (depth > MAX_PATH_DEPTH) {
            logger.warn("Path depth exceeds limit: {} (depth: {}, limit: {})", path, depth, MAX_PATH_DEPTH);
            throw FileSecurityException.pathDepthLimitExceeded(operation, path);
        }
    }

    /**
     * 检查路径黑名单
     */
    private void checkBlacklist(String operation, String path) throws FileSecurityException {
        // 检查精确匹配
        if (pathBlacklist.contains(path)) {
            logger.warn("Path is in blacklist: {}", path);
            throw FileSecurityException.blacklistedPath(operation, path);
        }

        // 检查前缀匹配（防止访问黑名单目录的子路径）
        for (String blacklistedPath : pathBlacklist) {
            if (path.startsWith(blacklistedPath + File.separator)) {
                logger.warn("Path is under blacklisted directory: {} (under: {})", path, blacklistedPath);
                throw FileSecurityException.blacklistedPath(operation, path);
            }
        }
    }

    /**
     * 检查路径白名单（支持动态白名单）
     */
    private void checkWhitelist(String operation, String path) throws FileSecurityException {
        // 1. 首先检查静态白名单
        if (isPathInStaticWhitelist(path)) {
            logger.debug("Path in static whitelist: {}", path);
            return;
        }

        // 2. 检查动态白名单（如果可用）
        if (dynamicWhitelistManager != null && dynamicWhitelistManager.isPathInWhitelist(path)) {
            logger.debug("Path in dynamic whitelist: {}", path);
            return;
        }

        // 3. 不在白名单中，拒绝
        logger.warn("Path is not in whitelist (static or dynamic): {}", path);
        throw FileSecurityException.blacklistedPath(operation, path);
    }

    /**
     * 检查文件类型是否允许
     */
    private void checkFileType(String operation, String path) throws FileSecurityException {
        // 如果是目录，不需要检查扩展名
        File file = new File(path);
        if (file.isDirectory()) {
            return;
        }

        // 提取文件扩展名
        String extension = getFileExtension(path);
        if (extension == null) {
            // 无扩展名的文件（如 README, Makefile）允许
            logger.debug("File has no extension, allowing: {}", path);
            return;
        }

        // 检查扩展名是否在允许列表中
        if (!allowedExtensions.contains(extension.toLowerCase())) {
            logger.warn("File type not allowed: {} (extension: {})", path, extension);
            throw FileSecurityException.dangerousOperation(operation, path);
        }

        logger.debug("File type allowed: {} (extension: {})", path, extension);
    }

    /**
     * 检查文件大小是否超限
     */
    private void checkFileSize(String operation, String path, long fileSize) throws FileSecurityException {
        if (fileSize > maxFileSize) {
            logger.warn("File size exceeds limit: {} (size: {} bytes, limit: {} bytes)",
                    path, fileSize, maxFileSize);
            throw FileSecurityException.fileSizeLimitExceeded(operation, path);
        }
        logger.debug("File size within limit: {} (size: {} bytes)", path, fileSize);
    }

    /**
     * 检查文件是否在受保护列表中
     */
    private void checkProtectedFile(String operation, String path) throws FileSecurityException {
        // 检查精确匹配
        if (protectedFiles.contains(path)) {
            logger.warn("File is in protected list: {}", path);
            throw FileSecurityException.protectedFile(operation, path);
        }

        // 检查前缀匹配（防止覆盖受保护文件的子路径）
        for (String protectedPath : protectedFiles) {
            // 检查是否是受保护目录下的文件
            File protectedFile = new File(protectedPath);
            if (protectedFile.isDirectory()) {
                if (path.startsWith(protectedPath + File.separator)) {
                    logger.warn("Path is under protected directory: {} (under: {})", path, protectedPath);
                    throw FileSecurityException.protectedFile(operation, path);
                }
            }
        }

        logger.debug("File is not protected: {}", path);
    }

    /**
     * 检查磁盘空间是否充足
     */
    private void checkDiskSpace(String operation, String path, long requiredSize) throws FileSecurityException {
        File file = new File(path);
        File parentDir = file.getParentFile();

        // 如果父目录不存在，使用项目根目录
        if (parentDir == null || !parentDir.exists()) {
            parentDir = new File(projectRoot);
        }

        // 获取可用空间
        long usableSpace = parentDir.getUsableSpace();

        // 计算所需空间（文件大小 + 最小安全余量）
        long requiredSpace = Math.max(requiredSize, minDiskSpace);

        if (usableSpace < requiredSpace) {
            logger.warn("Insufficient disk space: {} (required: {} bytes, available: {} bytes)",
                    path, requiredSpace, usableSpace);
            throw FileSecurityException.insufficientDiskSpace(operation, path);
        }

        logger.debug("Disk space is sufficient: {} (required: {} bytes, available: {} bytes)",
                path, requiredSpace, usableSpace);
    }

    // ==================== 辅助方法 ====================

    /**
     * 规范化路径
     *
     * <p>对于相对路径，会相对于 projectRoot 进行解析。</p>
     */
    private String normalizePath(String path) {
        try {
            Path inputPath = Paths.get(path);

            // 如果是绝对路径，直接规范化
            if (inputPath.isAbsolute()) {
                Path normalized = inputPath.normalize();
                return normalized.toAbsolutePath().toString();
            }

            // 如果是相对路径，相对于 projectRoot 解析
            Path normalized = Paths.get(projectRoot, path).normalize();
            return normalized.toAbsolutePath().toString();
        } catch (Exception e) {
            logger.error("Failed to normalize path: {}", path, e);
            throw new IllegalArgumentException("Invalid path: " + path, e);
        }
    }

    /**
     * 检查路径是否在静态白名单中
     *
     * <p>静态白名单包括：项目根目录和系统临时目录</p>
     */
    private boolean isPathInStaticWhitelist(String path) {
        // 检查是否在项目根目录下
        if (path.startsWith(projectRoot)) {
            return true;
        }

        // 检查是否在 /tmp 目录下
        String tmpDir = System.getProperty("java.io.tmpdir");
        if (tmpDir != null && path.startsWith(tmpDir)) {
            return true;
        }

        return false;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        // 跳过隐藏文件（如 .gitignore）
        File file = new File(path);
        String name = file.getName();
        if (name.startsWith(".")) {
            return null;
        }

        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < name.length() - 1) {
            return name.substring(dotIndex + 1);
        }

        return null;
    }

    /**
     * 初始化路径黑名单
     */
    private Set<String> initializePathBlacklist() {
        return Set.of(
                // Unix/Linux 系统敏感路径
                "/etc/passwd",
                "/etc/shadow",
                "/etc/sudoers",
                "/etc/hosts",
                "/root",
                "/root/.ssh",
                System.getProperty("user.home") + "/.ssh",
                System.getProperty("user.home") + "/.gnupg",

                // Windows 系统敏感路径
                "C:\\Windows\\System32\\config\\SAM",
                "C:\\Windows\\System32\\config\\SECURITY",

                // 项目外的敏感目录
                System.getProperty("user.home") + "/.aws",
                System.getProperty("user.home") + "/.kube"
        );
    }

    /**
     * 初始化允许的文件扩展名（文本格式）
     */
    private Set<String> initializeAllowedExtensions() {
        return Set.of(
                // 代码文件
                "java", "kt", "scala", "groovy",
                "js", "ts", "jsx", "tsx", "vue", "svelte",
                "py", "rb", "php", "go", "rs", "c", "cpp", "h", "hpp",
                "cs", "swift", "dart", "lua", "r", "m", "mm",

                // 配置文件
                "json", "xml", "yaml", "yml", "toml", "ini", "cfg", "conf",
                "properties", "env", ".env",

                // 文档文件
                "md", "markdown", "txt", "text", "csv",
                "html", "htm", "css", "scss", "sass", "less",

                // 脚本文件
                "sh", "bash", "zsh", "fish", "ps1", "bat", "cmd",

                // 其他文本文件
                "log", "gitignore", "dockerignore", "editorconfig",
                "eslintrc", "prettierrc", "babelrc",

                // SQL 和数据库
                "sql", "ddl", "dml",

                // 测试文件
                "test", "spec",

                // 构建文件
                "gradle", "pom", "mvn", "dockerfile"
        );
    }

    /**
     * 初始化受保护的文件列表
     */
    private Set<String> initializeProtectedFiles() {
        // 默认保护项目根目录下的关键文件和目录
        Set<String> protectedSet = new java.util.HashSet<>();

        // 添加项目根目录下的关键文件
        protectedSet.add(projectRoot + File.separator + "pom.xml");
        protectedSet.add(projectRoot + File.separator + "build.gradle");
        protectedSet.add(projectRoot + File.separator + "package.json");
        protectedSet.add(projectRoot + File.separator + ".git");

        // 添加数据目录
        protectedSet.add(projectRoot + File.separator + "data");
        // logs 和 target 目录不再保护 - 这些目录应该允许正常写入
        // protectedSet.add(projectRoot + File.separator + "logs");    // 日志目录应允许写入
        // protectedSet.add(projectRoot + File.separator + "target");  // 构建输出应允许写入
        protectedSet.add(projectRoot + File.separator + "build");

        // 添加配置文件
        protectedSet.add(projectRoot + File.separator + "application.yml");
        protectedSet.add(projectRoot + File.separator + "application.properties");
        protectedSet.add(projectRoot + File.separator + "application.conf");

        // 注意：.gitignore 等隐藏文件不在保护列表中，允许写入
        // 这是因为这些文件经常需要编辑

        // TODO: 未来可以从配置文件读取保护列表
        // 从 application.yml 的 filesystem.writeProtection.protectedFiles 配置项读取

        return Set.copyOf(protectedSet);
    }

    // ==================== Getter 方法 ====================

    /**
     * 获取项目根目录
     */
    public String getProjectRoot() {
        return projectRoot;
    }

    /**
     * 获取最大文件大小限制
     */
    public long getMaxFileSize() {
        return maxFileSize;
    }

    /**
     * 获取路径黑名单（不可修改）
     */
    public Set<String> getPathBlacklist() {
        return Set.copyOf(pathBlacklist);
    }

    /**
     * 获取允许的文件扩展名（不可修改）
     */
    public Set<String> getAllowedExtensions() {
        return Set.copyOf(allowedExtensions);
    }

    /**
     * 获取受保护的文件列表（不可修改）
     */
    public Set<String> getProtectedFiles() {
        return Set.copyOf(protectedFiles);
    }

    /**
     * 获取最小磁盘空间要求
     */
    public long getMinDiskSpace() {
        return minDiskSpace;
    }

    /**
     * 是否启用覆盖保护
     */
    public boolean isOverwriteProtectionEnabled() {
        return overwriteProtection;
    }

    @Override
    public String toString() {
        return String.format("FileSecurityValidator{projectRoot='%s', maxFileSize=%d bytes}",
                projectRoot, maxFileSize);
    }

    // ==================== 静态辅助方法（用于测试配置） ====================

    /**
     * 获取默认的最大文件大小限制
     *
     * @return 默认最大文件大小（字节）
     */
    public static long getMaxFileSizeDefault() {
        return DEFAULT_MAX_FILE_SIZE;
    }

    /**
     * 获取默认的最小磁盘空间要求
     *
     * @return 默认最小磁盘空间（字节）
     */
    public static long getMinDiskSpaceDefault() {
        return MIN_DISK_SPACE;
    }

    /**
     * 获取默认的覆盖保护设置
     *
     * @return 默认覆盖保护设置
     */
    public static boolean getDefaultOverwriteProtection() {
        return DEFAULT_OVERWRITE_PROTECTION;
    }
}
