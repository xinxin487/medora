# 05. 项目实施计划文档

## 0. 文档定位
本文基于以下设计文档继续推进：
- [technical-design.md](/d:/BOS/Medora/design/technical-design.md)
- [02-domain-db-design.md](/d:/BOS/Medora/design/02-domain-db-design.md)
- [03-api-contract-design.md](/d:/BOS/Medora/design/03-api-contract-design.md)
- [04-agent-rag-memory-design.md](/d:/BOS/Medora/design/04-agent-rag-memory-design.md)

本文目标是把项目整理成“可分阶段执行的实施计划”，便于：
- 按阶段推进编码
- 控制上下文窗口成本
- 明确每轮开发的目标、边界与验收标准
- 让 Codex 后续可以按明确粒度持续编写代码、做自测、做联调、补文档

本文不新增总体架构方向，不重新发散选型，只在既定路线下组织执行方案。

---

## 1. 实施目标与执行原则

## 1.1 实施目标
- 将项目拆解为一组可顺序推进的阶段，保证每阶段有明确产出和完成标准。
- 优先形成可运行骨架，再逐步补全业务闭环、对话链路、RAG、Memory、后台管理和部署。
- 控制每次编码任务的范围，避免单轮实现过大导致返工或上下文丢失。
- 保证每完成一个阶段，就可以做自测、联调和文档回填。

## 1.2 执行原则
- 先骨架、后细节；先闭环、后增强；先可运行、后优化。
- 先打通后端主干链路，再建设前端界面与管理后台。
- 所有高风险副作用业务先做正式实现，不建议长期 mock。
- 所有 AI 能力先打通最小闭环，再做质量优化。
- 每个阶段结束时必须形成阶段总结文档或 README 更新，降低后续上下文切换成本。

## 1.3 合理工程假设
- 一期项目默认采用单仓库组织，前后端同仓。
- 后端优先使用模块化单体工程结构。
- 前端优先构建用户端，再补管理端。
- 一期可以接受部分管理后台页面功能较简单，但核心业务、对话与知识链路必须是真实可用实现。

---

## 2. 推荐开发总顺序

建议按以下顺序推进：

1. 初始化工程骨架与基础规范
2. 数据库与基础主数据链路
3. 用户认证与通用响应规范
4. 医疗服务业务闭环：号源、预约、取消
5. 会话与消息模型 + SSE 基础链路
6. 知识库接入与基础 RAG
7. Agent 编排与 Tool Calling
8. Memory、摘要与上下文压缩
9. 管理后台与可观测性
10. 部署、联调、演示打磨

这个顺序的原因：
- 业务闭环是整个项目的“落地抓手”，必须先稳定。
- 对话、RAG、Agent 建立在用户、业务、数据结构已稳定的基础上。
- Memory 和摘要属于质量增强层，适合在对话主链路打通后补。
- 管理后台和部署适合在主链路基本完成后集中补齐。

---

## 3. 阶段总览

| 阶段 | 名称 | 核心目标 | 是否必须完成后再进入下一阶段 |
|---|---|---|---|
| Phase 0 | 工程初始化 | 建立仓库结构、依赖、规范、基础配置 | 是 |
| Phase 1 | 数据库与主数据 | 建库、migration、科室/医生/号源基础查询 | 是 |
| Phase 2 | 用户认证 | 登录、注册、JWT、当前用户信息 | 是 |
| Phase 3 | 业务闭环 | 预约、取消、列表、详情、事务一致性 | 是 |
| Phase 4 | 对话基础能力 | 会话、消息、SSE 基础流式返回 | 是 |
| Phase 5 | 知识库与基础 RAG | 文档上传、解析、切片、检索、引用 | 是 |
| Phase 6 | Agent 编排与 Tool | 路由、工具调用、对话与业务结合 | 是 |
| Phase 7 | Memory 与上下文管理 | 摘要、长期记忆、上下文裁剪 | 否，可并行微调 |
| Phase 8 | 后台与可观测性 | 管理后台、审计、trace、监控 | 否 |
| Phase 9 | 部署与验收 | Docker、Nginx、联调、演示打磨 | 是 |

---

