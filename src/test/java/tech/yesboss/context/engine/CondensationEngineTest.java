package tech.yesboss.context.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.yesboss.context.GlobalStreamManager;
import tech.yesboss.context.LocalStreamManager;
import tech.yesboss.context.engine.impl.CondensationEngineImpl;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.llm.LlmClient;
import tech.yesboss.llm.impl.ModelRouter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for CondensationEngine token threshold and summarization routing.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>checkAndCompactIfNeeded returns true when token count exceeds limit</li>
 *   <li>condenseAndMergeUpwards fetches local context, summarizes it, and appends to global context</li>
 *   <li>Token threshold management works correctly</li>
 *   <li>Parameter validation is enforced</li>
 * </ul>
 */
@DisplayName("CondensationEngine Tests")
class CondensationEngineTest {

    private LocalStreamManager mockLocalStreamManager;
    private GlobalStreamManager mockGlobalStreamManager;
    private ModelRouter mockModelRouter;
    private LlmClient mockLlmClient;
    private CondensationEngine condensationEngine;

    @BeforeEach
    void setUp() {
        mockLocalStreamManager = mock(LocalStreamManager.class);
        mockGlobalStreamManager = mock(GlobalStreamManager.class);
        mockModelRouter = mock(ModelRouter.class);
        mockLlmClient = mock(LlmClient.class);

        when(mockModelRouter.getSummarizer()).thenReturn(mockLlmClient);

        condensationEngine = new CondensationEngineImpl(
                mockLocalStreamManager,
                mockGlobalStreamManager,
                mockModelRouter
        );
    }

    @Test
    @DisplayName("Constructor should throw exception for null LocalStreamManager")
    void testConstructorWithNullLocalStreamManager() {
        assertThrows(IllegalArgumentException.class,
                () -> new CondensationEngineImpl(null, mockGlobalStreamManager, mockModelRouter),
                "Should throw exception for null LocalStreamManager");
    }

    @Test
    @DisplayName("Constructor should throw exception for null GlobalStreamManager")
    void testConstructorWithNullGlobalStreamManager() {
        assertThrows(IllegalArgumentException.class,
                () -> new CondensationEngineImpl(mockLocalStreamManager, null, mockModelRouter),
                "Should throw exception for null GlobalStreamManager");
    }

    @Test
    @DisplayName("Constructor should throw exception for null ModelRouter")
    void testConstructorWithNullModelRouter() {
        assertThrows(IllegalArgumentException.class,
                () -> new CondensationEngineImpl(mockLocalStreamManager, mockGlobalStreamManager, null),
                "Should throw exception for null ModelRouter");
    }

    @Test
    @DisplayName("checkAndCompactIfNeeded should return true when token count exceeds threshold")
    void testCheckAndCompactIfNeededExceedsThreshold() {
        // Arrange
        String workerSessionId = "worker_001";
        when(mockLocalStreamManager.getCurrentTokenCount(workerSessionId))
                .thenReturn(15000); // Above default threshold of 12000

        // Act
        boolean needsCompaction = condensationEngine.checkAndCompactIfNeeded(workerSessionId);

        // Assert
        assertTrue(needsCompaction, "Should return true when token count exceeds threshold");
    }

    @Test
    @DisplayName("checkAndCompactIfNeeded should return false when token count is within threshold")
    void testCheckAndCompactIfNeededWithinThreshold() {
        // Arrange
        String workerSessionId = "worker_002";
        when(mockLocalStreamManager.getCurrentTokenCount(workerSessionId))
                .thenReturn(8000); // Below default threshold of 12000

        // Act
        boolean needsCompaction = condensationEngine.checkAndCompactIfNeeded(workerSessionId);

        // Assert
        assertFalse(needsCompaction, "Should return false when token count is within threshold");
    }

    @Test
    @DisplayName("checkAndCompactIfNeeded should return false when token count equals threshold")
    void testCheckAndCompactIfNeededEqualsThreshold() {
        // Arrange
        String workerSessionId = "worker_003";
        when(mockLocalStreamManager.getCurrentTokenCount(workerSessionId))
                .thenReturn(12000); // Exactly at threshold

        // Act
        boolean needsCompaction = condensationEngine.checkAndCompactIfNeeded(workerSessionId);

        // Assert
        assertFalse(needsCompaction, "Should return false when token count equals threshold");
    }

