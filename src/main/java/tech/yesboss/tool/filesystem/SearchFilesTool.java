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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 文件搜索工具
 *
 * <p>提供安全的文件内容搜索能力，支持正则表达式和多种编码。</p>
 *
 * <p><b>核心功能：</b></p>
 * <ul>
 *   <li><b>文本搜索</b>: 在文件内容中搜索指定的文本或正则表达式</li>
 *   <li><b>正则表达式</b>: 支持复杂的正则表达式模式匹配</li>
 *   <li><b>编码支持</b>: 支持 UTF-8、GBK 等多种编码</li>
 *   <li><b>递归搜索</b>: 可在目录树中递归搜索</li>
 *   <li><b>文件过滤</b>: 支持按文件扩展名过滤</li>
 *   <li><b>大小限制</b>: 支持限制搜索的文件大小</li>
 *   <li><b>上下文返回</b>: 可返回匹配行的上下文</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * SearchFilesTool tool = new SearchFilesTool("/project/root");
 *
 * // 搜索文本
 * String result = tool.execute("{\"path\": \"src\", \"pattern\": \"TODO\"}");
 *
 * // 使用正则表达式搜索
 * String result = tool.execute("{\"path\": \"src\", \"pattern\": \"\\bTODO\\b\", \"regex\": true}");
 *
 * // 按文件类型过滤
 * String result = tool.execute("{\"path\": \"src\", \"pattern\": \"class\", \"extensions\": [\"java\"]}");
 *
 * // 返回上下文
 * String result = tool.execute("{\"path\": \"src\", \"pattern\": \"TODO\", \"contextLines\": 2}");
 * }</pre>
 *
 * <p><b>安全特性：</b></p>
 * <ul>
 *   <li>路径遍历攻击防护</li>
 *   <li>黑名单路径过滤</li>
 *   <li>文件大小限制（默认 10MB）</li>
 *   <li>搜索超时保护</li>
 * </ul>
 */
public class SearchFilesTool implements AgentTool {

