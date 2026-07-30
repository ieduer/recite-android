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
apps/recite-android/latest.apk
apps/recite-android/latest.json
```

`latest.apk` 是 canonical Portal 的固定最新版下载地址：

```text
https://img.bdfz.net/apps/recite-android/latest.apk
```

它是可变便利别名，不是审计或回滚权威。`latest.json` 的 `apkUrl` 与兼容字段
`downloadUrl` 必须继续指向同一条内容寻址 immutable APK，不得改成
`latest.apk`。当前 v0.1.3 alias、immutable APK 与 manifest 均为
2,529,258 bytes，SHA-256
`94d4ac0c02c52e9a8a9d19a587213dfadf0df56c2cf019af542ef42de9f46e23`。

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
package 相同、签章相同且 Play 无侧载权限。发布顺序固定为：

1. 上传 immutable APK 与 `release.json`；
2. 从公开 immutable URL 回读并核对；
3. 创建 GitHub Release；
4. 从 exact staged signed APK 覆写 `latest.apk`；
5. 从不带 query 的 bare `latest.apk` 完整下载，核对 bytes、size 与 SHA-256；
6. 最后才移动 `latest.json`。

Cloudflare edge 可能继续返回旧的 bare alias；请求加 `Cache-Control: no-cache`
也不能当作已绕过 edge 的证据。cache-busted query 只能帮助诊断 origin，
不能关闭发布门。若 bare URL 仍旧，限于精确 URL purge 或等待其刷新后重跑，
不得做全站 purge，也不得先移动 `latest.json`。

Portal 永久使用固定 `latest.apk` URL；每次 App release 通过 alias parity
gate 后无需改 Portal href。Portal verifier 必须持续确认该固定链接存在。

## 回滚

1. 确认上一版不可变 APK 的 URL 和 SHA-256。
2. 从上一版 immutable APK 恢复 `latest.apk`，并从 bare alias 重新核对
   bytes、size 与 SHA-256。
3. 最后恢复与该 immutable APK 匹配的 `latest.json`；不要覆盖任何版本 APK。
4. Portal href 保持固定 alias；如 alias 尚未恢复，可先回退 Portal deployment
   到已记录的 immutable 下载入口，不能让错误 alias 继续推广。
5. 已经升到高 code 的设备只能使用同签章更高 code 修复。
6. 如果 Play 已 rollout，使用 Play Console halt/rollback，禁止用 R2 APK 覆盖 Play 版。
