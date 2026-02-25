package tech.yesboss.gateway.webhook.executor.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.gateway.webhook.executor.WebhookEventExecutor;
import tech.yesboss.gateway.webhook.model.ImWebhookEvent;
import tech.yesboss.runner.MasterRunner;
import tech.yesboss.session.SessionManager;
import tech.yesboss.state.TaskManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Webhook Async Event Executor Implementation
 *
 * This implementation uses a cached thread pool for async processing.
 * Each webhook event is processed in a separate thread from the pool.
 *
 * <p>Key Features:</p>
 * <ul>
 *   <li>Cached thread pool executor (Java 17 compatible)</li>
 *   <li>Graceful shutdown with timeout</li>
 *   <li>Comprehensive error handling and logging</li>
 *   <li>Integration with SessionManager, TaskManager, and MasterRunner</li>
 * </ul>
 *
 * <p>Note: When upgrading to Java 21+, consider using Executors.newVirtualThreadPerTaskExecutor()
 * for improved resource efficiency with virtual threads.</p>
 */
public class WebhookEventExecutorImpl implements WebhookEventExecutor {

    private static final Logger logger = LoggerFactory.getLogger(WebhookEventExecutorImpl.class);

    private final ExecutorService executorService;
    private final SessionManager sessionManager;
    private final TaskManager taskManager;
    private final MasterRunner masterRunner;
    private final AtomicBoolean running;

    /**
     * Creates a new WebhookEventExecutorImpl.
     *
     * @param sessionManager The session manager for routing
     * @param taskManager    The task manager for state management
     * @param masterRunner   The master runner for orchestration
     * @throws IllegalArgumentException if any parameter is null
     */
    public WebhookEventExecutorImpl(
        SessionManager sessionManager,
        TaskManager taskManager,
        MasterRunner masterRunner
    ) {
        if (sessionManager == null) {
            throw new IllegalArgumentException("sessionManager cannot be null");
        }
        if (taskManager == null) {
            throw new IllegalArgumentException("taskManager cannot be null");
        }
        if (masterRunner == null) {
            throw new IllegalArgumentException("masterRunner cannot be null");
        }

        this.sessionManager = sessionManager;
        this.taskManager = taskManager;
        this.masterRunner = masterRunner;
        // Use cached thread pool for Java 17 compatibility
        // TODO: Upgrade to Executors.newVirtualThreadPerTaskExecutor() when migrating to Java 21+
        this.executorService = Executors.newCachedThreadPool();
        this.running = new AtomicBoolean(true);

        logger.info("WebhookEventExecutor initialized with cached thread pool executor");
    }

    @Override
    public void processAsync(ImWebhookEvent event) {
        if (!running.get()) {
            logger.warn("Executor is not running, rejecting event: {}", event.eventType());
            throw new IllegalStateException("WebhookEventExecutor is not running");
        }

        if (event == null) {
            throw new IllegalArgumentException("event cannot be null");
        }

        logger.info("Submitting async event processing: type={}, group={}, user={}",
            event.imType(), event.imGroupId(), event.userId());

        executorService.submit(() -> {
            try {
                processEventInternal(event);
            } catch (Exception e) {
                logger.error("Error processing webhook event: type={}, group={}, error={}",
                    event.imType(), event.imGroupId(), e.getMessage(), e);
            }
        });
    }

    /**
     * Internal method to process the webhook event.
     *
     * <p>Execution Flow:</p>
     * <ol>
     *   <li>For CLI mode, create session directly and run master</li>
     *   <li>For IM modes (Feishu/Slack), handle group delete events via SessionManager</li>
     *   <li>For message events, bind or create session and run master</li>
     * </ol>
     *
     * @param event The webhook event to process
     */
    private void processEventInternal(ImWebhookEvent event) {
        logger.debug("Processing event in virtual thread: type={}, event={}", event.imType(), event.eventType());

        if (event.isCli()) {
            // CLI mode: create session and run directly
            String sessionId = sessionManager.bindOrCreateTaskSession(
                event.imType(),
                event.imGroupId(),
                "CLI Command"
            );
            logger.info("CLI session created: {}", sessionId);
            masterRunner.run(sessionId);
            return;
        }

        // IM modes: handle different event types
        if (event.isGroupDeleteEvent()) {
            // Handle group dissolution
            logger.info("Group delete event detected, destroying session: {}", event.imGroupId());
            sessionManager.destroySessionCascade(event.imGroupId());
            return;
        }

        // Handle message events (default behavior)
        if (event.isMessageEvent() || event.isGroupJoinEvent()) {
            // Bind or create session for this group
            String sessionId = sessionManager.bindOrCreateTaskSession(
                event.imType(),
                event.imGroupId(),
                "IM Task: " + event.imGroupId()
            );
            logger.info("Session bound/created for event: session={}, group={}", sessionId, event.imGroupId());

            // Start master runner orchestration
            masterRunner.run(sessionId);
            return;
        }

        logger.warn("Unhandled event type: {}", event.eventType());
    }

    @Override
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            logger.warn("Executor already shut down");
            return;
        }

        logger.info("Shutting down WebhookEventExecutor...");

        executorService.shutdown();

        try {
            // Wait for pending tasks to complete (max 30 seconds)
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("Executor did not terminate in time, forcing shutdown");
                executorService.shutdownNow();
            }

            // Additional wait for forced shutdown
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.error("Executor did not terminate after forced shutdown");
            } else {
                logger.info("WebhookEventExecutor shut down successfully");
            }
        } catch (InterruptedException e) {
            logger.error("Shutdown interrupted", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public ExecutorService getExecutor() {
        return executorService;
    }
}
