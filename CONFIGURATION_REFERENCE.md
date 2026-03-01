# YesBoss 配置项完整清单

## 配置文件说明

YesBoss 使用两个配置文件：
1. **application.yml** - 主配置文件（YAML格式）
2. **.env** - 环境变量文件（用于敏感信息）

配置优先级：环境变量 > application.yml > 默认值

---

## 必需配置项（系统运行必需）

### 1. LLM Provider Configuration（LLM提供商配置）

**当前使用：Zhipu GLM（智谱）**

```yaml
llm:
  zhipu:
    enabled: true                                    # 必需：启用Zhipu
    apiKey: ${ZHIPU_API_KEY:}                        # 必需：API密钥
    baseUrl: https://open.bigmodel.cn               # 可选：API地址
    model:
      master: glm-4-plus                            # 推荐：Master使用的模型
      worker: glm-4-flash                            # 推荐：Worker使用的模型
    maxTokens: 8192                                  # 可选：最大token数
    temperature: 0.7                                 # 可选：温度参数
    timeoutSeconds: 120                              # 可选：超时时间
```

**环境变量配置（.env）：**
```bash
# 必需
ZHIPU_API_KEY=your_api_key_here
```

**其他LLM提供商（可选）：**
- Anthropic Claude
- Google Gemini
- OpenAI GPT

### 2. Feishu IM Configuration（飞书配置）

```yaml
im:
  feishu:
    enabled: true                                    # 必需：启用Feishu
    appId: ${FEISHU_APP_ID:cli_a905a3bc37b85cee}  # 必需：飞书应用ID
    appSecret: ${FEISHU_APP_SECRET:}                # 必需：飞书应用密钥
    encryptKey: ${FEISHU_ENCRYPT_KEY:KOBEforever668!}  # 必需：加密密钥
    webhook:
      path: /feishu/webhook                          # 可选：webhook路径
      port: 6000                                     # 可选：webhook端口（推荐6000）
    push:
      baseUrl: https://open.feishu.cn               # 可选：API地址
      messageApi: /open-apis/im/v1/messages          # 可选：消息API路径
    timeoutSeconds: 30                               # 可选：超时时间
```

**环境变量配置（.env）：**
```bash
# 必需
FEISHU_APP_ID=cli_xxxxxxxx
FEISHU_APP_SECRET=your_app_secret_here
FEISHU_ENCRYPT_KEY=your_encrypt_key_here

# 可选
FEISHU_WEBHOOK_PATH=/feishu/webhook
FEISHU_WEBHOOK_PORT=6000
```

### 3. Server Configuration（服务器配置）

```yaml
app:
  server:
    host: 0.0.0.0                                   # 可选：监听地址
    port: 6000                                       # 推荐：服务端口
    contextPath: /                                   # 可选：上下文路径
```

**环境变量：**
```bash
SERVER_PORT=6000  # 可选，默认6000
```

### 4. Database Configuration（数据库配置）

```yaml
database:
  type: sqlite                                      # 固定值
  sqlite:
    path: data/yesboss.db                           # 可选：数据库文件路径
    journalMode: WAL                                # 推荐：WAL模式
    synchronous: NORMAL                             # 推荐：同步模式
    cacheSize: -2000                                # 可选：缓存大小
    tempStore: MEMORY                               # 可选：临时存储
```

**环境变量：**
```bash
SQLITE_PATH=data/yesboss.db  # 可选
```

---

## 可选配置项

### 5. Sandbox Security Configuration（沙箱安全配置）

```yaml
sandbox:
  enabled: true                                     # 推荐：启用沙箱
  toolNameBlacklist:                                # 可选：工具名称黑名单
    - format_disk
    - delete_all
    - wipe_system
  argumentBlacklist:                                # 可选：参数黑名单（正则）
    - "rm\\s+-rf\\s+/"
    - "curl.*\\|\\s*bash"
  pathBlacklist:                                    # 可选：路径黑名单
    - /etc/passwd
    - ~/.ssh/
    - /root/
```

**环境变量：**
```bash
SANDBOX_ENABLED=true  # 可选，默认true
```

### 6. Logging Configuration（日志配置）

