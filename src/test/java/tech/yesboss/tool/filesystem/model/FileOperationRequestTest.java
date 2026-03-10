package tech.yesboss.tool.filesystem.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileOperationRequest 单元测试
 */
@DisplayName("FileOperationRequest 测试")
class FileOperationRequestTest {

    @Test
    @DisplayName("测试从 JSON 反序列化")
    void testFromJson() {
        String json = """
                {
                    "operation": "READ",
                    "path": "/test/file.txt",
                    "content": null,
                    "pattern": null,
                    "recursive": false,
                    "maxResults": 0
                }
                """;

        FileOperationRequest request = FileOperationRequest.fromJson(json);

        assertEquals(FileOperationRequest.Operation.READ, request.operation());
        assertEquals("/test/file.txt", request.path());
        assertFalse(request.recursive());
    }

    @Test
    @DisplayName("测试序列化为 JSON")
    void testToJson() {
        FileOperationRequest request = FileOperationRequest.forRead("/test/file.txt");
        String json = request.toJson();

        assertTrue(json.contains("\"operation\":\"READ\""));
        assertTrue(json.contains("\"path\":\"/test/file.txt\""));
    }

    @Test
    @DisplayName("测试验证 - 有效请求")
    void testValidate_ValidRequest() {
        FileOperationRequest request = FileOperationRequest.forRead("/test/file.txt");
        assertDoesNotThrow(request::validate);
    }

    @Test
    @DisplayName("测试验证 - 操作为空")
    void testValidate_NullOperation() {
        FileOperationRequest request = new FileOperationRequest(
                null, "/test/file.txt", null, null, false, 0
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                request::validate
        );

        assertTrue(exception.getMessage().contains("Operation cannot be null"));
    }

    @Test
    @DisplayName("测试验证 - 路径为空")
    void testValidate_EmptyPath() {
        FileOperationRequest request = new FileOperationRequest(
                FileOperationRequest.Operation.READ, "", null, null, false, 0
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                request::validate
        );

        assertTrue(exception.getMessage().contains("Path cannot be null or empty"));
    }

    @Test
    @DisplayName("测试验证 - WRITE 操作缺少内容")
    void testValidate_WriteMissingContent() {
        FileOperationRequest request = new FileOperationRequest(
                FileOperationRequest.Operation.WRITE, "/test/file.txt", null, null, false, 0
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                request::validate
        );

        assertTrue(exception.getMessage().contains("Content cannot be null for WRITE operation"));
    }

    @Test
    @DisplayName("测试验证 - SEARCH 操作缺少模式")
    void testValidate_SearchMissingPattern() {
        FileOperationRequest request = new FileOperationRequest(
                FileOperationRequest.Operation.SEARCH, "/test", null, null, true, 100
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                request::validate
        );

        assertTrue(exception.getMessage().contains("Pattern cannot be null or empty for SEARCH operation"));
    }

    @Test
    @DisplayName("测试工厂方法 - forRead")
    void testFactoryForRead() {
        FileOperationRequest request = FileOperationRequest.forRead("/test/file.txt");

        assertEquals(FileOperationRequest.Operation.READ, request.operation());
        assertEquals("/test/file.txt", request.path());
        assertNull(request.content());
        assertFalse(request.recursive());
    }

    @Test
    @DisplayName("测试工厂方法 - forWrite")
    void testFactoryForWrite() {
        String content = "Hello, World!";
        FileOperationRequest request = FileOperationRequest.forWrite("/test/file.txt", content);

        assertEquals(FileOperationRequest.Operation.WRITE, request.operation());
        assertEquals("/test/file.txt", request.path());
        assertEquals(content, request.content());
    }

    @Test
    @DisplayName("测试工厂方法 - forList")
    void testFactoryForList() {
        FileOperationRequest request = FileOperationRequest.forList("/test", true);

        assertEquals(FileOperationRequest.Operation.LIST, request.operation());
        assertEquals("/test", request.path());
        assertTrue(request.recursive());
    }

    @Test
    @DisplayName("测试工厂方法 - forDelete")
    void testFactoryForDelete() {
        FileOperationRequest request = FileOperationRequest.forDelete("/test/file.txt", false);

        assertEquals(FileOperationRequest.Operation.DELETE, request.operation());
        assertEquals("/test/file.txt", request.path());
        assertFalse(request.recursive());
    }

    @Test
    @DisplayName("测试工厂方法 - forSearch")
    void testFactoryForSearch() {
        FileOperationRequest request = FileOperationRequest.forSearch("/test", "*.txt", true, 50);

        assertEquals(FileOperationRequest.Operation.SEARCH, request.operation());
        assertEquals("/test", request.path());
        assertEquals("*.txt", request.pattern());
        assertTrue(request.recursive());
        assertEquals(50, request.maxResults());
    }

    @Test
    @DisplayName("测试工厂方法 - forMetadata")
    void testFactoryForMetadata() {
        FileOperationRequest request = FileOperationRequest.forMetadata("/test/file.txt");

        assertEquals(FileOperationRequest.Operation.METADATA, request.operation());
        assertEquals("/test/file.txt", request.path());
    }

    @Test
    @DisplayName("测试往返序列化")
    void testRoundTripSerialization() {
        FileOperationRequest original = FileOperationRequest.forWrite(
                "/test/file.txt",
                "Hello, World!"
        );

        String json = original.toJson();
        FileOperationRequest restored = FileOperationRequest.fromJson(json);

        assertEquals(original.operation(), restored.operation());
        assertEquals(original.path(), restored.path());
        assertEquals(original.content(), restored.content());
    }

    @Test
    @DisplayName("测试所有操作类型")
    void testAllOperationTypes() {
        assertEquals(6, FileOperationRequest.Operation.values().length);

        // 验证所有操作类型都存在
        assertNotNull(FileOperationRequest.Operation.valueOf("READ"));
        assertNotNull(FileOperationRequest.Operation.valueOf("WRITE"));
        assertNotNull(FileOperationRequest.Operation.valueOf("LIST"));
        assertNotNull(FileOperationRequest.Operation.valueOf("DELETE"));
        assertNotNull(FileOperationRequest.Operation.valueOf("SEARCH"));
        assertNotNull(FileOperationRequest.Operation.valueOf("METADATA"));
    }
}
