#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="$PROJECT_ROOT/logs"
LOG_FILE="$LOG_DIR/frontend-dev.log"
PID_FILE="$LOG_DIR/frontend-dev.pid"

if [[ ! -f "$PROJECT_ROOT/.env" ]]; then
  echo "缺少 $PROJECT_ROOT/.env，请先配置环境变量。" >&2
  exit 1
fi

set +u
set -a
source "$PROJECT_ROOT/.env"
set +a
set -u

if ! command -v npm > /dev/null 2>&1; then
  echo "找不到 npm，请先安装 Node.js 并加入 PATH。" >&2
  exit 1
fi

mkdir -p "$LOG_DIR"
if [[ -f "$PID_FILE" ]]; then
  read -r existing_pid < "$PID_FILE"
  if [[ "$existing_pid" =~ ^[0-9]+$ ]] && kill -0 "$existing_pid" 2>/dev/null; then
    echo "前端已在运行（PID ${existing_pid}），日志：$LOG_FILE"
    exit 0
  fi
fi
if command -v lsof > /dev/null 2>&1; then
  occupied_pid="$(lsof -nP -tiTCP:3000 -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "$occupied_pid" ]]; then
    echo "3000 端口已被进程 $occupied_pid 占用，前端未启动。" >&2
    exit 1
  fi
fi

(cd "$PROJECT_ROOT/frontend" && exec nohup npm run dev >> "$LOG_FILE" 2>&1 < /dev/null) &
service_pid=$!
printf '%s\n' "$service_pid" > "$PID_FILE"
sleep 1
if ! kill -0 "$service_pid" 2>/dev/null; then
  echo "前端启动失败，请查看日志：$LOG_FILE" >&2
  exit 1
fi

echo "前端已启动（PID ${service_pid}），日志：$LOG_FILE"
