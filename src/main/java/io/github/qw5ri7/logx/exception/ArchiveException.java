package io.github.qw5ri7.logx.exception;

import java.io.Serial;

public class ArchiveException extends LauncherException {
    @Serial
    private static final long serialVersionUID = -1717657693853039589L;

    public ArchiveException(String message, boolean isFatal) {
        super(message, isFatal);
    }
    public ArchiveException(String message, Throwable cause, boolean isFatal) {
        super(message, cause, isFatal);
    }
}
