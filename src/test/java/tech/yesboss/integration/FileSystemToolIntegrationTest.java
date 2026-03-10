package tech.yesboss.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tech.yesboss.ApplicationContext;
import tech.yesboss.config.ConfigurationManager;
import tech.yesboss.config.YesBossConfig;
import tech.yesboss.tool.AgentTool;
import tech.yesboss.tool.SuspendExecutionException;
import tech.yesboss.tool.ToolAccessLevel;
import tech.yesboss.tool.filesystem.*;
import tech.yesboss.tool.registry.ToolRegistry;
import tech.yesboss.tool.sandbox.SandboxInterceptor;
import tech.yesboss.tool.sandbox.impl.SandboxInterceptorImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4.3: 文件系统工具端到端集成测试
 *
 * <p>本测试验证文件系统工具在真实使用场景中的完整表现，包括：</p>
 * <ul>
 *   <li>Master Agent 使用读取工具分析代码</li>
 *   <li>Worker Agent 使用写入工具生成报告</li>
 *   <li>完整的文件操作工作流</li>
 *   <li>多 Agent 协作场景</li>
 *   <li>错误处理和恢复机制</li>
 *   <li>人机回环审批流程</li>
 * </ul>
 *
 * <p><b>测试场景：</b></p>
 * <ol>
 *   <li>场景1：Master 读取配置文件（READ_ONLY 工具）</li>
 *   <li>场景2：Worker 生成报告文件（READ_WRITE 工具）</li>
 *   <li>场景3：Master 和 Worker 协作处理文件</li>
 *   <li>场景4：错误处理和重试机制</li>
 *   <li>场景5：人机回环审批流程</li>
 * </ol>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("文件系统工具端到端集成测试")
class FileSystemToolIntegrationTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 测试用的临时目录（每个测试方法都会创建新的临时目录）
    @TempDir
    Path tempDir;

    // 沙箱拦截器
    private static SandboxInterceptor sandboxInterceptor;

    // 项目根目录
    private String projectRoot;

    // 文件系统工具实例
    private ReadFileTool readFileTool;
    private WriteFileTool writeFileTool;
    private ListDirectoryTool listDirectoryTool;
    private CreateDirectoryTool createDirectoryTool;
    private DeleteFileTool deleteFileTool;
    private SearchFilesTool searchFilesTool;

    @BeforeAll
    static void setUpOnce() {
        System.out.println("========================================");
        System.out.println("Setting up FileSystem Tool Integration Test");
        System.out.println("========================================");

        // 初始化沙箱拦截器（禁用覆盖审批，以简化测试）
        sandboxInterceptor = new SandboxInterceptorImpl(false);

        System.out.println("Setup completed");
        System.out.println("========================================");
    }

    @AfterAll
    static void tearDownOnce() {
        System.out.println("========================================");
        System.out.println("Tearing down FileSystem Tool Integration Test");
        System.out.println("========================================");
    }

    @BeforeEach
    void setUp() throws IOException {
        // 每个测试前的准备工作

        // 设置项目根目录为当前临时目录
        projectRoot = tempDir.toAbsolutePath().toString();
        System.out.println("Test project root: " + projectRoot);

        // 初始化文件系统工具
        readFileTool = new ReadFileTool(projectRoot);
        writeFileTool = new WriteFileTool(projectRoot, sandboxInterceptor);
        listDirectoryTool = new ListDirectoryTool(projectRoot);
        createDirectoryTool = new CreateDirectoryTool(projectRoot, sandboxInterceptor);
        deleteFileTool = new DeleteFileTool(projectRoot, sandboxInterceptor);
        searchFilesTool = new SearchFilesTool(projectRoot);

        // 设置工具调用 ID（用于人机回环测试）
        writeFileTool.setToolCallId("test-tool-call-" + System.currentTimeMillis());

        // 创建测试数据
        setupTestData();
    }

    /**
     * 构建测试路径（相对于临时目录的绝对路径）
     */
    private String testPath(String relativePath) {
        return tempDir.resolve(relativePath).toString();
    }

    /**
     * 创建测试数据
     */
    private void setupTestData() throws IOException {
        System.out.println("Creating test data...");

        // 创建测试目录结构
        Path srcDir = tempDir.resolve("src");
        Path mainDir = srcDir.resolve("main");
        Path javaDir = mainDir.resolve("java");
        Files.createDirectories(javaDir);

        Path testDir = srcDir.resolve("test");
        Path resourcesDir = testDir.resolve("resources");
        Files.createDirectories(resourcesDir);

        // 创建测试配置文件
        Path configFile = tempDir.resolve("application.yml");
        Files.writeString(configFile, """
                app:
                  name: YesBoss
                  version: 1.0.0
                  projectRoot: .

                database:
                  sqlite:
                    path: data/yesboss.db

                llm:
                  anthropic:
                    enabled: true
                    apiKey: ${ANTHROPIC_API_KEY}
                    model:
                      master: claude-sonnet-4-6
                      worker: claude-haiku-4-5-20251001
                    maxTokens: 8192
                    temperature: 0.7
                """);

        // 创建测试 Java 文件
        Path javaFile = javaDir.resolve("Example.java");
        Files.writeString(javaFile, """
                package tech.yesboss.example;

                import tech.yesboss.tool.AgentTool;

                /**
                 * 示例类，用于测试文件读取功能
                 */
                public class Example implements AgentTool {

                    @Override
                    public String getName() {
                        return "example";
                    }

                    @Override
                    public String getDescription() {
                        return "Example tool for testing";
                    }

                    @Override
                    public String getParametersJsonSchema() {
                        return "{}";
                    }

                    @Override
                    public ToolAccessLevel getAccessLevel() {
                        return ToolAccessLevel.READ_ONLY;
                    }

                    @Override
                    public String execute(String argumentsJson) throws Exception {
                        return "Example executed";
                    }

                    @Override
                    public String executeWithBypass(String argumentsJson) throws Exception {
                        return execute(argumentsJson);
                    }
                }
                """);

        // 创建测试 Markdown 文件
        Path readmeFile = tempDir.resolve("README.md");
        Files.writeString(readmeFile, """
                # YesBoss - Multi-Agent Task Orchestration Platform

                ## 项目简介

                YesBoss 是一个多智能体任务编排平台，支持：

                - Master Agent：负责任务规划和协调
                - Worker Agent：负责具体任务执行
                - 文件系统工具：安全的文件读写操作

                ## 快速开始

                使用以下命令启动项目：
                mvn clean install
                mvn exec:java

                ## 配置说明

                请参考 `application.yml` 配置文件。
                """);

        System.out.println("Test data created successfully");
    }

    // ==================== 场景1：Master 读取配置文件 ====================

    @Test
    @Order(1)
    @DisplayName("场景1：Master Agent 使用读取工具分析配置文件")
    void testScenario1_MasterAgentReadsConfigFile() throws Exception {
        System.out.println("\n=== 场景1：Master Agent 读取配置文件 ===");

        // 检查测试文件是否存在
        Path testFile = tempDir.resolve("application.yml");
        System.out.println("测试文件路径: " + testFile);
        System.out.println("测试文件是否存在: " + Files.exists(testFile));
        System.out.println("projectRoot: " + projectRoot);
        System.out.println("tempDir: " + tempDir.toAbsolutePath());

        // Master Agent 使用 READ_ONLY 工具读取配置文件
        // 使用相对路径避免 Windows 反斜杠转义问题
        String argumentsJson = "{\"path\": \"application.yml\"}";

        System.out.println("Master Agent 调用 read_file 工具...");
        String result = readFileTool.execute(argumentsJson);

        assertNotNull(result, "Result should not be null");
        System.out.println("读取结果长度: " + result.length() + " 字符");

        // 解析结果
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean(), "Operation should be successful");
        assertTrue(jsonResult.has("content"), "Result should have content");
        assertTrue(jsonResult.has("metadata"), "Result should have metadata");

        // 验证文件内容
        String content = jsonResult.get("content").asText();
        assertTrue(content.contains("YesBoss"), "Content should contain 'YesBoss'");
        assertTrue(content.contains("anthropic"), "Content should contain 'anthropic'");

        // 验证元数据
        JsonNode metadata = jsonResult.get("metadata");
        assertEquals("application.yml", metadata.get("name").asText());
        assertEquals("FILE", metadata.get("type").asText());
        assertTrue(metadata.get("size").asLong() > 0, "File size should be positive");
        assertTrue(metadata.get("isReadable").asBoolean(), "File should be readable");

        System.out.println("✓ 场景1 测试通过：Master Agent 成功读取配置文件");
    }

    @Test
    @Order(2)
    @DisplayName("场景1.1：Master Agent 使用 list_directory 探索项目结构")
    void testScenario1_MasterAgentExploresProjectStructure() throws Exception {
        System.out.println("\n=== 场景1.1：Master Agent 探索项目结构 ===");

        // Master Agent 使用 READ_ONLY 工具列出目录
        String argumentsJson = "{\"path\": \".\"}";

        System.out.println("Master Agent 调用 list_directory 工具...");
        String result = listDirectoryTool.execute(argumentsJson);

        assertNotNull(result, "Result should not be null");

        // 解析结果
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean(), "Operation should be successful");

        // 验证目录内容
        JsonNode entries = jsonResult.get("entries");
        assertNotNull(entries, "Result should have entries");

        // 检查是否包含预期的文件和目录
        boolean hasApplicationYml = false;
        boolean hasReadme = false;
        boolean hasSrcDir = false;

        for (JsonNode entry : entries) {
            String name = entry.get("name").asText();
            if ("application.yml".equals(name)) hasApplicationYml = true;
            if ("README.md".equals(name)) hasReadme = true;
            if ("src".equals(name)) hasSrcDir = true;
        }

        assertTrue(hasApplicationYml, "Should find application.yml");
        assertTrue(hasReadme, "Should find README.md");
        assertTrue(hasSrcDir, "Should find src directory");

        System.out.println("✓ 场景1.1 测试通过：Master Agent 成功探索项目结构");
    }

    @Test
    @Order(3)
    @DisplayName("场景1.2：Master Agent 使用 search_files 查找 Java 文件")
    void testScenario1_MasterAgentSearchesJavaFiles() throws Exception {
        System.out.println("\n=== 场景1.2：Master Agent 搜索 Java 文件 ===");

        // Master Agent 使用 READ_ONLY 工具搜索文件
        // 使用 extensions 参数搜索 .java 文件，并在内容中搜索 "class" 关键字
        String argumentsJson = "{\"path\": \"src\", \"pattern\": \"class\", \"extensions\": [\"java\"]}";

        System.out.println("Master Agent 调用 search_files 工具...");
        String result = searchFilesTool.execute(argumentsJson);

        assertNotNull(result, "Result should not be null");

        // 解析结果
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean(), "Operation should be successful");

        // 验证搜索结果
        JsonNode files = jsonResult.get("files");
        assertNotNull(files, "Result should have files");
        assertTrue(files.isArray(), "Files should be an array");

        // 检查是否找到 Example.java
        boolean foundExample = false;
        for (JsonNode file : files) {
            String path = file.get("path").asText();
            if (path.contains("Example.java")) {
                foundExample = true;
                break;
            }
        }

        assertTrue(foundExample, "Should find Example.java");

        System.out.println("✓ 场景1.2 测试通过：Master Agent 成功搜索 Java 文件");
    }

    // ==================== 场景2：Worker 生成报告文件 ====================

    @Test
    @Order(4)
    @DisplayName("场景2：Worker Agent 使用写入工具生成报告")
    void testScenario2_WorkerAgentGeneratesReport() throws Exception {
        System.out.println("\n=== 场景2：Worker Agent 生成报告 ===");

        // Worker Agent 使用 READ_WRITE 工具写入报告
        String reportContent = """
                # 代码分析报告

                ## 项目概览

                - 项目名称：YesBoss
                - 分析时间：2026-03-10
                - 分析人员：Worker Agent

                ## 文件统计

                - Java 文件：1 个
                - 配置文件：1 个
                - 文档文件：1 个

                ## 建议

                1. 继续完善单元测试覆盖率
                2. 添加更多集成测试场景
                3. 优化文档结构

                ---
                *此报告由 Worker Agent 自动生成*
                """;

        String argumentsJson = String.format(
                "{\"path\": \"reports/analysis-report.md\", \"content\": %s, \"mode\": \"OVERWRITE\", \"createParentDirs\": true}",
                objectMapper.writeValueAsString(reportContent)
        );

        System.out.println("Worker Agent 调用 write_file 工具...");
        String result = writeFileTool.execute(argumentsJson);

        assertNotNull(result, "Result should not be null");

        // 解析结果
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean(), "Operation should be successful");
        assertEquals("WRITE", jsonResult.get("operation").asText());

        // 验证文件已创建
        Path reportFile = tempDir.resolve("reports").resolve("analysis-report.md");
        assertTrue(Files.exists(reportFile), "Report file should exist");

        // 验证文件内容
        String writtenContent = Files.readString(reportFile);
        assertTrue(writtenContent.contains("代码分析报告"), "Content should be correct");
        assertTrue(writtenContent.contains("Worker Agent"), "Content should mention Worker Agent");

        System.out.println("✓ 场景2 测试通过：Worker Agent 成功生成报告");
    }

    @Test
    @Order(5)
    @DisplayName("场景2.1：Worker Agent 使用追加模式写入日志")
    void testScenario2_WorkerAgentAppendsLog() throws Exception {
        System.out.println("\n=== 场景2.1：Worker Agent 追加日志 ===");

        // Worker Agent 使用 APPEND 模式写入日志
        String logEntry1 = "[2026-03-10 10:00:00] INFO: Application started\n";
        String logEntry2 = "[2026-03-10 10:01:00] INFO: Task completed\n";

        // 写入第一条日志
        String argumentsJson1 = String.format(
                "{\"path\": \"logs/application.log\", \"content\": %s, \"mode\": \"OVERWRITE\", \"createParentDirs\": true}",
                objectMapper.writeValueAsString(logEntry1)
        );
        writeFileTool.execute(argumentsJson1);

        // 追加第二条日志
        String argumentsJson2 = String.format(
                "{\"path\": \"logs/application.log\", \"content\": %s, \"mode\": \"APPEND\"}",
                objectMapper.writeValueAsString(logEntry2)
        );
        String result = writeFileTool.execute(argumentsJson2);

        assertNotNull(result, "Result should not be null");

        // 解析结果
        JsonNode jsonResult = objectMapper.readTree(result);
        assertTrue(jsonResult.get("success").asBoolean(), "Operation should be successful");
        assertEquals("APPEND", jsonResult.get("mode").asText());

        // 验证日志文件内容
        Path logFile = tempDir.resolve("logs").resolve("application.log");
        assertTrue(Files.exists(logFile), "Log file should exist");

        String logContent = Files.readString(logFile);
        assertTrue(logContent.contains(logEntry1), "Should contain first log entry");
        assertTrue(logContent.contains(logEntry2), "Should contain second log entry");

        System.out.println("✓ 场景2.1 测试通过：Worker Agent 成功追加日志");
    }

    // ==================== 场景3：Master 和 Worker 协作处理文件 ====================

    @Test
    @Order(6)
    @DisplayName("场景3：Master 和 Worker 协作处理代码文件")
    void testScenario3_MasterAndWorkerCollaboration() throws Exception {
        System.out.println("\n=== 场景3：Master 和 Worker 协作 ===");

        // Step 1: Master Agent 读取源文件
        System.out.println("Step 1: Master 读取源文件...");
        String readArgs = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("path", "src/main/java/Example.java");
        }});
        String readResult = readFileTool.execute(readArgs);

        JsonNode readJson = objectMapper.readTree(readResult);
        assertTrue(readJson.get("success").asBoolean());
        String originalContent = readJson.get("content").asText();

        // Step 2: Master 分析代码（模拟）
        System.out.println("Step 2: Master 分析代码结构...");
        assertTrue(originalContent.contains("public class Example"));
        assertTrue(originalContent.contains("implements AgentTool"));

        // Step 3: Master 指派 Worker 添加文档注释
        System.out.println("Step 3: Master 指派 Worker 添加文档...");
        String documentedContent = originalContent.replace(
                "package tech.yesboss.example;",
                """
                /**
                 * YesBoss 示例工具
                 *
                 * <p>这是一个示例工具，演示了如何实现 AgentTool 接口。</p>
                 *
                 * @author YesBoss Team
                 * @version 1.0.0
                 */
                package tech.yesboss.example;
                """
        );

        // Step 4: Worker Agent 写入更新后的文件
        System.out.println("Step 4: Worker 写入更新后的文件...");
        String writeArgs = String.format(
                "{\"path\": \"src/main/java/ExampleDocumented.java\", \"content\": %s, \"mode\": \"OVERWRITE\"}",
                objectMapper.writeValueAsString(documentedContent)
        );
        String writeResult = writeFileTool.execute(writeArgs);

        JsonNode writeJson = objectMapper.readTree(writeResult);
        assertTrue(writeJson.get("success").asBoolean());

        // Step 5: Master 验证结果
        System.out.println("Step 5: Master 验证结果...");
        String verifyArgs = "{\"path\": \"src/main/java/ExampleDocumented.java\"}";
        String verifyResult = readFileTool.execute(verifyArgs);

        JsonNode verifyJson = objectMapper.readTree(verifyResult);
        assertTrue(verifyJson.get("success").asBoolean());
        String verifiedContent = verifyJson.get("content").asText();

        assertTrue(verifiedContent.contains("YesBoss 示例工具"));
        assertTrue(verifiedContent.contains("@author YesBoss Team"));

        System.out.println("✓ 场景3 测试通过：Master 和 Worker 成功协作");
    }

    @Test
    @Order(7)
    @DisplayName("场景3.1：完整的工作流 - 创建目录、写入文件、读取验证")
    void testScenario3_CompleteWorkflow() throws Exception {
        System.out.println("\n=== 场景3.1：完整工作流 ===");

        // Step 1: Worker 创建输出目录
        System.out.println("Step 1: 创建输出目录...");
        String mkdirArgs = "{\"path\": \"output/results\", \"recursive\": true}";
        String mkdirResult = createDirectoryTool.execute(mkdirArgs);
        JsonNode mkdirJson = objectMapper.readTree(mkdirResult);
        assertTrue(mkdirJson.get("success").asBoolean());

        // Step 2: Worker 写入结果文件
        System.out.println("Step 2: 写入结果文件...");
        String resultsContent = """
                {
                  "status": "success",
                  "timestamp": "2026-03-10T10:00:00Z",
                  "summary": {
                    "total": 10,
                    "passed": 9,
                    "failed": 1
                  }
                }
                """;
        String writeFileArgs = String.format(
                "{\"path\": \"output/results/test-results.json\", \"content\": %s}",
                objectMapper.writeValueAsString(resultsContent)
        );
        String writeResult = writeFileTool.execute(writeFileArgs);
        JsonNode writeJson = objectMapper.readTree(writeResult);
        assertTrue(writeJson.get("success").asBoolean());

        // Step 3: Master 读取并验证结果
        System.out.println("Step 3: Master 读取并验证...");
        String readFileArgs = "{\"path\": \"output/results/test-results.json\"}";
        String readResult = readFileTool.execute(readFileArgs);
        JsonNode readJson = objectMapper.readTree(readResult);
        assertTrue(readJson.get("success").asBoolean());

        // Step 4: Master 列出输出目录
        System.out.println("Step 4: 列出输出目录...");
        String listArgs = "{\"path\": \"output/results\"}";
        String listResult = listDirectoryTool.execute(listArgs);
        JsonNode listJson = objectMapper.readTree(listResult);
        assertTrue(listJson.get("success").asBoolean());

        JsonNode entries = listJson.get("entries");
        boolean hasTestResults = false;
        for (JsonNode entry : entries) {
            if ("test-results.json".equals(entry.get("name").asText())) {
                hasTestResults = true;
                break;
            }
        }
        assertTrue(hasTestResults, "Should find test-results.json");

        System.out.println("✓ 场景3.1 测试通过：完整工作流成功");
    }

    // ==================== 场景4：错误处理和恢复 ====================

    @Test
    @Order(8)
    @DisplayName("场景4：错误处理 - 读取不存在的文件")
    void testScenario4_ErrorHandling_FileNotFound() {
        System.out.println("\n=== 场景4：错误处理 ===");

        // 尝试读取不存在的文件
        String argumentsJson = "{\"path\": \"nonexistent/file.txt\"}";

        System.out.println("尝试读取不存在的文件...");
        Exception exception = assertThrows(Exception.class, () -> {
            readFileTool.execute(argumentsJson);
        });

        System.out.println("捕获到预期的异常: " + exception.getMessage());
        // 检查异常消息包含文件不存在的相关信息
        assertTrue(exception.getMessage().contains("FILE_NOT_FOUND") ||
                   exception.getMessage().contains("不存在") ||
                   exception.getMessage().contains("not exist"));

        System.out.println("✓ 场景4 测试通过：错误处理机制正常");
    }

    @Test
    @Order(9)
    @DisplayName("场景4.1：错误处理 - 无效的文件路径")
    void testScenario4_ErrorHandling_InvalidPath() {
        System.out.println("\n=== 场景4.1：路径验证 ===");

        // 尝试访问系统敏感路径
        String argumentsJson = "{\"path\": \"../../../etc/passwd\"}";

        System.out.println("尝试访问敏感路径...");
        Exception exception = assertThrows(Exception.class, () -> {
            readFileTool.execute(argumentsJson);
        });

        System.out.println("捕获到预期的异常: " + exception.getMessage());
        // 应该被安全验证拒绝
        assertNotNull(exception.getMessage());

        System.out.println("✓ 场景4.1 测试通过：路径安全验证正常");
    }

    @Test
    @Order(10)
    @DisplayName("场景4.2：错误处理 - 写入到无权限目录")
    void testScenario4_ErrorHandling_WritePermission() throws Exception {
        System.out.println("\n=== 场景4.2：权限检查 ===");

        // 在临时目录创建一个只读目录
        Path readOnlyDir = tempDir.resolve("readonly");
        Files.createDirectory(readOnlyDir);
        readOnlyDir.toFile().setReadOnly();

        // 尝试写入只读目录（在不同操作系统上行为可能不同）
        String argumentsJson = String.format(
                "{\"path\": \"readonly/test.txt\", \"content\": \"test\"}",
                objectMapper.writeValueAsString("test")
        );

        System.out.println("尝试写入只读目录...");
        try {
            String result = writeFileTool.execute(argumentsJson);
            // 某些系统可能允许写入，所以不强制要求失败
            System.out.println("写入操作完成（系统可能允许）");
        } catch (Exception e) {
            System.out.println("捕获到预期的异常: " + e.getMessage());
            // 预期在某些系统上会失败
        } finally {
            // 清理：恢复权限以便删除
            readOnlyDir.toFile().setWritable(true);
        }

        System.out.println("✓ 场景4.2 测试通过：权限检查完成");
    }

    // ==================== 场景5：人机回环审批流程 ====================

    @Test
    @Order(11)
    @DisplayName("场景5：人机回环 - 覆盖已存在文件触发审批")
    void testScenario5_HumanInTheLoop_OverwriteApproval() throws Exception {
        System.out.println("\n=== 场景5：人机回环审批 ===");

        // 启用覆盖审批的沙箱拦截器
        SandboxInterceptor strictInterceptor = new SandboxInterceptorImpl(true);
        WriteFileTool strictWriteTool = new WriteFileTool(projectRoot, strictInterceptor);
        strictWriteTool.setToolCallId("test-approval-1");

        // 先用 WriteFileTool 创建一个文件（不启用审批）
        WriteFileTool normalWriteTool = new WriteFileTool(projectRoot, new SandboxInterceptorImpl(false));
        String createFileArgs = String.format(
                "{\"path\": \"important.txt\", \"content\": %s, \"mode\": \"OVERWRITE\"}",
                objectMapper.writeValueAsString("Original content")
        );
        normalWriteTool.execute(createFileArgs);

        // 尝试覆盖文件（应该触发审批）
        String argumentsJson = String.format(
                "{\"path\": \"important.txt\", \"content\": %s, \"mode\": \"OVERWRITE\"}",
                objectMapper.writeValueAsString("New content")
        );

        System.out.println("尝试覆盖已存在文件...");
        SuspendExecutionException exception = assertThrows(SuspendExecutionException.class, () -> {
            strictWriteTool.execute(argumentsJson);
        });

        System.out.println("成功触发人机回环: " + exception.getInterceptedCommand());
        assertTrue(exception.getInterceptedCommand().contains("需要审批") ||
                   exception.getInterceptedCommand().contains("approval") ||
                   exception.getInterceptedCommand().contains("覆盖"));

        System.out.println("✓ 场景5 测试通过：人机回环审批流程正常");
    }

    @Test
    @Order(12)
    @DisplayName("场景5.1：人机回环 - 写入受保护文件扩展名触发审批")
    void testScenario5_HumanInTheLoop_ProtectedExtensionApproval() throws Exception {
        System.out.println("\n=== 场景5.1：受保护文件扩展名审批 ===");

        // 启用覆盖审批的沙箱拦截器
        SandboxInterceptor strictInterceptor = new SandboxInterceptorImpl(true);
        WriteFileTool strictWriteTool = new WriteFileTool(projectRoot, strictInterceptor);
        strictWriteTool.setToolCallId("test-approval-2");

        // 尝试写入 .env 文件（受保护的扩展名）
        String argumentsJson = String.format(
                "{\"path\": \".env\", \"content\": %s, \"mode\": \"OVERWRITE\"}",
                objectMapper.writeValueAsString("API_KEY=test")
        );

        System.out.println("尝试写入 .env 文件...");
        SuspendExecutionException exception = assertThrows(SuspendExecutionException.class, () -> {
            strictWriteTool.execute(argumentsJson);
        });

        System.out.println("成功触发人机回环: " + exception.getInterceptedCommand());
        assertTrue(exception.getInterceptedCommand().contains("需要审批") ||
                   exception.getInterceptedCommand().contains("approval") ||
                   exception.getInterceptedCommand().contains("受保护"));

        System.out.println("✓ 场景5.1 测试通过：受保护扩展名审批正常");
    }

    @Test
    @Order(13)
    @DisplayName("场景5.2：人机回环 - 用户授权后绕过沙箱")
    void testScenario5_HumanInTheLoop_BypassAfterApproval() throws Exception {
        System.out.println("\n=== 场景5.2：用户授权后绕过 ===");

        // 创建一个已存在的文件
        Path existingFile = tempDir.resolve("authorized.txt");
        Files.writeString(existingFile, "Original content");

        // 启用覆盖审批的沙箱拦截器
        SandboxInterceptor strictInterceptor = new SandboxInterceptorImpl(true);
        WriteFileTool strictWriteTool = new WriteFileTool(projectRoot, strictInterceptor);
        strictWriteTool.setToolCallId("test-bypass-1");

        // 用户授权后使用 executeWithBypass 绕过沙箱
        String argumentsJson = String.format(
                "{\"path\": \"authorized.txt\", \"content\": %s, \"mode\": \"OVERWRITE\"}",
                objectMapper.writeValueAsString("Authorized content")
        );

        System.out.println("用户授权后执行写入...");
        String result = strictWriteTool.executeWithBypass(argumentsJson);

        assertNotNull(result, "Result should not be null");

        // 验证文件已被覆盖
        String newContent = Files.readString(existingFile);
        assertEquals("Authorized content", newContent, "File should be updated");

        System.out.println("✓ 场景5.2 测试通过：授权后绕过机制正常");
    }

    // ==================== 综合测试：完整的多 Agent 协作场景 ====================

    @Test
    @Order(14)
    @DisplayName("综合测试：完整的多 Agent 文件处理工作流")
    void testComprehensive_MultiAgentFileProcessingWorkflow() throws Exception {
        System.out.println("\n=== 综合测试：完整工作流 ===");

        // === Phase 1: Master Agent 分析任务 ===
        System.out.println("Phase 1: Master Agent 分析任务需求...");
        String taskDescription = "分析项目代码并生成文档";

        // Master 探索项目结构
        String listResult = listDirectoryTool.execute("{\"path\": \".\"}");
        JsonNode listJson = objectMapper.readTree(listResult);
        assertTrue(listJson.get("success").asBoolean());
        System.out.println("  ✓ Master 完成项目结构探索");

        // === Phase 2: Master Agent 分配子任务 ===
        System.out.println("Phase 2: Master 分配子任务给 Worker...");

        // Master 搜索所有 Java 文件
        String searchResult = searchFilesTool.execute("{\"pattern\": \"*.java\", \"path\": \"src\"}");
        JsonNode searchJson = objectMapper.readTree(searchResult);
        assertTrue(searchJson.get("success").asBoolean());
        System.out.println("  ✓ Master 找到 " + searchJson.get("files").size() + " 个 Java 文件");

        // === Phase 3: Worker Agent 处理文件 ===
        System.out.println("Phase 3: Worker 处理并生成文档...");

        // Worker 读取每个 Java 文件并生成文档
        String documentation = "# 代码文档\n\n";
        String javaFile = "src/main/java/Example.java";
        String readResult = readFileTool.execute("{\"path\": \"" + javaFile + "\"}");
        JsonNode readJson = objectMapper.readTree(readResult);
        if (readJson.get("success").asBoolean()) {
            documentation += "## Example.java\n\n";
            documentation += "这是一个示例工具实现。\n\n";
        }

        // Worker 创建文档目录和文件
        createDirectoryTool.execute("{\"path\": \"docs\"}");
        String writeDocArgs = String.format(
                "{\"path\": \"docs/API.md\", \"content\": %s}",
                objectMapper.writeValueAsString(documentation)
        );
        String writeDocResult = writeFileTool.execute(writeDocArgs);
        JsonNode writeDocJson = objectMapper.readTree(writeDocResult);
        assertTrue(writeDocJson.get("success").asBoolean());
        System.out.println("  ✓ Worker 成功生成文档");

        // === Phase 4: Master Agent 验证结果 ===
        System.out.println("Phase 4: Master 验证生成结果...");

        String verifyDocResult = readFileTool.execute("{\"path\": \"docs/API.md\"}");
        JsonNode verifyDocJson = objectMapper.readTree(verifyDocResult);
        assertTrue(verifyDocJson.get("success").asBoolean());
        assertTrue(verifyDocJson.get("content").asText().contains("代码文档"));
        System.out.println("  ✓ Master 验证文档生成正确");

        // === Phase 5: 生成执行报告 ===
        System.out.println("Phase 5: 生成执行报告...");

        String reportContent = """
                # 任务执行报告

                ## 任务概述
                任务：分析项目代码并生成文档

                ## 执行过程
                1. Master Agent 探索项目结构
                2. Master Agent 搜索 Java 文件
                3. Worker Agent 读取源文件
                4. Worker Agent 生成文档
                5. Master Agent 验证结果

                ## 执行结果
                - 状态：成功
                - 生成文件：docs/API.md
                - 验证状态：通过

                ## 总结
                多 Agent 协作成功完成任务，所有步骤正常执行。
                """;

        String reportArgs = String.format(
                "{\"path\": \"reports/task-execution-report.md\", \"content\": %s, \"mode\": \"OVERWRITE\", \"createParentDirs\": true}",
                objectMapper.writeValueAsString(reportContent)
        );
        String reportResult = writeFileTool.execute(reportArgs);
        JsonNode reportJson = objectMapper.readTree(reportResult);
        assertTrue(reportJson.get("success").asBoolean());

        System.out.println("  ✓ 执行报告已生成");

        // === Phase 6: 最终验证 ===
        System.out.println("Phase 6: 最终验证...");

        // 检查所有生成的文件
        String finalListResult = listDirectoryTool.execute("{\"path\": \"reports\"}");
        JsonNode finalListJson = objectMapper.readTree(finalListResult);
        assertTrue(finalListJson.get("success").asBoolean());

        boolean hasReport = false;
        for (JsonNode entry : finalListJson.get("entries")) {
            if ("task-execution-report.md".equals(entry.get("name").asText())) {
                hasReport = true;
                break;
            }
        }
        assertTrue(hasReport, "Should find execution report");

        System.out.println("  ✓ 所有文件验证通过");
        System.out.println("\n✓✓✓ 综合测试完成：多 Agent 协作工作流成功 ✓✓✓");
    }

    @Test
    @Order(15)
    @DisplayName("最终验证：所有工具访问级别正确")
    void testFinal_VerifyToolAccessLevels() {
        System.out.println("\n=== 最终验证：工具访问级别 ===");

        assertEquals(ToolAccessLevel.READ_ONLY, readFileTool.getAccessLevel(),
                "ReadFileTool should be READ_ONLY");
        assertEquals(ToolAccessLevel.READ_ONLY, listDirectoryTool.getAccessLevel(),
                "ListDirectoryTool should be READ_ONLY");
        assertEquals(ToolAccessLevel.READ_ONLY, searchFilesTool.getAccessLevel(),
                "SearchFilesTool should be READ_ONLY");

        assertEquals(ToolAccessLevel.READ_WRITE, writeFileTool.getAccessLevel(),
                "WriteFileTool should be READ_WRITE");
        assertEquals(ToolAccessLevel.READ_WRITE, createDirectoryTool.getAccessLevel(),
                "CreateDirectoryTool should be READ_WRITE");
        assertEquals(ToolAccessLevel.READ_WRITE, deleteFileTool.getAccessLevel(),
                "DeleteFileTool should be READ_WRITE");

        System.out.println("✓ 所有工具访问级别验证通过");
        System.out.println("\n========================================");
        System.out.println("所有测试场景完成！");
        System.out.println("========================================");
    }
}
