package tech.yesboss.tool.filesystem.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 操作审计日志记录器测试
 */
class OperationAuditLoggerTest {

    private OperationAuditLogger auditLogger;

    @BeforeEach
    void setUp() {
        auditLogger = new OperationAuditLogger();
    }

    @Test
    @DisplayName("应该正确记录成功操作")
    void testLogSuccess() {
        auditLogger.logSuccess(
                OperationAuditLogger.Operation.READ,
                "/test/path/file.txt",
                1024,
                50
        );

        OperationAuditLogger.AuditStatistics stats = auditLogger.getStatistics();

        assertEquals(1, stats.totalSuccess());
        assertEquals(0, stats.totalFailures());
        assertEquals(0, stats.totalRejections());
    }

    @Test
    @DisplayName("应该正确记录失败操作")
    void testLogFailure() {
        auditLogger.logFailure(
                OperationAuditLogger.Operation.WRITE,
                "/test/path/file.txt",
                "Permission denied",
                100
        );

        OperationAuditLogger.AuditStatistics stats = auditLogger.getStatistics();

        assertEquals(0, stats.totalSuccess());
        assertEquals(1, stats.totalFailures());
        assertEquals(0, stats.totalRejections());
    }

    @Test
    @DisplayName("应该正确记录拒绝操作")
    void testLogRejection() {
        auditLogger.logRejection(
                OperationAuditLogger.Operation.DELETE,
                "/etc/passwd",
                "BLACKLISTED"
        );

        OperationAuditLogger.AuditStatistics stats = auditLogger.getStatistics();

        assertEquals(0, stats.totalSuccess());
        assertEquals(0, stats.totalFailures());
        assertEquals(1, stats.totalRejections());
    }

    @Test
    @DisplayName("应该正确记录审批流程")
    void testLogApprovalFlow() {
        String toolCallId = "call-123";

        // 需要审批 - 记录但不计入统计
        auditLogger.logApprovalRequired(
                OperationAuditLogger.Operation.DELETE,
                "/test/delete.txt",
                toolCallId
        );

        // 审批通过 - 记录但不计入统计
        auditLogger.logApproved(
                OperationAuditLogger.Operation.DELETE,
                "/test/delete.txt",
                toolCallId
        );

        // 审批流程操作被记录到审计日志，但不计入统计计数器
        // 这是设计决策：审批流程是审计追踪，不是操作结果
        // 测试确保方法不会抛出异常
        OperationAuditLogger.AuditStatistics stats = auditLogger.getStatistics();
        // 审批流程操作不计入 totalOperations，这是预期行为
        assertEquals(0, stats.totalOperations());
    }

    @Test
    @DisplayName("应该正确记录审批拒绝")
    void testLogDenied() {
        // 审批拒绝被记录到审计日志，但不计入统计计数器
        auditLogger.logDenied(
                OperationAuditLogger.Operation.DELETE,
                "/important/file.txt",
                "call-456",
                "User rejected"
        );

        // 审批拒绝是审计追踪，不是操作结果，不计入统计
        OperationAuditLogger.AuditStatistics stats = auditLogger.getStatistics();
        assertEquals(0, stats.totalOperations());
    }

    @Test
    @DisplayName("应该正确记录安全事件")
    void testLogSecurityEvent() {
        // 不应该抛出异常
        assertDoesNotThrow(() ->
            auditLogger.logSecurityEvent(
                    "PATH_TRAVERSAL_ATTEMPT",
                    "/test/../etc/passwd",
                    "Attempted to access /etc/passwd"
            )
        );
    }

