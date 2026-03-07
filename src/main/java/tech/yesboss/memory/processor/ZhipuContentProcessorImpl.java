package tech.yesboss.memory.processor;

import tech.yesboss.memory.model.Snippet.MemoryType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.llm.LlmClient;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.memory.model.Preference;
import tech.yesboss.memory.model.Snippet;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Zhipu AI implementation of ContentProcessor.
 *
 * <p>This implementation uses Zhipu's LLM API to process conversation content,
 * generate summaries, extract structured memories, and classify snippets.</p>
 */
public class ZhipuContentProcessorImpl implements ContentProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ZhipuContentProcessorImpl.class);

    private LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final int maxRetries;
    private final long retryDelayMs;

    /**
     * Create a new ZhipuContentProcessorImpl with default settings.
     */
    public ZhipuContentProcessorImpl() {
        this.llmClient = null;
        this.maxRetries = 3;
        this.retryDelayMs = 1000;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Create a new ZhipuContentProcessorImpl with specified LLM client.
     *
     * @param llmClient The LLM client to use
     */
    public ZhipuContentProcessorImpl(LlmClient llmClient) {
        this();
        this.llmClient = llmClient;
    }

    /**
     * Create a new ZhipuContentProcessorImpl with full configuration.
     *
     * @param llmClient The LLM client to use
     * @param maxRetries Maximum number of retries
     * @param retryDelayMs Delay between retries in milliseconds
     */
    public ZhipuContentProcessorImpl(LlmClient llmClient, int maxRetries, long retryDelayMs) {
        this.llmClient = llmClient;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Set the LLM client (for dependency injection).
     *
     * @param llmClient The LLM client to use
     */
    public void setLlmClient(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * Ensure LLM client is initialized.
     */
    private void ensureLlmClientInitialized() {
        if (llmClient == null) {
            throw new ContentProcessingException("LlmClient not initialized. Please set LlmClient before using the processor.",
                    ContentProcessingException.ERROR_INVALID_INPUT);
        }
    }

    @Override
    public List<ConversationSegment> segmentConversation(String conversationContent) {
        ensureLlmClientInitialized();
        if (conversationContent == null || conversationContent.trim().isEmpty()) {
            throw new ContentProcessingException("Conversation content cannot be null or empty",
                    ContentProcessingException.ERROR_INVALID_INPUT);
        }

        try {
            String prompt = buildSegmentationPrompt(conversationContent);
            String response = executeWithRetry(() ->
                llmClient.chat(createMessages(prompt), buildSystemPrompt()).content()
            );

            return parseSegments(response, conversationContent);
        } catch (Exception e) {
            throw new ContentProcessingException("Failed to segment conversation: " + e.getMessage(),
                    ContentProcessingException.ERROR_SEGMENTATION_FAILURE, e);
        }
    }

    @Override
    public String generateSegmentAbstract(String segmentContent) {
        ensureLlmClientInitialized();
        if (segmentContent == null || segmentContent.trim().isEmpty()) {
            throw new ContentProcessingException("Segment content cannot be null or empty",
                    ContentProcessingException.ERROR_INVALID_INPUT);
        }

        try {
            String prompt = buildAbstractPrompt(segmentContent);
            String response = executeWithRetry(() ->
                llmClient.chat(createMessages(prompt), buildSystemPrompt()).content()
            );

            return extractAbstractFromResponse(response);
        } catch (Exception e) {
            throw new ContentProcessingException("Failed to generate segment abstract: " + e.getMessage(),
                    ContentProcessingException.ERROR_LLM_FAILURE, e);
        }
    }

    @Override
    @Deprecated
    public String generateAbstract(String content) {
        return generateSegmentAbstract(content);
    }

    @Override
    public List<String> extractStructuredMemories(String resourceContent, MemoryType memoryType) {
        ensureLlmClientInitialized();
        if (resourceContent == null || resourceContent.trim().isEmpty()) {
            throw new ContentProcessingException("Resource content cannot be null or empty",
                    ContentProcessingException.ERROR_INVALID_INPUT);
        }
        if (memoryType == null) {
            throw new ContentProcessingException("Memory type cannot be null",
                    ContentProcessingException.ERROR_INVALID_INPUT);
        }

        try {
            String prompt = buildExtractionPrompt(resourceContent, memoryType);
            String response = executeWithRetry(() ->
                llmClient.chat(createMessages(prompt), buildSystemPrompt()).content()
            );

            return parseExtractedMemories(response, memoryType);
        } catch (Exception e) {
            throw new ContentProcessingException("Failed to extract structured memories: " + e.getMessage(),
                    ContentProcessingException.ERROR_EXTRACTION_FAILURE, e);
        }
    }

    @Override
    public List<String> identifyPreferencesForSnippet(String snippetSummary, List<Preference> existingPreferences) {
        ensureLlmClientInitialized();
        if (snippetSummary == null || snippetSummary.trim().isEmpty()) {
            throw new ContentProcessingException("Snippet summary cannot be null or empty",
                    ContentProcessingException.ERROR_INVALID_INPUT);
        }
        if (existingPreferences == null || existingPreferences.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            String prompt = buildClassificationPrompt(snippetSummary, existingPreferences);
            String response = executeWithRetry(() ->
                llmClient.chat(createMessages(prompt), buildSystemPrompt()).content()
            );

            return parsePreferenceIds(response, existingPreferences);
        } catch (Exception e) {
            throw new ContentProcessingException("Failed to identify preferences: " + e.getMessage(),
                    ContentProcessingException.ERROR_CLASSIFICATION_FAILURE, e);
        }
    }

    @Override
    public String updatePreferenceSummary(String existingSummary, List<Snippet> newSnippets) {
        ensureLlmClientInitialized();
        if (newSnippets == null || newSnippets.isEmpty()) {
            throw new ContentProcessingException("New snippets list cannot be null or empty",
                    ContentProcessingException.ERROR_INVALID_INPUT);
        }

        try {
            String prompt = buildPreferenceUpdatePrompt(existingSummary, newSnippets);
            String response = executeWithRetry(() ->
                llmClient.chat(createMessages(prompt), buildSystemPrompt()).content()
            );

            return extractUpdatedSummary(response);
        } catch (Exception e) {
            throw new ContentProcessingException("Failed to update preference summary: " + e.getMessage(),
                    ContentProcessingException.ERROR_LLM_FAILURE, e);
        }
    }

    @Override
    public List<String> batchGenerateAbstracts(List<String> contents) {
        ensureLlmClientInitialized();
        if (contents == null || contents.isEmpty()) {
            throw new ContentProcessingException("Contents list cannot be null or empty",
                    ContentProcessingException.ERROR_INVALID_INPUT);
        }

        return contents.stream()
                .map(this::generateSegmentAbstract)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> batchGenerateSummaries(List<String> contents, MemoryType memoryType) {
        ensureLlmClientInitialized();
        if (contents == null || contents.isEmpty()) {
            throw new ContentProcessingException("Contents list cannot be null or empty",
                    ContentProcessingException.ERROR_INVALID_INPUT);
        }
        if (memoryType == null) {
            throw new ContentProcessingException("Memory type cannot be null",
                    ContentProcessingException.ERROR_INVALID_INPUT);
        }

        return contents.stream()
                .map(content -> extractStructuredMemories(content, memoryType))
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    // ========== Private helper methods ==========

    private String buildSystemPrompt() {
        return "You are a helpful AI assistant specialized in analyzing conversations and extracting structured information. " +
                "Always provide clear, concise responses in the requested format.";
    }

    private List<UnifiedMessage> createMessages(String prompt) {
        List<UnifiedMessage> messages = new ArrayList<>();
        messages.add(new UnifiedMessage(UnifiedMessage.Role.USER, prompt, UnifiedMessage.PayloadFormat.PLAIN_TEXT));
        return messages;
    }

    private String buildSegmentationPrompt(String content) {
        return "Please analyze the following conversation and split it into segments based on topic changes..." +
                "Conversation:." + content + ".." +
                "Provide your response in the following JSON format:." +
                "{." +
                "  .segments.: [." +
                "    {.content.: .segment content., .topic.: .topic description., .start.: 0, .end.: 100},." +
                "    ...." +
                "  ]." +
                "}";
    }

    private List<ConversationSegment> parseSegments(String response, String originalContent) {
        try {
            String jsonPart = extractJson(response);
            SegmentationResult result = objectMapper.readValue(jsonPart, SegmentationResult.class);

            List<ConversationSegment> segments = new ArrayList<>();
            for (SegmentData data : result.segments()) {
                ConversationSegment segment = ConversationSegment.builder()
                        .content(data.content())
                        .topic(data.topic())
                        .startIndex(data.start())
                        .endIndex(data.end())
                        .build();
                segments.add(segment);
            }

            logger.info("Parsed {} segments from conversation", segments.size());
            return segments;
        } catch (Exception e) {
            logger.warn("Failed to parse segments, returning single segment", e);
            return List.of(ConversationSegment.builder()
                    .content(originalContent)
                    .topic("General conversation")
                    .startIndex(0)
                    .endIndex(originalContent.length())
                    .build());
        }
    }

    private String buildAbstractPrompt(String segmentContent) {
        return "Please generate a 1-2 sentence abstract/summary for the following conversation segment:.." +
                segmentContent + ".." +
                "Provide only the summary text, no additional commentary.";
    }

    private String extractAbstractFromResponse(String response) {
        String cleaned = response.trim();
        cleaned = cleaned.replaceAll("^(Summary|Abstract|Response):.s*", "");
        cleaned = cleaned.replaceAll("^['.]|['.]$", "");
        return cleaned;
    }

    private String buildExtractionPrompt(String content, MemoryType memoryType) {
        return "Please extract structured memories of type ." + memoryType.getDisplayName() +
                ". (" + memoryType.getDescription() + ") from the following content:.." +
                content + ".." +
                "Provide your response as a JSON array of strings:." +
                "[.memory 1., .memory 2., ...]";
    }

    private List<String> parseExtractedMemories(String response, MemoryType memoryType) {
        try {
            String jsonPart = extractJson(response);
            List<String> memories = objectMapper.readValue(jsonPart,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));

            logger.info("Extracted {} memories of type {}", memories.size(), memoryType);
            return memories;
        } catch (Exception e) {
            logger.warn("Failed to parse memories, returning single item", e);
            return List.of(response);
        }
    }

    private String buildClassificationPrompt(String snippetSummary, List<Preference> preferences) {
        StringBuilder prefList = new StringBuilder("Available preferences:.");
        for (int i = 0; i < preferences.size(); i++) {
            Preference p = preferences.get(i);
            prefList.append(String.format("%d. ID: %s, Name: %s, Summary: %s.",
                    i + 1, p.getId(), p.getName(), p.getSummary()));
        }

        return "Please analyze the following snippet summary and identify which preferences it belongs to..." +
                prefList + "." +
                "Snippet Summary:." + snippetSummary + ".." +
                "Respond with a JSON array of preference IDs: [.id1., .id2., ...]. " +
                "Return an empty array [] if no preferences match.";
    }

    private List<String> parsePreferenceIds(String response, List<Preference> preferences) {
        try {
            String jsonPart = extractJson(response);
            List<String> ids = objectMapper.readValue(jsonPart,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));

            List<String> validIds = preferences.stream()
                    .map(Preference::getId)
                    .collect(Collectors.toList());

            return ids.stream()
                    .filter(validIds::contains)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("Failed to parse preference IDs, returning empty list", e);
            return new ArrayList<>();
        }
    }

    private String buildPreferenceUpdatePrompt(String existingSummary, List<Snippet> newSnippets) {
        StringBuilder snippetsText = new StringBuilder("New snippets:.");
        for (Snippet snippet : newSnippets) {
            snippetsText.append("- ").append(snippet.getSummary()).append(".");
        }

        return "Please update the following preference summary by incorporating new information from the snippets..." +
                "Existing Summary:." + (existingSummary != null ? existingSummary : "No existing summary") + ".." +
                snippetsText + "." +
                "Provide only the updated summary text, preserving important historical information while adding new insights.";
    }

    private String extractUpdatedSummary(String response) {
        return extractAbstractFromResponse(response);
    }

    private String extractJson(String response) {
        int jsonStart = response.indexOf('[');
        if (jsonStart == -1) {
            jsonStart = response.indexOf('{');
        }
        if (jsonStart == -1) {
            return response;
        }

        int bracketCount = 0;
        boolean inString = false;
        boolean escaped = false;
        int jsonEnd = jsonStart;

        for (int i = jsonStart; i < response.length(); i++) {
            char c = response.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '.') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '[' || c == '{') {
                    bracketCount++;
                } else if (c == ']' || c == '}') {
                    bracketCount--;
                    if (bracketCount == 0) {
                        jsonEnd = i + 1;
                        break;
                    }
                }
            }
        }

        return response.substring(jsonStart, jsonEnd);
    }

    private <T> T executeWithRetry(java.util.function.Supplier<T> operation) {
        Exception lastException = null;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetries - 1) {
                    long delay = retryDelayMs * (1L << attempt);
                    logger.warn("Operation failed (attempt {}/{}), retrying in {}ms: {}",
                            attempt + 1, maxRetries, delay, e.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ContentProcessingException("Interrupted during retry",
                                ContentProcessingException.ERROR_TIMEOUT, ie);
                    }
                }
            }
        }

        throw new ContentProcessingException("Operation failed after " + maxRetries + " retries",
                ContentProcessingException.ERROR_LLM_FAILURE, lastException);
    }

    // ========== Inner classes for JSON parsing ==========

    private record SegmentationResult(List<SegmentData> segments) {}

    private record SegmentData(
            @JsonProperty("content") String content,
            @JsonProperty("topic") String topic,
            @JsonProperty("start") int start,
            @JsonProperty("end") int end
    ) {}
}
