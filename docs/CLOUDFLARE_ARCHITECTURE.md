# Cloudflare 后端可行性与边界

## 结论

Cloudflare 可以满足琅琅 Android 的账号、进度同步、AI 网关、发布文件和运行观测需求。App 的离线核心不依赖 Cloudflare；Cloudflare 负责网络恢复后的同步和在线增强。

不新建每项目 Gemini key pool。在线 AI 继续通过 `apis.bdfz.net` / `APIS` 服务绑定。

## 目标架构

```text
Android UI
  -> packaged corpus + Room progress (always available)
  -> WorkManager outbox (network constrained)
      -> my.bdfz.net User Center
          -> D1 user/progress records
          -> existing growth-evidence projection
          -> /api/feedback -> server-side Telegram notification
      -> recite.bdfz.net online grading endpoints
          -> APIS service binding -> shared Gemini gateway
      -> recite.bdfz.net/api/rankings
          -> recite-rankings Worker
          -> USER_CENTER service binding verifies session and reads progress
          -> isolated recite-rankings D1 stores pseudonymous snapshots
  -> pulse.bdfz.net
      -> recite-gk Worker analytics for the public product runtime
      -> recite-rankings script-only analytics for the app-owned API
      -> USER_CENTER service binding supplies aggregate authenticated activity

Release:
GitHub source/tag/release
  -> signed direct APK -> R2 blog-images/apps/recite-android/releases/...
  -> latest.json -> immutable APK URL + SHA-256
Google Play:
  -> same-package signed AAB, separate play flavor, Play-managed updates
```

## 能力判断

| 需求 | Cloudflare 方案 | 判断 |
|---|---|---|
| Seiue / BDFZ 登录 | 现有 `my.bdfz.net/api/login`；App 不保存密码 | 可满足 |
| 登录即建帐号 | User Center 先验证 Seiue；没有对应用户时自动建立，既有 BDFZ 用户继续兼容登录 | 可满足；App 不维护独立注册入口或用户表 |
| 跨设备进度 | User Center D1 `/api/progress?site=recite` | 可满足 |
| 离线学习 | APK assets + Room + WorkManager | 不依赖 Cloudflare |
| 语音/图片/AI | `recite-gk` Worker + `APIS` | 在线能力可满足；不声称离线 AI |
| 段位与今日／总榜 | `recite-rankings` Worker + 独立 D1；从 User Center 已同步进度计算 | 可满足；App 不提交任意总分 |
| App 反馈 | User Center `/api/feedback` + 服务端 Telegram 路由 | 可满足；Bot 凭据不进入 APK |
| 运行观测 | Pulse：`recite.bdfz.net` / `recite-gk` + `recite-rankings`；User Center 仅经服务绑定输出聚合 | 可满足；请求量与用户数分开解释 |
| APK 更新 | R2 内容寻址对象 + `latest.json` + SHA-256 | 可满足 |
| 商店更新 | Google Play | Cloudflare 不能替代 |
| 推送通知 | Worker 可做业务触发，设备投递仍需 FCM | 需 Google FCM |
| Play Integrity / Billing | Google Play 服务 | Cloudflare 不能替代 |

## 容量与限制

- Workers 付费计划没有每日请求数上限，HTTP 请求体上限依计划为 100 MB 或以上；现有语音与图片请求必须继续设置业务层尺寸限制。官方限制：<https://developers.cloudflare.com/workers/platform/limits/>
- D1 付费单数据库上限 10 GB、账号总量 1 TB。单个数据库写入按单线程执行，所以进度写入必须保持短事务、索引和幂等。官方限制：<https://developers.cloudflare.com/d1/platform/limits/>
- D1 Sessions/bookmarks 可提供顺序一致和 read-your-writes；跨设备合并仍采用字段最大值与时间戳合并，而不是覆盖本机新进度。官方说明：<https://developers.cloudflare.com/d1/best-practices/read-replication/>
- R2 标准存储按量计费且互联网出口不收费，适合 APK 与不可变发布资产。官方定价：<https://developers.cloudflare.com/r2/pricing/>
- Turnstile 在原生移动端仍需受控 WebView 执行 JavaScript；本 App 不因此改成 WebView，也不另建注册入口。登录继续由 User Center 服务端完成 Seiue 验证、首次自动建号和限流。官方说明：<https://developers.cloudflare.com/turnstile/get-started/mobile-implementation/>

