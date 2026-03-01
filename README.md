# YesBoss - Multi-Agent Task Orchestration Platform

一个结果导向、极简的多Agent系统，通过Master-Worker分层架构和深度IM集成，自动化处理复杂的工程和业务任务。

## 快速开始

### 1. 环境要求

- **Java**: JDK 17+ (需要启用preview特性)
- **Maven**: 3.6+
- **飞书应用**: 需要创建飞书应用并获取相关凭证
- **LLM API密钥**: 智谱AI/Claude/Gemini/GPT至少一个

### 2. 配置 .env 文件

在项目根目录创建 `.env` 文件：

```bash
# =============================================================================
# LLM 配置（至少配置一个）
# =============================================================================

# 智谱AI（推荐，已测试）
ZHIPU_API_KEY=your_zhipu_api_key_here

# 如果使用其他LLM提供商，取消注释相应的配置：
# ANTHROPIC_API_KEY=your_anthropic_api_key_here
# GEMINI_API_KEY=your_gemini_api_key_here
# OPENAI_API_KEY=your_openai_api_key_here

# =============================================================================
# 飞书配置（必需）
# =============================================================================

# 飞书应用凭证（从飞书开放平台获取）
FEISHU_APP_ID=cli_aaaaaaaabbbbbbbb
FEISHU_APP_SECRET=your_feishu_app_secret_here

# 飞书加密密钥（用于webhook签名验证）
FEISHU_ENCRYPT_KEY=your_feishu_encrypt_key_here

# 飞书验证令牌（可选，用于URL验证）
FEISHU_VERIFICATION_TOKEN=your_verification_token_here

# Webhook配置（可选，有默认值）
FEISHU_WEBHOOK_PATH=/feishu/webhook
FEISHU_WEBHOOK_PORT=6000

# =============================================================================
# 服务器配置（可选）
# =============================================================================

# 服务监听端口（默认6000）
SERVER_PORT=6000

# =============================================================================
# 数据库配置（可选）
# =============================================================================

# SQLite数据库文件路径（默认 data/yesboss.db）
SQLITE_PATH=data/yesboss.db

# =============================================================================
# 日志配置（可选）
# =============================================================================

# 日志级别（默认 INFO）
LOG_LEVEL=INFO

# 日志文件路径（默认 logs/yesboss.log）
LOG_FILE_PATH=logs/yesboss.log
```

#### 如何获取飞书应用凭证？

