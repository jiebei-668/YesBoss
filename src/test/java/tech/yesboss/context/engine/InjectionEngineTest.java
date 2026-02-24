package tech.yesboss.context.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.yesboss.context.GlobalStreamManager;
import tech.yesboss.context.engine.impl.InjectionEngineImpl;
import tech.yesboss.domain.message.UnifiedMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for InjectionEngine prompt formatting.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>injectInitialContext extracts relevant global history</li>
 *   <li>The returned UnifiedMessage contains the assigned task and global constraints</li>
 *   <li>The role is set to SYSTEM</li>
 * </ul>
 */
@DisplayName("InjectionEngine Tests")
class InjectionEngineTest {

    private GlobalStreamManager mockGlobalStreamManager;
    private InjectionEngine injectionEngine;

    @BeforeEach
    void setUp() {
        mockGlobalStreamManager = mock(GlobalStreamManager.class);
        injectionEngine = new InjectionEngineImpl(mockGlobalStreamManager);
    }

    @Test
    @DisplayName("Constructor should throw exception for null GlobalStreamManager")
    void testConstructorWithNullGlobalStreamManager() {
        assertThrows(IllegalArgumentException.class,
                () -> new InjectionEngineImpl(null),
                "Should throw exception for null GlobalStreamManager");
    }

    @Test
    @DisplayName("injectInitialContext should generate system prompt with assigned task")
    void testInjectInitialContextGeneratesSystemPrompt() {
        // Arrange
        String masterSessionId = "session_master_001";
        String assignedTask = "重构 tui 模块";
        List<UnifiedMessage> emptyContext = List.of();

        when(mockGlobalStreamManager.fetchContext(eq(masterSessionId)))
                .thenReturn(emptyContext);

        // Act
        UnifiedMessage result = injectionEngine.injectInitialContext(masterSessionId, assignedTask);

        // Assert
        assertNotNull(result);
        assertEquals(UnifiedMessage.Role.SYSTEM, result.role());
        assertTrue(result.content().contains("重构 tui 模块"));
        assertTrue(result.content().contains("Worker Agent"));
        assertTrue(result.content().contains("Assigned Task"));
    }

