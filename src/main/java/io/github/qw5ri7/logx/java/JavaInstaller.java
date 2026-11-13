package io.github.qw5ri7.logx.java;

import io.github.qw5ri7.logx.core.DownloadManager;
import io.github.qw5ri7.logx.core.DownloadProgressListener;
import io.github.qw5ri7.logx.exception.DownloadException;
import io.github.qw5ri7.logx.exception.LauncherException;
import io.github.qw5ri7.logx.model.DownloadTask;
import io.github.qw5ri7.logx.util.archive.ZipUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static io.github.qw5ri7.logx.Launcher.APP_DIR;

@Slf4j
public class JavaInstaller {
    public static CompletableFuture<Void> installJava(int majorVersion, DownloadProgressListener listener)  {
        majorVersion = getFallbackMajorVersion(majorVersion);
        try {
            DownloadTask downloadTask = JavaMirrorParser.getLatestJavaDownloadTask(majorVersion);
            int finalMajorVersion = majorVersion;
            return new DownloadManager(listener).download(List.of(downloadTask))
                    .thenRun(() -> {
                        try {
                            File targetRuntimeDir = new File(APP_DIR, "runtime/java-" + finalMajorVersion);
                            log.info("开始解压 Java {} 到 {}", finalMajorVersion, targetRuntimeDir.getAbsolutePath());
                            if (!targetRuntimeDir.exists() && !targetRuntimeDir.mkdirs()) {
                                throw new LauncherException("无法创建 Java 解压目录", true);
                            }
                            ZipUtil.unzip(downloadTask.getSavePath(), targetRuntimeDir.getAbsolutePath());
                            unwrapSingleDirectory(targetRuntimeDir);
                        } catch (Exception e) {
                            log.error("解压 Java {} 失败", finalMajorVersion, e);
                            throw new CompletionException(e);
                        } finally {
                            try {
                                Path path = Paths.get(downloadTask.getSavePath());
                                Files.deleteIfExists(path);
                                log.info("临时压缩包已清理");
                            } catch (Exception e) {
                                log.warn("清理 Java 临时压缩包失败（不影响使用）: {}", e.getMessage());
                            }
                        }

                    })
                    .exceptionally(ex -> {
                        // 统一捕获整个异步链的异常
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        log.error("安装 Java {} 失败", finalMajorVersion, cause);
                        throw new CompletionException(cause);
                    });
        } catch (Exception e) {
            log.error("安装 Java {} 失败", majorVersion, e);
            return CompletableFuture.failedFuture(new DownloadException("安装 Java " + majorVersion + " 失败", e, true));
        }

    }
    private static int getFallbackMajorVersion(int requiredVersion) {
        int[] supportedLtsVersions = {8, 11, 17, 21, 25};
        for (int lts : supportedLtsVersions) {
            if (requiredVersion <= lts) {
                return lts;
            }
        }
        return requiredVersion;
    }
    private static void unwrapSingleDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null && files.length == 1 && files[0].isDirectory()) {
            File innerDir = files[0];
            File[] innerFiles = innerDir.listFiles();
            if (innerFiles != null) {
                for (File f : innerFiles) {
                    // 将里面的文件移动到外层
                    if (!f.renameTo(new File(dir, f.getName()))) {
                        log.warn("移动 Java 内部文件失败: {}", f.getName());
                    }
                }
                innerDir.delete(); // 删掉空壳文件夹
            }
        }
    }
}
