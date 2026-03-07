# Data Isolation Validation Guide

## Overview

The memory module maintains **complete data isolation** from the core YesBoss system through multiple layers of separation. This document describes the validation mechanisms and guarantees provided by the `DataIsolationValidator`.

## Table of Contents

1. [Isolation Guarantees](#isolation-guarantees)
2. [Validation Layers](#validation-layers)
3. [Database Separation](#database-separation)
4. [Table Namespace Isolation](#table-namespace-isolation)
5. [Transaction Isolation](#transaction-isolation)
6. [Cache Isolation](#cache-isolation)
7. [Backup/Restore Isolation](#backuprestore-isolation)
8. [Using DataIsolationValidator](#using-dataisolationvalidator)
9. [Validation Results](#validation-results)
10. [Troubleshooting](#troubleshooting)

---

## Isolation Guarantees

The memory module provides the following data isolation guarantees:

✅ **Separate Database Files**: Memory data stored in independent database file
✅ **Table Namespace**: All memory tables use `memory_` prefix
✅ **Transaction Independence**: Memory transactions don't affect core system
✅ **Connection Pool Isolation**: Separate connection pools for memory operations
✅ **Cache Separation**: Memory cache is independent from core cache
✅ **Backup Independence**: Memory data can be backed up/restored independently
✅ **Migration Safety**: Schema migrations only affect memory tables

---

## Validation Layers

The `DataIsolationValidator` performs 8 comprehensive validation checks:

### 1. Database Separation Validation
- Verifies memory and core systems use different database files
- Checks database file paths are distinct
- Validates parent directory permissions
- Detects shared database configurations

### 2. Table Namespace Validation
- Ensures all memory tables use `memory_` prefix
- Detects table name conflicts with core system
- Verifies expected memory tables exist
- Validates naming conventions

### 3. Transaction Isolation Validation
- Tests transaction rollback behavior
- Verifies independent transaction management
- Confirms no cross-database transactions
- Validates ACID properties

### 4. Data Access Pattern Validation
- Ensures memory module doesn't access core tables
- Validates data access through service layer
- Checks for cross-module dependencies
- Verifies proper encapsulation

### 5. Cache Isolation Validation
- Verifies memory cache is separate
- Checks cache key namespacing
- Validates cache size configuration
- Ensures no cache key conflicts

### 6. Backup/Restore Isolation Validation
- Confirms independent backup capability
- Verifies separate database files
- Validates backup directory structure
- Checks file permissions

### 7. Migration Safety Validation
- Ensures migrations only target memory tables
- Verifies migration table isolation
- Validates migration script scope
- Checks Flyway configuration

### 8. Connection Pool Isolation Validation
- Verifies separate connection pools
- Validates pool size configuration
- Checks connection management
- Ensures resource independence

---

## Database Separation

### Architecture

```
YesBoss System
├── Core System Database
│   └── data/yesboss.db
│       ├── task_session
│       ├── task_step
│       ├── webhook_event
│       ├── im_message
│       └── ... (core tables)
│
└── Memory Module Database
    └── data/memory_vec.db (separate file)
        ├── memory_resources
        ├── memory_snippets
        ├── memory_preferences
        └── memory_embeddings
```

### Validation Checks

```java
DataIsolationValidator validator = new DataIsolationValidator();
ValidationResult result = validator.validate();

// Check database separation
DatabaseIsolationInfo dbInfo = validator.getDatabaseIsolationInfo();
assert dbInfo.isSeparateDatabases();  // Must be true
assert !dbInfo.getMemoryDatabasePath().equals(dbInfo.getCoreDatabasePath());
```

### Configuration

```yaml
# application-memory.yml
memory:
  database:
    path: data/memory_vec.db  # Separate from core database
    poolSize: 10
    poolTimeout: 30000
```

---

## Table Namespace Isolation

### Naming Convention

All memory module tables MUST use the `memory_` prefix:

| Table Name | Purpose | Isolated? |
|------------|---------|-----------|
| `memory_resources` | Store conversation resources | ✅ Yes |
| `memory_snippets` | Store structured memories | ✅ Yes |
| `memory_preferences` | Store user preferences | ✅ Yes |
| `memory_embeddings` | Store vector embeddings | ✅ Yes |

### Reserved Table Names

The following core system tables are **RESERVED** and must NOT be used by memory module:

```
task_session, task_step, webhook_event, im_message,
user_profile, agent_config, system_config
```

### Validation

```java
// Validator checks for proper table names
Set<String> tables = getDatabaseTables("data/memory_vec.db");
for (String table : tables) {
    assert table.startsWith("memory_") : "Table must use memory_ prefix";
}
```

---

## Transaction Isolation

### Transaction Boundaries

Memory module transactions are **completely independent** from core system transactions:

```java
// Memory transaction (doesn't affect core)
Connection memoryConn = getMemoryConnection();
memoryConn.setAutoCommit(false);
try {
    // Memory operations
    saveMemoryResource(resource);
    saveMemorySnippet(snippet);
    memoryConn.commit();  // Only affects memory database
} catch (Exception e) {
    memoryConn.rollback();  // Only rolls back memory changes
}

// Core transaction (doesn't affect memory)
Connection coreConn = getCoreConnection();
coreConn.setAutoCommit(false);
try {
    // Core operations
    saveTaskSession(session);
    coreConn.commit();  // Only affects core database
} catch (Exception e) {
    coreConn.rollback();  // Only rolls back core changes
}
```

### Validation Tests

The validator performs the following transaction tests:

1. **Rollback Test**: Create test table, rollback, verify it's gone
2. **Connection Test**: Verify separate database connections
3. **Isolation Level Test**: Check transaction isolation level
4. **Independence Test**: Ensure transactions don't interfere

---

## Cache Isolation

### Cache Architecture

```
YesBoss System
├── Core Cache
│   └── Cache keys: "task:*", "session:*", "user:*"
│
└── Memory Cache (Separate)
    └── Cache keys: "memory:resource:*", "memory:snippet:*"
```

### Cache Key Namespacing

All memory cache keys MUST use the `memory:` prefix:

```java
// Memory cache keys (properly namespaced)
String memoryKey = "memory:resource:" + resourceId;
cache.put(memoryKey, resource);

// Core cache keys (different namespace)
String taskKey = "task:session:" + sessionId;
cache.put(taskKey, session);  // No conflict with memory keys
```

### Validation

```java
// Validator checks cache configuration
int cacheSize = config.get(MemoryConfig.CACHE_SIZE);
assert cacheSize > 0 : "Memory cache must be configured";
```

---

## Backup/Restore Isolation

### Independent Backup

Memory database can be backed up **independently** from core database:

```bash
# Backup memory database only
cp data/memory_vec.db backups/memory_vec_$(date +%Y%m%d).db

# Restore memory database only
cp backups/memory_vec_20260307.db data/memory_vec.db

# Core database is unaffected
ls -lh data/yesboss.db  # Still intact
```

### Backup Strategy

#### Full System Backup
```bash
# Backup entire system
tar -czf yesboss_full_$(date +%Y%m%d).tar.gz \
    data/yesboss.db \
    data/memory_vec.db \
    logs/
```

#### Memory-Only Backup
```bash
# Backup only memory data
tar -czf memory_only_$(date +%Y%m%d).tar.gz \
    data/memory_vec.db
```

#### Core-Only Backup
```bash
# Backup only core data
tar -czf core_only_$(date +%Y%m%d).tar.gz \
    data/yesboss.db
```

### Validation

```java
// Validator checks backup isolation
DatabaseIsolationInfo info = validator.getDatabaseIsolationInfo();
assert info.isSeparateDatabases() : "Cannot backup independently";
```

---

## Migration Safety

### Migration Table Isolation

Memory module uses **separate Flyway migration table**:

```
Core System Migrations:
├── flyway_schema_history (for core tables)

Memory Module Migrations:
└── flyway_schema_history_memory (for memory tables)
```

### Migration Script Safety

Memory migration scripts MUST:

1. ✅ Only target `memory_*` tables
2. ✅ Use separate migration history table
3. ✅ Not modify core system tables
4. ✅ Be idempotent (can be re-run safely)

### Example Migration Script

```sql
-- V2__init_memory_tables.sql
CREATE TABLE IF NOT EXISTS memory_resources (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36),
    content TEXT,
    embedding BLOB(6144),
    created_at BIGINT
);

-- Only creates memory_* tables, doesn't touch core tables
```

### Validation

```java
// Validator checks migration safety
Set<String> tables = getDatabaseTables("data/memory_vec.db");
for (String table : tables) {
    assert table.startsWith("memory_") : "Migration must only create memory tables";
}
```

---

## Using DataIsolationValidator

### Basic Usage

```java
// Create validator
DataIsolationValidator validator = new DataIsolationValidator();

// Run validation
ValidationResult result = validator.validate();

// Check result
if (result.isValid()) {
    logger.info("Data isolation validation passed");
} else {
    logger.error("Data isolation validation failed");
    for (ValidationIssue issue : result.getCriticalIssues()) {
        logger.error("CRITICAL: {}", issue);
    }
}
```

### Detailed Report

```java
// Get detailed report
String report = validator.getDetailedReport();
System.out.println(report);
```

Output:
```
Data Isolation Validation Summary:
  Critical Issues: 0
  Warnings: 1
  Info: 5
  Total: 6

Status: PASSED - Review warnings recommended

Metrics:
  validation_duration_ms: 145
  total_issues: 6
  memory_database_path: data/memory_vec.db
  core_database_path: data/yesboss.db
  separate_databases: true
  memory_tables_count: 4
  core_tables_count: 7

Detailed Issues:
  [INFO] cache_isolation: Cache isolation verified - Cache size: 1000 entries
  [WARNING] table_namespace: Memory table missing 'memory_' prefix: test_table - Rename table to use 'memory_' prefix for clarity
  ...
```

### Deployment Validation

```java
// Validate before deployment
DataIsolationValidator validator = new DataIsolationValidator();
if (validator.validateForDeployment()) {
    logger.info("Safe to deploy - data isolation verified");
    startApplication();
} else {
    logger.error("Cannot deploy - data isolation issues detected");
    System.exit(1);
}
```

### Programmatic Access to Isolation Info

```java
// Get database isolation information
DatabaseIsolationInfo info = validator.getDatabaseIsolationInfo();

System.out.println("Memory DB: " + info.getMemoryDatabasePath());
System.out.println("Core DB: " + info.getCoreDatabasePath());
System.out.println("Separate: " + info.isSeparateDatabases());
System.out.println("Memory Tables: " + info.getMemoryTables());
System.out.println("Core Tables: " + info.getCoreTables());
```

---

## Validation Results

### Issue Severity Levels

| Severity | Meaning | Action Required |
|----------|---------|-----------------|
| **CRITICAL** | Deployment blocker | Must fix before deployment |
| **WARNING** | Potential issue | Should fix, review recommended |
| **INFO** | Informational | No action required, for awareness |

### Common Issues

#### Issue: Same Database File
```
[CRITICAL] database_separation: Memory module using same database file as core system
```
**Solution**: Configure separate database:
```yaml
memory:
  database:
    path: data/memory_vec.db  # Separate from data/yesboss.db
```

#### Issue: Table Name Conflict
```
[CRITICAL] table_namespace_conflict: Memory table using core system name: task_session
```
**Solution**: Rename table with `memory_` prefix:
```sql
ALTER TABLE task_session RENAME TO memory_task_session;
```

#### Issue: Missing Table Prefix
```
[WARNING] table_namespace: Memory table missing 'memory_' prefix: my_table
```
**Solution**: Rename table:
```sql
ALTER TABLE my_table RENAME TO memory_my_table;
```

---

## Troubleshooting

### Validation Fails

**Problem**: Validation returns critical issues

**Solution**:
1. Review detailed report: `validator.getDetailedReport()`
2. Fix configuration issues
3. Re-run validation: `validator.validate()`
4. Repeat until no critical issues

### Database Permission Errors

**Problem**: Cannot access memory database

**Solution**:
```bash
# Check permissions
ls -la data/memory_vec.db

# Fix permissions
chmod 660 data/memory_vec.db
chown appuser:appgroup data/memory_vec.db
```

### Connection Pool Exhaustion

**Problem**: Too many connections to memory database

**Solution**:
```yaml
memory:
  database:
    poolSize: 10  # Adjust based on load
    poolTimeout: 30000
```

### Cache Conflicts

**Problem**: Cache keys conflicting with core system

**Solution**: Ensure all memory cache keys use `memory:` prefix:
```java
String key = "memory:resource:" + resourceId;  // Correct
String key = "resource:" + resourceId;          // Wrong - conflicts with core
```

---

## Best Practices

### 1. Always Validate Before Deployment
```java
assert validator.validateForDeployment() : "Data isolation check failed";
```

### 2. Use Separate Databases
Never share database files between memory and core modules.

### 3. Follow Naming Conventions
- Tables: `memory_*` prefix
- Cache keys: `memory:` prefix
- Configuration: `memory.*` prefix

### 4. Test Transaction Isolation
```java
// Test rollback works
testRollback();
assert !testTableExists() : "Rollback failed";
```

### 5. Monitor Validation Metrics
```java
Map<String, Object> metrics = result.getMetrics();
logger.info("Validation took: {}ms", metrics.get("validation_duration_ms"));
```

---

## Summary

The `DataIsolationValidator` provides comprehensive validation of data isolation between the memory module and core YesBoss system. It ensures:

✅ Separate database files
✅ Distinct table namespaces
✅ Independent transactions
✅ Isolated connection pools
✅ Separate cache namespaces
✅ Independent backup/restore
✅ Safe schema migrations

**Key Principle**: The memory module is designed to be **completely independent** from the core system, with no shared data, no shared transactions, and no shared resources. This ensures that memory module failures or issues cannot affect the core YesBoss system.

---

**Document Version**: 1.0
**Last Updated**: 2026-03-07
**Author**: YesBoss Team
