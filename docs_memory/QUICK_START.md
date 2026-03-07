# Memory Persistence Module - Quick Start Guide

## Overview

The YesBoss Memory Persistence Module provides a comprehensive solution for extracting, storing, and retrieving conversational memories using a three-tier architecture (Resources, Snippets, Preferences) with vector-based semantic search capabilities.

## Features

- **Automatic Memory Extraction**: Extract structured memories from conversations
- **Three-Tier Architecture**: Resources (raw content) → Snippets (structured memories) → Preferences (aggregated insights)
- **Vector-Based Search**: Semantic similarity search using embeddings
- **Dual Backend Support**: SQLite with sqlite-vec or PostgreSQL with pgvector
- **Batch Processing**: Efficient batch vectorization and memory extraction
- **Flexible Triggers**: Interval-based, epoch-based, and conversation-round-based triggers

## Architecture

```
┌─────────────────────────────────────────────────┐
│         MemoryQueryService（统一查询）            │
├─────────────────────────────────────────────────┤
│  MemoryManager（三层关联管理）                    │
├──────────────┬──────────────┬───────────────────┤
│  Resource    │   Snippet    │   Preference       │
│  Repository  │  Repository  │   Repository      │
│  (对话资源)  │ (结构化记忆)  │   (偏好主题)      │
├──────────────┴──────────────┴───────────────────┤
│         VectorStore（向量存储抽象）               │
├──────────────────┬──────────────────────────────┤
│  SQLiteVecStore  │  PostgreSQLPgVectorStore     │
└──────────────────┴──────────────────────────────┘
```

## Getting Started

### 1. Dependencies

Add the following dependencies to your `pom.xml`:

```xml
<dependencies>
    <!-- Memory Module Core -->
    <dependency>
        <groupId>tech.yesboss</groupId>
        <artifactId>memory-core</artifactId>
        <version>${project.version}</version>
    </dependency>

    <!-- SQLite with sqlite-vec (default) -->
    <dependency>
        <groupId>org.xerial</groupId>
        <artifactId>sqlite-jdbc</artifactId>
        <version>3.42.0.0</version>
    </dependency>
    <dependency>
        <groupId>io.github.absolute-zero</groupId>
        <artifactId>sqlite-vec</artifactId>
        <version>0.1.30</version>
    </dependency>

    <!-- PostgreSQL with pgvector (optional) -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <version>42.6.0</version>
    </dependency>

    <!-- Spring Data JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
</dependencies>
```

### 2. Configuration

Create or update `application-memory.yml` in your `src/main/resources/`:

```yaml
memory:
  enabled: true

  # Vector Store Configuration
  vector-store:
    type: sqlite  # or postgresql
    embedding-dimension: 1536
    similarity-threshold: 0.7

  # Embedding Service Configuration
  embedding:
    provider: zhipu  # or anthropic, gemini, openai
    model: embedding-2
    batch-size: 100
    timeout: 30000  # 30 seconds

  # Content Processing Configuration
  content-processor:
    max-segment-length: 2000
    min-segment-length: 100
    abstract-max-length: 200

  # Batch Processing Configuration
  batch-processing:
    enabled: true
    interval: 60000  # 1 minute
    batch-size: 100
    max-retries: 3

  # Trigger Configuration
  triggers:
    interval:
      enabled: true
      cron: "0 */5 * * * ?"  # Every 5 minutes
    epoch-max:
      enabled: true
      max-resources: 1000
    conversation-round:
      enabled: true
      trigger-rounds: [5, 10]  # Trigger at rounds 5 and 10

  # Monitoring Configuration
  monitoring:
    enabled: true
    metrics-export: prometheus
    alert-threshold:
      error-rate: 0.05
      latency-p99: 1000  # 1 second

# Database Configuration
spring:
  datasource:
    url: jdbc:sqlite:data/yesboss.db
    driver-class-name: org.sqlite.JDBC
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
```

### 3. Basic Usage

#### 3.1 Inject MemoryService

```java
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.domain.message.UnifiedMessage;
import org.springframework.stereotype.Service;

@Service
public class MyConversationService {

    private final MemoryService memoryService;

    public MyConversationService(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    public void processConversation(List<UnifiedMessage> messages,
                                   String conversationId,
                                   String sessionId) {
        // Extract memories from conversation
        List<Resource> resources = memoryService.extractFromMessages(
            messages,
            conversationId,
            sessionId
        );

        System.out.println("Extracted " + resources.size() + " resources");
    }
}
```

#### 3.2 Manual Memory Extraction

```java
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.memory.model.Resource;
import tech.yesboss.memory.model.Snippet;
import tech.yesboss.memory.processor.ConversationSegment;
import tech.yesboss.memory.model.Snippet.MemoryType;

@Service
public class ManualMemoryExtractor {

    private final MemoryService memoryService;

    // Step 1: Concatenate conversation content
    public String concatenateContent(List<UnifiedMessage> messages) {
        return memoryService.concatenateConversationContent(messages);
    }

    // Step 2: Segment conversation by topics
    public List<ConversationSegment> segmentConversation(String content) {
        return memoryService.segmentConversation(content);
    }

    // Step 3: Generate abstract for each segment
    public String generateAbstract(String segmentContent) {
        return memoryService.generateSegmentAbstract(segmentContent);
    }

    // Step 4: Create resource
    public Resource createResource(String conversationId,
                                  String sessionId,
                                  String content,
                                  String abstractText) {
        return memoryService.buildResource(
            conversationId,
            sessionId,
            content,
            abstractText
        );
    }

    // Step 5: Extract structured memories
    public List<Snippet> extractMemories(String resourceContent) {
        return memoryService.extractStructuredMemories(resourceContent);
    }

    // Step 6: Extract specific memory type
    public List<String> extractByType(String content, MemoryType type) {
        return memoryService.extractMemoriesByType(content, type);
    }
}
```

