package tech.yesboss.runner;

/**
 * 总 Agent 执行器接口 (Master Runner Interface)
 *
 * <p>Master Agent 是全局控制器，负责整体任务规划和子任务调度。</p>
 *
 * <p><b>权限边界：</b></p>
 * <ul>
 *   <li>只读探索工具（不允许修改系统状态）</li>
 *   <li>全局流读写（维护全局上下文）</li>
 * </ul>
 *
 * <p><b>核心职责：</b></p>
 * <ul>
 *   <li>需求澄清和环境探索</li>
 *   <li>制定执行计划并生成子任务</li>
 *   <li>协调 Worker 任务执行</li>
 *   <li>生成最终总结报告</li>
 * </ul>
 */
public interface MasterRunner extends AgentRunner {

    /**
     * 制定执行蓝图
     *
     * <p>在环境探索完毕后，生成 JSON 格式的子任务队列。</p>
     *
     * <p><b>执行流程：</b></p>
     * <ol>
     *   <li>拉取全局上下文（包含用户需求和探索结果）</li>
     *   <li>调用专用的规划工具（PlanningTool）</li>
     *   <li>生成结构化的子任务 JSON 数组</li>
     *   <li>保存到 TaskSession 的 execution_plan 字段</li>
     * </ol>
     *
     * <p><b>输出格式：</b></p>
     * <pre>
     * [
     *   {"id": "task-1", "description": "Research X", "priority": "high"},
     *   {"id": "task-2", "description": "Implement Y", "priority": "medium"},
     *   ...
     * ]
     * </pre>
     *
     * @param sessionId Master 的会话 ID
     * @return JSON 格式的子任务队列字符串
     * @throws IllegalStateException 如果会话不存在或不在 PLANNING 状态
     */
    String generateExecutionPlan(String sessionId);
}
