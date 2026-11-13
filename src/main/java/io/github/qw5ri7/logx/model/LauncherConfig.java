package io.github.qw5ri7.logx.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class LauncherConfig {
    private List<MinecraftPath> minecraftPaths;
    private Double x, y, width, height;
    private Integer selectedUserId;
    private Integer defaultMaxMemory;
    private String defaultCustomJvmArgs;
    private String defaultJavaPath;
    private String selectedMinecraftPath;
    private Boolean isVersionIsolation;
    private DownloadSourceType defaultDownloadSourceType;
    private String language;
    private String backgroundPath;
    @Data
    public static class MinecraftPath {
        private String path;
        private String lastSelectedVersion;
        private Map<String, GameConfig> configurations;
    }
    @Data
    public static class GameConfig {
        private String versionId;
        private Integer maxMemory;
        private String javaPath;
        private String customJvmArgs;
    }
}
