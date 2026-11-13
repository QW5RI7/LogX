package io.github.qw5ri7.logx.core;

import io.github.qw5ri7.logx.exception.GameLaunchException;
import io.github.qw5ri7.logx.model.LauncherConfig;
import io.github.qw5ri7.logx.model.User;
import io.github.qw5ri7.logx.model.VersionJson;
import io.github.qw5ri7.logx.util.ConfigUtil;
import io.github.qw5ri7.logx.util.GameLanguageUtil;
import io.github.qw5ri7.logx.util.RuleUtil;
import io.github.qw5ri7.logx.util.TaskBuilder;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class GameLauncher {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 启动游戏
     * @param user 用户信息
     * @param gamePath 游戏路径，类似于.minecraft\versions\1.20.4\
     * @return 游戏进程
     */
    public static Process launchGame(User user, String gamePath) throws Exception {
        // 1. 路径与基础信息推导
        File versionDir = new File(gamePath);
        if (!versionDir.exists()) {
            throw new GameLaunchException("版本目录不存在: " + gamePath, true);
        }
        File mcDirFile = versionDir.getParentFile().getParentFile(); // .minecraft
        String mcDir = mcDirFile.getAbsolutePath();

        // 2. 读取本地 JSON
        File jsonFile = findVersionJson(versionDir);
        VersionJson json = objectMapper.readValue(jsonFile, VersionJson.class);
        String versionId = json.getId();
        if (versionId == null || versionId.trim().isEmpty()) {
            versionId = jsonFile.getName().replace(".json", "");
        }
        GameLanguageUtil.applyLanguageIfNeeded(mcDir, versionId);

        // 3. 准备核心占位符字典
        Map<String, String> params = new HashMap<>();
        params.put("auth_player_name", user.getUsername());
        params.put("version_name", versionId);
        LauncherConfig config = ConfigUtil.getCurrentConfig();
        boolean isIsolated = config.getIsVersionIsolation() != null && config.getIsVersionIsolation();
        String gameDir;
        if (isIsolated) {
            gameDir = versionDir.getAbsolutePath(); // 隔离模式
        } else {
            gameDir = mcDir; // 非隔离模式
        }
        params.put("game_directory", gameDir);
        params.put("assets_root", new File(mcDir, "assets").getAbsolutePath());
        params.put("assets_index_name", json.getAssetIndex() != null ? json.getAssetIndex().getId() : "legacy");
        params.put("auth_uuid", user.getUuid().replace("-", "")); // 绝大多数情况下去掉横杠
        params.put("auth_access_token", user.getAccessToken() != null ? user.getAccessToken() : "0");
        params.put("clientid", "0"); // 离线随便填
        params.put("auth_xuid", "0"); // 离线随便填
        params.put("user_type", user.getAccountType() == io.github.qw5ri7.logx.model.AccountType.LOCAL ? "legacy" : "msa");
        params.put("version_type", json.getType() != null ? json.getType().getValue() : "release");
        params.put("natives_directory", new File(versionDir, versionId + "-natives").getAbsolutePath());
        params.put("classpath", TaskBuilder.buildClasspath(json, mcDir));
        params.put("launcher_name", "LogX");
        params.put("launcher_version", "1.0.0");
        if (json.getLogging() != null && json.getLogging().getClient() != null) {
            String logId = json.getLogging().getClient().getFile().getId();
            String logPath = new File(mcDir, "assets/log_configs/" + logId).getAbsolutePath();
            params.put("path", logPath); // 对应 -Dlog4j.configurationFile=${path}
        } else {
            params.put("path", "");
        }

        // 4. 准备 Java 路径（这里暂时读取系统默认 Java，建议以后改成配置读取）
        String javaPath = System.getProperty("java.home") + File.separator + "bin" + File.separator + "javaw.exe";

        // 5. 组装最终命令 List
        List<String> command = new ArrayList<>();
        command.add(javaPath);

        // 6. 拼装 JVM 参数
        if (json.getArguments() != null && json.getArguments().getJvm() != null) {
            for (VersionJson.Argument arg : json.getArguments().getJvm()) {
                addArgsToList(command, arg, params);
            }
        } else {
            // 极老版本兜底
            command.add("-Djava.library.path=" + params.get("natives_directory"));
            command.add("-cp");
            command.add(params.get("classpath"));
        }

        // 7. 添加主类
        command.add(json.getMainClass());

        // 8. 拼装游戏参数
        if (json.getArguments() != null && json.getArguments().getGame() != null) {
            for (VersionJson.Argument arg : json.getArguments().getGame()) {
                addArgsToList(command, arg, params);
            }
        } else if (json.getMinecraftArguments() != null) {
            // 老版本兼容 (1.12.2 及以前)
            String[] oldArgs = json.getMinecraftArguments().split(" ");
            for (String arg : oldArgs) {
                String replaced = replacePlaceholders(arg, params);
                if (!replaced.isEmpty()) command.add(replaced);
            }
        }

        // 9. 打印命令（方便排错）
        log.info("启动命令: {}", String.join(" ", command));

        // 10. 执行启动
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(mcDirFile); // 设置工作目录
        pb.redirectErrorStream(true); // 将错误流合并到标准流，方便读取

        return pb.start();
    }

    /**
     * 辅助方法：处理并添加 Argument 到命令列表
     */
    private static void addArgsToList(List<String> command, VersionJson.Argument arg, Map<String, String> params) {
        // 1. 判断规则 (比如只允许 OSX 的参数，在 Windows 下跳过)
        if (arg.getRules() != null && !RuleUtil.isAllowed(arg.getRules())) {
            return;
        }
        // 2. 提取值并替换占位符
        if (arg.getValue() != null) {
            for (String val : arg.getValue()) {
                String replaced = replacePlaceholders(val, params);
                if (!replaced.isEmpty()) {
                    command.add(replaced);
                }
            }
        }
    }

    /**
     * 辅助方法：替换占位符
     */
    private static String replacePlaceholders(String input, Map<String, String> params) {
        if (input == null) return "";
        String result = input;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
    private static File findVersionJson(File versionDir) {
        File[] jsonFiles = versionDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (jsonFiles == null || jsonFiles.length == 0) {
            throw new GameLaunchException("在该目录下找不到版本 JSON 文件: " + versionDir.getAbsolutePath(), true);
        }
        for (File jsonFile : jsonFiles) {
            VersionJson versionJson = objectMapper.readValue(jsonFile, VersionJson.class);
            if (versionJson.getMainClass() != null) {
                return jsonFile;
            }
        }
        throw new GameLaunchException("在该目录下找不到版本 JSON 文件: " + versionDir.getAbsolutePath(), true);
    }
}
