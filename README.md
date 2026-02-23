# YesBoss - Multi-Agent Task Orchestration Platform

A result-oriented, minimalist Multi-Agent system designed for complex engineering and business task automation through Master-Worker hierarchy architecture with deep office IM integration.

## Project Overview

YesBoss is an intelligent task orchestration platform that uses a hierarchical multi-agent architecture to autonomously handle complex tasks. The system features:

- **Master-Worker Architecture**: A central orchestrator (Master) manages multiple execution units (Workers)
- **Deep IM Integration**: Seamless integration with Feishu and Slack for natural user interaction
- **Dual-Stream Context Management**: Separate global and local context streams for optimal token usage
- **Human-in-the-Loop**: Safety mechanisms requiring human approval for high-risk operations
- **Multi-LLM Support**: Compatible with Claude, Gemini, GPT, and ZhipuGLM
- **MCP Protocol**: Full support for Model Context Protocol tools

## Architecture

### Core Components

1. **Gateway Layer (接入路由层)**
   - IM Webhook routing for real-time event capture
   - Session lifecycle management (one task per group chat)
   - Minimalist UI renderer for progress bars and summary cards

2. **Agent Orchestration Layer (业务编排层)**
   - **Master Agent**: Global controller responsible for:
     - Requirement clarification
     - Environment exploration with read-only tools
     - Task planning and distribution
     - Final summary generation
   - **Worker Agents**: Isolated execution units responsible for:
     - Micro ReAct loops for autonomous tool calling
     - Task execution with sandbox protection
     - Local context management

3. **Shared Core Engine Layer (共享核心引擎层)**
   - **Dual-Stream Context Manager**:
     - Global Stream: Maintains master's global constraints and exploration summaries
     - Local Stream: Maintains worker's isolated task context
   - **Unified Toolchain & Sandbox**:
     - RBAC-based tool access control
     - Blacklist-based高危命令拦截
   - **Multi-Model Router**: Flexible LLM allocation for different roles

4. **Infrastructure Layer (基础设施层)**
   - SQLite concurrent persistence with lock-free async queue
   - Cascade data deletion mechanism for privacy

## Tech Stack

- **Language**: Java 21+
- **Key Features**:
  - Java Records for immutable data structures
  - Virtual Threads for high-concurrency async operations
  - Sealed interfaces for type-safe patterns
- **Database**: SQLite with custom single-thread writer
- **LLM Providers**: Anthropic Claude, Google Gemini, OpenAI GPT, ZhipuGLM
- **IM Platforms**: Feishu, Slack

## Workflow

### Standard Task Execution Flow

```
1. User inputs task via IM
   ↓
2. Gateway creates dedicated group chat
   ↓
3. Master Agent wakes up for requirement clarification
   ↓
4. Environment exploration (read-only tools)
   ↓
5. Fixed planning process generates sub-tasks
   ↓
6. Framework injects initial context to Workers
   ↓
7. Workers execute in isolated ReAct loops
   ├─ Normal execution with tool calls
   ├─ Circuit breaker after 20 rounds
   └─ Human-in-the-loop for blacklisted commands
   ↓
8. Context condensation (bottom-up merging)
   ↓
9. Master generates final summary card
```

### Human-in-the-Loop Flow

```
1. Worker attempts blacklisted tool
   ↓
2. SandboxInterceptor throws SuspendExecutionException
   ↓
3. SuspendResumeEngine suspends task (SUSPENDED state)
   ↓
4. System spoofs global message and pushes UI card to IM
   ↓
5. User clicks approve/reject button
   ↓
6. Webhook routes decision back to SuspendResumeEngine
   ↓
7a. Approved: Execute with bypass and resume
7b. Rejected: Forge error and resume
```

## Directory Structure

```
YesBoss/
├── docs/                          # Design documents
│   ├── 整体架构设计.md
│   ├── 功能需求文档.txt
│   ├── 技术模块设计.md
│   ├── LLM路由网关模块.md
│   ├── 双流上下文管理模块设计.md
│   ├── 统一工具链与沙箱模块设计.md
│   ├── 状态机与调度引擎模块设计.md
│   ├── 接入与路由模块设计.md
│   ├── 异步持久化模块设计.md
│   └── 时序图/
├── task.json                      # Task tracking database
├── log.log                        # Execution history log
└── README.md                      # This file
```

## Autonomous Agent Workflow

This project follows a strict autonomous agent workflow:

### Phase 1: Project Initialization
1. Initialize Git repository
2. Create `log.log` for execution tracking
3. Create `README.md` with project documentation
4. Initial commit

### Phase 2: Standard Operating Procedure (SOP)

**Step 1: Context Synchronization**
- Read `README.md` for project overview
- Read `log.log` for recent execution history
- Check git history for latest code changes

**Step 2: Task Acquisition**
- Read `task.json`
- Find first task with `"passes": false`
- If all complete, output "ALL TASKS COMPLETED"

**Step 3: Task Execution**
- Execute task requirements
- Follow TDD approach (develop → test pairs)

**Step 4: Status Update & Logging**
- Update `"passes"` to `true` on success
- Append execution summary to `log.log`
- Commit changes with descriptive message

### Strict Rules

**CRITICAL**: The agent is strictly forbidden from modifying `task.json` except for updating `"passes"` values:
- ❌ DO NOT add new tasks
- ❌ DO NOT delete existing tasks
- ❌ DO NOT alter task descriptions or IDs
- ✅ ONLY update `"passes": false` to `"passes": true` upon success

## Development Status

### Task Modules

1. **异步持久化模块 (Async Persistence Module)**
   - SQLite schema initialization
   - DbWriteEvent definitions
   - SingleThreadDbWriter engine
   - Repository layer implementation

2. **LLM路由网关模块 (LLM Routing Gateway)**
   - UnifiedMessage domain entity
   - VendorSdkAdapter implementations
   - LlmClient and ModelRouter

3. **统一工具链与沙箱模块 (Unified Toolchain & Sandbox)**
   - AgentTool interface
   - SandboxInterceptor with blacklist
   - ToolRegistry for RBAC
   - McpToolAdapter for external services

4. **双流上下文管理模块 (Dual-Stream Context Manager)**
   - GlobalStreamManager
   - LocalStreamManager
   - InjectionEngine for top-down context assembly
   - CondensationEngine for bottom-up summarization

5. **状态机与调度引擎模块 (State Machine & Scheduling)**
   - TaskManager with status transitions
   - CircuitBreaker for infinite loop prevention
   - SuspendResumeEngine for human-in-the-loop
   - MasterRunner and WorkerRunner orchestration

6. **接入与路由模块 (Access & Routing)**
   - SessionManager for IM route binding
   - UICardRenderer and IMMessagePusher
   - WebhookController for async API handling
   - Interactive UI callback handling

## Key Design Principles

1. **Hybrid Engine**: Separation of AI-driven problem solving and framework-controlled lifecycle management
2. **Unified Tool Abstraction**: All tools (native and MCP) exposed through a standard interface
3. **Async-First**: Webhook responses return immediately; actual processing happens on virtual threads
4. **Safety by Default**: Blacklist sandbox with mandatory human approval for risky operations
5. **Resource Efficiency**: Suspended workers release all threads; resumed workers create new threads

## License

[Specify your license here]

## Contributing

[Specify contribution guidelines here]
