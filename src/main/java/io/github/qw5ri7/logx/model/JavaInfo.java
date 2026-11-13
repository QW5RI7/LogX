package io.github.qw5ri7.logx.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JavaInfo {
    /**
     * Java 版本号，比如 "17.32.1"，"1.8.0_361"等
     */
    private int majorVersion;
    /**
     * Java 的主路径，比如 "C:\\Program Files\\Java\\jdk-17.32.1"等
     */
    private String path;
    /**
     * Java 类型，JDK 或 JRE
     */
    private JavaType type;
    /**
     * Java 发布商，比如 Oracle 或 OpenJDK等
     */
    private String vendor;
    /**
     * 是否为 64 位 Java 版本
     */
    private boolean is64Bit;
    public String getExecutablePath() {
        String exeName = System.getProperty("os.name").toLowerCase().contains("win") ? "javaw.exe" : "java";
        return path + java.io.File.separator + "bin" + java.io.File.separator + exeName;
    }
}
