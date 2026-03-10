package tech.yesboss.tool.filesystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.tool.AgentTool;
import tech.yesboss.tool.ToolAccessLevel;
import tech.yesboss.tool.filesystem.exception.FileOperationException;
import tech.yesboss.tool.filesystem.exception.FileSecurityException;
import tech.yesboss.tool.filesystem.model.FileMetadata;
import tech.yesboss.tool.filesystem.security.DynamicWhitelistManager;
import tech.yesboss.tool.filesystem.security.FileSecurityValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 目录列表工具
 *
 * <p>提供安全的目录内容列表能力，支持递归浏览和文件过滤。</p>
 *
 * <p><b>核心功能：</b></p>
 * <ul>
 *   <li><b>单层列表</b>: 列出指定目录下的直接子项</li>
 *   <li><b>递归列表</b>: 递归列出所有子目录内容</li>
 *   <li><b>深度限制</b>: 控制递归深度，防止无限递归</li>
 *   <li><b>文件过滤</b>: 支持通配符模式过滤文件</li>
 *   <li><b>分页支持</b>: 支持大目录分页浏览</li>
 *   <li><b>安全验证</b>: 集成 FileSecurityValidator 进行路径验证</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * ListDirectoryTool tool = new ListDirectoryTool("/project/root");
 *
 * // 列出单层目录
 * String result = tool.execute("{\"path\": \"src/main\"}");
 *
 * // 递归列出目录（深度 3）
 * String result = tool.execute("{\"path\": \"src\", \"recursive\": true, \"depth\": 3}");
 *
 * // 过滤 Java 文件
 * String result = tool.execute("{\"path\": \"src\", \"pattern\": \"*.java\"}");
 *
 * // 分页浏览
 * String result = tool.execute("{\"path\": \"src\", \"limit\": 10, \"offset\": 20}");
 * }</pre>
 *
 * <p><b>安全特性：</b></p>
 * <ul>
 *   <li>路径遍历攻击防护（../）</li>
 *   <li>黑名单路径过滤</li>
 *   <li>递归深度限制（默认 5 层）</li>
 *   <li>返回数量限制（防止内存溢出）</li>
 * </ul>
 */
public class ListDirectoryTool implements AgentTool {

