# Memory Persistence Module - Usage Examples

## Table of Contents

1. [Basic Usage](#1-basic-usage)
2. [Advanced Queries](#2-advanced-queries)
3. [Batch Operations](#3-batch-operations)
4. [Custom Triggers](#4-custom-triggers)
5. [Error Handling](#5-error-handling)
6. [Integration Patterns](#6-integration-patterns)
7. [Testing](#7-testing)

---

## 1. Basic Usage

### 1.1 Simple Memory Extraction

Extract memories from a conversation:

```java
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.memory.model.Resource;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ConversationHandler {

    private final MemoryService memoryService;

    public void handleNewConversation(List<UnifiedMessage> messages) {
        String conversationId = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString();

        try {
            List<Resource> resources = memoryService.extractFromMessages(
                messages,
                conversationId,
                sessionId
            );

            System.out.println("Extracted " + resources.size() + " memory resources");

            // Process each resource
            for (Resource resource : resources) {
                System.out.println("Resource: " + resource.getAbstractText());
            }

        } catch (MemoryServiceException e) {
            System.err.println("Failed to extract memories: " + e.getMessage());
        }
    }
}
```

### 1.2 Manual Step-by-Step Extraction

Extract memories with full control over each step:

```java
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.memory.model.Resource;
import tech.yesboss.memory.model.Snippet;
import tech.yesboss.memory.processor.ConversationSegment;
import tech.yesboss.memory.model.Snippet.MemoryType;

@Service
public class ManualExtractionService {

    private final MemoryService memoryService;

    public void detailedExtraction(List<UnifiedMessage> messages) {
        // Step 1: Concatenate conversation
        String content = memoryService.concatenateConversationContent(messages);
        System.out.println("Conversation length: " + content.length());

        // Step 2: Segment by topics
        List<ConversationSegment> segments = memoryService.segmentConversation(content);
        System.out.println("Found " + segments.size() + " segments");

        // Step 3: Process each segment
        for (ConversationSegment segment : segments) {
            // Generate abstract
            String abstractText = memoryService.generateSegmentAbstract(
                segment.getContent()
            );

            // Create resource
            Resource resource = memoryService.buildResource(
                "conv-" + UUID.randomUUID(),
                "session-" + UUID.randomUUID(),
                segment.getContent(),
                abstractText
            );

            // Extract structured memories
            List<Snippet> snippets = memoryService.extractStructuredMemories(
                segment.getContent()
            );

            // Group by memory type
            Map<MemoryType, List<Snippet>> byType = snippets.stream()
                .collect(Collectors.groupingBy(Snippet::getMemoryType));

            System.out.println("Snippets by type:");
            byType.forEach((type, snippetList) -> {
                System.out.println("  " + type + ": " + snippetList.size());
            });
        }
    }
}
```

---

## 2. Advanced Queries

### 2.1 Semantic Search

Search for similar memories using semantic similarity:

```java
import tech.yesboss.memory.query.MemoryQueryService;
import tech.yesboss.memory.model.Resource;
import tech.yesboss.memory.model.Snippet;
import java.util.List;

@Service
public class SemanticSearchService {

    private final MemoryQueryService queryService;

    public void searchUserPreferences(String query) {
        try {
            // Search resources
            List<Resource> resources = queryService.searchResources(query, 10);
            System.out.println("Found " + resources.size() + " similar resources");

            // Search specific memory types
            List<Snippet> profiles = queryService.searchSnippets(
                query,
                Snippet.MemoryType.PROFILE,
                5
            );

            System.out.println("Profile matches:");
            profiles.forEach(snippet ->
                System.out.println("  - " + snippet.getSummary())
            );

        } catch (MemoryQueryException e) {
            System.err.println("Search failed: " + e.getMessage());
        }
    }
}
```

### 2.2 Agentic RAG Query

Three-layer retrieval with context awareness:

```java
import tech.yesboss.memory.query.MemoryQueryService;
import tech.yesboss.memory.model.AgenticRagResult;
import tech.yesboss.memory.model.DecisionLog;

@Service
public class AgenticRAGService {

    private final MemoryQueryService queryService;

    public void answerWithContext(String userQuery, String conversationId) {
        try {
            AgenticRagResult result = queryService.agenticRagQuery(
                userQuery,
                conversationId,
                10,  // topK
                0.7  // similarity threshold
            );

            System.out.println("Query: " + userQuery);
            System.out.println("Resources: " + result.getResources().size());
            System.out.println("Snippets: " + result.getSnippets().size());
            System.out.println("Preferences: " + result.getPreferences().size());

            // Display decision logs
            System.out.println("\nDecision Process:");
            for (DecisionLog log : result.getDecisionLogs()) {
                System.out.println("  - " + log.getDecision() +
                    " (confidence: " + log.getConfidence() + ")");
            }

            // Relevance scores
            result.getRelevanceScores().forEach((layer, score) -> {
                System.out.println(layer + " relevance: " + score);
            });

        } catch (MemoryQueryException e) {
            System.err.println("RAG query failed: " + e.getMessage());
        }
    }
}
```

### 2.3 Memory Chain Retrieval

Retrieve context chain for conversation continuity:

```java
import tech.yesboss.memory.query.MemoryQueryService;
import tech.yesboss.memory.model.MemoryChain;
import java.util.List;

@Service
public class ContextContinuityService {

    private final MemoryQueryService queryService;

    public void provideContext(String conversationId, int depth) {
        try {
            List<MemoryChain> chains = queryService.getMemoryChain(
                conversationId,
                depth
            );

            System.out.println("Memory Context (depth " + depth + "):");

            for (MemoryChain chain : chains) {
                System.out.println("\nMemory: " + chain.getContent());
                System.out.println("Type: " + chain.getMemoryType());
                System.out.println("Timestamp: " + chain.getTimestamp());

                System.out.println("Related memories:");
                chain.getRelatedMemories().forEach(related -> {
                    System.out.println("  -> " + related.getContent());
                });
            }

        } catch (MemoryQueryException e) {
            System.err.println("Failed to retrieve memory chain: " + e.getMessage());
        }
    }
}
```

---

## 3. Batch Operations

### 3.1 Batch Memory Extraction

Process multiple conversations efficiently:

```java
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.memory.service.MemoryService.MessageBatch;
import tech.yesboss.memory.model.Resource;
import java.util.List;
import java.util.ArrayList;

@Service
public class BatchConversationProcessor {

    private final MemoryService memoryService;

    public void processMultipleConversations() {
        List<MessageBatch> batches = new ArrayList<>();

        // Add multiple conversation batches
        batches.add(new MessageBatch(
            getConversation1Messages(),
            "conv-1",
            "session-1"
        ));

        batches.add(new MessageBatch(
            getConversation2Messages(),
            "conv-2",
            "session-2"
        ));

        try {
            List<Resource> allResources = memoryService.batchExtractFromMessages(batches);

            System.out.println("Processed " + batches.size() + " conversations");
            System.out.println("Total resources extracted: " + allResources.size());

            // Statistics
            Map<String, Long> byConversation = allResources.stream()
                .collect(Collectors.groupingBy(
                    Resource::getConversationId,
                    Collectors.counting()
                ));

            byConversation.forEach((convId, count) -> {
                System.out.println("Conversation " + convId + ": " + count + " resources");
            });

        } catch (MemoryServiceException e) {
            System.err.println("Batch extraction failed: " + e.getMessage());
        }
    }
}
```

### 3.2 Batch Vectorization

Generate embeddings for all pending items:

```java
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.memory.service.MemoryService.BatchEmbeddingResult;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class VectorizationScheduler {

    private final MemoryService memoryService;

    // Run every 5 minutes
    @Scheduled(fixedRate = 300000)
    public void vectorizePendingItems() {
        try {
            BatchEmbeddingResult result = memoryService.processBatchEmbedding();

            System.out.println("Vectorization completed:");
            System.out.println("  Total items: " + result.getTotalCount());
            System.out.println("  Resources: " + result.getResourceCount());
            System.out.println("  Snippets: " + result.getSnippetCount());
            System.out.println("  Preferences: " + result.getPreferenceCount());
            System.out.println("  Success: " + result.getSuccessCount());
            System.out.println("  Failures: " + result.getFailureCount());
            System.out.println("  Time: " + result.getProcessingTimeMs() + "ms");

            // Log errors if any
            if (!result.getErrors().isEmpty()) {
                System.err.println("Errors encountered:");
                result.getErrors().forEach(error ->
                    System.err.println("  - " + error)
                );
            }

        } catch (MemoryServiceException e) {
            System.err.println("Vectorization failed: " + e.getMessage());
        }
    }
}
```

### 3.3 Process Pending Resources

Extract structured memories for resources without snippets:

```java
import tech.yesboss.memory.service.MemoryService;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class PendingResourceProcessor {

    private final MemoryService memoryService;

    // Run every hour
    @Scheduled(cron = "0 0 * * * ?")
    public void processPending() {
        try {
            int processed = memoryService.processPendingResources();

            if (processed > 0) {
                System.out.println("Processed " + processed + " pending resources");
            } else {
                System.out.println("No pending resources to process");
            }

        } catch (MemoryServiceException e) {
            System.err.println("Failed to process pending resources: " + e.getMessage());
        }
    }
}
```

---

## 4. Custom Triggers

### 4.1 Conversation Round Trigger

Trigger memory extraction at specific conversation rounds:

```java
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.memory.trigger.ConversationRoundTrigger;
import org.springframework.stereotype.Component;

@Component
public class ConversationRoundHandler {

    private final MemoryService memoryService;
    private final ConversationRoundTrigger trigger;

    public void onConversationRound(
        List<UnifiedMessage> messages,
        String conversationId,
        int currentRound
    ) {
        // Check if should trigger
        if (trigger.shouldTrigger(currentRound)) {
            System.out.println("Triggering memory extraction at round " + currentRound);

            String sessionId = UUID.randomUUID().toString();
            try {
                List<Resource> resources = memoryService.extractFromMessages(
                    messages,
                    conversationId,
                    sessionId
                );

                System.out.println("Extracted " + resources.size() + " memories");

            } catch (MemoryServiceException e) {
                System.err.println("Extraction failed: " + e.getMessage());
            }
        }
    }
}
```

### 4.2 Custom Interval Trigger

Create a custom trigger based on time and resource count:

```java
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.memory.repository.ResourceRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CustomIntervalTrigger {

    private final MemoryService memoryService;
    private final ResourceRepository resourceRepository;

    private static final int RESOURCE_THRESHOLD = 100;

    @Scheduled(fixedRate = 60000)  // Every minute
    public void checkAndTrigger() {
        long pendingCount = resourceRepository.countResourcesWithoutEmbedding();

        if (pendingCount >= RESOURCE_THRESHOLD) {
            System.out.println("Threshold reached: " + pendingCount + " pending resources");
            triggerBatchVectorization();
        }
    }

    private void triggerBatchVectorization() {
        try {
            BatchEmbeddingResult result = memoryService.processBatchEmbedding();
            System.out.println("Vectorization completed: " +
                result.getSuccessCount() + "/" + result.getTotalCount());

        } catch (MemoryServiceException e) {
            System.err.println("Vectorization failed: " + e.getMessage());
        }
    }
}
```

---

## 5. Error Handling

### 5.1 Retry Mechanism

Implement retry logic for transient failures:

```java
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.memory.service.MemoryServiceException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class ResilientMemoryService {

    private final MemoryService memoryService;

    @Retryable(
        value = {MemoryServiceException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public List<Resource> extractWithRetry(
        List<UnifiedMessage> messages,
        String conversationId,
        String sessionId
    ) throws MemoryServiceException {
        return memoryService.extractFromMessages(messages, conversationId, sessionId);
    }

    public void safeExtract(List<UnifiedMessage> messages,
                           String conversationId,
                           String sessionId) {
        try {
            extractWithRetry(messages, conversationId, sessionId);
        } catch (MemoryServiceException e) {
            // Log and handle final failure
            System.err.println("Failed after retries: " + e.getMessage());
            // Fallback: store in error queue for later processing
            storeForRetry(messages, conversationId, sessionId);
        }
    }

    private void storeForRetry(List<UnifiedMessage> messages,
                              String conversationId,
                              String sessionId) {
        // Implementation: store in database or message queue
    }
}
```

### 5.2 Graceful Degradation

Handle service unavailability:

```java
import tech.yesboss.memory.service.MemoryService;
import org.springframework.stereotype.Service;

@Service
public class GracefulMemoryHandler {

    private final MemoryService memoryService;

    public void handleConversation(List<UnifiedMessage> messages,
                                  String conversationId,
                                  String sessionId) {
        // Check service availability
        if (!memoryService.isAvailable()) {
            System.out.println("Memory service unavailable, using fallback");
            handleWithFallback(messages, conversationId);
            return;
        }

        try {
            memoryService.extractFromMessages(messages, conversationId, sessionId);

        } catch (MemoryServiceException e) {
            System.err.println("Memory extraction failed: " + e.getMessage());
            // Fallback: basic storage without memory extraction
            handleWithFallback(messages, conversationId);
        }
    }

    private void handleWithFallback(List<UnifiedMessage> messages,
                                   String conversationId) {
        // Store messages without memory extraction
        // Can be processed later when service is available
        System.out.println("Messages stored for later processing");
    }
}
```

### 5.3 Error Recovery

Recover from partial failures:

```java
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.memory.service.MemoryService.BatchEmbeddingResult;
import org.springframework.stereotype.Service;

@Service
public class ErrorRecoveryService {

    private final MemoryService memoryService;

    public void processWithErrorRecovery() {
        BatchEmbeddingResult result = memoryService.processBatchEmbedding();

        if (result.getFailureCount() > 0) {
            System.out.println("Some items failed, attempting recovery...");

            // Log failed items
            result.getErrors().forEach(error ->
                System.err.println("Error: " + error)
            );

            // Retry failed items
            if (shouldRetry(result)) {
                retryFailedItems();
            }
        }
    }

    private boolean shouldRetry(BatchEmbeddingResult result) {
        // Retry if failure rate is below threshold
        double failureRate = (double) result.getFailureCount() / result.getTotalCount();
        return failureRate < 0.5;  // Retry if less than 50% failed
    }

    private void retryFailedItems() {
        // Implementation: retry only failed items
        System.out.println("Retrying failed items...");
    }
}
```

---

## 6. Integration Patterns

### 6.1 Webhook Integration

Integrate with webhook handlers:

```java
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.domain.message.UnifiedMessage;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class WebhookMemoryIntegration {

    private final MemoryService memoryService;

    public void handleWebhookMessage(UnifiedMessage message,
                                    String conversationId,
                                    String sessionId) {
        // Collect messages in buffer
        List<UnifiedMessage> messageBuffer = getBufferedMessages(conversationId);
        messageBuffer.add(message);

        // Trigger extraction based on conditions
        if (shouldTriggerExtraction(messageBuffer)) {
            try {
                memoryService.extractFromMessages(
                    messageBuffer,
                    conversationId,
                    sessionId
                );

                // Clear buffer after successful extraction
                clearBuffer(conversationId);

            } catch (MemoryServiceException e) {
                System.err.println("Extraction failed: " + e.getMessage());
            }
        }
    }

    private boolean shouldTriggerExtraction(List<UnifiedMessage> messages) {
        // Trigger conditions:
        // - Number of messages reaches threshold
        // - User ends conversation
        // - Time since last message exceeds threshold
        return messages.size() >= 10;
    }
}
```

### 6.2 Message Queue Integration

Integrate with message queue for async processing:

```java
import tech.yesboss.memory.service.MemoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class QueueMemoryProcessor {

    private final MemoryService memoryService;

    @KafkaListener(topics = "conversation-events")
    public void processConversationEvent(ConversationEvent event) {
        try {
            List<Resource> resources = memoryService.extractFromMessages(
                event.getMessages(),
                event.getConversationId(),
                event.getSessionId()
            );

            System.out.println("Processed conversation event: " +
                event.getConversationId() + ", extracted " +
                resources.size() + " resources");

        } catch (MemoryServiceException e) {
            System.err.println("Failed to process event: " + e.getMessage());
            // Send to DLQ or retry
        }
    }
}
```

### 6.3 REST API Integration

Expose memory operations as REST endpoints:

```java
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.memory.query.MemoryQueryService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemoryService memoryService;
    private final MemoryQueryService queryService;

    @PostMapping("/extract")
    public ResponseEntity<ExtractionResult> extractMemories(
        @RequestBody ExtractionRequest request
    ) {
        try {
            List<Resource> resources = memoryService.extractFromMessages(
                request.getMessages(),
                request.getConversationId(),
                request.getSessionId()
            );

            ExtractionResult result = new ExtractionResult(
                true,
                resources.size(),
                "Extraction successful"
            );

            return ResponseEntity.ok(result);

        } catch (MemoryServiceException e) {
            ExtractionResult result = new ExtractionResult(
                false,
                0,
                e.getMessage()
            );
            return ResponseEntity.badRequest().body(result);
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<Resource>> searchMemories(
        @RequestParam String query,
        @RequestParam(defaultValue = "10") int topK
    ) {
        try {
            List<Resource> results = queryService.searchResources(query, topK);
            return ResponseEntity.ok(results);

        } catch (MemoryQueryException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
```

---

## 7. Testing

### 7.1 Unit Testing Memory Service

```java
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.domain.message.UnifiedMessage;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MemoryServiceTest {

    @Mock
    private MemoryService memoryService;

    @Test
    void testExtractFromMessages() {
        // Arrange
        List<UnifiedMessage> messages = createTestMessages();
        String conversationId = "test-conv";
        String sessionId = "test-session";

        List<Resource> expectedResources = createTestResources();
        when(memoryService.extractFromMessages(messages, conversationId, sessionId))
            .thenReturn(expectedResources);

        // Act
        List<Resource> actualResources = memoryService.extractFromMessages(
            messages,
            conversationId,
            sessionId
        );

        // Assert
        assertEquals(expectedResources.size(), actualResources.size());
        verify(memoryService, times(1))
            .extractFromMessages(messages, conversationId, sessionId);
    }

    private List<UnifiedMessage> createTestMessages() {
        // Create test messages
        return List.of(
            new UnifiedMessage("user", "Hello"),
            new UnifiedMessage("assistant", "Hi there")
        );
    }
}
```

### 7.2 Integration Testing

```java
import tech.yesboss.memory.service.MemoryService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MemoryIntegrationTest {

    @Autowired
    private MemoryService memoryService;

    @Test
    void testEndToEndExtraction() {
        // Arrange
        List<UnifiedMessage> messages = createRealConversation();
        String conversationId = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString();

        // Act
        List<Resource> resources = memoryService.extractFromMessages(
            messages,
            conversationId,
            sessionId
        );

        // Assert
        assertNotNull(resources);
        assertFalse(resources.isEmpty());
        assertTrue(resources.stream().allMatch(r ->
            r.getId() != null &&
            r.getConversationId().equals(conversationId) &&
            r.getSessionId().equals(sessionId)
        ));
    }

    @Test
    void testBatchVectorization() {
        // Act
        BatchEmbeddingResult result = memoryService.processBatchEmbedding();

        // Assert
        assertNotNull(result);
        assertTrue(result.getSuccessCount() >= 0);
        assertTrue(result.getFailureCount() >= 0);
    }
}
```

### 7.3 Performance Testing

```java
import tech.yesboss.memory.service.MemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.util.StopWatch;

class MemoryPerformanceTest {

    @Autowired
    private MemoryService memoryService;

    @Test
    void testExtractionPerformance() {
        List<UnifiedMessage> messages = createLargeConversation(100);
        String conversationId = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString();

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        List<Resource> resources = memoryService.extractFromMessages(
            messages,
            conversationId,
            sessionId
        );

        stopWatch.stop();

        System.out.println("Extracted " + resources.size() + " resources in " +
            stopWatch.getTotalTimeMillis() + "ms");

        // Performance assertions
        assertTrue(stopWatch.getTotalTimeMillis() < 5000);  // < 5 seconds
    }
}
```

---

For more information, see:
- [Quick Start Guide](./QUICK_START.md)
- [API Documentation](./API_DOCUMENTATION.md)
- [Configuration Guide](./CONFIGURATION_GUIDE.md)
