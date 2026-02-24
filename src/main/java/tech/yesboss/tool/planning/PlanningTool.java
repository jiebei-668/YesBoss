package tech.yesboss.tool.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.yesboss.domain.message.UnifiedMessage;
import tech.yesboss.llm.LlmClient;
import tech.yesboss.tool.AgentTool;
import tech.yesboss.tool.ToolAccessLevel;

/**
 * 规划工具 (Planning Tool)
 *
 * <p>专门用于 Master Agent 的固定规划流程工具。</p>
 *
 * <p><b>核心职责：</b></p>
 * <ul>
 *   <li>将大任务拆分为细小的子任务</li>
 *   <li>生成结构化的 JSON 格式执行计划</li>
 *   <li>使用专门定制的 Prompt 进行任务分解</li>
 * </ul>
 *
 * <p><b>使用场景：</b></p>
 * <p>当 Master Agent 完成需求确认和环境探索后，必须调用此工具进行任务规划。
 * 这是系统设计中的"固定流程"，确保任务分解的一致性和可控性。</p>
 *
 * <p><b>输出格式：</b></p>
 * <pre>
 * [
 *   {
 *     "id": "task-1",
 *     "description": "具体的子任务描述",
 *     "priority": "high|medium|low"
 *   },
 *   ...
 * ]
 * </pre>
 */
public class PlanningTool implements AgentTool {

    private static final Logger logger = LoggerFactory.getLogger(PlanningTool.class);

    private static final String TOOL_NAME = "planning_tool";
    private static final String TOOL_DESCRIPTION = """
        将大任务拆分为细小的子任务。输出为JSON格式的子任务数组，每个子任务包含：
        - id: 子任务唯一标识
        - description: 子任务描述
        - priority: 优先级 (high/medium/low)

        示例输出：
        [
          {"id": "task-1", "description": "分析需求", "priority": "high"},
          {"id": "task-2", "description": "设计方案", "priority": "high"}
        ]
        """;

    private static final String JSON_SCHEMA = """
        {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "id": {"type": "string"},
              "description": {"type": "string"},
              "priority": {"type": "string", "enum": ["high", "medium", "low"]}
            },
            "required": ["id", "description", "priority"]
          }
        }
        """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数
     *
     * @param llmClient LLM 客户端（用于生成规划）
     * @throws IllegalArgumentException 如果 llmClient 为 null
     */
    public PlanningTool(LlmClient llmClient) {
        if (llmClient == null) {
            throw new IllegalArgumentException("llmClient cannot be null");
        }
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
        logger.info("PlanningTool initialized");
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return TOOL_DESCRIPTION;
    }

    @Override
    public String getParametersJsonSchema() {
        return JSON_SCHEMA;
    }

    @Override
    public ToolAccessLevel getAccessLevel() {
        // PlanningTool 是 Master 专属工具，为只读级别
        return ToolAccessLevel.READ_ONLY;
    }

    @Override
    public String execute(String argumentsJson) throws Exception {
        logger.info("PlanningTool.execute called with arguments: {}", argumentsJson);

        // 解析输入参数
        PlanningRequest request = parseRequest(argumentsJson);

        // 构造专门定制的规划 Prompt
        String planningPrompt = buildPlanningPrompt(request);

        // 调用 LLM 生成规划
        logger.debug("Calling LLM with planning prompt for task: {}", request.taskDescription());

        // 使用系统提示词和用户消息
        UnifiedMessage userPrompt = UnifiedMessage.user(planningPrompt);

        UnifiedMessage response = llmClient.chat(java.util.List.of(userPrompt), getSystemPrompt());

        // 提取响应内容
        String llmResponse = response.content();

        // 提取并验证 JSON 格式的规划
        String planJson = extractAndValidatePlan(llmResponse);

        logger.info("PlanningTool completed successfully, generated plan: {}", planJson);
        return planJson;
    }

    @Override
    public String executeWithBypass(String argumentsJson) throws Exception {
        // PlanningTool 是只读工具，不需要绕过沙箱
        // 直接调用 execute 即可
        return execute(argumentsJson);
    }

