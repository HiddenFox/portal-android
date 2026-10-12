# TASK-003 MainActivity 配置页与状态显示

## 目标
用户入口：开始/停止按钮、状态显示、token 配置页。token 写 filesDir/portal.toml。

## 实现
1. MainActivity：三元素——开始/停止（startForegroundService/stopService）、状态显示（绑定 TASK-002 广播：relay 连接状态、MCP 端口、进程 PID）、配置页。
2. 配置页：token 粘贴框（多行）→ 写 filesDir/portal.toml。toml 格式按 heart-portal 配置键名（以源码 config.rs 为准，读 D:\Workspace\code\heart-portal）。写后 Service 重启生效。
3. 不用 SharedPreferences——portal 直接读 toml。
4. workspace 目录：filesDir/workspace，Service 首次启动时创建。

## 验收（桌面）
- 配置写入单测：token 写入 → toml 解析回读一致。
- 状态显示单测：广播解析。

## 真机验收（人类伙伴）
- 粘 token 启动 → status 显示连接成功 → 桌面侧 portal_exec 下发命令（手机端 workspace 文件操作）。

## 不做
- Fancy UI；MediaStore 桥接（MVP 砍掉）。
