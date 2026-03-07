package tech.yesboss.memory.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Preference entity representing user preference topics.
 *
 * Stores user preferences and interests with optional embeddings for semantic search.
 * Corresponds to the preferences table in the database.
 */
public class Preference {

    private String id;
    private String name;
    private String summary;
    private byte[] embedding;
    private boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Default constructor.
     * Initializes timestamps.
     */
    public Preference() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.deleted = false;
    }

    /**
     * Constructor with required fields.
     */
    public Preference(String name, String summary) {
        this();
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.summary = summary;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public byte[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(byte[] embedding) {
        this.embedding = embedding;
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
     * Check if this preference has an embedding.
     */
    public boolean hasEmbedding() {
        return embedding != null && embedding.length > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Preference preference = (Preference) o;
        return Objects.equals(id, preference.id) || Objects.equals(name, preference.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "Preference{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", summaryLength=" + (summary != null ? summary.length() : 0) +
                ", hasEmbedding=" + hasEmbedding() +
                ", deleted=" + deleted +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    /**
     * Builder for creating Preference instances.
     */
    public static class Builder {
        private final Preference preference;

        public Builder() {
            this.preference = new Preference();
        }

        public Builder(Preference preference) {
            this.preference = preference;
        }

        public Builder id(String id) {
            preference.setId(id);
            return this;
        }

        public Builder name(String name) {
            preference.setName(name);
            return this;
        }

        public Builder summary(String summary) {
            preference.setSummary(summary);
            return this;
        }

        public Builder embedding(byte[] embedding) {
            preference.setEmbedding(embedding);
            return this;
        }

        public Builder deleted(boolean deleted) {
            preference.setDeleted(deleted);
            return this;
        }

        public Preference build() {
            // Generate ID if not set
            if (preference.getId() == null || preference.getId().isEmpty()) {
                preference.setId(UUID.randomUUID().toString());
            }
            // Ensure timestamps are set
            if (preference.getCreatedAt() == null) {
                preference.setCreatedAt(LocalDateTime.now());
            }
            if (preference.getUpdatedAt() == null) {
                preference.setUpdatedAt(LocalDateTime.now());
            }
            // Validate required fields
            if (preference.getName() == null || preference.getName().isEmpty()) {
                throw new IllegalArgumentException("name cannot be null or empty");
            }
            if (preference.getSummary() == null || preference.getSummary().isEmpty()) {
                throw new IllegalArgumentException("summary cannot be null or empty");
            }
            return preference;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
