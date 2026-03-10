package tech.yesboss.tool.filesystem.exception;

/**
 * 文件操作异常
 *
 * <p>当文件操作执行过程中发生错误时抛出此异常。</p>
 *
 * <p><b>与 FileSecurityException 的区别：</b></p>
 * <ul>
 *   <li><b>FileSecurityException</b>: 操作被安全策略拒绝（预先阻止）</li>
 *   <li><b>FileOperationException</b>: 操作执行过程中发生的错误（运行时错误）</li>
 * </ul>
 *
 * <p><b>典型使用场景：</b></p>
 * <ul>
 *   <li>文件不存在</li>
 *   <li>I/O 错误</li>
 *   <li>磁盘空间不足</li>
 *   <li>文件已被占用</li>
 *   <li>编码问题</li>
 * </ul>
 */
public class FileOperationException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String operation;
    private final String path;
    private final ErrorType errorType;

    /**
     * 错误类型枚举
     */
    public enum ErrorType {
        /**
         * 文件不存在
         */
        FILE_NOT_FOUND,

        /**
         * 目录不存在
         */
        DIRECTORY_NOT_FOUND,

        /**
         * 文件已存在
         */
        FILE_ALREADY_EXISTS,

        /**
         * I/O 错误
         */
        IO_ERROR,

        /**
         * 磁盘空间不足
         */
        INSUFFICIENT_DISK_SPACE,

        /**
         * 权限被拒绝
         */
        ACCESS_DENIED,

        /**
         * 文件已被占用
         */
        FILE_LOCKED,

        /**
         * 目录不为空
         */
        DIRECTORY_NOT_EMPTY,

        /**
         * 路径无效
         */
        INVALID_PATH,

        /**
         * 编码错误
         */
        ENCODING_ERROR,

        /**
         * 超时
         */
        TIMEOUT,

        /**
         * 未知错误
         */
        UNKNOWN_ERROR
    }

    /**
     * 创建一个新的 FileOperationException
     *
     * @param operation 失败的操作类型
     * @param path 目标路径
     * @param errorType 错误类型
     * @param cause 原始异常（可为 null）
     */
    public FileOperationException(String operation, String path, ErrorType errorType, Throwable cause) {
        super(String.format("File operation '%s' failed on path '%s': %s%s",
                operation,
                path,
                errorType,
                cause != null ? " (" + cause.getMessage() + ")" : ""),
                cause);
        this.operation = operation;
        this.path = path;
        this.errorType = errorType;
    }

    /**
     * 获取失败的操作类型
     *
     * @return 操作类型
     */
    public String getOperation() {
        return operation;
    }

    /**
     * 获取目标路径
     *
     * @return 路径
     */
    public String getPath() {
        return path;
    }

    /**
     * 获取错误类型
     *
     * @return 错误类型
     */
    public ErrorType getErrorType() {
        return errorType;
    }

    /**
     * 获取原始异常
     *
     * @return 原始异常（可为 null）
     */
    public Throwable getCause() {
        return super.getCause();
    }

    /**
     * 创建文件不存在异常
     *
     * @param operation 操作类型
     * @param path 文件路径
     * @return FileOperationException 对象
     */
    public static FileOperationException fileNotFound(String operation, String path) {
        return new FileOperationException(operation, path, ErrorType.FILE_NOT_FOUND, null);
    }

    /**
     * 创建目录不存在异常
     *
     * @param operation 操作类型
     * @param path 目录路径
     * @return FileOperationException 对象
     */
    public static FileOperationException directoryNotFound(String operation, String path) {
        return new FileOperationException(operation, path, ErrorType.DIRECTORY_NOT_FOUND, null);
    }

    /**
     * 创建文件已存在异常
     *
     * @param operation 操作类型
     * @param path 文件路径
     * @return FileOperationException 对象
     */
    public static FileOperationException fileAlreadyExists(String operation, String path) {
        return new FileOperationException(operation, path, ErrorType.FILE_ALREADY_EXISTS, null);
    }

    /**
     * 创建 I/O 错误异常
     *
     * @param operation 操作类型
     * @param path 路径
     * @param cause 原始异常
     * @return FileOperationException 对象
     */
    public static FileOperationException ioError(String operation, String path, Throwable cause) {
        return new FileOperationException(operation, path, ErrorType.IO_ERROR, cause);
    }

    /**
     * 创建磁盘空间不足异常
     *
     * @param operation 操作类型
     * @param path 路径
     * @return FileOperationException 对象
     */
    public static FileOperationException insufficientDiskSpace(String operation, String path) {
        return new FileOperationException(operation, path, ErrorType.INSUFFICIENT_DISK_SPACE, null);
    }

    /**
     * 创建访问被拒绝异常
     *
     * @param operation 操作类型
     * @param path 路径
     * @return FileOperationException 对象
     */
    public static FileOperationException accessDenied(String operation, String path) {
        return new FileOperationException(operation, path, ErrorType.ACCESS_DENIED, null);
    }

    /**
     * 创建文件被锁定异常
     *
     * @param operation 操作类型
     * @param path 路径
     * @return FileOperationException 对象
     */
    public static FileOperationException fileLocked(String operation, String path) {
        return new FileOperationException(operation, path, ErrorType.FILE_LOCKED, null);
    }

    /**
     * 创建目录不为空异常
     *
     * @param operation 操作类型
     * @param path 路径
     * @return FileOperationException 对象
     */
    public static FileOperationException directoryNotEmpty(String operation, String path) {
        return new FileOperationException(operation, path, ErrorType.DIRECTORY_NOT_EMPTY, null);
    }

    /**
     * 创建无效路径异常
     *
     * @param operation 操作类型
     * @param path 路径
     * @return FileOperationException 对象
     */
    public static FileOperationException invalidPath(String operation, String path) {
        return new FileOperationException(operation, path, ErrorType.INVALID_PATH, null);
    }

    /**
     * 创建编码错误异常
     *
     * @param operation 操作类型
     * @param path 路径
     * @param cause 原始异常
     * @return FileOperationException 对象
     */
    public static FileOperationException encodingError(String operation, String path, Throwable cause) {
        return new FileOperationException(operation, path, ErrorType.ENCODING_ERROR, cause);
    }

    /**
     * 创建超时异常
     *
     * @param operation 操作类型
     * @param path 路径
     * @return FileOperationException 对象
     */
    public static FileOperationException timeout(String operation, String path) {
        return new FileOperationException(operation, path, ErrorType.TIMEOUT, null);
    }

    /**
     * 创建未知错误异常
     *
     * @param operation 操作类型
     * @param path 路径
     * @param cause 原始异常
     * @return FileOperationException 对象
     */
    public static FileOperationException unknownError(String operation, String path, Throwable cause) {
        return new FileOperationException(operation, path, ErrorType.UNKNOWN_ERROR, cause);
    }

    /**
     * 判断错误是否可重试
     *
     * <p>某些错误是暂时性的，可以通过重试来解决。</p>
     *
     * @return 如果错误可重试返回 true
     */
    public boolean isRetryable() {
        return errorType == ErrorType.IO_ERROR ||
               errorType == ErrorType.FILE_LOCKED ||
               errorType == ErrorType.TIMEOUT ||
               errorType == ErrorType.INSUFFICIENT_DISK_SPACE;
    }

    /**
     * 获取用户友好的错误消息
     *
     * @return 用户友好的错误消息
     */
    public String getUserFriendlyMessage() {
        return switch (errorType) {
            case FILE_NOT_FOUND ->
                String.format("文件 '%s' 不存在", path);
            case DIRECTORY_NOT_FOUND ->
                String.format("目录 '%s' 不存在", path);
            case FILE_ALREADY_EXISTS ->
                String.format("文件 '%s' 已存在", path);
            case IO_ERROR ->
                String.format("读写 '%s' 时发生 I/O 错误", path);
            case INSUFFICIENT_DISK_SPACE ->
                "磁盘空间不足，无法完成操作";
            case ACCESS_DENIED ->
                String.format("访问 '%s' 被拒绝，权限不足", path);
            case FILE_LOCKED ->
                String.format("文件 '%s' 已被其他程序占用", path);
            case DIRECTORY_NOT_EMPTY ->
                String.format("目录 '%s' 不为空，无法删除", path);
            case INVALID_PATH ->
                String.format("路径 '%s' 无效", path);
            case ENCODING_ERROR ->
                String.format("文件 '%s' 编码解析失败", path);
            case TIMEOUT ->
                String.format("操作 '%s' 超时", operation);
            case UNKNOWN_ERROR ->
                String.format("操作 '%s' 失败：未知错误", operation);
        };
    }

    /**
     * 获取建议的解决方案
     *
     * @return 建议的解决方案（可为 null）
     */
    public String getSuggestedSolution() {
        return switch (errorType) {
            case FILE_NOT_FOUND, DIRECTORY_NOT_FOUND ->
                "请检查路径是否正确，或使用 LIST 操作查看目录内容";
            case FILE_ALREADY_EXISTS ->
                "如需覆盖现有文件，请先删除原文件";
            case IO_ERROR ->
                "请检查磁盘状态和文件系统完整性";
            case INSUFFICIENT_DISK_SPACE ->
                "请清理磁盘空间后重试";
            case ACCESS_DENIED ->
                "请检查文件权限设置";
            case FILE_LOCKED ->
                "请关闭使用该文件的程序后重试";
            case DIRECTORY_NOT_EMPTY ->
                "如需删除非空目录，请使用递归删除参数";
            case INVALID_PATH ->
                "请检查路径格式是否正确";
            case ENCODING_ERROR ->
                "请尝试指定正确的文件编码";
            case TIMEOUT ->
                "请稍后重试，或检查系统性能";
            case UNKNOWN_ERROR ->
                "请查看详细错误日志以获取更多信息";
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FileOperationException{");
        sb.append("operation='").append(operation).append('\'');
        sb.append(", path='").append(path).append('\'');
        sb.append(", errorType=").append(errorType);
        if (getCause() != null) {
            sb.append(", cause=").append(getCause().getClass().getSimpleName());
        }
        sb.append('}');
        return sb.toString();
    }
}