## 4. 建议项目目录结构

## 4.1 仓库级目录建议
```text
Medora/
  ├─ design/
  ├─ backend/
  ├─ frontend/
  ├─ deploy/
  ├─ scripts/
  ├─ docs/
  ├─ .env.example
  └─ README.md
```

## 4.2 后端目录建议
```text
backend/
  ├─ src/main/java/com/medora/
  │  ├─ common/
  │  ├─ auth/
  │  ├─ user/
  │  ├─ masterdata/
  │  ├─ appointment/
  │  ├─ chat/
  │  ├─ agent/
  │  ├─ knowledge/
  │  ├─ memory/
  │  ├─ admin/
  │  └─ config/
  ├─ src/main/resources/
  │  ├─ db/migration/
  │  ├─ application.yml
  │  ├─ application-dev.yml
  │  └─ application-prod.yml
  ├─ build.gradle or pom.xml
  └─ README.md
```

## 4.3 前端目录建议
```text
frontend/
  ├─ app/
  │  ├─ login/
  │  ├─ chat/
  │  ├─ appointments/
  │  ├─ doctors/
  │  ├─ admin/
  │  └─ layout.tsx
  ├─ components/
  ├─ lib/
  │  ├─ api/
  │  ├─ auth/
  │  ├─ sse/
  │  └─ types/
  ├─ hooks/
  ├─ styles/
  └─ README.md
```

## 4.4 部署目录建议
```text
deploy/
  ├─ docker/
  │  ├─ backend.Dockerfile
  │  ├─ frontend.Dockerfile
  │  └─ nginx.conf
  ├─ compose/
  │  ├─ docker-compose.dev.yml
  │  └─ docker-compose.prod.yml
  └─ README.md
```

---

## 5. 分阶段实施计划

## 5.1 Phase 0：工程初始化

### 目标
- 建立单仓基础结构
- 完成前后端工程初始化
- 补齐基础配置、统一约定、环境说明

### 范围
- 初始化 `backend` 工程
- 初始化 `frontend` 工程
- 初始化 `design`、`deploy`、`scripts` 目录
- 建立 `.env.example`
- 配置代码格式化和基础 lint

### 产出物
- 后端空项目可启动
- 前端空项目可启动
- 基础 README
- 本地开发环境说明

### 建议新增目录与文件
**后端**
- `backend/build.gradle` 或 `pom.xml`
- `backend/src/main/java/com/medora/Application.java`
- `backend/src/main/resources/application.yml`

**前端**
- `frontend/package.json`
- `frontend/app/page.tsx`
- `frontend/lib/api/client.ts`

**仓库根目录**
- `.env.example`
- `README.md`

### 哪些先做骨架
- 全部采用骨架，不做具体业务实现

### 完成标准
- `backend` 能启动一个健康检查接口
- `frontend` 能启动首页
- 本地目录结构稳定

### 自测与联调建议
- 后端启动测试
- 前端启动测试
- 环境变量读取测试

### 风险点
- 前后端工程初始化选型不一致
- 环境变量管理混乱

---

## 5.2 Phase 1：数据库与主数据

### 目标
- 按数据库设计文档建立 migration 基线
- 打通科室、医生、号源查询主链路

### 范围
- 创建基础表
- 建立种子数据
- 实现主数据查询接口

### 产出物
- 初版 migration
- 科室、医生、号源查询 API
- 基础 Repository / Query Service

### 建议新增目录与文件
**后端**
- `backend/src/main/resources/db/migration/V1__init_extensions.sql`
- `V2__init_user_tables.sql`
- `V3__init_masterdata_tables.sql`
- `V4__init_schedule_tables.sql`
- `masterdata/controller/*`
- `masterdata/service/*`
- `masterdata/repository/*`

### 哪些先做骨架
- `visit_record` 可先只建表不开放接口
- 后续模糊搜索、复杂过滤先不做

### 哪些建议正式实现
- `department`
- `doctor`
- `schedule_slot`
- migration 和索引必须正式实现

### 完成标准
- 数据库可一键迁移
- `/departments` `/doctors` `/schedule-slots` 可用
- 数据表与查询路径能支撑基础联调

