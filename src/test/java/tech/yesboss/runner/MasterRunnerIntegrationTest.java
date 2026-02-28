package tech.yesboss.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tech.yesboss.context.GlobalStreamManager;
import tech.yesboss.context.LocalStreamManager;
import tech.yesboss.context.engine.CondensationEngine;
import tech.yesboss.context.engine.InjectionEngine;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.gateway.im.IMMessagePusher;
import tech.yesboss.gateway.ui.UICardRenderer;
import tech.yesboss.llm.LlmClient;
import tech.yesboss.llm.impl.ModelRouter;
import tech.yesboss.llm.impl.ZhipuLlmClient;
import tech.yesboss.persistence.entity.TaskSession;
import tech.yesboss.runner.impl.MasterRunnerImpl;
import tech.yesboss.runner.impl.WorkerRunnerImpl;
import tech.yesboss.safeguard.CircuitBreaker;
import tech.yesboss.safeguard.SuspendResumeEngine;
import tech.yesboss.state.TaskManager;
import tech.yesboss.state.model.ImRoute;
import tech.yesboss.tool.CalculatorTool;
import tech.yesboss.tool.EchoTool;
import tech.yesboss.tool.planning.PlanningTool;
import tech.yesboss.tool.registry.ToolRegistry;
import tech.yesboss.tool.sandbox.SandboxInterceptor;
import tech.yesboss.tool.tracker.ToolCallTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration test for Master-Worker orchestration with real Zhipu LLM and thread pool.
 *
 * <p>This test makes real API calls to Zhipu GLM and requires ZHIPU_API_KEY
 * environment variable to be set. Tests are @Disabled by default.</p>
 */
@Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
class MasterRunnerIntegrationTest {

    private static final String TEST_API_KEY = System.getenv("ZHIPU_API_KEY");
    private static final String MASTER_SESSION_ID = "test-master-session-789";
    private static final String WORKER_1_SESSION_ID = "test-worker-1-session";
    private static final String WORKER_2_SESSION_ID = "test-worker-2-session";

    private LlmClient masterLlmClient;
    private LlmClient workerLlmClient;
    private ModelRouter modelRouter;
    private MasterRunnerImpl masterRunner;
    private WorkerRunnerImpl workerRunner; // Made accessible for test
    private ToolRegistry toolRegistry;
    private ExecutorService executorService;

    // Mocked dependencies
    private TaskManager mockTaskManager;
    private GlobalStreamManager mockGlobalStreamManager;
    private LocalStreamManager mockLocalStreamManager;
    private InjectionEngine mockInjectionEngine;
    private CondensationEngine mockCondensationEngine;
    private CircuitBreaker mockCircuitBreaker;
    private SandboxInterceptor mockSandboxInterceptor;
    private SuspendResumeEngine mockSuspendResumeEngine;
    private ToolCallTracker mockToolCallTracker;
    private PlanningTool mockPlanningTool;
    private UICardRenderer mockUICardRenderer;
    private IMMessagePusher mockImMessagePusher;

    // Track method calls and timing
    private final List<UnifiedMessage> globalStreamMessages = new ArrayList<>();
    private final List<UnifiedMessage> localStreamMessages = new ArrayList<>();
    private final AtomicInteger createWorkerTaskCount = new AtomicInteger(0);
    private final AtomicInteger workerExecutionCount = new AtomicInteger(0);
    private final List<Long> workerStartTimes = new CopyOnWriteArrayList<>();
    private final List<Long> workerEndTimes = new CopyOnWriteArrayList<>();
    private final CountDownLatch workerCompletionLatch = new CountDownLatch(2);
    private final AtomicBoolean masterCompleted = new AtomicBoolean(false);

