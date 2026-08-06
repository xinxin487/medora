# 《Medora 项目当前实现与后续优化技术分析报告》

## 一、项目当前实现概览

当前项目采用单仓库结构，后端为 Spring Boot 3.3.5 + Java 21 + Spring Security + JPA + Flyway + PostgreSQL/pgvector + Redis + MinIO + Micrometer，前端为 Next.js。后端包结构基本符合 `design/05-implementation-plan.md` 规划，已覆盖 `auth`、`user`、`masterdata`、`appointment`、`chat`、`agent`、`knowledge`、`memory`、`admin`、`common` 等模块。

从设计目标看，项目已经不只是骨架，而是形成了较完整的后端主链路：

- 用户认证：注册、登录、刷新 token、登出、当前用户信息。
- 医疗服务业务：科室、医生、号源查询，预约创建，预约取消，预约列表与详情。
- 会话能力：会话创建、消息持久化、SSE 流式响应。
- AI 应用能力：Agent 编排、意图路由、RAG 检索、Tool Calling、Prompt 构造、输出安全处理。
- 记忆能力：会话摘要、业务状态、长期用户偏好记忆。
- 后台能力：知识库、会话、Agent Trace、审计日志、安全统计。
- 可观测性：requestId、结构化日志、审计日志、Agent Trace、Prometheus 指标。

本次审查执行了后端测试：

```powershell
cd backend
mvn test
```

测试结果：`Tests run: 33, Failures: 0, Errors: 0, Skipped: 0`。

整体判断：当前代码完成度大致覆盖 `Phase 0` 到 `Phase 8` 的核心后端能力，但 `Phase 9` 部署验收、OpenAPI 契约、RAG 质量工程化、Memory 治理、SSE 中断、生产安全配置仍需继续补齐。

## 二、API 契约实现对比分析

### 2.1 已完成部分

设计文档 `03-api-contract-design.md` 中要求所有一期 HTTP 接口以 `/api/v1` 为根路径。当前主要 Controller 均已遵循该约定：

- `backend/src/main/java/com/medora/auth/controller/AuthController.java`
- `backend/src/main/java/com/medora/user/controller/CurrentUserController.java`
- `backend/src/main/java/com/medora/masterdata/controller/DepartmentController.java`
- `backend/src/main/java/com/medora/masterdata/controller/DoctorController.java`
- `backend/src/main/java/com/medora/masterdata/controller/ScheduleSlotController.java`
- `backend/src/main/java/com/medora/appointment/controller/AppointmentController.java`
- `backend/src/main/java/com/medora/chat/controller/ConversationController.java`
- `backend/src/main/java/com/medora/chat/controller/ChatStreamController.java`
- `backend/src/main/java/com/medora/knowledge/controller/AdminKnowledgeController.java`
- `backend/src/main/java/com/medora/admin/controller/AdminConversationController.java`
- `backend/src/main/java/com/medora/admin/controller/AdminAuditLogController.java`
- `backend/src/main/java/com/medora/admin/controller/AdminAgentTraceController.java`

统一响应结构已落地：

- `common/api/ApiResponse.java`
- `common/api/ApiErrorResponse.java`
- `common/api/PageResponse.java`
- `common/api/ErrorDetail.java`

成功响应包含 `code`、`message`、`data`、`requestId`、`timestamp`，错误响应包含 `code`、`message`、`details`、`requestId`、`timestamp`，与设计文档基本一致。

统一异常处理已落地：

- `common/exception/GlobalExceptionHandler.java`
- `common/exception/BusinessException.java`

认证鉴权已落地：

- `auth/security/SecurityConfig.java`
- `auth/security/JwtAuthenticationFilter.java`
- `auth/security/JwtTokenProvider.java`

当前实现使用 Bearer Token，`/api/v1/admin/**` 要求 `ADMIN` 角色，其他接口默认需要认证，基本符合设计。

### 2.2 缺失与偏差

#### 2.2.1 OpenAPI / Swagger 未落地

