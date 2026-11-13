package io.github.qw5ri7.logx.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

public enum VersionType {
    RELEASE("release", "正式版"),
    SNAPSHOT("snapshot", "快照版"),

    // 合并后的常量
    LEGACY("legacy", "远古版本");

    private final String value;
    @Getter
    private final String displayName;

    VersionType(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static VersionType fromValue(String value) {
        if (value == null) return null;

        // 在这里将多个官方字符串映射到同一个枚举常量
        return switch (value.toLowerCase()) {
            case "release" -> RELEASE;
            case "snapshot" -> SNAPSHOT;
            case "old_alpha", "old_beta" -> LEGACY;
            default -> null;// 如果官方出了新类型（比如 "new_type"），默认当作快照版或直接返回 null
        };
    }
}