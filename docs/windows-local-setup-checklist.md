# Windows 本地环境安装与验证清单

本文档用于在 Windows 环境下为 Medora 项目准备本地开发与编译验证环境。

当前适用范围：

- 后端：Spring Boot 3.3.x
- 构建工具：Maven
- JDK：21
- 数据库：PostgreSQL
- 前端：Next.js 15 + Node.js

## 1. 目标

完成本文档后，你应该能够在本机完成以下动作：

- 运行后端编译命令
- 启动后端服务并执行 Flyway migration
- 启动前端开发服务
- 访问健康检查接口
- 为后续 Phase 1 及后续阶段提供稳定的本地开发环境

## 2. 推荐安装项

建议至少安装以下软件：

| 组件 | 推荐版本 | 是否必需 | 用途 |
|------|----------|----------|------|
| JDK | 21 | 是 | 后端编译与运行 |
| Maven | 3.9+ | 是 | 后端构建 |
| PostgreSQL | 15 或 16 | 是 | 本地数据库 |
| Node.js | 20 LTS | 建议 | 前端开发与构建 |
| Git | 最新稳定版 | 建议 | 版本管理 |
| VS Code / IntelliJ IDEA | 自选 | 建议 | 开发与调试 |

## 3. 安装顺序

建议按以下顺序准备环境：

1. 安装 JDK 21
2. 安装 Maven
3. 安装 PostgreSQL
4. 安装 Node.js
5. 配置项目环境变量
6. 验证后端编译
7. 验证后端启动
8. 验证前端启动

## 4. JDK 21 安装与验证

### 4.1 安装要求

Medora 后端在 [backend/pom.xml](/D:/BOS/Medora/backend/pom.xml) 中固定使用 `Java 21`。如果本机不是 Java 21，后续编译和运行都可能失败。

### 4.2 安装后需要确认

- 已正确设置 `JAVA_HOME`
- `Path` 中可以找到 `java`
- 命令行可以输出正确版本

### 4.3 验证命令

```powershell
java -version
```

期望结果：

- 命令可以执行
- 输出中包含 `21`

### 4.4 常见问题

`java` 命令不存在：

- 原因：未加入 `Path`
- 处理：重新检查 JDK 安装与环境变量

输出版本不是 21：

- 原因：机器上存在旧版 Java 且优先级更高
- 处理：调整 `JAVA_HOME` 与 `Path` 顺序

## 5. Maven 安装与验证

### 5.1 安装要求

当前项目后端使用 Maven 构建。建议使用 `Maven 3.9+`。

### 5.2 验证命令

```powershell
mvn -version
```

期望结果：

- 命令可以执行
- 输出中包含 Maven 版本信息
- 输出中显示 Java 运行时为 21

### 5.3 常见问题

`mvn` 命令不存在：

- 原因：Maven 未安装或未加入 `Path`
- 处理：检查 Maven 安装目录并补充到环境变量

Maven 使用的 Java 不是 21：

- 原因：Maven 跟随系统默认 Java
- 处理：修正 `JAVA_HOME`

## 6. PostgreSQL 安装与验证

### 6.1 安装要求

当前后端默认连接 PostgreSQL，配置位于 [backend/src/main/resources/application.yml](/D:/BOS/Medora/backend/src/main/resources/application.yml)。

项目当前默认数据库连接参数来自 [.env.example](/D:/BOS/Medora/.env.example)：

```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=medora
DB_USERNAME=postgres
DB_PASSWORD=postgres
```

### 6.2 本地数据库准备建议

安装 PostgreSQL 后，请至少完成以下动作：

1. 启动 PostgreSQL 服务
2. 创建数据库 `medora`
3. 准备一个可用账号，默认可先使用 `postgres`
4. 确认该账号拥有建表权限

### 6.3 验证命令

如果本机安装了 `psql`：

```powershell
psql --version
```

连接数据库示例：

```powershell
psql -h localhost -p 5432 -U postgres -d medora
```

### 6.4 常见问题

连接被拒绝：

- 原因：数据库服务未启动
- 处理：检查 PostgreSQL 服务状态

数据库不存在：

- 原因：尚未创建 `medora`
- 处理：手动创建数据库

认证失败：

- 原因：用户名或密码不正确
- 处理：检查本地数据库账号密码，并同步到项目环境变量

## 7. Node.js 安装与验证

### 7.1 安装建议

前端当前使用 Next.js，建议安装 `Node.js 20 LTS`。

### 7.2 验证命令

```powershell
node -v
npm -v
```

期望结果：

- 两个命令都可以执行
- `node` 为可用版本
- `npm` 可正常工作

## 8. 项目环境变量准备

### 8.1 配置方式

