package io.github.qw5ri7.logx.util;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class NetworkSpeedTester {

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)) // 3秒连不上直接算超时
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static Boolean useMirror;

    /**
     * 测试单个 URL 的响应延迟
     * @return 耗时（毫秒），如果超时或失败返回 Long.MAX_VALUE
     */
    public static long testLatency(String url) {
        long startTime = System.currentTimeMillis();
        try {
            // 发送一个 HEAD 请求，不下载文件内容，只拿响应头，极快
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();

            client.send(request, HttpResponse.BodyHandlers.discarding());
            return System.currentTimeMillis() - startTime;
        } catch (Exception e) {
            return Long.MAX_VALUE; // 失败了就返回最大值
        }
    }

    /**
     * 判断用官方源还是镜像源
     */
    public static boolean useMirror() {
        if (useMirror != null) {
            return useMirror;
        }
        // 拿两个体积极小、且是真实下载服务器的文件来测
        String officialTestUrl = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
        String mirrorTestUrl = "https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json";

        System.out.println("正在测试网络环境...");

        // 并发测试，不要一个一个等，免得启动器卡死
        CompletableFuture<Long> officialTask = CompletableFuture.supplyAsync(() -> testLatency(officialTestUrl));
        CompletableFuture<Long> mirrorTask = CompletableFuture.supplyAsync(() -> testLatency(mirrorTestUrl));

        long officialTime = officialTask.join();
        long mirrorTime = mirrorTask.join();

        log.info("官方源延迟: {} ms | 镜像源延迟: {} ms", officialTime, mirrorTime);

        // 逻辑：如果官方超时（被墙），或者官方比镜像慢太多，就选镜像
        if (officialTime > 1500) {
            return true;
        }
        if (mirrorTime == Long.MAX_VALUE) {
            return false;
        }
        // 缓存结果
        useMirror = officialTime > mirrorTime;
        return useMirror;
    }
}