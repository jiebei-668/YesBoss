package tech.yesboss.memory.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Resource entity representing conversation data.
 *
 * Stores raw conversation content with optional embeddings for semantic search.
 * Corresponds to the resources table in the database.
 */
public class Resource {

    private String id;
    private String conversationId;
    private String sessionId;
    private String content;
    private String abstractText;
    private byte[] embedding;
    private long timestamp;
    private int messageCount;
    private boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Default constructor.
     * Initializes timestamp and timestamps.
     */
    public Resource() {
        this.timestamp = System.currentTimeMillis();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.deleted = false;
    }

    /**
     * Constructor with required fields.
     */
    public Resource(String conversationId, String sessionId, String content) {
        this();
        this.id = UUID.randomUUID().toString();
        this.conversationId = conversationId;
        this.sessionId = sessionId;
        this.content = content;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAbstract() {
        return abstractText;
    }

    public void setAbstract(String abstractText) {
        this.abstractText = abstractText;
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

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
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
     * Check if this resource has an embedding.
     */
    public boolean hasEmbedding() {
        return embedding != null && embedding.length > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Resource resource = (Resource) o;
        return Objects.equals(id, resource.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Resource{" +
                "id='" + id + '\'' +
                ", conversationId='" + conversationId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", contentLength=" + (content != null ? content.length() : 0) +
                ", hasEmbedding=" + hasEmbedding() +
                ", deleted=" + deleted +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    /**
     * Builder for creating Resource instances.
     */
    public static class Builder {
        private final Resource resource;

        public Builder() {
            this.resource = new Resource();
        }

        public Builder(Resource resource) {
            this.resource = resource;
        }

        public Builder id(String id) {
            resource.setId(id);
            return this;
        }

        public Builder conversationId(String conversationId) {
            resource.setConversationId(conversationId);
            return this;
        }

        public Builder sessionId(String sessionId) {
            resource.setSessionId(sessionId);
            return this;
        }

        public Builder content(String content) {
            resource.setContent(content);
            return this;
        }

        public Builder abstractText(String abstractText) {
            resource.setAbstract(abstractText);
            return this;
        }

        public Builder embedding(byte[] embedding) {
            resource.setEmbedding(embedding);
            return this;
        }

        public Builder timestamp(long timestamp) {
            resource.setTimestamp(timestamp);
            return this;
        }

        public Builder messageCount(int messageCount) {
            resource.setMessageCount(messageCount);
            return this;
        }

        public Builder deleted(boolean deleted) {
            resource.setDeleted(deleted);
            return this;
        }

        public Resource build() {
            // Generate ID if not set
            if (resource.getId() == null || resource.getId().isEmpty()) {
                resource.setId(UUID.randomUUID().toString());
            }
            // Ensure timestamps are set
            if (resource.getCreatedAt() == null) {
                resource.setCreatedAt(LocalDateTime.now());
            }
            if (resource.getUpdatedAt() == null) {
                resource.setUpdatedAt(LocalDateTime.now());
            }
            return resource;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
