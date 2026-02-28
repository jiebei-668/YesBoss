# 飞书卡片消息集成完成日志

## 任务概述
实现飞书（Lark/Feishu）卡片消息发送功能，使YesBoss多智能体平台能够向飞书群聊推送结构化卡片消息。

**完成时间**: 2026-02-28
**主要目标**: 飞书卡片消息格式调试和API对接

---

## 背景说明

在完成飞书webhook事件订阅和加密解密功能后（见 `FEISHU_WEBHOOK_TASK_LOG.md`），下一个关键任务是实现向飞书群聊发送卡片消息的功能，以便与用户进行交互式沟通（如需求澄清、进度汇报、审批等）。

---

## 核心问题与解决过程

### 问题1: receive_id_type 参数位置错误

**错误现象:**
```
HTTP 400: {"code":99992402,"msg":"field validation failed",
"field_violations":[{"field":"receive_id_type","description":"receive_id_type is required"}]}
```

**根本原因:**
`receive_id_type` 被放在请求体中，但飞书API要求它必须是 **URL查询参数**。

**错误格式:**
```json
POST https://open.feishu.cn/open-apis/im/v1/messages
{
  "receive_id_type": "chat_id",  // ❌ 错误：不应该在请求体中
  "receive_id": "oc_xxx",
  "msg_type": "interactive",
  ...
}
```

**正确格式:**
```http
POST https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id
{
  "receive_id": "oc_xxx",  // ✅ 正确
  "msg_type": "interactive",
  ...
}
```

**修复代码:**
`FeishuApiClient.java` - Line 270-276
```java
// Build URL with receive_id_type as query parameter
String messageUrl = MESSAGE_URL + "?receive_id_type=" + receiveIdType;

HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(messageUrl))
        .header("Authorization", "Bearer " + accessToken)
        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
        .build();
```

**结果**: ✅ 解决

---

### 问题2: content 字段格式错误（JSON对象 vs JSON字符串）

**错误现象:**
```
HTTP 400: {"code":230099,"msg":"Failed to create card content",
"ext=ErrCode: 200621; ErrMsg: parse card json err"}
```

**根本原因:**
`content` 字段必须是 **JSON字符串**，而不是JSON对象。代码使用了 `payload.set("content", parseContent(...))` 导致content被序列化为嵌套对象。

**错误代码:**
```java
ObjectNode payload = objectMapper.createObjectNode();
payload.set("content", parseContent(messageType, content));
// ❌ set() 会将JsonNode作为对象，而不是字符串
```

**修复代码:**
`FeishuApiClient.java` - Line 258-269
```java
// Build request payload
ObjectNode payload = objectMapper.createObjectNode();
payload.put("receive_id", receiveId);
payload.put("msg_type", messageType);

// For interactive messages, content must be a JSON string
// For text messages, content must be a JSON object {"text":"..."}
if ("interactive".equals(messageType)) {
    payload.put("content", content);  // ✅ Set as JSON string
} else {
    payload.set("content", parseContent(messageType, content));  // Set as JsonNode
}
```

**结果**: ✅ 解决

---

### 问题3: 卡片JSON结构嵌套错误

**错误现象:**
```
HTTP 400: parse card json err, please check whether the card json is correct
```

**尝试过的错误格式:**

**尝试1: 包裹在card对象中**
```json
{
  "content": "{\"card\":{\"header\":{...},\"elements\":[{...}]}}"
}
```
结果: ❌ 仍然报错

**尝试2: 添加schema字段**
```json
{
  "content": "{\"card\":{\"schema\":\"2.0\",\"header\":{...},\"elements\":[{...}]}}"
}
```
结果: ❌ 仍然报错

**尝试3: 移除schema，添加config**
```json
{
  "content": "{\"card\":{\"config\":{\"wide_screen_mode\":true},\"header\":{...},\"elements\":[{...}]}}"
}
```
结果: ❌ 仍然报错

**正确的解决方案:**

**直接使用header和elements，不嵌套在card对象中！**

**成功格式:**
```json
{
  "receive_id": "oc_xxx",
  "msg_type": "interactive",
  "content": "{\"header\":{\"title\":{\"tag\":\"plain_text\",\"content\":\"标题\"}},\"elements\":[{\"tag\":\"div\",\"text\":{\"tag\":\"plain_text\",\"content\":\"内容\"}}]}"
}
```

**修复代码:**
`UICardRendererImpl.java` - renderSummaryCard方法
```java
// ✅ 正确：直接创建header和elements，不嵌套在card中
ObjectNode feishuCard = objectMapper.createObjectNode();

// 创建header
ObjectNode header = objectMapper.createObjectNode();
ObjectNode title = objectMapper.createObjectNode();
title.put("tag", "plain_text");
title.put("content", statusLabel);
header.set("title", title);
feishuCard.set("header", header);

// 创建elements数组
ArrayNode elements = objectMapper.createArrayNode();
ObjectNode divElement = objectMapper.createObjectNode();
divElement.put("tag", "div");
ObjectNode textObj = objectMapper.createObjectNode();
textObj.put("tag", "plain_text");
textObj.put("content", summaryText);
divElement.set("text", textObj);
elements.add(divElement);

feishuCard.set("elements", elements);
```

**结果**: ✅ 成功！

