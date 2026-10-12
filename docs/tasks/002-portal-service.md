# TASK-002 PortalService 前台 Service

## 目标
前台 Service 拉起 libportal 进程并保活：ProcessBuilder 启动 + 退出监听 + 5s 自动重启 + 通知栏常驻。

## 读什么
- D:\Workspace\code\heart-portal 仓库 android-termux-support 分支：main.rs 的 CLI 参数（--config、--version、status 子命令）、portal.toml 配置结构（token、workspace 路径配置键名以代码为准）。
- TASK-001 产出的工程骨架。

## 实现
1. `PortalService.kt`：startForegroundService，通知渠道 portal，常驻通知（显示连接状态）。
2. 启动逻辑：从 `applicationInfo.nativeLibraryDir` 取 libportal.so 路径，`ProcessBuilder(arrayOf(libPath, "--config", configFile))` 启动，configFile = filesDir/portal.toml。
3. 退出监听：`process.waitFor()` 后台线程 + `onDestroy` 杀进程（process.destroy()，SIGTERM）；退出非正常码则 5s 后自动重启（循环 + 退避上限 3 次放弃）。
4. WakeLock（PARTIAL）+ WifiLock（WIFI_MODE_FULL_HIGH_PERF）持到进程稳定。
5. 状态上报：Service 广播 Intent（连接状态 + PID），MainActivity（TASK-003）接收。

## 验收（桌面）
- PortalService 单元测试：启动/退出/重启决策逻辑（用假进程 stub）。
- assembleDebug 通过。

## 真机验收（人类伙伴）
- VIVO 启动 Service 后 `adb shell ps | grep portal` 或 status 子命令返回连接中；杀进程 5s 内自动重启；锁屏 1 分钟不断连（白名单前提下）。

## 不做
- 配置 UI（TASK-003）；保活引导页（TASK-004）。
