package tech.yesboss.gateway.webhook.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import tech.yesboss.config.YesBossConfig;
import tech.yesboss.gateway.webhook.controller.WebhookController;

import static spark.Spark.*;

/**
 * Webhook Route Handler - Bridges WebhookController to Spark HTTP Routes
 *
 * <p>This class extracts all Spark route setup logic from the main application
 * and provides a clean integration between the WebhookController and the HTTP layer.</p>
 *
 * <p><b>Key Design Principles:</b></p>
 * <ul>
 *   <li>Immediate 200 OK response after delegating to async executor</li>
 *   <li>Proper header extraction for signature verification</li>
 *   <li>Graceful error handling without exposing sensitive information</li>
 *   <li>Request body validation before processing</li>
 * </ul>
 *
 * <p><b>Routes Configured:</b></p>
 * <ul>
 *   <li>/webhook/feishu - Feishu webhook events</li>
 *   <li>/webhook/feishu/callback - Feishu interactive callbacks (human-in-the-loop)</li>
 *   <li>/webhook/slack - Slack webhook events</li>
 *   <li>/webhook/slack/callback - Slack interactive callbacks (human-in-the-loop)</li>
 * </ul>
 */
public class WebhookRouteHandler {

    private static final Logger logger = LoggerFactory.getLogger(WebhookRouteHandler.class);

    private static final String HTTP_200_OK = "200 OK";
    private static final String CONTENT_TYPE_JSON = "application/json";

    // HTTP Header names for Feishu
    private static final String FEISHU_HEADER_TIMESTAMP = "X-Lark-Request-Timestamp";
    private static final String FEISHU_HEADER_NONCE = "X-Lark-Request-Nonce";
    private static final String FEISHU_HEADER_SIGNATURE = "X-Lark-Signature";

    // HTTP Header names for Slack
    private static final String SLACK_HEADER_TIMESTAMP = "X-Slack-Request-Timestamp";
    private static final String SLACK_HEADER_SIGNATURE = "X-Slack-Signature";

    private final WebhookController webhookController;
    private final YesBossConfig config;

