# Medora Agent 系统实现现状

> 用途:与外部 LLM 讨论后续优化 / 功能扩充方向的基础文档。专注 Phase 9(ADK 重构)之后的 agent 编排系统,不覆盖认证 / 业务 CRUD / 前端。
>
> 时间基准:2026-06-30。Phase 9.0-9.7 落地完成,后端测试 181/181 通过,ADK 路径已通过 UI 联调。

---

## 1. 项目背景

**Medora** 是一个医疗预约助手:用户用自然语言描述症状或预约诉求,系统通过多 agent 协作完成「症状分诊 → 科室推荐 → 查号源 → 挂号/取消」全流程,同时支持医学常识 RAG 问答。

技术栈:
- 后端:Spring Boot 3.3.5 + Java 21 + JPA + Postgres (+ pgvector) + Redis + MinIO
- LLM:DeepSeek(走 OpenAI 兼容端点)
- Agent 框架:**Google ADK Java 0.3.0** + `google-adk-langchain4j` 0.3.0(把 DeepSeek 桥接到 ADK 的 LangChain4j 接口)
- 前端:Next.js + SSE 流式

---

## 2. 整体架构:双层编排 + 灰度切换

系统目前处于**从手写编排迁移到 ADK 的灰度阶段**,两套编排路径并存:

```
HTTP POST /api/v1/chat/conversations/{id}/stream
        ↓
ChatStreamController
        ↓
ChatRuntimeService   ← 负责消息持久化、SSE emitter 生命周期、trace 记录
        ↓
ChatOrchestrator    ← 主入口
   ├── if medora.adk.enabled=true  →  AdkOrchestrator.orchestrate()  (新路径)
   └── else                        →  旧手写编排逻辑(IntentRouter + AgentModelGateway)
```

**关键点**:
- 灰度开关 `medora.adk.enabled` 用 `@ConditionalOnProperty` 控制 ADK 相关 bean 是否加载。
- 两条路径对外**SSE 协议完全一致**(都是 `message_start` / `message_delta` / `tool_status` / `message_end` / `error`)。
- 旧路径尚未删除 — 是兜底,等 ADK 路径稳定运行足够时间后再做 Phase 9.8 清理。

**入口文件**:
- [ChatStreamController.java](../backend/src/main/java/com/medora/chat/controller/ChatStreamController.java)
- [ChatRuntimeService.java](../backend/src/main/java/com/medora/chat/service/ChatRuntimeService.java)
- [ChatOrchestrator.java](../backend/src/main/java/com/medora/agent/application/ChatOrchestrator.java)(主入口,内含灰度分叉)
- [AdkOrchestrator.java](../backend/src/main/java/com/medora/adk/orchestrator/AdkOrchestrator.java)(新路径)

---

## 3. ADK 4-Agent 拓扑(新路径)

拓扑定义在 [AdkAgentTopology.java](../backend/src/main/java/com/medora/adk/agents/AdkAgentTopology.java)。**LLM-native routing** — 路由靠 LLM 决策,不是规则或向量。

```
root_router_agent   (instruction-only, 无业务工具, maxSteps=2)
  │   职责: 判断意图, 调 transfer_to_agent 切到子 agent
  │   路由规则(in instruction 里写死):
  │     症状/不适       → triage_agent
  │     挂号/预约/取消   → booking_agent
  │     医学知识/常识    → knowledge_agent
  │
  ├── triage_agent     tools=[searchKnowledge]                     maxSteps=4
  │     职责: 症状 → 推荐就诊科室(不做诊断)
  │
  ├── booking_agent    tools=[6 个预约工具]                         maxSteps=8
  │     职责: 查询/挂号/取消
  │     beforeTool: HighRiskToolBeforeCallback (slotId/appointmentId 非空校验)
  │
  └── knowledge_agent  tools=[searchKnowledge]                     maxSteps=5
        职责: 基于 RAG 的医学常识问答
        afterModel: LlmHallucinationAfterCallback (引用支撑度评分)
```

**所有 4 个 agent 共享** `LlmInputSafetyBeforeCallback`(beforeModel) 和 `LlmOutputSafetyAfterCallback`(afterModel)。

