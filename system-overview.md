# Medora 系统总结(给 GPT 讨论后续优化用)

> 截至 2026-07-08,代码状态:`feature/p10` 分支,Phase 9.7 已落地并通过 UI 联调。
> 本文档面向"和 GPT 讨论后续优化方案",重点描述**当前能力**与**Agent 架构实现**,并诚实列出**已识别的薄弱处**。

---

## 1. 项目概览

**Medora** 是一个面向医疗服务的工程化智能助手系统,覆盖:导诊咨询、科室/医生/号源查询、预约挂号、取消挂号、知识库 RAG、Agent 工具调用、会话记忆与后台排障。

| 维度 | 选型 |
|------|------|
| 后端 | Spring Boot + Java 21 + Maven |
| 数据 | PostgreSQL + pgvector + Redis |
| AI | OpenAI 兼容 / Anthropic 兼容双 LLM 端点 |
| 前端 | Next.js 15 + React 19 + TypeScript 5.6 + Tailwind 3.4 |
| Agent 框架 | 自研 ADK 抽象层(Phase 9.0-9.7 重构,2026 年中完成) |

**进度**:Phase 0-8 主线完成,Phase 9.x ADK 重构灰度上线,Phase 9.8(旧代码清理)未启动。

---

## 2. 顶层能力清单

### 2.1 业务能力(后端)

| 模块 | API | 实现 |
|------|-----|------|
| 用户认证 | `POST /api/v1/auth/{register,login,refresh,logout}` | [AuthController](backend/src/main/java/com/medora/auth/controller/AuthController.java) |
| 科室查询 | `GET /api/v1/departments` | [DepartmentController](backend/src/main/java/com/medora/masterdata/controller/DepartmentController.java) |
| 医生查询 | `GET /api/v1/doctors{,/{id}}` | [DoctorController](backend/src/main/java/com/medora/masterdata/controller/DoctorController.java) |
| 号源查询 | `GET /api/v1/schedule-slots` | [ScheduleSlotController](backend/src/main/java/com/medora/masterdata/controller/ScheduleSlotController.java) |
| 预约创建 | `POST /api/v1/appointments`(支持 `Idempotency-Key`) | [AppointmentController](backend/src/main/java/com/medora/appointment/controller/AppointmentController.java) |
| 预约取消 | `POST /api/v1/appointments/{id}/cancel` | 同上 |
| 会话管理 | `POST/GET /api/v1/chat/conversations...` | [ConversationController](backend/src/main/java/com/medora/chat/controller/ConversationController.java) |
| SSE 流式 | `POST /api/v1/chat/conversations/{id}/stream` | [ChatStreamController](backend/src/main/java/com/medora/chat/controller/ChatStreamController.java) |

### 2.2 AI 能力

- **RAG**:文档摄入 → 切片 → embedding → pgvector → 混合检索 → prompt 注入
- **Agent 编排**:4-agent 拓扑(Root/Triage/Booking/Knowledge),支持灰度切换
- **工具调用**:7 个业务工具(查询类 5 个、写操作 2 个高风险、知识检索 1 个)
- **会话记忆**:短期(最近 12 条 Redis 缓存)+ 中期(会话摘要)+ 长期(偏好/最后成功预约)
- **上下文预算**:Token 预算规划 + 三级裁剪(检索/记忆/近期消息)

### 2.3 管理后台

- 知识库管理:[AdminKnowledgeController](backend/src/main/java/com/medora/knowledge/controller/AdminKnowledgeController.java)(上传/列表/发布/rebuild/检索测试)
- 会话排查:[AdminConversationController](backend/src/main/java/com/medora/admin/controller/AdminConversationController.java)
- Agent Trace:[AdminAgentTraceController](backend/src/main/java/com/medora/admin/controller/AdminAgentTraceController.java)(含意图决策/检索/工具调用)
- 审计日志:[AdminAuditLogController](backend/src/main/java/com/medora/admin/controller/AdminAuditLogController.java)

### 2.4 前端能力

