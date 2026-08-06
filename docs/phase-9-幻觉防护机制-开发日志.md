# Phase 9 幻觉防护机制开发日志

## 1. 本轮目标

按照 Phase 9 差距分析文档，基于 RAG 证据约束、Prompt 安全边界、工具白名单与 AgentTrace 审计四大支柱构建幻觉防护机制。分三个子阶段实施：9.1 安全基础加固、9.2 可追溯性增强、9.3 工程优化与审计增强。

## 2. 设计决策

| 决策 | 选项 | 结论 |
|------|------|------|
| SafetyGuard 返回类型 | CheckResult / 抛异常 | 返回 `CheckResult` record，保留 `validate()` 抛异常方法，兼顾命令式与声明式调用 |
| 输出校验策略 | 阻塞等待 / 流式后验 | 流式输出后校验，校验不通过时追加免责声明而非替换已发送内容 |
| 工具风险等级 | 注解 / 枚举方法 | `ToolDefinition` 接口增加 `default riskLevel()` 方法，各工具重写返回枚举 |
| 引用回溯 | Trace 完整快照 / 标记法 | 在 `retrieval_json` 中对每个片段增加 `referenced` 布尔标记 |
| 幻影风险计算 | 独立 Evaluator / 内联 | 内联到 `ChatOrchestrator.hallucinationRisk()` 方法，避免过度抽象 |

## 3. 实施内容

### 3.1 Phase 9.1 — 安全基础加固

共 7 项任务，覆盖 P0 + 核心 P1 差距：

| # | 任务 | 涉及文件 | 状态 |
|---|------|----------|------|
| 1 | 扩展 SafetyGuard 规则引擎 | `SafetyGuard.java` | ✅ |
| 2 | 系统提示增加格式约束和引用要求 | `AgentModelGateway.systemPrompt()` | ✅ |
| 3 | 检索为空降级 contextNote | `PromptBuilder.java` | ✅ |
| 4 | OutputSafetyGuard 输出校验组件 | 新建 `OutputSafetyGuard.java` | ✅ |
| 5 | CitationCoverageChecker 引用覆盖校验 | 新建 `CitationCoverageChecker.java` | ✅ |
| 6 | ChatOrchestrator 集成输出安全校验 | `ChatOrchestrator.java` | ✅ |
| 7 | 工具风险分级 | `ToolDefinition.java`, BookAppointment/CancelAppointment | ✅ |

**SafetyGuard 重构要点**：
- 从 6 条硬编码模式升级为 5 类正则规则引擎：ROLE_HIJACK、INSTRUCTION_OVERRIDE、PROMPT_PROBE、CODE_INJECTION、DATA_DESTRUCTION
- 支持 10+ 中英文注入变体匹配
- 保留 `preCheck()` 返回 CheckResult，新增 `validate()` 抛异常方法

**OutputSafetyGuard**：
- 诊断性语言检测：你患有/确诊为/病情是
- 系统信息泄露检测：traceId、内部工具名
- 三级动作：NONE / APPEND_DISCLAIMER / REPLACE_REPLY

**CitationCoverageChecker**：
- 检查回复中的 `[1][2]` 编号引用标记
- 检查关键实体（科室名、医生名）是否覆盖
- 返回未覆盖声明的发现列表

### 3.2 Phase 9.2 — 可追溯性增强

共 6 项任务，覆盖 P1 trace + P2 prompt 差距：

| # | 任务 | 涉及文件 | 状态 |
|---|------|----------|------|
| 8 | agent_trace 增加安全字段 | V13 migration, `AgentTraceEntity.java` | ✅ |
| 9 | SafetyGuard 拦截写入 trace | `ChatOrchestrator.java`, `AgentTraceService.java`, `ChatRuntimeService.java` | ✅ |
| 10 | 回复后幻觉风险标记 | `ChatOrchestrator.java` | ✅ |
| 11 | PromptBuilder 动态安全策略注入 | `PromptBuilder.java` | ✅ |
| 12 | 医疗免责声明强制追加 | `ChatOrchestrator.java` | ✅ |
| 13 | 工具调用频率限制 | `ChatOrchestrator.java` | ✅ |

**agent_trace 新增字段**：
- `safety_events_json JSONB` — 安全拦截事件列表
- `hallucination_risk VARCHAR(16)` — LOW / MEDIUM / HIGH
- `risk_factors_json JSONB` — 风险因子列表

**幻觉风险计算逻辑**：
- 长回复 + 无检索 + 无工具结果 → HIGH
- 有工具结果 + 无检索 + 长回复 → MEDIUM
- 引用覆盖校验不通过 → MEDIUM
- 其余 → LOW

**频率限制**：单轮对话中 `BOOK_APPOINTMENT` 和 `CANCEL_APPOINTMENT` 最多各执行 1 次。

### 3.3 Phase 9.3 — 工程优化与审计增强

共 6 项任务，覆盖 P2 工具 + P3 trace 差距：

| # | 任务 | 涉及文件 | 状态 |
|---|------|----------|------|
| 14 | ToolRegistry 白名单可配置化 | `ToolRegistry.java`, `AgentModelGateway.isAllowedTool()` | ✅ |
| 15 | 工具参数来源追溯 | `IntentDecision.java`, `ChatOrchestrator.java` | ✅ |
| 16 | 引用锚定 [1][2] 标注 | `AgentModelGateway.systemPrompt()` | ✅ |
| 17 | 检索分数阈值过滤 | `HybridRetriever.java` | ✅ |
| 18 | Admin 安全统计 API + 安全概览页 | `AdminSafetyController.java`, `AdminSafetyQueryService.java`, `frontend/app/admin/safety/page.tsx` | ✅ |
| 19 | Agent Trace 引用回溯标记 | `ChatOrchestrator.java` | ✅ |

