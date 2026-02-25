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

## Quick Start

### Prerequisites

- **Java**: JDK 17 or higher (with preview features enabled)
- **Maven**: 3.6+ for building the project
- **Feishu App**: A Feishu/Lark app with webhook permissions
- **LLM API Key**: At least one LLM provider API key (Anthropic Claude, Google Gemini, OpenAI, or ZhipuGLM)

### Installation

#### 1. Clone the Repository

```bash
git clone <repository-url>
cd YesBoss
```

#### 2. Configure Application

**Option 1: Using .env file (Recommended)**

Create or edit `.env` file in project root:

```bash
# LLM Provider Configuration (configure at least one)
ZHIPU_API_KEY=your-zhipu-api-key-here
# ANTHROPIC_API_KEY=your-anthropic-api-key-here

# Feishu Configuration
FEISHU_APP_ID=cli_axxxxxxxxxxxx
FEISHU_APP_SECRET=your-app-secret
FEISHU_ENCRYPT_KEY=your-encrypt-key
FEISHU_VERIFICATION_TOKEN=your-token

# Server Configuration (optional)
SERVER_PORT=8080
```

Load environment variables before running:

```bash
# Load .env file
source .env 2>/dev/null || export $(cat .env | grep -v '^#' | xargs)

# Or load manually
export ZHIPU_API_KEY="your-api-key"
export FEISHU_APP_ID="cli_axxxxxxxxxxxx"
export FEISHU_APP_SECRET="your-app-secret"
export FEISHU_ENCRYPT_KEY="your-encrypt-key"
export FEISHU_VERIFICATION_TOKEN="your-token"
```

**Option 2: Edit application.yml**

Edit `src/main/resources/application.yml` to configure your settings:

```yaml
# LLM Provider Configuration (configure at least one)
llm:
  zhipu:
    enabled: true
    apiKey: YOUR_ZHIPU_API_KEY  # Replace with your API key

# Feishu Configuration
im:
  feishu:
    enabled: true
    appId: cli_axxxxxxxxxxxx      # Your Feishu App ID
    appSecret: YOUR_APP_SECRET     # Your Feishu App Secret
    encryptKey: YOUR_ENCRYPT_KEY   # Your Feishu Encrypt Key
    verificationToken: YOUR_TOKEN  # Your Verification Token

# Server Configuration
app:
  server:
    port: 8080
```

**Option 3: Using environment variables directly**

```bash
export ZHIPU_API_KEY="your-api-key"
export FEISHU_APP_ID="cli_axxxxxxxxxxxx"
export FEISHU_APP_SECRET="your-app-secret"
export FEISHU_ENCRYPT_KEY="your-encrypt-key"
export FEISHU_VERIFICATION_TOKEN="your-token"
```

#### 3. Build the Project

```bash
mvn clean compile
```

#### 4. Run Tests (Optional)

```bash
mvn test
```

#### 5. Start the Application

**Important**: This project uses Java 17 preview features (virtual threads), so you must run with `--enable-preview` flag.

**Method 1: Using Maven + Java (Recommended)**

```bash
# Load environment variables from .env
source .env 2>/dev/null || export $(cat .env | grep -v '^#' | xargs)

# Build classpath
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt

# Run application
CP=$(cat cp.txt):target/classes
java --enable-preview -cp "$CP" tech.yesboss.YesBossApplication
```

**Method 2: Using Maven exec plugin**

```bash
# Load environment variables
source .env 2>/dev/null || export $(cat .env | grep -v '^#' | xargs)

# Set MAVEN_OPTS to enable preview features
export MAVEN_OPTS="--enable-preview"

# Run with Maven
mvn exec:java
```

**Method 3: Background运行**

```bash
# Load environment variables
source .env 2>/dev/null || export $(cat .env | grep -v '^#' | xargs)

# Build classpath and run in background
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
CP=$(cat cp.txt):target/classes
nohup java --enable-preview -cp "$CP" tech.yesboss.YesBossApplication > app.log 2>&1 &

# View logs
tail -f app.log
```

**Method 4: From IDE**
- Run the `main()` method in `src/main/java/tech/yesboss/YesBossApplication.java`
- **Important**: Add `--enable-preview` to VM options in IDE run configuration

### Setting up Feishu Webhook

#### 1. Create Feishu App

