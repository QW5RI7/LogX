package io.github.qw5ri7.logx.util;

import io.github.qw5ri7.logx.Launcher;
import io.github.qw5ri7.logx.exception.ConfigException;
import io.github.qw5ri7.logx.model.LauncherConfig;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 配置文件工具类
 */
@Slf4j
public class ConfigUtil {
    private static final ObjectMapper objectMapper = JsonMapper.builder().enable(JsonReadFeature.ALLOW_JAVA_COMMENTS).build();;
    private static final String CONFIG_FILE_NAME = "config.json";
    private static LauncherConfig currentConfig;

    /**
     * 加载配置文件，在配置文件不存在时加载默认配置
     */
    public static void loadConfig() {
        File configFile = new File(Launcher.APP_DIR, CONFIG_FILE_NAME);

        // 情况 1：配置文件不存在，加载默认配置
        if (!configFile.exists()) {
            log.info("配置文件不存在，正在生成默认配置...");
            loadDefaultAndSave();
            return;
        }

        // 情况 2：配置文件存在，尝试读取
        try {
            currentConfig = objectMapper.readValue(configFile, LauncherConfig.class);
            log.info("配置文件加载成功");
        } catch (Exception e) {
            // 情况 3：配置文件格式损坏！启动保底机制
            log.error("配置文件损坏，将备份原文件并重置为默认配置", e);
            backupBrokenConfig(configFile);
            loadDefaultAndSave();
            throw new ConfigException("配置文件损坏，已重置为默认配置", e, false);
        }
    }

    private static void loadDefaultAndSave() {
        try (InputStream is = ConfigUtil.class.getResourceAsStream("/defaultConfig.json")) {
            if (is == null) {
                throw new ConfigException("找不到内置的 defaultConfig.json", true);
            }
            currentConfig = objectMapper.readValue(is, LauncherConfig.class);
            saveConfig();
        } catch (ConfigException ce) {
            throw ce; // 直接抛出我们自定义的致命错误，不再套娃
        } catch (Exception e) {
            throw new ConfigException("读取默认配置失败", e, true);
        }
    }

    private static void backupBrokenConfig(File configFile) {
        try {
            Path source = configFile.toPath();
            Path target = new File(Launcher.APP_DIR, CONFIG_FILE_NAME + ".bak").toPath();
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("已将损坏的配置文件备份为 config.json.bak");
        } catch (Exception ex) {
            log.error("备份损坏的配置文件失败", ex);
        }
    }

    /**
     * 保存当前配置到文件
     * @throws ConfigException 如果保存失败
     */
    public static void saveConfig() {
        File configFile = new File(Launcher.APP_DIR, CONFIG_FILE_NAME);
        try {
            // 开启美化输出，玩家用记事本也能轻松阅读和修改
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile, currentConfig);
            log.info("配置文件已保存");
        } catch (Exception e) {
            String message = "保存配置文件失败，请检查磁盘权限";
            throw new ConfigException(message, e, false);
        }
    }

    /**
     * 获取当前配置对象
     * @return 当前配置对象，如果不存在则加载默认配置并返回
     */
    public static LauncherConfig getCurrentConfig() {
        if (currentConfig == null) {
            try {
                loadConfig();
            } catch (ConfigException e) {
                if (e.isFatal() || currentConfig == null) throw e;
                log.warn("配置已恢复为默认值，继续启动: {}", e.getMessage());
            }
        }
        return currentConfig;
    }
}