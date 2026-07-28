# 琅琅 Android 验证标准

## 1. Source of truth

- 源码：本仓库 `main`
- 包名：直装 `net.bdfz.recite.direct`；Play `net.bdfz.recite`
- 语料：`app/src/main/assets/corpus.json`
- 学习清单：`app/src/main/assets/learning-manifest.json`
- 云端进度：User Center `siteKey=recite`
- 更新清单：`https://img.bdfz.net/apps/recite-android/latest.json`

## 2. Health probe

```bash
curl -sS https://recite.bdfz.net/api/health | jq .
curl -sS https://recite.bdfz.net/api/learning/health | jq .
curl -sS https://img.bdfz.net/apps/recite-android/latest.json | jq .
```

App 启动后必须在飞行模式下列出 78 篇并打开原文。

## 3. Contract checks

```bash
jq '.pieces | length' app/src/main/assets/corpus.json
jq '{siteKey,itemCount,totalStages,manifestVersion,resourceKeyHash}' \
  app/src/main/assets/learning-manifest.json
./gradlew :app:testDirectDebugUnitTest
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

## 5. Dependency regression

- 手机：compact 布局、底部导航、键盘不遮挡提交按钮
- 平板：至少 800dp 宽，导航栏 + 篇目列表 + 练习双栏
- 离线：五阶段可用，杀进程后本机进度保留
- 联网：登录后本机 outbox 排空，User Center 读回不倒退
- `recite.bdfz.net` 网页端进度仍正常
- User Center Student Growth 的 recite 来源健康检查仍通过

## 6. Backup and restore

- 发布前保存上一个 Git tag、GitHub Release、R2 `latest.json` 和对应不可变 APK key。
- D1 不做 destructive migration；App 只调用既有 progress upsert。
- 本机数据库升级必须先写 Room migration 和升级保留测试。

## 7. Rollback

- GitHub：重新标记上一稳定 commit，保留失败版本供审计。
- R2：把 `latest.json` 恢复为上一版本的 immutable `downloadUrl` 和 SHA-256；不可覆盖旧 APK。
- Play：停止 rollout 或通过 Play Console 回滚；不使用 R2 更新 Play flavor。
- 设备：安装更高 `versionCode` 的修复版；Android 不允许普通降级覆盖。

## 8. Last verified

2026-07-28：

- AGP 9.2.1 / Gradle 9.6.1 / JDK 17 / API 37 构建成功
- Direct Debug APK SHA-256 `babe16436881edc3438cb0f454b86e37d93c2d77c91d7b33ca3d43a90f8215ce`
- 7 项单元测试通过（离线评分、数据库进度、登录会话和 Stublogs 注册契约）
- emulator-5554：1080×2400 compact 与 2560×1600 / 320 dpi expanded 布局已读取
- 离线 `p1` 通读后 Room `stage=1, readPercent=100`
- 未登录时 outbox 保留 1 条、未产生云端写入
- Direct Release v0.1.0 / code 1 已用组织签名构建；APK v1/v2 签名有效
- Release APK SHA-256 `cebee8e670f3575c1cd0dca1a2f1f081e2e2cf0b421877efbe22fc8d4c0d3b4f`
- GitHub Release 与 R2 不可变 APK 已发布，公开回下载 SHA-256 与清单一致
- 从公开 R2 下载的 release APK 在 API 35 模拟器安装、冷启动并离线列出 78 篇

以下仍未通过，不得称为 production-ready：

- 物理手机和真实平板
- 真实 Seiue 登录与跨重启读回
- 同签名高版本覆盖安装并保留本机数据
- Play Console 预发布报告