    @Test
    @DisplayName("checkAndCompactIfNeeded should throw exception for null session ID")
    void testCheckAndCompactIfNeededWithNullSessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> condensationEngine.checkAndCompactIfNeeded(null),
                "Should throw exception for null session ID");
    }

    @Test
    @DisplayName("checkAndCompactIfNeeded should throw exception for empty session ID")
    void testCheckAndCompactIfNeededWithEmptySessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> condensationEngine.checkAndCompactIfNeeded(""),
                "Should throw exception for empty session ID");
    }

    @Test
    @DisplayName("condenseAndMergeUpwards should fetch local context and summarize it")
    void testCondenseAndMergeUpwardsSummarizesContext() {
        // Arrange
        String workerSessionId = "worker_summarize";
        String masterSessionId = "master_001";
        String summary = "Worker completed the task successfully.";

        List<UnifiedMessage> localContext = List.of(
                UnifiedMessage.user("Initial task"),
                UnifiedMessage.ofText(UnifiedMessage.Role.ASSISTANT, "Working on it...")
        );

        when(mockLocalStreamManager.fetchContext(workerSessionId)).thenReturn(localContext);
        when(mockLlmClient.summarize(any())).thenReturn(summary);

        // Act
        String result = condensationEngine.condenseAndMergeUpwards(workerSessionId, masterSessionId);

        // Assert
        assertEquals(summary, result);
        verify(mockLocalStreamManager).fetchContext(workerSessionId);
        verify(mockLlmClient).summarize(any());
        verify(mockGlobalStreamManager).appendMasterMessage(eq(masterSessionId), any());
    }

    @Test
    @DisplayName("condenseAndMergeUpwards should handle empty local context")
    void testCondenseAndMergeUpwardsWithEmptyContext() {
        // Arrange
        String workerSessionId = "worker_empty";
        String masterSessionId = "master_002";

        when(mockLocalStreamManager.fetchContext(workerSessionId)).thenReturn(List.of());

        // Act
        String result = condensationEngine.condenseAndMergeUpwards(workerSessionId, masterSessionId);

        // Assert
        assertEquals("No execution history to summarize.", result);
        verify(mockLocalStreamManager).fetchContext(workerSessionId);
        verify(mockLlmClient, never()).summarize(any());
        verify(mockGlobalStreamManager, never()).appendMasterMessage(any(), any());
    }

    @Test
    @DisplayName("condenseAndMergeUpwards should throw exception for null worker session ID")
    void testCondenseAndMergeUpwardsWithNullWorkerSessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> condensationEngine.condenseAndMergeUpwards(null, "master"),
                "Should throw exception for null worker session ID");
    }

    @Test
    @DisplayName("condenseAndMergeUpwards should throw exception for empty worker session ID")
    void testCondenseAndMergeUpwardsWithEmptyWorkerSessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> condensationEngine.condenseAndMergeUpwards("", "master"),
                "Should throw exception for empty worker session ID");
    }

    @Test
    @DisplayName("condenseAndMergeUpwards should throw exception for null master session ID")
    void testCondenseAndMergeUpwardsWithNullMasterSessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> condensationEngine.condenseAndMergeUpwards("worker", null),
                "Should throw exception for null master session ID");
    }

    @Test
    @DisplayName("condenseAndMergeUpwards should throw exception for empty master session ID")
    void testCondenseAndMergeUpwardsWithEmptyMasterSessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> condensationEngine.condenseAndMergeUpwards("worker", ""),
                "Should throw exception for empty master session ID");
    }

    @Test
    @DisplayName("getTokenThreshold should return default threshold")
    void testGetTokenThreshold() {
        // Act
        int threshold = condensationEngine.getTokenThreshold();

        // Assert
        assertEquals(12000, threshold, "Default threshold should be 12000");
    }

    @Test
    @DisplayName("setTokenThreshold should update the threshold")
    void testSetTokenThreshold() {
        // Act
        condensationEngine.setTokenThreshold(8000);

        // Assert
        assertEquals(8000, condensationEngine.getTokenThreshold());
    }

    @Test
    @DisplayName("setTokenThreshold should throw exception for non-positive threshold")
    void testSetTokenThresholdWithNonPositiveValue() {
        assertThrows(IllegalArgumentException.class,
                () -> condensationEngine.setTokenThreshold(0),
                "Should throw exception for zero threshold");
        assertThrows(IllegalArgumentException.class,
                () -> condensationEngine.setTokenThreshold(-100),
                "Should throw exception for negative threshold");
    }

    @Test
    @DisplayName("condenseAndMergeUpwards should use custom threshold after setting")
    void testCustomThresholdUsedInCheck() {
        // Arrange
        condensationEngine.setTokenThreshold(5000);
        String workerSessionId = "worker_custom";
        when(mockLocalStreamManager.getCurrentTokenCount(workerSessionId))
                .thenReturn(6000);

        // Act
        boolean needsCompaction = condensationEngine.checkAndCompactIfNeeded(workerSessionId);

        // Assert
        assertTrue(needsCompaction, "Should use custom threshold");
    }

    @Test
    @DisplayName("condenseAndMergeUpwards should append formatted report to global stream")
    void testCondenseAndMergeUpwardsAppendsFormattedReport() {
        // Arrange
        String workerSessionId = "worker_format";
        String masterSessionId = "master_format";
        String summary = "Task completed successfully";

        List<UnifiedMessage> localContext = List.of(
                UnifiedMessage.user("Do something")
        );

        when(mockLocalStreamManager.fetchContext(workerSessionId)).thenReturn(localContext);
        when(mockLlmClient.summarize(any())).thenReturn(summary);

        // Act
        condensationEngine.condenseAndMergeUpwards(workerSessionId, masterSessionId);

        // Assert
        verify(mockGlobalStreamManager).appendMasterMessage(eq(masterSessionId), argThat(msg ->
                msg.content().contains("Worker Report") &&
                msg.content().contains(workerSessionId) &&
                msg.content().contains(summary)
        ));
    }

    @Test
    @DisplayName("condenseAndMergeUpwards should call ModelRouter.getSummarizer")
    void testCondenseAndMergeUpwardsCallsModelRouter() {
        // Arrange
        String workerSessionId = "worker_router";
        String masterSessionId = "master_router";
        List<UnifiedMessage> localContext = List.of(UnifiedMessage.user("Task"));

        when(mockLocalStreamManager.fetchContext(workerSessionId)).thenReturn(localContext);
        when(mockLlmClient.summarize(any())).thenReturn("Summary");

        // Act
        condensationEngine.condenseAndMergeUpwards(workerSessionId, masterSessionId);

        // Assert
        verify(mockModelRouter).getSummarizer();
    }

    @Test
    @DisplayName("condenseAndMergeUpwards should handle complex local context with tool calls")
    void testCondenseAndMergeUpwardsWithToolCalls() {
        // Arrange
        String workerSessionId = "worker_tools";
        String masterSessionId = "master_tools";
        String summary = "Executed tools and completed";

        List<UnifiedMessage> localContext = List.of(
                UnifiedMessage.user("Run tests"),
                UnifiedMessage.ofToolCalls(List.of(
                        new UnifiedMessage.ToolCall("call_1", "run_tests", "{}")
                )),
                UnifiedMessage.ofToolResult("call_1", "Tests passed", false)
        );

        when(mockLocalStreamManager.fetchContext(workerSessionId)).thenReturn(localContext);
        when(mockLlmClient.summarize(any())).thenReturn(summary);

        // Act
        String result = condensationEngine.condenseAndMergeUpwards(workerSessionId, masterSessionId);

        // Assert
        assertEquals(summary, result);
        verify(mockLlmClient).summarize(argThat(text ->
                text.contains("Tool Calls") &&
                text.contains("run_tests") &&
                text.contains("Tool Results")
        ));
    }
}
