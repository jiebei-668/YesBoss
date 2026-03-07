# 06-Interval触发批量处理 - 简化版

## 核心流程

```mermaid
sequenceDiagram
    participant Scheduler
    participant TriggerService
    participant ChatMessageRepository
    participant MemoryService
    participant ContentProcessor
    participant EmbeddingService
    participant MemoryManager
    participant ResourceRepository
    participant SnippetRepository
    participant PreferenceRepository
    participant VectorStore

    Scheduler->>TriggerService: TriggerService::checkIntervalCondition()
    TriggerService->>ChatMessageRepository: ChatMessageRepository::findUnprocessedMessages(intervalMs, 0)
    ChatMessageRepository-->>TriggerService: List<ChatMessage>

    TriggerService->>MemoryService: MemoryService::processBatch(messages)

    loop 批量处理
        MemoryService->>ContentProcessor: ContentProcessor::batchGenerateCaptions(contents)
        ContentProcessor-->>MemoryService: List<String>

        MemoryService->>EmbeddingService: EmbeddingService::batchGenerateEmbeddings(texts)
        EmbeddingService-->>MemoryService: List<float[]>

        MemoryService->>MemoryManager: MemoryManager::saveResources(resources)
        MemoryManager->>ResourceRepository: ResourceRepository::saveAll(resources)
        ResourceRepository-->>MemoryManager: List<Resource>
        MemoryManager->>VectorStore: VectorStore::bulkInsert("resource_index", data)

        MemoryService->>ContentProcessor: ContentProcessor::batchGenerateSummaries(contents, types)
        MemoryService->>MemoryManager: MemoryManager::createSnippets(requests)
        MemoryManager->>SnippetRepository: SnippetRepository::createAll(requests)

        MemoryService->>MemoryManager: MemoryManager::createOrUpdatePreferences(requests)
        MemoryManager->>PreferenceRepository: PreferenceRepository::upsertAll(requests)
    end

    TriggerService->>ChatMessageRepository: ChatMessageRepository::markAsProcessed(messageIds)
```

## 关键接口

### ChatMessageRepository
- findUnprocessedMessages(intervalMs, epochMax)
- markAsProcessed(messageIds)

### ContentProcessor
- batchGenerateCaptions(contents)
- batchGenerateSummaries(contents, memoryTypes)

### EmbeddingService
- batchGenerateEmbeddings(texts)

### VectorStore
- bulkInsert(indexName, vectorDataList)

### MemoryService
- processBatch(messages)

### MemoryManager
- saveResources(resources)
- createSnippets(requests)
- createOrUpdatePreferences(requests)
