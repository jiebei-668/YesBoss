package tech.yesboss.tool.filesystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.yesboss.tool.ToolAccessLevel;
import tech.yesboss.tool.filesystem.exception.FileOperationException;
import tech.yesboss.tool.filesystem.exception.FileSecurityException;
import tech.yesboss.tool.filesystem.model.FileMetadata;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CreateDirectoryTool 单元测试
 *
 * <p>测试目录创建工具的各项功能，包括：
 * <ul>
 *   <li>单层目录创建</li>
 *   <li>递归目录创建</li>
 *   <li>幂等性验证</li>
 *   <li>安全违规检测</li>
 *   <li>异常处理</li>
 * </ul>
 */
class CreateDirectoryToolTest {

    private CreateDirectoryTool tool;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // 使用临时目录作为项目根目录
        String projectRoot = tempDir.toAbsolutePath().toString();
        tool = new CreateDirectoryTool(projectRoot);
        objectMapper = new ObjectMapper();
    }

    /**
     * 创建 JSON 参数字符串
     *
     * @param path 目录路径
     * @param recursive 是否递归创建
     * @return JSON 参数字符串
     */
    private String createArgumentsJson(String path, Boolean recursive) {
        StringBuilder json = new StringBuilder("{\"path\": \"");
        // 转义路径中的反斜杠
        String escapedPath = path.replace("\\", "\\\\");
        json.append(escapedPath);
        json.append("\"");

        if (recursive != null) {
            json.append(", \"recursive\": ").append(recursive);
        }

        json.append("}");
        return json.toString();
    }

    /**
     * 创建 JSON 参数字符串（简化版本）
     */
    private String createArgumentsJson(String path) {
        return createArgumentsJson(path, null);
    }

    // ==================== 基础功能测试 ====================

    @Test
    void testGetName() {
        assertEquals("create_directory", tool.getName());
    }

    @Test
    void testGetDescription() {
        String description = tool.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("创建目录"));
        assertTrue(description.contains("递归创建"));
        assertTrue(description.contains("幂等操作"));
    }

    @Test
    void testGetParametersJsonSchema() {
        String schema = tool.getParametersJsonSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("\"type\": \"object\""));
        assertTrue(schema.contains("\"path\""));
        assertTrue(schema.contains("\"recursive\""));
    }

    @Test
    void testGetAccessLevel() {
        assertEquals(ToolAccessLevel.READ_WRITE, tool.getAccessLevel());
    }

    // ==================== 单层目录创建测试 ====================

    @Test
    void testCreateSingleDirectory() throws Exception {
        // 创建单个目录
        Path testDir = tempDir.resolve("test_single");
        String dirPath = testDir.toAbsolutePath().toString();

        String result = tool.execute(createArgumentsJson(dirPath));

        // 验证创建成功
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals("CREATE_DIRECTORY", jsonResult.get("operation").asText());
        assertTrue(jsonResult.get("created").asBoolean());
        assertFalse(jsonResult.get("recursive").asBoolean());

        // 验证目录存在
        assertTrue(Files.exists(testDir));
        assertTrue(Files.isDirectory(testDir));

        // 验证元数据
        JsonNode metadata = jsonResult.get("metadata");
        assertNotNull(metadata);
        assertEquals("DIRECTORY", metadata.get("type").asText());
        assertEquals("test_single", metadata.get("name").asText());
    }

    @Test
    void testCreateSingleDirectoryWithLeadingSlash() throws Exception {
        // 测试带前导斜杠的路径（使用临时目录内的路径）
        Path testDir = tempDir.resolve("test_with_slash");
        String dirPath = testDir.toAbsolutePath().toString();

        String result = tool.execute(createArgumentsJson(dirPath));

        // 验证创建成功
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());

        // 验证目录存在
        assertTrue(Files.exists(testDir));
        assertTrue(Files.isDirectory(testDir));
    }

    // ==================== 递归目录创建测试 ====================

    @Test
    void testCreateNestedDirectoriesRecursive() throws Exception {
        // 递归创建多层目录
        Path nestedDir = tempDir.resolve("level1/level2/level3");
        String dirPath = nestedDir.toAbsolutePath().toString();

        String result = tool.execute(createArgumentsJson(dirPath, true));

        // 验证创建成功
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals("CREATE_DIRECTORY", jsonResult.get("operation").asText());
        assertTrue(jsonResult.get("created").asBoolean());
        assertTrue(jsonResult.get("recursive").asBoolean());

        // 验证所有层级目录都存在
        Path level1 = tempDir.resolve("level1");
        Path level2 = tempDir.resolve("level1/level2");
        Path level3 = tempDir.resolve("level1/level2/level3");

        assertTrue(Files.exists(level1) && Files.isDirectory(level1));
        assertTrue(Files.exists(level2) && Files.isDirectory(level2));
        assertTrue(Files.exists(level3) && Files.isDirectory(level3));
    }

    @Test
    void testCreateSingleDirectoryRecursive() throws Exception {
        // 使用递归模式创建单层目录
        Path testDir = tempDir.resolve("test_single_recursive");
        String dirPath = testDir.toAbsolutePath().toString();

        String result = tool.execute(createArgumentsJson(dirPath, true));

        // 验证创建成功
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
        assertTrue(jsonResult.get("created").asBoolean());
        assertTrue(jsonResult.get("recursive").asBoolean());

        // 验证目录存在
        assertTrue(Files.exists(testDir));
        assertTrue(Files.isDirectory(testDir));
    }

    // ==================== 幂等性测试 ====================

    @Test
    void testCreateExistingDirectoryIdempotent() throws Exception {
        // 创建目录
        Path testDir = tempDir.resolve("test_idempotent");
        String dirPath = testDir.toAbsolutePath().toString();

        // 首次创建
        String result1 = tool.execute(createArgumentsJson(dirPath));

        // 验证首次创建成功
        JsonNode jsonResult1 = objectMapper.readTree(result1);
        assertTrue(jsonResult1.get("success").asBoolean());
        assertTrue(jsonResult1.get("created").asBoolean());

        // 重复创建
        String result2 = tool.execute(createArgumentsJson(dirPath));

        // 验证重复创建也成功（幂等）
        JsonNode jsonResult2 = objectMapper.readTree(result2);
        assertTrue(jsonResult2.get("success").asBoolean());
        assertFalse(jsonResult2.get("created").asBoolean()); // 未创建新目录
        assertEquals("Directory already exists", jsonResult2.get("message").asText());

        // 验证目录仍然存在
        assertTrue(Files.exists(testDir));
        assertTrue(Files.isDirectory(testDir));
    }

    @Test
    void testCreateExistingDirectoryRecursiveIdempotent() throws Exception {
        // 先递归创建多层目录
        Path nestedDir = tempDir.resolve("level1/level2/level3");
        String dirPath = nestedDir.toAbsolutePath().toString();

        String result1 = tool.execute(createArgumentsJson(dirPath, true));

        // 验证首次创建成功
        JsonNode jsonResult1 = objectMapper.readTree(result1);
        assertTrue(jsonResult1.get("success").asBoolean());
        assertTrue(jsonResult1.get("created").asBoolean());

        // 重复创建
        String result2 = tool.execute(createArgumentsJson(dirPath, true));

        // 验证重复创建也成功（幂等）
        JsonNode jsonResult2 = objectMapper.readTree(result2);
        assertTrue(jsonResult2.get("success").asBoolean());
        assertFalse(jsonResult2.get("created").asBoolean()); // 未创建新目录
    }

    // ==================== 错误处理测试 ====================

    @Test
    void testCreateNestedDirectoryWithoutRecursive() throws Exception {
        // 不使用递归模式创建多层目录（应该失败）
        Path nestedDir = tempDir.resolve("nonexistent/child");
        String dirPath = nestedDir.toAbsolutePath().toString();

        // 应该抛出异常
        assertThrows(FileOperationException.class, () -> {
            tool.execute(createArgumentsJson(dirPath, false));
        });

        // 验证目录未被创建
        assertFalse(Files.exists(nestedDir));
    }

    @Test
    void testCreateDirectoryWhereFileExists() throws Exception {
        // 先创建一个文件
        Path testFile = tempDir.resolve("test_file");
        Files.writeString(testFile, "test content");

        String filePath = testFile.toAbsolutePath().toString();

        // 尝试创建同名目录（应该失败）
        assertThrows(FileOperationException.class, () -> {
            tool.execute(createArgumentsJson(filePath));
        });
    }

    // ==================== 安全验证测试 ====================

    @Test
    void testCreateDirectoryWithPathTraversal() throws Exception {
        // 测试路径遍历攻击防护
        String maliciousPath = tempDir.resolve("safe").toAbsolutePath().toString();
        String traversalPath = maliciousPath + "/../../../etc/passwd";

        // 应该抛出安全异常
        assertThrows(FileSecurityException.class, () -> {
            tool.execute(createArgumentsJson(traversalPath));
        });
    }

    @Test
    void testCreateDirectoryWithBlacklistedPath() throws Exception {
        // 测试黑名单路径过滤
        String projectRoot = tempDir.toAbsolutePath().toString();
        String outsidePath = projectRoot + "/../outside_dir";

        // 应该抛出安全异常
        assertThrows(FileSecurityException.class, () -> {
            tool.execute(createArgumentsJson(outsidePath));
        });
    }

    // ==================== 参数验证测试 ====================

    @Test
    void testCreateDirectoryWithMissingPath() {
        // 测试缺少路径参数
        String invalidJson = "{\"recursive\": true}";

        assertThrows(Exception.class, () -> {
            tool.execute(invalidJson);
        });
    }

    @Test
    void testCreateDirectoryWithEmptyPath() {
        // 测试空路径
        String invalidJson = "{\"path\": \"\"}";

        assertThrows(Exception.class, () -> {
            tool.execute(invalidJson);
        });
    }

    @Test
    void testCreateDirectoryWithInvalidJson() {
        // 测试无效的 JSON
        String invalidJson = "{path: test}";

        assertThrows(Exception.class, () -> {
            tool.execute(invalidJson);
        });
    }

    // ==================== 元数据测试 ====================

    @Test
    void testCreateDirectoryMetadata() throws Exception {
        // 测试返回的元数据
        Path testDir = tempDir.resolve("test_metadata");
        String dirPath = testDir.toAbsolutePath().toString();

        String result = tool.execute(createArgumentsJson(dirPath));

        // 验证元数据
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());

        JsonNode metadata = jsonResult.get("metadata");
        assertNotNull(metadata);
        assertEquals(dirPath, metadata.get("path").asText());
        assertEquals("test_metadata", metadata.get("name").asText());
        assertEquals("DIRECTORY", metadata.get("type").asText());
        assertEquals(0, metadata.get("size").asLong()); // 目录大小为 0
        assertTrue(metadata.get("lastModified").asLong() > 0);
        // 检查扩展名是否为 null 或空字符串
        JsonNode extensionNode = metadata.get("extension");
        assertTrue(extensionNode == null || extensionNode.isNull() || extensionNode.asText().isEmpty());
    }

    // ==================== executeWithBypass 测试 ====================

    @Test
    void testExecuteWithBypass() throws Exception {
        // 测试绕过沙箱的执行
        Path testDir = tempDir.resolve("test_bypass");
        String dirPath = testDir.toAbsolutePath().toString();

        String result = tool.executeWithBypass(createArgumentsJson(dirPath));

        // 验证执行成功
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());

        // 验证目录存在
        assertTrue(Files.exists(testDir));
        assertTrue(Files.isDirectory(testDir));
    }

    // ==================== 边界情况测试 ====================

    @Test
    void testCreateDirectoryWithRelativePath() throws Exception {
        // 测试相对路径（使用临时目录下的相对路径）
        Path relativeDir = tempDir.resolve("relative_dir");
        String dirPath = relativeDir.toAbsolutePath().toString();

        String result = tool.execute(createArgumentsJson(dirPath));

        // 验证创建成功
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());

        // 验证目录存在
        assertTrue(Files.exists(relativeDir));
    }

    @Test
    void testCreateDirectoryWithSpacesInName() throws Exception {
        // 测试带空格的目录名
        Path testDir = tempDir.resolve("dir with spaces");
        String dirPath = testDir.toAbsolutePath().toString();

        String result = tool.execute(createArgumentsJson(dirPath));

        // 验证创建成功
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());

        // 验证目录存在
        assertTrue(Files.exists(testDir));
    }

    @Test
    void testCreateDeeplyNestedDirectories() throws Exception {
        // 测试创建深层嵌套目录
        Path deepDir = tempDir.resolve("a/b/c/d/e/f/g");
        String dirPath = deepDir.toAbsolutePath().toString();

        String result = tool.execute(createArgumentsJson(dirPath, true));

        // 验证创建成功
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());

        // 验证所有层级都存在
        assertTrue(Files.exists(deepDir));
        assertTrue(Files.isDirectory(deepDir));
    }

    // ==================== 多次创建测试 ====================

    @Test
    void testCreateMultipleDirectories() throws Exception {
        // 测试创建多个不同的目录
        Path dir1 = tempDir.resolve("dir1");
        Path dir2 = tempDir.resolve("dir2");
        Path dir3 = tempDir.resolve("dir3");

        // 创建所有目录
        String result1 = tool.execute(createArgumentsJson(dir1.toAbsolutePath().toString()));
        String result2 = tool.execute(createArgumentsJson(dir2.toAbsolutePath().toString()));
        String result3 = tool.execute(createArgumentsJson(dir3.toAbsolutePath().toString()));

        // 验证所有创建都成功
        JsonNode jsonResult1 = objectMapper.readTree(result1);
        JsonNode jsonResult2 = objectMapper.readTree(result2);
        JsonNode jsonResult3 = objectMapper.readTree(result3);

        assertTrue(jsonResult1.get("success").asBoolean());
        assertTrue(jsonResult2.get("success").asBoolean());
        assertTrue(jsonResult3.get("success").asBoolean());

        // 验证所有目录都存在
        assertTrue(Files.exists(dir1));
        assertTrue(Files.exists(dir2));
        assertTrue(Files.exists(dir3));
    }
}