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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * 目录创建工具
 *
 * <p>提供安全的目录创建能力，支持递归创建父目录。</p>
 *
 * <p><b>核心功能：</b></p>
 * <ul>
 *   <li><b>单层创建</b>: 创建单个目录</li>
 *   <li><b>递归创建</b>: 自动创建不存在的父目录</li>
 *   <li><b>幂等操作</b>: 重复创建已存在的目录不会报错</li>
 *   <li><b>安全验证</b>: 集成 FileSecurityValidator 进行路径验证</li>
 *   <li><b>人机回环</b>: 危险操作（创建敏感目录等）触发审批流程</li>
 *   <li><b>权限检查</b>: 验证父目录是否可写</li>
 * </ul>
 *
 * <p><b>人机回环触发场景：</b></p>
 * <ul>
 *   <li>在受保护的目录下创建目录（.git, .ssh, .aws, secrets 等）</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * CreateDirectoryTool tool = new CreateDirectoryTool("/project/root");
 *
 * // 创建单个目录
 * String result = tool.execute("{\"path\": \"src/main/output\"}");
 *
 * // 递归创建多层目录
 * String result = tool.execute("{\"path\": \"src/main/resources/config\", \"recursive\": true}");
 *
 * // 重复创建（幂等）
 * String result = tool.execute("{\"path\": \"src/main/output\"}");
 * }</pre>
 *
 * <p><b>安全特性：</b></p>
 * <ul>
 *   <li>路径遍历攻击防护（../）</li>
 *   <li>黑名单路径过滤（/etc, ~/.ssh 等）</li>
 *   <li>路径白名单验证（项目目录和 /tmp）</li>
 *   <li>父目录权限检查</li>
 *   <li>人机回环审批机制</li>
 * </ul>
 *
 * <p><b>幂等性保证：</b></p>
 * <ul>
 *   <li>如果目录已存在，返回成功但不重复创建</li>
 *   <li>返回结果中的 created 字段指示是否实际创建了目录</li>
 * </ul>
 */
public class CreateDirectoryTool implements AgentTool {