```yaml
logging:
  level: INFO                                       # 可选：日志级别
  format: text                                      # 可选：日志格式
  console:
    enabled: true                                   # 可选：控制台输出
    colorized: true                                 # 可选：彩色输出
  file:
    enabled: true                                   # 可选：文件输出
    path: logs/yesboss.log                          # 可选：日志文件路径
    maxSize: 100MB                                  # 可选：单个文件大小
    maxHistory: 30                                  # 可选：保留历史数量
    totalCapacity: 1GB                              # 可选：总容量限制
  components:
    llm: DEBUG                                      # 可选：LLM组件日志级别
    persistence: INFO                               # 可选：持久化组件日志级别
    scheduler: INFO                                 # 可选：调度器组件日志级别
    sandbox: WARN                                   # 可选：沙箱组件日志级别
```

**环境变量：**
```bash
LOG_LEVEL=INFO                           # 可选
LOG_FILE_PATH=logs/yesboss.log            # 可选
LOG_CONSOLE_ENABLED=true                   # 可选
```

### 7. Scheduler Configuration（调度器配置）

```yaml
scheduler:
  circuitBreaker:
    maxLoopCount: 20                                # 可选：最大循环次数
    checkIntervalMs: 100                            # 可选：检查间隔
  condensation:
    tokenThreshold: 120000                          # 可选：token压缩阈值
    summarizationIntervalMs: 60000                  # 可选：摘要间隔
    maxMessagesToCondense: 100                      # 可选：最大压缩消息数
  threadPool:
    virtualThreadsEnabled: true                     # 推荐：启用虚拟线程
    platformThreadPoolSize: 16                      # 可选：平台线程池大小
    queueCapacity: 1000                             # 可选：队列容量
    keepAliveSeconds: 60                            # 可选：保活时间
```

### 8. Task Configuration（任务配置）

```yaml
app:
  task:
    defaultTimeoutMinutes: 60                       # 可选：默认超时时间
    maxConcurrentTasks: 10                          # 可选：最大并发任务数
  hitl:
    approvalTimeoutMinutes: 30                      # 可选：审批超时时间
    maxRetries: 3                                   # 可选：最大重试次数
```

---

## 当前实际配置（根据代码检查）

### application.yml 中的关键配置

```yaml
llm:
  zhipu:
    enabled: true                                    # ✅ 已启用
    apiKey: ${ZHIPU_API_KEY:}                        # ⚠️ 需要在.env中配置

im:
  feishu:
    enabled: true                                    # ✅ 已启用
    appId: cli_a905a3bc37b85cee                     # ✅ 已配置
    appSecret: ${FEISHU_APP_SECRET:}                # ⚠️ 需要在.env中配置
    encryptKey: ${FEISHU_ENCRYPT_KEY:KOBEforever668!}  # ⚠️ 需要在.env中配置
    webhook:
      port: 8080                                    # ⚠️ 应改为6000
```

### .env 文件当前配置

```bash
# ✅ 已配置
ZHIPU_API_KEY=175f4fa91720480b9768845f33357803.Sk2pdQKVWQYpSm6v
FEISHU_WEBHOOK_PATH=/feishu/webhook
FEISHU_WEBHOOK_PORT=6000
FEISHU_APP_SECRET=EeSLGM6u01phyKw161Pe3bAOuD5sumNV
FEISHU_ENCRYPT_KEY=KOBEforever668!
```

---

## 配置问题与建议

### ⚠️ 发现的问题

1. **端口配置不一致**
   - `application.yml` 中 `FEISHU_WEBHOOK_PORT` 默认值为 `8080`
   - `.env` 中设置为 `6000`
   - `app.server.port` 默认值为 `6000`

   **建议**：统一使用 `6000` 端口

2. **ZHIPU_API_KEY 硬编码风险**
   - API密钥已暴露在 `.env` 文件中
   - 建议定期更换密钥

3. **Encrypt Key 使用默认值**
   - `FEISHU_ENCRYPT_KEY` 使用了简单的默认值
   - 建议更换为更强的密钥

### ✅ 配置优化建议

#### 1. 更新 application.yml