- 患者端 5 页 + 管理端 5 页 + 高保真原型(`/prototype`)
- SSE 流式消费(fetch + ReadableStream 自实现)
- Markdown 渲染(react-markdown + remark-gfm)

---

## 3. Agent / ADK 架构(重点)

### 3.1 拓扑结构

```
                ┌──────────────────────┐
                │  RootRouterAgent     │
                │  (LLM 路由,无工具)   │
                └──────────┬───────────┘
                           │ transfer_to_*
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│ TriageAgent   │  │ BookingAgent  │  │ KnowledgeAgent│
│ 症状→科室     │  │ 挂号全流程    │  │ RAG 问答      │
│ searchKnowledge│ │ 6 个业务工具  │  │ searchKnowledge│
│ maxSteps=4    │  │ maxSteps=8    │  │ maxSteps=5    │
└───────────────┘  └───────────────┘  └───────────────┘
```

实现:[AdkAgentTopology](backend/src/main/java/com/medora/adk/agents/AdkAgentTopology.java)。

### 3.2 四个 Agent 的职责划分

| Agent | 职责 | 工具 | 安全 Callback | maxSteps |
|-------|------|------|---------------|----------|
| RootRouterAgent | LLM 判断请求类型,transfer 到子 agent | 无(强制走子 agent) | inputSafety + outputSafety | 2 |
| TriageAgent | 症状 → 推荐科室,**不做诊断** | searchKnowledge | inputSafety + outputSafety | 4 |
| BookingAgent | 挂号/查询/取消全流程 | queryDepartments/queryDoctors/queryScheduleSlots/getUserAppointments/bookAppointment/cancelAppointment | inputSafety + outputSafety + **highRiskToolGuard** | 8 |
| KnowledgeAgent | 知识库 RAG 问答 | searchKnowledge | inputSafety + outputSafety + **hallucinationGuard(阈值 0.6)** | 5 |

### 3.3 关键类清单

#### ADK 核心
- [AdkAgentTopology](backend/src/main/java/com/medora/adk/agents/AdkAgentTopology.java) — 4 agent 拓扑配置
- [AdkOrchestrator](backend/src/main/java/com/medora/adk/orchestrator/AdkOrchestrator.java) — ADK 灰度入口,SSE 推送
- [AdkProperties](backend/src/main/java/com/medora/adk/config/AdkProperties.java) — 灰度开关 `medora.adk.enabled`
- [AdkRunnerConfig](backend/src/main/java/com/medora/adk/config/AdkRunnerConfig.java) — Runner Bean 配置

#### SSE 适配
- [AdkEventToSseAdapter](backend/src/main/java/com/medora/adk/sse/AdkEventToSseAdapter.java) — ADK Event → SSE 协议翻译
- [AdkStreamResult](backend/src/main/java/com/medora/adk/sse/AdkStreamResult.java) — 聚合结果(finalReply/toolEvents/safetyEvents/transferChain/hallucinationRisk)

#### Session/State
- [PostgresBackedSessionService](backend/src/main/java/com/medora/adk/session/PostgresBackedSessionService.java) — Session 持久化到 `conversation.session_state` JSONB,支持从 `chat_message` 反构最近 30 条为 ADK Event

#### 安全 Callback(分层防护)
- [LlmInputSafetyBeforeCallback](backend/src/main/java/com/medora/adk/guard/LlmInputSafetyBeforeCallback.java) — 输入安全审查
- [LlmOutputSafetyAfterCallback](backend/src/main/java/com/medora/adk/guard/LlmOutputSafetyAfterCallback.java) — 输出安全审查
- [HighRiskToolBeforeCallback](backend/src/main/java/com/medora/adk/guard/HighRiskToolBeforeCallback.java) — 高风险工具参数校验(Booking 专用)
- [LlmHallucinationAfterCallback](backend/src/main/java/com/medora/adk/guard/LlmHallucinationAfterCallback.java) — 幻觉检测(Knowledge 专用,阈值 0.6)