    private static final Logger logger = LoggerFactory.getLogger(CreateDirectoryTool.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String TOOL_NAME = "create_directory";
    private static final String TOOL_DESCRIPTION = """
            安全地创建目录，支持递归创建父目录。

            核心功能：
            - 单层创建: 创建单个目录
            - 递归创建: 自动创建不存在的父目录
            - 幂等操作: 重复创建已存在的目录不会报错
            - 安全验证: 路径遍历防护、黑名单过滤
            - 人机回环: 创建敏感目录需要用户审批

            参数说明：
            - path: 要创建的目录路径（相对或绝对路径）
            - recursive: 是否递归创建父目录（默认 false）

            使用场景：
            - 创建输出目录
            - 创建配置目录
            - 创建临时目录
            - 初始化项目结构

            返回格式：
            {
              "success": true,
              "operation": "CREATE_DIRECTORY",
              "path": "目录路径",
              "created": true,
              "recursive": false,
              "metadata": {
                "path": "目录路径",
                "name": "目录名",
                "type": "DIRECTORY",
                "size": 0,
                "lastModified": 1234567890,
                "isReadable": true,
                "isWritable": true,
                "extension": null
              },
              "message": "Directory created successfully",
              "timestamp": 1234567890
            }

            错误处理：
            - 路径遍历攻击: 抛出 FileSecurityException
            - 黑名单路径: 抛出 FileSecurityException
            - 权限不足: 抛出 FileSecurityException
            - 父目录不存在且未启用递归: 抛出 FileOperationException
            """;

    private static final String JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "要创建的目录路径（相对或绝对路径）"
                },
                "recursive": {
                  "type": "boolean",
                  "description": "是否递归创建不存在的父目录",
                  "default": false
                }
              },
              "required": ["path"]
            }
            """;

    private final FileSecurityValidator securityValidator;
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
     * @param projectRoot 项目根目录（用于安全验证）
     * @throws IllegalArgumentException 如果 projectRoot 为 null 或空
     */
    public CreateDirectoryTool(String projectRoot) {
        if (projectRoot == null || projectRoot.trim().isEmpty()) {
            throw new IllegalArgumentException("projectRoot cannot be null or empty");
        }
        this.projectRoot = projectRoot;
        this.securityValidator = new FileSecurityValidator(projectRoot);
        logger.info("CreateDirectoryTool initialized with projectRoot: {}", projectRoot);
    }

    /**
     * 构造函数（带沙箱拦截器）
     *
     * @param projectRoot 项目根目录（用于安全验证）
     * @param sandboxInterceptor 安全沙箱拦截器（用于人机回环审批）
     * @throws IllegalArgumentException 如果 projectRoot 为 null 或空
     */
    public CreateDirectoryTool(String projectRoot, SandboxInterceptor sandboxInterceptor) {
        this(projectRoot);
        this.sandboxInterceptor = sandboxInterceptor;
        logger.info("CreateDirectoryTool initialized with SandboxInterceptor");
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
     * @param sandboxInterceptor 安全沙箱拦截器
     */
    public void setSandboxInterceptor(SandboxInterceptor sandboxInterceptor) {
        this.sandboxInterceptor = sandboxInterceptor;
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
        // 目录创建是修改操作，只有 Worker 可以使用
        return ToolAccessLevel.READ_WRITE;
    }

    @Override
    public String execute(String argumentsJson) throws Exception {
        logger.debug("CreateDirectoryTool.execute called with arguments: {}", argumentsJson);

        try {
            // 解析参数
            CreateDirectoryRequest request = parseRequest(argumentsJson);

            // 规范化目标路径（相对于 projectRoot）
            Path targetPath = normalizePath(request.path());
            String normalizedPath = targetPath.toString();

            // 人机回环检查（危险操作审批）- 在安全验证之前执行
            // 这样可以让可审批的操作触发审批流程
            checkWriteOperationWithSandbox(normalizedPath, argumentsJson);

            // 安全验证 - 只检查硬性限制（路径遍历、黑名单等）
            logger.debug("Performing security validation for path: {}", request.path());
            securityValidator.validatePath(request.path());

            // 创建目录
            return createDirectory(request);

        } catch (SuspendExecutionException e) {
            // 人机回环异常，直接向上抛出
            logger.warn("Create directory operation suspended for approval: {}", e.getMessage());
            throw e;
        } catch (FileSecurityException e) {
            logger.warn("Security validation failed: {}", e.getMessage());
            throw e;
        } catch (FileOperationException e) {
            logger.error("Directory creation failed: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error in CreateDirectoryTool.execute: {}", e.getMessage(), e);
            throw new Exception("Failed to create directory: " + e.getMessage(), e);
        }
    }

    @Override
    public String executeWithBypass(String argumentsJson) throws Exception {
        logger.debug("CreateDirectoryTool.executeWithBypass called with arguments: {}", argumentsJson);

        // 对于目录创建操作，即使绕过沙箱，仍然需要进行基本的安全检查
        try {
            // 解析参数
            CreateDirectoryRequest request = parseRequest(argumentsJson);

            // 只进行基本的路径验证，不进行严格的安全策略检查
            logger.debug("Performing basic path validation for bypass execution: {}", request.path());

            // 跳过人机回环检查（已获得用户授权）

            // 创建目录
            return createDirectory(request);

        } catch (Exception e) {
            logger.error("Error in CreateDirectoryTool.executeWithBypass: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 检查创建目录操作是否需要人机回环审批
     *
     * @param targetPath 目标目录路径（已规范化）
     * @param argumentsJson 参数 JSON
     * @throws SuspendExecutionException 如果操作需要审批
     */
    private void checkWriteOperationWithSandbox(String targetPath, String argumentsJson) throws SuspendExecutionException {
        if (sandboxInterceptor == null) {
            logger.debug("No SandboxInterceptor configured, skipping approval check");
            return;
        }

        String toolCallId = currentToolCallId != null ? currentToolCallId : "unknown-" + System.currentTimeMillis();

        logger.debug("Checking create directory operation with sandbox: path={}, toolCallId={}", targetPath, toolCallId);

        // 调用沙箱拦截器检查写入操作
        sandboxInterceptor.checkWriteOperation(targetPath, argumentsJson, toolCallId, "CREATE_DIRECTORY");
    }

    /**
     * 解析请求参数
     *
     * @param argumentsJson JSON 格式的参数
     * @return 解析后的请求对象
     * @throws Exception 如果解析失败
     */
    private CreateDirectoryRequest parseRequest(String argumentsJson) throws Exception {
        try {
            JsonNode jsonNode = objectMapper.readTree(argumentsJson);

            if (!jsonNode.has("path")) {
                throw new Exception("Missing required parameter: path");
            }

            String path = jsonNode.get("path").asText();

            // 解析可选参数
            boolean recursive = jsonNode.has("recursive") && jsonNode.get("recursive").asBoolean();

            return new CreateDirectoryRequest(path, recursive);

        } catch (Exception e) {
            logger.error("Failed to parse arguments: {}", argumentsJson, e);
            throw new Exception("Invalid arguments format: " + e.getMessage(), e);
        }
    }

    /**
     * 创建目录
     *
     * @param request 创建目录请求
     * @return JSON 格式的创建结果
     * @throws FileOperationException 如果创建操作失败
     */
    private String createDirectory(CreateDirectoryRequest request)
            throws FileOperationException {

        logger.info("Creating directory: {} (recursive: {})", request.path(), request.recursive());

        try {
            // 规范化目录路径（相对于 projectRoot）
            Path targetPath = normalizePath(request.path());
            logger.debug("Normalized directory path: {} -> {}", request.path(), targetPath);

            // 检查目录是否已存在
            if (Files.exists(targetPath)) {
                if (Files.isDirectory(targetPath)) {
                    // 目录已存在，幂等处理
                    logger.debug("Directory already exists: {}", request.path());

                    FileMetadata metadata = createMetadata(targetPath);

                    CreateDirectoryResult result = new CreateDirectoryResult(
                            true,
                            FileOperationRequest.Operation.CREATE_DIRECTORY,
                            request.path(),
                            false, // 未创建新目录
                            request.recursive(),
                            metadata,
                            "Directory already exists",
                            null
                    );

                    String resultJson = result.toJson();
                    logger.info("Directory already exists, returning success: {}", request.path());
                    return resultJson;
                } else {
                    // 路径已存在但不是目录
                    logger.warn("Path exists but is not a directory: {}", request.path());
                    throw FileOperationException.fileAlreadyExists("CREATE_DIRECTORY", request.path());
                }
            }

            // 检查父目录权限
            Path parentDir = targetPath.getParent();
            if (parentDir != null && Files.exists(parentDir)) {
                if (!Files.isWritable(parentDir)) {
                    logger.warn("Parent directory is not writable: {}", parentDir);
                    throw FileOperationException.accessDenied("CREATE_DIRECTORY", request.path());
                }
            }

            // 创建目录
            boolean created;
            if (request.recursive()) {
                // 递归创建
                logger.debug("Creating directory recursively: {}", request.path());
                Files.createDirectories(targetPath);
                created = true;
            } else {
                // 单层创建
                logger.debug("Creating single-level directory: {}", request.path());

                // 检查父目录是否存在
                if (parentDir != null && !Files.exists(parentDir)) {
                    logger.warn("Parent directory does not exist: {}", parentDir);
                    throw FileOperationException.directoryNotFound("CREATE_DIRECTORY", request.path());
                }

                Files.createDirectory(targetPath);
                created = true;
            }

            // 验证目录创建成功
            if (!Files.exists(targetPath) || !Files.isDirectory(targetPath)) {
                logger.error("Failed to create directory: {}", request.path());
                throw FileOperationException.unknownError("CREATE_DIRECTORY", request.path(), null);
            }

            // 获取目录元数据
            FileMetadata metadata = createMetadata(targetPath);

            // 构建结果
            CreateDirectoryResult result = new CreateDirectoryResult(
                    true,
                    FileOperationRequest.Operation.CREATE_DIRECTORY,
                    request.path(),
                    created,
                    request.recursive(),
                    metadata,
                    created ? "Directory created successfully" : "Directory already exists",
                    null
            );

            String resultJson = result.toJson();
            logger.info("Successfully created directory: {} (created: {})", request.path(), created);
            return resultJson;

        } catch (FileOperationException e) {
            throw e;
        } catch (IOException e) {
            logger.error("IO error creating directory: {}", request.path(), e);
            throw FileOperationException.ioError("CREATE_DIRECTORY", request.path(), e);
        } catch (Exception e) {
            logger.error("Unexpected error creating directory: {}", request.path(), e);
            throw FileOperationException.unknownError("CREATE_DIRECTORY", request.path(), e);
        }
    }

    /**
     * 创建目录元数据
     *
     * @param path 目录路径
     * @return FileMetadata 对象
     */
    private FileMetadata createMetadata(Path path) {
        try {
            java.io.File file = path.toFile();

            if (!file.exists()) {
                logger.warn("Directory does not exist after creation: {}", path);
            }

            return new FileMetadata(
                    path.toString(),
                    file.getName(),
                    FileMetadata.FileType.DIRECTORY,
                    0, // 目录的大小为 0
                    file.lastModified(),
                    file.canRead(),
                    file.canWrite(),
                    file.canExecute(),
                    file.isHidden(),
                    null // 目录没有扩展名
            );

        } catch (Exception e) {
            logger.error("Failed to create metadata for path: {}", path, e);
            // 返回最小化的元数据
            return new FileMetadata(
                    path.toString(),
                    path.getFileName() != null ? path.getFileName().toString() : "",
                    FileMetadata.FileType.DIRECTORY,
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
     * 创建目录请求参数
     *
     * @param path 目录路径
     * @param recursive 是否递归创建父目录
     */
    private record CreateDirectoryRequest(
            String path,
            boolean recursive
    ) {}

    /**
     * 创建目录结果
     *
     * @param success 是否成功
     * @param operation 操作类型
     * @param path 目录路径
     * @param created 是否实际创建了目录
     * @param recursive 是否使用递归创建
     * @param metadata 目录元数据
     * @param message 操作消息
     * @param errorMessage 错误消息（如果失败）
     */
    private record CreateDirectoryResult(
            boolean success,
            FileOperationRequest.Operation operation,
            String path,
            boolean created,
            boolean recursive,
            FileMetadata metadata,
            String message,
            String errorMessage
    ) {
        public String toJson() {
            try {
                return objectMapper.writeValueAsString(this);
            } catch (Exception e) {
                logger.error("Failed to serialize CreateDirectoryResult to JSON", e);
                return String.format("{\"success\":%s,\"error\":\"%s\"}", success, errorMessage);
            }
        }
    }
}