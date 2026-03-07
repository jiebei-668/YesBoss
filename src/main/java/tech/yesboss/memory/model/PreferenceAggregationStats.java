package tech.yesboss.memory.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Preference聚合统计信息
 */
public class PreferenceAggregationStats {

    /**
     * Preference ID（用于关联）
     */
    private String preferenceId;

    /**
     * Preference名称（用于显示）
     */
    private String preferenceName;

    /**
     * 关联的Snippet数量
     */
    private long snippetCount;

    /**
     * 关联的Resource数量
     */
    private long resourceCount;

    /**
     * Top Snippets（摘要列表）
     */
    private List<String> topSnippets;

    /**
     * 最早的时间戳
     */
    private LocalDateTime oldestTimestamp;

    /**
     * 最晚的时间戳
     */
    private LocalDateTime newestTimestamp;

    // Getters and Setters

    public String getPreferenceId() {
        return preferenceId;
    }

    public void setPreferenceId(String preferenceId) {
        this.preferenceId = preferenceId;
    }

    public String getPreferenceName() {
        return preferenceName;
    }

    public void setPreferenceName(String preferenceName) {
        this.preferenceName = preferenceName;
    }

    public long getSnippetCount() {
        return snippetCount;
    }

    public void setSnippetCount(long snippetCount) {
        this.snippetCount = snippetCount;
    }

    public long getResourceCount() {
        return resourceCount;
    }

    public void setResourceCount(long resourceCount) {
        this.resourceCount = resourceCount;
    }

    public List<String> getTopSnippets() {
        return topSnippets;
    }

    public void setTopSnippets(List<String> topSnippets) {
        this.topSnippets = topSnippets;
    }

    public LocalDateTime getOldestTimestamp() {
        return oldestTimestamp;
    }

    public void setOldestTimestamp(LocalDateTime oldestTimestamp) {
        this.oldestTimestamp = oldestTimestamp;
    }

    public LocalDateTime getNewestTimestamp() {
        return newestTimestamp;
    }

    public void setNewestTimestamp(LocalDateTime newestTimestamp) {
        this.newestTimestamp = newestTimestamp;
    }

    @Override
    public String toString() {
        return "PreferenceAggregationStats{" +
                "preferenceId='" + preferenceId + '\'' +
                ", preferenceName='" + preferenceName + '\'' +
                ", snippetCount=" + snippetCount +
                ", resourceCount=" + resourceCount +
                ", topSnippetsCount=" + (topSnippets != null ? topSnippets.size() : 0) +
                ", oldestTimestamp=" + oldestTimestamp +
                ", newestTimestamp=" + newestTimestamp +
                '}';
    }
}
