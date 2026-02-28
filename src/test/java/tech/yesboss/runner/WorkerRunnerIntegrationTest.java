package tech.yesboss.runner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import tech.yesboss.context.LocalStreamManager;
import tech.yesboss.context.engine.CondensationEngine;
import tech.yesboss.context.engine.InjectionEngine;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.llm.LlmClient;
import tech.yesboss.llm.impl.ModelRouter;
import tech.yesboss.llm.impl.ZhipuLlmClient;
import tech.yesboss.persistence.entity.TaskSession;
import tech.yesboss.runner.impl.WorkerRunnerImpl;
import tech.yesboss.safeguard.CircuitBreaker;
import tech.yesboss.safeguard.SuspendResumeEngine;
import tech.yesboss.state.TaskManager;
import tech.yesboss.tool.CalculatorTool;
import tech.yesboss.tool.EchoTool;
import tech.yesboss.tool.registry.ToolRegistry;
import tech.yesboss.tool.sandbox.SandboxInterceptor;
import tech.yesboss.tool.tracker.ToolCallTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration test for WorkerRunner ReAct loop with real Zhipu LLM.
 *
 * <p>This test makes real API calls to Zhipu GLM and requires ZHIPU_API_KEY
 * environment variable to be set. Tests are @Disabled by default.</p>
 */
@Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
class WorkerRunnerIntegrationTest {

    private static final String TEST_API_KEY = System.getenv("ZHIPU_API_KEY");
    private static final String TEST_SESSION_ID = "test-worker-session-123";
    private static final String MASTER_SESSION_ID = "test-master-session-456";

    private LlmClient realLlmClient;
    private ModelRouter modelRouter;
    private WorkerRunnerImpl workerRunner;
    private ToolRegistry toolRegistry;

    // Mocked dependencies
    private TaskManager mockTaskManager;
    private LocalStreamManager mockLocalStreamManager;
    private InjectionEngine mockInjectionEngine;
    private CondensationEngine mockCondensationEngine;
    private CircuitBreaker mockCircuitBreaker;
    private SandboxInterceptor mockSandboxInterceptor;
    private SuspendResumeEngine mockSuspendResumeEngine;
    private ToolCallTracker mockToolCallTracker;

    // Track method calls
    private final List<UnifiedMessage> localStreamMessages = new ArrayList<>();
    private final AtomicInteger appendWorkerMessageCount = new AtomicInteger(0);
    private final AtomicBoolean taskCompleted = new AtomicBoolean(false);

