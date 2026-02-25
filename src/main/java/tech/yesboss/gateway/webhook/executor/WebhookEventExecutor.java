package tech.yesboss.gateway.webhook.executor;

import tech.yesboss.gateway.webhook.model.ImWebhookEvent;

import java.util.concurrent.ExecutorService;

/**
 * Webhook Async Event Executor
 *
 * This executor handles IM webhook events asynchronously using virtual threads.
 * All time-consuming operations (routing, SessionManager calls, LLM inference) are
 * executed in background threads, while the HTTP webhook response returns immediately.
 *
 * <p>Key Design:</p>
 * <ul>
 *   <li>Uses virtual threads for high-concurrency async processing</li>
 *   <li>Processes events in background without blocking webhook responses</li>
 *   <li>Integrates with SessionManager, MasterRunner, and IMMessagePusher</li>
 * </ul>
 */
public interface WebhookEventExecutor {

    /**
     * Process the webhook event asynchronously in a background virtual thread.
     *
     * <p>Execution Flow:</p>
     * <ol>
     *   <li>Call SessionManager to bind or create session</li>
     *   <li>Call MasterRunner.run() to start LLM inference</li>
     *   <li>Push results via IMMessagePusher</li>
     * </ol>
     *
     * <p>This method must return immediately after submitting the task to the executor.
     * The actual processing happens in a background virtual thread.</p>
     *
     * @param event The internal webhook event object to process
     */
    void processAsync(ImWebhookEvent event);

    /**
     * Shutdown the executor gracefully.
     *
     * <p>Attempts to shutdown the executor, waiting for pending tasks to complete.
     * This method should be called during application shutdown.</p>
     */
    void shutdown();

    /**
     * Check if the executor is currently running.
     *
     * @return true if the executor is accepting new tasks, false otherwise
     */
    boolean isRunning();

    /**
     * Get the underlying ExecutorService for monitoring purposes.
     *
     * @return the executor service
     */
    ExecutorService getExecutor();
}
