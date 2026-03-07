package tech.yesboss.memory.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 记忆链
 *
 * 表示从Preference到Snippet再到Resource的完整记忆链路
 */
public class MemoryChain {

    /**
     * 记忆链ID
     */
    private String id;

    /**
     * 关联的Preference
     */
    private Preference preference;

    /**
     * 关联的Snippets列表
     */
    private List<Snippet> snippets;

    /**
     * 关联的Resources列表
     */
    private List<Resource> resources;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 创建时间戳
     */
    private LocalDateTime createdAt;

    /**
     * 最后更新时间戳
     */
    private LocalDateTime updatedAt;

    public MemoryChain() {
        this.snippets = new ArrayList<>();
        this.resources = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public MemoryChain(String id, Preference preference) {
        this();
        this.id = id;
        this.preference = preference;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Preference getPreference() {
        return preference;
    }

    public void setPreference(Preference preference) {
        this.preference = preference;
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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 添加Snippet到记忆链
     */
    public void addSnippet(Snippet snippet) {
        if (this.snippets == null) {
            this.snippets = new ArrayList<>();
        }
        this.snippets.add(snippet);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 添加Resource到记忆链
     */
    public void addResource(Resource resource) {
        if (this.resources == null) {
            this.resources = new ArrayList<>();
        }
        this.resources.add(resource);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 判断记忆链是否为空
     */
    public boolean isEmpty() {
        return preference == null &&
               (snippets == null || snippets.isEmpty()) &&
               (resources == null || resources.isEmpty());
    }

    /**
     * 获取记忆链中所有元素的总数
     */
    public int getTotalElementCount() {
        int count = 0;
        if (preference != null) count++;
        if (snippets != null) count += snippets.size();
        if (resources != null) count += resources.size();
        return count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MemoryChain that = (MemoryChain) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "MemoryChain{" +
                "id='" + id + '\'' +
                ", preference=" + (preference != null ? preference.getId() : "null") +
                ", snippetCount=" + (snippets != null ? snippets.size() : 0) +
                ", resourceCount=" + (resources != null ? resources.size() : 0) +
                ", sessionId='" + sessionId + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
