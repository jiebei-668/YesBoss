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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ListDirectoryTool 测试类
 *
 * <p>测试目录列表工具的各项功能，包括：</p>
 * <ul>
 *   <li>单层目录列表</li>
 *   <li>递归目录列表</li>
 *   <li>文件过滤</li>
 *   <li>深度限制</li>
 *   <li>分页支持</li>
 *   <li>安全验证</li>
 * </ul>
 */
class ListDirectoryToolTest {

    private ListDirectoryTool tool;
    private ObjectMapper objectMapper;
    private String formattedTempDir;  // 格式化为正斜杠的临时目录路径

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        tool = new ListDirectoryTool(tempDir.toString());
        objectMapper = new ObjectMapper();
    }

    /**
     * 创建相对路径的 JSON 参数
     * 避免绝对路径的转义问题
     */
    private String pathJson(String relativePath) {
        return "{\"path\": \"" + relativePath + "\"}";
    }

    /**
     * 构建列表目录的 JSON 参数
     */
    private String buildListJson(Path path) {
        return "{\"path\": \"" + formatPathForJson(path) + "\"}";
    }

    /**
     * 构建列表目录的 JSON 参数（带递归）
     */
    private String buildListJson(Path path, boolean recursive) {
        return "{\"path\": \"" + formatPathForJson(path) + "\", \"recursive\": " + recursive + "}";
    }

    /**
     * 构建列表目录的 JSON 参数（带递归和深度）
     */
    private String buildListJson(Path path, boolean recursive, int depth) {
        return "{\"path\": \"" + formatPathForJson(path) + "\", \"recursive\": " + recursive + ", \"depth\": " + depth + "}";
    }

    /**
     * 构建列表目录的 JSON 参数（带模式）
     */
    private String buildListJson(Path path, String pattern) {
        return "{\"path\": \"" + formatPathForJson(path) + "\", \"pattern\": \"" + pattern + "\"}";
    }

    /**
     * 构建列表目录的 JSON 参数（带分页）
     */
    private String buildListJson(Path path, int limit, int offset) {
        return "{\"path\": \"" + formatPathForJson(path) + "\", \"limit\": " + limit + ", \"offset\": " + offset + "}";
    }

    /**
     * 格式化路径为 JSON 格式（处理反斜杠转义）
     */
    private String formatPathForJson(Path path) {
        return path.toString().replace("\\", "\\\\");
    }

    // ==================== 基础功能测试 ====================

    @Test
    void testGetName() {
        assertEquals("list_directory", tool.getName());
    }

    @Test
    void testGetDescription() {
        String description = tool.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("目录"));
        assertTrue(description.contains("递归"));
    }

    @Test
    void testGetParametersJsonSchema() {
        String schema = tool.getParametersJsonSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("path"));
        assertTrue(schema.contains("recursive"));
        assertTrue(schema.contains("depth"));
        assertTrue(schema.contains("pattern"));
        assertTrue(schema.contains("limit"));
        assertTrue(schema.contains("offset"));
    }

    @Test
    void testGetAccessLevel() {
        assertEquals(ToolAccessLevel.READ_ONLY, tool.getAccessLevel());
    }

    // ==================== 单层目录列表测试 ====================

    @Test
    void testListEmptyDirectory() throws Exception {
        // 创建空目录
        Path emptyDir = tempDir.resolve("empty");
        Files.createDirectory(emptyDir);

        // 列出目录
        String result = tool.execute("{\"path\": \"" + formatPathForJson(emptyDir) + "\"}");

        // 验证结果
        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.get("success").asBoolean());
        assertEquals(0, json.get("total").asInt());
        assertEquals(0, json.get("returned").asInt());
        assertTrue(json.get("items").isArray());
        assertEquals(0, json.get("items").size());
    }

    @Test
    void testListSingleLevelDirectory() throws Exception {
        // 创建测试目录结构
        Path testDir = tempDir.resolve("test");
        Files.createDirectory(testDir);

        Files.createFile(testDir.resolve("file1.txt"));
        Files.createFile(testDir.resolve("file2.java"));
        Files.createFile(testDir.resolve("file3.md"));

        Path subDir = testDir.resolve("subdir");
        Files.createDirectory(subDir);

        // 列出目录
        String result = tool.execute("{\"path\": \"" + formatPathForJson(testDir) + "\"}");

        // 验证结果
        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.get("success").asBoolean());
        assertEquals(4, json.get("total").asInt()); // 3 files + 1 directory

        JsonNode items = json.get("items");
        List<String> names = new ArrayList<>();
        items.forEach(node -> names.add(node.get("name").asText()));

        assertTrue(names.contains("file1.txt"));
        assertTrue(names.contains("file2.java"));
        assertTrue(names.contains("file3.md"));
        assertTrue(names.contains("subdir"));
    }

    @Test
    void testListDirectoryWithFileMetadata() throws Exception {
        // 创建测试文件
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello, World!");

        // 列出目录
        String result = tool.execute("{\"path\": \"" + formatPathForJson(tempDir) + "\"}");

        // 验证元数据
        JsonNode json = objectMapper.readTree(result);
        JsonNode items = json.get("items");

        boolean found = false;
        for (JsonNode item : items) {
            if ("test.txt".equals(item.get("name").asText())) {
                found = true;
                assertEquals("FILE", item.get("type").asText());
                assertTrue(item.get("size").asLong() > 0);
                assertEquals("txt", item.get("extension").asText());
                assertTrue(item.get("isReadable").asBoolean());
                break;
            }
        }

        assertTrue(found, "test.txt not found in directory listing");
    }

    // ==================== 递归目录列表测试 ====================

    @Test
    void testListDirectoryRecursive() throws Exception {
        // 创建嵌套目录结构
        Path rootDir = tempDir.resolve("root");
        Files.createDirectory(rootDir);

        Files.createFile(rootDir.resolve("file1.txt"));

        Path subDir1 = rootDir.resolve("dir1");
        Files.createDirectory(subDir1);
        Files.createFile(subDir1.resolve("file2.java"));

        Path subDir2 = subDir1.resolve("dir2");
        Files.createDirectory(subDir2);
        Files.createFile(subDir2.resolve("file3.md"));

        // 递归列出目录
        String result = tool.execute("{\"path\": \"" + formatPathForJson(rootDir) + "\", \"recursive\": true}");

        // 验证结果
        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.get("success").asBoolean());
        assertEquals(6, json.get("total").asInt()); // 3 files + 3 directories

        JsonNode items = json.get("items");
        List<String> names = new ArrayList<>();
        items.forEach(node -> names.add(node.get("name").asText()));

        assertTrue(names.contains("file1.txt"));
        assertTrue(names.contains("file2.java"));
        assertTrue(names.contains("file3.md"));
        assertTrue(names.contains("dir1"));
        assertTrue(names.contains("dir2"));
    }

    @Test
    void testListDirectoryWithDepthLimit() throws Exception {
        // 创建多层嵌套结构
        Path rootDir = tempDir.resolve("root");
        Files.createDirectory(rootDir);

        Path level1 = rootDir.resolve("level1");
        Files.createDirectory(level1);
        Files.createFile(level1.resolve("file1.txt"));

        Path level2 = level1.resolve("level2");
        Files.createDirectory(level2);
        Files.createFile(level2.resolve("file2.java"));

        Path level3 = level2.resolve("level3");
        Files.createDirectory(level3);
        Files.createFile(level3.resolve("file3.md"));

        // 递归深度限制为 2
        String result = tool.execute("{\"path\": \"" + formatPathForJson(rootDir) + "\", \"recursive\": true, \"depth\": 2}");

        // 验证结果
        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.get("success").asBoolean());

        // 应该只列出到 level2（不包括 level3）
        JsonNode items = json.get("items");
        List<String> names = new ArrayList<>();
        items.forEach(node -> names.add(node.get("name").asText()));

        assertTrue(names.contains("file1.txt"));
        assertTrue(names.contains("file2.java"));
        assertTrue(names.contains("level2"));
        assertFalse(names.contains("file3.md")); // level3 超出深度限制
    }

    // ==================== 文件过滤测试 ====================

    @Test
    void testListDirectoryWithPatternFilter() throws Exception {
        // 创建测试文件
        Path testDir = tempDir.resolve("test");
        Files.createDirectory(testDir);

        Files.createFile(testDir.resolve("file1.java"));
        Files.createFile(testDir.resolve("file2.java"));
        Files.createFile(testDir.resolve("file3.txt"));
        Files.createFile(testDir.resolve("file4.md"));

        // 过滤 Java 文件
        String result = tool.execute("{\"path\": \"" + formatPathForJson(testDir) + "\", \"pattern\": \"*.java\"}");

        // 验证结果
        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.get("success").asBoolean());
        assertEquals(2, json.get("total").asInt()); // 只有 2 个 Java 文件

        JsonNode items = json.get("items");
        items.forEach(node -> {
            String name = node.get("name").asText();
            assertTrue(name.endsWith(".java"));
        });
    }

    @Test
    void testListDirectoryWithWildcardPattern() throws Exception {
        // 创建测试文件
        Path testDir = tempDir.resolve("test");
        Files.createDirectory(testDir);

        Files.createFile(testDir.resolve("test1.txt"));
        Files.createFile(testDir.resolve("test2.txt"));
        Files.createFile(testDir.resolve("other.txt"));

        // 过滤 test*.txt
        String result = tool.execute("{\"path\": \"" + formatPathForJson(testDir) + "\", \"pattern\": \"test*.txt\"}");

        // 验证结果
        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.get("success").asBoolean());
        assertEquals(2, json.get("total").asInt()); // test1.txt 和 test2.txt

        JsonNode items = json.get("items");
        List<String> names = new ArrayList<>();
        items.forEach(node -> names.add(node.get("name").asText()));

        assertTrue(names.contains("test1.txt"));
        assertTrue(names.contains("test2.txt"));
        assertFalse(names.contains("other.txt"));
    }

    // ==================== 分页测试 ====================

    @Test
    void testListDirectoryWithPagination() throws Exception {
        // 创建多个文件
        Path testDir = tempDir.resolve("test");
        Files.createDirectory(testDir);

        for (int i = 1; i <= 20; i++) {
            Files.createFile(testDir.resolve("file" + i + ".txt"));
        }

        // 分页查询：limit=10, offset=5
        String result = tool.execute("{\"path\": \"" + formatPathForJson(testDir) + "\", \"limit\": 10, \"offset\": 5}");

        // 验证结果
        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.get("success").asBoolean());
        assertEquals(20, json.get("total").asInt()); // 总共 20 个文件
        assertEquals(10, json.get("returned").asInt()); // 返回 10 个
        assertTrue(json.get("truncated").asBoolean()); // 还有剩余
    }

    @Test
    void testListDirectoryWithOffsetBeyondTotal() throws Exception {
        // 创建少量文件
        Path testDir = tempDir.resolve("test");
        Files.createDirectory(testDir);

        Files.createFile(testDir.resolve("file1.txt"));
        Files.createFile(testDir.resolve("file2.txt"));

        // 偏移量超过总数
        String result = tool.execute("{\"path\": \"" + formatPathForJson(testDir) + "\", \"offset\": 10}");

        // 验证结果
        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.get("success").asBoolean());
        assertEquals(2, json.get("total").asInt());
        assertEquals(0, json.get("returned").asInt()); // 没有返回任何项
        assertFalse(json.get("truncated").asBoolean());
    }

    // ==================== 安全验证测试 ====================

    @Test
    void testRejectPathTraversalAttack() {
        // 尝试路径遍历攻击
        String maliciousPath = tempDir + "/../../../etc/passwd";

        assertThrows(FileSecurityException.class, () -> {
            tool.execute("{\"path\": \"" + maliciousPath + "\"}");
        });
    }

    @Test
    void testRejectBlacklistedPath() {
        // 尝试访问黑名单路径
        assertThrows(FileSecurityException.class, () -> {
            tool.execute("{\"path\": \"/etc/passwd\"}");
        });
    }

    @Test
    void testRejectNonExistentDirectory() {
        // 访问不存在的目录
        assertThrows(FileOperationException.class, () -> {
            tool.execute("{\"path\": \"/nonexistent/directory\"}");
        });
    }

    @Test
    void testRejectFileInsteadOfDirectory() throws Exception {
        // 创建文件而不是目录
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "test");

        // 尝试列出文件
        assertThrows(FileOperationException.class, () -> {
            tool.execute("{\"path\": \"" + formatPathForJson(testFile) + "\"}");
        });
    }

    // ==================== 参数解析测试 ====================

    @Test
    void testRejectMissingPathParameter() {
        assertThrows(Exception.class, () -> {
            tool.execute("{}");
        });
    }

    @Test
    void testRejectInvalidDepthTooSmall() {
        Path testDir = tempDir.resolve("test");
        assertThrows(Exception.class, () -> {
            tool.execute("{\"path\": \"" + testDir + "\", \"depth\": 0}");
        });
    }

    @Test
    void testRejectInvalidDepthTooLarge() {
        Path testDir = tempDir.resolve("test");
        assertThrows(Exception.class, () -> {
            tool.execute("{\"path\": \"" + testDir + "\", \"depth\": 100}");
        });
    }

    @Test
    void testRejectInvalidLimitTooSmall() {
        Path testDir = tempDir.resolve("test");
        assertThrows(Exception.class, () -> {
            tool.execute("{\"path\": \"" + testDir + "\", \"limit\": 0}");
        });
    }

    @Test
    void testRejectInvalidLimitTooLarge() {
        Path testDir = tempDir.resolve("test");
        assertThrows(Exception.class, () -> {
            tool.execute("{\"path\": \"" + testDir + "\", \"limit\": 100000}");
        });
    }

    @Test
    void testRejectInvalidNegativeOffset() {
        Path testDir = tempDir.resolve("test");
        assertThrows(Exception.class, () -> {
            tool.execute("{\"path\": \"" + testDir + "\", \"offset\": -1}");
        });
    }

    @Test
    void testRejectInvalidJsonFormat() {
        assertThrows(Exception.class, () -> {
            tool.execute("invalid json");
        });
    }

    // ==================== 构造函数测试 ====================

    @Test
    void testRejectNullProjectRoot() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ListDirectoryTool(null);
        });
    }

    @Test
    void testRejectEmptyProjectRoot() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ListDirectoryTool("");
        });
    }

    @Test
    void testRejectWhitespaceProjectRoot() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ListDirectoryTool("   ");
        });
    }

    // ==================== executeWithBypass 测试 ====================

    @Test
    void testExecuteWithBypassSameAsExecute() throws Exception {
        // 创建测试目录
        Path testDir = tempDir.resolve("test");
        Files.createDirectory(testDir);

        // execute 和 executeWithBypass 应该返回相同结果
        String result1 = tool.execute("{\"path\": \"" + formatPathForJson(testDir) + "\"}");
        String result2 = tool.executeWithBypass("{\"path\": \"" + formatPathForJson(testDir) + "\"}");

        assertEquals(result1, result2);
    }

    // ==================== 边界情况测试 ====================

    @Test
    void testListDirectoryWithRelativePath() throws Exception {
        // 创建测试目录
        Path testDir = tempDir.resolve("test");
        Files.createDirectory(testDir);
        Files.createFile(testDir.resolve("file.txt"));

        // 使用相对路径
        String result = tool.execute("{\"path\": \"test\"}");

        // 验证结果
        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.get("success").asBoolean());
        assertTrue(json.get("total").asInt() > 0);
    }

    @Test
    void testListDirectoryWithAbsolutePath() throws Exception {
        // 创建测试目录
        Path testDir = tempDir.resolve("test");
        Files.createDirectory(testDir);
        Files.createFile(testDir.resolve("file.txt"));

        // 使用绝对路径
        String result = tool.execute("{\"path\": \"" + formatPathForJson(testDir) + "\"}");

        // 验证结果
        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.get("success").asBoolean());
        assertTrue(json.get("total").asInt() > 0);
    }

    @Test
    void testListDirectoryWithMixedFilesAndDirectories() throws Exception {
        // 创建混合结构
        Path testDir = tempDir.resolve("test");
        Files.createDirectory(testDir);

        Files.createFile(testDir.resolve("file1.txt"));
        Files.createFile(testDir.resolve("file2.java"));

        Path subDir1 = testDir.resolve("dir1");
        Files.createDirectory(subDir1);

        Path subDir2 = testDir.resolve("dir2");
        Files.createDirectory(subDir2);

        Files.createFile(testDir.resolve("file3.md"));

        // 列出目录
        String result = tool.execute("{\"path\": \"" + formatPathForJson(testDir) + "\"}");

        // 验证结果
        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.get("success").asBoolean());
        assertEquals(5, json.get("total").asInt()); // 3 files + 2 directories

        JsonNode items = json.get("items");
        long fileCount = 0;
        long dirCount = 0;
        for (JsonNode item : items) {
            String type = item.get("type").asText();
            if ("FILE".equals(type)) {
                fileCount++;
            } else if ("DIRECTORY".equals(type)) {
                dirCount++;
            }
        }

        assertEquals(3, fileCount);
        assertEquals(2, dirCount);
    }

    @Test
    void testToolConsistency() throws Exception {
        // 创建测试目录
        Path testDir = tempDir.resolve("test");
        Files.createDirectory(testDir);
        Files.createFile(testDir.resolve("file.txt"));

        // 多次执行应该返回一致的结果
        String result1 = tool.execute("{\"path\": \"" + formatPathForJson(testDir) + "\"}");
        String result2 = tool.execute("{\"path\": \"" + formatPathForJson(testDir) + "\"}");

        JsonNode json1 = objectMapper.readTree(result1);
        JsonNode json2 = objectMapper.readTree(result2);

        assertEquals(json1.get("total").asInt(), json2.get("total").asInt());
        assertEquals(json1.get("returned").asInt(), json2.get("returned").asInt());
    }

    @Test
    void testListHiddenFiles() throws Exception {
        // 创建隐藏文件
        Path testDir = tempDir.resolve("test");
        Files.createDirectory(testDir);

        Files.createFile(testDir.resolve(".hidden"));
        Files.createFile(testDir.resolve("visible.txt"));

        // 列出目录
        String result = tool.execute("{\"path\": \"" + formatPathForJson(testDir) + "\"}");

        // 验证结果
        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.get("success").asBoolean());
        assertEquals(2, json.get("total").asInt()); // 包括隐藏文件

        JsonNode items = json.get("items");
        List<String> names = new ArrayList<>();
        items.forEach(node -> names.add(node.get("name").asText()));

        assertTrue(names.contains(".hidden"));
        assertTrue(names.contains("visible.txt"));
    }

    @Test
    void testDirectoryMetadata() throws Exception {
        // 创建目录
        Path testDir = tempDir.resolve("test");
        Files.createDirectory(testDir);

        // 列出目录
        String result = tool.execute("{\"path\": \"" + formatPathForJson(testDir) + "\"}");

        // 验证元数据
        JsonNode json = objectMapper.readTree(result);
        JsonNode items = json.get("items");

        boolean found = false;
        for (JsonNode item : items) {
            if ("test".equals(item.get("name").asText())) {
                found = true;
                assertEquals("DIRECTORY", item.get("type").asText());
                assertEquals(0, item.get("size").asLong()); // 目录大小为 0
                assertNull(item.get("extension")); // 目录没有扩展名
                assertTrue(item.get("isReadable").asBoolean());
                assertTrue(item.get("isExecutable").asBoolean()); // 目录通常是可执行的
                break;
            }
        }

        assertTrue(found, "test directory not found in listing");
    }
}
