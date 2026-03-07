package tech.yesboss.memory.processor;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a segment of a conversation split by topic.
 *
 * <p>Conversation segments are created by analyzing the complete conversation
 * and identifying topic boundaries. Each segment represents a coherent topic
 * or sub-conversation.</p>
 */
public class ConversationSegment {

    private String id;
    private String content;
    private int startIndex;
    private int endIndex;
    private String topic;
    private LocalDateTime createdAt;

    /**
     * Default constructor.
     */
    public ConversationSegment() {
        this.id = java.util.UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Constructor with content.
     *
     * @param content Segment content
     */
    public ConversationSegment(String content) {
        this();
        this.content = content;
    }

    /**
     * Constructor with all fields.
     *
     * @param content Segment content
     * @param startIndex Start index in original conversation
     * @param endIndex End index in original conversation
     * @param topic Topic description
     */
    public ConversationSegment(String content, int startIndex, int endIndex, String topic) {
        this();
        this.content = content;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.topic = topic;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public void setEndIndex(int endIndex) {
        this.endIndex = endIndex;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Utility methods

    /**
     * Get the length of the segment content.
     *
     * @return Content length
     */
    public int getLength() {
        return content != null ? content.length() : 0;
    }

    /**
     * Check if this segment has a topic assigned.
     *
     * @return true if topic is not null and not empty
     */
    public boolean hasTopic() {
        return topic != null && !topic.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConversationSegment that = (ConversationSegment) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ConversationSegment{" +
                "id='" + id + '\'' +
                ", length=" + getLength() +
                ", topic='" + topic + '\'' +
                ", startIndex=" + startIndex +
                ", endIndex=" + endIndex +
                ", createdAt=" + createdAt +
                '}';
    }

    /**
     * Builder for creating ConversationSegment instances.
     */
    public static class Builder {
        private final ConversationSegment segment;

        public Builder() {
            this.segment = new ConversationSegment();
        }

        public Builder id(String id) {
            segment.setId(id);
            return this;
        }

        public Builder content(String content) {
            segment.setContent(content);
            return this;
        }

        public Builder startIndex(int startIndex) {
            segment.setStartIndex(startIndex);
            return this;
        }

        public Builder endIndex(int endIndex) {
            segment.setEndIndex(endIndex);
            return this;
        }

        public Builder topic(String topic) {
            segment.setTopic(topic);
            return this;
        }

        public ConversationSegment build() {
            return segment;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
