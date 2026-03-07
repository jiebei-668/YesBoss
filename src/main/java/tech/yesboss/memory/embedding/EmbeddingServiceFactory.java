package tech.yesboss.memory.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and managing EmbeddingService instances.
 *
 * <p>This factory provides:
 * <ul>
 *   <li>Singleton instance management</li>
 *   <li>Configuration-based provider selection</li>
 *   <li>Caching of created instances</li>
 *   <li>Thread-safe initialization</li>
 * </ul>
 */
public class EmbeddingServiceFactory {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingServiceFactory.class);

    private static volatile EmbeddingServiceFactory instance;
    private final Map<String, EmbeddingService> serviceCache = new ConcurrentHashMap<>();

    // Configuration keys
    private static final String PROVIDER_KEY = "embedding.provider";
    private static final String ZHIPU_API_KEY = "embedding.zhipu.apiKey";
    private static final String ZHIPU_MODEL = "embedding.zhipu.model";
    private static final String ZHIPU_BASE_URL = "embedding.zhipu.baseUrl";
    private static final String ZHIPU_CONNECT_TIMEOUT = "embedding.zhipu.connectTimeout";
    private static final String ZHIPU_READ_TIMEOUT = "embedding.zhipu.readTimeout";

    // Default values
    private static final String DEFAULT_PROVIDER = "zhipu";
    private static final String DEFAULT_ZHIPU_MODEL = "embedding-3";
    private static final String DEFAULT_ZHIPU_BASE_URL = "https://open.bigmodel.cn";
    private static final int DEFAULT_CONNECT_TIMEOUT = 10;
    private static final int DEFAULT_READ_TIMEOUT = 30;

    private EmbeddingServiceFactory() {
        // Private constructor for singleton
    }

    /**
     * Get the singleton factory instance.
     *
     * @return The factory instance
     */
    public static EmbeddingServiceFactory getInstance() {
        if (instance == null) {
            synchronized (EmbeddingServiceFactory.class) {
                if (instance == null) {
                    instance = new EmbeddingServiceFactory();
                }
            }
        }
        return instance;
    }

    /**
     * Get the default embedding service based on system configuration.
     *
     * @return The configured embedding service
     * @throws EmbeddingException if service cannot be created
     */
    public EmbeddingService getService() {
        return getService(null);
    }

    /**
     * Get an embedding service with the specified provider.
     *
     * @param provider The provider name (e.g., "zhipu", "openai"), or null for default
     * @return The configured embedding service
     * @throws EmbeddingException if service cannot be created
     */
    public EmbeddingService getService(String provider) {
        String actualProvider = provider != null ? provider : getDefaultProvider();

        return serviceCache.computeIfAbsent(actualProvider, p -> {
            try {
                return createService(p);
            } catch (Exception e) {
                logger.error("Failed to create embedding service for provider: {}", p, e);
                throw new EmbeddingException("Failed to create embedding service for provider: " + p,
                        EmbeddingException.ERROR_CONFIGURATION, e);
            }
        });
    }

    /**
     * Create a new embedding service instance.
     */
    private EmbeddingService createService(String provider) {
        logger.info("Creating embedding service for provider: {}", provider);

        switch (provider.toLowerCase()) {
            case "zhipu":
                return createZhipuService();

            // Future providers can be added here
            // case "openai":
            //     return createOpenAIService();

            default:
                logger.warn("Unknown embedding provider: {}, falling back to zhipu", provider);
                return createZhipuService();
        }
    }

    /**
     * Create Zhipu embedding service.
     */
    private EmbeddingService createZhipuService() {
        String apiKey = getConfigValue(ZHIPU_API_KEY, System.getenv("ZHIPU_API_KEY"));
        String model = getConfigValue(ZHIPU_MODEL, DEFAULT_ZHIPU_MODEL);
        String baseUrl = getConfigValue(ZHIPU_BASE_URL, DEFAULT_ZHIPU_BASE_URL);
        int connectTimeout = getConfigInt(ZHIPU_CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT);
        int readTimeout = getConfigInt(ZHIPU_READ_TIMEOUT, DEFAULT_READ_TIMEOUT);

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new EmbeddingException("Zhipu API key not configured",
                    EmbeddingException.ERROR_CONFIGURATION);
        }

        return new ZhipuEmbeddingServiceImpl(apiKey, model, connectTimeout, readTimeout, baseUrl);
    }

    /**
     * Get the default provider from configuration.
     */
    private String getDefaultProvider() {
        String provider = getConfigValue(PROVIDER_KEY, DEFAULT_PROVIDER);
        logger.debug("Using default embedding provider: {}", provider);
        return provider;
    }

    /**
     * Get configuration value from environment or system properties.
     */
    private String getConfigValue(String key, String defaultValue) {
        // Check environment variable first
        String envVar = System.getenv(key.toUpperCase().replace(".", "_"));
        if (envVar != null && !envVar.isEmpty()) {
            return envVar;
        }

        // Check system property
        String sysProp = System.getProperty(key);
        if (sysProp != null && !sysProp.isEmpty()) {
            return sysProp;
        }

        return defaultValue;
    }

    /**
     * Get integer configuration value.
     */
    private int getConfigInt(String key, int defaultValue) {
        String value = getConfigValue(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for {}: {}, using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Clear the service cache.
     */
    public void clearCache() {
        serviceCache.clear();
        logger.info("Embedding service cache cleared");
    }

    /**
     * Reload configuration and recreate services.
     */
    public void reloadConfig() {
        clearCache();
        logger.info("Embedding service configuration reloaded");
    }
}
