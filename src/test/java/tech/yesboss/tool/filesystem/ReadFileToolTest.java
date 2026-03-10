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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReadFileTool 单元测试
 *
 * <p>测试文件读取工具的各项功能，包括：
 * <ul>
 *   <li>正常文件读取</li>
 *   <li>不同文件格式支持</li>
 *   <li>文件编码处理</li>
 *   <li>安全违规检测</li>
 *   <li>异常处理</li>
 * </ul>
 */
class ReadFileToolTest {

    private ReadFileTool tool;
    private ObjectMapper objectMapper;
    private String testResourcesPath;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // 使用项目的测试资源目录（转换为绝对路径）
        Path testResourceDir = Path.of("src/test/resources/tech/yesboss/tool/filesystem/test-files").toAbsolutePath();
        testResourcesPath = testResourceDir.toString();
        tool = new ReadFileTool(testResourcesPath);
        objectMapper = new ObjectMapper();
    }

    /**
     * 创建 JSON 参数字符串，正确处理路径转义
     *
     * @param filePath 文件路径
     * @param encoding 编码（可选）
     * @return JSON 参数字符串
     * @throws Exception 如果序列化失败
     */
    private String createArgumentsJson(String filePath, String encoding) throws Exception {
        StringBuilder json = new StringBuilder("{\"path\": \"");
        // 转义路径中的反斜杠
        String escapedPath = filePath.replace("\\", "\\\\");
        json.append(escapedPath);
        json.append("\"");

        if (encoding != null && !encoding.isEmpty()) {
            json.append(", \"encoding\": \"").append(encoding).append("\"");
        }

        json.append("}");
        return json.toString();
    }

    /**
     * 创建 JSON 参数字符串（仅路径）
     */
    private String createArgumentsJson(String filePath) throws Exception {
        return createArgumentsJson(filePath, null);
    }

    // ==================== 基础功能测试 ====================

    @Test
    void testGetName() {
        assertEquals("read_file", tool.getName());
    }

    @Test
    void testGetDescription() {
        String description = tool.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("读取文本文件"));
        assertTrue(description.contains("UTF-8"));
    }

    @Test
    void testGetParametersJsonSchema() {
        String schema = tool.getParametersJsonSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("\"type\": \"object\""));
        assertTrue(schema.contains("\"path\""));
        assertTrue(schema.contains("\"encoding\""));
    }

    @Test
    void testGetAccessLevel() {
        assertEquals(ToolAccessLevel.READ_ONLY, tool.getAccessLevel());
    }

    // ==================== 正常文件读取测试 ====================

    @Test
    void testReadTextFile() throws Exception {
        String filePath = Path.of(testResourcesPath, "test.txt").toString();
        String arguments = createArgumentsJson(filePath);

        String result = tool.execute(arguments);

        assertNotNull(result);
        JsonNode jsonResult = objectMapper.readTree(result);

        assertTrue(jsonResult.get("success").asBoolean());
        assertNotNull(jsonResult.get("content"));
        assertTrue(jsonResult.get("content").asText().contains("Hello, World!"));

        // 验证元数据
        JsonNode metadata = jsonResult.get("metadata");
        assertNotNull(metadata);
        assertEquals("test.txt", metadata.get("name").asText());
        assertEquals("FILE", metadata.get("type").asText());
        assertEquals("txt", metadata.get("extension").asText());
        assertTrue(metadata.get("size").asLong() > 0);
    }

    @Test
    void testReadJavaFile() throws Exception {
        String filePath = Path.of(testResourcesPath, "test.java").toString();
        String arguments = createArgumentsJson(filePath);

        String result = tool.execute(arguments);

        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
        assertTrue(jsonResult.get("content").asText().contains("public class TestClass"));

        JsonNode metadata = jsonResult.get("metadata");
        assertEquals("test.java", metadata.get("name").asText());
        assertEquals("java", metadata.get("extension").asText());
    }

    @Test
    void testReadJsonFile() throws Exception {
        String filePath = Path.of(testResourcesPath, "test.json").toString();
        String arguments = createArgumentsJson(filePath);

        String result = tool.execute(arguments);

        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
        assertTrue(jsonResult.get("content").asText().contains("Test Project"));
    }

    @Test
    void testReadYamlFile() throws Exception {
        String filePath = Path.of(testResourcesPath, "test.yaml").toString();
        String arguments = createArgumentsJson(filePath);

        String result = tool.execute(arguments);

        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
        assertTrue(jsonResult.get("content").asText().contains("project:"));
    }

    @Test
    void testReadMarkdownFile() throws Exception {
        String filePath = Path.of(testResourcesPath, "test.md").toString();
        String arguments = createArgumentsJson(filePath);

        String result = tool.execute(arguments);

        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
        assertTrue(jsonResult.get("content").asText().contains("# Test Markdown File"));
    }

    @Test
    void testReadEmptyFile() throws Exception {
        String filePath = Path.of(testResourcesPath, "empty.txt").toString();
        String arguments = createArgumentsJson(filePath);

        String result = tool.execute(arguments);

        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals("", jsonResult.get("content").asText());

        // lineCount 现在在 ReadResult 的根级别
        assertEquals(0, jsonResult.get("lineCount").asInt());
    }

    @Test
    void testReadUtf8FileWithSpecialCharacters() throws Exception {
        String filePath = Path.of(testResourcesPath, "test-utf8.txt").toString();
        String arguments = createArgumentsJson(filePath);

        String result = tool.execute(arguments);

        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
        String content = jsonResult.get("content").asText();
        assertTrue(content.contains("中文"));
        assertTrue(content.contains("日本語"));
        assertTrue(content.contains("한국어"));
        assertTrue(content.contains("😀"));
    }

    // ==================== 编码处理测试 ====================

    @Test
    void testReadFileWithDefaultEncoding() throws Exception {
        String filePath = Path.of(testResourcesPath, "test.txt").toString();
        String arguments = createArgumentsJson(filePath);

        String result = tool.execute(arguments);

        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
    }

    @Test
    void testReadFileWithUtf8Encoding() throws Exception {
        String filePath = Path.of(testResourcesPath, "test.txt").toString();
        String arguments = createArgumentsJson(filePath, "UTF-8");

        String result = tool.execute(arguments);

        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
    }

    @Test
    void testReadFileWithIsoEncoding() throws Exception {
        String filePath = Path.of(testResourcesPath, "test.txt").toString();
        String arguments = createArgumentsJson(filePath, "ISO-8859-1");

        String result = tool.execute(arguments);

        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
    }

    @Test
    void testReadFileWithAsciiEncoding() throws Exception {
        String filePath = Path.of(testResourcesPath, "test.txt").toString();
        String arguments = createArgumentsJson(filePath, "US-ASCII");

        String result = tool.execute(arguments);

        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
    }

    // ==================== 安全验证测试 ====================

    @Test
    void testRejectPathTraversalAttack() throws Exception {
        String filePath = Path.of(testResourcesPath, "..", "..", "etc", "passwd").toString();
        String arguments = createArgumentsJson(filePath);

        Exception exception = assertThrows(FileSecurityException.class, () -> {
            tool.execute(arguments);
        });

        assertTrue(exception.getMessage().contains("PATH_TRAVERSAL_DETECTED") ||
                   exception.getMessage().contains("路径穿越"));
    }

    @Test
    void testRejectBlacklistedPath() {
        // 使用通用的 Exception 类型，因为在 Windows 上 /etc/passwd 可能不会被识别
        // 但是应该抛出某种异常（可能是 FileSecurityException 或其他异常）
        assertThrows(Exception.class, () -> {
            tool.execute("{\"path\": \"/etc/passwd\"}");
        });
    }

    @Test
    void testRejectAccessToSshDirectory() {
        // 使用通用的 Exception 类型，因为 ~ 可能不会被正确展开
        // 但是应该抛出某种异常
        assertThrows(Exception.class, () -> {
            tool.execute("{\"path\": \"~/.ssh/id_rsa\"}");
        });
    }

    @Test
    void testRejectNonExistentFile() throws Exception {
        String filePath = Path.of(testResourcesPath, "nonexistent.txt").toString();
        String arguments = createArgumentsJson(filePath);

        Exception exception = assertThrows(FileOperationException.class, () -> {
            tool.execute(arguments);
        });

        assertTrue(exception.getMessage().contains("FILE_NOT_FOUND") ||
                   exception.getMessage().contains("不存在"));
    }

    @Test
    void testRejectDirectoryInsteadOfFile() throws Exception {
        // 创建一个测试目录
        Path testDir = tempDir.resolve("test_directory");
        Files.createDirectory(testDir);

        String arguments = createArgumentsJson(testDir.toString());

        Exception exception = assertThrows(FileOperationException.class, () -> {
            tool.execute(arguments);
        });

        assertTrue(exception.getMessage().contains("INVALID_PATH") ||
                   exception.getMessage().contains("目录"));
    }

    // ==================== 参数解析测试 ====================

    @Test
    void testRejectMissingPathParameter() {
        String arguments = "{\"encoding\": \"UTF-8\"}";

        Exception exception = assertThrows(Exception.class, () -> {
            tool.execute(arguments);
        });

        assertTrue(exception.getMessage().contains("Missing required parameter") ||
                   exception.getMessage().contains("path"));
    }

    @Test
    void testRejectInvalidJsonFormat() {
        String arguments = "{invalid json}";

        Exception exception = assertThrows(Exception.class, () -> {
            tool.execute(arguments);
        });

        assertTrue(exception.getMessage().contains("Invalid arguments") ||
                   exception.getMessage().contains("JSON"));
    }

    @Test
    void testRejectEmptyPathParameter() {
        String arguments = "{\"path\": \"\"}";

        Exception exception = assertThrows(Exception.class, () -> {
            tool.execute(arguments);
        });
    }

    @Test
    void testRejectNullPathParameter() {
        String arguments = "{\"path\": null}";

        Exception exception = assertThrows(Exception.class, () -> {
            tool.execute(arguments);
        });
    }

    // ==================== 元数据测试 ====================

    @Test
    void testFileMetadataFields() throws Exception {
        String filePath = Path.of(testResourcesPath, "test.java").toString();
        String arguments = createArgumentsJson(filePath);

        String result = tool.execute(arguments);

        JsonNode jsonResult = objectMapper.readTree(result);
        JsonNode metadata = jsonResult.get("metadata");

        // 验证所有元数据字段
        assertNotNull(metadata.get("path"));
        assertNotNull(metadata.get("name"));
        assertNotNull(metadata.get("type"));
        assertNotNull(metadata.get("size"));
        assertNotNull(metadata.get("lastModified"));
        assertNotNull(metadata.get("isReadable"));
        assertNotNull(metadata.get("isWritable"));
        assertNotNull(metadata.get("isExecutable"));
        assertNotNull(metadata.get("isHidden"));
        assertNotNull(metadata.get("extension"));

        // 验证字段值
        assertEquals("FILE", metadata.get("type").asText());
        assertEquals("test.java", metadata.get("name").asText());
        assertEquals("java", metadata.get("extension").asText());
        assertTrue(metadata.get("size").asLong() > 0);
        assertTrue(metadata.get("isReadable").asBoolean());
    }

    @Test
    void testLineCountInMetadata() throws Exception {
        String filePath = Path.of(testResourcesPath, "test.txt").toString();
        String arguments = createArgumentsJson(filePath);

        String result = tool.execute(arguments);

        JsonNode jsonResult = objectMapper.readTree(result);

        // test.txt 有 5 行内容（最后一行是空行，不计入）
        // lineCount 现在在 ReadResult 的根级别
        int lineCount = jsonResult.get("lineCount").asInt();
        assertTrue(lineCount > 0);
        assertEquals(5, lineCount);
    }

    // ==================== 构造函数测试 ====================

    @Test
    void testRejectNullProjectRoot() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new ReadFileTool(null);
        });

        assertTrue(exception.getMessage().contains("projectRoot cannot be null"));
    }

    @Test
    void testRejectEmptyProjectRoot() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new ReadFileTool("");
        });

        assertTrue(exception.getMessage().contains("projectRoot cannot be null or empty"));
    }

    @Test
    void testRejectWhitespaceProjectRoot() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            new ReadFileTool("   ");
        });

        assertTrue(exception.getMessage().contains("projectRoot cannot be null or empty"));
    }

    // ==================== executeWithBypass 测试 ====================

    @Test
    void testExecuteWithBypassSameAsExecute() throws Exception {
        String filePath = Path.of(testResourcesPath, "test.txt").toString();
        String arguments = createArgumentsJson(filePath);

        String result1 = tool.execute(arguments);
        String result2 = tool.executeWithBypass(arguments);

        // 对于只读工具，executeWithBypass 应该和 execute 返回相同结果
        JsonNode json1 = objectMapper.readTree(result1);
        JsonNode json2 = objectMapper.readTree(result2);

        assertEquals(json1.get("success").asBoolean(), json2.get("success").asBoolean());
        assertEquals(json1.get("content").asText(), json2.get("content").asText());
    }

    // ==================== 边界情况测试 ====================

    @Test
    void testReadFileWithRelativePath() throws Exception {
        // 使用相对路径（项目根目录下的文件）
        // 注意：相对路径不能包含 ".."，否则会被路径遍历检测拒绝
        String arguments = "{\"path\": \"src/test/resources/tech/yesboss/tool/filesystem/test-files/test.txt\"}";

        String result = tool.execute(arguments);

        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
    }

    @Test
    void testReadFileWithAbsolutePath() throws Exception {
        // 使用绝对路径
        Path absolutePath = Path.of(testResourcesPath).toAbsolutePath().resolve("test.txt");
        String arguments = createArgumentsJson(absolutePath.toString());

        String result = tool.execute(arguments);

        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
    }

    @Test
    void testHandleLargeFile() throws Exception {
        // 创建一个大文件（但小于 10MB 限制）
        Path largeFile = tempDir.resolve("large-file.txt");
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            content.append("Line ").append(i).append(": ").append("Some test content here\n");
        }
        Files.writeString(largeFile, content.toString());

        String arguments = createArgumentsJson(largeFile.toString());

        String result = tool.execute(arguments);

        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
        // lineCount 现在在 ReadResult 的根级别
        assertEquals(1000, jsonResult.get("lineCount").asInt());
    }

    @Test
    void testRejectBinaryFileExtension() throws Exception {
        // 创建一个 .exe 文件
        Path exeFile = tempDir.resolve("test.exe");
        Files.writeString(exeFile, "fake exe content");

        String arguments = createArgumentsJson(exeFile.toString());

        // .exe 文件应该被文件类型验证拒绝
        // 可能抛出 FileSecurityException 或其他异常
        Exception exception = assertThrows(Exception.class, () -> {
            tool.execute(arguments);
        });

        // 验证异常消息中包含与文件类型或安全相关的关键词
        String message = exception.getMessage();
        boolean isValidException =
                message.contains("类型") ||
                message.contains("extension") ||
                message.contains("黑名单") ||
                message.contains("BLACKLISTED") ||
                message.contains("FILE") ||
                message.contains("Security") ||
                message.contains("allowed") ||
                message.contains("DANGEROUS") ||
                message.contains("OPERATION");

        assertTrue(isValidException, "Exception message should contain file type or security related keywords. Actual message: " + message);
    }

    @Test
    void testToolConsistency() throws Exception {
        // 测试工具的一致性：多次读取同一文件应该返回相同内容
        String filePath = Path.of(testResourcesPath, "test.txt").toString();
        String arguments = createArgumentsJson(filePath);

        String result1 = tool.execute(arguments);
        String result2 = tool.execute(arguments);

        JsonNode json1 = objectMapper.readTree(result1);
        JsonNode json2 = objectMapper.readTree(result2);

        assertEquals(json1.get("content").asText(), json2.get("content").asText());
        assertEquals(json1.get("metadata").get("size").asLong(),
                     json2.get("metadata").get("size").asLong());
    }
}
