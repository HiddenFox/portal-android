# TASK-005 DeliveryGuard 投递守护（WorkManager）

## 目标
手机=投递守护（协议性逻辑）：扫描本地录音目录 → 去重（md5）→ 投递中转 → 记账 → 重试。不含业务逻辑（分析/转写/CRM 全在电脑端）。

## 协议设计（与电脑端契约）
1. 账本：filesDir/delivery/ledger.json，key=文件相对路径+md5，value=状态（pending/sent/skipped）。dedup 靠账本，重试靠账本。
2. 完成语义：成功 put 到中转即完成（sent）。不做端到端 ACK。中转目录空 = 全链路闭环。
3. 幂等：文件名+md5 为投递键，重传不产生重复记录。
4. 扫描源：MediaStore 录音目录（MVP：固定 path pattern + MediaStore Audio）。
5. 投递目标：中转端点可配置（MVP 先飞书云盘 drive 直传 /open-apis/drive/v1/medias/upload_all，folder_token 可配；Town workspace upload 为备选——等 device token 通路）。
6. WorkManager 约束：电池不敏感（Battery NOT_LOW）、网络 UNSPECIFIED，PeriodicWorkRequest 15 分钟一次 + 失败指数退避（30s/2m/10m）。

## 验收（桌面）
- 账本读写/去重/重试决策单测（假 Worker）。
- 中转端点用 mock HTTP 测试。

## 真机验收（人类伙伴）
- VIVO 投递真录音 1 个，账本记账、中转收到、文件 md5 一致。
- 15 分钟周期扫描重复投递被 dedup。

## 不做
- 说话人分离、转写（电脑端）；业务逻辑（分析/CRM）。
