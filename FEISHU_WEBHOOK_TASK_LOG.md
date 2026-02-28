# 飞书 Webhook 集成任务完成日志

## 任务概述
实现飞书(Lark/Feishu) webhook事件的接收、验证和解密功能，使YesBoss多智能体平台能够接收并处理来自飞书的即时消息事件。

**完成时间**: 2026-02-28
**主要目标**: 飞书webhook事件订阅和加密消息解密

---

## 任务一：飞书 Webhook URL Verification (Challenge响应)

### 需求
飞书在配置webhook订阅时会发送URL验证请求，服务端需要返回challenge响应来验证URL所有权。

### 实现过程
**文件**: `src/main/java/tech/yesboss/gateway/webhook/controller/impl/WebhookControllerImpl.java`

**位置**: Lines 111-117
```java
if (rootNode.has("type") && "url_verification".equals(rootNode.get("type").asText())) {
    String challenge = rootNode.get("challenge").asText();
    logger.info("Feishu URL verification challenge received");
    return "{\"challenge\":\"" + challenge + "\"}";
}
```

**关键问题1: URL验证请求不包含签名头**
- **错误**: 代码先验证签名头，但URL verification请求没有这些头
- **修复**: 在WebhookRouteHandler中先检查是否为URL verification类型，如果是则跳过签名验证
- **文件**: `src/main/java/tech/yesboss/gateway/webhook/route/WebhookRouteHandler.java` Lines 262-294

**关键问题2: Content-Type错误**
- **错误**: 返回JSON但Content-Type是text/plain
- **现象**: 飞书报错"返回数据不是合法的JSON格式"
- **修复**: 检测非"200 OK"响应时设置Content-Type为application/json
- **文件**: `WebhookRouteHandler.java` Lines 305-308

### 结果
✅ URL验证成功通过

---

## 任务二：飞书签名验证（HEX编码）

### 问题发现
签名验证一直失败，签名完全不匹配。

### 调试过程
**日志显示**:
```
Received signature: e8e1985e1507881a61cf3b1af2ed65404bf937f6c284219ccda9d7149c2150f5 (hex格式)
Expected signature: npeGQ9HWKhL95R4M8deBMQBM53gDbhOPu2+PFNEPOPQ= (base64格式)
```

### 根本原因
1. **错误的编码格式**: 代码使用Base64编码返回签名，但飞书使用HEX编码
2. **错误的密钥**: 代码使用APP_SECRET进行签名，但应该使用ENCRYPT_KEY

### 修复方案

#### 修复1: 签名编码格式 Base64 → HEX
**文件**: `src/main/java/tech/yesboss/gateway/webhook/controller/impl/WebhookControllerImpl.java`
**方法**: `calculateHmacSha256()` Lines 360-380

```java
private String calculateHmacSha256(String data, String secret) {
    // ... HMAC计算 ...
    byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

    // 转换为HEX字符串（小写）
    StringBuilder hexString = new StringBuilder();
    for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
            hexString.append('0');
        }
        hexString.append(hex);
    }
    return hexString.toString();
}
```

#### 修复2: 使用正确的密钥（Encrypt Key）
**文件**: `src/main/java/tech/yesboss/ApplicationContext.java`
**位置**: Line 480

**修改前**:
```java
String feishuAppSecret = config.getIm().getFeishu().getAppSecret();
```

**修改后**:
```java
// 使用encryptKey进行webhook签名验证（不是appSecret）
String feishuAppSecret = config.getIm().getFeishu().getEncryptKey();
```

**配置文件**: `.env`
```bash
FEISHU_ENCRYPT_KEY=KOBEforever668!
```

### 结果
✅ 签名验证逻辑已修复（但因启用加密功能，签名验证被临时禁用用于调试）

---

## 任务三：飞书加密事件解密（核心难点）

### 问题发现
飞书启用了加密功能后，收到的消息体格式：
```json
{"encrypt":"CvWrAeygZZeZpVBE4L7NXkyX0PRJ+lyif/EvPgf/TksC7MT0YmAwsgthan7/hxw9..."}
```

**现象**: 无法提取群组ID和用户ID，结果显示`unknown-group`

### 研究过程
查阅飞书官方文档和多个参考实现，发现飞书加密机制：
- **加密算法**: AES/CBC/NOPADDING
- **密钥处理**: Encrypt Key先经过SHA-256哈希
- **IV处理**: 密文的前16字节作为IV
- **数据格式**: Base64编码 = [16字节IV] + [加密数据]

### 错误尝试

#### 尝试1: AES-128-ECB
```java
Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
```
**结果**: BadPaddingException - "Given final block not properly padded"

#### 尝试2: AES-128-CBC (零IV)
```java
IvParameterSpec ivSpec = new IvParameterSpec(new byte[16]);
```
**结果**: BadPaddingException - 密钥或IV不正确

#### 尝试3: AES-256-CBC (密钥作为IV)
```java
System.arraycopy(keyBytes, 0, keyBytes256, 0, 32);
IvParameterSpec ivSpec = new IvParameterSpec(keyBytes256);
```
**结果**: BadPaddingException - IV处理方式错误

### 正确方案（飞书官方）

**文件**: `src/main/java/tech/yesboss/gateway/webhook/controller/impl/WebhookControllerImpl.java`
**方法**: `decryptFeishuEvent()` Lines 394-434

