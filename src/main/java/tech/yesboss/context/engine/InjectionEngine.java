package tech.yesboss.context.engine;

import tech.yesboss.domain.message.UnifiedMessage;

/**
 * 向下注入引擎 (Top-Down Injection Engine)
 *
 * <p>负责在 Master 拆分出子任务后，组装 Worker 的初始记忆。</p>
 *
 * <p><b>核心职责：</b></p>
 * <ul>
 *   <li>从全局流中提取规则和约束</li>
 *   <li>将 Master 的全局规则与分配的任务组合</li>
 *   <li>生成 Worker 的初始 System Prompt</li>
 *   <li>实现 Master 到 Worker 的上下文传递</li>
 * </ul>
 */
public interface InjectionEngine {

    /**
     * 拼装 Worker 的初始 System Prompt
     *
     * <p>将提取全局流中的规则，通过硬编码模板与任务目标拼装，
     * 作为初始系统提示词静默注入给新建的 Worker。</p>
     *
     * <p><b>核心使用场景：</b>
     * 当 Master 将大任务拆分为多个子任务后，需要为每个 Worker 创建
     * 独立的 LOCAL 流。此方法负责为 Worker 注入必要的上下文信息，
     * 包括全局约束、任务目标和执行规范。</p>
     *
     * @param masterSessionId 父任务的 Session ID (用于提取全局规则)
     * @param assignedTask 给当前 Worker 下发的死命令 (如："重构 tui 模块")
     * @return 生成的标准化系统提示词消息，随后应由 LocalStreamManager 存入库中
     */
    UnifiedMessage injectInitialContext(String masterSessionId, String assignedTask);
}
