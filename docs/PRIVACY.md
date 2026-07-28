# 琅琅 Android 隐私说明（上架草案）

琅琅把古诗文语料和学习进度优先保存在设备本地。未登录时，学习不需要把个人信息发送到服务器。

用户主动登录后：

- Seiue 或 BDFZ 凭证仅通过 HTTPS 发送到 BDFZ User Center 完成验证；
- App 不保存密码；
- App 只保存 Android Keystore 加密的 User Center 会话；
- 学习进度会同步 `siteKey`、篇目 key、阶段、分数、尝试次数和时间；
- 不同步默写正文；
- 当前版本不采集广告标识、精确位置、通讯录、相册或支付信息。

R2 直装版会向 `img.bdfz.net` 检查更新；Play 版由 Google Play 管理更新。

未来如加入语音或图片评分，必须在功能上线前更新本说明，明确权限、上传内容、处理目的、保留时长和删除方式。

上架前必须补齐：

- 可公开访问的正式隐私政策 URL；
- User Center 网页账号删除入口；
- Google Play Data Safety 表；
- 联系方式与生效日期。
