package tech.yesboss.tool.filesystem.exception;

/**
 * 文件操作安全异常
 *
 * <p>当文件操作违反安全策略时抛出此异常。</p>
 *
 * <p><b>安全违规场景：</b></p>
 * <ul>
 *   <li><b>路径穿越攻击</b>: 尝试访问父目录（如 ../../etc/passwd）</li>
 *   <li><b>黑名单路径</b>: 尝试访问敏感目录（如 /etc, ~/.ssh）</li>
 *   <li><b>权限不足</b>: 尝试执行超出当前访问级别的操作</li>
 *   <li><b>危险操作</b>: 尝试执行危险操作（如删除系统文件）</li>
 * </ul>
 *
 * <p><b>与人机回环的关系：</b></p>
 * <p>此异常不同于 {@link tech.yesboss.tool.SuspendExecutionException}，</p>
 * <p>FileSecurityException 表示操作被拒绝，不会触发人机回环审批流程。</p>
 */
public class FileSecurityException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String operation;
    private final String path;
    private final Reason reason;
    private final String toolCallId;

    /**
     * 安全违规原因枚举
     */
    public enum Reason {
        /**
         * 路径穿越攻击检测
         */
        PATH_TRAVERSAL_DETECTED,

        /**
         * 访问黑名单路径
         */
        BLACKLISTED_PATH,

        /**
         * 权限不足
         */
        INSUFFICIENT_PERMISSIONS,

        /**
         * 危险操作被拦截
         */
        DANGEROUS_OPERATION,

        /**
         * 超出路径深度限制
         */
        PATH_DEPTH_LIMIT_EXCEEDED,

        /**
         * 路径包含非法字符
         */
        ILLEGAL_CHARACTERS,

        /**
         * 符号链接目标不安全
         */
        UNSAFE_SYMLINK_TARGET,

        /**
         * 文件大小超过限制
         */
        FILE_SIZE_LIMIT_EXCEEDED,

        /**
         * 试图写入受保护的文件
         */
        PROTECTED_FILE,

        /**
         * 文件覆盖确认被拒绝
         */
        OVERWRITE_REJECTED,

        /**
         * 磁盘空间不足
         */
        INSUFFICIENT_DISK_SPACE
    }

    /**
     * 创建一个新的 FileSecurityException
     *
     * @param operation 被拒绝的操作类型
     * @param path 被拒绝的路径
     * @param reason 拒绝原因
     * @param toolCallId 引发异常的工具调用 ID（可为 null）
     */
    public FileSecurityException(String operation, String path, Reason reason, String toolCallId) {
        super(String.format("File operation '%s' on path '%s' was rejected due to: %s",
                operation, path, reason));
        this.operation = operation;
        this.path = path;
        this.reason = reason;
        this.toolCallId = toolCallId;
    }

    /**
     * 获取被拒绝的操作类型
     *
     * @return 操作类型
     */
    public String getOperation() {
        return operation;
    }

    /**
     * 获取被拒绝的路径
     *
     * @return 路径
     */
    public String getPath() {
        return path;
    }

    /**
     * 获取拒绝原因
     *
     * @return 原因
     */
    public Reason getReason() {
        return reason;
    }

    /**
     * 获取工具调用 ID
     *
     * @return tool_call_id（可为 null）
     */
    public String getToolCallId() {
        return toolCallId;
    }

    /**
     * 创建路径穿越攻击异常
     *
     * @param operation 操作类型
     * @param path 恶意路径
     * @return FileSecurityException 对象
     */
    public static FileSecurityException pathTraversal(String operation, String path) {
        return new FileSecurityException(operation, path, Reason.PATH_TRAVERSAL_DETECTED, null);
    }

    /**
     * 创建黑名单路径异常
     *
     * @param operation 操作类型
     * @param path 黑名单路径
     * @return FileSecurityException 对象
     */
    public static FileSecurityException blacklistedPath(String operation, String path) {
        return new FileSecurityException(operation, path, Reason.BLACKLISTED_PATH, null);
    }

    /**
     * 创建权限不足异常
     *
     * @param operation 操作类型
     * @param path 目标路径
     * @return FileSecurityException 对象
     */
    public static FileSecurityException insufficientPermissions(String operation, String path) {
        return new FileSecurityException(operation, path, Reason.INSUFFICIENT_PERMISSIONS, null);
    }

    /**
     * 创建危险操作异常
     *
     * @param operation 操作类型
     * @param path 目标路径
     * @return FileSecurityException 对象
     */
    public static FileSecurityException dangerousOperation(String operation, String path) {
        return new FileSecurityException(operation, path, Reason.DANGEROUS_OPERATION, null);
    }

    /**
     * 创建路径深度超限异常
     *
     * @param operation 操作类型
     * @param path 目标路径
     * @return FileSecurityException 对象
     */
    public static FileSecurityException pathDepthLimitExceeded(String operation, String path) {
        return new FileSecurityException(operation, path, Reason.PATH_DEPTH_LIMIT_EXCEEDED, null);
    }

    /**
     * 创建非法字符异常
     *
     * @param operation 操作类型
     * @param path 目标路径
     * @return FileSecurityException 对象
     */
    public static FileSecurityException illegalCharacters(String operation, String path) {
        return new FileSecurityException(operation, path, Reason.ILLEGAL_CHARACTERS, null);
    }

    /**
     * 创建不安全符号链接异常
     *
     * @param operation 操作类型
     * @param path 目标路径
     * @return FileSecurityException 对象
     */
    public static FileSecurityException unsafeSymlinkTarget(String operation, String path) {
        return new FileSecurityException(operation, path, Reason.UNSAFE_SYMLINK_TARGET, null);
    }

    /**
     * 创建文件大小超限异常
     *
     * @param operation 操作类型
     * @param path 目标路径
     * @return FileSecurityException 对象
     */
    public static FileSecurityException fileSizeLimitExceeded(String operation, String path) {
        return new FileSecurityException(operation, path, Reason.FILE_SIZE_LIMIT_EXCEEDED, null);
    }

    /**
     * 创建受保护文件异常
     *
     * @param operation 操作类型
     * @param path 目标路径
     * @return FileSecurityException 对象
     */
    public static FileSecurityException protectedFile(String operation, String path) {
        return new FileSecurityException(operation, path, Reason.PROTECTED_FILE, null);
    }

    /**
     * 创建覆盖拒绝异常
     *
     * @param operation 操作类型
     * @param path 目标路径
     * @return FileSecurityException 对象
     */
    public static FileSecurityException overwriteRejected(String operation, String path) {
        return new FileSecurityException(operation, path, Reason.OVERWRITE_REJECTED, null);
    }

    /**
     * 创建磁盘空间不足异常
     *
     * @param operation 操作类型
     * @param path 目标路径
     * @return FileSecurityException 对象
     */
    public static FileSecurityException insufficientDiskSpace(String operation, String path) {
        return new FileSecurityException(operation, path, Reason.INSUFFICIENT_DISK_SPACE, null);
    }

    /**
     * 设置工具调用 ID
     *
     * @param toolCallId 工具调用 ID
     * @return 新的 FileSecurityException 对象
     */
    public FileSecurityException withToolCallId(String toolCallId) {
        return new FileSecurityException(operation, path, reason, toolCallId);
    }

    /**
     * 判断是否应该触发人机回环审批
     *
     * <p>某些安全违规可能需要人工审批，例如：</p>
     * <ul>
     *   <li>权限不足但文件路径看起来合理</li>
     *   <li>文件大小略超过限制</li>
     * </ul>
     *
     * @return 如果应该触发人机回环返回 true
     */
    public boolean shouldRequestApproval() {
        return reason == Reason.INSUFFICIENT_PERMISSIONS ||
               reason == Reason.FILE_SIZE_LIMIT_EXCEEDED;
    }

    /**
     * 获取用户友好的错误消息
     *
     * @return 用户友好的错误消息
     */
    public String getUserFriendlyMessage() {
        return switch (reason) {
            case PATH_TRAVERSAL_DETECTED ->
                String.format("路径 '%s' 包含非法的父目录引用（..），可能是路径穿越攻击", path);
            case BLACKLISTED_PATH ->
                String.format("路径 '%s' 在安全黑名单中，禁止访问", path);
            case INSUFFICIENT_PERMISSIONS ->
                String.format("当前权限不足，无法执行 '%s' 操作", operation);
            case DANGEROUS_OPERATION ->
                String.format("操作 '%s' 存在安全风险，已被拦截", operation);
            case PATH_DEPTH_LIMIT_EXCEEDED ->
                String.format("路径 '%s' 深度超过限制", path);
            case ILLEGAL_CHARACTERS ->
                String.format("路径 '%s' 包含非法字符", path);
            case UNSAFE_SYMLINK_TARGET ->
                String.format("符号链接目标 '%s' 不安全", path);
            case FILE_SIZE_LIMIT_EXCEEDED ->
                String.format("文件 '%s' 大小超过限制", path);
            case PROTECTED_FILE ->
                String.format("文件 '%s' 受保护，禁止写入", path);
            case OVERWRITE_REJECTED ->
                String.format("文件 '%s' 已存在，覆盖操作被拒绝", path);
            case INSUFFICIENT_DISK_SPACE ->
                String.format("磁盘空间不足，无法写入文件 '%s'", path);
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FileSecurityException{");
        sb.append("operation='").append(operation).append('\'');
        sb.append(", path='").append(path).append('\'');
        sb.append(", reason=").append(reason);
        if (toolCallId != null) {
            sb.append(", toolCallId='").append(toolCallId).append('\'');
        }
        sb.append('}');
        return sb.toString();
    }
}