    /**
     * 解析输入请求
     *
     * @param argumentsJson JSON 格式的参数
     * @return 解析后的请求对象
     * @throws Exception 如果解析失败
     */
    private PlanningRequest parseRequest(String argumentsJson) throws Exception {
        try {
            return objectMapper.readValue(argumentsJson, PlanningRequest.class);
        } catch (Exception e) {
            logger.error("Failed to parse arguments: {}", argumentsJson, e);
            throw new Exception("Invalid arguments format for PlanningTool: " + e.getMessage(), e);
        }
    }

    /**
     * 构造规划 Prompt
     *
     * @param request 规划请求
     * @return 规划 Prompt 文本
     */
    private String buildPlanningPrompt(PlanningRequest request) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("# 任务规划\n\n");
        prompt.append("请将以下大任务拆分为细小的、可执行的子任务：\n\n");
        prompt.append("## 原始任务\n");
        prompt.append(request.taskDescription()).append("\n\n");

        if (request.context() != null && !request.context().isEmpty()) {
            prompt.append("## 背景信息\n");
            prompt.append(request.context()).append("\n\n");
        }

        if (request.constraints() != null && !request.constraints().isEmpty()) {
            prompt.append("## 约束条件\n");
            prompt.append(request.constraints()).append("\n\n");
        }

        prompt.append("## 要求\n");
        prompt.append("1. 将任务拆分为 3-10 个子任务\n");
        prompt.append("2. 每个子任务应该是独立、可执行的\n");
        prompt.append("3. 子任务之间应该有清晰的依赖关系或执行顺序\n");
        prompt.append("4. 返回 JSON 格式的数组，格式如下：\n");
        prompt.append("""
            [
              {"id": "task-1", "description": "子任务描述", "priority": "high"},
              {"id": "task-2", "description": "子任务描述", "priority": "medium"}
            ]
            """);

        return prompt.toString();
    }

    /**
     * 获取系统 Prompt
     *
     * @return 系统 Prompt 文本
     */
    private String getSystemPrompt() {
        return """
            你是一个专业的任务规划专家。你的职责是将复杂的大任务拆分为细小的、可执行的子任务。

            请遵循以下原则：
            1. 每个子任务应该足够具体，可以在一次会话中完成
            2. 子任务之间应该有清晰的逻辑关系
            3. 优先使用 "high" 优先级标记关键路径任务
            4. 输出必须是合法的 JSON 格式，不要包含任何其他文本
            """;
    }

    /**
     * 从 LLM 响应中提取并验证规划 JSON
     *
     * @param llmResponse LLM 响应
     * @return 提取的 JSON 规划
     * @throws Exception 如果提取或验证失败
     */
    private String extractAndValidatePlan(String llmResponse) throws Exception {
        // 尝试直接解析 JSON
        try {
            // 验证是否为有效的 JSON 数组
            Object parsed = objectMapper.readValue(llmResponse, Object.class);

            // 如果是数组，直接返回
            if (parsed instanceof java.util.List) {
                return llmResponse.trim();
            }

            throw new Exception("LLM response is not a valid JSON array");

        } catch (Exception e) {
            // 尝试从响应中提取 JSON（处理可能包含额外文本的情况）
            logger.warn("Direct JSON parsing failed, attempting to extract JSON from response");

            // 查找 JSON 数组的开始和结束
            int startIndex = llmResponse.indexOf('[');
            int endIndex = llmResponse.lastIndexOf(']');

            if (startIndex >= 0 && endIndex > startIndex) {
                String extractedJson = llmResponse.substring(startIndex, endIndex + 1);

                // 验证提取的 JSON
                objectMapper.readValue(extractedJson, Object.class);

                logger.info("Successfully extracted JSON from LLM response");
                return extractedJson.trim();
            }

            throw new Exception("Failed to extract valid JSON from LLM response: " + e.getMessage(), e);
        }
    }

    /**
     * 规划请求参数
     *
     * @param taskDescription 任务描述
     * @param context 背景信息（可选）
     * @param constraints 约束条件（可选）
     */
    private record PlanningRequest(
            String taskDescription,
            String context,
            String constraints
    ) {}
}