#### 工具桥接
- [MedoraFunctionTool](backend/src/main/java/com/medora/adk/tool/MedoraFunctionTool.java) — `ToolDefinition` → ADK `BaseTool` 桥接
- [ToolSchemaRegistry](backend/src/main/java/com/medora/adk/tool/ToolSchemaRegistry.java) — 工具 Schema 注册表
- [AdkToolContextResolver](backend/src/main/java/com/medora/adk/tool/AdkToolContextResolver.java) — 从 ToolContext 解析 userAccountId/traceId

### 3.4 编排与灰度切换机制

**委托模式** — [ChatOrchestrator.handle()](backend/src/main/java/com/medora/agent/application/ChatOrchestrator.java):

```java
if (adkProperties.isEnabled() && adkOrchestrator.isPresent()) {
    return adkOrchestrator.get().orchestrate(command, emitter, cancelled);
}
// 否则走原有本地过程式路径(Phase 9.8 待清理)
```

- 配置开关:`medora.adk.enabled`(默认 false)
- 所有 ADK Bean 用 `@ConditionalOnProperty` 控制加载
- 灰度路径与本地路径**互斥**,零停机切换只需重启

### 3.5 工具调用清单

| 工具 | 风险 | 参数 | 返回 |
|------|------|------|------|
| queryDepartments | LOW | keyword, pageSize | `List<DepartmentDTO>` |
| queryDoctors | LOW | keyword, departmentId, pageSize | `List<DoctorDTO>` |
| queryScheduleSlots | LOW | scheduleDate, periodCode, departmentId | `List<SlotDTO>` |
| getUserAppointments | LOW | status, pageSize | `List<AppointmentDTO>` |
| bookAppointment | **HIGH** | slotId | `{appointmentNo, department, doctor, schedule}` |
| cancelAppointment | **HIGH** | appointmentId, reason | `{appointmentNo, cancelledAt}` |
| searchKnowledge | LOW | query | `List<RetrievalResult>` |

**调用链路**:ADK FunctionCall → `MedoraFunctionTool.runAsync()` → `ToolDefinition.execute()` → 序列化为 `{status, summary, data, errorCode, errorMessage}` → FunctionResponse 推送 SSE。

### 3.6 Trace 与可观测

**[AdkStreamResult](backend/src/main/java/com/medora/adk/sse/AdkStreamResult.java) 聚合字段**:
- `finalReply` — 最终回复
- `toolEventsJson` — 工具调用序列 `[{toolName, status, message, response}]`
- `safetyEventsJson` — 安全审查事件(当前为空,Phase 9.7 暂留)
- `hallucinationRisk` — 启发式风险(HIGH/MEDIUM/LOW)
- `transferChain` — agent 切换链路 `["root_router_agent", "booking_agent"]`

**[AgentTraceEntity](backend/src/main/java/com/medora/agent/trace/AgentTraceService.java) 持久化字段**:traceId/conversationId/messageId/intentType/routeMode/retrievalJson/toolEventsJson/latencyMs/status/errorMessage/safetyEventsJson/hallucinationRisk/riskFactorsJson。

### 3.7 LLM-first 原则(项目硬约束)

- **决策层 LLM-only**:意图路由、症状分诊、记忆提取、幻觉判断、引用覆盖 —— 失败抛 `LlmUnavailableException`,**不降级到正则**
- **格式层保留正则**:UUID、日期、schema 校验
- **消毒层双层**:正则快速前置 + LLM 语义审查
- **绝对红线**:不允许 `try { llm } catch { regex }` 同职责降级

### 3.8 Session/State 策略

- `sessionId = conversation.publicId`(跨系统安全 UUID)
- 每次请求都写入 per-request state keys(userAccountId/userMessage/traceId),避免"已存在就跳过"导致 state 丢失
- 历史上下文:从 `chat_message` 反构最近 30 条为 ADK Event(因为 ADK Runner 自身无跨会话记忆)

---

## 4. RAG / Knowledge / LLM 链路

