# Phase 7 开发日志

## 阶段目标

- 按实施计划推进 `Phase 7：Memory、摘要与上下文管理`。
- 在 Phase 6 已完成的 Agent 主链路上，补齐 `conversation_summary`、`user_memory_profile`、上下文裁剪与 Prompt 预算控制。
- 尽量不依赖真实 AI API，先把会话连续性和跨会话偏好闭环做成可测试、可落库、可持续扩展的后端能力。

## 主要实现内容

### 1. 会话摘要落库

- 新增 `V11__init_memory_tables.sql`。
- 新增：
  - `ConversationSummaryEntity`
  - `ConversationSummaryRepository`
  - `ConversationSummaryService`
- 对每次成功完成的对话回复，刷新一份结构化摘要，落库内容包括：
  - `topic`
  - `confirmedFacts`
  - `pendingTasks`
  - `toolHighlights`
  - `businessStateJson`
- `businessStateJson` 重点记录：
  - `lastIntent`
  - `routeMode`
  - `lastToolName`
  - `lastToolStatus`
  - `lastSlotId`
  - `lastAppointmentId`
  - `lastScheduleDate`
  - `lastDepartmentName`
  - `lastDoctorName`

### 2. 长期记忆读写

- 新增：
  - `UserMemoryProfileEntity`
  - `UserMemoryProfileRepository`
  - `MemoryService`
- 当前阶段先沉淀高置信长期记忆：
  - `PREFERRED_DEPARTMENT`
  - `PREFERRED_DOCTOR`
  - `PREFERRED_PERIOD`
  - `LAST_SUCCESSFUL_APPOINTMENT`
- 写入策略：
  - `bookAppointment` 成功后，写入科室、医生、时段、最近一次成功预约
  - 用户显式表达“我偏好上午/下午”时，写入时段偏好
- 读取策略：
  - 按意图相关加载
  - 避免把所有历史记忆无差别注入 Prompt

### 3. Prompt 预算与上下文裁剪

- 新增：
  - `TokenBudgetPlanner`
  - `ContextPruner`
- `PromptBuilder` 升级为 `buildPromptPackage(...)`：
  - 注入当前用户消息
  - 注入会话摘要与业务状态
  - 注入相关长期记忆
  - 注入最近消息
  - 注入检索结果与工具结果
- 新增近似 token 预算估算与裁剪结果记录：
  - `estimatedPromptTokensBeforePrune`
  - `estimatedPromptTokensAfterPrune`
  - `includedRecentMessages`
  - `includedMemoryItems`
  - `includedRetrievalItems`
  - 各类上下文是否被裁剪
- `ChatMessageEntity.token_usage_json` 不再固定写死为 `0`，而是保存 Phase 7 的预算估算结果。

### 4. 聊天主链路接入

- `ChatOrchestrator` 现在会：
  - 加载会话摘要上下文
  - 按最终意图加载相关长期记忆
  - 通过 `PromptBuilder` 统一组装模型 Prompt 与本地 fallback 回复
- `AgentModelGateway` 新增“已组装 Prompt 直接调用”的重载方法。
- `AgentModelGateway.systemPrompt` 增加约束：
  - 会话摘要和长期记忆只作辅助上下文
  - 若与当前用户输入、工具结果或知识引用冲突，以当前事实为准
- `ChatRuntimeService` 在 assistant 回复成功落库后，会同步：
  - 刷新 `conversation_summary`
  - 刷新 `user_memory_profile`

## 自测与联调记录

### 1. 后端测试

- 命令：

```bash
mvn -q test
```

- 结果：通过。

### 2. Phase 7 重点测试覆盖

- `PromptBuilderTest`
  - 验证会话摘要和长期记忆被正确注入 Prompt
  - 验证 fallback 回复会利用记忆和摘要提示连续性
- `ConversationSummaryServiceTest`
  - 验证号源查询场景能够生成结构化摘要和业务状态
- `MemoryServiceTest`
  - 验证预约成功后会写入稳定长期记忆
- `ContextPrunerTest`
  - 验证预算不足时优先保留最新消息
- `ChatRuntimeServiceTest`
  - 更新构造依赖后，保留原有 stub 回复能力验证

### 3. 本阶段联调说明

- 本阶段未启动真实 AI API，也未要求用户配合启动后端服务。
- 原因：
  - Phase 7 的核心增量集中在摘要、记忆和 Prompt 上下文管理
  - 这些能力可以通过本地 Prompt 组装与后置落库闭环完成验证
  - 不必依赖真实模型才能判断是否接入成功
- 后续如需端到端联调，建议验证以下场景：
  - 同一会话中“查号源 -> 继续预约”是否能保留业务状态
  - 跨会话中是否会优先提示既有科室/时段偏好
  - 工具结果与长期记忆冲突时是否始终以实时工具结果为准

## 当前结论

- `conversation_summary` 与 `user_memory_profile` 已落库。
- Phase 6 主链路现在具备了：
  - 会话摘要
  - 长期记忆
  - 上下文裁剪
  - Prompt 预算估算
- 这意味着后续即使不马上进入 Phase 8 后台，也已经有可持续扩展的 Phase 7 核心能力基础。

## 后续建议

- 若继续推进联调，建议由你启动服务后，优先跑“跨轮继续预约”和“跨会话偏好提示”两个真实场景。
- 若继续推进开发计划，下一阶段可进入 `Phase 8：后台与可观测性`，优先补：
  - Agent Trace 查询页
  - Conversation Summary 查询页
  - User Memory Profile 查询页
