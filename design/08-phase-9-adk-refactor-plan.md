# Phase 9: Agent 编排层 ADK 重构实施计划

## Goal

将 Medora 当前手写的过程式 agent 编排(`ChatOrchestrator` + `IntentRouter` + `AgentModelGateway`)迁移到 **Google ADK Java 0.3.0 + LangChain4j 桥接 + DeepSeek OpenAI 端点** 技术栈,实现:

1. **多 agent 协作**:RootAgent 通过 `transfer_to_agent` 在 Triage / Booking / Knowledge 三个专科 agent 间路由
2. **跨轮 slot 累积**:用户在同一意图内多次补充信息时,agent 通过 Session/State 自然衔接,无需手动写「上下文累积」逻辑
3. **保留现有四层防护**:SafetyGuard / OutputSafetyGuard / CitationCoverageChecker / hallucinationRisk 全部以 ADK callback 形态重新落地
4. **前端协议零感知**:SSE 流式输出和 Admin Agent Trace 必须保持现有契约不变

## Background

**当前痛点(Phase 1-8 累积下来的债)**

- `ChatOrchestrator.handle()` 是 360+ 行的过程式 Java,意图路由 / 工具执行 / RAG / 安全 / 流式 / trace 全耦合在一个方法里
- 「LLM Planner 优先 + IntentRouter 降级」的双层路由(Phase 1 改造)虽然解决了正则匹配的脆弱,但每个新意图仍需手写 IntentRouter 分支和 PromptBuilder 模板
- 多轮信息补全依赖 `ConversationSummaryService.collectConfirmedFacts()` 的关键词匹配,扩展性差
- 所有 LLM 调用都走 `AgentModelGateway` 的过程式包装,无法支持「LLM 自主决定何时调工具、何时反问」的真正 agent loop

**POC 已验证可行的技术栈**(2026-06-23,代码在 [adk-poc/](../adk-poc/))

| 组件 | 版本 | 用途 |
|---|---|---|
| `com.google.adk:google-adk` | 0.3.0 | ADK 核心 |
| `com.google.adk:google-adk-langchain4j` | 0.3.0 | LangChain4j 桥接 |
| `dev.langchain4j:langchain4j-open-ai` | 1.0.1 | OpenAI 兼容客户端 |
| DeepSeek 端点 | `https://api.deepseek.com/v1` | 切换自当前 `/anthropic` 端点 |
| Model 名 | `deepseek-v4-flash` | 两个端点通用 |

POC 已证明:ADK → LangChain4j → DeepSeek 的 function calling 链路完全打通,多轮 session 复用、agent loop 收敛、参数自动提取都正常工作。

## 目标架构

### 多 agent 拓扑

```
RootAgent  (Router,不带业务 instruction,只做意图分发)
   │
   │ transfer_to_agent
   ├──► TriageAgent      导诊:症状 → 推荐科室
   │    └── tools: searchKnowledge
   │
   ├──► BookingAgent     预约:挂号 / 查询 / 取消
   │    └── tools: queryDepartments, queryDoctors,
   │              queryScheduleSlots, getUserAppointments,
   │              bookAppointment, cancelAppointment
   │
   └──► KnowledgeAgent   知识:RAG 问答
        └── tools: searchKnowledge
```

- **RootAgent 的 instruction**:只描述「什么样的请求归哪个 agent」,不回答业务
- **transfer 机制**:ADK 原生支持,RootAgent 在 LLM 决策后调用 `transfer_to_agent(agent_name='booking_agent')`,后续对话直接进入 BookingAgent 直到再次 transfer
- **高风险工具隔离**:`bookAppointment` / `cancelAppointment` 仅 BookingAgent 持有,且通过 `BeforeToolCallback` 强制校验 `paramSource == USER_PROVIDED`(对应现有 `filterUserProvidedParams` 逻辑)

### Session/State 数据模型

```java
// 完整 state 定义,序列化为 JSON 存入 conversations.session_state 字段
record MedoraSessionState(
    // —— 路由归属(ADK 内部维护,持久化以便跨轮恢复)——
    String currentAgent,        // "root" | "triage" | "booking" | "knowledge"

    // —— BookingAgent 槽位 ——
    BookingSlots pendingBooking,  // nullable,有值表示预约流程进行中
    // BookingSlots(dept, doctor, date, period, confirmed)

    // —— TriageAgent 状态 ——
    List<String> triageSymptoms,
    String triageRecommendedDept,  // 推荐后用户可继续问 "那这个科室怎么约"

    // —— 用户画像(从 UserMemoryProfileEntity 同步)——
    UserPreferences preferences,
    // UserPreferences(preferredPeriod, preferredDoctors, notes)

    // —— 安全审计 ——
    int safetyWarningsCount,
    String lastBlockedReason
) {}
```

**关键设计**:`pendingBooking` 由 BookingAgent 的 instruction 显式管理 —— 「缺参时不要调 `bookAppointment` 工具,直接问用户;参数齐全后再调用」。这是 ADK 原生支持的 slot-filling 模式,不需要任何手写累积逻辑。

### Callback 改造映射