```yaml
# 修改前
im:
  feishu:
    webhook:
      port: ${FEISHU_WEBHOOK_PORT:8080}  # ❌

# 修改后
im:
  feishu:
    webhook:
      port: ${FEISHU_WEBHOOK_PORT:6000}  # ✅
```

#### 2. 增强安全性

```bash
# 生成更强的加密密钥
FEISHU_ENCRYPT_KEY=$(openssl rand -base64 32)

# 生成验证令牌
FEISHU_VERIFICATION_TOKEN=$(openssl rand -hex 16)
```

#### 3. 添加环境说明

```bash
# 开发环境
APP_ENV=development
LOG_LEVEL=DEBUG

# 生产环境
# APP_ENV=production
# LOG_LEVEL=INFO
```

---

## 配置检查清单

### 启动前必查项

- [ ] `ZHIPU_API_KEY` 已配置且有效
- [ ] `FEISHU_APP_ID` 已配置
- [ ] `FEISHU_APP_SECRET` 已配置
- [ ] `FEISHU_ENCRYPT_KEY` 已配置
- [ ] 端口配置统一（推荐6000）
- [ ] 数据库路径可写

### 可选优化项

- [ ] 配置日志轮转策略
- [ ] 启用Sandbox沙箱（默认启用）
- [ ] 配置任务超时时间
- [ ] 配置最大并发任务数

---

## 配置文件模板

### 完整 .env 文件模板

```bash
# =============================================================================
# LLM Configuration
# =============================================================================
ZHIPU_API_KEY=your_zhipu_api_key_here

# =============================================================================
# Feishu Configuration
# =============================================================================
FEISHU_APP_ID=cli_your_app_id_here
FEISHU_APP_SECRET=your_app_secret_here
FEISHU_ENCRYPT_KEY=your_encrypt_key_here
FEISHU_VERIFICATION_TOKEN=your_verification_token_here

# Webhook Configuration
FEISHU_WEBHOOK_PATH=/feishu/webhook
FEISHU_WEBHOOK_PORT=6000

# =============================================================================
# Server Configuration
# =============================================================================
SERVER_PORT=6000
SERVER_HOST=0.0.0.0

# =============================================================================
# Database Configuration
# =============================================================================
SQLITE_PATH=data/yesboss.db

# =============================================================================
# Logging Configuration
# =============================================================================
LOG_LEVEL=INFO
LOG_FILE_PATH=logs/yesboss.log
LOG_CONSOLE_ENABLED=true
LOG_FILE_ENABLED=true

# =============================================================================
# Application Environment
# =============================================================================
APP_ENV=development
```

---

## 配置验证

### 检查配置是否正确

```bash
# 1. 检查 .env 文件是否存在
ls -la .env

# 2. 检查 application.yml 是否存在
ls -la src/main/resources/application.yml

# 3. 验证配置加载
mvn exec:java -Dexec.mainClass="tech.yesboss.YesBossApplication"

# 4. 检查日志中的配置信息
grep "Initializing" app.log
```

### 常见配置问题

1. **端口被占用**
   ```
   ERROR: Address already in use
   ```
   解决：`fuser -k 6000/tcp`

2. **API密钥无效**
   ```
   ERROR: 401 Unauthorized
   ```
   解决：检查 `.env` 中的 `ZHIPU_API_KEY`

3. **Feishu签名验证失败**
   ```
   ERROR: Signature verification failed
   ```
   解决：检查 `FEISHU_ENCRYPT_KEY` 和 `FEISHU_APP_SECRET`

---

## 总结

### 最小化配置（必须）

```bash
# .env
ZHIPU_API_KEY=your_key_here
FEISHU_APP_SECRET=your_secret_here
FEISHU_ENCRYPT_KEY=your_encrypt_key
```

### 推荐配置（完整）

参考上面的"完整 .env 文件模板"

### 配置文件位置

- **主配置**: `src/main/resources/application.yml`
- **环境变量**: `.env`（项目根目录）
- **数据库**: `data/yesboss.db`（运行时生成）
- **日志文件**: `logs/yesboss.log`（运行时生成）

---

**文档版本**: 1.0
**更新日期**: 2026-03-01
**维护者**: YesBoss Team
