package tech.yesboss.tool.filesystem.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileMetadata 单元测试
 */
@DisplayName("FileMetadata 测试")
class FileMetadataTest {

    @Test
    @DisplayName("测试文件元数据 - forFile()")
    void testForFile() {
        long lastModified = System.currentTimeMillis();
        FileMetadata metadata = FileMetadata.forFile(
                "/test/file.txt",
                2048,
                lastModified,
                true,   // readable
                true,   // writable
                false,  // executable
                false   // hidden
        );

        assertEquals("/test/file.txt", metadata.path());
        assertEquals("file.txt", metadata.name());
        assertEquals(FileMetadata.FileType.FILE, metadata.type());
        assertEquals(2048, metadata.size());
        assertEquals(lastModified, metadata.lastModified());
        assertTrue(metadata.isReadable());
        assertTrue(metadata.isWritable());
        assertFalse(metadata.isExecutable());
        assertFalse(metadata.isHidden());
        assertEquals("txt", metadata.extension());
    }

    @Test
    @DisplayName("测试目录元数据 - forDirectory()")
    void testForDirectory() {
        FileMetadata metadata = FileMetadata.forDirectory(
                "/test/dir",
                true,   // readable
                true,   // writable
                false,  // executable
                true    // hidden
        );

        assertEquals("/test/dir", metadata.path());
        assertEquals("dir", metadata.name());
        assertEquals(FileMetadata.FileType.DIRECTORY, metadata.type());
        assertEquals(0, metadata.size());
        assertTrue(metadata.isReadable());
        assertTrue(metadata.isWritable());
        assertFalse(metadata.isExecutable());
        assertTrue(metadata.isHidden());
        assertNull(metadata.extension());
    }

    @Test
    @DisplayName("测试 isFile() 和 isDirectory()")
    void testTypeCheckers() {
        FileMetadata file = FileMetadata.forFile(
                "/test/file.txt", 100, System.currentTimeMillis(),
                true, true, false, false
        );
        FileMetadata dir = FileMetadata.forDirectory(
                "/test/dir", true, true, false, false
        );

        assertTrue(file.isFile());
        assertFalse(file.isDirectory());

        assertTrue(dir.isDirectory());
        assertFalse(dir.isFile());
    }

    @Test
    @DisplayName("测试 getFormattedSize() - 文件")
    void testGetFormattedSize_File() {
        FileMetadata file1 = FileMetadata.forFile("/test/f1", 512, System.currentTimeMillis(), true, true, false, false);
        assertEquals("512 B", file1.getFormattedSize());

        FileMetadata file2 = FileMetadata.forFile("/test/f2", 1024, System.currentTimeMillis(), true, true, false, false);
        assertEquals("1.0 KB", file2.getFormattedSize());

        FileMetadata file3 = FileMetadata.forFile("/test/f3", 1536, System.currentTimeMillis(), true, true, false, false);
        assertEquals("1.5 KB", file3.getFormattedSize());

        FileMetadata file4 = FileMetadata.forFile("/test/f4", 1024 * 1024, System.currentTimeMillis(), true, true, false, false);
        assertEquals("1.0 MB", file4.getFormattedSize());

        FileMetadata file5 = FileMetadata.forFile("/test/f5", 1024 * 1024 * 1024, System.currentTimeMillis(), true, true, false, false);
        assertEquals("1.0 GB", file5.getFormattedSize());
    }

    @Test
    @DisplayName("测试 getFormattedSize() - 目录")
    void testGetFormattedSize_Directory() {
        FileMetadata dir = FileMetadata.forDirectory("/test/dir", true, true, false, false);
        assertEquals("(directory)", dir.getFormattedSize());
    }

    @Test
    @DisplayName("测试 getPermissionString()")
    void testGetPermissionString() {
        FileMetadata metadata1 = FileMetadata.forFile(
                "/test/file.txt", 100, System.currentTimeMillis(),
                true, true, true, false
        );
        assertEquals("rwx", metadata1.getPermissionString());

        FileMetadata metadata2 = FileMetadata.forFile(
                "/test/file.txt", 100, System.currentTimeMillis(),
                true, false, false, false
        );
        assertEquals("r--", metadata2.getPermissionString());

        FileMetadata metadata3 = FileMetadata.forFile(
                "/test/file.txt", 100, System.currentTimeMillis(),
                false, false, false, false
        );
        assertEquals("---", metadata3.getPermissionString());
    }

