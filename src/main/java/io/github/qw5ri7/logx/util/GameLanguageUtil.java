package io.github.qw5ri7.logx.util;

import lombok.extern.slf4j.Slf4j;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

@Slf4j
public class GameLanguageUtil {

    /**
     * 🌟 不需要传参数，自动读取启动器当前语言并写入游戏
     * @param gameDir 游戏目录（需要处理版本隔离）
     */
    public static void applyLanguageIfNeeded(String gameDir, String versionId) {
        // 1. 自动获取启动器当前语言，并转换为 MC 支持的格式 (zh-cn -> zh_cn)
        String launcherLang = I18nUtil.getCurrentLanguage();
        String targetLang = launcherLang.replace("-", "_").toLowerCase();
        if (isLegacyVersion(versionId)) {
            String[] split = targetLang.split("_");
            targetLang = split[0] + "_" + split[1].toUpperCase();
        }

        File optionsFile = new File(gameDir, "options.txt");

        // 2. 如果文件不存在，直接创建并写入
        if (!optionsFile.exists()) {
            try {
                if (optionsFile.getParentFile() != null) {
                    optionsFile.getParentFile().mkdirs();
                }
                Files.writeString(optionsFile.toPath(), "lang:" + targetLang + System.lineSeparator(), StandardCharsets.UTF_8);
                log.info("首次启动，已自动同步游戏语言: {}", targetLang);
            } catch (IOException e) {
                log.error("创建 options.txt 并写入语言失败", e);
            }
            return;
        }

        // 3. 如果文件存在，检查是否包含 lang: 字段
        try {
            List<String> lines = Files.readAllLines(optionsFile.toPath(), StandardCharsets.UTF_8);
            boolean hasLang = false;
            for (String line : lines) {
                if (line.startsWith("lang:")) {
                    hasLang = true;
                    break;
                }
            }

            // 如果存在文件但没有 lang 字段
            if (!hasLang) {
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(optionsFile, true), StandardCharsets.UTF_8))) {
                    writer.write("lang:" + targetLang);
                    writer.newLine();
                }
                log.info("配置文件缺少语言设置，已自动追加: {}", targetLang);
            } else {
                log.debug("检测到玩家已设置游戏语言，跳过写入。");
            }
        } catch (IOException e) {
            log.error("读取或修改 options.txt 失败", e);
        }
    }
    private static boolean isLegacyVersion(String versionId) {
        if (versionId == null || versionId.isEmpty()) return false;

        try {
            // 处理类似 "1.12.2", "1.10.2-Forge" 这种格式
            if (versionId.startsWith("1.")) {
                String[] parts = versionId.split("\\.");
                if (parts.length >= 2) {
                    // 提取 "12" 或 "10" 转成整数
                    int minor = Integer.parseInt(parts[1]);
                    return minor < 11; // 1.11 以前强制大写
                }
            }
            // 极老的 Alpha 或 Beta 版本（如 b1.7.3），默认算作老版本
            if (versionId.startsWith("a") || versionId.startsWith("b")) {
                return true;
            }
        } catch (Exception e) {
            // 如果解析出错了，安全起见默认按新版处理（小写）
            log.warn("解析版本号失败，默认按新版格式处理: {}", versionId);
        }
        return false;
    }
}