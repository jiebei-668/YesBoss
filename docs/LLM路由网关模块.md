### 1. 防腐层契约：`VendorSdkAdapter.java`

这个接口定义了系统的统一消息（`UnifiedMessage`）与任意外部 SDK 之间互转的强制规范。注意这里使用了双泛型 `<Req, Res>` 来完美适配请求与响应不对称的 SDK。

```java
package com.yourproject.module5.adapter;

import com.yourproject.module5.model.UnifiedMessage;

/**
 * 模块 5：大模型厂商 SDK 防腐层适配器接口
 * * @param <Req> 厂商 SDK 的请求体类型 (例如 Claude 的 MessageParam)
 * @param <Res> 厂商 SDK 的响应体类型 (例如 Claude 的 Message)
 */
public interface VendorSdkAdapter<Req, Res> {

    /**
     * 获取当前适配器支持的序列化格式标识（将存入 chat_message.payload_format）
     * 例如："ANTHROPIC_V3", "OPENAI_V1"
     */
    String getSupportedFormat();

    /**
     * 【去程转换】将系统内部的标准 UnifiedMessage 转换为特定厂商的请求对象
     */
    Req toSdkRequest(UnifiedMessage unifiedMessage);

    /**
     * 【回程转换】将特定厂商的响应对象转换为系统内部的标准 UnifiedMessage
     */
    UnifiedMessage toUnifiedMessage(Res sdkResponse);

    /**
     * 【持久化提取】从 SDK 响应对象中，提取出需要存入数据库的原始多态 JSON 字符串
     * 这是 Resume 能够完美还原上下文的关键。
     */
    String extractRawContentJson(Res sdkResponse);

    /**
     * 【唤醒反序列化】从数据库捞出 JSON 字符串和 Role 后，将其完美还原为大模型请求对象
     * * @param jsonContent 数据库中的 chat_message.content
     * @param msgRole 数据库中的 chat_message.msg_role (user, assistant)
     */
    Req deserializeToRequest(String jsonContent, String msgRole);
}

```

---

### 2. 核心实现类：`ClaudeSdkAdapterImpl.java`

这个类完全基于你提供的 Anthropic Java SDK 结构实现。它承担了极其繁重的多态类型判断和 JSON 转换工作，从而让外部系统的脏活累活绝不污染内部的调度引擎。

