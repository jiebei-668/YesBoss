package tech.yesboss.memory.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AgenticRAG查询结果
 *
 * 包含三层检索（Preference、Snippet、Resource）的结果，
 * 以及LLM的决策历史记录
 */
public class AgenticRagResult {

    /**
     * 原始查询
     */
    private String query;

    /**
     * 最终查询（可能被LLM重写）
     */
    private String finalQuery;

    /**
     * Preference层检索结果
     */
    private List<Preference> preferences;

    /**
     * Snippet层检索结果
     */
    private List<Snippet> snippets;

    /**
     * Resource层检索结果
     */
    private List<Resource> resources;

    /**
     * 与Resource关联的Snippets（第三层检索时填充）
     */
    private List<Snippet> linkedSnippets;

    /**
     * LLM决策历史记录
     */
    private List<DecisionLog> decisionHistory;

    /**
     * 查询时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 总耗时（毫秒）
     */
    private long totalDurationMs;

    public AgenticRagResult() {
        this.preferences = new ArrayList<>();
        this.snippets = new ArrayList<>();
        this.resources = new ArrayList<>();
        this.linkedSnippets = new ArrayList<>();
        this.decisionHistory = new ArrayList<>();
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getFinalQuery() {
        return finalQuery;
    }

    public void setFinalQuery(String finalQuery) {
        this.finalQuery = finalQuery;
    }

    public List<Preference> getPreferences() {
        return preferences;
    }

    public void setPreferences(List<Preference> preferences) {
        this.preferences = preferences != null ? preferences : new ArrayList<>();
    }

    public List<Snippet> getSnippets() {
        return snippets;
    }

    public void setSnippets(List<Snippet> snippets) {
        this.snippets = snippets != null ? snippets : new ArrayList<>();
    }

    public List<Resource> getResources() {
        return resources;
    }

    public void setResources(List<Resource> resources) {
        this.resources = resources != null ? resources : new ArrayList<>();
    }

    public List<Snippet> getLinkedSnippets() {
        return linkedSnippets;
    }

    public void setLinkedSnippets(List<Snippet> linkedSnippets) {
        this.linkedSnippets = linkedSnippets != null ? linkedSnippets : new ArrayList<>();
    }

    public List<DecisionLog> getDecisionHistory() {
        return decisionHistory;
    }

    public void setDecisionHistory(List<DecisionLog> decisionHistory) {
        this.decisionHistory = decisionHistory != null ? decisionHistory : new ArrayList<>();
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public void setTotalDurationMs(long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }

    /**
     * 获取最终的检索层级
     */
    public RetrievalLevel getFinalLevel() {
        if (!resources.isEmpty()) {
            return RetrievalLevel.RESOURCE;
        } else if (!snippets.isEmpty()) {
            return RetrievalLevel.SNIPPET;
        } else if (!preferences.isEmpty()) {
            return RetrievalLevel.PREFERENCE;
        } else {
            return RetrievalLevel.NONE;
        }
    }

    /**
     * 判断结果是否为空
     */
    public boolean isEmpty() {
        return preferences.isEmpty() && snippets.isEmpty() && resources.isEmpty();
    }

    /**
     * 获取总结果数
     */
    public int getTotalResultCount() {
        return preferences.size() + snippets.size() + resources.size() + linkedSnippets.size();
    }

    /**
     * 添加决策日志
     */
    public void addDecisionLog(DecisionLog decisionLog) {
        this.decisionHistory.add(decisionLog);
    }

    /**
     * 检索层级枚举
     */
    public enum RetrievalLevel {
        NONE,
        PREFERENCE,
        SNIPPET,
        RESOURCE
    }
}
