# Phase 2 开发日志

## 基本信息
- 阶段：Phase 2 用户认证
- 完成日期：2026-04-22
- 对应计划：[design/05-implementation-plan.md](/D:/BOS/Medora/design/05-implementation-plan.md)

## 本阶段目标
- 打通注册、登录、刷新令牌、当前用户信息读取与更新链路。
- 建立统一鉴权能力，供后续预约、会话、知识库等模块复用。
- 提供一个可直接联调的前端认证页面，方便快速验证接口闭环。

## 已完成开发内容
- 后端新增认证与当前用户模块：
  - `auth/controller/AuthController`
  - `auth/service/AuthService`
  - `auth/security/*`
  - `user/controller/CurrentUserController`
  - `user/service/CurrentUserService`
- 后端补充认证相关数据模型与表结构：
  - `user_account`
  - `user_profile`
  - `refresh_token`
  - 迁移脚本 `V4__init_auth_tables.sql`
- 后端补充统一错误响应、请求 ID、基础异常处理能力。
- 前端补充登录联调页：
  - `frontend/app/login/page.tsx`
  - `frontend/lib/api/client.ts`
  - `frontend/lib/auth/token.ts`
- 前端支持：
  - 注册
  - 登录
  - 获取当前用户
  - 更新当前用户资料
  - 退出登录

## 自测与联调记录
### 1. 后端测试
- 测试日期：2026-04-22
- 验证方式：在项目目录显式切换到 Java 21 和 Maven 3.9.15 后执行 Maven 测试。
- 结果：后端测试通过，核心认证与当前用户服务相关测试通过。

### 2. 前端启动验证
- 验证命令：`npm run dev`
- 验证结果：前端开发服务正常启动，`http://localhost:3000/login` 可访问。

### 3. 后端健康检查
- 验证地址：`http://localhost:8080/api/v1/health`
- 验证结果：返回 `200`，服务状态为 `UP`。

### 4. 认证链路接口联调
- 联调日期：2026-04-22
- 实际验证链路：
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/login`
  - `GET /api/v1/users/me`
  - `PUT /api/v1/users/me`
- 联调结果：整条链路返回成功，说明认证、鉴权和当前用户上下文已经打通。

### 5. 页面级联调
- 验证页面：`http://localhost:3000/login`
- 验证方式：浏览器自动化触发页面注册、登录和当前用户读取流程。
- 结果：页面成功发起并收到以下接口的 `200` 响应：
  - `/api/v1/auth/register`
  - `/api/v1/auth/login`
  - `/api/v1/users/me`

## 过程中发现并修复的问题
- 问题：注册时 `user_profile.preference_json` 写入 PostgreSQL `jsonb` 列失败，导致注册接口返回 `500`。
- 原因：Hibernate 默认按字符串写入，数据库列类型为 `jsonb`。
- 修复：在 `UserProfileEntity.preferenceJson` 上增加 `@ColumnTransformer(write = "?::jsonb")`，显式按 `jsonb` 写入。
- 修复后结果：重新执行注册、登录、读取当前用户和更新资料链路后全部成功。

## 阶段产出结论
- Phase 2 已达到计划中的最小可运行目标。
- 后续 Phase 3 可以在当前统一鉴权能力之上继续实现预约、取消、列表和详情闭环。

## 已知遗留项
- `health` 接口返回中的 `phase` 仍显示为 `PHASE_0`，后续可调整为随阶段推进更新或改为配置驱动。
- 认证页面当前以联调为主，视觉和最终用户体验不是本阶段重点。

## 下一阶段建议
- 进入 Phase 3 医疗服务业务闭环。
- 优先实现：
  - 预约创建
  - 预约取消
  - 我的预约列表
  - 预约详情
  - 号源扣减与回补的一致性校验
