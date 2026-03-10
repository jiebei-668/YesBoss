package tech.yesboss.config;

import java.util.List;

/**
 * Root configuration class for YesBoss application.
 * Contains all configuration sections matching the application.yml structure.
 */
public class YesBossConfig {

    private LlmConfig llm;
    private ImConfig im;
    private DatabaseConfig database;
    private SchedulerConfig scheduler;
    private SandboxConfig sandbox;
    private FilesystemConfig filesystem;
    private LoggingConfig logging;
    private AppConfig app;

    // Default constructor for YAML deserialization
    public YesBossConfig() {
    }

    // Getters and Setters

    public LlmConfig getLlm() {
        return llm;
    }

    public void setLlm(LlmConfig llm) {
        this.llm = llm;
    }

    public ImConfig getIm() {
        return im;
    }

    public void setIm(ImConfig im) {
        this.im = im;
    }

    public DatabaseConfig getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseConfig database) {
        this.database = database;
    }

    public SchedulerConfig getScheduler() {
        return scheduler;
    }

    public void setScheduler(SchedulerConfig scheduler) {
        this.scheduler = scheduler;
    }

    public SandboxConfig getSandbox() {
        return sandbox;
    }

    public void setSandbox(SandboxConfig sandbox) {
        this.sandbox = sandbox;
    }

    public FilesystemConfig getFilesystem() {
        return filesystem;
    }

    public void setFilesystem(FilesystemConfig filesystem) {
        this.filesystem = filesystem;
    }

    public LoggingConfig getLogging() {
        return logging;
    }

    public void setLogging(LoggingConfig logging) {
        this.logging = logging;
    }

    public AppConfig getApp() {
        return app;
    }

    public void setApp(AppConfig app) {
        this.app = app;
    }

    /**
     * LLM Configuration
     */
    public static class LlmConfig {
        private AnthropicConfig anthropic;
        private GeminiConfig gemini;
        private OpenAIConfig openai;
        private ZhipuConfig zhipu;

        public LlmConfig() {
        }

        public AnthropicConfig getAnthropic() {
            return anthropic;
        }

        public void setAnthropic(AnthropicConfig anthropic) {
            this.anthropic = anthropic;
        }

        public GeminiConfig getGemini() {
            return gemini;
        }

        public void setGemini(GeminiConfig gemini) {
            this.gemini = gemini;
        }

        public OpenAIConfig getOpenai() {
            return openai;
        }

        public void setOpenai(OpenAIConfig openai) {
            this.openai = openai;
        }

        public ZhipuConfig getZhipu() {
            return zhipu;
        }

        public void setZhipu(ZhipuConfig zhipu) {
            this.zhipu = zhipu;
        }
    }

    /**
     * Anthropic Claude Configuration
     */
    public static class AnthropicConfig {
        private boolean enabled;
        private String apiKey;
        private String baseUrl;
        private ModelConfig model;
        private int maxTokens;
        private double temperature;
        private int timeoutSeconds;

        public AnthropicConfig() {
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public ModelConfig getModel() {
            return model;
        }

        public void setModel(ModelConfig model) {
            this.model = model;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    /**
     * Google Gemini Configuration
     */
    public static class GeminiConfig {
        private boolean enabled;
        private String apiKey;
        private String baseUrl;
        private ModelConfig model;
        private int maxTokens;
        private double temperature;
        private int timeoutSeconds;

        public GeminiConfig() {
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public ModelConfig getModel() {
            return model;
        }

        public void setModel(ModelConfig model) {
            this.model = model;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    /**
     * OpenAI GPT Configuration
     */
    public static class OpenAIConfig {
        private boolean enabled;
        private String apiKey;
        private String baseUrl;
        private ModelConfig model;
        private int maxTokens;
        private double temperature;
        private int timeoutSeconds;

        public OpenAIConfig() {
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public ModelConfig getModel() {
            return model;
        }

        public void setModel(ModelConfig model) {
            this.model = model;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    /**
     * Zhipu GLM Configuration
     */
    public static class ZhipuConfig {
        private boolean enabled;
        private String apiKey;
        private String baseUrl;
        private ModelConfig model;
        private int maxTokens;
        private double temperature;
        private int timeoutSeconds;

        public ZhipuConfig() {
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public ModelConfig getModel() {
            return model;
        }

        public void setModel(ModelConfig model) {
            this.model = model;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    /**
     * Model Configuration (Master/Worker)
     */
    public static class ModelConfig {
        private String master;
        private String worker;

        public ModelConfig() {
        }

        public String getMaster() {
            return master;
        }

        public void setMaster(String master) {
            this.master = master;
        }

        public String getWorker() {
            return worker;
        }

        public void setWorker(String worker) {
            this.worker = worker;
        }
    }

    /**
     * IM Platform Configuration
     */
    public static class ImConfig {
        private FeishuConfig feishu;
        private SlackConfig slack;

        public ImConfig() {
        }

        public FeishuConfig getFeishu() {
            return feishu;
        }

        public void setFeishu(FeishuConfig feishu) {
            this.feishu = feishu;
        }

        public SlackConfig getSlack() {
            return slack;
        }

        public void setSlack(SlackConfig slack) {
            this.slack = slack;
        }
    }

    /**
     * Feishu Configuration
     */
    public static class FeishuConfig {
        private boolean enabled;
        private String appId;
        private String appSecret;
        private String encryptKey;
        private String verificationToken;
        private WebhookConfig webhook;
        private PushConfig push;
        private int timeoutSeconds;

        public FeishuConfig() {
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getEncryptKey() {
            return encryptKey;
        }

        public void setEncryptKey(String encryptKey) {
            this.encryptKey = encryptKey;
        }

        public String getVerificationToken() {
            return verificationToken;
        }

        public void setVerificationToken(String verificationToken) {
            this.verificationToken = verificationToken;
        }

        public WebhookConfig getWebhook() {
            return webhook;
        }

        public void setWebhook(WebhookConfig webhook) {
            this.webhook = webhook;
        }

        public PushConfig getPush() {
            return push;
        }

        public void setPush(PushConfig push) {
            this.push = push;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    /**
     * Slack Configuration
     */
    public static class SlackConfig {
        private boolean enabled;
        private String signingSecret;
        private String botToken;
        private WebhookConfig webhook;
        private PushConfig push;
        private int timeoutSeconds;

        public SlackConfig() {
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSigningSecret() {
            return signingSecret;
        }

        public void setSigningSecret(String signingSecret) {
            this.signingSecret = signingSecret;
        }

        public String getBotToken() {
            return botToken;
        }

        public void setBotToken(String botToken) {
            this.botToken = botToken;
        }

        public WebhookConfig getWebhook() {
            return webhook;
        }

        public void setWebhook(WebhookConfig webhook) {
            this.webhook = webhook;
        }

        public PushConfig getPush() {
            return push;
        }

        public void setPush(PushConfig push) {
            this.push = push;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    /**
     * Webhook Configuration
     */
    public static class WebhookConfig {
        private String path;
        private int port;

        public WebhookConfig() {
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    /**
     * Push Configuration
     */
    public static class PushConfig {
        private String baseUrl;
        private String messageApi;

        public PushConfig() {
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getMessageApi() {
            return messageApi;
        }

        public void setMessageApi(String messageApi) {
            this.messageApi = messageApi;
        }
    }

    /**
     * Database Configuration
     */
    public static class DatabaseConfig {
        private String type;
        private SQLiteConfig sqlite;

        public DatabaseConfig() {
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public SQLiteConfig getSqlite() {
            return sqlite;
        }

        public void setSqlite(SQLiteConfig sqlite) {
            this.sqlite = sqlite;
        }
    }

    /**
     * SQLite Configuration
     */
    public static class SQLiteConfig {
        private String path;
        private PoolConfig pool;
        private String journalMode;
        private String synchronous;
        private int cacheSize;
        private String tempStore;

        public SQLiteConfig() {
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public PoolConfig getPool() {
            return pool;
        }

        public void setPool(PoolConfig pool) {
            this.pool = pool;
        }

        public String getJournalMode() {
            return journalMode;
        }

        public void setJournalMode(String journalMode) {
            this.journalMode = journalMode;
        }

        public String getSynchronous() {
            return synchronous;
        }

        public void setSynchronous(String synchronous) {
            this.synchronous = synchronous;
        }

        public int getCacheSize() {
            return cacheSize;
        }

        public void setCacheSize(int cacheSize) {
            this.cacheSize = cacheSize;
        }

        public String getTempStore() {
            return tempStore;
        }

        public void setTempStore(String tempStore) {
            this.tempStore = tempStore;
        }
    }

    /**
     * Connection Pool Configuration
     */
    public static class PoolConfig {
        private boolean enabled;
        private int maxConnections;
        private int connectionTimeoutMs;

        public PoolConfig() {
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxConnections() {
            return maxConnections;
        }

        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        public int getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(int connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }
    }

    /**
     * Scheduler Configuration
     */
    public static class SchedulerConfig {
        private CircuitBreakerConfig circuitBreaker;
        private CondensationConfig condensation;
        private ThreadPoolConfig threadPool;

        public SchedulerConfig() {
        }

        public CircuitBreakerConfig getCircuitBreaker() {
            return circuitBreaker;
        }

        public void setCircuitBreaker(CircuitBreakerConfig circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
        }

        public CondensationConfig getCondensation() {
            return condensation;
        }

        public void setCondensation(CondensationConfig condensation) {
            this.condensation = condensation;
        }

        public ThreadPoolConfig getThreadPool() {
            return threadPool;
        }

        public void setThreadPool(ThreadPoolConfig threadPool) {
            this.threadPool = threadPool;
        }
    }

    /**
     * Circuit Breaker Configuration
     */
    public static class CircuitBreakerConfig {
        private int maxLoopCount;
        private int checkIntervalMs;

        public CircuitBreakerConfig() {
        }

        public int getMaxLoopCount() {
            return maxLoopCount;
        }

        public void setMaxLoopCount(int maxLoopCount) {
            this.maxLoopCount = maxLoopCount;
        }

        public int getCheckIntervalMs() {
            return checkIntervalMs;
        }

        public void setCheckIntervalMs(int checkIntervalMs) {
            this.checkIntervalMs = checkIntervalMs;
        }
    }

    /**
     * Condensation Engine Configuration
     */
    public static class CondensationConfig {
        private int tokenThreshold;
        private int summarizationIntervalMs;
        private int maxMessagesToCondense;

        public CondensationConfig() {
        }

        public int getTokenThreshold() {
            return tokenThreshold;
        }

        public void setTokenThreshold(int tokenThreshold) {
            this.tokenThreshold = tokenThreshold;
        }

        public int getSummarizationIntervalMs() {
            return summarizationIntervalMs;
        }

        public void setSummarizationIntervalMs(int summarizationIntervalMs) {
            this.summarizationIntervalMs = summarizationIntervalMs;
        }

        public int getMaxMessagesToCondense() {
            return maxMessagesToCondense;
        }

        public void setMaxMessagesToCondense(int maxMessagesToCondense) {
            this.maxMessagesToCondense = maxMessagesToCondense;
        }
    }

    /**
     * Thread Pool Configuration
     */
    public static class ThreadPoolConfig {
        private boolean virtualThreadsEnabled;
        private int platformThreadPoolSize;
        private int queueCapacity;
        private int keepAliveSeconds;

        public ThreadPoolConfig() {
        }

        public boolean isVirtualThreadsEnabled() {
            return virtualThreadsEnabled;
        }

        public void setVirtualThreadsEnabled(boolean virtualThreadsEnabled) {
            this.virtualThreadsEnabled = virtualThreadsEnabled;
        }

        public int getPlatformThreadPoolSize() {
            return platformThreadPoolSize;
        }

        public void setPlatformThreadPoolSize(int platformThreadPoolSize) {
            this.platformThreadPoolSize = platformThreadPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public int getKeepAliveSeconds() {
            return keepAliveSeconds;
        }

        public void setKeepAliveSeconds(int keepAliveSeconds) {
            this.keepAliveSeconds = keepAliveSeconds;
        }
    }

    /**
     * Sandbox Security Configuration
     */
    public static class SandboxConfig {
        private boolean enabled;
        private List<String> toolNameBlacklist;
        private List<String> argumentBlacklist;
        private List<String> pathBlacklist;

        public SandboxConfig() {
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getToolNameBlacklist() {
            return toolNameBlacklist;
        }

        public void setToolNameBlacklist(List<String> toolNameBlacklist) {
            this.toolNameBlacklist = toolNameBlacklist;
        }

        public List<String> getArgumentBlacklist() {
            return argumentBlacklist;
        }

        public void setArgumentBlacklist(List<String> argumentBlacklist) {
            this.argumentBlacklist = argumentBlacklist;
        }

        public List<String> getPathBlacklist() {
            return pathBlacklist;
        }

        public void setPathBlacklist(List<String> pathBlacklist) {
            this.pathBlacklist = pathBlacklist;
        }
    }

    /**
     * Filesystem Configuration
     */
    public static class FilesystemConfig {
        private SizeLimitsConfig sizeLimits;
        private AllowedExtensionsConfig allowedExtensions;
        private List<String> pathWhitelist;
        private WriteProtectionConfig writeProtection;

        public FilesystemConfig() {
        }

        public SizeLimitsConfig getSizeLimits() {
            return sizeLimits;
        }

        public void setSizeLimits(SizeLimitsConfig sizeLimits) {
            this.sizeLimits = sizeLimits;
        }

        public AllowedExtensionsConfig getAllowedExtensions() {
            return allowedExtensions;
        }

        public void setAllowedExtensions(AllowedExtensionsConfig allowedExtensions) {
            this.allowedExtensions = allowedExtensions;
        }

        public List<String> getPathWhitelist() {
            return pathWhitelist;
        }

        public void setPathWhitelist(List<String> pathWhitelist) {
            this.pathWhitelist = pathWhitelist;
        }

        public WriteProtectionConfig getWriteProtection() {
            return writeProtection;
        }

        public void setWriteProtection(WriteProtectionConfig writeProtection) {
            this.writeProtection = writeProtection;
        }
    }

    /**
     * Size Limits Configuration
     */
    public static class SizeLimitsConfig {
        private long maxReadSize;
        private long maxWriteSize;
        private long minDiskSpace;

        public SizeLimitsConfig() {
        }

        public long getMaxReadSize() {
            return maxReadSize;
        }

        public void setMaxReadSize(long maxReadSize) {
            this.maxReadSize = maxReadSize;
        }

        public long getMaxWriteSize() {
            return maxWriteSize;
        }

        public void setMaxWriteSize(long maxWriteSize) {
            this.maxWriteSize = maxWriteSize;
        }

        public long getMinDiskSpace() {
            return minDiskSpace;
        }

        public void setMinDiskSpace(long minDiskSpace) {
            this.minDiskSpace = minDiskSpace;
        }
    }

    /**
     * Allowed Extensions Configuration
     */
    public static class AllowedExtensionsConfig {
        private List<String> code;
        private List<String> config;
        private List<String> docs;

        public AllowedExtensionsConfig() {
        }

        public List<String> getCode() {
            return code;
        }

        public void setCode(List<String> code) {
            this.code = code;
        }

        public List<String> getConfig() {
            return config;
        }

        public void setConfig(List<String> config) {
            this.config = config;
        }

        public List<String> getDocs() {
            return docs;
        }

        public void setDocs(List<String> docs) {
            this.docs = docs;
        }
    }

    /**
     * Write Protection Configuration
     */
    public static class WriteProtectionConfig {
        private boolean enabled;
        private boolean overwriteConfirmation;
        private long minDiskSpace;
        private List<String> protectedFiles;

        public WriteProtectionConfig() {
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isOverwriteConfirmation() {
            return overwriteConfirmation;
        }

        public void setOverwriteConfirmation(boolean overwriteConfirmation) {
            this.overwriteConfirmation = overwriteConfirmation;
        }

        public long getMinDiskSpace() {
            return minDiskSpace;
        }

        public void setMinDiskSpace(long minDiskSpace) {
            this.minDiskSpace = minDiskSpace;
        }

        public List<String> getProtectedFiles() {
            return protectedFiles;
        }

        public void setProtectedFiles(List<String> protectedFiles) {
            this.protectedFiles = protectedFiles;
        }
    }

    /**
     * Logging Configuration
     */
    public static class LoggingConfig {
        private String level;
        private String format;
        private ConsoleLogConfig console;
        private FileLogConfig file;
        private ComponentLogConfig components;

        public LoggingConfig() {
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public ConsoleLogConfig getConsole() {
            return console;
        }

        public void setConsole(ConsoleLogConfig console) {
            this.console = console;
        }

        public FileLogConfig getFile() {
            return file;
        }

        public void setFile(FileLogConfig file) {
            this.file = file;
        }

        public ComponentLogConfig getComponents() {
            return components;
        }

        public void setComponents(ComponentLogConfig components) {
            this.components = components;
        }
    }

    /**
     * Console Logging Configuration
     */
    public static class ConsoleLogConfig {
        private boolean enabled;
        private boolean colorized;

        public ConsoleLogConfig() {
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isColorized() {
            return colorized;
        }

        public void setColorized(boolean colorized) {
            this.colorized = colorized;
        }
    }

    /**
     * File Logging Configuration
     */
    public static class FileLogConfig {
        private boolean enabled;
        private String path;
        private String maxSize;
        private int maxHistory;
        private String totalCapacity;

        public FileLogConfig() {
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(String maxSize) {
            this.maxSize = maxSize;
        }

        public int getMaxHistory() {
            return maxHistory;
        }

        public void setMaxHistory(int maxHistory) {
            this.maxHistory = maxHistory;
        }

        public String getTotalCapacity() {
            return totalCapacity;
        }

        public void setTotalCapacity(String totalCapacity) {
            this.totalCapacity = totalCapacity;
        }
    }

    /**
     * Component Logging Configuration
     */
    public static class ComponentLogConfig {
        private String llm;
        private String persistence;
        private String scheduler;
        private String sandbox;

        public ComponentLogConfig() {
        }

        public String getLlm() {
            return llm;
        }

        public void setLlm(String llm) {
            this.llm = llm;
        }

        public String getPersistence() {
            return persistence;
        }

        public void setPersistence(String persistence) {
            this.persistence = persistence;
        }

        public String getScheduler() {
            return scheduler;
        }

        public void setScheduler(String scheduler) {
            this.scheduler = scheduler;
        }

        public String getSandbox() {
            return sandbox;
        }

        public void setSandbox(String sandbox) {
            this.sandbox = sandbox;
        }
    }

    /**
     * Application Configuration
     */
    public static class AppConfig {
        private String name;
        private String version;
        private String environment;
        private String projectRoot;
        private ServerConfig server;
        private TaskConfig task;
        private HitlConfig hitl;

        public AppConfig() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }

        public String getProjectRoot() {
            return projectRoot;
        }

        public void setProjectRoot(String projectRoot) {
            this.projectRoot = projectRoot;
        }

        public ServerConfig getServer() {
            return server;
        }

        public void setServer(ServerConfig server) {
            this.server = server;
        }

        public TaskConfig getTask() {
            return task;
        }

        public void setTask(TaskConfig task) {
            this.task = task;
        }

        public HitlConfig getHitl() {
            return hitl;
        }

        public void setHitl(HitlConfig hitl) {
            this.hitl = hitl;
        }
    }

    /**
     * Server Configuration
     */
    public static class ServerConfig {
        private String host;
        private int port;
        private String contextPath;

        public ServerConfig() {
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getContextPath() {
            return contextPath;
        }

        public void setContextPath(String contextPath) {
            this.contextPath = contextPath;
        }
    }

    /**
     * Task Configuration
     */
    public static class TaskConfig {
        private int defaultTimeoutMinutes;
        private int maxConcurrentTasks;

        public TaskConfig() {
        }

        public int getDefaultTimeoutMinutes() {
            return defaultTimeoutMinutes;
        }

        public void setDefaultTimeoutMinutes(int defaultTimeoutMinutes) {
            this.defaultTimeoutMinutes = defaultTimeoutMinutes;
        }

        public int getMaxConcurrentTasks() {
            return maxConcurrentTasks;
        }

        public void setMaxConcurrentTasks(int maxConcurrentTasks) {
            this.maxConcurrentTasks = maxConcurrentTasks;
        }
    }

    /**
     * Human-in-the-Loop Configuration
     */
    public static class HitlConfig {
        private int approvalTimeoutMinutes;
        private int maxRetries;

        public HitlConfig() {
        }

        public int getApprovalTimeoutMinutes() {
            return approvalTimeoutMinutes;
        }

        public void setApprovalTimeoutMinutes(int approvalTimeoutMinutes) {
            this.approvalTimeoutMinutes = approvalTimeoutMinutes;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }
    }
}