### 自测与联调建议
- migration 执行测试
- Repository 查询测试
- API 分页与过滤测试
- 前端临时页面联调科室/医生/号源列表

### 风险点
- migration 反复变更导致版本不稳定
- 种子数据结构不合理，后续频繁返工

---

## 5.3 Phase 2：用户认证

### 目标
- 打通注册、登录、刷新、当前用户信息
- 建立统一鉴权与当前用户上下文

### 范围
- JWT 生成与校验
- `user_account` / `user_profile` / `refresh_token`
- 认证相关 API
- Spring Security 基础接入

### 产出物
- 认证 API
- `SecurityConfig`
- 统一用户上下文读取方式

### 建议新增目录与文件
- `auth/controller/AuthController.java`
- `auth/service/AuthService.java`
- `auth/security/SecurityConfig.java`
- `auth/security/JwtTokenProvider.java`
- `user/controller/CurrentUserController.java`
- `common/web/ApiResponse.java`
- `common/exception/*`

### 哪些先做骨架
- RBAC 复杂权限先不做
- 忘记密码、短信验证码先不做

### 哪些建议正式实现
- 登录、刷新、当前用户信息必须正式实现
- 统一错误码和统一响应必须正式实现

### 完成标准
- 登录后可拿 token 访问受保护接口
- 管理员/普通用户基本角色可区分
- 所有后续接口可复用统一鉴权体系

### 自测与联调建议
- 登录成功/失败测试
- token 过期测试
- refresh token 测试
- 前端登录页联调

### 风险点
- token 生命周期设计不清
- 前后端 token 刷新逻辑不一致

---

## 5.4 Phase 3：医疗服务业务闭环

### 目标
- 打通预约、取消、预约列表、详情
- 保证号源扣减与回补事务一致

### 范围
- `appointment`
- `appointment_operation_log`
- 创建预约接口
- 取消预约接口
- 我的预约列表与详情

### 产出物
- 预约业务 Service
- 事务处理与幂等处理
- 预约页面可联调

### 建议新增目录与文件
- `appointment/controller/AppointmentController.java`
- `appointment/service/AppointmentCommandService.java`
- `appointment/service/AppointmentQueryService.java`
- `appointment/repository/*`
- `appointment/domain/*`
- `appointment/dto/*`

### 哪些先做骨架
- 就诊记录可先只保留数据结构
- 后台手工预约或修正先不做

### 哪些建议正式实现
- 预约事务
- 取消事务
- 号源回补
- 幂等键

### 完成标准
- 可以真实预约一个号源
- 可以真实取消并回补号源
- 不会发生重复预约或库存负数

### 自测与联调建议
- 单接口测试
- 并发预约测试
- 重复取消测试
- 前端预约列表和取消按钮联调

### 风险点
- 悲观锁实现不正确
- 幂等键遗漏
- slot 状态流转与库存不一致

---

## 5.5 Phase 4：对话基础能力

### 目标
- 打通会话、消息、SSE 基础流式返回
- 不先上完整 Agent，只先打通聊天运行时框架

### 范围
- `conversation`
- `chat_message`
- 创建会话
- 拉取消息
- 基础 `/stream` SSE 链路

### 产出物
- 会话 API
- SSE 事件模型
- 前端对话页基础版

### 建议新增目录与文件
- `chat/controller/ConversationController.java`
- `chat/controller/ChatStreamController.java`
- `chat/service/ConversationService.java`
- `chat/service/ChatRuntimeService.java`
- `chat/sse/StreamEventPublisher.java`
- `frontend/app/chat/page.tsx`
- `frontend/lib/sse/chat-stream.ts`

### 哪些先做骨架
- 这阶段可以先用固定回复或简单模型调用 stub 打通 SSE
- 先不接入完整检索和工具调用

### 哪些建议正式实现
- 会话和消息落库
- SSE 事件结构
- 前端流式渲染

### 完成标准
- 用户能创建会话并发消息
- 页面能流式展示回复
- 消息能持久化

### 自测与联调建议
- SSE 连接中断测试
- 会话列表与消息列表联调
- 打字机效果与错误提示联调

