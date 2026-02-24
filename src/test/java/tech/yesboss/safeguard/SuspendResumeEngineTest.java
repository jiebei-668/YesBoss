package tech.yesboss.safeguard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.yesboss.context.GlobalStreamManager;
import tech.yesboss.context.LocalStreamManager;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.gateway.im.IMMessagePusher;
import tech.yesboss.safeguard.impl.SuspendResumeEngineImpl;
import tech.yesboss.state.TaskManager;
import tech.yesboss.state.model.ImRoute;
import tech.yesboss.tool.AgentTool;
import tech.yesboss.tool.ToolAccessLevel;
import tech.yesboss.tool.tracker.ToolCallTracker;
import tech.yesboss.tool.registry.ToolRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class for SuspendResumeEngine
 *
 * <p>Tests the human-in-the-loop suspension and resumption workflow.</p>
 */
class SuspendResumeEngineTest {

    private TaskManager taskManager;
    private GlobalStreamManager globalStreamManager;
    private LocalStreamManager localStreamManager;
    private ToolRegistry toolRegistry;
    private IMMessagePusher imMessagePusher;
    private ToolCallTracker toolCallTracker;
    private SuspendResumeEngine suspendResumeEngine;

    private static final String WORKER_SESSION_ID = "worker-session-123";
    private static final String MASTER_SESSION_ID = "master-session-456";
    private static final String TOOL_CALL_ID = "call_abc123";
    private static final String INTERCEPTED_COMMAND = "rm -rf /";
    private static final String TOOL_NAME = "bash_execute";
    private static final String ARGUMENTS_JSON = "{\"command\": \"rm -rf /\"}";
    private static final String IM_TYPE = "FEISHU";
    private static final String IM_GROUP_ID = "group_789";

    @BeforeEach
    void setUp() {
        taskManager = mock(TaskManager.class);
        globalStreamManager = mock(GlobalStreamManager.class);
        localStreamManager = mock(LocalStreamManager.class);
        toolRegistry = mock(ToolRegistry.class);
        imMessagePusher = mock(IMMessagePusher.class);
        toolCallTracker = mock(ToolCallTracker.class);

        suspendResumeEngine = new SuspendResumeEngineImpl(
            taskManager,
            globalStreamManager,
            localStreamManager,
            toolRegistry,
            imMessagePusher,
            toolCallTracker
        );

        // Setup default mock behaviors
        when(taskManager.sessionExists(WORKER_SESSION_ID)).thenReturn(true);
        when(taskManager.getStatus(WORKER_SESSION_ID))
            .thenReturn(tech.yesboss.persistence.entity.TaskSession.Status.RUNNING);
        when(taskManager.getParentSessionId(WORKER_SESSION_ID)).thenReturn(MASTER_SESSION_ID);
        when(taskManager.getImRoute(MASTER_SESSION_ID))
            .thenReturn(new ImRoute(IM_TYPE, IM_GROUP_ID));
    }

    // ==========================================
    // Constructor Tests
    // ==========================================

    @Test
    void testConstructorWithValidParameters() {
        assertDoesNotThrow(() -> new SuspendResumeEngineImpl(
            taskManager,
            globalStreamManager,
            localStreamManager,
            toolRegistry,
            imMessagePusher,
            toolCallTracker
        ));
    }

    @Test
    void testConstructorWithNullTaskManager() {
        assertThrows(IllegalArgumentException.class, () -> new SuspendResumeEngineImpl(
            null,
            globalStreamManager,
            localStreamManager,
            toolRegistry,
            imMessagePusher,
            toolCallTracker
        ));
    }

    @Test
    void testConstructorWithNullGlobalStreamManager() {
        assertThrows(IllegalArgumentException.class, () -> new SuspendResumeEngineImpl(
            taskManager,
            null,
            localStreamManager,
            toolRegistry,
            imMessagePusher,
            toolCallTracker
        ));
    }

    @Test
    void testConstructorWithNullLocalStreamManager() {
        assertThrows(IllegalArgumentException.class, () -> new SuspendResumeEngineImpl(
            taskManager,
            globalStreamManager,
            null,
            toolRegistry,
            imMessagePusher,
            toolCallTracker
        ));
    }

    @Test
    void testConstructorWithNullToolRegistry() {
        assertThrows(IllegalArgumentException.class, () -> new SuspendResumeEngineImpl(
            taskManager,
            globalStreamManager,
            localStreamManager,
            null,
            imMessagePusher,
            toolCallTracker
        ));
    }

