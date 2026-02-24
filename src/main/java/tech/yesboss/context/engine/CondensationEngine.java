package tech.yesboss.context.engine;

/**
 * 向上收敛引擎 (Bottom-Up Condensation Engine)
 *
 * <p>负责解决上下文超载，提纯脏数据。</p>
 *
 * <p><b>核心职责：</b></p>
 * <ul>
 *   <li>监控 Worker 局部流的 Token 消耗</li>
 *   <li>在 Token 超过阈值时触发压缩机制</li>
 *   <li>将 Worker 的局部流历史提纯为简短报告</li>
 *   <li>将压缩后的摘要合并回 Master 的全局流</li>
 * </ul>
 */
public interface CondensationEngine {

    /**
     * 触发局部流压缩并合并回全局流
     *
     * <p>开启独立的全新 Context，调用和 Worker 相同的模型，
     * 注入"系统级日志压缩规则"，将庞大的局部流历史提纯为简短的执行报告，
     * 并最终合并回 Master 的全局流。</p>
     *
     * <p><b>核心使用场景：</b>
     * 当 Worker 任务完成或触发熔断时，需要将局部执行历史总结汇报给 Master。</p>
     *
     * @param workerSessionId 触发熔断或顺利完成的 Worker Session ID
     * @param masterSessionId 需要接收汇报的 Master Session ID
     * @return 压缩后的精简摘要文本
     */
    String condenseAndMergeUpwards(String workerSessionId, String masterSessionId);

    /**
     * Token 超载探针拦截
     *
     * <p>检查当前 Worker 的局部流 Token 是否超出阈值。
     * 如果超出，可以在内部触发会话内的 Compact 机制。</p>
     *
     * @param workerSessionId 需要检查的 Worker Session ID
     * @return true if token count exceeds threshold and compaction is needed, false otherwise
     */
    boolean checkAndCompactIfNeeded(String workerSessionId);

    /**
     * Get the current token threshold for triggering compaction.
     *
     * @return The token threshold
     */
    int getTokenThreshold();

    /**
     * Set a custom token threshold for triggering compaction.
     *
     * @param threshold The new token threshold (must be positive)
     */
    void setTokenThreshold(int threshold);
}
