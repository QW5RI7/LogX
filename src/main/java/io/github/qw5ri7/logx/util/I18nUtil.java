package io.github.qw5ri7.logx.util;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class I18nUtil {
    private static final String DEFAULT_LANGUAGE = "zh-cn";

    @Getter
    private static String currentLanguage = DEFAULT_LANGUAGE;

    // 换成 ConcurrentHashMap 保证多线程安全
    private static final Map<String, ObjectNode> languageMap = new ConcurrentHashMap<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        try {
            String configLanguage = ConfigUtil.getCurrentConfig().getLanguage();
            if (configLanguage != null && !configLanguage.isEmpty()) {
                currentLanguage = configLanguage;
            }
        } catch (Throwable e) {
            log.error("读取语言配置失败", e);
            currentLanguage = DEFAULT_LANGUAGE;
        } finally {
            loadLanguage(currentLanguage);
        }
    }

    /**
     * 获取翻译文本
     * @param key 语言文件的 key
     * @param pairs 占位符键值对（比如 "player", "Steve"），必须是偶数个
     */
    public static String get(String key, String... pairs) {
        if (key == null || key.isEmpty()) return "";

        ObjectNode langNode = languageMap.get(currentLanguage);
        if (langNode == null) return key;

        // 🌟 核心改动：支持通过 "source.auto" 去逐层解析嵌套的 JSON
        JsonNode valueNode = langNode;
        for (String part : key.split("\\.")) {
            if (valueNode == null) break;
            valueNode = valueNode.get(part);
        }

        // 如果找不到这个 Key，直接返回 key（防止 NPE）
        String value = (valueNode == null || valueNode.isNull()) ? key : valueNode.asString();

        // 剩下的是占位符替换逻辑，保持不变...
        if (pairs == null || pairs.length == 0) {
            return value;
        }
        if (pairs.length % 2 != 0) {
            log.warn("语言占位符参数必须是偶数个: key={}", key);
            return value;
        }
        for (int i = 0; i < pairs.length; i += 2) {
            String placeholder = pairs[i];
            String replacement = pairs[i + 1];
            if (placeholder != null && replacement != null) {
                value = value.replace("{" + placeholder + "}", replacement);
            }
        }
        return value;
    }

    /**
     * 切换语言
     */
    public static void setCurrentLanguage(String language) {
        currentLanguage = language;
        loadLanguage(language);
        try {
            ConfigUtil.getCurrentConfig().setLanguage(language);
            ConfigUtil.saveConfig();
        } catch (Exception e) {
            log.error("保存语言设置失败", e);
        }
    }

    private static void loadLanguage(String language) {
        if (!languageMap.containsKey(language)) {
            ObjectNode node = loadLanguageToJSONObject(language);
            if (node != null) {
                languageMap.put(language, node);
            }
        }
    }

    private static ObjectNode loadLanguageToJSONObject(String language) {
        // 尝试加载目标语言
        String path = "/language/" + language + ".json";
        InputStream is = I18nUtil.class.getResourceAsStream(path);

        // 如果目标语言不存在，降级加载默认语言
        if (is == null) {
            log.warn("找不到语言文件 {}，将降级使用默认语言 {}", path, DEFAULT_LANGUAGE);
            is = I18nUtil.class.getResourceAsStream("/language/" + DEFAULT_LANGUAGE + ".json");
        }

        // 如果连默认语言都没找到，说明你打包时资源漏了
        if (is == null) {
            log.error("致命错误：默认语言文件 /language/{}.json 也不存在！", DEFAULT_LANGUAGE);
            return MAPPER.createObjectNode(); // 返回空对象防止 NPE
        }

        try (InputStream inputStream = is) {
            // Java 9+ 极简读取，不用再手写 buffer 循环了
            String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return MAPPER.readValue(json, ObjectNode.class);
        } catch (IOException e) {
            log.error("读取语言文件失败: {}", path, e);
            return MAPPER.createObjectNode();
        }
    }
}