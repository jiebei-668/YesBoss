package tech.yesboss.tool.filesystem.security;

/**
 * 用户决策结果
 *
 * <p>封装了用户对路径访问请求的决策信息。</p>
 *
 * @param decisionType 决策类型
 * @param path 请求访问的路径
 * @param rationale 决策理由
 * @param timestamp 决策时间戳
 */
record WhitelistDecision(
    DynamicWhitelistManager.DecisionType decisionType,
    String path,
    String rationale,
    long timestamp
) {
    /**
     * 判断决策是否允许访问
     *
     * @return true 如果决策类型不是 DENY
     */
    public boolean isAllowed() {
        return decisionType != DynamicWhitelistManager.DecisionType.DENY;
    }
}