## 数据模型

本机：

- `piece_progress`: 每篇五阶段、最佳分数、尝试次数、活动时间
- `sync_outbox`: `clientMutationId`、payload、创建时间、重试次数

云端：

- 继续写 User Center `site_progress`
- `siteKey=recite`
- `itemKey=p1...p78`
- `schemaVersion=recite-progress-v2`
- `manifestVersion` 和 `resourceKeyHash` 必须与打包清单一致
- 排行快照位于独立 `recite-rankings` D1；只保存 HMAC 用户键、公开代号、段位值、完成／活跃篇数和日期
- 不保存原始用户名、显示名、头像、cookie、Seiue 标识或密码

合并规则：

- 阶段、阅读百分比、最佳分数和尝试次数取不会倒退的最大值
- 首次开始时间取最早值
- 最近活动和复习时间取最新值
- 401/403 停止重试并要求重新登录
- 5xx/断网由 WorkManager 有界重试

## 段位与榜单规则

- 78 篇 × 5 阶，段位值上限 390；服务端只读取 User Center 的 `siteKey=recite` 进度。
- 八段位为初识、启声、寻章、知音、博闻、文心、殿堂、巅峰。
- 殿堂启用金色头像框；巅峰启用紫青头像框。视觉为琅琅原创，不复用第三方游戏资产。
- 总榜按总段位值、完成篇数、活跃篇数排序。
- 今日榜只计当前北京时间自然日内服务器观察到的正向段位增量；首次建立快照不把历史进度算入今日榜。
- 排行同步最短间隔 10 秒；D1 upsert 只允许总值不倒退，重复请求不会重复增加今日值。
- `RANKING_PEPPER` 只保存在 Worker Secret，必须保持连续；轮换会改变公开代号，需按迁移方案执行。

## Cloudflare 边界

Cloudflare 足以承载当前账号桥接、同步、反馈、榜单、AI 网关和 R2 更新，但不能替代 Google Play 的商店签名、Play Integrity、Billing、预发布报告、商店内更新和 FCM 设备投递。离线学习继续以 Room 为本机权威，不把 D1 变成实时多主数据库。

Direct 與 Play 都使用 `net.bdfz.recite.direct`；Cloudflare 清單只服務
Direct flavor，Play flavor 不含側載權限。Play App Signing 必須沿用現有
app-signing identity，否則 Play 無法覆蓋 Direct 安裝，亦不允許以第二個
package 規避簽章連續性。

Pulse 不直接绑定 User Center D1。`siteKey=recite` 的用户、进度、记录和
反馈只通过版本化 `USER_CENTER` 服务绑定以站点级聚合进入 Pulse；
`recite-gk` 与 `recite-rankings` 则分别提供公开产品和 App 专属榜单 API
的 Worker 运行量与错误率。二者共同构成发布门槛，但不能把请求数当作
独立用户数。

## 上架前未关闭的门槛

1. User Center 提供面向 App 用户的网页账号删除入口与可验证删除流程。
2. 用真实 Seiue 账号完成登录、同步、杀进程、重启、读回和退出。
3. 接入原生语音录制与在线评分时，完成麦克风权限、隐私披露、音频保留时长和删除策略。
4. 建立 Play Console、隐私政策 URL、Data Safety 表、内容分级和商店素材。
5. Play 版 AAB 通过预发布报告；R2 直装版通过签名连续升级。
