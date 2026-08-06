# Task Plan: Medora LLM 智能化升级 — 从规则驱动到推理驱动

## Goal

将 Medora 智能助手项目中 10 个依赖硬编码正则/关键词匹配的关键决策点，改造为 LLM 推理驱动，实现真正的智能化。每个改动必须是实质性的代码变更，不是表面包装。

## AI 配置（已就绪）

- Chat Provider: `anthropic-compatible`（DeepSeek via `https://api.deepseek.com/anthropic`）
- Chat Model: `deepseek-v4-flash`
- Embedding Provider: `openai-compatible`（SiliconFlow via `https://api.siliconflow.cn/v1`）
- Embedding Model: `Qwen/Qwen3-Embedding-8B`
- API Keys: 在 `.env` 中已配置

## Current Phase

Phase 1

## Phases

### Phase 1: ChatOrchestrator 架构反转 — LLM Planner 优先（痛点 9，最核心）
- [x] 1.1 修改 `ChatOrchestrator.handle()` 中意图路由逻辑：LLM Planner 优先，IntentRouter 降级为 fallback
- [x] 1.2 重构 `shouldPreferLocalIntent()` → `resolveIntent()`，LLM 优先 + 高风险参数校验
- [x] 1.3 增强 `AgentModelGateway.plannerSystemPrompt()` 的 planner prompt，覆盖时间解析、症状导诊、意图识别规则
- [x] 1.4 验证：33 测试全部通过，编译成功
- **Status:** complete
- **涉及文件:** `ChatOrchestrator.java`, `AgentModelGateway.java`
- **关键改动位置:** `ChatOrchestrator.java:82-84`, `AgentModelGateway.java:325-400`

### Phase 2: IntentRouter 重构 — 保留为 LLM 不可用时的降级路由器（痛点 1, 2）
- [ ] 2.1 重写 `IntentRouter.route()` 方法签名，添加 @Nullable LLM fallback 标记
- [ ] 2.2 扩展 `extractDepartmentKeyword()`：增加更多症状→科室映射（皮肤→皮肤科、眼→眼科、骨→骨科、牙→口腔科、心理→心理科、妇→妇科、耳→耳鼻喉科）
- [ ] 2.3 扩展 `parseDate()`：支持"下周X"、"本周X"、"X天后"、"X号"、"MM月DD日"、"大后天"等自然语言时间
- [ ] 2.4 扩展 `containsKnowledgeCue()`：增加更多知识问答触发词
- [ ] 2.5 改进 `parsePeriod()`：支持"早上"、"中午"、"晚上"、"傍晚"等
- [ ] 2.6 验证：在 LLM 不可用时，本地路由仍然能工作
- **Status:** complete
- **涉及文件:** `IntentRouter.java`

### Phase 3: SafetyGuard + OutputSafetyGuard 双层安全（痛点 3）
- [ ] 3.1 在 `SafetyGuard` 中添加 LLM 语义安全审查方法 `llmSemanticCheck()`
- [ ] 3.2 保留正则作为快速前置过滤（性能考虑），正则通过后再调 LLM
- [ ] 3.3 新增 `SafetyGuard.llmSafetyPrompt`：让 LLM 判断输入是否包含注入/劫持/探查意图
- [ ] 3.4 在 `AgentModelGateway` 中添加专用的 safety check 调用方法（轻量，温度 0）
- [ ] 3.5 重构 `OutputSafetyGuard`：正则检测 + LLM 语义检测双层，LLM 判断回复是否包含诊断/用药建议
- [ ] 3.6 新增 `OutputSafetyGuard` 的 LLM prompt：让 LLM 判断回复是否构成医疗诊断
- [ ] 3.7 验证：正则绕过场景（插入空格、英文变体）能被 LLM 层拦截
- **Status:** pending
- **涉及文件:** `SafetyGuard.java`, `OutputSafetyGuard.java`, `AgentModelGateway.java`
- **关键改动位置:** `SafetyGuard.java:76-103`, `OutputSafetyGuard.java:35-61`

### Phase 4: MemoryService 智能记忆提取（痛点 4）
- [ ] 4.1 在 `AgentModelGateway` 中新增 `extractMemories()` 方法：让 LLM 从用户消息中提取结构化记忆
- [ ] 4.2 新增 memory extraction prompt：让 LLM 输出 `{memoryType, memoryKey, summary, confidence}` JSON 数组
- [ ] 4.3 重构 `MemoryService.captureFromUserMessage()`：先尝试 LLM 提取，LLM 不可用时回退到正则
- [ ] 4.4 移除硬编码的 `containsAny(userMessage, "我习惯上午", ...)` 枚举式检测
- [ ] 4.5 验证："我一般上午去"、"周末方便"、"帮我挂心内科"等能被正确提取
- **Status:** pending
- **涉及文件:** `MemoryService.java`, `AgentModelGateway.java`
- **关键改动位置:** `MemoryService.java:182-271`

