package io.github.qw5ri7.logx.model;

import lombok.Getter;

@Getter
public enum HashType {
    SHA1("SHA-1"),
    MD5("MD5"),
    SHA256("SHA-256");

    private final String algorithm; // 建议改名为 algorithm

    HashType(String algorithm) {
        this.algorithm = algorithm;
    }
}
