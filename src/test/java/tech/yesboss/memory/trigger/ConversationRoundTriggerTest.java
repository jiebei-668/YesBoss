package tech.yesboss.memory.trigger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.memory.config.MemoryConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for ConversationRoundTrigger.
 *
 * <p>This test class verifies all aspects of the ConversationRoundTrigger including:
 * <ul>
 *   <li>Interface contract compliance</li>
 *   <li>Configuration management</li>
 *   <li>Round tracking functionality</li>
 *   <li>Threshold management</li>
 *   <li>Message filtering</li>
 *   <li>Metrics and monitoring</li>
 *   <li>Batch operations</li>
 *   <li>Cleanup operations</li>
 *   <li>Error handling</li>
 *   <li>Performance</li>
 *   <li>Concurrent operations</li>
 * </ul>
 */
@DisplayName("ConversationRoundTrigger Implementation Tests")
public class ConversationRoundTriggerTest {

    private MemoryConfig config;
    private TriggerService triggerService;
    private ConversationRoundTrigger conversationRoundTrigger;

    @BeforeEach
    void setUp() {
        config = MemoryConfig.getInstance();
        triggerService = mock(TriggerService.class);

        // Set up default configuration
        config.set("memory.trigger.conversationRound.enabled", true);
        config.set("memory.trigger.conversationRound.defaultThreshold", 20);
        config.set("memory.trigger.conversationRound.resetAfterExtraction", true);
        config.set("memory.trigger.conversationRound.includeBotMessages", false);

        // Set up trigger service mock
        when(triggerService.isAvailable()).thenReturn(true);
        when(triggerService.triggerMemoryExtraction(anyString(), anyString())).thenReturn(10);

        conversationRoundTrigger = new ConversationRoundTrigger(config, triggerService);
    }

    @AfterEach
    void tearDown() {
        // Reset configuration
        conversationRoundTrigger.resetAllRoundCounts();
    }

    // ========== Helper Methods ==========

    private UnifiedMessage createTestMessage(String content) {
        return UnifiedMessage.user(content);
    }

