package tech.yesboss.safeguard;

/**
 * 死循环熔断器 (Circuit Breaker for Infinite Loop Prevention)
 *
 * <p>防止 Agent 在同一个报错上反复重试卡死。</p>
 *
 * <p><b>核心职责：</b></p>
 * <ul>
 *   <li>记录 Worker ReAct 循环的迭代次数</li>
 *   <li>当达到阈值（默认 20 轮）时抛出熔断异常</li>
 *   <li>支持重置计数器用于任务重启或修复后继续</li>
 * </ul>
 *
 * <p><b>核心使用场景：</b>
 * 当 Worker 在执行任务时陷入无限循环（例如反复调用同一个工具但总是失败），
 * 熔断器会强制中断并抛出异常，避免系统资源耗尽。</p>
 */
public interface CircuitBreaker {

    /**
     * 默认的循环次数阈值
     */
    int DEFAULT_THRESHOLD = 20;

    /**
     * 记录并检查对话轮数
     *
     * <p>每次调用此方法时，计数器会自增。当计数器达到阈值时，
     * 抛出 {@link CircuitBreakerOpenException} 熔断异常。</p>
     *
     * @param sessionId 当前的 Session ID
     * @throws CircuitBreakerOpenException 当达到 20 轮上限时抛出熔断异常
     * @throws IllegalArgumentException if sessionId is null or empty
     */
    void checkAndIncrement(String sessionId);

    /**
     * 重置计数器
     *
     * <p>通常在任务彻底完成或人工介入修复后调用。</p>
     *
     * <p><b>调用场景：</b></p>
     * <ul>
     *   <li>Worker 任务成功完成（COMPLETED）</li>
     *   <li>Worker 任务失败但需要重试</li>
     *   <li>人工修复后恢复执行</li>
     * </ul>
     *
     * @param sessionId 需要重置的 Session ID
     * @throws IllegalArgumentException if sessionId is null or empty
     */
    void reset(String sessionId);

    /**
     * 获取指定会话的当前循环次数
     *
     * @param sessionId 会话 ID
     * @return 当前循环次数，如果会话不存在则返回 0
     * @throws IllegalArgumentException if sessionId is null or empty
     */
    int getCurrentCount(String sessionId);

    /**
     * 获取循环次数阈值
     *
     * @return 当前阈值（默认 20）
     */
    int getThreshold();

    /**
     * 设置循环次数阈值
     *
     * @param threshold 新的阈值（必须大于 0）
     * @throws IllegalArgumentException if threshold is less than or equal to 0
     */
    void setThreshold(int threshold);
}
