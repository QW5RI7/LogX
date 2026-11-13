package io.github.qw5ri7.logx.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

public enum DownloadSourceType {
    AUTO("auto"),
    MIRROR("mirror"),
    OFFICIAL("official");
    @Getter
    private final String value;
    DownloadSourceType(String value) {
        this.value = value;
    }
    @JsonCreator
    public static DownloadSourceType fromValue(String value) {
        if (value == null) return AUTO; // 兜底默认值
        for (DownloadSourceType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return AUTO;
    }
}
