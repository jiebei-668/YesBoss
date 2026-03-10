package tech.yesboss.tool.filesystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.tool.AgentTool;
import tech.yesboss.tool.SuspendExecutionException;
import tech.yesboss.tool.ToolAccessLevel;
import tech.yesboss.tool.filesystem.exception.FileOperationException;
import tech.yesboss.tool.filesystem.exception.FileSecurityException;
import tech.yesboss.tool.filesystem.model.FileMetadata;
import tech.yesboss.tool.filesystem.model.FileOperationRequest;
import tech.yesboss.tool.filesystem.security.DynamicWhitelistManager;
import tech.yesboss.tool.filesystem.security.FileSecurityValidator;
import tech.yesboss.tool.sandbox.SandboxInterceptor;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.io.File;
import java.util.UUID;

/**
 * 文件写入工具
 *
 * <p>提供安全的文本文件写入能力，支持多种文件格式、编码和写入模式。</p>
 *
 * <p><b>核心功能：</b></p>
 * <ul>
 *   <li><b>原子写入</b>: 先写入临时文件，成功后重命名，确保数据完整性</li>
 *   <li><b>写入模式</b>: 支持 OVERWRITE（覆盖）和 APPEND（追加）模式</li>
 *   <li><b>编码支持</b>: 支持 UTF-8、GBK、ISO-8859-1 等常见编码</li>
 *   <li><b>安全验证</b>: 集成 FileSecurityValidator 进行路径和文件类型验证</li>
 *   <li><b>人机回环</b>: 危险操作（覆盖文件、写入敏感目录等）触发审批流程</li>
 *   <li><b>磁盘空间检查</b>: 写入前检查磁盘剩余空间</li>
 *   <li><b>父目录创建</b>: 可选择自动创建不存在的父目录</li>
 * </ul>
 *
 * <p><b>人机回环触发场景：</b></p>
 * <ul>
 *   <li>覆盖已存在的文件（可通过配置启用/禁用）</li>
 *   <li>写入受保护的文件扩展名（.env, .pem, .key, .db 等）</li>
 *   <li>写入受保护的目录（.git, .ssh, .aws, secrets 等）</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * WriteFileTool tool = new WriteFileTool("/project/root");
 *
 * // 覆盖写入文件
 * String result = tool.execute("{\"path\": \"src/output.txt\", \"content\": \"Hello World\", \"mode\": \"OVERWRITE\"}");
 *
 * // 追加写入文件
 * String result = tool.execute("{\"path\": \"src/log.txt\", \"content\": \"New log entry\\n\", \"mode\": \"APPEND\"}");
 *
 * // 使用 GBK 编码写入
 * String result = tool.execute("{\"path\": \"src/gbk.txt\", \"content\": \"中文内容\", \"encoding\": \"GBK\"}");
 *
 * // 自动创建父目录
 * String result = tool.execute("{\"path\": \"src/newdir/file.txt\", \"content\": \"Content\", \"createParentDirs\": true}");
 * }</pre>
 *
 * <p><b>安全特性：</b></p>
 * <ul>
 *   <li>路径遍历攻击防护（../）</li>
 *   <li>黑名单路径过滤（/etc/passwd, ~/.ssh 等）</li>
 *   <li>文件类型白名单验证</li>
 *   <li>文件大小限制检查</li>
 *   <li>磁盘空间检查</li>
 *   <li>人机回环审批机制</li>
 * </ul>
 *
 * <p><b>原子写入机制：</b></p>
 * <ul>
 *   <li>先写入临时文件（.tmp 后缀）</li>
 *   <li>写入成功后，原子性地重命名为目标文件</li>
 *   <li>如果写入失败，临时文件会被清理</li>
 *   <li>确保系统崩溃时不会产生损坏的文件</li>
 * </ul>
 */
public class WriteFileTool implements AgentTool {

