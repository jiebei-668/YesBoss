package tech.yesboss.tool.filesystem.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 操作审计日志记录器
 *
 * <p>为文件操作提供完整的审计日志，记录所有操作详情用于安全追踪和分析。</p>
 *
 * <p><b>核心功能：</b></p>
 * <ul>
 *   <li><b>操作记录</b>: 记录所有文件操作的详细信息</li>
 *   <li><b>安全审计</b>: 记录安全相关事件（拒绝、审批等）</li>
 *   <li><b>统计汇总</b>: 提供操作统计和趋势分析</li>
 *   <li><b>异常追踪</b>: 记录操作失败和异常情况</li>
 * </ul>
 *
 * <p><b>日志格式：</b></p>
 * <pre>
 * [AUDIT] 2024-01-15T10:30:45.123 | READ | SUCCESS | /path/to/file | 1024 bytes | 23ms | user@host
 * [AUDIT] 2024-01-15T10:30:46.456 | WRITE | REJECTED | /etc/passwd | BLACKLISTED | user@host
 * </pre>
 */
public class OperationAuditLogger {

    private static final Logger logger = LoggerFactory.getLogger(OperationAuditLogger.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");

    /**
     * 时间格式化器
     */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    /**
     * 操作结果枚举
     */
    public enum Result {
        SUCCESS("SUCCESS"),
        REJECTED("REJECTED"),
        FAILED("FAILED"),
        APPROVAL_REQUIRED("APPROVAL_REQUIRED"),
        APPROVED("APPROVED"),
        DENIED("DENIED");

        private final String code;

        Result(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * 操作类型枚举
     */
    public enum Operation {
        READ("READ"),
        WRITE("WRITE"),
        APPEND("APPEND"),
        DELETE("DELETE"),
        DELETE_RECURSIVE("DELETE_RECURSIVE"),
        CREATE_DIRECTORY("CREATE_DIRECTORY"),
        LIST("LIST"),
        SEARCH("SEARCH");

        private final String code;

        Operation(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * 操作统计计数器
     */
    private final Map<String, AtomicLong> successCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> failureCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> rejectionCounters = new ConcurrentHashMap<>();

    /**
     * 操作耗时统计
     */
    private final Map<String, AtomicLong> totalTimeMillis = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> operationCount = new ConcurrentHashMap<>();

    /**
     * 记录成功的操作
     *
     * @param operation 操作类型
     * @param path 操作路径
     * @param bytesProcessed 处理的字节数
     * @param durationMs 操作耗时（毫秒）
     */
    public void logSuccess(Operation operation, String path, long bytesProcessed, long durationMs) {
        log(operation, path, Result.SUCCESS, bytesProcessed, durationMs, null);
        incrementCounter(successCounters, operation.getCode());
        recordTiming(operation.getCode(), durationMs);
    }

    /**
     * 记录失败的操作
     *
     * @param operation 操作类型
     * @param path 操作路径
     * @param errorMessage 错误信息
     * @param durationMs 操作耗时（毫秒）
     */
    public void logFailure(Operation operation, String path, String errorMessage, long durationMs) {
        log(operation, path, Result.FAILED, 0, durationMs, errorMessage);
        incrementCounter(failureCounters, operation.getCode());
        recordTiming(operation.getCode(), durationMs);
    }

    /**
     * 记录被拒绝的操作
     *
     * @param operation 操作类型
     * @param path 操作路径
     * @param reason 拒绝原因
     */
    public void logRejection(Operation operation, String path, String reason) {
        log(operation, path, Result.REJECTED, 0, 0, reason);
        incrementCounter(rejectionCounters, operation.getCode());
    }

    /**
     * 记录需要审批的操作
     *
     * @param operation 操作类型
     * @param path 操作路径
     * @param toolCallId 工具调用 ID
     */
    public void logApprovalRequired(Operation operation, String path, String toolCallId) {
        String message = String.format("toolCallId=%s", toolCallId);
        log(operation, path, Result.APPROVAL_REQUIRED, 0, 0, message);
    }

    /**
     * 记录审批通过的操作
     *
     * @param operation 操作类型
     * @param path 操作路径
     * @param toolCallId 工具调用 ID
     */
    public void logApproved(Operation operation, String path, String toolCallId) {
        String message = String.format("toolCallId=%s", toolCallId);
        log(operation, path, Result.APPROVED, 0, 0, message);
    }

    /**
     * 记录审批被拒绝的操作
     *
     * @param operation 操作类型
     * @param path 操作路径
     * @param toolCallId 工具调用 ID
     * @param reason 拒绝原因
     */
    public void logDenied(Operation operation, String path, String toolCallId, String reason) {
        String message = String.format("toolCallId=%s, reason=%s", toolCallId, reason);
        log(operation, path, Result.DENIED, 0, 0, message);
    }

    /**
     * 记录安全事件
     *
     * @param eventType 事件类型
     * @param path 相关路径
     * @param details 事件详情
     */
    public void logSecurityEvent(String eventType, String path, String details) {
        String timestamp = TIME_FORMATTER.format(Instant.now());
        String user = System.getProperty("user.name", "unknown");
        String host = getHostname();

        String logLine = String.format("[AUDIT] %s | SECURITY | %s | %s | %s | %s@%s",
                timestamp, eventType, path, details, user, host);

        auditLogger.info(logLine);
        logger.debug("Security event: type={}, path={}", eventType, path);
    }

    /**
     * 核心日志记录方法
     */
    private void log(Operation operation, String path, Result result, long bytes, long durationMs, String details) {
        String timestamp = TIME_FORMATTER.format(Instant.now());
        String user = System.getProperty("user.name", "unknown");
        String host = getHostname();

        StringBuilder sb = new StringBuilder();
        sb.append("[AUDIT] ").append(timestamp);
        sb.append(" | ").append(operation.getCode());
        sb.append(" | ").append(result.getCode());
        sb.append(" | ").append(sanitizePath(path));

        if (bytes > 0) {
            sb.append(" | ").append(bytes).append(" bytes");
        } else {
            sb.append(" | -");
        }

        if (durationMs > 0) {
            sb.append(" | ").append(durationMs).append("ms");
        } else {
            sb.append(" | -");
        }

        if (details != null && !details.isEmpty()) {
            sb.append(" | ").append(sanitizeDetails(details));
        }

        sb.append(" | ").append(user).append("@").append(host);

        // 使用专门的审计日志记录器
        if (result == Result.SUCCESS) {
            auditLogger.info(sb.toString());
        } else if (result == Result.REJECTED || result == Result.DENIED) {
            auditLogger.warn(sb.toString());
        } else if (result == Result.FAILED) {
            auditLogger.error(sb.toString());
        } else {
            auditLogger.info(sb.toString());
        }

        logger.debug("Audit log: operation={}, result={}, path={}", operation, result, path);
    }

    /**
     * 获取操作统计
     *
     * @return 统计信息
     */
    public AuditStatistics getStatistics() {
        long totalSuccess = successCounters.values().stream().mapToLong(AtomicLong::get).sum();
        long totalFailures = failureCounters.values().stream().mapToLong(AtomicLong::get).sum();
        long totalRejections = rejectionCounters.values().stream().mapToLong(AtomicLong::get).sum();

        return new AuditStatistics(totalSuccess, totalFailures, totalRejections,
                Map.copyOf(successCounters).entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().get()
                        )));
    }

    /**
     * 获取操作平均耗时
     *
     * @param operation 操作类型
     * @return 平均耗时（毫秒），如果没有数据返回 0
     */
    public double getAverageDuration(String operation) {
        AtomicLong totalTime = totalTimeMillis.get(operation);
        AtomicLong count = operationCount.get(operation);

        if (totalTime == null || count == null || count.get() == 0) {
            return 0;
        }

        return (double) totalTime.get() / count.get();
    }

    /**
     * 重置所有计数器
     */
    public void resetStatistics() {
        successCounters.clear();
        failureCounters.clear();
        rejectionCounters.clear();
        totalTimeMillis.clear();
        operationCount.clear();
        logger.info("Audit statistics reset");
    }

    /**
     * 增加计数器
     */
    private void incrementCounter(Map<String, AtomicLong> counters, String key) {
        counters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * 记录耗时
     */
    private void recordTiming(String operation, long durationMs) {
        totalTimeMillis.computeIfAbsent(operation, k -> new AtomicLong(0)).addAndGet(durationMs);
        operationCount.computeIfAbsent(operation, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * 获取主机名
     */
    private String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 清理路径中的敏感信息
     */
    private String sanitizePath(String path) {
        if (path == null) return "-";
        // 截断过长的路径
        if (path.length() > 200) {
            return "..." + path.substring(path.length() - 197);
        }
        return path;
    }

    /**
     * 清理详情中的特殊字符
     */
    private String sanitizeDetails(String details) {
        if (details == null) return "";
        // 移除换行符和特殊字符
        return details.replace("\n", "\\n").replace("\r", "\\r").replace("|", "\\|");
    }

    /**
     * 审计统计信息
     *
     * @param totalSuccess 成功总数
     * @param totalFailures 失败总数
     * @param totalRejections 拒绝总数
     * @param successByOperation 各操作的成功次数
     */
    public record AuditStatistics(
            long totalSuccess,
            long totalFailures,
            long totalRejections,
            Map<String, Long> successByOperation
    ) {
        /**
         * 获取总操作数
         */
        public long totalOperations() {
            return totalSuccess + totalFailures + totalRejections;
        }

        /**
         * 获取成功率
         */
        public double successRate() {
            long total = totalOperations();
            if (total == 0) return 0;
            return (double) totalSuccess / total * 100;
        }

        @Override
        public String toString() {
            return String.format("AuditStatistics{total=%d, success=%d (%.1f%%), failures=%d, rejections=%d}",
                    totalOperations(), totalSuccess, successRate(), totalFailures, totalRejections);
        }
    }
}
