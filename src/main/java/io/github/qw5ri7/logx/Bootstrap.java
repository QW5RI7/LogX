package io.github.qw5ri7.logx;

import io.github.qw5ri7.logx.mapper.GameUserMapper;
import io.github.qw5ri7.logx.util.ConfigUtil;
import io.github.qw5ri7.logx.util.MyBatisUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

import static io.github.qw5ri7.logx.Launcher.APP_DIR;

@Slf4j
public final class Bootstrap {

    private static volatile boolean initialized = false;

    /** 幂等：调多少次都安全，只有第一次真正干活 */
    public static synchronized void init() {
        if (initialized) return;

        // 1. 目录（Files.createDirectories 天然幂等，比 exists+mkdirs 干净）
        try {
            Files.createDirectories(APP_DIR.toPath());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "无法创建应用数据目录，请检查磁盘空间或权限：" + APP_DIR.getAbsolutePath(), e);
        }
        String osArch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        if (osArch.equals("x86") || osArch.equals("i386") || osArch.equals("i686")) {
            // 32位系统，不支持
            throw new UnsupportedOperationException("该软件不支持32位系统，请升级到64位系统或更换电脑");
        }

        // 2. 建表
        try (SqlSession session = MyBatisUtil.getFactory().openSession(true)) {
            session.getMapper(GameUserMapper.class).createTable();
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("检测到 JVM 正在关闭，开始保存最后的配置...");
            try {
                ConfigUtil.saveConfig();
            } catch (Exception e) {
                log.error("保存配置失败", e);
            }
        }));
        // 这些系统属性需在 HttpClient 初始化前设置（如 main 方法最开头）
        System.setProperty("jdk.httpclient.connectionPoolSize", "64"); // 连接池大小
        System.setProperty("jdk.httpclient.keepalive.timeout", "60"); // Keep-Alive超时（秒），避免频繁重建


        initialized = true;
    }

    private Bootstrap() {}
}