    @Test
    void testConstructorWithNullImMessagePusher() {
        assertThrows(IllegalArgumentException.class, () -> new SuspendResumeEngineImpl(
            taskManager,
            globalStreamManager,
            localStreamManager,
            toolRegistry,
            null,
            toolCallTracker
        ));
    }

    @Test
    void testConstructorWithNullToolCallTracker() {
        assertThrows(IllegalArgumentException.class, () -> new SuspendResumeEngineImpl(
            taskManager,
            globalStreamManager,
            localStreamManager,
            toolRegistry,
            imMessagePusher,
            null
        ));
    }

    // ==========================================
    // suspendForApproval Tests
    // ==========================================

    @Test
    void testSuspendForApprovalSuccess() throws Exception {
        // Execute
        suspendResumeEngine.suspendForApproval(WORKER_SESSION_ID, INTERCEPTED_COMMAND, TOOL_CALL_ID);

        // Verify state transition to SUSPENDED
        verify(taskManager).transitionState(WORKER_SESSION_ID,
            tech.yesboss.persistence.entity.TaskSession.Status.SUSPENDED);

        // Verify card message pushed to IM
        ArgumentCaptor<String> cardJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(imMessagePusher).pushCardMessage(eq(IM_TYPE), eq(IM_GROUP_ID), cardJsonCaptor.capture());

        String cardJson = cardJsonCaptor.getValue();
        assertTrue(cardJson.contains(WORKER_SESSION_ID));
        assertTrue(cardJson.contains(INTERCEPTED_COMMAND));
        assertTrue(cardJson.contains(TOOL_CALL_ID));

        // Verify system message appended to global stream
        verify(globalStreamManager).appendSystemMessage(eq(MASTER_SESSION_ID), anyString());
    }

    @Test
    void testSuspendForApprovalWithNullWorkerSessionId() {
        assertThrows(IllegalArgumentException.class, () ->
            suspendResumeEngine.suspendForApproval(null, INTERCEPTED_COMMAND, TOOL_CALL_ID)
        );
    }

    @Test
    void testSuspendForApprovalWithEmptyWorkerSessionId() {
        assertThrows(IllegalArgumentException.class, () ->
            suspendResumeEngine.suspendForApproval("  ", INTERCEPTED_COMMAND, TOOL_CALL_ID)
        );
    }

    @Test
    void testSuspendForApprovalWithNullInterceptedCommand() {
        assertThrows(IllegalArgumentException.class, () ->
            suspendResumeEngine.suspendForApproval(WORKER_SESSION_ID, null, TOOL_CALL_ID)
        );
    }

    @Test
    void testSuspendForApprovalWithEmptyInterceptedCommand() {
        assertThrows(IllegalArgumentException.class, () ->
            suspendResumeEngine.suspendForApproval(WORKER_SESSION_ID, "  ", TOOL_CALL_ID)
        );
    }

    @Test
    void testSuspendForApprovalWithNullToolCallId() {
        assertThrows(IllegalArgumentException.class, () ->
            suspendResumeEngine.suspendForApproval(WORKER_SESSION_ID, INTERCEPTED_COMMAND, null)
        );
    }

    @Test
    void testSuspendForApprovalWithEmptyToolCallId() {
        assertThrows(IllegalArgumentException.class, () ->
            suspendResumeEngine.suspendForApproval(WORKER_SESSION_ID, INTERCEPTED_COMMAND, "  ")
        );
    }