项目根目录已提供模板文件 [.env.example](/D:/BOS/Medora/.env.example)。

建议在项目根目录创建 `.env`，至少填写以下内容：

```env
SPRING_PROFILES_ACTIVE=dev
BACKEND_PORT=8080
FRONTEND_PORT=3000

DB_HOST=localhost
DB_PORT=5432
DB_NAME=medora
DB_USERNAME=postgres
DB_PASSWORD=你的实际密码

NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
NEXT_PUBLIC_APP_NAME=Medora
```

### 8.2 当前阶段哪些变量必须有

后端本地启动至少需要：

- `SPRING_PROFILES_ACTIVE`
- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`
- `BACKEND_PORT`

前端本地启动建议同时准备：

- `FRONTEND_PORT`
- `NEXT_PUBLIC_API_BASE_URL`
- `NEXT_PUBLIC_APP_NAME`

## 9. 后端编译验证

进入后端目录后执行：

```powershell
cd D:\BOS\Medora\backend
mvn clean compile
```

验证通过标准：

- Maven 可以拉取依赖
- 编译成功结束
- 没有 `JAVA_HOME`、`mvn`、语法错误类报错

如果首次编译较慢，通常是 Maven 正在下载依赖，属于正常现象。

## 10. 后端启动与 migration 验证

当前后端启用了 Flyway 和 JPA 结构校验，因此启动验证是必须步骤。

启动命令：

```powershell
cd D:\BOS\Medora\backend
mvn spring-boot:run
```

验证通过标准：

- 服务启动成功
- Flyway migration 执行成功
- 数据库表被自动创建
- 应用监听在 `8080` 端口

启动成功后访问：

```text
http://localhost:8080/actuator/health
```

如果返回健康状态，说明后端基础环境已经具备。

## 11. 前端安装与启动验证

进入前端目录后执行：

```powershell
cd D:\BOS\Medora\frontend
npm install
npm run dev
```

验证通过标准：

- 依赖安装成功
- 开发服务正常启动
- 可以访问：

```text
http://localhost:3000
```

## 12. 当前阶段建议的完整验证顺序

建议按下面顺序执行一次完整本地检查：

1. `java -version`
2. `mvn -version`
3. `node -v`
4. `npm -v`
5. 确认 PostgreSQL 服务已启动
6. 确认 `medora` 数据库已创建
7. 在项目根目录准备 `.env`
8. 执行后端 `mvn clean compile`
9. 执行后端 `mvn spring-boot:run`
10. 访问 `http://localhost:8080/actuator/health`
11. 执行前端 `npm install`
12. 执行前端 `npm run dev`
13. 访问 `http://localhost:3000`

## 13. 故障排查清单

### 13.1 `mvn` 不存在

排查方向：

- Maven 是否安装
- Maven `bin` 是否加入 `Path`
- PowerShell 是否重新打开

### 13.2 编译时报 Java 版本错误

排查方向：

- `java -version` 是否为 21
- `mvn -version` 显示的 Java 是否为 21
- `JAVA_HOME` 是否指向 JDK 21

### 13.3 后端启动时报数据库连接错误

排查方向：

- PostgreSQL 服务是否启动
- `.env` 中数据库地址是否正确
- 用户名密码是否正确
- `medora` 数据库是否已创建

### 13.4 后端启动时报 Flyway 或表结构错误

排查方向：

- 本地数据库里是否已有旧表结构
- 当前 migration 是否已被部分执行
- 是否使用了错误数据库

如果本地数据库已经被错误初始化，建议新建一个干净数据库重新验证，不要直接手动改表。

### 13.5 前端启动失败

排查方向：

- Node.js 版本是否过低
- `npm install` 是否完整执行
- 端口 `3000` 是否被占用

## 14. 对当前仓库的补充建议

为了后续多人协作与稳定开发，建议后续补充以下内容：

1. 为后端加入 Maven Wrapper，即 `mvnw` 和 `mvnw.cmd`
2. 提供本地数据库初始化脚本或 `docker compose` 开发环境
3. 增加一个 `docs/local-runbook.md`，统一记录启动、调试、联调方式
4. 在 CI 中加入最小编译校验

## 15. 完成标准

满足以下条件，可视为 Windows 本地环境准备完成：

- `java -version` 正常
- `mvn -version` 正常
- PostgreSQL 可连接
- `.env` 已配置
- `mvn clean compile` 成功
- `mvn spring-boot:run` 成功
- `http://localhost:8080/actuator/health` 可访问
- `npm install` 成功
- `npm run dev` 成功
- `http://localhost:3000` 可访问

完成以上步骤后，就可以继续进入后续 Phase 1 和 Phase 2 的编码、联调与自测工作。
