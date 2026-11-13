package io.github.qw5ri7.logx.util.archive;

import io.github.qw5ri7.logx.exception.ArchiveException;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
public class ZipUtil {
    public static void extractNatives(String jarPath, String extractPath, List<String> excludes) {
        File jarFile = new File(jarPath);
        if (!jarFile.exists()) {
            log.warn("跳过解压，Natives Jar 文件不存在: {}", jarPath);
            return;
        }

        log.info("正在解压原生库: {} -> {}", jarFile.getName(), extractPath);

        // 直接复用你写好的 unzip 核心逻辑
        unzip(jarPath, extractPath, excludes);
    }
    /**
     * 解压 Zip/Jar 文件（自带 Zip Slip 防护和排除机制）
     *
     * @param zipPath  压缩包路径
     * @param destDir  解压目标目录
     * @param excludes 要排除的文件/文件夹前缀（比如 List.of("META-INF/")）
     */
    public static void unzip(String zipPath, String destDir, List<String> excludes) {
        File destDirFile = new File(destDir);
        if (!destDirFile.exists() && !destDirFile.mkdirs()) {
            throw new ArchiveException("无法创建解压目录: " + destDir, false);
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                // 1. 检查排除规则
                if (isExcluded(entryName, excludes)) {
                    continue;
                }

                // 2. 防范 Zip Slip（路径穿越）漏洞
                java.nio.file.Path destPath = destDirFile.toPath().resolve(entryName).normalize();
                if (!destPath.startsWith(destDirFile.toPath())) {
                    throw new ArchiveException("恶意压缩包！试图将文件解压到目录外: " + entryName, false);
                }

                File outFile = destPath.toFile();

                // 3. 如果是文件夹，创建目录
                if (entry.isDirectory()) {
                    if (!outFile.exists() && !outFile.mkdirs()) {
                        throw new IOException("无法创建目录: " + outFile);
                    }
                } else {
                    // 4. 如果是文件，确保父目录存在并写入
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("无法创建父目录: " + parent);
                    }
                    try (BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(outFile.toPath()))) {
                        int read;
                        while ((read = zis.read(buffer)) != -1) {
                            bos.write(buffer, 0, read);
                        }
                    }
                }
            }
            log.info("解压完成: {}", zipPath);
        } catch (IOException e) {
            log.error("解压失败: {}", zipPath, e);
            throw new ArchiveException("解压文件失败，可能文件已损坏", e, false);
        }
    }


    // ================== 压缩部分 ==================

    /**
     * 压缩文件夹为 Zip 文件
     *
     * @param sourceDirPath 源文件夹
     * @param zipFilePath   生成的 zip 文件路径
     * @param excludes      排除规则（支持文件夹名、相对路径、后缀匹配）
     */
    public static void zipDirectory(String sourceDirPath, String zipFilePath, List<String> excludes) {
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new ArchiveException("要压缩的源文件夹不存在: " + sourceDirPath, false);
        }

        File zipFile = new File(zipFilePath);
        if (zipFile.getParentFile() != null && !zipFile.getParentFile().exists()) {
            zipFile.getParentFile().mkdirs();
        }

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFile.toPath())))) {
            zipFileOrDir(sourceDir, "", zos, excludes);
            log.info("压缩完成: {}", zipFilePath);
        } catch (IOException e) {
            log.error("压缩失败: {}", sourceDirPath, e);
            throw new ArchiveException("压缩文件夹失败，请检查文件是否被占用", e, false);
        }
    }

    // --- 内部辅助方法 ---

    private static boolean isExcluded(String entryName, List<String> excludes) {
        if (excludes == null || excludes.isEmpty()) return false;
        for (String exclude : excludes) {
            if (entryName.startsWith(exclude)) return true;
        }
        return false;
    }

    private static void zipFileOrDir(File file, String currentRelativePath, ZipOutputStream zos, List<String> excludes) throws IOException {
        // 1. 忽略符号链接
        if (Files.isSymbolicLink(file.toPath())) {
            log.warn("跳过符号链接: {}", file.getAbsolutePath());
            return;
        }

        String thisPath = currentRelativePath.isEmpty() ? file.getName() : currentRelativePath + "/" + file.getName();

        // 2. 文件夹级别排除
        if (file.isDirectory() && excludes != null && excludes.contains(file.getName())) {
            return;
        }

        // 3. 精确路径排除
        if (excludes != null && excludes.contains(thisPath)) {
            return;
        }

        // 4. 强制保留 json 文件
        boolean isJson = file.getName().endsWith(".json");

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null || children.length == 0) {
                zos.putNextEntry(new ZipEntry(thisPath + "/"));
                zos.closeEntry();
                return;
            }
            for (File child : children) {
                zipFileOrDir(child, thisPath, zos, excludes);
            }
        } else {
            // 5. 后缀排除（排除非 json 的指定后缀）
            if (!isJson && excludes != null) {
                for (String exclude : excludes) {
                    if (exclude.startsWith("*.") && file.getName().endsWith(exclude.substring(1))) {
                        return;
                    }
                }
            }

            // 6. 写入文件
            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                zos.putNextEntry(new ZipEntry(thisPath));
                byte[] buffer = new byte[8192];
                int read;
                while ((read = bis.read(buffer)) != -1) {
                    zos.write(buffer, 0, read);
                }
                zos.closeEntry();
            } catch (IOException e) {
                log.warn("跳过无法读取的文件: {}", file.getName());
            }
        }
    }

    public static void unzip(String sourceDirPath, String zipFilePath) {
        unzip(sourceDirPath, zipFilePath, null);
    }
}
