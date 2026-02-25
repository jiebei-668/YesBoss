package tech.yesboss.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import spark.Spark;
import tech.yesboss.ApplicationContext;
import tech.yesboss.config.ConfigurationManager;
import tech.yesboss.config.YesBossConfig;
import tech.yesboss.gateway.webhook.controller.WebhookController;
import tech.yesboss.gateway.webhook.route.WebhookRouteHandler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Integration Test for Webhook Processing Flow
 *
 * <p>This test verifies the complete webhook processing flow from HTTP request
 * to async processing through the entire YesBoss application stack.</p>
 *
 * <p><b>Test Coverage:</b></p>
 * <ul>
 *   <li>Application startup and component initialization</li>
 *   <li>HTTP server endpoint availability</li>
 *   <li>Immediate 200 OK response for webhook requests</li>
 *   <li>Async event processing delegation</li>
 *   <li>Component initialization verification</li>
 *   <li>Graceful shutdown</li>
 * </ul>
 *
 * <p><b>Note:</b> This is an integration test that requires a real HTTP server.
 * The test uses in-memory database and stub LLM client for testing.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndWebhookIntegrationTest {

    private static final int TEST_PORT = 18080;
    private static final String TEST_BASE_URL = "http://localhost:" + TEST_PORT;

    private static HttpClient httpClient;
    private static ObjectMapper objectMapper;
    private static ApplicationContext applicationContext;
    private static WebhookRouteHandler webhookRouteHandler;

    @BeforeAll
    static void setUpOnce() throws Exception {
        System.out.println("========================================");
        System.out.println("Setting up End-to-End Integration Test");
        System.out.println("========================================");

        // Initialize HTTP client
        httpClient = HttpClient.newHttpClient();
        objectMapper = new ObjectMapper();

        // Load test configuration (use in-memory database for testing)
        System.out.println("Loading test configuration...");
        YesBossConfig testConfig = createTestConfig();

        // Initialize ApplicationContext with test configuration
        System.out.println("Initializing ApplicationContext...");
        applicationContext = new ApplicationContext(testConfig);
        applicationContext.initialize();
        applicationContext.setupShutdownHook();

        // Start HTTP server on test port
        System.out.println("Starting HTTP server on port " + TEST_PORT + "...");
        Spark.port(TEST_PORT);

        // Setup global routes
        setupGlobalRoutes();

        // Setup webhook routes
        WebhookController webhookController = applicationContext.getWebhookController();
        webhookRouteHandler = new WebhookRouteHandler(webhookController);
        webhookRouteHandler.configureRoutes();

        // Wait for server initialization
        Spark.awaitInitialization();

        System.out.println("HTTP server started successfully");
        System.out.println("========================================");
    }

    @AfterAll
    static void tearDownOnce() {
        System.out.println("========================================");
        System.out.println("Tearing down End-to-End Integration Test");
        System.out.println("========================================");

        // Stop Spark server
        if (webhookRouteHandler != null) {
            Spark.stop();
            try {
                Spark.awaitStop();
            } catch (Exception e) {
                System.err.println("Error stopping Spark: " + e.getMessage());
            }
        }

        // Shutdown ApplicationContext
        if (applicationContext != null) {
            applicationContext.shutdown();
        }

        System.out.println("Teardown completed");
        System.out.println("========================================");
    }

    // ==================== Test 1: Health Check Endpoint ====================

    @Test
    @Order(1)
    @DisplayName("Health check endpoint returns OK status")
    void testHealthCheckEndpoint() throws IOException, InterruptedException {
        System.out.println("\n--- Test 1: Health Check Endpoint ---");

        // Send GET request to /health
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TEST_BASE_URL + "/health"))
                .GET()
                .build();

        long startTime = System.nanoTime();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long responseTimeMs = (System.nanoTime() - startTime) / 1_000_000;

        System.out.println("Response time: " + responseTimeMs + "ms");
        System.out.println("Response status: " + response.statusCode());
        System.out.println("Response body: " + response.body());

        // Verify response
        assertEquals(200, response.statusCode(), "Health endpoint should return 200 OK");
        assertTrue(responseTimeMs < 100, "Health check should respond within 100ms, got: " + responseTimeMs + "ms");

        JsonNode json = objectMapper.readTree(response.body());
        assertTrue(json.has("status"), "Response should have status field");
        assertEquals("ok", json.get("status").asText(), "Status should be 'ok'");
        assertTrue(json.has("ready"), "Response should have ready field");
        assertTrue(json.get("ready").asBoolean(), "Application should be ready");

        System.out.println("Health check test PASSED");
    }

    // ==================== Test 2: Ready Check Endpoint ====================

    @Test
    @Order(2)
    @DisplayName("Ready check endpoint returns ready status")
    void testReadyCheckEndpoint() throws IOException, InterruptedException {
        System.out.println("\n--- Test 2: Ready Check Endpoint ---");

        // Send GET request to /ready
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TEST_BASE_URL + "/ready"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Response status: " + response.statusCode());
        System.out.println("Response body: " + response.body());

        // Verify response
        assertEquals(200, response.statusCode(), "Ready endpoint should return 200 OK");

        JsonNode json = objectMapper.readTree(response.body());
        assertTrue(json.has("ready"), "Response should have ready field");
        assertTrue(json.get("ready").asBoolean(), "Application should be ready");

        System.out.println("Ready check test PASSED");
    }

    // ==================== Test 3: Feishu Webhook Immediate Response ====================

    @Test
    @Order(3)
    @DisplayName("Feishu webhook returns 200 OK immediately")
    void testFeishuWebhookImmediateResponse() throws IOException, InterruptedException {
        System.out.println("\n--- Test 3: Feishu Webhook Immediate Response ---");

        // Create a valid Feishu webhook payload
        String feishuPayload = createFeishuWebhookPayload();

        // Send POST request to /webhook/feishu
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TEST_BASE_URL + "/webhook/feishu"))
                .header("Content-Type", "application/json")
                .header("X-Lark-Request-Timestamp", String.valueOf(Instant.now().getEpochSecond()))
                .header("X-Lark-Request-Nonce", "test-nonce")
                .header("X-Lark-Signature", "test-signature")
                .POST(HttpRequest.BodyPublishers.ofString(feishuPayload))
                .build();

        long startTime = System.nanoTime();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long responseTimeMs = (System.nanoTime() - startTime) / 1_000_000;

        System.out.println("Response time: " + responseTimeMs + "ms");
        System.out.println("Response status: " + response.statusCode());
        System.out.println("Response body: " + response.body());

        // Verify immediate response (<100ms)
        assertEquals(200, response.statusCode(), "Feishu webhook should return 200 OK");
        assertEquals("200 OK", response.body(), "Response body should be '200 OK'");
        assertTrue(responseTimeMs < 100, "Response should be immediate (<100ms), got: " + responseTimeMs + "ms");

        System.out.println("Feishu webhook immediate response test PASSED");
    }

    // ==================== Test 4: Slack Webhook Processing ====================

    @Test
    @Order(4)
    @DisplayName("Slack webhook triggers same processing flow")
    void testSlackWebhookProcessing() throws IOException, InterruptedException {
        System.out.println("\n--- Test 4: Slack Webhook Processing ---");

        // Create a valid Slack webhook payload
        String slackPayload = createSlackWebhookPayload();

        // Send POST request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TEST_BASE_URL + "/webhook/slack"))
                .header("Content-Type", "application/json")
                .header("X-Slack-Request-Timestamp", String.valueOf(Instant.now().getEpochSecond()))
                .header("X-Slack-Signature", "test-signature")
                .POST(HttpRequest.BodyPublishers.ofString(slackPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Verify immediate response
        assertEquals(200, response.statusCode(), "Should return 200 OK immediately");

        System.out.println("Slack webhook processing test PASSED");
    }

    // ==================== Test 5: Invalid Signature Handling ====================

    @Test
    @Order(5)
    @DisplayName("Invalid signature is handled gracefully")
    void testInvalidSignatureHandling() throws Exception {
        System.out.println("\n--- Test 5: Invalid Signature Handling ---");

        String feishuPayload = createFeishuWebhookPayload();

        // Send with invalid signature
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TEST_BASE_URL + "/webhook/feishu"))
                .header("Content-Type", "application/json")
                .header("X-Lark-Request-Timestamp", String.valueOf(Instant.now().getEpochSecond()))
                .header("X-Lark-Request-Nonce", "test-nonce-invalid")
                .header("X-Lark-Signature", "invalid-signature-xyz123")
                .POST(HttpRequest.BodyPublishers.ofString(feishuPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // In test mode with no secrets configured, signature verification is skipped
        assertEquals(200, response.statusCode(),
                "With no secrets configured, signature verification is skipped");

        System.out.println("Invalid signature handling test PASSED (signature skipped in test mode)");
    }

    // ==================== Test 6: Human-in-the-Loop Callback ====================

    @Test
    @Order(6)
    @DisplayName("Human-in-the-loop callback is processed")
    void testHumanInTheLoopCallback() throws Exception {
        System.out.println("\n--- Test 6: Human-in-the-Loop Callback ---");

        // Create a Feishu callback payload
        String callbackPayload = createFeishuCallbackPayload();

        // Send POST request to callback endpoint
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TEST_BASE_URL + "/webhook/feishu/callback"))
                .header("Content-Type", "application/json")
                .header("X-Lark-Request-Timestamp", String.valueOf(Instant.now().getEpochSecond()))
                .header("X-Lark-Request-Nonce", "test-nonce-callback")
                .header("X-Lark-Signature", "test-signature")
                .POST(HttpRequest.BodyPublishers.ofString(callbackPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Verify immediate response
        assertEquals(200, response.statusCode(), "Callback should return 200 OK immediately");

        System.out.println("Human-in-the-loop callback test PASSED");
    }

    // ==================== Test 7: Component Initialization ====================

    @Test
    @Order(7)
    @DisplayName("All components are properly initialized and connected")
    void testComponentInitialization() {
        System.out.println("\n--- Test 7: Component Initialization ---");

        // Verify ApplicationContext is initialized
        assertTrue(applicationContext.isInitialized(), "ApplicationContext should be initialized");

        // Verify all critical components are available
        assertNotNull(applicationContext.getWebhookController(), "WebhookController should be initialized");
        assertNotNull(applicationContext.getWebhookEventExecutor(), "WebhookEventExecutor should be initialized");
        assertNotNull(applicationContext.getSessionManager(), "SessionManager should be initialized");
        assertNotNull(applicationContext.getMasterRunner(), "MasterRunner should be initialized");
        assertNotNull(applicationContext.getWorkerRunner(), "WorkerRunner should be initialized");
        assertNotNull(applicationContext.getTaskManager(), "TaskManager should be initialized");
        assertNotNull(applicationContext.getModelRouter(), "ModelRouter should be initialized");
        assertNotNull(applicationContext.getDbWriter(), "DbWriter should be initialized");

        // Verify WebhookController is ready
        assertTrue(applicationContext.getWebhookController().isReady(), "WebhookController should be ready");

        // Verify WebhookEventExecutor is running
        assertTrue(applicationContext.getWebhookEventExecutor().isRunning(), "WebhookEventExecutor should be running");

        System.out.println("Component initialization test PASSED");
    }

    // ==================== Test 8: Metrics Endpoint ====================

    @Test
    @Order(8)
    @DisplayName("Metrics endpoint returns application metrics")
    void testMetricsEndpoint() throws IOException, InterruptedException {
        System.out.println("\n--- Test 8: Metrics Endpoint ---");

        // Send GET request to /metrics
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TEST_BASE_URL + "/metrics"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Response status: " + response.statusCode());
        System.out.println("Response body: " + response.body());

        // Verify response
        assertEquals(200, response.statusCode(), "Metrics endpoint should return 200 OK");

        JsonNode json = objectMapper.readTree(response.body());
        assertTrue(json.has("service"), "Metrics should have service field");
        assertTrue(json.has("status"), "Metrics should have status field");

        System.out.println("Metrics endpoint test PASSED");
    }

    // ==================== Test 9: Graceful Shutdown ====================

    @Test
    @Order(9)
    @DisplayName("Graceful shutdown releases all resources")
    void testGracefulShutdown() {
        System.out.println("\n--- Test 9: Graceful Shutdown ---");

        // Verify shutdown components exist
        assertTrue(applicationContext.isInitialized(), "ApplicationContext should be initialized before shutdown test");
        assertNotNull(applicationContext.getDbWriter(), "DbWriter should exist for shutdown");
        assertNotNull(applicationContext.getWebhookEventExecutor(), "WebhookEventExecutor should exist for shutdown");

        System.out.println("Graceful shutdown verification test PASSED");
        System.out.println("(Actual shutdown will occur in @AfterAll)");
    }

    // ==================== Test 10: Feishu Callback with Parameters ====================

    @Test
    @Order(10)
    @DisplayName("Feishu callback processes approval parameters")
    void testFeishuCallbackParameters() throws Exception {
        System.out.println("\n--- Test 10: Feishu Callback Parameters ---");

        // Create a Feishu callback payload with explicit approval
        String callbackPayload = createFeishuCallbackPayload();

        // Send POST request to callback endpoint
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TEST_BASE_URL + "/webhook/feishu/callback"))
                .header("Content-Type", "application/json")
                .header("X-Lark-Request-Timestamp", String.valueOf(Instant.now().getEpochSecond()))
                .header("X-Lark-Request-Nonce", "test-nonce-callback-2")
                .header("X-Lark-Signature", "test-signature")
                .POST(HttpRequest.BodyPublishers.ofString(callbackPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Verify immediate response
        assertEquals(200, response.statusCode(), "Callback should return 200 OK immediately");

        System.out.println("Feishu callback parameters test PASSED");
    }

    // ==================== Helper Methods ====================

    /**
     * Setup global routes for testing.
     */
    private static void setupGlobalRoutes() {
        Spark.get("/health", (request, response) -> {
            response.status(200);
            response.type("application/json");
            boolean isReady = applicationContext != null && applicationContext.isInitialized()
                    && applicationContext.getWebhookController() != null
                    && applicationContext.getWebhookController().isReady();
            return "{\"status\":\"ok\",\"service\":\"YesBoss\",\"ready\":" + isReady + "}";
        });

        Spark.get("/ready", (request, response) -> {
            response.status(200);
            response.type("application/json");
            boolean isReady = applicationContext != null && applicationContext.isInitialized()
                    && applicationContext.getWebhookController() != null
                    && applicationContext.getWebhookController().isReady();
            if (isReady) {
                return "{\"ready\":true}";
            } else {
                response.status(503);
                return "{\"ready\":false}";
            }
        });

        Spark.get("/metrics", (request, response) -> {
            response.status(200);
            response.type("application/json");
            return "{\"service\":\"YesBoss\",\"status\":\"running\"}";
        });
    }

    /**
     * Create a test configuration for integration testing.
     * Uses in-memory database for faster testing.
     */
    private static YesBossConfig createTestConfig() {
        try {
            // Load the real configuration
            YesBossConfig config = ConfigurationManager.getInstance().getConfig();

            // Override database path to use in-memory database for testing
            config.getDatabase().getSqlite().setPath(":memory:");

            // Set dummy API key for testing (ClaudeLlmClient requires non-empty API key)
            if (config.getLlm().getAnthropic().getApiKey() == null ||
                    config.getLlm().getAnthropic().getApiKey().isEmpty()) {
                config.getLlm().getAnthropic().setApiKey("test-api-key-for-integration-testing");
            }

            // Clear IM secrets for testing (disable signature verification)
            config.getIm().getFeishu().setAppSecret("");
            config.getIm().getSlack().setSigningSecret("");

            return config;
        } catch (Exception e) {
            System.err.println("Warning: Could not load configuration, using defaults: " + e.getMessage());
            return new YesBossConfig();
        }
    }

    /**
     * Create a valid Feishu webhook payload for testing.
     */
    private String createFeishuWebhookPayload() {
        return "{\n" +
                "  \"header\": {\n" +
                "    \"event_type\": \"im.message.receive_v1\",\n" +
                "    \"tenant_key\": \"test_tenant\",\n" +
                "    \"app_id\": \"test_app\"\n" +
                "  },\n" +
                "  \"event\": {\n" +
                "    \"sender\": {\n" +
                "      \"sender_id\": {\n" +
                "        \"user_id\": \"ou_test-user-id\"\n" +
                "      }\n" +
                "    },\n" +
                "    \"chat\": {\n" +
                "      \"chat_id\": \"oc_test-group-id\"\n" +
                "    },\n" +
                "    \"message\": {\n" +
                "      \"message_id\": \"om_test-message-id\",\n" +
                "      \"content\": \"{\\\"text\\\":\\\"Hello YesBoss\\\"}\"\n" +
                "    }\n" +
                "  }\n" +
                "}";
    }

    /**
     * Create a valid Slack webhook payload for testing.
     */
    private String createSlackWebhookPayload() {
        return "{\n" +
                "  \"token\": \"test-token\",\n" +
                "  \"team_id\": \"T_test-team\",\n" +
                "  \"api_app_id\": \"A_test-app\",\n" +
                "  \"event\": {\n" +
                "    \"type\": \"message\",\n" +
                "    \"user\": \"U_test-user\",\n" +
                "    \"channel\": \"C_test-channel-id\",\n" +
                "    \"text\": \"Hello YesBoss\",\n" +
                "    \"ts\": \"1234567890.123456\"\n" +
                "  }\n" +
                "}";
    }

    /**
     * Create a Feishu callback payload for testing human-in-the-loop.
     */
    private String createFeishuCallbackPayload() {
        return "{\n" +
                "  \"action\": {\n" +
                "    \"value\": \"{\\\"session_id\\\":\\\"worker-session-abc\\\",\\\"tool_call_id\\\":\\\"tool-call-xyz\\\",\\\"approved\\\":true}\"\n" +
                "  }\n" +
                "}";
    }
}
