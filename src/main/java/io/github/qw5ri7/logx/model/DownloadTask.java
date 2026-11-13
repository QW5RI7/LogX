package io.github.qw5ri7.logx.model;

import io.github.qw5ri7.logx.util.HashUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 下载任务模型类
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownloadTask {
    private String url; // 文件下载地址
    private String savePath; // 文件保存路径
    private String hash; // 文件哈希值
    private HashType hashType; // 哈希算法类型
    private Long fileSize; // 文件大小
    private Boolean isNative; // 是否有本地文件
    /**
     * 检查文件是否已完整下载
     * @return 如果已完整下载则返回 true，否则返回 false
     */
    public boolean isDownloaded() {
        if (savePath == null || savePath.isEmpty()) return false;
        java.io.File file = new java.io.File(savePath);
        if (!file.exists()) return false;
        // 如果文件存在，且有预期大小，就对比一下大小
        if (fileSize != null && fileSize > 0) {
            return file.length() == fileSize && HashUtil.verifyFileHash(savePath, hash, hashType);
        }
        return file.length() > 0;
    }
}