1. Go to [Feishu Open Platform](https://open.feishu.cn/)
2. Create a new app and get:
   - App ID
   - App Secret
   - Encrypt Key
   - Verification Token

#### 2. Configure Webhook in Feishu

In your Feishu app settings:

1. Go to **Event Subscriptions**
2. Add Request URL:
   ```
   https://your-domain.com/webhook/feishu
   ```
   For local testing, use ngrok:
   ```bash
   ngrok http 8080
   ```
3. Subscribe to events:
   - `im.message.receive_v1` - Receive messages
   - (Optional) Group chat events

#### 3. Enable Bot Permissions

In Feishu app permissions, enable:
- `im:message` - Send and receive messages
- `im:message:group_at_msg` - Group @ mentions
- `im:chat` - Access chat information

#### 4. Add Bot to Group

1. In Feishu, create a group chat
2. Add your app bot to the group
3. Grant necessary permissions

### Using the Application

#### Start the Application

When the application starts successfully, you should see:

```
========================================
YesBoss started successfully!
========================================
Application Status: READY
Accepting webhook traffic: YES
========================================
Webhook Endpoints:
Feishu:  http://0.0.0.0:8080/webhook/feishu
Feishu Callback: http://0.0.0.0:8080/webhook/feishu/callback
========================================
All systems operational!
========================================
```

#### Check Health Status

```bash
curl http://localhost:8080/health
curl http://localhost:8080/ready
curl http://localhost:8080/metrics
```

#### Send a Task

In your Feishu group chat:

```
@YesBoss 分析当前项目的代码结构并生成报告
```

The bot will:
1. Acknowledge the request with a progress bar
2. Use Master Agent to clarify requirements
3. Create a plan with Worker Agents
4. Execute tools to analyze code
5. Generate and send a summary card

### Development

#### Project Structure

```
YesBoss/
├── src/main/java/tech/yesboss/
│   ├── YesBossApplication.java    # Main entry point
│   ├── ApplicationContext.java     # Component lifecycle
│   ├── config/                     # Configuration management
│   ├── gateway/                    # Webhook & IM integration
│   ├── llm/                        # LLM routing & adapters
│   ├── state/                      # Task state machine
│   ├── runner/                     # Master/Worker orchestration
│   ├── context/                    # Context management
│   ├── tool/                       # Tool chain & sandbox
│   ├── persistence/                # Database layer
│   └── health/                     # Health checks
├── src/main/resources/
│   └── application.yml             # Configuration file
└── src/test/                       # Test suites
```

#### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=WebhookControllerTest

# Run with coverage
mvn test jacoco:report
```

#### Debug Mode

Enable debug logging:

```bash
export LOG_LEVEL=DEBUG
mvn exec:java -Dexec.mainClass="tech.yesboss.YesBossApplication"
```

Or in `application.yml`:

```yaml
logging:
  level: DEBUG
```

### Troubleshooting

#### Port Already in Use

```bash
# Change port in application.yml
app:
  server:
    port: 8081

# Or set environment variable
export SERVER_PORT=8081
```

#### Database Connection Error

```bash
# Ensure data directory exists
mkdir -p data

# Check SQLite is available
java -jar target/yesboss-1.0.0-SNAPSHOT.jar
```

#### Feishu Webhook Timeout

- Ensure your server is publicly accessible
- Use ngrok for local testing:
  ```bash
  ngrok http 8080
  ```
- Check firewall settings

#### LLM API Errors

- Verify API key is correct
- Check API quota/billing
- Ensure network connectivity to LLM provider

### Production Deployment

#### Docker Deployment

Create `Dockerfile`:

```dockerfile
FROM openjdk:17-slim
WORKDIR /app
COPY target/yesboss-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build and run:

```bash
docker build -t yesboss:latest .
docker run -d -p 8080:8080 \
  -e ZHIPU_API_KEY="${ZHIPU_API_KEY}" \
  -e FEISHU_APP_ID="${FEISHU_APP_ID}" \
  -e FEISHU_APP_SECRET="${FEISHU_APP_SECRET}" \
  yesboss:latest
```

#### Systemd Service

Create `/etc/systemd/system/yesboss.service`:

```ini
[Unit]
Description=YesBoss Multi-Agent Platform
After=network.target

[Service]
Type=simple
User=yesboss
WorkingDirectory=/opt/yesboss
ExecStart=/usr/bin/java -jar /opt/yesboss/yesboss-1.0.0-SNAPSHOT.jar
Restart=always
RestartSec=10
EnvironmentFile=/opt/yesboss/.env

[Install]
WantedBy=multi-user.target
```

Start service:

```bash
sudo systemctl daemon-reload
sudo systemctl enable yesboss
sudo systemctl start yesboss
sudo systemctl status yesboss
```

## License

[Specify your license here]

## Contributing

[Specify contribution guidelines here]
