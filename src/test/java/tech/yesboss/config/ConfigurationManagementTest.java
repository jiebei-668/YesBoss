package tech.yesboss.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for configuration loading, environment variable substitution,
 * and ConfigurationManager convenience methods.
 */
class ConfigurationManagementTest {

    private ConfigLoader configLoader;

    @BeforeEach
    void setUp() {
        configLoader = new ConfigLoader();
    }

    // Test 1: Loading configuration from application.yml
    @Test
    void testLoadConfigurationFromClasspath() {
        YesBossConfig config = configLoader.loadConfig("application.yml");

        assertNotNull(config, "Configuration should not be null");
        assertNotNull(config.getLlm(), "LLM config should not be null");
        assertNotNull(config.getIm(), "IM config should not be null");
        assertNotNull(config.getDatabase(), "Database config should not be null");
        assertNotNull(config.getScheduler(), "Scheduler config should not be null");
        assertNotNull(config.getSandbox(), "Sandbox config should not be null");
        assertNotNull(config.getLogging(), "Logging config should not be null");
        assertNotNull(config.getApp(), "App config should not be null");
    }

    // Test 2: Environment variable substitution
    @Test
    void testEnvironmentVariableSubstitution(@TempDir Path tempDir) throws IOException {
        // Create a test YAML file with environment variable placeholders
        Path testConfig = tempDir.resolve("test-config.yml");
        try (FileWriter writer = new FileWriter(testConfig.toFile())) {
            writer.write("""
                llm:
                  anthropic:
                    enabled: ${TEST_ENABLED:true}
                    apiKey: ${TEST_API_KEY:default-key}
                    baseUrl: ${TEST_BASE_URL:https://api.test.com}
                """);
        }

        // Set system properties to test substitution
        String originalEnabled = System.getProperty("TEST_ENABLED");
        String originalApiKey = System.getProperty("TEST_API_KEY");
        try {
            // Set system properties to test environment variable substitution
            System.setProperty("TEST_ENABLED", "false");
            System.setProperty("TEST_API_KEY", "env-var-key-12345");

            YesBossConfig config = configLoader.loadConfigFromFile(testConfig.toString());

            assertNotNull(config);
            // The substitution uses system properties when env vars are not set
            assertEquals("env-var-key-12345", config.getLlm().getAnthropic().getApiKey(),
                    "Should use system property value when env var is not set");
            assertFalse(config.getLlm().getAnthropic().isEnabled(),
                    "Should use system property value for boolean");

        } finally {
            // Restore original values
            if (originalEnabled != null) {
                System.setProperty("TEST_ENABLED", originalEnabled);
            } else {
                System.clearProperty("TEST_ENABLED");
            }
            if (originalApiKey != null) {
                System.setProperty("TEST_API_KEY", originalApiKey);
            } else {
                System.clearProperty("TEST_API_KEY");
            }
        }
    }

    // Test 3: Configuration property access via ConfigurationManager
    @Test
    void testConfigurationManagerPropertyAccess() {
        ConfigurationManager manager = ConfigurationManager.getInstance();
        YesBossConfig config = manager.getConfig();

        assertNotNull(config, "Config should not be null");

        // Test nested property access
        assertNotNull(config.getLlm().getAnthropic().getModel().getMaster(),
                "Master model name should be accessible");
        assertNotNull(config.getLlm().getAnthropic().getModel().getWorker(),
                "Worker model name should be accessible");

        // Test all top-level sections
        assertTrue(config.getDatabase().getType().equals("sqlite") ||
                        config.getDatabase().getType() == null,
                "Database type should be sqlite or null for default config");
    }

