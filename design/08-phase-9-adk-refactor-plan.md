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

Phase 9.1(ADK 依赖接入)— 待启动

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

- [ ] 9.1.1 在 [backend/pom.xml](../backend/pom.xml) 添加三个依赖:`google-adk:0.3.0`、`google-adk-langchain4j:0.3.0`、`langchain4j-open-ai:1.0.1`
- [ ] 9.1.2 在 `<maven-compiler-plugin>` 配置中加 `<parameters>true</parameters>`(消掉 POC 中的 `@Schema` 警告)
- [ ] 9.1.3 新建 `com.medora.adk.config.DeepSeekModelConfig`,提供 `OpenAiChatModel` Bean(从 `.env` 的 `AI_CHAT_*` 读取,baseUrl 切换到 `/v1` 而非 `/anthropic`)
- [ ] 9.1.4 新建 `com.medora.adk.config.AdkRunnerConfig`,提供 `InMemoryRunner` Bean(临时用内存版,9.3 替换为持久化版)
- [ ] 9.1.5 配置项 `medora.adk.enabled=false`,默认关闭,所有 9.x 阶段都不影响生产路径
- [ ] 9.1.6 验证:`mvn test` 通过;后端正常启动;ADK Bean 加载但不接入业务
- **Status:** pending
- **涉及文件:** `backend/pom.xml`,新建 `backend/src/main/java/com/medora/adk/config/DeepSeekModelConfig.java`、`AdkRunnerConfig.java`
- **风险:** LangChain4j 1.0.1 与 Spring Boot 3.x 自动配置可能冲突;若冲突,在主启动类排除 LangChain4j 自动配置

### Phase 9.2: 工具桥接 — 7 个 ToolDefinition 包装为 ADK FunctionTool

- [ ] 9.2.1 新建 `com.medora.adk.tool.ToolDefinitionAsFunctionTool`,实现 `FunctionTool` 接口,把现有 `ToolDefinition.execute(args, ctx)` 适配为 ADK 期待的 `Map<String,Object> → Map<String,Object>` 签名
- [ ] 9.2.2 为高风险工具(`bookAppointment`、`cancelAppointment`)定义强类型 `record` 入参,字段标 `@Schema`,替代现有 `Map<String,Object>` 通用入参
- [ ] 9.2.3 为查询类工具(`queryDepartments`、`queryDoctors`、`queryScheduleSlots`、`getUserAppointments`、`searchKnowledge`)同样定义强类型入参
- [ ] 9.2.4 单元测试:每个工具能被 ADK `FunctionTool` 框架正确反射出 schema,空参 / 全参 / 缺参三种 case 都能正确处理
- [ ] 9.2.5 验证:`mvn test -Dtest='*FunctionTool*Test'` 全绿
- **Status:** pending
- **涉及文件:** 新建 `backend/src/main/java/com/medora/adk/tool/ToolDefinitionAsFunctionTool.java`,新建 `backend/src/main/java/com/medora/adk/tool/dto/*.java`(各工具的 record 入参);测试在 `backend/src/test/java/com/medora/adk/tool/`
- **风险:** ADK 的 `FunctionTool.create` 默认走反射,自定义适配器更可控但代码量大;先尝试反射方案,失败再走自定义

### Phase 9.3: Session/State 接入现有持久化层

- [ ] 9.3.1 数据库迁移:在 `conversations` 表加 `session_state JSONB` 字段(向后兼容,允许 NULL);`schema.sql` + Flyway 迁移脚本
- [ ] 9.3.2 实现 `PostgresBackedSessionService implements BaseSessionService`,内部用 Jackson 序列化 `MedoraSessionState` 到新字段
- [ ] 9.3.3 实现 `RedisCachedSessionService` 包装层(L1 缓存),热会话状态走 Redis,落库走 Postgres
- [ ] 9.3.4 在 `AdkRunnerConfig` 中把 `InMemoryRunner` 替换为 `Runner` + `PostgresBackedSessionService`
- [ ] 9.3.5 集成测试:连续两轮对话,state 字段正确写入、读取;跨会话不串
- **Status:** pending
- **涉及文件:** 新建 `backend/src/main/java/com/medora/adk/session/PostgresBackedSessionService.java`、`RedisCachedSessionService.java`、`MedoraSessionState.java`;修改 [ConversationEntity.java](../backend/src/main/java/com/medora/chat/domain/ConversationEntity.java) 加 `sessionState` 字段;新增 Flyway 迁移
- **风险:** BaseSessionService 接口要求返回 RxJava `Single/Maybe`,需要小心 reactive 风格;建议用 `CompletableFuture` 转 RxJava 适配

### Phase 9.4: Callback 改造 — 重写为分层 LLM 防护(非包装现有代码)

> ⚠️ 设计原则变更:不是把现有 `SafetyGuard` / `OutputSafetyGuard` / `CitationCoverageChecker` 原样包装进 callback —— 它们内部都藏着 LLM+正则 fallback 双轨。本阶段**重写为纯 LLM**,除了下文明确列出的「输入消毒类」正则。

