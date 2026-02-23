package tech.yesboss.context;

import tech.yesboss.domain.message.UnifiedMessage;

import java.util.List;

/**
 * 全局流管理器 (Global Stream Manager)
 *
 * <p>专供 Master 和人类交互使用，绝对隔离底层执行的脏数据。</p>
 *
 * <p><b>核心职责：</b></p>
 * <ul>
 *   <li>管理用户（人类）与 Master 的对话历史</li>
 *   <li>自动生成 sequence_num 并封装为 DbWriteEvent 丢给异步队列</li>
 *   <li>提供严格按 sequence_num 排序的上下文查询</li>
 *   <li>支持系统级消息插入（用于人机回环等场景）</li>
 * </ul>
 */
public interface GlobalStreamManager {

    /**
     * 追加用户（人类）发来的消息
     *
     * <p>内部会自动生成 sequence_num，并封装为 DbWriteEvent 丢给异步队列。</p>
     *
     * @param masterSessionId Master 的会话 ID
     * @param humanText 人类输入的文本
     */
    void appendHumanMessage(String masterSessionId, String humanText);

    /**
     * 追加 Master 的回复或规划思考
     *
     * @param masterSessionId Master 的会话 ID
     * @param masterMessage Master 产出的 UnifiedMessage
     */
    void appendMasterMessage(String masterSessionId, UnifiedMessage masterMessage);

    /**
     * 获取 Master 的完整上下文，用于下一次向大模型发起请求
     *
     * @param masterSessionId Master 的会话 ID
     * @return 严格按 sequence_num 排序的 UnifiedMessage 列表
     */
    List<UnifiedMessage> fetchContext(String masterSessionId);

    /**
     * 向 Master 的全局流插入系统生成的消息
     *
     * <p>用于人机回环、框架级通知等跨 Agent 伪造场景。</p>
     *
     * <p><b>核心使用场景：</b>
     * 当 Worker 触发黑名单拦截时，系统需要越过 Master 的 LLM，
     * 直接在 Master 的全局流中插入"系统提问"消息，然后通过 IM 推送给用户。</p>
     *
     * @param masterSessionId Master 的会话 ID
     * @param systemText 系统生成的文本内容
     */
    void appendSystemMessage(String masterSessionId, String systemText);
}