```java
package com.yourproject.module5.adapter.impl;

import com.anthropic.models.messages.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourproject.module5.model.UnifiedMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 模块 5：Claude V3 适配器实现
 * 负责抹平 MessageParam (请求) 和 Message (响应) 的差异，以及复杂 Block 的序列化。
 */
public class ClaudeSdkAdapterImpl implements VendorSdkAdapter<MessageParam, Message> {

    // 推荐在 Spring 中注入，配置好多态反序列化特性
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getSupportedFormat() {
        return "ANTHROPIC_V3";
    }

    // ==========================================
    // 1. 【去程】UnifiedMessage -> MessageParam
    // ==========================================
    @Override
    public MessageParam toSdkRequest(UnifiedMessage unifiedMsg) {
        List<ContentBlockParam> blocks = new ArrayList<>();

        // 1.1 处理纯文本
        if (unifiedMsg.textContent() != null && !unifiedMsg.textContent().isEmpty()) {
            blocks.add(ContentBlockParam.ofText(
                TextBlockParam.builder().text(unifiedMsg.textContent()).build()
            ));
        }

        // 1.2 处理工具调用请求 (Assistant 想调用工具)
        if (unifiedMsg.toolCalls() != null && !unifiedMsg.toolCalls().isEmpty()) {
            for (UnifiedMessage.ToolCall call : unifiedMsg.toolCalls()) {
                // 将内部存的 JSON 字符串转回 SDK 需要的 Input 对象
                ToolUseBlockParam.Input inputObj = parseJsonToInput(call.argumentsJson());
                
                blocks.add(ContentBlockParam.ofToolUse(
                    ToolUseBlockParam.builder()
                        .id(call.id())
                        .name(call.name())
                        .input(inputObj)
                        .build()
                ));
            }
        }

        // 1.3 处理工具执行结果 (User 把工具执行结果返回给 Assistant)
        if (unifiedMsg.toolResults() != null && !unifiedMsg.toolResults().isEmpty()) {
            for (UnifiedMessage.ToolResult result : unifiedMsg.toolResults()) {
                blocks.add(ContentBlockParam.ofToolResult(
                    ToolResultBlockParam.builder()
                        .toolUseId(result.toolCallId())
                        .content(result.resultString()) // 工具执行完的输出
                        .isError(result.isError())
                        .build()
                ));
            }
        }

        return MessageParam.builder()
            // 将我们内部的 role 映射为 SDK 的 Role 枚举
            .role(MessageParam.Role.valueOf(unifiedMsg.role().toUpperCase()))
            .contentOfBlockParams(blocks)
            .build();
    }

    // ==========================================
    // 2. 【回程】Message -> UnifiedMessage
    // ==========================================
    @Override
    public UnifiedMessage toUnifiedMessage(Message claudeResponse) {
        StringBuilder textBuilder = new StringBuilder();
        List<UnifiedMessage.ToolCall> toolCalls = new ArrayList<>();

        // 遍历 Claude 返回的多态 ContentBlock，使用 Java 21 模式匹配 (Pattern Matching)
        for (ContentBlock block : claudeResponse.content()) {
            if (block.isTextBlock()) {
                textBuilder.append(block.asTextBlock().text());
            } else if (block.isToolUseBlock()) {
                ToolUseBlock toolUse = block.asToolUseBlock();
                toolCalls.add(new UnifiedMessage.ToolCall(
                    toolUse.id(),
                    toolUse.name(),
                    toolUse.input().toString() // 将 Input 重新转为 JSON 字符串供内部流转
                ));
            }
            // 忽略 ThinkingBlock 等内部不需要感知的块，保持核心流转的干净
        }

        return new UnifiedMessage(
            claudeResponse.role().toString(), 
            textBuilder.toString(),
            toolCalls,
            null, // 响应体中不会包含 ToolResult
            getSupportedFormat(),
            extractRawContentJson(claudeResponse) // 附带原始 JSON 供落盘
        );
    }

    // ==========================================
    // 3. 【持久化提取】将复杂的 ContentBlock 数组转为 JSON
    // ==========================================
    @Override
    public String extractRawContentJson(Message claudeResponse) {
        try {
            // 直接将 SDK 的 List<ContentBlock> 序列化为 JSON 字符串
            return objectMapper.writeValueAsString(claudeResponse.content());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化 Claude Content 失败", e);
        }
    }

    // ==========================================
    // 4. 【唤醒恢复】JSON -> MessageParam (挂起唤醒的核心)
    // ==========================================
    @Override
    public MessageParam deserializeToRequest(String jsonContent, String msgRole) {
        try {
            // 将底层 SQLite 存的纯 JSON 字符串，反序列化为 Claude SDK 需要的多态对象数组
            List<ContentBlockParam> blocks = objectMapper.readValue(
                jsonContent, 
                new TypeReference<List<ContentBlockParam>>() {}
            );
            
            return MessageParam.builder()
                .role(MessageParam.Role.valueOf(msgRole.toUpperCase()))
                .contentOfBlockParams(blocks)
                .build();
        } catch (Exception e) {
            throw new RuntimeException("Resume 唤醒失败：反序列化 Claude 上下文出错. JSON: " + jsonContent, e);
        }
    }
    
    // ------------------------------------------
    // 内部辅助方法
    // ------------------------------------------
    private ToolUseBlockParam.Input parseJsonToInput(String json) {
        try {
            // 将统一消息里的 argumentsJson 转为 Map
            Map<String, Object> properties = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            ToolUseBlockParam.Input.Builder builder = ToolUseBlockParam.Input.builder();
            // 动态放入参数
            properties.forEach(builder::putAdditionalProperty);
            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException("解析工具参数 JSON 失败: " + json, e);
        }
    }
}

```
### 3. 统一调用接口：`LlmClient` (纯净的契约)

无论底层用的是 Claude 3.5 Sonnet、GPT-4o 还是本地的 Qwen，调度引擎（模块 2）只需要面对这个接口。

```java
package com.yourproject.module5.client;

import com.yourproject.module5.model.UnifiedMessage;
import com.yourproject.module4.ToolRegistry; // 模块4的工具注册中心
import java.util.List;

/**
 * 模块 5：LLM 统一调用客户端
 * 所有的特定模型实现（如 ClaudeClientImpl）都必须实现此接口
 */
public interface LlmClient {
    
    /**
     * 核心聊天接口（支持工具调用）
     * @param context 历史上下文（双流组装好的 UnifiedMessage 列表）
     * @param systemPrompt 针对当前 Agent 的系统提示词
     * @param availableTools 当前 Agent 有权限使用的工具列表
     * @return UnifiedMessage 大模型的回复（可能是文本，也可能是工具调用指令）
     */
    UnifiedMessage chat(
        List<UnifiedMessage> context, 
        String systemPrompt, 
        ToolRegistry availableTools
    );

    /**
     * 文本摘要接口（专用于模块 3 的超长上下文压缩，使用 Worker 模型）
     */
    String summarize(String longText);
}

```

