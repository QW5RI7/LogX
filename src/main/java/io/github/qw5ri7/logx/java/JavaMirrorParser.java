package io.github.qw5ri7.logx.java;

import io.github.qw5ri7.logx.exception.DownloadException;
import io.github.qw5ri7.logx.model.DownloadTask;
import io.github.qw5ri7.logx.model.HashType;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Locale;
@Slf4j
public class JavaMirrorParser {
    private static final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ObjectMapper mapper = new ObjectMapper();
    public static DownloadTask getLatestJavaDownloadTask(int majorVersion) throws IOException, InterruptedException {
        String os = getAdoptiumOs();
        String arch = getAdoptiumArch();
        String apiUrl = String.format("https://api.adoptium.net/v3/assets/latest/%d/hotspot?architecture=%s&image_type=jre&os=%s&vendor=eclipse",
                majorVersion, arch, os);

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(apiUrl)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new DownloadException("获取 Java 元数据失败，状态码: " + response.statusCode(), true);
        }
        JsonNode rootArray = mapper.readTree(response.body());
        if (rootArray == null || rootArray.isEmpty()) {
            throw new DownloadException("未找到适用于当前系统的 Java " + majorVersion + " 下载", true);
        }

        JsonNode packageNode = rootArray.get(0).get("binary").get("package");
        String fileName = packageNode.get("name").asString(); // 例如 OpenJDK17U-jre_x64_windows_hotspot_17.0.21_9.zip
        String checksum = packageNode.get("checksum").asString();
        long fileSize = packageNode.get("size").asLong();
        String tunaUrl = String.format("https://mirrors.tuna.tsinghua.edu.cn/Adoptium/%d/jre/%s/%s/%s",
                majorVersion, arch, os, fileName);

        log.info("已获取适用于当前系统的 Java {} 信息，将从清华镜像下载: {}", majorVersion, fileName);

        // 5. 构造并返回 DownloadTask
        return DownloadTask.builder()
                .url(tunaUrl)
                .savePath(Files.createTempFile("logx-jre-", ".zip").toString())
                .hash(checksum)
                .hashType(HashType.SHA256)
                .fileSize(fileSize)
                .isNative(false)
                .build();
    }
    /**
     * 将 Java 的 os.name 映射为 Adoptium 的 OS 名称
     */
    private static String getAdoptiumOs() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return "windows";
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            return "mac";
        } else {
            return "linux";
        }
    }

    /**
     * 将 Java 的 os.arch 映射为 Adoptium 的 Architecture 名称
     */
    private static String getAdoptiumArch() {
        String osArch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            return "aarch64";
        } else {
            return "x64"; // 大多数情况
        }
    }
}
