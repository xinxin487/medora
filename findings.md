# Findings — Medora LLM 智能化升级

## Requirements

- 将 10 个硬编码正则/关键词决策点改为 LLM 推理驱动
- 保留正则作为 fallback，LLM 不可用时降级
- AI 配置已就绪（DeepSeek anthropic-compatible + SiliconFlow embedding）
- 不能跳步，不能只改表面，需要真正调用 LLM 做推理

## Architecture Analysis

### 核心调用链（当前）
```
用户输入 → IntentRouter.containsAny() → SafetyGuard.regex → MemoryService.regex
         → ToolExecutor → PromptBuilder.containsAny → AgentModelGateway.generateReply()
         → OutputSafetyGuard.regex → 输出
```

### 核心调用链（目标）
```
用户输入 → SafetyGuard.regex(快速前置) → SafetyGuard.LLM(语义审查)
         → AgentModelGateway.planIntent(LLM Planner，主力) → [IntentRouter 降级为 fallback]
         → MemoryService.LLM(智能提取) → ToolExecutor
         → AgentModelGateway.generateReply() → OutputSafetyGuard.regex+LLM(双层)
         → CitationCoverageChecker.embedding(语义覆盖) → 输出
```

### AgentModelGateway LLM 调用方法清单（现有 + 需新增）

| 方法 | 用途 | 状态 |
|------|------|------|
| `generateReply()` | 最终回复生成 | 已有 |
| `generateReplyStreaming()` | 流式回复生成 | 已有 |
| `planIntent()` | 意图规划 | 已有（需增强 prompt） |
| `complete()` | 通用 LLM 调用 | 已有（可复用） |
| `semanticSafetyCheck()` | 输入语义安全审查 | **需新增** |
| `semanticOutputCheck()` | 输出语义安全审查 | **需新增** |
| `extractMemories()` | 智能记忆提取 | **需新增** |
| `assessHallucination()` | 幻觉风险评估 | **需新增** |
| `generateSummary()` | 对话摘要生成 | **需新增** |
| `assessCitationCoverage()` | 引用覆盖语义检测 | **需新增** |

### IntentDecision 数据结构

```java
record IntentDecision(
    String intentType,
    RouteMode routeMode,       // CHAT_ONLY | RAG_ONLY | TOOL_ONLY | RAG_AND_TOOL
    boolean needRetrieval,
    List<ToolInvocation> toolInvocations
)

record ToolInvocation(
    String toolName,
    Map<String, Object> arguments,
    ParamSource paramSource    // USER_PROVIDED | MODEL_INFERRED
)
```

### AI 配置参数

```yaml
medora.ai:
  chat-provider: anthropic-compatible
  chat-api-key: sk-d04329aa77324456...
  chat-base-url: https://api.deepseek.com/anthropic
  chat-model: deepseek-v4-flash
  provider: openai-compatible
  api-key: sk-uqbfqfihnnqbstzp...
  base-url: https://api.siliconflow.cn/v1
  embedding-model: Qwen/Qwen3-Embedding-8B
  embedding-dimensions: 1024
```

## Key File Locations

| 文件 | 路径 | 行数 | 改动范围 |
|------|------|------|----------|
| ChatOrchestrator | agent/application/ | 376 | 71-85, 292-355 |
| IntentRouter | agent/application/ | 177 | 全文件 |
| SafetyGuard | agent/application/ | 121 | 全文件 |
| OutputSafetyGuard | agent/application/ | 80 | 全文件 |
| MemoryService | memory/service/ | 410 | 182-271 |
| AgentModelGateway | agent/application/ | 586 | 新增 5 个方法 |
| CitationCoverageChecker | agent/application/ | 106 | 全文件 |
| ConversationSummaryService | memory/service/ | 355 | 168-258 |
| DocumentChunker | knowledge/chunk/ | 118 | 101-117 |
| IntentDecision | agent/application/ | 34 | 不变 |
| AiProperties | ai/config/ | 128 | 不变 |
| EmbeddingService | ai/embedding/ | ~150 | 复用 |

## Technical Decisions

| Decision | Rationale |
|----------|-----------|
| 复用 `AgentModelGateway.complete()` 做所有新增 LLM 调用 | 统一入口，统一超时/重试/日志/降级逻辑 |
| 新增方法放 AgentModelGateway 而非新建类 | 保持架构一致性，避免分散 |
| 每个 LLM 调用都有 Optional 返回 + 正则 fallback | 降级策略，LLM 异常不影响基本可用性 |
| 幻觉检测 LLM 调用与回复生成并行 | 减少延迟 |
| Embedding 相似度用 EmbeddingService 现有方法 | 复用已有基础设施 |

## LLM Prompt 设计要点

### Planner Prompt 增强（Phase 1）
- 需包含时间解析能力：让 LLM 输出 `scheduleDate`（yyyy-MM-dd）
- 需包含症状导诊：让 LLM 输出 `departmentKeyword`
- 需处理歧义：一个消息可能同时需要取消+预约

### Safety Prompt（Phase 3）
- 输入审查：判断是否为角色劫持/指令覆盖/系统探查/代码注入，输出 JSON `{safe: boolean, reason: string}`
- 输出审查：判断是否构成医疗诊断/用药建议，输出 JSON `{hasDiagnostic: boolean, hasMedicationAdvice: boolean}`
- 温度 0，max_tokens 200（轻量快速）

### Memory Extraction Prompt（Phase 4）
- 输入：用户消息
- 输出：`[{memoryType, memoryKey, summary, confidence}]`
- 支持的类型：PREFERRED_DEPARTMENT, PREFERRED_DOCTOR, PREFERRED_PERIOD

### Hallucination Assessment Prompt（Phase 5）
- 输入：回复 + 检索结果 + 工具结果
- 输出：`{risk: "LOW"|"MEDIUM"|"HIGH", reasons: []}`
- 让 LLM 对照检索结果检查事实一致性

### Summary Prompt（Phase 7）
- 输入：用户消息 + 助手回复 + 工具结果
- 输出：`{topic, confirmedFacts, pendingTasks}`
