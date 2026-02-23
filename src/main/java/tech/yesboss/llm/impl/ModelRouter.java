package tech.yesboss.llm.impl;

import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.llm.LlmClient;

import java.util.List;

/**
 * Router for dynamically allocating LLM clients based on role.
 *
 * <p>This class manages multiple LLM client instances (e.g., master and worker)
 * and routes requests to the appropriate client based on the role context.</p>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Hold references to master and worker LlmClient instances</li>
 *   <li>Route requests by role via {@link #routeByRole(String)}</li>
 *   <li>Provide consistent access to summarizer via {@link #getSummarizer()}</li>
 * </ul>
 */
public class ModelRouter {

    private final LlmClient masterClient;
    private final LlmClient workerClient;

    /**
     * Create a new ModelRouter with master and worker clients.
     *
     * @param masterClient The LLM client for MASTER role tasks
     * @param workerClient The LLM client for WORKER role tasks
     */
    public ModelRouter(LlmClient masterClient, LlmClient workerClient) {
        if (masterClient == null) {
            throw new IllegalArgumentException("masterClient cannot be null");
        }
        if (workerClient == null) {
            throw new IllegalArgumentException("workerClient cannot be null");
        }
        this.masterClient = masterClient;
        this.workerClient = workerClient;
    }

    /**
     * Route to the appropriate LLM client based on role.
     *
     * <p>Role mapping:
     * <ul>
     *   <li>"MASTER" → returns master client</li>
     *   <li>"WORKER" → returns worker client</li>
     *   <li>Any other value → throws IllegalArgumentException</li>
     * </ul>
     *
     * @param role The role identifier (MASTER or WORKER)
     * @return The appropriate Llm client for the role
     * @throws IllegalArgumentException if role is not MASTER or WORKER
     */
    public LlmClient routeByRole(String role) {
        if ("MASTER".equalsIgnoreCase(role)) {
            return masterClient;
        } else if ("WORKER".equalsIgnoreCase(role)) {
            return workerClient;
        } else {
            throw new IllegalArgumentException("Unknown role: " + role + ". Valid roles are: MASTER, WORKER");
        }
    }

    /**
     * Get the LLM client for summarization tasks.
     *
     * <p>Consistently returns the worker client for all summarization needs,
     * as worker models are typically optimized for processing and summarizing.</p>
     *
     * @return The worker Llm client
     */
    public LlmClient getSummarizer() {
        return workerClient;
    }

    /**
     * Get the master LLM client.
     *
     * @return The master LlmClient
     */
    public LlmClient getMasterClient() {
        return masterClient;
    }

    /**
     * Get the worker LLM client.
     *
     * @return The worker LlmClient
     */
    public LlmClient getWorkerClient() {
        return workerClient;
    }
}
