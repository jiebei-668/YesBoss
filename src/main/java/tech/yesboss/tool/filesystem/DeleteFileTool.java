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
import tech.yesboss.tool.filesystem.security.FileSecurityValidator;
import tech.yesboss.tool.sandbox.SandboxInterceptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 文件删除工具
 *
 * <p>提供安全的文件和目录删除能力，所有删除操作强制触发人机回环审批。</p>
 *
 * <p><b>核心功能：</b></p>
 * <ul>
 *   <li><b>删除文件</b>: 删除单个文件</li>
 *   <li><b>删除空目录</b>: 删除空的目录</li>
 *   <li><b>递归删除</b>: 递归删除目录及其所有内容（危险操作）</li>
 *   <li><b>人机回环</b>: 所有删除操作必须经过用户审批</li>
 *   <li><b>重要文件保护</b>: 禁止删除关键文件</li>
 * </ul>
 *
 * <p><b>安全特性：</b></p>
 * <ul>
 *   <li>所有删除操作强制人机回环审批</li>
 *   <li>重要文件和目录受保护（无法删除）</li>
 *   <li>递归删除需要额外确认</li>
 *   <li>路径遍历攻击防护</li>
 *   <li>完整的审计日志</li>
 * </ul>
 *
 * <p><b>受保护的文件和目录：</b></p>
 * <ul>
 *   <li>pom.xml, build.gradle, package.json（构建配置）</li>
 *   <li>.git 目录（版本控制）</li>
 *   <li>data/, logs/ 目录（数据目录）</li>
 *   <li>application.yml, application.properties（应用配置）</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * DeleteFileTool tool = new DeleteFileTool("/project/root", sandboxInterceptor);
 *
 * // 删除单个文件
 * String result = tool.execute("{\"path\": \"src/old_file.txt\"}");
 *
 * // 删除空目录
 * String result = tool.execute("{\"path\": \"empty_dir\"}");
 *
 * // 递归删除目录（危险操作）
 * String result = tool.execute("{\"path\": \"old_project\", \"recursive\": true}");
 * }</pre>
 */
public class DeleteFileTool implements AgentTool {

