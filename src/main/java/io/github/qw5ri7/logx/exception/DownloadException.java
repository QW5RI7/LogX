package io.github.qw5ri7.logx.exception;

import java.io.Serial;

public class DownloadException extends LauncherException {
    @Serial
    private static final long serialVersionUID = -7639077766280861376L;

    public DownloadException(String message, Throwable cause, boolean isFatal) {
        super(message, cause, isFatal);
    }

    public DownloadException(String message, boolean isFatal) {
        super(message, isFatal);
    }
}
