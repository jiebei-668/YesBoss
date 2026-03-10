package tech.yesboss;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import tech.yesboss.config.YesBossConfig;
import tech.yesboss.tool.AgentTool;
import tech.yesboss.tool.ToolAccessLevel;
import tech.yesboss.tool.registry.ToolRegistry;
import tech.yesboss.tool.filesystem.ReadFileTool;
import tech.yesboss.tool.filesystem.WriteFileTool;
import tech.yesboss.tool.filesystem.ListDirectoryTool;
import tech.yesboss.tool.filesystem.CreateDirectoryTool;
import tech.yesboss.tool.filesystem.DeleteFileTool;
import tech.yesboss.tool.filesystem.SearchFilesTool;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试文件系统工具注册到 ApplicationContext
 *
 * <p>验证所有 6 个文件系统工具正确注册到 ToolRegistry，
 * 并且可以通过名称检索，元数据正确。</p>
 */
@DisplayName("文件系统工具注册测试")
class FileSystemToolRegistrationTest {

    private ApplicationContext applicationContext;
    private YesBossConfig config;

    @BeforeEach
    void setUp() {
        // 创建配置
        config = new YesBossConfig();
        // 初始化 AppConfig（如果为 null）
        if (config.getApp() == null) {
            config.setApp(new YesBossConfig.AppConfig());
        }
        config.getApp().setProjectRoot(System.getProperty("user.dir"));

        // 创建 ApplicationContext（但不初始化整个应用，只测试工具注册）
        applicationContext = new ApplicationContext(config);
    }

    @Test
    @DisplayName("验证所有文件系统工具类可以被实例化")
    void testFilesystemToolsInstantiation() {
        String projectRoot = System.getProperty("user.dir");

        // 测试所有工具类可以被实例化
        assertDoesNotThrow(() -> {
            ReadFileTool readFileTool = new ReadFileTool(projectRoot);
            assertNotNull(readFileTool);
            assertEquals("read_file", readFileTool.getName());
            assertEquals(ToolAccessLevel.READ_ONLY, readFileTool.getAccessLevel());
        }, "ReadFileTool should be instantiable");

        assertDoesNotThrow(() -> {
            ListDirectoryTool listDirectoryTool = new ListDirectoryTool(projectRoot);
            assertNotNull(listDirectoryTool);
            assertEquals("list_directory", listDirectoryTool.getName());
            assertEquals(ToolAccessLevel.READ_ONLY, listDirectoryTool.getAccessLevel());
        }, "ListDirectoryTool should be instantiable");

        assertDoesNotThrow(() -> {
            SearchFilesTool searchFilesTool = new SearchFilesTool(projectRoot);
            assertNotNull(searchFilesTool);
            assertEquals("search_files", searchFilesTool.getName());
            assertEquals(ToolAccessLevel.READ_ONLY, searchFilesTool.getAccessLevel());
        }, "SearchFilesTool should be instantiable");

        assertDoesNotThrow(() -> {
            WriteFileTool writeFileTool = new WriteFileTool(projectRoot);
            assertNotNull(writeFileTool);
            assertEquals("write_file", writeFileTool.getName());
            assertEquals(ToolAccessLevel.READ_WRITE, writeFileTool.getAccessLevel());
        }, "WriteFileTool should be instantiable");

        assertDoesNotThrow(() -> {
            CreateDirectoryTool createDirectoryTool = new CreateDirectoryTool(projectRoot);
            assertNotNull(createDirectoryTool);
            assertEquals("create_directory", createDirectoryTool.getName());
            assertEquals(ToolAccessLevel.READ_WRITE, createDirectoryTool.getAccessLevel());
        }, "CreateDirectoryTool should be instantiable");

        assertDoesNotThrow(() -> {
            DeleteFileTool deleteFileTool = new DeleteFileTool(projectRoot);
            assertNotNull(deleteFileTool);
            assertEquals("delete_file", deleteFileTool.getName());
            assertEquals(ToolAccessLevel.READ_WRITE, deleteFileTool.getAccessLevel());
        }, "DeleteFileTool should be instantiable");
    }

    @Test
    @DisplayName("验证工具名称正确")
    void testToolNames() {
        String projectRoot = System.getProperty("user.dir");

        assertEquals("read_file", new ReadFileTool(projectRoot).getName());
        assertEquals("list_directory", new ListDirectoryTool(projectRoot).getName());
        assertEquals("search_files", new SearchFilesTool(projectRoot).getName());
        assertEquals("write_file", new WriteFileTool(projectRoot).getName());
        assertEquals("create_directory", new CreateDirectoryTool(projectRoot).getName());
        assertEquals("delete_file", new DeleteFileTool(projectRoot).getName());
    }

