# 琅琅 Android 验证标准

## 1. Source of truth

- 源码：本仓库 `main`
- 唯一包名：直装与 Play 都是 `net.bdfz.recite.direct`
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
curl -sS 'https://pulse.bdfz.net/api/meta?verify=<PULSE_VERSION_ID>' \
  | jq '.registry[] | select(.host == "recite.bdfz.net" or .script == "recite-rankings")'
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
  :app:assembleDirectRelease :app:assemblePlayRelease :app:bundlePlayRelease
```

禁止：

- 用 WebView 代替原生学习界面
- 把密码、cookie、invite code 或签名材料写入仓库
- Play flavor 申请 `REQUEST_INSTALL_PACKAGES`
- Direct 与 Play 使用不同 application id 或不同 app-signing identity
- 更新器未核对 schema、appId、不可变 URL、size、SHA-256、APK package、
  versionCode 与当前安装签章就打开系统安装器
- 把未签名、未校验或可变 URL 的 APK 发布为 latest
- 未验证升级保留数据就提高 `minimumSupportedVersionCode`
- 让 App 上报可任意伪造的总段位值
- 在排行 D1 保存原始用户名、cookie、会话、密码或 Seiue 标识

## 5. Dependency regression

- 選定一台登記實體手機：OnePlus 9 Pro `LE2120` 或 OnePlus 8 Pro
  `IN2020`，以 hardware serial 固定目標，跑同一公開 APK 的低 code → 高 code
  原地更新、冷啟、前後台、Back、離線/恢復、Room/session/outbox 保留、
  自更新及 scoped fatal/ANR；第二台手機與 emulator 只作補充
- 同一手機先記錄 size/density/rotation/font/global + per-network
  proxy/keep-awake，再以可逆方式取得 App-observed maxWidth ≥840dp，驗證
  200% 字級、橫竖屏、多窗口與導航欄 + 篇目列表 + 練習雙欄，最後逐項恢復
  並讀回原始基線
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
  不卸載 canonical package，不清資料，也不另發第二個 application id。

## 8. Last verified

2026-07-29：

- AGP 9.2.1 / Gradle 9.6.1 / JDK 17 / API 37 的 Direct release lint、R8 APK 与 Play AAB 构建成功。
- 17 项 Android 单元测试通过：更新清单 5、离线评分 5、登录／反馈／排行榜网络契约 4、段位边界 3。
- OnePlus 9 Pro `LE2120 / c5467d2b / API 34` 与 OnePlus 8 Pro
  `IN2020 / 6393cccf / API 30` 均从公开 v0.1.1/code 2 经 App 内更新至
  v0.1.2/code 3，再经 App 内更新至 v0.1.3/code 4；两次均走下载、校验、
  系统安装器与安装后自检，没有卸载或清除数据。
- 两台 v0.1.3 冷启动分别为 336 ms 与 261 ms；学习进度保持为
  `1% / 0 完成 / 2 进行中` 与 `1% / 0 完成 / 3 进行中`。OnePlus 8 Pro
  的加密登录会话与空 outbox 保留；两台安装后手动检查都显示当前为最新版本。
- 两台首次安装时间与 user-0 `ceDataInode` 在两次覆盖升级后均未改变；
  设备上只有 `net.bdfz.recite.direct`，不存在第二个琅琅包名。两台拉回的
  installed base APK 均为 2,529,258 bytes，SHA-256
  `94d4ac0c02c52e9a8a9d19a587213dfadf0df56c2cf019af542ef42de9f46e23`，
  与公开 R2 对象一致。
- v0.1.3 修正手机学习详情页的 Android 系统返回键；两台均验证一次返回后
  留在 `MainActivity` 并回到篇目列表。覆盖更新、冷启动、返回与更新自检期间，
  scoped logcat 未见 App fatal/ANR。
- `recite-rankings` 使用 Wrangler 4.115.0、TypeScript 7.0.2、Vitest 4.1.10；类型检查、4 项 Worker 测试和 dry deploy 通过。
- 排行 Worker version `c1b94690-39b7-45c9-b84e-66f48c682374` 已部署；公开 GET 200、匿名 POST 401、health 200。
- 独立 APAC D1 `recite-rankings` 首次认证同步后聚合为 1 行、总段位值 5、今日值 0；重复同步未制造重复行，未读取或输出公开代号以外的身份字段。
- `recite.bdfz.net/api/health` 与 `/api/learning/health` 部署后仍为 200，78-item learning evidence receipt 保持 active。
- OnePlus 8 Pro `172.16.193.27:39553`：从公开 R2 下载的同签名 v0.1.1 / code 2 覆盖安装成功，Room 进度、加密登录会话与总榜本人快照保留；公开包冷启动 428 ms。
- 实机原生交互验收通过：当前学习阶段、纵向五阶路径、已完成阶段回看、今日／总榜切换。
- 同一实机临时使用 1600×2560 / 240 dpi 验证 expanded 布局：左侧导航、双栏学习和限宽排行榜均可操作；随后已恢复物理 1080×2376 / 450 dpi。
- App 反馈的真实 canary 已由 User Center 保存并返回 Telegram `sent=true`；APK 不含 Bot 凭据。
- User Center 聚合读回确认 `source=recite-android` 为 1 名用户、3 笔进度；Pulse 通过 `USER_CENTER` 服务绑定显示 `siteKey=recite` 共 7 名用户、26 笔聚合资料列。
- Pulse version `1e70e9ba-4de6-4845-b458-9e7ac17fd99b` 已将 `recite-rankings` 作为独立 App 后端输出为 `worker_analytics / tracked_zero`；同期 `recite-gk` 为 122 次请求、0 错误，公开站点缺失数为 0。
- GitHub Actions run `30504601737` 通过；GitHub Release [`v0.1.3`](https://github.com/ieduer/recite-android/releases/tag/v0.1.3) 与 R2 `latest.json` 已公开读回。
- 当前公开 Direct APK 为 2,529,258 bytes，SHA-256 `94d4ac0c02c52e9a8a9d19a587213dfadf0df56c2cf019af542ef42de9f46e23`；v1/v2 签名有效，证书 SHA-256 `508429787cb0605f73c9fe423324fd14bc60873802f8d5e167d591bfc352fe0d`。

以下仍需商店侧关闭：

- Google Play Console 建档、Data Safety、内容分级、商店素材与预发布报告
- 选定登记手机上的最终可逆 expanded-layout 人工验收；当前历史 expanded
  实机与平板模拟证据仍只作补充，须按现行设置保存／恢复标准重跑
