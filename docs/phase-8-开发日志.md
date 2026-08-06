# Phase 8 开发日志

## 阶段目标

- 按实施计划推进 `Phase 8：后台与可观测性`。
- 为知识库、会话、审计和 Agent Trace 提供最小可用后台。
- 补齐日志、指标和链路追踪，方便后续 Phase 9 做部署和整体验收。

## 主要实现内容

### 1. 审计日志落库

- 新增 `V12__init_audit_log.sql`。
- 新增：
  - `AuditLogEntity`
  - `AuditLogRepository`
  - `AuditLogService`
- 当前阶段已接入的审计写操作：
  - 预约创建
  - 预约取消
  - 知识文档上传
  - 知识文档重建
  - 知识文档发布/下线

审计字段包括：
- `operatorType`
- `actionType`
- `resourceType`
- `resourceId`
- `requestId`
- `detailJson`

### 2. Prometheus 指标与结构化日志

- 后端增加 `micrometer-registry-prometheus` 依赖。
- `application.yml` 暴露：
  - `/actuator/health`
  - `/actuator/info`
  - `/actuator/prometheus`
- 新增 `MedoraMetricsService`，当前阶段已记录：
  - `medora.agent.trace.completed`
  - `medora.agent.trace.latency`
  - `medora.appointment.command.completed`
  - `medora.audit.event.created`
- `RequestIdFilter` 与 `JwtAuthenticationFilter` 增加 MDC 字段：
  - `requestId`
  - `httpMethod`
  - `requestPath`
  - `userId`
  - `userRoles`
- 新增 `RequestLoggingFilter`，按统一 key=value 风格输出请求完成日志。

### 3. 后台查询接口

新增后台接口：

- `GET /api/v1/admin/conversations`
- `GET /api/v1/admin/conversations/{conversationId}`
- `GET /api/v1/admin/conversations/{conversationId}/messages`
- `GET /api/v1/admin/agent-traces`
- `GET /api/v1/admin/agent-traces/{traceId}`
- `GET /api/v1/admin/audit-logs`

其中：

- 会话详情会同时返回：
  - 会话基础信息
  - `conversation_summary`
  - `business_state_json`
  - 当前用户的活跃长期记忆
- 消息详情会额外返回：
  - `citationJson`
  - `toolCallJson`
  - `tokenUsageJson`
- Trace 详情会返回：
  - `retrievalJson`
  - `toolEventsJson`

这让 Phase 7 的摘要、记忆和上下文预算能力可以直接在后台观察。

### 4. 管理后台页面

前端新增统一后台壳层 `AdminShell`，并新增页面：

- `/admin`
- `/admin/conversations`
- `/admin/traces`
- `/admin/audit-logs`

同时把已有知识库页纳入统一后台导航：

- `/admin/knowledge`

页面能力包括：

- 后台总览
  - 显示知识文档、会话、Trace、审计总数
  - 提供 Prometheus 指标入口
- 会话查看页
  - 支持按关键词和状态筛选
  - 查看摘要、业务状态、长期记忆、消息流和 token 预算 JSON
- Trace 查看页
  - 支持按 `traceId`、`conversationId`、状态筛选
  - 查看检索结果和工具事件 JSON
- 审计日志页
  - 支持按操作人、资源类型、`requestId` 查询
  - 查看详情 JSON

### 5. 首页同步

- 首页从 Phase 4 文案升级为 Phase 8 状态说明。
- 新增后台总览入口 `/admin`。

## 自测与联调记录

### 1. 后端测试

命令：

```bash
mvn -q test
```

结果：通过。

本阶段新增覆盖：

- `AdminConversationQueryServiceTest`
- `AdminTraceQueryServiceTest`
- `AuditLogServiceTest`
- `AppointmentCommandServiceTest` 同步补齐新依赖验证

### 2. 前端构建验证

命令：

```bash
npm run build
```

结果：通过。

修复项：

- `admin/traces` 页面最初使用 `useSearchParams()`，在 Next 15 生产构建中要求 Suspense。
- 后续改为客户端读取 `window.location.search`，构建恢复正常。

### 3. 本阶段联调说明

- 本阶段未启动 `scripts/dev` 下的前后端启动脚本。
- 本阶段未要求你配合启动真实 AI API。
- 原因：
  - Phase 8 的主要增量集中在后台查询、审计、指标和页面结构
  - 这些能力可以通过单测和前端构建直接验证
  - 不依赖真实模型即可确认后台链路是否已接好

如需继续真实联调，建议后续重点验证：

- 管理员登录后访问 `/admin` 系列页面是否全部可用
- 执行一次预约/取消后，`/admin/audit-logs` 是否立即可见新事件
- 发起一次对话后，`/admin/conversations` 和 `/admin/traces` 是否出现对应记录
- `/actuator/prometheus` 是否能被本地 Prometheus 成功抓取

## 当前结论

- Phase 8 的最小可用后台已经落地。
- 当前系统已具备：
  - 知识库后台
  - 会话查看后台
  - Agent Trace 查看后台
  - 审计日志后台
  - Prometheus 指标暴露
  - 基础结构化请求日志

这意味着系统已经从“只有用户主链路可演示”升级为“前台业务链路 + 后台排障链路”双可演示状态。

## 后续建议

- 下一阶段可进入 `Phase 9：部署、联调与验收`，优先补：
  - Dockerfile
  - docker-compose
  - Prometheus / Grafana 本地联动
  - Nginx 对 SSE 的代理验证
- 如果你想先做真实运行态联调，我可以在你启动服务后，先跑：
  - 预约 -> 审计日志回看
  - 对话 -> 会话详情回看
  - 对话 -> Trace 详情回看
