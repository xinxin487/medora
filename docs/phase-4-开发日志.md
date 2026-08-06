# Phase 4 开发日志

## 基本信息
- 阶段：Phase 4 对话基础能力
- 完成日期：2026-05-11
- 对应计划：[design/05-implementation-plan.md](/D:/BOS/Medora/design/05-implementation-plan.md)

## 本阶段目标
- 打通会话创建、会话列表、消息列表和基础 SSE 流式返回。
- 保证用户消息与助手消息能够持久化到数据库。
- 先使用固定回复 stub 打通聊天运行时框架，为后续 RAG、Agent Tool Calling 和 Memory 接入预留主链路。

## 已完成开发内容
- 新增数据库迁移 `V6__init_chat_tables.sql`：
  - `conversation`
  - `chat_message`
  - 会话列表索引 `idx_conversation_user_last_message`
  - 消息列表索引 `idx_chat_message_conversation_created`
  - 客户端消息幂等索引 `uk_chat_message_conversation_client_message`
- 新增后端对话模块：
  - `ConversationController`
  - `ChatStreamController`
  - `ConversationService`
  - `ChatRuntimeService`
  - `StreamEventPublisher`
  - `ConversationEntity`
  - `ChatMessageEntity`
  - `ConversationRepository`
  - `ChatMessageRepository`
- 新增对话相关 API：
  - `POST /api/v1/chat/conversations`
  - `GET /api/v1/chat/conversations`
  - `GET /api/v1/chat/conversations/{conversationId}/messages`
  - `POST /api/v1/chat/conversations/{conversationId}/stream`
  - `POST /api/v1/chat/conversations/{conversationId}/stop`
- 新增前端对话联调页：
  - `frontend/app/chat/page.tsx`
  - 支持创建会话、切换会话、发送消息、流式拼接助手回复、刷新落库后的消息。
- 新增前端 SSE client：
  - `frontend/lib/sse/chat-stream.ts`
  - 支持解析 `message_start`、`message_delta`、`message_end`、`tool_status`、`citation`、`error`。
- 修复健康检查阶段显示：
  - `HealthController` 从 `medora.phase` 读取阶段。
  - `application.yml` 更新为 `phase-4`。
- 修复 SSE 完成后的安全拦截问题：
  - `SecurityConfig` 放行 `DispatcherType.ASYNC` 和 `DispatcherType.ERROR`。
  - 避免 `SseEmitter` 完成后触发内部 async/error dispatch 时被误判为未授权。

## 自测与联调记录
### 1. 后端单元测试
- 验证命令：`mvn test`
- 验证结果：通过
- 测试统计：`11 tests, 0 failures, 0 errors`
- 新增覆盖重点：
  - 默认会话创建。
  - Phase 4 stub 回复内容生成。

### 2. 前端构建
- 验证命令：`npm run build`
- 验证结果：通过
- 验证页面：
  - `/`
  - `/login`
  - `/appointments`
  - `/chat`

### 3. 数据库迁移
- 启动后端后 Flyway 验证 `6 migrations`。
- 当前 schema 版本：`v6`
- 结果：`Schema "public" is up to date. No migration necessary.`

### 4. 后端健康检查
- 验证地址：`http://localhost:8080/api/v1/health`
- 验证结果：返回 `200`
- 验证重点：`phase` 返回 `phase-4`

### 5. SSE 接口联调
- 联调日期：2026-05-11
- 实际验证链路：
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/login`
  - `POST /api/v1/chat/conversations`
  - `POST /api/v1/chat/conversations/{conversationId}/stream`
  - `GET /api/v1/chat/conversations/{conversationId}/messages`
- 联调结果：
  - 会话创建成功。
  - SSE 返回 `200`。
  - 响应类型为 `text/event-stream`。
  - 流式事件包含 `message_start`、`message_delta`、`message_end`。
  - 消息列表返回 `USER` 与 `ASSISTANT` 两条消息。
  - 助手消息内容已持久化。

### 6. 前端页面级联调
- 验证地址：`http://localhost:3000/chat`
- 验证方式：Playwright headless 浏览器。
- 验证结果：通过
- 验证重点：
  - 页面可打开。
  - 本地登录 token 可被读取。
  - 可点击创建会话。
  - 可发送消息。
  - 页面展示用户消息。
  - 页面展示助手回复。
  - 无 console error。
  - 无失败网络响应。

## 过程中发现并修复的问题
- 问题：`/api/v1/health` 的 `phase` 仍显示 `PHASE_0`。
- 原因：健康检查返回值硬编码。
- 修复：改为读取配置项 `medora.phase`，并更新配置为 `phase-4`。
- 修复后结果：健康检查返回 `phase-4`。

- 问题：SSE 请求完成后，Node/browser 风格客户端报连接异常关闭，后端日志出现 `AccessDeniedException` 和 response committed。
- 原因：`SseEmitter` 完成后的 async/error dispatch 被 Spring Security 当作普通请求重新鉴权。
- 修复：在 `SecurityConfig` 中放行 `DispatcherType.ASYNC`、`DispatcherType.ERROR` 和 `/error`。
- 修复后结果：SSE 能正常输出 `message_start`、`message_delta`、`message_end` 并干净结束。

- 问题：前端 dev server 一度返回 `/chat 500`，错误为 `Cannot find module './778.js'`。
- 原因：前端 dev server 运行期间执行了 `npm run build`，Next `.next` 产物被并发写入导致 dev 缓存损坏。
- 修复：重启前端 dev server。
- 修复后结果：`/chat` 返回 `200`，页面级联调通过。

## 阶段产出结论
- Phase 4 已达到计划中的最小可运行目标。
- 当前系统已经具备对话基础链路：
  - 用户登录
  - 创建会话
  - 发送用户消息
  - SSE 流式返回助手回复
  - 用户消息与助手消息持久化
  - 前端对话页基础展示

## 已知遗留项
- 当前助手回复仍是固定 stub，尚未接入真实模型、知识库检索或 Agent 编排。
- `citation` 与 `tool_status` 事件已在前端解析器预留，但本阶段不会主动发送。
- `/stop` 当前返回 `NO_ACTIVE_STREAM`，尚未实现真实中断控制。
- 前端对话页是联调基础版，后续可继续优化对话列表、加载态、错误态和移动端体验。
- 种子数据中文历史编码乱码仍存在，当前不影响 Phase 4 主链路。

## 下一阶段建议
- 进入 Phase 5 知识库与基础 RAG。
- 优先实现：
  - 知识文档上传与元数据入库。
  - 文档解析与切片。
  - 基础检索接口。
  - 对话返回引用结构。
  - 前端引用片段展示联调。
