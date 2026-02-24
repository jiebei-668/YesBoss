package tech.yesboss.runner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.yesboss.context.GlobalStreamManager;
import tech.yesboss.context.LocalStreamManager;
import tech.yesboss.context.engine.CondensationEngine;
import tech.yesboss.context.engine.InjectionEngine;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.llm.LlmClient;
import tech.yesboss.llm.impl.ModelRouter;
import tech.yesboss.runner.impl.MasterRunnerImpl;
import tech.yesboss.runner.impl.WorkerRunnerImpl;
import tech.yesboss.safeguard.CircuitBreaker;
import tech.yesboss.safeguard.CircuitBreakerOpenException;
import tech.yesboss.safeguard.SuspendResumeEngine;
import tech.yesboss.state.TaskManager;
import tech.yesboss.tool.AgentTool;
import tech.yesboss.tool.SuspendExecutionException;
import tech.yesboss.tool.ToolAccessLevel;
import tech.yesboss.tool.tracker.ToolCallTracker;
import tech.yesboss.tool.registry.ToolRegistry;
import tech.yesboss.tool.sandbox.SandboxInterceptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class for MasterRunner and WorkerRunner skeletons
 *
 * <p>Tests the basic structure and initialization of the runner interfaces.</p>
 */
class RunnerSkeletonTest {

    private TaskManager taskManager;
    private GlobalStreamManager globalStreamManager;
    private LocalStreamManager localStreamManager;
    private InjectionEngine injectionEngine;
    private CondensationEngine condensationEngine;
    private ModelRouter modelRouter;
    private CircuitBreaker circuitBreaker;
    private ToolRegistry toolRegistry;
    private SandboxInterceptor sandboxInterceptor;
    private SuspendResumeEngine suspendResumeEngine;
    private ToolCallTracker toolCallTracker;

    private static final String SESSION_ID = "session-123";
    private static final String MASTER_SESSION_ID = "master-session-456";

    @BeforeEach
    void setUp() {
        taskManager = mock(TaskManager.class);
        globalStreamManager = mock(GlobalStreamManager.class);
        localStreamManager = mock(LocalStreamManager.class);
        injectionEngine = mock(InjectionEngine.class);
        condensationEngine = mock(CondensationEngine.class);
        modelRouter = mock(ModelRouter.class);
        circuitBreaker = mock(CircuitBreaker.class);
        toolRegistry = mock(ToolRegistry.class);
        sandboxInterceptor = mock(SandboxInterceptor.class);
        suspendResumeEngine = mock(SuspendResumeEngine.class);
        toolCallTracker = mock(ToolCallTracker.class);
    }

    // ==========================================
    // MasterRunner Tests
    // ==========================================

    @Test
    void testMasterRunnerConstructorWithValidParameters() {
        assertDoesNotThrow(() -> new MasterRunnerImpl(
            taskManager,
            globalStreamManager,
            modelRouter
        ));
    }

    @Test
    void testMasterRunnerConstructorWithNullTaskManager() {
        assertThrows(IllegalArgumentException.class, () -> new MasterRunnerImpl(
            null,
            globalStreamManager,
            modelRouter
        ));
    }

    @Test
    void testMasterRunnerConstructorWithNullGlobalStreamManager() {
        assertThrows(IllegalArgumentException.class, () -> new MasterRunnerImpl(
            taskManager,
            null,
            modelRouter
        ));
    }

    @Test
    void testMasterRunnerConstructorWithNullModelRouter() {
        assertThrows(IllegalArgumentException.class, () -> new MasterRunnerImpl(
            taskManager,
            globalStreamManager,
            null
        ));
    }

    @Test
    void testMasterRunnerRunWithValidSession() {
        // Setup
        MasterRunnerImpl masterRunner = new MasterRunnerImpl(
            taskManager,
            globalStreamManager,
            modelRouter
        );
        when(taskManager.sessionExists(SESSION_ID)).thenReturn(true);
        when(taskManager.getStatus(SESSION_ID))
            .thenReturn(tech.yesboss.persistence.entity.TaskSession.Status.PLANNING);

        // Execute
        assertDoesNotThrow(() -> masterRunner.run(SESSION_ID));

        // Verify
        verify(taskManager, atLeastOnce()).sessionExists(SESSION_ID);
        verify(taskManager, atLeastOnce()).getStatus(SESSION_ID);
    }

