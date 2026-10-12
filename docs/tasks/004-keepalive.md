# TASK-004 保活与 VIVO 白名单引导

## 目标
锁屏/切后台长连不段连（WSS 长连接不掉），OEM 后台限制引导。

## 实现背景（实测坑）
- VIVO 真机实测：不进白名单锁屏 1 分钟断连（2026-10-11 heart-portal Termux 阶段实测）；前台 Service + WakeLock 只能部分缓解，OEM 高耗电白名单必须引导用户手动设置。

## 实现
1. 检测：读 Settings（PowerManager.isIgnoringBatteryOptimizations）+ 连接断开次数计数。
2. 断连 > 阈值（3 次/小时）弹引导页：VIVO 后台高耗电白名单设置路径（设置 → 电池 → 后台高耗电）+ 常规厂商检测。
3. 引导页按钮直达设置 Intent（Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS）。
4. 压测清单（真机验收项）：锁屏 10 分钟、锁屏 1 小时、杀进程恢复、重启后自动启动（RECEIVE_BOOT_COMPLETED）。

## 验收
- 引导触发逻辑单测（阈值判定、Intent 构造）。
- 真机压测由人类伙伴按清单执行，结果记录回填任务书。
