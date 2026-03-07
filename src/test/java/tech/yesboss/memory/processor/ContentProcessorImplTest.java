package tech.yesboss.memory.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.llm.LlmClient;
import tech.yesboss.memory.model.Preference;
import tech.yesboss.memory.model.Snippet;
import tech.yesboss.memory.model.Snippet.MemoryType;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for ContentProcessor implementations.
 *
 * <p>This test class verifies all aspects of the ContentProcessor including:
 * <ul>
 *   <li>Interface contract compliance</li>
 *   <li>Functional correctness</li>
 *   <li>Edge cases and boundary conditions</li>
 *   <li>Error handling and recovery</li>
 *   <li>Performance requirements</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ContentProcessor Implementation Tests")
public class ContentProcessorImplTest {

    @Mock
    private LlmClient mockLlmClient;

    private ContentProcessor contentProcessor;

    @BeforeEach
    void setUp() {
        contentProcessor = new ZhipuContentProcessorImpl(mockLlmClient);
    }

    // ========== Interface Contract Tests ==========

    @Nested
    @DisplayName("Interface Contract Tests")
    class InterfaceContractTests {

        @Test
        @DisplayName("segmentConversation() returns list of segments")
        void testSegmentConversationReturnsList() {
            when(mockLlmClient.chat(any(), anyString()))
                    .thenReturn(new UnifiedMessage(
                            UnifiedMessage.Role.ASSISTANT,
                            buildSegmentationResponse(),
                            UnifiedMessage.PayloadFormat.PLAIN_TEXT));

            List<ConversationSegment> segments = contentProcessor.segmentConversation("test conversation");
            assertNotNull(segments, "Segments should not be null");
            assertFalse(segments.isEmpty(), "Segments should not be empty");
        }

        @Test
        @DisplayName("segmentConversation() throws for null input")
        void testSegmentConversationThrowsForNullInput() {
            assertThrows(ContentProcessingException.class,
                    () -> contentProcessor.segmentConversation(null),
                    "Should throw ContentProcessingException for null input");
        }

        @Test
        @DisplayName("segmentConversation() throws for empty input")
        void testSegmentConversationThrowsForEmptyInput() {
            assertThrows(ContentProcessingException.class,
                    () -> contentProcessor.segmentConversation(""),
                    "Should throw ContentProcessingException for empty input");
        }

        @Test
        @DisplayName("generateSegmentAbstract() returns string")
        void testGenerateSegmentAbstractReturnsString() {
            when(mockLlmClient.chat(any(), anyString()))
                    .thenReturn(new UnifiedMessage(
                            UnifiedMessage.Role.ASSISTANT,
                            "Test abstract",
                            UnifiedMessage.PayloadFormat.PLAIN_TEXT));

            String abstractText = contentProcessor.generateSegmentAbstract("test content");
            assertNotNull(abstractText, "Abstract should not be null");
            assertFalse(abstractText.isEmpty(), "Abstract should not be empty");
        }

        @Test
        @DisplayName("generateSegmentAbstract() throws for null input")
        void testGenerateSegmentAbstractThrowsForNullInput() {
            assertThrows(ContentProcessingException.class,
                    () -> contentProcessor.generateSegmentAbstract(null),
                    "Should throw ContentProcessingException for null input");
        }

        @Test
        @DisplayName("extractStructuredMemories() returns list of strings")
        void testExtractStructuredMemoriesReturnsList() {
            when(mockLlmClient.chat(any(), anyString()))
                    .thenReturn(new UnifiedMessage(
                            UnifiedMessage.Role.ASSISTANT,
                            "[\"memory 1\", \"memory 2\"]",
                            UnifiedMessage.PayloadFormat.PLAIN_TEXT));

            List<String> memories = contentProcessor.extractStructuredMemories("test content", MemoryType.PROFILE);
            assertNotNull(memories, "Memories should not be null");
        }

        @Test
        @DisplayName("extractStructuredMemories() throws for null content")
        void testExtractStructuredMemoriesThrowsForNullContent() {
            assertThrows(ContentProcessingException.class,
                    () -> contentProcessor.extractStructuredMemories(null, MemoryType.PROFILE),
                    "Should throw ContentProcessingException for null content");
        }

