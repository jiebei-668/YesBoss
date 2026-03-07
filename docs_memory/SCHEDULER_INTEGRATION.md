# Memory Extraction Scheduler Integration Guide

## Overview

The `MemoryExtractionScheduler` integrates the memory extraction trigger system with Spring Boot's scheduling framework. It provides automated, periodic memory extraction from conversations with comprehensive monitoring, error handling, and retry mechanisms.

## Table of Contents

1. [Architecture](#architecture)
2. [Configuration](#configuration)
3. [Scheduled Tasks](#scheduled-tasks)
4. [API Usage](#api-usage)
5. [Error Handling](#error-handling)
6. [Performance Monitoring](#performance-monitoring)
7. [Best Practices](#best-practices)
8. [Troubleshooting](#troubleshooting)

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   Spring Boot Application                    │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │   MemoryExtractionScheduler                          │   │
│  │   ├─ @Scheduled Tasks                                │   │
│  │   ├─ Executor Service (Async Processing)             │   │
│  │   ├─ Retry Mechanism                                 │   │
│  │   ├─ Error Handling & Degradation                   │   │
│  │   └─ Metrics Collection                              │   │
│  └─────────────────┬───────────────────────────────────┘   │
│                    │                                         │
│                    ▼                                         │
│  ┌─────────────────────────────────────────────────────┐   │
│  │   TriggerService                                     │   │
│  │   ├─ Check Trigger Conditions                       │   │
│  │   ├─ Find Unprocessed Messages                      │   │
│  │   └─ Trigger Memory Extraction                      │   │
│  └─────────────────┬───────────────────────────────────┘   │
│                    │                                         │
│                    ▼                                         │
│  ┌─────────────────────────────────────────────────────┐   │
│  │   MemoryService                                      │   │
│  │   └─ Extract Memories from Messages                 │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## Configuration

### application-memory.yml

Add the following configuration to enable and customize the scheduler:

```yaml
memory:
  scheduler:
    # Enable/disable scheduler
    enabled: true

    # Fixed rate scheduling (milliseconds)
    # Default: 300000 (5 minutes)
    fixedRate: 300000

    # Initial delay before first execution (milliseconds)
    # Default: 60000 (1 minute)
    initialDelay: 60000

    # Maximum concurrent extraction tasks
    # Default: 5
    maxConcurrentTasks: 5

    # Retry configuration
    retryMaxAttempts: 3
    retryInitialDelay: 1000  # 1 second

    # Degradation mode: log_only, disable, backoff
    # Default: log_only
    degradationMode: log_only

    # Backoff delay when errors occur (milliseconds)
    # Default: 60000 (1 minute)
    backoffDelay: 60000

    # Failure rate threshold for automatic degradation (percentage)
    # Default: 50
    failureRateThreshold: 50
```

### Configuration Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | true | Enable/disable scheduler |
| `fixedRate` | long | 300000 | Execution interval in milliseconds |
| `initialDelay` | long | 60000 | Delay before first execution |
| `maxConcurrentTasks` | int | 5 | Maximum concurrent tasks |
| `retryMaxAttempts` | int | 3 | Maximum retry attempts |
| `retryInitialDelay` | long | 1000 | Initial retry delay |
| `degradationMode` | String | log_only | Error handling strategy |
| `backoffDelay` | long | 60000 | Backoff delay on error |
| `failureRateThreshold` | int | 50 | Failure rate threshold |

---

## Scheduled Tasks

### 1. Memory Extraction Task

**Schedule**: Every 5 minutes (configurable)

**Purpose**: Triggers memory extraction for all active conversations

**Code**:
```java
@Scheduled(fixedRateString = "${memory.scheduler.fixedRate:300000}",
           initialDelayString = "${memory.scheduler.initialDelay:60000}")
public void scheduledMemoryExtraction()
```

**Behavior**:
- Checks if scheduler is enabled
- Verifies TriggerService availability
- Executes extraction for all conversations
- Updates performance metrics
- Handles errors with degradation strategy

### 2. Health Check Task

**Schedule**: Every minute

**Purpose**: Monitors scheduler health and reports metrics

**Code**:
```java
@Scheduled(fixedRate = 60000, initialDelay = 30000)
public void scheduledHealthCheck()
```

**Behavior**:
- Logs health metrics
- Checks failure rate
- Triggers recovery if needed
- Reports system status

---

## API Usage

### Programmatic Trigger

#### Synchronous Extraction

```java
@Autowired
private MemoryExtractionScheduler scheduler;

// Trigger extraction for a specific conversation
try {
    int processed = scheduler.triggerService()
        .triggerMemoryExtraction(conversationId, sessionId);
    logger.info("Processed {} messages", processed);
} catch (Exception e) {
    logger.error("Extraction failed", e);
}
```

#### Asynchronous Extraction

```java
@Autowired
private MemoryExtractionScheduler scheduler;

// Trigger extraction asynchronously
CompletableFuture<Integer> future = scheduler.triggerExtractionAsync(
    conversationId, sessionId);

future.thenAccept(count -> {
    logger.info("Processed {} messages asynchronously", count);
}).exceptionally(ex -> {
    logger.error("Async extraction failed", ex);
    return 0;
});
```

#### Extraction with Retry

```java
@Autowired
private MemoryExtractionScheduler scheduler;

// Trigger extraction with automatic retry
CompletableFuture<Integer> future = scheduler.triggerExtractionWithRetry(
    conversationId, sessionId);

future.thenAccept(count -> {
    logger.info("Processed {} messages after retries", count);
}).exceptionally(ex -> {
    logger.error("Extraction failed after all retries", ex);
    return 0;
});
```

### Scheduler Control

```java
@Autowired
private MemoryExtractionScheduler scheduler;

// Enable scheduler
scheduler.enable();

// Disable scheduler
scheduler.disable();

// Check if enabled
if (scheduler.isEnabled()) {
    logger.info("Scheduler is enabled");
}

// Check if running
if (scheduler.isRunning()) {
    logger.info("Scheduler is running");
}

// Get active task count
int activeTasks = scheduler.getActiveTaskCount();
```

### Metrics Access

```java
@Autowired
private MemoryExtractionScheduler scheduler;

// Get comprehensive metrics
Map<String, Object> metrics = scheduler.getMetrics();

// Access specific metrics
long successRate = scheduler.calculateSuccessRate();
long avgProcessingTime = scheduler.calculateAverageProcessingTime();

// Log all metrics
metrics.forEach((key, value) -> {
    logger.info("{}: {}", key, value);
});
```

---

## Error Handling

### Degradation Modes

#### 1. Log Only (Default)

```yaml
memory:
  scheduler:
    degradationMode: log_only
```

- Logs errors
- Continues normal operation
- No automatic intervention

#### 2. Disable

```yaml
memory:
  scheduler:
    degradationMode: disable
```

- Logs errors
- Disables scheduler on error
- Requires manual re-enable

#### 3. Backoff

```yaml
memory:
  scheduler:
    degradationMode: backoff
    backoffDelay: 60000
```

- Logs errors
- Temporarily disables scheduler
- Automatically re-enables after backoff delay

### Retry Mechanism

Extraction uses exponential backoff retry:

```
Attempt 1: Immediate
Attempt 2: After 1 second
Attempt 3: After 2 seconds
Attempt 4: After 4 seconds
...
```

Configuration:
```yaml
memory:
  scheduler:
    retryMaxAttempts: 3
    retryInitialDelay: 1000
```

### Error Recovery

Automatic recovery on:

1. **High Failure Rate**: If failure rate exceeds threshold
   ```
   Failure rate: 60% (threshold: 50%)
   Action: Trigger degradation strategy
   ```

2. **Service Unavailability**: If TriggerService unavailable
   ```
   Status: TriggerService not available
   Action: Skip execution, log warning
   ```

3. **Concurrent Task Limit**: If max tasks reached
   ```
   Active: 5/5 tasks
   Action: Skip execution, log warning
   ```

---

## Performance Monitoring

### Metrics Collected

The scheduler collects the following metrics:

| Metric | Type | Description |
|--------|------|-------------|
| `enabled` | boolean | Scheduler enabled status |
| `running` | boolean | Currently running status |
| `activeTasks` | int | Number of active tasks |
| `totalExtractions` | long | Total extraction attempts |
| `successfulExtractions` | long | Successful extractions |
| `failedExtractions` | long | Failed extractions |
| `successRate` | long | Success rate percentage |
| `failureRate` | long | Failure rate percentage |
| `averageProcessingTime` | long | Avg processing time (ms) |
| `lastExecutionTime` | long | Last execution time (ms) |
| `lastExecutionTimestamp` | long | Last execution timestamp |

### Health Check Output

```
MemoryExtractionScheduler Health Metrics:
  Enabled: true
  Running: false
  Active Tasks: 2/5
  Total Extractions: 150
  Successful: 145 (96%)
  Failed: 5 (4%)
  Avg Processing Time: 1234ms
  Last Execution Time: 1567ms
```

### Monitoring Endpoints

Add to your health check endpoint:

```java
@RestController
@RequestMapping("/actuator/health")
public class HealthController {

    @Autowired
    private MemoryExtractionScheduler scheduler;

    @GetMapping("/memory-scheduler")
    public ResponseEntity<Map<String, Object>> memorySchedulerHealth() {
        return ResponseEntity.ok(scheduler.getMetrics());
    }
}
```

---

## Best Practices

### 1. Configuration

**DO**: Configure appropriate intervals based on your load
```yaml
memory:
  scheduler:
    fixedRate: 300000  # 5 minutes for normal load
```

**DON'T**: Use very short intervals
```yaml
# Bad: Too frequent
fixedRate: 1000  # 1 second - will overload system
```

### 2. Concurrency

**DO**: Set reasonable concurrent task limits
```yaml
memory:
  scheduler:
    maxConcurrentTasks: 5  # Good for most cases
```

**DON'T**: Set too high
```yaml
# Bad: Too many concurrent tasks
maxConcurrentTasks: 100  # May exhaust resources
```

### 3. Error Handling

**DO**: Use appropriate degradation mode
```yaml
memory:
  scheduler:
    degradationMode: backoff  # Auto-recovery
```

**DON'T**: Use disable in production
```yaml
# Risky: Manual intervention required
degradationMode: disable
```

### 4. Monitoring

**DO**: Monitor metrics regularly
```java
Map<String, Object> metrics = scheduler.getMetrics();
logger.info("Success rate: {}", metrics.get("successRate"));
```

**DON'T**: Ignore error rates
```java
// Bad: Not checking failure rate
if (scheduler.calculateFailureRate() > 50) {
    // Should handle this
}
```

### 5. Retry Configuration

**DO**: Set reasonable retry limits
```yaml
memory:
  scheduler:
    retryMaxAttempts: 3
    retryInitialDelay: 1000
```

**DON'T**: Retry indefinitely
```yaml
# Bad: May never complete
retryMaxAttempts: 999
```

---

## Troubleshooting

### Problem: Scheduler Not Running

**Symptoms**:
- No extractions occurring
- Health check shows "Enabled: false"

**Solutions**:
1. Check configuration:
   ```yaml
   memory:
     scheduler:
       enabled: true
   ```
2. Enable programmatically:
   ```java
   scheduler.enable();
   ```
3. Check application logs for startup errors

### Problem: High Failure Rate

**Symptoms**:
- Failure rate > 50%
- Many extraction errors in logs

**Solutions**:
1. Check TriggerService availability:
   ```java
   if (!triggerService.isAvailable()) {
       logger.error("TriggerService not available");
   }
   ```
2. Verify database connections
3. Check memory service configuration
4. Review error logs for specific issues

### Problem: Slow Processing

**Symptoms**:
- High average processing time
- Tasks queueing up

**Solutions**:
1. Increase concurrent tasks:
   ```yaml
   memory:
     scheduler:
       maxConcurrentTasks: 10
   ```
2. Optimize database queries
3. Check system resources (CPU, memory)
4. Review extraction logic

### Problem: Frequent Retries

**Symptoms**:
- Many retry attempts in logs
- High backoff delays

**Solutions**:
1. Identify root cause from logs
2. Fix underlying issues
3. Adjust retry configuration:
   ```yaml
   memory:
     scheduler:
       retryMaxAttempts: 2  # Reduce attempts
       retryInitialDelay: 2000  # Increase delay
   ```
4. Consider using backoff degradation mode

### Problem: Scheduler Disabled After Errors

**Symptoms**:
- Scheduler stopped running
- "Scheduler disabled" in logs

**Solutions**:
1. Check degradation mode:
   ```yaml
   memory:
     scheduler:
       degradationMode: backoff  # Auto-recovery
   ```
2. Re-enable scheduler:
   ```java
   scheduler.enable();
   ```
3. Restart scheduler:
   ```java
   scheduler.restart();
   ```

---

## Performance Tuning

### Low Load (Development)

```yaml
memory:
  scheduler:
    enabled: true
    fixedRate: 600000  # 10 minutes
    maxConcurrentTasks: 2
    retryMaxAttempts: 2
```

### Medium Load (Staging)

```yaml
memory:
  scheduler:
    enabled: true
    fixedRate: 300000  # 5 minutes
    maxConcurrentTasks: 5
    retryMaxAttempts: 3
```

### High Load (Production)

```yaml
memory:
  scheduler:
    enabled: true
    fixedRate: 180000  # 3 minutes
    maxConcurrentTasks: 10
    retryMaxAttempts: 3
    degradationMode: backoff
    failureRateThreshold: 40
```

---

## Summary

The `MemoryExtractionScheduler` provides:

✅ **Automated Scheduling**: Spring Boot @Scheduled integration
✅ **Async Processing**: Non-blocking execution with ExecutorService
✅ **Retry Mechanism**: Exponential backoff for reliability
✅ **Error Handling**: Multiple degradation strategies
✅ **Performance Monitoring**: Comprehensive metrics collection
✅ **Dynamic Configuration**: Runtime configuration updates
✅ **Graceful Shutdown**: Clean resource management
✅ **Health Monitoring**: Built-in health checks

**Key Features**:
- Configurable execution intervals
- Concurrent task control
- Automatic retry with backoff
- Error rate monitoring
- Degradation strategies
- Performance metrics
- Health check endpoints

---

**Document Version**: 1.0
**Last Updated**: 2026-03-08
**Author**: YesBoss Team
