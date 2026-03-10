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
import tech.yesboss.tool.sandbox.SandboxInterceptor;
import tech.yesboss.tool.sandbox.impl.SandboxInterceptorImpl;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CreateDirectoryTool 人机回环审批测试
 *
 * <p>测试目录创建工具的人机回环审批机制，包括：</p>
 * <ul>
 *   <li>危险操作触发审批流程</li>
 *   <li>审批通过后创建成功</li>
 *   <li>审批拒绝后创建被阻止</li>
 *   <li>正常操作不触发审批</li>
 * </ul>
 *
 * <p><b>触发审批的场景：</b></p>
 * <ul>
 *   <li>在受保护的目录下创建子目录（.git, .ssh, .aws, secrets 等）</li>
 * </ul>
 */
class CreateDirectoryToolApprovalTest {

    private CreateDirectoryTool tool;
    private SandboxInterceptor sandboxInterceptor;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // 使用临时目录作为项目根目录
        String projectRoot = tempDir.toAbsolutePath().toString();

        // 创建沙箱拦截器
        sandboxInterceptor = new SandboxInterceptorImpl(true);

        // 创建带沙箱拦截器的工具
        tool = new CreateDirectoryTool(projectRoot, sandboxInterceptor);
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

    // ==================== 测试危险操作触发审批 ====================

    /**
     * 测试：在受保护的目录下创建子目录触发审批
     */
    @ParameterizedTest
    @ValueSource(strings = {".git", ".ssh", ".aws", ".kube", "secrets", "credentials"})
    void testCreateInProtectedDirectory_triggersApproval(String protectedDir) throws Exception {
        // 创建受保护目录
        Path protectedPath = tempDir.resolve(protectedDir);
        Files.createDirectories(protectedPath);

        // 尝试在受保护目录中创建子目录
        Path subDir = protectedPath.resolve("subdirectory");
        String argumentsJson = createArgumentsJson(subDir.toString());
        tool.setToolCallId("test-toolcall-001");

        // 应该触发审批异常
        SuspendExecutionException exception = assertThrows(
                SuspendExecutionException.class,
                () -> tool.execute(argumentsJson)
        );

        // 验证异常被正确抛出
        assertNotNull(exception.getMessage());
        assertEquals("test-toolcall-001", exception.getToolCallId());
    }

    /**
     * 测试：递归创建包含受保护目录的路径触发审批
     */
    @Test
    void testRecursiveCreateWithProtectedDir_triggersApproval() throws Exception {
        // 尝试递归创建包含 .git 的路径
        Path gitSubDir = tempDir.resolve(".git").resolve("hooks");
        String argumentsJson = createArgumentsJson(gitSubDir.toString(), true);
        tool.setToolCallId("test-toolcall-002");

        // 应该触发审批异常
        SuspendExecutionException exception = assertThrows(
                SuspendExecutionException.class,
                () -> tool.execute(argumentsJson)
        );

        // 验证异常被正确抛出
        assertNotNull(exception.getMessage());
    }

    // ==================== 测试审批通过后执行创建 ====================

    /**
     * 测试：审批通过后在受保护目录创建成功
     */
    @Test
    void testApprovalGranted_createSucceeds() throws Exception {
        // 创建受保护目录
        Path protectedPath = tempDir.resolve("secrets");
        Files.createDirectories(protectedPath);

        // 尝试创建子目录
        Path subDir = protectedPath.resolve("api_keys");
        String argumentsJson = createArgumentsJson(subDir.toString());

        // 使用 executeWithBypass 模拟审批通过
        String result = tool.executeWithBypass(argumentsJson);

        // 验证创建成功
        JsonNode resultJson = objectMapper.readTree(result);
        assertTrue(resultJson.get("success").asBoolean());
        assertTrue(Files.exists(subDir));
        assertTrue(Files.isDirectory(subDir));
    }

    /**
     * 测试：审批通过后递归创建受保护目录成功
     */
    @Test
    void testApprovalGranted_recursiveCreateSucceeds() throws Exception {
        // 尝试递归创建包含 .aws 的路径
        Path awsConfigDir = tempDir.resolve(".aws").resolve("config");
        String argumentsJson = createArgumentsJson(awsConfigDir.toString(), true);

        // 使用 executeWithBypass 模拟审批通过
        String result = tool.executeWithBypass(argumentsJson);

        // 验证创建成功
        JsonNode resultJson = objectMapper.readTree(result);
        assertTrue(resultJson.get("success").asBoolean());
        assertTrue(Files.exists(awsConfigDir));
    }

