# Phase 3 开发日志

## 基本信息
- 阶段：Phase 3 医疗服务业务闭环
- 完成日期：2026-05-11
- 对应计划：[design/05-implementation-plan.md](/D:/BOS/Medora/design/05-implementation-plan.md)

## 本阶段目标
- 打通预约创建、取消、我的预约列表和预约详情。
- 保证预约创建与号源扣减在同一个数据库事务内完成。
- 保证预约取消与号源回补在同一个数据库事务内完成。
- 支持 `Idempotency-Key`，避免重复提交造成重复预约。

## 已完成开发内容
- 新增数据库迁移 `V5__init_appointment_tables.sql`：
  - `appointment`
  - `appointment_operation_log`
  - 幂等唯一索引 `uk_appointment_user_idempotency`
  - 预约列表、号源状态和操作日志查询索引
- 新增后端预约模块：
  - `AppointmentController`
  - `AppointmentCommandService`
  - `AppointmentQueryService`
  - `AppointmentEntity`
  - `AppointmentOperationLogEntity`
  - `AppointmentRepository`
  - `AppointmentOperationLogRepository`
- 新增预约相关 API：
  - `POST /api/v1/appointments`
  - `POST /api/v1/appointments/{appointmentId}/cancel`
  - `GET /api/v1/appointments`
  - `GET /api/v1/appointments/{appointmentId}`
- 补充号源实体的业务方法：
  - `reserveOne()`
  - `releaseOne()`
- 修复主数据查询懒加载问题：
  - `ScheduleSlotQueryService.list` 增加只读事务
  - `DoctorQueryService.list/getDetail` 增加只读事务
- 新增前端预约联调页：
  - `frontend/app/appointments/page.tsx`
  - 支持查号源、创建预约、查看我的预约、取消预约
- 新增本地开发启动脚本：
  - `scripts/dev/start-backend.bat`
  - `scripts/dev/start-frontend.bat`

## 自测与联调记录
### 1. 后端单元测试
- 验证命令：在项目目录显式切换到 Java 21 和 Maven 3.9.15 后执行 `mvn test`
- 验证结果：通过
- 测试统计：`9 tests, 0 failures, 0 errors`
- 覆盖重点：
  - 创建预约后号源扣减
  - 相同 `Idempotency-Key` 重复提交返回同一预约
  - 取消预约后号源回补
  - 操作日志写入调用

### 2. 前端构建
- 验证命令：`npm run build`
- 验证结果：通过
- 验证页面：
  - `/`
  - `/login`
  - `/appointments`

### 3. 前端页面访问
- 验证地址：`http://localhost:3000/appointments`
- 验证结果：返回 `200`

### 4. 后端健康检查
- 验证地址：`http://localhost:8080/api/v1/health`
- 验证结果：返回 `200`

### 5. 完整预约闭环联调
- 联调日期：2026-05-11
- 实际验证链路：
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/login`
  - `GET /api/v1/schedule-slots`
  - `POST /api/v1/appointments`
  - `POST /api/v1/appointments` 使用相同 `Idempotency-Key` 重复提交
  - `GET /api/v1/appointments`
  - `GET /api/v1/appointments/{appointmentId}`
  - `POST /api/v1/appointments/{appointmentId}/cancel`
  - 再次 `GET /api/v1/schedule-slots` 验证号源回补
- 联调结果：
  - 注册成功
  - 登录成功
  - 号源查询成功
  - 预约创建成功，状态为 `CONFIRMED`
  - 幂等重复提交返回同一预约
  - 我的预约列表返回成功
  - 预约详情返回成功，状态为 `CONFIRMED`
  - 取消预约成功，状态为 `CANCELLED`
  - 号源从 `8 -> 7 -> 8`，扣减与回补符合预期

## 过程中发现并修复的问题
- 问题：`/schedule-slots` 在认证后查询返回 `500`。
- 原因：`spring.jpa.open-in-view=false` 时，Service 返回后再映射懒加载的 `department/doctor` 会触发懒加载异常。
- 修复：为 `ScheduleSlotQueryService.list`、`DoctorQueryService.list` 和 `DoctorQueryService.getDetail` 增加 `@Transactional(readOnly = true)`。
- 修复后结果：`/schedule-slots` 返回 `200`，可正常返回号源列表。

- 问题：`appointment_operation_log.detail_json` 使用 PostgreSQL `jsonb` 时，Hibernate schema validate 认为字段类型应为 `varchar`。
- 修复：在实体字段上补充 `@JdbcTypeCode(SqlTypes.JSON)` 和 `@ColumnTransformer(write = "?::jsonb")`。
- 修复后结果：后端可正常启动，Flyway 迁移到 v5 后 JPA 校验通过。

## 阶段产出结论
- Phase 3 已达到计划中的最小可运行目标。
- 当前系统已经具备真实业务闭环：
  - 用户登录
  - 查询号源
  - 创建预约
  - 取消预约
  - 号源扣减与回补
  - 我的预约列表和详情

## 已知遗留项
- 种子数据中文存在历史编码乱码，当前不影响业务闭环，但后续建议统一修正迁移脚本编码和种子文案。
- `health` 接口返回中的 `phase` 仍显示 `PHASE_0`，后续建议改为配置驱动或随阶段更新。
- 当前预约取消规则较简单，尚未加入“就诊前多久不可取消”等业务限制。
- 前端预约页是联调用页面，已能跑通闭环，但还不是最终用户体验版。

## 下一阶段建议
- 进入 Phase 4 对话基础能力。
- 优先实现：
  - 会话创建
  - 消息落库
  - 基础 SSE 流式返回
  - 前端对话页流式渲染
  - 为后续 Agent Tool 调用预约能力预留一致的服务入口
