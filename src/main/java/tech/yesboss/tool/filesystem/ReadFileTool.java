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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * 文件读取工具
 *
 * <p>提供安全的文本文件读取能力，支持多种文件格式和编码。</p>
 *
 * <p><b>核心功能：</b></p>
 * <ul>
 *   <li><b>多格式支持</b>: 支持 .txt, .md, .java, .json, .xml, .yaml 等文本格式</li>
 *   <li><b>编码支持</b>: 支持 UTF-8、GBK、ISO-8859-1 等常见编码，避免乱码</li>
 *   <li><b>安全验证</b>: 集成 FileSecurityValidator 进行路径和文件类型验证</li>
 *   <li><b>大小限制</b>: 默认限制 10MB，防止读取过大文件</li>
 *   <li><b>元数据返回</b>: 返回文件内容和详细的元数据信息</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * ReadFileTool tool = new ReadFileTool("/project/root");
 *
 * // 读取文件（默认 UTF-8 编码）
 * String result = tool.execute("{\"path\": \"src/main.java\"}");
 *
 * // 读取文件（指定编码）
 * String result = tool.execute("{\"path\": \"src/file.txt\", \"encoding\": \"GBK\"}");
 * }</pre>
 *
 * <p><b>安全特性：</b></p>
 * <ul>
 *   <li>路径遍历攻击防护（../）</li>
 *   <li>黑名单路径过滤（/etc/passwd, ~/.ssh 等）</li>
 *   <li>文件类型白名单验证</li>
 *   <li>文件大小限制检查</li>
 * </ul>
 */
public class ReadFileTool implements AgentTool {