### 4. 动态路由调度：`ModelRouter` (大模型的 HR 部门)

为什么需要 Router？因为好钢要用在刀刃上！Master 规划任务需要最聪明的模型，Worker 试错和摘要压缩可以用经济高效的模型。

```java
package com.yourproject.module5.router;

import com.yourproject.module5.client.LlmClient;

/**
 * 模块 5：大模型动态路由器
 */
public class ModelRouter {

    private final LlmClient masterModelClient;  // 例如：Claude 3.5 Sonnet (最聪明，最贵)
    private final LlmClient workerModelClient;  // 例如：Claude 3.5 Haiku (写代码快，便宜，也用于摘要)

    public ModelRouter(LlmClient master, LlmClient worker) {
        this.masterModelClient = master;
        this.workerModelClient = worker;
    }

    /**
     * 根据不同的场景，路由到不同的底层模型
     */
    public LlmClient routeByRole(String agentRole) {
        return switch (agentRole.toUpperCase()) {
            case "MASTER" -> masterModelClient;
            case "WORKER" -> workerModelClient;
            default -> throw new IllegalArgumentException("未知的 Agent 角色: " + agentRole);
        };
    }

    /**
     * 获取摘要压缩模型（使用 Worker 模型）
     */
    public LlmClient getSummarizer() {
        return workerModelClient;
    }
}

```
### 5.核心实体：UnifiedMessage.java
这段代码使用了 Java 21 的 record 特性来保证绝对的线程安全和不可变性，同时内部提供了极其优雅的静态工厂方法（Static Factory Methods），让上层模块在组装消息时像写散文一样流畅。
```java
package com.yourproject.module5.model;

import java.util.Collections;
import java.util.List;

/**
 * 模块 5：系统内部统一消息模型 (Domain Entity)
 * 核心定位：防腐层的产物。系统内部所有模块只认它，绝对不依赖任何外部厂商的 SDK 类。
 * 特性：不可变 (Immutable)、线程安全、支持复杂多态工具调用。
 */
public record UnifiedMessage(
    Role role,                        // 消息角色
    String textContent,               // 纯文本内容 (如果是纯文本对话)
    List<ToolCall> toolCalls,         // 工具调用列表 (当 Assistant 决定使用工具时)
    List<ToolResult> toolResults,     // 工具执行结果 (当 User/System 返回工具结果时)
    String rawPayloadFormat,          // 契约标识：记录底层序列化协议 (如 "ANTHROPIC_V3")
    String rawJsonContent             // 原始载荷：专供模块 6 存入 SQLite，用于完美 Resume
) {

    // ==========================================
    // 1. 内部核心枚举与结构 (高度内聚)
    // ==========================================
    
    public enum Role {
        SYSTEM, USER, ASSISTANT, TOOL
    }

    /**
     * 大模型发起的工具调用指令
     */
    public record ToolCall(String id, String name, String argumentsJson) {}

    /**
     * 系统执行完毕后，回传给大模型的工具结果
     */
    public record ToolResult(String toolCallId, String resultString, boolean isError) {}


    // ==========================================
    // 2. 构造器防卫 (Defensive Constructor)
    // ==========================================
    
    public UnifiedMessage {
        // 保证列表永远不为 null，消灭系统内部的 NullPointerException
        toolCalls = toolCalls == null ? Collections.emptyList() : List.copyOf(toolCalls);
        toolResults = toolResults == null ? Collections.emptyList() : List.copyOf(toolResults);
    }


    // ==========================================
    // 3. 极其优雅的静态工厂方法 (供其他模块快速创建)
    // ==========================================

    /**
     * 场景 A：创建一个纯文本的用户或系统消息 (模块 3 拼装 Prompt 时最常用)
     */
    public static UnifiedMessage ofText(Role role, String text) {
        return new UnifiedMessage(role, text, null, null, "PLAIN_TEXT", text);
    }

    /**
     * 场景 B：创建一个单纯的工具执行结果消息 (模块 4 执行完沙箱工具后调用)
     */
    public static UnifiedMessage ofToolResult(String toolCallId, String result, boolean isError) {
        ToolResult tr = new ToolResult(toolCallId, result, isError);
        return new UnifiedMessage(
            Role.USER, // Claude 等模型通常要求工具结果以 User 身份返回
            null, 
            null, 
            List.of(tr), 
            "PLAIN_TEXT", // 简化的结果通常可以直接作为文本存盘，或由 Adapter 重新序列化
            result 
        );
    }

    // ==========================================
    // 4. 业务判断辅助方法 (供模块 2 调度引擎进行状态机流转)
    // ==========================================

    /**
     * 判断大模型是否在当前消息中发起了工具调用
     */
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
```