package io.github.qw5ri7.logx.util;

public class UrlRewriter {
    private static final String BMCL_BASE = "https://bmclapi2.bangbang93.com";

    public static String rewrite(String originalUrl, boolean useMirror) {
        if (!useMirror || originalUrl == null || originalUrl.isEmpty()) {
            return originalUrl;
        }

        // 1. 替换 Libraries 依赖库
        if (originalUrl.startsWith("https://libraries.minecraft.net/")) {
            return originalUrl.replace("https://libraries.minecraft.net/", BMCL_BASE + "/maven/");
        }

        // 2. 替换 Assets 资源文件
        if (originalUrl.startsWith("https://resources.download.minecraft.net/")) {
            return originalUrl.replace("https://resources.download.minecraft.net/", BMCL_BASE + "/assets/");
        }

        // 3. 替换版本清单 & 元数据 (Piston Meta)
        if (originalUrl.startsWith("https://launchermeta.mojang.com/")) {
            return originalUrl.replace("https://launchermeta.mojang.com/", BMCL_BASE + "/");
        }
        if (originalUrl.startsWith("https://piston-meta.mojang.com/")) {
            return originalUrl.replace("https://piston-meta.mojang.com/", BMCL_BASE + "/");
        }

        return originalUrl; // 其他域名（如 Forge Maven）原样返回
    }
}