# 发布、更新与回滚

## 版本规则

- `versionName`: 三段数字，例如 `0.1.0`
- `versionCode`: 严格递增的正整数
- 直装和 Play 从同一源码版本构建，统一使用
  `net.bdfz.recite.direct` 与同一 app-signing identity
- 更新通道与权限分离：Direct 使用 R2，Play 使用 Play managed update
- 同一产品禁止以第二个 application id 安装；发布前必须核对设备只存在
  一个 canonical package

## 签名

发布变量只从批准的本地 secret 环境加载。优先使用：

```text
RECITE_ANDROID_KEYSTORE_PATH
RECITE_ANDROID_KEYSTORE_PASSWORD
RECITE_ANDROID_KEY_ALIAS
RECITE_ANDROID_KEY_PASSWORD
```

如果未配置专用变量，可使用现有 `BDFZ_ANDROID_KEYSTORE_*` 组织签名变量。签名文件和密码不得进入 Git、GitHub Actions 明文、报告或聊天。

## R2 对象

```text
apps/recite-android/releases/v<VERSION>/<SHA12>/langlang-<VERSION>.apk
apps/recite-android/releases/v<VERSION>/<SHA12>/release.json
apps/recite-android/latest.json
```

`latest.json` 指向不可变 APK，包含：

```json
{
  "schema": "bdfz-android-update-v1",
  "appId": "net.bdfz.recite.direct",
  "version": "0.1.2",
  "versionCode": 3,
  "minAndroidApi": 23,
  "apkUrl": "https://img.bdfz.net/apps/recite-android/releases/v0.1.2/<sha12>/langlang-0.1.2.apk",
  "sha256": "<64-hex>",
  "size": 1,
  "publishedAt": "<ISO-8601 UTC>",
  "releaseNotes": ["<RELEASE_NOTE>"],
  "mandatory": false,
  "minimumSupportedVersionCode": 1,
  "downloadUrl": "https://img.bdfz.net/apps/recite-android/releases/v0.1.2/<sha12>/langlang-0.1.2.apk",
  "notes": ["<RELEASE_NOTE>"]
}
```

`downloadUrl`、`notes` 与 `minimumSupportedVersionCode` 只为公开 v0.1.1
客户端完成一次向新 schema 的兼容升级；它们必须与 canonical 字段完全一致。
v0.1.2 及以后客户端 fail closed 校验 schema、appId、版本、最低 API、
内容寻址 URL、大小、SHA-256、发布时间和 release notes，并在下载后及打开
系统安装器前再次核对 APK package、versionCode 与当前安装签章。

## 发布

```bash
scripts/release-android.zsh 0.1.0 1
```

脚本只接受 clean Git tree，先跑测试/lint/release build，再核对 Direct/Play
package 相同、签章相同且 Play 无侧载权限；随后上传不可变 R2 对象、公共
SHA-256 readback、创建 GitHub Release，最后才移动 `latest.json`。

## 回滚

1. 确认上一版不可变 APK 的 URL 和 SHA-256。
2. 恢复上一版 `latest.json`，不要覆盖任何版本 APK；已经升到高 code 的
   设备只能使用同签章更高 code 修复。
3. 公网下载并重新核对 hash。
4. 如果 Play 已 rollout，使用 Play Console halt/rollback，禁止用 R2 APK 覆盖 Play 版。
