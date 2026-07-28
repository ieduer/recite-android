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
      -> recite.bdfz.net online grading endpoints
          -> APIS service binding -> shared Gemini gateway

Release:
GitHub source/tag/release
  -> signed direct APK -> R2 blog-images/apps/recite-android/releases/...
  -> latest.json -> immutable APK URL + SHA-256
Google Play:
  -> signed AAB, separate play flavor, Play-managed updates
```

## 能力判断

| 需求 | Cloudflare 方案 | 判断 |
|---|---|---|
| Seiue / BDFZ 登录 | 现有 `my.bdfz.net/api/login`；App 不保存密码 | 可满足 |
| 直接用户名注册 | 现有 Stublogs 注册契约、邀请码和限流；随后由 User Center 登录 | 可满足，但上架前需补网页账号删除入口 |
| 跨设备进度 | User Center D1 `/api/progress?site=recite` | 可满足 |
| 离线学习 | APK assets + Room + WorkManager | 不依赖 Cloudflare |
| 语音/图片/AI | `recite-gk` Worker + `APIS` | 在线能力可满足；不声称离线 AI |
| APK 更新 | R2 内容寻址对象 + `latest.json` + SHA-256 | 可满足 |
| 商店更新 | Google Play | Cloudflare 不能替代 |
| 推送通知 | Worker 可做业务触发，设备投递仍需 FCM | 需 Google FCM |
| Play Integrity / Billing | Google Play 服务 | Cloudflare 不能替代 |

## 容量与限制

- Workers 付费计划没有每日请求数上限，HTTP 请求体上限依计划为 100 MB 或以上；现有语音与图片请求必须继续设置业务层尺寸限制。官方限制：<https://developers.cloudflare.com/workers/platform/limits/>
- D1 付费单数据库上限 10 GB、账号总量 1 TB。单个数据库写入按单线程执行，所以进度写入必须保持短事务、索引和幂等。官方限制：<https://developers.cloudflare.com/d1/platform/limits/>
- D1 Sessions/bookmarks 可提供顺序一致和 read-your-writes；跨设备合并仍采用字段最大值与时间戳合并，而不是覆盖本机新进度。官方说明：<https://developers.cloudflare.com/d1/best-practices/read-replication/>
- R2 标准存储按量计费且互联网出口不收费，适合 APK 与不可变发布资产。官方定价：<https://developers.cloudflare.com/r2/pricing/>
- Turnstile 在原生移动端仍需受控 WebView 执行 JavaScript。首版沿用现有邀请码和服务端限流，不把整个 App 变成 WebView。官方说明：<https://developers.cloudflare.com/turnstile/get-started/mobile-implementation/>

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

合并规则：

- 阶段、阅读百分比、最佳分数和尝试次数取不会倒退的最大值
- 首次开始时间取最早值
- 最近活动和复习时间取最新值
- 401/403 停止重试并要求重新登录
- 5xx/断网由 WorkManager 有界重试

## 上架前未关闭的门槛

1. User Center 提供面向 App 用户的网页账号删除入口与可验证删除流程。
2. 用真实 Seiue 账号完成登录、同步、杀进程、重启、读回和退出。
3. 接入原生语音录制与在线评分时，完成麦克风权限、隐私披露、音频保留时长和删除策略。
4. 建立 Play Console、隐私政策 URL、Data Safety 表、内容分级和商店素材。
5. Play 版 AAB 通过预发布报告；R2 直装版通过签名连续升级。
