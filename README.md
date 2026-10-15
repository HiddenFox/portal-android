# portal-android

heart-portal 的 Android 壳。把 NDK bionic 构建的 portal 二进制包成普通用户可装的 App：粘贴连接链接 → 点启动 → 前台 Service 常驻拉起 portal 进程，手机即成为 Heart 可远程触达的 Portal 节点。

**当前版本 v0.1.8**（[releases](https://github.com/HiddenFox/portal-android/releases)）｜主测机：Redmi Note 13（HyperOS）

## 产品定位：纯连接器（2026-10-13 与人类伙伴拍板）

App 只做一件事：**保持 portal 连接活着**。不内置业务逻辑。

- 配置只有一项：Being Loom connect link（粘贴后写入 `filesDir/portal.toml` 的 `connect` 字段，App 校验后自动重启服务）。
- 手机上的能力边界 = portal 本身的能力（远程 exec、文件读写，经 relay WSS 出站连接，无入站端口）。
- 已实证的核心用例：Heart 经 relay 远程读取手机本地文件（MIUI 通话录音 FUSE 直读，md5 与桌面副本一致）。
- **不做**：录音事件监听（FileObserver+上行回调不进本 App——连接器不内置业务监听器）、投递扫描（DeliveryGuard 已在 TASK-006 整体剥离）、Play Store 合规、多用户。

## 架构

- **二进制层**：heart-portal NDK bionic aarch64 构建（GHA 产出），改名 `libportal.so` 放 `jniLibs/arm64-v8a/`，Android 打包器自动解压到 nativeLibraryDir 并带执行权限。
- **PortalService**（前台 Service）：`startForegroundService` + 通知常驻；ProcessBuilder 启动 libportal，注入 `SSL_CERT_FILE`（内置 CA bundle，修 Android 无系统 CA 路径问题）；进程退出 5s 自动重启；启动时打印版本与权限状态日志。
- **MainActivity**：Material 3 单页——状态区（进程/relay）+ connect link 输入框 + 启动/停止。中英双语（`values-zh`）。
- **权限模型**（v0.1.3）：`RuntimePermissions` 按 API 级别给出权限表（13+：READ_MEDIA_AUDIO + POST_NOTIFICATIONS；12-：旧存储权限），每次启动查缺补弹、合并申请；永久拒绝显示提示条直达应用设置。录音权限用于远程读取通话录音。
- **保活**：前台 Service + WakeLock/WifiLock；`KeepAliveGuide` 按厂商给白名单指引（VIVO/小米/华为）；每次启动查电池优化白名单，未设置显示横幅直达。
- **远程启动钩子**：`am start --ez town.beings.portal.AUTOSTART true` 走与启动按钮相同的代码路径（MIUI 拦 adb 拉起非导出 foreground service，此钩子绕过）。
- **workspace**：`filesDir/workspace`（app 私有目录，免存储权限）。

## MIUI/HyperOS 实测边界（Redmi Note 13）

- 录音文件不进 MediaStore（查询 0 行），须 FUSE 直读 `/sdcard/MIUI/sound_recorder/call_rec/`（需 READ_MEDIA_AUDIO 运行时授权，用户弹窗点允许后永久生效）。
- `pm grant`、`content` 查询、`am start-foreground-service`、`input tap/keyevent`（需「USB 调试(安全设置)」）均被拦。`install -r` 会杀 app 进程，服务须从 UI 或 AUTOSTART 钩子重启。
- 安装：MIUI 拦部分 adb install（INSTALL_FAILED_USER_RESTRICTED），兜底 `adb push /sdcard/Download + am start VIEW` 拉起系统安装器。

## 任务板与历史

`docs/tasks/` TASK-001~006 任务书与 build notes。版本线：

- v0.1.0：service + 基础 UI（TASK-001~003）
- v0.1.1：向导式 UI + relay 状态行 + 应用内日志（da405ec）
- v0.1.2：Material 3 重设计、DeliveryGuard 剥离、connect link 单一配置（3cdc23e/fd4c74f）
- v0.1.3：权限自动化三件套 + AUTOSTART 钩子 + MIUI 录音直读（15cebeb，28/28 单测）
- v0.1.8：relay scheduling-gap diagnostics, validated-network recovery observation, relay lifecycle timestamps, and first-launch notification/battery/Xiaomi autostart checklist.

## 相关线

- heart-portal 主仓：`D:\Workspace\code\heart-portal`（android-termux-support 分支）
- 构建 CI：cak-ci 仓库 hp-android-smoke 分支（GHA，NDK r27）
- 转写：SenseVoice API 质量够用（VIVO 真机录音实测）；转写调度在电脑端，不在本 App
