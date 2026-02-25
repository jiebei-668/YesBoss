package tech.yesboss;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import tech.yesboss.config.ConfigurationManager;
import tech.yesboss.config.YesBossConfig;
import tech.yesboss.context.GlobalStreamManager;
import tech.yesboss.context.LocalStreamManager;
import tech.yesboss.context.engine.CondensationEngine;
import tech.yesboss.context.engine.InjectionEngine;
import tech.yesboss.context.engine.impl.CondensationEngineImpl;
import tech.yesboss.context.engine.impl.InjectionEngineImpl;
import tech.yesboss.context.impl.GlobalStreamManagerImpl;
import tech.yesboss.context.impl.LocalStreamManagerImpl;
import tech.yesboss.gateway.im.IMMessagePusher;
import tech.yesboss.gateway.im.impl.IMMessagePusherImpl;
import tech.yesboss.gateway.ui.UICardRenderer;
import tech.yesboss.gateway.ui.impl.UICardRendererImpl;
import tech.yesboss.gateway.webhook.controller.WebhookController;
import tech.yesboss.gateway.webhook.controller.impl.WebhookControllerImpl;
import tech.yesboss.gateway.webhook.executor.WebhookEventExecutor;
import tech.yesboss.gateway.webhook.executor.impl.WebhookEventExecutorImpl;
import tech.yesboss.llm.LlmClient;
import tech.yesboss.llm.impl.ClaudeLlmClient;
import tech.yesboss.llm.impl.ModelRouter;
import tech.yesboss.persistence.db.DatabaseInitializer;
import tech.yesboss.persistence.db.SingleThreadDbWriter;
import tech.yesboss.persistence.repository.ChatMessageRepository;
import tech.yesboss.persistence.repository.ChatMessageRepositoryImpl;
import tech.yesboss.persistence.repository.TaskSessionRepository;
import tech.yesboss.persistence.repository.TaskSessionRepositoryImpl;
import tech.yesboss.persistence.repository.ToolExecutionRepository;
import tech.yesboss.persistence.repository.ToolExecutionRepositoryImpl;
import tech.yesboss.runner.MasterRunner;
import tech.yesboss.runner.WorkerRunner;
import tech.yesboss.runner.impl.MasterRunnerImpl;
import tech.yesboss.runner.impl.WorkerRunnerImpl;
import tech.yesboss.safeguard.CircuitBreaker;
import tech.yesboss.safeguard.SuspendResumeEngine;
import tech.yesboss.safeguard.impl.CircuitBreakerImpl;
import tech.yesboss.safeguard.impl.SuspendResumeEngineImpl;
import tech.yesboss.session.SessionManager;
import tech.yesboss.session.impl.SessionManagerImpl;
import tech.yesboss.state.TaskManager;
import tech.yesboss.state.impl.TaskManagerImpl;
import tech.yesboss.tool.SuspendExecutionException;
import tech.yesboss.tool.planning.PlanningTool;
import tech.yesboss.tool.registry.ToolRegistry;
import tech.yesboss.tool.registry.impl.ToolRegistryImpl;
import tech.yesboss.tool.sandbox.SandboxInterceptor;
import tech.yesboss.tool.sandbox.impl.SandboxInterceptorImpl;
import tech.yesboss.tool.tracker.ToolCallTracker;
import tech.yesboss.tool.tracker.impl.ToolCallTrackerImpl;

/**
 * ApplicationContext - Application Component Container
 *
 * <p>This class manages the lifecycle and dependencies of all business components in the YesBoss application.
 * It follows a layered initialization approach to ensure proper dependency injection order.</p>
 *
 * <p><b>Initialization Order:</b></p>
 * <ol>
 *   <li>Infrastructure Layer: Database, repositories, event queues</li>
 *   <li>LLM Layer: Model router, SDK adapters</li>
 *   <li>Context Layer: Stream managers, engines</li>
 *   <li>Tool Layer: Registry, sandbox, tracker</li>
 *   <li>State Layer: Task manager, safeguards</li>
 *   <li>Runner Layer: Master and Worker runners</li>
 *   <li>UI Layer: Card renderer, message pusher</li>
 *   <li>Gateway Layer: Session manager, webhook executor, webhook controller</li>
 * </ol>
 *
 * <p><b>Graceful Shutdown:</b></p>
 * <p>Provides a shutdown hook that properly releases resources in reverse order.</p>
 */
