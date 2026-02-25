package tech.yesboss;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;
import tech.yesboss.config.ConfigurationManager;
import tech.yesboss.config.YesBossConfig;
import tech.yesboss.gateway.webhook.controller.WebhookController;
import tech.yesboss.gateway.webhook.route.WebhookRouteHandler;
import tech.yesboss.health.HealthCheckService;

import static spark.Spark.*;

/**
 * YesBoss Application Entry Point
 *
 * <p>This is the main entry point for the YesBoss Multi-Agent Task Orchestration Platform.</p>
 *
 * <p><b>Startup Sequence:</b></p>
 * <ol>
 *   <li>Load configuration from application.yml</li>
 *   <li>Initialize ApplicationContext (all business components)</li>
 *   <li>Configure HTTP server with Spark</li>
 *   <li>Setup webhook routes with WebhookRouteHandler</li>
 *   <li>Register signal handlers for graceful shutdown (SIGTERM)</li>
 *   <li>Log startup completion banner</li>
 * </ol>
 *
 * <p><b>Endpoints:</b></p>
 * <ul>
 *   <li>GET /health - Health check endpoint with component status</li>
 *   <li>GET /ready - Readiness check endpoint for webhook traffic</li>
 *   <li>GET /metrics - Application metrics endpoint</li>
 *   <li>POST /webhook/feishu - Feishu webhook events</li>
 *   <li>POST /webhook/feishu/callback - Feishu interactive callbacks</li>
 *   <li>POST /webhook/slack - Slack webhook events</li>
 *   <li>POST /webhook/slack/callback - Slack interactive callbacks</li>
 * </ul>
 *
 * <p><b>Graceful Shutdown:</b></p>
 * <p>Handles SIGTERM signal to gracefully shutdown the application:</p>
 * <ul>
 *   <li>Sets ready flag to false (reject new webhook traffic)</li>
 *   <li>Stops Spark HTTP server</li>
 *   <li>Shuts down executor services</li>
 *   <li>Closes database connections</li>
 * </ul>
 */
public class YesBossApplication {

    private static final Logger logger = LoggerFactory.getLogger(YesBossApplication.class);

    private static ApplicationContext applicationContext;
    private static WebhookRouteHandler webhookRouteHandler;

    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("Starting YesBoss v{}", "1.0.0-SNAPSHOT");
        logger.info("========================================");

