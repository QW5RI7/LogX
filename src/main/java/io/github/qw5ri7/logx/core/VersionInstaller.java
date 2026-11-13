package io.github.qw5ri7.logx.core;

import io.github.qw5ri7.logx.exception.DownloadException;
import io.github.qw5ri7.logx.exception.NetworkException;
import io.github.qw5ri7.logx.model.*;
import io.github.qw5ri7.logx.net.HttpDownloader;
import io.github.qw5ri7.logx.net.VersionFetcher;
import io.github.qw5ri7.logx.util.RuleUtil;
import io.github.qw5ri7.logx.util.TaskBuilder;
import io.github.qw5ri7.logx.util.archive.ZipUtil;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class VersionInstaller {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static CompletableFuture<Void> installVersion(String version, String mcDir, DownloadProgressListener listener) {
        return VersionFetcher.fetchVersionManifest()
                .thenCompose(manifest -> {
                    if (manifest == null || manifest.getVersions() == null) {
                        throw new NetworkException("版本清单为空", true);
                    }
                    MCVersion targetVersion = manifest.getVersions()
                            .stream()
                            .filter(v -> v.getId().equals(version.trim()))
                            .findFirst()
                            .orElse(null);
                    if (targetVersion == null) {
                        throw new NetworkException("版本 " + version + " 不存在", true);
                    }
                    listener.onStatus(DownloadStatus.DOWNLOADING_JSON);
                    File versionJson = new File(mcDir, "versions/" + version + "/" + version + ".json");
                    if (versionJson.getParentFile() != null && !versionJson.getParentFile().exists()) {
                        versionJson.getParentFile().mkdirs();
                    }
                    DownloadTask downloadTask = TaskBuilder.createDownloadTask(targetVersion.getUrl(), versionJson.getAbsolutePath(), targetVersion.getSha1(),
                            0L, false);
                    return HttpDownloader.downloadSingleFile(downloadTask, null)
                            .thenApply(v -> {
                                log.info("版本 {} 的 JSON 文件下载完成，现在可以开始解析", version);
                                return objectMapper.readValue(versionJson, VersionJson.class);

                            });
                })
                .thenCompose(json -> {
                    log.info("版本 {} 的 JSON 解析完成，现在开始下载jar文件", version);
                    // 此时 json 已经解析完成，可以开始下载jar文件了
                    DownloadTask clientTask = TaskBuilder.buildClientDownloadTask(json, mcDir);
                    if (clientTask == null) {
                        log.error("版本 {} 没有客户端下载任务", version);
                        throw new DownloadException("版本 " + version + " 没有客户端下载任务", true);
                    }
                    listener.onStatus(DownloadStatus.DOWNLOADING_JAR);
                    DownloadTask loggingTask = TaskBuilder.buildLoggingTask(json, mcDir);
                    if (loggingTask == null) {
                        log.error("版本 {} 没有 Logging 配置文件下载任务", version);
                        throw new DownloadException("版本 " + version + " 没有 Logging 配置文件下载任务", true);
                    }
                    return new DownloadManager(listener).download(List.of(clientTask, loggingTask))
                            .thenApply(v -> json);
                })
                .thenCompose(json -> {
                    log.info("版本 {} 的 jar 文件下载完成，现在开始下载库文件", version);
                    List<DownloadTask> downloadTasks = TaskBuilder.buildLibrariesDownloadTasks(json, mcDir);
                    if (downloadTasks.isEmpty()) {
                        log.error("版本 {} 没有库下载任务", version);
                        throw new DownloadException("版本 " + version + " 没有库下载任务", true);
                    }
                    listener.onStatus(DownloadStatus.DOWNLOADING_LIBRARIES);
                    return new DownloadManager(listener).download(downloadTasks)
                            .thenApply(v -> json);
                })
                .thenCompose(json -> {
                    log.info("版本 {} 的库文件下载完成，现在开始下载依赖索引", version);
                    VersionJson.AssetIndex assetIndex = json.getAssetIndex();
                    if (assetIndex == null) {
                        log.error("版本 {} 没有依赖索引", version);
                        throw new DownloadException("版本 " + version + " 没有依赖索引", true);
                    }
                    listener.onStatus(DownloadStatus.DOWNLOADING_ASSETS);
                    DownloadTask downloadTask = TaskBuilder.createDownloadTask(assetIndex.getUrl(), new File(mcDir, "assets/indexes/" + assetIndex.getId() + ".json").getAbsolutePath(), assetIndex.getSha1(), assetIndex.getSize(), false);
                    return new DownloadManager(listener).download(List.of(downloadTask))
                            .thenApply(v -> json);
                })
                .thenCompose(json -> {
                    log.info("版本 {} 的依赖索引下载完成，现在开始下载资源", version);
                    VersionJson.AssetIndex assetIndex = json.getAssetIndex();
                    try {
                        String assetIndexJson = Files.readString(Paths.get(mcDir, "assets/indexes/" + assetIndex.getId() + ".json"));
                        AssetIndexJson indexJson = objectMapper.readValue(assetIndexJson, AssetIndexJson.class);
                        if (indexJson == null || indexJson.getObjects() == null) {
                            log.error("资源索引文件为空");
                            throw new DownloadException("资源索引文件为空", true);
                        }
                        List<DownloadTask> downloadTasks = TaskBuilder.buildAssetsDownloadTasks(indexJson, mcDir);
                        if (downloadTasks.isEmpty()) {
                            log.error("版本 {} 没有资源下载任务", version);
                            throw new DownloadException("版本 " + version + " 没有资源下载任务", true);
                        }
                        return new DownloadManager(listener).download(downloadTasks)
                                .thenApply(v -> json);
                    } catch (IOException e) {
                        throw new DownloadException("读取资源索引文件失败，文件可能已损坏，请尝试重新下载", e, true);
                    }
                })
                .thenAccept(json -> {
                    log.info("版本 {} 的 natives 开始提取", version);
                    listener.onStatus(DownloadStatus.EXTRACTING_NATIVES);
                    extractNatives(json, mcDir, version);
                    log.info("版本 {} 的 natives 提取完成", version);
                })
                .thenRun(() -> {
                    log.info("版本 {} 下载完成", version);
                    listener.onStatus(DownloadStatus.COMPLETED);
                })
                .exceptionally(e -> {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    log.error("安装版本 {} 失败", version, cause);
                    if (listener != null) {
                        listener.onFileFailed(null, cause.getMessage());
                    }
                    return null; // 这里 return null 是让 exceptionally 吸收异常，不再向上抛
                });
    }
    private static void extractNatives(VersionJson json, String mcDir, String versionId) {
        if (json.getLibraries() == null) return;

        File nativesDir = new File(mcDir, "versions/" + versionId + "/" + versionId + "-natives");
        if (!nativesDir.exists()) nativesDir.mkdirs();

        String currentOs = RuleUtil.getCurrentOs();
        String arch = System.getProperty("os.arch").contains("64") ? "64" : "32";
        for (VersionJson.Library lib : json.getLibraries()) {
            if (lib.getNatives() != null && !lib.getNatives().isEmpty()) {
                String classifierKey = lib.getNatives().get(currentOs);
                if (classifierKey == null) continue;
                classifierKey = classifierKey.replace("${arch}", arch);
                if (lib.getDownloads() != null && lib.getDownloads().getClassifiers() != null) {
                    VersionJson.DownloadInfo nativeInfo = lib.getDownloads().getClassifiers().get(classifierKey);
                    if (nativeInfo != null) {
                        // 找到本地下载好的 jar 路径
                        String localJarPath = new File(mcDir, "libraries/" + nativeInfo.getPath()).getAbsolutePath();
                        // 解压到 -natives 文件夹，并排除 META-INF
                        ZipUtil.extractNatives(localJarPath, nativesDir.getAbsolutePath(), List.of("META-INF/"));
                    }
                }
            }
        }
    }
}
