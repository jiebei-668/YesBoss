package tech.yesboss.context;

/**
 * 记忆流类型枚举
 *
 * <p>对应数据库 `chat_message` 表中的 `stream_type` 字段。</p>
 *
 * <p><b>流类型说明：</b></p>
 * <ul>
 *   <li><b>GLOBAL</b>: 全局流 - 仅存储人类对话与 Master 的高维抽象，
 *       用于保持跨 Agent 的全局上下文一致性。</li>
 *
 *   <li><b>LOCAL</b>: 局部流 - 存储底层代码与 Worker 的工具试错噪音，
 *       各 Worker 之间物理隔离，绝不交叉。</li>
 * </ul>
 */
public enum StreamType {
    /**
     * 全局流：仅存人类对话与 Master 的高维抽象
     */
    GLOBAL,

    /**
     * 局部流：存满底层代码与 Worker 的工具试错噪音
     */
    LOCAL
}