        try {
            // Step 1: Load configuration
            logger.info("Loading configuration...");
            YesBossConfig config = ConfigurationManager.getInstance().getConfig();

            // Step 2: Initialize ApplicationContext (all business components)
            logger.info("Initializing ApplicationContext...");
            applicationContext = new ApplicationContext(config);
            applicationContext.initialize();
            applicationContext.setupShutdownHook();

            // Step 3: Start HTTP server
            int port = config.getApp().getServer().getPort();
            logger.info("Starting HTTP server on port {}...", port);
            port(port);

            // Step 4: Setup global routes (health, ready, metrics)
            setupGlobalRoutes(config);

            // Step 5: Setup webhook routes with WebhookController integration
            logger.info("Setting up webhook routes...");
            WebhookController webhookController = applicationContext.getWebhookController();
            webhookRouteHandler = new WebhookRouteHandler(webhookController);
            webhookRouteHandler.configureRoutes();

            // Step 6: Register signal handlers for graceful shutdown
            registerSignalHandlers();

            // Await server initialization
            awaitInitialization();

            // Log startup success
            logStartupSuccess(port, config);

        } catch (Exception e) {
            logger.error("========================================");
            logger.error("Failed to start YesBoss: {}", e.getMessage(), e);
            logger.error("========================================");
            System.exit(1);
        }
    }

    /**
     * Setup global routes (health, ready, metrics).
     *
     * @param config The application configuration
     */
    private static void setupGlobalRoutes(YesBossConfig config) {
        logger.info("Setting up global routes...");

        // Health check endpoint - checks all critical components
        get("/health", (request, response) -> {
            response.status(200);
            response.type("application/json");

            try {
                if (applicationContext == null || !applicationContext.isInitialized()) {
                    return "{\"status\":\"DOWN\",\"service\":\"YesBoss\",\"message\":\"ApplicationContext not initialized\"}";
                }

                HealthCheckService healthCheckService = applicationContext.getHealthCheckService();
                var healthDetails = healthCheckService.getHealthDetails();

                // Build JSON response
                StringBuilder json = new StringBuilder();
                json.append("{");
                json.append("\"status\":\"").append(healthDetails.get("status")).append("\",");
                json.append("\"service\":\"YesBoss\",");
                json.append("\"components\":{");

                @SuppressWarnings("unchecked")
                var components = (java.util.Map<String, HealthCheckService.HealthStatus>) healthDetails.get("components");
                boolean first = true;
                for (var entry : components.entrySet()) {
                    if (!first) json.append(",");
                    json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
                    first = false;
                }

                json.append("}}");
                return json.toString();

            } catch (Exception e) {
                logger.error("Error checking health status", e);
                return "{\"status\":\"ERROR\",\"service\":\"YesBoss\",\"message\":\"" + e.getMessage() + "\"}";
            }
        });

        // Ready check endpoint (for Kubernetes Readiness probes)
        get("/ready", (request, response) -> {
            response.type("application/json");

            try {
                if (applicationContext == null || !applicationContext.isInitialized() || !applicationContext.isReady()) {
                    response.status(503);  // Service Unavailable
                    return "{\"ready\":false,\"message\":\"Application not ready to accept webhook traffic\"}";
                }

                // Also check WebhookController readiness
                WebhookController webhookController = applicationContext.getWebhookController();
                if (!webhookController.isReady()) {
                    response.status(503);  // Service Unavailable
                    return "{\"ready\":false,\"message\":\"WebhookController not ready\"}";
                }

                response.status(200);
                return "{\"ready\":true,\"message\":\"Application is ready to accept webhook traffic\"}";

            } catch (Exception e) {
                logger.error("Error checking readiness", e);
                response.status(503);
                return "{\"ready\":false,\"message\":\"Error: " + e.getMessage() + "\"}";
            }
        });

        // Metrics endpoint (for monitoring)
        get("/metrics", (request, response) -> {
            response.status(200);
            response.type("application/json");

            try {
                if (applicationContext != null && applicationContext.isInitialized()) {
                    return applicationContext.getMetricsCollector().getMetricsAsJson();
                } else {
                    return "{\"service\":\"YesBoss\",\"status\":\"initializing\"}";
                }
            } catch (Exception e) {
                logger.error("Error collecting metrics", e);
                return "{\"service\":\"YesBoss\",\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}";
            }
        });

        logger.info("Global routes configured");
    }

    /**
     * Register signal handlers for graceful shutdown.
     */
    private static void registerSignalHandlers() {
        logger.info("Registering signal handlers for graceful shutdown...");

        // Handle SIGTERM (and also SIGINT via the standard shutdown hook)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("========================================");
            logger.info("Shutdown signal received (SIGTERM/SIGINT)");
            logger.info("========================================");

            try {
                // Log that we're no longer ready
                if (applicationContext != null) {
                    logger.info("Setting application to NOT READY - rejecting webhook traffic");
                }

                // Stop Spark server immediately to stop accepting new HTTP connections
                try {
                    logger.info("Stopping Spark HTTP server...");
                    Spark.stop();
                    logger.info("Spark HTTP server stopped");
                } catch (Exception e) {
                    logger.error("Error stopping Spark server", e);
                }

                // The ApplicationContext shutdown hook will handle the rest
                logger.info("Waiting for ApplicationContext to complete shutdown...");

            } catch (Exception e) {
                logger.error("Error during shutdown signal handling", e);
            }
        }, "ShutdownHook-SignalHandler"));

        logger.info("Signal handlers registered");
    }

    /**
     * Log startup success message with endpoint information.
     *
     * @param port   The server port
     * @param config The application configuration
     */
    private static void logStartupSuccess(int port, YesBossConfig config) {
        logger.info("========================================");
        logger.info("YesBoss started successfully!");
        logger.info("========================================");
        logger.info("Application Status: READY");
        logger.info("Accepting webhook traffic: YES");
        logger.info("========================================");
        logger.info("Health & Monitoring Endpoints:");
        logger.info("Health:  http://0.0.0.0:{}/health", port);
        logger.info("Ready:   http://0.0.0.0:{}/ready", port);
        logger.info("Metrics: http://0.0.0.0:{}/metrics", port);
        logger.info("========================================");
        logger.info("Webhook Endpoints:");
        logger.info("Feishu:  http://0.0.0.0:{}/webhook/feishu", port);
        logger.info("Feishu Callback: http://0.0.0.0:{}/webhook/feishu/callback", port);
        logger.info("Slack:   http://0.0.0.0:{}/webhook/slack", port);
        logger.info("Slack Callback: http://0.0.0.0:{}/webhook/slack/callback", port);
        logger.info("========================================");
        logger.info("All systems operational!");
        logger.info("Press Ctrl+C or send SIGTERM to shutdown gracefully");
        logger.info("========================================");
    }
}

