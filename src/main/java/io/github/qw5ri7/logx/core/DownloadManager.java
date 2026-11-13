package io.github.qw5ri7.logx.core;

import io.github.qw5ri7.logx.exception.DownloadException;
import io.github.qw5ri7.logx.model.DownloadTask;
import io.github.qw5ri7.logx.net.HttpDownloader;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

/**
 * 下载管理器，负责协调下载任务的分配和执行
 */
@Slf4j
public class DownloadManager {

    private final DownloadProgressListener listener;
    private final ExecutorService smallExecutorService;
    private final ExecutorService largeExecutorService;

    // 统计变量
    @Getter
    private final AtomicLong downloadedBytes = new AtomicLong(0);
    private final AtomicInteger completedFiles = new AtomicInteger(0);
    private long totalBytes = 0;
    private int totalFiles = 0;

    // 速度计算相关
    private ScheduledExecutorService uiUpdater;
    private long lastDownloadedBytes = 0;
    /**
     * 构造函数，初始化下载管理器
     * @param listener 下载进度监听器
     */
    public DownloadManager(DownloadProgressListener listener) {
        this.listener = listener;
        this.smallExecutorService = Executors.newFixedThreadPool(15, r -> {
            Thread thread = new Thread(r);
            thread.setName("SmallDownloadExecutor");
            thread.setDaemon(true);
            return thread;
        });
        this.largeExecutorService = Executors.newFixedThreadPool(3, r -> {
            Thread thread = new Thread(r);
            thread.setName("LargeDownloadExecutor");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void shutdown() {
        if (uiUpdater != null && !uiUpdater.isShutdown()) {
            uiUpdater.shutdownNow();
        }
        if (!smallExecutorService.isShutdown()) smallExecutorService.shutdownNow();
        if (!largeExecutorService.isShutdown()) largeExecutorService.shutdownNow();
    }
    /**
     * 下载任务列表，会自动规划任务分配给不同的线程池
     * @param tasks 下载任务列表
     * @return 下载完成的 CompletableFuture
     */
    public CompletableFuture<Void> download(List<DownloadTask> tasks) {
        // 1. 过滤已下载的任务
        List<DownloadTask> pendingTasks = tasks.stream()
                .filter(task -> !task.isDownloaded())
                .toList();

        this.totalFiles = pendingTasks.size();

        // 适配：触发 onStart 回调
        if (listener != null) {
            listener.onStart(totalFiles);
        }

        if (totalFiles == 0) {
            if (listener != null) listener.onAllFinished(true);
            return CompletableFuture.completedFuture(null);
        }

        // 2. 统计总字节数（必须防判空，防止有些任务没给 fileSize）
        this.totalBytes = pendingTasks.stream()
                .filter(task -> task.getFileSize() != null)
                .mapToLong(DownloadTask::getFileSize)
                .sum();

        // 3. 启动 UI 刷新定时器（每秒算一次速度并通知 UI）
        startUiUpdater();

        // 4. 提交任务到线程池
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (DownloadTask task : pendingTasks) {

            // 根据大小决定交给哪个线程池
            boolean isLarge = isLargeFile(task);
            ExecutorService targetExecutor = isLarge ? largeExecutorService : smallExecutorService;

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    LongConsumer onBytesReceived = this.downloadedBytes::addAndGet;
                    if (isLarge) {
                        int chunkCount;
                        long size;
                        if (task.getFileSize() == null)  {
                            size = HttpDownloader.fetchFileSize(task.getUrl()).get(5, TimeUnit.SECONDS);
                        } else {
                            size = task.getFileSize();
                        }
                        if (size < 1024 * 1024 * 10) {
                            chunkCount = 4;
                        } else if (size < 1024 * 1024 * 100) {
                            chunkCount = 8;
                        } else {
                            chunkCount = 10;
                        }
                        HttpDownloader.downloadLargeFile(task, chunkCount, onBytesReceived).join();
                    } else {
                        HttpDownloader.downloadSingleFile(task, onBytesReceived).join();
                    }
                    completedFiles.incrementAndGet();
                } catch (Exception e) {
                    log.error("文件下载失败: {}", task.getUrl(), e);
                    String errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    // 适配：触发 onFileFailed 回调
                    if (listener != null) {
                        listener.onFileFailed(task, errorMsg);
                    }
                    throw new DownloadException("下载文件失败: " + errorMsg, e, false);
                }
            }, targetExecutor);

            futures.add(future);
        }

        // 5. 等待所有任务完成，清理现场
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((v, ex) -> {
                    shutdown(); // 内部会关闭定时器和线程池
                    if (listener != null) {
                        listener.onAllFinished(ex == null);
                    }
                });
    }

    private void startUiUpdater() {
        uiUpdater = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("Download-UI-Updater");
            t.setDaemon(true);
            return t;
        });

        uiUpdater.scheduleAtFixedRate(() -> {
            long currentBytes = downloadedBytes.get();
            long deltaBytes = currentBytes - lastDownloadedBytes;
            lastDownloadedBytes = currentBytes;

            if (listener != null) {
                listener.onProgress(
                        completedFiles.get(),
                        totalFiles,
                        currentBytes,
                        totalBytes,
                        deltaBytes // Bytes per second
                );
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private boolean isLargeFile(DownloadTask task) {
        return task.getFileSize() != null && task.getFileSize() > 5 * 1024 * 1024;
    }
}