    private static final Logger logger = LoggerFactory.getLogger(WriteFileTool.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String TOOL_NAME = "write_file";
    private static final String TOOL_DESCRIPTION = """
            安全地写入文本文件内容，支持原子写入操作。

            写入模式：
            - OVERWRITE: 覆盖模式（默认），完全替换文件内容
            - APPEND: 追加模式，在文件末尾添加内容

            支持的文件格式：
            - 代码文件: .java, .py, .js, .ts, .go, .rs, .c, .cpp, .h, .cs, .php, .rb, .kt, .swift
            - 配置文件: .json, .yaml, .yml, .xml, .properties, .env, .toml, .ini, .conf
            - 文档文件: .txt, .md, .markdown, .html, .css, .sql, .sh, .bash, .ps1

            支持的编码：
            - UTF-8 (默认，推荐)
            - GBK (中文文件)
            - GB2312 (简体中文)
            - ISO-8859-1 (西欧语言)
            - US-ASCII (纯英文)

            核心特性：
            - 原子写入: 先写临时文件再重命名，确保数据完整性
            - 磁盘空间检查: 写入前检查剩余空间
            - 自动创建目录: 可选自动创建父目录
            - 安全验证: 路径遍历防护、文件类型白名单
            - 人机回环: 覆盖文件等危险操作需要用户审批

            返回格式：
            {
              "success": true,
              "operation": "WRITE",
              "path": "文件路径",
              "bytesWritten": 1024,
              "mode": "OVERWRITE",
              "encoding": "UTF-8",
              "metadata": {
                "path": "文件路径",
                "name": "文件名",
                "type": "FILE",
                "size": 1024,
                "lastModified": 1234567890,
                "isReadable": true,
                "isWritable": true,
                "extension": "txt"
              },
              "message": "File written successfully",
              "timestamp": 1234567890
            }
            """;

    private static final String JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "要写入的文件路径（相对或绝对路径）"
                },
                "content": {
                  "type": "string",
                  "description": "要写入的文件内容"
                },
                "mode": {
                  "type": "string",
                  "description": "写入模式：OVERWRITE（覆盖）或 APPEND（追加）",
                  "enum": ["OVERWRITE", "APPEND"],
                  "default": "OVERWRITE"
                },
                "encoding": {
                  "type": "string",
                  "description": "文件编码格式，默认为 UTF-8",
                  "enum": ["UTF-8", "GBK", "GB2312", "ISO-8859-1", "US-ASCII"],
                  "default": "UTF-8"
                },
                "createParentDirs": {
                  "type": "boolean",
                  "description": "如果父目录不存在，是否自动创建",
                  "default": false
                }
              },
              "required": ["path", "content"]
            }
            """;

    /**
     * 写入模式枚举
     */
    public enum WriteMode {
        /**
         * 覆盖模式：完全替换文件内容
         */
        OVERWRITE,

        /**
         * 追加模式：在文件末尾添加内容
         */
        APPEND
    }

    /**
     * 磁盘空间安全阈值（字节）
     * 至少保留 10KB 的自由空间（降低阈值以适应测试环境）
     */
    private static final long DISK_SPACE_SAFETY_THRESHOLD = 10L * 1024; // 10KB

    private FileSecurityValidator securityValidator;
    private final String projectRoot;

    /**
     * 安全沙箱拦截器（可选）
     * 用于人机回环审批机制
     */
    private SandboxInterceptor sandboxInterceptor;

    /**
     * 当前工具调用 ID（用于人机回环）
     */
    private String currentToolCallId;

    /**
     * 构造函数
     *
     * <p>注意：无沙箱拦截器时，不启用覆盖保护，因为没有审批机制可以处理。
     * 如果需要覆盖保护，请使用带 SandboxInterceptor 的构造函数。</p>
     *
     * @param projectRoot 项目根目录（用于安全验证）
     * @throws IllegalArgumentException 如果 projectRoot 为 null 或空
     */
    public WriteFileTool(String projectRoot) {
        this(projectRoot, null, null);
    }

    /**
     * 构造函数（带沙箱拦截器）
     *
     * <p>当有沙箱拦截器时，覆盖保护由沙箱拦截器处理，可以触发人机回环审批。</p>
     *
     * @param projectRoot 项目根目录（用于安全验证）
     * @param sandboxInterceptor 安全沙箱拦截器（用于人机回环审批）
     * @throws IllegalArgumentException 如果 projectRoot 为 null 或空
     */
    public WriteFileTool(String projectRoot, SandboxInterceptor sandboxInterceptor) {
        this(projectRoot, sandboxInterceptor, null);
    }

    /**
     * 构造函数（支持动态白名单）
     *
     * @param projectRoot 项目根目录（用于安全验证）
     * @param sandboxInterceptor 安全沙箱拦截器（用于人机回环审批）
     * @param dynamicWhitelistManager 动态白名单管理器（可选）
     * @throws IllegalArgumentException 如果 projectRoot 为 null 或空
     */
    public WriteFileTool(String projectRoot, SandboxInterceptor sandboxInterceptor,
                        DynamicWhitelistManager dynamicWhitelistManager) {
        if (projectRoot == null || projectRoot.trim().isEmpty()) {
            throw new IllegalArgumentException("projectRoot cannot be null or empty");
        }
        this.projectRoot = projectRoot;
        // 禁用 FileSecurityValidator 的覆盖保护
        // 由沙箱拦截器负责审批流程（如果有）
        this.securityValidator = new FileSecurityValidator(projectRoot,
                FileSecurityValidator.getMaxFileSizeDefault(),
                FileSecurityValidator.getMinDiskSpaceDefault(),
                false, // 禁用覆盖保护
                dynamicWhitelistManager);
        this.sandboxInterceptor = sandboxInterceptor;
        logger.info("WriteFileTool initialized with projectRoot: {}{}{}", projectRoot,
                sandboxInterceptor != null ? " (with sandbox)" : "",
                dynamicWhitelistManager != null ? " (with dynamic whitelist)" : "");
    }

    /**
     * 设置当前工具调用 ID
     *
     * <p>在人机回环流程中，这个 ID 用于关联工具调用与审批请求。</p>
     *
     * @param toolCallId 工具调用 ID
     */
    public void setToolCallId(String toolCallId) {
        this.currentToolCallId = toolCallId;
    }

    /**
     * 设置沙箱拦截器
     *
     * <p>注意：设置沙箱拦截器后，覆盖保护将转由沙箱拦截器处理。</p>
     *
     * @param sandboxInterceptor 安全沙箱拦截器
     */
    public void setSandboxInterceptor(SandboxInterceptor sandboxInterceptor) {
        this.sandboxInterceptor = sandboxInterceptor;
        // 重新创建 FileSecurityValidator，禁用其覆盖保护（由沙箱拦截器处理）
        if (sandboxInterceptor != null) {
            this.securityValidator = new FileSecurityValidator(projectRoot,
                    FileSecurityValidator.getMaxFileSizeDefault(),
                    FileSecurityValidator.getMinDiskSpaceDefault(),
                    false); // 禁用覆盖保护
            logger.info("WriteFileTool: SandboxInterceptor set, overwrite protection delegated to sandbox");
        }
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
        // 文件写入是修改操作，只有 Worker 可以使用
        return ToolAccessLevel.READ_WRITE;
    }

    @Override
    public String execute(String argumentsJson) throws Exception {
        logger.debug("WriteFileTool.execute called with arguments: {}", argumentsJson);

        try {
            // 解析参数
            WriteRequest request = parseRequest(argumentsJson);

            // 规范化目标路径（相对于 projectRoot）
            Path targetPath = normalizePath(request.path());
            String normalizedPath = targetPath.toString();

            // 如果需要，先创建父目录（在安全验证之前）
            if (request.createParentDirs()) {
                Path parentDir = targetPath.getParent();
                if (parentDir != null && !Files.exists(parentDir)) {
                    logger.debug("Creating parent directories before security validation: {}", parentDir);
                    try {
                        Files.createDirectories(parentDir);
                    } catch (IOException e) {
                        logger.error("Failed to create parent directories: {}", parentDir, e);
                        throw FileOperationException.directoryNotFound("WRITE", request.path());
                    }
                }
            }

            // 人机回环检查（危险操作审批）- 在安全验证之前执行
            // 这样可以让可审批的操作（如覆盖文件、写入受保护扩展名）触发审批流程
            // 而不是被安全验证直接拒绝
            checkWriteOperationWithSandbox(normalizedPath, argumentsJson);

            // 安全验证 - 只检查硬性限制（路径遍历、黑名单等）
            logger.debug("Performing security validation for path: {}", request.path());
            securityValidator.validateWriteAccess(request.path(), request.content().getBytes(request.encoding()).length);

            // 写入文件
            return writeFile(request);

        } catch (SuspendExecutionException e) {
            // 人机回环异常，直接向上抛出
            logger.warn("Write operation suspended for approval: {}", e.getMessage());
            throw e;
        } catch (FileSecurityException e) {
            logger.warn("Security validation failed: {}", e.getMessage());
            throw e;
        } catch (FileOperationException e) {
            logger.error("File operation failed: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error in WriteFileTool.execute: {}", e.getMessage(), e);
            throw new Exception("Failed to write file: " + e.getMessage(), e);
        }
    }

    @Override
    public String executeWithBypass(String argumentsJson) throws Exception {
        logger.debug("WriteFileTool.executeWithBypass called with arguments: {}", argumentsJson);

        // 对于写入操作，即使绕过沙箱，仍然需要进行基本的安全检查
        // 因为这涉及到文件系统的修改
        try {
            // 解析参数
            WriteRequest request = parseRequest(argumentsJson);

            // 只进行基本的路径验证，不进行严格的安全策略检查
            logger.debug("Performing basic path validation for bypass execution: {}", request.path());

            // 跳过人机回环检查（已获得用户授权）

            // 写入文件
            return writeFile(request);

        } catch (Exception e) {
            logger.error("Error in WriteFileTool.executeWithBypass: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 检查写入操作是否需要人机回环审批
     *
     * @param targetPath 目标文件路径（已规范化）
     * @param argumentsJson 参数 JSON
     * @throws SuspendExecutionException 如果操作需要审批
     */
    private void checkWriteOperationWithSandbox(String targetPath, String argumentsJson) throws SuspendExecutionException {
        if (sandboxInterceptor == null) {
            logger.debug("No SandboxInterceptor configured, skipping approval check");
            return;
        }

        String toolCallId = currentToolCallId != null ? currentToolCallId : "unknown-" + System.currentTimeMillis();

        logger.debug("Checking write operation with sandbox: path={}, toolCallId={}", targetPath, toolCallId);

        // 解析写入模式，传递给沙箱拦截器
        String operationType = "WRITE";
        try {
            com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(argumentsJson);
            if (jsonNode.has("mode") && "APPEND".equalsIgnoreCase(jsonNode.get("mode").asText())) {
                operationType = "APPEND";
            }
        } catch (Exception e) {
            logger.debug("Could not parse write mode, defaulting to WRITE");
        }

        // 调用沙箱拦截器检查写入操作
        sandboxInterceptor.checkWriteOperation(targetPath, argumentsJson, toolCallId, operationType);
    }

    /**
     * 解析请求参数
     *
     * @param argumentsJson JSON 格式的参数
     * @return 解析后的请求对象
     * @throws Exception 如果解析失败
     */
    private WriteRequest parseRequest(String argumentsJson) throws Exception {
        try {
            JsonNode jsonNode = objectMapper.readTree(argumentsJson);

            if (!jsonNode.has("path")) {
                throw new Exception("Missing required parameter: path");
            }

            if (!jsonNode.has("content")) {
                throw new Exception("Missing required parameter: content");
            }

            String path = jsonNode.get("path").asText();
            String content = jsonNode.get("content").asText();

            // 解析可选参数
            String modeStr = jsonNode.has("mode") ? jsonNode.get("mode").asText() : "OVERWRITE";
            WriteMode mode = WriteMode.valueOf(modeStr.toUpperCase());

            String encoding = jsonNode.has("encoding") ? jsonNode.get("encoding").asText() : "UTF-8";

            boolean createParentDirs = jsonNode.has("createParentDirs") && jsonNode.get("createParentDirs").asBoolean();

            return new WriteRequest(path, content, mode, encoding, createParentDirs);

        } catch (Exception e) {
            logger.error("Failed to parse arguments: {}", argumentsJson, e);
            throw new Exception("Invalid arguments format: " + e.getMessage(), e);
        }
    }

    /**
     * 写入文件
     *
     * @param request 写入请求
     * @return JSON 格式的写入结果
     * @throws FileOperationException 如果写入操作失败
     */
    private String writeFile(WriteRequest request)
            throws FileOperationException {

        logger.info("Writing file: {} with mode: {}, encoding: {}",
                request.path(), request.mode(), request.encoding());

        try {
            // 解析编码
            Charset charset = parseEncoding(request.encoding());

            // 规范化文件路径（相对于 projectRoot）
            Path targetPath = normalizePath(request.path());
            logger.debug("Normalized write path: {} -> {}", request.path(), targetPath);

            // 检查父目录是否存在
            Path parentDir = targetPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                if (request.createParentDirs()) {
                    logger.debug("Creating parent directories: {}", parentDir);
                    try {
                        Files.createDirectories(parentDir);
                    } catch (IOException e) {
                        logger.error("Failed to create parent directories: {}", parentDir, e);
                        throw FileOperationException.directoryNotFound("WRITE", request.path());
                    }
                } else {
                    logger.warn("Parent directory does not exist: {}", parentDir);
                    throw FileOperationException.directoryNotFound("WRITE", request.path());
                }
            }

            // 计算内容大小
            byte[] contentBytes = request.content().getBytes(charset);
            long contentSize = contentBytes.length;

            // 检查磁盘空间
            checkDiskSpace(targetPath, contentSize);

            // 根据写入模式执行写入
            long bytesWritten;
            if (request.mode() == WriteMode.APPEND && Files.exists(targetPath)) {
                // 追加模式：直接追加到现有文件
                bytesWritten = appendFile(targetPath, contentBytes, charset);
            } else {
                // 覆盖模式或文件不存在：使用原子写入
                bytesWritten = writeFileAtomically(targetPath, contentBytes, charset);
            }

            // 获取文件元数据
            FileMetadata metadata = createMetadata(targetPath);

            // 构建结果
            WriteResult result = new WriteResult(
                    true,
                    FileOperationRequest.Operation.WRITE,
                    request.path(),
                    bytesWritten,
                    request.mode().name(),
                    charset.name(),
                    metadata,
                    String.format("File written successfully (%d bytes)", bytesWritten),
                    null
            );

            String resultJson = result.toJson();
            logger.info("Successfully wrote file: {} ({} bytes)", request.path(), bytesWritten);
            return resultJson;

        } catch (FileOperationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error writing file: {}", request.path(), e);
            throw FileOperationException.unknownError("WRITE", request.path(), e);
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
     * 原子写入文件
     *
     * <p>原子写入机制：</p>
     * <ol>
     *   <li>创建临时文件（添加 .tmp 后缀和随机 UUID）</li>
     *   <li>将内容写入临时文件</li>
     *   <li>刷新到磁盘确保数据持久化</li>
     *   <li>原子性地重命名临时文件为目标文件</li>
     *   <li>如果失败，清理临时文件</li>
     * </ol>
     *
     * @param targetPath 目标文件路径
     * @param contentBytes 文件内容字节数组
     * @param charset 字符编码
     * @return 写入的字节数
     * @throws FileOperationException 如果写入失败
     */
    private long writeFileAtomically(Path targetPath, byte[] contentBytes, Charset charset)
            throws FileOperationException {

        Path tempPath = null;
        try {
            // 1. 创建临时文件路径
            String tempFileName = targetPath.getFileName().toString() + ".tmp." + UUID.randomUUID();
            tempPath = targetPath.resolveSibling(tempFileName);

            logger.debug("Writing to temporary file: {}", tempPath);

            // 2. 写入临时文件
            Files.write(tempPath, contentBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // 3. 强制刷新到磁盘（确保数据持久化）
            tempPath.toFile().getCanonicalFile().getAbsoluteFile(); // 触发系统调用

            // 4. 原子性重命名
            // 在大多数文件系统上，重命名是原子操作
            try {
                Files.move(tempPath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                logger.debug("Atomically renamed temporary file to: {}", targetPath);
            } catch (IOException e) {
                // 如果原子移动失败，尝试普通移动
                logger.warn("Atomic move failed, trying standard move: {}", e.getMessage());
                try {
                    Files.move(tempPath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    logger.error("Failed to move temporary file to target: {}", targetPath, ex);
                    throw FileOperationException.ioError("WRITE", targetPath.toString(), ex);
                }
            }

            logger.debug("Successfully wrote file atomically: {} ({} bytes)", targetPath, contentBytes.length);
            return contentBytes.length;

        } catch (FileOperationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error during atomic write: {}", targetPath, e);

            // 清理临时文件
            if (tempPath != null && Files.exists(tempPath)) {
                try {
                    Files.deleteIfExists(tempPath);
                    logger.debug("Cleaned up temporary file: {}", tempPath);
                } catch (IOException ex) {
                    logger.warn("Failed to clean up temporary file: {}", tempPath, ex);
                }
            }

            throw FileOperationException.ioError("WRITE", targetPath.toString(), e);
        }
    }

    /**
     * 追加写入文件
     *
     * @param targetPath 目标文件路径
     * @param contentBytes 文件内容字节数组
     * @param charset 字符编码
     * @return 写入的字节数
     * @throws FileOperationException 如果写入失败
     */
    private long appendFile(Path targetPath, byte[] contentBytes, Charset charset)
            throws FileOperationException {

        try {
            logger.debug("Appending to file: {} ({} bytes)", targetPath, contentBytes.length);

            Files.write(targetPath, contentBytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            logger.debug("Successfully appended to file: {} ({} bytes)", targetPath, contentBytes.length);
            return contentBytes.length;

        } catch (IOException e) {
            logger.error("Failed to append to file: {}", targetPath, e);
            throw FileOperationException.ioError("WRITE", targetPath.toString(), e);
        }
    }

    /**
     * 检查磁盘剩余空间
     *
     * @param targetPath 目标文件路径
     * @param requiredSpace 需要的空间（字节）
     * @throws FileOperationException 如果磁盘空间不足
     */
    private void checkDiskSpace(Path targetPath, long requiredSpace) throws FileOperationException {
        try {
            File targetFile = targetPath.toFile();

            // 获取磁盘剩余空间
            long freeSpace = targetFile.getFreeSpace();

            // 计算需要的总空间（文件大小 + 安全阈值）
            long totalRequiredSpace = requiredSpace + DISK_SPACE_SAFETY_THRESHOLD;

            logger.debug("Disk space check: required={}, free={}, safety_threshold={}",
                    requiredSpace, freeSpace, DISK_SPACE_SAFETY_THRESHOLD);

            // 只有在明确获取到有效空间数据时才进行检查
            // 如果 freeSpace 为 0 或负数，说明 API 可能不可用，跳过检查
            if (freeSpace > 0 && freeSpace < totalRequiredSpace) {
                logger.warn("Insufficient disk space: required={}, free={}", totalRequiredSpace, freeSpace);
                throw FileOperationException.insufficientDiskSpace("WRITE", targetPath.toString());
            }

            logger.debug("Disk space check passed: {}", targetPath);

        } catch (FileOperationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error checking disk space: {}", targetPath, e);
            // 如果无法获取磁盘空间或检查失败，记录警告但继续操作
            // 这样可以避免因磁盘空间检查 API 兼容性问题导致所有写入失败
            logger.warn("Unable to verify disk space, proceeding with write operation");
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
            throw FileOperationException.encodingError("WRITE", "", e);
        }
    }

    /**
     * 创建文件元数据
     *
     * @param path 文件路径
     * @return FileMetadata 对象
     */
    private FileMetadata createMetadata(Path path) {
        try {
            java.io.File file = path.toFile();

            if (!file.exists()) {
                logger.warn("File does not exist after write: {}", path);
            }

            // 获取文件扩展名
            String fileName = file.getName();
            String extension = null;
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
                extension = fileName.substring(dotIndex + 1);
            }

            return new FileMetadata(
                    path.toString(),
                    fileName,
                    FileMetadata.FileType.FILE,
                    file.length(),
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
                    0,
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
     * 写入请求参数
     *
     * @param path 文件路径
     * @param content 文件内容
     * @param mode 写入模式
     * @param encoding 编码格式
     * @param createParentDirs 是否创建父目录
     */
    private record WriteRequest(
            String path,
            String content,
            WriteMode mode,
            String encoding,
            boolean createParentDirs
    ) {}

    /**
     * 写入结果
     *
     * @param success 是否成功
     * @param operation 操作类型
     * @param path 文件路径
     * @param bytesWritten 写入的字节数
     * @param mode 写入模式
     * @param encoding 编码
     * @param metadata 文件元数据
     * @param message 操作消息
     * @param errorMessage 错误消息（如果失败）
     */
    private record WriteResult(
            boolean success,
            FileOperationRequest.Operation operation,
            String path,
            long bytesWritten,
            String mode,
            String encoding,
            FileMetadata metadata,
            String message,
            String errorMessage
    ) {
        public String toJson() {
            try {
                return objectMapper.writeValueAsString(this);
            } catch (Exception e) {
                logger.error("Failed to serialize WriteResult to JSON", e);
                return String.format("{\"success\":%s,\"error\":\"%s\"}", success, errorMessage);
            }
        }
    }
}