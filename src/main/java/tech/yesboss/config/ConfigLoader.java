package tech.yesboss.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Configuration loader for YesBoss application.
 * Handles YAML parsing, environment variable substitution, and caching.
 */
public class ConfigLoader {

    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{(.*?)(?::([^}]*))?}");

    private final ObjectMapper yamlMapper;
    private final Map<String, YesBossConfig> configCache;
    private final EnvironmentResolver envResolver;

    public ConfigLoader() {
        this.yamlMapper = createYamlMapper();
        this.configCache = new ConcurrentHashMap<>();
        this.envResolver = new EnvironmentResolver();
    }

    /**
     * Creates a configured YAML ObjectMapper.
     */
    private ObjectMapper createYamlMapper() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * Loads configuration from classpath resource (application.yml).
     *
     * @return loaded YesBossConfig instance
     * @throws ConfigLoadException if configuration loading fails
     */
    public YesBossConfig loadConfig() {
        return loadConfig("application.yml");
    }

    /**
     * Loads configuration from classpath resource with specified name.
     *
     * @param resourceName the name of the resource on classpath
     * @return loaded YesBossConfig instance
     * @throws ConfigLoadException if configuration loading fails
     */
    public YesBossConfig loadConfig(String resourceName) {
        logger.debug("Loading configuration from classpath resource: {}", resourceName);

        // Check cache first
        if (configCache.containsKey(resourceName)) {
            logger.debug("Returning cached configuration for: {}", resourceName);
            return configCache.get(resourceName);
        }

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new ConfigLoadException("Configuration resource not found on classpath: " + resourceName);
            }

            YesBossConfig config = loadAndProcessConfig(inputStream, resourceName);
            configCache.put(resourceName, config);
            return config;

        } catch (IOException e) {
            throw new ConfigLoadException("Failed to load configuration from classpath: " + resourceName, e);
        }
    }

    /**
     * Loads configuration from an external file path.
     *
     * @param filePath the path to the configuration file
     * @return loaded YesBossConfig instance
     * @throws ConfigLoadException if configuration loading fails
     */
    public YesBossConfig loadConfigFromFile(String filePath) {
        logger.debug("Loading configuration from file: {}", filePath);

        // Use absolute path as cache key to avoid conflicts
        String cacheKey = "file:" + filePath;

        // Check cache first
        if (configCache.containsKey(cacheKey)) {
            logger.debug("Returning cached configuration for: {}", filePath);
            return configCache.get(cacheKey);
        }

        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new ConfigLoadException("Configuration file not found: " + filePath);
        }

        if (!Files.isReadable(path)) {
            throw new ConfigLoadException("Configuration file is not readable: " + filePath);
        }

        try (InputStream inputStream = Files.newInputStream(path)) {
            YesBossConfig config = loadAndProcessConfig(inputStream, filePath);
            configCache.put(cacheKey, config);
            return config;

        } catch (IOException e) {
            throw new ConfigLoadException("Failed to load configuration from file: " + filePath, e);
        }
    }

    /**
     * Loads and processes configuration from input stream.
     * Performs environment variable substitution on string values.
     */
    private YesBossConfig loadAndProcessConfig(InputStream inputStream, String source) throws IOException {
        logger.info("Parsing YAML configuration from: {}", source);

        // First, read the YAML content as a generic map to perform substitution
        @SuppressWarnings("unchecked")
        Map<String, Object> rawConfig = yamlMapper.readValue(inputStream, Map.class);

        // Perform environment variable substitution recursively
        Map<String, Object> processedConfig = substituteEnvironmentVariables(rawConfig);

        // Convert the processed map back to YesBossConfig
        return yamlMapper.convertValue(processedConfig, YesBossConfig.class);
    }

    /**
     * Recursively substitutes environment variables in configuration values.
     * Supports ${VAR_NAME} and ${VAR_NAME:default} syntax.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> substituteEnvironmentVariables(Map<String, Object> config) {
        Map<String, Object> result = new ConcurrentHashMap<>();

        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String stringValue) {
                // Substitute environment variables in string values
                result.put(key, substituteVariables(stringValue));
            } else if (value instanceof Map<?, ?> mapValue) {
                // Recursively process nested maps
                result.put(key, substituteEnvironmentVariables((Map<String, Object>) mapValue));
            } else if (value instanceof Iterable<?> iterableValue) {
                // Process lists and other iterables
                result.put(key, processIterable(iterableValue));
            } else {
                // Keep other types as-is (numbers, booleans, etc.)
                result.put(key, value);
            }
        }

        return result;
    }

    /**
     * Processes iterable collections (lists, sets, etc.) with environment variable substitution.
     */
    @SuppressWarnings("unchecked")
    private Iterable<?> processIterable(Iterable<?> iterable) {
        java.util.List<Object> result = new java.util.ArrayList<>();

        for (Object item : iterable) {
            if (item instanceof String stringValue) {
                result.add(substituteVariables(stringValue));
            } else if (item instanceof Map<?, ?> mapValue) {
                result.add(substituteEnvironmentVariables((Map<String, Object>) mapValue));
            } else if (item instanceof Iterable<?> iterableValue) {
                result.add(processIterable(iterableValue));
            } else {
                result.add(item);
            }
        }

        return result;
    }

    /**
     * Substitutes environment variables in a string value.
     * Pattern: ${VAR_NAME} or ${VAR_NAME:default_value}
     */
    private String substituteVariables(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }

        Matcher matcher = ENV_VAR_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String defaultValue = matcher.group(2);

            String resolvedValue = envResolver.resolve(varName, defaultValue);
            matcher.appendReplacement(result, Matcher.quoteReplacement(resolvedValue));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Clears the configuration cache.
     * Useful for configuration reload during runtime.
     */
    public void clearCache() {
        logger.info("Clearing configuration cache");
        configCache.clear();
    }

    /**
     * Clears the configuration cache for a specific resource.
     *
     * @param resourceName the name of the resource to remove from cache
     */
    public void clearCache(String resourceName) {
        logger.debug("Clearing cache for: {}", resourceName);
        configCache.remove(resourceName);
    }

    /**
     * Returns the current cache size.
     */
    public int getCacheSize() {
        return configCache.size();
    }

    /**
     * Environment variable resolver with system property fallback.
     */
    static class EnvironmentResolver {

        /**
         * Resolves an environment variable or system property.
         *
         * @param varName the variable name to resolve
         * @param defaultValue the default value if variable is not found
         * @return the resolved value or default value
         */
        String resolve(String varName, String defaultValue) {
            // First, try environment variable
            String envValue = System.getenv(varName);
            if (envValue != null) {
                return envValue;
            }

            // Second, try system property
            String propValue = System.getProperty(varName);
            if (propValue != null) {
                return propValue;
            }

            // Finally, return default value (may be null)
            return defaultValue;
        }
    }

    /**
     * Exception thrown when configuration loading fails.
     */
    public static class ConfigLoadException extends RuntimeException {

        public ConfigLoadException(String message) {
            super(message);
        }

        public ConfigLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
