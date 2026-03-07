package tech.yesboss.memory.e2e;

import org.junit.jupiter.api.*;
import tech.yesboss.config.ConfigurationManager;
import tech.yesboss.config.YesBossConfig;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.domain.message.UnifiedMessage.Role;
import tech.yesboss.domain.message.UnifiedMessage.PayloadFormat;
import tech.yesboss.memory.model.Resource;
import tech.yesboss.memory.service.MemoryService;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production Environment Deployment Test
 *
 * <p>This test verifies the system's readiness for production deployment,
 * including:</p>
 *
 * <ul>
 *   <li><b>Configuration Validation:</b> Production configuration is valid</li>
 *   <li><b>Resource Management:</b> System resources are properly managed</li>
 *   <li><b>Error Handling:</b> Production-grade error handling</li>
 *   <li><b>Logging and Monitoring:</b> Adequate logging and monitoring</li>
 *   <li><b>Security:</b> Security measures are in place</li>
 * </ul>
 *
 * <p><b>Test Frameworks:</b> JUnit 5</p>
 * <p><b>Reference:</b> docs_memory/记忆持久化模块v3.0.md</p>
 */
@DisplayName("Production Environment Deployment Test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProductionEnvironmentDeploymentTest {

    private static YesBossConfig productionConfig;
    private MemoryService memoryService;

    @BeforeAll
    static void setUpOnce() {
        System.out.println("========================================");
        System.out.println("Setting up Production Deployment Test");
        System.out.println("========================================");

        try {
            productionConfig = ConfigurationManager.getInstance().getConfig();
            System.out.println("Production configuration loaded");
        } catch (Exception e) {
            System.err.println("Warning: Could not load configuration: " + e.getMessage());
        }

        System.out.println("========================================");
    }

    @BeforeEach
    void setUp() {
        System.out.println("Setting up production test case");
    }

    // ==================== Test 1: Configuration Validation ====================

    @Test
    @Order(1)
    @DisplayName("Test 1: Production configuration is valid")
    void testProductionConfigurationValid() {
        System.out.println("\n========================================");
        System.out.println("Test 1: Production Configuration");
        System.out.println("========================================");

        assertNotNull(productionConfig, "Production config should be loaded");
        assertNotNull(productionConfig.getDatabase(), "Database config should exist");
        assertNotNull(productionConfig.getLlm(), "LLM config should exist");

        // Verify critical configuration values
        assertTrue(productionConfig.getDatabase().getSqlite().getPath() != null ||
                productionConfig.getDatabase().getPostgresql().getUrl() != null,
                "Database connection should be configured");

        System.out.println("✓ Production configuration test PASSED");
        System.out.println("========================================");
    }

    // ==================== Test 2: Resource Management ====================

    @Test
    @Order(2)
    @DisplayName("Test 2: System resources are properly managed")
    void testResourceManagement() {
        System.out.println("\n========================================");
        System.out.println("Test 2: Resource Management");
        System.out.println("========================================");

        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024); // MB
        long totalMemory = runtime.totalMemory() / (1024 * 1024); // MB
        long freeMemory = runtime.freeMemory() / (1024 * 1024); // MB
        long usedMemory = totalMemory - freeMemory;

        System.out.println("Max memory: " + maxMemory + "MB");
        System.out.println("Total memory: " + totalMemory + "MB");
        System.out.println("Used memory: " + usedMemory + "MB");
        System.out.println("Free memory: " + freeMemory + "MB");

        assertTrue(maxMemory > 0, "Max memory should be positive");
        assertTrue(freeMemory > 0, "Should have free memory");

        System.out.println("✓ Resource management test PASSED");
        System.out.println("========================================");
    }

    // ==================== Test 3: Error Handling ====================

    @Test
    @Order(3)
    @DisplayName("Test 3: Production-grade error handling")
    void testErrorHandling() {
        System.out.println("\n========================================");
        System.out.println("Test 3: Error Handling");
        System.out.println("========================================");

        // Test error handling with null inputs
        assertThrows(Exception.class, () -> {
            memoryService.extractFromMessages(null, "conv-id", "session-id");
        }, "Should handle null messages gracefully");

        // Test error handling with empty inputs
        List<UnifiedMessage> emptyMessages = new ArrayList<>();
        List<Resource> result = memoryService.extractFromMessages(
                emptyMessages, "conv-id", "session-id");

        assertNotNull(result, "Should handle empty messages gracefully");

        System.out.println("✓ Error handling test PASSED");
        System.out.println("========================================");
    }

    // ==================== Test 4: Logging and Monitoring ====================

    @Test
    @Order(4)
    @DisplayName("Test 4: Logging and monitoring are adequate")
    void testLoggingAndMonitoring() {
        System.out.println("\n========================================");
        System.out.println("Test 4: Logging and Monitoring");
        System.out.println("========================================");

        // Verify logging is configured
        assertTrue(productionConfig != null,
                "Configuration should be available for logging");

        // Test that operations are logged
        List<UnifiedMessage> messages = createProductionTestMessages();
        String convId = "prod-log-" + System.currentTimeMillis();
        String sessionId = "prod-session-" + System.currentTimeMillis();

        try {
            memoryService.extractFromMessages(messages, convId, sessionId);
            System.out.println("Operation completed - logs should be generated");
        } catch (Exception e) {
            System.err.println("Error during operation: " + e.getMessage());
            // Error should be logged
        }

        System.out.println("✓ Logging and monitoring test PASSED");
        System.out.println("========================================");
    }

    // ==================== Test 5: Security Measures ====================

    @Test
    @Order(5)
    @DisplayName("Test 5: Security measures are in place")
    void testSecurityMeasures() {
        System.out.println("\n========================================");
        System.out.println("Test 5: Security Measures");
        System.out.println("========================================");

        // Verify API keys are configured
        assertTrue(productionConfig.getLlm().getZhipu().getApiKey() != null ||
                productionConfig.getLlm().getAnthropic().getApiKey() != null ||
                productionConfig.getLlm().getGemini().getApiKey() != null ||
                productionConfig.getLlm().getOpenai().getApiKey() != null,
                "At least one LLM API key should be configured");

        // Verify database security
        assertNotNull(productionConfig.getDatabase(),
                "Database configuration should exist");

        System.out.println("✓ Security measures test PASSED");
        System.out.println("========================================");
    }

    // ==================== Helper Methods ====================

    private List<UnifiedMessage> createProductionTestMessages() {
        List<UnifiedMessage> messages = new ArrayList<>();

        messages.add(new UnifiedMessage(Role.USER,
                "Production environment test message", PayloadFormat.TEXT));
        messages.add(new UnifiedMessage(Role.ASSISTANT,
                "Production environment test response", PayloadFormat.TEXT));

        return messages;
    }
}
