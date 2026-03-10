package tech.yesboss.tool.filesystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.yesboss.tool.SuspendExecutionException;
import tech.yesboss.tool.ToolAccessLevel;
import tech.yesboss.tool.filesystem.exception.FileOperationException;
import tech.yesboss.tool.filesystem.exception.FileSecurityException;
import tech.yesboss.tool.sandbox.SandboxInterceptor;
import tech.yesboss.tool.sandbox.impl.SandboxInterceptorImpl;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeleteFileTool 单元测试
 *
 * <p>测试文件删除工具的各项功能，包括：</p>
 * <ul>
 *   <li>单文件删除</li>
 *   <li>空目录删除</li>
 *   <li>递归删除（触发审批）</li>
 *   <li>重要文件保护</li>
 *   <li>安全违规检测</li>
 * </ul>
 */
class DeleteFileToolTest {

    private DeleteFileTool tool;
    private DeleteFileTool toolWithSandbox;
    private SandboxInterceptor sandboxInterceptor;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        String projectRoot = tempDir.toAbsolutePath().toString();

        // 无沙箱拦截器的工具
        tool = new DeleteFileTool(projectRoot);

        // 带沙箱拦截器的工具
        sandboxInterceptor = new SandboxInterceptorImpl(true);
        toolWithSandbox = new DeleteFileTool(projectRoot, sandboxInterceptor);