**transfer 机制**:ADK 原生 `transfer_to_agent`,Root LLM 输出该 tool call,ADK 自动切换会话状态到子 agent;子 agent 自己跑工具循环直到 `turnComplete`。子 agent 之间也能互转(如 triage 觉得用户实际要挂号,可转给 booking),但 instruction 里没显式鼓励。

**多 callback 装配限制**(踩过的坑):ADK 0.3.0 的 `LlmAgent.builder().beforeModelCallbackSync(...)` 字节码用 `putfield`(覆盖),不是 add — 多次链式调用只保留最后一个。所以**所有 beforeModel 逻辑必须合并到一个 callback 类里**(当前合并进 `LlmInputSafetyBeforeCallback`,同时做安全审查 + today 注入)。

---

## 4. 工具层

### 4.1 7 个工具([ToolNames.java](../backend/src/main/java/com/medora/agent/tool/ToolNames.java))

| 工具 | 风险等级 | 落库 |
|---|---|---|
| queryDepartments | LOW | 只读 |
| queryDoctors | LOW | 只读 |
| queryScheduleSlots | LOW | 只读 |
| getUserAppointments | LOW | 只读 |
| **bookAppointment** | **HIGH** | 写 appointment + 操作日志 + 审计日志 |
| **cancelAppointment** | **HIGH** | 改 appointment 状态 + 释放号源 + 操作/审计日志 |
| searchKnowledge | LOW | 只读(pgvector 检索) |

### 4.2 ToolDefinition → ADK BaseTool 桥接

[MedoraFunctionTool.java](../backend/src/main/java/com/medora/adk/tool/MedoraFunctionTool.java) 直接子类化 `BaseTool`,**手动构建 `FunctionDeclaration`**,而不是用 ADK 自带的反射方案。

**为什么不用 ADK 反射方案**:项目的 `ToolDefinition.execute(Map args, ToolExecutionContext ctx)` 中 `ToolExecutionContext` 含 `userAccountId` / `traceId` 等「LLM 不可见但工具需要」的内部上下文,无法被反射框架处理。

**返回结构**(给 LLM 看的):
```json
{
  "status": "SUCCESS" | "FAILED",
  "summary": "...",          // 给 LLM 的简短中文描述
  "data": {...},             // 工具原始数据(JsonNode 形式,避开 OffsetDateTime 序列化坑)
  "errorCode": "...",        // 失败时
  "errorMessage": "..."      // 失败时
}
```

### 4.3 高风险工具三层校验(以 bookAppointment 为例)

```
LLM 决定调 bookAppointment(slotId)
        ↓
1. HighRiskToolBeforeCallback (beforeTool, 确定性)
   校验 slotId 是 String 且非空 — 失败抛 AGENT_TOOL_ARGUMENT_MISSING
        ↓
2. BookAppointmentTool.execute (工具自身, 防御性)
   再次校验 slotId 非空 — 失败抛 BusinessException
        ↓
3. AppointmentCommandService.createForUser (落库层)
   - 幂等键查重 (idempotencyKey 命中直接返回已存在预约)
   - schedule_slot 行级锁 findByPublicIdForUpdate
   - 同用户同 slot 重复 (existsByUserAccountAndSlotAndStatus)
   - slot.reserveOne() 余量检查 (entity 内部)
   - 写 appointment + operation_log + audit_log
```

**已知校验缺口**(可能讨论点):
- 不校验 slotId 是否属于「上一轮 queryScheduleSlots 返回的集合」— LLM 可能凭空幻觉 UUID
- 不校验 slot.scheduleDate 在未来
- 不校验 slot 与对话上下文科室/医生一致

---

## 5. Callback 防护层(LLM-first 分层策略)

5 个 callback,按「LLM 决策 vs 确定性校验」分层:

| Callback | 类型 | 挂载点 | LLM 还是正则 |
|---|---|---|---|
| **LlmInputSafetyBeforeCallback** | beforeModel | 全部 agent | **正则 + LLM 双层** |
| **LlmOutputSafetyAfterCallback** | afterModel | 全部 agent | LLM |
| **LlmHallucinationAfterCallback** | afterModel | 仅 knowledge | LLM(评分) |
| **HighRiskToolBeforeCallback** | beforeTool | 仅 booking | 正则(参数非空) |
| **TodayContextInjection** | (合并进 InputSafety) | 全部 agent | — |

