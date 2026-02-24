package tech.yesboss.state.model;

/**
 * IM 路由信息对象 (IM Route Information Object)
 *
 * <p>用于封装会话与外部 IM 系统的绑定关系。</p>
 *
 * <p><b>核心职责：</b></p>
 * <ul>
 *   <li>存储 IM 平台类型和群组 ID</li>
 *   <li>用于人机回环流程中的消息推送</li>
 *   <li>由 SuspendResumeEngine 使用，通过 Master SessionId 获取路由信息</li>
 * </ul>
 *
 * @param imType    IM 平台类型 (FEISHU, SLACK, CLI)
 * @param imGroupId 群聊 ID
 */
public record ImRoute(String imType, String imGroupId) {

    /**
     * 创建 IM 路由信息
     *
     * @param imType    IM 平台类型
     * @param imGroupId 群聊 ID
     * @throws IllegalArgumentException if imType or imGroupId is null/empty
     */
    public ImRoute {
        if (imType == null || imType.trim().isEmpty()) {
            throw new IllegalArgumentException("imType cannot be null or empty");
        }
        if (imGroupId == null || imGroupId.trim().isEmpty()) {
            throw new IllegalArgumentException("imGroupId cannot be null or empty");
        }
    }
}
