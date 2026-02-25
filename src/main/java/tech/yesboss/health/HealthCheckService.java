package tech.yesboss.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.persistence.db.SingleThreadDbWriter;
import tech.yesboss.llm.LlmClient;
import tech.yesboss.state.TaskManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Health Check Service
 *
 * <p>Provides comprehensive health status checking for all critical application components.</p>
 *
 * <p><b>Components Checked:</b></p>
 * <ul>
 *   <li>Database connectivity</li>
 *   <li>SingleThreadDbWriter consumer thread status</li>
 *   <li>LLM client availability</li>
 *   <li>WebhookEventExecutor status</li>
 *   <li>TaskManager state</li>
 * </ul>
 *
 * <p><b>Health Status:</b></p>
 * <ul>
 *   <li>UP: Component is healthy and functioning</li>
 *   <li>DOWN: Component is not responding or failed</li>
 *   <li>DEGRADED: Component is partially functional</li>
 * </ul>
 */
public class HealthCheckService {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckService.class);

    private final Connection databaseConnection;
    private final SingleThreadDbWriter dbWriter;
    private final LlmClient masterLlmClient;
    private final LlmClient workerLlmClient;
    private final TaskManager taskManager;
    private final ExecutorService webhookExecutor;

    /**
     * Create a new HealthCheckService.
     *
     * @param databaseConnection The database connection to check
     * @param dbWriter           The database writer to check
     * @param masterLlmClient    The master LLM client to check
     * @param workerLlmClient    The worker LLM client to check
     * @param taskManager        The task manager to check
     * @param webhookExecutor    The webhook executor to check
     */
    public HealthCheckService(
            Connection databaseConnection,
            SingleThreadDbWriter dbWriter,
            LlmClient masterLlmClient,
            LlmClient workerLlmClient,
            TaskManager taskManager,
            ExecutorService webhookExecutor
    ) {
        this.databaseConnection = databaseConnection;
        this.dbWriter = dbWriter;
        this.masterLlmClient = masterLlmClient;
        this.workerLlmClient = workerLlmClient;
        this.taskManager = taskManager;
        this.webhookExecutor = webhookExecutor;
    }

    /**
     * Check the health of all critical components.
     *
     * @return A map of component names to their health status
     */
    public Map<String, HealthStatus> checkHealth() {
        Map<String, HealthStatus> healthStatusMap = new HashMap<>();

        // Check database connectivity
        healthStatusMap.put("database", checkDatabase());

        // Check database writer
        healthStatusMap.put("databaseWriter", checkDatabaseWriter());

        // Check LLM clients
        healthStatusMap.put("masterLlmClient", checkMasterLlmClient());
        healthStatusMap.put("workerLlmClient", checkWorkerLlmClient());

        // Check webhook executor
        healthStatusMap.put("webhookExecutor", checkWebhookExecutor());

        // Check task manager
        healthStatusMap.put("taskManager", checkTaskManager());

        return healthStatusMap;
    }

    /**
     * Check if all critical components are healthy.
     *
     * @return true if all components are UP, false otherwise
     */
    public boolean isHealthy() {
        Map<String, HealthStatus> healthStatusMap = checkHealth();
        return healthStatusMap.values().stream()
                .allMatch(status -> status == HealthStatus.UP);
    }

    /**
     * Get the overall health status with details.
     *
     * @return A map containing the overall status and component details
     */
    public Map<String, Object> getHealthDetails() {
        Map<String, Object> details = new HashMap<>();
        Map<String, HealthStatus> healthStatusMap = checkHealth();

        boolean allHealthy = healthStatusMap.values().stream()
                .allMatch(status -> status == HealthStatus.UP);

        details.put("status", allHealthy ? "UP" : "DEGRADED");
        details.put("components", healthStatusMap);

        return details;
    }

    // ==================== Individual Component Checks ====================

    private HealthStatus checkDatabase() {
        try {
            if (databaseConnection == null || databaseConnection.isClosed()) {
                logger.warn("Database health check failed: connection is null or closed");
                return HealthStatus.DOWN;
            }

            // Simple validation query
            databaseConnection.isValid(5);
            return HealthStatus.UP;

        } catch (SQLException e) {
            logger.warn("Database health check failed: {}", e.getMessage());
            return HealthStatus.DOWN;
        }
    }

    private HealthStatus checkDatabaseWriter() {
        try {
            if (dbWriter == null || !dbWriter.isRunning()) {
                logger.warn("Database writer health check failed: writer is null or not running");
                return HealthStatus.DOWN;
            }
            return HealthStatus.UP;

        } catch (Exception e) {
            logger.warn("Database writer health check failed: {}", e.getMessage());
            return HealthStatus.DOWN;
        }
    }

    private HealthStatus checkMasterLlmClient() {
        if (masterLlmClient == null) {
            logger.warn("Master LLM client health check failed: client is null");
            return HealthStatus.DOWN;
        }
        return HealthStatus.UP;
    }

    private HealthStatus checkWorkerLlmClient() {
        if (workerLlmClient == null) {
            logger.warn("Worker LLM client health check failed: client is null");
            return HealthStatus.DOWN;
        }
        return HealthStatus.UP;
    }

    private HealthStatus checkWebhookExecutor() {
        if (webhookExecutor == null || webhookExecutor.isShutdown()) {
            logger.warn("Webhook executor health check failed: executor is null or shutdown");
            return HealthStatus.DOWN;
        }
        return HealthStatus.UP;
    }

    private HealthStatus checkTaskManager() {
        if (taskManager == null) {
            logger.warn("Task manager health check failed: manager is null");
            return HealthStatus.DOWN;
        }
        return HealthStatus.UP;
    }

    /**
     * Health status enumeration.
     */
    public enum HealthStatus {
        UP,
        DOWN,
        DEGRADED
    }
}
