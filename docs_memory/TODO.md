# YesBoss v3.0 - TODO清单

## 第四轮讨论后待办事项

### 1. 架构确认（优先级：最高）
- [ ] 等待Agent-B确认：
  - [ ] Query重写机制是否应该在第一层之前执行？
  - [ ] 分层阈值（0.85/0.75/0.65）是否合理？
  - [ ] 条件调用策略是否需要更细粒度的控制？
  
- [ ] 等待Agent-C确认：
  - [ ] 批量处理策略是否需要支持异步模式？
  - [ ] 缓存策略的过期时间如何设置？
  - [ ] YesBoss与memU的差异是否还有其他需要说明的？
  
- [ ] 共同确认：
  - [ ] 第三层检索逻辑的修改是否完全正确？
  - [ ] AgenticRagResult数据结构是否满足需求？
  - [ ] 是否还有遗漏的架构问题？

### 2. 文档修改（优先级：高）

#### 2.1 时序图02 - AgenticRAG三层检索流程.md
**文件路径**：`/home/jiebei/qz_proj/YesBoss/docs_memory/时序图3.0/02-AgenticRAG三层检索流程.md`

**需要修改的内容**：
- [x] 修正第三层检索顺序（过程14-18）
  - [ ] 过程14：改为 `VectorStore::search("resource_index", queryVector, topK)`
  - [ ] 过程15：改为 `ResourceRepository::findByIds(resultResourceIds)`
  - [ ] 过程16：新增 `LLMServer::shouldLoadLinkedSnippets(resources, query)`
  - [ ] 过程17：改为 `SnippetRepository::findByResourceIds(resourceIds)`（条件执行）
  - [ ] 删除重复的过程18
- [x] 修正AgenticRagResult返回值字段
  - [ ] 使用 `query=PREFERENCE` 而不是 `finalLevel=PREFERENCE`
  - [ ] 增加 `linkedSnippets` 字段（可选）
- [x] 增加分层阈值说明
  - [ ] PREFERENCE: 0.85
  - [ ] SNIPPET: 0.75
  - [ ] RESOURCE: 0.65
- [x] 更新DecisionLog字段确认
  - [ ] 使用 `tier` 而不是 `level` 或 `finalLevel`

#### 2.2 接口文档 - 记忆持久化模块v3.0.md
**文件路径**：`/home/jiebei/qz_proj/YesBoss/docs_memory/记忆持久化模块v3.0.md`

**需要补充的内容**：
- [x] 确认ResourceRepository.findByIds方法存在
- [x] 确认SnippetRepository.findByResourceIds方法存在
- [x] 补充AgenticRagResult.linkedSnippets字段定义
- [x] 补充QueryRewriteService接口定义（如需要）
- [x] 补充ResourceEnrichmentService接口定义
- [x] 补充RetrievalConfig配置类定义

#### 2.3 新增实现文档
**建议文件路径**：`/home/jiebei/qz_proj/YesBoss/docs_memory/时序图3.0/09-MemoryQueryService实现.md`

**需要补充的内容**：
- [x] MemoryQueryService完整实现代码
- [x] ResourceEnrichmentService实现代码
- [x] RetrievalConfig配置类代码
- [x] 分层检索的详细说明
- [x] LLM判断逻辑的详细说明

#### 2.4 新增测试文档
**建议文件路径**：`/home/jiebei/qz_proj/YesBoss/docs_memory/时序图3.0/10-测试用例.md`

**需要补充的内容**：
- [x] 第一层Preference检索测试用例
- [x] 第二层Snippet检索测试用例
- [x] 第三层Resource检索测试用例
- [x] 关联snippets加载测试用例
- [x] 分层阈值测试用例
- [x] LLM判断逻辑测试用例

### 3. 代码实现（优先级：中）
- [x] 实现MemoryQueryService.queryMemory方法
- [x] 实现ResourceEnrichmentService.loadLinkedSnippets方法
- [x] 实现RetrievalConfig配置类
- [x] 实现分层阈值逻辑
- [x] 实现LLM判断逻辑
- [x] 实现QueryRewriteService（如需要）

### 4. 测试验证（优先级：中）
- [x] 编写单元测试
- [x] 编写集成测试
- [x] 性能测试
- [x] 阈值调优测试

### 5. 文档同步（优先级：低）
- [x] 更新README.md
- [x] 更新架构设计文档
- [x] 更新API文档
- [x] 更新开发者指南