    @Test
    @DisplayName("测试 getFormattedLastModified()")
    void testGetFormattedLastModified() {
        long timestamp = System.currentTimeMillis();
        FileMetadata metadata = FileMetadata.forFile(
                "/test/file.txt", 100, timestamp,
                true, true, false, false
        );

        String formatted = metadata.getFormattedLastModified();
        assertNotNull(formatted);
        assertTrue(formatted.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    @DisplayName("测试扩展名提取")
    void testExtensionExtraction() {
        FileMetadata metadata1 = FileMetadata.forFile(
                "/test/file.txt", 100, System.currentTimeMillis(),
                true, true, false, false
        );
        assertEquals("txt", metadata1.extension());

        FileMetadata metadata2 = FileMetadata.forFile(
                "/test/file.tar.gz", 100, System.currentTimeMillis(),
                true, true, false, false
        );
        assertEquals("gz", metadata2.extension());

        FileMetadata metadata3 = FileMetadata.forFile(
                "/test/.hidden", 100, System.currentTimeMillis(),
                true, true, false, false
        );
        assertNull(metadata3.extension());

        FileMetadata metadata4 = FileMetadata.forFile(
                "/test/noextension", 100, System.currentTimeMillis(),
                true, true, false, false
        );
        assertNull(metadata4.extension());
    }

    @Test
    @DisplayName("测试从 JSON 反序列化")
    void testFromJson() {
        String json = """
                {
                    "path": "/test/file.txt",
                    "name": "file.txt",
                    "type": "FILE",
                    "size": 2048,
                    "lastModified": 1234567890000,
                    "isReadable": true,
                    "isWritable": true,
                    "isExecutable": false,
                    "isHidden": false,
                    "extension": "txt"
                }
                """;

        FileMetadata metadata = FileMetadata.fromJson(json);

        assertEquals("/test/file.txt", metadata.path());
        assertEquals("file.txt", metadata.name());
        assertEquals(FileMetadata.FileType.FILE, metadata.type());
        assertEquals(2048, metadata.size());
    }

    @Test
    @DisplayName("测试序列化为 JSON")
    void testToJson() {
        FileMetadata metadata = FileMetadata.forFile(
                "/test/file.txt", 2048, System.currentTimeMillis(),
                true, true, false, false
        );

        String json = metadata.toJson();

        assertTrue(json.contains("\"path\":\"/test/file.txt\""));
        assertTrue(json.contains("\"name\":\"file.txt\""));
        assertTrue(json.contains("\"type\":\"FILE\""));
        assertTrue(json.contains("\"size\":2048"));
    }

    @Test
    @DisplayName("测试往返序列化")
    void testRoundTripSerialization() {
        FileMetadata original = FileMetadata.forFile(
                "/test/file.txt", 2048, System.currentTimeMillis(),
                true, true, false, false
        );

        String json = original.toJson();
        FileMetadata restored = FileMetadata.fromJson(json);

        assertEquals(original.path(), restored.path());
        assertEquals(original.name(), restored.name());
        assertEquals(original.type(), restored.type());
        assertEquals(original.size(), restored.size());
    }

    @Test
    @DisplayName("测试 equals() 和 hashCode()")
    void testEqualsAndHashCode() {
        FileMetadata metadata1 = FileMetadata.forFile(
                "/test/file.txt", 100, System.currentTimeMillis(),
                true, true, false, false
        );

        FileMetadata metadata2 = FileMetadata.forFile(
                "/test/file.txt", 200, System.currentTimeMillis(),
                false, false, true, true
        );

        FileMetadata metadata3 = FileMetadata.forFile(
                "/test/other.txt", 100, System.currentTimeMillis(),
                true, true, false, false
        );

        // 相同路径应该相等
        assertEquals(metadata1, metadata2);
        assertEquals(metadata1.hashCode(), metadata2.hashCode());

        // 不同路径不应该相等
        assertNotEquals(metadata1, metadata3);
        assertNotEquals(metadata1.hashCode(), metadata3.hashCode());
    }

    @Test
    @DisplayName("测试 toString()")
    void testToString() {
        FileMetadata file = FileMetadata.forFile(
                "/test/file.txt", 2048, System.currentTimeMillis(),
                true, true, false, false
        );

        String str = file.toString();
        assertTrue(str.contains("[FILE]"));
        assertTrue(str.contains("file.txt"));
    }

    @Test
    @DisplayName("测试所有文件类型")
    void testAllFileTypes() {
        assertEquals(2, FileMetadata.FileType.values().length);

        assertNotNull(FileMetadata.FileType.valueOf("FILE"));
        assertNotNull(FileMetadata.FileType.valueOf("DIRECTORY"));
    }
}
