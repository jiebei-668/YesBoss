package tech.yesboss.memory.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Configuration loader for ContentProcessor from application-memory.yml
 *
 * <p>This class loads processor-specific configuration including:
 * <ul>
 *   <li>Retry settings (maxRetries, delayMs, backoffMultiplier)</li>
 *   <li>Caching settings (enabled, maxSize, expireAfterWriteSeconds)</li>
 *   <li>Monitoring settings (enabled, trackPerformance, trackErrors)</li>
 *   <li>LLM settings (model, temperature, maxTokens)</li>
 * </ul>
 */
public class MemoryProcessorConfig {

    private static final Logger logger = LoggerFactory.getLogger(MemoryProcessorConfig.class);

    private static final String CONFIG_FILE = "/application-memory.yml";
    private static volatile MemoryProcessorConfig instance;

    // Retry configuration
    private final int maxRetries;
    private final long retryDelayMs;
    private final double backoffMultiplier;

    // Cache configuration
    private final boolean cacheEnabled;
    private final int cacheMaxSize;
    private final long cacheExpireAfterWriteSeconds;

    // Monitoring configuration
    private final boolean monitoringEnabled;
    private final boolean trackPerformance;
    private final boolean trackErrors;

    // LLM configuration
    private final String llmModel;
    private final double llmTemperature;
    private final int llmMaxTokens;

    private MemoryProcessorConfig(Map<String, Object> config) {
        // Extract memory configuration
        @SuppressWarnings("unchecked")
        Map<String, Object> memoryConfig = (Map<String, Object>) config.get("memory");

        // Extract retry configuration
        @SuppressWarnings("unchecked")
        Map<String, Object> retryConfig = memoryConfig != null ?
                (Map<String, Object>) memoryConfig.get("retry") : null;

        this.maxRetries = getIntValue(retryConfig, "maxRetries", 3);
        this.retryDelayMs = getIntValue(retryConfig, "delayMs", 1000);
        this.backoffMultiplier = getDoubleValue(retryConfig, "backoffMultiplier", 2.0);

        // Extract cache configuration
        @SuppressWarnings("unchecked")
        Map<String, Object> cacheConfig = memoryConfig != null ?
                (Map<String, Object>) memoryConfig.get("cache") : null;

        this.cacheEnabled = getBooleanValue(cacheConfig, "enabled", true);
        this.cacheMaxSize = getIntValue(cacheConfig, "maxSize", 10000);
        this.cacheExpireAfterWriteSeconds = getIntValue(cacheConfig, "expireAfterWriteSeconds", 3600);

        // Extract monitoring configuration
        @SuppressWarnings("unchecked")
        Map<String, Object> monitoringConfig = memoryConfig != null ?
                (Map<String, Object>) memoryConfig.get("monitoring") : null;

        this.monitoringEnabled = getBooleanValue(monitoringConfig, "enabled", true);
        this.trackPerformance = getBooleanValue(monitoringConfig, "trackPerformance", true);
        this.trackErrors = getBooleanValue(monitoringConfig, "trackErrors", true);

        // Extract LLM configuration (processor-specific)
        @SuppressWarnings("unchecked")
        Map<String, Object> processorConfig = memoryConfig != null ?
                (Map<String, Object>) memoryConfig.get("processor") : null;

        this.llmModel = getStringValue(processorConfig, "model", "glm-4");
        this.llmTemperature = getDoubleValue(processorConfig, "temperature", 0.7);
        this.llmMaxTokens = getIntValue(processorConfig, "maxTokens", 2000);

        logger.info("MemoryProcessorConfig loaded: " + this);
    }

    /**
     * Get singleton instance
     */
    public static MemoryProcessorConfig getInstance() {
        if (instance == null) {
            synchronized (MemoryProcessorConfig.class) {
                if (instance == null) {
                    instance = loadConfig();
                }
            }
        }
        return instance;
    }

    /**
     * Load configuration from YAML file
     */
    private static MemoryProcessorConfig loadConfig() {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            InputStream inputStream = MemoryProcessorConfig.class.getResourceAsStream(CONFIG_FILE);

            if (inputStream == null) {
                logger.warn("Configuration file not found: {}, using defaults", CONFIG_FILE);
                return createDefaultConfig();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> config = mapper.readValue(inputStream, Map.class);
            logger.info("Loaded configuration from {}", CONFIG_FILE);
            return new MemoryProcessorConfig(config);
        } catch (IOException e) {
            logger.error("Failed to load configuration from {}, using defaults", CONFIG_FILE, e);
            return createDefaultConfig();
        }
    }

    /**
     * Create default configuration
     */
    private static MemoryProcessorConfig createDefaultConfig() {
        return new MemoryProcessorConfig(Map.of());
    }

    // Getters
    public int getMaxRetries() {
        return maxRetries;
    }

    public long getRetryDelayMs() {
        return retryDelayMs;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public int getCacheMaxSize() {
        return cacheMaxSize;
    }

    public long getCacheExpireAfterWriteSeconds() {
        return cacheExpireAfterWriteSeconds;
    }

    public boolean isMonitoringEnabled() {
        return monitoringEnabled;
    }

    public boolean isTrackPerformance() {
        return trackPerformance;
    }

    public boolean isTrackErrors() {
        return trackErrors;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public double getLlmTemperature() {
        return llmTemperature;
    }

    public int getLlmMaxTokens() {
        return llmMaxTokens;
    }

    // Helper methods for extracting values from config map
    private boolean getBooleanValue(Map<String, Object> config, String key, boolean defaultValue) {
        if (config == null) {
            return defaultValue;
        }
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    private int getIntValue(Map<String, Object> config, String key, int defaultValue) {
        if (config == null) {
            return defaultValue;
        }
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                logger.warn("Invalid integer value for {}: {}", key, value);
            }
        }
        return defaultValue;
    }

    private double getDoubleValue(Map<String, Object> config, String key, double defaultValue) {
        if (config == null) {
            return defaultValue;
        }
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                logger.warn("Invalid double value for {}: {}", key, value);
            }
        }
        return defaultValue;
    }

    private String getStringValue(Map<String, Object> config, String key, String defaultValue) {
        if (config == null) {
            return defaultValue;
        }
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    @Override
    public String toString() {
        return "MemoryProcessorConfig{" +
                "maxRetries=" + maxRetries +
                ", retryDelayMs=" + retryDelayMs +
                ", backoffMultiplier=" + backoffMultiplier +
                ", cacheEnabled=" + cacheEnabled +
                ", cacheMaxSize=" + cacheMaxSize +
                ", cacheExpireAfterWriteSeconds=" + cacheExpireAfterWriteSeconds +
                ", monitoringEnabled=" + monitoringEnabled +
                ", trackPerformance=" + trackPerformance +
                ", trackErrors=" + trackErrors +
                ", llmModel='" + llmModel + '\'' +
                ", llmTemperature=" + llmTemperature +
                ", llmMaxTokens=" + llmMaxTokens +
                '}';
    }
}
