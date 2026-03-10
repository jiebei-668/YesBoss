package tech.yesboss.tool.filesystem.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileOperationResult 单元测试
 */
@DisplayName("FileOperationResult 测试")
class FileOperationResultTest {

    @Test
    @DisplayName("测试成功结果 - success()")
    void testSuccess_Generic() {
        FileOperationResult result = FileOperationResult.success(
                FileOperationRequest.Operation.READ,
                "/test/file.txt",
                "Operation completed"
        );

        assertTrue(result.success());
        assertEquals(FileOperationRequest.Operation.READ, result.operation());
        assertEquals("/test/file.txt", result.path());
        assertEquals("Operation completed", result.message());
        assertNull(result.errorMessage());
        assertTrue(result.timestamp() > 0);
    }

    @Test
    @DisplayName("测试成功结果 - successRead()")
    void testSuccess_Read() {
        FileOperationResult result = FileOperationResult.successRead(
                "/test/file.txt",
                "Hello, World!"
        );

        assertTrue(result.success());
        assertEquals(FileOperationRequest.Operation.READ, result.operation());
        assertEquals("/test/file.txt", result.path());
        assertEquals("Hello, World!", result.content());
        assertEquals("File read successfully", result.message());

        Optional<String> content = result.getOptionalContent();
        assertTrue(content.isPresent());
        assertEquals("Hello, World!", content.get());
    }

    @Test
    @DisplayName("测试成功结果 - successList()")
    void testSuccess_List() {
        List<FileMetadata> entries = List.of(
                FileMetadata.forDirectory("/test/dir", true, true, false, false),
                FileMetadata.forFile("/test/file.txt", 1024, System.currentTimeMillis(), true, true, false, false)
        );

        FileOperationResult result = FileOperationResult.successList("/test", entries);

        assertTrue(result.success());
        assertEquals(FileOperationRequest.Operation.LIST, result.operation());
        assertEquals("/test", result.path());
        assertEquals(2, result.entries().size());
        assertTrue(result.message().contains("2 entries"));

        Optional<List<FileMetadata>> optionalEntries = result.getOptionalEntries();
        assertTrue(optionalEntries.isPresent());
        assertEquals(2, optionalEntries.get().size());
    }

    @Test
    @DisplayName("测试成功结果 - successMetadata()")
    void testSuccess_Metadata() {
        FileMetadata metadata = FileMetadata.forFile(
                "/test/file.txt",
                2048,
                System.currentTimeMillis(),
                true, true, false, false
        );

        FileOperationResult result = FileOperationResult.successMetadata("/test/file.txt", metadata);

        assertTrue(result.success());
        assertEquals(FileOperationRequest.Operation.METADATA, result.operation());
        assertEquals("/test/file.txt", result.path());
        assertEquals(metadata, result.metadata());
        assertEquals("Metadata retrieved successfully", result.message());

        Optional<FileMetadata> optionalMetadata = result.getOptionalMetadata();
        assertTrue(optionalMetadata.isPresent());
        assertEquals(metadata, optionalMetadata.get());
    }

    @Test
    @DisplayName("测试成功结果 - successSearch()")
    void testSuccess_Search() {
        List<FileMetadata> results = List.of(
                FileMetadata.forFile("/test/file1.txt", 100, System.currentTimeMillis(), true, true, false, false),
                FileMetadata.forFile("/test/file2.txt", 200, System.currentTimeMillis(), true, true, false, false)
        );

        FileOperationResult result = FileOperationResult.successSearch("/test", results);

        assertTrue(result.success());
        assertEquals(FileOperationRequest.Operation.SEARCH, result.operation());
        assertEquals("/test", result.path());
        assertEquals(2, result.searchResults().size());
        assertTrue(result.message().contains("2 files"));

        Optional<List<FileMetadata>> optionalResults = result.getOptionalSearchResults();
        assertTrue(optionalResults.isPresent());
        assertEquals(2, optionalResults.get().size());
    }

    @Test
    @DisplayName("测试失败结果")
    void testFailure() {
        FileOperationResult result = FileOperationResult.failure(
                FileOperationRequest.Operation.READ,
                "/test/file.txt",
                "File not found"
        );

        assertFalse(result.success());
        assertEquals(FileOperationRequest.Operation.READ, result.operation());
        assertEquals("/test/file.txt", result.path());
        assertEquals("File not found", result.errorMessage());
        assertNull(result.message());
    }

    @Test
    @DisplayName("测试从 JSON 反序列化")
    void testFromJson() {
        String json = """
                {
                    "success": true,
                    "operation": "READ",
                    "path": "/test/file.txt",
                    "content": "Hello, World!",
                    "entries": null,
                    "metadata": null,
                    "searchResults": null,
                    "message": "File read successfully",
                    "errorMessage": null,
                    "timestamp": 1234567890000
                }
                """;

        FileOperationResult result = FileOperationResult.fromJson(json);

        assertTrue(result.success());
        assertEquals(FileOperationRequest.Operation.READ, result.operation());
        assertEquals("Hello, World!", result.content());
    }

    @Test
    @DisplayName("测试序列化为 JSON")
    void testToJson() {
        FileOperationResult result = FileOperationResult.successRead(
                "/test/file.txt",
                "Hello, World!"
        );

        String json = result.toJson();

        assertTrue(json.contains("\"success\":true"));
        assertTrue(json.contains("\"operation\":\"READ\""));
        assertTrue(json.contains("\"path\":\"/test/file.txt\""));
        assertTrue(json.contains("\"content\":\"Hello, World!\""));
    }

    @Test
    @DisplayName("测试 Optional getter")
    void testOptionalGetters() {
        FileOperationResult result = FileOperationResult.successRead(
                "/test/file.txt",
                "Hello, World!"
        );

        assertTrue(result.getOptionalContent().isPresent());
        assertTrue(result.getOptionalEntries().isEmpty());
        assertTrue(result.getOptionalMetadata().isEmpty());
        assertTrue(result.getOptionalSearchResults().isEmpty());
    }

    @Test
    @DisplayName("测试往返序列化")
    void testRoundTripSerialization() {
        FileOperationResult original = FileOperationResult.successRead(
                "/test/file.txt",
                "Hello, World!"
        );

        String json = original.toJson();
        FileOperationResult restored = FileOperationResult.fromJson(json);

        assertEquals(original.success(), restored.success());
        assertEquals(original.operation(), restored.operation());
        assertEquals(original.path(), restored.path());
        assertEquals(original.content(), restored.content());
    }

    @Test
    @DisplayName("测试时间戳")
    void testTimestamp() {
        long before = System.currentTimeMillis();
        FileOperationResult result = FileOperationResult.success(
                FileOperationRequest.Operation.READ,
                "/test/file.txt",
                "OK"
        );
        long after = System.currentTimeMillis();

        assertTrue(result.timestamp() >= before);
        assertTrue(result.timestamp() <= after);
    }
}
