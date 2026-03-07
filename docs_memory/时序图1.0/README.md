# YesBoss记忆模块时序图总览

## 文档说明
本文档提供了YesBoss记忆模块的8个核心时序图，用于审查接口设计是否满足功能需求。

---

## 📋 时序图清单

### 核心流程（5个）

#### 1. 自动触发记忆提取流程
**文件**: `01-自动触发记忆提取流程.mmd`

**功能**: 定时检查触发条件（interval/epoch_max），自动提取记忆并持久化

**关键接口**:
- `TriggerService::checkTriggerConditions()`
- `ChatMessageRepository::getUnprocessedMessages(interval, epochMax)`
- `CaptionGenerator::generateCaption(content, modality)`
- `SummaryGenerator::generateSummary(content, memoryType)`
- `EmbeddingService::generateEmbedding(text)`
- `ResourceRepository::save(resource, caption, embedding)`
- `SnippetRepository::save(snippet, summary, embedding, resourceId)`
- `PreferenceRepository::updateSummary(preferenceId, updatedSummary, embedding)`

---

#### 2. Agentic RAG三层检索流程
**文件**: `02-AgenticRAG三层检索流程.mmd`

**功能**: 基于memU的Agentic RAG实现，分层检索记忆信息

**关键接口**:
- `MemoryQueryService::agenticRagRetrieve(query, topK)`
- `EmbeddingService::generateEmbedding(query)`
- `PreferenceRepository::findBySimilarity(queryVector, topK)`
- `SnippetRepository::findBySimilarity(queryVector, topK)`
- `ResourceRepository::findBySimilarity(queryVector, topK)`
- `LlmClient::decideIfRetrievalNeeded(query, retrievedContent)`
- `ResultAssembler::formatPreferenceContent(preferences)`
- `ResultAssembler::assembleFinalResult(preferences, snippets, resources)`

**LLM判断逻辑**:
- Tier 1: Preference检索后判断是否足够
- Tier 2: Snippet检索后判断是否足够
- Tier 3: Resource检索（不判断）

---

#### 3. Resource Caption生成
**文件**: `03-ResourceCaption生成.mmd`

**功能**: 为不同modality的Resource生成caption

**关键接口**:
- `CaptionGenerator::generateCaption(content, modality, metadata)`
- `CaptionGenerator::segmentConversation(messages, segmentSize)`
- `CaptionGenerator::mergeSegmentCaptions(segmentCaptions)`
- `CaptionGenerator::parseCaptionFromResponse(response)`
- `CaptionGenerator::extractVideoFrames(videoPath)`
- `CaptionGenerator::cleanTranscript(transcript)`
- `VisionApiClient::analyzeImage(imagePath)`
- `VisionApiClient::analyzeVideoFrames(frames)`
- `AudioTranscriptionService::transcribe(audioPath)`

**支持模态**: conversation, document, image, video, audio

---

#### 4. Snippet Summary生成
**文件**: `04-SnippetSummary生成.mmd`

**功能**: 从Resource中提取Snippet并生成summary

**关键接口**:
- `SummaryGenerator::generateSummary(resourceContent, memoryTypes)`
- `SummaryGenerator::buildPromptForMemoryType(memoryType, content)`
- `SummaryGenerator::parseMemoryItemsFromXML(xmlResponse)`
- `SummaryGenerator::validateItem(item, memoryType)`
- `SummaryGenerator::deduplicateAndMergeSnippets(snippets)`
- `CategoryMapper::mapCategories(categoryNames)`
- `SnippetRepository::save(snippet, embedding, preferenceIds)`

**支持类型**: profile, event, knowledge, behavior, skill, tool

---

#### 5. Preference Summary更新
**文件**: `05-PreferenceSummary更新.mmd`

**功能**: 将新Snippet合并到Preference summary

**关键接口**:
- `PreferenceRepository::findById(preferenceId)`
- `SnippetRepository::getSnippetsByPreference(preferenceId)`
- `SummaryGenerator::updatePreferenceSummary(preference, newSnippets)`
- `SummaryGenerator::buildCategorySummaryPrompt(preference, newSnippets)`
- `SummaryGenerator::cleanMarkdownSummary(markdownSummary)`
- `EmbeddingService::generateEmbedding(text)`
- `PreferenceRepository::updateSummary(preferenceId, summary, embedding)`
- `PreferenceRepository::incrementSnippetCount(preferenceId, count)`

**更新策略**: 仅使用add/update操作，不删除信息

---

### 批量处理（3个）

#### 6. Interval触发批量处理
**文件**: `06-Interval触发批量处理.mmd`

**功能**: 时间间隔超过阈值时批量处理对话

**关键接口**:
- `TriggerService::checkIntervalTrigger()`
- `ChatMessageRepository::findOldestUnprocessedMessage()`
- `ChatMessageRepository::getMessagesInTimeRange(startTime, endTime)`
- `BatchMemoryProcessor::processBatch(messages)`
- `BatchMemoryProcessor::groupMessagesBySession(messages)`
- `ResourceService::batchCreateResources(sessionMessages)`
- `SnippetService::batchExtractSnippets(resources)`
- `PreferenceService::batchUpdatePreferences(snippets)`
- `ChatMessageRepository::batchMarkAsProcessed(messageIds)`

---

#### 7. EpochMax触发批量处理
**文件**: `07-EpochMax触发批量处理.mmd`