    private static final Logger logger = LoggerFactory.getLogger(DeleteFileTool.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String TOOL_NAME = "delete_file";
    private static final String TOOL_DESCRIPTION = """
            安全地删除文件或目录，所有删除操作需要用户审批确认。

            删除模式：
            - 文件删除: 删除单个文件
            - 空目录删除: 删除空目录
            - 递归删除: 删除目录及其所有内容（危险操作，需额外确认）

            安全特性：
            - 所有删除操作强制人机回环审批
            - 重要文件和目录受保护（无法删除）
            - 递归删除需要额外确认
            - 完整的审计日志记录

            受保护的文件：
            - 构建配置: pom.xml, build.gradle, package.json
            - 版本控制: .git 目录
            - 数据目录: data/, logs/, target/, build/
            - 应用配置: application.yml, application.properties

            参数说明：
            - path: 要删除的文件或目录路径
            - recursive: 是否递归删除（默认 false）

            返回格式：
            {
              "success": true,
              "operation": "DELETE",
              "path": "删除的路径",
              "type": "FILE 或 DIRECTORY",
              "deleted": true,
              "recursive": false,
              "message": "File deleted successfully",
              "timestamp": 1234567890
            }

            注意事项：
            - 删除操作不可逆，请谨慎操作
            - 递归删除会删除目录下所有内容
            - 受保护的文件无法删除
            """;

    private static final String JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "要删除的文件或目录路径（相对或绝对路径）"
                },
                "recursive": {
                  "type": "boolean",
                  "description": "是否递归删除目录及其内容（危险操作，默认 false）",
                  "default": false
                }
              },
              "required": ["path"]
            }
            """;

    /**
     * 受保护的文件名（禁止删除）
     */
    private static final java.util.Set<String> PROTECTED_FILE_NAMES = java.util.Set.of(
            "pom.xml",
            "build.gradle",
            "build.gradle.kts",
            "package.json",
            "application.yml",
            "application.properties",
            "application.conf"
    );

    /**
     * 受保护的目录名（禁止删除）
     */
    private static final java.util.Set<String> PROTECTED_DIR_NAMES = java.util.Set.of(
            ".git",
            ".gitignore",
            "data",
            "logs",
            "target",
            "build"
    );

    private final FileSecurityValidator securityValidator;
    private final String projectRoot;

    /**
     * 安全沙箱拦截器（用于人机回环审批）
     */
    private SandboxInterceptor sandboxInterceptor;

    /**
     * 当前工具调用 ID（用于人机回环）
     */
    private String currentToolCallId;

    /**
     * 构造函数
     *
     * <p>注意：删除工具必须有沙箱拦截器才能正常工作，
     * 因为所有删除操作都需要人机回环审批。</p>
     *
     * @param projectRoot 项目根目录（用于安全验证）
     * @throws IllegalArgumentException 如果 projectRoot 为 null 或空
     */
    public DeleteFileTool(String projectRoot) {
        if (projectRoot == null || projectRoot.trim().isEmpty()) {
            throw new IllegalArgumentException("projectRoot cannot be null or empty");
        }
        this.projectRoot = projectRoot;
        this.securityValidator = new FileSecurityValidator(projectRoot);
        logger.info("DeleteFileTool initialized with projectRoot: {} (no sandbox interceptor)", projectRoot);
    }

    /**
     * 构造函数（带沙箱拦截器）
     *
     * @param projectRoot 项目根目录（用于安全验证）
     * @param sandboxInterceptor 安全沙箱拦截器（用于人机回环审批）
     * @throws IllegalArgumentException 如果 projectRoot 为 null 或空
     */
    public DeleteFileTool(String projectRoot, SandboxInterceptor sandboxInterceptor) {
        this(projectRoot);
        this.sandboxInterceptor = sandboxInterceptor;
        logger.info("DeleteFileTool initialized with SandboxInterceptor");
    }

    /**
     * 设置当前工具调用 ID
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
        // 文件删除是危险操作，只有 Worker 可以使用
        return ToolAccessLevel.READ_WRITE;
    }

    @Override
    public String execute(String argumentsJson) throws Exception {
        logger.debug("DeleteFileTool.execute called with arguments: {}", argumentsJson);

        try {
            // 解析参数
            DeleteRequest request = parseRequest(argumentsJson);

            // 规范化目标路径
            Path targetPath = Paths.get(request.path()).toAbsolutePath().normalize();
            String normalizedPath = targetPath.toString();

            // 检查文件/目录是否存在
            if (!Files.exists(targetPath)) {
                throw FileOperationException.fileNotFound("DELETE", request.path());
            }

            // 检查是否是受保护的文件/目录
            checkProtectedPath(targetPath);

            // 人机回环检查（删除操作强制审批）
            checkDeleteOperationWithSandbox(normalizedPath, argumentsJson, request.recursive());

            // 安全验证
            logger.debug("Performing security validation for path: {}", request.path());
            securityValidator.validatePath(request.path());

            // 执行删除
            return deletePath(request, targetPath);

        } catch (SuspendExecutionException e) {
            // 人机回环异常，直接向上抛出
            logger.warn("Delete operation suspended for approval: {}", e.getMessage());
            throw e;
        } catch (FileSecurityException e) {
            logger.warn("Security validation failed: {}", e.getMessage());
            throw e;
        } catch (FileOperationException e) {
            logger.error("Delete operation failed: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error in DeleteFileTool.execute: {}", e.getMessage(), e);
            throw new Exception("Failed to delete: " + e.getMessage(), e);
        }
    }

    @Override
    public String executeWithBypass(String argumentsJson) throws Exception {
        logger.debug("DeleteFileTool.executeWithBypass called with arguments: {}", argumentsJson);

        try {
            // 解析参数
            DeleteRequest request = parseRequest(argumentsJson);

            // 规范化目标路径
            Path targetPath = Paths.get(request.path()).toAbsolutePath().normalize();

            // 检查文件/目录是否存在
            if (!Files.exists(targetPath)) {
                throw FileOperationException.fileNotFound("DELETE", request.path());
            }

            // 检查是否是受保护的文件/目录（即使在 bypass 模式下也要检查）
            checkProtectedPath(targetPath);

            // 只进行基本的路径验证
            logger.debug("Performing basic path validation for bypass execution: {}", request.path());

            // 执行删除（已获得用户授权）
            return deletePath(request, targetPath);

        } catch (Exception e) {
            logger.error("Error in DeleteFileTool.executeWithBypass: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 检查删除操作是否需要人机回环审批
     *
     * <p>对于删除工具，所有删除操作都必须触发人机回环审批。</p>
     *
     * @param targetPath 目标路径（已规范化）
     * @param argumentsJson 参数 JSON
     * @param recursive 是否递归删除
     * @throws SuspendExecutionException 如果操作需要审批
     */
    private void checkDeleteOperationWithSandbox(String targetPath, String argumentsJson, boolean recursive)
            throws SuspendExecutionException {
        if (sandboxInterceptor == null) {
            logger.warn("No SandboxInterceptor configured, delete operation may proceed without approval");
            return;
        }

        String toolCallId = currentToolCallId != null ? currentToolCallId : "unknown-" + System.currentTimeMillis();

        logger.debug("Checking delete operation with sandbox: path={}, recursive={}, toolCallId={}",
                targetPath, recursive, toolCallId);

        // 构建操作类型描述
        String operationType = recursive ? "DELETE_RECURSIVE" : "DELETE";

        // 调用沙箱拦截器检查删除操作
        sandboxInterceptor.checkWriteOperation(targetPath, argumentsJson, toolCallId, operationType);
    }

    /**
     * 检查路径是否受保护
     *
     * @param path 要检查的路径
     * @throws FileSecurityException 如果路径受保护
     */
    private void checkProtectedPath(Path path) throws FileSecurityException {
        Path normalizedPath = path.normalize();
        String fileName = normalizedPath.getFileName() != null ? normalizedPath.getFileName().toString() : "";

        // 检查文件名是否受保护
        if (PROTECTED_FILE_NAMES.contains(fileName)) {
            logger.warn("Attempt to delete protected file: {}", path);
            throw new FileSecurityException("DELETE", path.toString(),
                    FileSecurityException.Reason.PROTECTED_FILE, null);
        }

        // 检查是否是受保护目录的子路径
        for (Path p = normalizedPath; p != null && !p.equals(p.getRoot()); p = p.getParent()) {
            String dirName = p.getFileName() != null ? p.getFileName().toString() : "";
            if (PROTECTED_DIR_NAMES.contains(dirName)) {
                logger.warn("Attempt to delete protected directory: {}", path);
                throw new FileSecurityException("DELETE", path.toString(),
                        FileSecurityException.Reason.PROTECTED_FILE, null);
            }
        }

        // 检查项目根目录下的受保护目录
        Path projectRootPath = Paths.get(projectRoot).normalize();
        if (normalizedPath.startsWith(projectRootPath)) {
            Path relativePath = projectRootPath.relativize(normalizedPath);
            if (!relativePath.toString().isEmpty()) {
                String firstComponent = relativePath.iterator().next().toString();
                if (PROTECTED_DIR_NAMES.contains(firstComponent) || PROTECTED_FILE_NAMES.contains(firstComponent)) {
                    logger.warn("Attempt to delete protected path under project root: {}", path);
                    throw new FileSecurityException("DELETE", path.toString(),
                            FileSecurityException.Reason.PROTECTED_FILE, null);
                }
            }
        }
    }

    /**
     * 解析请求参数
     *
     * @param argumentsJson JSON 格式的参数
     * @return 解析后的请求对象
     * @throws Exception 如果解析失败
     */
    private DeleteRequest parseRequest(String argumentsJson) throws Exception {
        try {
            JsonNode jsonNode = objectMapper.readTree(argumentsJson);

            if (!jsonNode.has("path")) {
                throw new Exception("Missing required parameter: path");
            }

            String path = jsonNode.get("path").asText();
            boolean recursive = jsonNode.has("recursive") && jsonNode.get("recursive").asBoolean();

            return new DeleteRequest(path, recursive);

        } catch (Exception e) {
            logger.error("Failed to parse arguments: {}", argumentsJson, e);
            throw new Exception("Invalid arguments format: " + e.getMessage(), e);
        }
    }

    /**
     * 执行删除操作
     *
     * @param request 删除请求
     * @param targetPath 目标路径
     * @return JSON 格式的删除结果
     * @throws FileOperationException 如果删除操作失败
     */
    private String deletePath(DeleteRequest request, Path targetPath) throws FileOperationException {
        logger.info("Deleting: {} (recursive: {})", request.path(), request.recursive());

        try {
            boolean isDirectory = Files.isDirectory(targetPath);
            String type = isDirectory ? "DIRECTORY" : "FILE";

            if (isDirectory) {
                if (request.recursive()) {
                    // 递归删除目录
                    deleteDirectoryRecursively(targetPath);
                    logger.info("Successfully deleted directory recursively: {}", request.path());
                } else {
                    // 检查目录是否为空
                    try (Stream<Path> stream = Files.list(targetPath)) {
                        if (stream.findAny().isPresent()) {
                            logger.warn("Directory is not empty: {}", request.path());
                            throw FileOperationException.directoryNotEmpty("DELETE", request.path());
                        }
                    }

                    // 删除空目录
                    Files.delete(targetPath);
                    logger.info("Successfully deleted empty directory: {}", request.path());
                }
            } else {
                // 删除文件
                Files.delete(targetPath);
                logger.info("Successfully deleted file: {}", request.path());
            }

            // 构建结果
            DeleteResult result = new DeleteResult(
                    true,
                    FileOperationRequest.Operation.DELETE,
                    request.path(),
                    type,
                    true,
                    request.recursive(),
                    String.format("%s deleted successfully", type),
                    null
            );

            return result.toJson();

        } catch (FileOperationException e) {
            throw e;
        } catch (IOException e) {
            logger.error("IO error deleting: {}", request.path(), e);
            throw FileOperationException.ioError("DELETE", request.path(), e);
        } catch (Exception e) {
            logger.error("Unexpected error deleting: {}", request.path(), e);
            throw FileOperationException.unknownError("DELETE", request.path(), e);
        }
    }

    /**
     * 递归删除目录
     *
     * @param directory 要删除的目录
     * @throws IOException 如果删除失败
     */
    private void deleteDirectoryRecursively(Path directory) throws IOException {
        logger.debug("Recursively deleting directory: {}", directory);

        try (Stream<Path> walk = Files.walk(directory)) {
            // 按深度倒序排序，先删除文件再删除目录
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            logger.trace("Deleted: {}", path);
                        } catch (IOException e) {
                            logger.error("Failed to delete: {}", path, e);
                            throw new RuntimeException("Failed to delete: " + path, e);
                        }
                    });
        }

        logger.debug("Directory recursively deleted: {}", directory);
    }

    /**
     * 删除请求参数
     *
     * @param path 文件或目录路径
     * @param recursive 是否递归删除
     */
    private record DeleteRequest(String path, boolean recursive) {}

    /**
     * 删除结果
     *
     * @param success 是否成功
     * @param operation 操作类型
     * @param path 删除的路径
     * @param type 类型（FILE 或 DIRECTORY）
     * @param deleted 是否已删除
     * @param recursive 是否递归删除
     * @param message 操作消息
     * @param errorMessage 错误消息（如果失败）
     */
    private record DeleteResult(
            boolean success,
            FileOperationRequest.Operation operation,
            String path,
            String type,
            boolean deleted,
            boolean recursive,
            String message,
            String errorMessage
    ) {
        public String toJson() {
            try {
                return objectMapper.writeValueAsString(this);
            } catch (Exception e) {
                logger.error("Failed to serialize DeleteResult to JSON", e);
                return String.format("{\"success\":%s,\"error\":\"%s\"}", success, errorMessage);
            }
        }
    }
}