    private static final Logger logger = LoggerFactory.getLogger(ReadFileTool.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String TOOL_NAME = "read_file";
    private static final String TOOL_DESCRIPTION = """
            读取文本文件内容并返回文件元数据。

            支持的文件格式：
            - 代码文件: .java, .py, .js, .ts, .go, .rs, .c, .cpp, .h, .cs, .php, .rb, .kt, .swift
            - 配置文件: .json, .yaml, .yml, .xml, .properties, .env, .toml, .ini, .conf
            - 文档文件: .txt, .md, .markdown, .html, .css, .sql, .sh, .bash, .ps1

            支持的编码（避免乱码）：
            - UTF-8 (默认，推荐)
            - GBK (中文文件)
            - GB2312 (简体中文)
            - ISO-8859-1 (西欧语言)
            - US-ASCII (纯英文)
            - UTF-16, UTF-16BE, UTF-16LE

            安全特性：
            - 路径遍历攻击防护
            - 黑名单路径过滤
            - 文件类型白名单验证
            - 文件大小限制（10MB）

            返回格式：
            {
              "success": true,
              "content": "文件内容",
              "metadata": {
                "path": "文件路径",
                "name": "文件名",
                "type": "FILE",
                "size": 1024,
                "lastModified": 1234567890,
                "encoding": "UTF-8",
                "lineCount": 42,
                "isReadable": true,
                "isWritable": true,
                "isExecutable": false,
                "isHidden": false,
                "extension": "java"
              }
            }
            """;

    private static final String JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "要读取的文件路径（相对或绝对路径）"
                },
                "encoding": {
                  "type": "string",
                  "description": "文件编码格式，默认为 UTF-8。建议指定正确编码以避免乱码。",
                  "enum": ["UTF-8", "GBK", "GB2312", "ISO-8859-1", "US-ASCII", "UTF-16", "UTF-16BE", "UTF-16LE"],
                  "default": "UTF-8"
                }
              },
              "required": ["path"]
            }
            """;

    private final FileSecurityValidator securityValidator;
    private final String projectRoot;

    /**
     * 构造函数
     *
     * @param projectRoot 项目根目录（用于安全验证）
     * @throws IllegalArgumentException 如果 projectRoot 为 null 或空
     */
    public ReadFileTool(String projectRoot) {
        this(projectRoot, null);
    }

    /**
     * 构造函数（支持动态白名单）
     *
     * @param projectRoot 项目根目录（用于安全验证）
     * @param dynamicWhitelistManager 动态白名单管理器（可选）
     * @throws IllegalArgumentException 如果 projectRoot 为 null 或空
     */
    public ReadFileTool(String projectRoot, DynamicWhitelistManager dynamicWhitelistManager) {
        if (projectRoot == null || projectRoot.trim().isEmpty()) {
            throw new IllegalArgumentException("projectRoot cannot be null or empty");
        }
        this.projectRoot = projectRoot;
        this.securityValidator = new FileSecurityValidator(
                projectRoot,
                FileSecurityValidator.getMaxFileSizeDefault(),
                FileSecurityValidator.getMinDiskSpaceDefault(),
                true, // 默认启用覆盖保护（对读取工具无影响）
                dynamicWhitelistManager
        );
        logger.info("ReadFileTool initialized with projectRoot: {}{}", projectRoot,
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
        // 文件读取是只读操作，Master 和 Worker 都可以使用
        return ToolAccessLevel.READ_ONLY;
    }

    @Override
    public String execute(String argumentsJson) throws Exception {
        logger.debug("ReadFileTool.execute called with arguments: {}", argumentsJson);

        try {
            // 解析参数
            ReadRequest request = parseRequest(argumentsJson);

            // 安全验证
            logger.debug("Performing security validation for path: {}", request.path());
            securityValidator.validateReadAccess(request.path());

            // 读取文件
            return readFile(request.path(), request.encoding());

        } catch (FileSecurityException e) {
            logger.warn("Security validation failed: {}", e.getMessage());
            throw e;
        } catch (FileOperationException e) {
            logger.error("File operation failed: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error in ReadFileTool.execute: {}", e.getMessage(), e);
            throw new Exception("Failed to read file: " + e.getMessage(), e);
        }
    }

    @Override
    public String executeWithBypass(String argumentsJson) throws Exception {
        logger.debug("ReadFileTool.executeWithBypass called with arguments: {}", argumentsJson);

        // ReadFileTool 是只读工具，不需要绕过沙箱
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
    private ReadRequest parseRequest(String argumentsJson) throws Exception {
        try {
            JsonNode jsonNode = objectMapper.readTree(argumentsJson);

            if (!jsonNode.has("path")) {
                throw new Exception("Missing required parameter: path");
            }

            String path = jsonNode.get("path").asText();
            String encoding = jsonNode.has("encoding") ? jsonNode.get("encoding").asText() : "UTF-8";

            return new ReadRequest(path, encoding);

        } catch (Exception e) {
            logger.error("Failed to parse arguments: {}", argumentsJson, e);
            throw new Exception("Invalid arguments format: " + e.getMessage(), e);
        }
    }

    /**
     * 读取文件内容
     *
     * @param filePath 文件路径
     * @param encoding 编码格式
     * @return JSON 格式的读取结果
     * @throws FileSecurityException 如果安全验证失败
     * @throws FileOperationException 如果读取操作失败
     */
    private String readFile(String filePath, String encoding)
            throws FileSecurityException, FileOperationException {

        logger.info("Reading file: {} with encoding: {}", filePath, encoding);

        try {
            // 解析编码
            Charset charset = parseEncoding(encoding);

            // 规范化文件路径（相对于 projectRoot）
            Path path = normalizePath(filePath);
            logger.debug("Normalized file path: {} -> {}", filePath, path);

            // 检查文件是否存在
            if (!Files.exists(path)) {
                logger.warn("File does not exist: {}", path);
                throw FileOperationException.fileNotFound("READ", filePath);
            }

            // 检查是否为文件（不是目录）
            if (Files.isDirectory(path)) {
                logger.warn("Path is a directory, not a file: {}", filePath);
                throw FileOperationException.invalidPath("READ", filePath);
            }

            // 检查文件可读性
            if (!Files.isReadable(path)) {
                logger.warn("File is not readable: {}", filePath);
                throw FileOperationException.accessDenied("READ", filePath);
            }

            // 获取文件大小
            long fileSize = Files.size(path);

            // 验证文件大小（再次检查，防止竞争条件）
            try {
                securityValidator.validateReadAccess(filePath);
            } catch (FileSecurityException e) {
                logger.warn("File size validation failed for {}: {}", filePath, e.getMessage());
                throw e;
            }

            // 读取文件内容
            String content;
            try {
                content = Files.readString(path, charset);
                logger.debug("Successfully read {} bytes from file: {}", content.getBytes(charset).length, filePath);
            } catch (IOException e) {
                logger.error("Failed to read file content: {}", filePath, e);
                throw FileOperationException.ioError("READ", filePath, e);
            }

            // 计算行数
            int lineCount = countLines(content);

            // 创建文件元数据
            FileMetadata metadata = createMetadata(path, fileSize, charset.name(), lineCount);

            // 构建结果
            ReadResult result = new ReadResult(true, content, metadata, lineCount, null);

            String resultJson = result.toJson();
            logger.info("Successfully read file: {} ({} bytes, {} lines)", filePath, fileSize, lineCount);
            return resultJson;

        } catch (FileSecurityException | FileOperationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error reading file: {}", filePath, e);
            throw FileOperationException.unknownError("READ", filePath, e);
        }
    }

    /**
     * 解析编码格式
     *
     * @param encoding 编码字符串
     * @return Charset 对象
     * @throws FileOperationException 如果编码不支持
     */
    private Charset parseEncoding(String encoding) throws FileOperationException {
        try {
            return Charset.forName(encoding);
        } catch (Exception e) {
            logger.warn("Unsupported encoding: {}", encoding);
            throw FileOperationException.encodingError("READ", "", e);
        }
    }

    /**
     * 计算文本行数
     *
     * @param content 文本内容
     * @return 行数
     */
    private int countLines(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        // 使用 split 计算行数，处理不同操作系统的换行符
        // 使用 -1 参数确保保留尾部的空字符串
        String[] lines = content.split("\\R", -1);
        // 如果最后一个元素是空字符串且不是唯一的元素，说明文件以换行符结尾
        // 我们需要减去这个空元素
        if (lines.length > 0 && lines[lines.length - 1].isEmpty()) {
            return lines.length - 1;
        }
        return lines.length;
    }

    /**
     * 创建文件元数据
     *
     * @param path 文件路径
     * @param size 文件大小
     * @param encoding 编码
     * @param lineCount 行数
     * @return FileMetadata 对象
     */
    private FileMetadata createMetadata(Path path, long size, String encoding, int lineCount) {
        try {
            java.io.File file = path.toFile();

            // 获取文件扩展名
            String fileName = file.getName();
            String extension = null;
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
                extension = fileName.substring(dotIndex + 1);
            }

            // 创建包含编码和行数的扩展元数据
            return new FileMetadata(
                    path.toString(),
                    fileName,
                    FileMetadata.FileType.FILE,
                    size,
                    file.lastModified(),
                    file.canRead(),
                    file.canWrite(),
                    file.canExecute(),
                    file.isHidden(),
                    extension
            );

        } catch (Exception e) {
            logger.error("Failed to create metadata for path: {}", path, e);
            // 返回最小化的元数据
            return new FileMetadata(
                    path.toString(),
                    path.getFileName().toString(),
                    FileMetadata.FileType.FILE,
                    size,
                    System.currentTimeMillis(),
                    true,
                    false,
                    false,
                    false,
                    null
            );
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

    /**
     * 读取请求参数
     *
     * @param path 文件路径
     * @param encoding 编码格式
     */
    private record ReadRequest(String path, String encoding) {}

    /**
     * 读取结果
     *
     * @param success 是否成功
     * @param content 文件内容
     * @param metadata 文件元数据
     * @param lineCount 行数
     * @param error 错误信息（如果失败）
     */
    private record ReadResult(
            boolean success,
            String content,
            FileMetadata metadata,
            int lineCount,
            String error
    ) {
        public String toJson() {
            try {
                return objectMapper.writeValueAsString(this);
            } catch (Exception e) {
                logger.error("Failed to serialize ReadResult to JSON", e);
                return String.format("{\"success\":%s,\"error\":\"%s\"}", success, error);
            }
        }
    }
}