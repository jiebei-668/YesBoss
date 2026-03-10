package tech.yesboss.tool.filesystem.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 文件操作请求数据模型
 *
 * <p>这是文件系统工具的统一请求格式，所有文件操作都使用此模型进行参数传递。</p>
 *
 * <p><b>操作类型说明：</b></p>
 * <ul>
 *   <li><b>READ</b>: 读取文件内容</li>
 *   <li><b>WRITE</b>: 写入文件内容</li>
 *   <li><b>LIST</b>: 列出目录内容</li>
 *   <li><b>DELETE</b>: 删除文件或目录</li>
 *   <li><b>SEARCH</b>: 搜索文件</li>
 *   <li><b>METADATA</b>: 获取文件元数据</li>
 * </ul>
 *
 * @param operation 操作类型
 * @param path 目标文件或目录路径
 * @param content 文件内容（仅用于 WRITE 操作）
 * @param pattern 搜索模式（仅用于 SEARCH 操作）
 * @param recursive 是否递归（仅用于 LIST、DELETE、SEARCH 操作）
 * @param maxResults 最大结果数（仅用于 SEARCH 操作，默认 100）
 */
public record FileOperationRequest(
        @JsonProperty("operation")
        Operation operation,

        @JsonProperty("path")
        String path,

        @JsonProperty("content")
        String content,

        @JsonProperty("pattern")
        String pattern,

        @JsonProperty("recursive")
        boolean recursive,

        @JsonProperty("maxResults")
        int maxResults
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 文件操作类型枚举
     */
    public enum Operation {
        /**
         * 读取文件内容
         */
        READ,

        /**
         * 写入文件内容
         */
        WRITE,

        /**
         * 列出目录内容
         */
        LIST,

        /**
         * 删除文件或目录
         */
        DELETE,

        /**
         * 搜索文件
         */
        SEARCH,

        /**
         * 获取文件元数据
         */
        METADATA,

        /**
         * 创建目录
         */
        CREATE_DIRECTORY
    }

    /**
     * 从 JSON 字符串反序列化为 FileOperationRequest 对象
     *
     * @param json JSON 字符串
     * @return FileOperationRequest 对象
     * @throws IllegalArgumentException 如果 JSON 解析失败
     */
    public static FileOperationRequest fromJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, FileOperationRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse FileOperationRequest from JSON: " + e.getMessage(), e);
        }
    }

    /**
     * 将 FileOperationRequest 对象序列化为 JSON 字符串
     *
     * @return JSON 字符串
     */
    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize FileOperationRequest to JSON: " + e.getMessage(), e);
        }
    }

    /**
     * 验证请求参数的合法性
     *
     * @throws IllegalArgumentException 如果参数不合法
     */
    public void validate() {
        if (operation == null) {
            throw new IllegalArgumentException("Operation cannot be null");
        }

        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        // 根据操作类型验证必需参数
        switch (operation) {
            case WRITE -> {
                if (content == null) {
                    throw new IllegalArgumentException("Content cannot be null for WRITE operation");
                }
            }
            case SEARCH -> {
                if (pattern == null || pattern.trim().isEmpty()) {
                    throw new IllegalArgumentException("Pattern cannot be null or empty for SEARCH operation");
                }
                if (maxResults <= 0) {
                    throw new IllegalArgumentException("maxResults must be positive for SEARCH operation");
                }
            }
        }
    }

    /**
     * 创建读取文件操作的请求
     *
     * @param path 文件路径
     * @return FileOperationRequest 对象
     */
    public static FileOperationRequest forRead(String path) {
        return new FileOperationRequest(Operation.READ, path, null, null, false, 0);
    }

    /**
     * 创建写入文件操作的请求
     *
     * @param path 文件路径
     * @param content 文件内容
     * @return FileOperationRequest 对象
     */
    public static FileOperationRequest forWrite(String path, String content) {
        return new FileOperationRequest(Operation.WRITE, path, content, null, false, 0);
    }

    /**
     * 创建列出目录操作的请求
     *
     * @param path 目录路径
     * @param recursive 是否递归
     * @return FileOperationRequest 对象
     */
    public static FileOperationRequest forList(String path, boolean recursive) {
        return new FileOperationRequest(Operation.LIST, path, null, null, recursive, 0);
    }

    /**
     * 创建删除文件操作的请求
     *
     * @param path 文件或目录路径
     * @param recursive 是否递归删除
     * @return FileOperationRequest 对象
     */
    public static FileOperationRequest forDelete(String path, boolean recursive) {
        return new FileOperationRequest(Operation.DELETE, path, null, null, recursive, 0);
    }

    /**
     * 创建搜索文件操作的请求
     *
     * @param path 搜索起始路径
     * @param pattern 搜索模式（支持通配符）
     * @param recursive 是否递归搜索
     * @param maxResults 最大结果数
     * @return FileOperationRequest 对象
     */
    public static FileOperationRequest forSearch(String path, String pattern, boolean recursive, int maxResults) {
        return new FileOperationRequest(Operation.SEARCH, path, null, pattern, recursive, maxResults);
    }

    /**
     * 创建获取元数据操作的请求
     *
     * @param path 文件或目录路径
     * @return FileOperationRequest 对象
     */
    public static FileOperationRequest forMetadata(String path) {
        return new FileOperationRequest(Operation.METADATA, path, null, null, false, 0);
    }
}