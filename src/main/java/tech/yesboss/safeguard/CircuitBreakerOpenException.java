package tech.yesboss.safeguard;

/**
 * 熔断器开启异常 (Circuit Breaker Open Exception)
 *
 * <p>当 Worker 的 ReAct 循环达到阈值（默认 20 轮）时抛出此异常，
 * 防止 Agent 陷入无限循环。</p>
 *
 * <p><b>触发场景：</b></p>
 * <ul>
 *   <li>Worker 反复调用同一个工具但总是失败</li>
 *   <li>Worker 在同一个报错上反复重试</li>
 *   <li>Worker 无法从错误中恢复</li>
 * </ul>
 *
 * <p><b>处理方式：</b></p>
 * <ul>
 *   <li>捕获此异常并将任务状态标记为 FAILED</li>
 *   <li>记录详细的错误日志供人工排查</li>
 *   <li>可选：通过 IM 通知用户任务失败</li>
 * </ul>
 */
public class CircuitBreakerOpenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String sessionId;
    private final int currentCount;
    private final int threshold;

    /**
     * 创建熔断器开启异常
     *
     * @param message     错误消息
     * @param sessionId   触发熔断的会话 ID
     * @param currentCount 当前的循环次数
     * @param threshold   熔断阈值
     */
    public CircuitBreakerOpenException(String message, String sessionId, int currentCount, int threshold) {
        super(message);
        this.sessionId = sessionId;
        this.currentCount = currentCount;
        this.threshold = threshold;
    }

    /**
     * 获取触发熔断的会话 ID
     *
     * @return 会话 ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 获取当前的循环次数
     *
     * @return 当前循环次数
     */
    public int getCurrentCount() {
        return currentCount;
    }

    /**
     * 获取熔断阈值
     *
     * @return 阈值
     */
    public int getThreshold() {
        return threshold;
    }

    @Override
    public String toString() {
        return String.format("CircuitBreakerOpenException: %s [sessionId=%s, count=%d, threshold=%d]",
                getMessage(), sessionId, currentCount, threshold);
    }
}
