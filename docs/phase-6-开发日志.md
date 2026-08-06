# Phase 6 开发日志

## 阶段目标

- 按设计文档推进单 Agent + Tool Calling 主链路。
- 将聊天运行时、RAG 检索、预约业务工具、Agent Trace 与 SSE 流式输出串联起来。
- 引入真实大模型联调，避免仅靠本地 stub 或规则模板验证。

## 主要实现内容

### 1. Agent 编排主链路

- 新增 `agent/application` 编排层：
  - `ChatOrchestrator`
  - `IntentRouter`
  - `AgentModelGateway`
  - `PromptBuilder`
  - `SafetyGuard`
- 新增 `agent/tool` 工具体系：
  - 科室查询：`queryDepartments`
  - 医生查询：`queryDoctors`
  - 号源查询：`queryScheduleSlots`
  - 我的预约：`getUserAppointments`
  - 预约挂号：`bookAppointment`
  - 取消挂号：`cancelAppointment`
  - 知识检索：`searchKnowledge`
- 工具调用不直接写库，统一复用现有 Service，保持业务规则、权限和事务边界一致。

### 2. Agent Trace 落库

- 新增 `V10__init_agent_trace.sql`。
- 新增 `AgentTraceEntity`、`AgentTraceRepository`、`AgentTraceService`。
- 对每次对话记录：
  - `traceId`
  - `intentType`
  - `routeMode`
  - `retrieval_json`
  - `tool_events_json`
  - `latency_ms`
  - `status`
- 后续补充记录 `plannedTools`，用于排查模型规划出的原始工具参数。

### 3. 对话与 SSE

- `ChatRuntimeService` 接入 `ChatOrchestrator`。
- `StreamEventPublisher` 增加 `tool_status` 事件。
- 前端聊天页支持展示 Phase 6 Agent 状态与工具调用状态。
- 修复“假流式”问题：
  - 原问题：后端先等模型完整响应，再拆分成 delta 发送，前端表现为等待很久后一次性展示。
  - 解决：`AgentModelGateway` 支持上游模型 streaming，`ChatOrchestrator` 边读模型 token 边发布 `message_delta`。

### 4. 真实模型接入

- 真实模型配置：
  - `ANTHROPIC_BASE_URL=https://182.139.39.10:40009`
  - `ANTHROPIC_MODEL=glm-5.1`
- `AgentModelGateway` 支持：
  - `anthropic-compatible`
  - `openai-compatible`
  - 本地兜底
- 修复模型代理 TLS 问题：
  - 原问题：Java TLS 校验失败，日志为 `No subject alternative names matching IP address 182.139.39.10 found`。
  - 解决：增加开发环境私有模型代理 TLS 放行逻辑，并提供 `AI_INSECURE_TLS` 配置。
- 验证结果：
  - 后端日志出现 `Anthropic-compatible chat call succeeded. model=glm-5.1`。
  - 后端日志出现 `Anthropic-compatible streaming chat call succeeded. model=glm-5.1`。

## 联调问题与修复记录

### 1. 管理员知识库检索报错

- 现象：管理员知识库检索“取消挂号”时报 `COMMON_INTERNAL_ERROR`。
- 原因：Spring Data projection 将 `Instant` 投影为 `OffsetDateTime` 失败。
- 修复：调整 projection/DTO 时间类型映射，并在全局异常处理器记录真实异常日志。

### 2. 无关问题强行召回

- 现象：用户说“我最近咳嗽”时召回“取消挂号”知识片段。
- 修复：
  - 后台检索与聊天检索增加“无关问题不强行召回”规则。
  - 聊天导诊场景优先走科室工具，而不是盲目 RAG。

### 3. 预约工具幂等键超长

- 现象：预约工具执行失败，数据库报 `varchar(64)` 超长。
- 原因：`idempotency_key` 使用 `traceId + slotId`，长度超过字段限制。
- 修复：Agent 预约幂等键改为短格式 `AGT-...`。

### 4. 取消预约被模型误判为知识检索

