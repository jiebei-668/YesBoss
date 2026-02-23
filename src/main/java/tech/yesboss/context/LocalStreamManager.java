package tech.yesboss.context;

import tech.yesboss.domain.message.UnifiedMessage;

import java.util.List;

/**
 * 局部流管理器 (Local Stream Manager)
 *
 * <p>专供 Worker 底层试错使用，各 Worker 之间物理隔离，绝不交叉。</p>
 *
 * <p><b>核心职责：</b></p>
 * <ul>
 *   <li>管理 Worker 的执行思考和工具调用历史</li>
 *   <li>存储工具执行结果（包括错误和成功输出）</li>
 *   <li>提供严格按 sequence_num 排序的上下文查询</li>
 *   <li>维护 Token 消耗统计供压缩引擎使用</li>
 * </ul>
 */
public interface LocalStreamManager {

    /**
     * 追加 Worker 的执行思考或工具调用指令
     *
     * @param workerSessionId Worker 的会话 ID
     * @param workerMessage Worker 产出的 UnifiedMessage
     */
    void appendWorkerMessage(String workerSessionId, UnifiedMessage workerMessage);

    /**
     * 追加底层沙箱工具的执行结果
     *
     * <p>包括成功输出和报错信息。</p>
     *
     * @param workerSessionId Worker 的会话 ID
     * @param toolResult 系统执行完工具后返回的结果封装
     */
    void appendToolResult(String workerSessionId, UnifiedMessage toolResult);

    /**
     * 获取 Worker 的局部上下文
     *
     * @param workerSessionId Worker 的会话 ID
     * @return 严格按 sequence_num 排序的局部历史记录
     */
    List<UnifiedMessage> fetchContext(String workerSessionId);

    /**
     * 获取当前局部流的总 Token 消耗估算
     *
     * <p>供 CondensationEngine 的压缩探针使用。</p>
     *
     * @param workerSessionId Worker 的会话 ID
     * @return 当前未压缩消息的总 Token 数估算
     */
    int getCurrentTokenCount(String workerSessionId);
}
