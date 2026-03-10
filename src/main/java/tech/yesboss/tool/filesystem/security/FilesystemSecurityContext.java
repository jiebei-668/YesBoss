package tech.yesboss.tool.filesystem.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.tool.SuspendExecutionException;
import tech.yesboss.tool.filesystem.exception.FileSecurityException;
import tech.yesboss.tool.sandbox.SandboxInterceptor;

/**
 * 文件系统工具安全上下文
 *
 * <p>整合所有安全组件，为文件工具提供统一的安全检查、审计日志和性能监控。</p>
 *
 * <p><b>核心功能：</b></p>
 * <ul>
 *   <li><b>安全验证</b>: 路径验证、频率限制、权限检查</li>
 *   <li><b>审计日志</b>: 完整的操作审计记录</li>
 *   <li><b>性能监控</b>: 操作耗时和吞吐量统计</li>
 *   <li><b>人机回环</b>: 危险操作的审批机制</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * FilesystemSecurityContext context = new FilesystemSecurityContext(projectRoot, sandboxInterceptor);
 *
 * // 执行安全操作
 * context.beforeOperation("read_file", path, argumentsJson, toolCallId);
 * try {
 *     // 执行文件操作
 *     String result = readFile(path);
 *     context.afterSuccess("read_file", path, result.length());
 * } catch (Exception e) {
 *     context.afterFailure("read_file", path, e.getMessage());
 * }
 * }</pre>
 */
public class FilesystemSecurityContext {

    private static final Logger logger = LoggerFactory.getLogger(FilesystemSecurityContext.class);

    /**
     * 文件安全验证器
     */
    private final FileSecurityValidator securityValidator;

    /**
     * 操作频率限制器
     */
    private final OperationRateLimiter rateLimiter;

    /**
     * 审计日志记录器
     */
    private final OperationAuditLogger auditLogger;

    /**
     * 性能监控器
     */
    private final PerformanceMonitor performanceMonitor;

    /**
     * 安全沙箱拦截器（可选）
     */
    private final SandboxInterceptor sandboxInterceptor;

    /**
     * 项目根目录
     */
    private final String projectRoot;

    /**
     * 创建默认的安全上下文
     *
     * @param projectRoot 项目根目录
     */
    public FilesystemSecurityContext(String projectRoot) {
        this(projectRoot, null, true);
    }

    /**
     * 创建安全上下文（带沙箱拦截器）
     *
     * @param projectRoot 项目根目录
     * @param sandboxInterceptor 安全沙箱拦截器（可为 null）
     */
    public FilesystemSecurityContext(String projectRoot, SandboxInterceptor sandboxInterceptor) {
        this(projectRoot, sandboxInterceptor, true);
    }

    /**
     * 创建完全自定义的安全上下文
     *
     * @param projectRoot 项目根目录
     * @param sandboxInterceptor 安全沙箱拦截器（可为 null）
     * @param enableRateLimiting 是否启用频率限制
     */
    public FilesystemSecurityContext(String projectRoot, SandboxInterceptor sandboxInterceptor, boolean enableRateLimiting) {
        if (projectRoot == null || projectRoot.trim().isEmpty()) {
            throw new IllegalArgumentException("projectRoot cannot be null or empty");
        }

        this.projectRoot = projectRoot;
        this.sandboxInterceptor = sandboxInterceptor;
        this.securityValidator = new FileSecurityValidator(projectRoot);
        this.rateLimiter = enableRateLimiting ? new OperationRateLimiter() : null;
        this.auditLogger = new OperationAuditLogger();
        this.performanceMonitor = new PerformanceMonitor();

        logger.info("FilesystemSecurityContext initialized: projectRoot={}, rateLimiting={}, sandbox={}",
                projectRoot, enableRateLimiting, sandboxInterceptor != null);
    }

    /**
     * 操作前安全检查
     *
     * <p>执行以下检查：</p>
     * <ol>
     *   <li>频率限制检查</li>
     *   <li>路径安全验证</li>
     *   <li>人机回环检查（如适用）</li>
     * </ol>
     *
     * @param operation 操作名称
     * @param path 操作路径
     * @param argumentsJson 参数 JSON
     * @param toolCallId 工具调用 ID
     * @return 计时器 ID，用于 afterSuccess/afterFailure
     * @throws FileSecurityException 如果安全验证失败
     * @throws SuspendExecutionException 如果需要人机回环审批
     */
    public long beforeOperation(String operation, String path, String argumentsJson, String toolCallId)
            throws FileSecurityException, SuspendExecutionException {

        logger.debug("Before operation: operation={}, path={}", operation, path);

        // 1. 频率限制检查
        if (rateLimiter != null && !rateLimiter.tryAcquire(operation, path)) {
            auditLogger.logRejection(
                    mapOperation(operation),
                    path,
                    "RATE_LIMIT_EXCEEDED"
            );
            throw new FileSecurityException(operation, path, FileSecurityException.Reason.DANGEROUS_OPERATION, null);
        }

        // 2. 开始性能计时
        long timerId = performanceMonitor.startTimer(operation);

        return timerId;
    }

    /**
     * 验证读取访问权限
     *
     * @param path 文件路径
     * @throws FileSecurityException 如果验证失败
     */
    public void validateReadAccess(String path) throws FileSecurityException {
        securityValidator.validateReadAccess(path);
    }