    @Test
    void testMasterRunnerRunWithNullSessionId() {
        MasterRunnerImpl masterRunner = new MasterRunnerImpl(
            taskManager,
            globalStreamManager,
            modelRouter
        );

        assertThrows(IllegalArgumentException.class, () -> masterRunner.run(null));
    }

    @Test
    void testMasterRunnerRunWithEmptySessionId() {
        MasterRunnerImpl masterRunner = new MasterRunnerImpl(
            taskManager,
            globalStreamManager,
            modelRouter
        );

        assertThrows(IllegalArgumentException.class, () -> masterRunner.run("  "));
    }

    @Test
    void testMasterRunnerRunWithNonExistentSession() {
        MasterRunnerImpl masterRunner = new MasterRunnerImpl(
            taskManager,
            globalStreamManager,
            modelRouter
        );
        when(taskManager.sessionExists(SESSION_ID)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> masterRunner.run(SESSION_ID));
    }

    @Test
    void testMasterRunnerGenerateExecutionPlan() {
        // Setup
        MasterRunnerImpl masterRunner = new MasterRunnerImpl(
            taskManager,
            globalStreamManager,
            modelRouter
        );
        when(taskManager.sessionExists(SESSION_ID)).thenReturn(true);
        when(taskManager.getStatus(SESSION_ID))
            .thenReturn(tech.yesboss.persistence.entity.TaskSession.Status.PLANNING);

        // Execute
        String plan = masterRunner.generateExecutionPlan(SESSION_ID);

        // Verify
        assertNotNull(plan);
        assertTrue(plan.contains("task-1"));
        assertTrue(plan.contains("task-2"));
        assertTrue(plan.contains("task-3"));
        verify(taskManager).sessionExists(SESSION_ID);
        verify(taskManager).getStatus(SESSION_ID);
    }