    /**
     * Creates a new WebhookRouteHandler.
     *
     * @param webhookController The webhook controller that processes events
     * @param config            The application configuration
     * @throws IllegalArgumentException if webhookController or config is null
     */
    public WebhookRouteHandler(WebhookController webhookController, YesBossConfig config) {
        if (webhookController == null) {
            throw new IllegalArgumentException("webhookController cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        this.webhookController = webhookController;
        this.config = config;
    }

    /**
     * Configure all webhook routes in Spark.
     *
     * <p>This method sets up:
     * <ul>
     *   <li>Feishu webhook and callback routes</li>
     *   <li>Slack webhook and callback routes</li>
     *   <li>CORS support for cross-origin requests</li>
     *   <li>Error handling for all routes</li>
     * </ul>
     */
    public void configureRoutes() {
        logger.info("Configuring webhook routes...");

        // Configure CORS before all routes
        setupCorsSupport();

        // Setup Feishu webhook routes
        setupFeishuRoutes();

        // Setup Slack webhook routes
        setupSlackRoutes();

        // Setup exception handling for webhook routes
        setupExceptionHandling();

        logger.info("Webhook routes configured successfully");
    }

    /**
     * Setup CORS support for webhook endpoints.
     */
    private void setupCorsSupport() {
        before((request, response) -> {
            // Apply CORS to configured webhook paths
            String path = request.pathInfo();
            String feishuPath = config.getIm().getFeishu().getWebhook().getPath();
            String slackPath = config.getIm().getSlack().getWebhook().getPath();

            if (path != null && (path.startsWith(feishuPath) || path.startsWith(slackPath))) {
                response.header("Access-Control-Allow-Origin", "*");
                response.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                response.header("Access-Control-Allow-Headers", "*");
                response.header("Access-Control-Max-Age", "3600");

                // Handle OPTIONS preflight request
                if ("OPTIONS".equalsIgnoreCase(request.requestMethod())) {
                    response.status(200);
                    response.body(HTTP_200_OK);
                }
            }
        });

        // Handle OPTIONS requests explicitly for configured paths
        String feishuPath = config.getIm().getFeishu().getWebhook().getPath();
        String slackPath = config.getIm().getSlack().getWebhook().getPath();

        options(feishuPath + "/*", (request, response) -> {
            response.status(200);
            return HTTP_200_OK;
        });
        options(slackPath + "/*", (request, response) -> {
            response.status(200);
            return HTTP_200_OK;
        });
    }

    /**
     * Setup Feishu webhook and callback routes.
     */
    private void setupFeishuRoutes() {
        logger.info("Setting up Feishu webhook routes...");

        // Get webhook path from configuration
        String feishuWebhookPath = config.getIm().getFeishu().getWebhook().getPath();
        logger.info("Feishu webhook path from config: {}", feishuWebhookPath);

        // Feishu webhook event route (configured path)
        post(feishuWebhookPath, (request, response) -> {
            return handleFeishuEvent(request, response);
        });

        // Feishu interactive callback route
        post(feishuWebhookPath + "/callback", (request, response) -> {
            return handleFeishuCallback(request, response);
        });
    }

    /**
     * Setup Slack webhook and callback routes.
     */
    private void setupSlackRoutes() {
        logger.info("Setting up Slack webhook routes...");

        // Get webhook path from configuration
        String slackWebhookPath = config.getIm().getSlack().getWebhook().getPath();
        logger.info("Slack webhook path from config: {}", slackWebhookPath);

        // Slack webhook event route
        post(slackWebhookPath, (request, response) -> {
            return handleSlackEvent(request, response);
        });

        // Slack interactive callback route (for human-in-the-loop)
        post(slackWebhookPath + "/callback", (request, response) -> {
            return handleSlackCallback(request, response);
        });
    }

    /**
     * Setup exception handling for webhook routes.
     */
    private void setupExceptionHandling() {
        // Handle SecurityException (signature verification failures)
        exception(SecurityException.class, (exception, request, response) -> {
            logger.warn("Security exception on webhook route {}: {}",
                request.pathInfo(), exception.getMessage());

            response.status(403);  // Forbidden
            response.type(CONTENT_TYPE_JSON);
            response.body("{\"error\":\"Forbidden\",\"message\":\"Signature verification failed\"}");
        });

        // Handle IllegalArgumentException (invalid request)
        exception(IllegalArgumentException.class, (exception, request, response) -> {
            logger.warn("Invalid request on webhook route {}: {}",
                request.pathInfo(), exception.getMessage());

            response.status(400);  // Bad Request
            response.type(CONTENT_TYPE_JSON);
            response.body("{\"error\":\"Bad Request\",\"message\":\"" +
                sanitizeErrorMessage(exception.getMessage()) + "\"}");
        });

        // Handle generic exceptions
        exception(Exception.class, (exception, request, response) -> {
            logger.error("Unhandled exception on webhook route {}: {}",
                request.pathInfo(), exception.getMessage(), exception);

            response.status(500);  // Internal Server Error
            response.type(CONTENT_TYPE_JSON);
            response.body("{\"error\":\"Internal Server Error\"}");
        });
    }

    // ==================== Feishu Route Handlers ====================

    /**
     * Handle Feishu webhook event.
     *
     * @param request  The HTTP request
     * @param response The HTTP response
     * @return "200 OK" string
     */
    private String handleFeishuEvent(Request request, Response response) {
        try {
            // Validate request method
            if (!"POST".equalsIgnoreCase(request.requestMethod())) {
                logger.warn("Feishu webhook received non-POST request: {}", request.requestMethod());
                response.status(405);  // Method Not Allowed
                return HTTP_200_OK;
            }

            // Extract headers
            String timestamp = request.headers(FEISHU_HEADER_TIMESTAMP);
            String nonce = request.headers(FEISHU_HEADER_NONCE);
            String signature = request.headers(FEISHU_HEADER_SIGNATURE);

            // Validate headers presence
            if (timestamp == null || timestamp.isEmpty()) {
                logger.warn("Feishu webhook missing {} header", FEISHU_HEADER_TIMESTAMP);
                throw new IllegalArgumentException("Missing timestamp header");
            }
            if (nonce == null || nonce.isEmpty()) {
                logger.warn("Feishu webhook missing {} header", FEISHU_HEADER_NONCE);
                throw new IllegalArgumentException("Missing nonce header");
            }
            if (signature == null || signature.isEmpty()) {
                logger.warn("Feishu webhook missing {} header", FEISHU_HEADER_SIGNATURE);
                throw new IllegalArgumentException("Missing signature header");
            }

            // Extract and validate body
            String body = request.body();
            if (body == null || body.isEmpty()) {
                logger.warn("Feishu webhook received empty body");
                throw new IllegalArgumentException("Empty request body");
            }

            // Log incoming request (without exposing sensitive data)
            logger.info("Feishu webhook received: timestamp={}, bodyLength={}",
                timestamp, body.length());

            // Delegate to WebhookController
            String result = webhookController.handleFeishuEvent(timestamp, nonce, signature, body);

            // Set response
            response.status(200);
            response.type("text/plain");

            return result;

        } catch (Exception e) {
            logger.error("Error handling Feishu webhook event", e);
            throw e;
        }
    }

    /**
     * Handle Feishu interactive callback (human-in-the-loop).
     *
     * @param request  The HTTP request
     * @param response The HTTP response
     * @return "200 OK" string
     */
    private String handleFeishuCallback(Request request, Response response) {
        try {
            // Validate request method
            if (!"POST".equalsIgnoreCase(request.requestMethod())) {
                logger.warn("Feishu callback received non-POST request: {}", request.requestMethod());
                response.status(405);
                return HTTP_200_OK;
            }

            // Extract headers
            String timestamp = request.headers(FEISHU_HEADER_TIMESTAMP);
            String nonce = request.headers(FEISHU_HEADER_NONCE);
            String signature = request.headers(FEISHU_HEADER_SIGNATURE);

            // Validate headers presence
            if (timestamp == null || timestamp.isEmpty()) {
                logger.warn("Feishu callback missing {} header", FEISHU_HEADER_TIMESTAMP);
                throw new IllegalArgumentException("Missing timestamp header");
            }
            if (nonce == null || nonce.isEmpty()) {
                logger.warn("Feishu callback missing {} header", FEISHU_HEADER_NONCE);
                throw new IllegalArgumentException("Missing nonce header");
            }
            if (signature == null || signature.isEmpty()) {
                logger.warn("Feishu callback missing {} header", FEISHU_HEADER_SIGNATURE);
                throw new IllegalArgumentException("Missing signature header");
            }

            // Extract and validate body
            String body = request.body();
            if (body == null || body.isEmpty()) {
                logger.warn("Feishu callback received empty body");
                throw new IllegalArgumentException("Empty request body");
            }

            logger.info("Feishu callback received: timestamp={}", timestamp);

            // Delegate to WebhookController
            String result = webhookController.handleFeishuCallback(timestamp, nonce, signature, body);

            response.status(200);
            response.type("text/plain");

            return result;

        } catch (Exception e) {
            logger.error("Error handling Feishu callback", e);
            throw e;
        }
    }

    // ==================== Slack Route Handlers ====================

    /**
     * Handle Slack webhook event.
     *
     * @param request  The HTTP request
     * @param response The HTTP response
     * @return "200 OK" string or challenge response
     */
    private String handleSlackEvent(Request request, Response response) {
        try {
            // Validate request method
            if (!"POST".equalsIgnoreCase(request.requestMethod())) {
                logger.warn("Slack webhook received non-POST request: {}", request.requestMethod());
                response.status(405);
                return HTTP_200_OK;
            }

            // Extract headers
            String timestamp = request.headers(SLACK_HEADER_TIMESTAMP);
            String signature = request.headers(SLACK_HEADER_SIGNATURE);

            // Validate headers presence
            if (timestamp == null || timestamp.isEmpty()) {
                logger.warn("Slack webhook missing {} header", SLACK_HEADER_TIMESTAMP);
                throw new IllegalArgumentException("Missing timestamp header");
            }
            if (signature == null || signature.isEmpty()) {
                logger.warn("Slack webhook missing {} header", SLACK_HEADER_SIGNATURE);
                throw new IllegalArgumentException("Missing signature header");
            }

            // Extract and validate body
            String body = request.body();
            if (body == null || body.isEmpty()) {
                logger.warn("Slack webhook received empty body");
                throw new IllegalArgumentException("Empty request body");
            }

            logger.info("Slack webhook received: timestamp={}, bodyLength={}",
                timestamp, body.length());

            // Delegate to WebhookController
            String result = webhookController.handleSlackEvent(timestamp, signature, body);

            // Check if this is a Slack URL verification challenge
            // In that case, return the challenge as-is with different content type
            if (!HTTP_200_OK.equals(result)) {
                response.status(200);
                response.type(CONTENT_TYPE_JSON);
                return result;
            }

            response.status(200);
            response.type("text/plain");

            return result;

        } catch (Exception e) {
            logger.error("Error handling Slack webhook event", e);
            throw e;
        }
    }

    /**
     * Handle Slack interactive callback (human-in-the-loop).
     *
     * @param request  The HTTP request
     * @param response The HTTP response
     * @return "200 OK" string
     */
    private String handleSlackCallback(Request request, Response response) {
        try {
            // Validate request method
            if (!"POST".equalsIgnoreCase(request.requestMethod())) {
                logger.warn("Slack callback received non-POST request: {}", request.requestMethod());
                response.status(405);
                return HTTP_200_OK;
            }

            // Extract headers
            String timestamp = request.headers(SLACK_HEADER_TIMESTAMP);
            String signature = request.headers(SLACK_HEADER_SIGNATURE);

            // Validate headers presence
            if (timestamp == null || timestamp.isEmpty()) {
                logger.warn("Slack callback missing {} header", SLACK_HEADER_TIMESTAMP);
                throw new IllegalArgumentException("Missing timestamp header");
            }
            if (signature == null || signature.isEmpty()) {
                logger.warn("Slack callback missing {} header", SLACK_HEADER_SIGNATURE);
                throw new IllegalArgumentException("Missing signature header");
            }

            // Slack sends callbacks as form data with a "payload" field
            String payload = request.queryParams("payload");
            if (payload == null || payload.isEmpty()) {
                // Try body as form data
                payload = request.body();
                if (payload == null || payload.isEmpty()) {
                    logger.warn("Slack callback received empty payload");
                    throw new IllegalArgumentException("Empty payload");
                }
            }

            logger.info("Slack callback received: timestamp={}, payloadLength={}",
                timestamp, payload.length());

            // Delegate to WebhookController
            String result = webhookController.handleSlackCallback(payload, timestamp, signature);

            response.status(200);
            response.type("text/plain");

            return result;

        } catch (Exception e) {
            logger.error("Error handling Slack callback", e);
            throw e;
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Sanitize error messages to prevent exposing sensitive information.
     *
     * @param message The original error message
     * @return A sanitized version safe to return to clients
     */
    private String sanitizeErrorMessage(String message) {
        if (message == null) {
            return "Invalid request";
        }

        // Remove potential sensitive information
        String sanitized = message
            .replaceAll("password=[^,\\s}]*", "password=***")
            .replaceAll("secret=[^,\\s}]*", "secret=***")
            .replaceAll("token=[^,\\s}]*", "token=***")
            .replaceAll("key=[^,\\s}]*", "key=***");

        // Limit length
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200);
        }

        return sanitized;
    }
}