    // Test 4: Configuration fallback to defaults
    @Test
    void testConfigurationFallbackToDefaults() {
        ConfigLoader loader = new ConfigLoader();

        // Try to load a non-existent configuration
        assertThrows(ConfigLoader.ConfigLoadException.class,
                () -> loader.loadConfig("non-existent.yml"));

        // ConfigurationManager should still provide default config
        ConfigurationManager manager = ConfigurationManager.getInstance();
        YesBossConfig config = manager.getConfig();

        assertNotNull(config, "Should have default configuration");
        assertNotNull(config.getLlm(), "Default LLM config should exist");
        assertNotNull(config.getIm(), "Default IM config should exist");
    }

    // Test 5: Configuration reload
    @Test
    void testConfigurationReload(@TempDir Path tempDir) throws IOException {
        // Create initial configuration file
        Path testConfig = tempDir.resolve("reload-test.yml");
        try (FileWriter writer = new FileWriter(testConfig.toFile())) {
            writer.write("app:\n  name: InitialName\n");
        }

        ConfigLoader loader = new ConfigLoader();

        // Load initial configuration
        YesBossConfig config1 = loader.loadConfigFromFile(testConfig.toString());
        assertEquals("InitialName", config1.getApp().getName());

        int cacheSizeBeforeClear = loader.getCacheSize();
        assertTrue(cacheSizeBeforeClear > 0, "Cache should have at least one entry");

        // Modify the file
        try (FileWriter writer = new FileWriter(testConfig.toFile())) {
            writer.write("app:\n  name: ReloadedName\n");
        }

        // Clear cache and reload
        loader.clearCache();
        YesBossConfig config2 = loader.loadConfigFromFile(testConfig.toString());
        assertEquals("ReloadedName", config2.getApp().getName());

        // Verify cache was cleared and has new entry
        assertEquals(1, loader.getCacheSize(), "Cache should have 1 entry after reload");
    }

    // Test 6: Invalid configuration file handling
    @Test
    void testInvalidConfigurationFileHandling(@TempDir Path tempDir) throws IOException {
        // Create an invalid YAML file
        Path invalidConfig = tempDir.resolve("invalid.yml");
        try (FileWriter writer = new FileWriter(invalidConfig.toFile())) {
            writer.write("""
                invalid:
                  yaml:
                    [unclosed bracket
                """);
        }

        ConfigLoader loader = new ConfigLoader();

        assertThrows(ConfigLoader.ConfigLoadException.class,
                () -> loader.loadConfigFromFile(invalidConfig.toString()),
                "Should throw ConfigLoadException for invalid YAML");
    }

    // Test 7: Nested configuration property access
    @Test
    void testNestedConfigurationPropertyAccess() {
        ConfigurationManager manager = ConfigurationManager.getInstance();

        // Test deeply nested property: llm.anthropic.model.master
        String masterModel = manager.getAnthropicMasterModel();
        assertNotNull(masterModel, "Master model should not be null");
        assertFalse(masterModel.isEmpty(), "Master model should not be empty");

        // Test worker model
        String workerModel = manager.getAnthropicWorkerModel();
        assertNotNull(workerModel, "Worker model should not be null");
        assertFalse(workerModel.isEmpty(), "Worker model should not be empty");

        // Test other nested properties
        int maxLoops = manager.getCircuitBreakerMaxLoops();
        assertTrue(maxLoops > 0, "Circuit breaker max loops should be positive");

        int tokenThreshold = manager.getCondensationTokenThreshold();
        assertTrue(tokenThreshold > 0, "Condensation token threshold should be positive");
    }

