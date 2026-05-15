# Medora

Medora 是一个面向医疗服务场景的工程化智能助手系统。当前项目已经完成 Phase 0 到 Phase 6 的核心开发，具备本地可运行的前后端、数据库迁移、用户认证、主数据查询、预约闭环、会话 SSE、知识库 RAG，以及 Agent 编排与业务工具调用能力。

## 当前进度

- Phase 0：工程骨架、前后端基础项目、环境约定已完成。
- Phase 1：数据库 migration、科室/医生/号源主数据查询已完成。
- Phase 2：注册、登录、JWT 鉴权、当前用户接口已完成。
- Phase 3：预约创建、幂等提交、预约列表/详情、取消预约、号源库存扣减与回补已完成。
- Phase 4：会话创建、消息落库、SSE 基础流式返回、前端对话联调页已完成。
- Phase 5：知识库上传、切片、向量检索、发布、检索测试、对话引用展示已完成。
- Phase 6：Agent 编排、真实模型网关、业务工具调用、工具轨迹落库、真实 SSE 流式输出已完成核心闭环。

## 仓库结构

```text
Medora/
  design/      # 设计文档与实施计划
  backend/     # Spring Boot 后端
  frontend/    # Next.js 前端
  deploy/      # Docker / Nginx / Compose 预留
  docs/        # 阶段日志与补充文档
  scripts/     # 项目辅助脚本
  logs/        # 本地启动日志，默认不提交
```

## 本地环境

后端编译和测试请固定使用项目指定的 Java 21 与 Maven：

```bat
set "JAVA_HOME=D:\BOS\medoraresourse\jdk-21.0.10"
set "MAVEN_HOME=D:\BOS\medoraresourse\apache-maven-3.9.15"
set "PATH=%MAVEN_HOME%\bin;%JAVA_HOME%\bin;%PATH%"
```

前端进入 `frontend` 后启动：

```bat
npm run dev
```

项目提供本地开发启动脚本：

```bat
scripts\dev\start-backend.bat
scripts\dev\start-frontend.bat
```

默认日志输出：

```text
logs/backend-dev.log
logs/backend-dev.err.log
logs/frontend-dev.log
logs/frontend-dev.err.log
```

## 常用验证

后端测试：

```bat
cd backend
set "JAVA_HOME=D:\BOS\medoraresourse\jdk-21.0.10"
set "MAVEN_HOME=D:\BOS\medoraresourse\apache-maven-3.9.15"
set "PATH=%MAVEN_HOME%\bin;%JAVA_HOME%\bin;%PATH%"
mvn -Dmaven.repo.local=D:\BOS\Medora\.m2 test
```

前端构建：

```bat
cd frontend
npm run build
```

本地访问：

```text
后端健康检查：http://localhost:8080/api/v1/health
前端首页：http://localhost:3000
预约页面：http://localhost:3000/appointments
对话页面：http://localhost:3000/chat
知识库后台：http://localhost:3000/admin/knowledge
```

## Phase 6 核心能力

- `POST /api/v1/chat/conversations/{conversationId}/stream`：通过 Agent 编排处理对话，并以真实 SSE token 增量返回。
- Intent Router：识别预约、取消预约、查询号源、查询科室/医生、知识库问答等意图。
- Tool Calling：支持号源查询、预约创建、预约取消、用户预约查询、知识库检索等业务工具。
- Agent Trace：将 intent、工具调用、工具结果、模型响应写入 `agent_trace`。
- Model Gateway：支持本地兜底、OpenAI-compatible、Anthropic-compatible 模型调用，并支持开发代理的 insecure TLS 配置。
- 前端对话页展示 Phase 6 Agent 状态、工具执行状态与引用信息。

## 文档入口

- [实施计划](./design/05-implementation-plan.md)
- [技术设计](./design/technical-design.md)
- [API 契约设计](./design/03-api-contract-design.md)
- [阶段文档索引](./docs/README.md)
- [Phase 3 开发日志](./docs/phase-3-%E5%BC%80%E5%8F%91%E6%97%A5%E5%BF%97.md)
- [Phase 4 开发日志](./docs/phase-4-%E5%BC%80%E5%8F%91%E6%97%A5%E5%BF%97.md)
- [Phase 5 开发日志](./docs/phase-5-%E5%BC%80%E5%8F%91%E6%97%A5%E5%BF%97.md)
- [Phase 6 开发日志](./docs/phase-6-%E5%BC%80%E5%8F%91%E6%97%A5%E5%BF%97.md)

## 已验证结果

- 后端单元测试通过：`mvn test`
- Phase 6 真实模型联调通过：号源查询、预约创建、预约取消、工具轨迹落库、真实流式输出。
- Phase 6 约束测试通过：模型回复包含必要业务字段，不再要求用户重复提供姓名、手机号、身份证号。
