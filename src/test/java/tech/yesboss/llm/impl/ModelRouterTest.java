package tech.yesboss.llm.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.llm.LlmClient;
import tech.yesboss.tool.AgentTool;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ModelRouter dynamic allocation logic.
 *
 * <p>Verifies that ModelRouter correctly routes requests to master or worker
 * LlmClient instances based on role, and provides consistent access to the summarizer.</p>
 */
@DisplayName("ModelRouter Tests")
class ModelRouterTest {

    @Test
    @DisplayName("Constructor should initialize with master and worker clients")
    void testConstructorInitialization() {
        // Arrange
        LlmClient masterClient = createMockLlmClient("master");
        LlmClient workerClient = createMockLlmClient("worker");

        // Act
        ModelRouter router = new ModelRouter(masterClient, workerClient);

        // Assert
        assertNotNull(router.getMasterClient(), "Master client should not be null");
        assertNotNull(router.getWorkerClient(), "Worker client should not be null");
        assertSame(masterClient, router.getMasterClient(), "Should return same master client instance");
        assertSame(workerClient, router.getWorkerClient(), "Should return same worker client instance");
    }

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException for null master client")
    void testConstructorNullMasterClient() {
        // Arrange
        LlmClient workerClient = createMockLlmClient("worker");

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new ModelRouter(null, workerClient);
        }, "Should throw IllegalArgumentException for null master client");
    }

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException for null worker client")
    void testConstructorNullWorkerClient() {
        // Arrange
        LlmClient masterClient = createMockLlmClient("master");

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new ModelRouter(masterClient, null);
        }, "Should throw IllegalArgumentException for null worker client");
    }

    @Test
    @DisplayName("routeByRole with MASTER should return master client")
    void testRouteByRoleReturnsMaster() {
        // Arrange
        LlmClient masterClient = createMockLlmClient("master");
        LlmClient workerClient = createMockLlmClient("worker");
        ModelRouter router = new ModelRouter(masterClient, workerClient);

        // Act
        LlmClient result = router.routeByRole("MASTER");

        // Assert
        assertSame(masterClient, result, "Should return master client for MASTER role");
    }

    @Test
    @DisplayName("routeByRole with WORKER should return worker client")
    void testRouteByRoleReturnsWorker() {
        // Arrange
        LlmClient masterClient = createMockLlmClient("master");
        LlmClient workerClient = createMockLlmClient("worker");
        ModelRouter router = new ModelRouter(masterClient, workerClient);

        // Act
        LlmClient result = router.routeByRole("WORKER");

        // Assert
        assertSame(workerClient, result, "Should return worker client for WORKER role");
    }

    @Test
    @DisplayName("routeByRole with lowercase should work")
    void testRouteByRoleCaseInsensitive() {
        // Arrange
        LlmClient masterClient = createMockLlmClient("master");
        LlmClient workerClient = createMockLlmClient("worker");
        ModelRouter router = new ModelRouter(masterClient, workerClient);

        // Act
        LlmClient result1 = router.routeByRole("master");
        LlmClient result2 = router.routeByRole("Master");
        LlmClient result3 = router.routeByRole("WoRkEr");

        // Assert
        assertSame(masterClient, result1, "Should return master client for lowercase 'master'");
        assertSame(masterClient, result2, "Should return master client for 'Master'");
        assertSame(workerClient, result3, "Should return worker client for mixed case 'WoRkEr'");
    }

    @Test
    @DisplayName("routeByRole with invalid role should throw IllegalArgumentException")
    void testRouteByRoleInvalidRole() {
        // Arrange
        LlmClient masterClient = createMockLlmClient("master");
        LlmClient workerClient = createMockLlmClient("worker");
        ModelRouter router = new ModelRouter(masterClient, workerClient);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> router.routeByRole("INVALID_ROLE"),
                "Should throw IllegalArgumentException for invalid role"
        );

        assertTrue(exception.getMessage().contains("INVALID_ROLE"),
                "Exception message should contain the invalid role name");
        assertTrue(exception.getMessage().contains("MASTER"),
                "Exception message should list valid roles");
    }

    @Test
    @DisplayName("getSummarizer should consistently return worker client")
    void testGetSummarizerReturnsWorker() {
        // Arrange
        LlmClient masterClient = createMockLlmClient("master");
        LlmClient workerClient = createMockLlmClient("worker");
        ModelRouter router = new ModelRouter(masterClient, workerClient);

        // Act
        LlmClient summarizer = router.getSummarizer();

        // Assert
        assertNotNull(summarizer, "Summarizer should not be null");
        assertSame(workerClient, summarizer, "Summarizer should be the worker client");
    }

    @Test
    @DisplayName("getSummarizer should return same instance on multiple calls")
    void testGetSummarizerConsistent() {
        // Arrange
        LlmClient masterClient = createMockLlmClient("master");
        LlmClient workerClient = createMockLlmClient("worker");
        ModelRouter router = new ModelRouter(masterClient, workerClient);

        // Act
        LlmClient result1 = router.getSummarizer();
        LlmClient result2 = router.getSummarizer();
        LlmClient result3 = router.getSummarizer();

        // Assert
        assertSame(result1, result2, "Should return same instance on second call");
        assertSame(result2, result3, "Should return same instance on third call");
    }

    @Test
    @DisplayName("Integration: routeByRole and getSummarizer should work together")
    void testRouteByRoleAndGetSummarizerIntegration() {
        // Arrange
        LlmClient masterClient = createMockLlmClient("master");
        LlmClient workerClient = createMockLlmClient("worker");
        ModelRouter router = new ModelRouter(masterClient, workerClient);

        // Act
        LlmClient routedWorker = router.routeByRole("WORKER");
        LlmClient summarizer = router.getSummarizer();

        // Assert - Both should return the same worker client instance
        assertSame(workerClient, routedWorker, "Routed WORKER should be worker client");
        assertSame(workerClient, summarizer, "Summarizer should be worker client");
        assertSame(routedWorker, summarizer, "Both methods should return same instance");
    }

    // ==========================================
    // Private Helper Methods
    // ==========================================

    /**
     * Create a mock LlmClient for testing.
     *
     * @param name Identifier for the mock client (for debugging)
     * @return A mock LlmClient that returns predefined responses
     */
    private LlmClient createMockLlmClient(String name) {
        return new LlmClient() {
            @Override
            public UnifiedMessage chat(List<UnifiedMessage> messages, String systemPrompt) {
                return chat(messages, systemPrompt, null);
            }

            @Override
            public UnifiedMessage chat(List<UnifiedMessage> messages, String systemPrompt, List<AgentTool> tools) {
                // Return a simple response indicating which client was used
                return UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "Response from " + name + " client");
            }

            @Override
            public String summarize(String content) {
                // Return a summary indicating which client was used
                return "[Summarized by " + name + "]";
            }
        };
    }
}