    @Test
    @DisplayName("验证工具访问级别正确")
    void testToolAccessLevels() {
        String projectRoot = System.getProperty("user.dir");

        // READ_ONLY 工具
        assertEquals(ToolAccessLevel.READ_ONLY, new ReadFileTool(projectRoot).getAccessLevel(),
                "ReadFileTool should be READ_ONLY");
        assertEquals(ToolAccessLevel.READ_ONLY, new ListDirectoryTool(projectRoot).getAccessLevel(),
                "ListDirectoryTool should be READ_ONLY");
        assertEquals(ToolAccessLevel.READ_ONLY, new SearchFilesTool(projectRoot).getAccessLevel(),
                "SearchFilesTool should be READ_ONLY");

        // READ_WRITE 工具
        assertEquals(ToolAccessLevel.READ_WRITE, new WriteFileTool(projectRoot).getAccessLevel(),
                "WriteFileTool should be READ_WRITE");
        assertEquals(ToolAccessLevel.READ_WRITE, new CreateDirectoryTool(projectRoot).getAccessLevel(),
                "CreateDirectoryTool should be READ_WRITE");
        assertEquals(ToolAccessLevel.READ_WRITE, new DeleteFileTool(projectRoot).getAccessLevel(),
                "DeleteFileTool should be READ_WRITE");
    }

    @Test
    @DisplayName("验证工具描述和 JSON Schema 非空")
    void testToolMetadata() {
        String projectRoot = System.getProperty("user.dir");

        // 测试 ReadFileTool
        AgentTool readFileTool = new ReadFileTool(projectRoot);
        assertNotNull(readFileTool.getDescription());
        assertFalse(readFileTool.getDescription().isEmpty());
        assertNotNull(readFileTool.getParametersJsonSchema());
        assertFalse(readFileTool.getParametersJsonSchema().isEmpty());

        // 测试 ListDirectoryTool
        AgentTool listDirectoryTool = new ListDirectoryTool(projectRoot);
        assertNotNull(listDirectoryTool.getDescription());
        assertFalse(listDirectoryTool.getDescription().isEmpty());
        assertNotNull(listDirectoryTool.getParametersJsonSchema());
        assertFalse(listDirectoryTool.getParametersJsonSchema().isEmpty());

        // 测试 SearchFilesTool
        AgentTool searchFilesTool = new SearchFilesTool(projectRoot);
        assertNotNull(searchFilesTool.getDescription());
        assertFalse(searchFilesTool.getDescription().isEmpty());
        assertNotNull(searchFilesTool.getParametersJsonSchema());
        assertFalse(searchFilesTool.getParametersJsonSchema().isEmpty());

        // 测试 WriteFileTool
        AgentTool writeFileTool = new WriteFileTool(projectRoot);
        assertNotNull(writeFileTool.getDescription());
        assertFalse(writeFileTool.getDescription().isEmpty());
        assertNotNull(writeFileTool.getParametersJsonSchema());
        assertFalse(writeFileTool.getParametersJsonSchema().isEmpty());

        // 测试 CreateDirectoryTool
        AgentTool createDirectoryTool = new CreateDirectoryTool(projectRoot);
        assertNotNull(createDirectoryTool.getDescription());
        assertFalse(createDirectoryTool.getDescription().isEmpty());
        assertNotNull(createDirectoryTool.getParametersJsonSchema());
        assertFalse(createDirectoryTool.getParametersJsonSchema().isEmpty());

        // 测试 DeleteFileTool
        AgentTool deleteFileTool = new DeleteFileTool(projectRoot);
        assertNotNull(deleteFileTool.getDescription());
        assertFalse(deleteFileTool.getDescription().isEmpty());
        assertNotNull(deleteFileTool.getParametersJsonSchema());
        assertFalse(deleteFileTool.getParametersJsonSchema().isEmpty());
    }

    @Test
    @DisplayName("验证工具可以注册到 ToolRegistry")
    void testToolRegistration() {
        ToolRegistry registry = new tech.yesboss.tool.registry.impl.ToolRegistryImpl();
        String projectRoot = System.getProperty("user.dir");

        // 注册所有工具
        assertDoesNotThrow(() -> {
            registry.register(new ReadFileTool(projectRoot));
            registry.register(new ListDirectoryTool(projectRoot));
            registry.register(new SearchFilesTool(projectRoot));
            registry.register(new WriteFileTool(projectRoot));
            registry.register(new CreateDirectoryTool(projectRoot));
            registry.register(new DeleteFileTool(projectRoot));
        }, "All tools should be registered successfully");

        // 验证工具数量
        assertEquals(6, registry.getToolCount(), "ToolRegistry should contain 6 tools");
    }

