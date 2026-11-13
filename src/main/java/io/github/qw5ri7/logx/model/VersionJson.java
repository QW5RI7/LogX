package io.github.qw5ri7.logx.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * version.json（单个 Minecraft Java 版本的清单文件）模型类。
 * <p>
 * 兼容旧版使用 {@code minecraftArguments} 的版本，以及新版使用
 * {@code arguments} 的版本；同时包含 26.1 起新增的
 * {@code default-user-jvm} 和 {@code clientVersion} 字段。
 */
@Data
public class VersionJson {
    private String id;
    private VersionType type;
    private String mainClass;
    private Integer minimumLauncherVersion;
    private OffsetDateTime releaseTime;
    private OffsetDateTime time;
    private String clientVersion;
    private String assets;
    private Integer complianceLevel;
    private String minecraftArguments;
    private Arguments arguments;
    private AssetIndex assetIndex;
    private Downloads downloads;
    private JavaVersion javaVersion;
    private List<Library> libraries;
    private Logging logging;

    /**
     * 游戏参数与 JVM 参数。
     */
    @Data
    public static class Arguments {
        private List<Argument> game;
        private List<Argument> jvm;
        @JsonProperty("default-user-jvm")
        private List<Argument> defaultUserJvm;
    }

    /**
     * 单条启动参数：可以是纯字符串，也可以是带 rules 的条件参数。
     * <p>
     * 纯字符串会被归一化为只包含一个元素的 {@link #value}，
     * 便于后续统一处理。
     */
    @Data
    public static class Argument {
        private List<Rule> rules;
        private List<String> value;

        @JsonCreator
        public static Argument fromJson(JsonNode node) {
            Argument argument = new Argument();
            if (node == null || node.isNull()) return argument;

            if (node.isString()) {
                List<String> values = new ArrayList<>(1);
                values.add(node.asString());
                argument.setValue(values);
                return argument;
            }

            if (!node.isObject()) return argument;

            JsonNode rulesNode = node.get("rules");
            if (rulesNode != null && rulesNode.isArray()) {
                List<Rule> rules = new ArrayList<>(rulesNode.size());
                for (JsonNode ruleNode : rulesNode) {
                    rules.add(Rule.fromJson(ruleNode));
                }
                argument.setRules(rules);
            }

            argument.setValue(readValues(node.get("value")));
            return argument;
        }

        private static List<String> readValues(JsonNode valueNode) {
            if (valueNode == null || valueNode.isNull()) return null;

            List<String> values = new ArrayList<>();
            if (valueNode.isArray()) {
                for (JsonNode item : valueNode) {
                    if (item != null && !item.isNull()) {
                        values.add(item.asString());
                    }
                }
            } else {
                values.add(valueNode.asString());
            }
            return values;
        }
    }

    /**
     * 参数、依赖库等使用的规则。
     */
    @Data
    public static class Rule {
        private String action;
        private OperatingSystem os;
        private Map<String, Boolean> features;

        private static Rule fromJson(JsonNode node) {
            Rule rule = new Rule();
            if (node == null || !node.isObject()) return rule;

            JsonNode action = node.get("action");
            if (action != null) rule.setAction(action.asString());

            JsonNode osNode = node.get("os");
            if (osNode != null && osNode.isObject()) {
                rule.setOs(OperatingSystem.fromJson(osNode));
            }

            JsonNode featuresNode = node.get("features");
            if (featuresNode != null && featuresNode.isObject()) {
                Map<String, Boolean> features = new LinkedHashMap<>();
                for (String name : featuresNode.propertyNames()) {
                    features.put(name, featuresNode.get(name).asBoolean());
                }
                rule.setFeatures(features);
            }
            return rule;
        }
    }

    /**
     * rules 中的操作系统条件。
     */
    @Data
    public static class OperatingSystem {
        private String name;
        private String version;
        private String arch;
        private VersionRange versionRange;

        private static OperatingSystem fromJson(JsonNode node) {
            OperatingSystem os = new OperatingSystem();

            JsonNode name = node.get("name");
            if (name != null) os.setName(name.asString());

            JsonNode version = node.get("version");
            if (version != null) os.setVersion(version.asString());

            JsonNode arch = node.get("arch");
            if (arch != null) os.setArch(arch.asString());

            JsonNode versionRangeNode = node.get("versionRange");
            if (versionRangeNode != null && versionRangeNode.isObject()) {
                os.setVersionRange(VersionRange.fromJson(versionRangeNode));
            }
            return os;
        }
    }

    /**
     * 操作系统版本范围，min 和 max 均可省略。
     */
    @Data
    public static class VersionRange {
        private String min;
        private String max;

        private static VersionRange fromJson(JsonNode node) {
            VersionRange range = new VersionRange();

            JsonNode min = node.get("min");
            if (min != null) range.setMin(min.asString());

            JsonNode max = node.get("max");
            if (max != null) range.setMax(max.asString());
            return range;
        }
    }

    /**
     * 资源索引文件信息。
     */
    @Data
    public static class AssetIndex {
        private String id;
        private String sha1;
        private Long size;
        private Long totalSize;
        private String url;
    }

    /**
     * 客户端、服务端及其映射文件的下载信息。
     */
    @Data
    public static class Downloads {
        private DownloadInfo client;
        @JsonProperty("client_mappings")
        private DownloadInfo clientMappings;
        private DownloadInfo server;
        @JsonProperty("server_mappings")
        private DownloadInfo serverMappings;
        @JsonProperty("windows_server")
        private DownloadInfo windowsServer;
    }

    /**
     * 单个文件的下载信息。
     */
    @Data
    public static class DownloadInfo {
        private String path;
        private String sha1;
        private Long size;
        private String url;
    }

    /**
     * 版本所需的 Java 运行环境。
     */
    @Data
    public static class JavaVersion {
        private String component;
        private Integer majorVersion;
    }

    /**
     * 一个依赖库。
     */
    @Data
    public static class Library {
        private String name;
        private String url;
        private LibraryDownloads downloads;
        private Map<String, String> natives;
        private Extract extract;
        private List<Rule> rules;
    }

    /**
     * 依赖库的下载信息。
     */
    @Data
    public static class LibraryDownloads {
        private DownloadInfo artifact;
        private Map<String, DownloadInfo> classifiers;
    }

    /**
     * natives 解压规则。
     */
    @Data
    public static class Extract {
        private List<String> exclude;
    }

    /**
     * 日志配置。
     */
    @Data
    public static class Logging {
        private LoggingClient client;
    }

    /**
     * 客户端日志配置。
     */
    @Data
    public static class LoggingClient {
        private String argument;
        private LogFile file;
        private String type;
    }

    /**
     * 日志配置文件信息。
     */
    @Data
    public static class LogFile {
        private String id;
        private String sha1;
        private Long size;
        private String url;
    }
}
