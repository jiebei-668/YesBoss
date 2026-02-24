package tech.yesboss;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.config.ConfigurationManager;
import tech.yesboss.config.YesBossConfig;

import static spark.Spark.*;

/**
 * YesBoss Application Entry Point - Simplified Version
 *
 * <p>Basic HTTP server for testing webhook endpoints.</p>
 * <p>Full initialization will be implemented incrementally.</p>
 */
public class YesBossApplication {

    private static final Logger logger = LoggerFactory.getLogger(YesBossApplication.class);

    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("Starting YesBoss v{}", "1.0.0-SNAPSHOT");
        logger.info("========================================");

        try {
            // Step 1: Load configuration
            logger.info("Loading configuration...");
            YesBossConfig config = ConfigurationManager.getInstance().getConfig();

            // Step 2: Start HTTP server
            int port = config.getApp().getServer().getPort();
            logger.info("Starting HTTP server on port {}...", port);
            port(port);

            // CORS support
            before((request, response) -> {
                response.header("Access-Control-Allow-Origin", "*");
                response.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                response.header("Access-Control-Allow-Headers", "*");
            });

            // Health check endpoint
            get("/health", (request, response) -> {
                response.status(200);
                response.type("application/json");
                return "{\"status\":\"ok\",\"service\":\"YesBoss\"}";
            });

            // Feishu webhook endpoint (echo for testing)
            String feishuPath = config.getIm().getFeishu().getWebhook().getPath();
            post(feishuPath, (request, response) -> {
                logger.info("Feishu webhook received");
                logger.debug("Headers: {}", request.headers());
                logger.debug("Body: {}", request.body());
                response.status(200);
                return "200 OK";
            });

            // Slack webhook endpoint (echo for testing)
            String slackPath = config.getIm().getSlack().getWebhook().getPath();
            post(slackPath, (request, response) -> {
                logger.info("Slack webhook received");
                logger.debug("Headers: {}", request.headers());
                logger.debug("Body: {}", request.body());
                response.status(200);
                return "200 OK";
            });

            // Exception handling
            exception(Exception.class, (exception, request, response) -> {
                logger.error("Unhandled exception: {}", exception.getMessage(), exception);
                response.status(500);
                response.body("{\"error\":\"Internal server error\"}");
            });

            // Await server initialization
            awaitInitialization();

            logger.info("========================================");
            logger.info("YesBoss started successfully!");
            logger.info("Health:  http://0.0.0.0:{}/health", port);
            logger.info("Feishu:  http://0.0.0.0:{}{}", port, feishuPath);
            logger.info("Slack:   http://0.0.0.0:{}{}", port, slackPath);
            logger.info("========================================");
            logger.warn("Note: This is a simplified version for testing.");
            logger.warn("Full business logic initialization will be added incrementally.");

        } catch (Exception e) {
            logger.error("Failed to start YesBoss: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
