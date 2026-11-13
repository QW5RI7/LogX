# LogX Launcher

![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)
![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)
![JavaFX](https://img.shields.io/badge/JavaFX-17.0.6-blueviolet.svg)
![UI](https://img.shields.io/badge/UI-JavaFX%20%2B%20JFoenix-ff69b4.svg)
![Build](https://img.shields.io/badge/Build-Maven-green.svg)

**LogX** 是一款基于 JavaFX 构建的轻量级、高性能 Minecraft 第三方启动器。项目旨在提供极速的下载体验、智能的 Java 环境管理和现代化的用户界面。

> **项目状态**：核心引擎（下载、版本解析、启动逻辑、环境管理）已基本完成。UI 界面与微软正版登录（基于 `MinecraftAuth`）正在火热开发中。

## 核心特性

### 极速且健壮的下载引擎
- **双重并发策略**：基于 Java 11+ `HttpClient` 和 `CompletableFuture` 纯异步实现。小文件采用内存直写（减少磁盘 I/O），大文件自动切块（Range）多线程并发下载。
- **智能降级与容错**：当服务器不支持分块下载（返回 206 错误）或遇到限流时，自动平滑降级为单线程下载；自带失败重试机制。
- **严格的安全校验**：所有下载文件均支持 SHA1 / SHA256 校验，校验通过后才会重命名为正式文件，确保游戏文件永不损坏。
- **双线程池调度**：自动区分大文件与小文件，分配至不同的线程池，避免小文件下载被大文件阻塞。

### 全自动 Java 环境管理
- **并发扫描**：多线程深度扫描系统环境变量、默认安装路径、各大启动器缓存目录，精准识别 Java 8 - 25 及各大发行版（Oracle, Adoptium, GraalVM 等）。
- **智能下载安装**：内置 `Adoptium` API 解析，当缺少对应版本的 Java 时，自动通过**清华镜像**极速下载 JRE 并静默解压到启动器专属目录，免去用户手动配置烦恼。

### 全版本游戏兼容与启动
- **跨时代版本支持**：完美兼容古老版本（如 1.12.2 及以前的 `minecraftArguments` 格式）与最新版本（如 26.3 及以后的 `arguments` 复杂树状结构）。
- **规则引擎**：内置 `RuleUtil`，精准处理不同操作系统（Windows, macOS, Linux）下的 JVM 参数和库文件加载规则。
- **原生库自动化**：自动识别并提取 `natives` 原生动态链接库，支持版本隔离与非隔离模式自由切换。

### 现代化的沉浸式 UI
- **无边框窗口**：基于 JavaFX 与 JFoenix 构建，支持自定义标题栏、窗口拖拽与边缘丝滑缩放（`ResizeHelper`）。
- **丝滑动画**：高度还原 HMCL 的窗口交互，拥有流畅的启动进场、最小化及关闭过渡动画。
- **矢量图标**：集成 **Ikonli** 图标库，提供高清、流畅的 Lucide 矢量图标体验。
- **动态背景**：支持自定义图片背景与默认随机图集，并自动叠加半透明遮罩与高斯模糊效果。

### 极致单文件封装
- **一体化分发**：项目内置了高级的 Maven 构建脚本，通过 `maven-antrun-plugin` 和 PowerShell 脚本，将 `.exe` 包装器与 Java 运行环境及 Fat Jar 进行**二进制级拼接**。
- **双击即玩**：最终打包产物为单个 `.exe` 文件，玩家无需预装 Java 环境，彻底告别环境配置烦恼。

##  技术栈

- **开发语言**: Java 17
- **前端/UI**: JavaFX 17.0.6, JFoenix 9.0.10, Ikonli 12.3.1
- **网络通信**: Java `java.net.http.HttpClient`
- **JSON 解析**: Jackson 3.x (`tools.jackson`)
- **本地存储**: MyBatis 3.5.19 + SQLite (账号与配置持久化)
- **日志系统**: SLF4J + Logback 1.6.3 (按时间与大小滚动，支持自动清理)
- **认证协议**: Microsoft OAuth 2.0 设备代码流 (`MinecraftAuth 5.0.0`)
- **构建工具**: Maven (Shade Plugin + Antrun Plugin)

## 核心架构一览

```text
io.github.qw5ri7.logx
├── auth/           # 认证模块 (离线登录已完成，微软正版登录预留)
├── core/           # 核心业务 (DownloadManager 下载调度, GameLauncher 启动拼装, VersionInstaller 安装)
├── java/           # Java 环境 (JavaScanner 扫描, JavaInstaller 镜像安装)
├── mapper/         # MyBatis 数据层 (SQLite 数据库交互)
├── model/          # 数据实体 (配置类、任务类、JSON 映射类)
├── net/            # 网络请求 (HttpDownloader 分块下载, VersionFetcher 版本清单)
├── ui/             # UI 模块 (JavaFX 视图与动画)
└── util/           # 工具类 (配置读取、哈希校验、参数构建等)
```

## 快速开始

### 环境要求
- **JDK 17** 或更高版本（开发环境）
- **Maven** 3.6+
- **PowerShell** (仅限 Windows 下生成单文件 EXE 时需要)

### 编译与运行
1. 克隆仓库：
   ```bash
   git clone https://github.com/QW5RI7/LogX.git
   ```
2. 导入项目至 IntelliJ IDEA。
3. 等待依赖加载完成，找到 `Launcher.java` 并运行 `main` 方法即可在 IDE 中启动项目。

### 打包构建
在项目根目录执行以下命令：
```bash
mvn clean package
```
构建完成后，在 `target/` 目录下你将获得：
- `logx-1.0-SNAPSHOT.jar` (包含了所有依赖的 Fat Jar)
- `logx-1.0-SNAPSHOT.exe` (基于二进制拼接技术生成的单文件可执行程序)

## 开发进度 (Roadmap)

- [x] 多线程分块下载引擎与断点容错
- [x] Java 运行时自动扫描与镜像下载安装
- [x] 复杂版本 JSON 解析、依赖下载与 Natives 提取
- [x] 现代化无边框 UI 与丝滑窗口动画
- [x] 离线账号存储与启动参数拼装
- [x] 引入 `MinecraftAuth 5.0.0` 依赖
- [ ] **完成接入 Microsoft OAuth 2.0 正版登录逻辑**
- [ ] 整合包 (Modpack) 一键安装支持
- [ ] 模组与资源包快捷管理界面

## 开源协议

本项目基于 [MIT License](LICENSE) 协议开源。

*声明：LogX 为第三方开源项目，与 Mojang Studios 及 Microsoft 无任何从属关系。请支持并购买 Minecraft 正版游戏。*
```
