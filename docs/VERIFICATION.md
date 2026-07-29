# 琅琅 Android 验证标准

## 1. Source of truth

- 源码：本仓库 `main`
- 包名：直装 `net.bdfz.recite.direct`；Play `net.bdfz.recite`
- 语料：`app/src/main/assets/corpus.json`
- 学习清单：`app/src/main/assets/learning-manifest.json`
- 云端进度：User Center `siteKey=recite`
- 排行 API：`https://recite.bdfz.net/api/rankings`
- 排行 Worker / D1：`recite-rankings`
- 运行观测：Pulse `recite.bdfz.net` + script-only `recite-rankings`
- 更新清单：`https://img.bdfz.net/apps/recite-android/latest.json`

## 2. Health probe

```bash
curl -sS https://recite.bdfz.net/api/health | jq .
curl -sS https://recite.bdfz.net/api/learning/health | jq .
curl -sS https://recite.bdfz.net/api/rankings/health | jq .
curl -sS 'https://recite.bdfz.net/api/rankings?limit=20' | jq .
curl -sS https://img.bdfz.net/apps/recite-android/latest.json | jq .
curl -sS https://pulse.bdfz.net/api/meta \
  | jq '.registry[] | select(.host == "recite.bdfz.net")'
curl -sS 'https://pulse.bdfz.net/api/range?from=<FROM>&to=<TO>' \
  | jq '[.sites[] | select(.host == "recite.bdfz.net" or .script == "recite-rankings")]'
curl -sS https://pulse.bdfz.net/api/live | jq .
```

App 启动后必须在飞行模式下列出 78 篇并打开原文。

## 3. Contract checks

```bash
jq '.pieces | length' app/src/main/assets/corpus.json
jq '{siteKey,itemCount,totalStages,manifestVersion,resourceKeyHash}' \
  app/src/main/assets/learning-manifest.json
./gradlew :app:testDirectDebugUnitTest
(cd cloudflare/rankings && npm run check && npm run deploy:dry)
```

期望：78 篇、`siteKey=recite`、`totalStages=5`。

## 4. Build and forbidden actions

```bash
./gradlew :app:lintDirectDebug :app:testDirectDebugUnitTest \
  :app:assembleDirectRelease :app:bundlePlayRelease
```

禁止：

- 用 WebView 代替原生学习界面
- 把密码、cookie、invite code 或签名材料写入仓库
- Play flavor 申请 `REQUEST_INSTALL_PACKAGES`
- 把未签名、未校验或可变 URL 的 APK 发布为 latest
- 未验证升级保留数据就提高 `minimumSupportedVersionCode`
- 让 App 上报可任意伪造的总段位值
- 在排行 D1 保存原始用户名、cookie、会话、密码或 Seiue 标识

## 5. Dependency regression

- 手机：compact 布局、底部导航、键盘不遮挡提交按钮
- 平板：至少 800dp 宽，导航栏 + 篇目列表 + 练习双栏
- 离线：五阶段可用，杀进程后本机进度保留
- 联网：登录后本机 outbox 排空，User Center 读回不倒退
- 排行：未登录 GET 可读公开榜，未登录 POST 必须 401；登录 POST 只从 User Center 进度计算本人快照
- 段位：315 为殿堂金框，375 为巅峰紫青框；边界单元测试必须通过
- `recite.bdfz.net` 网页端进度仍正常
- User Center Student Growth 的 recite 来源健康检查仍通过
- Pulse 同时显示 `recite-gk` 的公开站点流量、`recite-rankings` 的基础设施流量和 `siteKey=recite` 的 User Center 聚合；不得把请求数当用户数

## 6. Backup and restore

- 发布前保存上一个 Git tag、GitHub Release、R2 `latest.json` 和对应不可变 APK key。
- 排行 D1 只做 additive migration；发布前记录 Worker version、migration 清单和聚合行数，不导出公开代号。
- User Center D1 不做 destructive migration；App 只调用既有 progress upsert。
- 本机数据库升级必须先写 Room migration 和升级保留测试。

## 7. Rollback

- GitHub：重新标记上一稳定 commit，保留失败版本供审计。
- R2：把 `latest.json` 恢复为上一版本的 immutable `downloadUrl` 和 SHA-256；不可覆盖旧 APK。
- 排行：回滚 `recite-rankings` Worker 到上一 version 或移除该窄路径 route；已有快照默认保留，不因代码回滚删除 D1。
- Play：停止 rollout 或通过 Play Console 回滚；不使用 R2 更新 Play flavor。
- 设备：安装更高 `versionCode` 的修复版；Android 不允许普通降级覆盖。

## 8. Last verified

2026-07-29：

- AGP 9.2.1 / Gradle 9.6.1 / JDK 17 / API 37 的 Direct release lint、R8 APK 与 Play AAB 构建成功。
- 12 项 Android 单元测试通过：离线评分 5、登录／反馈／排行榜网络契约 4、段位边界 3。
- `recite-rankings` 使用 Wrangler 4.115.0、TypeScript 7.0.2、Vitest 4.1.10；类型检查、4 项 Worker 测试和 dry deploy 通过。
- 排行 Worker version `c1b94690-39b7-45c9-b84e-66f48c682374` 已部署；公开 GET 200、匿名 POST 401、health 200。
- 独立 APAC D1 `recite-rankings` 首次认证同步后聚合为 1 行、总段位值 5、今日值 0；重复同步未制造重复行，未读取或输出公开代号以外的身份字段。
- `recite.bdfz.net/api/health` 与 `/api/learning/health` 部署后仍为 200，78-item learning evidence receipt 保持 active。
- OnePlus 8 Pro `172.16.193.27:39553`：从公开 R2 下载的同签名 v0.1.1 / code 2 覆盖安装成功，Room 进度、加密登录会话与总榜本人快照保留；公开包冷启动 428 ms。
- 实机原生交互验收通过：当前学习阶段、纵向五阶路径、已完成阶段回看、今日／总榜切换。
- 同一实机临时使用 1600×2560 / 240 dpi 验证 expanded 布局：左侧导航、双栏学习和限宽排行榜均可操作；随后已恢复物理 1080×2376 / 450 dpi。
- App 反馈的真实 canary 已由 User Center 保存并返回 Telegram `sent=true`；APK 不含 Bot 凭据。
- GitHub Actions run `30425879074` 通过；GitHub Release [`v0.1.1`](https://github.com/ieduer/recite-android/releases/tag/v0.1.1) 与 R2 `latest.json` 已公开读回。
- 当前公开 Direct APK 为 2,512,871 bytes，SHA-256 `54a893373cf1a22215832fe387133d057f1fcd9c281c05835e94b5f9812317b0`；v1/v2 签名有效，证书 SHA-256 `508429787cb0605f73c9fe423324fd14bc60873802f8d5e167d591bfc352fe0d`。

以下仍需商店侧关闭：

- Google Play Console 建档、Data Safety、内容分级、商店素材与预发布报告
- 一台真实 Android 平板的最终人工验收；当前已有 expanded 实机显示覆盖和既有平板模拟验证