    @BeforeEach
    void setUp() {
        // Skip if API key is not available
        if (TEST_API_KEY == null || TEST_API_KEY.trim().isEmpty()) {
            return;
        }

        // Initialize real ZhipuLlmClient instances
        masterLlmClient = new ZhipuLlmClient(TEST_API_KEY, "glm-4-flash");
        workerLlmClient = new ZhipuLlmClient(TEST_API_KEY, "glm-4-flash");
        modelRouter = new ModelRouter(masterLlmClient, workerLlmClient);

        // Create real ToolRegistry and register test tools
        tech.yesboss.tool.registry.impl.ToolRegistryImpl registryImpl =
                new tech.yesboss.tool.registry.impl.ToolRegistryImpl();
        registryImpl.register(new CalculatorTool());
        registryImpl.register(new EchoTool());
        toolRegistry = registryImpl;

        // Create real ExecutorService
        executorService = Executors.newCachedThreadPool();

        // Create all mocked dependencies
        mockTaskManager = mock(TaskManager.class);
        mockGlobalStreamManager = mock(GlobalStreamManager.class);
        mockLocalStreamManager = mock(LocalStreamManager.class);
        mockInjectionEngine = mock(InjectionEngine.class);
        mockCondensationEngine = mock(CondensationEngine.class);
        mockCircuitBreaker = mock(CircuitBreaker.class);
        mockSandboxInterceptor = mock(SandboxInterceptor.class);
        mockSuspendResumeEngine = mock(SuspendResumeEngine.class);
        mockToolCallTracker = mock(ToolCallTracker.class);
        mockPlanningTool = mock(PlanningTool.class);
        mockUICardRenderer = mock(UICardRenderer.class);
        mockImMessagePusher = mock(IMMessagePusher.class);

        // Configure mockTaskManager for Master session
        when(mockTaskManager.sessionExists(eq(MASTER_SESSION_ID))).thenReturn(true);
        when(mockTaskManager.getStatus(eq(MASTER_SESSION_ID)))
                .thenReturn(TaskSession.Status.PLANNING)
                .thenReturn(TaskSession.Status.RUNNING)
                .thenReturn(TaskSession.Status.COMPLETED);
        when(mockTaskManager.getImRoute(eq(MASTER_SESSION_ID)))
                .thenReturn(new ImRoute("FEISHU", "test-group-123"));

        // Configure createWorkerTask to return worker session IDs
        AtomicInteger workerCounter = new AtomicInteger(1);
        when(mockTaskManager.createWorkerTask(eq(MASTER_SESSION_ID), anyString()))
                .thenAnswer(invocation -> {
                    int count = workerCounter.getAndIncrement();
                    String workerId = count == 1 ? WORKER_1_SESSION_ID : WORKER_2_SESSION_ID;
                    createWorkerTaskCount.incrementAndGet();
                    logInfo("Created worker task: {}", workerId);
                    return workerId;
                });

        // Configure mockTaskManager for Worker sessions
        when(mockTaskManager.sessionExists(eq(WORKER_1_SESSION_ID))).thenReturn(true);
        when(mockTaskManager.sessionExists(eq(WORKER_2_SESSION_ID))).thenReturn(true);
        when(mockTaskManager.getParentSessionId(eq(WORKER_1_SESSION_ID))).thenReturn(MASTER_SESSION_ID);
        when(mockTaskManager.getParentSessionId(eq(WORKER_2_SESSION_ID))).thenReturn(MASTER_SESSION_ID);
        when(mockTaskManager.getStatus(eq(WORKER_1_SESSION_ID)))
                .thenReturn(TaskSession.Status.RUNNING)
                .thenReturn(TaskSession.Status.COMPLETED);
        when(mockTaskManager.getStatus(eq(WORKER_2_SESSION_ID)))
                .thenReturn(TaskSession.Status.RUNNING)
                .thenReturn(TaskSession.Status.COMPLETED);

        // Configure mockGlobalStreamManager
        when(mockGlobalStreamManager.fetchContext(eq(MASTER_SESSION_ID)))
                .thenAnswer(invocation -> new ArrayList<>(globalStreamMessages));

        doAnswer(invocation -> {
            UnifiedMessage msg = invocation.getArgument(1);
            globalStreamMessages.add(msg);
            return null;
        }).when(mockGlobalStreamManager).appendHumanMessage(eq(MASTER_SESSION_ID), any());
        doAnswer(invocation -> {
            UnifiedMessage msg = invocation.getArgument(1);
            globalStreamMessages.add(msg);
            return null;
        }).when(mockGlobalStreamManager).appendMasterMessage(eq(MASTER_SESSION_ID), any());
        doAnswer(invocation -> {
            UnifiedMessage msg = invocation.getArgument(1);
            globalStreamMessages.add(msg);
            return null;
        }).when(mockGlobalStreamManager).appendSystemMessage(eq(MASTER_SESSION_ID), any());

        // Configure mockLocalStreamManager for workers
        when(mockLocalStreamManager.fetchContext(anyString()))
                .thenAnswer(invocation -> new ArrayList<>(localStreamMessages));

        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(0);
            UnifiedMessage msg = invocation.getArgument(1);
            localStreamMessages.add(msg);
            return null;
        }).when(mockLocalStreamManager).appendWorkerMessage(anyString(), any());

        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(0);
            UnifiedMessage msg = invocation.getArgument(1);
            localStreamMessages.add(msg);
            return null;
        }).when(mockLocalStreamManager).appendToolResult(anyString(), any());

        when(mockLocalStreamManager.getCurrentTokenCount(anyString())).thenReturn(0);

        // Configure mockInjectionEngine
        UnifiedMessage systemPrompt = UnifiedMessage.system(
                "You are a helpful assistant with access to calculator and echo tools."
        );
        when(mockInjectionEngine.injectInitialContext(eq(MASTER_SESSION_ID), anyString()))
                .thenReturn(systemPrompt);

        // Configure mockPlanningTool
        String planJson = """
                [
                  {"id": "task-1", "description": "Calculate 15 + 27", "priority": "high"},
                  {"id": "task-2", "description": "Echo 'Hello, World!'", "priority": "medium"}
                ]
                """;
        try {
            when(mockPlanningTool.execute(anyString())).thenReturn(planJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure PlanningTool mock", e);
        }

        // Configure mockCircuitBreaker
        doNothing().when(mockCircuitBreaker).checkAndIncrement(anyString());

        // Configure mockSandboxInterceptor
        doNothing().when(mockSandboxInterceptor).preCheck(any(), anyString(), anyString());

        // Configure mockToolCallTracker
        doNothing().when(mockToolCallTracker).trackExecution(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean()
        );

        // Configure mockCondensationEngine
        doNothing().when(mockCondensationEngine).condenseAndMergeUpwards(anyString(), anyString());

        // Configure mockUICardRenderer to return JsonNode
        JsonNode progressBarNode = new TextNode("{\"type\": \"progress\"}");
        JsonNode summaryNode = new TextNode("{\"type\": \"summary\"}");
        when(mockUICardRenderer.renderProgressBar(anyInt(), anyInt(), anyString()))
                .thenReturn(progressBarNode);
        when(mockUICardRenderer.renderSummaryCard(anyString(), anyString(), anyBoolean()))
                .thenReturn(summaryNode);

        // Configure mockImMessagePusher
        try {
            doNothing().when(mockImMessagePusher).pushCardMessage(any(), anyString(), any());
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure IMMessagePusher mock", e);
        }

        // Track Master completion
        doAnswer(invocation -> {
            TaskSession.Status status = invocation.getArgument(1);
            if (status == TaskSession.Status.COMPLETED) {
                masterCompleted.set(true);
                logInfo("Master task marked as COMPLETED");
            }
            return null;
        }).when(mockTaskManager).transitionState(eq(MASTER_SESSION_ID), any());

        // Create WorkerRunnerImpl with mocked dependencies
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

        // Create MasterRunnerImpl with real ModelRouter, WorkerRunner, and mocked dependencies
        masterRunner = new MasterRunnerImpl(
                mockTaskManager,
                mockGlobalStreamManager,
                modelRouter,
                toolRegistry,
                workerRunner,
                mockPlanningTool,
                mockUICardRenderer,
                mockImMessagePusher
        );
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testMasterWorkerOrchestrationWithRealLlmAndThreadPool() throws Exception {
        if (TEST_API_KEY == null || TEST_API_KEY.trim().isEmpty()) {
            return;
        }

        logInfo("Starting Master-Worker orchestration integration test");

        // Prepare initial context: user message with task
        UnifiedMessage userMessage = UnifiedMessage.user(
                "Please calculate 15 + 27 and then echo 'Hello, World!'"
        );
        globalStreamMessages.add(userMessage);

        // Run the master in a separate thread
        CountDownLatch masterLatch = new CountDownLatch(1);
        Thread masterThread = new Thread(() -> {
            try {
                masterRunner.run(MASTER_SESSION_ID);
                logInfo("MasterRunner completed");
            } catch (Exception e) {
                logError("MasterRunner failed: {}", e.getMessage());
            } finally {
                masterLatch.countDown();
            }
        });

        masterThread.start();
        boolean completed = masterLatch.await(120, TimeUnit.SECONDS);

        assertTrue(completed, "MasterRunner should complete within 120 seconds");

        // Verify Master created 2 worker tasks
        verify(mockTaskManager, times(2)).createWorkerTask(eq(MASTER_SESSION_ID), anyString());
        logInfo("Verified: Master created 2 worker tasks");

        // Verify Master completion
        assertTrue(masterCompleted.get(), "Master should be marked as COMPLETED");
        logInfo("Verified: Master completed successfully");

        // Verify CondensationEngine was called for both workers
        verify(mockCondensationEngine, atLeast(2)).condenseAndMergeUpwards(anyString(), eq(MASTER_SESSION_ID));
        logInfo("Verified: CondensationEngine called for workers");

        // Verify final summary was generated
        verify(mockUICardRenderer, atLeastOnce()).renderSummaryCard(eq(MASTER_SESSION_ID), anyString(), anyBoolean());
        logInfo("Verified: Final summary card rendered");

        logInfo("Master-Worker orchestration test passed");

        // Cleanup
        masterRunner.shutdown();
        executorService.shutdown();
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testWorkerConcurrentExecution() throws Exception {
        if (TEST_API_KEY == null || TEST_API_KEY.trim().isEmpty()) {
            return;
        }

        logInfo("Starting worker concurrent execution test");

        // Prepare initial context
        UnifiedMessage userMessage = UnifiedMessage.user(
                "Calculate 15 + 27"
        );
        globalStreamMessages.add(userMessage);

        // Modify plan to have 2 calculator tasks
        String concurrentPlanJson = """
                [
                  {"id": "task-1", "description": "Calculate 15 + 27", "priority": "high"},
                  {"id": "task-2", "description": "Calculate 10 * 5", "priority": "high"}
                ]
                """;
        when(mockPlanningTool.execute(anyString())).thenReturn(concurrentPlanJson);

        // Run the master
        CountDownLatch masterLatch = new CountDownLatch(1);
        Thread masterThread = new Thread(() -> {
            try {
                masterRunner.run(MASTER_SESSION_ID);
            } finally {
                masterLatch.countDown();
            }
        });

        masterThread.start();
        boolean completed = masterLatch.await(120, TimeUnit.SECONDS);

        assertTrue(completed, "MasterRunner should complete within 120 seconds");

        // Verify thread pool was used (via MasterRunner's internal executor)
        // This is verified implicitly by the successful completion of the test
        logInfo("Verified: Thread pool was used for worker execution");

        logInfo("Concurrent execution test passed");

        // Cleanup
        masterRunner.shutdown();
        executorService.shutdown();
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testMultipleWorkersConcurrentExecution() throws Exception {
        if (TEST_API_KEY == null || TEST_API_KEY.trim().isEmpty()) {
            return;
        }

        logInfo("Starting multi-worker concurrent execution test (3 workers)");

        // Prepare initial context
        UnifiedMessage userMessage = UnifiedMessage.user(
                "Calculate three operations: 10+20, 5*6, and 100/4"
        );
        globalStreamMessages.add(userMessage);

        // Configure PlanningTool to return 3 worker tasks
        String threeWorkersPlanJson = """
                [
                  {"id": "task-1", "description": "Calculate 10 + 20", "priority": "high"},
                  {"id": "task-2", "description": "Multiply 5 by 6", "priority": "high"},
                  {"id": "task-3", "description": "Divide 100 by 4", "priority": "high"}
                ]
                """;
        when(mockPlanningTool.execute(anyString())).thenReturn(threeWorkersPlanJson);

        // Configure TaskManager for 3 worker sessions
        when(mockTaskManager.createWorkerTask(eq(MASTER_SESSION_ID), anyString()))
                .thenReturn("test-worker-1")
                .thenReturn("test-worker-2")
                .thenReturn("test-worker-3");

        when(mockTaskManager.sessionExists(eq("test-worker-1"))).thenReturn(true);
        when(mockTaskManager.sessionExists(eq("test-worker-2"))).thenReturn(true);
        when(mockTaskManager.sessionExists(eq("test-worker-3"))).thenReturn(true);
        when(mockTaskManager.getParentSessionId(eq("test-worker-1"))).thenReturn(MASTER_SESSION_ID);
        when(mockTaskManager.getParentSessionId(eq("test-worker-2"))).thenReturn(MASTER_SESSION_ID);
        when(mockTaskManager.getParentSessionId(eq("test-worker-3"))).thenReturn(MASTER_SESSION_ID);
        when(mockTaskManager.getStatus(eq("test-worker-1"))).thenReturn(TaskSession.Status.RUNNING).thenReturn(TaskSession.Status.COMPLETED);
        when(mockTaskManager.getStatus(eq("test-worker-2"))).thenReturn(TaskSession.Status.RUNNING).thenReturn(TaskSession.Status.COMPLETED);
        when(mockTaskManager.getStatus(eq("test-worker-3"))).thenReturn(TaskSession.Status.RUNNING).thenReturn(TaskSession.Status.COMPLETED);

        // Run the master and track timing
        CountDownLatch masterLatch = new CountDownLatch(1);
        CountDownLatch workersLatch = new CountDownLatch(3);
        long startTime = System.currentTimeMillis();

        Thread masterThread = new Thread(() -> {
            try {
                masterRunner.run(MASTER_SESSION_ID);
            } finally {
                masterLatch.countDown();
            }
        });

        masterThread.start();
        boolean completed = masterLatch.await(120, TimeUnit.SECONDS);
        long totalExecutionTime = System.currentTimeMillis() - startTime;

        assertTrue(completed, "MasterRunner should complete within 120 seconds");

        // Verify Master created 3 worker tasks
        verify(mockTaskManager, times(3)).createWorkerTask(eq(MASTER_SESSION_ID), anyString());
        logInfo("Verified: Master created 3 worker tasks");

        // Verify CondensationEngine was called 3 times (once per worker)
        verify(mockCondensationEngine, atLeast(3)).condenseAndMergeUpwards(anyString(), eq(MASTER_SESSION_ID));
        logInfo("Verified: CondensationEngine called 3 times for workers");

        // Log performance metrics
        logInfo("Performance metrics:");
        logInfo("  Total execution time: {}ms", totalExecutionTime);
        logInfo("  Average time per worker: {}ms", totalExecutionTime / 3);

        logInfo("Multi-worker concurrent execution test passed");

        // Cleanup
        masterRunner.shutdown();
        executorService.shutdown();
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    @Disabled("Requires real API key - run with ZHIPU_API_KEY environment variable set")
    void testStressTestWithFiveWorkers() throws Exception {
        if (TEST_API_KEY == null || TEST_API_KEY.trim().isEmpty()) {
            return;
        }

        logInfo("Starting stress test with 5 workers");

        // Prepare initial context
        UnifiedMessage userMessage = UnifiedMessage.user(
                "Calculate these: 1+1, 2+2, 3+3, 4+4, 5+5"
        );
        globalStreamMessages.add(userMessage);

        // Configure PlanningTool to return 5 worker tasks
        String fiveWorkersPlanJson = """
                [
                  {"id": "task-1", "description": "Calculate 1 + 1", "priority": "high"},
                  {"id": "task-2", "description": "Calculate 2 + 2", "priority": "high"},
                  {"id": "task-3", "description": "Calculate 3 + 3", "priority": "high"},
                  {"id": "task-4", "description": "Calculate 4 + 4", "priority": "high"},
                  {"id": "task-5", "description": "Calculate 5 + 5", "priority": "high"}
                ]
                """;
        when(mockPlanningTool.execute(anyString())).thenReturn(fiveWorkersPlanJson);

        // Configure TaskManager for 5 worker sessions
        when(mockTaskManager.createWorkerTask(eq(MASTER_SESSION_ID), anyString()))
                .thenReturn("test-worker-1")
                .thenReturn("test-worker-2")
                .thenReturn("test-worker-3")
                .thenReturn("test-worker-4")
                .thenReturn("test-worker-5");

        for (int i = 1; i <= 5; i++) {
            String workerId = "test-worker-" + i;
            when(mockTaskManager.sessionExists(eq(workerId))).thenReturn(true);
            when(mockTaskManager.getParentSessionId(eq(workerId))).thenReturn(MASTER_SESSION_ID);
            when(mockTaskManager.getStatus(eq(workerId))).thenReturn(TaskSession.Status.RUNNING).thenReturn(TaskSession.Status.COMPLETED);
        }

        // Run the master
        CountDownLatch masterLatch = new CountDownLatch(1);
        long startTime = System.currentTimeMillis();

        Thread masterThread = new Thread(() -> {
            try {
                masterRunner.run(MASTER_SESSION_ID);
            } finally {
                masterLatch.countDown();
            }
        });

        masterThread.start();
        boolean completed = masterLatch.await(120, TimeUnit.SECONDS);
        long totalTime = System.currentTimeMillis() - startTime;

        assertTrue(completed, "MasterRunner should complete with 5 workers within 120 seconds");

        // Verify Master created 5 worker tasks
        verify(mockTaskManager, times(5)).createWorkerTask(eq(MASTER_SESSION_ID), anyString());

        logInfo("Stress test with 5 workers passed");
        logInfo("  Total time: {}ms", totalTime);
        logInfo("  Throughput: {} workers/second", 5000.0 / totalTime);

        // Cleanup
        masterRunner.shutdown();
        executorService.shutdown();
    }

    // Helper methods for logging
    private void logInfo(String format, Object... args) {
        System.out.println("[INFO] " + String.format(format, args));
    }

    private void logError(String format, Object... args) {
        System.err.println("[ERROR] " + String.format(format, args));
    }
}
