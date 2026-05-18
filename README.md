# Medora

Medora 是一个面向医疗服务场景的工程化智能助手系统，覆盖导诊咨询、科室/医生/号源查询、预约挂号、取消挂号、知识库检索增强、Agent 工具调用、会话记忆与后台排障能力。

## 当前进度

- 已完成 `Phase 0` 到 `Phase 8` 主线能力落地
- 已完成 `Phase 7`：会话摘要、长期记忆、上下文预算与裁剪
- 已完成 `Phase 8`：后台与可观测性、审计日志、Trace 查看、知识库后台
- 已补充正式前端结构方案、网页端产品原型与高保真界面稿

## 主要能力

- 用户认证：注册、登录、刷新令牌、当前用户信息
- 医疗业务：科室、医生、号源查询，预约创建与取消
- 对话能力：会话创建、消息持久化、SSE 流式输出
- AI 能力：RAG、Agent 编排、工具调用、会话摘要、长期记忆
- 管理后台：知识库、会话排查、Agent Trace、审计日志
- 前端产物：正式用户端页面、后台页面、高保真产品原型页面

## 仓库结构

```text
Medora/
  backend/    Spring Boot 后端
  frontend/   Next.js 前端
  design/     设计文档与实施计划
  docs/       阶段日志与补充文档
  scripts/    开发辅助脚本
  deploy/     Docker / Nginx / Compose 预留
  logs/       本地运行日志，不提交
```

## 本地环境

后端默认使用本机约定目录下的 `Java 21` 与 `Maven`：

```powershell
$env:JAVA_HOME="D:\BOS\medoraresourse\jdk-21.0.10"
$env:MAVEN_HOME="D:\BOS\medoraresourse\apache-maven-3.9.15"
$env:PATH="$env:MAVEN_HOME\bin;$env:JAVA_HOME\bin;$env:PATH"
```

前端开发：

```powershell
cd frontend
npm run dev
```

## 开发脚本

```text
scripts\dev\start-backend.bat
scripts\dev\start-frontend.bat
```

日志默认输出到：

```text
logs/backend-dev.log
logs/backend-dev.err.log
logs/frontend-dev.log
logs/frontend-dev.err.log
```

## 常用地址

- 后端健康检查：`http://localhost:8080/api/v1/health`
- 前端首页：`http://localhost:3000`
- 登录页：`http://localhost:3000/login`
- 预约页：`http://localhost:3000/appointments`
- 对话页：`http://localhost:3000/chat`
- 后台首页：`http://localhost:3000/admin`
- 知识库后台：`http://localhost:3000/admin/knowledge`
- 网页端界面总览：`http://localhost:3000/prototype`

## 最近验证

- 后端测试：`mvn -q test`
- 前端类型检查：`cd frontend && npx tsc --noEmit`
- 前端生产构建：`cd frontend && $env:NEXT_DIST_DIR='.next-buildcheck'; npm run build`

## 文档入口

- [实施计划](./design/05-implementation-plan.md)
- [技术设计](./design/technical-design.md)
- [API 契约设计](./design/03-api-contract-design.md)
- [正式前端结构与视觉方案](./design/06-frontend-page-structure-and-visual-design.md)
- [网页端页面清单与原型方案](./design/07-web-product-page-map-and-prototype-plan.md)
- [阶段文档索引](./docs/README.md)

## 最新阶段日志

- [Phase 6 开发日志](./docs/phase-6-%E5%BC%80%E5%8F%91%E6%97%A5%E5%BF%97.md)
- [Phase 7 开发日志](./docs/phase-7-%E5%BC%80%E5%8F%91%E6%97%A5%E5%BF%97.md)
- [Phase 8 开发日志](./docs/phase-8-%E5%BC%80%E5%8F%91%E6%97%A5%E5%BF%97.md)
- [Phase 8 正式前端开发日志](./docs/phase-8-%E6%AD%A3%E5%BC%8F%E5%89%8D%E7%AB%AF%E5%BC%80%E5%8F%91%E6%97%A5%E5%BF%97.md)
- [Phase 8 网页端产品原型日志](./docs/phase-8-%E7%BD%91%E9%A1%B5%E7%AB%AF%E4%BA%A7%E5%93%81%E5%8E%9F%E5%9E%8B%E6%97%A5%E5%BF%97.md)
- [Phase 8 高保真原型调整日志](./docs/phase-8-%E9%AB%98%E4%BF%9D%E7%9C%9F%E5%8E%9F%E5%9E%8B%E8%B0%83%E6%95%B4%E6%97%A5%E5%BF%97.md)