        @Test
        @DisplayName("extractStructuredMemories() throws for null memory type")
        void testExtractStructuredMemoriesThrowsForNullMemoryType() {
            assertThrows(ContentProcessingException.class,
                    () -> contentProcessor.extractStructuredMemories("test content", null),
                    "Should throw ContentProcessingException for null memory type");
        }

        @Test
        @DisplayName("identifyPreferencesForSnippet() returns list of IDs")
        void testIdentifyPreferencesForSnippetReturnsList() {
            List<Preference> preferences = createTestPreferences();
            when(mockLlmClient.chat(any(), anyString()))
                    .thenReturn(new UnifiedMessage(
                            UnifiedMessage.Role.ASSISTANT,
                            "[\"pref1\"]",
                            UnifiedMessage.PayloadFormat.PLAIN_TEXT));

            List<String> ids = contentProcessor.identifyPreferencesForSnippet("test summary", preferences);
            assertNotNull(ids, "IDs should not be null");
        }

        @Test
        @DisplayName("identifyPreferencesForSnippet() returns empty list for empty preferences")
        void testIdentifyPreferencesForSnippetReturnsEmptyForNoPreferences() {
            List<String> ids = contentProcessor.identifyPreferencesForSnippet("test summary", List.of());
            assertNotNull(ids, "IDs should not be null");
            assertTrue(ids.isEmpty(), "IDs should be empty");
        }

        @Test
        @DisplayName("updatePreferenceSummary() returns string")
        void testUpdatePreferenceSummaryReturnsString() {
            List<Snippet> snippets = createTestSnippets();
            when(mockLlmClient.chat(any(), anyString()))
                    .thenReturn(new UnifiedMessage(
                            UnifiedMessage.Role.ASSISTANT,
                            "Updated summary",
                            UnifiedMessage.PayloadFormat.PLAIN_TEXT));

            String summary = contentProcessor.updatePreferenceSummary("existing summary", snippets);
            assertNotNull(summary, "Summary should not be null");
        }

        @Test
        @DisplayName("updatePreferenceSummary() throws for empty snippets")
        void testUpdatePreferenceSummaryThrowsForEmptySnippets() {
            assertThrows(ContentProcessingException.class,
                    () -> contentProcessor.updatePreferenceSummary("existing summary", List.of()),
                    "Should throw ContentProcessingException for empty snippets");
        }

        @Test
        @DisplayName("batchGenerateAbstracts() returns list of strings")
        void testBatchGenerateAbstractsReturnsList() {
            when(mockLlmClient.chat(any(), anyString()))
                    .thenReturn(new UnifiedMessage(
                            UnifiedMessage.Role.ASSISTANT,
                            "Abstract",
                            UnifiedMessage.PayloadFormat.PLAIN_TEXT));

            List<String> abstracts = contentProcessor.batchGenerateAbstracts(List.of("content1", "content2"));
            assertNotNull(abstracts, "Abstracts should not be null");
            assertEquals(2, abstracts.size(), "Should have 2 abstracts");
        }

        @Test
        @DisplayName("batchGenerateAbstracts() throws for null input")
        void testBatchGenerateAbstractsThrowsForNullInput() {
            assertThrows(ContentProcessingException.class,
                    () -> contentProcessor.batchGenerateAbstracts(null),
                    "Should throw ContentProcessingException for null input");
        }

        @Test
        @DisplayName("batchGenerateSummaries() returns list of strings")
        void testBatchGenerateSummariesReturnsList() {
            when(mockLlmClient.chat(any(), anyString()))
                    .thenReturn(new UnifiedMessage(
                            UnifiedMessage.Role.ASSISTANT,
                            "[\"memory\"]",
                            UnifiedMessage.PayloadFormat.PLAIN_TEXT));

            List<String> summaries = contentProcessor.batchGenerateSummaries(
                    List.of("content1", "content2"), MemoryType.PROFILE);
            assertNotNull(summaries, "Summaries should not be null");
        }

        @Test
        @DisplayName("batchGenerateSummaries() throws for null memory type")
        void testBatchGenerateSummariesThrowsForNullMemoryType() {
            assertThrows(ContentProcessingException.class,
                    () -> contentProcessor.batchGenerateSummaries(List.of("content"), null),
                    "Should throw ContentProcessingException for null memory type");
        }
    }

    // ========== Functional Correctness Tests ==========