### 4.1 RAG 数据流

```
文档上传 → DocumentParser
  → KnowledgeDocumentService.upload() 创建 KnowledgeDocumentEntity
  → DocumentIngestService.ingestAsync() 异步处理
  → DocumentChunker.chunk() 按章节/段落切分(500-800 字)
  → EmbeddingService.embed() 向量化(OpenAI 兼容 / 本地 Hash 兜底)
  → KnowledgeChunkRepository.save() 写 PostgreSQL + pgvector

查询 → RetrievalService.retrieve()
  → HybridRetriever: FTS + 向量检索融合
  → RetrievalResultAssembler 组装注入 Prompt
```

**关键类**:
- [DocumentIngestService](backend/src/main/java/com/medora/knowledge/service/DocumentIngestService.java)
- [EmbeddingService](backend/src/main/java/com/medora/ai/embedding/EmbeddingService.java)
- [RetrievalService](backend/src/main/java/com/medora/knowledge/service/RetrievalService.java)
- [KnowledgeVectorSearchRepository](backend/src/main/java/com/medora/knowledge/repository/KnowledgeVectorSearchRepository.java)

### 4.2 决策/格式/消毒三层分离

| 层 | 实现 | 降级策略 |
|----|------|----------|
| 决策层 | LLM Planner(LLM-only) | 失败抛异常,不降级 |
| 格式层 | PromptBuilder / Token 预算 | 确定性规则 |
| 消毒层 | SafetyGuard + OutputSafetyGuard + CitationCoverageChecker | 正则前置 + LLM 语义 |

### 4.3 上下文/记忆管理

- **短期**:最近 12 条消息,Redis 缓存(TTL 3600s),失败回源 DB
- **会话摘要**:主题/确认事实/待办/业务状态,LLM 优先生成
- **长期记忆**:`PREFERRED_DEPARTMENT/DOCTOR/PERIOD` + `LAST_SUCCESSFUL_APPOINTMENT`,TTL 180 天
- **置信度**:BOOK_APPOINTMENT=1.0 / USER_EXPLICIT=0.8 / USER_MENTION=0.3
- **裁剪顺序**:检索结果(分数)→ 长期记忆(置信度)→ 最近消息(时间倒序),最低保留 1 条
- **Token 预算**:prompt 1400 + completion reserve 600 + retrieval 120-420 + memory 80-260

---

## 5. SSE 流式协议

### 5.1 事件类型

| 事件 | Payload | 用途 |
|------|---------|------|
| `message_start` | `{messageId, conversationId, traceId}` | 流开始 |
| `message_delta` | `{delta}` | 文本增量(注:**当前为事件级流式,非 token 级**) |
| `tool_status` | `{toolName, status, message}` | 工具执行状态(STARTED/SUCCESS/FAILED) |
| `citation` | `{sourceId, title, snippet, index}` | 知识引用 |
| `message_end` | `{messageId, finishReason, usage, status}` | 流结束 |
| `error` | `{code, message}` | 错误 |

### 5.2 连接管理

- Spring `SseEmitter`(60s 超时)
- `ConcurrentHashMap<String, ActiveStreamHandle>` 追踪活跃流
- `AtomicBoolean cancelled` 协作式取消
- 支持 `POST .../stop` 主动停止
- onCompletion/onTimeout/onError 自动清理

---

## 6. 安全与运维

### 6.1 安全防护

- **输入层**:[SafetyGuard](backend/src/main/java/com/medora/agent/application/SafetyGuard.java) = 正则快速前置 + LLM 语义审查
- **输出层**:[OutputSafetyGuard](backend/src/main/java/com/medora/agent/application/OutputSafetyGuard.java) = 正则检测 + LLM 语义 + 医疗免责声明追加
- **引用覆盖**:[CitationCoverageChecker](backend/src/main/java/com/medora/agent/application/CitationCoverageChecker.java) = Embedding 余弦相似度 + 字符串匹配
- **高风险工具**:book/cancel 仅接受用户**明确提供**的参数,拒绝 LLM 推断 ID
- **频率限制**:单次对话内高风险工具只能调用一次