### 风险点
- SSE 事件协议过早频繁变化
- 消息持久化时机设计不稳定

---

## 5.6 Phase 5：知识库与基础 RAG

### 目标
- 打通文档上传、解析、切片、向量化、检索
- 让对话可以基于知识库回答并返回引用

### 范围
- `knowledge_document`
- `knowledge_chunk`
- `knowledge_ingest_task`
- 文档上传
- 检索测试接口
- 基础 Hybrid Retrieval

### 产出物
- 文档入库链路
- 检索服务
- 引用结构

### 建议新增目录与文件
- `knowledge/controller/AdminKnowledgeController.java`
- `knowledge/service/KnowledgeDocumentService.java`
- `knowledge/service/DocumentIngestService.java`
- `knowledge/service/RetrievalService.java`
- `knowledge/retriever/KeywordRetriever.java`
- `knowledge/retriever/VectorRetriever.java`
- `knowledge/retriever/HybridRetriever.java`
- `knowledge/parser/*`
- `knowledge/chunk/*`

### 哪些先做骨架
- rerank 先做占位接口
- 文档增量重建先不做复杂优化

### 哪些建议正式实现
- 文档上传
- 切片入库
- FTS + 向量混合检索
- 引用返回

### 完成标准
- 管理后台可上传知识文档
- 检索测试接口可用
- 对话可返回至少 1 条有效引用

### 自测与联调建议
- 文档入库状态流转测试
- 检索命中率人工验证
- 前端引用片段展示联调

### 风险点
- 中文切分效果不稳定
- 文档清洗不足导致召回噪音大

---

## 5.7 Phase 6：Agent 编排与 Tool Calling

### 目标
- 将对话、RAG 和业务工具整合成统一编排链路
- 建立普通对话 / RAG / Tool / 混合模式的路由能力

### 范围
- `ChatOrchestrator`
- `IntentRouter`
- `ToolExecutor`
- 查询型 Tool
- 动作型 Tool

### 产出物
- Agent 主链路
- Tool 注册与执行机制
- Trace 记录

### 建议新增目录与文件
- `agent/application/ChatOrchestrator.java`
- `agent/application/IntentRouter.java`
- `agent/tool/ToolExecutor.java`
- `agent/tool/ToolRegistry.java`
- `agent/tool/tools/*`
- `agent/trace/AgentTraceService.java`

### 哪些先做骨架
- 多 Agent、Router Agent 先不做
- 高级 Query Rewrite 先不做

### 哪些建议正式实现
- `queryDepartments`
- `queryDoctors`
- `queryScheduleSlots`
- `bookAppointment`
- `cancelAppointment`
- `searchKnowledge`

### 完成标准
- 用户可在对话里查号源
- 用户可在对话里预约和取消
- 知识检索与工具调用能在统一回复里协作

### 自测与联调建议
- 每个 Tool 独立测试
- Tool 与对话组合测试
- 失败回退测试
- Agent Trace 与 SSE 事件联调

### 风险点
- Tool 参数解析不稳定
- 模型自由发挥越过工具边界
- 动作型 Tool 失败后提示不清晰

---

## 5.8 Phase 7：Memory、摘要与上下文管理

### 目标
- 打通会话摘要、长期记忆、上下文裁剪
- 提升多轮连续对话质量

### 范围
- `conversation_summary`
- `user_memory_profile`
- `ConversationSummaryService`
- `MemoryService`
- `ContextPruner`
- `PromptBuilder`

### 产出物
- 摘要生成与更新链路
- 长期记忆读取与写入链路
- Prompt 预算控制

### 建议新增目录与文件
- `memory/service/MemoryService.java`
- `memory/service/ConversationSummaryService.java`
- `agent/application/PromptBuilder.java`
- `agent/support/TokenBudgetPlanner.java`
- `agent/support/ContextPruner.java`

### 哪些先做骨架
- 长期记忆可以先只做读取与简单位点写入
- 复杂记忆冲突合并规则先不做深

### 哪些建议正式实现
- 会话摘要触发
- 上下文压缩
- 高置信长期记忆写入

### 完成标准
- 会话长到一定轮数后能自动摘要
- 新会话可读到相关长期记忆
- 模型上下文长度受控

