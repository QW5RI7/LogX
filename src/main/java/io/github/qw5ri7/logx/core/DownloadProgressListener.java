package io.github.qw5ri7.logx.core;

import io.github.qw5ri7.logx.model.DownloadStatus;
import io.github.qw5ri7.logx.model.DownloadTask;

public interface DownloadProgressListener {
    /**
     * 下载开始，告诉真正的总任务数，过滤已下载的任务
     * @param totalTasks 总任务数
     */
    void onStart(int totalTasks);
    /**
     * 下载进度，每一秒调用一次，更新下载进度
     * @param completed 已完成任务数
     * @param total 总任务数
     * @param downloadedBytes 已下载字节数
     * @param totalBytes 总字节数
     * @param speedBps 下载速度（单位：字节/秒）
     */
    void onProgress(int completed, int total, long downloadedBytes, long totalBytes, long speedBps);
    /**
     * 下载文件失败，告诉UI层失败的任务和错误信息
     * @param task 失败的任务
     * @param errorMsg 错误信息
     */
    void onFileFailed(DownloadTask task, String errorMsg);
    /**
     * 下载所有文件完成，告诉UI层是否成功
     * @param isSuccess 是否成功
     */
    void onAllFinished(boolean isSuccess);
    /**
     * 下载状态更新，告诉UI层当前下载状态
     * @param status 当前状态
     */
    void onStatus(DownloadStatus status);
}
