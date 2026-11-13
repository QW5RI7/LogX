package io.github.qw5ri7.logx.net;

import io.github.qw5ri7.logx.exception.DownloadException;
import io.github.qw5ri7.logx.model.DownloadTask;
import io.github.qw5ri7.logx.util.HashUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongConsumer;

@Slf4j
public class HttpDownloader {

    private static final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * 对外暴露的下载单文件方法，默认失败重试 3 次
     */
    public static CompletableFuture<Void> downloadSingleFile(DownloadTask task, LongConsumer onBytesReceived) {
        return downloadSingleFileWithRetry(task, onBytesReceived, 3);
    }

    /**
     * 带有重试逻辑的实际下载方法
     */
    private static CompletableFuture<Void> downloadSingleFileWithRetry(DownloadTask task, LongConsumer onBytesReceived, int retriesLeft) {
        // 调用真正的下载逻辑
        return doDownloadSingleFile(task, onBytesReceived)
                .exceptionallyCompose(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    String errorMsg = cause.getMessage() != null ? cause.getMessage() : "";

                    // 判断是否属于可以重试的网络错误
                    // 如果是 404 (找不到文件) 或 403 (禁止访问)，重试也没用，直接放弃
                    boolean isFatalError = errorMsg.contains("404") || errorMsg.contains("403") || errorMsg.contains("401");

                    if (retriesLeft > 0 && !isFatalError) {
                        log.warn("下载遇到异常 [{}]，将在 0.8 秒后进行第 {} 次重试: {}",
                                errorMsg, (4 - retriesLeft), task.getSavePath());

                        // 延迟 1.5 秒后，递归调用自己（重试次数 -1）
                        return CompletableFuture.runAsync(() -> {
                            try {
                                Thread.sleep(800); // 给服务器喘息时间，也避开瞬时网络抖动
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }).thenCompose(v -> downloadSingleFileWithRetry(task, onBytesReceived, retriesLeft - 1));
                    }

                    // 如果次数耗尽，或者遇到了致命错误，直接抛出最终异常
                    log.error("文件最终下载失败，放弃重试: {}", task.getSavePath());
                    throw new CompletionException(new DownloadException("单线程下载最终失败: " + errorMsg, cause, false));
                });
    }