### LLM-first 原则

**决策层 LLM-only**(失败抛 `LlmUnavailableException`,不降级):
- InputSafety 的角色劫持 / 指令覆盖 / 提示探查检测
- OutputSafety 的医疗诊断 / 用药建议检测
- Hallucination 的引用支撑度评分(score < 0.6 替换回复)

**格式/消毒层保留正则**:
- InputSanitizer 的代码注入 / SQL 注入 / 数据破坏检测
- HighRiskToolBeforeCallback 的 slotId/appointmentId 格式校验
- UUID / schema / 日期格式解析

**关键设计意图**:LLM 不可用时,业务直接失败而不是退回规则兜底 — 避免规则路径与 LLM 路径行为分叉(参见 [feedback_no_regex_fallback](../memory/feedback_no_regex_fallback.md))。

### Today 注入

2026-06-30 新增,合并进 `LlmInputSafetyBeforeCallback.call()` 头部:
```java
builder.appendInstructions(List.of(
    "当前日期: " + today + " (" + weekdayCn + ")。解读\"今天/明天/下周X\"等相对日期时以此为准。"
));
```

用 `Clock` bean(`medora.ai.zone-id`,默认 Asia/Shanghai)取 today,避免裸用 `LocalDate.now()` 依赖 JVM 默认时区导致跨日 bug。

---

## 6. Session 持久化层

[PostgresBackedSessionService.java](../backend/src/main/java/com/medora/adk/session/PostgresBackedSessionService.java) — ADK `BaseSessionService` 的 Postgres 实现。

### 6.1 映射关系

| ADK 概念 | Medora 落地 |
|---|---|
| `appName` | 固定 `"medora"` |
| `sessionId` | `conversation.publicId` (UUID 字符串) |
| `userId` | `userAccount.id` (字符串) |
| `session.state` | `conversation.session_state` 列(JSONB) |
| `session.events` | 内存中从 `chat_message` 表反构最近 30 条 |

### 6.2 关键设计:events 反构

**ADK Runner 内部不调 `listEvents`** — 它依赖 `Session.events()` 字段自带全部历史。所以每次 `getSession`/`createSession` 时,必须从 `chat_message` 表反构最近 30 条历史消息为 ADK Event,塞进 `Session.events()`(用 `CopyOnWriteArrayList`,兼顾可变 + 线程安全)。

Runner 在本轮 `appendEvent` 加的新 user/assistant Event 会在内存 list 上叠加。下一轮请求来时 `getSession` 重新反构(那时上一轮消息已被 `ChatRuntimeService` 写入 `chat_message`),自然延续。

### 6.3 跨轮 slot 累积(upsert + merge 语义)

`createSession` **不覆盖**已有 state,先读后合并:
```java
Map merged = new ConcurrentHashMap<>(existing);
merged.putAll(incoming);  // 新 key 覆盖同 key, 旧 key 保留
```

这保证每轮工具调用写入的 slot 值不会被下一轮请求重置。这是 Phase 9.3 之后的修复 — 之前是直接 setSessionState 覆盖,每轮丢失上一轮累积。

### 6.4 ToolExecutionContext 注入

工具执行时需要的「LLM 不可见」上下文(userAccountId / traceId / userMessage)通过 `session.state` 传递。`AdkToolContextResolver` 从 state 取 3 个 key:
- `medora:userAccountId` (Long)
- `medora:userMessage` (String)
- `medora:traceId` (String)

字段缺失时 `IllegalStateException` fail-fast。

---

## 7. SSE 事件流

### 7.1 协议(7 种事件)

[StreamEventPublisher.java](../backend/src/main/java/com/medora/chat/sse/StreamEventPublisher.java) 定义:

| 事件 | 用途 |
|---|---|
| `message_start` | 消息开始(messageId/conversationId/traceId) |
| `message_delta` | 流式 token |
| `message_end` | 消息结束 |
| `tool_status` | 工具状态(STARTED/SUCCESS/FAILED) |
| `citation` | 知识库引用来源 |
| `error` | 错误(code + message) |
| `trace` | 调试信息(聚合 trace 字段) |

