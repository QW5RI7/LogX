package io.github.qw5ri7.logx.util;

import io.github.qw5ri7.logx.model.AssetIndexJson;
import io.github.qw5ri7.logx.model.DownloadTask;
import io.github.qw5ri7.logx.model.HashType;
import io.github.qw5ri7.logx.model.VersionJson;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TaskBuilder {
    /**
     * 构建客户端下载任务
     * @param json 版本 JSON 数据
     * @param mcDir Minecraft 目录
     * @return 下载任务
     */
    public static DownloadTask buildClientDownloadTask(VersionJson json, String mcDir) {
        if (json.getDownloads() == null || json.getDownloads().getClient() == null) {
            return null; // 极少数魔改端可能没有 client
        }
        VersionJson.DownloadInfo client = json.getDownloads().getClient();
        String savePath = mcDir + "/versions/" + json.getId() + "/" + json.getId() + ".jar";

        return createDownloadTask(client.getUrl(), savePath, client.getSha1(), client.getSize(), false);
    }
    /**
     * 构建库下载任务
     * @param json 版本 JSON 数据
     * @param mcDir Minecraft 目录
     * @return 下载任务列表
     */
    public static List<DownloadTask> buildLibrariesDownloadTasks(VersionJson json, String mcDir) {
        List<DownloadTask> tasks = new ArrayList<>();
        if (json == null || json.getLibraries() == null) {
            return List.of();
        }
        List<VersionJson.Library> downloadTasks = json.getLibraries();
        for (VersionJson.Library lib : downloadTasks) {
            if (!RuleUtil.isAllowed(lib.getRules())) {
                continue;
            }
            if (lib.getDownloads() != null && lib.getDownloads().getArtifact() != null) {
                VersionJson.DownloadInfo artifact = lib.getDownloads().getArtifact();
                String savePath = new File(mcDir, "libraries/" + artifact.getPath()).getAbsolutePath();
                tasks.add(createDownloadTask(artifact.getUrl(), savePath, artifact.getSha1(), artifact.getSize(), false));
            }
            if (lib.getNatives() != null && !lib.getNatives().isEmpty()) {
                String currentOs = RuleUtil.getCurrentOs();
                String arch = System.getProperty("os.arch").contains("64") ? "64" : "32";
                String classifierKey = lib.getNatives().get(currentOs);
                if (classifierKey != null) {
                    // 处理极个别老版本中的 ${arch} 变量
                    classifierKey = classifierKey.replace("${arch}", arch);

                    if (lib.getDownloads() != null && lib.getDownloads().getClassifiers() != null) {
                        VersionJson.DownloadInfo nativeInfo = lib.getDownloads().getClassifiers().get(classifierKey);
                        if (nativeInfo != null) {
                            String savePath = new File(mcDir, "libraries/" + nativeInfo.getPath()).getAbsolutePath();
                            // 🌟 注意这里 isNative 传 true
                            tasks.add(createDownloadTask(nativeInfo.getUrl(), savePath, nativeInfo.getSha1(), nativeInfo.getSize(), true));
                        }
                    }
                }
            }
        }
        return tasks;
    }
    public static List<DownloadTask> buildAssetsDownloadTasks(AssetIndexJson json, String mcDir) {
        List<DownloadTask> tasks = new ArrayList<>();
        if (json == null || json.getObjects() == null) {
            return tasks;
        }
        for (Map.Entry<String, AssetIndexJson.AssetObject> entry : json.getObjects().entrySet()) {
            String hash = entry.getValue().getHash();
            if (hash == null || hash.length() <= 2) {
                continue;
            }
            String url = "https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash;
            String savePath = new File(mcDir, "assets/objects/" + hash.substring(0, 2) + "/" + hash).getAbsolutePath();
            tasks.add(createDownloadTask(url, savePath, hash, entry.getValue().getSize(), false));
        }
        return tasks;
    }
    /**
     * 创建下载任务
     * @param url 下载 URL
     * @param savePath 保存路径
     * @param hash 哈希值
     * @param fileSize 文件大小
     * @param isNative 是否为本地文件
     * @return 下载任务
     */
    public static DownloadTask createDownloadTask(String url, String savePath, String hash, Long fileSize, Boolean isNative) {
        return DownloadTask.builder()
                .url(UrlRewriter.rewrite(url, NetworkSpeedTester.useMirror()))
                .savePath(savePath)
                .hash(hash)
                .hashType(HashType.SHA1)
                .fileSize(fileSize)
                .isNative(isNative)
                .build();
    }

    public static String buildClasspath(VersionJson json, String mcDir) {
        List<String> cpEntries = new ArrayList<>();
        if (json != null && json.getLibraries() != null) {
            for (VersionJson.Library lib : json.getLibraries()) {
                if (!RuleUtil.isAllowed(lib.getRules())) {
                    continue;
                }
                if (lib.getDownloads() != null && lib.getDownloads().getArtifact() != null) {
                    String path = lib.getDownloads().getArtifact().getPath();
                    cpEntries.add(new File(mcDir, "libraries/" + path).getAbsolutePath());
                }
            }
        }
        String clientJarPath = null;
        if (json != null) {
            clientJarPath = new File(mcDir, "versions/" + json.getId() + "/" + json.getId() + ".jar").getAbsolutePath();
        }
        cpEntries.add(clientJarPath);
        return String.join(File.pathSeparator, cpEntries);
    }
    /**
     * 构建 Logging 配置文件的下载任务 (通常是 log4j.xml)
     */
    public static DownloadTask buildLoggingTask(VersionJson json, String mcDir) {
        if (json.getLogging() == null || json.getLogging().getClient() == null || json.getLogging().getClient().getFile() == null) {
            return null;
        }

        VersionJson.LogFile logFile = json.getLogging().getClient().getFile();
        // 官方存放路径通常在 assets/log_configs/
        String savePath = new File(mcDir, "assets/log_configs/" + logFile.getId()).getAbsolutePath();

        return createDownloadTask(
                logFile.getUrl(),
                savePath,
                logFile.getSha1(),
                logFile.getSize(),
                false
        );
    }
}
