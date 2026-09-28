#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE_ROOT="$(mktemp -d)"

cleanup() {
  for service in backend frontend; do
    pid_file="$FIXTURE_ROOT/logs/${service}-dev.pid"
    if [[ -f "$pid_file" ]]; then
      read -r service_pid < "$pid_file"
      kill "$service_pid" 2>/dev/null || true
    fi
  done
  rm -rf "$FIXTURE_ROOT"
}
trap cleanup EXIT

mkdir -p "$FIXTURE_ROOT/scripts/dev" "$FIXTURE_ROOT/backend" "$FIXTURE_ROOT/frontend" "$FIXTURE_ROOT/bin"
cp "$PROJECT_ROOT/scripts/dev/start-backend.sh" "$PROJECT_ROOT/scripts/dev/start-frontend.sh" "$FIXTURE_ROOT/scripts/dev/"
printf 'TEST_ENV_VALUE=from_env\n' > "$FIXTURE_ROOT/.env"

for service in backend frontend; do
  command_name=mvn
  log_marker=BACKEND
  if [[ "$service" == frontend ]]; then
    command_name=npm
    log_marker=FRONTEND
  fi
  printf '#!/usr/bin/env bash\nprintf "%s\\n" "%s_LOG_TEST:${TEST_ENV_VALUE}:${PWD}:$*"\nexec sleep 30\n' \
    '%s' "$log_marker" > "$FIXTURE_ROOT/bin/$command_name"
  chmod +x "$FIXTURE_ROOT/bin/$command_name"
done

export PATH="$FIXTURE_ROOT/bin:$PATH"
printf '#!/usr/bin/env bash\nif [[ -n "${TEST_LISTEN_PID:-}" ]]; then\n  printf "%%s\\n" "$TEST_LISTEN_PID"\n  exit 0\nfi\nexit 1\n' > "$FIXTURE_ROOT/bin/lsof"
chmod +x "$FIXTURE_ROOT/bin/lsof"

for service in backend frontend; do
  log_marker=BACKEND
  if [[ "$service" == frontend ]]; then log_marker=FRONTEND; fi
  if TEST_LISTEN_PID=999 bash "$FIXTURE_ROOT/scripts/dev/start-$service.sh" > /dev/null 2>&1; then
    echo "$service ignored an occupied port" >&2
    exit 1
  fi
  [[ ! -f "$FIXTURE_ROOT/logs/${service}-dev.pid" ]] || {
    echo "$service created a PID file despite an occupied port" >&2
    exit 1
  }
  output="$(cd / && bash "$FIXTURE_ROOT/scripts/dev/start-$service.sh")"
  pid_file="$FIXTURE_ROOT/logs/${service}-dev.pid"
  log_file="$FIXTURE_ROOT/logs/${service}-dev.log"
  [[ -f "$pid_file" ]] || { echo "missing $service PID file" >&2; exit 1; }
  read -r service_pid < "$pid_file"
  kill -0 "$service_pid" || { echo "$service is not running" >&2; exit 1; }
  for attempt in {1..30}; do
    if [[ -f "$log_file" ]] && grep -q "${log_marker}_LOG_TEST:from_env:$FIXTURE_ROOT/$service:" "$log_file"; then
      break
    fi
    sleep 0.1
  done
  grep -q "${log_marker}_LOG_TEST:from_env:$FIXTURE_ROOT/$service:" "$log_file" || {
    echo "$service did not write its log with the loaded environment" >&2
    exit 1
  }
  [[ "$output" != *"${log_marker}_LOG_TEST"* ]] || { echo "$service log appeared in terminal" >&2; exit 1; }

  bash "$FIXTURE_ROOT/scripts/dev/start-$service.sh" > /dev/null
  read -r second_pid < "$pid_file"
  [[ "$service_pid" == "$second_pid" ]] || { echo "$service started twice" >&2; exit 1; }
done

echo "dev start scripts: PASS"