设计文档建议按 Tag 组织 OpenAPI 文档，但当前 `backend/pom.xml` 中未发现 `springdoc` 相关依赖，Controller 代码中也未见 `@Tag`、`@Operation` 等注解。

影响：

- 前后端联调缺少机器可读契约。
- 接口字段变化不易被发现。
- 面试或演示时不容易展示工程化 API 管理能力。

建议：

- 引入 `springdoc-openapi-starter-webmvc-ui`。
- 按 `Auth`、`CurrentUser`、`Department`、`Doctor`、`ScheduleSlot`、`Appointment`、`Conversation`、`ChatStream`、`AdminKnowledge`、`AdminAudit`、`AdminAgentTrace` 等 Tag 补齐接口文档。

#### 2.2.2 部分设计接口未体现

设计中存在但当前代码未体现：

- `GET /api/v1/chat/conversations/{conversationId}/summary`
- `GET /api/v1/admin/system-config`
- `PUT /api/v1/admin/system-config`
- `AdminSystemConfigController`

当前会话摘要能力实际存在于 `memory/service/ConversationSummaryService.java`，后台会话详情也会读取摘要，但用户侧 summary API 未开放。

建议：

- 在 `ConversationController` 补充 summary 查询接口。
- 新增 `AdminSystemConfigController`，先做只读配置展示，再逐步支持可编辑配置。

#### 2.2.3 SSE 字段与设计略有偏差

设计文档中 SSE 事件包含：

- `message_start`
- `tool_status`
- `citation`
- `message_delta`
- `message_end`
- `error`

当前 `chat/sse/StreamEventPublisher.java` 已实现这些事件名，但 `tool_status` 字段使用 `toolName/status/message`，而设计示例使用 `tool/stage/message`。

影响：

- 如果前端按设计文档实现，会出现字段不一致。
- 后续扩展工具状态时容易产生兼容性问题。

建议：

- 明确以后以代码实现为准，还是回调设计字段。
- 若前端已经对接 `toolName/status`，建议在 OpenAPI 或 SSE 协议文档中固化当前字段。

#### 2.2.4 `/stop` 仍是占位实现

`ConversationController.stop()` 当前直接返回 `NO_ACTIVE_STREAM`，没有真正基于 `traceId` 取消正在运行的模型流或后台任务。

位置：

- `chat/controller/ConversationController.java`
- `chat/service/ChatRuntimeService.java`

影响：

- 用户点击停止生成时无法真正中断模型调用。
- 长时间模型请求或网络卡顿时，服务端资源仍会消耗。

建议：

- 建立 `traceId -> Future/Task` 映射。
- 在 `/stop` 中标记取消状态，并让 `ChatOrchestrator`、`AgentModelGateway`、SSE 发布层感知中断。

#### 2.2.5 Query DTO 校验不充分

Body DTO 普遍使用 `@Valid`，但查询参数多在 Controller 中以 `@RequestParam` 手工接收。例如：

- `masterdata/controller/ScheduleSlotController.java`
- `masterdata/controller/DepartmentController.java`
- `appointment/controller/AppointmentController.java`

影响：

- 参数校验规则分散。
- `pageNo/pageSize`、日期格式、未来日期等约束不够显式。

建议：

- 使用 Query DTO + `@Validated`。
- 补齐 `@Min`、`@Max`、`@Pattern`、`@FutureOrPresent` 等校验。

## 三、Agent / RAG / Memory 实现对比分析

### 3.1 Agent 编排实现情况

设计文档 `04-agent-rag-memory-design.md` 中建议的核心模块包括：

- `ChatOrchestrator`
- `IntentRouter`
- `RetrievalService`
- `PromptBuilder`
- `ToolExecutor`
- `ModelGateway`
- `StreamEventPublisher`
- `MemoryService`
- `ConversationSummaryService`
- `AgentTraceService`

当前这些模块均已有代码落点。

核心入口：

- `agent/application/ChatOrchestrator.java`

当前执行链路为：

