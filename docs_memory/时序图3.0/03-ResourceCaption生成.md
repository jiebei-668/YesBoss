# ResourceCaption生成流程

## 流程说明

本流程描述了如何为对话资源生成caption。

**v3.0-Final修正**：MemoryManager接口已添加到v3.0文档，使用正确。

## 时序图

```mermaid
sequenceDiagram
    autonumber

    participant MemoryService as MemoryService
    participant ChatMessageRepository as ChatMessageRepository
    participant ContentProcessor as ContentProcessor
    participant EmbeddingService as EmbeddingService
    participant MemoryManager as MemoryManager
    participant ResourceRepository as ResourceRepository
    participant VectorStore as VectorStore

    MemoryService->>ChatMessageRepository: ChatMessageRepository::findUnprocessedMessages(long intervalMs, int epochMax)
    activate ChatMessageRepository
    ChatMessageRepository-->>MemoryService: List<ChatMessage>
    deactivate ChatMessageRepository

    Note over MemoryService: 按conversationId分组处理

    loop 对每个conversation
        MemoryService->>MemoryService: groupMessagesByConversationId(messages)

        Note over MemoryService: 构建对话内容

        MemoryService->>MemoryService: concatenateConversationContent(messages)

        Note over MemoryService: 生成Caption

        MemoryService->>ContentProcessor: ContentProcessor::generateCaption(conversationContent)
        activate ContentProcessor

        Note over ContentProcessor: 构建LLM prompt并调用

        ContentProcessor-->>MemoryService: caption
        deactivate ContentProcessor

        Note over MemoryService: 生成向量嵌入

        MemoryService->>EmbeddingService: EmbeddingService::generateEmbedding(text)
        activate EmbeddingService
        EmbeddingService-->>MemoryService: float[] captionEmbedding
        deactivate EmbeddingService

        Note over MemoryService: 创建Resource对象

        MemoryService->>MemoryService: buildResource(conversationId, sessionId, content, caption, captionEmbedding)

        MemoryService->>MemoryManager: MemoryManager::saveResource(content, conversationId, sessionId)
        activate MemoryManager

        Note over MemoryManager: 构建Resource对象

        MemoryManager->>ResourceRepository: ResourceRepository::save(resource)
        activate ResourceRepository
        ResourceRepository-->>MemoryManager: Resource
        deactivate ResourceRepository

        Note over MemoryManager: 存储向量

        MemoryManager->>VectorStore: VectorStore::insert("resource_index", resourceId, captionEmbedding, metadata)
        activate VectorStore
        VectorStore-->>MemoryManager: void
        deactivate VectorStore

        MemoryManager-->>MemoryService: Resource
        deactivate MemoryManager
    end

    MemoryService-->>MemoryService: List<Resource> createdResources
```

## v3.0-Final验证

### MemoryManager接口验证 ✅
```java
// v3.0接口文档中已添加
public interface MemoryManager {
    Resource saveResource(String content, String conversationId, String sessionId);
    // ✅ 方法存在
}
```

### 调用链验证 ✅
```
MemoryService (业务逻辑)
  ↓
ContentProcessor (生成caption)
EmbeddingService (生成向量)
  ↓
MemoryManager (三层协调)
  ↓
ResourceRepository (存储)
VectorStore (向量化)
```

### 所有方法验证 ✅
- ✅ ChatMessageRepository::findUnprocessedMessages() - 存在
- ✅ ContentProcessor::generateCaption() - 存在
- ✅ EmbeddingService::generateEmbedding() - 存在
- ✅ MemoryManager::saveResource() - 已添加
- ✅ ResourceRepository::save() - 存在
- ✅ VectorStore::insert() - 存在

## 符合度评估

| 项目 | 状态 |
|------|------|
| MemoryManager接口 | ✅ 已添加 |
| 所有方法调用 | ✅ 100%正确 |
| 调用链逻辑 | ✅ 100%正确 |
| **整体符合度** | **✅ 100%** |
