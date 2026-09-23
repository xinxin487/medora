# A-stage 作品集交付验收清单

验收日期：2026-09-23。验收使用独立的 PostgreSQL 数据库 `medora_m6_acceptance`、后端端口 `18081` 和前端端口 `13000`；未删除 `medora` 数据库或任何 Docker volume。所有数据均为 m6-v1 合成演示夹具。

| 验收项 | 实际执行命令 | 结果 |
|---|---|---|
| Compose 契约 | `docker compose -f deploy/compose/docker-compose.dev.yml config` | 退出码 0 |
| 全新库启动 | `DB_NAME=medora_m6_acceptance ... mvn spring-boot:run`，启用全部 A-stage feature flags | Flyway + Hibernate schema 校验通过，应用监听 `18081` |
| Seed 幂等 | `DB_NAME=medora_m6_acceptance ./scripts/test-seed-idempotency.sh` | 退出码 0；计数 `1,2,2,4,1,4,0` 未随第二次执行变化 |
| Smoke 脚本契约 | `./scripts/test-smoke-a-stage.sh` | 退出码 0；覆盖成功路径、后端 DOWN 路径和 token 不泄露断言 |
| 最终真实 smoke | `DB_NAME=medora_m6_acceptance BACKEND_URL=http://localhost:18081 FRONTEND_URL=http://localhost:13000 ./scripts/smoke-a-stage.sh` | 退出码 0；PostgreSQL/pgvector、Redis、MinIO、后端、前端、注册登录、知识夹具查询和 MCP `tools/list` 均通过 |
| 后端全量测试 | `cd backend && mvn --no-transfer-progress clean test` | 退出码 0；333 tests，0 failures，0 errors，2 skipped |
| 前端生产构建 | `cd frontend && npm run build` | 退出码 0；33 个静态页面生成 |
| 证据报告生成 | `./scripts/generate-a-stage-reports.sh` | 退出码 0；三份脱敏、确定性报告已写入 `docs/reports/` |

## 已知且透明记录的限制

- 两项跳过测试依赖 Testcontainers；当前 Codex 沙箱中的 Testcontainers 无法发现 Docker socket。Docker Compose 服务和真实 smoke 已在 Docker Desktop 上通过，但这不等同于 Testcontainers 并发集成测试已经执行。
- RAG 与预约报告是确定性夹具/回放证据，不是生产流量准确率或硬件性能承诺；报告正文已明确标注该边界。
- `.env` 和真实 API key 不纳入仓库。真实聊天演示前，按根目录 [README](../README.md) 填写 `AI_CHAT_PROVIDER`、`AI_CHAT_API_KEY`、`AI_CHAT_BASE_URL`、`AI_CHAT_MODEL` 与 `JWT_SECRET`。

## 可展示证据

- [Context Builder 效率报告](reports/context-efficiency.md)
- [分层 RAG 评测报告](reports/rag-evaluation.md)
- [预约库存回放报告](reports/appointment-load.md)
- [架构总览](architecture/a-stage-overview.md)
- [三段演示 runbook](demo/01-long-conversation.md)
