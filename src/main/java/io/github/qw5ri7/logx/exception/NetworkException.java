package io.github.qw5ri7.logx.exception;

import java.io.Serial;

/**
 * 网络 API 请求异常（如获取版本清单、获取 Java 列表失败）
 */
public class NetworkException extends LauncherException {
    @Serial
    private static final long serialVersionUID = 1234567890123456789L; // 随便生成一个

    public NetworkException(String message, boolean isFatal) {
        super(message, isFatal);
    }

    public NetworkException(String message, Throwable cause, boolean isFatal) {
        super(message, cause, isFatal);
    }
}