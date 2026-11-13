package io.github.qw5ri7.logx.net;

import io.github.qw5ri7.logx.BaseTest;
import io.github.qw5ri7.logx.model.DownloadTask;
import io.github.qw5ri7.logx.model.HashType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class HttpDownloaderTest extends BaseTest {

    @Test
    public void testDownloadFile() throws Exception {
        DownloadTask task = DownloadTask.builder()
                .url("https://cdn-fastly.obsproject.com/downloads/OBS-Studio-33.0.1-Windows-x64-Installer.exe")
                .savePath("test.exe")
                .hashType(HashType.SHA256)
                .isNative(false)
                .build();
        AtomicLong downloadedBytes = new AtomicLong(0);
        AtomicInteger count = new AtomicInteger(0);
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

        timer.scheduleAtFixedRate(new Runnable() {
            private long lastBytes = 0;

            @Override
            public void run() {
                long current = downloadedBytes.get();
                long delta = current - lastBytes;
                lastBytes = current;

                // 计算速度 (KB/s)
                double speedKb = delta / 1024.0;

                // 如果你想看下载到多少了，也可以顺便打印总进度
                // System.out.printf("当前进度: %.2f MB, 速度: %.2f KB/s%n", current / 1024.0 / 1024.0, speedKb);

                System.out.printf("下载速度: %.2f KB/s%n", speedKb);
            }
        }, 0, 1, TimeUnit.SECONDS);

        HttpDownloader.downloadLargeFile(task, 8, t -> {
            downloadedBytes.addAndGet(t);
            if (count.incrementAndGet() % 1000 == 0) {
                System.out.printf("已下载 %.2f MB%n", downloadedBytes.get() / 1024.0 / 1024.0);
            }
        }).join();
        timer.shutdown();
        // 4. 断言校验
        Assertions.assertTrue(task.isDownloaded(), "文件下载或 Hash 校验失败");
        System.out.println("测试通过，文件成功下载: " + task.getSavePath());
    }
}