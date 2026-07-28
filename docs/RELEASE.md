# 发布、更新与回滚

## 版本规则

- `versionName`: 三段数字，例如 `0.1.0`
- `versionCode`: 严格递增的正整数
- 直装和 Play 从同一源码版本构建，但 package 与更新通道分离

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
  "version": "0.1.0",
  "versionCode": 1,
  "minimumSupportedVersionCode": 1,
  "sha256": "<64-hex>",
  "downloadUrl": "https://img.bdfz.net/apps/recite-android/releases/v0.1.0/<sha12>/langlang-0.1.0.apk",
  "publishedAt": "<ISO-8601>",
  "notes": []
}
```

## 发布

```bash
scripts/release-android.zsh 0.1.0 1
```

脚本只接受 clean Git tree，先跑测试/lint/release build，再创建 GitHub Release，最后上传 R2 并做公共 SHA-256 readback。

## 回滚

1. 确认上一版不可变 APK 的 URL 和 SHA-256。
2. 恢复上一版 `latest.json`，不要覆盖任何版本 APK。
3. 公网下载并重新核对 hash。
4. 如果 Play 已 rollout，使用 Play Console halt/rollback，禁止用 R2 APK 覆盖 Play 版。
