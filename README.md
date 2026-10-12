# portal-android

heart-portal 的 Android 产品化壳工程。把已验证的 NDK bionic 构建的 portal 二进制包装成面向普通用户的 Android App：自动加载 Portal 为后台 Service、配置 loom token，并提供录音等本地数据的可靠投递。

## 项目定位

两个职责，一个 App：

1. **Portal 壳**：启动 App 即拉起 portal 进程（前台 Service 常驻），用户无需命令行。token 配置走 App 配置页。
2. **投递守护**：带账本的采集器。扫描本地文件（如通话录音）→ 去重 → 投递到中转（飞书云盘/Town workspace）→ 记账 → 重试。协议性逻辑，不含业务逻辑。

## 设计哲学（与人类伙伴拍板）

- **手机 = 投递守护，电脑 = 工作空间**。手机上只放协议性逻辑（扫描/去重/传输/记账/重试，filebeat 模式）；业务性逻辑（分析、转写调度、CRM 对接）全在电脑端。
- 判断标准是可靠性而非位置：手机能无障碍闭环的部分（直传中转）放手机；一切需要频繁迭代的逻辑不进手机。
- 中转完成语义：手机 put 到中转即完成，不做端到端 ACK。中转目录空 = 全链路闭环。
- 幂等：文件名+md5 做投递键，电脑端按 md5 去重。

## 架构

- **二进制层**：heart-portal NDK bionic aarch64 构建（GHA workflow 产出，真机 VIVO V2366HA/Android 16 验证：--version/status 通过），改名 libportal.so 放 jniLibs/arm64-v8a/，Android 打包器自动解压到 nativeLibraryDir 并带执行权限。
- **PortalService**（前台 Service）：startForegroundService + 通知栏常驻；ProcessBuilder 启动 libportal（--config 指向 app 私有目录 toml）；onDestroy 杀进程；进程退出监听 5s 自动重启。
- **MainActivity**：开始/停止按钮、状态显示（relay 连接、MCP 端口）、配置页（token 粘贴 → filesDir/portal.toml）。
- **DeliveryGuard**（投递守护）：WorkManager，JSON 账本（文件路径+md5 → 状态 pending/sent），扫描→去重→投递→记账→重试。
- 保活：前台 Service + WakeLock/WifiLock（WSS 长连接不掉），VIVO 后台高耗电白名单引导页。
- workspace：filesDir/workspace（app 私有目录，免存储权限）。

## 不做（MVP）

Play Store 合规、多用户、fancy UI、MediaStore 桥接、入站端口监听（portal 是出站 WSS，无入站）。

## 任务板

docs/tasks/ 下按序号排列，TASK-001 起步。真机验收项在任务书内单列，由人类伙伴在 VIVO 真机执行。

## 相关线

- heart-portal 主仓：D:\Workspace\code\heart-portal（android-termux-support 分支）
- 构建 CI：cak-ci 仓库 hp-android-smoke 分支（GHA，NDK r27 工具链）
- SenseVoice 转写结论（2026-10-12）：API 质量够用（VIVO 真机录音实测），采集端走本 App 投递守护，转写调度在电脑端。