    @Test
    @DisplayName("统计信息应该正确计算")
    void testStatistics() {
        // 记录多个操作
        auditLogger.logSuccess(OperationAuditLogger.Operation.READ, "/file1.txt", 100, 10);
        auditLogger.logSuccess(OperationAuditLogger.Operation.READ, "/file2.txt", 200, 20);
        auditLogger.logSuccess(OperationAuditLogger.Operation.WRITE, "/file3.txt", 300, 30);
        auditLogger.logFailure(OperationAuditLogger.Operation.READ, "/file4.txt", "Error", 5);
        auditLogger.logRejection(OperationAuditLogger.Operation.DELETE, "/file5.txt", "Protected");

        OperationAuditLogger.AuditStatistics stats = auditLogger.getStatistics();

        assertEquals(5, stats.totalOperations());
        assertEquals(3, stats.totalSuccess());
        assertEquals(1, stats.totalFailures());
        assertEquals(1, stats.totalRejections());

        // 成功率应该是 60%
        assertEquals(60.0, stats.successRate(), 0.1);
    }

    @Test
    @DisplayName("应该正确计算平均耗时")
    void testAverageDuration() {
        auditLogger.logSuccess(OperationAuditLogger.Operation.READ, "/file1.txt", 100, 10);
        auditLogger.logSuccess(OperationAuditLogger.Operation.READ, "/file2.txt", 100, 20);
        auditLogger.logSuccess(OperationAuditLogger.Operation.READ, "/file3.txt", 100, 30);

        double avgDuration = auditLogger.getAverageDuration("READ");

        assertEquals(20.0, avgDuration, 0.1);
    }

    @Test
    @DisplayName("resetStatistics 应该清除所有统计")
    void testResetStatistics() {
        auditLogger.logSuccess(OperationAuditLogger.Operation.READ, "/file.txt", 100, 10);
        auditLogger.logFailure(OperationAuditLogger.Operation.WRITE, "/file.txt", "Error", 5);

        auditLogger.resetStatistics();

        OperationAuditLogger.AuditStatistics stats = auditLogger.getStatistics();
        assertEquals(0, stats.totalOperations());
    }

    @Test
    @DisplayName("Operation 枚举应该包含所有操作类型")
    void testOperationEnum() {
        assertNotNull(OperationAuditLogger.Operation.READ);
        assertNotNull(OperationAuditLogger.Operation.WRITE);
        assertNotNull(OperationAuditLogger.Operation.APPEND);
        assertNotNull(OperationAuditLogger.Operation.DELETE);
        assertNotNull(OperationAuditLogger.Operation.DELETE_RECURSIVE);
        assertNotNull(OperationAuditLogger.Operation.CREATE_DIRECTORY);
        assertNotNull(OperationAuditLogger.Operation.LIST);
        assertNotNull(OperationAuditLogger.Operation.SEARCH);
    }

    @Test
    @DisplayName("Result 枚举应该包含所有结果类型")
    void testResultEnum() {
        assertNotNull(OperationAuditLogger.Result.SUCCESS);
        assertNotNull(OperationAuditLogger.Result.REJECTED);
        assertNotNull(OperationAuditLogger.Result.FAILED);
        assertNotNull(OperationAuditLogger.Result.APPROVAL_REQUIRED);
        assertNotNull(OperationAuditLogger.Result.APPROVED);
        assertNotNull(OperationAuditLogger.Result.DENIED);
    }

    @Test
    @DisplayName("AuditStatistics 应该正确格式化输出")
    void testAuditStatisticsToString() {
        auditLogger.logSuccess(OperationAuditLogger.Operation.READ, "/file.txt", 100, 10);

        OperationAuditLogger.AuditStatistics stats = auditLogger.getStatistics();
        String str = stats.toString();

        assertTrue(str.contains("total=1"));
        assertTrue(str.contains("success=1"));
    }

    @Test
    @DisplayName("应该处理空路径")
    void testNullPath() {
        assertDoesNotThrow(() ->
            auditLogger.logSuccess(OperationAuditLogger.Operation.READ, null, 100, 10)
        );
    }

    @Test
    @DisplayName("应该处理长路径")
    void testLongPath() {
        StringBuilder longPath = new StringBuilder("/test");
        for (int i = 0; i < 100; i++) {
            longPath.append("/directory").append(i);
        }

        assertDoesNotThrow(() ->
            auditLogger.logSuccess(OperationAuditLogger.Operation.READ, longPath.toString(), 100, 10)
        );
    }
}