    // Test 8: Verify all convenience methods return correct values
    @Test
    void testConvenienceMethods() {
        ConfigurationManager manager = ConfigurationManager.getInstance();

        // LLM provider status
        assertNotNull(manager.isAnthropicEnabled(), "Anthropic enabled status should not be null");
        assertNotNull(manager.isGeminiEnabled(), "Gemini enabled status should not be null");
        assertNotNull(manager.isOpenAIEnabled(), "OpenAI enabled status should not be null");
        assertNotNull(manager.isZhipuEnabled(), "Zhipu enabled status should not be null");

        // IM platform status
        assertNotNull(manager.isFeishuEnabled(), "Feishu enabled status should not be null");
        assertNotNull(manager.isSlackEnabled(), "Slack enabled status should not be null");

        // Server configuration
        int serverPort = manager.getServerPort();
        assertTrue(serverPort > 0 && serverPort <= 65535,
                "Server port should be in valid range: " + serverPort);

        // Application configuration
        String environment = manager.getEnvironment();
        assertNotNull(environment, "Environment should not be null");
        assertFalse(environment.isEmpty(), "Environment should not be empty");

        // Sandbox configuration
        assertNotNull(manager.isSandboxEnabled(), "Sandbox enabled status should not be null");

        // Threading configuration
        assertNotNull(manager.isVirtualThreadsEnabled(),
                "Virtual threads enabled status should not be null");

        // Logging configuration
        String logLevel = manager.getLoggingLevel();
        assertNotNull(logLevel, "Log level should not be null");
        assertFalse(logLevel.isEmpty(), "Log level should not be empty");

        String logFilePath = manager.getLogFilePath();
        assertNotNull(logFilePath, "Log file path should not be null");
        assertFalse(logFilePath.isEmpty(), "Log file path should not be empty");
    }

    // Test 9: Test cache functionality
    @Test
    void testCacheFunctionality(@TempDir Path tempDir) throws IOException {
        Path testConfig = tempDir.resolve("cache-test.yml");
        try (FileWriter writer = new FileWriter(testConfig.toFile())) {
            writer.write("app:\n  version: 1.0.0\n");
        }

        ConfigLoader loader = new ConfigLoader();

        // First load should populate cache
        YesBossConfig config1 = loader.loadConfigFromFile(testConfig.toString());
        int cacheSizeAfterFirstLoad = loader.getCacheSize();
        assertTrue(cacheSizeAfterFirstLoad > 0, "Cache should contain at least 1 entry");

        // Second load should use cache
        YesBossConfig config2 = loader.loadConfigFromFile(testConfig.toString());
        assertEquals(config1.getApp().getVersion(), config2.getApp().getVersion(),
                "Cached config should be identical");

        // Clear all cache
        loader.clearCache();
        assertEquals(0, loader.getCacheSize(), "Cache should be empty after clearing all");
    }

    // Test 10: Test singleton behavior
    @Test
    void testSingletonBehavior() {
        ConfigurationManager instance1 = ConfigurationManager.getInstance();
        ConfigurationManager instance2 = ConfigurationManager.getInstance();

        assertSame(instance1, instance2,
                "Should return the same instance (singleton pattern)");
    }

    // Test 11: Test default configuration creation
    @Test
    void testDefaultConfigurationCreation() {
        ConfigurationManager manager = ConfigurationManager.getInstance();

        // If application.yml doesn't exist or fails to load, default config should be used
        YesBossConfig config = manager.getConfig();

        // Verify all sections are present in default config
        assertNotNull(config.getLlm(), "Default LLM config should exist");
        assertNotNull(config.getIm(), "Default IM config should exist");
        assertNotNull(config.getDatabase(), "Default Database config should exist");
        assertNotNull(config.getScheduler(), "Default Scheduler config should exist");
        assertNotNull(config.getSandbox(), "Default Sandbox config should exist");
        assertNotNull(config.getLogging(), "Default Logging config should exist");
        assertNotNull(config.getApp(), "Default App config should exist");
    }

    // Test 12: Test configuration reload functionality
    @Test
    void testConfigurationReloadFunctionality() {
        ConfigurationManager manager = ConfigurationManager.getInstance();

        // Get initial config
        YesBossConfig config1 = manager.getConfig();
        assertNotNull(config1);

        // Reload should not throw exceptions
        assertDoesNotThrow(() -> manager.reloadConfig(),
                "Reload should complete without exceptions");

        // Config should still be valid after reload
        YesBossConfig config2 = manager.getConfig();
        assertNotNull(config2);
    }
}
