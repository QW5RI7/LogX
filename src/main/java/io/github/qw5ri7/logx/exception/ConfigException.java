package io.github.qw5ri7.logx.exception;

import java.io.Serial;

public class ConfigException extends LauncherException{
    @Serial
    private static final long serialVersionUID = -4265301160873503520L;

    public ConfigException(String message, Throwable cause, boolean isFatal) {
        super(message, cause, isFatal);
    }
    public ConfigException(String message, boolean isFatal) {
        super(message, isFatal);
    }
}