1. `SafetyGuard.preCheck()` 做输入安全检查。
2. `IntentRouter.route()` 做本地规则路由。
3. `AgentModelGateway.planIntent()` 在需要时让模型辅助规划意图。
4. `ConversationSummaryService.loadContext()` 加载会话摘要、业务状态和最近消息。
5. `MemoryService.loadRelevantMemory()` 加载相关长期记忆。
6. `RetrievalService.retrieve()` 执行知识检索。
7. `ToolExecutor.execute()` 执行业务工具。
8. `PromptBuilder.buildPromptPackage()` 构造模型输入。
9. `AgentModelGateway.generateReplyStreaming()` 执行模型流式生成。
10. `OutputSafetyGuard` 与 `CitationCoverageChecker` 做输出层安全与引用覆盖检查。
11. 返回 `AgentRunResult`，由 `ChatRuntimeService` 持久化消息、Trace、摘要与记忆。

整体符合设计中的“单 Agent + Tool Calling + RAG + Memory + SSE”方向。

### 3.2 Tool Calling 实现情况

Tool 注册与执行：

- `agent/tool/ToolRegistry.java`
- `agent/tool/ToolExecutor.java`
- `agent/tool/tools/*`

已实现工具：

- `QueryDepartmentsTool`
- `QueryDoctorsTool`
- `QueryScheduleSlotsTool`
- `GetUserAppointmentsTool`
- `BookAppointmentTool`
- `CancelAppointmentTool`
- `SearchKnowledgeTool`

设计要求“Agent 内部不直接调 HTTP，而是调用内部 Service”。当前实现符合该要求：

- `BookAppointmentTool` 调用 `AppointmentCommandService.createForUser()`。
- `CancelAppointmentTool` 调用 `AppointmentCommandService.cancelForUser()`。
- `QueryScheduleSlotsTool` 调用 `ScheduleSlotQueryService`。
- `SearchKnowledgeTool` 调用 `RetrievalService`。

高风险工具有基础保护：

- `BookAppointmentTool`、`CancelAppointmentTool` 标记为 `ToolRiskLevel.HIGH`。
- `ChatOrchestrator.filterUserProvidedParams()` 会拒绝模型推断出来的高风险参数。

不足：

- 工具参数确认流程仍偏简单，缺少标准化的“确认卡片/确认状态”。
- 高风险工具目前主要依赖 `slotId` / `appointmentId` 是否由用户提供，不足以覆盖所有误操作场景。

### 3.3 RAG 实现情况

知识库接入：

- `knowledge/controller/AdminKnowledgeController.java`
- `knowledge/service/KnowledgeDocumentService.java`
- `knowledge/service/DocumentIngestService.java`
- `knowledge/parser/DocumentParser.java`
- `knowledge/chunk/DocumentChunker.java`

检索链路：

- `knowledge/service/RetrievalService.java`
- `knowledge/retriever/KeywordRetriever.java`
- `knowledge/retriever/VectorRetriever.java`
- `knowledge/retriever/HybridRetriever.java`
- `knowledge/retriever/RerankService.java`
- `knowledge/retriever/NoopReranker.java`
- `knowledge/retriever/RetrievalResultAssembler.java`

数据库支撑：

- `V7__init_knowledge_tables.sql`
- `V8__add_knowledge_embedding_vector.sql`
- `V9__resize_knowledge_embedding_dimension.sql`

当前能力：

- 支持文档上传。
- 支持 Apache Tika 文档解析。
- 支持文本清洗和切片。
- 支持本地 hash embedding 或 OpenAI-compatible embedding。
- 支持 PostgreSQL FTS。
- 支持 pgvector 向量检索。
- 支持 keyword/vector 加权融合。
- 支持已发布、已索引、active chunk 的过滤。
- 支持后台检索测试接口。

不足：

- `RerankService` 当前为 Noop，占位多于实际重排。
- `RetrievalService.isRelevant()` 是轻量规则过滤，质量可控性有限。
- 缺少检索评测集、命中率统计、无结果率统计、人工标注回归。
- 检索参数如 topK、权重、score threshold 当前写死较多，不利于后台调优。
- Query rewrite 未体现。

### 3.4 Memory 实现情况

