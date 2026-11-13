package io.github.qw5ri7.logx.java;

import io.github.qw5ri7.logx.model.JavaInfo;
import io.github.qw5ri7.logx.model.JavaType;
import io.github.qw5ri7.logx.util.JavaVendorUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.qw5ri7.logx.Launcher.APP_DIR;

@Slf4j
public class JavaScanner {
    private static List<JavaInfo> javaInfos;
    private static final AtomicBoolean isScanning = new AtomicBoolean(false);
    public static synchronized List<JavaInfo> scan() {
        if (isScanning.get()) {
            log.info("正在扫描 Java 版本，请稍后");
            return null;
        }
        isScanning.set(true);
        if (javaInfos != null) {
            return javaInfos;
        }
        Set<File> foundDirs = ConcurrentHashMap.newKeySet();
        CompletableFuture<Void> task1 = CompletableFuture.runAsync(() ->
                getJavaDirs(new File(APP_DIR, "runtime"), 2, foundDirs)
        );
        CompletableFuture<Void> task2 = CompletableFuture.runAsync(() -> {
            String javaHome = System.getenv("JAVA_HOME");
            if (javaHome != null && !javaHome.trim().isEmpty()) {
                File javaHomeDir = new File(javaHome);
                if (isJavaDir(javaHomeDir)) {
                    foundDirs.add(javaHomeDir.getAbsoluteFile());
                }
            }
        });

        CompletableFuture<Void> task3 = CompletableFuture.runAsync(() ->
                scanBySystemCommand(foundDirs)
        );

        CompletableFuture<Void> task4 = CompletableFuture.runAsync(() ->
                scanAllDrivesProgramFiles(foundDirs)
        );

        CompletableFuture<Void> task5 = CompletableFuture.runAsync(() ->
                scanUserDirectories(foundDirs)
        );
        CompletableFuture.allOf(task1, task2, task3, task4, task5).join();
        javaInfos = List.copyOf(convertToJavaInfos(foundDirs));
        isScanning.set(false);
        return javaInfos;
    }
    public static synchronized void clearCache() {
        javaInfos = null;
    }
    private static List<JavaInfo> convertToJavaInfos(Set<File> foundDirs) {
        // 并发安全的去重 Map
        Map<String, JavaInfo> uniqueInfos = new ConcurrentHashMap<>();

        // 核心：使用 parallelStream() 开启多线程并行验证
        foundDirs.parallelStream().forEach(dir -> {
            // 这里的 parseJavaInfo 会被多个线程同时调用
            JavaInfo info = parseJavaInfoFromCommand(dir);
            if (info != null) {
                // 以绝对路径为 Key，天然去重且并发安全
                uniqueInfos.put(info.getPath(), info);
            }
        });

        // 把 Map 转回 List 并进行最终排序
        List<JavaInfo> resultList = new ArrayList<>(uniqueInfos.values());
        // 按主版本号从高到低排序
        resultList.sort((a, b) -> Integer.compare(b.getMajorVersion(), a.getMajorVersion()));

        return resultList;
    }
    private static JavaInfo parseJavaInfo(File javaDir) {
        File releaseFile = new File(javaDir, "release");
        int majorVersion = -1;
        boolean is64Bit = true; // 默认假定为64位
        String rawImplementor = "Unknown";
        boolean isJdk = false;

        if (releaseFile.exists()) {
            try {
                List<String> lines = Files.readAllLines(releaseFile.toPath());
                for (String line : lines) {
                    // 统一去掉首尾空格
                    line = line.trim();

                    if (line.startsWith("JAVA_VERSION=")) {
                        // 拿到 "25.0.3"
                        String versionStr = line.substring("JAVA_VERSION=".length()).replace("\"", "");
                        if (versionStr.startsWith("1.")) {
                            majorVersion = Integer.parseInt(versionStr.substring(2, 3));
                        } else {
                            majorVersion = Integer.parseInt(versionStr.split("\\.")[0]);
                        }
                    } else if (line.startsWith("OS_ARCH=")) {
                        // 拿到 "x86_64"
                        String arch = line.substring("OS_ARCH=".length()).replace("\"", "");
                        is64Bit = arch.contains("64");
                    } else if (line.startsWith("IMPLEMENTOR=")) {
                        // 拿到 "Oracle Corporation"
                        rawImplementor = JavaVendorUtil.formatVendor(line.substring("IMPLEMENTOR=".length()).replace("\"", ""));
                    } else if (line.startsWith("MODULES=")) {
                        if (line.contains("jdk.compiler") || line.contains("jdk.javadoc")) {
                            isJdk = true;
                        }
                    }
                }
            } catch (Exception e) {
                // 解析失败，当作坏包处理
            }
        }

        String cmdOutput = null;
        if (majorVersion == -1 || rawImplementor.isEmpty() || "Unknown".equals(rawImplementor) || "Oracle".equals(rawImplementor)) {
            JavaInfo cmdInfo = parseJavaInfoFromCommand(javaDir);
            if (cmdInfo != null) {
                majorVersion = cmdInfo.getMajorVersion();
                is64Bit = cmdInfo.is64Bit();
                isJdk = cmdInfo.getType() == JavaType.JDK;
                cmdOutput = cmdInfo.getVendor(); // 借用一下命令拿到的厂商作为判断依据
            }
        }
        if (majorVersion == -1) {
            return null;
        }
        String finalVendor = resolveVendor(rawImplementor, cmdOutput);
        if ("Unknown".equals(finalVendor)) finalVendor = "OpenJDK";
        return new JavaInfo(majorVersion, javaDir.getAbsolutePath(), isJdk ? JavaType.JDK : JavaType.JRE, finalVendor, is64Bit);
    }
    private static JavaInfo parseJavaInfoFromCommand(File javaDir) {
        // 1. 跨平台找 java.exe
        String osName = System.getProperty("os.name").toLowerCase();
        String exeSuffix = osName.contains("win") ? ".exe" : "";
        File javaExe = new File(javaDir, "bin/java" + exeSuffix);

        if (!javaExe.exists()) {
            return null;
        }

        int majorVersion = -1;
        boolean is64Bit = true;
        String vendor = "Unknown";

        Process process = null;
        try {
            // 2. 执行 java -version
            ProcessBuilder pb = new ProcessBuilder(javaExe.getAbsolutePath(), "-version");
            pb.redirectErrorStream(true);
            process = pb.start();

            // 3. 加超时防卡死
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }

            // 4. 读取输出
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null) {
                    count++;
                    line = line.trim();

                    // --- 解析版本号 ---
                    if (line.contains("version") && line.contains("\"") && count == 1) {
                        // 匹配引号内的内容，例如 "25.0.1" 或 "1.8.0_392"
                        String versionStr = line.substring(line.indexOf("\"") + 1, line.lastIndexOf("\""));
                        if (versionStr.startsWith("1.")) {
                            majorVersion = Integer.parseInt(versionStr.substring(2, 3)); // 1.8 -> 8
                        } else {
                            majorVersion = Integer.parseInt(versionStr.split("\\.")[0]); // 25.0.1 -> 25
                        }
                    }

                    // --- 解析架构 ---
                    if (line.contains("64-Bit") || line.contains("Server VM")) {
                        is64Bit = true; // 实际上只要跑起来了，大部分现代电脑都是64位
                    }

                    // --- 解析厂商 ---
                    if ("Unknown".equals(vendor)) {
                        String lowerLine = line.toLowerCase();
                        if (lowerLine.contains("microsoft")) {
                            vendor = "Microsoft";
                        } else if (lowerLine.contains("temurin") || lowerLine.contains("adoptium")) {
                            vendor = "Adoptium";
                        } else if (lowerLine.contains("jetbrains")) {
                            vendor = "JetBrains";
                        } else if (lowerLine.contains("corretto")) {
                            vendor = "Amazon";
                        } else if (lowerLine.contains("zulu")) {
                            vendor = "Azul";
                        } else if (lowerLine.contains("graalvm")) {
                            vendor = "GraalVM";
                        } else if (lowerLine.contains("openjdk")) {
                            vendor = "OpenJDK";
                        } else if (lowerLine.contains("java(tm)") || lowerLine.contains("hotspot(tm)") || lowerLine.contains("oracle")) {
                            vendor = "Oracle";
                        }
                    }
                }
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        }

        // 如果连版本号都没拿到，说明这是个坏文件
        if (majorVersion == -1) return null;
        if (vendor.equals("Unknown")) vendor = "OpenJDK";

        // 兜底判断 JDK 还是 JRE
        boolean isJdk = new File(javaDir, "bin/javac" + exeSuffix).exists();

        return new JavaInfo(majorVersion, javaDir.getAbsolutePath(), isJdk ? JavaType.JDK : JavaType.JRE, vendor, is64Bit);
    }
    private static String resolveVendor(String rawImplementor, String commandOutput) {
        String lowerRaw = rawImplementor == null ? "" : rawImplementor.toLowerCase();
        String lowerCmd = commandOutput == null ? "" : commandOutput.toLowerCase();

        // ================= 第二优先级：看命令行输出 =================
        // （如果是跑了命令兜底，命令输出的准确率极高）
        if (lowerCmd.contains("graalvm")) return "GraalVM";
        if (lowerCmd.contains("microsoft")) return "Microsoft";
        if (lowerCmd.contains("jetbrains")) return "JetBrains";
        if (lowerCmd.contains("corretto")) return "Amazon";
        if (lowerCmd.contains("zulu")) return "Azul";
        if (lowerCmd.contains("temurin") || lowerCmd.contains("adoptium")) return "Adoptium";
        if (lowerCmd.contains("openjdk")) return "OpenJDK";

        // ================= 第三优先级：看 release 文件 =================
        if (lowerRaw.contains("graalvm")) return "GraalVM";
        if (lowerRaw.contains("microsoft")) return "Microsoft";
        if (lowerRaw.contains("adoptium")) return "Adoptium";
        if (lowerRaw.contains("amazon")) return "Amazon";
        if (lowerRaw.contains("azul")) return "Azul";
        if (lowerRaw.contains("jetbrains")) return "JetBrains";

        // ================= 终极兜底 =================
        // 如果确实是 Oracle 的商标，或者是老版本的 JRE
        if (lowerRaw.contains("oracle") || lowerCmd.contains("java(tm)") || lowerCmd.contains("hotspot(tm)")) {
            return "Oracle";
        }

        return "Unknown";
    }
    private static void scanUserDirectories(Set<File> foundDirs) {
        String userHome = System.getProperty("user.home");
        File appDataRoaming = new File(userHome, "AppData\\Roaming\\.minecraft\\runtime");
        getJavaDirs(appDataRoaming, 2, foundDirs);
        File localAppData = new File(userHome, "AppData\\Local\\Packages\\Microsoft.4297127D64EC6_8wekyb3d8bbwe\\LocalCache\\Local\\runtime");
        getJavaDirs(localAppData, 3, foundDirs);
        File jdks = new File(userHome, ".jdks");
        getJavaDirs(jdks, 2, foundDirs);
        File gradleJdks = new File(userHome, ".gradle\\jdks");
        getJavaDirs(gradleJdks, 2, foundDirs);
        File userJava = new File(userHome, "Java");
        getJavaDirs(userJava, 3, foundDirs);
    }

    private static void scanAllDrivesProgramFiles(Set<File> foundDirs) {
        // 1. 获取当前电脑所有的盘符 (例如 C:\, D:\, E:\)
        File[] roots = File.listRoots();
        if (roots == null) return;

        for (File root : roots) {
            // 2. 拼出当前盘符下的 Program Files 目录
            File pf = new File(root, "Program Files");
            File pf86 = new File(root, "Program Files (x86)");

            // 3. 如果存在，就去里面找 Java（深度限制为 3）
            if (pf.exists() && pf.isDirectory()) {
                getJavaDirs(pf, 3, foundDirs);
            }
            if (pf86.exists() && pf86.isDirectory()) {
                getJavaDirs(pf86, 3, foundDirs);
            }

            // （可选）如果玩家喜欢把 Java 放根目录，比如 D:\Java
            File rootJava = new File(root, "Java");
            if (rootJava.exists() && rootJava.isDirectory()) {
                getJavaDirs(rootJava, 3, foundDirs);
            }
        }
    }
    private static void scanBySystemCommand(Set<File> foundDirs) {
        // 1. 跨平台判断命令
        String osName = System.getProperty("os.name").toLowerCase();
        String command = osName.contains("win") ? "where" : "which";

        Process process = null;
        try {
            process = new ProcessBuilder(command, "java").start();

            // 2. 等待命令执行，设置 1 秒超时防止卡死
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return;
            }

            // 3. 读取输出，必须指定 UTF-8 编码防乱码
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    File javaExe = new File(line);
                    if (javaExe.getParentFile() == null) continue;

                    File javaDir = javaExe.getParentFile().getParentFile();
                    if (javaDir != null && isJavaDir(javaDir)) {
                        foundDirs.add(javaDir.getAbsoluteFile());
                    }
                }
            }
        } catch (Exception e) {
            // 报错直接忽略（大概率是玩家没装 Java），不要影响其他扫描逻辑
            log.debug("系统命令扫描失败", e);
        } finally {
            // 4. 无论如何，强制销毁进程和流，防止句柄泄露
            if (process != null) {
                process.destroy();
            }
        }
    }
    private static void getJavaDirs(File dir, int maxDepth, Set<File> foundDirs) {
        if (maxDepth < 0 || dir == null || !dir.isDirectory()) {
            return;
        }
        if (Files.isSymbolicLink(dir.toPath())) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        if (dir.isDirectory()) {
            for (File file : files) {
                if (file.isDirectory()) {
                    String name = file.getName().toLowerCase();
                    if (name.equals("windows") || name.equals("system32") ||
                            name.equals("node_modules") || name.equals("$recycle.bin")) {
                        continue;
                    }
                    if (isJavaDir(file)) {
                        foundDirs.add(file);
                        continue;
                    }
                    getJavaDirs(file, maxDepth - 1, foundDirs);
                }
            }
        }
    }

    private static final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    private static final String exeSuffix = isWindows ? ".exe" : "";
    private static boolean isJavaDir(File dir) {
        if (!dir.isDirectory()) {
            return false;
        }
        File java = new File(dir, "bin/java" + exeSuffix);
        File javaw = new File(dir, "bin/javaw" + exeSuffix);
        File releaseFile = new File(dir, "release");
        return (java.isFile() || javaw.isFile()) && releaseFile.isFile();
    }
}
