package tech.yesboss.tool;

/**
 * 工具访问级别枚举
 *
 * <p>用于区分工具的访问权限，实现基于角色的访问控制（RBAC）。</p>
 *
 * <p><b>访问级别说明：</b></p>
 * <ul>
 *   <li><b>READ_ONLY</b>: 只读工具，不会修改系统状态。
 *       例如：读取文件、列出目录、搜索代码等。
 *       Master Agent 可以使用这些工具进行环境探索。</li>
 *
 *   <li><b>READ_WRITE</b>: 读写工具，会修改系统状态。
 *       例如：写入文件、执行命令、修改配置等。
 *       只有 Worker Agent 可以使用这些工具，并且受到严格沙箱管控。</li>
 * </ul>
 */
public enum ToolAccessLevel {
    /**
     * 只读工具 - 安全探索型工具，Master 可以使用
     */
    READ_ONLY,

    /**
     * 读写工具 - 会修改系统状态，仅 Worker 可以使用
     */
    READ_WRITE
}
