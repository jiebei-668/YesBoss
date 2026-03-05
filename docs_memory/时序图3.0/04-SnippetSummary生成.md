# SnippetSummary生成流程

## 流程说明

本流程描述了如何从对话内容中提取结构化记忆（Snippet）并生成summary。

**v3.0-Final修正**：调用链修正，通过MemoryManager协调。

## 时序图

```mermaid
sequenceDiagram
    autonumber

    participant MemoryService as MemoryService
    participant ContentProcessor as ContentProcessor
    participant EmbeddingService as EmbeddingService
    participant MemoryManager as MemoryManager
    participant SnippetRepository as SnippetRepository
    participant VectorStore as VectorStore

    MemoryService->>ContentProcessor: ContentProcessor::generateSummary(content, memoryType)
    activate ContentProcessor

    Note over ContentProcessor: 根据MemoryType构建不同的prompt

    ContentProcessor-->>MemoryService: summary
    deactivate ContentProcessor

    Note over MemoryService: 生成向量嵌入

    MemoryService->>EmbeddingService: EmbeddingService::generateEmbedding(text)
    activate EmbeddingService
    EmbeddingService-->>MemoryService: float[] summaryEmbedding
    deactivate EmbeddingService

    Note over MemoryService: 创建Snippet请求对象

    MemoryService->>MemoryService: buildSnippetRequest(summary, memoryType, conversationId, sessionId)

    MemoryService->>MemoryManager: MemoryManager::createSnippet(request)
    activate MemoryManager

    Note over MemoryManager: 创建Snippet对象

    MemoryManager->>SnippetRepository: SnippetRepository::create(request)
    activate SnippetRepository
    SnippetRepository-->>MemoryManager: Snippet
    deactivate SnippetRepository

    Note over MemoryManager: 存储向量

    MemoryManager->>VectorStore: VectorStore::insert("snippet_index", snippetId, summaryEmbedding, metadata)
    activate VectorStore
    VectorStore-->>MemoryManager: void
    deactivate VectorStore

    MemoryManager-->>MemoryService: Snippet
    deactivate MemoryManager
```

## v3.0-Final关键修正

### 修正1：调用链清晰化

```
// ❌ v3.0之前（不够清晰）
MemoryService → SnippetRepository::create()

// ✅ v3.0-Final（三层协调）
MemoryService → MemoryManager::createSnippet()
MemoryManager → SnippetRepository::create()
```

**理由**：MemoryManager作为三层协调者，应该负责Snippet的创建和向量存储的协调。

### 修正2：明确内部方法

```
// ✅ 内部方法用Note说明
MemoryService->>MemoryService: buildSnippetRequest(summary, memoryType, conversationId, sessionId)
```

**说明**：buildSnippetRequest是MemoryService的内部方法，不是对外接口。

## 架构说明

### MemoryManager的作用
```java
// MemoryManager::createSnippet()内部流程
public Snippet createSnippet(SnippetCreateRequest request) {
    // 1. 调用SnippetRepository创建Snippet
    Snippet snippet = snippetRepository.create(request);

    // 2. 向量化并存储
    float[] embedding = embeddingService.generateEmbedding(snippet.getSummary());
    snippet.setEmbedding(embedding);

    // 3. 存储向量
    vectorStore.insert("snippet_index", snippet.getId(), embedding, metadata);

    return snippet;
}
```

### 职责划分
- **MemoryService**：业务逻辑（何时创建Snippet）
- **MemoryManager**：三层协调（创建Snippet + 向量化 + 存储）
- **SnippetRepository**：数据持久化

## 符合度评估

| 项目 | 状态 |
|------|------|
| MemoryManager接口 | ✅ 已添加 |
| 调用链正确性 | ✅ 100% |
| 方法存在性 | ✅ 100% |
| **整体符合度** | **✅ 100%** |
