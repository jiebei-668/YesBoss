# Memory Persistence Module - Configuration Guide

## Table of Contents

1. [Overview](#1-overview)
2. [Basic Configuration](#2-basic-configuration)
3. [Vector Store Configuration](#3-vector-store-configuration)
4. [Embedding Service Configuration](#4-embedding-service-configuration)
5. [Content Processing Configuration](#5-content-processing-configuration)
6. [Trigger Configuration](#6-trigger-configuration)
7. [Monitoring Configuration](#7-monitoring-configuration)
8. [Database Configuration](#8-database-configuration)
9. [Performance Tuning](#9-performance-tuning)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Overview

The memory persistence module is configured through `application-memory.yml` (or `application-memory.properties`). Configuration is organized into logical sections:

- **Core settings**: Enable/disable module, vector store type
- **Embedding service**: Text embedding generation
- **Content processing**: Segmentation and summarization
- **Triggers**: Automatic memory extraction triggers
- **Monitoring**: Health checks and metrics
- **Performance**: Batch processing and optimization

---

## 2. Basic Configuration

### 2.1 Enable/Disable Module

```yaml
memory:
  enabled: true  # Set to false to disable the module
```

### 2.2 Minimal Configuration

The simplest working configuration:

```yaml
memory:
  enabled: true
  vector-store:
    type: sqlite
  embedding:
    provider: zhipu
    api-key: ${ZHIPU_API_KEY}
```

### 2.3 Complete Configuration Example

```yaml
memory:
  enabled: true

  vector-store:
    type: sqlite
    embedding-dimension: 1536
    similarity-threshold: 0.7

  embedding:
    provider: zhipu
    model: embedding-2
    batch-size: 100
    timeout: 30000
    max-retries: 3

  content-processor:
    max-segment-length: 2000
    min-segment-length: 100
    abstract-max-length: 200

  batch-processing:
    enabled: true
    interval: 60000
    batch-size: 100
    max-retries: 3

  triggers:
    interval:
      enabled: true
      cron: "0 */5 * * * ?"
    epoch-max:
      enabled: true
      max-resources: 1000
    conversation-round:
      enabled: true
      trigger-rounds: [5, 10, 15]

  monitoring:
    enabled: true
    metrics-export: prometheus
    alert-threshold:
      error-rate: 0.05
      latency-p99: 1000

  cache:
    enabled: true
    ttl: 3600000
    max-size: 1000
```

---

## 3. Vector Store Configuration

### 3.1 SQLite with sqlite-vec (Default)

**Recommended for**: Development, small-scale production, embedded deployments

```yaml
memory:
  vector-store:
    type: sqlite
    embedding-dimension: 1536  # Must match embedding model
    similarity-threshold: 0.7  # 0.0 to 1.0

spring:
  datasource:
    url: jdbc:sqlite:data/yesboss.db
    driver-class-name: org.sqlite.JDBC
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
```

**SQLite Configuration Options:**
```yaml
spring:
  datasource:
    url: jdbc:sqlite:${user.home}/.yesboss/data/yesboss.db?journal_mode=WAL
    hikari:
      maximum-pool-size: 5
      minimum-idle: 1
      connection-timeout: 30000
```

### 3.2 PostgreSQL with pgvector

**Recommended for**: Large-scale production, high-concurrency scenarios

```yaml
memory:
  vector-store:
    type: postgresql
    embedding-dimension: 1536
    similarity-threshold: 0.7
    index-type: hnsw  # or ivfflat
    hnsw-parameters:
      m: 16
      ef-construction: 64

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/yesboss
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

**PostgreSQL Connection Pool:**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### 3.3 Vector Store Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `type` | String | `sqlite` | Vector store type: `sqlite` or `postgresql` |
| `embedding-dimension` | int | `1536` | Dimension of embedding vectors |
| `similarity-threshold` | double | `0.7` | Minimum similarity for search results (0.0-1.0) |
| `index-type` | String | `hnsw` | Index type for PostgreSQL: `hnsw` or `ivfflat` |
| `hnsw-m` | int | `16` | HNSW M parameter (higher = better recall, more memory) |
| `hnsw-ef-construction` | int | `64` | HNSW construction ef (higher = better quality) |

---

## 4. Embedding Service Configuration

### 4.1 Provider Selection

**Supported Providers:**
- `zhipu` - Zhipu AI (recommended, tested)
- `anthropic` - Anthropic Claude
- `gemini` - Google Gemini
- `openai` - OpenAI GPT

```yaml
memory:
  embedding:
    provider: zhipu  # Choose provider
    api-key: ${API_KEY}  # Provider-specific API key
```

### 4.2 Provider-Specific Configuration

#### Zhipu AI

```yaml
memory:
  embedding:
    provider: zhipu
    api-key: ${ZHIPU_API_KEY}
    model: embedding-2  # or embedding-3
    base-url: https://open.bigmodel.cn/api/paas/v4/embeddings
```

#### Anthropic Claude

```yaml
memory:
  embedding:
    provider: anthropic
    api-key: ${ANTHROPIC_API_KEY}
    model: claude-v2
    base-url: https://api.anthropic.com/v1/messages
```

#### Google Gemini

```yaml
memory:
  embedding:
    provider: gemini
    api-key: ${GEMINI_API_KEY}
    model: embedding-001
    base-url: https://generativelanguage.googleapis.com/v1beta/models
```

#### OpenAI

```yaml
memory:
  embedding:
    provider: openai
    api-key: ${OPENAI_API_KEY}
    model: text-embedding-ada-002
    base-url: https://api.openai.com/v1/embeddings
```

### 4.3 Embedding Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `provider` | String | `zhipu` | Embedding service provider |
| `api-key` | String | - | API key for the provider |
| `model` | String | - | Model name (provider-specific) |
| `batch-size` | int | `100` | Maximum texts per batch request |
| `timeout` | int | `30000` | Request timeout in milliseconds |
| `max-retries` | int | `3` | Maximum retry attempts |
| `retry-delay` | int | `1000` | Initial retry delay in milliseconds |

### 4.4 Performance Tuning

```yaml
memory:
  embedding:
    batch-size: 200  # Increase for better throughput
    timeout: 60000   # Increase for slow connections
    max-retries: 5   # Increase for unreliable networks
    retry-delay: 2000  # Increase for backoff strategy
```

---

## 5. Content Processing Configuration

### 5.1 Segmentation Parameters

```yaml
memory:
  content-processor:
    max-segment-length: 2000  # Maximum characters per segment
    min-segment-length: 100   # Minimum characters per segment
    overlap: 200              # Overlap between segments (characters)
    segment-by: topic         # Strategy: topic, length, sentence
```

### 5.2 Summarization Parameters

```yaml
memory:
  content-processor:
    abstract-max-length: 200  # Maximum abstract length (characters)
    abstract-min-length: 50   # Minimum abstract length (characters)
    summary-max-length: 300   # Maximum snippet summary length
    summary-min-length: 100   # Minimum snippet summary length
```

### 5.3 Memory Type Extraction

```yaml
memory:
  content-processor:
    memory-types:
      - PROFILE      # User profile information
      - EVENT        # Events and occurrences
      - KNOWLEDGE    # Knowledge and facts
      - BEHAVIOR     # Behavioral patterns
      - SKILL        # Skills and capabilities
      - TOOL         # Tool preferences
    extraction-strategy: llm  # or rule-based, hybrid
```

### 5.4 Content Processor Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `max-segment-length` | int | `2000` | Maximum segment length in characters |
| `min-segment-length` | int | `100` | Minimum segment length in characters |
| `overlap` | int | `200` | Overlap between adjacent segments |
| `segment-by` | String | `topic` | Segmentation strategy |
| `abstract-max-length` | int | `200` | Maximum abstract length |
| `summary-max-length` | int | `300` | Maximum snippet summary length |

---

## 6. Trigger Configuration

### 6.1 Interval Trigger

Triggers memory extraction at regular intervals.

```yaml
memory:
  triggers:
    interval:
      enabled: true
      cron: "0 */5 * * * ?"  # Every 5 minutes
      # Cron format: second minute hour day month weekday
      # Examples:
      # "0 */1 * * * ?"   - Every 1 minute
      # "0 0 */1 * * ?"   - Every hour
      # "0 0 0 * * ?"     - Every day at midnight
```

### 6.2 Epoch-Max Trigger

Triggers when a certain number of resources accumulate.

```yaml
memory:
  triggers:
    epoch-max:
      enabled: true
      max-resources: 1000  # Trigger every 1000 resources
      check-interval: 60000  # Check every 60 seconds
```

### 6.3 Conversation-Round Trigger

Triggers at specific conversation rounds.

```yaml
memory:
  triggers:
    conversation-round:
      enabled: true
      trigger-rounds: [5, 10, 15, 20]  # Trigger at these rounds
      include-final-round: true  # Always trigger at conversation end
```

### 6.4 Trigger Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable/disable trigger |
| `cron` | String | - | Cron expression for interval trigger |
| `max-resources` | int | `1000` | Threshold for epoch trigger |
| `trigger-rounds` | int[] | - | Rounds for conversation trigger |

---

## 7. Monitoring Configuration

### 7.1 Enable Monitoring

```yaml
memory:
  monitoring:
    enabled: true
    metrics-export: prometheus  # or influx, graphite,日志
```

### 7.2 Metrics Collection

```yaml
memory:
  monitoring:
    metrics:
      collect-operations: true
      collect-latency: true
      collect-errors: true
      collect-resource-count: true
      export-interval: 60000  # Export every 60 seconds
```

### 7.3 Alert Thresholds

```yaml
memory:
  monitoring:
    alert-threshold:
      error-rate: 0.05  # Alert if error rate > 5%
      latency-p99: 1000  # Alert if P99 latency > 1000ms
      latency-p95: 500   # Alert if P95 latency > 500ms
      queue-depth: 1000  # Alert if queue depth > 1000
```

### 7.4 Health Checks

```yaml
memory:
  monitoring:
    health-check:
      enabled: true
      interval: 30000  # Check every 30 seconds
      timeout: 5000    # Timeout per check
      failure-threshold: 3  # Alert after 3 consecutive failures
```

### 7.5 Monitoring Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable monitoring |
| `metrics-export` | String | `prometheus` | Metrics export system |
| `error-rate` | double | `0.05` | Error rate threshold (0.0-1.0) |
| `latency-p99` | int | `1000` | P99 latency threshold (ms) |
| `latency-p95` | int | `500` | P95 latency threshold (ms) |

---

## 8. Database Configuration

### 8.1 SQLite Configuration

```yaml
spring:
  datasource:
    url: jdbc:sqlite:data/yesboss.db?journal_mode=WAL&synchronous=NORMAL
    driver-class-name: org.sqlite.JDBC
  jpa:
    hibernate:
      ddl-auto: update  # or validate, create, create-drop
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.community.dialect.SQLiteDialect
```

### 8.2 PostgreSQL Configuration

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/yesboss
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        jdbc:
          batch_size: 50
          order_inserts: true
          order_updates: true
```

### 8.3 Connection Pool Configuration

```yaml
spring:
  datasource:
    hikari:
      # Pool size
      maximum-pool-size: 20  # Maximum connections
      minimum-idle: 5        # Minimum idle connections

      # Timeouts
      connection-timeout: 30000     # 30 seconds
      idle-timeout: 600000          # 10 minutes
      max-lifetime: 1800000         # 30 minutes

      # Validation
      validation-timeout: 5000      # 5 seconds
      connection-test-query: SELECT 1

      # Performance
      auto-commit: true
      pool-name: YesBossHikariPool
```

### 8.4 JPA Configuration

```yaml
spring:
  jpa:
    # Database initialization
    hibernate:
      ddl-auto: update

    # SQL logging
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
        generate_statistics: false

    # Caching
    properties:
      hibernate:
        cache:
          use_second_level_cache: true
          use_query_cache: true
          region:
            factory_class: org.hibernate.cache.jcache.JCacheRegionFactory
```

---

## 9. Performance Tuning

### 9.1 Batch Processing

```yaml
memory:
  batch-processing:
    enabled: true
    interval: 60000      # Run every 60 seconds
    batch-size: 100      # Process 100 items per batch
    max-retries: 3       # Retry failed batches up to 3 times
    parallel-batches: 4  # Process 4 batches in parallel
```

### 9.2 Caching

```yaml
memory:
  cache:
    enabled: true
    type: caffeine  # or redis, ehcache
    ttl: 3600000    # Cache TTL: 1 hour
    max-size: 1000  # Maximum cache entries
    cache-spec: "maximumSize=1000,expireAfterWrite=1h"
```

### 9.3 Thread Pool Configuration

```yaml
memory:
  thread-pool:
    core-size: 4       # Core thread pool size
    max-size: 16       # Maximum thread pool size
    queue-capacity: 1000  # Queue capacity
    thread-name-prefix: "memory-"
    keep-alive-seconds: 60
```

### 9.4 Memory Limits

```yaml
memory:
  performance:
    max-heap-percentage: 70  # Use max 70% of heap
    max-buffer-size: 1000000  # Max buffer size (items)
    gc-threshold: 80  # Trigger GC at 80% memory usage
```

### 9.5 Performance Optimization Checklist

- **Enable batch processing** for high-throughput scenarios
- **Increase batch size** for better throughput (but watch memory)
- **Use connection pooling** with appropriate pool size
- **Enable caching** for frequently accessed data
- **Tune thread pool** based on CPU cores
- **Use HNSW indexes** for PostgreSQL (better performance than IVFFlat)
- **Enable WAL mode** for SQLite (better concurrency)
- **Monitor memory usage** and adjust heap size accordingly

---

## 10. Troubleshooting

### 10.1 Common Issues

#### Issue: Slow embedding generation

**Solution:**
```yaml
memory:
  embedding:
    batch-size: 200  # Increase batch size
    timeout: 60000   # Increase timeout
```

#### Issue: Out of memory errors

**Solution:**
```yaml
memory:
  batch-processing:
    batch-size: 50  # Reduce batch size
  cache:
    max-size: 500   # Reduce cache size
```

#### Issue: Database connection errors

**Solution:**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # Increase pool size
      minimum-idle: 5
      connection-timeout: 30000
```

#### Issue: Poor search results

**Solution:**
```yaml
memory:
  vector-store:
    similarity-threshold: 0.8  # Increase threshold
  embedding:
    model: embedding-3  # Use better model
```

### 10.2 Debug Mode

Enable debug logging:

```yaml
logging:
  level:
    tech.yesboss.memory: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

### 10.3 Performance Profiling

Enable statistics:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        generate_statistics: true
        format_sql: true
```

### 10.4 Health Check Endpoints

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
```

Access health check:
```bash
curl http://localhost:6000/actuator/health
```

---

## Configuration Validation

### Validate Configuration

Use this endpoint to validate your configuration:

```bash
curl http://localhost:6000/actuator/configprops | grep memory
```

### Check Active Profile

```bash
curl http://localhost:6000/actuator/env | grep activeProfiles
```

---

For more information, see:
- [Quick Start Guide](./QUICK_START.md)
- [API Documentation](./API_DOCUMENTATION.md)
- [Usage Examples](./USAGE_EXAMPLES.md)