### 7.2 AdkEventToSseAdapter(新路径)

[AdkEventToSseAdapter.java](../backend/src/main/java/com/medora/adk/sse/AdkEventToSseAdapter.java) 订阅 ADK 的 `Flowable<Event>` 流,逐事件翻译:

| ADK Event 内容 | 翻译为 |
|---|---|
| `Part.text()` | `message_delta` |
| `Part.functionCall()` | `tool_status(STARTED)` |
| `Part.functionResponse()` | `tool_status(SUCCESS/FAILED)` |
| `Event.actions().transferToAgent` | 累积到 transferChain(前端可见性待补) |
| 异常 / 订阅终止 | `error` 事件 |

同步累积 `Part.text()` 到 StringBuilder,最终用于 `chat_message` 持久化。

**Fallback**:若工具调用后 reply 为空,调用轻量级 LLM 生成总结,或走模板兜底。

### 7.3 流式输出链路

```
DeepSeek 流式 token
  → ADK Runner Flowable<Event>
  → AdkEventToSseAdapter.processPart 提取 Part.text()
  → StreamEventPublisher.publishDelta (SseEmitter.send)
  → 前端 EventSource
```

---

## 8. 关键设计决策(供 GPT 评估)

1. **LLM-native routing,不用规则路由**:Root agent 完全靠 LLM 决策转给哪个子 agent。优点是处理复杂/模糊意图;缺点是延迟高(每次都过一次 LLM)、不可解释。

2. **决策层 LLM-only,不降级**:InputSafety / OutputSafety / Hallucination 评分失败时直接抛异常中断,不退回规则路径。理由是规则路径与 LLM 路径行为会分叉。代价是 LLM 服务波动 = 业务全停。

3. **工具落库校验不调 LLM**:HighRiskToolBeforeCallback 只做格式校验(slotId 非空),不验证 slotId 是否真的属于上一轮 queryScheduleSlots 返回集合。理由是「每次预约多一次 LLM 调用得不偿失」。代价是 LLM 幻觉的 UUID 能通过 callback,直到落库层 `MASTER_SLOT_NOT_FOUND` 才暴露。

4. **跨轮 slot 累积靠 Session.state JSONB**:不另建表。优点是 schema 灵活;代价是 state 大小无限制,长对话可能膨胀。

5. **events 反构最近 30 条**:不是无限上下文。30 条之后早期消息 LLM 看不到(只看 `chat_message` 表完整历史)。优点是 token 受控;代价是长对话会「失忆」。

6. **灰度切换但不删旧路径**:旧 ChatOrchestrator + IntentRouter 完整保留,通过 `medora.adk.enabled` 切换。代价是双份维护成本、双份 bug 风险。

7. **session_state 用 conversation 表的 JSONB 列**:不另建 session 表。代价是 conversation 行变大,JSONB 索引/查询性能受限。

8. **所有时间获取走 Clock bean(2026-06-30 修复)**:不允许裸用 `LocalDate.now()`。时区配置 `medora.ai.zone-id`(默认 Asia/Shanghai)。

---

## 9. 已知问题 & 扩展候选(讨论焦点)

### 9.1 已知问题

- **transferToAgent 前端可见性未完成**:SSE adapter 累积了 transferChain 但没推给前端,用户看不到「我正在把你转给预约助手」。
- **Root router maxSteps=2 偏紧**:复杂意图可能在 Root 这里就被截断,无法 transfer。
- **30 条 events 上限**:长会话失忆。无 summary-based compression 机制。
- **HighRiskTool 不校验 slotId 来源**:LLM 幻觉的 UUID 能过 callback。
- **无 streaming tool for cancellation**:取消预约要等同步落库完才知道结果,中间无反馈。
- **旧代码未清理**:IntentRouter 维护成本(本次时区修复就同时改了新旧两套)。

### 9.2 功能扩充候选

- **新增 agent**:复诊提醒 / 报告解读 / 投诉受理 / 跨科室会诊推荐
- **多模态**:化验单图片上传 + OCR + 解读
- **主动外呼**:约定时间到达时 agent 主动 SSE 推送提醒
- **用户偏好长期记忆**:已有 `UserMemoryProfileEntity` 基础,可加 agent 自动提取偏好
- **多语言**:当前 instruction 全中文,扩展英文/方言
- **专家 agent 推荐**:根据用户历史预约 + 评价,主动推荐医生
- **agent 自我反思**:每次 turn 结束后让 agent 评估自己的回复质量(类似 reflexion 模式)

