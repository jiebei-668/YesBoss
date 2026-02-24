package tech.yesboss.gateway.webhook.model;

import java.time.Instant;

/**
 * IM Webhook Internal Event Carrier
 *
 * This record encapsulates the normalized event data from different IM platforms (Feishu, Slack).
 * It serves as the internal event object that gets passed to the async executor.
 *
 * @param imType       The IM platform type (FEISHU, SLACK, CLI)
 * @param eventType    The type of event (message, group_join, group_delete, etc.)
 * @param imGroupId    The external group chat ID from the IM platform
 * @param userId       The user ID who triggered the event
 * @param payload      The original JSON payload string
 * @param receivedAt   The timestamp when this event was received
 */
public record ImWebhookEvent(
    String imType,
    String eventType,
    String imGroupId,
    String userId,
    String payload,
    long receivedAt
) {
    /**
     * Creates a new ImWebhookEvent with the current timestamp.
     *
     * @param imType    The IM platform type
     * @param eventType The event type
     * @param imGroupId The group chat ID
     * @param userId    The user ID
     * @param payload   The JSON payload
     * @return A new ImWebhookEvent instance
     */
    public static ImWebhookEvent create(
        String imType,
        String eventType,
        String imGroupId,
        String userId,
        String payload
    ) {
        return new ImWebhookEvent(
            imType,
            eventType,
            imGroupId,
            userId,
            payload,
            Instant.now().toEpochMilli()
        );
    }

    /**
     * Creates a new ImWebhookEvent with a specific timestamp.
     *
     * @param imType     The IM platform type
     * @param eventType  The event type
     * @param imGroupId  The group chat ID
     * @param userId     The user ID
     * @param payload    The JSON payload
     * @param receivedAt The timestamp in milliseconds
     */
    public ImWebhookEvent {
        if (imType == null || imType.isBlank()) {
            throw new IllegalArgumentException("imType cannot be null or blank");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType cannot be null or blank");
        }
        if (imGroupId == null || imGroupId.isBlank()) {
            throw new IllegalArgumentException("imGroupId cannot be null or blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be null or blank");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload cannot be null");
        }
    }

    /**
     * Checks if this is a message event.
     *
     * @return true if the event type is "message"
     */
    public boolean isMessageEvent() {
        return "message".equalsIgnoreCase(eventType);
    }

    /**
     * Checks if this is a group join event.
     *
     * @return true if the event type is "group_join"
     */
    public boolean isGroupJoinEvent() {
        return "group_join".equalsIgnoreCase(eventType);
    }

    /**
     * Checks if this is a group delete event.
     *
     * @return true if the event type is "group_delete"
     */
    public boolean isGroupDeleteEvent() {
        return "group_delete".equalsIgnoreCase(eventType);
    }

    /**
     * Checks if this event is from Feishu.
     *
     * @return true if imType is "FEISHU"
     */
    public boolean isFeishu() {
        return "FEISHU".equalsIgnoreCase(imType);
    }

    /**
     * Checks if this event is from Slack.
     *
     * @return true if imType is "SLACK"
     */
    public boolean isSlack() {
        return "SLACK".equalsIgnoreCase(imType);
    }

    /**
     * Checks if this event is from CLI.
     *
     * @return true if imType is "CLI"
     */
    public boolean isCli() {
        return "CLI".equalsIgnoreCase(imType);
    }
}