    @Nested
    @DisplayName("Functional Correctness Tests")
    class FunctionalCorrectnessTests {

        @Test
        @DisplayName("segmentConversation() handles JSON with fallback")
        void testSegmentConversationHandlesJsonWithFallback() {
            when(mockLlmClient.chat(any(), anyString()))
                    .thenReturn(new UnifiedMessage(
                            UnifiedMessage.Role.ASSISTANT,
                            buildSegmentationResponse(),
                            UnifiedMessage.PayloadFormat.PLAIN_TEXT));

            List<ConversationSegment> segments = contentProcessor.segmentConversation(
                    "User: Hello\nAssistant: Hi there\nUser: How are you?\nAssistant: I'm doing well.");

            assertEquals(1, segments.size(), "Should return fallback segment");
            assertEquals("General conversation", segments.get(0).getTopic(), "Should use fallback topic");
        }

        @Test
        @DisplayName("segmentConversation() handles invalid JSON with fallback")
        void testSegmentConversationHandlesInvalidJsonWithFallback() {
            when(mockLlmClient.chat(any(), anyString()))
                    .thenReturn(new UnifiedMessage(
                            UnifiedMessage.Role.ASSISTANT,
                            "Invalid JSON response",
                            UnifiedMessage.PayloadFormat.PLAIN_TEXT));

            List<ConversationSegment> segments = contentProcessor.segmentConversation("test conversation");

            assertNotNull(segments, "Should return fallback segment");
            assertEquals(1, segments.size(), "Should return single fallback segment");
        }

        @Test
        @DisplayName("generateSegmentAbstract() cleans response text")
        void testGenerateSegmentAbstractCleansResponse() {
            when(mockLlmClient.chat(any(), anyString()))
                    .thenReturn(new UnifiedMessage(
                            UnifiedMessage.Role.ASSISTANT,
                            "Summary: This is a test summary",
                            UnifiedMessage.PayloadFormat.PLAIN_TEXT));

            String abstractText = contentProcessor.generateSegmentAbstract("test content");

            assertFalse(abstractText.contains("Summary:"), "Should remove 'Summary:' prefix");
            assertTrue(abstractText.contains("test summary"), "Should contain actual content");
        }
    }

    // ========== Edge Cases and Boundary Conditions Tests ==========

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("segmentConversation() handles single character")
        void testSegmentConversationHandlesSingleCharacter() {
            when(mockLlmClient.chat(any(), anyString()))
                    .thenReturn(new UnifiedMessage(
                            UnifiedMessage.Role.ASSISTANT,
                            buildSegmentationResponse(),
                            UnifiedMessage.PayloadFormat.PLAIN_TEXT));

            List<ConversationSegment> segments = contentProcessor.segmentConversation("a");
            assertNotNull(segments, "Segments should not be null");
        }

        @Test
        @DisplayName("segmentConversation() handles special characters")
        void testSegmentConversationHandlesSpecialCharacters() {
            when(mockLlmClient.chat(any(), anyString()))
                    .thenReturn(new UnifiedMessage(
                            UnifiedMessage.Role.ASSISTANT,
                            buildSegmentationResponse(),
                            UnifiedMessage.PayloadFormat.PLAIN_TEXT));

            String specialContent = "!@#$%^&*()_+-=[]{}|;':\",./<>?";
            List<ConversationSegment> segments = contentProcessor.segmentConversation(specialContent);
            assertNotNull(segments, "Segments should not be null");
        }

        @Test
        @DisplayName("segmentConversation() handles Unicode characters")
        void testSegmentConversationHandlesUnicode() {
            when(mockLlmClient.chat(any(), anyString()))
                    .thenReturn(new UnifiedMessage(
                            UnifiedMessage.Role.ASSISTANT,
                            buildSegmentationResponse(),
                            UnifiedMessage.PayloadFormat.PLAIN_TEXT));

            String unicodeContent = "Hello 世界 🌍 привет مرحبا";
            List<ConversationSegment> segments = contentProcessor.segmentConversation(unicodeContent);
            assertNotNull(segments, "Segments should not be null");
        }

        @Test
        @DisplayName("generateSegmentAbstract() handles whitespace-only input")
        void testGenerateSegmentAbstractHandlesWhitespace() {
            assertThrows(ContentProcessingException.class,
                    () -> contentProcessor.generateSegmentAbstract("   "),
                    "Should throw for whitespace-only input");
        }

