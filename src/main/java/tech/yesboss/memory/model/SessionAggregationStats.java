package tech.yesboss.memory.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话聚合统计信息
 */
public class SessionAggregationStats {

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * Resource数量
     */
    private long resourceCount;

    /**
     * Snippet数量
     */
    private long snippetCount;

    /**
     * Preference数量
     */
    private long preferenceCount;

    /**
     * Top Preferences（摘要列表）
     */
    private List<String> topPreferences;

    /**
     * 最早的时间戳
     */
    private LocalDateTime oldestTimestamp;

    /**
     * 最晚的时间戳
     */
    private LocalDateTime newestTimestamp;

    // Getters and Setters

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public long getResourceCount() {
        return resourceCount;
    }

    public void setResourceCount(long resourceCount) {
        this.resourceCount = resourceCount;
    }

    public long getSnippetCount() {
        return snippetCount;
    }

    public void setSnippetCount(long snippetCount) {
        this.snippetCount = snippetCount;
    }

    public long getPreferenceCount() {
        return preferenceCount;
    }

    public void setPreferenceCount(long preferenceCount) {
        this.preferenceCount = preferenceCount;
    }

    public List<String> getTopPreferences() {
        return topPreferences;
    }

    public void setTopPreferences(List<String> topPreferences) {
        this.topPreferences = topPreferences;
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

    /**
     * 获取总记忆元素数量
     */
    public long getTotalElementCount() {
        return resourceCount + snippetCount + preferenceCount;
    }

    @Override
    public String toString() {
        return "SessionAggregationStats{" +
                "sessionId='" + sessionId + '\'' +
                ", resourceCount=" + resourceCount +
                ", snippetCount=" + snippetCount +
                ", preferenceCount=" + preferenceCount +
                ", topPreferencesCount=" + (topPreferences != null ? topPreferences.size() : 0) +
                ", oldestTimestamp=" + oldestTimestamp +
                ", newestTimestamp=" + newestTimestamp +
                ", totalElementCount=" + getTotalElementCount() +
                '}';
    }
}