短期记忆：

- `conversation`
- `chat_message`
- `conversation_summary`
- Redis 最近消息缓存：`chat/cache/RedisContextCacheService.java`

长期记忆：

- `user_memory_profile`
- `memory/service/MemoryService.java`

摘要：

- `memory/service/ConversationSummaryService.java`

上下文预算：

- `agent/support/TokenBudgetPlanner.java`
- `agent/support/ContextPruner.java`

当前实现已支持：

- 每轮 assistant 回复后刷新会话摘要。
- 保存 `summaryText`、`summaryJson`、`businessStateJson`。
- 从预约成功、号源查询、用户显式表达中抽取长期偏好。
- 按 intentType 选择相关记忆。
- 对最近消息、长期记忆、检索结果做 token 预算裁剪。
- 定时清理过期记忆。

不足：

- 摘要不是模型压缩，而是规则化摘要，长对话语义保真度有限。
- 长期记忆写入缺少“候选记忆 -> 用户确认 -> 激活”的完整策略。
- 低置信记忆也可能进入记忆表，存在偏好污染风险。
- 记忆冲突合并规则仍较简单。

## 四、阶段计划完成度分析

### Phase 0：工程初始化

完成度：基本完成。

证据：

- `backend/pom.xml`
- `frontend/package.json`
- `scripts/dev/start-backend.bat`
- `scripts/dev/start-frontend.bat`
- `README.md`

遗留：

- 部分文档在终端显示存在编码问题，需要统一 UTF-8 检查。

### Phase 1：数据库与主数据

完成度：完成。

证据：

- `V1__init_extensions.sql`
- `V2__init_masterdata_tables.sql`
- `V3__seed_masterdata.sql`
- `DepartmentController`
- `DoctorController`
- `ScheduleSlotController`

遗留：

- 主数据高级搜索、标签检索仍较基础。

### Phase 2：用户认证

完成度：完成。

证据：

- `AuthController`
- `AuthService`
- `SecurityConfig`
- `JwtTokenProvider`
- `CurrentUserController`
- `V4__init_auth_tables.sql`

遗留：

- 生产安全配置仍需强化。
- logout 接口鉴权语义可收紧。

### Phase 3：医疗服务业务闭环

完成度：完成度较高。

证据：

- `AppointmentCommandService`
- `AppointmentQueryService`
- `AppointmentController`
- `AppointmentRepository`
- `V5__init_appointment_tables.sql`

已实现：

- 预约创建。
- 预约取消。
- 号源扣减与回补。
- 悲观锁。
- 幂等键。
- 操作日志。
- 审计日志。

遗留：

- 并发场景目前主要靠单元测试，缺少真实 PostgreSQL 集成压测证据。

### Phase 4：会话基础能力

完成度：基本完成。

证据：

- `ConversationController`
- `ChatStreamController`
- `ConversationService`
- `ChatRuntimeService`
- `StreamEventPublisher`
- `V6__init_chat_tables.sql`

遗留：

- `/stop` 未真正中断流。
- SSE 超时、断线、重连策略需要继续工程化。

### Phase 5：知识库与基础 RAG

完成度：基本完成。

证据：

- `AdminKnowledgeController`
- `KnowledgeDocumentService`
- `DocumentIngestService`
- `RetrievalService`
- `HybridRetriever`
- `V7/V8/V9` migrations

遗留：

- Rerank 为 Noop。
- 检索质量评测和后台调参不足。
- 异步任务治理不足。

### Phase 6：Agent 编排与 Tool Calling

完成度：基本完成。

证据：

- `ChatOrchestrator`
- `IntentRouter`
- `PromptBuilder`
- `ToolExecutor`
- `ToolRegistry`
- `agent/tool/tools/*`
- `AgentTraceService`

遗留：

- 高风险 Tool 的用户确认流程还不够产品化。
- 模型规划失败后的 fallback 有，但可解释性和 trace 粒度仍可增强。

### Phase 7：Memory 与上下文管理

完成度：基础能力完成。

证据：