    /**
     * 验证写入访问权限
     *
     * @param path 文件路径
     * @param fileSize 文件大小
     * @throws FileSecurityException 如果验证失败
     */
    public void validateWriteAccess(String path, long fileSize) throws FileSecurityException {
        securityValidator.validateWriteAccess(path, fileSize);
    }

    /**
     * 验证路径（基础验证）
     *
     * @param path 路径
     * @throws FileSecurityException 如果验证失败
     */
    public void validatePath(String path) throws FileSecurityException {
        securityValidator.validatePath(path);
    }

    /**
     * 检查写入操作是否需要审批
     *
     * @param targetPath 目标路径
     * @param argumentsJson 参数 JSON
     * @param toolCallId 工具调用 ID
     * @param operationType 操作类型
     * @throws SuspendExecutionException 如果需要审批
     */
    public void checkWriteOperation(String targetPath, String argumentsJson, String toolCallId, String operationType)
            throws SuspendExecutionException {

        if (sandboxInterceptor != null) {
            auditLogger.logApprovalRequired(mapOperationByType(operationType), targetPath, toolCallId);
            sandboxInterceptor.checkWriteOperation(targetPath, argumentsJson, toolCallId, operationType);
            auditLogger.logApproved(mapOperationByType(operationType), targetPath, toolCallId);
        }
    }

    /**
     * 操作成功后记录
     *
     * @param operation 操作名称
     * @param path 操作路径
     * @param timerId 计时器 ID
     * @param bytesProcessed 处理的字节数
     */
    public void afterSuccess(String operation, String path, long timerId, long bytesProcessed) {
        long durationMs = performanceMonitor.stopTimer(timerId, operation, bytesProcessed);
        auditLogger.logSuccess(mapOperation(operation), path, bytesProcessed, durationMs);

        logger.debug("Operation success: operation={}, path={}, bytes={}, duration={}ms",
                operation, path, bytesProcessed, durationMs);
    }

    /**
     * 操作失败后记录
     *
     * @param operation 操作名称
     * @param path 操作路径
     * @param timerId 计时器 ID
     * @param errorMessage 错误信息
     */
    public void afterFailure(String operation, String path, long timerId, String errorMessage) {
        long durationMs = performanceMonitor.stopTimer(timerId, operation);
        auditLogger.logFailure(mapOperation(operation), path, errorMessage, durationMs);

        logger.debug("Operation failure: operation={}, path={}, error={}, duration={}ms",
                operation, path, errorMessage, durationMs);
    }

    /**
     * 记录安全事件
     *
     * @param eventType 事件类型
     * @param path 相关路径
     * @param details 详情
     */
    public void logSecurityEvent(String eventType, String path, String details) {
        auditLogger.logSecurityEvent(eventType, path, details);
    }

    /**
     * 获取性能监控器
     */
    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }

    /**
     * 获取审计日志记录器
     */
    public OperationAuditLogger getAuditLogger() {
        return auditLogger;
    }

    /**
     * 获取操作频率限制器
     */
    public OperationRateLimiter getRateLimiter() {
        return rateLimiter;
    }

    /**
     * 获取文件安全验证器
     */
    public FileSecurityValidator getSecurityValidator() {
        return securityValidator;
    }

    /**
     * 获取性能摘要
     */
    public String getPerformanceSummary() {
        return performanceMonitor.getPerformanceSummary();
    }

    /**
     * 获取审计统计
     */
    public OperationAuditLogger.AuditStatistics getAuditStatistics() {
        return auditLogger.getStatistics();
    }

    /**
     * 重置所有统计
     */
    public void resetStatistics() {
        performanceMonitor.reset();
        auditLogger.resetStatistics();
        if (rateLimiter != null) {
            rateLimiter.resetAll();
        }
        logger.info("All statistics reset");
    }

    /**
     * 将操作名称映射到审计日志操作类型
     */
    private OperationAuditLogger.Operation mapOperation(String operation) {
        return switch (operation.toLowerCase()) {
            case "read_file" -> OperationAuditLogger.Operation.READ;
            case "write_file" -> OperationAuditLogger.Operation.WRITE;
            case "list_directory" -> OperationAuditLogger.Operation.LIST;
            case "create_directory" -> OperationAuditLogger.Operation.CREATE_DIRECTORY;
            case "delete_file" -> OperationAuditLogger.Operation.DELETE;
            case "search_files" -> OperationAuditLogger.Operation.SEARCH;
            default -> OperationAuditLogger.Operation.READ;
        };
    }

    /**
     * 根据操作类型字符串映射到审计日志操作类型
     */
    private OperationAuditLogger.Operation mapOperationByType(String operationType) {
        return switch (operationType.toUpperCase()) {
            case "WRITE" -> OperationAuditLogger.Operation.WRITE;
            case "APPEND" -> OperationAuditLogger.Operation.APPEND;
            case "DELETE" -> OperationAuditLogger.Operation.DELETE;
            case "DELETE_RECURSIVE" -> OperationAuditLogger.Operation.DELETE_RECURSIVE;
            case "CREATE_DIRECTORY" -> OperationAuditLogger.Operation.CREATE_DIRECTORY;
            default -> OperationAuditLogger.Operation.WRITE;
        };
    }
}
