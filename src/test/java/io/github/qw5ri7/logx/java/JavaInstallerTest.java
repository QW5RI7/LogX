package io.github.qw5ri7.logx.java;

import io.github.qw5ri7.logx.Launcher;
import io.github.qw5ri7.logx.core.DownloadProgressListener;
import io.github.qw5ri7.logx.model.DownloadStatus;
import io.github.qw5ri7.logx.model.DownloadTask;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;

public class JavaInstallerTest {
    @Test
    public void testInstallJava() {
        JavaInstaller.installJava(25, new DownloadProgressListener() {
            @Override
            public void onStart(int totalTasks) {
                System.out.println("开始下载Java，总任务数：" + totalTasks);
            }

            @Override
            public void onProgress(int completed, int total, long downloadedBytes, long totalBytes, long speedBps) {
                double progress = (((double) completed) / total) * 100;
                double sppdMBps = speedBps / 1024.0 / 1024.0;
                System.out.printf("已下载 %.2f MB/%.2f MB，下载进度 %.2f%%，下载速度 %.2f MB/s%n", downloadedBytes / 1024.0 / 1024.0, totalBytes / 1024.0 / 1024.0, progress, sppdMBps);
            }

            @Override
            public void onFileFailed(DownloadTask task, String errorMsg) {
                System.out.println("下载文件失败：" + errorMsg);
            }

            @Override
            public void onAllFinished(boolean isSuccess) {
                System.out.println("所有文件下载完成：" + isSuccess);
            }

            @Override
            public void onStatus(DownloadStatus status) {
                System.out.println("下载状态：" + status);
            }
        }).join();
        System.out.println("Java下载完成");
        Assertions.assertTrue(new File(Launcher.APP_DIR, "runtime/java-21").exists());
    }
}