**参数来源追溯**：
- `IntentDecision.ParamSource` 枚举：USER_PROVIDED / MODEL_INFERRED
- 高风险工具拒绝 MODEL_INFERRED 的参数
- `ToolInvocation.userProvided()` 工厂方法标记参数来源

**检索分数阈值**：`HybridRetriever` 增加 `MIN_SCORE_THRESHOLD = 0.15` 过滤器。

**安全概览页**：三指标卡片（Trace 总量、工具调用成功率、安全拦截次数）+ 幻觉风险分布图 + 高风险 Trace 列表 + 按日拦截统计。

## 4. 文件变更清单

### 4.1 后端新增文件 (8)

| 文件 | 职责 |
|------|------|
| `agent/application/OutputSafetyGuard.java` | 输出层安全校验 |
| `agent/application/CitationCoverageChecker.java` | 引用覆盖校验 |
| `agent/tool/ToolRiskLevel.java` | 工具风险等级枚举 |
| `admin/controller/AdminSafetyController.java` | 安全统计 API |
| `admin/service/AdminSafetyQueryService.java` | 安全统计查询服务 |
| `db/migration/V13__agent_trace_safety_fields.sql` | agent_trace 新增安全字段 |

### 4.2 后端修改文件 (15)

| 文件 | 修改内容 |
|------|----------|
| `SafetyGuard.java` | 规则引擎重构，正则分类匹配 |
| `AgentModelGateway.java` | 增强系统提示 + ToolRegistry 依赖 |
| `ChatOrchestrator.java` | 集成输出校验、引用回溯、幻觉风险、免责声明、频率限制 |
| `PromptBuilder.java` | 检索为空降级 + 动态安全策略注入 |
| `ToolDefinition.java` | 增加 riskLevel 默认方法 |
| `ToolRegistry.java` | 增加 isRegistered / allToolNames |
| `IntentDecision.java` | 增加 ParamSource 枚举 |
| `AgentTraceEntity.java` | 新增 3 个安全字段 |
| `AgentTraceService.java` | 增加安全事件记录 |
| `AgentRunResult.java` | 增加安全字段 |
| `ChatRuntimeService.java` | 安全拦截 trace 写入 |
| `HybridRetriever.java` | 分数阈值过滤 |
| `IntentRouter.java` | 使用 userProvided 工厂方法 |
| `BookAppointmentTool.java` | 标记为 HIGH 风险 |
| `CancelAppointmentTool.java` | 标记为 HIGH 风险 |

### 4.3 前端新增/修改

| 文件 | 内容 |
|------|------|
| `frontend/app/admin/safety/page.tsx` | 安全概览页面 |

## 5. 验收结果

### 5.1 后端编译
- `mvn compile`: ✅ 通过

### 5.2 后端测试
- `mvn test`: ✅ 31 测试全部通过，0 失败

### 5.3 前端 TypeScript 检查
- `tsc --noEmit`: ✅ 通过

## 6. 差距覆盖矩阵

| 差距编号 | 描述 | 状态 | 验证方式 |
|----------|------|------|----------|
| G-RAG-1 | 引用覆盖校验 | ✅ | CitationCoverageChecker |
| G-RAG-2 | 无检索降级策略 | ✅ | PromptBuilder contextNote |
| G-RAG-3 | 引用锚定 | ✅ | 系统提示要求 [1][2] 标注 |
| G-RAG-4 | 检索分数阈值 | ✅ | HybridRetriever MIN_SCORE_THRESHOLD |
| G-PROMPT-1 | Prompt注入防护增强 | ✅ | SafetyGuard 5 类正则规则引擎 |
| G-PROMPT-2 | 输出层安全校验 | ✅ | OutputSafetyGuard |
| G-PROMPT-3 | 系统提示格式约束 | ✅ | AgentModelGateway systemPrompt |
| G-PROMPT-4 | 动态安全策略注入 | ✅ | PromptBuilder |
| G-PROMPT-5 | 医疗免责声明强制追加 | ✅ | ChatOrchestrator.applyOutputSafety |
| G-TOOL-1 | 白名单可配置化 | ✅ | ToolRegistry |
| G-TOOL-2 | 工具风险分级 | ✅ | ToolRiskLevel enum |
| G-TOOL-3 | 参数来源追溯 | ✅ | ParamSource USER_PROVIDED/MODEL_INFERRED |
| G-TOOL-4 | 工具调用频率限制 | ✅ | ChatOrchestrator.filterHighRiskFrequency |
| G-TRACE-1 | 安全事件记录 | ✅ | agent_trace.safety_events_json |
| G-TRACE-2 | 幻觉检测标记 | ✅ | agent_trace.hallucination_risk |
| G-TRACE-3 | 引用回溯 | ✅ | retrieval_json referenced 标记 |
| G-TRACE-4 | 审计聚合统计 | ✅ | AdminSafetyController + 前端安全概览页 |
| G-TRACE-5 | 完整对话回放 | ⏳ | 列入 Phase 10 |

## 7. 已知问题

- **流式输出校验延迟**：输出校验在完整文本生成后进行，不阻断流式 SSE 推送。免责声明以追加方式附加，不替换已发送内容（当前可接受，后续可优化为预校验 + 后校验双阶段）
- **幻影风险规则评估**：当前基于启发式规则（回复长度、检索/工具结果、引用覆盖），假阳性不可避免，后续可引入轻量模型评估
- **SafetyGuardTest 适配**：`preCheck()` 返回值从 `void` 改为 `CheckResult` record，测试改用 `validate()` 抛异常方法验证拦截逻辑