### Phase 5: ChatOrchestrator 智能幻觉检测 + 医疗免责（痛点 6, 10）
- [ ] 5.1 重构 `hallucinationRisk()`：从硬编码规则改为 LLM 自评 + 规则综合评分
- [ ] 5.2 新增 hallucination check prompt：让 LLM 对照检索结果评估回复的事实一致性
- [ ] 5.3 重构 `applyOutputSafety()` 中的医疗免责逻辑：让 LLM 判断回复是否涉及医疗建议
- [ ] 5.4 移除硬编码的 `reply.contains("症状") || reply.contains("咳嗽")` 关键词检测
- [ ] 5.5 验证："头晕"、"恶心"等未在关键词中的医学术语也能触发免责
- **Status:** pending
- **涉及文件:** `ChatOrchestrator.java`, `AgentModelGateway.java`
- **关键改动位置:** `ChatOrchestrator.java:292-355`

### Phase 6: CitationCoverageChecker 语义覆盖检测（痛点 7）
- [ ] 6.1 重构 `CitationCoverageChecker.check()`：用 Embedding 余弦相似度替代字符串 contains
- [ ] 6.2 将回复和检索片段分别 embedding，计算语义相似度
- [ ] 6.3 替换硬编码的 `factualIndicators` 数组：让 LLM 判断回复是否包含知识库未覆盖的事实声明
- [ ] 6.4 验证：改写后的引用（语义相同但用词不同）不再被判为低覆盖
- **Status:** pending
- **涉及文件:** `CitationCoverageChecker.java`, `EmbeddingService.java`
- **关键改动位置:** `CitationCoverageChecker.java` 全文件

### Phase 7: ConversationSummaryService 智能摘要（痛点 5）
- [ ] 7.1 重构 `resolveTopic()`：让 LLM 判断对话主题，替代关键词匹配
- [ ] 7.2 重构 `collectConfirmedFacts()`：让 LLM 从对话中提取确认的事实，替代关键词 contains
- [ ] 7.3 新增 summary prompt：让 LLM 生成简洁的对话摘要和主题标签
- [ ] 7.4 验证："上午去了但没号了，下午还能约吗"只提取"用户关注下午号源"
- **Status:** pending
- **涉及文件:** `ConversationSummaryService.java`, `AgentModelGateway.java`
- **关键改动位置:** `ConversationSummaryService.java:168-258`

### Phase 8: DocumentChunker 增强（痛点 8）
- [ ] 8.1 扩展 `isFaqContent()`：增加 "问题："、"Q."、数字编号 "1. "、"1)" 等格式
- [ ] 8.2 扩展 `looksLikeHeading()`：支持 Markdown 标题层级 `##`、`###`，增加更多标题关键词
- [ ] 8.3 验证：多种 FAQ 格式和标题格式都能正确识别
- **Status:** pending
- **涉及文件:** `DocumentChunker.java`
- **关键改动位置:** `DocumentChunker.java:101-117`

## Key Questions

1. LLM 调用延迟是否可接受？→ planner + safety 每次对话额外增加 ~500ms-1s，可通过异步并行缓解
2. LLM 降级策略是否合理？→ 正则作为 fallback，LLM 异常时自动降级，不影响基本可用性
3. Embedding 相似度阈值如何设定？→ Phase 6 需要实验确定最佳阈值，初始建议 0.75
4. 记忆提取 LLM 调用是否需要异步？→ 是，避免阻塞对话主流程

## Decisions Made

| Decision | Rationale |
|----------|-----------|
| LLM Planner 优先，IntentRouter 降级为 fallback | 痛点 9 是架构级问题，先解决它才能让后续改动有意义 |
| 安全防护保留正则做快速前置 + LLM 语义审查 | 性能考虑：正则毫秒级过滤明显攻击，LLM 处理语义级绕过 |
| Memory 提取先 LLM 后正则 | 记忆提取不阻塞对话流，可异步执行，LLM 不可用时回退正则 |
| 幻觉检测用 LLM 自评 | deepseek-v4-flash 支持推理，成本可控 |
| Embedding 相似度替代字符串匹配 | 语义级覆盖检测，解决改写变体问题 |
| DocumentChunker 只扩展正则不引入 LLM | 文档分块是离线批处理，优先级最低，扩展正则即可 |

## Errors Encountered

| Error | Attempt | Resolution |
|-------|---------|------------|
| (空) | | |

## Notes

- 每个阶段完成后运行 `mvn test` 确保编译通过
- Phase 1 是最核心的架构改动，后续所有 Phase 都依赖它
- AgentModelGateway 是所有 LLM 调用的统一入口，新增方法都在这里
- 保持向后兼容：所有 LLM 功能都有正则 fallback
