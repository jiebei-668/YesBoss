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
 * FileSecurityValidator 写入保护机制单元测试
 *
 * <p>验证写入操作的额外安全保护，包括：</p>
 * <ul>
 *   <li>重要文件保护</li>
 *   <li>文件覆盖保护</li>
 *   <li>磁盘空间检查</li>
 *   <li>配置化保护机制</li>
 * </ul>
 */
@DisplayName("FileSecurityValidator Write Protection Tests")
class FileSecurityValidatorWriteTest {

    private FileSecurityValidator validator;
    private String projectRoot;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        projectRoot = tempDir.toAbsolutePath().toString();
        validator = new FileSecurityValidator(projectRoot);
    }

    // ==================== 受保护文件测试 ====================

    @Test
    @DisplayName("应该拒绝写入 pom.xml 受保护文件")
    void testValidateWriteAccess_RejectsProtectedPomFile() throws IOException {
        // Arrange
        Path pomFile = tempDir.resolve("pom.xml");
        Files.writeString(pomFile, "<project></project>");

        // Act & Assert
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> validator.validateWriteAccess(pomFile.toString(), 100));
        assertEquals(FileSecurityException.Reason.PROTECTED_FILE, exception.getReason());
        assertTrue(exception.getUserFriendlyMessage().contains("受保护"));
    }

    @Test
    @DisplayName("应该拒绝写入 .git 受保护目录")
    void testValidateWriteAccess_RejectsProtectedGitDirectory() throws IOException {
        // Arrange
        Path gitDir = tempDir.resolve(".git");
        Files.createDirectories(gitDir);
        Path gitFile = gitDir.resolve("config");

        // Act & Assert
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> validator.validateWriteAccess(gitFile.toString(), 100));
        assertEquals(FileSecurityException.Reason.PROTECTED_FILE, exception.getReason());
    }

    @Test
    @DisplayName("应该拒绝写入 application.yml 受保护文件")
    void testValidateWriteAccess_RejectsProtectedApplicationYml() throws IOException {
        // Arrange
        Path configFile = tempDir.resolve("application.yml");
        Files.writeString(configFile, "app:\n  name: test");

        // Act & Assert
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> validator.validateWriteAccess(configFile.toString(), 100));
        assertEquals(FileSecurityException.Reason.PROTECTED_FILE, exception.getReason());
    }

    @Test
    @DisplayName("应该拒绝写入 data 受保护目录")
    void testValidateWriteAccess_RejectsProtectedDataDirectory() throws IOException {
        // Arrange
        Path dataDir = tempDir.resolve("data");
        Files.createDirectories(dataDir);
        Path dataFile = dataDir.resolve("test.db");

        // Act & Assert
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> validator.validateWriteAccess(dataFile.toString(), 100));
        assertEquals(FileSecurityException.Reason.PROTECTED_FILE, exception.getReason());
    }

    @Test
    @DisplayName("应该拒绝写入 logs 受保护目录")
    void testValidateWriteAccess_RejectsProtectedLogsDirectory() throws IOException {
        // Arrange
        Path logsDir = tempDir.resolve("logs");
        Files.createDirectories(logsDir);
        Path logFile = logsDir.resolve("app.log");

        // Act & Assert
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> validator.validateWriteAccess(logFile.toString(), 100));
        assertEquals(FileSecurityException.Reason.PROTECTED_FILE, exception.getReason());
    }

    @Test
    @DisplayName("应该允许写入非受保护文件")
    void testValidateWriteAccess_AcceptsNonProtectedFile() throws IOException, FileSecurityException {
        // Arrange
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        Path regularFile = srcDir.resolve("Main.java");

        // Act & Assert
        assertDoesNotThrow(() -> validator.validateWriteAccess(regularFile.toString(), 100));
    }

    // ==================== 覆盖保护测试 ====================

    @Test
    @DisplayName("应该拒绝覆盖已存在的文件（覆盖保护启用）")
    void testValidateWriteAccess_RejectsOverwriteWhenProtectionEnabled() throws IOException {
        // Arrange
        Path existingFile = tempDir.resolve("test.txt");
        Files.writeString(existingFile, "existing content");

        // Act & Assert
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> validator.validateWriteAccess(existingFile.toString(), 100));
        assertEquals(FileSecurityException.Reason.OVERWRITE_REJECTED, exception.getReason());
        assertTrue(exception.getUserFriendlyMessage().contains("覆盖"));
    }

    @Test
    @DisplayName("应该允许覆盖已存在的文件（覆盖保护禁用）")
    void testValidateWriteAccess_AllowsOverwriteWhenProtectionDisabled() throws IOException, FileSecurityException {
        // Arrange
        FileSecurityValidator noProtectionValidator = new FileSecurityValidator(
                projectRoot,
                FileSecurityValidator.getMaxFileSizeDefault(),
                FileSecurityValidator.getMinDiskSpaceDefault(),
                false // 覆盖保护禁用
        );
        Path existingFile = tempDir.resolve("test.txt");
        Files.writeString(existingFile, "existing content");

        // Act & Assert
        assertDoesNotThrow(() -> noProtectionValidator.validateWriteAccess(existingFile.toString(), 100));
    }

    @Test
    @DisplayName("应该允许写入不存在的文件")
    void testValidateWriteAccess_AllowsWritingToNonExistentFile() throws FileSecurityException {
        // Arrange
        Path nonExistentFile = tempDir.resolve("newfile.txt");

        // Act & Assert
        assertDoesNotThrow(() -> validator.validateWriteAccess(nonExistentFile.toString(), 100));
    }

    // ==================== 磁盘空间检查测试 ====================

    @Test
    @DisplayName("应该检查磁盘空间是否充足")
    void testValidateWriteAccess_ChecksDiskSpace() throws FileSecurityException {
        // Arrange
        Path file = tempDir.resolve("test.txt");
        // 使用一个小于默认文件大小限制（10MB）但较大的值
        long largeFileSize = 5L * 1024 * 1024; // 5MB

        // Act & Assert - 这应该通过验证（只要磁盘空间充足）
        assertDoesNotThrow(() -> validator.validateWriteAccess(file.toString(), largeFileSize),
                "Should complete validation even with large file size");
    }

    @Test
    @DisplayName("应该使用自定义的最小磁盘空间要求")
    void testValidateWriteAccess_UsesCustomMinDiskSpace() throws FileSecurityException {
        // Arrange
        FileSecurityValidator customValidator = new FileSecurityValidator(
                projectRoot,
                FileSecurityValidator.getMaxFileSizeDefault(),
                1024, // 1KB 最小磁盘空间
                true
        );
        Path file = tempDir.resolve("test.txt");

        // Act & Assert
        assertDoesNotThrow(() -> customValidator.validateWriteAccess(file.toString(), 100));
        assertEquals(1024, customValidator.getMinDiskSpace());
    }

    // ==================== 配置化保护测试 ====================

    @Test
    @DisplayName("应该返回受保护的文件列表")
    void testGetProtectedFiles_ReturnsUnmodifiableSet() {
        // Act
        var protectedFiles = validator.getProtectedFiles();

        // Assert
        assertNotNull(protectedFiles);
        assertThrows(UnsupportedOperationException.class, () -> protectedFiles.add("/new/path"));
    }

    @Test
    @DisplayName("受保护文件列表应该包含关键文件")
    void testGetProtectedFiles_ContainsKeyFiles() {
        // Act
        var protectedFiles = validator.getProtectedFiles();

        // Assert
        assertTrue(protectedFiles.stream().anyMatch(p -> p.contains("pom.xml")),
                "Should contain pom.xml");
        assertTrue(protectedFiles.stream().anyMatch(p -> p.contains(".git")),
                "Should contain .git directory");
        assertTrue(protectedFiles.stream().anyMatch(p -> p.contains("application.yml")),
                "Should contain application.yml");
    }

    @Test
    @DisplayName("应该返回正确的覆盖保护状态")
    void testIsOverwriteProtectionEnabled() {
        // Act & Assert
        assertTrue(validator.isOverwriteProtectionEnabled(),
                "Default validator should have overwrite protection enabled");

        FileSecurityValidator noProtectionValidator = new FileSecurityValidator(
                projectRoot,
                FileSecurityValidator.getMaxFileSizeDefault(),
                FileSecurityValidator.getMinDiskSpaceDefault(),
                false
        );
        assertFalse(noProtectionValidator.isOverwriteProtectionEnabled(),
                "Custom validator should have overwrite protection disabled");
    }

    // ==================== 边界情况测试 ====================

    @Test
    @DisplayName("应该处理受保护目录的深层嵌套文件")
    void testProtectedFile_DeepNestedPath() throws IOException {
        // Arrange
        Path targetDir = tempDir.resolve("target").resolve("classes").resolve("com");
        Files.createDirectories(targetDir);
        Path deepNestedFile = targetDir.resolve("test.class");

        // Act & Assert
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> validator.validateWriteAccess(deepNestedFile.toString(), 100));
        assertEquals(FileSecurityException.Reason.PROTECTED_FILE, exception.getReason());
    }

    @Test
    @DisplayName("应该区分受保护和不受保护的目录")
    void testProtectedFile_DistinguishesProtectedFromUnprotected() throws IOException, FileSecurityException {
        // Arrange
        Path dataDir = tempDir.resolve("data");
        Files.createDirectories(dataDir);
        Path protectedDirFile = dataDir.resolve("test.txt");

        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        Path unprotectedDirFile = srcDir.resolve("test.txt");

        // Act & Assert
        assertThrows(FileSecurityException.class,
                () -> validator.validateWriteAccess(protectedDirFile.toString(), 100),
                "Should reject writing to protected directory");

        assertDoesNotThrow(() -> validator.validateWriteAccess(unprotectedDirFile.toString(), 100),
                "Should allow writing to unprotected directory");
    }

    @Test
    @DisplayName("应该允许写入零字节文件")
    void testValidateWriteAccess_AllowsZeroByteFile() throws FileSecurityException {
        // Arrange
        Path emptyFile = tempDir.resolve("empty.txt");

        // Act & Assert
        assertDoesNotThrow(() -> validator.validateWriteAccess(emptyFile.toString(), 0));
    }

    @Test
    @DisplayName("应该使用默认配置创建验证器")
    void testConstructor_UsesDefaultConfiguration() {
        // Act
        FileSecurityValidator defaultValidator = new FileSecurityValidator(projectRoot);

        // Assert
        assertTrue(defaultValidator.isOverwriteProtectionEnabled());
        assertTrue(defaultValidator.getMinDiskSpace() > 0);
        assertNotNull(defaultValidator.getProtectedFiles());
        assertFalse(defaultValidator.getProtectedFiles().isEmpty());
    }

    // ==================== 综合场景测试 ====================

    @Test
    @DisplayName("应该按优先级检查写入保护（受保护 > 覆盖 > 磁盘空间）")
    void testValidateWriteAccess_PriorityOrder() throws IOException {
        // Arrange - 创建一个已存在的受保护文件
        Path protectedFile = tempDir.resolve("pom.xml");
        Files.writeString(protectedFile, "<project></project>");

        // Act & Assert - 应该首先检测到受保护文件，而不是覆盖问题
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> validator.validateWriteAccess(protectedFile.toString(), 100));
        assertEquals(FileSecurityException.Reason.PROTECTED_FILE, exception.getReason(),
                "Should report protected file, not overwrite rejection");
    }

    @Test
    @DisplayName("应该在磁盘空间不足时提供清晰的错误信息")
    void testValidateWriteAccess_InsufficientDiskSpaceErrorMessage() {
        // Arrange - 创建一个自定义验证器，设置非常高的最小磁盘空间要求
        FileSecurityValidator strictValidator = new FileSecurityValidator(
                projectRoot,
                1000, // 1KB 文件大小限制
                Long.MAX_VALUE, // 极高的最小磁盘空间要求
                true
        );
        Path file = tempDir.resolve("huge.txt");

        // Act & Assert
        FileSecurityException exception = assertThrows(FileSecurityException.class,
                () -> strictValidator.validateWriteAccess(file.toString(), 100));
        assertEquals(FileSecurityException.Reason.INSUFFICIENT_DISK_SPACE, exception.getReason());
    }

    // ==================== 辅助方法测试 ====================

    @Test
    @DisplayName("getter 方法应该返回正确的配置值")
    void testGetters_ReturnCorrectConfigurationValues() {
        // Arrange
        long customMaxSize = 5000;
        long customMinDiskSpace = 2000;
        boolean customOverwriteProtection = false;

        FileSecurityValidator customValidator = new FileSecurityValidator(
                projectRoot,
                customMaxSize,
                customMinDiskSpace,
                customOverwriteProtection
        );

        // Act & Assert
        assertEquals(projectRoot, customValidator.getProjectRoot());
        assertEquals(customMaxSize, customValidator.getMaxFileSize());
        assertEquals(customMinDiskSpace, customValidator.getMinDiskSpace());
        assertEquals(customOverwriteProtection, customValidator.isOverwriteProtectionEnabled());
    }
}
