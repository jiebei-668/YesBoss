package tech.yesboss.tool.filesystem.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileSecurityException 单元测试
 */
@DisplayName("FileSecurityException 测试")
class FileSecurityExceptionTest {

    @Test
    @DisplayName("测试路径穿越攻击异常")
    void testPathTraversal() {
        FileSecurityException exception = FileSecurityException.pathTraversal("READ", "../../etc/passwd");

        assertEquals("READ", exception.getOperation());
        assertEquals("../../etc/passwd", exception.getPath());
        assertEquals(FileSecurityException.Reason.PATH_TRAVERSAL_DETECTED, exception.getReason());
        assertNull(exception.getToolCallId());

        assertTrue(exception.getMessage().contains("PATH_TRAVERSAL_DETECTED"));
    }

    @Test
    @DisplayName("测试黑名单路径异常")
    void testBlacklistedPath() {
        FileSecurityException exception = FileSecurityException.blacklistedPath("DELETE", "/etc/passwd");

        assertEquals("DELETE", exception.getOperation());
        assertEquals("/etc/passwd", exception.getPath());
        assertEquals(FileSecurityException.Reason.BLACKLISTED_PATH, exception.getReason());
    }

    @Test
    @DisplayName("测试权限不足异常")
    void testInsufficientPermissions() {
        FileSecurityException exception = FileSecurityException.insufficientPermissions("WRITE", "/root/file.txt");

        assertEquals("WRITE", exception.getOperation());
        assertEquals("/root/file.txt", exception.getPath());
        assertEquals(FileSecurityException.Reason.INSUFFICIENT_PERMISSIONS, exception.getReason());
        assertTrue(exception.shouldRequestApproval());
    }

    @Test
    @DisplayName("测试危险操作异常")
    void testDangerousOperation() {
        FileSecurityException exception = FileSecurityException.dangerousOperation("DELETE", "/System/Library");

        assertEquals("DELETE", exception.getOperation());
        assertEquals(FileSecurityException.Reason.DANGEROUS_OPERATION, exception.getReason());
        assertFalse(exception.shouldRequestApproval());
    }

    @Test
    @DisplayName("测试路径深度超限异常")
    void testPathDepthLimitExceeded() {
        FileSecurityException exception = FileSecurityException.pathDepthLimitExceeded("LIST", "/a/b/c/d/e/f/g/h");

        assertEquals(FileSecurityException.Reason.PATH_DEPTH_LIMIT_EXCEEDED, exception.getReason());
    }

    @Test
    @DisplayName("测试非法字符异常")
    void testIllegalCharacters() {
        FileSecurityException exception = FileSecurityException.illegalCharacters("READ", "/test/file\u0000.txt");

        assertEquals(FileSecurityException.Reason.ILLEGAL_CHARACTERS, exception.getReason());
    }

    @Test
    @DisplayName("测试不安全符号链接异常")
    void testUnsafeSymlinkTarget() {
        FileSecurityException exception = FileSecurityException.unsafeSymlinkTarget("READ", "/etc/passwd");

        assertEquals(FileSecurityException.Reason.UNSAFE_SYMLINK_TARGET, exception.getReason());
    }

    @Test
    @DisplayName("测试文件大小超限异常")
    void testFileSizeLimitExceeded() {
        FileSecurityException exception = FileSecurityException.fileSizeLimitExceeded("READ", "/test/huge.bin");

        assertEquals(FileSecurityException.Reason.FILE_SIZE_LIMIT_EXCEEDED, exception.getReason());
        assertTrue(exception.shouldRequestApproval());
    }

    @Test
    @DisplayName("测试设置 toolCallId")
    void testWithToolCallId() {
        FileSecurityException exception1 = FileSecurityException.pathTraversal("READ", "../../etc/passwd");
        FileSecurityException exception2 = exception1.withToolCallId("call_123");

        assertNull(exception1.getToolCallId());
        assertEquals("call_123", exception2.getToolCallId());

        // 其他字段应保持不变
        assertEquals(exception1.getOperation(), exception2.getOperation());
        assertEquals(exception1.getPath(), exception2.getPath());
        assertEquals(exception1.getReason(), exception2.getReason());
    }

