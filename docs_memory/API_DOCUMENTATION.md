# Memory Persistence Module - API Documentation

## Table of Contents

1. [MemoryService](#1-memoryservice)
2. [MemoryQueryService](#2-memoryqueryservice)
3. [MemoryManager](#3-memorymanager)
4. [ContentProcessor](#4-contentprocessor)
5. [EmbeddingService](#5-embeddingservice)
6. [VectorStore](#6-vectorstore)
7. [Repositories](#7-repositories)
8. [Models](#8-models)
9. [Monitoring](#9-monitoring)

---

## 1. MemoryService

The main service for memory extraction and management.

### Core Methods

#### `extractFromMessages`

Extracts memories from a list of conversation messages.

**Signature:**
```java
List<Resource> extractFromMessages(
    List<UnifiedMessage> messages,
    String conversationId,
    String sessionId
) throws MemoryServiceException
```

**Parameters:**
- `messages` - List of conversation messages to process
- `conversationId` - Unique identifier for the conversation
- `sessionId` - Unique identifier for the session

**Returns:**
- `List<Resource>` - List of created resources

**Throws:**
- `MemoryServiceException` - If extraction fails

**Example:**
```java
List<UnifiedMessage> messages = getConversationMessages();
String conversationId = UUID.randomUUID().toString();
String sessionId = UUID.randomUUID().toString();

List<Resource> resources = memoryService.extractFromMessages(
    messages,
    conversationId,
    sessionId
);
```

---

#### `concatenateConversationContent`

Concatenates conversation content from messages.

**Signature:**
```java
String concatenateConversationContent(List<UnifiedMessage> messages)
```

**Parameters:**
- `messages` - List of conversation messages

**Returns:**
- `String` - Concatenated conversation content

**Example:**
```java
String content = memoryService.concatenateConversationContent(messages);
```

---

#### `segmentConversation`

Segments conversation into topic-based segments.

**Signature:**
```java
List<ConversationSegment> segmentConversation(String conversationContent)
    throws MemoryServiceException
```

**Parameters:**
- `conversationContent` - Full conversation content

**Returns:**
- `List<ConversationSegment>` - List of conversation segments

**Throws:**
- `MemoryServiceException` - If segmentation fails

**Example:**
```java
String content = "User: Hello\nAssistant: Hi there\nUser: How are you?";
List<ConversationSegment> segments = memoryService.segmentConversation(content);
```

---

#### `generateSegmentAbstract`

Generates abstract for a conversation segment.

**Signature:**
```java
String generateSegmentAbstract(String segmentContent)
    throws MemoryServiceException
```

**Parameters:**
- `segmentContent` - Content of the segment

**Returns:**
- `String` - Generated abstract (1-2 sentence summary)

**Throws:**
- `MemoryServiceException` - If abstract generation fails

**Example:**
```java
String abstractText = memoryService.generateSegmentAbstract(
    "The user discussed their preference for morning meetings and afternoon coding sessions."
);
// Returns: "User prefers morning meetings and afternoon coding."
```

---

#### `buildResource`

Builds a Resource object from segment data.

**Signature:**
```java
Resource buildResource(
    String conversationId,
    String sessionId,
    String segmentContent,
    String abstractText
)
```

**Parameters:**
- `conversationId` - Conversation identifier
- `sessionId` - Session identifier
- `segmentContent` - Segment content
- `abstractText` - Generated abstract

**Returns:**
- `Resource` - Resource object

**Example:**
```java
Resource resource = memoryService.buildResource(
    "conv-123",
    "session-456",
    "Full conversation content here...",
    "Summary of the conversation"
);
```

---

#### `extractStructuredMemories`

Extracts structured memories from resource content.

**Signature:**
```java
List<Snippet> extractStructuredMemories(String resourceContent)
    throws MemoryServiceException
```

**Parameters:**
- `resourceContent` - Content of the resource

**Returns:**
- `List<Snippet>` - List of extracted snippets

**Throws:**
- `MemoryServiceException` - If extraction fails

**Example:**
```java
List<Snippet> snippets = memoryService.extractStructuredMemories(
    "User name is John, works as a software engineer, prefers Python over Java."
);
// Returns snippets with types: PROFILE, KNOWLEDGE, PREFERENCE
```

---

#### `extractMemoriesByType`

Extracts structured memories for a specific memory type.

**Signature:**
```java
List<String> extractMemoriesByType(
    String resourceContent,
    Snippet.MemoryType memoryType
) throws MemoryServiceException
```

**Parameters:**
- `resourceContent` - Content of the resource
- `memoryType` - Type of memory to extract (PROFILE, EVENT, KNOWLEDGE, BEHAVIOR, SKILL, TOOL)

**Returns:**
- `List<String>` - List of extracted memory strings

**Throws:**
- `MemoryServiceException` - If extraction fails

**Example:**
```java
List<String> profiles = memoryService.extractMemoriesByType(
    content,
    Snippet.MemoryType.PROFILE
);
```

---

#### `associateWithPreferences`

Associates snippets with preferences.

**Signature:**
```java
Map<String, List<Snippet>> associateWithPreferences(List<Snippet> snippets)
    throws MemoryServiceException
```

**Parameters:**
- `snippets` - List of snippets to associate

**Returns:**
- `Map<String, List<Snippet>>` - Map of preference IDs to lists of associated snippets

**Throws:**
- `MemoryServiceException` - If association fails

**Example:**
```java
Map<String, List<Snippet>> associations = memoryService.associateWithPreferences(snippets);
associations.forEach((prefId, snippetList) -> {
    System.out.println("Preference " + prefId + " has " + snippetList.size() + " snippets");
});
```

---

#### `processBatchEmbedding`

Processes batch embedding for all items that need vectorization.

**Signature:**
```java
BatchEmbeddingResult processBatchEmbedding() throws MemoryServiceException
```

**Returns:**
- `BatchEmbeddingResult` - Result with processing statistics

**Throws:**
- `MemoryServiceException` - If batch processing fails

**Example:**
```java
BatchEmbeddingResult result = memoryService.processBatchEmbedding();
System.out.println("Processed " + result.getTotalCount() + " items in " +
    result.getProcessingTimeMs() + "ms");
```

---

#### `isAvailable`

Checks if the MemoryService is available and operational.

**Signature:**
```java
boolean isAvailable()
```

**Returns:**
- `boolean` - true if available, false otherwise

**Example:**
```java
if (memoryService.isAvailable()) {
    // Proceed with memory operations
} else {
    // Handle unavailability
}
```

---

## 2. MemoryQueryService

Service for querying memories with semantic search.

### Core Methods

#### `agenticRagQuery`

Performs Agentic RAG query with three-layer retrieval.

**Signature:**
```java
AgenticRagResult agenticRagQuery(
    String userQuery,
    String conversationId,
    int topK,
    double similarityThreshold
) throws MemoryQueryException
```

**Parameters:**
- `userQuery` - User's query text
- `conversationId` - Conversation identifier for context
- `topK` - Number of results to return
- `similarityThreshold` - Minimum similarity score (0.0 to 1.0)

**Returns:**
- `AgenticRagResult` - Query results with three-layer retrieval

**Throws:**
- `MemoryQueryException` - If query fails

**Example:**
```java
AgenticRagResult result = queryService.agenticRagQuery(
    "What are the user's programming preferences?",
    "conv-123",
    5,
    0.7
);

System.out.println("Relevant Resources: " + result.getResources().size());
System.out.println("Relevant Snippets: " + result.getSnippets().size());
System.out.println("Relevant Preferences: " + result.getPreferences().size());
```

---

#### `searchResources`

Performs similarity search on resources.

**Signature:**
```java
List<Resource> searchResources(String queryText, int topK)
    throws MemoryQueryException
```

**Parameters:**
- `queryText` - Query text
- `topK` - Number of results

**Returns:**
- `List<Resource>` - Similar resources ranked by similarity

**Throws:**
- `MemoryQueryException` - If search fails

**Example:**
```java
List<Resource> results = queryService.searchResources(
    "Python programming preferences",
    10
);
```

---

#### `searchSnippets`

Performs similarity search on snippets.

**Signature:**
```java
List<Snippet> searchSnippets(
    String queryText,
    Snippet.MemoryType memoryType,
    int topK
) throws MemoryQueryException
```

**Parameters:**
- `queryText` - Query text
- `memoryType` - Filter by memory type (null for all types)
- `topK` - Number of results

**Returns:**
- `List<Snippet>` - Similar snippets ranked by similarity

**Throws:**
- `MemoryQueryException` - If search fails

**Example:**
```java
List<Snippet> profiles = queryService.searchSnippets(
    "user background",
    Snippet.MemoryType.PROFILE,
    5
);
```

---

#### `getMemoryChain`

Gets memory chain for context reconstruction.

**Signature:**
```java
List<MemoryChain> getMemoryChain(String conversationId, int depth)
    throws MemoryQueryException
```

**Parameters:**
- `conversationId` - Conversation identifier
- `depth` - Depth of memory chain to retrieve

**Returns:**
- `List<MemoryChain>` - Memory chains for context

**Throws:**
- `MemoryQueryException` - If retrieval fails

**Example:**
```java
List<MemoryChain> chain = queryService.getMemoryChain("conv-123", 3);
chain.forEach(memory -> {
    System.out.println("Memory: " + memory.getContent());
    System.out.println("Related: " + memory.getRelatedMemories().size());
});
```

---

## 3. MemoryManager

Manages three-tier memory operations (Resource, Snippet, Preference).

### Core Methods

#### `saveResource`

Saves a resource with optional embedding.

**Signature:**
```java
Resource saveResource(Resource resource, boolean generateEmbedding)
    throws MemoryManagerException
```

**Parameters:**
- `resource` - Resource to save
- `generateEmbedding` - Whether to generate embedding

**Returns:**
- `Resource` - Saved resource with generated ID and timestamps

**Throws:**
- `MemoryManagerException` - If save fails

**Example:**
```java
Resource resource = new Resource();
resource.setContent("Conversation content...");
resource.setAbstract("Summary...");

Resource saved = memoryManager.saveResource(resource, true);
```

---

#### `saveSnippet`

Saves a snippet with resource association.

**Signature:**
```java
Snippet saveSnippet(Snippet snippet, String resourceId, boolean generateEmbedding)
    throws MemoryManagerException
```

**Parameters:**
- `snippet` - Snippet to save
- `resourceId` - Associated resource ID
- `generateEmbedding` - Whether to generate embedding

**Returns:**
- `Snippet` - Saved snippet with generated ID and timestamps

**Throws:**
- `MemoryManagerException` - If save fails

**Example:**
```java
Snippet snippet = new Snippet();
snippet.setSummary("User prefers Python");
snippet.setMemoryType(Snippet.MemoryType.PREFERENCE);

Snippet saved = memoryManager.saveSnippet(snippet, resourceId, true);
```

---

#### `updatePreference`

Updates preference with new snippet.

**Signature:**
```java
Preference updatePreference(String preferenceId, Snippet snippet)
    throws MemoryManagerException
```

**Parameters:**
- `preferenceId` - Preference ID to update
- `snippet` - New snippet to merge

**Returns:**
- `Preference` - Updated preference

**Throws:**
- `MemoryManagerException` - If update fails

**Example:**
```java
Preference updated = memoryManager.updatePreference(
    "pref-123",
    newSnippet
);
```

---

## 4. ContentProcessor

Processes content for segmentation and summarization.

### Core Methods

#### `segmentContent`

Segments content into topic-based segments.

**Signature:**
```java
List<ConversationSegment> segmentContent(String content, int maxLength)
    throws ContentProcessingException
```

**Parameters:**
- `content` - Content to segment
- `maxLength` - Maximum segment length

**Returns:**
- `List<ConversationSegment>` - List of segments

**Throws:**
- `ContentProcessingException` - If segmentation fails

**Example:**
```java
List<ConversationSegment> segments = contentProcessor.segmentContent(
    longConversationText,
    2000
);
```

---

#### `generateSummary`

Generates summary for content.

**Signature:**
```java
String generateSummary(String content, int maxLength)
    throws ContentProcessingException
```

**Parameters:**
- `content` - Content to summarize
- `maxLength` - Maximum summary length

**Returns:**
- `String` - Generated summary

**Throws:**
- `ContentProcessingException` - If summarization fails

**Example:**
```java
String summary = contentProcessor.generateSummary(
    longText,
    200
);
```

---

## 5. EmbeddingService

Generates embeddings for text.

### Core Methods

#### `generateEmbedding`

Generates embedding for single text.

**Signature:**
```java
float[] generateEmbedding(String text) throws EmbeddingException
```

**Parameters:**
- `text` - Text to embed

**Returns:**
- `float[]` - Embedding vector (1536 dimensions)

**Throws:**
- `EmbeddingException` - If generation fails

**Example:**
```java
float[] embedding = embeddingService.generateEmbedding("Hello world");
```

---

#### `generateBatchEmbeddings`

Generates embeddings for multiple texts.

**Signature:**
```java
List<float[]> generateBatchEmbeddings(List<String> texts)
    throws EmbeddingException
```

**Parameters:**
- `texts` - List of texts to embed

**Returns:**
- `List<float[]>` - List of embedding vectors

**Throws:**
- `EmbeddingException` - If generation fails

**Example:**
```java
List<String> texts = Arrays.asList("text1", "text2", "text3");
List<float[]> embeddings = embeddingService.generateBatchEmbeddings(texts);
```

---

## 6. VectorStore

Abstract interface for vector storage operations.

### Core Methods

#### `insert`

Inserts vector into store.

**Signature:**
```java
String insert(float[] vector, int dimension) throws VectorStoreException
```

**Parameters:**
- `vector` - Vector to insert
- `dimension` - Vector dimension

**Returns:**
- `String` - Vector ID

**Throws:**
- `VectorStoreException` - If insert fails

**Example:**
```java
float[] vector = embeddingService.generateEmbedding("text");
String vectorId = vectorStore.insert(vector, 1536);
```

---

#### `search`

Searches for similar vectors.

**Signature:**
```java
List<SearchResult> search(float[] queryVector, int topK)
    throws VectorStoreException
```

**Parameters:**
- `queryVector` - Query vector
- `topK` - Number of results

**Returns:**
- `List<SearchResult>` - Similar vectors with scores

**Throws:**
- `VectorStoreException` - If search fails

**Example:**
```java
float[] queryVector = embeddingService.generateEmbedding("search query");
List<SearchResult> results = vectorStore.search(queryVector, 10);
results.forEach(r -> {
    System.out.println("ID: " + r.getId() + ", Score: " + r.getScore());
});
```

---

#### `delete`

Deletes vector from store.

**Signature:**
```java
boolean delete(String vectorId) throws VectorStoreException
```

**Parameters:**
- `vectorId` - Vector ID to delete

**Returns:**
- `boolean` - True if deleted, false otherwise

**Throws:**
- `VectorStoreException` - If delete fails

**Example:**
```java
boolean deleted = vectorStore.delete("vector-123");
```

---

## 7. Repositories

### ResourceRepository

Repository for Resource entities.

**Key Methods:**
```java
Optional<Resource> findById(String id)
Page<Resource> findByConversationId(String conversationId, Pageable pageable)
List<Resource> findResourcesWithoutEmbedding()
List<Resource> saveAll(List<Resource> resources)
void deleteInBatch(List<Resource> resources)
```

### SnippetRepository

Repository for Snippet entities.

**Key Methods:**
```java
Optional<Snippet> findById(String id)
Page<Snippet> findByResourceId(String resourceId, Pageable pageable)
Page<Snippet> findByMemoryType(Snippet.MemoryType memoryType, Pageable pageable)
List<Snippet> findSnippetsWithoutEmbedding()
List<Snippet> saveAll(List<Snippet> snippets)
void deleteByResourceId(String resourceId)
```

### PreferenceRepository

Repository for Preference entities.

**Key Methods:**
```java
Optional<Preference> findById(String id)
Optional<Preference> findByName(String name)
List<Preference> findPreferencesWithoutEmbedding()
boolean updateSummaryAndEmbedding(String name, String newSummary)
List<Preference> saveAll(List<Preference> preferences)
void deleteByName(String name)
```

---

## 8. Models

### Resource

Represents a conversation resource.

**Fields:**
```java
private String id;              // UUID
private String conversationId;  // Foreign key
private String sessionId;       // Foreign key
private String content;         // TEXT (max 10000 chars)
private String abstractText;    // TEXT summary
private byte[] embedding;       // BLOB (6144 bytes for 1536-dim float32)
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
```

### Snippet

Represents a structured memory snippet.

**Fields:**
```java
private String id;              // UUID
private String resourceId;      // Foreign key to Resource
private String summary;         // TEXT structured memory
private MemoryType memoryType;  // ENUM: PROFILE, EVENT, KNOWLEDGE,
                                //       BEHAVIOR, SKILL, TOOL
private byte[] embedding;       // BLOB (6144 bytes)
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
```

**MemoryType Enum:**
```java
public enum MemoryType {
    PROFILE,      // User profile information
    EVENT,        // Events and occurrences
    KNOWLEDGE,    // Knowledge and facts
    BEHAVIOR,     // Behavioral patterns
    SKILL,        // Skills and capabilities
    TOOL          // Tool preferences
}
```

### Preference

Represents aggregated user preferences.

**Fields:**
```java
private String id;              // UUID
private String name;            // VARCHAR(100) unique
private String summary;         // TEXT classification summary
private byte[] embedding;       // BLOB (6144 bytes)
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
```

### AgenticRagResult

Result of Agentic RAG query.

**Fields:**
```java
private List<Resource> resources;
private List<Snippet> snippets;
private List<Preference> preferences;
private Map<String, Double> relevanceScores;
private List<DecisionLog> decisionLogs;
```

---

## 9. Monitoring

### MemoryMonitor

Monitors memory module health and performance.

**Key Methods:**
```java
boolean isHealthy()
Metrics getMetrics()
void recordOperation(String operation, long durationMs, boolean success)
```

**Metrics:**
```java
class Metrics {
    private long totalResources;
    private long totalSnippets;
    private long totalPreferences;
    private double successRate;
    private long averageLatency;
    private long p99Latency;
    private long errorCount;
}
```

**Example:**
```java
if (memoryMonitor.isHealthy()) {
    MemoryMonitor.Metrics metrics = memoryMonitor.getMetrics();
    System.out.println("Success Rate: " + metrics.getSuccessRate());
    System.out.println("P99 Latency: " + metrics.getP99Latency() + "ms");
}
```

---

## Exception Hierarchy

```
RuntimeException
└── MemoryException
    ├── MemoryServiceException
    ├── MemoryQueryException
    ├── MemoryManagerException
    ├── ContentProcessingException
    ├── EmbeddingException
    └── VectorStoreException
```

---

## Configuration Properties

### Memory Properties

```yaml
memory:
  enabled: true
  vector-store:
    type: sqlite|postgresql
    embedding-dimension: 1536
    similarity-threshold: 0.7
  embedding:
    provider: zhipu|anthropic|gemini|openai
    model: embedding-2
    batch-size: 100
    timeout: 30000
  content-processor:
    max-segment-length: 2000
    min-segment-length: 100
    abstract-max-length: 200
  batch-processing:
    enabled: true
    interval: 60000
    batch-size: 100
    max-retries: 3
```

---

For more information, see:
- [Quick Start Guide](./QUICK_START.md)
- [Usage Examples](./USAGE_EXAMPLES.md)
- [Configuration Guide](./CONFIGURATION_GUIDE.md)