### 自测与联调建议
- 多轮对话回归测试
- 摘要前后回答一致性测试
- 记忆污染测试

### 风险点
- 摘要覆盖过度导致信息丢失
- 长期记忆误写入导致污染

---

## 5.9 Phase 8：管理后台与可观测性

### 目标
- 为知识库、会话、审计、trace 提供最小可用后台
- 补齐日志、指标与可追踪性

### 范围
- 知识库管理页
- 会话查看页
- 审计日志页
- Agent Trace 页
- 指标导出

### 产出物
- 管理后台基础页面
- Prometheus 指标
- 结构化日志

### 建议新增目录与文件
- `admin/controller/*`
- `frontend/app/admin/*`
- `common/logging/*`
- `common/metrics/*`
- `deploy/prometheus/*`

### 哪些先做骨架
- 高级筛选器先简化
- 复杂图表先不做

### 哪些建议正式实现
- 文档列表/详情/重建
- 会话查看
- 审计日志查询
- Agent Trace 查询

### 完成标准
- 管理员可查看知识文档状态
- 可查看用户会话和审计事件
- 基础指标可被 Prometheus 抓取

### 自测与联调建议
- 管理后台权限测试
- 后台查询分页测试
- 指标抓取测试

### 风险点
- 后台接口与用户接口权限混淆
- 日志字段不统一

---

## 5.10 Phase 9：部署、联调与验收

### 目标
- 将项目打包为可运行的单机部署版本
- 完成跨模块联调与验收

### 范围
- Dockerfile
- docker-compose
- Nginx
- HTTPS 与代理配置
- 演示数据与脚本

### 产出物
- 本地 compose 方案
- 生产近似 compose 方案
- 部署说明文档
- 验收清单

### 建议新增目录与文件
- `deploy/docker/backend.Dockerfile`
- `deploy/docker/frontend.Dockerfile`
- `deploy/compose/docker-compose.dev.yml`
- `deploy/compose/docker-compose.prod.yml`
- `deploy/docker/nginx.conf`
- `deploy/README.md`

### 哪些先做骨架
- 监控告警通知可以先简化
- 自动化 CI/CD 可以先不做满

### 哪些建议正式实现
- 本地与服务器部署脚本
- Nginx 代理 SSE
- 数据库和 MinIO 持久卷

### 完成标准
- 一条命令可启动核心服务
- 前端、后端、数据库、Redis、MinIO、Prometheus、Grafana 可联动
- 关键演示路径可跑通

### 自测与联调建议
- 冷启动测试
- 登录到对话到预约到取消完整回归
- 知识库上传到问答引用回归
- 服务器近似环境压测和稳定性测试

### 风险点
- SSE 被代理层缓冲
- 配置文件与环境变量不一致
- Docker 网络配置错误

---

## 6. 骨架优先与正式实现策略

## 6.1 适合先做骨架的模块
- 前端页面壳层
- 管理后台页面结构
- RerankService
- 多 Agent 扩展接口
- Query Rewrite
- 高级搜索过滤器
- 高级 UI 状态与动效

## 6.2 建议一开始就正式实现的模块
- migration
- 用户认证
- 科室/医生/号源查询
- 预约与取消事务
- 会话与消息落库
- SSE 协议
- 知识库上传与基础检索
- Tool Calling 边界

## 6.3 原因说明
- 上述核心模块是系统稳定性与真实性的基础，长期 mock 会导致后续返工成本很高。
- 后台 UI、排序优化、rerank、多 Agent 这类模块适合在主链路稳定后迭代。

---

## 7. 前后端、数据库、Agent、知识库、部署工作的拆分方式

## 7.1 后端拆分建议
- 先基础框架和通用能力
- 再主数据和业务闭环
- 再对话主链路
- 再知识库与 RAG
- 再 Agent 编排
- 再 Memory 和上下文管理

## 7.2 前端拆分建议
- 登录页
- 科室/医生/号源基础查询页
- 预约列表页
- 对话页
- 后台知识库页
- 后台日志/trace 页

## 7.3 数据库拆分建议
- migration 单独阶段推进
- 每完成一个子域，就补对应 migration 和种子数据
- 不要等全部模块设计完成后再一次性建表

