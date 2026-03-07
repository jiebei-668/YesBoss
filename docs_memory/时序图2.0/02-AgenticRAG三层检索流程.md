# AgenticRAG三层检索流程

## 流程说明

本流程描述了AgenticRAG（检索增强生成）的三层检索机制。系统从Preference层开始，逐层深入到Snippet和Resource层，并在每层使用LLM判断是否需要继续检索。

## 时序图

```mermaid
sequenceDiagram
    autonumber

    participant User as 用户
    participant MemoryService as MemoryService
    participant EmbeddingService as EmbeddingService
    participant PreferenceRepository as PreferenceRepository
    participant SnippetRepository as SnippetRepository
    participant ResourceRepository as ResourceRepository
    participant VectorStore as VectorStore

    User->>MemoryService: MemoryService::queryMemory(query, topK)
    activate MemoryService

    Note over MemoryService: 第一层：Preference检索

    MemoryService->>EmbeddingService: EmbeddingService::generateEmbedding(text)
    activate EmbeddingService
    EmbeddingService-->>MemoryService: float[] queryVector
    deactivate EmbeddingService

    MemoryService->>VectorStore: VectorStore::search("preference_index", queryVector, topK)
    activate VectorStore
    VectorStore-->>MemoryService: List<VectorSearchResult> preferenceResults
    deactivate VectorStore

    Note over MemoryService: 提取preferenceIds

    MemoryService->>PreferenceRepository: PreferenceRepository::findByIds(preferenceIds)
    activate PreferenceRepository
    PreferenceRepository-->>MemoryService: List<Preference>
    deactivate PreferenceRepository

    Note over MemoryService: LLM判断：Preference层结果是否充足

    alt Preference层结果充足
        MemoryService-->>User: AgenticRagResult (Preference层结果)
        deactivate MemoryService

    else 需要继续检索
        Note over MemoryService: 第二层：Snippet检索

        MemoryService->>VectorStore: VectorStore::search("snippet_index", queryVector, topK)
        activate VectorStore
        VectorStore-->>MemoryService: List<VectorSearchResult> snippetResults
        deactivate VectorStore

        MemoryService->>SnippetRepository: SnippetRepository::findByIds(snippetIds)
        activate SnippetRepository
        SnippetRepository-->>MemoryService: List<Snippet>
        deactivate SnippetRepository

        Note over MemoryService: LLM判断：Snippet层结果是否充足

        alt Snippet层结果充足
            MemoryService-->>User: AgenticRagResult (Snippet层结果)
            deactivate MemoryService

        else 需要继续检索
            Note over MemoryService: 第三层：Resource检索

            MemoryService->>SnippetRepository: SnippetRepository::findByResourceIds(resourceIds)
            activate SnippetRepository
            SnippetRepository-->>MemoryService: List<Snippet> linkedSnippets
            deactivate SnippetRepository

            MemoryService->>ResourceRepository: ResourceRepository::findByIds(resourceIds)
            activate ResourceRepository
            ResourceRepository-->>MemoryService: List<Resource>
            deactivate ResourceRepository

            MemoryService->>VectorStore: VectorStore::search("resource_index", queryVector, topK)
            activate VectorStore
            VectorStore-->>MemoryService: List<VectorSearchResult> resourceResults
            deactivate VectorStore

            Note over MemoryService: 提取resultResourceIds

            MemoryService->>ResourceRepository: ResourceRepository::findByIds(resultResourceIds)
            activate ResourceRepository
            ResourceRepository-->>MemoryService: List<Resource>
            deactivate ResourceRepository

            MemoryService-->>User: AgenticRagResult (Resource层结果 + 关联信息)
            deactivate MemoryService
        end
    end
```

## 关键接口说明

### MemoryService::queryMemory
- **功能**：执行AgenticRAG三层检索
- **参数**：
  - query: 用户查询文本
  - topK: 每层返回的候选数量
- **返回**：AgenticRagResult 包含检索结果和决策日志
- **流程**：
  1. Preference层检索 → LLM判断
  2. Snippet层检索 → LLM判断
  3. Resource层检索 → 返回完整结果

### EmbeddingService::generateEmbedding
- **功能**：生成文本的向量嵌入
- **参数**：text 待向量化的文本
- **返回**：float[] 向量数组

### VectorStore::search
- **功能**：向量相似度搜索
- **参数**：
  - indexName: 索引名称（preference_index/snippet_index/resource_index）
  - queryVector: 查询向量
  - topK: 返回top-K结果
- **返回**：List<VectorSearchResult> 包含ID和相似度分数

### PreferenceRepository::findByIds
- **功能**：批量获取Preference对象
- **参数**：preferenceIds Preference ID列表
- **返回**：List<Preference> Preference对象列表

### SnippetRepository::findByIds
- **功能**：批量获取Snippet对象
- **参数**：snippetIds Snippet ID列表
- **返回**：List<Snippet> Snippet对象列表

### SnippetRepository::findByResourceIds
- **功能**：根据Resource ID查找关联的Snippet
- **参数**：resourceIds Resource ID列表
- **返回**：List<Snippet> 关联的Snippet列表

### ResourceRepository::findByIds
- **功能**：批量获取Resource对象
- **参数**：resourceIds Resource ID列表
- **返回**：List<Resource> Resource对象列表

## LLM决策逻辑

### 第一层：Preference层判断
- **输入**：用户query + top-K Preference的summary
- **判断依据**：
  - Preference summary是否直接回答了query？
  - 是否包含足够的上下文信息？
- **输出**：
  - SUFFICIENT: 返回Preference层结果
  - CONTINUE: 继续Snippet层检索

### 第二层：Snippet层判断
- **输入**：用户query + top-K Snippet的summary
- **判断依据**：
  - Snippet summary是否提供了足够的细节？
  - 是否需要原始对话内容来补充？
- **输出**：
  - SUFFICIENT: 返回Snippet层结果
  - CONTINUE: 继续Resource层检索

### 第三层：Resource层
- 直接返回结果，不再判断
- 包含：
  - Resource内容（对话caption）
  - 关联的Snippet信息
  - 关联的Preference信息

## 检索策略

### 向量检索策略
1. **Preference层**：基于preference summary的embedding
2. **Snippet层**：基于snippet summary的embedding
3. **Resource层**：基于conversation caption的embedding

### 相似度计算
- 使用余弦相似度（Cosine Similarity）
- 可配置相似度阈值（默认0.7）

### 结果聚合
- 每层返回top-K结果
- LLM在每层进行充足性判断
- 最终返回最详细层的结果

## 数据结构

### AgenticRagResult
```java
public class AgenticRagResult {
    private String query;
    private RetrievalLevel finalLevel;  // PREFERENCE/SNIPPET/RESOURCE
    private List<Preference> preferences;
    private List<Snippet> snippets;
    private List<Resource> resources;
    private List<DecisionLog> decisionLogs;  // LLM决策记录
    private boolean isSufficient;
}
```

### DecisionLog
```java
public class DecisionLog {
    private RetrievalLevel level;
    private String decision;  // SUFFICIENT/CONTINUE
    private String reasoning;
    private int candidateCount;
    private double similarityThreshold;
}
```

## 优化点

1. **缓存机制**：常用query的embedding可以缓存
2. **并行检索**：Preference/Snippet/Resource检索可以并行进行
3. **增量检索**：先检索少量结果，LLM判断后决定是否增加
4. **阈值调整**：根据历史数据动态调整相似度阈值