    @BeforeEach
    void setUp() {
        // Skip if API key is not available
        if (TEST_API_KEY == null || TEST_API_KEY.trim().isEmpty()) {
            return;
        }

        // Initialize real ZhipuLlmClient
        realLlmClient = new ZhipuLlmClient(TEST_API_KEY, "glm-4-flash");
        modelRouter = new ModelRouter(realLlmClient, realLlmClient);

        // Create real ToolRegistry and register test tools
        tech.yesboss.tool.registry.impl.ToolRegistryImpl registryImpl =
                new tech.yesboss.tool.registry.impl.ToolRegistryImpl();
        registryImpl.register(new CalculatorTool());
        registryImpl.register(new EchoTool());
        toolRegistry = registryImpl;

        // Create all mocked dependencies
        mockTaskManager = mock(TaskManager.class);
        mockLocalStreamManager = mock(LocalStreamManager.class);
        mockInjectionEngine = mock(InjectionEngine.class);
        mockCondensationEngine = mock(CondensationEngine.class);
        mockCircuitBreaker = mock(CircuitBreaker.class);
        mockSandboxInterceptor = mock(SandboxInterceptor.class);
        mockSuspendResumeEngine = mock(SuspendResumeEngine.class);
        mockToolCallTracker = mock(ToolCallTracker.class);

        // Configure mockTaskManager
        when(mockTaskManager.sessionExists(eq(TEST_SESSION_ID))).thenReturn(true);
        when(mockTaskManager.getParentSessionId(eq(TEST_SESSION_ID))).thenReturn(MASTER_SESSION_ID);
        when(mockTaskManager.getStatus(eq(TEST_SESSION_ID)))
                .thenReturn(TaskSession.Status.RUNNING)
                .thenReturn(TaskSession.Status.COMPLETED);

        // Configure mockLocalStreamManager to return context and track messages
        when(mockLocalStreamManager.fetchContext(eq(TEST_SESSION_ID)))
                .thenAnswer(invocation -> {
                    // Return tracked messages
                    List<UnifiedMessage> messages = new ArrayList<>(localStreamMessages);
                    logDebug("fetchContext returning {} messages", messages.size());
                    return messages;
                });

        // Track appendWorkerMessage calls
        doAnswer(invocation -> {
            UnifiedMessage msg = invocation.getArgument(1);
            localStreamMessages.add(msg);
            int count = appendWorkerMessageCount.incrementAndGet();
            logDebug("appendWorkerMessage called (call #{}, total messages: {})", count, localStreamMessages.size());
            return null;
        }).when(mockLocalStreamManager).appendWorkerMessage(eq(TEST_SESSION_ID), any());

        // Track appendToolResult calls
        doAnswer(invocation -> {
            UnifiedMessage msg = invocation.getArgument(1);
            localStreamMessages.add(msg);
            logDebug("appendToolResult called, total messages: {}", localStreamMessages.size());
            return null;
        }).when(mockLocalStreamManager).appendToolResult(eq(TEST_SESSION_ID), any());

        when(mockLocalStreamManager.getCurrentTokenCount(eq(TEST_SESSION_ID))).thenReturn(0);

        // Configure mockInjectionEngine
        UnifiedMessage systemPrompt = UnifiedMessage.system(
                "You are a helpful assistant with access to a calculator tool. " +
                "Use the calculator to solve math problems."
        );
        when(mockInjectionEngine.injectInitialContext(eq(MASTER_SESSION_ID), anyString()))
                .thenReturn(systemPrompt);

        // Configure mockCircuitBreaker to allow all iterations
        doNothing().when(mockCircuitBreaker).checkAndIncrement(anyString());

        // Configure mockSandboxInterceptor to allow all tools
        doNothing().when(mockSandboxInterceptor).preCheck(any(), anyString(), anyString());

        // Configure mockToolCallTracker
        doNothing().when(mockToolCallTracker).trackExecution(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean()
        );

        // Configure mockCondensationEngine
        doNothing().when(mockCondensationEngine).condenseAndMergeUpwards(anyString(), anyString());

        // Track task completion
        doAnswer(invocation -> {
            TaskSession.Status status = invocation.getArgument(1);
            if (status == TaskSession.Status.COMPLETED) {
                taskCompleted.set(true);
                logInfo("Task marked as COMPLETED");
            }
            return null;
        }).when(mockTaskManager).transitionState(eq(TEST_SESSION_ID), any());

        // Create WorkerRunnerImpl with all dependencies
        workerRunner = new WorkerRunnerImpl(
                mockTaskManager,
                mockLocalStreamManager,
                mockInjectionEngine,
                mockCondensationEngine,
                modelRouter,
                mockCircuitBreaker,
                toolRegistry,
                mockSandboxInterceptor,
                mockSuspendResumeEngine,
                mockToolCallTracker
        );
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testWorkerReActLoopWithRealLlm() throws Exception {
        if (TEST_API_KEY == null || TEST_API_KEY.trim().isEmpty()) {
            return;
        }

        logInfo("Starting WorkerRunner ReAct loop integration test");

        // Prepare initial context: user message with calculator task
        UnifiedMessage userMessage = UnifiedMessage.user(
                "Please use the calculator to add 15 and 27. What is the result?"
        );
        localStreamMessages.add(userMessage);

        // Run the worker
        CountDownLatch completionLatch = new CountDownLatch(1);

        Thread workerThread = new Thread(() -> {
            try {
                workerRunner.run(TEST_SESSION_ID);
                logInfo("WorkerRunner completed");
            } finally {
                completionLatch.countDown();
            }
        });

        workerThread.start();
        boolean completed = completionLatch.await(60, TimeUnit.SECONDS);

        assertTrue(completed, "WorkerRunner should complete within 60 seconds");
        assertTrue(taskCompleted.get(), "Task should be marked as COMPLETED");

        // Verify ReAct loop executed
        int messageCount = appendWorkerMessageCount.get();
        logInfo("Total appendWorkerMessage calls: {}", messageCount);
        assertTrue(messageCount >= 2, "appendWorkerMessage should be called at least 2 times " +
                "(initial context + LLM response + tool results)");

        // Verify tool was executed
        verify(mockToolCallTracker, atLeastOnce()).trackExecution(
                eq(TEST_SESSION_ID),
                anyString(),
                eq("calculator"),
                anyString(),
                anyString(),
                eq(false)
        );

        logInfo("Integration test passed successfully");

        // Cleanup
        workerRunner.shutdown();
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testWorkerReActLoopWithEchoTool() throws Exception {
        if (TEST_API_KEY == null || TEST_API_KEY.trim().isEmpty()) {
            return;
        }

        logInfo("Starting WorkerRunner ReAct loop with EchoTool integration test");

        // Prepare initial context: user message with echo task
        UnifiedMessage userMessage = UnifiedMessage.user(
                "Please use the echo tool to return the message 'Hello, World!'"
        );
        localStreamMessages.add(userMessage);

        // Run the worker
        CountDownLatch completionLatch = new CountDownLatch(1);
        AtomicBoolean testPassed = new AtomicBoolean(false);

        Thread workerThread = new Thread(() -> {
            try {
                workerRunner.run(TEST_SESSION_ID);

                // Verify echo tool was called
                verify(mockToolCallTracker, atLeastOnce()).trackExecution(
                        eq(TEST_SESSION_ID),
                        anyString(),
                        eq("echo"),
                        anyString(),
                        anyString(),
                        eq(false)
                );

                testPassed.set(true);
                logInfo("Echo tool integration test passed");
            } finally {
                completionLatch.countDown();
            }
        });

        workerThread.start();
        boolean completed = completionLatch.await(60, TimeUnit.SECONDS);

        assertTrue(completed, "WorkerRunner should complete within 60 seconds");
        assertTrue(testPassed.get(), "Echo tool should have been called");

        // Cleanup
        workerRunner.shutdown();
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testWorkerReActLoopWithMultiStepCalculation() throws Exception {
        if (TEST_API_KEY == null || TEST_API_KEY.trim().isEmpty()) {
            return;
        }

        logInfo("Starting multi-step calculation integration test");

        // Prepare initial context: user message with multi-step calculation
        UnifiedMessage userMessage = UnifiedMessage.user(
                "Calculate 15 + 27, then multiply the result by 2"
        );
        localStreamMessages.add(userMessage);

        // Run the worker
        CountDownLatch completionLatch = new CountDownLatch(1);

        Thread workerThread = new Thread(() -> {
            try {
                workerRunner.run(TEST_SESSION_ID);
                logInfo("Multi-step calculation test completed");
            } finally {
                completionLatch.countDown();
            }
        });

        workerThread.start();
        boolean completed = completionLatch.await(60, TimeUnit.SECONDS);

        assertTrue(completed, "WorkerRunner should complete within 60 seconds");

        // Verify calculator was called at least once
        verify(mockToolCallTracker, atLeast(1)).trackExecution(
                eq(TEST_SESSION_ID),
                anyString(),
                eq("calculator"),
                anyString(),
                anyString(),
                eq(false)
        );

        logInfo("Multi-step calculation test passed");

        // Cleanup
        workerRunner.shutdown();
    }

    // Helper methods for logging
    private void logInfo(String format, Object... args) {
        System.out.println("[INFO] " + String.format(format, args));
    }

    private void logDebug(String format, Object... args) {
        System.out.println("[DEBUG] " + String.format(format, args));
    }
}