## 7.4 Agent 拆分建议
- 先固定对话链路
- 再加入知识检索
- 再加入查询型 Tool
- 再加入动作型 Tool
- 最后加摘要与长期记忆

## 7.5 知识库拆分建议
- 先文档上传与元数据
- 再解析与切片
- 再 embedding 与检索
- 再引用与测试工具

## 7.6 部署拆分建议
- 本地开发 compose
- 单机生产 compose
- Nginx 反向代理
- 指标采集

---

## 8. 每个阶段的完成标准模板

建议每个阶段都使用统一验收模板：

### 8.1 代码层完成标准
- 关键模块代码已落地
- 编译通过
- 基础单元测试或集成测试通过

### 8.2 接口层完成标准
- 对应接口可访问
- 响应结构符合契约
- 错误码与状态码符合设计

### 8.3 前端联调完成标准
- 页面能调用真实接口
- 正常路径可跑通
- 基础异常提示可见

### 8.4 文档完成标准
- 阶段 README 或总结已更新
- 关键配置与启动步骤已记录

---

## 9. 每个阶段如何自测与联调

## 9.1 自测原则
- 先做服务层或 repository 层自测
- 再做 API 集成测试
- 再做前端联调
- 再做跨模块回归

## 9.2 建议测试层次

| 层次 | 用途 |
|---|---|
| 单元测试 | 规则逻辑、工具解析、摘要策略 |
| Repository/DB 测试 | 查询路径、事务、锁 |
| API 集成测试 | DTO、鉴权、状态码 |
| 前端联调测试 | 页面与接口交互 |
| 端到端场景测试 | 登录->查询->预约->对话->取消 |

## 9.3 各阶段推荐自测重点
- Phase 1：migration 和查询
- Phase 2：认证与权限
- Phase 3：事务一致性与幂等
- Phase 4：SSE 流式协议
- Phase 5：检索命中质量
- Phase 6：Tool 调用边界
- Phase 7：摘要与记忆污染
- Phase 8：后台权限和追踪
- Phase 9：部署与完整回归

---

## 10. Mock / Stub 使用建议

## 10.1 建议先用 mock / stub 的部分
- 前端页面布局阶段的假数据列表
- SSE 初始打通阶段的固定流式文本
- RerankService
- 多 Agent Router
- 高级统计图表

## 10.2 建议一开始就做正式实现的部分
- 数据库 migration
- 认证
- 预约/取消
- 会话消息持久化
- 知识库入库链路
- 检索
- Tool Calling

## 10.3 原则
- 影响数据真相和事务一致性的地方，不要长期 mock
- 影响体验但不影响核心事实的地方，可以短期 stub

---

## 11. 针对上下文窗口限制的执行建议

## 11.1 文档组织方式
后续建议保持以下文档分层：
- 总体设计：`technical-design.md`
- 子域设计：`02-03-04`
- 实施计划：`05-implementation-plan.md`
- 每阶段总结：`design/phases/phase-x-summary.md`

## 11.2 代码任务组织方式
每轮 Codex 任务尽量控制在以下粒度之一：
- 一个后端 Controller + DTO + Service 骨架
- 一个子域 migration + Entity + Repository
- 一个前端页面 + 对应 API 联调
- 一个 Agent 子模块

避免单轮要求：
- 同时写完整后端多个子域
- 同时写前后端和部署和测试
- 同时把 RAG、Memory、Agent 整体一次性落完

## 11.3 阶段性总结建议
每完成一个阶段，建议新增：
- `phase-goals`
- `done`
- `not-done`
- `known-risks`
- `next-step`

格式可统一为：
`design/phases/phase-<n>-summary.md`

## 11.4 上下文压缩建议
后续让 Codex 编码时，优先引用：
- 当前阶段实施计划
- 当前子域设计文档
- 最近阶段总结

不要每次都重新塞入全量设计文档，除非确实需要全局对照。

---

## 12. Codex 分轮次写代码的推荐粒度

