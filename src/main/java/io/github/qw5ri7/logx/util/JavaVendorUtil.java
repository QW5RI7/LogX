package io.github.qw5ri7.logx.util;

import java.util.HashMap;
import java.util.Map;

public class JavaVendorUtil {

    // 常见 Java 厂商的映射字典
    private static final Map<String, String> VENDOR_MAP = new HashMap<>();

    static {
        // 标准匹配
        VENDOR_MAP.put("eclipse adoptium", "Adoptium");
        VENDOR_MAP.put("temurin", "Adoptium");
        VENDOR_MAP.put("oracle", "Oracle");
        VENDOR_MAP.put("amazon", "Amazon");
        VENDOR_MAP.put("azul", "Azul");
        VENDOR_MAP.put("bellsoft", "BellSoft");
        VENDOR_MAP.put("microsoft", "Microsoft");
        VENDOR_MAP.put("red hat", "Red Hat");
        VENDOR_MAP.put("sap", "SAP");
        VENDOR_MAP.put("alibaba", "Alibaba");
        VENDOR_MAP.put("tencent", "Tencent");
        VENDOR_MAP.put("jetbrains", "JetBrains");
        VENDOR_MAP.put("graalvm", "GraalVM");
        VENDOR_MAP.put("openjdk", "OpenJDK");
        VENDOR_MAP.put("ibm", "IBM");
    }

    /**
     * 规范化厂商名称
     * @param rawVendor 从 release 文件里读取的原始值（如 "Eclipse Adoptium"）
     * @return 映射后的短名称（如 "Adoptium"）
     */
    public static String formatVendor(String rawVendor) {
        if (rawVendor == null || rawVendor.trim().isEmpty()) {
            return "Unknown";
        }

        String lowerVendor = rawVendor.toLowerCase();

        // 遍历字典，只要包含关键字就返回对应的短名称
        for (Map.Entry<String, String> entry : VENDOR_MAP.entrySet()) {
            if (lowerVendor.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 如果不在映射表里，原样返回（可能是个小众发行版）
        return rawVendor;
    }
}