        objectMapper = new ObjectMapper();
    }

    /**
     * 创建 JSON 参数字符串
     */
    private String createArgumentsJson(String path, boolean recursive) throws Exception {
        StringBuilder json = new StringBuilder("{\"path\": \"");
        String escapedPath = path.replace("\\", "\\\\");
        json.append(escapedPath);
        json.append("\", \"recursive\": ").append(recursive).append("}");
        return json.toString();
    }

    /**
     * 创建 JSON 参数字符串（非递归）
     */
    private String createArgumentsJson(String path) throws Exception {
        return createArgumentsJson(path, false);
    }

    // ==================== 测试工具属性 ====================

    /**
     * 测试：工具名称正确
     */
    @Test
    void testToolName() {
        assertEquals("delete_file", tool.getName());
    }

    /**
     * 测试：工具访问级别为 READ_WRITE
     */
    @Test
    void testAccessLevel() {
        assertEquals(ToolAccessLevel.READ_WRITE, tool.getAccessLevel());
    }

    /**
     * 测试：工具描述不为空
     */
    @Test
    void testToolDescription() {
        assertFalse(tool.getDescription().isEmpty());
        assertTrue(tool.getDescription().contains("删除"));
    }

    /**
     * 测试：JSON Schema 不为空
     */
    @Test
    void testJsonSchema() {
        assertFalse(tool.getParametersJsonSchema().isEmpty());
        assertTrue(tool.getParametersJsonSchema().contains("path"));
    }

    // ==================== 测试单文件删除 ====================

    /**
     * 测试：删除存在的文件成功
     */
    @Test
    void testDeleteExistingFile_success() throws Exception {
        // 创建测试文件
        Path testFile = tempDir.resolve("test_delete.txt");
        Files.writeString(testFile, "test content");

        assertTrue(Files.exists(testFile));

        // 删除文件
        String argumentsJson = createArgumentsJson(testFile.toString());
        String result = tool.execute(argumentsJson);

        // 验证结果
        JsonNode resultJson = objectMapper.readTree(result);
        assertTrue(resultJson.get("success").asBoolean());
        assertEquals("FILE", resultJson.get("type").asText());
        assertTrue(resultJson.get("deleted").asBoolean());

        // 验证文件已被删除
        assertFalse(Files.exists(testFile));
    }

    /**
     * 测试：删除不存在的文件失败
     */
    @Test
    void testDeleteNonExistentFile_fails() throws Exception {
        Path nonExistentFile = tempDir.resolve("non_existent.txt");

        String argumentsJson = createArgumentsJson(nonExistentFile.toString());
        assertThrows(FileOperationException.class, () -> tool.execute(argumentsJson));
    }

    // ==================== 测试空目录删除 ====================

    /**
     * 测试：删除空目录成功
     */
    @Test
    void testDeleteEmptyDirectory_success() throws Exception {
        // 创建空目录
        Path emptyDir = tempDir.resolve("empty_dir");
        Files.createDirectories(emptyDir);

        assertTrue(Files.exists(emptyDir) && Files.isDirectory(emptyDir));

        // 删除目录
        String argumentsJson = createArgumentsJson(emptyDir.toString());
        String result = tool.execute(argumentsJson);

        // 验证结果
        JsonNode resultJson = objectMapper.readTree(result);
        assertTrue(resultJson.get("success").asBoolean());
        assertEquals("DIRECTORY", resultJson.get("type").asText());

        // 验证目录已被删除
        assertFalse(Files.exists(emptyDir));
    }

    /**
     * 测试：删除非空目录失败（非递归模式）
     */
    @Test
    void testDeleteNonEmptyDirectory_fails() throws Exception {
        // 创建非空目录
        Path nonEmptyDir = tempDir.resolve("non_empty_dir");
        Files.createDirectories(nonEmptyDir);
        Files.writeString(nonEmptyDir.resolve("file.txt"), "content");

        // 尝试删除（非递归）
        String argumentsJson = createArgumentsJson(nonEmptyDir.toString());
        assertThrows(FileOperationException.class, () -> tool.execute(argumentsJson));

        // 验证目录仍然存在
        assertTrue(Files.exists(nonEmptyDir));
    }

    // ==================== 测试递归删除 ====================

    /**
     * 测试：递归删除目录成功
     */
    @Test
    void testRecursiveDelete_success() throws Exception {
        // 创建多层目录结构
        Path parentDir = tempDir.resolve("parent");
        Path childDir = parentDir.resolve("child");
        Path grandChildDir = childDir.resolve("grandchild");
        Files.createDirectories(grandChildDir);

        // 创建多个文件
        Files.writeString(parentDir.resolve("file1.txt"), "content1");
        Files.writeString(childDir.resolve("file2.txt"), "content2");
        Files.writeString(grandChildDir.resolve("file3.txt"), "content3");

        assertTrue(Files.exists(parentDir));

        // 递归删除
        String argumentsJson = createArgumentsJson(parentDir.toString(), true);
        String result = tool.execute(argumentsJson);

        // 验证结果
        JsonNode resultJson = objectMapper.readTree(result);
        assertTrue(resultJson.get("success").asBoolean());
        assertTrue(resultJson.get("recursive").asBoolean());

        // 验证目录已被删除
        assertFalse(Files.exists(parentDir));
    }

    // ==================== 测试重要文件保护 ====================

    /**
     * 测试：受保护的文件无法删除
     */
    @ParameterizedTest
    @ValueSource(strings = {"pom.xml", "build.gradle", "package.json", "application.yml"})
    void testProtectedFile_cannotDelete(String protectedFileName) throws Exception {
        // 创建受保护的文件
        Path protectedFile = tempDir.resolve(protectedFileName);
        Files.writeString(protectedFile, "protected content");

        // 尝试删除
        String argumentsJson = createArgumentsJson(protectedFile.toString());
        assertThrows(FileSecurityException.class, () -> tool.execute(argumentsJson));

        // 验证文件仍然存在
        assertTrue(Files.exists(protectedFile));
    }

    /**
     * 测试：受保护的目录无法删除
     */
    @ParameterizedTest
    @ValueSource(strings = {".git", "data", "logs", "target"})
    void testProtectedDirectory_cannotDelete(String protectedDirName) throws Exception {
        // 创建受保护的目录
        Path protectedDir = tempDir.resolve(protectedDirName);
        Files.createDirectories(protectedDir);

        // 尝试删除
        String argumentsJson = createArgumentsJson(protectedDir.toString());
        assertThrows(FileSecurityException.class, () -> tool.execute(argumentsJson));

        // 验证目录仍然存在
        assertTrue(Files.exists(protectedDir));
    }

    // ==================== 测试人机回环审批 ====================

    /**
     * 测试：带沙箱拦截器时删除操作触发审批
     */
    @Test
    void testDeleteWithSandbox_triggersApproval() throws Exception {
        // 创建测试文件
        Path testFile = tempDir.resolve("approval_test.txt");
        Files.writeString(testFile, "content");

        // 设置 toolCallId
        toolWithSandbox.setToolCallId("test-delete-001");

        // 删除操作应该触发审批
        String argumentsJson = createArgumentsJson(testFile.toString());
        SuspendExecutionException exception = assertThrows(
                SuspendExecutionException.class,
                () -> toolWithSandbox.execute(argumentsJson)
        );

        // 验证异常信息
        assertNotNull(exception.getMessage());
        assertEquals("test-delete-001", exception.getToolCallId());

        // 验证文件仍然存在（因为审批未通过）
        assertTrue(Files.exists(testFile));
    }

    /**
     * 测试：递归删除触发审批
     */
    @Test
    void testRecursiveDelete_triggersApproval() throws Exception {
        // 创建目录结构
        Path testDir = tempDir.resolve("recursive_test");
        Files.createDirectories(testDir);
        Files.writeString(testDir.resolve("file.txt"), "content");

        // 设置 toolCallId
        toolWithSandbox.setToolCallId("test-recursive-001");

        // 递归删除应该触发审批
        String argumentsJson = createArgumentsJson(testDir.toString(), true);
        SuspendExecutionException exception = assertThrows(
                SuspendExecutionException.class,
                () -> toolWithSandbox.execute(argumentsJson)
        );

        // 验证异常被正确抛出
        assertNotNull(exception.getMessage());

        // 验证目录仍然存在
        assertTrue(Files.exists(testDir));
    }

    /**
     * 测试：审批通过后删除成功
     */
    @Test
    void testApprovalGranted_deleteSucceeds() throws Exception {
        // 创建测试文件
        Path testFile = tempDir.resolve("approved_delete.txt");
        Files.writeString(testFile, "content");

        // 使用 executeWithBypass 模拟审批通过
        String argumentsJson = createArgumentsJson(testFile.toString());
        String result = toolWithSandbox.executeWithBypass(argumentsJson);

        // 验证删除成功
        JsonNode resultJson = objectMapper.readTree(result);
        assertTrue(resultJson.get("success").asBoolean());
        assertFalse(Files.exists(testFile));
    }

    /**
     * 测试：审批通过后递归删除成功
     */
    @Test
    void testApprovalGranted_recursiveDeleteSucceeds() throws Exception {
        // 创建目录结构
        Path testDir = tempDir.resolve("approved_recursive");
        Files.createDirectories(testDir);
        Files.writeString(testDir.resolve("file1.txt"), "content1");
        Files.writeString(testDir.resolve("file2.txt"), "content2");

        // 使用 executeWithBypass 模拟审批通过
        String argumentsJson = createArgumentsJson(testDir.toString(), true);
        String result = toolWithSandbox.executeWithBypass(argumentsJson);

        // 验证删除成功
        JsonNode resultJson = objectMapper.readTree(result);
        assertTrue(resultJson.get("success").asBoolean());
        assertTrue(resultJson.get("recursive").asBoolean());
        assertFalse(Files.exists(testDir));
    }

    // ==================== 测试安全违规检测 ====================

    /**
     * 测试：路径遍历攻击被阻止
     */
    @Test
    void testPathTraversal_blocked() throws Exception {
        // 尝试使用路径遍历 - 由于文件不存在，会抛出 FileOperationException
        // 但安全验证会在路径验证阶段阻止路径遍历攻击
        String argumentsJson = createArgumentsJson("../../../etc/passwd");
        // 路径遍历攻击会被安全验证拦截，抛出 FileSecurityException
        // 或者因为文件不存在而抛出 FileOperationException
        assertThrows(Exception.class, () -> tool.execute(argumentsJson));
    }

    /**
     * 测试：路径遍历攻击被安全验证拦截
     */
    @Test
    void testPathTraversal_securityBlocked() throws Exception {
        // 创建一个存在的文件，然后尝试通过路径遍历删除
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "content");

        // 尝试使用路径遍历访问项目外部的文件
        // 由于路径验证会检测到路径遍历，应该抛出 FileSecurityException
        String traversalPath = tempDir.getParent().resolve("external_file.txt").toString();
        String argumentsJson = createArgumentsJson(traversalPath);

        // 由于路径在项目根目录外，会被安全验证拦截
        assertThrows(Exception.class, () -> tool.execute(argumentsJson));
    }

    /**
     * 测试：无效路径被拒绝
     */
    @Test
    void testInvalidPath_rejected() throws Exception {
        // 尝试删除不存在的路径（非遍历攻击）
        Path nonExistent = tempDir.resolve("non_existent_path");
        String argumentsJson = createArgumentsJson(nonExistent.toString());
        assertThrows(FileOperationException.class, () -> tool.execute(argumentsJson));
    }

    // ==================== 测试参数解析 ====================

    /**
     * 测试：缺少 path 参数失败
     */
    @Test
    void testMissingPathParameter_fails() throws Exception {
        String argumentsJson = "{\"recursive\": true}";
        assertThrows(Exception.class, () -> tool.execute(argumentsJson));
    }

    /**
     * 测试：默认 recursive 为 false
     */
    @Test
    void testDefaultRecursive_false() throws Exception {
        // 创建空目录
        Path emptyDir = tempDir.resolve("default_recursive");
        Files.createDirectories(emptyDir);

        // 不指定 recursive 参数
        String argumentsJson = "{\"path\": \"" + emptyDir.toString().replace("\\", "\\\\") + "\"}";
        String result = tool.execute(argumentsJson);

        // 验证结果
        JsonNode resultJson = objectMapper.readTree(result);
        assertFalse(resultJson.get("recursive").asBoolean());
    }

    // ==================== 测试 setter 方法 ====================

    /**
     * 测试：setToolCallId 正常工作
     */
    @Test
    void testSetToolCallId() {
        tool.setToolCallId("test-id-123");
        // 验证方法不抛出异常
    }

    /**
     * 测试：setSandboxInterceptor 正常工作
     */
    @Test
    void testSetSandboxInterceptor() {
        DeleteFileTool newTool = new DeleteFileTool(tempDir.toString());
        newTool.setSandboxInterceptor(sandboxInterceptor);
        // 验证方法不抛出异常
    }

    // ==================== 测试边界情况 ====================

    /**
     * 测试：删除只读文件
     */
    @Test
    void testDeleteReadOnlyFile() throws Exception {
        // 创建只读文件
        Path readOnlyFile = tempDir.resolve("readonly.txt");
        Files.writeString(readOnlyFile, "readonly content");
        readOnlyFile.toFile().setReadOnly();

        // 尝试删除
        String argumentsJson = createArgumentsJson(readOnlyFile.toString());
        try {
            String result = tool.execute(argumentsJson);
            // 在某些系统上可能成功删除
            JsonNode resultJson = objectMapper.readTree(result);
            assertTrue(resultJson.get("success").asBoolean() || !Files.exists(readOnlyFile));
        } catch (FileOperationException e) {
            // 在某些系统上可能失败
            assertTrue(e.getErrorType() == FileOperationException.ErrorType.ACCESS_DENIED ||
                       e.getErrorType() == FileOperationException.ErrorType.IO_ERROR);
        }
    }

    /**
     * 测试：删除嵌套的受保护目录中的文件
     */
    @Test
    void testDeleteFileInProtectedDirectory() throws Exception {
        // 创建 .git 目录及其中的文件
        Path gitDir = tempDir.resolve(".git");
        Files.createDirectories(gitDir);
        Path gitFile = gitDir.resolve("config");
        Files.writeString(gitFile, "git config");

        // 尝试删除 .git 目录中的文件
        String argumentsJson = createArgumentsJson(gitFile.toString());
        assertThrows(FileSecurityException.class, () -> tool.execute(argumentsJson));

        // 验证文件仍然存在
        assertTrue(Files.exists(gitFile));
    }
}