public class ApplicationContext {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationContext.class);

    private final YesBossConfig config;

    // ==================== Infrastructure Layer ====================
    private DatabaseInitializer databaseInitializer;
    private SingleThreadDbWriter dbWriter;
    private ChatMessageRepository chatMessageRepository;
    private TaskSessionRepository taskSessionRepository;
    private ToolExecutionRepository toolExecutionRepository;

    // ==================== LLM Layer ====================
    private LlmClient masterLlmClient;
    private LlmClient workerLlmClient;
    private ModelRouter modelRouter;

    // ==================== Context Layer ====================
    private GlobalStreamManager globalStreamManager;
    private LocalStreamManager localStreamManager;
    private InjectionEngine injectionEngine;
    private CondensationEngine condensationEngine;

    // ==================== Tool Layer ====================
    private ToolRegistry toolRegistry;
    private SandboxInterceptor sandboxInterceptor;
    private ToolCallTracker toolCallTracker;
    private PlanningTool planningTool;

    // ==================== State Layer ====================
    private TaskManager taskManager;
    private CircuitBreaker circuitBreaker;
    private SuspendResumeEngine suspendResumeEngine;

    // ==================== Runner Layer ====================
    private WorkerRunner workerRunner;
    private MasterRunner masterRunner;

    // ==================== UI Layer ====================
    private UICardRenderer uiCardRenderer;
    private IMMessagePusher imMessagePusher;

    // ==================== Gateway Layer ====================
    private SessionManager sessionManager;
    private WebhookEventExecutor webhookEventExecutor;
    private WebhookController webhookController;

    // Initialization flag
    private boolean initialized = false;

    /**
     * Create a new ApplicationContext with the given configuration.
     *
     * @param config The application configuration
     */
    public ApplicationContext(YesBossConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }
        this.config = config;
    }

    /**
     * Initialize all application components in the correct dependency order.
     *
     * @throws Exception if initialization fails
     */
    public synchronized void initialize() throws Exception {
        if (initialized) {
            logger.warn("ApplicationContext already initialized, skipping");
            return;
        }

        logger.info("========================================");
        logger.info("Initializing ApplicationContext");
        logger.info("========================================");

        try {
            // Step 1: Initialize Infrastructure Layer
            logger.info("Step 1: Initializing Infrastructure Layer...");
            initializeInfrastructureLayer();

            // Step 2: Initialize LLM Layer
            logger.info("Step 2: Initializing LLM Layer...");
            initializeLlmLayer();

            // Step 3: Initialize Context Layer
            logger.info("Step 3: Initializing Context Layer...");
            initializeContextLayer();

            // Step 4: Initialize Tool Layer
            logger.info("Step 4: Initializing Tool Layer...");
            initializeToolLayer();

            // Step 5: Initialize State Layer
            logger.info("Step 5: Initializing State Layer...");
            initializeStateLayer();

            // Step 6: Initialize Runner Layer
            logger.info("Step 6: Initializing Runner Layer...");
            initializeRunnerLayer();

            // Step 7: Initialize UI Layer
            logger.info("Step 7: Initializing UI Layer...");
            initializeUiLayer();

            // Step 8: Initialize Gateway Layer
            logger.info("Step 8: Initializing Gateway Layer...");
            initializeGatewayLayer();

            initialized = true;

            logger.info("========================================");
            logger.info("ApplicationContext initialized successfully!");
            logger.info("========================================");

        } catch (Exception e) {
            logger.error("Failed to initialize ApplicationContext", e);
            throw e;
        }
    }

    /**
     * Initialize Infrastructure Layer (Database, Repositories)
     */
    private void initializeInfrastructureLayer() throws Exception {
        logger.info("Initializing Database...");

        // Initialize database
        databaseInitializer = new DatabaseInitializer(
                config.getDatabase().getSqlite().getPath()
        );
        databaseInitializer.initialize();

        logger.info("Initializing SingleThreadDbWriter...");
        dbWriter = new SingleThreadDbWriter();
        dbWriter.startConsumer();

        logger.info("Initializing Repositories...");
        chatMessageRepository = new ChatMessageRepositoryImpl(dbWriter);
        taskSessionRepository = new TaskSessionRepositoryImpl(dbWriter);
        toolExecutionRepository = new ToolExecutionRepositoryImpl(dbWriter);

        logger.info("Infrastructure Layer initialized");
    }

    /**
     * Initialize LLM Layer (Model Router, SDK Adapters)
     */
    private void initializeLlmLayer() {
        logger.info("Initializing LLM Layer...");

        // Create LLM clients for Master and Worker roles
        String anthropicKey = config.getLlm().getAnthropic().getApiKey();
        String masterModel = config.getLlm().getAnthropic().getModel().getMaster();
        String workerModel = config.getLlm().getAnthropic().getModel().getWorker();
        int maxTokens = config.getLlm().getAnthropic().getMaxTokens();
        double temperature = config.getLlm().getAnthropic().getTemperature();

        logger.info("Creating Master LLM client with model: {}", masterModel);
        masterLlmClient = new ClaudeLlmClient(anthropicKey, masterModel, maxTokens, temperature);

        logger.info("Creating Worker LLM client with model: {}", workerModel);
        workerLlmClient = new ClaudeLlmClient(anthropicKey, workerModel, maxTokens, temperature);

        logger.info("Initializing ModelRouter...");
        modelRouter = new ModelRouter(masterLlmClient, workerLlmClient);

        logger.info("LLM Layer initialized");
    }

    /**
     * Initialize Context Layer (Stream Managers, Engines)
     */
    private void initializeContextLayer() {
        logger.info("Initializing Context Layer...");

        logger.info("Initializing GlobalStreamManager...");
        globalStreamManager = new GlobalStreamManagerImpl(chatMessageRepository);

        logger.info("Initializing LocalStreamManager...");
        localStreamManager = new LocalStreamManagerImpl(chatMessageRepository);

        logger.info("Initializing InjectionEngine...");
        injectionEngine = new InjectionEngineImpl(globalStreamManager);

        logger.info("Initializing CondensationEngine...");
        condensationEngine = new CondensationEngineImpl(
                localStreamManager,
                globalStreamManager,
                modelRouter
        );

        logger.info("Context Layer initialized");
    }

    /**
     * Initialize Tool Layer (Registry, Sandbox, Tracker)
     */
    private void initializeToolLayer() {
        logger.info("Initializing Tool Layer...");

        logger.info("Initializing ToolRegistry...");
        toolRegistry = new ToolRegistryImpl();

        logger.info("Initializing SandboxInterceptor...");
        sandboxInterceptor = new SandboxInterceptorImpl();

        logger.info("Initializing ToolCallTracker...");
        toolCallTracker = new ToolCallTrackerImpl(toolExecutionRepository);

        logger.info("Initializing PlanningTool...");
        planningTool = new PlanningTool(modelRouter.routeByRole("MASTER"));

        // Register the PlanningTool for MASTER role
        toolRegistry.register(planningTool);

        logger.info("Tool Layer initialized");
    }

    /**
     * Initialize State Layer (Task Manager, Safeguards)
     */
    private void initializeStateLayer() {
        logger.info("Initializing State Layer...");

        logger.info("Initializing TaskManager...");
        taskManager = new TaskManagerImpl(taskSessionRepository);

        logger.info("Initializing CircuitBreaker...");
        circuitBreaker = new CircuitBreakerImpl();

        logger.info("Initializing SuspendResumeEngine...");
        suspendResumeEngine = new SuspendResumeEngineImpl(
                taskManager,
                globalStreamManager,
                localStreamManager,
                toolRegistry,
                imMessagePusher,
                toolCallTracker
        );

        logger.info("State Layer initialized");
    }

    /**
     * Initialize Runner Layer (Master Runner, Worker Runner)
     */
    private void initializeRunnerLayer() {
        logger.info("Initializing Runner Layer...");

        // Need UI components first for MasterRunner
        if (uiCardRenderer == null || imMessagePusher == null) {
            throw new IllegalStateException("UI components must be initialized before Runner Layer");
        }

        logger.info("Initializing WorkerRunner...");
        workerRunner = new WorkerRunnerImpl(
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

        logger.info("Initializing MasterRunner...");
        masterRunner = new MasterRunnerImpl(
                taskManager,
                globalStreamManager,
                modelRouter,
                toolRegistry,
                workerRunner,
                planningTool,
                uiCardRenderer,
                imMessagePusher
        );

        logger.info("Runner Layer initialized");
    }

    /**
     * Initialize UI Layer (Card Renderer, Message Pusher)
     */
    private void initializeUiLayer() {
        logger.info("Initializing UI Layer...");

        logger.info("Initializing UICardRenderer...");
        ObjectMapper objectMapper = new ObjectMapper();
        uiCardRenderer = new UICardRendererImpl(objectMapper);

        logger.info("Initializing IMMessagePusher...");
        imMessagePusher = new IMMessagePusherImpl();

        logger.info("UI Layer initialized");
    }

    /**
     * Initialize Gateway Layer (Session Manager, Webhook Executor, Webhook Controller)
     */
    private void initializeGatewayLayer() {
        logger.info("Initializing Gateway Layer...");

        logger.info("Initializing SessionManager...");
        sessionManager = new SessionManagerImpl(
                taskManager,
                taskSessionRepository,
                dbWriter
        );

        // Need MasterRunner for WebhookEventExecutor
        logger.info("Initializing WebhookEventExecutor...");
        webhookEventExecutor = new WebhookEventExecutorImpl(
                sessionManager,
                taskManager,
                masterRunner
        );

        logger.info("Initializing WebhookController...");
        String feishuAppSecret = config.getIm().getFeishu().getAppSecret();
        String slackSigningSecret = config.getIm().getSlack().getSigningSecret();

        webhookController = new WebhookControllerImpl(
                webhookEventExecutor,
                suspendResumeEngine,
                feishuAppSecret,
                slackSigningSecret
        );

        logger.info("Gateway Layer initialized");
    }

    /**
     * Setup shutdown hook for graceful resource cleanup.
     */
    public void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered, cleaning up resources...");
            shutdown();
            logger.info("Shutdown completed");
        }));
        logger.info("Shutdown hook registered");
    }

    /**
     * Gracefully shutdown all components and release resources.
     */
    public synchronized void shutdown() {
        if (!initialized) {
            logger.warn("ApplicationContext not initialized, nothing to shutdown");
            return;
        }

        logger.info("Starting ApplicationContext shutdown...");

        // Stop Spark server
        try {
            logger.info("Stopping Spark server...");
            Spark.stop();
            logger.info("Spark server stopped");
        } catch (Exception e) {
            logger.error("Error stopping Spark server", e);
        }

        // Shutdown Runner Layer
        if (workerRunner instanceof WorkerRunnerImpl) {
            try {
                logger.info("Shutting down WorkerRunner...");
                ((WorkerRunnerImpl) workerRunner).shutdown();
            } catch (Exception e) {
                logger.error("Error shutting down WorkerRunner", e);
            }
        }

        if (masterRunner instanceof MasterRunnerImpl) {
            try {
                logger.info("Shutting down MasterRunner...");
                ((MasterRunnerImpl) masterRunner).shutdown();
            } catch (Exception e) {
                logger.error("Error shutting down MasterRunner", e);
            }
        }

        // Shutdown WebhookEventExecutor
        if (webhookEventExecutor != null) {
            try {
                logger.info("Shutting down WebhookEventExecutor...");
                webhookEventExecutor.shutdown();
            } catch (Exception e) {
                logger.error("Error shutting down WebhookEventExecutor", e);
            }
        }

        // Shutdown Database Writer
        if (dbWriter != null) {
            try {
                logger.info("Shutting down SingleThreadDbWriter...");
                dbWriter.shutdown();
            } catch (Exception e) {
                logger.error("Error shutting down SingleThreadDbWriter", e);
            }
        }

        initialized = false;
        logger.info("ApplicationContext shutdown completed");
    }

    // ==================== Getters for Components ====================

    public WebhookController getWebhookController() {
        if (!initialized) {
            throw new IllegalStateException("ApplicationContext not initialized");
        }
        return webhookController;
    }

    public WebhookEventExecutor getWebhookEventExecutor() {
        if (!initialized) {
            throw new IllegalStateException("ApplicationContext not initialized");
        }
        return webhookEventExecutor;
    }

    public SessionManager getSessionManager() {
        if (!initialized) {
            throw new IllegalStateException("ApplicationContext not initialized");
        }
        return sessionManager;
    }

    public MasterRunner getMasterRunner() {
        if (!initialized) {
            throw new IllegalStateException("ApplicationContext not initialized");
        }
        return masterRunner;
    }

    public WorkerRunner getWorkerRunner() {
        if (!initialized) {
            throw new IllegalStateException("ApplicationContext not initialized");
        }
        return workerRunner;
    }

    public TaskManager getTaskManager() {
        if (!initialized) {
            throw new IllegalStateException("ApplicationContext not initialized");
        }
        return taskManager;
    }

    public ModelRouter getModelRouter() {
        if (!initialized) {
            throw new IllegalStateException("ApplicationContext not initialized");
        }
        return modelRouter;
    }

    public SingleThreadDbWriter getDbWriter() {
        if (!initialized) {
            throw new IllegalStateException("ApplicationContext not initialized");
        }
        return dbWriter;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
