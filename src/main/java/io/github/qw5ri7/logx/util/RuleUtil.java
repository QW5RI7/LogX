package io.github.qw5ri7.logx.util;

import io.github.qw5ri7.logx.model.VersionJson;

import java.util.List;
import java.util.Locale;

public class RuleUtil {
    public static boolean isAllowed(List<VersionJson.Rule> rules) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        String currentOs = getCurrentOs();
        boolean allowed = false;
        for (VersionJson.Rule rule : rules) {
            if (rule.getFeatures() != null && !rule.getFeatures().isEmpty()) {
                continue;
            }
            boolean osMatches = rule.getOs() == null
                    || rule.getOs().getName() == null
                    || rule.getOs().getName().equals(currentOs);
            if (osMatches) {
                if ("allow".equals(rule.getAction())) {
                    allowed = true;
                } else if ("disallow".equals(rule.getAction())) {
                    allowed = false;
                }
            }
        }
        return allowed;
    }
    public static String getCurrentOs() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return "windows";
        }
        if (osName.contains("mac")) {
            return "osx"; // Mojang 的 JSON 里 macOS 叫 osx
        }
        return "linux";
    }
}