**功能**: 未处理对话超过epochMax时批量处理

**关键接口**:
- `TriggerService::checkEpochMaxTrigger()`
- `ChatMessageRepository::countUnprocessedMessages()`
- `ChatMessageRepository::getRecentUnprocessedMessages(epochMax)`
- `ResourceService::createResourceFromSession(sessionMessages)`
- `ResourceService::generateCaptionForResource(sessionMessages)`
- `ResourceService::summarizeSegment(segment)`
- `SnippetService::extractSnippetsFromResource(resource)`
- `SnippetService::extractMemoryTypes(resource.content)`
- `EmbeddingService::batchGenerateEmbeddings(texts)`

---

#### 8. 向量化批处理
**文件**: `08-向量化批处理.mmd`

**功能**: 批量生成embedding并更新向量库

**关键接口**:
- `EmbeddingQueue::getBatchItems(batchSize)`
- `EmbeddingService::processEmbeddingQueue()`
- `EmbeddingService::categorizeItems(items)`
- `EmbeddingService::extractTexts(items)`
- `EmbeddingService::groupResourceByModality(resources)`
- `EmbeddingService::groupSnippetByMemoryType(snippets)`
- `LlmClient::batchEmbed(texts, model)`
- `VectorStore::bulkInsert(indexName, ids, embeddings, metadata)`
- `ResourceRepository::batchUpdateEmbedding(resourceIds, embeddings)`
- `SnippetRepository::batchUpdateEmbedding(snippetIds, embeddings)`
- `PreferenceRepository::batchUpdateEmbedding(preferenceIds, embeddings)`
- `MetricsCollector::recordEmbeddingMetrics(batchResult)`

---

## 🔍 接口审查清单

### 新增接口（需要实现）

#### 配置类
- [ ] `TriggerConfig`: interval和epoch_max配置
- [ ] `MemoryExtractionConfig`: 记忆提取配置
- [ ] `CaptionConfig`: Caption生成配置
- [ ] `SummaryConfig`: Summary生成配置
- [ ] `EmbeddingConfig`: Embedding配置

#### 核心服务
- [ ] `TriggerService`: 触发条件检查
- [ ] `CaptionGenerator`: 资源描述生成
- [ ] `SummaryGenerator`: 摘要生成
- [ ] `MemoryExtractor`: 记忆提取器
- [ ] `BatchMemoryProcessor`: 批量处理器

#### 辅助服务
- [ ] `VisionApiClient`: 视觉API客户端
- [ ] `AudioTranscriptionService`: 音频转写服务
- [ ] `CategoryMapper`: 分类映射器
- [ ] `ResultAssembler`: 结果组装器
- [ ] `MetricsCollector`: 指标收集器

#### Repository扩展
- [ ] `ResourceRepository::batchCreateResources(resources)`
- [ ] `ResourceRepository::batchUpdateEmbedding(resourceIds, embeddings)`
- [ ] `SnippetRepository::batchExtractSnippets(resources)`
- [ ] `SnippetRepository::batchSaveSnippets(snippets)`
- [ ] `SnippetRepository::batchUpdateEmbedding(snippetIds, embeddings)`
- [ ] `PreferenceRepository::batchUpdatePreferences(snippets)`
- [ ] `PreferenceRepository::batchUpdateEmbedding(preferenceIds, embeddings)`

### 已有接口（需要确认）

#### 从之前设计继承
- [ ] `ResourceRepository::save(resource, caption, embedding)`
- [ ] `SnippetRepository::save(snippet, summary, embedding, resourceId)`
- [ ] `PreferenceRepository::updateSummary(preferenceId, summary, embedding)`
- [ ] `EmbeddingService::generateEmbedding(text)`
- [ ] `VectorStore::insert(indexName, id, embedding, metadata)`

---

## 🎯 审查要点

### 1. 接口完整性
- [ ] 检查所有时序图中调用的接口是否都已定义
- [ ] 检查接口的参数是否匹配时序图中的调用
- [ ] 检查返回值类型是否正确

### 2. 数据结构
- [ ] Resource是否包含caption和embedding字段？
- [ ] Snippet是否包含summary、embedding、memoryType、happenedAt字段？
- [ ] Preference是否包含summary和embedding字段？
- [ ] 是否支持6种memory_type？

### 3. 提示词模板
- [ ] 是否包含了memU的prompt模板？
- [ ] 不同memory_type的prompt是否完整？
- [ ] Preference summary更新的prompt是否符合memU？

### 4. 触发机制
- [ ] interval和epoch_max配置是否正确？
- [ ] 触发条件检查逻辑是否完整？
- [ ] 批量处理的边界情况是否处理？

### 5. Agentic RAG
- [ ] 三层检索流程是否正确？
- [ ] LLM判断逻辑是否清晰？
- [ ] 查询重写机制是否实现？

### 6. 向量化策略
- [ ] Resource embedding基于caption？
- [ ] Snippet embedding基于summary？
- [ ] Preference embedding基于summary？
- [ ] 批量向量化是否高效？

---

## 📝 下一步行动

### 阶段1: 接口补充
根据时序图审查结果，补充缺失的接口定义

### 阶段2: 数据模型更新
更新Resource、Snippet、Preference的数据结构

### 阶段3: 实现核心功能
按照优先级实现核心接口

### 阶段4: 集成测试
编写测试验证时序图流程

---

**请审查这些时序图，确认接口设计是否满足功能需求。**