### 6.2 幂等性

- 预约创建支持 `Idempotency-Key` Header + DB 唯一索引
- SSE 流支持 stop 协议

### 6.3 后台/审计

- 操作者类型(USER/AGENT/SYSTEM)、操作类型、资源类型、requestId 关联
- 全链路 traceId 贯穿(ADK Event → SSE → Trace 表)

---

## 7. 已做的优化(按时间倒序)

### 7.1 Phase 9.7(最近一次)

1. **4 个运行时 bug 修复**(commit `bf2fef3`):
   - JAXB ClassNotFoundException:排除 `docker-java-transport-jersey`
   - `Runner.runAsync` 参数顺序修正为 `(userId, sessionId, content)`
   - `Session.events` 不可变 → 改为 `CopyOnWriteArrayList`
   - `ensureSession` state 写入被跳过 → 每次都调用 `createSession` 写 per-request keys
2. **SSE adapter 主体**(commit `c697a9d`):
   - `AdkEventToSseAdapter` 逐事件翻译
   - toolEvents 用 `functionCall.id` 配对
   - hallucinationRisk 启发式判断
3. **状态持久化策略调整**:per-request state keys 强制写入,避免并发请求互相覆盖

### 7.2 Embedding 混用过滤(2026-06-29)

- **问题**:`knowledge_chunks` 表发现多 embedding 模型混用,导致检索结果向量维度不一致
- **解决**:[KnowledgeVectorSearchRepository.searchPublished()](backend/src/main/java/com/medora/knowledge/repository/KnowledgeVectorSearchRepository.java) 增加 `embeddingModel` 参数过滤,只检索同模型产生的向量
- **遗留**:旧 chunks 需 rebuild 才能彻底统一

### 7.3 Phase 7-8 基础能力

- 会话摘要、长期记忆、上下文预算与裁剪
- 后台可观测:Trace 查看、审计日志、知识库管理
- 预约幂等性
- Redis 短期上下文缓存
- 高风险工具参数校验

---

## 8. 当前薄弱处 / 未完成点

> 这部分是讨论优化的重点。按优先级排序。

### 8.1 Agent 架构层面

| 优先级 | 问题 | 影响 |
|--------|------|------|
| **P0** | **本地降级路径冗余**:`ChatOrchestrator.handle()` 中 ADK 路径外仍有约 360 行过程式代码(IntentRouter/AgentModelGateway/PromptBuilder) | 维护成本翻倍,行为可能不一致 |
| **P0** | **双轨 Intent 决策**:`resolveIntent()` 仍保留 LLM Planner + IntentRouter 降级,违反 LLM-first 原则 | 决策不一致,测试覆盖难 |
| **P0** | **幻觉检测双轨**:`hallucinationRisk()` 保留规则分支,与 `LlmHallucinationAfterCallback` 并存 | 同上 |
| P1 | **token 级流式未实现**:当前用 `OpenAiChatModel` 非流式,事件级 SSE,首字延迟高 | UX 不佳 |
| P1 | **transferChain 未持久化**:agent 切换链路只在内存,Trace 表查不到 | 排障困难 |
| P1 | **Metrics 未分离 ADK 路径**:首 token 延迟/总延迟/工具调用成功率未按路径上报 | 灰度评估缺数据 |
| P2 | **Session State 结构化**:当前 `Map<String, Object>`,应定义 `MedoraSessionState` record | 类型安全差 |
| P2 | **测试覆盖**:`AdkOrchestratorTest` 未覆盖 transfer 场景 | 回归风险 |
| P2 | **`adk-poc/` 目录**:POC 代码与正式 backend 关系待确认 | 代码库混乱 |

### 8.2 RAG / Knowledge 层面