    @Test
    @DisplayName("injectInitialContext should throw exception for null masterSessionId")
    void testInjectInitialContextWithNullMasterSessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> injectionEngine.injectInitialContext(null, "task"));
    }

    @Test
    @DisplayName("injectInitialContext should throw exception for empty masterSessionId")
    void testInjectInitialContextWithEmptyMasterSessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> injectionEngine.injectInitialContext("", "task"));
    }

    @Test
    @DisplayName("injectInitialContext should throw exception for null assignedTask")
    void testInjectInitialContextWithNullAssignedTask() {
        assertThrows(IllegalArgumentException.class,
                () -> injectionEngine.injectInitialContext("session", null));
    }

    @Test
    @DisplayName("injectInitialContext should throw exception for empty assignedTask")
    void testInjectInitialContextWithEmptyAssignedTask() {
        assertThrows(IllegalArgumentException.class,
                () -> injectionEngine.injectInitialContext("session", ""));
    }

    @Test
    @DisplayName("injectInitialContext should include global rules in prompt")
    void testInjectInitialContextIncludesGlobalRules() {
        // Arrange
        String masterSessionId = "session_master_002";
        String assignedTask = "实现用户认证功能";

        List<UnifiedMessage> globalContext = List.of(
                UnifiedMessage.user("需要支持 OAuth2.0 登录"),
                UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "我将设计一个安全的认证系统")
        );

        when(mockGlobalStreamManager.fetchContext(eq(masterSessionId)))
                .thenReturn(globalContext);

        // Act
        UnifiedMessage result = injectionEngine.injectInitialContext(masterSessionId, assignedTask);

        // Assert
        assertEquals(UnifiedMessage.Role.SYSTEM, result.role());
        assertTrue(result.content().contains("实现用户认证功能"));
        assertTrue(result.content().contains("Global Rules and Constraints"));
        assertTrue(result.content().contains("OAuth2.0 登录"));
        assertTrue(result.content().contains("安全的认证系统"));
    }

    @Test
    @DisplayName("injectInitialContext should handle empty global context")
    void testInjectInitialContextWithEmptyGlobalContext() {
        // Arrange
        String masterSessionId = "session_master_empty";
        String assignedTask = "简单的测试任务";

        when(mockGlobalStreamManager.fetchContext(eq(masterSessionId)))
                .thenReturn(List.of());

        // Act
        UnifiedMessage result = injectionEngine.injectInitialContext(masterSessionId, assignedTask);

        // Assert
        assertNotNull(result);
        assertEquals(UnifiedMessage.Role.SYSTEM, result.role());
        assertTrue(result.content().contains("简单的测试任务"));
        // Should not crash with empty context, just skip the rules section
    }

    @Test
    @DisplayName("injectInitialContext should include execution guidelines")
    void testInjectInitialContextIncludesExecutionGuidelines() {
        // Arrange
        String masterSessionId = "session_master_003";
        String assignedTask = "编写单元测试";

        when(mockGlobalStreamManager.fetchContext(eq(masterSessionId)))
                .thenReturn(List.of());

        // Act
        UnifiedMessage result = injectionEngine.injectInitialContext(masterSessionId, assignedTask);

        // Assert
        assertTrue(result.content().contains("Execution Guidelines"));
        assertTrue(result.content().contains("Use available tools"));
        assertTrue(result.content().contains("Report progress"));
    }

    @Test
    @DisplayName("injectInitialContext should truncate long master guidance")
    void testInjectInitialContextTruncatesLongGuidance() {
        // Arrange
        String masterSessionId = "session_master_long";
        String assignedTask = "实现复杂功能";

        // Create a very long master message
        String longMessage = "A".repeat(300);
        List<UnifiedMessage> globalContext = List.of(
                UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, longMessage)
        );

        when(mockGlobalStreamManager.fetchContext(eq(masterSessionId)))
                .thenReturn(globalContext);

        // Act
        UnifiedMessage result = injectionEngine.injectInitialContext(masterSessionId, assignedTask);

        // Assert - The long message should be truncated
        assertTrue(result.content().contains("..."));
        // And it should still contain some of the original message
        assertTrue(result.content().contains("AAA")); // First few characters
    }

    @Test
    @DisplayName("injectInitialContext calls GlobalStreamManager with correct session ID")
    void testInjectInitialContextCallsGlobalStreamManager() {
        // Arrange
        String masterSessionId = "session_master_verify";
        String assignedTask = "测试任务";

        when(mockGlobalStreamManager.fetchContext(eq(masterSessionId)))
                .thenReturn(List.of());

        // Act
        injectionEngine.injectInitialContext(masterSessionId, assignedTask);

        // Assert
        verify(mockGlobalStreamManager).fetchContext(masterSessionId);
    }

    @Test
    @DisplayName("injectInitialContext formats complex global context correctly")
    void testInjectInitialContextWithComplexGlobalContext() {
        // Arrange
        String masterSessionId = "session_master_complex";
        String assignedTask = "多步骤重构任务";

        List<UnifiedMessage> globalContext = List.of(
                UnifiedMessage.user("需求：支持多用户并发"),
                UnifiedMessage.system("系统约束：使用乐观锁"),
                UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "计划：分3个阶段完成"),
                UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "第一阶段：数据层重构")
        );

        when(mockGlobalStreamManager.fetchContext(eq(masterSessionId)))
                .thenReturn(globalContext);

        // Act
        UnifiedMessage result = injectionEngine.injectInitialContext(masterSessionId, assignedTask);

        // Assert
        assertEquals(UnifiedMessage.Role.SYSTEM, result.role());
        assertTrue(result.content().contains("多用户并发"));
        assertTrue(result.content().contains("使用乐观锁"));
        assertTrue(result.content().contains("分3个阶段完成"));
    }

    @Test
    @DisplayName("injectInitialContext generates different prompts for different tasks")
    void testInjectInitialContextGeneratesDifferentPrompts() {
        // Arrange
        when(mockGlobalStreamManager.fetchContext(any()))
                .thenReturn(List.of());

        // Act
        UnifiedMessage result1 = injectionEngine.injectInitialContext("session1", "任务A");
        UnifiedMessage result2 = injectionEngine.injectInitialContext("session2", "任务B");

        // Assert
        assertTrue(result1.content().contains("任务A"));
        assertFalse(result1.content().contains("任务B"));

        assertTrue(result2.content().contains("任务B"));
        assertFalse(result2.content().contains("任务A"));
    }

    @Test
    @DisplayName("injectInitialContext handles system messages in global context")
    void testInjectInitialContextWithSystemMessages() {
        // Arrange
        String masterSessionId = "session_master_system";
        String assignedTask = "遵循系统约束";

        List<UnifiedMessage> globalContext = List.of(
                UnifiedMessage.system("必须使用只读工具"),
                UnifiedMessage.system("禁止直接修改生产数据")
        );

        when(mockGlobalStreamManager.fetchContext(eq(masterSessionId)))
                .thenReturn(globalContext);

        // Act
        UnifiedMessage result = injectionEngine.injectInitialContext(masterSessionId, assignedTask);

        // Assert
        assertTrue(result.content().contains("必须使用只读工具"));
        assertTrue(result.content().contains("禁止直接修改生产数据"));
    }

    @Test
    @DisplayName("injectInitialContext includes all required sections")
    void testInjectInitialContextIncludesAllRequiredSections() {
        // Arrange - Provide some global context so all sections are included
        String masterSessionId = "session_master_sections";
        String assignedTask = "test task";

        List<UnifiedMessage> globalContext = List.of(
                UnifiedMessage.user("User requirement")
        );

        when(mockGlobalStreamManager.fetchContext(eq(masterSessionId)))
                .thenReturn(globalContext);

        // Act
        UnifiedMessage result = injectionEngine.injectInitialContext(masterSessionId, assignedTask);

        // Assert - Verify all required sections are present
        String content = result.content();
        assertTrue(content.contains("# You are a Worker Agent"));
        assertTrue(content.contains("## Your Assigned Task"));
        assertTrue(content.contains("## Global Rules and Constraints"));
        assertTrue(content.contains("## Execution Guidelines"));
    }

    @Test
    @DisplayName("injectInitialContext properly formats assigned task section")
    void testInjectInitialContextFormatsAssignedTask() {
        // Arrange
        when(mockGlobalStreamManager.fetchContext(any()))
                .thenReturn(List.of());

        // Act
        UnifiedMessage result = injectionEngine.injectInitialContext("session", "实现REST API接口");

        // Assert
        assertTrue(result.content().contains("**Task:**"));
        assertTrue(result.content().contains("实现REST API接口"));
    }
}
