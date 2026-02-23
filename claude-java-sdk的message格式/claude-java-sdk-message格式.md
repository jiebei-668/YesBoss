---                                                                                     
Anthropic Java SDK - 消息系统完整分析

一、核心消息类型

1. MessageParam (请求消息参数)

位置:                                                                                   
anthropic-java-core/src/main/kotlin/com/anthropic/models/messages/MessageParam.kt:33

用于构建发送给 API 的请求消息。

字段结构：                                                                              
┌─────────┬─────────┬──────┬──────────────────────────────────────┐                     
│  字段   │  类型   │ 必填 │                 说明                 │                     
├─────────┼─────────┼──────┼──────────────────────────────────────┤                     
│ content │ Content │ 是   │ 消息内容（可以是字符串或内容块数组） │                     
├─────────┼─────────┼──────┼──────────────────────────────────────┤                     
│ role    │ Role    │ 是   │ 消息角色（user 或 assistant）        │                     
└─────────┴─────────┴──────┴──────────────────────────────────────┘                     
Content 类型（联合类型）：
- String - 简单文本内容
- List<ContentBlockParam> - 结构化内容块数组

Role 枚举：
- USER ("user")
- ASSISTANT ("assistant")

  ---                                                                                     
2. Message (响应消息)

位置: anthropic-java-core/src/main/kotlin/com/anthropic/models/messages/Message.kt:22

API 返回的消息对象。

字段结构：                                                                              
┌───────────────┬────────────────────┬──────┬──────────────────────┐                    
│     字段      │        类型        │ 必填 │         说明         │                    
├───────────────┼────────────────────┼──────┼──────────────────────┤                    
│ id            │ String             │ 是   │ 消息唯一标识符       │                    
├───────────────┼────────────────────┼──────┼──────────────────────┤                    
│ content       │ List<ContentBlock> │ 是   │ 模型生成的内容块数组 │                    
├───────────────┼────────────────────┼──────┼──────────────────────┤                    
│ model         │ Model              │ 是   │ 使用的模型           │                    
├───────────────┼────────────────────┼──────┼──────────────────────┤                    
│ role          │ JsonValue          │ 是   │ 固定为 "assistant"   │                    
├───────────────┼────────────────────┼──────┼──────────────────────┤                    
│ stop_reason   │ StopReason         │ 否   │ 停止原因             │                    
├───────────────┼────────────────────┼──────┼──────────────────────┤                    
│ stop_sequence │ String             │ 否   │ 触发的停止序列       │                    
├───────────────┼────────────────────┼──────┼──────────────────────┤                    
│ type          │ JsonValue          │ 是   │ 固定为 "message"     │                    
├───────────────┼────────────────────┼──────┼──────────────────────┤                    
│ usage         │ Usage              │ 是   │ token 使用情况       │                    
├───────────────┼────────────────────┼──────┼──────────────────────┤                    
│ container     │ Container          │ 否   │ 代码执行容器信息     │                    
└───────────────┴────────────────────┴──────┴──────────────────────┘                    
StopReason 枚举值：
- end_turn - 自然停止
- max_tokens - 达到最大 token 数
- stop_sequence - 触发自定义停止序列
- tool_use - 调用了工具
- pause_turn - 暂停长轮次
- refusal - 拒绝响应（策略违规）

  ---                                                                                     
二、内容块类型

请求端 - ContentBlockParam

位置: anthropic-java-core/src/main/kotlin/com/anthropic/models/messages/ContentBlockPara
m.kt:24

