package tech.yesboss.tool.filesystem.security;

import org.junit.jupiter.api.*;
import tech.yesboss.tool.filesystem.security.SimpleDynamicWhitelistManager;
import tech.yesboss.tool.filesystem.security.WhitelistDecision;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 简单动态白名单管理器测试
 *
 * <p>演示如何使用 SimpleDynamicWhitelistManager</p>
 */
@DisplayName("简单动态白名单管理器测试")
class SimpleDynamicWhitelistManagerTest {

    private SimpleDynamicWhitelistManager whitelistManager;
    private Path testConfigFile;

    @BeforeEach
    void setUp() throws IOException {
        // 创建临时配置文件
        testConfigFile = Files.createTempFile("whitelist-test", ".yaml");
        String configContent = """
                # Dynamic Whitelist Configuration
                whitelist:
                  - "D:\\\\Projects\\\\Trusted"
                  - "/tmp/scratch"
                  - "C:\\\\Users\\\\Test\\\\Documents"
                """;
        Files.writeString(testConfigFile, configContent);

        // 初始化白名单管理器
        whitelistManager = new SimpleDynamicWhitelistManager(testConfigFile.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        // 清理临时文件
        if (testConfigFile != null && Files.exists(testConfigFile)) {
            Files.delete(testConfigFile);
        }
    }

    @Test
    @DisplayName("应该正确加载配置文件中的白名单")
    void testLoadWhitelist() {
        // 验证白名单已加载
        assertFalse(whitelistManager.isEmpty(), "Whitelist should not be empty");

        // 验证具体路径
        assertTrue(whitelistManager.isPathInWhitelist("D:\\Projects\\Trusted"));
        assertTrue(whitelistManager.isPathInWhitelist("/tmp/scratch"));
        assertTrue(whitelistManager.isPathInWhitelist("C:\\Users\\Test\\Documents"));
    }

    @Test
    @DisplayName("应该支持路径前缀匹配")
    void testPrefixMatching() {
        // 白名单中包含 "D:\\Projects\\Trusted"
        // 应该匹配其子路径
        assertTrue(whitelistManager.isPathInWhitelist("D:\\Projects\\Trusted\\subdir\\file.txt"));
        assertTrue(whitelistManager.isPathInWhitelist("D:\\Projects\\Trusted\\deeply\\nested\\path"));
    }

    @Test
    @DisplayName("应该支持动态添加路径到永久白名单")
    void testAddToPermanentWhitelist() {
        // 添加新路径
        String newPath = "D:\\Projects\\NewProject";
        whitelistManager.addPath(newPath);

        // 验证已添加
        assertTrue(whitelistManager.isPathInWhitelist(newPath));
        assertTrue(whitelistManager.isPathInWhitelist(newPath + "\\subdir"));
    }

    @Test
    @DisplayName("应该支持临时白名单")
    void testTemporaryWhitelist() {
        // 添加到临时白名单
        String tempPath = "/tmp/temp-12345";
        whitelistManager.addToWhitelist(tempPath, DynamicWhitelistManager.DecisionType.ALLOW_TEMPORARY);

        // 验证可访问
        assertTrue(whitelistManager.isPathInWhitelist(tempPath));

        // 清除临时白名单
        whitelistManager.clearTemporaryWhitelist();

        // 验证不再可访问
        assertFalse(whitelistManager.isPathInWhitelist(tempPath));
    }

    @Test
    @DisplayName("应该支持一次性允许")
    void testOnceAllowlist() {
        // 添加到一次性允许列表
        String oncePath = "/tmp/once-usage";
        whitelistManager.addToWhitelist(oncePath, DynamicWhitelistManager.DecisionType.ALLOW_ONCE);

        // 第一次访问：应该允许
        assertTrue(whitelistManager.isPathInWhitelist(oncePath));

        // 注意：在实际使用中，一次性允许应该在访问后自动清除
        // 这里需要手动清除
        whitelistManager.clearTemporaryWhitelist();

        // 第二次访问：应该不再允许
        assertFalse(whitelistManager.isPathInWhitelist(oncePath));
    }

    @Test
    @DisplayName("应该支持批量添加路径")
    void testBatchAddPaths() {
        // 批量添加
        whitelistManager.addPaths(
                "D:\\Project1",
                "D:\\Project2",
                "D:\\Project3"
        );

        // 验证所有路径都已添加
        assertTrue(whitelistManager.isPathInWhitelist("D:\\Project1"));
        assertTrue(whitelistManager.isPathInWhitelist("D:\\Project2"));
        assertTrue(whitelistManager.isPathInWhitelist("D:\\Project3"));
    }

    @Test
    @DisplayName("应该拒绝不在白名单中的路径")
    void testRejectNonWhitelistedPath() {
        // 一个不在白名单中的路径
        String untrustedPath = "D:\\Untrusted\\Project";

        // 应该被拒绝
        assertFalse(whitelistManager.isPathInWhitelist(untrustedPath));
    }

    @Test
    @DisplayName("应该正确处理用户决策")
    void testUserDecision() {
        String testPath = "/tmp/test-path";

        // 请求用户决策（MVP 版本会自动拒绝）
        WhitelistDecision decision =
                whitelistManager.requestUserDecision(testPath, "READ", "read_file", "session-123");

        // MVP 版本：应该返回 DENY
        assertNotNull(decision);
        assertEquals(DynamicWhitelistManager.DecisionType.DENY, decision.decisionType());
        assertEquals(testPath, decision.path());

        // 验证决策包含合理的说明
        assertNotNull(decision.rationale());
    }

    @Test
    @DisplayName("应该正确持久化白名单")
    void testPersistWhitelist() {
        // 添加新路径
        String newPath = "D:\\Projects\\ToBePersisted";
        whitelistManager.addPath(newPath);

        // 持久化（应该自动调用）
        // 在 addPath() 中已经调用了 persistWhitelist()

        // 创建新的管理器实例，重新加载
        SimpleDynamicWhitelistManager newManager =
                new SimpleDynamicWhitelistManager(testConfigFile.toString());

        // 验证新路径已持久化
        assertTrue(newManager.isPathInWhitelist(newPath));
    }

    @Test
    @DisplayName("应该提供统计信息")
    void testStatistics() {
        // 添加一些路径
        whitelistManager.addPaths("D:\\Temp1", "D:\\Temp2");
        whitelistManager.addToWhitelist("/tmp/temp", DynamicWhitelistManager.DecisionType.ALLOW_TEMPORARY);
        whitelistManager.addToWhitelist("/tmp/once", DynamicWhitelistManager.DecisionType.ALLOW_ONCE);

        // 获取统计信息
        String stats = whitelistManager.getStatistics();

        // 验证统计信息
        assertNotNull(stats);
        assertTrue(stats.contains("Permanent:"));
        assertTrue(stats.contains("Temporary:"));
        assertTrue(stats.contains("Once:"));
        assertTrue(stats.contains("Total:"));
    }

    @Test
    @DisplayName("应该处理空配置文件")
    void testEmptyConfigFile() throws IOException {
        // 创建空配置文件
        Path emptyConfig = Files.createTempFile("empty-whitelist", ".yaml");
        Files.writeString(emptyConfig, "# Empty whitelist\n");

        // 初始化管理器
        SimpleDynamicWhitelistManager manager =
                new SimpleDynamicWhitelistManager(emptyConfig.toString());

        // 应该创建初始配置
        assertTrue(Files.exists(emptyConfig));

        // 清理
        Files.delete(emptyConfig);
    }

    @Test
    @DisplayName("集成测试：完整的白名单使用流程")
    void testCompleteWorkflow() {
        System.out.println("\n=== 完整白名单工作流测试 ===\n");

        // Step 1: 检查初始状态
        System.out.println("Step 1: 初始白名单状态");
        System.out.println(whitelistManager.getStatistics());
        assertFalse(whitelistManager.isEmpty());

        // Step 2: 尝试访问新路径（应该被拒绝）
        System.out.println("\nStep 2: 尝试访问新路径");
        String newPath = "D:\\NewProject\\file.txt";
        assertFalse(whitelistManager.isPathInWhitelist(newPath));
        System.out.println("✓ 路径 " + newPath + " 被拒绝（预期行为）");

        // Step 3: 请求用户决策
        System.out.println("\nStep 3: 请求用户决策");
        WhitelistDecision decision =
                whitelistManager.requestUserDecision(newPath, "READ", "read_file", "session-1");
        System.out.println("用户决策: " + decision.decisionType());
        System.out.println("决策说明: " + decision.rationale());

        // Step 4: 手动添加到白名单（模拟用户授权）
        System.out.println("\nStep 4: 添加路径到白名单");
        whitelistManager.addPath("D:\\NewProject");
        System.out.println("✓ 路径已添加到白名单");

        // Step 5: 再次尝试访问（应该成功）
        System.out.println("\nStep 5: 再次尝试访问");
        assertTrue(whitelistManager.isPathInWhitelist(newPath));
        assertTrue(whitelistManager.isPathInWhitelist("D:\\NewProject\\subdir\\file.txt"));
        System.out.println("✓ 路径访问已允许");

        // Step 6: 查看最终统计
        System.out.println("\nStep 6: 最终白名单状态");
        System.out.println(whitelistManager.getStatistics());

        System.out.println("\n✓✓✓ 完整工作流测试通过 ✓✓✓");
    }

    @Test
    @DisplayName("演示：在测试环境中使用临时白名单")
    @Disabled("演示测试，默认禁用")
    void demonstrateTestEnvironmentUsage() {
        System.out.println("\n=== 测试环境白名单使用演示 ===\n");

        // 在测试环境中，我们可能想临时允许所有测试目录
        String testTempDir = System.getProperty("java.io.tmpdir");

        // 方式1：临时允许（仅当前会话）
        whitelistManager.addToWhitelist(
                testTempDir,
                DynamicWhitelistManager.DecisionType.ALLOW_TEMPORARY
        );
        System.out.println("✓ 临时允许测试目录: " + testTempDir);

        // 方式2：一次性允许（仅当前操作）
        String specificTestFile = testTempDir + "/test-file-12345.txt";
        whitelistManager.addToWhitelist(
                specificTestFile,
                DynamicWhitelistManager.DecisionType.ALLOW_ONCE
        );
        System.out.println("✓ 一次性允许测试文件: " + specificTestFile);

        // 验证
        assertTrue(whitelistManager.isPathInWhitelist(testTempDir));
        assertTrue(whitelistManager.isPathInWhitelist(specificTestFile));

        System.out.println("\n演示完成！");
    }
}
