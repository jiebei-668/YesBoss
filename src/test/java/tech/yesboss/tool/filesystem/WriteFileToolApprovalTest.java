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
 * WriteFileTool 人机回环审批测试
 *
 * <p>测试文件写入工具的人机回环审批机制，包括：</p>
 * <ul>
 *   <li>危险操作触发审批流程</li>
 *   <li>审批通过后写入成功</li>
 *   <li>审批拒绝后写入被阻止</li>
 *   <li>正常操作不触发审批</li>
 * </ul>
 *
 * <p><b>触发审批的场景：</b></p>
 * <ul>
 *   <li>覆盖已存在的文件</li>
 *   <li>写入受保护的文件扩展名（.env, .pem, .key, .db 等）</li>
 *   <li>写入受保护的目录（.git, .ssh, .aws, secrets 等）</li>
 * </ul>
 */
class WriteFileToolApprovalTest {

    private WriteFileTool tool;
    private SandboxInterceptor sandboxInterceptor;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // 使用临时目录作为项目根目录
        String projectRoot = tempDir.toAbsolutePath().toString();

        // 创建启用覆盖审批的沙箱拦截器
        sandboxInterceptor = new SandboxInterceptorImpl(true);

        // 创建带沙箱拦截器的工具
        tool = new WriteFileTool(projectRoot, sandboxInterceptor);
        objectMapper = new ObjectMapper();
    }

    /**
     * 创建 JSON 参数字符串
     */
    private String createArgumentsJson(String filePath, String content) throws Exception {
        StringBuilder json = new StringBuilder("{\"path\": \"");
        String escapedPath = filePath.replace("\\", "\\\\");
        json.append(escapedPath);
        json.append("\", \"content\": \"");
        String escapedContent = content.replace("\\", "\\\\")
                                       .replace("\"", "\\\"")
                                       .replace("\n", "\\n")
                                       .replace("\r", "\\r")
                                       .replace("\t", "\\t");
        json.append(escapedContent);
        json.append("\"}");
        return json.toString();
    }

    // ==================== 测试危险操作触发审批 ====================

    /**
     * 测试：覆盖已存在的文件触发审批
     */
    @Test
    void testOverwriteExistingFile_triggersApproval() throws Exception {
        // 1. 先创建一个已存在的文件
        Path existingFile = tempDir.resolve("existing.txt");
        Files.writeString(existingFile, "Original content");

        // 2. 尝试覆盖写入
        String argumentsJson = createArgumentsJson(existingFile.toString(), "New content");
        tool.setToolCallId("test-toolcall-001");

        // 3. 应该触发审批异常
        SuspendExecutionException exception = assertThrows(
                SuspendExecutionException.class,
                () -> tool.execute(argumentsJson)
        );

        // 4. 验证异常信息
        assertNotNull(exception.getMessage());
        assertEquals("test-toolcall-001", exception.getToolCallId());
    }

    /**
     * 测试：写入受保护的文件扩展名触发审批
     */
    @ParameterizedTest
    @ValueSource(strings = {"config.env", "server.pem", "private.key", "data.db", "test.sqlite"})
    void testWriteProtectedExtension_triggersApproval(String fileName) throws Exception {
        // 尝试写入受保护的文件类型
        Path protectedFile = tempDir.resolve(fileName);
        String argumentsJson = createArgumentsJson(protectedFile.toString(), "sensitive data");
        tool.setToolCallId("test-toolcall-002");

        // 应该触发审批异常
        SuspendExecutionException exception = assertThrows(
                SuspendExecutionException.class,
                () -> tool.execute(argumentsJson)
        );

        // 验证异常被正确抛出
        assertNotNull(exception.getMessage());
    }

    /**
     * 测试：写入受保护的目录触发审批
     */
    @ParameterizedTest
    @ValueSource(strings = {".git", ".ssh", ".aws", ".kube", "secrets", "credentials"})
    void testWriteToProtectedDirectory_triggersApproval(String protectedDir) throws Exception {
        // 创建受保护目录
        Path protectedPath = tempDir.resolve(protectedDir);
        Files.createDirectories(protectedPath);

        // 尝试在受保护目录中写入文件
        Path targetFile = protectedPath.resolve("config.txt");
        String argumentsJson = createArgumentsJson(targetFile.toString(), "config content");
        tool.setToolCallId("test-toolcall-003");

        // 应该触发审批异常
        SuspendExecutionException exception = assertThrows(
                SuspendExecutionException.class,
                () -> tool.execute(argumentsJson)
        );

        // 验证异常被正确抛出
        assertNotNull(exception.getMessage());
    }

    // ==================== 测试审批通过后执行写入 ====================

    /**
     * 测试：审批通过后覆盖文件成功
     */
    @Test
    void testApprovalGranted_writeSucceeds() throws Exception {
        // 1. 创建已存在的文件
        Path existingFile = tempDir.resolve("approved.txt");
        Files.writeString(existingFile, "Original content");

        // 2. 使用 executeWithBypass 模拟审批通过
        String argumentsJson = createArgumentsJson(existingFile.toString(), "Approved content");

        // 3. 执行写入（绕过审批）
        String result = tool.executeWithBypass(argumentsJson);

        // 4. 验证写入成功
        JsonNode resultJson = objectMapper.readTree(result);
        assertTrue(resultJson.get("success").asBoolean());
        assertEquals("Approved content", Files.readString(existingFile));
    }

    /**
     * 测试：审批通过后写入受保护扩展名文件成功
     */
    @Test
    void testApprovalGranted_protectedExtensionWriteSucceeds() throws Exception {
        // 尝试写入 .env 文件
        Path envFile = tempDir.resolve("production.env");
        String argumentsJson = createArgumentsJson(envFile.toString(), "API_KEY=secret123");

        // 使用 executeWithBypass 模拟审批通过
        String result = tool.executeWithBypass(argumentsJson);

        // 验证写入成功
        JsonNode resultJson = objectMapper.readTree(result);
        assertTrue(resultJson.get("success").asBoolean());
        assertTrue(Files.exists(envFile));
        assertEquals("API_KEY=secret123", Files.readString(envFile));
    }

    /**
     * 测试：审批通过后写入受保护目录成功
     */
    @Test
    void testApprovalGranted_protectedDirectoryWriteSucceeds() throws Exception {
        // 创建 .secrets 目录
        Path secretsDir = tempDir.resolve("secrets");
        Files.createDirectories(secretsDir);

        // 尝试写入文件
        Path secretFile = secretsDir.resolve("api_key.txt");
        String argumentsJson = createArgumentsJson(secretFile.toString(), "super-secret-key");

        // 使用 executeWithBypass 模拟审批通过
        String result = tool.executeWithBypass(argumentsJson);

        // 验证写入成功
        JsonNode resultJson = objectMapper.readTree(result);
        assertTrue(resultJson.get("success").asBoolean());
        assertEquals("super-secret-key", Files.readString(secretFile));
    }

    // ==================== 测试审批拒绝后写入被阻止 ====================

    /**
     * 测试：审批拒绝后文件保持原样（不执行写入）
     */
    @Test
    void testApprovalRejected_fileUnchanged() throws Exception {
        // 1. 创建已存在的文件
        Path existingFile = tempDir.resolve("rejected.txt");
        Files.writeString(existingFile, "Original content");

        // 2. 模拟审批拒绝（不调用 executeWithBypass，即不执行写入）

        // 3. 验证文件保持原样
        assertEquals("Original content", Files.readString(existingFile));
    }

    /**
     * 测试：审批拒绝后文件不存在（新文件不被创建）
     */
    @Test
    void testApprovalRejected_newFileNotCreated() throws Exception {
        // 尝试写入一个需要审批的文件（受保护扩展名）
        Path protectedFile = tempDir.resolve("config.env");
        String argumentsJson = createArgumentsJson(protectedFile.toString(), "sensitive data");

        // 触发审批异常
        assertThrows(SuspendExecutionException.class, () -> tool.execute(argumentsJson));

        // 验证文件未被创建（因为审批未通过，未调用 executeWithBypass）
        assertFalse(Files.exists(protectedFile), "File should not be created when approval is rejected");
    }

    // ==================== 测试正常操作不触发审批 ====================

    /**
     * 测试：写入新文件不触发审批
     */
    @Test
    void testWriteNewFile_noApprovalNeeded() throws Exception {
        // 写入一个新文件（不存在的文件）
        Path newFile = tempDir.resolve("newfile.txt");
        String argumentsJson = createArgumentsJson(newFile.toString(), "New content");

        // 应该成功执行，不触发审批
        String result = tool.execute(argumentsJson);

        // 验证写入成功
        JsonNode resultJson = objectMapper.readTree(result);
        assertTrue(resultJson.get("success").asBoolean());
        assertEquals("New content", Files.readString(newFile));
    }

    /**
     * 测试：写入普通文件类型不触发审批
     */
    @ParameterizedTest
    @ValueSource(strings = {"normal.txt", "config.json", "app.yaml", "Main.java", "script.py", "index.js"})
    void testWriteNormalFile_noApprovalNeeded(String fileName) throws Exception {
        // 写入普通文件类型
        Path normalFile = tempDir.resolve(fileName);
        String argumentsJson = createArgumentsJson(normalFile.toString(), "content");

        // 应该成功执行，不触发审批
        String result = tool.execute(argumentsJson);

        // 验证写入成功
        JsonNode resultJson = objectMapper.readTree(result);
        assertTrue(resultJson.get("success").asBoolean());
    }

    /**
     * 测试：追加模式写入已存在文件不触发审批
     */
    @Test
    void testAppendMode_noApprovalNeeded() throws Exception {
        // 创建已存在的文件
        Path existingFile = tempDir.resolve("append.txt");
        Files.writeString(existingFile, "Original");

        // 使用追加模式
        StringBuilder json = new StringBuilder("{\"path\": \"");
        json.append(existingFile.toString().replace("\\", "\\\\"));
        json.append("\", \"content\": \" appended\", \"mode\": \"APPEND\"}");
        String argumentsJson = json.toString();

        // 追加模式不应该触发覆盖审批
        String result = tool.execute(argumentsJson);

        // 验证写入成功
        JsonNode resultJson = objectMapper.readTree(result);
        assertTrue(resultJson.get("success").asBoolean());
        assertEquals("Original appended", Files.readString(existingFile));
    }

    // ==================== 测试无沙箱拦截器情况 ====================

    /**
     * 测试：无沙箱拦截器时不触发审批（写入新文件）
     */
    @Test
    void testNoSandboxInterceptor_noApprovalNeeded() throws Exception {
        // 创建无沙箱拦截器的工具
        WriteFileTool toolWithoutSandbox = new WriteFileTool(tempDir.toAbsolutePath().toString());

        // 写入新文件（不需要审批）
        Path newFile = tempDir.resolve("no-sandbox-new.txt");
        String argumentsJson = createArgumentsJson(newFile.toString(), "New content");
        String result = toolWithoutSandbox.execute(argumentsJson);

        // 验证写入成功
        JsonNode resultJson = objectMapper.readTree(result);
        assertTrue(resultJson.get("success").asBoolean());
        assertEquals("New content", Files.readString(newFile));
    }

    /**
     * 测试：无沙箱拦截器时覆盖文件不触发审批（无审批机制）
     */
    @Test
    void testNoSandboxInterceptor_overwriteAllowed() throws Exception {
        // 创建无沙箱拦截器的工具
        WriteFileTool toolWithoutSandbox = new WriteFileTool(tempDir.toAbsolutePath().toString());

        // 创建已存在的文件
        Path existingFile = tempDir.resolve("existing-no-sandbox.txt");
        Files.writeString(existingFile, "Original");

        // 无沙箱拦截器时，覆盖写入直接执行（不触发审批）
        String argumentsJson = createArgumentsJson(existingFile.toString(), "New content");
        String result = toolWithoutSandbox.execute(argumentsJson);

        // 验证写入成功
        JsonNode resultJson = objectMapper.readTree(result);
        assertTrue(resultJson.get("success").asBoolean());
        assertEquals("New content", Files.readString(existingFile));
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
        assertEquals("write_file", tool.getName());
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
        // 创建已存在的文件
        Path existingFile = tempDir.resolve("trace.txt");
        Files.writeString(existingFile, "Original");

        // 设置 toolCallId
        String expectedToolCallId = "toolcall-trace-123";
        tool.setToolCallId(expectedToolCallId);

        // 触发审批
        String argumentsJson = createArgumentsJson(existingFile.toString(), "New content");
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
        // 创建已存在的文件
        Path existingFile = tempDir.resolve("default-id.txt");
        Files.writeString(existingFile, "Original");

        // 不设置 toolCallId
        tool.setToolCallId(null);

        // 触发审批
        String argumentsJson = createArgumentsJson(existingFile.toString(), "New content");
        SuspendExecutionException exception = assertThrows(
                SuspendExecutionException.class,
                () -> tool.execute(argumentsJson)
        );

        // 验证 toolCallId 被自动生成
        assertNotNull(exception.getToolCallId());
        assertTrue(exception.getToolCallId().startsWith("unknown-"));
    }

    // ==================== 测试禁用覆盖审批 ====================

    /**
     * 测试：禁用覆盖审批时不触发审批
     */
    @Test
    void testOverwriteApprovalDisabled_noApprovalNeeded() throws Exception {
        // 创建禁用覆盖审批的沙箱拦截器
        SandboxInterceptor interceptorWithoutOverwriteCheck = new SandboxInterceptorImpl(false);
        WriteFileTool toolWithoutOverwriteCheck = new WriteFileTool(
                tempDir.toAbsolutePath().toString(),
                interceptorWithoutOverwriteCheck
        );

        // 创建已存在的文件
        Path existingFile = tempDir.resolve("no-overwrite-check.txt");
        Files.writeString(existingFile, "Original");

        // 应该直接执行，不触发审批
        String argumentsJson = createArgumentsJson(existingFile.toString(), "New content");
        String result = toolWithoutOverwriteCheck.execute(argumentsJson);

        // 验证写入成功
        JsonNode resultJson = objectMapper.readTree(result);
        assertTrue(resultJson.get("success").asBoolean());
    }
}