    @Test
    @DisplayName("测试 shouldRequestApproval()")
    void testShouldRequestApproval() {
        FileSecurityException exception1 = FileSecurityException.insufficientPermissions("WRITE", "/root/file.txt");
        assertTrue(exception1.shouldRequestApproval());

        FileSecurityException exception2 = FileSecurityException.fileSizeLimitExceeded("READ", "/test/huge.bin");
        assertTrue(exception2.shouldRequestApproval());

        FileSecurityException exception3 = FileSecurityException.pathTraversal("READ", "../../etc/passwd");
        assertFalse(exception3.shouldRequestApproval());

        FileSecurityException exception4 = FileSecurityException.dangerousOperation("DELETE", "/System");
        assertFalse(exception4.shouldRequestApproval());
    }

    @Test
    @DisplayName("测试 getUserFriendlyMessage()")
    void testGetUserFriendlyMessage() {
        FileSecurityException exception1 = FileSecurityException.pathTraversal("READ", "../../etc/passwd");
        String message1 = exception1.getUserFriendlyMessage();
        assertTrue(message1.contains("路径穿越攻击"));
        assertTrue(message1.contains("../../etc/passwd"));

        FileSecurityException exception2 = FileSecurityException.blacklistedPath("DELETE", "/etc/passwd");
        String message2 = exception2.getUserFriendlyMessage();
        assertTrue(message2.contains("安全黑名单"));

        FileSecurityException exception3 = FileSecurityException.insufficientPermissions("WRITE", "/root/file.txt");
        String message3 = exception3.getUserFriendlyMessage();
        assertTrue(message3.contains("权限不足"));

        FileSecurityException exception4 = FileSecurityException.dangerousOperation("DELETE", "/System");
        String message4 = exception4.getUserFriendlyMessage();
        assertTrue(message4.contains("安全风险"));
    }

    @Test
    @DisplayName("测试 toString()")
    void testToString() {
        FileSecurityException exception1 = FileSecurityException.pathTraversal("READ", "../../etc/passwd");
        String str1 = exception1.toString();
        assertTrue(str1.contains("operation='READ'"));
        assertTrue(str1.contains("path='../../etc/passwd'"));
        assertTrue(str1.contains("PATH_TRAVERSAL_DETECTED"));
        assertFalse(str1.contains("toolCallId"));

        FileSecurityException exception2 = exception1.withToolCallId("call_123");
        String str2 = exception2.toString();
        assertTrue(str2.contains("toolCallId='call_123'"));
    }

    @Test
    @DisplayName("测试所有安全违规原因")
    void testAllReasons() {
        assertEquals(8, FileSecurityException.Reason.values().length);

        assertNotNull(FileSecurityException.Reason.valueOf("PATH_TRAVERSAL_DETECTED"));
        assertNotNull(FileSecurityException.Reason.valueOf("BLACKLISTED_PATH"));
        assertNotNull(FileSecurityException.Reason.valueOf("INSUFFICIENT_PERMISSIONS"));
        assertNotNull(FileSecurityException.Reason.valueOf("DANGEROUS_OPERATION"));
        assertNotNull(FileSecurityException.Reason.valueOf("PATH_DEPTH_LIMIT_EXCEEDED"));
        assertNotNull(FileSecurityException.Reason.valueOf("ILLEGAL_CHARACTERS"));
        assertNotNull(FileSecurityException.Reason.valueOf("UNSAFE_SYMLINK_TARGET"));
        assertNotNull(FileSecurityException.Reason.valueOf("FILE_SIZE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("测试异常消息格式")
    void testExceptionMessageFormat() {
        FileSecurityException exception = FileSecurityException.pathTraversal("READ", "../../etc/passwd");

        String message = exception.getMessage();
        assertTrue(message.contains("READ"));
        assertTrue(message.contains("../../etc/passwd"));
        assertTrue(message.contains("PATH_TRAVERSAL_DETECTED"));
    }
}