1. **创建飞书应用**
   - 访问 [飞书开放平台](https://open.feishu.cn/)
   - 创建企业自建应用
   - 记录 `App ID` 和 `App Secret`

2. **配置加密密钥**
   - 在应用管理 → 功能权限 → 事件订阅
   - 启用"加密"并设置 `Encrypt Key`
   - 记录这个密钥

3. **获取权限**
   - 在应用权限管理中启用：
     - `im:message` - 收发消息
     - `im:message:group_at_msg` - 群组@消息
     - `im:chat` - 获取群聊信息

#### 如何获取LLM API密钥？

**智谱AI（推荐）**：
1. 访问 [智谱AI开放平台](https://open.bigmodel.cn/)
2. 注册/登录账号
3. 进入API密钥管理
4. 创建新的API密钥

**其他提供商**：
- Anthropic Claude: https://console.anthropic.com/
- Google Gemini: https://makersuite.google.com/
- OpenAI GPT: https://platform.openai.com/

### 3. 构建项目

```bash
# 克隆项目
git clone <repository-url>
cd YesBoss

# 编译项目
mvn clean compile
```

### 4. 启动应用

```bash
# 前台启动（开发调试）
mvn exec:java

# 后台启动（生产环境）
nohup mvn exec:java > app.log 2>&1 &

# 查看日志
tail -f app.log
```

启动成功后，你应该看到：

```
========================================
YesBoss started successfully!
========================================
Application Status: READY
Accepting webhook traffic: YES
========================================
Webhook Endpoints:
Feishu:  http://0.0.0.0:6000/webhook/feishu
Feishu Callback: http://0.0.0.0:6000/webhook/feishu/callback
========================================
All systems operational!
========================================
```

### 5. 配置飞书Webhook

#### 本地开发（使用ngrok）

1. 安装ngrok: https://ngrok.com/download
2. 启动ngrok隧道：
   ```bash
   ngrok http 6000
   ```
3. 复制ngrok提供的HTTPS地址
4. 在飞书开放平台配置事件订阅：
   - **请求URL**: `https://xxxxx.ngrok.io/webhook/feishu`
   - **订阅事件**: `im.message.receive_v1`

#### 生产环境

1. 确保服务器有公网IP
2. 配置域名和HTTPS证书（推荐使用Nginx）
3. 在飞书开放平台配置事件订阅：
   - **请求URL**: `https://your-domain.com/webhook/feishu`
   - **订阅事件**: `im.message.receive_v1`

### 6. 添加Bot到飞书群

1. 在飞书中创建一个群聊
2. 群设置 → 群机器人 → 添加机器人
3. 选择你创建的应用
4. 授予必要的权限（发送消息、@消息等）

## 程序使用指南

### 基本使用流程

在飞书群中直接@Bot并发送任务：

```
@YesBoss 你的任务描述
```

### 执行流程

```
1. 用户发送任务
   ↓
2. Master Agent 分析需求
   ↓
3. 如需澄清，Master会提问
   ↓
4. 环境探索（只读工具）
   ↓
5. 任务分解为子任务
   ↓
6. Workers 并行执行
   ├─ 正常执行：调用工具完成任务
   ├─ 高危操作：要求人工批准
   └─ 循环限制：最多20轮
   ↓
7. 生成最终总结报告
```

### 使用示例

#### 示例1：代码分析

```
@YesBoss 分析当前项目的代码结构，生成详细报告
```

#### 示例2：技术调研

```
@YesBoss 调研Web3技术的核心概念、应用场景和发展趋势
```

#### 示例3：文档生成

```
@YesBoss 根据src/main/java目录下的代码，生成API文档
```

#### 示例4：问题排查

```
@YesBoss 分析app.log中的错误，给出解决方案
```

### 查看执行进度

程序会实时在群聊中推送进度条：

```
任务执行进度
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
已完成 3/6 个子任务 (50%)
Progress: 3/6 tasks completed

正在执行：分析核心算法模块
```

### 查看最终报告

任务完成后，会推送总结卡片：

```
📋 任务完成报告

## 总体概述
所有 6 个子任务已成功完成！本次任务围绕"分析代码结构"展开...

## 各子任务执行详情

### 1. 分析项目结构
[详细报告...]

### 2. 分析核心模块
[详细报告...]
```

### 高危操作审批

当Worker尝试执行高危操作（如删除文件、格式化磁盘等）时：

1. 系统自动拦截操作
2. 推送审批卡片到群聊
3. 你点击"批准"或"拒绝"按钮
4. 系统根据你的决定继续或终止

## 配置详解

### 最小化配置（仅必需项）

```bash
# .env 文件
ZHIPU_API_KEY=your_api_key
FEISHU_APP_ID=cli_xxxxxxxxx
FEISHU_APP_SECRET=your_secret
FEISHU_ENCRYPT_KEY=your_encrypt_key
```

### 推荐配置（完整功能）

参考上面的"配置 .env 文件"部分。

### 高级配置（application.yml）

如果需要更细粒度的控制，可以编辑 `src/main/resources/application.yml`。

**注意**: 环境变量（.env）优先级高于application.yml。

#### 启用其他LLM提供商

```yaml
llm:
  anthropic:
    enabled: true
    apiKey: ${ANTHROPIC_API_KEY:}
    model:
      master: claude-sonnet-4-20250514
      worker: claude-haiku-4-20250514

  gemini:
    enabled: true
    apiKey: ${GEMINI_API_KEY:}
    model:
      master: gemini-2.0-flash-exp
      worker: gemini-1.5-flash
```

#### 调整任务执行参数

```yaml
app:
  task:
    defaultTimeoutMinutes: 60      # 任务默认超时时间
    maxConcurrentTasks: 10         # 最大并发任务数
  hitl:
    approvalTimeoutMinutes: 30     # 审批超时时间
    maxRetries: 3                  # 最大重试次数
```

#### 配置沙箱安全

```yaml
sandbox:
  enabled: true
  toolNameBlacklist:
    - format_disk
    - delete_all
    - wipe_system
  argumentBlacklist:
    - "rm\\s+-rf\\s+/"
    - "curl.*\\|\\s*bash"
  pathBlacklist:
    - /etc/passwd
    - ~/.ssh/
```

## 健康检查

```bash
# 检查应用状态
curl http://localhost:6000/health

# 检查就绪状态
curl http://localhost:6000/ready

# 查看指标
curl http://localhost:6000/metrics
```

## 故障排查

### 问题1：端口被占用

**症状**：
```
ERROR: Address already in use
```

**解决**：
```bash
# 查找占用端口的进程
fuser -k 6000/tcp

# 或修改.env中的端口
SERVER_PORT=6001
```

### 问题2：LLM API调用失败

**症状**：
```
ERROR: 401 Unauthorized
```

**解决**：
1. 检查 `.env` 中的API密钥是否正确
2. 确认API余额充足
3. 检查网络连接

### 问题3：飞书Webhook接收不到

**症状**：
```
在飞书群@Bot没有反应
```

**解决**：
1. 检查应用是否正常运行：`curl http://localhost:6000/health`
2. 确认飞书Webhook配置正确
3. 检查 `FEISHU_ENCRYPT_KEY` 是否匹配
4. 查看应用日志：`tail -f app.log`

### 问题4：签名验证失败

**症状**：
```
ERROR: Feishu signature verification failed
```

**解决**：
1. 确保 `.env` 中的 `FEISHU_ENCRYPT_KEY` 与飞书平台配置的完全一致
2. 检查密钥前后没有多余的空格
3. 重启应用使配置生效

### 问题5：数据库错误

**症状**：
```
ERROR: SQLite database file locked
```

**解决**：
```bash
# 停止应用
pkill -f YesBossApplication

# 删除数据库文件（会丢失历史数据，谨慎操作）
rm data/yesboss.db

# 重新启动应用
mvn exec:java
```

## 生产部署

### Docker部署

1. 构建镜像：
```bash
docker build -t yesboss:latest .
```

2. 运行容器：
```bash
docker run -d \
  --name yesboss \
  -p 6000:6000 \
  --env-file .env \
  -v $(pwd)/data:/app/data \
  -v $(pwd)/logs:/app/logs \
  yesboss:latest
```

### Systemd服务

1. 创建服务文件 `/etc/systemd/system/yesboss.service`：
```ini
[Unit]
Description=YesBoss Multi-Agent Platform
After=network.target

[Service]
Type=simple
User=your-username
WorkingDirectory=/opt/yesboss
ExecStart=/usr/bin/java -jar /opt/yesboss/yesboss.jar
Restart=always
RestartSec=10
EnvironmentFile=/opt/yesboss/.env

[Install]
WantedBy=multi-user.target
```

2. 启动服务：
```bash
sudo systemctl daemon-reload
sudo systemctl enable yesboss
sudo systemctl start yesboss
sudo systemctl status yesboss
```

## 项目架构

### 核心组件

```
┌─────────────────────────────────────────────────┐
│          飞书/Slack IM（用户交互层）             │
└───────────────┬─────────────────────────────────┘
                │
┌───────────────▼─────────────────────────────────┐
│       Webhook Controller（接入路由层）          │
│       SessionManager（会话管理）                │
└───────────────┬─────────────────────────────────┘
                │
┌───────────────▼─────────────────────────────────┐
│        Master Agent（业务编排层）              │
│   - 需求澄清                                    │
│   - 环境探索                                    │
│   - 任务分解                                    │
│   - 最终总结                                    │
└───────────────┬─────────────────────────────────┘
                │
        ┌───────┴───────┐
        │               │
┌───────▼────┐   ┌───▼─────────┐
│ Worker 1   │   │ Worker 2    │  ...
└───────┬────┘   └───┬─────────┘
        │             │
┌───────▼─────────────▼─────────────────────────┐
│     Tool Registry（统一工具链 + 沙箱）        │
│   - Bash命令                                  │
│   - 文件操作                                  │
│   - Web搜索                                  │
│   - MCP工具                                  │
└────────────────────────────────────────────────┘
```

### 技术栈

- **Java 17**: Records, Virtual Threads, Sealed Interfaces
- **SQLite**: 单线程写入 + 异步队列
- **LLM**: 智谱/Claude/Gemini/GPT多模型支持
- **IM**: 飞书/Slack深度集成

## 开发指南

### 项目结构

```
YesBoss/
├── docs/                      # 设计文档
├── src/main/java/tech/yesboss/
│   ├── YesBossApplication.java   # 主入口
│   ├── ApplicationContext.java    # 组件生命周期
│   ├── config/                  # 配置管理
│   ├── gateway/                 # Webhook & IM
│   ├── llm/                     # LLM路由
│   ├── runner/                  # Master/Worker编排
│   ├── context/                 # 上下文管理
│   ├── tool/                    # 工具链
│   ├── state/                   # 状态机
│   └── persistence/             # 数据持久化
├── src/main/resources/
│   └── application.yml          # 配置文件
└── .env                        # 环境变量（需创建）
```

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定测试
mvn test -Dtest=WebhookControllerTest

# 生成覆盖率报告
mvn test jacoco:report
```

### 调试模式

```bash
# 启用调试日志
export LOG_LEVEL=DEBUG
mvn exec:java

# 或在 .env 中配置
LOG_LEVEL=DEBUG
```

## 许可证

[指定您的许可证]

## 贡献指南

[指定贡献指南]

---

**文档版本**: 1.0
**更新日期**: 2026-03-01
**维护者**: YesBoss Team
