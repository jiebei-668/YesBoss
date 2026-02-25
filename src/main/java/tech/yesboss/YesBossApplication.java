package tech.yesboss;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;
import tech.yesboss.config.ConfigurationManager;
import tech.yesboss.config.YesBossConfig;
import tech.yesboss.gateway.webhook.controller.WebhookController;
import tech.yesboss.gateway.webhook.route.WebhookRouteHandler;

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
 *   <li>Register shutdown hooks for graceful cleanup</li>
 * </ol>
 *
 * <p><b>Endpoints:</b></p>
 * <ul>
 *   <li>GET /health - Health check endpoint</li>
 *   <li>POST /webhook/feishu - Feishu webhook events</li>
 *   <li>POST /webhook/feishu/callback - Feishu interactive callbacks</li>
 *   <li>POST /webhook/slack - Slack webhook events</li>
 *   <li>POST /webhook/slack/callback - Slack interactive callbacks</li>
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

            // Step 4: Setup global routes
            setupGlobalRoutes(config);

            // Step 5: Setup webhook routes with WebhookController integration
            logger.info("Setting up webhook routes...");
            WebhookController webhookController = applicationContext.getWebhookController();
            webhookRouteHandler = new WebhookRouteHandler(webhookController);
            webhookRouteHandler.configureRoutes();

            // Await server initialization
            awaitInitialization();

            // Log startup success
            logStartupSuccess(port, config);

        } catch (Exception e) {
            logger.error("Failed to start YesBoss: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    /**
     * Setup global routes (health check, etc.).
     *
     * @param config The application configuration
     */
    private static void setupGlobalRoutes(YesBossConfig config) {
        logger.info("Setting up global routes...");

        // Health check endpoint
        get("/health", (request, response) -> {
            response.status(200);
            response.type("application/json");

            // Check if ApplicationContext is ready
            boolean isReady = applicationContext != null && applicationContext.isInitialized()
                && applicationContext.getWebhookController() != null
                && applicationContext.getWebhookController().isReady();

            if (isReady) {
                return "{\"status\":\"ok\",\"service\":\"YesBoss\",\"ready\":true}";
            } else {
                return "{\"status\":\"ok\",\"service\":\"YesBoss\",\"ready\":false}";
            }
        });

        // Ready check endpoint (for Kubernetes/Liveness probes)
        get("/ready", (request, response) -> {
            response.status(200);
            response.type("application/json");

            boolean isReady = applicationContext != null && applicationContext.isInitialized()
                && applicationContext.getWebhookController() != null
                && applicationContext.getWebhookController().isReady();

            if (isReady) {
                return "{\"ready\":true}";
            } else {
                response.status(503);  // Service Unavailable
                return "{\"ready\":false}";
            }
        });

        // Metrics endpoint (for monitoring)
        get("/metrics", (request, response) -> {
            response.status(200);
            response.type("application/json");

            // Return basic metrics
            // TODO: Add more detailed metrics as needed
            return "{\"service\":\"YesBoss\",\"status\":\"running\"}";
        });

        logger.info("Global routes configured");
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
        logger.info("========================================");
    }
}