    @Test
    void testMasterRunnerGenerateExecutionPlanWithNonExistentSession() {
        MasterRunnerImpl masterRunner = new MasterRunnerImpl(
            taskManager,
            globalStreamManager,
            modelRouter
        );
        when(taskManager.sessionExists(SESSION_ID)).thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
            masterRunner.generateExecutionPlan(SESSION_ID)
        );
    }

    @Test
    void testMasterRunnerGenerateExecutionPlanWithWrongStatus() {
        MasterRunnerImpl masterRunner = new MasterRunnerImpl(
            taskManager,
            globalStreamManager,
            modelRouter
        );
        when(taskManager.sessionExists(SESSION_ID)).thenReturn(true);
        when(taskManager.getStatus(SESSION_ID))
            .thenReturn(tech.yesboss.persistence.entity.TaskSession.Status.RUNNING);

        assertThrows(IllegalStateException.class, () ->
            masterRunner.generateExecutionPlan(SESSION_ID)
        );
    }

    @Test
    void testMasterRunnerGetVirtualThreadExecutor() {
        MasterRunnerImpl masterRunner = new MasterRunnerImpl(
            taskManager,
            globalStreamManager,
            modelRouter
        );

        assertNotNull(masterRunner.getVirtualThreadExecutor());
    }

    @Test
    void testMasterRunnerShutdown() {
        MasterRunnerImpl masterRunner = new MasterRunnerImpl(
            taskManager,
            globalStreamManager,
            modelRouter
        );

        assertDoesNotThrow(() -> masterRunner.shutdown());
    }

    // ==========================================
    // WorkerRunner Tests
    // ==========================================

    @Test
    void testWorkerRunnerConstructorWithValidParameters() {
        assertDoesNotThrow(() -> new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        ));
    }

    @Test
    void testWorkerRunnerConstructorWithNullTaskManager() {
        assertThrows(IllegalArgumentException.class, () -> new WorkerRunnerImpl(
            null,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        ));
    }

    @Test
    void testWorkerRunnerConstructorWithNullLocalStreamManager() {
        assertThrows(IllegalArgumentException.class, () -> new WorkerRunnerImpl(
            taskManager,
            null,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        ));
    }

    @Test
    void testWorkerRunnerConstructorWithNullInjectionEngine() {
        assertThrows(IllegalArgumentException.class, () -> new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            null,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        ));
    }

    @Test
    void testWorkerRunnerConstructorWithNullCondensationEngine() {
        assertThrows(IllegalArgumentException.class, () -> new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            null,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        ));
    }

    @Test
    void testWorkerRunnerConstructorWithNullModelRouter() {
        assertThrows(IllegalArgumentException.class, () -> new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            null,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        ));
    }

    @Test
    void testWorkerRunnerConstructorWithNullCircuitBreaker() {
        assertThrows(IllegalArgumentException.class, () -> new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            null,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        ));
    }

    @Test
    void testWorkerRunnerConstructorWithNullToolRegistry() {
        assertThrows(IllegalArgumentException.class, () -> new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            null,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        ));
    }

    @Test
    void testWorkerRunnerConstructorWithNullSandboxInterceptor() {
        assertThrows(IllegalArgumentException.class, () -> new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            null,
            suspendResumeEngine,
            toolCallTracker
        ));
    }

    @Test
    void testWorkerRunnerConstructorWithNullSuspendResumeEngine() {
        assertThrows(IllegalArgumentException.class, () -> new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            null,
            toolCallTracker
        ));
    }

    @Test
    void testWorkerRunnerConstructorWithNullToolCallTracker() {
        assertThrows(IllegalArgumentException.class, () -> new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            null
        ));
    }

    @Test
    void testWorkerRunnerIsResumingFromSuspensionWithEmptyContext() {
        // Setup
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );
        when(localStreamManager.fetchContext(SESSION_ID)).thenReturn(new ArrayList<>());

        // Execute
        boolean isResuming = workerRunner.isResumingFromSuspension(SESSION_ID);

        // Verify
        assertFalse(isResuming, "Empty context should indicate new worker, not resuming");
    }

    @Test
    void testWorkerRunnerIsResumingFromSuspensionWithTextMessage() {
        // Setup
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );
        List<UnifiedMessage> context = List.of(
            UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "Hello")
        );
        when(localStreamManager.fetchContext(SESSION_ID)).thenReturn(context);

        // Execute
        boolean isResuming = workerRunner.isResumingFromSuspension(SESSION_ID);

        // Verify
        assertFalse(isResuming, "Text message should indicate new worker, not resuming");
    }

    @Test
    void testWorkerRunnerIsResumingFromSuspensionWithToolResult() {
        // Setup
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );
        List<UnifiedMessage> context = List.of(
            UnifiedMessage.ofToolResult("call-123", "Tool output", false)
        );
        when(localStreamManager.fetchContext(SESSION_ID)).thenReturn(context);

        // Execute
        boolean isResuming = workerRunner.isResumingFromSuspension(SESSION_ID);

        // Verify
        assertTrue(isResuming, "ToolResult should indicate resuming from suspension");
    }

    @Test
    void testWorkerRunnerIsResumingFromSuspensionWithMixedMessage() {
        // Setup: Last message has ToolResult
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );
        List<UnifiedMessage> context = List.of(
            UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "Hello"),
            UnifiedMessage.ofToolResult("call-123", "Tool output", false)
        );
        when(localStreamManager.fetchContext(SESSION_ID)).thenReturn(context);

        // Execute
        boolean isResuming = workerRunner.isResumingFromSuspension(SESSION_ID);

        // Verify
        assertTrue(isResuming, "Last message with ToolResult should indicate resuming");
    }

    @Test
    void testWorkerRunnerIsResumingFromSuspensionWithNullSessionId() {
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );

        assertThrows(IllegalArgumentException.class, () ->
            workerRunner.isResumingFromSuspension(null)
        );
    }

    @Test
    void testWorkerRunnerIsResumingFromSuspensionWithEmptySessionId() {
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );

        assertThrows(IllegalArgumentException.class, () ->
            workerRunner.isResumingFromSuspension("  ")
        );
    }

    @Test
    void testWorkerRunnerRunWithValidSessionNewWorker() {
        // Setup
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );
        when(taskManager.sessionExists(SESSION_ID)).thenReturn(true);
        when(taskManager.getParentSessionId(SESSION_ID)).thenReturn(MASTER_SESSION_ID);
        when(localStreamManager.fetchContext(SESSION_ID)).thenReturn(new ArrayList<>());
        when(injectionEngine.injectInitialContext(eq(MASTER_SESSION_ID), anyString()))
            .thenReturn(UnifiedMessage.system("Initial prompt"));
        doNothing().when(circuitBreaker).checkAndIncrement(SESSION_ID);

        // Create a single mock LlmClient instance
        LlmClient llmClientMock = mock(LlmClient.class);
        when(modelRouter.routeByRole("WORKER")).thenReturn(llmClientMock);
        when(localStreamManager.getCurrentTokenCount(SESSION_ID)).thenReturn(1000);

        // Mock LLM response with no tool calls (task complete)
        UnifiedMessage llmResponse = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "Task complete");
        when(llmClientMock.chat(any(List.class), any(String.class))).thenReturn(llmResponse);

        // Execute
        assertDoesNotThrow(() -> workerRunner.run(SESSION_ID));

        // Verify initialization was called
        verify(injectionEngine).injectInitialContext(eq(MASTER_SESSION_ID), anyString());
        verify(localStreamManager, atLeastOnce()).appendWorkerMessage(eq(SESSION_ID), any());

        // Verify ReAct loop steps
        verify(circuitBreaker).checkAndIncrement(SESSION_ID);
        verify(condensationEngine).condenseAndMergeUpwards(SESSION_ID, MASTER_SESSION_ID);
        verify(taskManager).transitionState(SESSION_ID,
            tech.yesboss.persistence.entity.TaskSession.Status.COMPLETED);
    }

    @Test
    void testWorkerRunnerRunWithValidSessionResumingWorker() {
        // Setup
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );
        List<UnifiedMessage> context = List.of(
            UnifiedMessage.ofToolResult("call-123", "Tool output", false)
        );
        when(taskManager.sessionExists(SESSION_ID)).thenReturn(true);
        when(localStreamManager.fetchContext(SESSION_ID)).thenReturn(context);
        doNothing().when(circuitBreaker).checkAndIncrement(SESSION_ID);

        // Create a single mock LlmClient instance
        LlmClient llmClientMock = mock(LlmClient.class);
        when(modelRouter.routeByRole("WORKER")).thenReturn(llmClientMock);
        when(localStreamManager.getCurrentTokenCount(SESSION_ID)).thenReturn(1000);

        // Mock LLM response with no tool calls (task complete)
        UnifiedMessage llmResponse = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "Task complete");
        when(llmClientMock.chat(any(List.class), any(String.class))).thenReturn(llmResponse);

        // Execute
        assertDoesNotThrow(() -> workerRunner.run(SESSION_ID));

        // Verify initialization was NOT called (resuming from suspension)
        verify(injectionEngine, never()).injectInitialContext(any(), any());

        // Verify ReAct loop continued and completed
        verify(circuitBreaker).checkAndIncrement(SESSION_ID);
    }

    @Test
    void testWorkerRunnerRunWithNullSessionId() {
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );

        assertThrows(IllegalArgumentException.class, () -> workerRunner.run(null));
    }

    @Test
    void testWorkerRunnerRunWithNonExistentSession() {
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );
        when(taskManager.sessionExists(SESSION_ID)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> workerRunner.run(SESSION_ID));
    }

    @Test
    void testWorkerRunnerGenerateExecutionReport() {
        // Setup
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );
        when(taskManager.sessionExists(SESSION_ID)).thenReturn(true);
        when(taskManager.getStatus(SESSION_ID))
            .thenReturn(tech.yesboss.persistence.entity.TaskSession.Status.COMPLETED);
        when(localStreamManager.fetchContext(SESSION_ID)).thenReturn(new ArrayList<>());
        when(localStreamManager.getCurrentTokenCount(SESSION_ID)).thenReturn(1000);

        // Execute
        String report = workerRunner.generateExecutionReport(SESSION_ID);

        // Verify
        assertNotNull(report);
        assertTrue(report.contains(SESSION_ID));
        assertTrue(report.contains("COMPLETED"));
        assertTrue(report.contains("0")); // Total Messages
    }

    @Test
    void testWorkerRunnerGenerateExecutionReportWithNonExistentSession() {
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );
        when(taskManager.sessionExists(SESSION_ID)).thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
            workerRunner.generateExecutionReport(SESSION_ID)
        );
    }

    @Test
    void testWorkerRunnerGetVirtualThreadExecutor() {
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );

        assertNotNull(workerRunner.getVirtualThreadExecutor());
    }

    @Test
    void testWorkerRunnerShutdown() {
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );

        assertDoesNotThrow(() -> workerRunner.shutdown());
    }

    // ==========================================
    // WorkerRunner ReAct Loop Tests
    // ==========================================

    @Test
    void testWorkerRunnerReActLoopWithToolCallThenTextAnswer() throws Exception {
        // Setup
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );

        // Session setup
        when(taskManager.sessionExists(SESSION_ID)).thenReturn(true);
        when(taskManager.getParentSessionId(SESSION_ID)).thenReturn(MASTER_SESSION_ID);
        when(localStreamManager.fetchContext(SESSION_ID)).thenReturn(new ArrayList<>());
        when(injectionEngine.injectInitialContext(eq(MASTER_SESSION_ID), anyString()))
            .thenReturn(UnifiedMessage.system("Initial prompt"));

        // LLM client setup
        LlmClient llmClientMock = mock(LlmClient.class);
        when(modelRouter.routeByRole("WORKER")).thenReturn(llmClientMock);
        when(localStreamManager.getCurrentTokenCount(SESSION_ID)).thenReturn(1000);

        // First response: Tool call, then text completion
        UnifiedMessage toolCallResponse = UnifiedMessage.ofToolCalls(
            List.of(new UnifiedMessage.ToolCall("call-123", "test_tool", "{\"param\":\"value\"}"))
        );
        UnifiedMessage textResponse = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "Task complete");
        when(llmClientMock.chat(any(List.class), any(String.class)))
            .thenReturn(toolCallResponse)
            .thenReturn(textResponse);

        // Tool setup
        AgentTool mockTool = mock(AgentTool.class);
        when(toolRegistry.getTool("test_tool")).thenReturn(mockTool);
        when(mockTool.execute(anyString())).thenReturn("Tool executed successfully");

        // Execute and verify
        assertDoesNotThrow(() -> workerRunner.run(SESSION_ID));

        // Verify tool execution was called
        verify(mockTool).execute("{\"param\":\"value\"}");
        verify(toolCallTracker).trackExecution(
            eq(SESSION_ID),
            eq("call-123"),
            eq("test_tool"),
            eq("{\"param\":\"value\"}"),
            eq("Tool executed successfully"),
            eq(false)
        );

        // Verify CondensationEngine was called on completion
        verify(condensationEngine).condenseAndMergeUpwards(SESSION_ID, MASTER_SESSION_ID);

        // Verify task was marked as COMPLETED
        verify(taskManager).transitionState(SESSION_ID,
            tech.yesboss.persistence.entity.TaskSession.Status.COMPLETED);
    }

    @Test
    void testWorkerRunnerCircuitBreakerIncrementCount() throws Exception {
        // Setup
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );

        // Session setup
        when(taskManager.sessionExists(SESSION_ID)).thenReturn(true);
        when(taskManager.getParentSessionId(SESSION_ID)).thenReturn(MASTER_SESSION_ID);
        when(localStreamManager.fetchContext(SESSION_ID)).thenReturn(new ArrayList<>());
        when(injectionEngine.injectInitialContext(eq(MASTER_SESSION_ID), anyString()))
            .thenReturn(UnifiedMessage.system("Initial prompt"));

        // LLM client setup - 3 iterations before completion
        LlmClient llmClientMock = mock(LlmClient.class);
        when(modelRouter.routeByRole("WORKER")).thenReturn(llmClientMock);
        when(localStreamManager.getCurrentTokenCount(SESSION_ID)).thenReturn(1000);

        // First two responses: Tool calls
        UnifiedMessage toolCallResponse1 = UnifiedMessage.ofToolCalls(
            List.of(new UnifiedMessage.ToolCall("call-1", "test_tool", "{}"))
        );
        UnifiedMessage toolCallResponse2 = UnifiedMessage.ofToolCalls(
            List.of(new UnifiedMessage.ToolCall("call-2", "test_tool", "{}"))
        );
        // Third response: Text completion
        UnifiedMessage textResponse = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "Task complete");

        when(llmClientMock.chat(any(List.class), any(String.class)))
            .thenReturn(toolCallResponse1)
            .thenReturn(toolCallResponse2)
            .thenReturn(textResponse);

        // Tool setup
        AgentTool mockTool = mock(AgentTool.class);
        when(toolRegistry.getTool("test_tool")).thenReturn(mockTool);
        when(mockTool.execute(anyString())).thenReturn("Success");

        // Execute
        assertDoesNotThrow(() -> workerRunner.run(SESSION_ID));

        // Verify CircuitBreaker was incremented exactly 3 times (once per iteration)
        verify(circuitBreaker, times(3)).checkAndIncrement(SESSION_ID);

        // Verify CondensationEngine was called once at completion
        verify(condensationEngine).condenseAndMergeUpwards(SESSION_ID, MASTER_SESSION_ID);

        // Verify task completed
        verify(taskManager).transitionState(SESSION_ID,
            tech.yesboss.persistence.entity.TaskSession.Status.COMPLETED);
    }

    @Test
    void testWorkerRunnerSuspensionTriggersGracefulExit() throws Exception {
        // Setup
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );

        // Session setup - resuming from suspension
        List<UnifiedMessage> context = List.of(
            UnifiedMessage.ofToolResult("call-123", "Tool output", false)
        );
        when(taskManager.sessionExists(SESSION_ID)).thenReturn(true);
        when(localStreamManager.fetchContext(SESSION_ID)).thenReturn(context);

        // LLM client setup
        LlmClient llmClientMock = mock(LlmClient.class);
        when(modelRouter.routeByRole("WORKER")).thenReturn(llmClientMock);

        // Response: Tool call that will be intercepted
        UnifiedMessage toolCallResponse = UnifiedMessage.ofToolCalls(
            List.of(new UnifiedMessage.ToolCall("call-456", "dangerous_tool", "{}"))
        );
        when(llmClientMock.chat(any(List.class), any(String.class))).thenReturn(toolCallResponse);

        // Tool setup - throws SuspendExecutionException
        AgentTool mockTool = mock(AgentTool.class);
        when(toolRegistry.getTool("dangerous_tool")).thenReturn(mockTool);

        // SandboxInterceptor throws SuspendExecutionException
        doThrow(new SuspendExecutionException("rm -rf /", "call-456"))
            .when(sandboxInterceptor).preCheck(any(), any(), any());

        // Execute
        assertDoesNotThrow(() -> workerRunner.run(SESSION_ID));

        // Verify SuspendResumeEngine.suspendForApproval was called
        verify(suspendResumeEngine).suspendForApproval(
            eq(SESSION_ID),
            eq("rm -rf /"),
            eq("call-456")
        );

        // Verify intercepted execution was tracked
        verify(toolCallTracker).trackExecution(
            eq(SESSION_ID),
            eq("call-456"),
            eq("dangerous_tool"),
            eq("{}"),
            contains("SuspendExecutionException"),
            eq(true)
        );

        // Verify CondensationEngine was NOT called (suspension, not completion)
        verify(condensationEngine, never()).condenseAndMergeUpwards(any(), any());

        // Verify task was NOT marked as COMPLETED
        verify(taskManager, never()).transitionState(eq(SESSION_ID),
            eq(tech.yesboss.persistence.entity.TaskSession.Status.COMPLETED));
    }

    @Test
    void testWorkerRunnerSuspensionExitsGracefullyWithoutException() throws Exception {
        // Setup
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );

        // Session setup
        when(taskManager.sessionExists(SESSION_ID)).thenReturn(true);
        when(taskManager.getParentSessionId(SESSION_ID)).thenReturn(MASTER_SESSION_ID);
        when(localStreamManager.fetchContext(SESSION_ID)).thenReturn(new ArrayList<>());
        when(injectionEngine.injectInitialContext(eq(MASTER_SESSION_ID), anyString()))
            .thenReturn(UnifiedMessage.system("Initial prompt"));

        // LLM client setup
        LlmClient llmClientMock = mock(LlmClient.class);
        when(modelRouter.routeByRole("WORKER")).thenReturn(llmClientMock);

        // Response: Tool call that will be intercepted
        UnifiedMessage toolCallResponse = UnifiedMessage.ofToolCalls(
            List.of(new UnifiedMessage.ToolCall("call-789", "unsafe_tool", "{}"))
        );
        when(llmClientMock.chat(any(List.class), any(String.class))).thenReturn(toolCallResponse);

        // Tool setup
        AgentTool mockTool = mock(AgentTool.class);
        when(toolRegistry.getTool("unsafe_tool")).thenReturn(mockTool);

        // SandboxInterceptor throws SuspendExecutionException
        doThrow(new SuspendExecutionException("format_disk", "call-789"))
            .when(sandboxInterceptor).preCheck(any(), any(), any());

        // Execute - should NOT throw any exception
        assertDoesNotThrow(() -> workerRunner.run(SESSION_ID));

        // Verify graceful suspension
        verify(suspendResumeEngine).suspendForApproval(
            eq(SESSION_ID),
            eq("format_disk"),
            eq("call-789")
        );

        // Verify thread exited cleanly (no re-thrown exception)
        // The fact we got here without exception proves graceful exit
    }

    @Test
    void testWorkerRunnerCondensationEngineCalledOnCompletion() throws Exception {
        // Setup
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );

        // Session setup
        when(taskManager.sessionExists(SESSION_ID)).thenReturn(true);
        when(taskManager.getParentSessionId(SESSION_ID)).thenReturn(MASTER_SESSION_ID);
        when(localStreamManager.fetchContext(SESSION_ID)).thenReturn(new ArrayList<>());
        when(injectionEngine.injectInitialContext(eq(MASTER_SESSION_ID), anyString()))
            .thenReturn(UnifiedMessage.system("Initial prompt"));

        // LLM client setup - immediate text completion
        LlmClient llmClientMock = mock(LlmClient.class);
        when(modelRouter.routeByRole("WORKER")).thenReturn(llmClientMock);
        when(localStreamManager.getCurrentTokenCount(SESSION_ID)).thenReturn(1000);

        UnifiedMessage textResponse = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "Task completed successfully");
        when(llmClientMock.chat(any(List.class), any(String.class))).thenReturn(textResponse);

        // Execute
        assertDoesNotThrow(() -> workerRunner.run(SESSION_ID));

        // Verify CondensationEngine.condenseAndMergeUpwards was called exactly once
        verify(condensationEngine, times(1)).condenseAndMergeUpwards(SESSION_ID, MASTER_SESSION_ID);

        // Verify task was marked as COMPLETED
        verify(taskManager).transitionState(SESSION_ID,
            tech.yesboss.persistence.entity.TaskSession.Status.COMPLETED);

        // Verify no tool was executed (no tool calls in response)
        verify(toolCallTracker, never()).trackExecution(
            any(), any(), any(), any(), any(), anyBoolean()
        );
    }

    @Test
    void testWorkerRunnerReActLoopWithMultipleToolCalls() throws Exception {
        // Setup
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );

        // Session setup
        when(taskManager.sessionExists(SESSION_ID)).thenReturn(true);
        when(taskManager.getParentSessionId(SESSION_ID)).thenReturn(MASTER_SESSION_ID);
        when(localStreamManager.fetchContext(SESSION_ID)).thenReturn(new ArrayList<>());
        when(injectionEngine.injectInitialContext(eq(MASTER_SESSION_ID), anyString()))
            .thenReturn(UnifiedMessage.system("Initial prompt"));

        // LLM client setup
        LlmClient llmClientMock = mock(LlmClient.class);
        when(modelRouter.routeByRole("WORKER")).thenReturn(llmClientMock);
        when(localStreamManager.getCurrentTokenCount(SESSION_ID)).thenReturn(1000);

        // Response with multiple tool calls
        UnifiedMessage multiToolResponse = UnifiedMessage.ofToolCalls(
            List.of(
                new UnifiedMessage.ToolCall("call-1", "tool_a", "{\"arg\":\"a\"}"),
                new UnifiedMessage.ToolCall("call-2", "tool_b", "{\"arg\":\"b\"}"),
                new UnifiedMessage.ToolCall("call-3", "tool_c", "{\"arg\":\"c\"}")
            )
        );
        // Followed by completion
        UnifiedMessage textResponse = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "All done");

        when(llmClientMock.chat(any(List.class), any(String.class)))
            .thenReturn(multiToolResponse)
            .thenReturn(textResponse);

        // Tools setup
        AgentTool toolA = mock(AgentTool.class);
        AgentTool toolB = mock(AgentTool.class);
        AgentTool toolC = mock(AgentTool.class);
        when(toolRegistry.getTool("tool_a")).thenReturn(toolA);
        when(toolRegistry.getTool("tool_b")).thenReturn(toolB);
        when(toolRegistry.getTool("tool_c")).thenReturn(toolC);
        when(toolA.execute(anyString())).thenReturn("Result A");
        when(toolB.execute(anyString())).thenReturn("Result B");
        when(toolC.execute(anyString())).thenReturn("Result C");

        // Execute
        assertDoesNotThrow(() -> workerRunner.run(SESSION_ID));

        // Verify all tools were executed
        verify(toolA).execute("{\"arg\":\"a\"}");
        verify(toolB).execute("{\"arg\":\"b\"}");
        verify(toolC).execute("{\"arg\":\"c\"}");

        // Verify all tool calls were tracked
        verify(toolCallTracker).trackExecution(SESSION_ID, "call-1", "tool_a", "{\"arg\":\"a\"}", "Result A", false);
        verify(toolCallTracker).trackExecution(SESSION_ID, "call-2", "tool_b", "{\"arg\":\"b\"}", "Result B", false);
        verify(toolCallTracker).trackExecution(SESSION_ID, "call-3", "tool_c", "{\"arg\":\"c\"}", "Result C", false);

        // Verify completion
        verify(condensationEngine).condenseAndMergeUpwards(SESSION_ID, MASTER_SESSION_ID);
    }

    @Test
    void testWorkerRunnerToolErrorContinuesLoop() throws Exception {
        // Setup
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );

        // Session setup
        when(taskManager.sessionExists(SESSION_ID)).thenReturn(true);
        when(taskManager.getParentSessionId(SESSION_ID)).thenReturn(MASTER_SESSION_ID);
        when(localStreamManager.fetchContext(SESSION_ID)).thenReturn(new ArrayList<>());
        when(injectionEngine.injectInitialContext(eq(MASTER_SESSION_ID), anyString()))
            .thenReturn(UnifiedMessage.system("Initial prompt"));

        // LLM client setup
        LlmClient llmClientMock = mock(LlmClient.class);
        when(modelRouter.routeByRole("WORKER")).thenReturn(llmClientMock);
        when(localStreamManager.getCurrentTokenCount(SESSION_ID)).thenReturn(1000);

        // Tool call response, then completion
        UnifiedMessage toolCallResponse = UnifiedMessage.ofToolCalls(
            List.of(new UnifiedMessage.ToolCall("call-1", "failing_tool", "{}"))
        );
        UnifiedMessage textResponse = UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "Done");

        when(llmClientMock.chat(any(List.class), any(String.class)))
            .thenReturn(toolCallResponse)
            .thenReturn(textResponse);

        // Tool setup - throws exception (not SuspendExecutionException)
        AgentTool mockTool = mock(AgentTool.class);
        when(toolRegistry.getTool("failing_tool")).thenReturn(mockTool);
        when(mockTool.execute(anyString())).thenThrow(new RuntimeException("Tool failed"));

        // Execute
        assertDoesNotThrow(() -> workerRunner.run(SESSION_ID));

        // Verify error result was appended to local stream
        verify(localStreamManager).appendToolResult(eq(SESSION_ID), argThat(msg ->
            msg.toolResults().size() == 1 &&
            msg.toolResults().get(0).toolCallId().equals("call-1") &&
            msg.toolResults().get(0).isError() == true &&
            msg.toolResults().get(0).resultString().contains("Tool execution failed")
        ));

        // Verify loop continued and completed successfully
        verify(condensationEngine).condenseAndMergeUpwards(SESSION_ID, MASTER_SESSION_ID);
        verify(taskManager).transitionState(SESSION_ID,
            tech.yesboss.persistence.entity.TaskSession.Status.COMPLETED);
    }

    @Test
    void testWorkerRunnerCircuitBreakerOpenExceptionHandling() throws Exception {
        // Setup
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            condensationEngine,
            modelRouter,
            circuitBreaker,
            toolRegistry,
            sandboxInterceptor,
            suspendResumeEngine,
            toolCallTracker
        );

        // Session setup
        when(taskManager.sessionExists(SESSION_ID)).thenReturn(true);
        when(taskManager.getParentSessionId(SESSION_ID)).thenReturn(MASTER_SESSION_ID);
        when(localStreamManager.fetchContext(SESSION_ID)).thenReturn(new ArrayList<>());
        when(injectionEngine.injectInitialContext(eq(MASTER_SESSION_ID), anyString()))
            .thenReturn(UnifiedMessage.system("Initial prompt"));

        // CircuitBreaker throws exception on 3rd iteration
        doNothing().when(circuitBreaker).checkAndIncrement(SESSION_ID);
        doThrow(new CircuitBreakerOpenException("Circuit breaker threshold reached", SESSION_ID, 20, 20))
            .when(circuitBreaker).checkAndIncrement(SESSION_ID);

        // Execute
        CircuitBreakerOpenException exception = assertThrows(
            CircuitBreakerOpenException.class,
            () -> workerRunner.run(SESSION_ID)
        );

        // Verify task was marked as FAILED
        verify(taskManager).transitionState(SESSION_ID,
            tech.yesboss.persistence.entity.TaskSession.Status.FAILED);

        // Verify CondensationEngine was NOT called
        verify(condensationEngine, never()).condenseAndMergeUpwards(any(), any());
    }
}
