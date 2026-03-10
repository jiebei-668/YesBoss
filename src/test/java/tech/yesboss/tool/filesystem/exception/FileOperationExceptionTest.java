package tech.yesboss.tool.filesystem.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileOperationException 单元测试
 */
@DisplayName("FileOperationException 测试")
class FileOperationExceptionTest {

    @Test
    @DisplayName("测试文件不存在异常")
    void testFileNotFound() {
        FileOperationException exception = FileOperationException.fileNotFound("READ", "/test/nonexistent.txt");

        assertEquals("READ", exception.getOperation());
        assertEquals("/test/nonexistent.txt", exception.getPath());
        assertEquals(FileOperationException.ErrorType.FILE_NOT_FOUND, exception.getErrorType());
        assertNull(exception.getCause());
        assertFalse(exception.isRetryable());
    }

    @Test
    @DisplayName("测试目录不存在异常")
    void testDirectoryNotFound() {
        FileOperationException exception = FileOperationException.directoryNotFound("LIST", "/test/nonexistent");

        assertEquals(FileOperationException.ErrorType.DIRECTORY_NOT_FOUND, exception.getErrorType());
    }

    @Test
    @DisplayName("测试文件已存在异常")
    void testFileAlreadyExists() {
        FileOperationException exception = FileOperationException.fileAlreadyExists("WRITE", "/test/existing.txt");

        assertEquals(FileOperationException.ErrorType.FILE_ALREADY_EXISTS, exception.getErrorType());
    }

    @Test
    @DisplayName("测试 I/O 错误异常")
    void testIoError() {
        IOException ioException = new IOException("Disk read error");
        FileOperationException exception = FileOperationException.ioError("READ", "/test/file.txt", ioException);

        assertEquals(FileOperationException.ErrorType.IO_ERROR, exception.getErrorType());
        assertEquals(ioException, exception.getCause());
        assertTrue(exception.isRetryable());
    }

    @Test
    @DisplayName("测试磁盘空间不足异常")
    void testInsufficientDiskSpace() {
        FileOperationException exception = FileOperationException.insufficientDiskSpace("WRITE", "/test/file.txt");

        assertEquals(FileOperationException.ErrorType.INSUFFICIENT_DISK_SPACE, exception.getErrorType());
        assertTrue(exception.isRetryable());
    }

    @Test
    @DisplayName("测试访问被拒绝异常")
    void testAccessDenied() {
        FileOperationException exception = FileOperationException.accessDenied("DELETE", "/test/protected.txt");

        assertEquals(FileOperationException.ErrorType.ACCESS_DENIED, exception.getErrorType());
        assertFalse(exception.isRetryable());
    }

    @Test
    @DisplayName("测试文件被锁定异常")
    void testFileLocked() {
        FileOperationException exception = FileOperationException.fileLocked("WRITE", "/test/locked.txt");

        assertEquals(FileOperationException.ErrorType.FILE_LOCKED, exception.getErrorType());
        assertTrue(exception.isRetryable());
    }

    @Test
    @DisplayName("测试目录不为空异常")
    void testDirectoryNotEmpty() {
        FileOperationException exception = FileOperationException.directoryNotEmpty("DELETE", "/test/dir");

        assertEquals(FileOperationException.ErrorType.DIRECTORY_NOT_EMPTY, exception.getErrorType());
        assertFalse(exception.isRetryable());
    }

    @Test
    @DisplayName("测试无效路径异常")
    void testInvalidPath() {
        FileOperationException exception = FileOperationException.invalidPath("READ", "/test\0/file.txt");

        assertEquals(FileOperationException.ErrorType.INVALID_PATH, exception.getErrorType());
    }

    @Test
    @DisplayName("测试编码错误异常")
    void testEncodingError() {
        Throwable cause = new RuntimeException("Unsupported encoding");
        FileOperationException exception = FileOperationException.encodingError("READ", "/test/file.txt", cause);

        assertEquals(FileOperationException.ErrorType.ENCODING_ERROR, exception.getErrorType());
        assertEquals(cause, exception.getCause());
        assertFalse(exception.isRetryable());
    }

    @Test
    @DisplayName("测试超时异常")
    void testTimeout() {
        FileOperationException exception = FileOperationException.timeout("READ", "/test/file.txt");

        assertEquals(FileOperationException.ErrorType.TIMEOUT, exception.getErrorType());
        assertTrue(exception.isRetryable());
    }

    @Test
    @DisplayName("测试未知错误异常")
    void testUnknownError() {
        Throwable cause = new RuntimeException("Unexpected error");
        FileOperationException exception = FileOperationException.unknownError("READ", "/test/file.txt", cause);

        assertEquals(FileOperationException.ErrorType.UNKNOWN_ERROR, exception.getErrorType());
        assertEquals(cause, exception.getCause());
        assertFalse(exception.isRetryable());
    }

    @Test
    @DisplayName("测试 isRetryable()")
    void testIsRetryable() {
        // 可重试的错误
        assertTrue(FileOperationException.ioError("READ", "/test/file.txt", new IOException()).isRetryable());
        assertTrue(FileOperationException.fileLocked("WRITE", "/test/file.txt").isRetryable());
        assertTrue(FileOperationException.timeout("READ", "/test/file.txt").isRetryable());
        assertTrue(FileOperationException.insufficientDiskSpace("WRITE", "/test/file.txt").isRetryable());

        // 不可重试的错误
        assertFalse(FileOperationException.fileNotFound("READ", "/test/file.txt").isRetryable());
        assertFalse(FileOperationException.accessDenied("DELETE", "/test/file.txt").isRetryable());
        assertFalse(FileOperationException.invalidPath("READ", "/test/file.txt").isRetryable());
        assertFalse(FileOperationException.unknownError("READ", "/test/file.txt", null).isRetryable());
    }

