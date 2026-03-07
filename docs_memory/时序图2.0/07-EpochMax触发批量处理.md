# 07-EpochMax触发批量处理 - 简化版

## 核心流程

```mermaid
sequenceDiagram
    participant MessageListener
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

    MessageListener->>TriggerService: TriggerService::checkEpochMaxCondition()
    TriggerService->>ChatMessageRepository: TriggerService::countUnprocessed()
    ChatMessageRepository-->>TriggerService: long count

    alt count >= epochMax
        TriggerService->>ChatMessageRepository: TriggerService::getUnprocessedMessages(0, epochMax)
        ChatMessageRepository-->>TriggerService: List<ChatMessage>

        TriggerService->>MemoryService: MemoryService::processBatch(messages)

        par 并行处理
            MemoryService->>ContentProcessor: ContentProcessor::batchGenerateCaptions(contents)
            MemoryService->>EmbeddingService: EmbeddingService::batchGenerateEmbeddings(texts)
        and
            MemoryService->>ContentProcessor: ContentProcessor::batchGenerateSummaries(contents, types)
            MemoryService->>EmbeddingService: EmbeddingService::batchGenerateEmbeddings(texts)
        end

        MemoryService->>MemoryManager: MemoryManager::saveResources(resources)
        MemoryManager->>ResourceRepository: ResourceRepository::saveAll(resources)
        MemoryManager->>VectorStore: VectorStore::bulkInsert("resource_index", data)

        MemoryService->>MemoryManager: MemoryManager::createSnippets(requests)
        MemoryManager->>SnippetRepository: SnippetRepository::createAll(requests)

        MemoryService->>MemoryManager: MemoryManager::createOrUpdatePreferences(requests)
        MemoryManager->>PreferenceRepository: PreferenceRepository::upsertAll(requests)

        TriggerService->>ChatMessageRepository: ChatMessageRepository::markAsProcessed(messageIds)
    end
```

## 关键接口

### TriggerService
- checkEpochMaxCondition()
- getUnprocessedMessages(intervalMs, epochMax)
- countUnprocessed()

### ChatMessageRepository
- findUnprocessedMessages(intervalMs, epochMax)
- markAsProcessed(messageIds)
- countUnprocessed()

### 其他接口同06号文件
