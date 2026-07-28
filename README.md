# 琅琅 Android

`recite.bdfz.net` 的原生 Android 客户端。不是 WebView 壳，也不是网页端改版。

## 当前能力

- Kotlin + Jetpack Compose 原生界面
- 78 篇高考古诗文随 APK 打包，断网可读
- 通读、填空、理解、默写、测验五阶段离线学习
- Room 本地进度库与幂等同步 outbox
- WorkManager 在网络恢复后同步至 BDFZ User Center
- Seiue 或既有 BDFZ 用户名登录
- 邀请码保护的直接用户名注册
- 手机底部导航；平板导航栏与篇目/练习双栏
- R2 直装版内置受校验更新；Play 版不申请侧载权限

## 技术栈

- Android Gradle Plugin 9.2.1
- Gradle 9.6.1
- JDK 17
- Kotlin 2.3.10（AGP 内建 Kotlin）
- Jetpack Compose BOM 2026.06.00
- compileSdk / targetSdk 37，minSdk 23
- Room 2.8.4、WorkManager 2.11.2、OkHttp 5.4.0

## 本地构建

```bash
export ANDROID_USER_HOME=/private/tmp/recite-android-home
export GRADLE_USER_HOME=/private/tmp/recite-gradle-home
./gradlew :app:testDirectDebugUnitTest :app:assembleDirectDebug
```

直装调试 APK：

```text
app/build/outputs/apk/direct/debug/app-direct-debug.apk
```

发布构建需要以下环境变量，值不得写入仓库：

```text
RECITE_ANDROID_KEYSTORE_PATH
RECITE_ANDROID_KEYSTORE_PASSWORD
RECITE_ANDROID_KEY_ALIAS
RECITE_ANDROID_KEY_PASSWORD
```

```bash
./gradlew :app:assembleDirectRelease :app:bundlePlayRelease
```

## 两个发行通道

| 通道 | applicationId | 更新方式 |
|---|---|---|
| R2 直装 | `net.bdfz.recite.direct` | `img.bdfz.net` 第一方清单 + 内容寻址 APK |
| Google Play | `net.bdfz.recite` | Play 商店；无 `REQUEST_INSTALL_PACKAGES` |

直装版与 Play 版故意使用不同 package，避免 Google Play 签名与站外签名互相破坏升级连续性。

## 文档

- [Cloudflare 后端分析](docs/CLOUDFLARE_ARCHITECTURE.md)
- [验证标准](docs/VERIFICATION.md)
- [发布与回滚](docs/RELEASE.md)
- [隐私说明](docs/PRIVACY.md)

代码采用 MIT License。`app/src/main/assets` 内的语料与试题不自动适用 MIT，权利归原始权利人。