- [ ] 9.4.1 实现 `LlmInputSafetyBeforeCallback implements BeforeModelCallbackSync`,**只调 LLM 做语义安全审查**(角色劫持 / 指令覆盖 / 提示探查)。现有 `SafetyGuard.BLOCKED_RULES` 中的 `CODE_INJECTION`(drop table / truncate / system() 等)抽出到独立 `InputSanitizer` 确定性消毒器,在 callback 之前执行 —— 这是输入消毒,不是决策,保留正则符合 LayerPolicy
- [ ] 9.4.2 实现 `LlmOutputSafetyAfterCallback implements AfterModelCallbackSync`,**只调 LLM** 判断输出是否构成医疗诊断 / 用药建议。原 `OutputSafetyGuard` 中的关键词列表全部删除
- [ ] 9.4.3 实现 `LlmHallucinationAfterCallback implements AfterModelCallbackSync`(仅挂 KnowledgeAgent),**纯 LLM** 评分,删除原 `hallucinationRisk()` 的所有规则分支(line 336-347)。LLM 不可用 → 抛 `LlmUnavailableException`
- [ ] 9.4.4 实现 `EmbeddingCitationAfterCallback implements AfterModelCallbackSync`(仅挂 KnowledgeAgent),用 embedding 余弦相似度替代 `CitationCoverageChecker.check()` 中的 `String.contains()`。阈值通过测试确定(初始 0.75)
- [ ] 9.4.5 实现 `HighRiskToolBeforeCallback implements BeforeToolCallbackSync`(仅挂 BookingAgent),分两层:**schema 层** 确定性校验 `userConfirmed==true` 字段(工具签名强约束)→ **若 false 直接拒绝**;不再做 LLM 二次核验(避免每次预约多一次 LLM 调用)
- [ ] 9.4.6 实现 `TraceInjectionCallback`,在每个 callback 入口 / 出口调用 `AgentTraceService.record(...)`
- [ ] 9.4.7 单元测试:每个 callback 在 mock LLM 输入下行为正确;**额外测试**:LLM 抛异常时,callback 透传 `LlmUnavailableException`(不吞异常)
- **Status:** pending
- **涉及文件:** 新建 `backend/src/main/java/com/medora/adk/callback/*.java`(6 个 callback 类)、`backend/src/main/java/com/medora/adk/sanitizer/InputSanitizer.java`
- **关键改动参考:** `SafetyGuard.java:26-60`(拆分消毒 vs 语义)、`ChatOrchestrator.java:311-355`(hallucinationRisk 全删)、`OutputSafetyGuard.java:35-61`、`CitationCoverageChecker.java`(全文件改写)

### Phase 9.5: 多 agent 拓扑落地

- [ ] 9.5.1 新建 `com.medora.adk.agents.RootRouterAgent`,定义为 `LlmAgent`,只描述「什么样的请求归哪个 agent」的 instruction,挂载 SafetyGuard callbacks,subAgents 列出三个专科 agent
- [ ] 9.5.2 新建 `TriageAgent`,挂载 searchKnowledge 工具,instruction 描述症状→科室映射的引导逻辑
- [ ] 9.5.3 新建 `BookingAgent`,挂载 6 个 booking 相关工具 + HighRiskToolBeforeCallback,instruction 明确「缺参时反问用户,不要硬调 bookAppointment」
- [ ] 9.5.4 新建 `KnowledgeAgent`,挂载 searchKnowledge 工具 + HallucinationAfterModelCallback
- [ ] 9.5.5 在 `AdkRunnerConfig` 中用 RootRouterAgent 替换临时单 agent
- [ ] 9.5.6 集成测试:不同类型的 query(导诊/预约/知识)被正确路由到对应 sub-agent;transfer 链路在多轮间保持
- **Status:** pending
- **涉及文件:** 新建 `backend/src/main/java/com/medora/adk/agents/{RootRouterAgent,TriageAgent,BookingAgent,KnowledgeAgent}.java`
- **关键点:** transfer 机制由 ADK 自动处理,RootAgent 的 instruction 里描述「何时 transfer 给谁」即可

### Phase 9.6: 跨轮 slot 累积 + 灰度切换

- [ ] 9.6.1 在 `BookingAgent` 的 instruction 中显式列出所需 slot(dept / doctor / date / period)和缺失时的反问模板
- [ ] 9.6.2 在 `MedoraSessionState` 中确认 `pendingBooking` 字段的读写路径,保证跨轮不丢
- [ ] 9.6.3 在 [ChatOrchestrator.java:71](../backend/src/main/java/com/medora/agent/application/ChatOrchestrator.java#L71) 的 `handle()` 方法入口加分支:`if (adkEnabled && routeMode == ADK) { return adkOrchestrator.handle(...); }`
- [ ] 9.6.4 新建 `AdkOrchestrator implements 当前 ChatOrchestrator 同接口`,内部调用 `runner.runAsync(...)` 收集结果,产出 `AgentRunResult`
- [ ] 9.6.5 灰度开关:`medora.adk.enabled=true` 仅对 `IntentType.TRIAGE` 启用,其他意图仍走旧路径
- [ ] 9.6.6 端到端测试:`mvn test -Dtest='AdkE2ETest'` 跑通三类典型场景(单轮预约、跨轮补全信息后预约、纯知识问答)
- **Status:** pending
- **涉及文件:** 修改 `ChatOrchestrator.java`;新建 `AdkOrchestrator.java`;修改配置类加灰度开关
- **风险:** 跨轮 slot 累积若失败,通常是因为 instruction 没有明确告诉 LLM 「未齐参数时不要调工具」;可通过单测 mock 多轮对话验证

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
