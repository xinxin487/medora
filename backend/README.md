# Backend

这是 Medora 的后端工程骨架，当前处于 `Phase 0：工程初始化`。

## 当前包含

- Spring Boot 基础工程
- Actuator 健康检查
- 一个最小业务健康检查接口
- 基础 `application.yml`

## 当前不包含

- 数据库接入
- 鉴权
- 业务模块
- Agent / RAG / Memory 代码

## 启动

```bash
mvn spring-boot:run
```

## 可用接口

- `GET /actuator/health`
- `GET /api/v1/health`

## 下一步

- 接入数据库配置
- 增加 migration
- 初始化主数据模块

