package tech.yesboss.tool.filesystem.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.yesboss.tool.filesystem.exception.FileSecurityException;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件系统安全上下文测试
 */
class FilesystemSecurityContextTest {

    @TempDir
    Path tempDir;

    private FilesystemSecurityContext context;

    @BeforeEach
    void setUp() {
        context = new FilesystemSecurityContext(tempDir.toString());
    }

    @Test
    @DisplayName("默认构造函数应该创建有效的上下文")
    void testDefaultConstructor() {
        assertNotNull(context);
        assertNotNull(context.getSecurityValidator());
        assertNotNull(context.getRateLimiter());
        assertNotNull(context.getAuditLogger());
        assertNotNull(context.getPerformanceMonitor());
    }

    @Test
    @DisplayName("构造函数应该拒绝空的项目根目录")
    void testConstructorRejectsNullProjectRoot() {
        assertThrows(IllegalArgumentException.class, () ->
            new FilesystemSecurityContext(null)
        );

        assertThrows(IllegalArgumentException.class, () ->
            new FilesystemSecurityContext("")
        );

        assertThrows(IllegalArgumentException.class, () ->
            new FilesystemSecurityContext("  ")
        );
    }

    @Test
    @DisplayName("beforeOperation 应该返回有效的计时器 ID")
    void testBeforeOperationReturnsTimerId() throws FileSecurityException {
        long timerId = context.beforeOperation("read_file", tempDir.toString(), "{}", "call-1");

        assertTrue(timerId > 0);
    }

    @Test
    @DisplayName("afterSuccess 应该正确记录成功操作")
    void testAfterSuccess() throws FileSecurityException {
        long timerId = context.beforeOperation("read_file", tempDir.toString(), "{}", "call-1");
        context.afterSuccess("read_file", tempDir.toString(), timerId, 1024);

        // 验证统计被记录
        var stats = context.getAuditStatistics();
        assertTrue(stats.totalOperations() >= 1);
    }

    @Test
    @DisplayName("afterFailure 应该正确记录失败操作")
    void testAfterFailure() throws FileSecurityException {
        long timerId = context.beforeOperation("read_file", tempDir.toString(), "{}", "call-1");
        context.afterFailure("read_file", tempDir.toString(), timerId, "File not found");

        var stats = context.getAuditStatistics();
        assertTrue(stats.totalOperations() >= 1);
    }

    @Test
    @DisplayName("validateReadAccess 应该验证项目目录内的路径")
    void testValidateReadAccess() {
        // 项目目录内的路径应该通过
        assertDoesNotThrow(() ->
            context.validateReadAccess(tempDir.resolve("test.txt").toString())
        );
    }

    @Test
    @DisplayName("validatePath 应该拒绝路径遍历攻击")
    void testValidatePathRejectsTraversal() {
        assertThrows(FileSecurityException.class, () ->
            context.validatePath(tempDir.resolve("../../../etc/passwd").toString())
        );
    }

    @Test
    @DisplayName("validateWriteAccess 应该验证写入权限")
    void testValidateWriteAccess() {
        assertDoesNotThrow(() ->
            context.validateWriteAccess(tempDir.resolve("output.txt").toString(), 100)
        );
    }

    @Test
    @DisplayName("logSecurityEvent 应该记录安全事件")
    void testLogSecurityEvent() {
        assertDoesNotThrow(() ->
            context.logSecurityEvent("TEST_EVENT", "/test/path", "Test details")
        );
    }

    @Test
    @DisplayName("getPerformanceSummary 应该返回有效的摘要")
    void testGetPerformanceSummary() throws FileSecurityException {
        long timerId = context.beforeOperation("read_file", tempDir.toString(), "{}", "call-1");
        context.afterSuccess("read_file", tempDir.toString(), timerId, 1024);

        String summary = context.getPerformanceSummary();

        assertNotNull(summary);
        assertTrue(summary.contains("Performance Summary"));
    }

