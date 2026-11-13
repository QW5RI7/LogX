package io.github.qw5ri7.logx.exception;

import lombok.Getter;

import java.io.Serial;

public class LauncherException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = -7873731726803996389L;
    @Getter
    private String message;
    @Getter
    private boolean isFatal = false;
    public LauncherException(String message, Throwable cause, boolean isFatal) {
        super(message, cause);
        this.message = message;
        this.isFatal = isFatal;
    }
    public LauncherException(String message, boolean isFatal) {
        super(message);
        this.message = message;
        this.isFatal = isFatal;
    }

}