        @Test
        @DisplayName("extractStructuredMemories() handles all memory types")
        void testExtractStructuredMemoriesHandlesAllTypes() {
            when(mockLlmClient.chat(any(), anyString()))
                    .thenReturn(new UnifiedMessage(
                            UnifiedMessage.Role.ASSISTANT,
                            "[\"test memory\"]",
                            UnifiedMessage.PayloadFormat.PLAIN_TEXT));

            for (tech.yesboss.memory.model.Snippet.MemoryType type : MemoryType.values()) {
                List<String> memories = contentProcessor.extractStructuredMemories("test content", type);
                assertNotNull(memories, "Memories should not be null for type " + type);
            }
        }

        @Test
        @DisplayName("identifyPreferencesForSnippet() filters invalid IDs")
        void testIdentifyPreferencesForSnippetFiltersInvalidIds() {
            List<Preference> preferences = createTestPreferences();
            when(mockLlmClient.chat(any(), anyString()))
                    .thenReturn(new UnifiedMessage(
                            UnifiedMessage.Role.ASSISTANT,
                            "[\"pref1\", \"invalid_id\"]",
                            UnifiedMessage.PayloadFormat.PLAIN_TEXT));

            List<String> ids = contentProcessor.identifyPreferencesForSnippet("test summary", preferences);
            assertEquals(1, ids.size(), "Should filter out invalid ID");
            assertEquals("pref1", ids.get(0), "Should contain valid ID");
        }
    }

    // ========== Error Handling Tests ==========

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Throws exception with correct error code for invalid input")
        void testThrowsWithCorrectErrorCodeForInvalidInput() {
            try {
                contentProcessor.segmentConversation(null);
                fail("Should have thrown ContentProcessingException");
            } catch (ContentProcessingException e) {
                assertEquals(ContentProcessingException.ERROR_INVALID_INPUT, e.getErrorCode(),
                        "Should have correct error code");
            }
        }

        @Test
        @DisplayName("Exception message is descriptive")
        void testExceptionMessageIsDescriptive() {
            try {
                contentProcessor.segmentConversation("");
                fail("Should have thrown ContentProcessingException");
            } catch (ContentProcessingException e) {
                assertNotNull(e.getMessage(), "Error message should not be null");
                assertFalse(e.getMessage().isEmpty(), "Error message should not be empty");
            }
        }
    }

    // ========== Performance Tests ==========

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("segmentConversation() completes within reasonable time")
        void testSegmentConversationPerformance() {
            when(mockLlmClient.chat(any(), anyString()))
                    .thenReturn(new UnifiedMessage(
                            UnifiedMessage.Role.ASSISTANT,
                            buildSegmentationResponse(),
                            UnifiedMessage.PayloadFormat.PLAIN_TEXT));

            long startTime = System.currentTimeMillis();
            contentProcessor.segmentConversation("test conversation");
            long duration = System.currentTimeMillis() - startTime;

            // Allow more time since it involves LLM calls
            assertTrue(duration < 5000,
                    "segmentConversation() should complete within 5s, took " + duration + "ms");
        }

        @Test
        @DisplayName("batchGenerateAbstracts(10) completes within reasonable time")
        void testBatchGenerateAbstractsPerformance() {
            when(mockLlmClient.chat(any(), anyString()))
                    .thenReturn(new UnifiedMessage(
                            UnifiedMessage.Role.ASSISTANT,
                            "Abstract",
                            UnifiedMessage.PayloadFormat.PLAIN_TEXT));

            List<String> contents = List.of("content1", "content2", "content3",
                    "content4", "content5", "content6", "content7", "content8", "content9", "content10");

            long startTime = System.currentTimeMillis();
            contentProcessor.batchGenerateAbstracts(contents);
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(duration < 10000,
                    "batchGenerateAbstracts(10) should complete within 10s, took " + duration + "ms");
        }
    }

    // ========== MemoryType Tests ==========

    @Nested
    @DisplayName("MemoryType Tests")
    class MemoryTypeTests {

        @Test
        @DisplayName("All memory types have display names")
        void testAllMemoryTypesHaveDisplayNames() {
            for (tech.yesboss.memory.model.Snippet.MemoryType type : MemoryType.values()) {
                assertNotNull(type.getDisplayName(), type + " should have display name");
                assertFalse(type.getDisplayName().isEmpty(), type + " display name should not be empty");
            }
        }

        @Test
        @DisplayName("All memory types have descriptions")
        void testAllMemoryTypesHaveDescriptions() {
            for (tech.yesboss.memory.model.Snippet.MemoryType type : MemoryType.values()) {
                assertNotNull(type.getDescription(), type + " should have description");
                assertFalse(type.getDescription().isEmpty(), type + " description should not be empty");
            }
        }

        @Test
        @DisplayName("fromDisplayName() finds correct type")
        void testFromDisplayNameFindsCorrectType() {
            assertEquals(MemoryType.PROFILE, MemoryType.fromDisplayName("人物档案"));
            assertEquals(MemoryType.EVENT, MemoryType.fromDisplayName("事件"));
            assertEquals(MemoryType.KNOWLEDGE, MemoryType.fromDisplayName("知识"));
            assertEquals(MemoryType.BEHAVIOR, MemoryType.fromDisplayName("行为模式"));
            assertEquals(MemoryType.SKILL, MemoryType.fromDisplayName("技能"));
            assertEquals(MemoryType.TOOL, MemoryType.fromDisplayName("工具使用"));
        }

        @Test
        @DisplayName("fromDisplayName() returns null for invalid name")
        void testFromDisplayNameReturnsNullForInvalidName() {
            assertNull(MemoryType.fromDisplayName("invalid type"),
                    "Should return null for invalid display name");
        }
    }

    // ========== ConversationSegment Tests ==========

    @Nested
    @DisplayName("ConversationSegment Tests")
    class ConversationSegmentTests {

        @Test
        @DisplayName("ConversationSegment builder works correctly")
        void testConversationSegmentBuilder() {
            ConversationSegment segment = ConversationSegment.builder()
                    .content("test content")
                    .topic("test topic")
                    .startIndex(0)
                    .endIndex(10)
                    .build();

            assertEquals("test content", segment.getContent(), "Content should match");
            assertEquals("test topic", segment.getTopic(), "Topic should match");
            assertEquals(0, segment.getStartIndex(), "Start index should match");
            assertEquals(10, segment.getEndIndex(), "End index should match");
        }

        @Test
        @DisplayName("ConversationSegment hasTopic() works correctly")
        void testConversationSegmentHasTopic() {
            ConversationSegment segmentWithTopic = ConversationSegment.builder()
                    .content("content")
                    .topic("topic")
                    .build();
            assertTrue(segmentWithTopic.hasTopic(), "Should have topic");

            ConversationSegment segmentWithoutTopic = ConversationSegment.builder()
                    .content("content")
                    .build();
            assertFalse(segmentWithoutTopic.hasTopic(), "Should not have topic");
        }

        @Test
        @DisplayName("ConversationSegment getLength() works correctly")
        void testConversationSegmentGetLength() {
            ConversationSegment segment = ConversationSegment.builder()
                    .content("test content")
                    .build();

            assertEquals(12, segment.getLength(), "Length should match content length");
        }
    }

    // ========== Helper methods ==========

    private String buildSegmentationResponse() {
        return """
                {
                  "segments": [
                    {"content": "First segment content", "topic": "Topic 1", "start": 0, "end": 50},
                    {"content": "Second segment content", "topic": "Topic 2", "start": 50, "end": 100}
                  ]
                }
                """;
    }

    private List<Preference> createTestPreferences() {
        Preference pref1 = new Preference();
        pref1.setId("pref1");
        pref1.setName("Preference 1");
        pref1.setSummary("Summary 1");

        Preference pref2 = new Preference();
        pref2.setId("pref2");
        pref2.setName("Preference 2");
        pref2.setSummary("Summary 2");

        return List.of(pref1, pref2);
    }

    private List<Snippet> createTestSnippets() {
        Snippet snippet = new Snippet();
        snippet.setId("snippet1");
        snippet.setResourceId("resource1");
        snippet.setSummary("Test snippet summary");
        snippet.setMemoryType(MemoryType.PROFILE);
        snippet.setCreatedAt(LocalDateTime.now());
        snippet.setUpdatedAt(LocalDateTime.now());

        return List.of(snippet);
    }
}