支持的内容块类型：                                                                      
类型: TextBlockParam                                                                    
说明: 文本内容                                                                          
类型标识: "text"                                                                        
────────────────────────────────────────                                                
类型: ImageBlockParam                                                                   
说明: 图片内容（base64 或 URL）                                                         
类型标识: "image"                                                                       
────────────────────────────────────────                                                
类型: DocumentBlockParam                                                                
说明: 文档内容                                                                          
类型标识: "document"                                                                    
────────────────────────────────────────                                                
类型: SearchResultBlockParam                                                            
说明: 搜索结果                                                                          
类型标识: "search_result"                                                               
────────────────────────────────────────                                                
类型: ThinkingBlockParam                                                                
说明: 模型思考过程                                                                      
类型标识: "thinking"                                                                    
────────────────────────────────────────                                                
类型: RedactedThinkingBlockParam                                                        
说明: 编辑后的思考                                                                      
类型标识: "redacted_thinking"                                                           
────────────────────────────────────────                                                
类型: ToolUseBlockParam                                                                 
说明: 工具调用                                                                          
类型标识: "tool_use"                                                                    
────────────────────────────────────────                                                
类型: ToolResultBlockParam                                                              
说明: 工具执行结果                                                                      
类型标识: "tool_result"                                                                 
────────────────────────────────────────                                                
类型: ServerToolUseBlockParam                                                           
说明: 服务端工具调用                                                                    
类型标识: "server_tool_use"                                                             
────────────────────────────────────────                                                
类型: WebSearchToolResultBlockParam                                                     
说明: 网络搜索结果                                                                      
类型标识: "web_search_tool_result"                                                      
────────────────────────────────────────                                                
类型: WebFetchToolResultBlockParam                                                      
说明: 网页获取结果                                                                      
类型标识: "web_fetch_tool_result"                                                       
────────────────────────────────────────                                                
类型: CodeExecutionToolResultBlockParam                                                 
说明: 代码执行结果                                                                      
类型标识: "code_execution_tool_result"                                                  
────────────────────────────────────────                                                
类型: BashCodeExecutionToolResultBlockParam                                             
说明: Bash 代码执行结果                                                                 
类型标识: "bash_code_execution_tool_result"                                             
────────────────────────────────────────                                                
类型: TextEditorCodeExecutionToolResultBlockParam                                       
说明: 文本编辑器代码执行结果                                                            
类型标识: "text_editor_code_execution_tool_result"                                      
────────────────────────────────────────                                                
类型: ToolSearchToolResultBlockParam                                                    
说明: 工具搜索结果                                                                      
类型标识: "tool_search_tool_result"                                                     
────────────────────────────────────────                                                
类型: ContainerUploadBlockParam                                                         
说明: 容器文件上传                                                                      
类型标识: "container_upload"                                                            
响应端 - ContentBlock

位置:                                                                                   
anthropic-java-core/src/main/kotlin/com/anthropic/models/messages/ContentBlock.kt:24

响应中的内容块（请求端的子集）：                                                        
┌────────────────────────────────────────┬────────────────────┐                         
│                  类型                  │        说明        │                         
├────────────────────────────────────────┼────────────────────┤                         
│ TextBlock                              │ 文本内容           │                         
├────────────────────────────────────────┼────────────────────┤                         
│ ThinkingBlock                          │ 思考内容           │                         
├────────────────────────────────────────┼────────────────────┤                         
│ RedactedThinkingBlock                  │ 编辑后的思考       │                         
├────────────────────────────────────────┼────────────────────┤                         
│ ToolUseBlock                           │ 工具调用           │                         
├────────────────────────────────────────┼────────────────────┤                         
│ ServerToolUseBlock                     │ 服务端工具调用     │                         
├────────────────────────────────────────┼────────────────────┤                         
│ WebSearchToolResultBlock               │ 网络搜索结果       │                         
├────────────────────────────────────────┼────────────────────┤                         
│ WebFetchToolResultBlock                │ 网页获取结果       │                         
├────────────────────────────────────────┼────────────────────┤                         
│ CodeExecutionToolResultBlock           │ 代码执行结果       │                         
├────────────────────────────────────────┼────────────────────┤                         
│ BashCodeExecutionToolResultBlock       │ Bash 代码执行结果  │                         
├────────────────────────────────────────┼────────────────────┤                         
│ TextEditorCodeExecutionToolResultBlock │ 文本编辑器执行结果 │                         
├────────────────────────────────────────┼────────────────────┤                         
│ ToolSearchToolResultBlock              │ 工具搜索结果       │                         
├────────────────────────────────────────┼────────────────────┤                         
│ ContainerUploadBlock                   │ 容器上传           │                         
└────────────────────────────────────────┴────────────────────┘
  ---                                                                                     
三、重要内容块详解

1. TextBlockParam (文本块)

位置:                                                                                   
anthropic-java-core/src/main/kotlin/com/anthropic/models/messages/TextBlockParam.kt:22

字段：
- text: String        [必填] 文本内容
- type: "text"        [固定] 类型标识
- cacheControl: CacheControlEphemeral [可选] 缓存控制
- citations: List<TextCitationParam> [可选] 引用列表