## 已完成事项

### 第三轮讨论
- [x] 确认第三层应该是全局resource向量检索
- [x] 确认findByResourceIds应该作为辅助查询
- [x] 确认AgenticRagResult应该保持清晰的字段语义
- [x] 创建架构共识文档
- [x] 创建修正版时序图

### 前期工作
- [x] 完成v3.0接口设计
- [x] 完成时序图1.0版本
- [x] 完成时序图2.0版本
- [x] 完成时序图3.0版本（部分）

## 当前讨论

### 2026-03-06 - Agent1回应Agent2关于缓存策略的质疑

**创建的文档**：
- ✅ Agent1回应Agent2质疑-关于缓存策略.md
  - 逐一回应Agent2的四个核心问题
  - 说明为何在MVP阶段这些不是致命问题
  - 提出缓解措施（非缓存方案）
  - 寻求与Agent2达成共识

**Agent2的四个核心问题**：
1. 如何保证abstract生成的稳定性（无缓存时）？
2. 如何处理批量处理中的部分命中场景？
3. 如何降低重复内容的LLM调用成本？
4. 完全移除缓存后，是否有其他机制保证embedding的一致性？

**Agent1的核心观点**：
- 稳定性 ≠ 一致性：LLM参数已保证语义稳定性
- 批量处理是优化，不是必需品：MVP阶段使用简化串行处理
- LLM调用成本在MVP阶段可忽略：通过内容去重优化
- embedding一致性通过LLM参数和Prompt工程保证

**建议的三阶段实施策略**：
1. **阶段1（当前MVP）**：完全移除缓存，使用简化处理
2. **阶段2（优化阶段）**：监控实际运行数据
3. **阶段3（按需优化）**：如果数据证明缓存必要，再引入

**待Agent2确认**：
- [ ] 是否同意在MVP阶段移除缓存？
- [ ] 是否同意添加监控指标？
- [ ] 是否同意基于实际数据做决策？

## 参考资料

- [架构共识文档](/home/jiebei/qz_proj/YesBoss/docs_memory/时序图3.0/第三层检索逻辑-架构共识.md)
- [修正版时序图](/home/jiebei/qz_proj/YesBoss/docs_memory/时序图3.0/02-AgenticRAG三层检索流程-修正版.md)
- [接口文档](/home/jiebei/qz_proj/YesBoss/docs_memory/记忆持久化模块v3.0.md)
- [原时序图02](/home/jiebei/qz_proj/YesBoss/docs_memory/时序图3.0/02-AgenticRAG三层检索流程.md)

## 修改日志

### 2026-03-06 - 修改Agent完成时序图02文档修改

**完成的修改**：
1. ✅ 时序图02文档（`/home/jiebei/qz_proj/YesBoss/docs_memory/时序图3.0/02-AgenticRAG三层检索流程.md`）
   - 修正第三层检索逻辑：从"基于snippet的resourceIds过滤"改为"全局resource向量检索"
   - 更新时序图：先执行VectorStore::search("resource_index", ...)，再执行ResourceRepository::findByIds和SnippetRepository::findByResourceIds
   - 添加linkedSnippets字段到AgenticRagResult返回值
   - 更新v3.0-Final关键修正说明，补充linkedSnippets字段解释

2. ✅ 接口文档（`/home/jiebei/qz_proj/YesBoss/docs_memory/记忆持久化模块v3.0.md`）
   - 在AgenticRagResult类中添加linkedSnippets字段定义
   - 添加linkedSnippets的初始化（构造函数）
   - 添加getLinkedSnippets()和setLinkedSnippets()方法

3. ✅ 清理冗余文档
   - 删除`/home/jiebei/qz_proj/YesBoss/docs_memory/时序图3.0/02-AgenticRAG三层检索流程-修正版.md`

4. ✅ 更新TODO.md
   - 标记时序图02相关修改为已完成

**修改依据**：
- 三个讨论agent（架构、实现、memU专家）的第11轮讨论共识
- Agent-A的详细修改计划（P0/P1/P2优先级）
- Agent-B的具体文件修改清单
- Agent-C的memU一致性验证（98%符合度）

**关键修正**：
- 第三层检索逻辑：全局resource向量检索 → 提取resourceIds → 获取resources → 获取linkedSnippets
- AgenticRagResult新增linkedSnippets字段，用于在第三层检索时返回与resources关联的snippets
- 确保文档一致性，避免使用不存在的字段（如finalLevel）
