# Conversation Round Trigger Mechanism Guide

## Overview

The `ConversationRoundTrigger` provides a conversation round-based triggering mechanism for memory extraction. It tracks the number of conversation rounds (message exchanges) and triggers memory extraction when a configured threshold is reached.

## Table of Contents

1. [Architecture](#architecture)
2. [Configuration](#configuration)
3. [Usage](#usage)
4. [API Reference](#api-reference)
5. [Best Practices](#best-practices)
6. [Examples](#examples)
7. [Troubleshooting](#troubleshooting)

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│              ConversationRoundTrigger                    │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌─────────────────────────────────────────────────┐   │
│  │  Round Tracking (Per Conversation)              │   │
│  │  ├─ conversationRounds: Map<String, AtomicInteger>│  │
│  │  └─ lastUpdateTimestamps: Map<String, Long>      │   │
│  └─────────────────────────────────────────────────┘   │
│                                                          │
│  ┌─────────────────────────────────────────────────┐   │
│  │  Threshold Management                           │   │
│  │  ├─ defaultThreshold: int (default: 20)         │   │
│  │  └─ perConversationThresholds: Map<String, int> │   │
│  └─────────────────────────────────────────────────┘   │
│                                                          │
│  ┌─────────────────────────────────────────────────┐   │
│  │  Trigger Logic                                  │   │
│  │  ├─ trackMessage()                              │   │
│  │  ├─ isTriggerConditionMet()                     │   │
│  │  └─ triggerExtraction()                         │   │
│  └─────────────────────────────────────────────────┘   │
│                                                          │
│  ┌─────────────────────────────────────────────────┐   │
│  │  Metrics & Monitoring                           │   │
│  │  ├─ totalRoundsTracked                          │   │
│  │  ├─ totalTriggers                               │   │
│  │  ├─ totalExtractions                            │   │
│  │  └─ averageProcessingTime                       │   │
│  └─────────────────────────────────────────────────┘   │
│                                                          │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────┐
│                  TriggerService                          │
│                  (Memory Extraction)                     │
└─────────────────────────────────────────────────────────┘
```

---

## Configuration

### application-memory.yml

```yaml
memory:
  trigger:
    conversationRound:
      # Enable/disable conversation round trigger
      enabled: true

      # Default round threshold for all conversations
      defaultThreshold: 20

      # Per-conversation custom thresholds
      perConversationThresholds:
        conversation_123: 10
        conversation_456: 30

      # Reset round count after extraction
      resetAfterExtraction: true

      # Include bot messages in round count
      includeBotMessages: false
```

### Configuration Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | true | Enable/disable trigger |
| `defaultThreshold` | int | 20 | Default round threshold |
| `perConversationThresholds` | Map | {} | Custom thresholds per conversation |
| `resetAfterExtraction` | boolean | true | Reset count after extraction |
| `includeBotMessages` | boolean | false | Count bot messages |

---

## Usage

### Basic Usage

#### 1. Track Messages

```java
@Autowired
private ConversationRoundTrigger roundTrigger;

// Track a message
boolean triggered = roundTrigger.trackMessage(message, conversationId);

if (triggered) {
    // Threshold reached, trigger extraction
    int processed = roundTrigger.triggerExtraction(conversationId, sessionId);
    logger.info("Extracted memories for {} messages", processed);
}
```

#### 2. Check Trigger Condition

```java
// Check if threshold is met
if (roundTrigger.isTriggerConditionMet(conversationId)) {
    // Trigger extraction
    roundTrigger.triggerExtraction(conversationId, sessionId);
}
```

#### 3. Get Round Count

```java
// Get current round count for a conversation
int currentRound = roundTrigger.getRoundCount(conversationId);
logger.info("Conversation {} has {} rounds", conversationId, currentRound);
```

### Advanced Usage

#### Custom Thresholds

```java
// Set custom threshold for a specific conversation
roundTrigger.setThresholdForConversation(conversationId, 15);

// Get threshold for a conversation
int threshold = roundTrigger.getThresholdForConversation(conversationId);
```

#### Batch Operations

```java
// Track multiple messages at once
List<UnifiedMessage> messages = getMessages();
int triggerCount = roundTrigger.batchTrackMessages(messages, conversationId);

logger.info("Tracked {} messages, {} triggers occurred", messages.size(), triggerCount);
```

#### Get All Round Counts

```java
// Get round counts for all conversations
Map<String, Integer> allCounts = roundTrigger.getAllRoundCounts();

allCounts.forEach((convId, rounds) -> {
    logger.info("Conversation {}: {} rounds", convId, rounds);
});
```

#### Get Conversations Meeting Threshold

```java
// Get all conversations that meet the threshold
List<String> conversations = roundTrigger.getConversationsMeetingThreshold();

logger.info("{} conversations meet the threshold", conversations.size());

// Trigger extraction for all
for (String convId : conversations) {
    roundTrigger.triggerExtraction(convId, sessionId);
}
```

---

## API Reference

### Core Methods

#### trackMessage()

```java
boolean trackMessage(UnifiedMessage message, String conversationId)
```

Track a message and increment round count.

**Parameters:**
- `message` - The message to track
- `conversationId` - The conversation ID

**Returns:** `true` if trigger threshold was reached

**Throws:** No exceptions (logs errors)

#### isTriggerConditionMet()

```java
boolean isTriggerConditionMet(String conversationId)
```

Check if trigger condition is met for a conversation.

**Parameters:**
- `conversationId` - The conversation ID

**Returns:** `true` if threshold reached

#### triggerExtraction()

```java
int triggerExtraction(String conversationId, String sessionId)
```

Trigger memory extraction for a conversation.

**Parameters:**
- `conversationId` - The conversation ID
- `sessionId` - The session ID

**Returns:** Number of messages processed

### Round Counting Methods

#### getRoundCount()

```java
int getRoundCount(String conversationId)
```

Get current round count for a conversation.

**Returns:** Current round count

#### resetRoundCount()

```java
void resetRoundCount(String conversationId)
```

Reset round count for a conversation.

#### resetAllRoundCounts()

```java
void resetAllRoundCounts()
```

Reset all round counts.

### Threshold Management

#### getThresholdForConversation()

```java
int getThresholdForConversation(String conversationId)
```

Get threshold for a specific conversation.

**Returns:** Threshold value

#### setThresholdForConversation()

```java
void setThresholdForConversation(String conversationId, int threshold)
```

Set custom threshold for a conversation.

**Parameters:**
- `conversationId` - The conversation ID
- `threshold` - The threshold value (must be positive)

**Throws:** `IllegalArgumentException` if threshold <= 0

#### getDefaultThreshold()

```java
int getDefaultThreshold()
```

Get the default threshold.

**Returns:** Default threshold value

#### setDefaultThreshold()

```java
void setDefaultThreshold(int threshold)
```

Set the default threshold.

**Parameters:**
- `threshold` - The default threshold (must be positive)

**Throws:** `IllegalArgumentException` if threshold <= 0

### Configuration Methods

#### enable() / disable()

```java
void enable()
void disable()
```

Enable or disable the trigger.

#### isEnabled()

```java
boolean isEnabled()
```

Check if trigger is enabled.

**Returns:** `true` if enabled

#### setResetAfterExtraction()

```java
void setResetAfterExtraction(boolean reset)
```

Configure whether to reset round count after extraction.

#### setIncludeBotMessages()

```java
void setIncludeBotMessages(boolean include)
```

Configure whether to include bot messages in round count.

### Metrics Methods

#### getMetrics()

```java
Map<String, Object> getMetrics()
```

Get comprehensive metrics.

**Returns:** Metrics map containing:
- `enabled` - Trigger enabled status
- `defaultThreshold` - Default threshold
- `trackedConversations` - Number of tracked conversations
- `totalRoundsTracked` - Total rounds tracked
- `totalTriggers` - Total triggers fired
- `totalExtractions` - Total extractions performed
- `averageProcessingTime` - Average processing time (ms)

#### calculateAverageProcessingTime()

```java
long calculateAverageProcessingTime()
```

Calculate average processing time.

**Returns:** Average time in milliseconds

#### getStatistics()

```java
Map<String, Object> getStatistics()
```

Get statistics about tracked conversations.

**Returns:** Statistics map containing:
- `totalConversations` - Total conversations tracked
- `averageRounds` - Average rounds per conversation
- `maxRounds` - Maximum rounds in any conversation
- `minRounds` - Minimum rounds in any conversation
- `totalRounds` - Total rounds across all conversations

### Batch Operations

#### batchTrackMessages()

```java
int batchTrackMessages(List<UnifiedMessage> messages, String conversationId)
```

Track multiple messages at once.

**Parameters:**
- `messages` - List of messages to track
- `conversationId` - The conversation ID

**Returns:** Number of messages that triggered threshold

#### getConversationsMeetingThreshold()

```java
List<String> getConversationsMeetingThreshold()
```

Get all conversations that meet the threshold.

**Returns:** List of conversation IDs

### Cleanup Methods

#### removeConversation()

```java
void removeConversation(String conversationId)
```

Remove tracking for a conversation.

#### cleanupOldConversations()

```java
int cleanupOldConversations(long maxAgeMs)
```

Clean up old conversation tracking data.

**Parameters:**
- `maxAgeMs` - Maximum age in milliseconds

**Returns:** Number of conversations cleaned up

---

## Best Practices

### 1. Threshold Selection

**DO**: Choose thresholds based on conversation patterns
```yaml
# Short conversations
defaultThreshold: 10

# Long conversations
defaultThreshold: 30

# Very active chats
defaultThreshold: 50
```

**DON'T**: Use arbitrary thresholds
```yaml
# Bad: Too low - triggers too frequently
defaultThreshold: 1

# Bad: Too high - rarely triggers
defaultThreshold: 1000
```

### 2. Bot Message Handling

**DO**: Decide whether to count bot messages
```yaml
# For user-focused memory extraction
includeBotMessages: false

# For complete conversation tracking
includeBotMessages: true
```

**DON'T**: Forget to configure this
```yaml
# Bad: Default may not match your use case
# Always explicitly set this
```

### 3. Reset Strategy

**DO**: Configure reset behavior appropriately
```yaml
# For periodic extraction
resetAfterExtraction: true

# For cumulative tracking
resetAfterExtraction: false
```

**DON'T**: Mix reset strategies
```yaml
# Risky: Inconsistent behavior
resetAfterExtraction: true  # Sometimes reset
resetAfterExtraction: false  # Sometimes don't
```

### 4. Custom Thresholds

**DO**: Use custom thresholds for specific conversations
```java
// High-priority conversations
roundTrigger.setThresholdForConversation(priorityConvId, 5);

// Low-priority conversations
roundTrigger.setThresholdForConversation(lowPriorityConvId, 50);
```

**DON'T**: Set too many custom thresholds
```java
// Bad: Hard to manage
for (String convId : allConversations) {
    roundTrigger.setThresholdForConversation(convId, randomThreshold);
}
```

### 5. Monitoring

**DO**: Monitor metrics regularly
```java
Map<String, Object> metrics = roundTrigger.getMetrics();
logger.info("Tracked conversations: {}", metrics.get("trackedConversations"));
logger.info("Average rounds: {}", metrics.get("averageRounds"));
```

**DON'T**: Ignore metrics
```java
// Bad: Not tracking performance
roundTrigger.trackMessage(message, convId);  // Fire and forget
```

---

## Examples

### Example 1: Basic Message Tracking

```java
@Service
public class MessageProcessingService {

    @Autowired
    private ConversationRoundTrigger roundTrigger;

    @Autowired
    private MemoryService memoryService;

    public void processMessage(UnifiedMessage message, String conversationId) {
        // Track message
        boolean triggered = roundTrigger.trackMessage(message, conversationId);

        if (triggered) {
            // Trigger memory extraction
            logger.info("Threshold reached for conversation: {}", conversationId);
            int processed = roundTrigger.triggerExtraction(conversationId, sessionId);
            logger.info("Extracted memories from {} messages", processed);
        }
    }
}
```

### Example 2: Custom Thresholds

```java
@Service
public class ConversationManager {

    @Autowired
    private ConversationRoundTrigger roundTrigger;

    public void configureConversation(String conversationId, ConversationType type) {
        // Set threshold based on conversation type
        switch (type) {
            case HIGH_PRIORITY:
                roundTrigger.setThresholdForConversation(conversationId, 5);
                break;
            case NORMAL:
                roundTrigger.setThresholdForConversation(conversationId, 20);
                break;
            case LOW_PRIORITY:
                roundTrigger.setThresholdForConversation(conversationId, 50);
                break;
        }
    }
}
```

### Example 3: Batch Processing

```java
@Service
public class BatchMessageProcessor {

    @Autowired
    private ConversationRoundTrigger roundTrigger;

    public void processMessageBatch(List<UnifiedMessage> messages, String conversationId) {
        // Track all messages
        int triggerCount = roundTrigger.batchTrackMessages(messages, conversationId);

        logger.info("Processed {} messages, {} triggers", messages.size(), triggerCount);

        if (triggerCount > 0) {
            // Trigger extraction
            roundTrigger.triggerExtraction(conversationId, sessionId);
        }
    }
}
```

### Example 4: Monitoring and Cleanup

```java
@Service
public class MaintenanceService {

    @Autowired
    private ConversationRoundTrigger roundTrigger;

    @Scheduled(fixedRate = 3600000) // Every hour
    public void performMaintenance() {
        // Log metrics
        Map<String, Object> metrics = roundTrigger.getMetrics();
        logger.info("Round trigger metrics: {}", metrics);

        // Log statistics
        Map<String, Object> stats = roundTrigger.getStatistics();
        logger.info("Conversation statistics: {}", stats);

        // Cleanup old conversations (older than 24 hours)
        int cleaned = roundTrigger.cleanupOldConversations(24 * 60 * 60 * 1000L);
        logger.info("Cleaned up {} old conversations", cleaned);
    }
}
```

### Example 5: Triggering for All Conversations

```java
@Service
public class BulkExtractionService {

    @Autowired
    private ConversationRoundTrigger roundTrigger;

    public void triggerForAllEligibleConversations() {
        // Get conversations meeting threshold
        List<String> conversations = roundTrigger.getConversationsMeetingThreshold();

        logger.info("Found {} conversations meeting threshold", conversations.size());

        // Trigger extraction for each
        for (String conversationId : conversations) {
            try {
                int processed = roundTrigger.triggerExtraction(conversationId, sessionId);
                logger.info("Extracted {} messages from conversation {}",
                           processed, conversationId);
            } catch (Exception e) {
                logger.error("Error extracting from conversation {}: {}",
                           conversationId, e.getMessage());
            }
        }
    }
}
```

---

## Troubleshooting

### Problem: Trigger Not Firing

**Symptoms:**
- Round count increasing but no extraction triggered
- `isTriggerConditionMet()` returns false

**Solutions:**
1. Check threshold value:
   ```java
   int threshold = roundTrigger.getThresholdForConversation(convId);
   int current = roundTrigger.getRoundCount(convId);
   logger.info("Threshold: {}, Current: {}", threshold, current);
   ```
2. Verify trigger is enabled:
   ```java
   if (!roundTrigger.isEnabled()) {
       roundTrigger.enable();
   }
   ```
3. Check if messages are being counted:
   ```java
   boolean counted = roundTrigger.trackMessage(message, convId);
   logger.info("Message counted: {}", counted);
   ```

### Problem: Too Many Triggers

**Symptoms:**
- Extraction triggered too frequently
- Performance degradation

**Solutions:**
1. Increase threshold:
   ```java
   roundTrigger.setDefaultThreshold(50);  // Increase from 20
   ```
2. Exclude bot messages:
   ```yaml
   includeBotMessages: false
   ```
3. Enable reset after extraction:
   ```yaml
   resetAfterExtraction: true
   ```

### Problem: Memory Leak

**Symptoms:**
- `trackedConversations` keeps growing
- Memory usage increasing

**Solutions:**
1. Enable periodic cleanup:
   ```java
   @Scheduled(fixedRate = 3600000)
   public void cleanup() {
       roundTrigger.cleanupOldConversations(24 * 60 * 60 * 1000L);
   }
   ```
2. Remove ended conversations:
   ```java
   roundTrigger.removeConversation(conversationId);
   ```
3. Reset all counts:
   ```java
   roundTrigger.resetAllRoundCounts();
   ```

### Problem: Inaccurate Round Counts

**Symptoms:**
- Round count doesn't match expected
- Some messages not counted

**Solutions:**
1. Check bot message filtering:
   ```java
   roundTrigger.setIncludeBotMessages(true);  // Count all messages
   ```
2. Verify message tracking:
   ```java
   boolean triggered = roundTrigger.trackMessage(message, convId);
   logger.debug("Message tracked: {}, triggered: {}", convId, triggered);
   ```
3. Check round count manually:
   ```java
   int rounds = roundTrigger.getRoundCount(convId);
   logger.info("Current rounds: {}", rounds);
   ```

---

## Performance Considerations

### Thread Safety

All operations are thread-safe:
- `ConcurrentHashMap` for round tracking
- `AtomicInteger` for round counts
- `AtomicLong` for metrics

### Memory Usage

Approximate memory per tracked conversation:
- Round counter: ~48 bytes
- Timestamp: ~32 bytes
- Total: ~80 bytes per conversation

For 10,000 conversations: ~800 KB

### Performance Optimization

1. **Batch Operations**
   ```java
   // Good: Batch processing
   roundTrigger.batchTrackMessages(messages, convId);

   // Bad: Individual tracking
   for (UnifiedMessage msg : messages) {
       roundTrigger.trackMessage(msg, convId);
   }
   ```

2. **Periodic Cleanup**
   ```java
   @Scheduled(fixedRate = 3600000)
   public void cleanup() {
       roundTrigger.cleanupOldConversations(24 * 60 * 60 * 1000L);
   }
   ```

3. **Metrics Sampling**
   ```java
   // Don't call getMetrics() too frequently
   @Scheduled(fixedRate = 60000)  // Once per minute
   public void logMetrics() {
       logger.info("Metrics: {}", roundTrigger.getMetrics());
   }
   ```

---

## Summary

The `ConversationRoundTrigger` provides:

✅ **Round-based Triggering**: Track conversation rounds and trigger extraction
✅ **Configurable Thresholds**: Default and per-conversation thresholds
✅ **Message Filtering**: Option to include/exclude bot messages
✅ **Batch Operations**: Efficient batch message tracking
✅ **Thread Safety**: Concurrent access support
✅ **Metrics Collection**: Comprehensive monitoring
✅ **Auto-cleanup**: Remove old conversation data
✅ **Flexible Configuration**: Runtime configuration updates

**Key Features**:
- Simple round counting per conversation
- Configurable thresholds (default and per-conversation)
- Automatic reset after extraction (optional)
- Bot message filtering
- Batch message tracking
- Comprehensive metrics
- Thread-safe operations
- Memory-efficient cleanup

---

**Document Version**: 1.0
**Last Updated**: 2026-03-08
**Author**: YesBoss Team
