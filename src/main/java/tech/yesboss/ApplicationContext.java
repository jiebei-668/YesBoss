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
import tech.yesboss.gateway.im.FeishuApiClient;
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
import tech.yesboss.persistence.db.SQLiteConnectionManager;
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
import tech.yesboss.health.HealthCheckService;
import tech.yesboss.health.MetricsCollector;

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
    private SQLiteConnectionManager connectionManager;
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
    private FeishuApiClient feishuApiClient;
    private IMMessagePusher imMessagePusher;

    // ==================== Gateway Layer ====================
    private SessionManager sessionManager;
    private WebhookEventExecutor webhookEventExecutor;
    private WebhookController webhookController;

    // ==================== Health & Monitoring ====================
    private HealthCheckService healthCheckService;
    private MetricsCollector metricsCollector;

    // Initialization flag
    private boolean initialized = false;

    // Ready flag - set to true when application is ready to accept webhook traffic
    private boolean ready = false;

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

            // Step 5: Initialize UI Layer (before State Layer since SuspendResumeEngine needs imMessagePusher)
            logger.info("Step 5: Initializing UI Layer...");
            initializeUiLayer();

            // Step 6: Initialize State Layer
            logger.info("Step 6: Initializing State Layer...");
            initializeStateLayer();

            // Step 7: Initialize Runner Layer
            logger.info("Step 7: Initializing Runner Layer...");
            initializeRunnerLayer();

            // Step 8: Initialize Gateway Layer
            logger.info("Step 8: Initializing Gateway Layer...");
            initializeGatewayLayer();

            // Step 9: Initialize Health & Monitoring
            logger.info("Step 9: Initializing Health & Monitoring...");
            initializeHealthAndMonitoring();

            initialized = true;
            ready = true; // Application is now ready to accept webhook traffic

            logger.info("========================================");
            logger.info("ApplicationContext initialized successfully!");
            logger.info("Application is READY to accept webhook traffic");
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

        // Initialize database connection manager
        String dbPath = config.getDatabase().getSqlite().getPath();
        logger.info("Database path: {}", dbPath);

        // Create parent directory if it doesn't exist
        java.nio.file.Path dbFilePath = java.nio.file.Paths.get(dbPath);
        java.nio.file.Path parentDir = dbFilePath.getParent();
        if (parentDir != null && !java.nio.file.Files.exists(parentDir)) {
            logger.info("Creating database directory: {}", parentDir);
            java.nio.file.Files.createDirectories(parentDir);
        }

        // Create connection manager
        connectionManager = SQLiteConnectionManager.forFile(dbFilePath);
        java.sql.Connection connection = connectionManager.getConnection();

        // Initialize database schema
        databaseInitializer = new DatabaseInitializer(connection);
        databaseInitializer.initialize();

        logger.info("Initializing SingleThreadDbWriter...");
        dbWriter = new SingleThreadDbWriter(connection);
        dbWriter.startConsumer();

        logger.info("Initializing Repositories...");
        chatMessageRepository = new ChatMessageRepositoryImpl(connection, dbWriter);
        taskSessionRepository = new TaskSessionRepositoryImpl(connection, dbWriter);
        toolExecutionRepository = new ToolExecutionRepositoryImpl(connection, dbWriter);

        logger.info("Infrastructure Layer initialized");
    }

    /**
     * Initialize LLM Layer (Model Router, SDK Adapters)
     */
    private void initializeLlmLayer() {
        logger.info("Initializing LLM Layer...");

        // Determine which LLM provider to use based on configuration and API key availability
        // Priority: 1) Anthropic (if enabled and has API key), 2) Zhipu (if enabled and has API key)
        boolean anthropicEnabled = config.getLlm().getAnthropic().isEnabled();
        String anthropicKey = config.getLlm().getAnthropic().getApiKey();
        boolean zhipuEnabled = config.getLlm().getZhipu().isEnabled();
        String zhipuKey = config.getLlm().getZhipu().getApiKey();

        String provider;
        String apiKey;
        String masterModel;
        String workerModel;
        int maxTokens;
        double temperature;

        if (anthropicEnabled && anthropicKey != null && !anthropicKey.isEmpty()) {
            // Use Anthropic Claude
            provider = "Anthropic";
            apiKey = anthropicKey;
            masterModel = config.getLlm().getAnthropic().getModel().getMaster();
            workerModel = config.getLlm().getAnthropic().getModel().getWorker();
            maxTokens = config.getLlm().getAnthropic().getMaxTokens();
            temperature = config.getLlm().getAnthropic().getTemperature();
            logger.info("Using LLM Provider: Anthropic Claude");
        } else if (zhipuEnabled && zhipuKey != null && !zhipuKey.isEmpty()) {
            // Use Zhipu GLM
            provider = "Zhipu";
            apiKey = zhipuKey;
            masterModel = config.getLlm().getZhipu().getModel().getMaster();
            workerModel = config.getLlm().getZhipu().getModel().getWorker();
            maxTokens = config.getLlm().getZhipu().getMaxTokens();
            temperature = config.getLlm().getZhipu().getTemperature();
            logger.info("Using LLM Provider: Zhipu GLM");
        } else {
            throw new IllegalArgumentException(
                "No LLM provider configured. Please set either ANTHROPIC_API_KEY or ZHIPU_API_KEY in .env file or environment variables."
            );
        }

        logger.info("Creating Master LLM client with provider: {}, model: {}", provider, masterModel);
        if ("Zhipu".equals(provider)) {
            masterLlmClient = new tech.yesboss.llm.impl.ZhipuLlmClient(apiKey, masterModel, maxTokens, temperature);
        } else {
            masterLlmClient = new ClaudeLlmClient(apiKey, masterModel, maxTokens, temperature);
        }

        logger.info("Creating Worker LLM client with provider: {}, model: {}", provider, workerModel);
        if ("Zhipu".equals(provider)) {
            workerLlmClient = new tech.yesboss.llm.impl.ZhipuLlmClient(apiKey, workerModel, maxTokens, temperature);
        } else {
            workerLlmClient = new ClaudeLlmClient(apiKey, workerModel, maxTokens, temperature);
        }

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
        toolCallTracker = new ToolCallTrackerImpl(dbWriter);

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

        logger.info("Initializing FeishuApiClient...");
        String feishuAppId = config.getIm().getFeishu().getAppId();
        String feishuAppSecret = config.getIm().getFeishu().getAppSecret();
        int feishuTimeout = config.getIm().getFeishu().getTimeoutSeconds();

        if (feishuAppId != null && !feishuAppId.isEmpty()
                && feishuAppSecret != null && !feishuAppSecret.isEmpty()) {
            feishuApiClient = new FeishuApiClient(feishuAppId, feishuAppSecret, feishuTimeout);
            logger.info("FeishuApiClient initialized with app_id: {}", feishuAppId);
        } else {
            logger.warn("Feishu credentials not configured, FeishuApiClient will be null");
        }

        logger.info("Initializing IMMessagePusher...");
        imMessagePusher = new IMMessagePusherImpl(feishuApiClient);

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
                masterRunner,
                globalStreamManager
        );

        logger.info("Initializing WebhookController...");
        // Use encryptKey for webhook signature verification (not appSecret)
        String feishuAppSecret = config.getIm().getFeishu().getEncryptKey();
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
     * Initialize Health & Monitoring Services.
     */
    private void initializeHealthAndMonitoring() throws Exception {
        logger.info("Initializing Health & Monitoring...");

        logger.info("Initializing HealthCheckService...");
        java.sql.Connection dbConnection = connectionManager.getConnection();
        healthCheckService = new HealthCheckService(
                dbConnection,
                dbWriter,
                masterLlmClient,
                workerLlmClient,
                taskManager,
                webhookEventExecutor.getExecutor()
        );

        logger.info("Initializing MetricsCollector...");
        metricsCollector = new MetricsCollector(
                sessionManager,
                dbWriter,
                circuitBreaker,
                webhookEventExecutor.getExecutor()
        );

        logger.info("Health & Monitoring initialized");
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

        logger.info("========================================");
        logger.info("Starting ApplicationContext shutdown...");
        logger.info("========================================");

        // Set ready flag to false to stop accepting webhook traffic
        ready = false;
        logger.info("Application is no longer ready - webhook traffic will be rejected");

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
                dbWriter.stopConsumer();
            } catch (InterruptedException e) {
                logger.error("Error shutting down SingleThreadDbWriter", e);
                Thread.currentThread().interrupt();
            }
        }

        // Close database connection
        if (connectionManager != null) {
            try {
                logger.info("Closing database connection...");
                connectionManager.close();
            } catch (Exception e) {
                logger.error("Error closing database connection", e);
            }
        }

        initialized = false;
        logger.info("========================================");
        logger.info("ApplicationContext shutdown completed!");
        logger.info("========================================");
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

    public HealthCheckService getHealthCheckService() {
        if (!initialized) {
            throw new IllegalStateException("ApplicationContext not initialized");
        }
        return healthCheckService;
    }

    public MetricsCollector getMetricsCollector() {
        if (!initialized) {
            throw new IllegalStateException("ApplicationContext not initialized");
        }
        return metricsCollector;
    }

    public boolean isReady() {
        return initialized && ready;
    }
}
