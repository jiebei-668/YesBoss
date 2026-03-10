package tech.yesboss.tool.filesystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 文件元数据模型
 *
 * <p>包含文件或目录的详细信息，如大小、权限、时间戳等。</p>
 *
 * @param path 文件或目录的完整路径
 * @param name 文件或目录名称
 * @param type 类型（FILE 或 DIRECTORY）
 * @param size 文件大小（字节），目录为 0
 * @param lastModified 最后修改时间戳（毫秒）
 * @param isReadable 是否可读
 * @param isWritable 是否可写
 * @param isExecutable 是否可执行
 * @param isHidden 是否隐藏
 * @param extension 文件扩展名（目录为 null）
 */
public record FileMetadata(
        @JsonProperty("path")
        String path,

        @JsonProperty("name")
        String name,

        @JsonProperty("type")
        FileType type,

        @JsonProperty("size")
        long size,

        @JsonProperty("lastModified")
        long lastModified,

        @JsonProperty("isReadable")
        boolean isReadable,

        @JsonProperty("isWritable")
        boolean isWritable,

        @JsonProperty("isExecutable")
        boolean isExecutable,

        @JsonProperty("isHidden")
        boolean isHidden,

        @JsonProperty("extension")
        String extension
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 文件类型枚举
     */
    public enum FileType {
        /**
         * 普通文件
         */
        FILE,

        /**
         * 目录
         */
        DIRECTORY
    }

    /**
     * 从 JSON 字符串反序列化为 FileMetadata 对象
     *
     * @param json JSON 字符串
     * @return FileMetadata 对象
     * @throws IllegalArgumentException 如果 JSON 解析失败
     */
    public static FileMetadata fromJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, FileMetadata.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse FileMetadata from JSON: " + e.getMessage(), e);
        }
    }

    /**
     * 将 FileMetadata 对象序列化为 JSON 字符串
     *
     * @return JSON 字符串
     */
    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize FileMetadata to JSON: " + e.getMessage(), e);
        }
    }

    /**
     * 获取格式化的最后修改时间
     *
     * @return 格式化的日期时间字符串
     */
    @JsonIgnore
    public String getFormattedLastModified() {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(lastModified),
                ZoneId.systemDefault()
        );
        return DATE_FORMATTER.format(dateTime);
    }

    /**
     * 获取人类可读的文件大小
     *
     * @return 格式化的大小字符串（如 "1.5 KB"）
     */
    @JsonIgnore
    public String getFormattedSize() {
        if (type == FileType.DIRECTORY) {
            return "(directory)";
        }

        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * 判断是否为目录
     *
     * @return 如果是目录返回 true
     */
    @JsonIgnore
    public boolean isDirectory() {
        return type == FileType.DIRECTORY;
    }

    /**
     * 判断是否为文件
     *
     * @return 如果是文件返回 true
     */
    @JsonIgnore
    public boolean isFile() {
        return type == FileType.FILE;
    }

    /**
     * 从路径中提取文件名或目录名
     *
     * @param path 完整路径
     * @return 文件名或目录名
     */
    private static String extractName(String path) {
        // 处理 Unix 和 Windows 路径分隔符
        int lastSlash = Math.max(
                path.lastIndexOf('/'),
                path.lastIndexOf(java.io.File.separatorChar)
        );
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * 从文件名中提取扩展名
     *
     * @param name 文件名
     * @return 扩展名（不含点号），如果没有扩展名则返回 null
     */
    private static String extractExtension(String name) {
        // 跳过以点开头的隐藏文件（如 .gitignore）
        if (name.startsWith(".")) {
            return null;
        }

        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < name.length() - 1) {
            return name.substring(dotIndex + 1);
        }
        return null;
    }

    /**
     * 获取权限字符串（类似 Unix 的 ls -l 格式）
     *
     * @return 权限字符串（如 "rw-r--r--"）
     */
    @JsonIgnore
    public String getPermissionString() {
        StringBuilder sb = new StringBuilder(9);
        sb.append(isReadable ? 'r' : '-');
        sb.append(isWritable ? 'w' : '-');
        sb.append(isExecutable ? 'x' : '-');
        return sb.toString();
    }

    /**
     * 创建目录的元数据
     *
     * @param path 目录路径
     * @param isReadable 是否可读
     * @param isWritable 是否可写
     * @param isExecutable 是否可执行
     * @param isHidden 是否隐藏
     * @return FileMetadata 对象
     */
    public static FileMetadata forDirectory(
            String path,
            boolean isReadable,
            boolean isWritable,
            boolean isExecutable,
            boolean isHidden) {
        String name = extractName(path);
        return new FileMetadata(
                path,
                name,
                FileType.DIRECTORY,
                0,
                System.currentTimeMillis(),
                isReadable,
                isWritable,
                isExecutable,
                isHidden,
                null
        );
    }

    /**
     * 创建文件的元数据
     *
     * @param path 文件路径
     * @param size 文件大小
     * @param lastModified 最后修改时间
     * @param isReadable 是否可读
     * @param isWritable 是否可写
     * @param isExecutable 是否可执行
     * @param isHidden 是否隐藏
     * @return FileMetadata 对象
     */
    public static FileMetadata forFile(
            String path,
            long size,
            long lastModified,
            boolean isReadable,
            boolean isWritable,
            boolean isExecutable,
            boolean isHidden) {
        String name = extractName(path);
        String extension = extractExtension(name);

        return new FileMetadata(
                path,
                name,
                FileType.FILE,
                size,
                lastModified,
                isReadable,
                isWritable,
                isExecutable,
                isHidden,
                extension
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileMetadata that = (FileMetadata) o;
        return Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    @Override
    public String toString() {
        return String.format("FileMetadata[%s] %s (%s, %s)",
                type == FileType.DIRECTORY ? "DIR" : "FILE",
                name,
                getFormattedSize(),
                getFormattedLastModified());
    }
}