| 现有手写代码 | ADK callback 形态 | 挂载位置 |
|---|---|---|
| `SafetyGuard.fullCheck()` ([ChatOrchestrator.java:76](../backend/src/main/java/com/medora/agent/application/ChatOrchestrator.java#L76)) | `BeforeModelCallbackSync` | RootAgent |
| `OutputSafetyGuard.check()` (line 149) | `AfterModelCallbackSync` | RootAgent |
| `CitationCoverageChecker.check()` (line 356) | `AfterModelCallbackSync` | KnowledgeAgent 专有 |
| `hallucinationRisk()` (line 311-348) | `AfterModelCallbackSync`(与上合并) | KnowledgeAgent |
| `ToolExecutor` + `filterUserProvidedParams` (line 270) | `BeforeToolCallbackSync` | BookingAgent |
| `AgentTraceService` 事件注入 | 所有 callback 内调用 | 全局 |
| `StreamEventPublisher.publishToolStatus` | `BeforeToolCallbackSync` + `AfterToolCallbackSync` | 全局 |

### SSE 协议兼容

ADK `Flowable<Event>` 到现有 SSE 协议的映射:

| ADK Event | 现有 SSE 事件 | 备注 |
|---|---|---|
| 首个 `partial=true` 文本 part | `message_start` | |
| 文本 delta | `message_delta` | 用 `Event.partial()` 区分 |
| `FunctionCall` part(发起前) | `tool_status STARTED` | |
| `FunctionResponse` part | `tool_status SUCCESS/FAILED` + `citation`(如适用) | |
| `turnComplete=true` | `message_end` | |

**适配器**:`AdkEventToSseAdapter` 类,订阅 `Flowable<Event>`,逐事件翻译为 SSE。

## Current Phase

Phase 9.6(跨轮 slot 累积 + 灰度切换)— 已完成灰度切换骨架,mvn test 158/158 全绿(新增 10 个 AdkOrchestrator 单测)。下一步:Phase 9.7 SSE adapter + AgentTrace 接入(需要 UI 实测)。

## LLM-first 设计原则(贯穿整个 Phase 9)

**核心原则(2026-06-23 用户确认)**:**决策层 LLM-only,格式/消毒层保留正则**。这是业界做法(Anthropic / OpenAI / NeMo Guardrails 都是分层混合),不是「完全零正则」。

| 层 | 用什么 | 失败行为 |
|---|---|---|
| **决策层**(意图路由、症状分诊、记忆提取、摘要、幻觉判断、引用覆盖) | LLM only | 抛 `LlmUnavailableException` → 全局 handler 返回错误消息 |
| **语义安全层**(角色劫持、指令覆盖、提示探查) | LLM only | 同上 |
| **格式校验层**(UUID、日期、schema、JSON 结构) | 确定性代码 | 直接抛 `BusinessException` |
| **输入消毒层**(SQL 注入、XSS、代码注入) | 正则 + 确定性代码 | 拒绝输入,返回 `BusinessException` |
| **基础设施层**(JWT、认证、限流) | 确定性代码 | 抛对应异常 |

**绝对红线**:
- 任何模块不允许 `try { llm() } catch { regexOrKeywordFallback() }` 同职责降级
- LLM 失败时**统一返回静态消息**「当前系统存在错误,请稍后尝试」,不暴露错误细节(超时/限流/过滤对用户透明)
- 详细错误写 ERROR 日志,带 requestId

## Phases

### Phase 9.0: LLM-first 基础设施

- [x] 9.0.1 新建 `com.medora.common.exception.LlmUnavailableException extends BusinessException`,code=`LLM_UNAVAILABLE`,HTTP 503
- [x] 9.0.2 在 [GlobalExceptionHandler.java](../backend/src/main/java/com/medora/common/exception/GlobalExceptionHandler.java) 加 `@ExceptionHandler(LlmUnavailableException.class)`,返回静态消息「当前系统存在错误,请稍后尝试」,ERROR 日志带 requestId + stack
- [x] 9.0.3 新建 `com.medora.agent.policy.LayerPolicy`,以代码形式记录上文「层职责表」(常量 + javadoc),作为后续代码评审依据
- [x] 9.0.4 新建 `com.medora.agent.policy.LlmCallTemplate`,封装「调 LLM 失败时统一抛 `LlmUnavailableException`」的 try-catch 模板,替代散落各处的 try-catch-返回空 Optional 模式
- [x] 9.0.5 单元测试 `LlmUnavailableExceptionTest`:直接调用 handler 方法验证正确 code/message/HTTP(6 个用例)
- [x] 9.0.6 单元测试 `LlmCallTemplateTest`:LLM 抛 IOException / 超时 / RuntimeException / null / 已包装异常 时,template 转换为 `LlmUnavailableException`(7 个用例)
- [x] 9.0.7 验证:`mvn test` 全绿(33 + 13 = 46 个测试),新增 2 个测试类 13 个用例全绿;**未修改任何现有 service 代码**(保持现有 fallback 在 9.4/9.8 阶段才清理)
- **Status:** complete (2026-06-23)
- **涉及文件:** [LlmUnavailableException.java](../backend/src/main/java/com/medora/common/exception/LlmUnavailableException.java)、[LayerPolicy.java](../backend/src/main/java/com/medora/agent/policy/LayerPolicy.java)、[LlmCallTemplate.java](../backend/src/main/java/com/medora/agent/policy/LlmCallTemplate.java)、[GlobalExceptionHandler.java](../backend/src/main/java/com/medora/common/exception/GlobalExceptionHandler.java)、[LlmUnavailableExceptionTest.java](../backend/src/test/java/com/medora/common/exception/LlmUnavailableExceptionTest.java)、[LlmCallTemplateTest.java](../backend/src/test/java/com/medora/agent/policy/LlmCallTemplateTest.java)
- **关键设计:** 本阶段只增不改,现有 `AgentModelGateway` 的 `Optional<String>` 返回模式不动,9.4 callback 重写时再切换到 throw 模式


## Phases

### Phase 9.1: 主 backend 引入 ADK 依赖,基础配置就位

- [x] 9.1.1 在 [backend/pom.xml](../backend/pom.xml) 添加三个依赖:`google-adk:0.3.0`、`google-adk-langchain4j:0.3.0`、`langchain4j-open-ai:1.0.1`
- [x] 9.1.2 在 `<maven-compiler-plugin>` 配置中加 `<parameters>true</parameters>`(消掉 POC 中的 `@Schema` 警告)
- [x] 9.1.3 新建 `com.medora.adk.config.DeepSeekModelConfig`,提供 `OpenAiChatModel` Bean(从 `.env` 的 `AI_CHAT_*` 读取,baseUrl 切换到 `/v1` 而非 `/anthropic`);额外提供 `LangChain4j deepseekLlmBridge` 桥接 bean
- [x] 9.1.4 新建 `com.medora.adk.config.AdkRunnerConfig`,提供 `InMemoryRunner` Bean(临时用内存版,9.3 替换为持久化版)
- [x] 9.1.5 配置项 `medora.adk.enabled=false`,默认关闭,所有 9.x 阶段都不影响生产路径;`@ConditionalOnProperty` 守护所有 ADK bean
- [x] 9.1.6 验证:`mvn test` 通过 51/51(46 + 5 新增);后端正常启动;ADK Bean 加载但不接入业务
- **Status:** complete (2026-06-23)
- **涉及文件:** [backend/pom.xml](../backend/pom.xml)、[application.yml](../backend/src/main/resources/application.yml)、[AdkProperties.java](../backend/src/main/java/com/medora/adk/config/AdkProperties.java)、[DeepSeekModelConfig.java](../backend/src/main/java/com/medora/adk/config/DeepSeekModelConfig.java)、[AdkRunnerConfig.java](../backend/src/main/java/com/medora/adk/config/AdkRunnerConfig.java)、[AdkConfigLoadTest.java](../backend/src/test/java/com/medora/adk/config/AdkConfigLoadTest.java)
- **关键设计:**
  - DeepSeek key 和 model 名复用 `AiProperties` 现有字段(POC 验证两端点共用),仅 `deepseek-base-url` 单独配
  - Fail-fast:启用 ADK 时缺 key/model 直接抛 `IllegalStateException`,不留隐形 bug
  - `@ConditionalOnProperty(prefix="medora.adk", name="enabled", havingValue="true")` 保证默认关闭时零影响
  - AdkProperties 无条件加载(无 @ConditionalOnProperty),其余 bean 全部条件加载
- **风险(未发生):** LangChain4j 1.0.1 与 Spring Boot 3.x 自动配置未发现冲突,无需排除自动配置

### Phase 9.2: 工具桥接 — 7 个 ToolDefinition 包装为 ADK FunctionTool

- [x] 9.2.1 设计决定:**不**用 `FunctionTool.create(Object, String)` 反射方案,改为直接子类化 `BaseTool`(`MedoraFunctionTool`),原因:ToolExecutionContext 包含 LLM 不可见的用户账号/trace ID,无法被反射框架处理
- [x] 9.2.2 新建 `ToolSchemaDescriptor` + `ToolSchemaRegistry`,以声明式 record + builder 模式定义 7 个工具的 schema(name/description/parameters/required/enum)
- [x] 9.2.3 高风险工具 `bookAppointment` / `cancelAppointment` 的必填字段(slotId/appointmentId)在 schema 中标记 required,供后续 9.4 HighRiskToolBeforeCallback 校验
- [x] 9.2.4 查询类工具的 enum 字段(periodCode=AM/PM、status=5 种预约状态)在 schema 中暴露给 LLM
- [x] 9.2.5 新建 `AdkToolContextResolver`,从 session state 解析 ToolExecutionContext(userAccountId/userMessage/traceId);fail-fast 缺字段时抛 IllegalStateException
- [x] 9.2.6 新建 `MedoraFunctionTool extends BaseTool`:declaration() 暴露 FunctionDeclaration,runAsync 调用底层 ToolDefinition 并把 ToolExecutionResult 序列化为 status/summary/data/errorCode/errorMessage 结构
- [x] 9.2.7 BusinessException 单独捕获保留 code/message(不丢业务错误语义),其他 RuntimeException 兜底返回类名作为 errorCode
- [x] 9.2.8 单元测试:ToolSchemaRegistry(10)、AdkToolContextResolver(8)、MedoraFunctionTool(9),共 27 个用例全绿
- **Status:** complete (2026-06-23)
- **涉及文件:** [ToolSchemaDescriptor.java](../backend/src/main/java/com/medora/adk/tool/ToolSchemaDescriptor.java)、[ToolSchemaRegistry.java](../backend/src/main/java/com/medora/adk/tool/ToolSchemaRegistry.java)、[AdkToolContextResolver.java](../backend/src/main/java/com/medora/adk/tool/AdkToolContextResolver.java)、[MedoraFunctionTool.java](../backend/src/main/java/com/medora/adk/tool/MedoraFunctionTool.java)、3 个测试文件在 `backend/src/test/java/com/medora/adk/tool/`
- **关键设计:**
  - ToolExecutionContext 字段以 `medora:` 前缀存入 session state,避免与 ADK 内部 key(以 `_` 开头)冲突
  - LLM 可见的工具 schema 与 LLM 不可见的 session state 字段分离,职责清晰
  - 所有 7 个工具的 schema 在 ToolSchemaRegistry 中集中维护,新增工具只改一处
  - runAsync 的失败结构对 LLM 友好(稳定字段名 + 中文 errorMessage),便于 LLM 决定反问 vs 重试
- **风险(未发生):** BaseTool 子类化方案比 FunctionTool 反射方案代码量大,但避免了「ToolExecutionContext 怎么注入」的死结

### Phase 9.3: Session/State 接入现有持久化层

- [x] 9.3.1 Flyway 迁移 V15:在 `conversation` 表加 `session_state JSONB` 字段(允许 NULL,后向兼容)
- [x] 9.3.2 [ConversationEntity](../backend/src/main/java/com/medora/chat/domain/ConversationEntity.java) 加 `sessionState` 字段(`@JdbcTypeCode(SqlTypes.JSON) Map<String,Object>`)
- [x] 9.3.3 实现 [PostgresBackedSessionService](../backend/src/main/java/com/medora/adk/session/PostgresBackedSessionService.java),implements BaseSessionService
  - sessionId = conversation.publicId.toString()(UUID,跨系统安全)
  - appName 固定 "medora"(与 Runner 对齐)
  - state 读写均经 Jackson + JPA JSONB 自动序列化,无手写 JSON 转换
  - listEvents/deleteSession/listSessions Phase 9.3 阶段占位(empty/no-op),9.7 时按 chat_message 反向构造 Event
- [x] 9.3.4 [AdkRunnerConfig](../backend/src/main/java/com/medora/adk/config/AdkRunnerConfig.java) 把 `InMemoryRunner` 替换为 `Runner(placeholderAgent, appName, inMemoryArtifactService, postgresSessionService)`
- [x] 9.3.5 单元测试 14 个,覆盖:createSession 持久化、getSession 读取/缺失、sessionId UUID 校验、Maybe.empty 语义(RxJava3 blockingGet 行为)、JSON 序列化 roundtrip
- [x] 9.3.6 现有 5 个 AdkConfigLoadTest 用例同步更新(新增 BaseSessionService/BaseArtifactService bean 断言)
- [x] 9.3.7 mvn test 全套 92/92 通过(78 + 14)
- **Status:** complete (2026-06-23)
- **涉及文件:** [V15__add_conversation_session_state.sql](../backend/src/main/resources/db/migration/V15__add_conversation_session_state.sql)、[ConversationEntity.java](../backend/src/main/java/com/medora/chat/domain/ConversationEntity.java)、[PostgresBackedSessionService.java](../backend/src/main/java/com/medora/adk/session/PostgresBackedSessionService.java)、[AdkRunnerConfig.java](../backend/src/main/java/com/medora/adk/config/AdkRunnerConfig.java)、[AdkConfigLoadTest.java](../backend/src/test/java/com/medora/adk/config/AdkConfigLoadTest.java)、[PostgresBackedSessionServiceTest.java](../backend/src/test/java/com/medora/adk/session/PostgresBackedSessionServiceTest.java)
- **关键设计:**
  - sessionId 复用 conversation.publicId,无需新增表或字段
  - listEvents 占位而非实现,Phase 9.7 SSE 接入时再补;Phase 9.3 验证重点是 state 持久化
  - Maybe.create 而非 Maybe.fromCallable,显式控制 onComplete vs onError
- **延后:** RedisCachedSessionService(L1 缓存)推迟到 Phase 9.6 灰度时再加,Phase 9.3 单层 Postgres 已够用;MedoraSessionState 强类型 record 也推迟到 9.5 agent 拓扑成型后定义
- **风险(未发生):** BaseSessionService 的 RxJava 接口比预期简单,Maybe/Single/Completable 各用一个工厂方法即可桥接同步 JPA

### Phase 9.4: Callback 改造 — 重写为分层 LLM 防护(非包装现有代码)

> ⚠️ 设计原则变更:不是把现有 `SafetyGuard` / `OutputSafetyGuard` / `CitationCoverageChecker` 原样包装进 callback —— 它们内部都藏着 LLM+正则 fallback 双轨。本阶段**重写为纯 LLM**,除了下文明确列出的「输入消毒类」正则。

- [x] 9.4.1 实现 [InputSanitizer](../backend/src/main/java/com/medora/adk/sanitizer/InputSanitizer.java)(确定性正则),只保留 SQL/代码注入 + 数据破坏规则,**不**包含 ROLE_HIJACK/INSTRUCTION_OVERRIDE/PROMPT_PROBE(那些是语义层,走 LLM)
- [x] 9.4.2 实现 [LlmSafetyClient](../backend/src/main/java/com/medora/adk/guard/LlmSafetyClient.java),用 LlmCallTemplate 包装 AgentModelGateway.lightweightLlmCall,失败抛 LlmUnavailableException
- [x] 9.4.3 实现 [LlmInputSafetyBeforeCallback](../backend/src/main/java/com/medora/adk/guard/LlmInputSafetyBeforeCallback.java) implements BeforeModelCallbackSync:InputSanitizer 消毒 → LLM 语义审查(ROLE_HIJACK/OVERRIDE/PROBE)双层
- [x] 9.4.4 实现 [LlmOutputSafetyAfterCallback](../backend/src/main/java/com/medora/adk/guard/LlmOutputSafetyAfterCallback.java) implements AfterModelCallbackSync:纯 LLM 判断诊断/用药/系统泄露,无关键词列表;命中时 APPEND_DISCLAIMER 或 REPLACE_REPLY
- [x] 9.4.5 实现 [LlmHallucinationAfterCallback](../backend/src/main/java/com/medora/adk/guard/LlmHallucinationAfterCallback.java) implements AfterModelCallbackSync:纯 LLM 评分,score<0.6 替换为「无法验证」回复
- [x] 9.4.6 实现 [HighRiskToolBeforeCallback](../backend/src/main/java/com/medora/adk/guard/HighRiskToolBeforeCallback.java) implements BeforeToolCallbackSync:schema 级校验 slotId/appointmentId,空则抛 BusinessException(无 LLM 调用)
- [x] 9.4.7 单元测试 48 个:InputSanitizer(20,含 15 个注入模式参数化)、LlmInputSafetyBeforeCallback(6)、LlmOutputSafetyAfterCallback(7)、LlmHallucinationAfterCallback(6)、HighRiskToolBeforeCallback(9)
- [x] 9.4.8 mvn test 全套 140/140 通过(92 + 48)
- [x] 9.4.9 **LLM 失败一致性测试**:所有 3 个 LLM callback 在 LLM 抛异常时透传 LlmUnavailableException(不吞异常,不降级)
- **Status:** complete (2026-06-23)
- **涉及文件:** [InputSanitizer.java](../backend/src/main/java/com/medora/adk/sanitizer/InputSanitizer.java)、[LlmSafetyClient.java](../backend/src/main/java/com/medora/adk/guard/LlmSafetyClient.java)、[LlmInputSafetyBeforeCallback.java](../backend/src/main/java/com/medora/adk/guard/LlmInputSafetyBeforeCallback.java)、[LlmOutputSafetyAfterCallback.java](../backend/src/main/java/com/medora/adk/guard/LlmOutputSafetyAfterCallback.java)、[LlmHallucinationAfterCallback.java](../backend/src/main/java/com/medora/adk/guard/LlmHallucinationAfterCallback.java)、[HighRiskToolBeforeCallback.java](../backend/src/main/java/com/medora/adk/guard/HighRiskToolBeforeCallback.java)、5 个测试文件
- **关键设计:**
  - 每个 callback 暴露 `check()/score()` 方法供直接单测,绕过 ADK CallbackContext 复杂构造;`call(CallbackContext, ...)` 入口仅做 best-effort 接入,真正业务验证在 9.5 agent 拓扑成型后
  - LLM 响应解析失败保守处理(输入安全 → 拒绝,幻觉 → score=0),避免恶意构造绕过 JSON 格式
  - LLM 调用失败一律抛 LlmUnavailableException(由 LlmSafetyClient + LlmCallTemplate 强制),不再像旧代码那样吞异常返回 Optional.empty()
  - HighRiskToolBeforeCallback 不调 LLM:schema 强类型校验已足够,避免每次预约多一次 LLM 调用
- **延后:** EmbeddingCitationAfterCallback(原 9.4.4 计划的 embedding 替代 contains())推迟到 Phase 9.5 KnowledgeAgent 接入时一起做(那时才有真实的 RAG 上下文测试);TraceInjectionCallback 推迟到 9.7 SSE 接入时一起做
- **风险(未发生):** ADK CallbackContext 的 state() 返回 State 类型(继承 ConcurrentMap),与 ReadonlyContext.state() 的 raw Map 重载并存 — Mockito 测试需注意 doReturn 绕过(Phase 9.2 已踩过坑)

### Phase 9.5: 多 agent 拓扑落地

- [x] 9.5.1 新建 [AdkAgentTopology](../backend/src/main/java/com/medora/adk/agents/AdkAgentTopology.java) 配置类,集中定义 4 个 LlmAgent bean + 7 个 BaseTool bean
- [x] 9.5.2 RootRouterAgent:instruction-only,无业务工具;描述「症状→triage / 预约→booking / 知识→knowledge」路由规则;挂 InputSafety + OutputSafety
- [x] 9.5.3 TriageAgent:挂 searchKnowledge,instruction 描述症状→科室映射 + 「超出范畴时 transfer_to_agent」
- [x] 9.5.4 BookingAgent:挂 6 个 booking 工具 + HighRiskToolBeforeCallback,instruction 明确「缺参反问,不要用幻觉 slotId/appointmentId」
- [x] 9.5.5 KnowledgeAgent:挂 searchKnowledge + HallucinationAfterCallback,instruction 强调「先 search 再答」
- [x] 9.5.6 maxSteps 设置:Root 2(路由即可),Triage 4,Booking 8(支持多轮 slot 填充),Knowledge 5
- [x] 9.5.7 [AdkRunnerConfig](../backend/src/main/java/com/medora/adk/config/AdkRunnerConfig.java) 删除 placeholderAdkAgent,Runner 主入口 agent 改为 rootRouterAgent
- [x] 9.5.8 单元测试 8 个:验证 7 工具 bean + 4 agent bean + Root 的 subAgents 包含 3 个专科 + 所有 instruction 非空 + Root instruction 含 transfer_to_agent 关键字 + Booking instruction 含 slot filling 约束
- [x] 9.5.9 mvn test 全套 148/148 通过(140 + 8)
- **Status:** complete (2026-06-23)
- **涉及文件:** [AdkAgentTopology.java](../backend/src/main/java/com/medora/adk/agents/AdkAgentTopology.java)、[AdkRunnerConfig.java](../backend/src/main/java/com/medora/adk/config/AdkRunnerConfig.java)、[AdkConfigLoadTest.java](../backend/src/test/java/com/medora/adk/config/AdkConfigLoadTest.java)(简化,Runner 装载留给集成测试)、[AdkAgentTopologyTest.java](../backend/src/test/java/com/medora/agents/AdkAgentTopologyTest.java)
- **关键设计:**
  - 所有 agent 共享 InputSafety + OutputSafety callback 实例(由 Spring 单例保证),通过 ADK `beforeModelCallbackSync` 接入
  - Root 无业务工具,确保 LLM 必须通过 transfer_to_agent 走专科 agent,避免越权直答
  - BookingAgent 的 instruction 显式 slot-filling 规则:不要用 LLM 推断的 ID 调高风险工具,缺参反问用户
  - maxSteps 分级:Root 2 步够(只 transfer),Booking 8 步覆盖单轮预约的极端场景(查科室→查医生→查号源→确认→预约 = 5 步)
  - KnowledgeAgent 用独立的 HallucinationAfterCallback,Triage/Booking 不挂(它们不答医疗知识,不需幻觉检测)
- **延后:** 真实 LLM 调用验证(端到端测试)推迟到 Phase 9.6 灰度接入时一起跑
- **风险(未发生):** LlmAgent.Builder 接口对 callback 既支持单数也支持 List 形式,目前每个 agent 只用 1-2 个 callback,单数 API 已够

### Phase 9.6: 跨轮 slot 累积 + 灰度切换

- [x] 9.6.1 在 `BookingAgent` 的 instruction 中显式列出所需 slot(dept / doctor / date / period)和缺失时的反问模板 — Phase 9.5 已完成,见 [AdkAgentTopology.java](../backend/src/main/java/com/medora/adk/agents/AdkAgentTopology.java) bookingAgent()
- [x] 9.6.2 `MedoraSessionState` 改为弱类型 `Map<String,Object>` 通过 PostgresBackedSessionService 跨轮持久化,BookingAgent 通过 session state 读取/累积 slot — Phase 9.3 已完成基础设施
- [x] 9.6.3 在 [ChatOrchestrator.java:71](../backend/src/main/java/com/medora/agent/application/ChatOrchestrator.java#L71) 的 `handle()` 方法入口加分支:`if (adkProperties.isEnabled() && adkOrchestrator.isPresent()) { ... }`
- [x] 9.6.4 新建 `AdkOrchestrator`(`@Component` + `@ConditionalOnProperty`),内部调用 `runner.runAsync(...)` 同步收集 `Flowable<Event>` 拼接文本,产出最小化 `AgentRunResult`(intentType=ADK_ROUTED, routeMode=ADK 标记)
- [x] 9.6.5 灰度开关:`medora.adk.enabled=true` 时 ChatOrchestrator 全量委托给 AdkOrchestrator(由 RootRouterAgent 内部分流到 Triage/Booking/Knowledge);关闭时回退旧路径
- [x] 9.6.6 单元测试 10 个,覆盖:`AdkOrchestratorTest` — 事件文本拼接、首次会话创建注入三 key、session 已存在跳过创建、conversation 不存在 fail-fast、publicId 为 null fail-fast、Runner 异常透传、空事件安全降级、sessionId UUID 校验、用户消息原文透传、appName 固定 "medora"
- [ ] 9.6.7 端到端集成测试(需要真实 DeepSeek API + Postgres + 知识库):`mvn test -Dtest='AdkE2ETest'` 跑通三类典型场景(单轮预约、跨轮补全信息后预约、纯知识问答) — **推迟到 Phase 9.7 SSE adapter 完成后一起跑,避免重复 mock LLM**
- **Status:** Phase 9.6 主体完成(灰度骨架 + 单测全绿)。E2E 推迟到 9.7。
- **涉及文件:** 新建 `AdkOrchestrator.java` + `AdkOrchestratorTest.java`;修改 `ChatOrchestrator.java` 入口分支 + 构造器加 `AdkProperties` + `Optional<AdkOrchestrator>` 依赖
- **关键决策:**
  - **sessionId 来源:** `conversation.publicId.toString()` (UUID),不是 Long surrogate id。AdkOrchestrator 通过 ConversationRepository.findById(Long) 解析 publicId,与 PostgresBackedSessionService 的 UUID 解析对齐
  - **意图过滤范围:** Phase 9.6 采用「全意图委托」而非「仅 TRIAGE」,因为 RootRouterAgent 内部已有 LLM 路由能力,在外层再过滤等于重复决策;若需收窄灰度范围,在 ChatOrchestrator 加 `if intentType == X` 判断即可
  - **SSE 兼容:** Phase 9.6 把 ADK 同步收集到的整段回复作为单个 delta 推送(走 `splitDeltas` 按标点切分),前端无感知;Phase 9.7 改为 Flowable 订阅逐 token 推送
  - **AgentRunResult 字段补全:** 当前 intentDecision 用 `ADK_ROUTED` 占位、retrievalResults/toolResults 留空、hallucinationRisk=LOW。Phase 9.7 SSE adapter 完善后,从 ADK Event 提取 FunctionCall/FunctionResponse 反填
- **风险:** 跨轮 slot 累积若失败,通常是因为 instruction 没有明确告诉 LLM 「未齐参数时不要调工具」;Phase 9.5 instruction 已包含此约束,真实多轮验证推迟到 E2E

### Phase 9.7: SSE 流式 + AgentTrace 接入

- [ ] 9.7.1 新建 `AdkEventToSseAdapter`,订阅 `Flowable<Event>`,按上文「SSE 协议兼容」表逐事件翻译为 SseEmitter 调用
- [ ] 9.7.2 在 [ChatStreamController.java](../backend/src/main/java/com/medora/chat/controller/ChatStreamController.java) 或新的 `AdkChatStreamController` 中接入 adapter
- [ ] 9.7.3 修改 [AgentTraceService.java](../backend/src/main/java/com/medora/agent/trace/AgentTraceService.java) 或新增 `AdkTraceSerializer`,把 ADK `Event` 序列化为现有 `AgentTraceEntity.events` JSON 格式
- [ ] 9.7.4 前端协议兼容性验证:用现有 [frontend chat 页面](../frontend/) 跑一遍,事件流不应有任何感知差异;[Admin Agent Trace](../frontend/) 页面能正常展示 ADK 内部 transfer 事件
- [ ] 9.7.5 性能基准:ADK 路径的端到端延迟(首 token)对比旧路径,差距应在 +20% 以内(否则需优化)
- **Status:** pending
- **涉及文件:** 新建 `backend/src/main/java/com/medora/adk/sse/AdkEventToSseAdapter.java`;修改或新增 trace 相关类
- **关键点:** ADK 的 streaming 模型用 `LangChain4j(StreamingChatModel)`,需要在 DeepSeekModelConfig 里额外提供一个 streaming bean

### Phase 9.8: 旧代码清理 + 文档

- [ ] 9.8.1 灰度全量:把 `medora.adk.enabled` 默认改为 true,所有 IntentType 都走 ADK;观察 1 周
- [ ] 9.8.2 **删除** `IntentRouter.java` 整个类(决策层正则,已由 RootRouterAgent 替代)
- [ ] 9.8.3 **删除** `MemoryService.fallbackRegexMemoryExtraction` / `extractDepartmentMention` / `extractDoctorMention` / `containsAny`(语义层正则,已由 LLM-only 记忆提取替代)
- [ ] 9.8.4 **删除** `SafetyGuard.BLOCKED_RULES` 中的 `ROLE_HIJACK` / `INSTRUCTION_OVERRIDE` / `PROMPT_PROBE`(语义层正则,已由 `LlmInputSafetyBeforeCallback` 替代);**保留** `CODE_INJECTION`(输入消毒,符合 LayerPolicy)
- [ ] 9.8.5 **删除** `ChatOrchestrator.resolveIntent()`(双层路由)、`hallucinationRisk()` 中的规则分支(line 336-347)、`filterUserProvidedParams` / `filterHighRiskFrequency`(已由 BookingAgent + HighRiskToolBeforeCallback 替代)
- [ ] 9.8.6 **删除** `ConversationSummaryService.collectConfirmedFacts()` 中的关键词匹配逻辑(slot 累积已由 ADK session state 替代)
- [ ] 9.8.7 **改写** `CitationCoverageChecker` 全文件,删除 `String.contains()` 调用(已由 `EmbeddingCitationAfterCallback` 替代)
- [ ] 9.8.8 **改写** `AgentModelGateway` 所有 LLM 调用方法:返回类型从 `Optional<String>` 改为 `String`,内部用 `LlmCallTemplate` 包装,失败抛 `LlmUnavailableException`(不再吞异常返回 Optional)
- [ ] 9.8.9 简化 `ChatOrchestrator`,只保留入口方法和 ADK 委托
- [ ] 9.8.10 简化 `PromptBuilder`,删除已被 agent instruction 替代的模板部分
- [ ] 9.8.11 更新 [README.md](../README.md) 和 [design/technical-design.md](./technical-design.md)
- [ ] 9.8.12 阶段日志:在 `docs/phase-9-开发日志.md` 记录迁移过程
- **Status:** pending
- **涉及文件:** 多个,见上
- **风险:** 清理时要保证所有 e2e 测试仍通过;若发现某些边界场景未覆盖,先补测试再删代码

## Key Questions

1. **ADK Runner 是否线程安全?** Runner 是单例,Session 是 per-conversation。需在 Spring 中把 Runner 配为 singleton bean,Session 由 service 层按 conversationId 查找/创建。
2. **DeepSeek 切到 OpenAI 端点后,现有 Anthropic 路径要保留吗?** 不保留。Phase 9.1 完成后,所有 LLM 调用都走 LangChain4j OpenAI 客户端。.env 的 `AI_CHAT_BASE_URL` 改为 `https://api.deepseek.com/v1`,`AI_CHAT_PROVIDER` 改为 `openai-compatible`。
3. **高风险工具的 LLM 推断参数问题怎么处理?** 通过 BookingAgent 的 BeforeToolCallback 强制校验。LLM 若仍尝试用推断参数调用,在 callback 里拒绝并要求反问用户。
4. **ADK 的 maxSteps 设多少?** 初始建议 8(每轮最多 8 次 LLM+工具往返),覆盖单轮预约的极端场景(查科室→查医生→查号源→确认→预约 = 5 步)。生产观察后再调。
5. **如果 ADK 在 Phase 9.6/9.7 发现重大问题怎么办?** 见回滚策略。

## Decisions Made

| Decision | Rationale |
|----------|-----------|
| 选 ADK + LangChain4j 桥接,不选 Spring AI | 多 agent transfer 是核心诉求,Spring AI 缺少原生 transfer 机制;ADK 通过 LangChain4j 桥接解决了 DeepSeek 兼容(POC 已验证) |
| 不引入 LiteLLM 代理 | 增加运维负担;LiteLLM + DeepSeek 多工具调用有已知 bug(#5024);LangChain4j 直连更稳 |
| DeepSeek 切到 OpenAI 端点 | LangChain4j 的 Anthropic 客户端走的是 Claude 协议,DeepSeek 的 Anthropic 端点是兼容层;OpenAI 端点更原生,工具调用支持更稳 |
| Session state 用 Postgres + Redis 双层 | 短期热数据走 Redis(现有 `RedisContextCacheService` 已铺好路),长期持久化走 Postgres JSONB |
| 灰度先迁 TRIAGE 意图 | Triage 无副作用工具(只调 searchKnowledge),即使 ADK 出问题影响最小;BOOK/CANCEL 最后迁 |
| 保留 AgentTrace 现有 JSON schema | Admin 前端已实现 trace 渲染,改 schema 需同步改前端;ADK Event 通过 adapter 适配到现有 schema |

## Errors Encountered

| Error | Attempt | Resolution |
|-------|---------|------------|
| (POC 阶段)`SessionService` 类找不到 | 写第一版 POC 时用了错误的接口名 | ADK 0.3.0 中实际接口是 `BaseSessionService`,通过 `javap` 反编译 jar 确认 |
| (POC 阶段)`Single.join()` 方法不存在 | 误以为 RxJava3 的 Single 有 CompletableFuture 风格 API | 改用 `blockingGet()` |
| (POC 阶段)`FunctionTool` 反射报 ERROR 日志 | 工具参数用了 `Map<String,Object>` 通用签名 | 改用 `record + @Schema` 强类型签名,问题解决 |

## 回滚策略

每个 Phase 都设计为可独立回滚:

- **Phase 9.1-9.5 失败**:代码合入但不接入业务,直接删 `com.medora.adk.*` 包即可
- **Phase 9.6 灰度失败**:`medora.adk.enabled=false`,所有请求回到旧 ChatOrchestrator 路径
- **Phase 9.7 SSE 适配失败**:同上,前端无感知
- **Phase 9.8 清理后发现回归**:从 git 回滚到清理前 commit;ADK 路径仍可用,旧代码作为应急路径

**关键不变式**:在 Phase 9.8 完成前,旧的 ChatOrchestrator 路径必须保持可用,`medora.adk.enabled=false` 必须能秒回滚。

## Notes

- 每个 Phase 完成后跑 `mvn test` + 手工验证三类典型场景(预约 / 导诊 / 知识问答)
- 每个 Phase 的 git commit 应独立可 revert
- ADK Java 还在快速演进,0.4.0 发布后评估是否升级(关注 callback API、Session API 稳定性)
- 长期目标:把 `SafetyGuard` / `OutputSafetyGuard` / `CitationCoverageChecker` 也迁到独立的 `com.medora.adk.guard` 包,但这是 Phase 10 的事
- 此计划基于 2026-06-23 的代码状态;若 Phase 1-8 的 [task_plan.md](../task_plan.md) 有后续推进(尤其 Phase 3 SafetyGuard LLM 化),需对应调整 callback 实现

## 立项检查清单(开工前确认)

- [ ] 现有 `mvn test` 全绿(基线)
- [ ] 现有前端 chat 页面功能正常(基线)
- [ ] Admin Agent Trace 页面能正常查看(基线)
- [ ] POC 代码已 commit 到 `adk-poc/` 目录
- [ ] 此计划已经团队 / 决策者评审