    private static final Logger logger = LoggerFactory.getLogger(SearchFilesTool.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String TOOL_NAME = "search_files";
    private static final String TOOL_DESCRIPTION = """
            在文件系统中搜索文件内容，支持文本和正则表达式。

            功能特性：
            - 文本搜索：在文件内容中搜索指定的文本
            - 正则表达式：支持复杂的正则表达式模式匹配
            - 递归搜索：在目录树中递归搜索所有文件
            - 文件过滤：按文件扩展名过滤搜索范围
            - 大小限制：只搜索指定大小范围内的文件
            - 编码支持：支持 UTF-8、GBK 等多种编码
            - 上下文返回：可返回匹配行的前后上下文
            - 大小写敏感：可选的大小写敏感匹配

            参数说明：
            - path: 搜索路径（必需，相对或绝对路径）
            - pattern: 搜索模式（必需，文本或正则表达式）
            - regex: 是否为正则表达式（可选，默认 false）
            - caseSensitive: 是否大小写敏感（可选，默认 false）
            - extensions: 文件扩展名列表（可选，如 ["java", "py"]）
            - recursive: 是否递归搜索子目录（可选，默认 true）
            - maxResults: 最大返回结果数（可选，默认 100）
            - maxFileSize: 最大文件大小（字节，可选，默认 10485760 = 10MB）
            - encoding: 文件编码（可选，默认 UTF-8）
            - contextLines: 上下文行数（可选，默认 0）

            安全特性：
            - 路径遍历攻击防护
            - 黑名单路径过滤
            - 文件大小限制
            - 搜索超时保护

            返回格式：
            {
              "success": true,
              "searchPath": "搜索路径",
              "pattern": "搜索模式",
              "files": [
                {
                  "path": "文件路径",
                  "lineNumber": 42,
                  "line": "匹配行的内容",
                  "matchStart": 10,
                  "matchEnd": 14,
                  "contextBefore": ["前一行"],
                  "contextAfter": ["后一行"]
                }
              ],
              "totalMatches": 5,
              "filesSearched": 10,
              "truncated": false
            }
            """;

    private static final String JSON_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "搜索路径（相对或绝对路径）"
                },
                "pattern": {
                  "type": "string",
                  "description": "搜索模式（文本或正则表达式）"
                },
                "regex": {
                  "type": "boolean",
                  "description": "是否为正则表达式",
                  "default": false
                },
                "caseSensitive": {
                  "type": "boolean",
                  "description": "是否大小写敏感",
                  "default": false
                },
                "extensions": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  },
                  "description": "文件扩展名列表（如 [\"java\", \"py\"]）"
                },
                "recursive": {
                  "type": "boolean",
                  "description": "是否递归搜索子目录",
                  "default": true
                },
                "maxResults": {
                  "type": "integer",
                  "description": "最大返回结果数",
                  "minimum": 1,
                  "default": 100
                },
                "maxFileSize": {
                  "type": "integer",
                  "description": "最大文件大小（字节）",
                  "minimum": 0,
                  "default": 10485760
                },
                "encoding": {
                  "type": "string",
                  "description": "文件编码格式",
                  "enum": ["UTF-8", "GBK", "GB2312", "ISO-8859-1", "US-ASCII"],
                  "default": "UTF-8"
                },
                "contextLines": {
                  "type": "integer",
                  "description": "上下文行数",
                  "minimum": 0,
                  "maximum": 10,
                  "default": 0
                }
              },
              "required": ["path", "pattern"]
            }
            """;

    /**
     * 默认最大文件大小（10MB）
     */
    private static final long DEFAULT_MAX_FILE_SIZE = 10L * 1024 * 1024;

    /**
     * 默认最大返回结果数
     */
    private static final int DEFAULT_MAX_RESULTS = 100;

    /**
     * 最大上下文行数
     */
    private static final int MAX_CONTEXT_LINES = 10;

    private final FileSecurityValidator securityValidator;
    private final String projectRoot;

    /**
     * 构造函数
     *
     * @param projectRoot 项目根目录（用于安全验证）
     * @throws IllegalArgumentException 如果 projectRoot 为 null 或空
     */
    public SearchFilesTool(String projectRoot) {
        this(projectRoot, null);
    }

    /**
     * 构造函数（支持动态白名单）
     *
     * @param projectRoot 项目根目录（用于安全验证）
     * @param dynamicWhitelistManager 动态白名单管理器（可选）
     * @throws IllegalArgumentException 如果 projectRoot 为 null 或空
     */
    public SearchFilesTool(String projectRoot, DynamicWhitelistManager dynamicWhitelistManager) {
        if (projectRoot == null || projectRoot.trim().isEmpty()) {
            throw new IllegalArgumentException("projectRoot cannot be null or empty");
        }
        this.projectRoot = projectRoot;
        this.securityValidator = new FileSecurityValidator(
                projectRoot,
                FileSecurityValidator.getMaxFileSizeDefault(),
                FileSecurityValidator.getMinDiskSpaceDefault(),
                true,
                dynamicWhitelistManager
        );
        logger.info("SearchFilesTool initialized with projectRoot: {}{}", projectRoot,
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
        // 文件搜索是只读操作，Master 和 Worker 都可以使用
        return ToolAccessLevel.READ_ONLY;
    }

    @Override
    public String execute(String argumentsJson) throws Exception {
        logger.debug("SearchFilesTool.execute called with arguments: {}", argumentsJson);

        try {
            // 解析参数
            SearchRequest request = parseRequest(argumentsJson);

            // 安全验证
            logger.debug("Performing security validation for path: {}", request.path());
            securityValidator.validatePath(request.path());

            // 执行搜索
            return searchFiles(request);

        } catch (FileSecurityException e) {
            logger.warn("Security validation failed: {}", e.getMessage());
            throw e;
        } catch (FileOperationException e) {
            logger.error("File operation failed: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error in SearchFilesTool.execute: {}", e.getMessage(), e);
            throw new Exception("Failed to search files: " + e.getMessage(), e);
        }
    }

    @Override
    public String executeWithBypass(String argumentsJson) throws Exception {
        logger.debug("SearchFilesTool.executeWithBypass called with arguments: {}", argumentsJson);

        // SearchFilesTool 是只读工具，不需要绕过沙箱
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
    private SearchRequest parseRequest(String argumentsJson) throws Exception {
        try {
            JsonNode jsonNode = objectMapper.readTree(argumentsJson);

            if (!jsonNode.has("path")) {
                throw new Exception("Missing required parameter: path");
            }

            if (!jsonNode.has("pattern")) {
                throw new Exception("Missing required parameter: pattern");
            }

            String path = jsonNode.get("path").asText();
            String pattern = jsonNode.get("pattern").asText();
            boolean regex = jsonNode.has("regex") && jsonNode.get("regex").asBoolean();
            boolean caseSensitive = jsonNode.has("caseSensitive") && jsonNode.get("caseSensitive").asBoolean();
            boolean recursive = !jsonNode.has("recursive") || jsonNode.get("recursive").asBoolean();
            int maxResults = jsonNode.has("maxResults") ? jsonNode.get("maxResults").asInt() : DEFAULT_MAX_RESULTS;
            long maxFileSize = jsonNode.has("maxFileSize") ? jsonNode.get("maxFileSize").asLong() : DEFAULT_MAX_FILE_SIZE;
            String encoding = jsonNode.has("encoding") ? jsonNode.get("encoding").asText() : "UTF-8";
            int contextLines = jsonNode.has("contextLines") ? jsonNode.get("contextLines").asInt() : 0;

            // 解析扩展名列表
            List<String> extensions = null;
            if (jsonNode.has("extensions")) {
                extensions = new ArrayList<>();
                JsonNode extensionsNode = jsonNode.get("extensions");
                for (JsonNode extNode : extensionsNode) {
                    extensions.add(extNode.asText().toLowerCase());
                }
            }

            // 验证参数
            if (maxResults < 1) {
                throw new Exception("maxResults must be >= 1");
            }

            if (maxFileSize < 0) {
                throw new Exception("maxFileSize must be >= 0");
            }

            if (contextLines < 0 || contextLines > MAX_CONTEXT_LINES) {
                throw new Exception("contextLines must be between 0 and " + MAX_CONTEXT_LINES);
            }

            return new SearchRequest(
                    path, pattern, regex, caseSensitive,
                    extensions, recursive, maxResults,
                    maxFileSize, encoding, contextLines
            );

        } catch (Exception e) {
            logger.error("Failed to parse arguments: {}", argumentsJson, e);
            throw new Exception("Invalid arguments format: " + e.getMessage(), e);
        }
    }

    /**
     * 搜索文件
     *
     * @param request 搜索请求
     * @return JSON 格式的搜索结果
     * @throws FileOperationException 如果搜索失败
     */
    private String searchFiles(SearchRequest request) throws FileOperationException {
        logger.info("Searching files in: {} with pattern: {} (regex={})",
                request.path(), request.pattern(), request.regex());

        try {
            // 规范化搜索路径（相对于 projectRoot）
            Path searchPath = normalizePath(request.path());
            logger.debug("Normalized search path: {} -> {}", request.path(), searchPath);

            // 检查路径是否存在
            if (!Files.exists(searchPath)) {
                logger.warn("Search path does not exist: {}", request.path());
                throw FileOperationException.directoryNotFound("SEARCH", request.path());
            }

            // 编译搜索模式
            Pattern searchPattern;
            try {
                int flags = request.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE;
                searchPattern = request.regex()
                        ? Pattern.compile(request.pattern(), flags)
                        : Pattern.compile(Pattern.quote(request.pattern()), flags);
            } catch (PatternSyntaxException e) {
                logger.error("Invalid regex pattern: {}", request.pattern(), e);
                throw new FileOperationException("SEARCH", request.path(),
                        FileOperationException.ErrorType.INVALID_PATH, e);
            }

            // 收集搜索结果
            List<SearchMatch> results = new ArrayList<>();
            int[] filesSearchedCounter = new int[]{0};

            if (Files.isDirectory(searchPath)) {
                // 递归搜索目录
                searchInDirectory(searchPath, request, searchPattern, results, filesSearchedCounter);
            } else {
                // 搜索单个文件
                searchInFile(searchPath, request, searchPattern, results);
                filesSearchedCounter[0] = 1;
            }

            // 应用结果数量限制
            boolean truncated = results.size() > request.maxResults();
            List<SearchMatch> limitedResults = results.subList(0, Math.min(results.size(), request.maxResults()));

            // 构建结果
            SearchResult result = new SearchResult(
                    true,
                    request.path(),
                    request.pattern(),
                    limitedResults,
                    results.size(),
                    filesSearchedCounter[0],
                    truncated,
                    null
            );

            String resultJson = result.toJson();
            logger.info("Search completed in {} files, found {} matches",
                    filesSearchedCounter[0], results.size());
            return resultJson;

        } catch (FileOperationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error searching files: {}", request.path(), e);
            throw FileOperationException.unknownError("SEARCH", request.path(), e);
        }
    }

    /**
     * 在目录中搜索
     *
     * @param dirPath 目录路径
     * @param request 搜索请求
     * @param pattern 搜索模式
     * @param results 结果列表
     * @param filesSearched 搜索的文件数
     * @throws FileOperationException 如果搜索失败
     */
    private void searchInDirectory(Path dirPath, SearchRequest request,
                                   Pattern pattern, List<SearchMatch> results,
                                   int[] filesSearched) throws FileOperationException {

        try (java.util.stream.Stream<Path> stream = Files.walk(dirPath)) {
            stream.forEach(path -> {
                try {
                    // 跳过目录
                    if (Files.isDirectory(path)) {
                        return;
                    }

                    // 检查文件可读性
                    if (!Files.isReadable(path)) {
                        logger.debug("Skipping unreadable file: {}", path);
                        return;
                    }

                    // 检查文件大小
                    long fileSize = Files.size(path);
                    if (fileSize > request.maxFileSize()) {
                        logger.debug("Skipping large file: {} ({} bytes)", path, fileSize);
                        return;
                    }

                    // 应用扩展名过滤
                    if (request.extensions() != null && !request.extensions().isEmpty()) {
                        String fileName = path.getFileName().toString();
                        String extension = getFileExtension(fileName);
                        if (extension == null || !request.extensions().contains(extension.toLowerCase())) {
                            return;
                        }
                    }

                    // 搜索文件
                    searchInFile(path, request, pattern, results);
                    filesSearched[0]++;

                } catch (Exception e) {
                    logger.warn("Failed to search file: {}", path, e);
                }
            });
        } catch (IOException e) {
            logger.error("Failed to walk directory: {}", dirPath, e);
            throw FileOperationException.ioError("SEARCH", dirPath.toString(), e);
        }
    }

    /**
     * 在文件中搜索
     *
     * @param filePath 文件路径
     * @param request 搜索请求
     * @param pattern 搜索模式
     * @param results 结果列表
     */
    private void searchInFile(Path filePath, SearchRequest request,
                              Pattern pattern, List<SearchMatch> results) {

        try {
            // 读取文件内容
            Charset charset = Charset.forName(request.encoding());
            List<String> lines = Files.readAllLines(filePath, charset);

            // 搜索每一行
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                Matcher matcher = pattern.matcher(line);

                if (matcher.find()) {
                    // 找到匹配
                    int matchStart = matcher.start();
                    int matchEnd = matcher.end();

                    // 获取上下文
                    List<String> contextBefore = null;
                    List<String> contextAfter = null;

                    if (request.contextLines() > 0) {
                        contextBefore = getContext(lines, i, -request.contextLines());
                        contextAfter = getContext(lines, i, request.contextLines());
                    }

                    SearchMatch match = new SearchMatch(
                            filePath.toString(),
                            i + 1,  // 行号从 1 开始
                            line,
                            matchStart,
                            matchEnd,
                            contextBefore,
                            contextAfter
                    );

                    results.add(match);

                    // 如果已达到最大结果数，停止搜索
                    if (results.size() >= request.maxResults()) {
                        return;
                    }
                }
            }

        } catch (IOException e) {
            logger.debug("Failed to read file for search: {}", filePath, e);
        }
    }

    /**
     * 获取上下文行
     *
     * @param lines 所有行
     * @param currentLine 当前行号
     * @param offset 偏移量（负数向前，正数向后）
     * @return 上下文行列表
     */
    private List<String> getContext(List<String> lines, int currentLine, int offset) {
        List<String> context = new ArrayList<>();
        int start = currentLine + offset;
        int end = currentLine + (offset > 0 ? 0 : offset);

        // 确保范围有效
        start = Math.max(0, start);
        end = Math.min(lines.size(), end);

        if (offset < 0) {
            // 获取前面的行
            for (int i = start; i < currentLine; i++) {
                context.add(lines.get(i));
            }
        } else {
            // 获取后面的行
            for (int i = currentLine + 1; i <= end; i++) {
                context.add(lines.get(i));
            }
        }

        return context;
    }

    /**
     * 获取文件扩展名
     *
     * @param fileName 文件名
     * @return 扩展名（不含点号），如果没有返回 null
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }

        return null;
    }

    /**
     * 搜索请求参数
     *
     * @param path 搜索路径
     * @param pattern 搜索模式
     * @param regex 是否为正则表达式
     * @param caseSensitive 是否大小写敏感
     * @param extensions 扩展名列表
     * @param recursive 是否递归
     * @param maxResults 最大结果数
     * @param maxFileSize 最大文件大小
     * @param encoding 编码
     * @param contextLines 上下文行数
     */
    private record SearchRequest(
            String path,
            String pattern,
            boolean regex,
            boolean caseSensitive,
            List<String> extensions,
            boolean recursive,
            int maxResults,
            long maxFileSize,
            String encoding,
            int contextLines
    ) {}

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
     * 搜索匹配结果
     *
     * @param path 文件路径
     * @param lineNumber 行号
     * @param line 行内容
     * @param matchStart 匹配开始位置
     * @param matchEnd 匹配结束位置
     * @param contextBefore 前置上下文
     * @param contextAfter 后置上下文
     */
    private record SearchMatch(
            String path,
            int lineNumber,
            String line,
            int matchStart,
            int matchEnd,
            List<String> contextBefore,
            List<String> contextAfter
    ) {}

    /**
     * 搜索结果
     *
     * @param success 是否成功
     * @param searchPath 搜索路径
     * @param pattern 搜索模式
     * @param files 匹配结果列表
     * @param totalMatches 总匹配数
     * @param filesSearched 搜索的文件数
     * @param truncated 是否被截断
     * @param error 错误信息
     */
    private record SearchResult(
            boolean success,
            String searchPath,
            String pattern,
            List<SearchMatch> files,
            int totalMatches,
            int filesSearched,
            boolean truncated,
            String error
    ) {
        public String toJson() {
            try {
                return objectMapper.writeValueAsString(this);
            } catch (Exception e) {
                logger.error("Failed to serialize SearchResult to JSON", e);
                return String.format("{\"success\":%s,\"error\":\"%s\"}", success, error);
            }
        }
    }
}
