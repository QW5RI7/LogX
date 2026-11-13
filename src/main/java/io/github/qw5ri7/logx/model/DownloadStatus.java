package io.github.qw5ri7.logx.model;

import lombok.Getter;

@Getter
public enum DownloadStatus {
    DOWNLOADING_JSON("status.downloading_json"), // 下载版本 JSON 文件
    DOWNLOADING_JAR("status.downloading_jar"), // 下载版本 JAR 文件
    DOWNLOADING_LIBRARIES("status.downloading_libraries"), // 下载版本依赖
    DOWNLOADING_ASSETS("status.downloading_assets"), // 下载版本资源
    EXTRACTING_NATIVES("status.extracting_natives"), // 提取版本原生文件
    COMPLETED("status.completed"); // 下载完成

    private final String i18nKey;

    DownloadStatus(String i18nKey) {
        this.i18nKey = i18nKey;
    }
}