    /**
     * 单线程基础下载方法（纯异步版）
     * @param task 下载任务
     * @param onBytesReceived 字节接收回调，用于实时更新进度（每次读 8KB 调一次）
     */
    private static CompletableFuture<Void> doDownloadSingleFile(DownloadTask task, LongConsumer onBytesReceived) {
        if (task.isDownloaded()) {
            log.debug("文件已下载，跳过: {}", task.getSavePath());
            // 如果文件已存在，直接给回调塞满它的总大小，保证进度条正常
            if (onBytesReceived != null && task.getFileSize() != null) {
                onBytesReceived.accept(task.getFileSize());
            }
            return CompletableFuture.completedFuture(null);
        }

        File targetFile = new File(task.getSavePath());
        File tmpFile = new File(task.getSavePath() + ".tmp");

        try {
            prepareDirectory(targetFile);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(new DownloadException("无法创建目录", e, false));
        }

        log.debug("开始异步单线程下载: {}", targetFile.getName());
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(task.getUrl())).GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        throw new CompletionException(new DownloadException("下载失败，状态码: " + response.statusCode(), false));
                    }

                    long fileSize = task.getFileSize() != null ? task.getFileSize() : 0;

                    if (fileSize > 0 && fileSize < 1024 * 1024) {
                        // ================= 车道 1：小文件（全内存） =================
                        try (InputStream in = response.body();
                             java.io.ByteArrayOutputStream memoryStream = new java.io.ByteArrayOutputStream()) {
                            byte[] buffer = new byte[65536];
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                memoryStream.write(buffer, 0, read);
                                if (onBytesReceived != null) onBytesReceived.accept(read);
                            }
                            if (!HashUtil.verifyBytesHash(memoryStream.toByteArray(), task.getHash(), task.getHashType())) {
                                throw new DownloadException("小文件内存哈希值校验失败", false);
                            }
                            // 直接写正式文件
                            Files.write(targetFile.toPath(), memoryStream.toByteArray());
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }

                    } else {
                        // ================= 车道 2：大文件（落盘 + 校验） =================
                        try (InputStream in = response.body();
                             java.io.OutputStream out = Files.newOutputStream(tmpFile.toPath())) {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                                if (onBytesReceived != null) onBytesReceived.accept(read);
                            }
                            verifyAndFinalize(task, tmpFile, targetFile);
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    }
                })
                // 2. 统一的异常兜底与清理
                .exceptionally(ex -> {
                    cleanupTempFile(tmpFile); // 无论什么情况，清理掉可能残留的 .tmp
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    throw new CompletionException(new DownloadException("单线程下载异常: " + cause.getMessage(), cause, false));
                });
    }
    /**
     * 单线程基础下载方法（纯异步版）
     * @param task 下载任务
     */
    public static CompletableFuture<Void> downloadSingleFile(DownloadTask task) {
        return downloadSingleFile(task, null);
    }

    /**
     * 分块（Range）大文件下载方法（纯异步版）
     * @param task 下载任务
     * @param chunkCount 切块数量
     * @param onBytesReceived 字节接收回调
     */
    public static CompletableFuture<Void> downloadLargeFile(DownloadTask task, int chunkCount, LongConsumer onBytesReceived) {
        if (chunkCount <= 1) {
            return downloadSingleFile(task, onBytesReceived);
        }

        if (task.isDownloaded()) {
            log.debug("文件已下载，跳过: {}", task.getSavePath());
            if (onBytesReceived != null && task.getFileSize() != null) {
                onBytesReceived.accept(task.getFileSize());
            }
            return CompletableFuture.completedFuture(null);
        }

        File targetFile = new File(task.getSavePath());
        File tmpFile = new File(task.getSavePath() + ".tmp");
        Long totalSize = task.getFileSize();

        if (totalSize == null || totalSize <= 0) {
            log.info("未知文件大小，尝试自动获取: {}", task.getUrl());
            return fetchFileSize(task.getUrl())
                    .thenCompose(fetchedSize -> {
                        if (fetchedSize != null && fetchedSize > 0) {
                            log.debug("成功获取文件大小: {} bytes", fetchedSize);
                            task.setFileSize(fetchedSize); // 动态填入大小
                            // 递归调用自己，此时 totalSize 已经就绪，会直接走分块逻辑
                            return downloadLargeFile(task, chunkCount, onBytesReceived);
                        } else {
                            log.warn("无法获取文件大小，降级为单线程下载: {}", new File(task.getSavePath()).getName());
                            return downloadSingleFile(task, onBytesReceived);
                        }
                    });
        }

        try {
            prepareDirectory(targetFile);
            try (RandomAccessFile raf = new RandomAccessFile(tmpFile, "rw")) {
                raf.setLength(totalSize);
            }
        } catch (Exception e) {
            return CompletableFuture.failedFuture(new DownloadException("创建临时文件失败", e, false));
        }

        log.debug("开始异步分块下载: {}，切块数: {}", targetFile.getName(), chunkCount);

        long chunkSize = totalSize / chunkCount;
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < chunkCount; i++) {
            long start = i * chunkSize;
            long end = (i == chunkCount - 1) ? totalSize - 1 : (start + chunkSize - 1);
            futures.add(downloadChunk(task, tmpFile, start, end, onBytesReceived));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    try {
                        verifyAndFinalize(task, tmpFile, targetFile);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                })
                .exceptionallyCompose(ex -> {
                    futures.forEach(f -> f.cancel(true));
                    cleanupTempFile(tmpFile);
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    String msg = cause.getMessage() != null ? cause.getMessage() : "";
                    if (msg.contains("206") || msg.contains("Range") || msg.contains("429") || msg.contains("RST_STREAM") || msg.contains("closed")) {
                        log.warn("分块下载失败 ({}), 尝试等待0.8秒降级为单线程下载: {}", msg, targetFile.getName());
                        return CompletableFuture.runAsync(() -> {
                            try {
                                Thread.sleep(800);
                            } catch (InterruptedException ignored) {}
                        }).thenCompose(v -> downloadSingleFile(task, onBytesReceived));
                    }
                    throw new CompletionException(new DownloadException("分块下载异常: " + cause.getMessage(), cause, false));
                });
    }
    /**
     * 分块（Range）大文件下载方法（纯异步版）
     * @param task 下载任务
     * @param chunkCount 切块数量
     */
    public static CompletableFuture<Void> downloadLargeFile(DownloadTask task, int chunkCount) {
        return downloadLargeFile(task, chunkCount, null);
    }

    // ---------------- 内部辅助方法 ----------------

    private static CompletableFuture<Void> downloadChunk(DownloadTask task, File tmpFile, long startByte, long endByte, LongConsumer onBytesReceived) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(task.getUrl()))
                .header("Range", "bytes=" + startByte + "-" + endByte)
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenAccept(response -> {
                    if (response.statusCode() != 206) {
                        throw new CompletionException(new DownloadException("服务器不支持 Range，状态码: " + response.statusCode(), false));
                    }
                    try (InputStream in = response.body();
                         RandomAccessFile raf = new RandomAccessFile(tmpFile, "rw")) {
                        raf.seek(startByte);
                        byte[] buffer = new byte[65536];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            raf.write(buffer, 0, read);
                            if (onBytesReceived != null) {
                                onBytesReceived.accept(read); // 实时回调
                            }
                        }
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                });
    }

    private static void prepareDirectory(File targetFile) throws IOException {
        File parentFile = targetFile.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            Files.createDirectories(parentFile.toPath());
        }
    }

    private static void verifyAndFinalize(DownloadTask task, File tmpFile, File targetFile) throws Exception {
        if (!tmpFile.exists()) {
            return;
        }
        if (task.getHash() != null && task.getHashType() != null) {
            boolean isValid = HashUtil.verifyFileHash(tmpFile.getAbsolutePath(), task.getHash(), task.getHashType());
            if (!isValid) {
                throw new DownloadException("Hash 校验失败，文件已损坏", false);
            }
        }
        Files.move(tmpFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        log.debug("文件下载完成: {}", targetFile.getName());
    }

    private static void cleanupTempFile(File tmpFile) {
        if (tmpFile.exists()) {
            boolean ignored = tmpFile.delete();
        }
    }
    /**
     * 自动获取文件大小（HEAD 请求）
     * @param url 下载文件的 URL
     * @return 文件大小（字节）
     */
    public static CompletableFuture<Long> fetchFileSize(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(response -> {
                    // 状态码 200 且响应头中有 Content-Length
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                    }
                    return -1L;
                })
                .exceptionally(ex -> {
                    log.error("自动获取文件大小发生异常: {}", ex.getMessage());
                    return -1L;
                });
    }
}