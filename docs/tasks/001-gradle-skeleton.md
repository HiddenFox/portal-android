# TASK-001 Gradle 工程骨架 + portal 二进制集成

## 目标
可构建的 Android 工程骨架，libportal.so 集成进 jniLibs，`./gradlew assembleDebug` 产出 APK。

## 前置事实（不需要重做）
- 二进制已验证：heart-portal NDK bionic aarch64 动态链接版（2026-10-11 GHA 构建，10,986,328B，e_type=3 PIE，PT_INTERP=/system/bin/linker64，NEEDED 仅系统库）。本地产物先找 `dir /s /b D:\Workspace heart-portal-android*`（cak-ci 检出目录或 D:\Workspace\code\cak-ci）；找不到再从 GHA run 38139921541（cak-ci 仓库 hp-android-smoke 分支）下载。
- 二进制改名 libportal.so 放 `app/src/main/jniLibs/arm64-v8a/`。ABI 只 arm64-v8a。
- minSdk 26 / targetSdk 36 / Kotlin 2.x / AGP 8.x 最新稳定版。applicationId `town.beings.portal`，包名 `town.beings.portal`。
- 工程结构：单模块 `app`（com.android.application + org.jetbrains.kotlin.android）。入口 Activity 先用模板 MainActivity（TASK-003 重写）。

## 步骤
1. Gradle 工程骨架：settings.gradle.kts / build.gradle.kts / app/build.gradle.kts / AndroidManifest.xml（声明前台 Service 权限 FOREGROUND_SERVICE + INTERNET + WAKE_LOCK，VIVO 兼容）。
2. 二进制集成：找本地产物，sha256 校验记录进 docs/binaries.md，改名 libportal.so 入 jniLibs。
3. 验证 `nativeLibraryDir` 解压逻辑（构建产物 APK unzip 检查 lib/arm64-v8a/libportal.so 完整性 + sha256 与源一致）。
4. Gradle wrapper（gradle-8.x）可 `./gradlew assembleDebug`。

## 验收（桌面可验）
- `./gradlew assembleDebug` 成功产出 app-debug.apk
- APK 内 lib/arm64-v8a/libportal.so 存在且 sha256 == 源二进制
- `aapt dump badging` 包名/权限正确

## 真机验收（人类伙伴执行，另行单独列）
- VIVO V2366HA（Android 16）安装启动无 crash。

## 不做
- PortalService / Activity 功能（TASK-002/003）
- 签名发布流程（debug keystore 即可）