- 现象：用户明确提供 `appointmentId` 取消预约，模型 planner 仍路由到 RAG。
- 修复：
  - 明确业务动作采用本地确定性路由优先。
  - 预约、取消、号源、医生、科室等核心业务工具不完全依赖模型规划。

### 5. 模型参数漂移导致号源查询 0 条

- 现象：同样查询“2026-04-24 下午呼吸内科号源”，模型有时传 `periodCode=AFTERNOON`，导致查不到号源。
- 修复：
  - `QueryScheduleSlotsTool` 增加 `AM/PM` 参数归一化。
  - 支持 `上午/下午/早上/晚上/MORNING/AFTERNOON/EVENING`。
  - 本地业务路由优先，减少模型参数漂移对核心业务链路的影响。

### 6. 模型回复不符合业务约束

- 现象：
  - 号源回复不展示 `slotId`。
  - 预约成功回复不展示 `appointmentId`。
  - 模型曾要求补充姓名和电话，但当前工具并不需要这些字段。
- 修复：
  - 强化 system prompt：
    - 号源回复必须展示 `slotId`、日期、时段、科室、医生、剩余可约数量。
    - 预约成功必须展示 `appointmentId`、`appointmentNo`、日期、时段、科室、医生。
    - 取消成功必须展示 `appointmentId`、取消状态和取消时间。
    - 不得要求当前工具不需要的姓名、电话、身份证等字段。

## 自测与联调记录

### 1. 单元测试

- 命令：

```bash
mvn -q test
```

- 结果：通过。

### 2. 真实模型探针

- 探针问题：`请用一句话说明你是谁，并在回答里包含：模型联调探针`
- 结果：
  - 模型回复正常。
  - 日志确认 `glm-5.1` 调用成功。

### 3. 号源查询联调

- 输入：`帮我查2026-04-24下午呼吸内科号源`
- 结果：
  - `queryScheduleSlots` 工具执行成功。
  - 返回 1 条号源。
  - 回复展示 `slotId=bd60ffcc-f0d5-4ac6-9a01-26782c88c05e`。
  - 未再要求补充姓名或电话。

### 4. 预约挂号联调

- 输入：`帮我预约 slotId=bd60ffcc-f0d5-4ac6-9a01-26782c88c05e`
- 结果：
  - `bookAppointment` 工具执行成功。
  - 生成预约号 `APT20260515D9D7074A`。
  - 生成预约 ID `b654e77b-e2aa-4713-a0fa-a4d365290757`。
  - 回复展示预约号与预约 ID。

### 5. 取消挂号联调

- 输入：`帮我取消 appointmentId=b654e77b-e2aa-4713-a0fa-a4d365290757`
- 结果：
  - `cancelAppointment` 工具执行成功。
  - 预约状态变更为 `CANCELLED`。
  - 回复展示预约 ID、取消状态和取消时间。

### 6. 数据库核验

- 核验结果：
  - 测试预约最终状态为 `CANCELLED`。
  - `source_channel=AGENT`。
  - 号源余量释放回 `9`。
  - `agent_trace` 记录 `QUERY_SCHEDULE_SLOTS`、`BOOK_APPOINTMENT`、`CANCEL_APPOINTMENT`。
  - `tool_events_json` 包含工具结果和 `plannedTools`。
  - `chat_message.tool_call_json` 正常保存工具调用结果。

## 当前结论

- Phase 6 核心链路已经完成真实模型联调。
- 单 Agent + Tool Calling + RAG + SSE + Trace 主链路可运行。
- 真实模型已接入并能流式输出。
- 核心业务工具采用确定性路由优先，模型主要负责自然语言组织，降低业务参数漂移风险。

## 后续建议

- 后续 Phase 7 可继续补 Agent Trace 管理后台页面。
- 继续沉淀模型输出约束测试用例，尤其是工具参数、业务字段展示和禁止臆造字段。
- 私有模型代理的 `AI_INSECURE_TLS` 仅建议开发环境使用，生产环境应使用证书域名匹配的 HTTPS 入口。