    @Test
    @DisplayName("验证工具可以从 ToolRegistry 按名称检索")
    void testToolRetrievalByName() {
        ToolRegistry registry = new tech.yesboss.tool.registry.impl.ToolRegistryImpl();
        String projectRoot = System.getProperty("user.dir");

        // 注册所有工具
        registry.register(new ReadFileTool(projectRoot));
        registry.register(new ListDirectoryTool(projectRoot));
        registry.register(new SearchFilesTool(projectRoot));
        registry.register(new WriteFileTool(projectRoot));
        registry.register(new CreateDirectoryTool(projectRoot));
        registry.register(new DeleteFileTool(projectRoot));

        // 验证可以按名称检索
        assertDoesNotThrow(() -> registry.getTool("read_file"),
                "read_file should be retrievable");
        assertDoesNotThrow(() -> registry.getTool("list_directory"),
                "list_directory should be retrievable");
        assertDoesNotThrow(() -> registry.getTool("search_files"),
                "search_files should be retrievable");
        assertDoesNotThrow(() -> registry.getTool("write_file"),
                "write_file should be retrievable");
        assertDoesNotThrow(() -> registry.getTool("create_directory"),
                "create_directory should be retrievable");
        assertDoesNotThrow(() -> registry.getTool("delete_file"),
                "delete_file should be retrievable");

        // 验证检索的工具类型正确
        assertTrue(registry.getTool("read_file") instanceof ReadFileTool);
        assertTrue(registry.getTool("list_directory") instanceof ListDirectoryTool);
        assertTrue(registry.getTool("search_files") instanceof SearchFilesTool);
        assertTrue(registry.getTool("write_file") instanceof WriteFileTool);
        assertTrue(registry.getTool("create_directory") instanceof CreateDirectoryTool);
        assertTrue(registry.getTool("delete_file") instanceof DeleteFileTool);
    }

    @Test
    @DisplayName("验证 MASTER 角色可以访问 READ_ONLY 工具")
    void testMasterRoleAccess() {
        ToolRegistry registry = new tech.yesboss.tool.registry.impl.ToolRegistryImpl();
        String projectRoot = System.getProperty("user.dir");

        // 注册所有工具
        registry.register(new ReadFileTool(projectRoot));
        registry.register(new ListDirectoryTool(projectRoot));
        registry.register(new SearchFilesTool(projectRoot));
        registry.register(new WriteFileTool(projectRoot));
        registry.register(new CreateDirectoryTool(projectRoot));
        registry.register(new DeleteFileTool(projectRoot));

        // 获取 MASTER 角色可用的工具
        List<AgentTool> masterTools = registry.getAvailableTools("MASTER");

        // MASTER 应该只能访问 READ_ONLY 工具
        assertTrue(masterTools.size() >= 3, "MASTER should have access to at least 3 READ_ONLY tools");

        // 验证只包含 READ_ONLY 工具
        for (AgentTool tool : masterTools) {
            assertEquals(ToolAccessLevel.READ_ONLY, tool.getAccessLevel(),
                    "MASTER should only have access to READ_ONLY tools");
        }
    }

    @Test
    @DisplayName("验证 WORKER 角色可以访问所有工具")
    void testWorkerRoleAccess() {
        ToolRegistry registry = new tech.yesboss.tool.registry.impl.ToolRegistryImpl();
        String projectRoot = System.getProperty("user.dir");

        // 注册所有工具
        registry.register(new ReadFileTool(projectRoot));
        registry.register(new ListDirectoryTool(projectRoot));
        registry.register(new SearchFilesTool(projectRoot));
        registry.register(new WriteFileTool(projectRoot));
        registry.register(new CreateDirectoryTool(projectRoot));
        registry.register(new DeleteFileTool(projectRoot));

        // 获取 WORKER 角色可用的工具
        List<AgentTool> workerTools = registry.getAvailableTools("WORKER");

        // WORKER 应该可以访问所有工具
        assertEquals(6, workerTools.size(), "WORKER should have access to all 6 tools");
    }

    @Test
    @DisplayName("验证 ApplicationContext 的 getProjectRoot 方法")
    void testGetProjectRoot() {
        // 测试默认使用当前工作目录
        String defaultProjectRoot = applicationContext.getProjectRoot();
        assertNotNull(defaultProjectRoot);
        assertFalse(defaultProjectRoot.isEmpty());
        assertEquals(System.getProperty("user.dir"), defaultProjectRoot);

        // 测试自定义项目根目录
        String customProjectRoot = "/custom/project/root";
        config.getApp().setProjectRoot(customProjectRoot);
        String actualProjectRoot = applicationContext.getProjectRoot();
        assertEquals(customProjectRoot, actualProjectRoot);
    }

    @Test
    @DisplayName("验证工具名称唯一性")
    void testToolNameUniqueness() {
        String projectRoot = System.getProperty("user.dir");

        // 创建所有工具实例
        ReadFileTool readFileTool = new ReadFileTool(projectRoot);
        ListDirectoryTool listDirectoryTool = new ListDirectoryTool(projectRoot);
        SearchFilesTool searchFilesTool = new SearchFilesTool(projectRoot);
        WriteFileTool writeFileTool = new WriteFileTool(projectRoot);
        CreateDirectoryTool createDirectoryTool = new CreateDirectoryTool(projectRoot);
        DeleteFileTool deleteFileTool = new DeleteFileTool(projectRoot);

        // 收集所有工具名称
        String[] toolNames = {
            readFileTool.getName(),
            listDirectoryTool.getName(),
            searchFilesTool.getName(),
            writeFileTool.getName(),
            createDirectoryTool.getName(),
            deleteFileTool.getName()
        };

        // 验证所有名称唯一
        for (int i = 0; i < toolNames.length; i++) {
            for (int j = i + 1; j < toolNames.length; j++) {
                assertNotEquals(toolNames[i], toolNames[j],
                        "Tool names should be unique: " + toolNames[i] + " vs " + toolNames[j]);
            }
        }
    }
}