- `MemoryService`
- `ConversationSummaryService`
- `TokenBudgetPlanner`
- `ContextPruner`
- `V11__init_memory_tables.sql`

遗留：

- 摘要不是模型压缩摘要。
- 长期记忆确认、冲突、遗忘策略仍需完善。

### Phase 8：后台与可观测性

完成度：部分完成。

证据：

- `AdminConversationController`
- `AdminAuditLogController`
- `AdminAgentTraceController`
- `AdminSafetyController`
- `AuditLogService`
- `AgentTraceService`
- `MedoraMetricsService`
- `V10__init_agent_trace.sql`
- `V12__init_audit_log.sql`
- `V13__agent_trace_safety_fields.sql`

遗留：

- 系统配置后台接口缺失。
- 指标维度较基础。
- 业务链路 trace 还可细化到检索、工具、模型调用每一步。

### Phase 9：部署与验收

完成度：当前代码证据不足，无法确认。

证据：

- `deploy/README.md` 仍表示 Dockerfile、Nginx、Compose 为后续补齐内容。

遗留：

- Dockerfile。
- docker-compose。
- Nginx SSE 代理配置。
- 近生产环境启动脚本。
- 端到端验收清单自动化。

## 五、当前实现中的主要问题

### 5.1 接口契约不稳定

问题背景：

项目接口较多，前端、后台、Agent Tool 都依赖统一契约。

代码依据：

- Controller 已覆盖大部分路径。
- 但 OpenAPI 未落地。
- `summary`、`system-config` 等设计接口缺失。

潜在影响：

- 前后端联调容易出现字段理解偏差。
- 后续简历或面试展示时，工程化接口管理证据不足。

### 5.2 RAG 链路可用但质量闭环不足

问题背景：

RAG 不只是能检索，更重要的是能证明召回可靠、引用可信。

代码依据：

- `RetrievalService.retrieve()`
- `HybridRetriever.retrieve()`
- `NoopReranker`

潜在影响：

- 检索结果可能不稳定。
- 无法量化“答得好不好”。
- 引用覆盖检查有基础能力，但未形成后台质量看板。

### 5.3 记忆体系实现偏规则化

问题背景：

长期记忆会持续影响用户后续会话，误写入会造成持续污染。

代码依据：

- `MemoryService.captureFromAgentRun()`
- `MemoryService.captureFromUserMessage()`
- `UserMemoryProfileEntity`

潜在影响：

- 模糊表达可能被写成偏好。
- 旧记忆与当前输入冲突时，需要更清晰的优先级和冲突处理。

### 5.4 异步任务缺少治理

问题背景：

知识库入库、embedding、模型流式调用都是长任务。

代码依据：

- `DocumentIngestService.ingestAsync()`
- `AsyncConfig`
- `ChatRuntimeService` 注入 `TaskExecutor`

潜在影响：

- 默认线程池不可控。
- 缺少任务排队、重试、失败恢复、死信、任务查询。
- 高并发上传或长对话时稳定性不足。

### 5.5 SSE 中断能力不足

问题背景：

设计中包含 `/stop`，用户应能停止生成。

代码依据：

- `ConversationController.stop()` 当前返回 `NO_ACTIVE_STREAM`。

潜在影响：

- 用户取消无效。
- 模型请求和后台线程可能继续执行。

### 5.6 生产环境安全配置风险

问题背景：

开发配置不能直接进入生产环境。

代码依据：

- `application.yml` 中 `JWT_SECRET` 有 dev-only 默认值。
- `management.endpoint.health.show-details=always`。
- `AI_INSECURE_TLS` 虽默认为 false，但 `AgentModelGateway` 对 HTTPS IP baseUrl 有自动不安全 TLS 逻辑。

潜在影响：

- 密钥弱配置。
- 健康检查泄露服务细节。
- 模型代理 TLS 校验可能被弱化。

### 5.7 测试层次仍需补强

问题背景：

当前单元测试通过，但数据库、SSE、鉴权、Flyway、pgvector 等更依赖集成测试。

代码依据：

