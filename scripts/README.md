# Scripts

在 macOS 上，从项目根目录分别启动前后端（需要先按根目录 README 启动 Docker 基础设施，并配置 `.env`）：

```bash
./scripts/dev/start-backend.sh
./scripts/dev/start-frontend.sh
```

两个脚本互不依赖，可分开执行，也可以从其他工作目录执行。它们在后台运行服务，不会把服务日志打印到终端；日志分别追加到 `logs/backend-dev.log` 和 `logs/frontend-dev.log`。可用 `tail -f logs/backend-dev.log` 或 `tail -f logs/frontend-dev.log` 查看实时日志。PID 记录在同目录的 `*-dev.pid` 文件中。脚本会检查已有 PID 和端口占用，不会在已运行的服务旁再启动一份。