### 9.3 架构演进候选

- **删除旧路径**:Phase 9.8 旧代码清理
- **event sourcing**:把 chat_message 改为 event log,支持 replay
- **agent registry / plugin 化**:agent 定义外置( yaml / db),不重编代码加新 agent
- **token 预算分配**:每个子 agent 独立 token 上限,避免一个 agent 吃掉整个对话预算
- **可观测性**:OpenTelemetry trace 接入,看到每次 transfer / tool call 的 span
- **A/B 测试框架**:同时跑新旧路径,对比质量

---

## 10. 文件导航(主要文件清单)

```
backend/src/main/java/com/medora/
├── chat/
│   ├── controller/ChatStreamController.java          ← HTTP 入口
│   ├── service/ChatRuntimeService.java               ← 消息持久化 + SSE 生命周期
│   └── sse/StreamEventPublisher.java                 ← SSE 事件发布器
├── agent/application/
│   ├── ChatOrchestrator.java                         ← 旧路径主入口 + 灰度分叉
│   ├── IntentRouter.java                             ← 旧路径规则路由(中文日期解析等)
│   └── AgentModelGateway.java                        ← 旧路径 LLM 网关(planner/reply/safety)
├── adk/
│   ├── orchestrator/AdkOrchestrator.java             ← 新路径主入口
│   ├── agents/AdkAgentTopology.java                  ← 4 agent 装配
│   ├── guard/
│   │   ├── LlmInputSafetyBeforeCallback.java         ← beforeModel (含 today 注入)
│   │   ├── LlmOutputSafetyAfterCallback.java         ← afterModel
│   │   ├── LlmHallucinationAfterCallback.java        ← afterModel (仅 knowledge)
│   │   └── HighRiskToolBeforeCallback.java           ← beforeTool (仅 booking)
│   ├── tool/
│   │   ├── MedoraFunctionTool.java                   ← ToolDefinition → ADK BaseTool 桥接
│   │   ├── ToolSchemaRegistry.java                   ← 工具 schema 注册
│   │   └── AdkToolContextResolver.java               ← session state → ToolExecutionContext
│   ├── session/PostgresBackedSessionService.java     ← ADK Session 接 Postgres JSONB
│   └── sse/AdkEventToSseAdapter.java                 ← ADK Event → 项目 SSE 协议
├── agent/tool/
│   ├── ToolNames.java                                ← 7 个工具名常量
│   ├── ToolDefinition.java                           ← 工具接口
│   ├── ToolExecutionResult.java                      ← 工具返回结构
│   └── tools/                                        ← 7 个工具实现
└── appointment/service/AppointmentCommandService.java ← 落库层(预约/取消业务)
```

---

## 11. 与 GPT 讨论的开放问题

1. **多 agent 路由的延迟问题**:Root router 多一次 LLM 调用,首 token 延迟可能不可接受。是否应该用更小的 fast model 做 routing,大 model 做实际回答?
2. **LLM-only 不降级策略 vs 可用性**:服务波动时业务全停,是否应该选择性降级(如 InputSafety 失败仍允许通过,但加风险标记)?
3. **session_state JSONB 的扩展性**:state 大小会膨胀,是否应该改为独立的 session_state 表 + TTL 清理?
4. **30 条 events 上限**:是否该上 summary-based compression(每 N 条总结一次,保留 summary + 最近 K 条原文)?
5. **HighRiskTool 是否需要 source-of-truth 校验**:在 session.state 维护「最近一次 queryScheduleSlots 返回的 slotId 集合」,bookAppointment 时校验 slotId ∈ 该集合?
6. **agent 拓扑的演进路径**:加新 agent 是直接在 AdkAgentTopology 改代码,还是上 plugin / registry 化?
7. **可观测性**:当前 AgentTrace 表记录了什么?够不够做线上问题定位?
8. **多模态扩充**:加图片输入(化验单 OCR)对当前架构冲击多大?需要哪些改造?