**验证日志:**
```
20:28:27.184 [pool-3-thread-1] INFO tech.yesboss.gateway.im.FeishuApiClient -- sendMessage succeeded with status 200
20:28:27.184 [pool-3-thread-1] INFO tech.yesboss.gateway.im.FeishuApiClient -- Message sent successfully, message_id: om_x100b5519b86034a0c2a9abea290306e
20:28:27.184 [pool-3-thread-1] INFO tech.yesboss.gateway.im.impl.IMMessagePusherImpl -- Card message sent successfully to Feishu chat_id: oc_1ff91ac69ee14432a6de8d56491e421f
```

**飞书群显示结果:**
```
任务失败
Question: Please provide more details about your requirement.
```

---

## 技术总结

### 飞书卡片消息API正确格式

**HTTP请求格式:**
```http
POST https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id
Authorization: Bearer {tenant_access_token}
Content-Type: application/json; charset=UTF-8

{
  "receive_id": "oc_xxxxxxxxxxxxxxxxxxx",
  "msg_type": "interactive",
  "content": "{\"header\":{...},\"elements\":[{...}]}"
}
```

**关键要点:**

1. **receive_id_type**: URL查询参数，不是请求体字段
2. **msg_type**: "interactive" 表示卡片消息
3. **content**: 必须是JSON字符串，包含header和elements

**卡片结构（content字符串内的JSON）:**
```json
{
  "header": {
    "title": {
      "tag": "plain_text",
      "content": "卡片标题"
    }
  },
  "elements": [
    {
      "tag": "div",
      "text": {
        "tag": "plain_text",
        "content": "卡片内容"
      }
    },
    {
      "tag": "hr"
    },
    {
      "tag": "div",
      "text": {
        "tag": "plain_text",
        "content": "更多内容"
      }
    }
  ]
}
```

---

## 修改的文件清单

### 1. `src/main/java/tech/yesboss/gateway/im/FeishuApiClient.java`
**修改内容:**
- Line 258-269: 移除receive_id_type从请求体
- Line 270-276: 将receive_id_type作为URL参数添加
- Line 258-269: 区分interactive和text消息的content处理方式

### 2. `src/main/java/tech/yesboss/gateway/ui/impl/UICardRendererImpl.java`
**修改内容:**
- renderSummaryCard(): 重构卡片结构为直接header+elements格式
- renderProgressBar(): 重构卡片结构为直接header+elements格式
- renderSuspensionCard(): 重构卡片结构为直接header+elements格式
- 所有方法移除了schema和config字段
- 使用plain_text标签代替lark_md（简化）

### 3. `src/main/java/tech/yesboss/runner/impl/MasterRunnerImpl.java`
**修改内容:**
- Line 477-483: 提取feishu_card_json字段
- Line 529-538: 提取feishu_card_json字段
- Line 554-558: 提取feishu_card_json字段

---

## 已知问题（待修复）

### ⚠️ 问题: 用户消息循环

**现象:**
- 系统发送澄清问题卡片
- 用户在飞书群里回复
- 系统再次发送澄清问题卡片（无限循环）

**根本原因分析:**

1. **消息内容没有被提取**
   - WebhookControllerImpl只提取了事件类型、群组ID、用户ID
   - **没有提取用户的消息文本内容**
   - ImWebhookEvent也没有消息内容字段

2. **用户消息没有被保存到context**
   - 没有代码将用户消息添加到GlobalStreamManager
   - 导致context永远是空的

3. **每次都重新启动MasterRunner**
   - WebhookEventExecutorImpl每次都调用`masterRunner.run(sessionId)`
   - 即使session已存在，也会重新启动新的MasterRunner实例
   - clarifyRequirements()检查context.isEmpty()永远为true

**修复计划:**

1. **WebhookControllerImpl** - 提取飞书消息文本
   - 从decrypted body中解析 `event.message.content.text`
   - 将消息内容添加到ImWebhookEvent

2. **WebhookEventExecutorImpl** - 保存用户消息到context
   - 提取消息文本
   - 调用 `globalStreamManager.appendUserMessage(sessionId, message)`

3. **MasterRunnerImpl/WebhookEventExecutorImpl** - 智能判断
   - 检查session状态
   - 如果是新session → 启动MasterRunner
   - 如果是已有session且在等待回复 → 继续执行（不是重新启动）

---

## 参考资料

- [飞书开放平台 - 发送消息API](https://open.feishu.cn/document/server-docs/im-v1/message/create)
- [飞书卡片概述](https://open.feishu.cn/document/feishu-cards/feishu-card-overview?lang=zh-CN)
- [飞书卡片常见问题](https://open.feishu.cn/document/common-capabilities/message-card/message-card?lang=zh-CN)
- [飞书卡片JSON示例](https://m.blog.csdn.net/weixin_40186428/article/details/141372016)

---

## 测试验证

**测试环境:**
- 飞书群聊ID: oc_1ff91ac69ee14432a6de8d56491e421f
- 应用端口: 6000
- Webhook地址: http://1.12.236.246:6000/webhook/feishu

**测试结果:**
✅ URL验证成功
✅ 事件解密成功
✅ Session创建成功
✅ 卡片发送成功
✅ 飞书群显示正确

**实际显示效果:**
```
任务失败
Question: Please provide more details about your requirement.
```

---

**记录人**: Claude (AI Assistant)
**记录日期**: 2026-02-28
**相关任务**: 飞书Webhook集成、卡片消息发送
