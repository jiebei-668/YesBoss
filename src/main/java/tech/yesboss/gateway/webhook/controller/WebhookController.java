package tech.yesboss.gateway.webhook.controller;

/**
 * Unified Webhook Route Controller (Async Design)
 *
 * <p>This controller handles incoming IM webhook events from Feishu and Slack.
 * The core design principle is immediate HTTP 200 OK response to prevent
 * webhook timeout and retry storms.</p>
 *
 * <p><b>Critical Constraint:</b> Upon receiving a payload, the controller must
 * immediately return 200 OK. All time-consuming operations (routing, LLM inference,
 * etc.) are executed asynchronously in background virtual threads.</p>
 *
 * <p>Execution Flow:</p>
 * <ol>
 *   <li>Verify signature (fast, &lt;100ms)</li>
 *   <li>Parse payload into ImWebhookEvent</li>
 *   <li>Delegate to WebhookEventExecutor for async processing</li>
 *   <li><b>Immediately return HTTP 200 OK</b> (within 3 seconds)</li>
 * </ol>
 *
 * <p>This interface is designed to work with HTTP frameworks like:</p>
 * <ul>
 *   <li>Spring Boot (@RestController, @PostMapping)</li>
 *   <li>Jakarta EE (JAX-RS)</li>
 *   <li>Vert.x</li>
 *   <li>Raw HTTP server implementations</li>
 * </ul>
 */
public interface WebhookController {

    /**
     * Handle Feishu (Lark) webhook events.
     *
     * <p>Execution Flow:</p>
     * <ol>
     *   <li>Verify Feishu signature using timestamp and nonce</li>
     *   <li>Parse JSON payload into ImWebhookEvent</li>
     *   <li>Submit event to WebhookEventExecutor for async processing</li>
     *   <li><b>Immediately return "200 OK" string</b></li>
     * </ol>
     *
     * <p>Feishu Webhook Signature Algorithm:</p>
     * <pre>
     * signature = base64(hmac_sha256(secret, timestamp + nonce + body))
     * </pre>
     *
     * @param timestamp The timestamp from X-Lark-Request-Timestamp header
     * @param nonce     The nonce from X-Lark-Request-Nonce header
     * @param signature The signature from X-Lark-Signature header
     * @param body      The raw JSON payload string
     * @return The string "200 OK" to be returned as HTTP response
     * @throws SecurityException   if signature verification fails
     * @throws IllegalArgumentException if parsing fails
     */
    String handleFeishuEvent(String timestamp, String nonce, String signature, String body);

    /**
     * Handle Slack Event API webhook callbacks.
     *
     * <p>Execution Flow:</p>
     * <ol>
     *   <li>Verify Slack signature using timestamp and signing secret</li>
     *   <li>Parse JSON payload into ImWebhookEvent</li>
     *   <li>Submit event to WebhookEventExecutor for async processing</li>
     *   <li><b>Immediately return "200 OK" string</b></li>
     * </ol>
     *
     * <p>Slack Webhook Signature Algorithm:</p>
     * <pre>
     * signature = "v0=" + hmac_sha256(signing_secret, "v0:" + timestamp + ":" + body)
     * </pre>
     *
     * @param timestamp The timestamp from X-Slack-Request-Timestamp header
     * @param signature The signature from X-Slack-Signature header
     * @param body      The raw JSON payload string
     * @return The string "200 OK" to be returned as HTTP response
     * @throws SecurityException   if signature verification fails
     * @throws IllegalArgumentException if parsing fails
     */
    String handleSlackEvent(String timestamp, String signature, String body);

    /**
     * Handle CLI command input (for local testing).
     *
     * <p>CLI mode doesn't require async processing since it's interactive.
     * This method can execute synchronously.</p>
     *
     * @param command The command string from terminal
     */
    void handleCliCommand(String command);

    /**
     * Check if the controller is ready to accept webhook events.
     *
     * @return true if the controller and its executor are running
     */
    boolean isReady();
}
