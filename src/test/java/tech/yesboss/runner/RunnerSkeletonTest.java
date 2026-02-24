package tech.yesboss.runner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.yesboss.context.GlobalStreamManager;
import tech.yesboss.context.LocalStreamManager;
import tech.yesboss.context.engine.InjectionEngine;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.llm.impl.ModelRouter;
import tech.yesboss.runner.impl.MasterRunnerImpl;
import tech.yesboss.runner.impl.WorkerRunnerImpl;
import tech.yesboss.safeguard.CircuitBreaker;
import tech.yesboss.state.TaskManager;

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
    private ModelRouter modelRouter;
    private CircuitBreaker circuitBreaker;

    private static final String SESSION_ID = "session-123";
    private static final String MASTER_SESSION_ID = "master-session-456";

    @BeforeEach
    void setUp() {
        taskManager = mock(TaskManager.class);
        globalStreamManager = mock(GlobalStreamManager.class);
        localStreamManager = mock(LocalStreamManager.class);
        injectionEngine = mock(InjectionEngine.class);
        modelRouter = mock(ModelRouter.class);
        circuitBreaker = mock(CircuitBreaker.class);
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
            modelRouter,
            circuitBreaker
        ));
    }

    @Test
    void testWorkerRunnerConstructorWithNullTaskManager() {
        assertThrows(IllegalArgumentException.class, () -> new WorkerRunnerImpl(
            null,
            localStreamManager,
            injectionEngine,
            modelRouter,
            circuitBreaker
        ));
    }

    @Test
    void testWorkerRunnerConstructorWithNullLocalStreamManager() {
        assertThrows(IllegalArgumentException.class, () -> new WorkerRunnerImpl(
            taskManager,
            null,
            injectionEngine,
            modelRouter,
            circuitBreaker
        ));
    }

    @Test
    void testWorkerRunnerConstructorWithNullInjectionEngine() {
        assertThrows(IllegalArgumentException.class, () -> new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            null,
            modelRouter,
            circuitBreaker
        ));
    }

    @Test
    void testWorkerRunnerConstructorWithNullModelRouter() {
        assertThrows(IllegalArgumentException.class, () -> new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            null,
            circuitBreaker
        ));
    }

    @Test
    void testWorkerRunnerConstructorWithNullCircuitBreaker() {
        assertThrows(IllegalArgumentException.class, () -> new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            modelRouter,
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
            modelRouter,
            circuitBreaker
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
            modelRouter,
            circuitBreaker
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
            modelRouter,
            circuitBreaker
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
            modelRouter,
            circuitBreaker
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
            modelRouter,
            circuitBreaker
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
            modelRouter,
            circuitBreaker
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
            modelRouter,
            circuitBreaker
        );
        when(taskManager.sessionExists(SESSION_ID)).thenReturn(true);
        when(taskManager.getParentSessionId(SESSION_ID)).thenReturn(MASTER_SESSION_ID);
        when(localStreamManager.fetchContext(SESSION_ID)).thenReturn(new ArrayList<>());
        when(injectionEngine.injectInitialContext(eq(MASTER_SESSION_ID), anyString()))
            .thenReturn(UnifiedMessage.system("Initial prompt"));

        // Execute
        assertDoesNotThrow(() -> workerRunner.run(SESSION_ID));

        // Verify initialization was called
        verify(injectionEngine).injectInitialContext(eq(MASTER_SESSION_ID), anyString());
        verify(localStreamManager).appendWorkerMessage(eq(SESSION_ID), any());
    }

    @Test
    void testWorkerRunnerRunWithValidSessionResumingWorker() {
        // Setup
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            modelRouter,
            circuitBreaker
        );
        List<UnifiedMessage> context = List.of(
            UnifiedMessage.ofToolResult("call-123", "Tool output", false)
        );
        when(taskManager.sessionExists(SESSION_ID)).thenReturn(true);
        when(localStreamManager.fetchContext(SESSION_ID)).thenReturn(context);

        // Execute
        assertDoesNotThrow(() -> workerRunner.run(SESSION_ID));

        // Verify initialization was NOT called (resuming from suspension)
        verify(injectionEngine, never()).injectInitialContext(any(), any());
    }

    @Test
    void testWorkerRunnerRunWithNullSessionId() {
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            modelRouter,
            circuitBreaker
        );

        assertThrows(IllegalArgumentException.class, () -> workerRunner.run(null));
    }

    @Test
    void testWorkerRunnerRunWithNonExistentSession() {
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            modelRouter,
            circuitBreaker
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
            modelRouter,
            circuitBreaker
        );
        when(taskManager.sessionExists(SESSION_ID)).thenReturn(true);

        // Execute
        String report = workerRunner.generateExecutionReport(SESSION_ID);

        // Verify
        assertNotNull(report);
        assertTrue(report.contains(SESSION_ID));
        assertTrue(report.contains("COMPLETED"));
    }

    @Test
    void testWorkerRunnerGenerateExecutionReportWithNonExistentSession() {
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            modelRouter,
            circuitBreaker
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
            modelRouter,
            circuitBreaker
        );

        assertNotNull(workerRunner.getVirtualThreadExecutor());
    }

    @Test
    void testWorkerRunnerShutdown() {
        WorkerRunnerImpl workerRunner = new WorkerRunnerImpl(
            taskManager,
            localStreamManager,
            injectionEngine,
            modelRouter,
            circuitBreaker
        );

        assertDoesNotThrow(() -> workerRunner.shutdown());
    }
}
