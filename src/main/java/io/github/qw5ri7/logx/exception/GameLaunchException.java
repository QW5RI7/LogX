package io.github.qw5ri7.logx.exception;

import java.io.Serial;

public class GameLaunchException extends LauncherException{
    @Serial
    private static final long serialVersionUID = -5495990294180361099L;

    public GameLaunchException(String message, Throwable cause, boolean isFatal) {
        super(message, cause, isFatal);
    }

    public GameLaunchException(String message, boolean isFatal) {
        super(message, isFatal);
    }
}