- `backend/src/test/java` 下已有 20 个测试类。
- 未发现 Testcontainers、MockMvc、PostgreSQLContainer 等证据。

潜在影响：

- 单元测试通过不代表真实 PostgreSQL/pgvector/Flyway 环境稳定。
- 并发预约、SSE、知识库入库等场景缺少端到端保证。

## 六、后续优化建议

### 6.1 短期必须修复的问题

#### 建议 1：冻结 API 契约并补齐缺失接口

问题背景：

当前接口实现多，但契约文档缺少机器可读产物。

当前代码依据：

- Controller 已覆盖大多数设计路径。
- 缺少 `GET /chat/conversations/{id}/summary`。
- 缺少 `/admin/system-config`。
- 缺少 OpenAPI。

优化方案：

- 引入 springdoc。
- 补齐 summary API。
- 补齐 system-config API。
- 固化 SSE 事件字段。

涉及模块：

- `chat`
- `admin`
- `common/api`
- `backend/pom.xml`

预期收益：

- 降低联调成本。
- 提升接口稳定性。
- 增强项目工程化展示能力。

#### 建议 2：补齐生产安全配置

问题背景：

当前配置明显偏开发环境。

当前代码依据：

- `application.yml` 默认 JWT secret。
- actuator health details always。
- CORS 仅本地开发友好。

优化方案：

- 新增或完善 `application-prod.yml`。
- 生产环境强制外部注入 `JWT_SECRET`。
- health details 改为 `never` 或 `when_authorized`。
- 收紧 CORS origin。
- 明确禁止生产启用 insecure TLS。

涉及模块：

- `auth`
- `config`
- `deploy`

预期收益：

- 降低部署安全风险。
- 使项目更接近真实生产规范。

#### 建议 3：修复 `/stop` 的真实中断能力

问题背景：

当前停止接口是占位。

当前代码依据：

- `ConversationController.stop()` 返回固定 `NO_ACTIVE_STREAM`。

优化方案：

- 在 `ChatRuntimeService` 中维护 `traceId -> Future/TaskHandle`。
- 模型流和工具执行检查取消状态。
- `/stop` 设置取消标记并中断后续 SSE 输出。

涉及模块：

- `chat`
- `agent`
- `ai`

预期收益：

- 改善用户体验。
- 降低长连接和模型调用资源浪费。

### 6.2 中期需要完善的功能

#### 建议 4：RAG 质量工程化

问题背景：

当前 RAG 可用，但缺少质量闭环。

当前代码依据：

- `HybridRetriever` 已融合 FTS 和 vector。
- `RerankService` 当前为 Noop。
- `CitationCoverageChecker` 有基础检查，但未形成评测体系。

优化方案：

- 建立测试知识集和问题集。
- 记录 topK 命中率、无结果率、引用覆盖率。
- 增加可配置 rerank。
- 后台增加检索调试和质量指标。

涉及模块：

- `knowledge`
- `agent`
- `admin`
- `metrics`

预期收益：

- RAG 从“功能实现”升级为“可评估、可调优、可解释”。

#### 建议 5：长期记忆写入治理

问题背景：

记忆写入需要避免污染。

当前代码依据：

- `MemoryService` 根据用户文本和工具结果直接 upsert。

优化方案：

- 增加 `CANDIDATE/ACTIVE/INACTIVE` 状态。
- 低置信记忆先进入候选。
- 用户明确确认后激活。
- 后台支持查看、禁用、删除记忆。

涉及模块：

- `memory`
- `agent`
- `admin`

预期收益：

- 降低记忆污染。
- 提升多轮对话稳定性。

#### 建议 6：异步任务治理

问题背景：

知识库入库和模型调用均是长任务。

当前代码依据：

- `DocumentIngestService.ingestAsync()`
- `AsyncConfig`

优化方案：

- 自定义线程池。
- 增加入库任务状态查询。
- 增加失败重试、错误详情、任务耗时。
- 对 embedding 失败做可恢复处理。

涉及模块：

- `knowledge`
- `ai`
- `admin`

预期收益：

- 提高知识库入库稳定性。
- 便于后台排障。

