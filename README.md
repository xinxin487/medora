# Medora

> 面向医疗服务的工程化智能助手系统 — 多 Agent 架构 + LLM 评测平台 + RAG 知识库 + 全栈可观测

Medora 覆盖导诊咨询、科室/医生/号源查询、预约挂号与取消、知识库 RAG 问答、会话记忆与后台排障能力。项目基于 Google ADK 构建多 Agent 编排,自建 LLM 评测平台与 CI 回归,坚持 LLM-first 原则与四层医疗安全防护。

## 界面展示

### 患者端

| 页面 | 截图 |
|---|---|
| 登录入口 | ![登录](docs/screenshots/login.png) |
| AI 助手对话(SSE 流式 + Markdown 渲染) | ![AI 助手](docs/screenshots/patient-assistant.png) |
| 会话详情(患者画像 + 最近预约 + 偏好记忆) | ![会话详情](docs/screenshots/patient-detail.png) |
| 数据记录与偏好设置 | ![数据与偏好](docs/screenshots/patient-profile.png) |

### 管理后台

| 页面 | 截图 |
|---|---|
| 管理控制台(知识文档 / 会话 / Trace / 审计 概览) | ![管理控制台](docs/screenshots/admin-overview.png) |

## 核心亮点

- **基于 Google ADK 构建多 Agent 编排架构** — 配置 Root、Triage、Booking、Knowledge 四 Agent 拓扑,通过 LLM 路由与 transfer 实现意图分发;落地分层安全 Callback、工具桥接与会话持久化,完成旧过程式编排的全量替换。

- **LLM 评测与 CI 回归平台** — 自建测试用例、评分器、执行器三层框架,支持 LLM 自动评分、对抗用例生成;测试用例随代码版本管理,PR 提交时 GitHub 自动跑回归测试,LLM 行为退化立即报警。

- **RAG 知识问答** — 基于 PostgreSQL、pgvector 构建医疗知识库,落地文档解析、切片、Embedding、混合检索全链路;通过 TopK 召回、相似度阈值与元数据过滤,问答命中率稳定 90% 以上。

- **多层级会话记忆** — Redis 短期缓存、PostgreSQL 长期记忆与摘要压缩三层架构,降低 token 成本,支持跨会话上下文恢复与偏好复用。

- **医疗合规与可观测** — 坚持 LLM-first(决策层不降级到正则),落地 prompt 注入、越权请求、处方建议、幻觉检测四层防护;Agent Trace 持久化路由链、工具事件、检索结果、延迟、风险等级,支持按 traceId 排障。

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 3 · Java 21 · Maven |
| 数据 | PostgreSQL · pgvector · Redis |
| AI | Google ADK 0.3 · LangChain4j · DeepSeek · Anthropic |
| 前端 | Next.js 15 · React 19 · TypeScript 5.6 · Tailwind 3.4 |
| 评测 | EvalCli(自研)· JUnit 5 · GitHub Actions |

## 系统架构

<!-- TODO: 在 docs/diagrams/ 下补一张架构图,展示四 Agent 拓扑 + 安全 Callback 分层 + RAG/记忆链路 -->

`![系统架构](docs/diagrams/architecture.png)`

## 快速开始

### 本地环境

后端依赖 Java 21 与 Maven:

```powershell
$env:JAVA_HOME="D:\BOS\medoraresource\jdk-21.0.10"
$env:MAVEN_HOME="D:\BOS\medoraresource\apache-maven-3.9.15"
$env:PATH="$env:MAVEN_HOME\bin;$env:JAVA_HOME\bin;$env:PATH"
```

需要 PostgreSQL(含 pgvector 扩展)+ Redis + DeepSeek API Key。

### 启动

```powershell
# 后端
scripts\dev\start-backend.bat

# 前端
scripts\dev\start-frontend.bat
```

### 跑评测

```bash
cd backend

# 模拟模式(无 LLM,验证流程)
mvn -B test-compile exec:java -Dexec.mainClass=com.medora.eval.EvalCli \
    -Dexec.classpathScope=test \
    '-Dexec.args=--mode stub --scenarios src/test/resources/eval/scenarios/demo-perfect.yaml'

# 回放模式(用入 git 的 recordings,CI 友好)
mvn -B test-compile exec:java -Dexec.mainClass=com.medora.eval.EvalCli \
    -Dexec.classpathScope=test \
    '-Dexec.args=--mode replay --recordings src/test/resources/eval/recordings/judge.json \
     --scenarios src/test/resources/eval/scenarios/demo-perfect.yaml'
```

## 项目结构

```text
Medora/
  backend/                    Spring Boot 后端
    src/main/java/com/medora/
      adk/                    Google ADK 多 Agent 编排(agents/orchestrator/guard/session/tool/sse)
      agent/                  Chat orchestrator + Trace
      ai/                     LLM / Embedding / RAG
      appointment/            预约业务
      auth/                   认证
      chat/                   会话与 SSE
      knowledge/              知识库与 RAG 检索
      masterdata/             科室/医生/号源
    src/test/java/com/medora/
      eval/                   LLM 评测平台(domain/loader/judge/runner/report/llm/gen)
  frontend/                   Next.js 前端
    app/app/                  患者端
    app/admin/                管理后台
    app/prototype/            高保真原型
  docs/                       阶段日志与补充文档
  design/                     设计文档与实施计划
  scripts/                    开发辅助脚本
  .github/workflows/          CI(eval stub + replay 双 job)
```

## 常用地址

- 后端健康检查:`http://localhost:8080/api/v1/health`
- 前端首页:`http://localhost:3000`
- 患者端助手:`http://localhost:3000/app/assistant`
- 管理后台:`http://localhost:3000/admin`
- 高保真原型:`http://localhost:3000/prototype`

## 文档导航

- [优化方案与进度](./docs/optimization-plan.md)
- [系统总览](./system-overview.md)
- [阶段日志索引](./docs/README.md)
- [实施计划](./design/05-implementation-plan.md)
- [技术设计](./design/technical-design.md)
- [API 契约设计](./design/03-api-contract-design.md)
- [正式前端结构与视觉方案](./design/06-frontend-page-structure-and-visual-design.md)
