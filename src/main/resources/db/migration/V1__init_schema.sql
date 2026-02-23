-- ==========================================
-- YesBoss Database Schema Initialization
-- SQLite DDL with Compound Indexes
-- Version: 1.0
-- ==========================================

-- ==========================================
-- 1. 任务会话表 (task_session)
-- Core Entity: State Machine Engine
-- Manages Master/Worker lifecycle, task breakdown, and IM routing mapping
-- ==========================================
CREATE TABLE IF NOT EXISTS task_session (
    id TEXT PRIMARY KEY,
    parent_id TEXT,
    im_type TEXT NOT NULL CHECK(im_type IN ('FEISHU', 'SLACK', 'CLI')),
    im_group_id TEXT NOT NULL,
    role TEXT NOT NULL CHECK(role IN ('MASTER', 'WORKER')),
    status TEXT NOT NULL CHECK(status IN ('PLANNING', 'RUNNING', 'SUSPENDED', 'COMPLETED', 'FAILED')),
    topic TEXT NOT NULL,
    execution_plan TEXT,
    assigned_task TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- Index: Accelerate Webhook message routing to locate specific task (highest frequency query)
CREATE INDEX IF NOT EXISTS idx_session_im_route ON task_session(im_type, im_group_id);

-- Index: Accelerate finding all Worker child tasks under a Master
CREATE INDEX IF NOT EXISTS idx_session_parent ON task_session(parent_id);


-- ==========================================
-- 2. 上下文消息表 (chat_message)
-- Core Entity: Dual-Stream Memory Hippocampus
-- Supports LLM prompt assembly, noise isolation, and complex SDK polymorphic object deserialization
-- ==========================================
CREATE TABLE IF NOT EXISTS chat_message (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL,
    stream_type TEXT NOT NULL CHECK(stream_type IN ('GLOBAL', 'LOCAL')),
    sequence_num INTEGER NOT NULL,
    msg_role TEXT NOT NULL CHECK(msg_role IN ('system', 'user', 'assistant', 'tool')),
    payload_format TEXT NOT NULL CHECK(payload_format IN ('PLAIN_TEXT', 'ANTHROPIC_BLOCKS', 'OPENAI_CHATS')),
    content TEXT NOT NULL,
    token_count INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL,

    -- Foreign key constraint (optional for SQLite, but good for data integrity)
    FOREIGN KEY (session_id) REFERENCES task_session(id) ON DELETE CASCADE
);

-- [CRITICAL UNIQUE COMPOUND INDEX]: Ensures dual-stream absolute isolation and strict sequence ordering
-- Prevents out-of-order context that could cause LLM hallucination
CREATE UNIQUE INDEX IF NOT EXISTS idx_chat_msg_seq ON chat_message(session_id, stream_type, sequence_num);

-- Index: Optimize context retrieval queries
CREATE INDEX IF NOT EXISTS idx_chat_msg_session ON chat_message(session_id, stream_type);


-- ==========================================
-- 3. 工具执行流水表 (tool_execution_log)
-- Core Entity: Sandbox Audit Black Box
-- Perfectly stitched with LLM reasoning context via tool_call_id, provides UI rendering data source
-- ==========================================
CREATE TABLE IF NOT EXISTS tool_execution_log (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL,
    tool_call_id TEXT NOT NULL,
    tool_name TEXT NOT NULL,
    arguments TEXT NOT NULL,
    result TEXT,
    is_intercepted INTEGER DEFAULT 0 CHECK(is_intercepted IN (0, 1)),
    created_at INTEGER NOT NULL,

    -- Foreign key constraint
    FOREIGN KEY (session_id) REFERENCES task_session(id) ON DELETE CASCADE
);

-- Index: Accelerate frontend UI pulling Worker execution logs
CREATE INDEX IF NOT EXISTS idx_tool_log_session ON tool_execution_log(session_id);

-- Index: Accelerate reverse tracing LLM original prompt thoughts via tool_call_id
CREATE INDEX IF NOT EXISTS idx_tool_log_call_id ON tool_execution_log(tool_call_id);


-- ==========================================
-- Database Metadata
-- ==========================================
CREATE TABLE IF NOT EXISTS db_metadata (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);

-- Track schema version
INSERT OR REPLACE INTO db_metadata (key, value, updated_at)
VALUES ('schema_version', '1.0', strftime('%s', 'now') * 1000);
