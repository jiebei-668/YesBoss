package tech.yesboss.tool.filesystem.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.yesboss.tool.filesystem.exception.FileSecurityException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileSecurityValidator 单元测试
 *
 * <p>验证文件操作安全策略的正确性，包括：</p>
 * <ul>
 *   <li>路径白名单验证（项目目录、/tmp）</li>
 *   <li>路径黑名单过滤（敏感文件）</li>
 *   <li>路径遍历攻击防护</li>
 *   <li>文件类型检查</li>
 *   <li>文件大小验证</li>
 *   <li>路径规范化</li>
 * </ul>
 */
@DisplayName("FileSecurityValidator Tests")
class FileSecurityValidatorTest {

    private FileSecurityValidator validator;
    private String projectRoot;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        projectRoot = tempDir.toAbsolutePath().toString();
        validator = new FileSecurityValidator(projectRoot);
    }

    // ==================== 基础路径验证测试 ====================

    @Test
    @DisplayName("应该接受合法的项目内路径")
    void testValidatePath_AcceptsValidProjectPath() throws FileSecurityException {
        // Arrange
        String validPath = tempDir.resolve("src/Main.java").toString();

        // Act & Assert
        assertDoesNotThrow(() -> validator.validatePath(validPath));
    }

    @Test
    @DisplayName("应该拒绝 null 路径")
    void testValidatePath_RejectsNullPath() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> validator.validatePath(null));
    }

    @Test
    @DisplayName("应该拒绝空路径")
    void testValidatePath_RejectsEmptyPath() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> validator.validatePath(""));
        assertThrows(IllegalArgumentException.class, () -> validator.validatePath("   "));
    }

    @Test
    @DisplayName("应该拒绝包含非法字符的路径")
    void testValidatePath_RejectsIllegalCharacters() {
        // Arrange
        String pathWithNullChar = tempDir.resolve("test\0file.txt").toString();

        // Act & Assert
        assertThrows(FileSecurityException.class, () -> validator.validatePath(pathWithNullChar));
    }

    // ==================== 路径遍历攻击测试 ====================

    @Test
    @DisplayName("应该拒绝包含 .. 的路径遍历攻击")
    void testValidatePath_RejectsPathTraversalWithDoubleDot() throws FileSecurityException {
        // Arrange
        String traversalPath = tempDir.resolve("src").resolve("..").resolve("etc").resolve("passwd").toString();

        // Act & Assert
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> validator.validatePath(traversalPath));
        assertEquals(FileSecurityException.Reason.PATH_TRAVERSAL_DETECTED, exception.getReason());
    }

    @Test
    @DisplayName("应该拒绝使用 ../ 逃逸出项目目录的攻击")
    void testValidatePath_RejectsEscapeFromProjectDirectory() {
        // Arrange
        String escapePath = tempDir.resolve("..").resolve("..").resolve("etc").resolve("passwd").toString();

        // Act & Assert
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> validator.validatePath(escapePath));
        assertEquals(FileSecurityException.Reason.PATH_TRAVERSAL_DETECTED, exception.getReason());
    }

    @Test
    @DisplayName("应该拒绝多层 .. 嵌套的路径遍历攻击")
    void testValidatePath_RejectsMultipleNestedDoubleDots() {
        // Arrange
        String complexTraversal = tempDir.resolve("a").resolve("b").resolve("..").resolve("..").resolve("c").toString();

        // Act & Assert
        assertThrows(FileSecurityException.class, () -> validator.validatePath(complexTraversal));
    }

    // ==================== 路径黑名单测试 ====================

    @Test
    @DisplayName("应该拒绝访问 /etc/passwd 敏感文件")
    void testValidatePath_RejectsEtcPasswd() {
        // Arrange
        String etcPasswdPath = "/etc/passwd";

        // Act & Assert
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> validator.validatePath(etcPasswdPath));
        assertEquals(FileSecurityException.Reason.BLACKLISTED_PATH, exception.getReason());
    }

    @Test
    @DisplayName("应该拒绝访问 /etc/shadow 敏感文件")
    void testValidatePath_RejectsEtcShadow() {
        // Arrange
        String etcShadowPath = "/etc/shadow";

        // Act & Assert
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> validator.validatePath(etcShadowPath));
        assertEquals(FileSecurityException.Reason.BLACKLISTED_PATH, exception.getReason());
    }

    @Test
    @DisplayName("应该拒绝访问 ~/.ssh 目录")
    void testValidatePath_RejectsSshDirectory() {
        // Arrange
        String sshPath = System.getProperty("user.home") + "/.ssh";

        // Act & Assert
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> validator.validatePath(sshPath));
        assertEquals(FileSecurityException.Reason.BLACKLISTED_PATH, exception.getReason());
    }

    @Test
    @DisplayName("应该拒绝访问 ~/.ssh/config 文件")
    void testValidatePath_RejectsSshConfigFile() {
        // Arrange
        String sshConfigPath = System.getProperty("user.home") + "/.ssh/config";

        // Act & Assert
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> validator.validatePath(sshConfigPath));
        assertEquals(FileSecurityException.Reason.BLACKLISTED_PATH, exception.getReason());
    }

    @Test
    @DisplayName("应该拒绝访问黑名单目录的子路径")
    void testValidatePath_RejectsSubdirectoryOfBlacklistedPath() {
        // Arrange
        String subPath = System.getProperty("user.home") + "/.ssh/known_hosts";

        // Act & Assert
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> validator.validatePath(subPath));
        assertEquals(FileSecurityException.Reason.BLACKLISTED_PATH, exception.getReason());
    }

    // ==================== 路径白名单测试 ====================

    @Test
    @DisplayName("应该接受项目根目录下的路径")
    void testValidatePath_AcceptsProjectRootPath() throws FileSecurityException {
        // Arrange
        String projectPath = tempDir.resolve("src").resolve("test.java").toString();

        // Act & Assert
        assertDoesNotThrow(() -> validator.validatePath(projectPath));
    }

    @Test
    @DisplayName("应该接受 /tmp 目录下的路径")
    void testValidatePath_AcceptsTmpDirectory() throws FileSecurityException {
        // Arrange
        String tmpPath = System.getProperty("java.io.tmpdir") + "/test_file.txt";

        // Act & Assert
        assertDoesNotThrow(() -> validator.validatePath(tmpPath));
    }

    @Test
    @DisplayName("应该拒绝项目目录外的路径")
    void testValidatePath_RejectsPathOutsideProject() {
        // Arrange
        String outsidePath = "/var/log/test.log";

        // Act & Assert
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> validator.validatePath(outsidePath));
        assertEquals(FileSecurityException.Reason.BLACKLISTED_PATH, exception.getReason());
    }

    // ==================== 读取权限验证测试 ====================

    @Test
    @DisplayName("应该允许读取存在的文本文件")
    void testValidateReadAccess_AcceptsExistingTextFile() throws IOException, FileSecurityException {
        // Arrange
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello, World!");

        // Act & Assert
        assertDoesNotThrow(() -> validator.validateReadAccess(testFile.toString()));
    }

    @Test
    @DisplayName("应该允许读取存在的 Java 文件")
    void testValidateReadAccess_AcceptsJavaFile() throws IOException, FileSecurityException {
        // Arrange
        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, "public class Test {}");

        // Act & Assert
        assertDoesNotThrow(() -> validator.validateReadAccess(testFile.toString()));
    }

    @Test
    @DisplayName("应该拒绝读取不允许的文件类型（.exe）")
    void testValidateReadAccess_RejectsExecutableFile() throws IOException {
        // Arrange
        Path testFile = tempDir.resolve("test.exe");
        Files.writeString(testFile, "fake executable");

        // Act & Assert
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> validator.validateReadAccess(testFile.toString()));
        assertEquals(FileSecurityException.Reason.DANGEROUS_OPERATION, exception.getReason());
    }

    @Test
    @DisplayName("应该拒绝读取超过大小限制的文件")
    void testValidateReadAccess_RejectsOversizedFile() throws IOException {
        // Arrange
        FileSecurityValidator strictValidator = new FileSecurityValidator(projectRoot, 1024); // 1KB limit
        Path testFile = tempDir.resolve("large.txt");
        Files.writeString(testFile, "x".repeat(2000)); // 2KB

        // Act & Assert
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> strictValidator.validateReadAccess(testFile.toString()));
        assertEquals(FileSecurityException.Reason.FILE_SIZE_LIMIT_EXCEEDED, exception.getReason());
    }

    @Test
    @DisplayName("应该允许读取目录")
    void testValidateReadAccess_AcceptsDirectory() throws FileSecurityException {
        // Arrange
        Path testDir = tempDir.resolve("testdir");
        testDir.toFile().mkdir();

        // Act & Assert
        assertDoesNotThrow(() -> validator.validateReadAccess(testDir.toString()));
    }

    @Test
    @DisplayName("应该处理不存在的文件（不抛出安全异常）")
    void testValidateReadAccess_HandlesNonExistentFile() throws FileSecurityException {
        // Arrange
        String nonExistentPath = tempDir.resolve("nonexistent.txt").toString();

        // Act & Assert
        // 不存在的文件应该通过安全验证（虽然业务上会失败，但不是安全问题）
        assertDoesNotThrow(() -> validator.validateReadAccess(nonExistentPath));
    }

    // ==================== 写入权限验证测试 ====================

    @Test
    @DisplayName("应该允许写入允许的文件类型")
    void testValidateWriteAccess_AcceptsAllowedFileType() throws FileSecurityException {
        // Arrange
        String filePath = tempDir.resolve("output.txt").toString();

        // Act & Assert
        assertDoesNotThrow(() -> validator.validateWriteAccess(filePath, 100));
    }

    @Test
    @DisplayName("应该拒绝写入不允许的文件类型（.exe）")
    void testValidateWriteAccess_RejectsExecutableFileType() {
        // Arrange
        String filePath = tempDir.resolve("program.exe").toString();

        // Act & Assert
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> validator.validateWriteAccess(filePath, 100));
        assertEquals(FileSecurityException.Reason.DANGEROUS_OPERATION, exception.getReason());
    }

    @Test
    @DisplayName("应该拒绝写入超过大小限制的文件")
    void testValidateWriteAccess_RejectsOversizedFile() {
        // Arrange
        String filePath = tempDir.resolve("large.txt").toString();
        long oversizedSize = 20L * 1024 * 1024; // 20MB

        // Act & Assert
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> validator.validateWriteAccess(filePath, oversizedSize));
        assertEquals(FileSecurityException.Reason.FILE_SIZE_LIMIT_EXCEEDED, exception.getReason());
    }

    @Test
    @DisplayName("应该允许写入无扩展名的文件")
    void testValidateWriteAccess_AcceptsFileWithoutExtension() throws FileSecurityException {
        // Arrange
        String filePath = tempDir.resolve("README").toString();

        // Act & Assert
        assertDoesNotThrow(() -> validator.validateWriteAccess(filePath, 100));
    }

    @Test
    @DisplayName("应该允许写入隐藏文件（.gitignore）")
    void testValidateWriteAccess_AcceptsHiddenFiles() throws FileSecurityException {
        // Arrange
        String filePath = tempDir.resolve(".gitignore").toString();

        // Act & Assert
        assertDoesNotThrow(() -> validator.validateWriteAccess(filePath, 100));
    }

    // ==================== 文件类型检查测试 ====================

    @Test
    @DisplayName("应该允许所有代码文件类型")
    void testFileType_AllowedCodeFiles() throws FileSecurityException {
        // Arrange
        String[] codeFiles = {
                "test.java", "test.py", "test.js", "test.ts",
                "test.go", "test.rs", "test.c", "test.cpp",
                "test.kt", "test.scala", "test.rb", "test.php"
        };

        // Act & Assert
        for (String fileName : codeFiles) {
            String filePath = tempDir.resolve(fileName).toString();
            assertDoesNotThrow(() -> validator.validateWriteAccess(filePath),
                    "Should allow: " + fileName);
        }
    }

    @Test
    @DisplayName("应该允许所有配置文件类型")
    void testFileType_AllowedConfigFiles() throws FileSecurityException {
        // Arrange
        String[] configFiles = {
                "config.json", "config.yaml", "config.yml", "config.xml",
                "config.properties", ".env", "app.conf"
        };

        // Act & Assert
        for (String fileName : configFiles) {
            String filePath = tempDir.resolve(fileName).toString();
            assertDoesNotThrow(() -> validator.validateWriteAccess(filePath),
                    "Should allow: " + fileName);
        }
    }

    @Test
    @DisplayName("应该允许所有文档文件类型")
    void testFileType_AllowedDocumentFiles() throws FileSecurityException {
        // Arrange
        String[] docFiles = {
                "README.md", "docs.txt", "notes.html", "style.css"
        };

        // Act & Assert
        for (String fileName : docFiles) {
            String filePath = tempDir.resolve(fileName).toString();
            assertDoesNotThrow(() -> validator.validateWriteAccess(filePath),
                    "Should allow: " + fileName);
        }
    }

    @Test
    @DisplayName("应该拒绝二进制文件类型")
    void testFileType_RejectsBinaryFiles() {
        // Arrange
        String[] binaryFiles = {
                "program.exe", "image.png", "image.jpg", "video.mp4",
                "data.zip", "archive.tar", "document.pdf"
        };

        // Act & Assert
        for (String fileName : binaryFiles) {
            String filePath = tempDir.resolve(fileName).toString();
            FileSecurityException exception = assertThrows(FileSecurityException.class,
                    () -> validator.validateWriteAccess(filePath),
                    "Should reject: " + fileName);
            assertEquals(FileSecurityException.Reason.DANGEROUS_OPERATION, exception.getReason(),
                    "Should reject binary file: " + fileName);
        }
    }

    // ==================== 文件大小验证测试 ====================

    @Test
    @DisplayName("应该允许文件大小等于限制的文件")
    void testFileSize_AcceptsFileAtLimit() throws FileSecurityException {
        // Arrange
        FileSecurityValidator customValidator = new FileSecurityValidator(projectRoot, 1000);
        String filePath = tempDir.resolve("test.txt").toString();

        // Act & Assert
        assertDoesNotThrow(() -> customValidator.validateWriteAccess(filePath, 1000));
    }

    @Test
    @DisplayName("应该拒绝文件大小略大于限制的文件")
    void testFileSize_RejectsFileJustOverLimit() {
        // Arrange
        FileSecurityValidator customValidator = new FileSecurityValidator(projectRoot, 1000);
        String filePath = tempDir.resolve("test.txt").toString();

        // Act & Assert
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> customValidator.validateWriteAccess(filePath, 1001));
        assertEquals(FileSecurityException.Reason.FILE_SIZE_LIMIT_EXCEEDED, exception.getReason());
    }

    @Test
    @DisplayName("应该使用默认 10MB 限制")
    void testDefaultFileSizeLimit() {
        // Arrange
        String filePath = tempDir.resolve("test.txt").toString();

        // Act & Assert
        assertEquals(10L * 1024 * 1024, validator.getMaxFileSize());
        assertDoesNotThrow(() -> validator.validateWriteAccess(filePath, 10L * 1024 * 1024));
    }

    // ==================== 构造函数测试 ====================

    @Test
    @DisplayName("构造函数应该拒绝 null 项目根目录")
    void testConstructor_RejectsNullProjectRoot() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> new FileSecurityValidator(null));
    }

    @Test
    @DisplayName("构造函数应该拒绝空项目根目录")
    void testConstructor_RejectsEmptyProjectRoot() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> new FileSecurityValidator(""));
        assertThrows(IllegalArgumentException.class, () -> new FileSecurityValidator("   "));
    }

    @Test
    @DisplayName("构造函数应该接受自定义文件大小限制")
    void testConstructor_AcceptsCustomFileSizeLimit() {
        // Act
        FileSecurityValidator customValidator = new FileSecurityValidator(projectRoot, 5000);

        // Assert
        assertEquals(5000, customValidator.getMaxFileSize());
    }

    @Test
    @DisplayName("构造函数应该正确设置项目根目录")
    void testConstructor_SetsProjectRoot() {
        // Act & Assert
        assertEquals(projectRoot, validator.getProjectRoot());
    }

    // ==================== Getter 方法测试 ====================

    @Test
    @DisplayName("getPathBlacklist 应该返回不可修改的黑名单")
    void testGetPathBlacklist_ReturnsUnmodifiableSet() {
        // Act
        var blacklist = validator.getPathBlacklist();

        // Assert
        assertThrows(UnsupportedOperationException.class, () -> blacklist.add("/new/path"));
    }

    @Test
    @DisplayName("getAllowedExtensions 应该返回不可修改的扩展名集合")
    void testGetAllowedExtensions_ReturnsUnmodifiableSet() {
        // Act
        var extensions = validator.getAllowedExtensions();

        // Assert
        assertThrows(UnsupportedOperationException.class, () -> extensions.add("newext"));
    }

    @Test
    @DisplayName("路径黑名单应该包含常见的敏感路径")
    void testPathBlacklist_ContainsCommonSensitivePaths() {
        // Act
        var blacklist = validator.getPathBlacklist();

        // Assert
        assertTrue(blacklist.contains("/etc/passwd"));
        assertTrue(blacklist.contains("/etc/shadow"));
    }

    @Test
    @DisplayName("允许的扩展名应该包含常见的文本格式")
    void testAllowedExtensions_ContainsCommonTextFormats() {
        // Act
        var extensions = validator.getAllowedExtensions();

        // Assert
        assertTrue(extensions.contains("java"));
        assertTrue(extensions.contains("py"));
        assertTrue(extensions.contains("txt"));
        assertTrue(extensions.contains("md"));
        assertTrue(extensions.contains("json"));
    }

    // ==================== 边界情况测试 ====================

    @Test
    @DisplayName("应该处理非常深的路径（但在限制内）")
    void testDeepPath_WithinLimit() throws FileSecurityException {
        // Arrange
        Path deepPath = tempDir.resolve("a").resolve("b").resolve("c").resolve("d").resolve("e");

        // Act & Assert
        assertDoesNotThrow(() -> validator.validatePath(deepPath.toString()));
    }

    @Test
    @DisplayName("应该处理包含多个连续分隔符的路径")
    void testPathWithMultipleSeparators() throws FileSecurityException {
        // Arrange - 注意：Java 的 Path.normalize() 会处理这种情况
        Path pathWithDoubleSeparators = tempDir.resolve("src").resolve("").resolve("test.txt");

        // Act & Assert
        assertDoesNotThrow(() -> validator.validatePath(pathWithDoubleSeparators.toString()));
    }

    @Test
    @DisplayName("toString 应该返回有用的信息")
    void testToString_ReturnsUsefulInformation() {
        // Act
        String str = validator.toString();

        // Assert
        assertTrue(str.contains("FileSecurityValidator"));
        assertTrue(str.contains(projectRoot));
        assertTrue(str.contains("maxFileSize"));
    }
}