#### 3.3 Querying Memories

```java
import tech.yesboss.memory.query.MemoryQueryService;
import tech.yesboss.memory.model.AgenticRagResult;
import tech.yesboss.memory.model.MemoryChain;

@Service
public class MemoryQueryHandler {

    private final MemoryQueryService queryService;

    // Agentic RAG query with three-layer retrieval
    public AgenticRagResult queryMemories(String userQuery,
                                         String conversationId,
                                         int topK) {
        return queryService.agenticRagQuery(
            userQuery,
            conversationId,
            topK,
            0.7  // similarity threshold
        );
    }

    // Get memory chain for context
    public List<MemoryChain> getMemoryChain(String conversationId,
                                           int depth) {
        return queryService.getMemoryChain(conversationId, depth);
    }

    // Simple similarity search
    public List<Resource> searchSimilar(String queryText,
                                       int topK) {
        return queryService.searchResources(queryText, topK);
    }
}
```

### 4. Automatic Memory Extraction

The memory module supports three types of automatic triggers:

#### 4.1 Interval-Based Trigger

Extracts memories at regular intervals:

```yaml
memory:
  triggers:
    interval:
      enabled: true
      cron: "0 */5 * * * ?"  # Every 5 minutes
```

#### 4.2 Epoch-Max Trigger

Triggers when a certain number of resources accumulate:

```yaml
memory:
  triggers:
    epoch-max:
      enabled: true
      max-resources: 1000  # Trigger every 1000 resources
```

#### 4.3 Conversation-Round Trigger

Triggers at specific conversation rounds:

```yaml
memory:
  triggers:
    conversation-round:
      enabled: true
      trigger-rounds: [5, 10, 15]  # Trigger at rounds 5, 10, 15
```

### 5. Batch Processing

Process pending resources and generate embeddings:

```java
import tech.yesboss.memory.service.MemoryService;
import tech.yesboss.memory.service.MemoryService.BatchEmbeddingResult;

@Service
public class BatchProcessor {

    private final MemoryService memoryService;

    // Process pending resources
    public int processPending() {
        return memoryService.processPendingResources();
    }

    // Batch vectorization
    public BatchEmbeddingResult vectorizeAll() {
        return memoryService.processBatchEmbedding();
    }

    public void printBatchReport(BatchEmbeddingResult result) {
        System.out.println("Vectorized " + result.getTotalCount() + " items:");
        System.out.println("  Resources: " + result.getResourceCount());
        System.out.println("  Snippets: " + result.getSnippetCount());
        System.out.println("  Preferences: " + result.getPreferenceCount());
        System.out.println("  Success: " + result.getSuccessCount());
        System.out.println("  Failures: " + result.getFailureCount());
        System.out.println("  Time: " + result.getProcessingTimeMs() + "ms");
    }
}
```

### 6. Monitoring

Check memory module status:

```java
import tech.yesboss.memory.monitoring.MemoryMonitor;
import tech.yesboss.memory.monitoring.MemoryMonitor.Metrics;

@Service
public class MemoryHealthChecker {

    private final MemoryMonitor monitor;

    public void checkHealth() {
        if (monitor.isHealthy()) {
            Metrics metrics = monitor.getMetrics();
            System.out.println("Memory Module Status:");
            System.out.println("  Total Resources: " + metrics.getTotalResources());
            System.out.println("  Total Snippets: " + metrics.getTotalSnippets());
            System.out.println("  Success Rate: " + metrics.getSuccessRate());
            System.out.println("  Avg Latency: " + metrics.getAverageLatency() + "ms");
        } else {
            System.out.println("Memory Module is unhealthy!");
        }
    }
}
```

### 7. Error Handling

```java
import tech.yesboss.memory.service.MemoryServiceException;

@Service
public class SafeMemoryExtractor {

    private final MemoryService memoryService;

    public void safeExtract(List<UnifiedMessage> messages,
                           String conversationId,
                           String sessionId) {
        try {
            List<Resource> resources = memoryService.extractFromMessages(
                messages, conversationId, sessionId
            );
            System.out.println("Successfully extracted " + resources.size() + " memories");
        } catch (MemoryServiceException e) {
            System.err.println("Memory extraction failed: " + e.getMessage());
            // Handle error: retry, log, notify, etc.
        }
    }

    public boolean isMemoryServiceAvailable() {
        return memoryService.isAvailable();
    }
}
```

## Next Steps

- Read [API Documentation](./API_DOCUMENTATION.md) for detailed API reference
- Check [Usage Examples](./USAGE_EXAMPLES.md) for more advanced examples
- See [Configuration Guide](./CONFIGURATION_GUIDE.md) for advanced configuration
- Review [记忆持久化模块v3.0.md](./记忆持久化模块v3.0.md) for architecture details

## Troubleshooting

### Problem: Memory extraction is slow

**Solution**: Enable batch processing and adjust batch size:
```yaml
memory:
  batch-processing:
    enabled: true
    batch-size: 200  # Increase for better throughput
```

### Problem: Low quality search results

**Solution**: Adjust similarity threshold and embedding model:
```yaml
memory:
  vector-store:
    similarity-threshold: 0.8  # Increase for stricter matching
  embedding:
    model: embedding-3  # Use better model
```

### Problem: Database connection errors

**Solution**: Configure connection pool:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
```

## Support

For issues or questions:
- Check logs in `logs/yesboss.log`
- Enable debug logging: `logging.level.tech.yesboss.memory=DEBUG`
- Review documentation in `docs_memory/`
