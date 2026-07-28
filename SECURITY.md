# Security policy

请使用 GitHub 私有安全公告报告漏洞，不要在公开 issue 中提交：

- 会话、cookie、密码、invite code 或 token
- 签名文件或签名密码
- 学生身份、学习正文或截图
- 未公开的服务端漏洞细节

App 不保存登录密码。会话由 Android Keystore 加密。更新只接受 `img.bdfz.net` 下的不可变 APK 路径，并在安装前校验 SHA-256。
