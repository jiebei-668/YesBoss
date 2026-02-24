package tech.yesboss.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleton configuration manager for YesBoss application.
 * Provides convenient access to configuration properties with fallback mechanisms.
 */
public class ConfigurationManager {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationManager.class);

    private static volatile ConfigurationManager instance;
    private final ConfigLoader configLoader;
    private volatile YesBossConfig config;
    private String configPath;

    private ConfigurationManager() {
        this.configLoader = new ConfigLoader();
        this.configPath = "application.yml";
        initializeConfig();
    }

    /**
     * Gets the singleton instance of ConfigurationManager.
     * Uses double-checked locking for thread safety.
     *
     * @return the singleton instance
     */
    public static ConfigurationManager getInstance() {
        if (instance == null) {
            synchronized (ConfigurationManager.class) {
                if (instance == null) {
                    instance = new ConfigurationManager();
                }
            }
        }
        return instance;
    }

    /**
     * Initializes the configuration by loading from the default path.
     * Falls back to default configuration if loading fails.
     */
    private void initializeConfig() {
        try {
            logger.info("Loading configuration from: {}", configPath);
            this.config = configLoader.loadConfig(configPath);
            logger.info("Configuration loaded successfully");
        } catch (ConfigLoader.ConfigLoadException e) {
            logger.warn("Failed to load configuration from {}, using default configuration: {}",
                    configPath, e.getMessage());
            this.config = createDefaultConfig();
        }
    }

    /**
     * Gets the current configuration instance.
     *
     * @return the YesBossConfig instance
     */
    public YesBossConfig getConfig() {
        return config;
    }

    /**
     * Reloads the configuration from the configured path.
     * Clears the cache and reinitializes the configuration.
     */
    public void reloadConfig() {
        logger.info("Reloading configuration from: {}", configPath);
        configLoader.clearCache();
        initializeConfig();
        logger.info("Configuration reloaded successfully");
    }

    /**
     * Sets a custom configuration file path and reloads.
     *
     * @param path the path to the configuration file
     */
    public void setConfigPath(String path) {
        logger.info("Setting configuration path to: {}", path);
        this.configPath = path;
        reloadConfig();
    }

    /**
     * Creates a default configuration when no configuration file is available.
     * Provides sensible defaults for all required properties.
     *
     * @return a default YesBossConfig instance
     */
    YesBossConfig createDefaultConfig() {
        logger.info("Creating default configuration");

        YesBossConfig config = new YesBossConfig();

        // LLM Configuration
        config.setLlm(createDefaultLlmConfig());

        // IM Configuration
        config.setIm(createDefaultImConfig());

        // Database Configuration
        config.setDatabase(createDefaultDatabaseConfig());

        // Scheduler Configuration
        config.setScheduler(createDefaultSchedulerConfig());

        // Sandbox Configuration
        config.setSandbox(createDefaultSandboxConfig());

        // Logging Configuration
        config.setLogging(createDefaultLoggingConfig());

        // Application Configuration
        config.setApp(createDefaultAppConfig());

        return config;
    }

    private YesBossConfig.LlmConfig createDefaultLlmConfig() {
        YesBossConfig.LlmConfig llm = new YesBossConfig.LlmConfig();

        // Anthropic (enabled by default)
        YesBossConfig.AnthropicConfig anthropic = new YesBossConfig.AnthropicConfig();
        anthropic.setEnabled(true);
        anthropic.setApiKey(getEnvVarFallback("ANTHROPIC_API_KEY", ""));
        anthropic.setBaseUrl("https://api.anthropic.com");

        YesBossConfig.ModelConfig anthropicModel = new YesBossConfig.ModelConfig();
        anthropicModel.setMaster("claude-sonnet-4-20250514");
        anthropicModel.setWorker("claude-haiku-4-20250514");
        anthropic.setModel(anthropicModel);

        anthropic.setMaxTokens(8192);
        anthropic.setTemperature(0.7);
        anthropic.setTimeoutSeconds(120);
        llm.setAnthropic(anthropic);

        // Gemini (disabled by default)
        YesBossConfig.GeminiConfig gemini = new YesBossConfig.GeminiConfig();
        gemini.setEnabled(false);
        gemini.setApiKey(getEnvVarFallback("GEMINI_API_KEY", ""));
        gemini.setBaseUrl("https://generativelanguage.googleapis.com");

        YesBossConfig.ModelConfig geminiModel = new YesBossConfig.ModelConfig();
        geminiModel.setMaster("gemini-2.0-flash-exp");
        geminiModel.setWorker("gemini-1.5-flash");
        gemini.setModel(geminiModel);

        gemini.setMaxTokens(8192);
        gemini.setTemperature(0.7);
        gemini.setTimeoutSeconds(120);
        llm.setGemini(gemini);

        // OpenAI (disabled by default)
        YesBossConfig.OpenAIConfig openai = new YesBossConfig.OpenAIConfig();
        openai.setEnabled(false);
        openai.setApiKey(getEnvVarFallback("OPENAI_API_KEY", ""));
        openai.setBaseUrl("https://api.openai.com");

        YesBossConfig.ModelConfig openaiModel = new YesBossConfig.ModelConfig();
        openaiModel.setMaster("gpt-4o");
        openaiModel.setWorker("gpt-4o-mini");
        openai.setModel(openaiModel);

        openai.setMaxTokens(16384);
        openai.setTemperature(0.7);
        openai.setTimeoutSeconds(120);
        llm.setOpenai(openai);

        // Zhipu (disabled by default)
        YesBossConfig.ZhipuConfig zhipu = new YesBossConfig.ZhipuConfig();
        zhipu.setEnabled(false);
        zhipu.setApiKey(getEnvVarFallback("ZHIPU_API_KEY", ""));
        zhipu.setBaseUrl("https://open.bigmodel.cn");

        YesBossConfig.ModelConfig zhipuModel = new YesBossConfig.ModelConfig();
        zhipuModel.setMaster("glm-4-plus");
        zhipuModel.setWorker("glm-4-flash");
        zhipu.setModel(zhipuModel);

        zhipu.setMaxTokens(8192);
        zhipu.setTemperature(0.7);
        zhipu.setTimeoutSeconds(120);
        llm.setZhipu(zhipu);

        return llm;
    }

    private YesBossConfig.ImConfig createDefaultImConfig() {
        YesBossConfig.ImConfig im = new YesBossConfig.ImConfig();

        // Feishu (enabled by default)
        YesBossConfig.FeishuConfig feishu = new YesBossConfig.FeishuConfig();
        feishu.setEnabled(true);
        feishu.setAppId(getEnvVarFallback("FEISHU_APP_ID", ""));
        feishu.setAppSecret(getEnvVarFallback("FEISHU_APP_SECRET", ""));
        feishu.setEncryptKey(getEnvVarFallback("FEISHU_ENCRYPT_KEY", ""));
        feishu.setVerificationToken(getEnvVarFallback("FEISHU_VERIFICATION_TOKEN", ""));

        YesBossConfig.WebhookConfig feishuWebhook = new YesBossConfig.WebhookConfig();
        feishuWebhook.setPath("/webhook/feishu");
        feishuWebhook.setPort(8080);
        feishu.setWebhook(feishuWebhook);

        YesBossConfig.PushConfig feishuPush = new YesBossConfig.PushConfig();
        feishuPush.setBaseUrl("https://open.feishu.cn");
        feishuPush.setMessageApi("/open-apis/im/v1/messages");
        feishu.setPush(feishuPush);

        feishu.setTimeoutSeconds(30);
        im.setFeishu(feishu);

        // Slack (disabled by default)
        YesBossConfig.SlackConfig slack = new YesBossConfig.SlackConfig();
        slack.setEnabled(false);
        slack.setSigningSecret(getEnvVarFallback("SLACK_SIGNING_SECRET", ""));
        slack.setBotToken(getEnvVarFallback("SLACK_BOT_TOKEN", ""));

        YesBossConfig.WebhookConfig slackWebhook = new YesBossConfig.WebhookConfig();
        slackWebhook.setPath("/webhook/slack");
        slackWebhook.setPort(8080);
        slack.setWebhook(slackWebhook);

        YesBossConfig.PushConfig slackPush = new YesBossConfig.PushConfig();
        slackPush.setBaseUrl("https://slack.com/api");
        slackPush.setMessageApi("/api/chat.postMessage");
        slack.setPush(slackPush);

        slack.setTimeoutSeconds(30);
        im.setSlack(slack);

        return im;
    }

    private YesBossConfig.DatabaseConfig createDefaultDatabaseConfig() {
        YesBossConfig.DatabaseConfig database = new YesBossConfig.DatabaseConfig();
        database.setType("sqlite");

        YesBossConfig.SQLiteConfig sqlite = new YesBossConfig.SQLiteConfig();
        sqlite.setPath(getEnvVarFallback("SQLITE_PATH", "data/yesboss.db"));
        sqlite.setJournalMode("WAL");
        sqlite.setSynchronous("NORMAL");
        sqlite.setCacheSize(-2000);
        sqlite.setTempStore("MEMORY");

        YesBossConfig.PoolConfig pool = new YesBossConfig.PoolConfig();
        pool.setEnabled(false);
        pool.setMaxConnections(10);
        pool.setConnectionTimeoutMs(30000);
        sqlite.setPool(pool);

        database.setSqlite(sqlite);
        return database;
    }

    private YesBossConfig.SchedulerConfig createDefaultSchedulerConfig() {
        YesBossConfig.SchedulerConfig scheduler = new YesBossConfig.SchedulerConfig();

        YesBossConfig.CircuitBreakerConfig circuitBreaker = new YesBossConfig.CircuitBreakerConfig();
        circuitBreaker.setMaxLoopCount(20);
        circuitBreaker.setCheckIntervalMs(100);
        scheduler.setCircuitBreaker(circuitBreaker);

        YesBossConfig.CondensationConfig condensation = new YesBossConfig.CondensationConfig();
        condensation.setTokenThreshold(120000);
        condensation.setSummarizationIntervalMs(60000);
        condensation.setMaxMessagesToCondense(100);
        scheduler.setCondensation(condensation);

        YesBossConfig.ThreadPoolConfig threadPool = new YesBossConfig.ThreadPoolConfig();
        threadPool.setVirtualThreadsEnabled(true);
        threadPool.setPlatformThreadPoolSize(16);
        threadPool.setQueueCapacity(1000);
        threadPool.setKeepAliveSeconds(60);
        scheduler.setThreadPool(threadPool);

        return scheduler;
    }

    private YesBossConfig.SandboxConfig createDefaultSandboxConfig() {
        YesBossConfig.SandboxConfig sandbox = new YesBossConfig.SandboxConfig();
        sandbox.setEnabled(true);

        List<String> toolNameBlacklist = List.of(
                "format_disk", "delete_all", "wipe_system", "drop_database"
        );
        sandbox.setToolNameBlacklist(toolNameBlacklist);

        List<String> argumentBlacklist = List.of(
                "rm\\s+-rf\\s+/",
                "curl.*\\|\\s*bash",
                "wget.*\\|\\s*sh",
                "eval\\s*\\(",
                "chmod\\s+000",
                "chown\\s+-R",
                "dd\\s+if=",
                ">\\s*/dev/",
                "mkfs\\.",
                "fdisk",
                ":\\(\\)\\{\\s*:\\|:\\s*;\\s*\\}"
        );
        sandbox.setArgumentBlacklist(argumentBlacklist);

        List<String> pathBlacklist = List.of(
                "/etc/passwd", "/etc/shadow", "~/.ssh/", "/root/", "/boot/", "/sys/"
        );
        sandbox.setPathBlacklist(pathBlacklist);

        return sandbox;
    }

    private YesBossConfig.LoggingConfig createDefaultLoggingConfig() {
        YesBossConfig.LoggingConfig logging = new YesBossConfig.LoggingConfig();
        logging.setLevel("INFO");
        logging.setFormat("text");

        YesBossConfig.ConsoleLogConfig console = new YesBossConfig.ConsoleLogConfig();
        console.setEnabled(true);
        console.setColorized(true);
        logging.setConsole(console);

        YesBossConfig.FileLogConfig file = new YesBossConfig.FileLogConfig();
        file.setEnabled(true);
        file.setPath("logs/yesboss.log");
        file.setMaxSize("100MB");
        file.setMaxHistory(30);
        file.setTotalCapacity("1GB");
        logging.setFile(file);

        YesBossConfig.ComponentLogConfig components = new YesBossConfig.ComponentLogConfig();
        components.setLlm("DEBUG");
        components.setPersistence("INFO");
        components.setScheduler("INFO");
        components.setSandbox("WARN");
        logging.setComponents(components);

        return logging;
    }

    private YesBossConfig.AppConfig createDefaultAppConfig() {
        YesBossConfig.AppConfig app = new YesBossConfig.AppConfig();
        app.setName("YesBoss");
        app.setVersion(getEnvVarFallback("APP_VERSION", "1.0.0"));
        app.setEnvironment(getEnvVarFallback("APP_ENV", "development"));

        YesBossConfig.ServerConfig server = new YesBossConfig.ServerConfig();
        server.setHost(getEnvVarFallback("SERVER_HOST", "0.0.0.0"));
        server.setPort(Integer.parseInt(getEnvVarFallback("SERVER_PORT", "8080")));
        server.setContextPath(getEnvVarFallback("SERVER_CONTEXT_PATH", "/"));
        app.setServer(server);

        YesBossConfig.TaskConfig task = new YesBossConfig.TaskConfig();
        task.setDefaultTimeoutMinutes(60);
        task.setMaxConcurrentTasks(10);
        app.setTask(task);

        YesBossConfig.HitlConfig hitl = new YesBossConfig.HitlConfig();
        hitl.setApprovalTimeoutMinutes(30);
        hitl.setMaxRetries(3);
        app.setHitl(hitl);

        return app;
    }

    // ============================================================================
    // Convenience Methods for Common Configuration Properties
    // ============================================================================

    /**
     * Gets Anthropic API key with environment variable fallback.
     */
    public String getAnthropicApiKey() {
        String apiKey = config.getLlm().getAnthropic().getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = getEnvVarFallback("ANTHROPIC_API_KEY", "");
        }
        return apiKey;
    }

    /**
     * Gets Feishu app secret with environment variable fallback.
     */
    public String getFeishuAppSecret() {
        String secret = config.getIm().getFeishu().getAppSecret();
        if (secret == null || secret.isEmpty()) {
            secret = getEnvVarFallback("FEISHU_APP_SECRET", "");
        }
        return secret;
    }

    /**
     * Gets database path with environment variable fallback.
     */
    public String getDatabasePath() {
        String path = config.getDatabase().getSqlite().getPath();
        if (path == null || path.isEmpty()) {
            path = getEnvVarFallback("SQLITE_PATH", "data/yesboss.db");
        }
        return path;
    }

    /**
     * Checks if Feishu integration is enabled.
     */
    public boolean isFeishuEnabled() {
        return config.getIm().getFeishu().isEnabled();
    }

    /**
     * Checks if Slack integration is enabled.
     */
    public boolean isSlackEnabled() {
        return config.getIm().getSlack().isEnabled();
    }

    /**
     * Checks if Anthropic LLM is enabled.
     */
    public boolean isAnthropicEnabled() {
        return config.getLlm().getAnthropic().isEnabled();
    }

    /**
     * Checks if Gemini LLM is enabled.
     */
    public boolean isGeminiEnabled() {
        return config.getLlm().getGemini().isEnabled();
    }

    /**
     * Checks if OpenAI LLM is enabled.
     */
    public boolean isOpenAIEnabled() {
        return config.getLlm().getOpenai().isEnabled();
    }

    /**
     * Checks if Zhipu LLM is enabled.
     */
    public boolean isZhipuEnabled() {
        return config.getLlm().getZhipu().isEnabled();
    }

    /**
     * Gets the master model name for Anthropic.
     */
    public String getAnthropicMasterModel() {
        return config.getLlm().getAnthropic().getModel().getMaster();
    }

    /**
     * Gets the worker model name for Anthropic.
     */
    public String getAnthropicWorkerModel() {
        return config.getLlm().getAnthropic().getModel().getWorker();
    }

    /**
     * Gets the server port.
     */
    public int getServerPort() {
        return config.getApp().getServer().getPort();
    }

    /**
     * Gets the application environment.
     */
    public String getEnvironment() {
        return config.getApp().getEnvironment();
    }

    /**
     * Checks if sandbox is enabled.
     */
    public boolean isSandboxEnabled() {
        return config.getSandbox().isEnabled();
    }

    /**
     * Gets the circuit breaker max loop count.
     */
    public int getCircuitBreakerMaxLoops() {
        return config.getScheduler().getCircuitBreaker().getMaxLoopCount();
    }

    /**
     * Gets the condensation token threshold.
     */
    public int getCondensationTokenThreshold() {
        return config.getScheduler().getCondensation().getTokenThreshold();
    }

    /**
     * Checks if virtual threads are enabled.
     */
    public boolean isVirtualThreadsEnabled() {
        return config.getScheduler().getThreadPool().isVirtualThreadsEnabled();
    }

    /**
     * Gets the logging level.
     */
    public String getLoggingLevel() {
        return config.getLogging().getLevel();
    }

    /**
     * Gets the log file path.
     */
    public String getLogFilePath() {
        return config.getLogging().getFile().getPath();
    }

    /**
     * Helper method to get environment variable with fallback.
     *
     * @param varName the environment variable name
     * @param defaultValue the default value if variable is not set
     * @return the environment variable value or default
     */
    private String getEnvVarFallback(String varName, String defaultValue) {
        String value = System.getenv(varName);
        if (value == null || value.isEmpty()) {
            value = System.getProperty(varName, defaultValue);
        }
        return value;
    }
}
