# Contributing

1. 保持原生 Compose 界面，不引入 WebView 学习壳。
2. 新学习功能必须在断网时有明确行为。
3. 进度写入必须先进入 Room，再通过 outbox 同步。
4. 提交前运行：

```bash
./gradlew :app:lintDirectDebug :app:testDirectDebugUnitTest :app:assembleDirectDebug
```

5. 不提交 secret、签名文件、用户数据或测试账号。