    // ==================== 测试审批拒绝后创建被阻止 ====================

    /**
     * 测试：审批拒绝后目录未被创建
     */
    @Test
    void testApprovalRejected_directoryNotCreated() throws Exception {
        // 创建受保护目录
        Path protectedPath = tempDir.resolve(".ssh");
        Files.createDirectories(protectedPath);

        // 尝试创建子目录
        Path subDir = protectedPath.resolve("keys");
        String argumentsJson = createArgumentsJson(subDir.toString());

        // 触发审批异常
        assertThrows(SuspendExecutionException.class, () -> tool.execute(argumentsJson));

        // 验证目录未被创建（因为审批未通过，未调用 executeWithBypass）
        assertFalse(Files.exists(subDir), "Directory should not be created when approval is rejected");
    }

    // ==================== 测试正常操作不触发审批 ====================

    /**
     * 测试：创建普通目录不触发审批
     */
    @Test
    void testCreateNormalDirectory_noApprovalNeeded() throws Exception {
        // 创建普通目录
        Path normalDir = tempDir.resolve("output");
        String argumentsJson = createArgumentsJson(normalDir.toString());

        // 应该成功执行，不触发审批
        String result = tool.execute(argumentsJson);

        // 验证创建成功
        JsonNode resultJson = objectMapper.readTree(result);
        assertTrue(resultJson.get("success").asBoolean());
        assertTrue(Files.exists(normalDir));
    }

    /**
     * 测试：递归创建普通目录结构不触发审批
     */
    @Test
    void testRecursiveCreateNormalDirs_noApprovalNeeded() throws Exception {
        // 递归创建多层目录
        Path deepDir = tempDir.resolve("src").resolve("main").resolve("java");
        String argumentsJson = createArgumentsJson(deepDir.toString(), true);

        // 应该成功执行，不触发审批
        String result = tool.execute(argumentsJson);

        // 验证创建成功
        JsonNode resultJson = objectMapper.readTree(result);
        assertTrue(resultJson.get("success").asBoolean());
        assertTrue(Files.exists(deepDir));
    }

    /**
     * 测试：创建已存在的目录不触发审批（幂等操作）
     */
    @Test
    void testCreateExistingDirectory_noApprovalNeeded() throws Exception {
        // 创建已存在的目录
        Path existingDir = tempDir.resolve("existing");
        Files.createDirectories(existingDir);

        // 再次创建相同目录
        String argumentsJson = createArgumentsJson(existingDir.toString());

        // 应该成功执行（幂等），不触发审批
        String result = tool.execute(argumentsJson);

        // 验证返回成功
        JsonNode resultJson = objectMapper.readTree(result);
        assertTrue(resultJson.get("success").asBoolean());
        assertFalse(resultJson.get("created").asBoolean(), "created should be false for existing directory");
    }

    // ==================== 测试无沙箱拦截器情况 ====================

    /**
     * 测试：无沙箱拦截器时不触发审批
     */
    @Test
    void testNoSandboxInterceptor_noApprovalNeeded() throws Exception {
        // 创建无沙箱拦截器的工具
        CreateDirectoryTool toolWithoutSandbox = new CreateDirectoryTool(tempDir.toAbsolutePath().toString());

        // 创建受保护目录
        Path protectedPath = tempDir.resolve("secrets");
        Files.createDirectories(protectedPath);

        // 在受保护目录中创建子目录
        Path subDir = protectedPath.resolve("keys");

        // 应该直接执行，不触发审批
        String argumentsJson = createArgumentsJson(subDir.toString());
        String result = toolWithoutSandbox.execute(argumentsJson);

        // 验证创建成功
        JsonNode resultJson = objectMapper.readTree(result);
        assertTrue(resultJson.get("success").asBoolean());
    }

    // ==================== 测试工具属性 ====================

    /**
     * 测试：工具访问级别为 READ_WRITE
     */
    @Test
    void testAccessLevel() {
        assertEquals(ToolAccessLevel.READ_WRITE, tool.getAccessLevel());
    }

    /**
     * 测试：工具名称正确
     */
    @Test
    void testToolName() {
        assertEquals("create_directory", tool.getName());
    }

