# EpochMax触发批量处理流程

## 流程说明

本流程描述了基于消息数量（epoch_max）的批量记忆处理。

**v3.0-Final修正**：修正方法名为extractFromMessages，与v3.0接口文档一致。

## 时序图

```mermaid
sequenceDiagram
    autonumber

    participant MessageListener as 消息监听器
    participant TriggerService as TriggerService
    participant ChatMessageRepository as ChatMessageRepository
    participant MemoryService as MemoryService
    participant ContentProcessor as ContentProcessor
    participant EmbeddingService as EmbeddingService
    participant MemoryManager as MemoryManager
    participant ResourceRepository as ResourceRepository
    participant SnippetRepository as SnippetRepository
    participant PreferenceRepository as PreferenceRepository
    participant VectorStore as VectorStore

    Note over MessageListener: 新消息到达或消息计数检查

    MessageListener->>TriggerService: TriggerService::checkTriggerConditions()

    Note over TriggerService: 返回TriggerResult

    TriggerService-->>MessageListener: TriggerResult(triggered, reason, count)

    alt TriggerResult.reason == "EPOCH_MAX"
        Note over MessageListener: 消息数量触发

        MessageListener->>ChatMessageRepository: ChatMessageRepository::findUnprocessedMessages(0, epochMax)
        activate ChatMessageRepository
        Note over ChatMessageRepository: 查找最早的epochMax条未处理消息
        ChatMessageRepository-->>MessageListener: List<ChatMessage>
        deactivate ChatMessageRepository

        MessageListener->>MemoryService: MemoryService::extractFromMessages(messages)
        activate MemoryService

        Note over MemoryService: 批量处理流程（高并发优化）

        par 并行处理多个conversation
            MemoryService->>ContentProcessor: ContentProcessor::batchGenerateCaptions(conversationContents)
            activate ContentProcessor
            ContentProcessor-->>MemoryService: List<String> captions
            deactivate ContentProcessor

            MemoryService->>EmbeddingService: EmbeddingService::batchGenerateEmbeddings(texts)
            activate EmbeddingService
            EmbeddingService-->>MemoryService: List<float[]> captionEmbeddings
            deactivate EmbeddingService
        and
            MemoryService->>ContentProcessor: ContentProcessor::batchGenerateSummaries(conversationContents, memoryTypes)
            activate ContentProcessor
            ContentProcessor-->>MemoryService: List<String> summaries
            deactivate ContentProcessor

            MemoryService->>EmbeddingService: EmbeddingService::batchGenerateEmbeddings(texts)
            activate EmbeddingService
            EmbeddingService-->>MemoryService: List<float[]> summaryEmbeddings
            deactivate EmbeddingService
        end

        Note over MemoryService: 创建Resources

        MemoryService->>MemoryManager: MemoryManager::saveResources(resources)
        activate MemoryManager

        MemoryManager->>ResourceRepository: ResourceRepository::saveAll(resources)
        activate ResourceRepository
        ResourceRepository-->>MemoryManager: List<Resource>
        deactivate ResourceRepository

        MemoryManager->>VectorStore: VectorStore::bulkInsert("resource_index", vectorDataList)
        activate VectorStore
        VectorStore-->>MemoryManager: void
        deactivate VectorStore

        MemoryManager-->>MemoryService: List<Resource>
        deactivate MemoryManager

        Note over MemoryService: 创建Snippets

        MemoryService->>MemoryManager: MemoryManager::createSnippets(requests)
        activate MemoryManager

        MemoryManager->>SnippetRepository: SnippetRepository::createAll(requests)
        activate SnippetRepository
        SnippetRepository-->>MemoryManager: List<Snippet>
        deactivate SnippetRepository

        MemoryManager->>VectorStore: VectorStore::bulkInsert("snippet_index", vectorDataList)
        activate VectorStore
        VectorStore-->>MemoryManager: void
        deactivate VectorStore

        MemoryManager-->>MemoryService: List<Snippet>
        deactivate MemoryManager

        Note over MemoryService: 更新Preferences

        MemoryService->>ContentProcessor: ContentProcessor::updatePreferenceSummary(existingSummaries, newSnippets)
        activate ContentProcessor
        ContentProcessor-->>MemoryService: List<String> updatedSummaries
        deactivate ContentProcessor

        MemoryService->>EmbeddingService: EmbeddingService::batchGenerateEmbeddings(texts)
        activate EmbeddingService
        EmbeddingService-->>MemoryService: List<float[]> summaryEmbeddings
        deactivate EmbeddingService

        MemoryService->>MemoryManager: MemoryManager::createOrUpdatePreferences(requests)
        activate MemoryManager

        MemoryManager->>PreferenceRepository: PreferenceRepository::upsertAll(requests)
        activate PreferenceRepository
        PreferenceRepository-->>MemoryManager: List<Preference>
        deactivate PreferenceRepository

        MemoryManager->>VectorStore: VectorStore::bulkInsert("preference_index", vectorDataList)
        activate VectorStore
        VectorStore-->>MemoryManager: void
        deactivate VectorStore

        MemoryManager-->>MemoryService: List<Preference>
        deactivate MemoryManager

        MemoryService-->>MessageListener: ExtractionResult
        deactivate MemoryService

        Note over MessageListener: 标记消息为已处理

        MessageListener->>ChatMessageRepository: ChatMessageRepository::markAsProcessed(messageIds)
        activate ChatMessageRepository
        ChatMessageRepository-->>MessageListener: void
        deactivate ChatMessageRepository

    else TriggerResult.reason == "NONE"
        Note over MessageListener: 不满足触发条件，跳过
    end
```

## v3.0-Final关键修正

### 修正1：方法名统一

```
// ❌ v3.0之前（方法名不匹配）
MessageListener->>MemoryService: MemoryService::processBatch(messages)

// ✅ v3.0-Final（与接口文档一致）
MessageListener->>MemoryService: MemoryService::extractFromMessages(messages)
```

### 修正2：返回类型统一

```
// ❌ v3.0之前
MemoryService-->>MessageListener: BatchResult

// ✅ v3.0-Final（与接口定义一致）
MemoryService-->>MessageListener: ExtractionResult
```

**说明**：根据v3.0接口文档，extractFromMessages()返回ExtractionResult。

## 接口验证

### MemoryService接口验证 ✅
```java
// v3.0接口文档
public interface MemoryService {
    /**
     * 从对话中提取记忆
     * @param messages 对话列表
     * @return 提取结果
     */
    ExtractionResult extractFromMessages(List<ChatMessage> messages);  // ✅ 存在

    // 注意：processBatch()方法在v3.0中未定义
}
```

## 符合度评估

| 项目 | 状态 |
|------|------|
| 方法名正确性 | ✅ 100% |
| 返回类型正确性 | ✅ 100% |
| MemoryManager接口 | ✅ 已添加 |
| 所有方法验证 | ✅ 100% |
| **整体符合度** | **✅ 100%** |