    private static final Logger logger = LoggerFactory.getLogger(ListDirectoryTool.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String TOOL_NAME = "list_directory";
    private static final String TOOL_DESCRIPTION = """
            列出目录内容，支持递归浏览和文件过滤。

            功能特性：
            - 单层列表：列出指定目录的直接子项（文件和目录）
            - 递归列表：递归列出所有子目录内容
            - 深度限制：控制递归深度，防止无限递归（默认 5 层，最大 10 层）
            - 文件过滤：支持通配符模式过滤文件（如 *.java, *.md）
            - 分页支持：支持 limit 和 offset 参数分页浏览大目录
            - 元数据返回：返回文件名、大小、类型、修改时间等详细信息

            参数说明：
            - path: 目录路径（必需，相对或绝对路径）
            - recursive: 是否递归列出子目录（可选，默认 false）
            - depth: 递归深度限制（可选，默认 5，最大 10）
            - pattern: 文件过滤模式（可选，支持通配符，如 *.java）
            - limit: 返回结果数量限制（可选，用于分页，默认 1000）
            - offset: 结果偏移量（可选，用于分页，默认 0）

            安全特性：
            - 路径遍历攻击防护
            - 黑名单路径过滤
            - 递归深度限制
            - 返回数量限制

            返回格式：
            {
              "success": true,
              "path": "目录路径",
              "items": [
                {
                  "path": "完整路径",
                  "name": "文件名",
                  "type": "FILE/DIRECTORY",
                  "size": 1024,
                  "lastModified": 1234567890,
                  "isReadable": true,
                  "isWritable": true,
                  "isExecutable": false,
                  "isHidden": false,
                  "extension": "java"
                }
              ],
              "total": 42,
              "returned": 10,
              "truncated": false
            }
            """;

    private static final String JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "要列出的目录路径（相对或绝对路径）"
                },
                "recursive": {
                  "type": "boolean",
                  "description": "是否递归列出子目录内容",
                  "default": false
                },
                "depth": {
                  "type": "integer",
                  "description": "递归深度限制（1-10，默认 5）",
                  "minimum": 1,
                  "maximum": 10,
                  "default": 5
                },
                "pattern": {
                  "type": "string",
                  "description": "文件过滤模式（支持通配符，如 *.java, *.md）"
                },
                "limit": {
                  "type": "integer",
                  "description": "返回结果数量限制（用于分页，默认 1000）",
                  "minimum": 1,
                  "default": 1000
                },
                "offset": {
                  "type": "integer",
                  "description": "结果偏移量（用于分页，默认 0）",
                  "minimum": 0,
                  "default": 0
                }
              },
              "required": ["path"]
            }
            """;

    /**
     * 默认递归深度限制
     */
    private static final int DEFAULT_DEPTH = 5;

    /**
     * 最大递归深度限制
     */
    private static final int MAX_DEPTH = 10;

    /**
     * 默认返回数量限制
     */
    private static final int DEFAULT_LIMIT = 1000;

    /**
     * 最大返回数量限制
     */
    private static final int MAX_LIMIT = 10000;

    private final FileSecurityValidator securityValidator;
    private final String projectRoot;

    /**
     * 构造函数
     *
     * @param projectRoot 项目根目录（用于安全验证）
     * @throws IllegalArgumentException 如果 projectRoot 为 null 或空
     */
    public ListDirectoryTool(String projectRoot) {
        this(projectRoot, null);
    }

    /**
     * 构造函数（支持动态白名单）
     *
     * @param projectRoot 项目根目录（用于安全验证）
     * @param dynamicWhitelistManager 动态白名单管理器（可选）
     * @throws IllegalArgumentException 如果 projectRoot 为 null 或空
     */
    public ListDirectoryTool(String projectRoot, DynamicWhitelistManager dynamicWhitelistManager) {
        if (projectRoot == null || projectRoot.trim().isEmpty()) {
            throw new IllegalArgumentException("projectRoot cannot be null or empty");
        }
        this.projectRoot = projectRoot;
        this.securityValidator = new FileSecurityValidator(
                projectRoot,
                FileSecurityValidator.getMaxFileSizeDefault(),
                FileSecurityValidator.getMinDiskSpaceDefault(),
                true, // 列表操作不需要覆盖保护
                dynamicWhitelistManager
        );
        logger.info("ListDirectoryTool initialized with projectRoot: {}{}", projectRoot,
                dynamicWhitelistManager != null ? " (with dynamic whitelist)" : "");
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return TOOL_DESCRIPTION;
    }

    @Override
    public String getParametersJsonSchema() {
        return JSON_SCHEMA;
    }

    @Override
    public ToolAccessLevel getAccessLevel() {
        // 目录列表是只读操作，Master 和 Worker 都可以使用
        return ToolAccessLevel.READ_ONLY;
    }

    @Override
    public String execute(String argumentsJson) throws Exception {
        logger.debug("ListDirectoryTool.execute called with arguments: {}", argumentsJson);

        try {
            // 解析参数
            ListRequest request = parseRequest(argumentsJson);

            // 安全验证
            logger.debug("Performing security validation for path: {}", request.path());
            securityValidator.validatePath(request.path());

            // 列出目录
            return listDirectory(request);

        } catch (FileSecurityException e) {
            logger.warn("Security validation failed: {}", e.getMessage());
            throw e;
        } catch (FileOperationException e) {
            logger.error("File operation failed: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error in ListDirectoryTool.execute: {}", e.getMessage(), e);
            throw new Exception("Failed to list directory: " + e.getMessage(), e);
        }
    }

    @Override
    public String executeWithBypass(String argumentsJson) throws Exception {
        logger.debug("ListDirectoryTool.executeWithBypass called with arguments: {}", argumentsJson);

        // ListDirectoryTool 是只读工具，不需要绕过沙箱
        // 直接调用 execute 即可
        return execute(argumentsJson);
    }

    /**
     * 解析请求参数
     *
     * @param argumentsJson JSON 格式的参数
     * @return 解析后的请求对象
     * @throws Exception 如果解析失败
     */
    private ListRequest parseRequest(String argumentsJson) throws Exception {
        try {
            JsonNode jsonNode = objectMapper.readTree(argumentsJson);

            if (!jsonNode.has("path")) {
                throw new Exception("Missing required parameter: path");
            }

            String path = jsonNode.get("path").asText();
            boolean recursive = jsonNode.has("recursive") && jsonNode.get("recursive").asBoolean();
            int depth = jsonNode.has("depth") ? jsonNode.get("depth").asInt() : DEFAULT_DEPTH;
            String pattern = jsonNode.has("pattern") ? jsonNode.get("pattern").asText() : null;
            int limit = jsonNode.has("limit") ? jsonNode.get("limit").asInt() : DEFAULT_LIMIT;
            int offset = jsonNode.has("offset") ? jsonNode.get("offset").asInt() : 0;

            // 验证和限制参数
            if (depth < 1 || depth > MAX_DEPTH) {
                throw new Exception("Depth must be between 1 and " + MAX_DEPTH);
            }

            if (limit < 1 || limit > MAX_LIMIT) {
                throw new Exception("Limit must be between 1 and " + MAX_LIMIT);
            }

            if (offset < 0) {
                throw new Exception("Offset must be >= 0");
            }

            return new ListRequest(path, recursive, depth, pattern, limit, offset);

        } catch (Exception e) {
            logger.error("Failed to parse arguments: {}", argumentsJson, e);
            throw new Exception("Invalid arguments format: " + e.getMessage(), e);
        }
    }

    /**
     * 列出目录内容
     *
     * @param request 列表请求参数
     * @return JSON 格式的列表结果
     * @throws FileOperationException 如果列表操作失败
     */
    private String listDirectory(ListRequest request)
            throws FileOperationException {

        logger.info("Listing directory: {} (recursive={}, depth={}, pattern={})",
                request.path(), request.recursive(), request.depth(), request.pattern());

        try {
            // 获取目录路径（相对于 projectRoot）
            Path dirPath = normalizePath(request.path());
            logger.debug("Normalized list path: {} -> {}", request.path(), dirPath);

            // 检查目录是否存在
            if (!Files.exists(dirPath)) {
                logger.warn("Directory does not exist: {}", request.path());
                throw FileOperationException.directoryNotFound("LIST", request.path());
            }

            // 检查是否为目录
            if (!Files.isDirectory(dirPath)) {
                logger.warn("Path is not a directory: {}", request.path());
                throw FileOperationException.invalidPath("LIST", request.path());
            }

            // 检查目录可读性
            if (!Files.isReadable(dirPath)) {
                logger.warn("Directory is not readable: {}", request.path());
                throw FileOperationException.accessDenied("LIST", request.path());
            }

            // 收集目录内容
            List<FileMetadata> allItems = new ArrayList<>();

            if (request.recursive()) {
                // 递归列表
                listDirectoryRecursive(dirPath, request, 0, allItems);
            } else {
                // 单层列表
                listDirectorySingle(dirPath, request, allItems);
            }

            // 应用分页
            int total = allItems.size();
            int fromIndex = Math.min(request.offset(), total);
            int toIndex = Math.min(fromIndex + request.limit(), total);
            List<FileMetadata> pagedItems = allItems.subList(fromIndex, toIndex);
            boolean truncated = toIndex < total;

            // 构建结果
            ListResult result = new ListResult(
                    true,
                    dirPath.toString(),
                    pagedItems,
                    total,
                    pagedItems.size(),
                    truncated,
                    null
            );

            String resultJson = result.toJson();
            logger.info("Successfully listed directory: {} ({} items, returned {})",
                    request.path(), total, pagedItems.size());
            return resultJson;

        } catch (FileOperationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error listing directory: {}", request.path(), e);
            throw FileOperationException.unknownError("LIST", request.path(), e);
        }
    }

    /**
     * 单层目录列表
     *
     * @param dirPath 目录路径
     * @param request 请求参数
     * @param items 结果列表
     * @throws FileOperationException 如果读取失败
     */
    private void listDirectorySingle(Path dirPath, ListRequest request, List<FileMetadata> items)
            throws FileOperationException {

        logger.debug("Listing single-level directory: {}", dirPath);

        try (Stream<Path> stream = Files.list(dirPath)) {
            stream.forEach(path -> {
                try {
                    // 检查安全性和可读性
                    if (!Files.isReadable(path)) {
                        logger.debug("Skipping unreadable path: {}", path);
                        return;
                    }

                    // 应用文件过滤
                    if (request.pattern() != null && !matchesPattern(path, request.pattern())) {
                        return;
                    }

                    // 创建文件元数据
                    FileMetadata metadata = createMetadata(path);
                    items.add(metadata);

                } catch (FileSecurityException e) {
                    // 跳过无权访问的路径
                    logger.debug("Skipping restricted path: {}", path);
                } catch (Exception e) {
                    logger.warn("Failed to process path: {}", path, e);
                }
            });
        } catch (IOException e) {
            logger.error("Failed to list directory: {}", dirPath, e);
            throw FileOperationException.ioError("LIST", dirPath.toString(), e);
        }
    }

    /**
     * 递归目录列表
     *
     * @param dirPath 目录路径
     * @param request 请求参数
     * @param currentDepth 当前深度
     * @param items 结果列表
     * @throws FileOperationException 如果读取失败
     */
    private void listDirectoryRecursive(Path dirPath, ListRequest request, int currentDepth, List<FileMetadata> items)
            throws FileOperationException {

        logger.debug("Listing directory recursively: {} (depth: {})", dirPath, currentDepth);

        // 检查深度限制
        if (currentDepth >= request.depth()) {
            logger.debug("Reached maximum depth at: {}", dirPath);
            return;
        }

        try (Stream<Path> stream = Files.list(dirPath)) {
            stream.forEach(path -> {
                try {
                    // 检查安全性和可读性
                    if (!Files.isReadable(path)) {
                        logger.debug("Skipping unreadable path: {}", path);
                        return;
                    }

                    boolean isDirectory = Files.isDirectory(path);

                    // 如果是目录，递归处理
                    if (isDirectory) {
                        // 创建目录元数据
                        FileMetadata metadata = createMetadata(path);
                        items.add(metadata);

                        // 递归进入子目录
                        try {
                            listDirectoryRecursive(path, request, currentDepth + 1, items);
                        } catch (FileOperationException e) {
                            // 跳过无法访问的子目录
                            logger.debug("Skipping inaccessible directory: {}", path);
                        }
                    } else {
                        // 如果是文件，应用文件过滤
                        if (request.pattern() != null && !matchesPattern(path, request.pattern())) {
                            return;
                        }

                        // 创建文件元数据
                        FileMetadata metadata = createMetadata(path);
                        items.add(metadata);
                    }

                } catch (FileSecurityException e) {
                    // 跳过无权访问的路径
                    logger.debug("Skipping restricted path: {}", path);
                } catch (Exception e) {
                    logger.warn("Failed to process path: {}", path, e);
                }
            });
        } catch (IOException e) {
            logger.error("Failed to list directory: {}", dirPath, e);
            throw FileOperationException.ioError("LIST", dirPath.toString(), e);
        }
    }

    /**
     * 创建文件元数据
     *
     * @param path 文件路径
     * @return FileMetadata 对象
     * @throws FileSecurityException 如果安全验证失败
     */
    private FileMetadata createMetadata(Path path) throws FileSecurityException {
        try {
            java.io.File file = path.toFile();
            boolean isDirectory = Files.isDirectory(path);

            if (isDirectory) {
                return FileMetadata.forDirectory(
                        path.toString(),
                        file.canRead(),
                        file.canWrite(),
                        file.canExecute(),
                        file.isHidden()
                );
            } else {
                return FileMetadata.forFile(
                        path.toString(),
                        Files.size(path),
                        Files.getLastModifiedTime(path).toMillis(),
                        file.canRead(),
                        file.canWrite(),
                        file.canExecute(),
                        file.isHidden()
                );
            }
        } catch (Exception e) {
            logger.error("Failed to create metadata for path: {}", path, e);
            throw FileSecurityException.illegalCharacters("LIST", path.toString());
        }
    }

    /**
     * 检查文件名是否匹配模式
     *
     * @param path 文件路径
     * @param pattern 匹配模式（支持通配符）
     * @return 如果匹配返回 true
     */
    private boolean matchesPattern(Path path, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return true;
        }

        String fileName = path.getFileName().toString();
        if (fileName == null) {
            return false;
        }

        // 简单的通配符匹配
        // 将 * 替换为 .*, 将 ? 替换为 .
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");

        return fileName.matches(regex);
    }

    /**
     * 列表请求参数
     *
     * @param path 目录路径
     * @param recursive 是否递归
     * @param depth 递归深度
     * @param pattern 文件过滤模式
     * @param limit 返回数量限制
     * @param offset 偏移量
     */
    private record ListRequest(
            String path,
            boolean recursive,
            int depth,
            String pattern,
            int limit,
            int offset
    ) {}

    /**
     * 列表结果
     *
     * @param success 是否成功
     * @param path 目录路径
     * @param items 文件列表
     * @param total 总数量
     * @param returned 返回数量
     * @param truncated 是否被截断
     * @param error 错误信息（如果失败）
     */
    private record ListResult(
            boolean success,
            String path,
            List<FileMetadata> entries,
            int total,
            int returned,
            boolean truncated,
            String error
    ) {
        public String toJson() {
            try {
                return objectMapper.writeValueAsString(this);
            } catch (Exception e) {
                logger.error("Failed to serialize ListResult to JSON", e);
                return String.format("{\"success\":%s,\"error\":\"%s\"}", success, error);
            }
        }
    }

    /**
     * 规范化路径（相对于 projectRoot）
     *
     * <p>对于相对路径，会相对于 projectRoot 进行解析。</p>
     *
     * @param path 文件路径
     * @return 规范化后的绝对路径
     */
    private Path normalizePath(String path) {
        Path inputPath = Paths.get(path);

        // 如果是绝对路径，直接规范化
        if (inputPath.isAbsolute()) {
            return inputPath.normalize();
        }

        // 如果是相对路径，相对于 projectRoot 解析
        return Paths.get(projectRoot, path).normalize();
    }
}