    @Test
    @DisplayName("测试 getUserFriendlyMessage()")
    void testGetUserFriendlyMessage() {
        FileOperationException exception1 = FileOperationException.fileNotFound("READ", "/test/nonexistent.txt");
        String message1 = exception1.getUserFriendlyMessage();
        assertTrue(message1.contains("不存在"));
        assertTrue(message1.contains("/test/nonexistent.txt"));

        FileOperationException exception2 = FileOperationException.accessDenied("DELETE", "/test/protected.txt");
        String message2 = exception2.getUserFriendlyMessage();
        assertTrue(message2.contains("权限不足"));

        FileOperationException exception3 = FileOperationException.fileLocked("WRITE", "/test/locked.txt");
        String message3 = exception3.getUserFriendlyMessage();
        assertTrue(message3.contains("被其他程序占用"));

        FileOperationException exception4 = FileOperationException.directoryNotEmpty("DELETE", "/test/dir");
        String message4 = exception4.getUserFriendlyMessage();
        assertTrue(message4.contains("不为空"));
    }

    @Test
    @DisplayName("测试 getSuggestedSolution()")
    void testGetSuggestedSolution() {
        FileOperationException exception1 = FileOperationException.fileNotFound("READ", "/test/nonexistent.txt");
        String solution1 = exception1.getSuggestedSolution();
        assertNotNull(solution1);
        assertTrue(solution1.contains("检查路径"));

        FileOperationException exception2 = FileOperationException.insufficientDiskSpace("WRITE", "/test/file.txt");
        String solution2 = exception2.getSuggestedSolution();
        assertNotNull(solution2);
        assertTrue(solution2.contains("清理磁盘空间"));

        FileOperationException exception3 = FileOperationException.accessDenied("DELETE", "/test/protected.txt");
        String solution3 = exception3.getSuggestedSolution();
        assertNotNull(solution3);
        assertTrue(solution3.contains("文件权限"));

        FileOperationException exception4 = FileOperationException.directoryNotEmpty("DELETE", "/test/dir");
        String solution4 = exception4.getSuggestedSolution();
        assertNotNull(solution4);
        assertTrue(solution4.contains("递归删除"));
    }

    @Test
    @DisplayName("测试 toString()")
    void testToString() {
        // 没有 cause
        FileOperationException exception1 = FileOperationException.fileNotFound("READ", "/test/file.txt");
        String str1 = exception1.toString();
        assertTrue(str1.contains("operation='READ'"));
        assertTrue(str1.contains("path='/test/file.txt'"));
        assertTrue(str1.contains("FILE_NOT_FOUND"));
        assertFalse(str1.contains("cause="));

        // 有 cause
        IOException ioException = new IOException("Disk error");
        FileOperationException exception2 = FileOperationException.ioError("READ", "/test/file.txt", ioException);
        String str2 = exception2.toString();
        assertTrue(str2.contains("cause=IOException"));
    }

    @Test
    @DisplayName("测试所有错误类型")
    void testAllErrorTypes() {
        assertEquals(12, FileOperationException.ErrorType.values().length);

        assertNotNull(FileOperationException.ErrorType.valueOf("FILE_NOT_FOUND"));
        assertNotNull(FileOperationException.ErrorType.valueOf("DIRECTORY_NOT_FOUND"));
        assertNotNull(FileOperationException.ErrorType.valueOf("FILE_ALREADY_EXISTS"));
        assertNotNull(FileOperationException.ErrorType.valueOf("IO_ERROR"));
        assertNotNull(FileOperationException.ErrorType.valueOf("INSUFFICIENT_DISK_SPACE"));
        assertNotNull(FileOperationException.ErrorType.valueOf("ACCESS_DENIED"));
        assertNotNull(FileOperationException.ErrorType.valueOf("FILE_LOCKED"));
        assertNotNull(FileOperationException.ErrorType.valueOf("DIRECTORY_NOT_EMPTY"));
        assertNotNull(FileOperationException.ErrorType.valueOf("INVALID_PATH"));
        assertNotNull(FileOperationException.ErrorType.valueOf("ENCODING_ERROR"));
        assertNotNull(FileOperationException.ErrorType.valueOf("TIMEOUT"));
        assertNotNull(FileOperationException.ErrorType.valueOf("UNKNOWN_ERROR"));
    }

    @Test
    @DisplayName("测试异常消息格式")
    void testExceptionMessageFormat() {
        FileOperationException exception = FileOperationException.fileNotFound("READ", "/test/file.txt");

        String message = exception.getMessage();
        assertTrue(message.contains("READ"));
        assertTrue(message.contains("/test/file.txt"));
        assertTrue(message.contains("FILE_NOT_FOUND"));
    }

    @Test
    @DisplayName("测试异常链")
    void testExceptionChaining() {
        IOException cause = new IOException("Root cause");
        FileOperationException exception = FileOperationException.ioError("READ", "/test/file.txt", cause);

        assertSame(cause, exception.getCause());
        assertEquals(cause, exception.getCause());
    }
}