2. ToolUseBlockParam (工具调用块)

位置: anthropic-java-core/src/main/kotlin/com/anthropic/models/messages/ToolUseBlockPara
m.kt:31

字段：
- id: String          [必填] 工具调用唯一ID
- name: String        [必填] 工具名称
- input: Input        [必填] 工具输入参数（JSON对象）
- type: "tool_use"    [固定] 类型标识
- cacheControl: CacheControlEphemeral [可选] 缓存控制
- caller: Caller      [可选] 调用者信息

Caller 类型：
- DirectCaller        [type: "direct"] 直接调用
- ServerToolCaller    [type: "code_execution_20250825"] 代码执行
- ServerToolCaller20260120 [type: "code_execution_20260120"] 新版代码执行

3. ToolResultBlockParam (工具结果块)

位置: anthropic-java-core/src/main/kotlin/com/anthropic/models/messages/ToolResultBlockP
aram.kt:33

字段：
- toolUseId: String   [必填] 对应的工具调用ID
- type: "tool_result" [固定] 类型标识
- content: Content    [可选] 结果内容（字符串或内容块数组）
- isError: Boolean    [可选] 是否为错误
- cacheControl: CacheControlEphemeral [可选] 缓存控制

Content 类型：
- String              简单字符串结果
- List<Block>         结构化结果块数组

Block 子类型：
- TextBlockParam      文本
- ImageBlockParam     图片
- SearchResultBlockParam 搜索结果
- DocumentBlockParam  文档
- ToolReferenceBlockParam 工具引用

  ---                                                                                     
四、消息创建参数

MessageCreateParams

位置: anthropic-java-core/src/main/kotlin/com/anthropic/models/messages/MessageCreatePar
ams.kt:47

创建消息请求的完整参数：

必填字段：
- maxTokens: Long           最大生成 token 数
- messages: List<MessageParam> 消息列表
- model: Model              模型名称

可选字段：
- system: System            系统提示词
- temperature: Double       温度参数 (0.0-1.0)
- topK: Integer             top-k 采样
- topP: Double              top-p 采样
- stopSequences: List<String> 停止序列
- stream: Boolean           是否流式返回
- tools: List<Tool>         工具定义列表
- toolChoice: ToolChoice    工具选择策略
- metadata: Metadata        元数据
- cacheControl: CacheControlEphemeral 顶层缓存控制
- thinking: ThinkingConfigParam 扩展思考配置
- container: String         容器标识符
- inferenceGeo: String      推理地理位置
- serviceTier: ServiceTier  服务层级
- outputConfig: OutputConfig 输出配置

  ---                                                                                     
五、使用示例

// 创建简单文本消息                                                                     
MessageParam userMessage = MessageParam.builder()                                       
.role(MessageParam.Role.USER)                                                       
.content("Hello, Claude!")                                                          
.build();

// 创建多模态消息（文本+图片）                                                          
List<ContentBlockParam> content = Arrays.asList(                                        
TextBlockParam.builder().text("What's in this image?").build(),                     
ImageBlockParam.builder()                                                           
.source(ContentBlockSource.builder()                                            
.type(ImageBlockParam.Source.Type.BASE64)                                   
.mediaType("image/jpeg")                                                    
.data("/9j/4AAQSkZJRg...")                                                  
.build())                                                                   
.build()                                                                        
);

MessageParam multiModalMessage = MessageParam.builder()                                 
.role(MessageParam.Role.USER)                                                       
.contentOfBlockParams(content)                                                      
.build();

// 创建工具调用消息                                                                     
ContentBlockParam toolUse = ContentBlockParam.ofToolUse(                                
ToolUseBlockParam.builder()                                                         
.id("toolu_abc123")                                                             
.name("get_weather")                                                            
.input(ToolUseBlockParam.Input.builder()                                        
.putAdditionalProperty("location", "San Francisco")                         
.putAdditionalProperty("unit", "celsius")                                   
.build())                                                                   
.build()                                                                        
);

// 创建工具结果消息                                                                     
ContentBlockParam toolResult = ContentBlockParam.ofToolResult(                          
ToolResultBlockParam.builder()                                                      
.toolUseId("toolu_abc123")                                                      
.content("The weather in San Francisco is 22°C")                                
.isError(false)                                                                 
.build()                                                                        
);
                                                                                          
---                                                         