    @Test
    @DisplayName("resetStatistics 应该重置所有统计")
    void testResetStatistics() throws FileSecurityException {
        // 记录一些操作
        long timerId = context.beforeOperation("read_file", tempDir.toString(), "{}", "call-1");
        context.afterSuccess("read_file", tempDir.toString(), timerId, 1024);

        // 重置
        context.resetStatistics();

        // 验证统计被重置
        var stats = context.getAuditStatistics();
        assertEquals(0, stats.totalOperations());
    }

    @Test
    @DisplayName("上下文应该支持禁用频率限制")
    void testDisableRateLimiting() {
        FilesystemSecurityContext noRateLimitContext =
            new FilesystemSecurityContext(tempDir.toString(), null, false);

        assertNull(noRateLimitContext.getRateLimiter());

        // 不应该抛出频率限制异常
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 1000; i++) {
                noRateLimitContext.beforeOperation("read_file", tempDir.toString(), "{}", "call-" + i);
            }
        });
    }

    @Test
    @DisplayName("性能监控应该正确集成")
    void testPerformanceMonitorIntegration() throws FileSecurityException {
        PerformanceMonitor monitor = context.getPerformanceMonitor();

        long timerId = context.beforeOperation("read_file", tempDir.toString(), "{}", "call-1");
        context.afterSuccess("read_file", tempDir.toString(), timerId, 1024);

        PerformanceMonitor.OperationStats stats = monitor.getStats("read_file");

        assertNotNull(stats);
        assertEquals(1, stats.getCount());
        assertEquals(1024, stats.getTotalBytes());
    }

    @Test
    @DisplayName("审计日志应该正确集成")
    void testAuditLoggerIntegration() throws FileSecurityException {
        OperationAuditLogger auditLogger = context.getAuditLogger();

        long timerId = context.beforeOperation("write_file", tempDir.toString(), "{}", "call-1");
        context.afterSuccess("write_file", tempDir.toString(), timerId, 2048);

        OperationAuditLogger.AuditStatistics stats = auditLogger.getStatistics();

        assertTrue(stats.totalSuccess() >= 1);
    }

    @Test
    @DisplayName("频率限制器应该正确集成")
    void testRateLimiterIntegration() {
        OperationRateLimiter rateLimiter = context.getRateLimiter();
        assertNotNull(rateLimiter);

        // 测试频率限制器在工作
        assertTrue(rateLimiter.tryAcquire("read_file", tempDir.toString()));
    }

    @Test
    @DisplayName("完整操作流程应该正确工作")
    void testCompleteOperationFlow() throws FileSecurityException {
        String operation = "read_file";
        String path = tempDir.resolve("test.txt").toString();
        String args = "{\"path\": \"" + path + "\"}";
        String toolCallId = "call-test-1";

        // 1. 操作前检查
        long timerId = context.beforeOperation(operation, path, args, toolCallId);
        assertTrue(timerId > 0);

        // 2. 验证读取权限
        context.validateReadAccess(path);

        // 3. 模拟操作执行
        // ... (实际读取文件)

        // 4. 记录成功
        context.afterSuccess(operation, path, timerId, 500);

        // 5. 验证统计
        var auditStats = context.getAuditStatistics();
        assertTrue(auditStats.totalSuccess() >= 1);

        var perfSummary = context.getPerformanceSummary();
        assertTrue(perfSummary.contains("read_file"));
    }

    @Test
    @DisplayName("失败操作流程应该正确记录")
    void testFailedOperationFlow() throws FileSecurityException {
        String operation = "write_file";
        String path = tempDir.resolve("protected.txt").toString();
        String args = "{\"path\": \"" + path + "\"}";
        String toolCallId = "call-test-2";

        // 1. 操作前检查
        long timerId = context.beforeOperation(operation, path, args, toolCallId);

        // 2. 模拟失败
        context.afterFailure(operation, path, timerId, "Permission denied");

        // 3. 验证统计
        var auditStats = context.getAuditStatistics();
        assertTrue(auditStats.totalFailures() >= 1);
    }
}