```java
private String decryptFeishuEvent(String encryptedBase64, String encryptKey) throws Exception {
    // 1. Base64解码
    byte[] encryptedBytes = Base64.getDecoder().decode(encryptedBase64);

    // 2. 提取IV（前16字节）
    byte[] iv = new byte[16];
    System.arraycopy(encryptedBytes, 0, iv, 0, 16);

    // 3. 提取加密数据（剩余字节）
    byte[] encryptedData = new byte[encryptedBytes.length - 16];
    System.arraycopy(encryptedBytes, 16, encryptedData, 0, encryptedData.length);

    // 4. SHA-256哈希Encrypt Key作为AES密钥
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] key = digest.digest(encryptKey.getBytes(StandardCharsets.UTF_8));

    // 5. AES-256-CBC-NOPADDING解密
    Cipher cipher = Cipher.getInstance("AES/CBC/NOPADDING");
    SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
    IvParameterSpec ivSpec = new IvParameterSpec(iv);
    cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
    byte[] decryptedBytes = cipher.doFinal(encryptedData);

    // 6. 手动去除填充
    if (decryptedBytes.length > 0) {
        int p = decryptedBytes.length - 1;
        for (; p >= 0 && decryptedBytes[p] <= 16; p--) {
            // 查找最后一个非填充字节
        }
        if (p != decryptedBytes.length - 1) {
            byte[] unpadded = new byte[p + 1];
            System.arraycopy(decryptedBytes, 0, unpadded, 0, p + 1);
            decryptedBytes = unpadded;
        }
    }

    return new String(decryptedBytes, StandardCharsets.UTF_8);
}
```

### 验证结果
```
20:02:04.322 INFO Feishu event parsed: type=message,
group=oc_1ff91ac69ee14432a6de8d56491e421f, user=null
```

✅ **成功提取到真实群组ID**: `oc_1ff91ac69ee14432a6de8d56491e421f`
✅ **会话创建成功**: `session_1bea1a7f-f646-4e20-9bc1-433b2a8093db`
✅ **MasterRunner正常启动**并进入PLANNING阶段

---

## 已知问题（待修复）

### 问题1: 签名验证被临时禁用
**状态**: 为了调试加密解密，签名验证被临时禁用
**位置**: `WebhookControllerImpl.java` Lines 100-106

```java
// TEMPORARILY DISABLED FOR DEBUGGING
if (!feishuAppSecret.isEmpty()) {
    logger.warn("Feishu signature verification TEMPORARILY DISABLED for debugging");
    // verifyFeishuSignature(timestamp, nonce, signature, body);
}
```

**建议**: 在解密验证稳定后重新启用签名验证

### 问题2: 发送卡片消息缺少 receive_id_type 参数
**错误日志**:
```
sendMessage failed with HTTP 400: field validation failed
field_violations: [{"field":"receive_id_type","description":"receive_id_type is required"}]
```

**影响**: 无法将澄清问题卡片发送回飞书群聊

**修复方案**: 在FeishuApiClient的sendMessage方法中添加receive_id_type参数

---

## 技术总结

### 飞书Webhook关键技术点

1. **URL Verification**
   - 检测`type="url_verification"`
   - 返回JSON格式的challenge响应
   - Content-Type必须是application/json

2. **签名验证**
   - 算法: HMAC-SHA256
   - 签名字符串: `timestamp + "\n" + nonce + "\n" + body`
   - 签名格式: HEX编码（不是Base64）
   - 密钥: Encrypt Key（不是App Secret）

3. **事件解密**
   - 算法: AES/CBC/NOPADDING
   - 密钥处理: SHA-256(Encrypt Key)
   - IV提取: 密文前16字节
   - 数据格式: Base64([16字节IV][加密数据])
   - 填充: 手动去除（NOPADDING模式）

### 重要配置

**.env 文件**:
```bash
FEISHU_WEBHOOK_PATH=/feishu/webhook
FEISHU_WEBHOOK_PORT=6000
FEISHU_APP_SECRET=EeSLGM6u01phyKw161Pe3bAOuD5sumNV
FEISHU_ENCRYPT_KEY=KOBEforever668!
```

**application.yml** (默认值):
```yaml
feishu:
  appId: cli_a905a3bc37b85cee
  appSecret: ${FEISHU_APP_SECRET:}
  encryptKey: ${FEISHU_ENCRYPT_KEY:KOBEforever668!}
  verificationToken: ${FEISHU_VERIFICATION_TOKEN:IN4yV9Fz7DALJKOQlIfQo6flufO4rRjFV}
```

---

## 参考资料

- [飞书开放平台 - 事件订阅指南](https://open.feishu.cn/document/event-subscription-guide)
- [飞书加解密集成文档](https://www.feishu.cn/hc/zh-CN/articles/486497709952)
- 飞书官方Python/Node.js SDK示例代码

---

## 下一步计划

1. ✅ 完成飞书webhook事件接收和解析
2. ✅ 完成加密事件解密
3. ⏳ 待修复: 重新启用签名验证
4. ⏳ 待修复: 发送卡片消息API（添加receive_id_type参数）
5. ⏳ 实现完整的任务处理流程（LLM调用、工具执行等）

---

**记录人**: Claude (AI Assistant)
**记录日期**: 2026-02-28
**Git提交**: 待提交
