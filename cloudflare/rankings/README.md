# 琅琅排行榜 Worker

`recite.bdfz.net/api/rankings` 的独立 Cloudflare Worker。它不维护账号，也不接受客户端提交总分。

## 请求

- `GET /api/rankings?limit=20`：公开今日榜与总榜。
- `POST /api/rankings?limit=20`：验证 `bdfz_uc_session`，从 User Center 拉取本人 `siteKey=recite` 进度，刷新快照后返回榜单。
- `GET /api/rankings/health`：不访问 D1 的健康检查。

## 隐私

- `USER_CENTER` service binding 完成会话验证与进度读取。
- D1 用户键为 `HMAC-SHA256(slug, RANKING_PEPPER)`。
- D1 不保存原始用户名、显示名、头像、cookie、Seiue 标识或密码。
- 公共名称由 HMAC 用户键稳定生成，例如 `学子·A1B2`。

## 本地验证

```bash
npm install
npm run types
npm run check
npm run deploy:dry
```

`.dev.vars` 只用于本地类型生成和开发，已被 Git 忽略。生产 secret：

```bash
openssl rand -hex 32 | npx wrangler secret put RANKING_PEPPER
```

不要打印、提交或轮换现有生产值。轮换会改变公开代号，必须先设计迁移。

## 部署

```bash
npx wrangler d1 migrations apply recite-rankings --remote
npm run deploy
curl -sS https://recite.bdfz.net/api/rankings/health
```

回滚 Worker version 或移除 `recite.bdfz.net/api/rankings*` route；已经产生的 D1 快照默认保留。