## 12.1 后端推荐粒度
每轮最好控制在以下范围之一：
- 一个子域 migration + Entity + Repository
- 一个 Query Service + Controller
- 一个 Command Service + Controller + 事务测试
- 一个 Agent 子模块

### 示例任务
- “实现 department/doctor/schedule_slot 的 migration 和查询接口”
- “实现登录、刷新、当前用户接口”
- “实现 appointment 的创建与取消事务”
- “实现 conversation + chat_message + SSE 基础链路”
- “实现 KnowledgeDocument 上传与任务入库”

## 12.2 前端推荐粒度
- 一个页面 + 所需 API client
- 一个业务流程页
- 一个管理后台子页面

### 示例任务
- “实现登录页和 token 管理”
- “实现号源查询页”
- “实现预约列表页”
- “实现对话页的 SSE 渲染”

## 12.3 Agent 推荐粒度
- 一个路由器
- 一个 PromptBuilder
- 一个 ToolExecutor
- 一个 RetrievalService
- 一个 MemoryService

---

## 13. 风险点、依赖关系与注意事项

## 13.1 关键依赖关系
- Phase 1 是后续所有阶段的基础
- Phase 2 鉴权完成后，Phase 3/4/5/6 才能稳定推进
- Phase 3 业务闭环完成后，Phase 6 的动作型 Tool 才能正式接入
- Phase 5 基础 RAG 完成后，Phase 6 Agent 才能构造知识增强回答
- Phase 4 和 Phase 6 对 SSE 协议必须保持一致

## 13.2 关键风险点

### 风险 A：过早追求大而全
- 现象：同时做后台、前台、RAG、Agent、部署
- 处理：严格按阶段推进

### 风险 B：接口和数据库反复改动
- 现象：前端联调时频繁推翻契约
- 处理：先稳住 `02` 和 `03` 文档，再编码

### 风险 C：AI 部分过早复杂化
- 现象：多 Agent、复杂 rerank、复杂 memory 一次性上
- 处理：先单 Agent + Hybrid Retrieval + 结构化 Memory

### 风险 D：SSE 协议频繁变化
- 现象：前端对话页频繁返工
- 处理：Phase 4 先定事件协议，后续只加字段不改语义

### 风险 E：知识库链路质量不稳定
- 现象：切片乱、召回差、引用不可信
- 处理：先做基础人工验证与检索测试接口

## 13.3 注意事项
- 每阶段优先跑通“最短可演示链路”
- 核心链路必须有真实数据与真实事务支撑
- 所有阶段结束都要回填文档或阶段总结
- 不建议在设计尚未稳定前一次性写大批量代码

---

## 14. 里程碑开发计划

## 14.1 里程碑 M1：基础可运行骨架
**包含**
- Phase 0
- Phase 1
- Phase 2

**结果**
- 系统能启动
- 能登录
- 能查询科室、医生、号源

## 14.2 里程碑 M2：业务闭环可演示
**包含**
- Phase 3

**结果**
- 用户可真实预约、取消、查看预约
- 数据一致性稳定

## 14.3 里程碑 M3：对话链路可运行
**包含**
- Phase 4

**结果**
- 可以创建会话并流式聊天
- 消息持久化

## 14.4 里程碑 M4：RAG 问答可展示
**包含**
- Phase 5

**结果**
- 后台上传知识文档
- 对话可返回带引用的知识回答

## 14.5 里程碑 M5：智能助手闭环完成
**包含**
- Phase 6
- Phase 7

**结果**
- 对话中可查询号源、预约、取消
- 有基础记忆和摘要能力

## 14.6 里程碑 M6：可上线演示版
**包含**
- Phase 8
- Phase 9

**结果**
- 管理后台可用
- 可观测性基本具备
- Docker 部署可运行
- 可作为作品集完整演示

---

## 15. 后续建议
- 当前这份实施计划文档已经适合作为后续编码的“总施工图”。
- 下一步进入编码前，建议先继续补一份 `design/06-backend-package-and-service-design.md`，把后端包结构、Application Service、Domain Service、Query Service、Repository、异常体系再固定下来。
- 如果希望尽快进入代码，也可以直接以本计划为执行基线，按 `Phase 0 -> Phase 1 -> Phase 2` 逐步开始实现。