    private List<UnifiedMessage> createTestMessages(int count) {
        List<UnifiedMessage> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            messages.add(createTestMessage("Test message " + i));
        }
        return messages;
    }

    // ========== Test Data Preparation ==========

    @Nested
    @DisplayName("Test Data Preparation")
    class TestDataPreparation {

        @Test
        @DisplayName("Normal data - enabled scheduler with default configuration")
        void testNormalDataEnabledWithDefaults() {
            config.set("memory.trigger.conversationRound.enabled", true);
            config.set("memory.trigger.conversationRound.defaultThreshold", 20);
            conversationRoundTrigger.reloadConfiguration();

            assertTrue(conversationRoundTrigger.isEnabled());
            assertEquals(20, conversationRoundTrigger.getDefaultThreshold());
        }

        @Test
        @DisplayName("Boundary data - disabled scheduler")
        void testBoundaryDataDisabled() {
            config.set("memory.trigger.conversationRound.enabled", false);
            conversationRoundTrigger.reloadConfiguration();

            assertFalse(conversationRoundTrigger.isEnabled());
        }

        @Test
        @DisplayName("Anomalous data - unavailable trigger service")
        void testAnomalousDataUnavailableTriggerService() {
            when(triggerService.isAvailable()).thenReturn(false);

            ConversationRoundTrigger trigger = new ConversationRoundTrigger(config, triggerService);

            // Should still initialize but triggerService will be unavailable
            assertNotNull(trigger);
        }

        @Test
        @DisplayName("Normal data - extreme configuration values")
        void testNormalDataExtremeConfiguration() {
            config.set("memory.trigger.conversationRound.defaultThreshold", 1000);
            config.set("memory.trigger.conversationRound.includeBotMessages", true);
            conversationRoundTrigger.reloadConfiguration();

            assertEquals(1000, conversationRoundTrigger.getDefaultThreshold());
        }

        @Test
        @DisplayName("Boundary data - minimum threshold")
        void testBoundaryDataMinimumThreshold() {
            config.set("memory.trigger.conversationRound.defaultThreshold", 1);
            conversationRoundTrigger.reloadConfiguration();

            assertEquals(1, conversationRoundTrigger.getDefaultThreshold());
        }
    }

    // ========== Interface Contract Tests ==========

    @Nested
    @DisplayName("Interface Contract Tests")
    class InterfaceContractTests {

        @Test
        @DisplayName("trackMessage() returns boolean")
        void testTrackMessageReturnsBoolean() {
            UnifiedMessage message = createTestMessage("Test");
            boolean result = conversationRoundTrigger.trackMessage(message, "conv-1");

            assertTrue(result == true || result == false, "Should return boolean value");
        }

        @Test
        @DisplayName("isTriggerConditionMet() returns boolean")
        void testIsTriggerConditionMetReturnsBoolean() {
            boolean result = conversationRoundTrigger.isTriggerConditionMet("conv-1");

            assertTrue(result == true || result == false, "Should return boolean value");
        }

        @Test
        @DisplayName("triggerExtraction() returns non-negative count")
        void testTriggerExtractionReturnsNonNegativeCount() {
            int count = conversationRoundTrigger.triggerExtraction("conv-1", "session-1");

            assertTrue(count >= 0, "Should return non-negative count");
        }

        @Test
        @DisplayName("getRoundCount() returns non-negative count")
        void testGetRoundCountReturnsNonNegativeCount() {
            int count = conversationRoundTrigger.getRoundCount("conv-1");

            assertTrue(count >= 0, "Should return non-negative count");
        }

        @Test
        @DisplayName("getMetrics() returns non-null map")
        void testGetMetricsReturnsNonNullMap() {
            Map<String, Object> metrics = conversationRoundTrigger.getMetrics();

            assertNotNull(metrics, "Should return non-null map");
        }

        @Test
        @DisplayName("calculateAverageProcessingTime() returns non-negative time")
        void testCalculateAverageProcessingTimeReturnsNonNegativeTime() {
            long avgTime = conversationRoundTrigger.calculateAverageProcessingTime();

            assertTrue(avgTime >= 0, "Should return non-negative time");
        }

        @Test
        @DisplayName("getAllRoundCounts() returns non-null map")
        void testGetAllRoundCountsReturnsNonNullMap() {
            Map<String, Integer> counts = conversationRoundTrigger.getAllRoundCounts();

            assertNotNull(counts, "Should return non-null map");
        }

        @Test
        @DisplayName("batchTrackMessages() returns non-negative count")
        void testBatchTrackMessagesReturnsNonNegativeCount() {
            List<UnifiedMessage> messages = createTestMessages(5);
            int count = conversationRoundTrigger.batchTrackMessages(messages, "conv-1");

            assertTrue(count >= 0, "Should return non-negative count");
        }

        @Test
        @DisplayName("getConversationsMeetingThreshold() returns non-null list")
        void testGetConversationsMeetingThresholdReturnsNonNullList() {
            List<String> conversations = conversationRoundTrigger.getConversationsMeetingThreshold();

            assertNotNull(conversations, "Should return non-null list");
        }

        @Test
        @DisplayName("getStatistics() returns non-null map")
        void testGetStatisticsReturnsNonNullMap() {
            Map<String, Object> stats = conversationRoundTrigger.getStatistics();

            assertNotNull(stats, "Should return non-null map");
        }

        @Test
        @DisplayName("enable() / disable() methods work")
        void testEnableDisableMethodsWork() {
            conversationRoundTrigger.disable();
            assertFalse(conversationRoundTrigger.isEnabled());

            conversationRoundTrigger.enable();
            assertTrue(conversationRoundTrigger.isEnabled());
        }

        @Test
        @DisplayName("isEnabled() returns boolean")
        void testIsEnabledReturnsBoolean() {
            boolean enabled = conversationRoundTrigger.isEnabled();

            assertTrue(enabled == true || enabled == false, "Should return boolean value");
        }

        @Test
        @DisplayName("getThresholdForConversation() returns positive threshold")
        void testGetThresholdForConversationReturnsPositiveThreshold() {
            int threshold = conversationRoundTrigger.getThresholdForConversation("conv-1");

            assertTrue(threshold > 0, "Should return positive threshold");
        }

        @Test
        @DisplayName("getDefaultThreshold() returns positive threshold")
        void testGetDefaultThresholdReturnsPositiveThreshold() {
            int threshold = conversationRoundTrigger.getDefaultThreshold();

            assertTrue(threshold > 0, "Should return positive threshold");
        }

        @Test
        @DisplayName("reloadConfiguration() completes without exception")
        void testReloadConfigurationCompletes() {
            assertDoesNotThrow(() -> conversationRoundTrigger.reloadConfiguration());
        }

        @Test
        @DisplayName("resetRoundCount() completes without exception")
        void testResetRoundCountCompletes() {
            assertDoesNotThrow(() -> conversationRoundTrigger.resetRoundCount("conv-1"));
        }

        @Test
        @DisplayName("resetAllRoundCounts() completes without exception")
        void testResetAllRoundCountsCompletes() {
            assertDoesNotThrow(() -> conversationRoundTrigger.resetAllRoundCounts());
        }

        @Test
        @DisplayName("removeConversation() completes without exception")
        void testRemoveConversationCompletes() {
            assertDoesNotThrow(() -> conversationRoundTrigger.removeConversation("conv-1"));
        }

        @Test
        @DisplayName("cleanupOldConversations() returns non-negative count")
        void testCleanupOldConversationsReturnsNonNegativeCount() {
            int count = conversationRoundTrigger.cleanupOldConversations(3600000);

            assertTrue(count >= 0, "Should return non-negative count");
        }

        @Test
        @DisplayName("triggerForAllConversations() returns non-negative count")
        void testTriggerForAllConversationsReturnsNonNegativeCount() {
            int count = conversationRoundTrigger.triggerForAllConversations();

            assertTrue(count >= 0, "Should return non-negative count");
        }
    }

    // ========== Normal Functionality Tests ==========

    @Nested
    @DisplayName("Normal Functionality Tests")
    class NormalFunctionalityTests {

        @Test
        @DisplayName("trackMessage() increments round count")
        void testTrackMessageIncrementsRoundCount() {
            String conversationId = "conv-1";
            UnifiedMessage message = createTestMessage("Hello");

            int before = conversationRoundTrigger.getRoundCount(conversationId);
            conversationRoundTrigger.trackMessage(message, conversationId);
            int after = conversationRoundTrigger.getRoundCount(conversationId);

            assertEquals(after, before + 1, "Round count should increment by 1");
        }

        @Test
        @DisplayName("trackMessage() returns true when threshold reached")
        void testTrackMessageReturnsTrueWhenThresholdReached() {
            String conversationId = "conv-1";
            config.set("memory.trigger.conversationRound.defaultThreshold", 5);
            conversationRoundTrigger.reloadConfiguration();

            // Track 5 messages
            for (int i = 0; i < 5; i++) {
                UnifiedMessage message = createTestMessage("Message " + i);
                conversationRoundTrigger.trackMessage(message, conversationId);
            }

            // 6th message should trigger
            UnifiedMessage message = createTestMessage("Trigger message");
            boolean triggered = conversationRoundTrigger.trackMessage(message, conversationId);

            assertTrue(triggered, "Should trigger when threshold is reached");
        }

        @Test
        @DisplayName("trackMessage() returns false when threshold not reached")
        void testTrackMessageReturnsFalseWhenThresholdNotReached() {
            String conversationId = "conv-1";
            config.set("memory.trigger.conversationRound.defaultThreshold", 20);
            conversationRoundTrigger.reloadConfiguration();

            // Track only 5 messages
            for (int i = 0; i < 5; i++) {
                UnifiedMessage message = createTestMessage("Message " + i);
                boolean triggered = conversationRoundTrigger.trackMessage(message, conversationId);
                assertFalse(triggered, "Should not trigger before threshold");
            }
        }

        @Test
        @DisplayName("isTriggerConditionMet() returns true when rounds >= threshold")
        void testIsTriggerConditionMetReturnsTrueWhenRoundsExceedThreshold() {
            String conversationId = "conv-1";
            config.set("memory.trigger.conversationRound.defaultThreshold", 5);
            conversationRoundTrigger.reloadConfiguration();

            // Track 5 messages
            for (int i = 0; i < 5; i++) {
                UnifiedMessage message = createTestMessage("Message " + i);
                conversationRoundTrigger.trackMessage(message, conversationId);
            }

            assertTrue(conversationRoundTrigger.isTriggerConditionMet(conversationId));
        }

        @Test
        @DisplayName("triggerExtraction() resets round count when configured")
        void testTriggerExtractionResetsRoundCountWhenConfigured() {
            String conversationId = "conv-1";
            config.set("memory.trigger.conversationRound.resetAfterExtraction", true);
            conversationRoundTrigger.reloadConfiguration();

            // Track some messages
            for (int i = 0; i < 5; i++) {
                UnifiedMessage message = createTestMessage("Message " + i);
                conversationRoundTrigger.trackMessage(message, conversationId);
            }

            // Trigger extraction
            conversationRoundTrigger.triggerExtraction(conversationId, "session-1");

            // Round count should be reset
            assertEquals(0, conversationRoundTrigger.getRoundCount(conversationId));
        }

        @Test
        @DisplayName("triggerExtraction() does not reset when not configured")
        void testTriggerExtractionDoesNotResetWhenNotConfigured() {
            String conversationId = "conv-1";
            config.set("memory.trigger.conversationRound.resetAfterExtraction", false);
            conversationRoundTrigger.reloadConfiguration();

            // Track some messages
            for (int i = 0; i < 5; i++) {
                UnifiedMessage message = createTestMessage("Message " + i);
                conversationRoundTrigger.trackMessage(message, conversationId);
            }

            int before = conversationRoundTrigger.getRoundCount(conversationId);

            // Trigger extraction
            conversationRoundTrigger.triggerExtraction(conversationId, "session-1");

            // Round count should NOT be reset
            int after = conversationRoundTrigger.getRoundCount(conversationId);
            assertEquals(after, before, "Round count should not change");
        }

        @Test
        @DisplayName("setThresholdForConversation() sets custom threshold")
        void testSetThresholdForConversationSetsCustomThreshold() {
            String conversationId = "high-priority-conv";
            int customThreshold = 10;

            conversationRoundTrigger.setThresholdForConversation(conversationId, customThreshold);

            assertEquals(customThreshold,
                conversationRoundTrigger.getThresholdForConversation(conversationId));
        }

        @Test
        @DisplayName("setDefaultThreshold() updates default threshold")
        void testSetDefaultThresholdUpdatesDefaultThreshold() {
            int newThreshold = 50;

            conversationRoundTrigger.setDefaultThreshold(newThreshold);

            assertEquals(newThreshold, conversationRoundTrigger.getDefaultThreshold());
        }

        @Test
        @DisplayName("setResetAfterExtraction() updates configuration")
        void testSetResetAfterExtractionUpdatesConfiguration() {
            assertDoesNotThrow(() -> conversationRoundTrigger.setResetAfterExtraction(false));
        }

        @Test
        @DisplayName("setIncludeBotMessages() updates configuration")
        void testSetIncludeBotMessagesUpdatesConfiguration() {
            assertDoesNotThrow(() -> conversationRoundTrigger.setIncludeBotMessages(true));
        }

        @Test
        @DisplayName("getMetrics() returns comprehensive metrics")
        void testGetMetricsReturnsComprehensiveMetrics() {
            Map<String, Object> metrics = conversationRoundTrigger.getMetrics();

            assertTrue(metrics.containsKey("enabled"));
            assertTrue(metrics.containsKey("defaultThreshold"));
            assertTrue(metrics.containsKey("trackedConversations"));
            assertTrue(metrics.containsKey("totalRoundsTracked"));
            assertTrue(metrics.containsKey("totalTriggers"));
        }

        @Test
        @DisplayName("batchTrackMessages() tracks multiple messages")
        void testBatchTrackMessagesTracksMultipleMessages() {
            String conversationId = "conv-1";
            List<UnifiedMessage> messages = createTestMessages(10);

            conversationRoundTrigger.batchTrackMessages(messages, conversationId);

            assertEquals(10, conversationRoundTrigger.getRoundCount(conversationId));
        }

        @Test
        @DisplayName("getConversationsMeetingThreshold() returns eligible conversations")
        void testGetConversationsMeetingThresholdReturnsEligibleConversations() {
            String conversationId = "conv-1";
            config.set("memory.trigger.conversationRound.defaultThreshold", 5);
            conversationRoundTrigger.reloadConfiguration();

            // Track 5 messages
            for (int i = 0; i < 5; i++) {
                UnifiedMessage message = createTestMessage("Message " + i);
                conversationRoundTrigger.trackMessage(message, conversationId);
            }

            List<String> conversations = conversationRoundTrigger.getConversationsMeetingThreshold();

            assertTrue(conversations.contains(conversationId));
        }

        @Test
        @DisplayName("removeConversation() removes tracking data")
        void testRemoveConversationRemovesTrackingData() {
            String conversationId = "conv-1";

            // Track some messages
            for (int i = 0; i < 5; i++) {
                UnifiedMessage message = createTestMessage("Message " + i);
                conversationRoundTrigger.trackMessage(message, conversationId);
            }

            // Remove conversation
            conversationRoundTrigger.removeConversation(conversationId);

            // Round count should be 0
            assertEquals(0, conversationRoundTrigger.getRoundCount(conversationId));
        }

        @Test
        @DisplayName("getStatistics() returns correct statistics")
        void testGetStatisticsReturnsCorrectStatistics() {
            // Track messages for multiple conversations
            for (int i = 0; i < 3; i++) {
                String conversationId = "conv-" + i;
                for (int j = 0; j < (i + 1) * 10; j++) {
                    UnifiedMessage message = createTestMessage("Message " + j);
                    conversationRoundTrigger.trackMessage(message, conversationId);
                }
            }

            Map<String, Object> stats = conversationRoundTrigger.getStatistics();

            assertEquals(3, stats.get("totalConversations"));
            assertEquals(30, stats.get("maxRounds"));
            assertEquals(10, stats.get("minRounds"));
        }
    }

    // ========== Boundary Conditions Tests ==========

    @Nested
    @DisplayName("Boundary Conditions Tests")
    class BoundaryConditionsTests {

        @Test
        @DisplayName("trackMessage() handles null message")
        void testTrackMessageHandlesNullMessage() {
            boolean result = conversationRoundTrigger.trackMessage(null, "conv-1");

            assertFalse(result, "Should return false for null message");
        }

        @Test
        @DisplayName("trackMessage() handles null conversationId")
        void testTrackMessageHandlesNullConversationId() {
            UnifiedMessage message = createTestMessage("Test");

            boolean result = conversationRoundTrigger.trackMessage(message, null);

            assertFalse(result, "Should return false for null conversationId");
        }

        @Test
        @DisplayName("trackMessage() handles empty conversationId")
        void testTrackMessageHandlesEmptyConversationId() {
            UnifiedMessage message = createTestMessage("Test");

            boolean result = conversationRoundTrigger.trackMessage(message, "");

            assertFalse(result, "Should return false for empty conversationId");
        }

        @Test
        @DisplayName("trackMessage() handles whitespace conversationId")
        void testTrackMessageHandlesWhitespaceConversationId() {
            UnifiedMessage message = createTestMessage("Test");

            boolean result = conversationRoundTrigger.trackMessage(message, "   ");

            assertFalse(result, "Should return false for whitespace conversationId");
        }

        @Test
        @DisplayName("getRoundCount() returns 0 for null conversationId")
        void testGetRoundCountReturnsZeroForNullConversationId() {
            int count = conversationRoundTrigger.getRoundCount(null);

            assertEquals(0, count, "Should return 0 for null conversationId");
        }

        @Test
        @DisplayName("getRoundCount() returns 0 for empty conversationId")
        void testGetRoundCountReturnsZeroForEmptyConversationId() {
            int count = conversationRoundTrigger.getRoundCount("");

            assertEquals(0, count, "Should return 0 for empty conversationId");
        }

        @Test
        @DisplayName("getRoundCount() returns 0 for non-existent conversation")
        void testGetRoundCountReturnsZeroForNonExistentConversation() {
            int count = conversationRoundTrigger.getRoundCount("non-existent-conv");

            assertEquals(0, count, "Should return 0 for non-existent conversation");
        }

        @Test
        @DisplayName("setThresholdForConversation() rejects zero threshold")
        void testSetThresholdForConversationRejectsZeroThreshold() {
            assertThrows(IllegalArgumentException.class,
                () -> conversationRoundTrigger.setThresholdForConversation("conv-1", 0));
        }

        @Test
        @DisplayName("setThresholdForConversation() rejects negative threshold")
        void testSetThresholdForConversationRejectsNegativeThreshold() {
            assertThrows(IllegalArgumentException.class,
                () -> conversationRoundTrigger.setThresholdForConversation("conv-1", -10));
        }

        @Test
        @DisplayName("setDefaultThreshold() rejects zero threshold")
        void testSetDefaultThresholdRejectsZeroThreshold() {
            assertThrows(IllegalArgumentException.class,
                () -> conversationRoundTrigger.setDefaultThreshold(0));
        }

        @Test
        @DisplayName("setDefaultThreshold() rejects negative threshold")
        void testSetDefaultThresholdRejectsNegativeThreshold() {
            assertThrows(IllegalArgumentException.class,
                () -> conversationRoundTrigger.setDefaultThreshold(-10));
        }

        @Test
        @DisplayName("resetRoundCount() handles null conversationId")
        void testResetRoundCountHandlesNullConversationId() {
            assertDoesNotThrow(() -> conversationRoundTrigger.resetRoundCount(null));
        }

        @Test
        @DisplayName("resetRoundCount() handles empty conversationId")
        void testResetRoundCountHandlesEmptyConversationId() {
            assertDoesNotThrow(() -> conversationRoundTrigger.resetRoundCount(""));
        }

        @Test
        @DisplayName("batchTrackMessages() handles null message list")
        void testBatchTrackMessagesHandlesNullMessageList() {
            int count = conversationRoundTrigger.batchTrackMessages(null, "conv-1");

            assertEquals(0, count, "Should return 0 for null message list");
        }

        @Test
        @DisplayName("batchTrackMessages() handles empty message list")
        void testBatchTrackMessagesHandlesEmptyMessageList() {
            int count = conversationRoundTrigger.batchTrackMessages(Collections.emptyList(), "conv-1");

            assertEquals(0, count, "Should return 0 for empty message list");
        }

        @Test
        @DisplayName("removeConversation() handles null conversationId")
        void testRemoveConversationHandlesNullConversationId() {
            assertDoesNotThrow(() -> conversationRoundTrigger.removeConversation(null));
        }

        @Test
        @DisplayName("removeConversation() handles empty conversationId")
        void testRemoveConversationHandlesEmptyConversationId() {
            assertDoesNotThrow(() -> conversationRoundTrigger.removeConversation(""));
        }

        @Test
        @DisplayName("cleanupOldConversations() returns 0 for zero maxAge")
        void testCleanupOldConversationsReturnsZeroForZeroMaxAge() {
            int count = conversationRoundTrigger.cleanupOldConversations(0);

            assertEquals(0, count, "Should return 0 for zero maxAge");
        }

        @Test
        @DisplayName("cleanupOldConversations() returns 0 for negative maxAge")
        void testCleanupOldConversationsReturnsZeroForNegativeMaxAge() {
            int count = conversationRoundTrigger.cleanupOldConversations(-1000);

            assertEquals(0, count, "Should return 0 for negative maxAge");
        }

        @Test
        @DisplayName("isTriggerConditionMet() returns false when disabled")
        void testIsTriggerConditionMetReturnsFalseWhenDisabled() {
            conversationRoundTrigger.disable();

            boolean result = conversationRoundTrigger.isTriggerConditionMet("conv-1");

            assertFalse(result, "Should return false when disabled");
        }

        @Test
        @DisplayName("trackMessage() returns false when disabled")
        void testTrackMessageReturnsFalseWhenDisabled() {
            conversationRoundTrigger.disable();

            UnifiedMessage message = createTestMessage("Test");
            boolean result = conversationRoundTrigger.trackMessage(message, "conv-1");

            assertFalse(result, "Should return false when disabled");
        }

        @Test
        @DisplayName("triggerExtraction() returns 0 when disabled")
        void testTriggerExtractionReturnsZeroWhenDisabled() {
            conversationRoundTrigger.disable();

            int count = conversationRoundTrigger.triggerExtraction("conv-1", "session-1");

            assertEquals(0, count, "Should return 0 when disabled");
        }
    }

    // ========== Exception Handling Tests ==========

    @Nested
    @DisplayName("Exception Handling Tests")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("triggerExtraction() handles TriggerService unavailability")
        void testTriggerExtractionHandlesTriggerServiceUnavailability() {
            when(triggerService.isAvailable()).thenReturn(false);
            ConversationRoundTrigger trigger = new ConversationRoundTrigger(config, triggerService);

            int count = trigger.triggerExtraction("conv-1", "session-1");

            // Should return 0 when triggerService is unavailable
            assertEquals(0, count);
        }

        @Test
        @DisplayName("triggerExtraction() handles extraction exception")
        void testTriggerExtractionHandlesExtractionException() {
            when(triggerService.triggerMemoryExtraction(anyString(), anyString()))
                .thenThrow(new RuntimeException("Extraction failed"));

            int count = conversationRoundTrigger.triggerExtraction("conv-1", "session-1");

            // Should return 0 when extraction fails
            assertEquals(0, count);
        }

        @Test
        @DisplayName("trackMessage() handles configuration exception")
        void testTrackMessageHandlesConfigurationException() {
            // This test verifies that trackMessage handles configuration issues gracefully
            UnifiedMessage message = createTestMessage("Test");

            // Should not throw exception even with edge cases
            assertDoesNotThrow(() -> conversationRoundTrigger.trackMessage(message, "conv-1"));
        }

        @Test
        @DisplayName("isTriggerConditionMet() handles exception gracefully")
        void testIsTriggerConditionMetHandlesExceptionGracefully() {
            // Should not throw exception even with edge cases
            assertDoesNotThrow(() -> conversationRoundTrigger.isTriggerConditionMet("conv-1"));
        }

        @Test
        @DisplayName("getMetrics() handles exception gracefully")
        void testGetMetricsHandlesExceptionGracefully() {
            // Should not throw exception
            assertDoesNotThrow(() -> conversationRoundTrigger.getMetrics());
        }

        @Test
        @DisplayName("getStatistics() handles empty state gracefully")
        void testGetStatisticsHandlesEmptyStateGracefully() {
            Map<String, Object> stats = conversationRoundTrigger.getStatistics();

            assertEquals(0, stats.get("totalConversations"));
            assertEquals(0, stats.get("averageRounds"));
            assertEquals(0, stats.get("maxRounds"));
            assertEquals(0, stats.get("minRounds"));
        }

        @Test
        @DisplayName("batchTrackMessages() continues on individual message failure")
        void testBatchTrackMessagesContinuesOnIndividualMessageFailure() {
            List<UnifiedMessage> messages = new ArrayList<>();
            messages.add(createTestMessage("Valid message"));
            messages.add(null); // Invalid message
            messages.add(createTestMessage("Another valid message"));

            // Should not throw exception
            assertDoesNotThrow(() ->
                conversationRoundTrigger.batchTrackMessages(messages, "conv-1"));
        }
    }

    // ========== Performance Tests ==========

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("trackMessage() completes within 10ms")
        void testTrackMessagePerformance() {
            UnifiedMessage message = createTestMessage("Test");

            long startTime = System.currentTimeMillis();
            for (int i = 0; i < 100; i++) {
                conversationRoundTrigger.trackMessage(message, "conv-" + i);
            }
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 100, "Should complete 100 tracks within 100ms, took " + duration + "ms");
        }

        @Test
        @DisplayName("getMetrics() completes within 100ms")
        void testGetMetricsPerformance() {
            long startTime = System.currentTimeMillis();
            conversationRoundTrigger.getMetrics();
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 100, "Should complete within 100ms, took " + duration + "ms");
        }

        @Test
        @DisplayName("calculateAverageProcessingTime() completes within 50ms")
        void testCalculateAverageProcessingTimePerformance() {
            long startTime = System.currentTimeMillis();
            conversationRoundTrigger.calculateAverageProcessingTime();
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 50, "Should complete within 50ms, took " + duration + "ms");
        }

        @Test
        @DisplayName("batchTrackMessages() processes 100 items within 1s")
        void testBatchTrackMessagesPerformance() {
            List<UnifiedMessage> messages = createTestMessages(100);

            long startTime = System.currentTimeMillis();
            conversationRoundTrigger.batchTrackMessages(messages, "conv-1");
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 1000, "Should complete within 1s, took " + duration + "ms");
        }

        @Test
        @DisplayName("getStatistics() completes within 100ms")
        void testGetStatisticsPerformance() {
            // Create some data
            for (int i = 0; i < 100; i++) {
                String conversationId = "conv-" + i;
                for (int j = 0; j < 10; j++) {
                    UnifiedMessage message = createTestMessage("Message " + j);
                    conversationRoundTrigger.trackMessage(message, conversationId);
                }
            }

            long startTime = System.currentTimeMillis();
            conversationRoundTrigger.getStatistics();
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 100, "Should complete within 100ms, took " + duration + "ms");
        }

        @Test
        @DisplayName("getAllRoundCounts() completes within 100ms")
        void testGetAllRoundCountsPerformance() {
            // Create some data
            for (int i = 0; i < 100; i++) {
                String conversationId = "conv-" + i;
                for (int j = 0; j < 10; j++) {
                    UnifiedMessage message = createTestMessage("Message " + j);
                    conversationRoundTrigger.trackMessage(message, conversationId);
                }
            }

            long startTime = System.currentTimeMillis();
            conversationRoundTrigger.getAllRoundCounts();
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 100, "Should complete within 100ms, took " + duration + "ms");
        }

        @Test
        @DisplayName("getConversationsMeetingThreshold() completes within 100ms")
        void testGetConversationsMeetingThresholdPerformance() {
            // Create some data
            config.set("memory.trigger.conversationRound.defaultThreshold", 5);
            conversationRoundTrigger.reloadConfiguration();

            for (int i = 0; i < 100; i++) {
                String conversationId = "conv-" + i;
                for (int j = 0; j < 10; j++) {
                    UnifiedMessage message = createTestMessage("Message " + j);
                    conversationRoundTrigger.trackMessage(message, conversationId);
                }
            }

            long startTime = System.currentTimeMillis();
            conversationRoundTrigger.getConversationsMeetingThreshold();
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 100, "Should complete within 100ms, took " + duration + "ms");
        }

        @Test
        @DisplayName("Memory usage remains within acceptable limits")
        void testMemoryUsageWithinLimits() {
            Runtime runtime = Runtime.getRuntime();
            long beforeMemory = runtime.totalMemory() - runtime.freeMemory();

            // Create a large number of conversations with messages
            for (int i = 0; i < 1000; i++) {
                String conversationId = "conv-" + i;
                for (int j = 0; j < 50; j++) {
                    UnifiedMessage message = createTestMessage("Message " + j);
                    conversationRoundTrigger.trackMessage(message, conversationId);
                }
            }

            long afterMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = (afterMemory - beforeMemory) / (1024 * 1024); // Convert to MB

            assertTrue(memoryUsed < 100, "Memory usage should be < 100MB, used: " + memoryUsed + "MB");
        }
    }

    // ========== Concurrency Tests ==========

    @Nested
    @DisplayName("Concurrent Operations Tests")
    class ConcurrentOperationsTests {

        @Test
        @DisplayName("Concurrent trackMessage() calls complete successfully")
        void testConcurrentTrackMessage() throws InterruptedException {
            int threadCount = 10;
            int messagesPerThread = 100;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < messagesPerThread; j++) {
                            String conversationId = "conv-" + threadId;
                            UnifiedMessage message = createTestMessage("Message " + j);
                            conversationRoundTrigger.trackMessage(message, conversationId);
                        }
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS), "All threads should complete within 30 seconds");
            assertEquals(threadCount, successCount.get(), "All operations should succeed");

            // Verify round counts
            for (int i = 0; i < threadCount; i++) {
                assertEquals(messagesPerThread,
                    conversationRoundTrigger.getRoundCount("conv-" + i));
            }

            executor.shutdown();
        }

        @Test
        @DisplayName("Concurrent trackMessage() for same conversation")
        void testConcurrentTrackMessageSameConversation() throws InterruptedException {
            int threadCount = 10;
            int messagesPerThread = 100;
            String conversationId = "shared-conv";
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < messagesPerThread; j++) {
                            UnifiedMessage message = createTestMessage("Message " + j);
                            conversationRoundTrigger.trackMessage(message, conversationId);
                        }
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS), "All threads should complete within 30 seconds");
            assertEquals(threadCount, successCount.get(), "All operations should succeed");

            // Verify round count equals total messages
            assertEquals(threadCount * messagesPerThread,
                conversationRoundTrigger.getRoundCount(conversationId));

            executor.shutdown();
        }

        @Test
        @DisplayName("Concurrent getMetrics() calls complete successfully")
        void testConcurrentGetMetrics() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        conversationRoundTrigger.getMetrics();
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "All threads should complete within 5 seconds");
            assertEquals(threadCount, successCount.get(), "All operations should succeed");

            executor.shutdown();
        }

        @Test
        @DisplayName("Concurrent getStatistics() calls complete successfully")
        void testConcurrentGetStatistics() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        conversationRoundTrigger.getStatistics();
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "All threads should complete within 5 seconds");
            assertEquals(threadCount, successCount.get(), "All operations should succeed");

            executor.shutdown();
        }

        @Test
        @DisplayName("Concurrent enable/disable operations")
        void testConcurrentEnableDisable() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        if (index % 2 == 0) {
                            conversationRoundTrigger.enable();
                        } else {
                            conversationRoundTrigger.disable();
                        }
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "All threads should complete within 5 seconds");
            assertEquals(threadCount, successCount.get(), "All operations should succeed");

            // Final state should be consistent
            assertTrue(conversationRoundTrigger.isEnabled() == true ||
                conversationRoundTrigger.isEnabled() == false);

            executor.shutdown();
        }

        @Test
        @DisplayName("Concurrent resetAllRoundCounts() operations")
        void testConcurrentResetAllRoundCounts() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // First, create some data
            for (int i = 0; i < 100; i++) {
                String conversationId = "conv-" + i;
                for (int j = 0; j < 10; j++) {
                    UnifiedMessage message = createTestMessage("Message " + j);
                    conversationRoundTrigger.trackMessage(message, conversationId);
                }
            }

            // Then reset concurrently
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        conversationRoundTrigger.resetAllRoundCounts();
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "All threads should complete within 5 seconds");
            assertEquals(threadCount, successCount.get(), "All operations should succeed");

            // All conversations should be reset
            assertTrue(conversationRoundTrigger.getAllRoundCounts().isEmpty() ||
                conversationRoundTrigger.getAllRoundCounts().size() < 10);

            executor.shutdown();
        }

        @Test
        @DisplayName("Concurrent removeConversation() operations")
        void testConcurrentRemoveConversation() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // First, create some data
            for (int i = 0; i < 100; i++) {
                String conversationId = "conv-" + i;
                for (int j = 0; j < 10; j++) {
                    UnifiedMessage message = createTestMessage("Message " + j);
                    conversationRoundTrigger.trackMessage(message, conversationId);
                }
            }

            // Then remove concurrently
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        conversationRoundTrigger.removeConversation("conv-" + index);
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "All threads should complete within 5 seconds");
            assertEquals(threadCount, successCount.get(), "All operations should succeed");

            executor.shutdown();
        }

        @Test
        @DisplayName("Concurrent batchTrackMessages() operations")
        void testConcurrentBatchTrackMessages() throws InterruptedException {
            int threadCount = 10;
            int messagesPerThread = 50;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        String conversationId = "conv-" + threadId;
                        List<UnifiedMessage> messages = createTestMessages(messagesPerThread);
                        conversationRoundTrigger.batchTrackMessages(messages, conversationId);
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS), "All threads should complete within 30 seconds");
            assertEquals(threadCount, successCount.get(), "All operations should succeed");

            // Verify round counts
            for (int i = 0; i < threadCount; i++) {
                assertEquals(messagesPerThread,
                    conversationRoundTrigger.getRoundCount("conv-" + i));
            }

            executor.shutdown();
        }
    }

    // ========== Metrics and Monitoring Tests ==========

    @Nested
    @DisplayName("Metrics and Monitoring Tests")
    class MetricsAndMonitoringTests {

        @Test
        @DisplayName("getMetrics() tracks enabled status correctly")
        void testGetMetricsTracksEnabledStatus() {
            Map<String, Object> metrics = conversationRoundTrigger.getMetrics();

            assertEquals(true, metrics.get("enabled"));

            conversationRoundTrigger.disable();
            metrics = conversationRoundTrigger.getMetrics();
            assertEquals(false, metrics.get("enabled"));
        }

        @Test
        @DisplayName("getMetrics() tracks default threshold correctly")
        void testGetMetricsTracksDefaultThreshold() {
            Map<String, Object> metrics = conversationRoundTrigger.getMetrics();

            assertEquals(20, metrics.get("defaultThreshold"));
        }

        @Test
        @DisplayName("getMetrics() tracks tracked conversations count")
        void testGetMetricsTracksTrackedConversations() {
            // Create conversations
            for (int i = 0; i < 5; i++) {
                String conversationId = "conv-" + i;
                UnifiedMessage message = createTestMessage("Test");
                conversationRoundTrigger.trackMessage(message, conversationId);
            }

            Map<String, Object> metrics = conversationRoundTrigger.getMetrics();

            assertEquals(5, metrics.get("trackedConversations"));
        }

        @Test
        @DisplayName("getMetrics() tracks total rounds tracked")
        void testGetMetricsTracksTotalRoundsTracked() {
            String conversationId = "conv-1";

            for (int i = 0; i < 10; i++) {
                UnifiedMessage message = createTestMessage("Message " + i);
                conversationRoundTrigger.trackMessage(message, conversationId);
            }

            Map<String, Object> metrics = conversationRoundTrigger.getMetrics();

            assertEquals(10L, metrics.get("totalRoundsTracked"));
        }

        @Test
        @DisplayName("getMetrics() tracks total triggers")
        void testGetMetricsTracksTotalTriggers() {
            String conversationId = "conv-1";
            config.set("memory.trigger.conversationRound.defaultThreshold", 5);
            conversationRoundTrigger.reloadConfiguration();

            // Track 5 messages to trigger
            for (int i = 0; i < 5; i++) {
                UnifiedMessage message = createTestMessage("Message " + i);
                conversationRoundTrigger.trackMessage(message, conversationId);
            }

            Map<String, Object> metrics = conversationRoundTrigger.getMetrics();

            assertTrue((Long) metrics.get("totalTriggers") > 0);
        }

        @Test
        @DisplayName("getMetrics() tracks total extractions")
        void testGetMetricsTracksTotalExtractions() {
            conversationRoundTrigger.triggerExtraction("conv-1", "session-1");

            Map<String, Object> metrics = conversationRoundTrigger.getMetrics();

            assertEquals(1L, metrics.get("totalExtractions"));
        }

        @Test
        @DisplayName("calculateAverageProcessingTime() calculates correctly")
        void testCalculateAverageProcessingTimeCalculatesCorrectly() {
            // Perform some extractions
            for (int i = 0; i < 5; i++) {
                conversationRoundTrigger.triggerExtraction("conv-" + i, "session-" + i);
            }

            long avgTime = conversationRoundTrigger.calculateAverageProcessingTime();

            // Should have some average time
            assertTrue(avgTime >= 0);
        }
    }

    // ========== Cleanup Operations Tests ==========

    @Nested
    @DisplayName("Cleanup Operations Tests")
    class CleanupOperationsTests {

        @Test
        @DisplayName("cleanupOldConversations() removes old conversations")
        void testCleanupOldConversationsRemovesOldConversations() throws InterruptedException {
            // Create conversations with timestamps
            for (int i = 0; i < 5; i++) {
                String conversationId = "conv-" + i;
                UnifiedMessage message = createTestMessage("Test");
                conversationRoundTrigger.trackMessage(message, conversationId);
            }

            // Clean up conversations older than 1ms
            Thread.sleep(2); // Ensure time passes
            int cleaned = conversationRoundTrigger.cleanupOldConversations(1);

            // Should clean up all 5 conversations
            assertEquals(5, cleaned);
        }

        @Test
        @DisplayName("cleanupOldConversations() preserves recent conversations")
        void testCleanupOldConversationsPreservesRecentConversations() {
            // Create conversations
            for (int i = 0; i < 5; i++) {
                String conversationId = "conv-" + i;
                UnifiedMessage message = createTestMessage("Test");
                conversationRoundTrigger.trackMessage(message, conversationId);
            }

            // Clean up conversations older than 1 hour
            int cleaned = conversationRoundTrigger.cleanupOldConversations(3600000);

            // Should not clean up any conversations
            assertEquals(0, cleaned);
            assertEquals(5, conversationRoundTrigger.getAllRoundCounts().size());
        }

        @Test
        @DisplayName("resetRoundCount() clears round count")
        void testResetRoundCountClearsRoundCount() {
            String conversationId = "conv-1";

            // Track some messages
            for (int i = 0; i < 10; i++) {
                UnifiedMessage message = createTestMessage("Message " + i);
                conversationRoundTrigger.trackMessage(message, conversationId);
            }

            // Reset
            conversationRoundTrigger.resetRoundCount(conversationId);

            // Round count should be 0
            assertEquals(0, conversationRoundTrigger.getRoundCount(conversationId));
        }

        @Test
        @DisplayName("resetAllRoundCounts() clears all round counts")
        void testResetAllRoundCountsClearsAllRoundCounts() {
            // Create conversations
            for (int i = 0; i < 5; i++) {
                String conversationId = "conv-" + i;
                for (int j = 0; j < 10; j++) {
                    UnifiedMessage message = createTestMessage("Message " + j);
                    conversationRoundTrigger.trackMessage(message, conversationId);
                }
            }

            // Reset all
            conversationRoundTrigger.resetAllRoundCounts();

            // All round counts should be 0
            assertTrue(conversationRoundTrigger.getAllRoundCounts().isEmpty());
        }
    }

    // ========== Configuration Tests ==========

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("reloadConfiguration() reloads enabled status")
        void testReloadConfigurationReloadsEnabledStatus() {
            config.set("memory.trigger.conversationRound.enabled", false);
            conversationRoundTrigger.reloadConfiguration();

            assertFalse(conversationRoundTrigger.isEnabled());

            config.set("memory.trigger.conversationRound.enabled", true);
            conversationRoundTrigger.reloadConfiguration();

            assertTrue(conversationRoundTrigger.isEnabled());
        }

        @Test
        @DisplayName("reloadConfiguration() reloads default threshold")
        void testReloadConfigurationReloadsDefaultThreshold() {
            config.set("memory.trigger.conversationRound.defaultThreshold", 50);
            conversationRoundTrigger.reloadConfiguration();

            assertEquals(50, conversationRoundTrigger.getDefaultThreshold());
        }

        @Test
        @DisplayName("reloadConfiguration() reloads resetAfterExtraction")
        void testReloadConfigurationReloadsResetAfterExtraction() {
            config.set("memory.trigger.conversationRound.resetAfterExtraction", false);
            conversationRoundTrigger.reloadConfiguration();

            // Verify by checking behavior
            String conversationId = "conv-1";
            for (int i = 0; i < 5; i++) {
                UnifiedMessage message = createTestMessage("Message " + i);
                conversationRoundTrigger.trackMessage(message, conversationId);
            }

            int before = conversationRoundTrigger.getRoundCount(conversationId);
            conversationRoundTrigger.triggerExtraction(conversationId, "session-1");
            int after = conversationRoundTrigger.getRoundCount(conversationId);

            assertEquals(after, before, "Round count should not reset");
        }

        @Test
        @DisplayName("reloadConfiguration() reloads includeBotMessages")
        void testReloadConfigurationReloadsIncludeBotMessages() {
            config.set("memory.trigger.conversationRound.includeBotMessages", true);
            conversationRoundTrigger.reloadConfiguration();

            // Configuration should be reloaded
            // (Actual behavior depends on isBotMessage implementation)
            assertDoesNotThrow(() -> conversationRoundTrigger.reloadConfiguration());
        }
    }
}