### 6.3 长期可以增强的架构能力

#### 建议 7：补齐部署与端到端验收

问题背景：

项目当前更像开发态，部署态证据不足。

当前代码依据：

- `deploy/README.md` 仍为预留说明。

优化方案：

- 补齐 backend Dockerfile。
- 补齐 frontend Dockerfile。
- 补齐 docker-compose，包含 Postgres、Redis、MinIO、Prometheus、Grafana。
- 补齐 Nginx SSE 代理配置。
- 编写登录 -> 查询 -> 预约 -> 对话 -> RAG -> 取消的验收脚本。

涉及模块：

- `deploy`
- `scripts`
- `backend`
- `frontend`

预期收益：

- 项目可一键演示。
- 形成作品集级别交付能力。

#### 建议 8：引入真实集成测试

问题背景：

单元测试不足以覆盖数据库锁、pgvector、Flyway、SSE。

当前代码依据：

- 当前 `mvn test` 通过 33 个测试，但未发现 Testcontainers 证据。

优化方案：

- 引入 Testcontainers PostgreSQL。
- 增加 Flyway migration 测试。
- 增加预约并发集成测试。
- 增加 RAG 检索集成测试。
- 增加 MockMvc 鉴权和 API 契约测试。

涉及模块：

- `backend/src/test`
- `pom.xml`

预期收益：

- 提高核心链路回归可靠性。
- 强化面试中“工程质量”表达。

## 七、面向简历或面试的项目亮点提炼

以下表述基于当前真实代码实现，不夸大未完成内容。

### Java 后端工程化方向

- 基于 Spring Boot 3 + Java 21 构建医疗服务智能助手后端，按认证、主数据、预约、会话、知识库、Agent、Memory、后台审计等领域模块拆分。
- 设计并实现统一 API 响应、业务错误码、JWT 鉴权、请求追踪 `requestId`、MDC 日志和 Prometheus 指标。
- 实现预约挂号核心事务，基于 PostgreSQL 悲观锁完成号源扣减与取消回补，并结合 `Idempotency-Key` 防止重复预约。
- 使用 Flyway 管理数据库演进，覆盖用户、主数据、预约、会话、知识库、Agent Trace、Memory、Audit Log 等核心表结构。
- 建设后台排障能力，支持知识库管理、会话查看、审计日志、Agent Trace 与安全统计。

### AI 应用开发方向

- 构建基础 RAG 链路，支持知识文档上传、Tika 解析、文本切片、PostgreSQL FTS、pgvector 向量检索和引用返回。
- 实现单 Agent 编排链路，结合规则意图路由、模型辅助规划、RAG 检索、业务 Tool Calling、SSE 流式输出和 Agent Trace。
- 将 Agent 工具调用限制在内部 Service 层，避免模型直接操作数据库，确保预约和取消等高风险动作仍受业务事务边界控制。
- 实现会话摘要与长期记忆基础能力，将会话状态、工具结果和用户偏好写入结构化表，并通过 TokenBudget/ContextPruner 控制上下文长度。
- 增加输入安全、输出安全和引用覆盖检查，为医疗服务场景下的幻觉控制和安全回答提供基础防线。

## 八、结论

Medora 当前已经完成了从传统 Java 后端业务系统到 AI 应用后端的主要骨架和核心链路。项目最有价值的部分不是单点功能，而是把预约业务、知识库检索、Agent 编排、Tool Calling、SSE、Memory、Trace、Audit 这些模块放在同一个工程化边界中进行了整合。

当前后续优化重点应从“继续堆功能”转向“稳定契约、提升质量、补齐生产化能力”：

1. 短期优先补齐 OpenAPI、summary/system-config、真实 stop、生产安全配置。
2. 中期重点做 RAG 质量评测、Memory 写入治理、异步任务治理。
3. 长期补齐 Docker/Compose/Nginx/端到端验收和真实集成测试。

只要上述优化完成，项目可以从“功能完整的开发项目”进一步升级为“可演示、可复盘、可面试讲深度的 AI 医疗服务工程项目”。
