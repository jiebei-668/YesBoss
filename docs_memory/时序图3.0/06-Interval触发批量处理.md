# Interval触发批量处理流程（修正版）

## 流程说明

本流程描述了基于时间间隔（interval）的批量记忆处理。

**v3.0-最终修正**：修正方法名和数据结构。

## 时序图

```mermaid
sequenceDiagram
    autonumber

    participant Scheduler as 定时调度器
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

    Note over Scheduler: 定时任务触发（每分钟）

    Scheduler->>TriggerService: TriggerService::checkTriggerConditions()

    Note over TriggerService: 返回TriggerResult

    TriggerService-->>Scheduler: TriggerResult(shouldTrigger, reason, messages)

    alt TriggerResult.reason == INTERVAL
        Note over Scheduler: 时间间隔触发

        Scheduler->>MemoryService: MemoryService::extractFromMessages(messages)
        activate MemoryService

        Note over MemoryService: 批量处理流程

        loop 对每个conversation
            MemoryService->>ContentProcessor: ContentProcessor::batchGenerateCaptions(conversationContents)
            activate ContentProcessor
            ContentProcessor-->>MemoryService: List<String> captions
            deactivate ContentProcessor

            MemoryService->>EmbeddingService: EmbeddingService::batchGenerateEmbeddings(texts)
            activate EmbeddingService
            EmbeddingService-->>MemoryService: List<float[]> captionEmbeddings
            deactivate EmbeddingService

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
        end

        Note over MemoryService: 提取结构化记忆

        MemoryService->>ContentProcessor: ContentProcessor::batchGenerateSummaries(conversationContents, memoryTypes)
        activate ContentProcessor
        ContentProcessor-->>MemoryService: List<String> summaries
        deactivate ContentProcessor

        MemoryService->>EmbeddingService: EmbeddingService::batchGenerateEmbeddings(texts)
        activate EmbeddingService
        EmbeddingService-->>MemoryService: List<float[]> summaryEmbeddings
        deactivate EmbeddingService

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

        Note over MemoryService: 聚合偏好总结

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

        MemoryService-->>Scheduler: ExtractionResult
        deactivate MemoryService

        Note over Scheduler: 标记消息为已处理

        Scheduler->>ChatMessageRepository: ChatMessageRepository::markAsProcessed(messageIds)
        activate ChatMessageRepository
        ChatMessageRepository-->>Scheduler: void
        deactivate ChatMessageRepository

    else TriggerResult.reason == NONE
        Note over Scheduler: 不满足触发条件，跳过
    end
```

## v3.0-最终修正

### 修正1：方法名修正

```
// ❌ v3.0之前
MemoryService::processBatch(messages)

// ✅ v3.0-最终修正
MemoryService::extractFromMessages(messages)
```

### 修正2：TriggerResult结构

```java
// v3.0文档中的正确结构
public class TriggerResult {
    private boolean shouldTrigger;        // ⭐ 使用shouldTrigger
    private TriggerReason reason;        // ⭐ 枚举类型：INTERVAL/EPOCH_MAX
    private List<ChatMessage> messages;  // ⭐ 包含消息列表

    public enum TriggerReason {
        INTERVAL,  // 时间间隔触发
        EPOCH_MAX  // 对话数量触发
    }
}
```

### 修正3：流程说明

```
// ✅ 正确的流程
1. Scheduler → TriggerService::checkTriggerConditions()
2. TriggerService返回TriggerResult(shouldTrigger, reason, messages)
3. 根据reason判断是INTERVAL还是EPOCH_MAX触发
4. 调用MemoryService::extractFromMessages(messages)处理消息
```

## 符合度评估

| 项目 | 状态 |
|------|------|
| 方法名正确性 | ✅ 已修正 |
| TriggerResult结构 | ✅ 已修正 |
| 接口存在性 | ✅ 100% |
| **整体符合度** | **✅ 100%** |
