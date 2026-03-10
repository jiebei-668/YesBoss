package tech.yesboss.tool.filesystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 文件操作结果数据模型
 *
 * <p>这是文件系统工具的统一返回格式，包含操作状态、结果数据和错误信息。</p>
 *
 * @param success 操作是否成功
 * @param operation 操作类型
 * @param path 目标文件或目录路径
 * @param content 文件内容（仅用于 READ 操作）
 * @param entries 目录条目列表（仅用于 LIST 操作）
 * @param metadata 文件元数据（仅用于 METADATA 操作）
 * @param searchResults 搜索结果列表（仅用于 SEARCH 操作）
 * @param message 操作消息（成功或失败的信息）
 * @param errorMessage 错误消息（仅当操作失败时）
 * @param timestamp 操作时间戳（毫秒）
 */
public record FileOperationResult(
        @JsonProperty("success")
        boolean success,

        @JsonProperty("operation")
        FileOperationRequest.Operation operation,

        @JsonProperty("path")
        String path,

        @JsonProperty("content")
        String content,

        @JsonProperty("entries")
        List<FileMetadata> entries,

        @JsonProperty("metadata")
        FileMetadata metadata,

        @JsonProperty("searchResults")
        List<FileMetadata> searchResults,

        @JsonProperty("message")
        String message,

        @JsonProperty("errorMessage")
        String errorMessage,

        @JsonProperty("timestamp")
        long timestamp
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 创建成功的操作结果（通用）
     *
     * @param operation 操作类型
     * @param path 文件路径
     * @param message 成功消息
     * @return FileOperationResult 对象
     */
    public static FileOperationResult success(
            FileOperationRequest.Operation operation,
            String path,
            String message) {
        return new FileOperationResult(
                true,
                operation,
                path,
                null,
                null,
                null,
                null,
                message,
                null,
                Instant.now().toEpochMilli()
        );
    }

    /**
     * 创建成功的读取操作结果
     *
     * @param path 文件路径
     * @param content 文件内容
     * @return FileOperationResult 对象
     */
    public static FileOperationResult successRead(String path, String content) {
        return new FileOperationResult(
                true,
                FileOperationRequest.Operation.READ,
                path,
                content,
                null,
                null,
                null,
                "File read successfully",
                null,
                Instant.now().toEpochMilli()
        );
    }

    /**
     * 创建成功的列表操作结果
     *
     * @param path 目录路径
     * @param entries 目录条目列表
     * @return FileOperationResult 对象
     */
    public static FileOperationResult successList(String path, List<FileMetadata> entries) {
        return new FileOperationResult(
                true,
                FileOperationRequest.Operation.LIST,
                path,
                null,
                entries,
                null,
                null,
                String.format("Listed %d entries", entries.size()),
                null,
                Instant.now().toEpochMilli()
        );
    }

    /**
     * 创建成功的元数据操作结果
     *
     * @param path 文件路径
     * @param metadata 文件元数据
     * @return FileOperationResult 对象
     */
    public static FileOperationResult successMetadata(String path, FileMetadata metadata) {
        return new FileOperationResult(
                true,
                FileOperationRequest.Operation.METADATA,
                path,
                null,
                null,
                metadata,
                null,
                "Metadata retrieved successfully",
                null,
                Instant.now().toEpochMilli()
        );
    }

    /**
     * 创建成功的搜索操作结果
     *
     * @param path 搜索起始路径
     * @param searchResults 搜索结果列表
     * @return FileOperationResult 对象
     */
    public static FileOperationResult successSearch(String path, List<FileMetadata> searchResults) {
        return new FileOperationResult(
                true,
                FileOperationRequest.Operation.SEARCH,
                path,
                null,
                null,
                null,
                searchResults,
                String.format("Found %d files", searchResults.size()),
                null,
                Instant.now().toEpochMilli()
        );
    }

    /**
     * 创建失败的操作结果
     *
     * @param operation 操作类型
     * @param path 文件路径
     * @param errorMessage 错误消息
     * @return FileOperationResult 对象
     */
    public static FileOperationResult failure(
            FileOperationRequest.Operation operation,
            String path,
            String errorMessage) {
        return new FileOperationResult(
                false,
                operation,
                path,
                null,
                null,
                null,
                null,
                null,
                errorMessage,
                Instant.now().toEpochMilli()
        );
    }

    /**
     * 从 JSON 字符串反序列化为 FileOperationResult 对象
     *
     * @param json JSON 字符串
     * @return FileOperationResult 对象
     * @throws IllegalArgumentException 如果 JSON 解析失败
     */
    public static FileOperationResult fromJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, FileOperationResult.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse FileOperationResult from JSON: " + e.getMessage(), e);
        }
    }

    /**
     * 将 FileOperationResult 对象序列化为 JSON 字符串
     *
     * @return JSON 字符串
     */
    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize FileOperationResult to JSON: " + e.getMessage(), e);
        }
    }

    /**
     * 获取可选的内容（仅用于 READ 操作）
     *
     * @return Optional 包裹的内容
     */
    @JsonIgnore
    public Optional<String> getOptionalContent() {
        return Optional.ofNullable(content);
    }

    /**
     * 获取可选的目录条目列表（仅用于 LIST 操作）
     *
     * @return Optional 包裹的条目列表
     */
    @JsonIgnore
    public Optional<List<FileMetadata>> getOptionalEntries() {
        return Optional.ofNullable(entries);
    }

    /**
     * 获取可选的元数据（仅用于 METADATA 操作）
     *
     * @return Optional 包裹的元数据
     */
    @JsonIgnore
    public Optional<FileMetadata> getOptionalMetadata() {
        return Optional.ofNullable(metadata);
    }

    /**
     * 获取可选的搜索结果列表（仅用于 SEARCH 操作）
     *
     * @return Optional 包裹的搜索结果
     */
    @JsonIgnore
    public Optional<List<FileMetadata>> getOptionalSearchResults() {
        return Optional.ofNullable(searchResults);
    }
}
