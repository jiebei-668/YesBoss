-- ==========================================
-- YesBoss Memory Persistence Module Tables
-- SQLite DDL with Vector Index Support
-- Version: 2.0
-- ==========================================

-- ==========================================
-- 1. Resources Table (对话资源存储)
-- Stores raw conversation data with embeddings
-- ==========================================
CREATE TABLE IF NOT EXISTS resources (
    id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL,
    session_id TEXT NOT NULL,
    content TEXT NOT NULL,
    abstract TEXT,
    embedding BLOB,
    message_count INTEGER DEFAULT 0,
    deleted INTEGER DEFAULT 0 CHECK(deleted IN (0, 1)),
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,

    -- Foreign key constraints
    FOREIGN KEY (conversation_id) REFERENCES task_session(id) ON DELETE CASCADE,
    FOREIGN KEY (session_id) REFERENCES task_session(id) ON DELETE CASCADE
);

-- Index: Accelerate queries by conversation_id
CREATE INDEX IF NOT EXISTS resource_conversation_idx ON resources(conversation_id);

-- Index: Accelerate queries by session_id
CREATE INDEX IF NOT EXISTS resource_session_idx ON resources(session_id);

-- Index: Find resources without embeddings for batch processing
CREATE INDEX IF NOT EXISTS resource_embedding_null_idx ON resources(embedding) WHERE embedding IS NULL;

-- Index: Query by timestamp
CREATE INDEX IF NOT EXISTS resource_created_at_idx ON resources(created_at);

-- ==========================================
-- 2. Snippets Table (结构化记忆)
-- Stores structured memory extracted from Resources
-- ==========================================
CREATE TABLE IF NOT EXISTS snippets (
    id TEXT PRIMARY KEY,
    resource_id TEXT NOT NULL,
    summary TEXT NOT NULL,
    memory_type TEXT NOT NULL CHECK(memory_type IN ('PROFILE', 'EVENT', 'KNOWLEDGE', 'BEHAVIOR', 'SKILL', 'TOOL')),
    embedding BLOB,
    timestamp INTEGER NOT NULL,
    deleted INTEGER DEFAULT 0 CHECK(deleted IN (0, 1)),
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,

    -- Foreign key constraint with cascade delete
    FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE CASCADE
);

-- Index: Accelerate queries by resource_id
CREATE INDEX IF NOT EXISTS snippet_resource_idx ON snippets(resource_id);

-- Index: Find snippets without embeddings for batch processing
CREATE INDEX IF NOT EXISTS snippet_embedding_null_idx ON snippets(embedding) WHERE embedding IS NULL;

-- Index: Query by memory_type
CREATE INDEX IF NOT EXISTS snippet_memory_type_idx ON snippets(memory_type);

-- Index: Query by timestamp
CREATE INDEX IF NOT EXISTS snippet_timestamp_idx ON snippets(timestamp);

-- ==========================================
-- 3. Preferences Table (偏好主题)
-- Stores user preference topics with embeddings
-- ==========================================
CREATE TABLE IF NOT EXISTS preferences (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    summary TEXT NOT NULL,
    embedding BLOB,
    deleted INTEGER DEFAULT 0 CHECK(deleted IN (0, 1)),
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- Index: Query by name (unique constraint already provides indexing)
CREATE INDEX IF NOT EXISTS preference_name_idx ON preferences(name);

-- Index: Find preferences without embeddings for batch processing
CREATE INDEX IF NOT EXISTS preference_embedding_null_idx ON preferences(embedding) WHERE embedding IS NULL;

-- ==========================================
-- Vector Indexes (for sqlite-vec extension)
-- Note: These indexes will be created when sqlite-vec is available
-- The vector indexes use HNSW algorithm with M=16, ef_construction=64
-- ==========================================

-- Resource vector index (1536 dimensions, float32 = 6144 bytes)
-- CREATE VIRTUAL TABLE IF NOT EXISTS resource_vector_idx USING vec0(
--     embedding BLOB(6144)
-- );

-- Snippet vector index (1536 dimensions, float32 = 6144 bytes)
-- CREATE VIRTUAL TABLE IF NOT EXISTS snippet_vector_idx USING vec0(
--     embedding BLOB(6144)
-- );

-- Preference vector index (1536 dimensions, float32 = 6144 bytes)
-- CREATE VIRTUAL TABLE IF NOT EXISTS preference_vector_idx USING vec0(
--     embedding BLOB(6144)
-- );

-- ==========================================
-- Database Metadata Update
-- ==========================================
INSERT OR REPLACE INTO db_metadata (key, value, updated_at)
VALUES ('schema_version', '2.0', strftime('%s', 'now') * 1000);