    /**
     * 测试：工具描述不为空
     */
    @Test
    void testToolDescription() {
        assertFalse(tool.getDescription().isEmpty());
        assertTrue(tool.getDescription().contains("人机回环") || tool.getDescription().contains("approval"));
    }

    // ==================== 测试 ToolCallId 追踪 ====================

    /**
     * 测试：SuspendExecutionException 包含正确的 toolCallId
     */
    @Test
    void testSuspendExceptionContainsToolCallId() throws Exception {
        // 创建受保护目录
        Path protectedPath = tempDir.resolve(".aws");
        Files.createDirectories(protectedPath);

        // 设置 toolCallId
        String expectedToolCallId = "toolcall-createdir-456";
        tool.setToolCallId(expectedToolCallId);

        // 触发审批
        Path subDir = protectedPath.resolve("credentials");
        String argumentsJson = createArgumentsJson(subDir.toString());
        SuspendExecutionException exception = assertThrows(
                SuspendExecutionException.class,
                () -> tool.execute(argumentsJson)
        );

        // 验证 toolCallId
        assertEquals(expectedToolCallId, exception.getToolCallId());
    }

    /**
     * 测试：未设置 toolCallId 时使用默认值
     */
    @Test
    void testDefaultToolCallId() throws Exception {
        // 创建受保护目录
        Path protectedPath = tempDir.resolve("secrets");
        Files.createDirectories(protectedPath);

        // 不设置 toolCallId
        tool.setToolCallId(null);

        // 触发审批
        Path subDir = protectedPath.resolve("api_keys");
        String argumentsJson = createArgumentsJson(subDir.toString());
        SuspendExecutionException exception = assertThrows(
                SuspendExecutionException.class,
                () -> tool.execute(argumentsJson)
        );

        // 验证 toolCallId 被自动生成
        assertNotNull(exception.getToolCallId());
        assertTrue(exception.getToolCallId().startsWith("unknown-"));
    }

    // ==================== 测试边界情况 ====================

    /**
     * 测试：在根目录下创建受保护名称的目录触发审批
     */
    @Test
    void testCreateProtectedDirAtRoot_triggersApproval() throws Exception {
        // 尝试创建 .git 目录
        Path gitDir = tempDir.resolve(".git");
        String argumentsJson = createArgumentsJson(gitDir.toString());
        tool.setToolCallId("test-toolcall-git");

        // 应该触发审批异常
        SuspendExecutionException exception = assertThrows(
                SuspendExecutionException.class,
                () -> tool.execute(argumentsJson)
        );

        // 验证异常信息
        assertNotNull(exception.getMessage());
    }

    /**
     * 测试：使用 setter 设置沙箱拦截器
     */
    @Test
    void testSetSandboxInterceptor() throws Exception {
        // 创建无沙箱拦截器的工具
        CreateDirectoryTool toolWithoutSandbox = new CreateDirectoryTool(tempDir.toAbsolutePath().toString());

        // 创建受保护目录
        Path protectedPath = tempDir.resolve(".ssh");
        Files.createDirectories(protectedPath);

        // 设置沙箱拦截器
        toolWithoutSandbox.setSandboxInterceptor(sandboxInterceptor);

        // 尝试创建子目录
        Path subDir = protectedPath.resolve("keys");
        String argumentsJson = createArgumentsJson(subDir.toString());

        // 现在应该触发审批
        assertThrows(SuspendExecutionException.class, () -> toolWithoutSandbox.execute(argumentsJson));
    }

    /**
     * 测试：混合路径（部分受保护部分不受保护）
     */
    @Test
    void testMixedPath_partialProtection() throws Exception {
        // 创建普通目录下的受保护名称子目录
        Path normalDir = tempDir.resolve("config");
        Files.createDirectories(normalDir);

        // 在普通目录下创建 secrets 子目录
        Path secretsSubDir = normalDir.resolve("secrets");
        String argumentsJson = createArgumentsJson(secretsSubDir.toString());
        tool.setToolCallId("test-mixed-path");

        // 应该触发审批异常
        SuspendExecutionException exception = assertThrows(
                SuspendExecutionException.class,
                () -> tool.execute(argumentsJson)
        );

        // 验证异常信息
        assertNotNull(exception.getMessage());
    }
}
