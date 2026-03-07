package tech.yesboss.memory.query;

import tech.yesboss.memory.model.*;

import java.util.List;

/**
 * 统一查询接口
 *
 * 提供跨层查询和高级搜索功能，支持AgenticRAG三层检索机制
 */
public interface MemoryQueryService {

    /**
     * AgenticRAG三层查询
     *
     * 使用LLM驱动的三层检索机制，从Preference层开始，
     * 逐层深入到Snippet和Resource层，并在每层使用LLM判断是否需要继续检索
     *
     * @param query 查询文本
     * @param topK 返回数量
     * @return 查询结果，包含三层检索结果和LLM决策历史
     */
    AgenticRagResult queryMemory(String query, int topK);

    /**
     * 跨层查询：根据查询文本查找相关的Resources
     *
     * @param query 查询文本
     * @param topK 返回前K个结果
     * @return Resource列表
     */
    List<Resource> findResourcesByQuery(String query, int topK);

    /**
     * 跨层查询：根据查询文本查找相关的Snippets
     *
     * @param query 查询文本
     * @param topK 返回前K个结果
     * @return Snippet列表
     */
    List<Snippet> findSnippetsByQuery(String query, int topK);

    /**
     * 跨层查询：根据查询文本查找相关的Preferences
     *
     * @param query 查询文本
     * @param topK 返回前K个结果
     * @return Preference列表
     */
    List<Preference> findPreferencesByQuery(String query, int topK);

    /**
     * 分层查询：从Preference开始，查找相关的Snippets，再查找Resources
     *
     * @param preferenceId Preference ID
     * @param query 查询文本
     * @param topK 返回前K个结果
     * @return Resource列表
     */
    List<Resource> findResourcesByPreference(String preferenceId, String query, int topK);

    /**
     * 分层查询：根据时间范围和类别查找完整的记忆链
     *
     * @param preferenceId Preference ID
     * @param startTime 开始时间戳
     * @param endTime 结束时间戳
     * @return 记忆链
     */
    MemoryChain findMemoryChainByPreferenceAndTime(String preferenceId, long startTime, long endTime);

    /**
     * 分层查询：根据会话ID查找完整的记忆链
     *
     * @param sessionId 会话ID
     * @return 记忆链列表
     */
    List<MemoryChain> findMemoryChainsBySessionId(String sessionId);

    /**
     * 模糊查询：根据关键词查找相关的记忆内容
     *
     * @param keyword 关键词
     * @param topK 返回前K个结果
     * @return Snippet列表
     */
    List<Snippet> fuzzySearchSnippets(String keyword, int topK);

    /**
     * 模糊查询：根据关键词和Preference查找相关的记忆内容
     *
     * @param keyword 关键词
     * @param preferenceId Preference ID
     * @param topK 返回前K个结果
     * @return Snippet列表
     */
    List<Snippet> fuzzySearchSnippetsByPreference(String keyword, String preferenceId, int topK);

    /**
     * 语义搜索：结合关键词和语义相似度
     *
     * @param query 查询文本
     * @param preferenceId Preference ID（可选）
     * @param topK 返回前K个结果
     * @return Snippet列表
     */
    List<Snippet> semanticSearch(String query, String preferenceId, int topK);

    /**
     * 语义搜索：结合关键词和语义相似度，带时间范围过滤
     *
     * @param query 查询文本
     * @param preferenceId Preference ID（可选）
     * @param startTime 开始时间戳（可选）
     * @param endTime 结束时间戳（可选）
     * @param topK 返回前K个结果
     * @return Snippet列表
     */
    List<Snippet> semanticSearch(String query, String preferenceId, Long startTime, Long endTime, int topK);

    /**
     * 混合搜索：结合关键词搜索和语义搜索
     *
     * @param query 查询文本
     * @param preferenceId Preference ID（可选）
     * @param topK 返回前K个结果
     * @return Snippet列表
     */
    List<Snippet> hybridSearch(String query, String preferenceId, int topK);

    /**
     * 推荐搜索：根据上下文推荐相关记忆
     *
     * @param context 上下文文本
     * @param sessionId 会话ID
     * @param topK 返回前K个结果
     * @return Snippet列表
     */
    List<Snippet> recommendByContext(String context, String sessionId, int topK);

    /**
     * 时间序列搜索：查找时间上相关的记忆
     *
     * @param timestamp 时间戳
     * @param timeWindow 时间窗口（毫秒）
     * @param topK 返回前K个结果
     * @return Snippet列表
     */
    List<Snippet> searchByTimeWindow(long timestamp, long timeWindow, int topK);

    /**
     * 聚合查询：获取Preference的聚合统计信息
     *
     * @param preferenceId Preference ID
     * @return 聚合统计信息
     */
    PreferenceAggregationStats getPreferenceAggregation(String preferenceId);

    /**
     * 聚合查询：获取会话的聚合统计信息
     *
     * @param sessionId 会话ID
     * @return 聚合统计信息
     */
    SessionAggregationStats getSessionAggregation(String sessionId);
}
