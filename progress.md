# Progress Log — Medora LLM 智能化升级

## Session: 2026-06-08

### Phase 1: ChatOrchestrator 架构反转 — LLM Planner 优先
- **Status:** complete
- Actions:
  - 重构 `ChatOrchestrator.handle()` 意图路由逻辑：LLM Planner 优先
  - 新增 `resolveIntent()` 方法替代旧的 `shouldPreferLocalIntent()`
  - 增强 `AgentModelGateway.plannerSystemPrompt()`：新增时间解析规则、症状导诊规则、意图识别规则
  - 增强 `plannerUserPrompt()`：传入 `dayOfWeek` 帮助计算"下周X"
- Files modified:
  - `ChatOrchestrator.java` — 核心路由逻辑反转
  - `AgentModelGateway.java` — Planner prompt 大幅增强

### Phase 2: IntentRouter 重构为增强型 fallback
- **Status:** complete
- Actions:
  - 扩展 `extractDepartmentKeyword()`：新增 13 个症状→科室映射（神经内科、消化内科、皮肤科、眼科、骨科、口腔科、心理科、妇科、耳鼻喉、心血管、泌尿）
  - 扩展 `parseDate()`：新增"大后天"、"X天后"、"下周X"、"本周X"、"X月X号/日"支持
  - 扩展 `containsKnowledgeCue()`：新增"费用"、"医保"、"注意"、"准备"等
  - 扩展 `parsePeriod()`：新增"早上"、"中午"、"晚上"、"傍晚"等
  - 扩展 `route()` 入口关键词：新增 30+ 个症状/医疗相关关键词
- Files modified:
  - `IntentRouter.java` — 全面重写

### Phase 3: SafetyGuard + OutputSafetyGuard 双层安全
- **Status:** complete
- Actions:
  - `SafetyGuard` 新增 `fullCheck()` 和 `llmSemanticCheck()` — 正则前置 + LLM 语义审查双层
  - `SafetyGuard` 注入 `AgentModelGateway` 依赖
  - `OutputSafetyGuard` 新增 `fullCheck()` 和 `llmOutputCheck()` — 检测诊断/用药/系统泄露
  - `ChatOrchestrator` 改为调用 `safetyGuard.fullCheck()` 和 `outputSafetyGuard.fullCheck()`
  - `AgentModelGateway` 新增 `lightweightLlmCall()` 通用轻量方法
- Files modified:
  - `SafetyGuard.java` — 全面重写
  - `OutputSafetyGuard.java` — 全面重写
  - `ChatOrchestrator.java` — 调用方式更新
  - `AgentModelGateway.java` — 新增方法
  - `SafetyGuardTest.java` — 适配新构造函数

### Phase 4: MemoryService 智能记忆提取
- **Status:** complete
- Actions:
  - `AgentModelGateway` 新增 `extractMemories()` LLM 记忆提取方法
  - `MemoryService.captureFromUserMessage()` 重构为 LLM 优先 + 正则降级
  - 新增 `tryLlmMemoryExtraction()` 和 `fallbackRegexMemoryExtraction()`
  - 新增 `isValidMemoryType()` 校验
- Files modified:
  - `AgentModelGateway.java` — 新增 `extractMemories()`
  - `MemoryService.java` — 核心方法重构
  - `MemoryServiceTest.java` — 适配新构造函数

### Phase 5: 智能幻觉检测 + 医疗免责
- **Status:** complete
- Actions:
  - `AgentModelGateway` 新增 `assessHallucination()` 和 `checkMedicalAdvice()`
  - `ChatOrchestrator.hallucinationRisk()` 重构为 LLM 评估 + 规则降级
  - `ChatOrchestrator.applyOutputSafety()` 重构医疗免责为 LLM 语义判断 + 关键词降级
- Files modified:
  - `AgentModelGateway.java` — 新增 2 个方法
  - `ChatOrchestrator.java` — 2 个方法重构

### Phase 6: CitationCoverageChecker 语义覆盖检测
- **Status:** complete
- Actions:
  - 全面重写 `CitationCoverageChecker`：新增 Embedding 语义相似度检测
  - 新增 `checkSemanticCoverage()` — 使用 `EmbeddingService` 计算余弦相似度
  - 新增 `cosineSimilarity()` 工具方法
  - 保留 `checkStringCoverage()` 作为降级
  - 扩展 `factualIndicators` 数组：新增 6 个指标词
- Files modified:
  - `CitationCoverageChecker.java` — 全面重写

### Phase 7: ConversationSummaryService 智能摘要
- **Status:** complete
- Actions:
  - `AgentModelGateway` 新增 `generateConversationSummary()` LLM 摘要方法
  - `ConversationSummaryService.resolveTopic()` 重构为 LLM 优先 + 规则降级
  - `ConversationSummaryService.collectConfirmedFacts()` 重构为 LLM 提取 + 关键词降级
- Files modified:
  - `AgentModelGateway.java` — 新增方法
  - `ConversationSummaryService.java` — 2 个方法重构
  - `ConversationSummaryServiceTest.java` — 适配新构造函数

### Phase 8: DocumentChunker 增强
- **Status:** complete
- Actions:
  - `isFaqContent()` 新增 "问题："、"Q."、"q:" 和数字编号格式支持
  - `looksLikeHeading()` 新增 "##"/"###" Markdown 层级 + 13 个新标题关键词
- Files modified:
  - `DocumentChunker.java`

## Test Results

| Test | Tests | Failures | Status |
|------|-------|----------|--------|
| Phase 1 后 | 33 | 0 | PASS |
| Phase 2 后 | 33 | 0 | PASS |
| Phase 3 后 | 33 | 0 | PASS |
| Phase 4 后 | 33 | 0 | PASS |
| Phase 5 后 | 33 | 0 | PASS |
| Phase 6 后 | 33 | 0 | PASS |
| Phase 7 后 | 33 | 0 | PASS |
| Phase 8 后 | 33 | 0 | PASS |

## Error Log

| Timestamp | Error | Attempt | Resolution |
|-----------|-------|---------|------------|
| 2026-06-08 | SafetyGuardTest 构造函数不匹配 | 1 | 添加 AgentModelGateway mock 参数 |
| 2026-06-08 | MemoryServiceTest 构造函数不匹配 | 1 | 添加 AgentModelGateway mock 参数 |
| 2026-06-08 | ConversationSummaryServiceTest 构造函数不匹配 | 1 | 添加 AgentModelGateway mock + when().thenReturn() |

## 5-Question Reboot Check

| Question | Answer |
|----------|--------|
| Where am I? | Phase 1-8 全部完成 |
| Where am I going? | 全部计划已完成 |
| What's the goal? | 将 10 个硬编码正则/关键词决策点改为 LLM 推理驱动 — 已完成 |
| What have I learned? | 每个模块都成功实现了 LLM 优先 + 规则降级模式 |
| What have I done? | 8 个 Phase，修改 10+ 个文件，33 测试全程通过 |