    @Test
    void testSuspendForApprovalWithNonExistentSession() {
        when(taskManager.sessionExists(WORKER_SESSION_ID)).thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
            suspendResumeEngine.suspendForApproval(WORKER_SESSION_ID, INTERCEPTED_COMMAND, TOOL_CALL_ID)
        );
    }

    @Test
    void testSuspendForApprovalWithNonRunningStatus() {
        when(taskManager.getStatus(WORKER_SESSION_ID))
            .thenReturn(tech.yesboss.persistence.entity.TaskSession.Status.COMPLETED);

        assertThrows(IllegalStateException.class, () ->
            suspendResumeEngine.suspendForApproval(WORKER_SESSION_ID, INTERCEPTED_COMMAND, TOOL_CALL_ID)
        );
    }

    @Test
    void testSuspendForApprovalWithNullParentSessionId() {
        when(taskManager.getParentSessionId(WORKER_SESSION_ID)).thenReturn(null);

        assertThrows(IllegalStateException.class, () ->
            suspendResumeEngine.suspendForApproval(WORKER_SESSION_ID, INTERCEPTED_COMMAND, TOOL_CALL_ID)
        );
    }

    // ==========================================
    // resume Tests
    // ==========================================

    @Test
    void testResumeWithApproval() throws Exception {
        // Setup suspension context
        ((SuspendResumeEngineImpl) suspendResumeEngine).storeSuspensionContext(
            TOOL_CALL_ID, TOOL_NAME, ARGUMENTS_JSON, WORKER_SESSION_ID
        );

        // Setup task status as SUSPENDED
        when(taskManager.getStatus(WORKER_SESSION_ID))
            .thenReturn(tech.yesboss.persistence.entity.TaskSession.Status.SUSPENDED);

        // Setup tool mock
        AgentTool mockTool = mock(AgentTool.class);
        when(mockTool.getName()).thenReturn(TOOL_NAME);
        when(mockTool.getAccessLevel()).thenReturn(ToolAccessLevel.READ_WRITE);
        when(mockTool.executeWithBypass(ARGUMENTS_JSON)).thenReturn("Execution successful");
        when(toolRegistry.getTool(TOOL_NAME)).thenReturn(mockTool);

        // Execute resume with approval
        suspendResumeEngine.resume(WORKER_SESSION_ID, TOOL_CALL_ID, true, "User approved");

        // Verify tool executed with bypass
        verify(mockTool).executeWithBypass(ARGUMENTS_JSON);

        // Verify tool call tracked
        verify(toolCallTracker).trackExecution(
            eq(WORKER_SESSION_ID),
            eq(TOOL_CALL_ID),
            eq(TOOL_NAME),
            eq(ARGUMENTS_JSON),
            eq("Execution successful"),
            eq(false)  // isIntercepted = false
        );

        // Verify tool result appended to local stream
        ArgumentCaptor<UnifiedMessage> messageCaptor = ArgumentCaptor.forClass(UnifiedMessage.class);
        verify(localStreamManager).appendToolResult(eq(WORKER_SESSION_ID), messageCaptor.capture());

        UnifiedMessage result = messageCaptor.getValue();
        assertEquals(UnifiedMessage.Role.USER, result.role());
        assertFalse(result.hasToolCalls());
        assertTrue(result.hasToolResults());
        assertEquals(TOOL_CALL_ID, result.toolResults().get(0).toolCallId());
        assertFalse(result.toolResults().get(0).isError());

        // Verify state transition back to RUNNING
        verify(taskManager).transitionState(WORKER_SESSION_ID,
            tech.yesboss.persistence.entity.TaskSession.Status.RUNNING);
    }

    @Test
    void testResumeWithRejection() throws Exception {
        // Setup suspension context
        ((SuspendResumeEngineImpl) suspendResumeEngine).storeSuspensionContext(
            TOOL_CALL_ID, TOOL_NAME, ARGUMENTS_JSON, WORKER_SESSION_ID
        );

        // Setup task status as SUSPENDED
        when(taskManager.getStatus(WORKER_SESSION_ID))
            .thenReturn(tech.yesboss.persistence.entity.TaskSession.Status.SUSPENDED);

        // Execute resume with rejection
        suspendResumeEngine.resume(WORKER_SESSION_ID, TOOL_CALL_ID, false, "User rejected this operation");

        // Verify tool NOT executed
        verify(toolRegistry, never()).getTool(anyString());

        // Verify tool call tracked with rejection
        verify(toolCallTracker).trackExecution(
            eq(WORKER_SESSION_ID),
            eq(TOOL_CALL_ID),
            eq(TOOL_NAME),
            eq(ARGUMENTS_JSON),
            contains("工具执行被用户拒绝"),
            eq(true)  // isIntercepted = true
        );

        // Verify tool result appended to local stream with error
        ArgumentCaptor<UnifiedMessage> messageCaptor = ArgumentCaptor.forClass(UnifiedMessage.class);
        verify(localStreamManager).appendToolResult(eq(WORKER_SESSION_ID), messageCaptor.capture());

        UnifiedMessage result = messageCaptor.getValue();
        assertTrue(result.hasToolResults());
        assertEquals(TOOL_CALL_ID, result.toolResults().get(0).toolCallId());
        assertTrue(result.toolResults().get(0).isError());
        assertTrue(result.toolResults().get(0).resultString().contains("被用户拒绝"));

        // Verify state transition back to RUNNING
        verify(taskManager).transitionState(WORKER_SESSION_ID,
            tech.yesboss.persistence.entity.TaskSession.Status.RUNNING);
    }

    @Test
    void testResumeWithNullWorkerSessionId() {
        assertThrows(IllegalArgumentException.class, () ->
            suspendResumeEngine.resume(null, TOOL_CALL_ID, true, "feedback")
        );
    }

    @Test
    void testResumeWithEmptyWorkerSessionId() {
        assertThrows(IllegalArgumentException.class, () ->
            suspendResumeEngine.resume("  ", TOOL_CALL_ID, true, "feedback")
        );
    }

    @Test
    void testResumeWithNullToolCallId() {
        assertThrows(IllegalArgumentException.class, () ->
            suspendResumeEngine.resume(WORKER_SESSION_ID, null, true, "feedback")
        );
    }

    @Test
    void testResumeWithEmptyToolCallId() {
        assertThrows(IllegalArgumentException.class, () ->
            suspendResumeEngine.resume(WORKER_SESSION_ID, "  ", true, "feedback")
        );
    }

    @Test
    void testResumeWithNonExistentSession() {
        when(taskManager.sessionExists(WORKER_SESSION_ID)).thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
            suspendResumeEngine.resume(WORKER_SESSION_ID, TOOL_CALL_ID, true, "feedback")
        );
    }

    @Test
    void testResumeWithNonSuspendedStatus() {
        when(taskManager.getStatus(WORKER_SESSION_ID))
            .thenReturn(tech.yesboss.persistence.entity.TaskSession.Status.RUNNING);

        assertThrows(IllegalStateException.class, () ->
            suspendResumeEngine.resume(WORKER_SESSION_ID, TOOL_CALL_ID, true, "feedback")
        );
    }

    @Test
    void testResumeWithApprovalWhenToolExecutionFails() throws Exception {
        // Setup suspension context
        ((SuspendResumeEngineImpl) suspendResumeEngine).storeSuspensionContext(
            TOOL_CALL_ID, TOOL_NAME, ARGUMENTS_JSON, WORKER_SESSION_ID
        );

        // Setup task status as SUSPENDED
        when(taskManager.getStatus(WORKER_SESSION_ID))
            .thenReturn(tech.yesboss.persistence.entity.TaskSession.Status.SUSPENDED);

        // Setup tool mock to throw exception
        AgentTool mockTool = mock(AgentTool.class);
        when(mockTool.getName()).thenReturn(TOOL_NAME);
        when(mockTool.getAccessLevel()).thenReturn(ToolAccessLevel.READ_WRITE);
        when(mockTool.executeWithBypass(ARGUMENTS_JSON))
            .thenThrow(new RuntimeException("Tool execution failed"));
        when(toolRegistry.getTool(TOOL_NAME)).thenReturn(mockTool);

        // Execute resume with approval
        suspendResumeEngine.resume(WORKER_SESSION_ID, TOOL_CALL_ID, true, null);

        // Verify tool result appended with error
        ArgumentCaptor<UnifiedMessage> messageCaptor = ArgumentCaptor.forClass(UnifiedMessage.class);
        verify(localStreamManager).appendToolResult(eq(WORKER_SESSION_ID), messageCaptor.capture());

        UnifiedMessage result = messageCaptor.getValue();
        assertTrue(result.toolResults().get(0).isError());
        assertTrue(result.toolResults().get(0).resultString().contains("工具执行失败"));
    }

    @Test
    void testResumeWithRejectionAndNoHumanFeedback() throws Exception {
        // Setup suspension context
        ((SuspendResumeEngineImpl) suspendResumeEngine).storeSuspensionContext(
            TOOL_CALL_ID, TOOL_NAME, ARGUMENTS_JSON, WORKER_SESSION_ID
        );

        // Setup task status as SUSPENDED
        when(taskManager.getStatus(WORKER_SESSION_ID))
            .thenReturn(tech.yesboss.persistence.entity.TaskSession.Status.SUSPENDED);

        // Execute resume with rejection and no feedback
        suspendResumeEngine.resume(WORKER_SESSION_ID, TOOL_CALL_ID, false, null);

        // Verify tool result contains basic rejection message
        ArgumentCaptor<UnifiedMessage> messageCaptor = ArgumentCaptor.forClass(UnifiedMessage.class);
        verify(localStreamManager).appendToolResult(eq(WORKER_SESSION_ID), messageCaptor.capture());

        UnifiedMessage result = messageCaptor.getValue();
        assertTrue(result.toolResults().get(0).resultString().contains("工具执行被用户拒绝"));
        assertFalse(result.toolResults().get(0).resultString().contains("用户反馈"));
    }

    @Test
    void testSuspensionContextStorage() {
        // Store context
        ((SuspendResumeEngineImpl) suspendResumeEngine).storeSuspensionContext(
            TOOL_CALL_ID, TOOL_NAME, ARGUMENTS_JSON, WORKER_SESSION_ID
        );

        // Clear context (testing the method exists)
        assertDoesNotThrow(() ->
            ((SuspendResumeEngineImpl) suspendResumeEngine).clearSuspensionContext(TOOL_CALL_ID)
        );
    }
}
