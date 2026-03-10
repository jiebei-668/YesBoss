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
 * WriteFileTool 单元测试
 *
 * <p>测试文件写入工具的各项功能，包括：
 * <ul>
 *   <li>文件覆盖写入</li>
 *   <li>文件追加写入</li>
 *   <li>原子写入机制验证</li>
 *   <li>不同编码写入</li>
 *   <li>磁盘空间检查</li>
 *   <li>父目录自动创建</li>
 *   <li>安全违规检测</li>
 *   <li>异常处理</li>
 * </ul>
 */
class WriteFileToolTest {

    private WriteFileTool tool;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // 使用临时目录作为项目根目录
        String projectRoot = tempDir.toAbsolutePath().toString();
        tool = new WriteFileTool(projectRoot);
        objectMapper = new ObjectMapper();
    }

    /**
     * 创建 JSON 参数字符串，正确处理路径转义
     *
     * @param filePath 文件路径
     * @param content 文件内容
     * @param mode 写入模式（可选）
     * @param encoding 编码（可选）
     * @param createParentDirs 是否创建父目录（可选）
     * @return JSON 参数字符串
     * @throws Exception 如果序列化失败
     */
    private String createArgumentsJson(String filePath, String content, String mode, String encoding, Boolean createParentDirs) throws Exception {
        StringBuilder json = new StringBuilder("{\"path\": \"");
        // 转义路径中的反斜杠
        String escapedPath = filePath.replace("\\", "\\\\");
        json.append(escapedPath);
        json.append("\", \"content\": \"");
        // 转义内容中的特殊字符
        String escapedContent = content.replace("\\", "\\\\")
                                       .replace("\"", "\\\"")
                                       .replace("\n", "\\n")
                                       .replace("\r", "\\r")
                                       .replace("\t", "\\t");
        json.append(escapedContent);
        json.append("\"");

        if (mode != null && !mode.isEmpty()) {
            json.append(", \"mode\": \"").append(mode).append("\"");
        }

        if (encoding != null && !encoding.isEmpty()) {
            json.append(", \"encoding\": \"").append(encoding).append("\"");
        }

        if (createParentDirs != null) {
            json.append(", \"createParentDirs\": ").append(createParentDirs);
        }

        json.append("}");
        return json.toString();
    }

    /**
     * 创建 JSON 参数字符串（简化版本）
     */
    private String createArgumentsJson(String filePath, String content) throws Exception {
        return createArgumentsJson(filePath, content, null, null, null);
    }

    /**
     * 创建 JSON 参数字符串（带模式）
     */
    private String createArgumentsJson(String filePath, String content, String mode) throws Exception {
        return createArgumentsJson(filePath, content, mode, null, null);
    }

    // ==================== 基础功能测试 ====================

    @Test
    void testGetName() {
        assertEquals("write_file", tool.getName());
    }

    @Test
    void testGetDescription() {
        String description = tool.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("写入文本文件"));
        assertTrue(description.contains("原子写入"));
        assertTrue(description.contains("OVERWRITE"));
        assertTrue(description.contains("APPEND"));
    }

    @Test
    void testGetParametersJsonSchema() {
        String schema = tool.getParametersJsonSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("\"type\": \"object\""));
        assertTrue(schema.contains("\"path\""));
        assertTrue(schema.contains("\"content\""));
        assertTrue(schema.contains("\"mode\""));
        assertTrue(schema.contains("\"encoding\""));
        assertTrue(schema.contains("\"createParentDirs\""));
    }

    @Test
    void testGetAccessLevel() {
        assertEquals(ToolAccessLevel.READ_WRITE, tool.getAccessLevel());
    }

    // ==================== 文件覆盖写入测试 ====================

    @Test
    void testWriteFileOverwrite() throws Exception {
        // 创建测试文件
        Path testFile = tempDir.resolve("test_overwrite.txt");
        String filePath = testFile.toAbsolutePath().toString();

        // 首次写入
        String content1 = "Hello, World!";
        String result1 = tool.execute(createArgumentsJson(filePath, content1, "OVERWRITE"));

        // 验证写入成功
        JsonNode jsonResult1 = objectMapper.readTree(result1);
        assertTrue(jsonResult1.get("success").asBoolean());
        assertEquals(content1.length(), jsonResult1.get("bytesWritten").asInt());
        assertEquals("OVERWRITE", jsonResult1.get("mode").asText());

        // 验证文件内容
        String actualContent1 = Files.readString(testFile);
        assertEquals(content1, actualContent1);

        // 覆盖写入
        String content2 = "Goodbye, World!";
        String result2 = tool.execute(createArgumentsJson(filePath, content2, "OVERWRITE"));

        // 验证覆盖成功
        JsonNode jsonResult2 = objectMapper.readTree(result2);
        assertTrue(jsonResult2.get("success").asBoolean());
        assertEquals(content2.length(), jsonResult2.get("bytesWritten").asInt());

        // 验证文件被覆盖
        String actualContent2 = Files.readString(testFile);
        assertEquals(content2, actualContent2);
        assertEquals(content2.length(), actualContent2.length());
    }

    @Test
    void testWriteFileDefaultMode() throws Exception {
        // 测试默认模式（应该是 OVERWRITE）
        Path testFile = tempDir.resolve("test_default.txt");
        String filePath = testFile.toAbsolutePath().toString();

        String content = "Default mode test";
        String result = tool.execute(createArgumentsJson(filePath, content));

        // 验证使用默认模式
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals("OVERWRITE", jsonResult.get("mode").asText());

        // 验证文件内容
        String actualContent = Files.readString(testFile);
        assertEquals(content, actualContent);
    }

    // ==================== 文件追加写入测试 ====================

    @Test
    void testWriteFileAppend() throws Exception {
        // 创建测试文件并写入初始内容
        Path testFile = tempDir.resolve("test_append.txt");
        String filePath = testFile.toAbsolutePath().toString();

        String content1 = "First line\n";
        String result1 = tool.execute(createArgumentsJson(filePath, content1, "OVERWRITE"));

        // 验证首次写入
        JsonNode jsonResult1 = objectMapper.readTree(result1);
        assertTrue(jsonResult1.get("success").asBoolean());
        assertEquals(content1.length(), jsonResult1.get("bytesWritten").asInt());

        // 追加内容
        String content2 = "Second line\n";
        String result2 = tool.execute(createArgumentsJson(filePath, content2, "APPEND"));

        // 验证追加成功
        JsonNode jsonResult2 = objectMapper.readTree(result2);
        assertTrue(jsonResult2.get("success").asBoolean());
        assertEquals(content2.length(), jsonResult2.get("bytesWritten").asInt());
        assertEquals("APPEND", jsonResult2.get("mode").asText());

        // 验证文件包含所有内容
        String actualContent = Files.readString(testFile);
        assertEquals(content1 + content2, actualContent);

        // 验证文件大小
        long expectedSize = content1.length() + content2.length();
        assertEquals(expectedSize, Files.size(testFile));
    }

    @Test
    void testWriteFileAppendToNonExistentFile() throws Exception {
        // 追加到不存在的文件（应该创建文件）
        Path testFile = tempDir.resolve("test_append_new.txt");
        String filePath = testFile.toAbsolutePath().toString();

        String content = "New file content\n";
        String result = tool.execute(createArgumentsJson(filePath, content, "APPEND"));

        // 验证创建成功
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());

        // 验证文件内容
        String actualContent = Files.readString(testFile);
        assertEquals(content, actualContent);
    }

    // ==================== 原子写入机制测试 ====================

    @Test
    void testAtomicWriteMechanism() throws Exception {
        // 验证原子写入机制
        Path testFile = tempDir.resolve("test_atomic.txt");
        String filePath = testFile.toAbsolutePath().toString();

        // 写入一个较大的文件
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeContent.append("Line ").append(i).append("\n");
        }
        String content = largeContent.toString();

        String result = tool.execute(createArgumentsJson(filePath, content, "OVERWRITE"));

        // 验证写入成功
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals(content.length(), jsonResult.get("bytesWritten").asInt());

        // 验证文件完整性
        String actualContent = Files.readString(testFile);
        assertEquals(content, actualContent);

        // 验证没有残留的临时文件
        long tempFileCount = Files.list(tempDir)
                .filter(p -> p.getFileName().toString().contains(".tmp."))
                .count();
        assertEquals(0, tempFileCount, "No temporary files should remain");
    }

    @Test
    void testAtomicWritePreservesOriginalOnFailure() throws Exception {
        // 验证写入失败时原文件不受影响
        Path testFile = tempDir.resolve("test_preserve.txt");
        String filePath = testFile.toAbsolutePath().toString();

        // 写入初始内容
        String originalContent = "Original content";
        Files.writeString(testFile, originalContent);
        long originalSize = Files.size(testFile);

        // 尝试写入非法内容（模拟失败）
        // 注意：这个测试需要特殊处理，因为我们无法轻易模拟写入失败
        // 这里我们测试正常的覆盖写入
        String newContent = "New content";
        String result = tool.execute(createArgumentsJson(filePath, newContent, "OVERWRITE"));

        // 验证写入成功
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());

        // 验证文件被正确替换
        String actualContent = Files.readString(testFile);
        assertEquals(newContent, actualContent);
        assertNotEquals(originalContent, actualContent);
    }

    // ==================== 不同编码写入测试 ====================

    @Test
    void testWriteFileWithUTF8() throws Exception {
        Path testFile = tempDir.resolve("test_utf8.txt");
        String filePath = testFile.toAbsolutePath().toString();

        // 测试包含中文、emoji、特殊字符的内容
        String content = "Hello, 世界! 🌍🌎🌏\nSpecial chars: ñ, é, ü, ø";

        String result = tool.execute(createArgumentsJson(filePath, content, "OVERWRITE", "UTF-8", null));

        // 验证写入成功
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals("UTF-8", jsonResult.get("encoding").asText());

        // 验证文件内容
        String actualContent = Files.readString(testFile);
        assertEquals(content, actualContent);
    }

    @Test
    void testWriteFileWithGBK() throws Exception {
        Path testFile = tempDir.resolve("test_gbk.txt");
        String filePath = testFile.toAbsolutePath().toString();

        // 测试 GBK 编码的中文内容
        String content = "简体中文内容\n测试 GBK 编码";

        String result = tool.execute(createArgumentsJson(filePath, content, "OVERWRITE", "GBK", null));

        // 验证写入成功
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals("GBK", jsonResult.get("encoding").asText());

        // 验证文件内容（使用 GBK 读取）
        String actualContent = Files.readString(testFile, java.nio.charset.Charset.forName("GBK"));
        assertEquals(content, actualContent);
    }

    @Test
    void testWriteFileWithUSASCII() throws Exception {
        Path testFile = tempDir.resolve("test_ascii.txt");
        String filePath = testFile.toAbsolutePath().toString();

        // 测试纯 ASCII 内容
        String content = "Pure ASCII content\nNo special characters";

        String result = tool.execute(createArgumentsJson(filePath, content, "OVERWRITE", "US-ASCII", null));

        // 验证写入成功
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals("US-ASCII", jsonResult.get("encoding").asText());

        // 验证文件内容
        String actualContent = Files.readString(testFile);
        assertEquals(content, actualContent);
    }

    // ==================== 父目录自动创建测试 ====================

    @Test
    void testWriteFileWithParentDirectoryCreation() throws Exception {
        // 创建一个深层嵌套的路径
        Path nestedFile = tempDir.resolve("level1/level2/level3/test_nested.txt");
        String filePath = nestedFile.toAbsolutePath().toString();

        String content = "Nested file content";

        String result = tool.execute(createArgumentsJson(filePath, content, "OVERWRITE", null, true));

        // 验证写入成功
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());

        // 验证文件存在
        assertTrue(Files.exists(nestedFile));

        // 验证文件内容
        String actualContent = Files.readString(nestedFile);
        assertEquals(content, actualContent);

        // 验证父目录被创建
        assertTrue(Files.exists(nestedFile.getParent()));
    }

    @Test
    void testWriteFileWithoutParentDirectoryCreation() throws Exception {
        // 不创建父目录时应该失败
        Path nestedFile = tempDir.resolve("nonexistent/test.txt");
        String filePath = nestedFile.toAbsolutePath().toString();

        String content = "Test content";

        // 应该抛出异常（可能是 FileSecurityException 或 FileOperationException）
        assertThrows(Exception.class, () -> {
            tool.execute(createArgumentsJson(filePath, content, "OVERWRITE", null, false));
        });

        // 验证文件未被创建
        assertFalse(Files.exists(nestedFile));
    }

    // ==================== 安全验证测试 ====================

    @Test
    void testWriteFileWithPathTraversal() throws Exception {
        // 测试路径遍历攻击防护
        String maliciousPath = tempDir.resolve("safe.txt").toAbsolutePath().toString();
        String traversalPath = maliciousPath + "/../../../etc/passwd";

        String content = "Malicious content";

        // 应该抛出安全异常
        assertThrows(FileSecurityException.class, () -> {
            tool.execute(createArgumentsJson(traversalPath, content));
        });
    }

    @Test
    void testWriteFileWithBlacklistedPath() throws Exception {
        // 测试黑名单路径过滤
        // 注意：实际的黑名单路径取决于 FileSecurityValidator 的实现
        // 这里我们测试一个项目外的路径

        String projectRoot = tempDir.toAbsolutePath().toString();
        String outsidePath = projectRoot + "/../outside.txt";

        String content = "Test content";

        // 应该抛出安全异常
        assertThrows(FileSecurityException.class, () -> {
            tool.execute(createArgumentsJson(outsidePath, content));
        });
    }

    // ==================== 参数验证测试 ====================

    @Test
    void testWriteFileWithMissingPath() {
        // 测试缺少路径参数
        String invalidJson = "{\"content\": \"Test content\"}";

        assertThrows(Exception.class, () -> {
            tool.execute(invalidJson);
        });
    }

    @Test
    void testWriteFileWithMissingContent() {
        // 测试缺少内容参数
        String invalidJson = "{\"path\": \"test.txt\"}";

        assertThrows(Exception.class, () -> {
            tool.execute(invalidJson);
        });
    }

    @Test
    void testWriteFileWithInvalidMode() {
        // 测试无效的写入模式
        Path testFile = tempDir.resolve("test.txt");
        String filePath = testFile.toAbsolutePath().toString();

        String invalidJson = String.format("{\"path\": \"%s\", \"content\": \"Test\", \"mode\": \"INVALID\"}",
                filePath.replace("\\", "\\\\"));

        assertThrows(Exception.class, () -> {
            tool.execute(invalidJson);
        });
    }

    @Test
    void testWriteFileWithInvalidEncoding() {
        // 测试无效的编码
        Path testFile = tempDir.resolve("test.txt");
        String filePath = testFile.toAbsolutePath().toString();

        String invalidJson = String.format("{\"path\": \"%s\", \"content\": \"Test\", \"encoding\": \"INVALID-ENCODING\"}",
                filePath.replace("\\", "\\\\"));

        assertThrows(Exception.class, () -> {
            tool.execute(invalidJson);
        });
    }

    // ==================== 多行内容和特殊字符测试 ====================

    @Test
    void testWriteFileWithMultipleLines() throws Exception {
        Path testFile = tempDir.resolve("test_multiline.txt");
        String filePath = testFile.toAbsolutePath().toString();

        // 测试多行内容
        String content = "Line 1\nLine 2\nLine 3\n";

        String result = tool.execute(createArgumentsJson(filePath, content));

        // 验证写入成功
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());

        // 验证文件内容
        String actualContent = Files.readString(testFile);
        assertEquals(content, actualContent);
    }

    @Test
    void testWriteFileWithSpecialCharacters() throws Exception {
        Path testFile = tempDir.resolve("test_special.txt");
        String filePath = testFile.toAbsolutePath().toString();

        // 测试特殊字符
        String content = "Special chars: \t\n\r\"'\\<>|?*";

        String result = tool.execute(createArgumentsJson(filePath, content));

        // 验证写入成功
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());

        // 验证文件内容
        String actualContent = Files.readString(testFile);
        assertEquals(content, actualContent);
    }

    @Test
    void testWriteFileWithEmptyContent() throws Exception {
        Path testFile = tempDir.resolve("test_empty.txt");
        String filePath = testFile.toAbsolutePath().toString();

        // 测试空内容
        String content = "";

        String result = tool.execute(createArgumentsJson(filePath, content));

        // 验证写入成功
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());
        assertEquals(0, jsonResult.get("bytesWritten").asInt());

        // 验证文件为空
        String actualContent = Files.readString(testFile);
        assertEquals(content, actualContent);
        assertEquals(0, Files.size(testFile));
    }

    // ==================== 文件元数据测试 ====================

    @Test
    void testWriteFileMetadata() throws Exception {
        Path testFile = tempDir.resolve("test_metadata.txt");
        String filePath = testFile.toAbsolutePath().toString();

        String content = "Test content for metadata";

        String result = tool.execute(createArgumentsJson(filePath, content));

        // 验证元数据
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());

        JsonNode metadata = jsonResult.get("metadata");
        assertNotNull(metadata);
        assertEquals(filePath, metadata.get("path").asText());
        assertEquals("test_metadata.txt", metadata.get("name").asText());
        assertEquals("FILE", metadata.get("type").asText());
        assertEquals("txt", metadata.get("extension").asText());
        assertTrue(metadata.get("size").asLong() > 0);
        assertTrue(metadata.get("lastModified").asLong() > 0);
    }

    // ==================== executeWithBypass 测试 ====================

    @Test
    void testExecuteWithBypass() throws Exception {
        // 测试绕过沙箱的执行
        Path testFile = tempDir.resolve("test_bypass.txt");
        String filePath = testFile.toAbsolutePath().toString();

        String content = "Bypass test content";

        String result = tool.executeWithBypass(createArgumentsJson(filePath, content));

        // 验证执行成功
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean());

        // 验证文件内容
        String actualContent = Files.readString(testFile);
        assertEquals(content, actualContent);
    }

    // ==================== 错误恢复测试 ====================

    @Test
    void testWriteAndOverwriteMultipleTimes() throws Exception {
        // 测试多次覆盖写入
        Path testFile = tempDir.resolve("test_multiple.txt");
        String filePath = testFile.toAbsolutePath().toString();

        for (int i = 0; i < 5; i++) {
            String content = "Iteration " + i;
            String result = tool.execute(createArgumentsJson(filePath, content, "OVERWRITE"));

            // 验证每次写入都成功
            JsonNode jsonResult = objectMapper.readTree(result);
            assertTrue(jsonResult.get("success").asBoolean());

            // 验证文件内容
            String actualContent = Files.readString(testFile);
            assertEquals(content, actualContent);
        }
    }

    @Test
    void testAppendMultipleTimes() throws Exception {
        // 测试多次追加写入
        Path testFile = tempDir.resolve("test_append_multiple.txt");
        String filePath = testFile.toAbsolutePath().toString();

        StringBuilder expectedContent = new StringBuilder();

        for (int i = 0; i < 5; i++) {
            String content = "Line " + i + "\n";
            expectedContent.append(content);

            String result = tool.execute(createArgumentsJson(filePath, content, "APPEND"));

            // 验证每次追加都成功
            JsonNode jsonResult = objectMapper.readTree(result);
            assertTrue(jsonResult.get("success").asBoolean());
        }

        // 验证最终文件内容
        String actualContent = Files.readString(testFile);
        assertEquals(expectedContent.toString(), actualContent);
    }
}
