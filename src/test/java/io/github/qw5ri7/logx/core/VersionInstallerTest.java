package io.github.qw5ri7.logx.core;

import io.github.qw5ri7.logx.BaseTest;
import io.github.qw5ri7.logx.model.DownloadStatus;
import io.github.qw5ri7.logx.model.DownloadTask;
import io.github.qw5ri7.logx.util.I18nUtil;
import org.junit.jupiter.api.Test;

public class VersionInstallerTest extends BaseTest {
    @Test
    public void testInstallVersion() {
        VersionInstaller.installVersion("1.20.4", "./.minecraft", new DownloadProgressListener() {
            @Override
            public void onStart(int totalTasks) {
                System.out.println("开始下载，当前批次总任务数：" + totalTasks);
            }

            @Override
            public void onProgress(int completed, int total, long downloadedBytes, long totalBytes, long speedBps) {
                double downloadedMB = downloadedBytes / 1024.0 / 1024.0;
                double totalMB = totalBytes / 1024.0 / 1024.0;
                double speedMBps = speedBps / 1024.0 / 1024.0;

                System.out.printf("进度: %d/%d | %.2f MB / %.2f MB | 速度: %.2f MB/s%n",
                        completed, total, downloadedMB, totalMB, speedMBps);
            }

            @Override
            public void onFileFailed(DownloadTask task, String errorMsg) {
                if (task == null) {
                    System.out.println("全局错误：" + errorMsg);
                } else {
                    System.out.println("下载文件：" + task.getSavePath() + " 失败：" + errorMsg);
                }
            }

            @Override
            public void onAllFinished(boolean isSuccess) {
                System.out.println("当前批次文件下载完成，是否成功：" + isSuccess);
            }

            @Override
            public void onStatus(DownloadStatus status) {
                System.out.println("当前下载状态：" + I18nUtil.get(status.getI18nKey()));
            }
        }).join();
        System.out.println("所有文件下载完成");
    }
}
