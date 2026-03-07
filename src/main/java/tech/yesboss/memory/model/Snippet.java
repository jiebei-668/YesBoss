package tech.yesboss.memory.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Snippet entity representing structured memory fragments.
 *
 * Stores processed and structured memory extracted from Resources.
 * Categorized by memory_type for semantic organization.
 * Corresponds to the snippets table in the database.
 */
public class Snippet {

    /**
     * Memory type enumeration for categorizing snippets.
     */
    public enum MemoryType {
        PROFILE,     // User profile information
        EVENT,       // Events and occurrences
        KNOWLEDGE,   // Knowledge and facts
        BEHAVIOR,    // Behavioral patterns
        SKILL,       // Skills and capabilities
        TOOL;        // Tools and utilities

        /**
         * Get the display name in Chinese.
         * @return Display name
         */
        public String getDisplayName() {
            return switch (this) {
                case PROFILE -> "人物档案";
                case EVENT -> "事件";
                case KNOWLEDGE -> "知识";
                case BEHAVIOR -> "行为模式";
                case SKILL -> "技能";
                case TOOL -> "工具使用";
            };
        }

        /**
         * Get the description in English.
         * @return Description
         */
        public String getDescription() {
            return switch (this) {
                case PROFILE -> "User profile and personal characteristics";
                case EVENT -> "Significant events and occurrences";
                case KNOWLEDGE -> "Domain knowledge and factual information";
                case BEHAVIOR -> "Behavioral patterns and habits";
                case SKILL -> "User skills and capabilities";
                case TOOL -> "Tool preferences and usage patterns";
            };
        }

        /**
         * Get MemoryType from display name.
         * @param displayName Display name to match
         * @return Matching MemoryType or null
         */
        public static MemoryType fromDisplayName(String displayName) {
            for (MemoryType type : values()) {
                if (type.getDisplayName().equals(displayName)) {
                    return type;
                }
            }
            return null;
        }

    }

    private String id;
    private String resourceId;
    private String summary;
    private MemoryType memoryType;
    private byte[] embedding;
    private long timestamp;
    private boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Default constructor.
     * Initializes timestamp and timestamps.
     */
    public Snippet() {
        this.timestamp = System.currentTimeMillis();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.deleted = false;
    }

    /**
     * Constructor with required fields.
     */
    public Snippet(String resourceId, String summary, MemoryType memoryType) {
        this();
        this.id = UUID.randomUUID().toString();
        this.resourceId = resourceId;
        this.summary = summary;
        this.memoryType = memoryType;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public MemoryType getMemoryType() {
        return memoryType;
    }

    public void setMemoryType(MemoryType memoryType) {
        this.memoryType = memoryType;
    }

    public byte[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(byte[] embedding) {
        this.embedding = embedding;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
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

    // Utility methods

    /**
     * Update the updatedAt timestamp.
     */
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if this snippet has an embedding.
     */
    public boolean hasEmbedding() {
        return embedding != null && embedding.length > 0;
    }

    /**
     * Get memory type as string.
     */
    public String getMemoryTypeString() {
        return memoryType != null ? memoryType.name() : null;
    }

    /**
     * Set memory type from string.
     */
    public void setMemoryTypeFromString(String typeStr) {
        if (typeStr != null) {
            this.memoryType = MemoryType.valueOf(typeStr);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Snippet snippet = (Snippet) o;
        return Objects.equals(id, snippet.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Snippet{" +
                "id='" + id + '\'' +
                ", resourceId='" + resourceId + '\'' +
                ", summaryLength=" + (summary != null ? summary.length() : 0) +
                ", memoryType=" + memoryType +
                ", hasEmbedding=" + hasEmbedding() +
                ", deleted=" + deleted +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    /**
     * Builder for creating Snippet instances.
     */
    public static class Builder {
        private final Snippet snippet;

        public Builder() {
            this.snippet = new Snippet();
        }

        public Builder(Snippet snippet) {
            this.snippet = snippet;
        }

        public Builder id(String id) {
            snippet.setId(id);
            return this;
        }

        public Builder resourceId(String resourceId) {
            snippet.setResourceId(resourceId);
            return this;
        }

        public Builder summary(String summary) {
            snippet.setSummary(summary);
            return this;
        }

        public Builder memoryType(MemoryType memoryType) {
            snippet.setMemoryType(memoryType);
            return this;
        }

        public Builder embedding(byte[] embedding) {
            snippet.setEmbedding(embedding);
            return this;
        }

        public Builder timestamp(long timestamp) {
            snippet.setTimestamp(timestamp);
            return this;
        }

        public Builder deleted(boolean deleted) {
            snippet.setDeleted(deleted);
            return this;
        }

        public Snippet build() {
            // Generate ID if not set
            if (snippet.getId() == null || snippet.getId().isEmpty()) {
                snippet.setId(UUID.randomUUID().toString());
            }
            // Ensure timestamps are set
            if (snippet.getCreatedAt() == null) {
                snippet.setCreatedAt(LocalDateTime.now());
            }
            if (snippet.getUpdatedAt() == null) {
                snippet.setUpdatedAt(LocalDateTime.now());
            }
            // Validate required fields
            if (snippet.getResourceId() == null || snippet.getResourceId().isEmpty()) {
                throw new IllegalArgumentException("resourceId cannot be null or empty");
            }
            if (snippet.getSummary() == null || snippet.getSummary().isEmpty()) {
                throw new IllegalArgumentException("summary cannot be null or empty");
            }
            if (snippet.getMemoryType() == null) {
                throw new IllegalArgumentException("memoryType cannot be null");
            }
            return snippet;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