| 优先级 | 问题 | 影响 |
|--------|------|------|
| P1 | **Rerank 未实现**:接口预留,当前仅依赖分数排序 | 检索精度有限 |
| P1 | **Query Rewrite 未实现**:用户口语化查询直接检索,召回率低 | 检索质量差 |
| P1 | **Prompt 模板硬编码**:无动态模板管理 | A/B 测试困难 |
| P2 | **多模态检索**:仅支持文本,不支持图片/表格 | 场景受限 |
| P2 | **检索结果无缓存**:高频查询重复 embedding | 成本高 |
| P2 | **向量库选型**:pgvector 在大规模(>10M chunks)下性能不如专业向量库(Milvus/Qdrant) | 扩展性受限 |

### 8.3 SSE / 前端层面

| 优先级 | 问题 | 影响 |
|--------|------|------|
| P1 | **前端无重连/心跳**:网络抖动直接断流 | 用户体验差 |
| P1 | **前端无 Token 自动刷新**:过期需重新登录 | 体验断 |
| P1 | **前端无错误边界**:React 错误会白屏 | 稳定性差 |
| P2 | **无 SWR/React Query**:每次进页面重复请求 | 性能浪费 |
| P2 | **Markdown 无代码高亮**:prism.js 未接入 | UX |
| P2 | **前端无测试** | 回归风险 |

### 8.4 运维 / 可观测 / 扩展性

| 优先级 | 问题 | 影响 |
|--------|------|------|
| **P0** | **无限流/熔断**:无 Resilience4j,LLM 调用无频率限制 | 滥用/雪崩风险 |
| P1 | **SSE 单点**:`ChatRuntimeService.activeStreams` 在本地内存,多实例不共享 | 无法水平扩展 |
| P1 | **业务指标缺失**:意图识别准确率、工具调用成功率、检索命中率均未上报 | 难以量化优化效果 |
| P1 | **Trace ID 未注入日志上下文**:日志与 Trace 关联需手工 | 排障难 |
| P2 | **无日志聚合**:无 ELK/Loki | 跨实例排障难 |
| P2 | **Redis 单节点**:未见集群配置 | 高可用差 |

---

## 9. 给 GPT 讨论的几个开放问题

1. **灰度切换之后**:Phase 9.8 清理本地路径时,如何保证清理后**没有行为回退**?是否需要先把 Metrics 上齐,数据对比 1-2 周再删?
2. **token 级流式**:切到 `OpenAiStreamingChatModel` 后,`AdkEventToSseAdapter` 的事件配对逻辑需要怎么改?有没有更优雅的方式?
3. **transferChain 持久化**:应该作为独立字段,还是塞进 `toolEventsJson`?对前端展示有什么影响?
4. **多 agent 拓扑下一步**:现在是 4 agent 静态拓扑。是否应该引入动态路由(让 RootAgent 根据上下文选择子 agent)?或者更进一步,引入"专家 agent 协商"(多个 agent 共同回答)?
5. **RAG 精度**:Rerank + Query Rewrite 之外,要不要引入知识图谱?医疗领域图谱构建成本与收益怎么评估?
6. **水平扩展**:SSE 单点是 P0 级问题。Redis Pub/Sub vs Sticky Session vs 改用 WebSocket,哪个更适合当前架构?
7. **LLM 成本**:多 agent + 多 Callback(每次请求至少 4-6 次 LLM 调用)成本不低。是否应该用小模型做 Callback(安全审查/幻觉检测),大模型只做主决策?
8. **记忆系统**:长期记忆 TTL 180 天 + 置信度模型,会不会导致用户偏好"过期"反而更准?是否需要 decay 而非 hard TTL?

---

## 10. 技术栈一览

**后端**:Spring Boot / Java 21 / Maven / PostgreSQL + pgvector / Redis / Spring Data JPA / Micrometer + Prometheus

**前端**:Next.js 15 / React 19 / TypeScript 5.6 / Tailwind CSS 3.4 / react-markdown + remark-gfm

**AI**:OpenAI 兼容 API / Anthropic 兼容 API / 自研 ADK 抽象层

**部署**:Docker / Nginx / Compose 预留(deploy/ 目录)
