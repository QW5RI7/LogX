package io.github.qw5ri7.logx.net;

import io.github.qw5ri7.logx.exception.NetworkException;
import io.github.qw5ri7.logx.model.VersionManifest;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Slf4j
public class VersionFetcher {
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL) // 配合镜像源必须加
            .build();

    private static final ObjectMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    // 建议以后把这里换成从配置读取，或者在前面加个 UrlRewriter 处理
    private static final String VERSION_URL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

    // 内存缓存
    private static VersionManifest versionManifest;

    /**
     * 异步获取版本清单（带内存缓存）
     * @param forceRefresh 是否强制刷新（忽略缓存）
     */
    public static CompletableFuture<VersionManifest> fetchVersionManifest(boolean forceRefresh) {
        // 1. 如果有缓存且不强制刷新，直接秒回
        if (versionManifest != null && !forceRefresh) {
            return CompletableFuture.completedFuture(versionManifest);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(VERSION_URL))
                .GET()
                .build();

        log.info("正在从网络获取版本清单...");

        // 2. 改成 sendAsync 纯异步发送
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            versionManifest = mapper.readValue(response.body(), VersionManifest.class);
                            log.info("版本清单获取成功，共 {} 个版本", versionManifest.getVersions().size());
                            return versionManifest;
                        } catch (Exception e) {
                            throw new NetworkException("解析 JSON 失败", e, false);
                        }
                    } else {
                        throw new NetworkException("获取清单失败，HTTP 状态码: " + response.statusCode(), false);
                    }
                })
                .exceptionally(ex -> {
                    log.error("获取版本清单发生异常", ex);
                    // 把底层异常包一层抛给调用方（让 UI 去弹窗）
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    throw new NetworkException("获取版本清单异常", cause, false);
                });
    }

    /**
     * 重载方法：默认使用缓存
     */
    public static CompletableFuture<VersionManifest> fetchVersionManifest() {
        return fetchVersionManifest(false);
    }

    /**
     * 清空缓存（用于玩家点击“刷新列表”按钮）
     */
    public static void clearCache() {
        versionManifest = null;
    }
}