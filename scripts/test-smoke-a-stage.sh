#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

FAKE_BIN="$TMP_DIR/bin"
mkdir -p "$FAKE_BIN"

cat >"$FAKE_BIN/docker" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
args="$*"
case "$args" in
  *" pg_isready "*) printf 'server is accepting connections\n' ;;
  *" redis-cli ping"*) printf 'PONG\n' ;;
  *" psql "*) printf '1\n' ;;
  *) : ;;
esac
SH

cat >"$FAKE_BIN/curl" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

output_file=""
header_file=""
url=""
payload=""
while (($#)); do
  case "$1" in
    --output) output_file="$2"; shift 2 ;;
    --dump-header) header_file="$2"; shift 2 ;;
    --data|--data-raw) payload="$2"; shift 2 ;;
    http://*|https://*) url="$1"; shift ;;
    *) shift ;;
  esac
done

status=200
body='{}'
case "$url" in
  */actuator/health)
    if [[ "${FAKE_BACKEND_DOWN:-false}" == "true" ]]; then
      status=503
      body='{"status":"DOWN"}'
    else
      body='{"status":"UP"}'
    fi
    ;;
  */api/v1/auth/register)
    body='{"code":"SUCCESS","data":{"userId":"fixture"}}'
    ;;
  */api/v1/auth/login)
    body='{"code":"SUCCESS","data":{"accessToken":"fixture-token"}}'
    ;;
  */mcp)
    if [[ "$payload" == *'"method":"initialize"'* ]]; then
      body='{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-11-25"}}'
      [[ -n "$header_file" ]] && printf 'HTTP/1.1 200 OK\r\nMcp-Session-Id: fixture-session\r\n\r\n' >"$header_file"
    elif [[ "$payload" == *'"method":"notifications/initialized"'* ]]; then
      status=202
      body=''
    elif [[ "$payload" == *'"method":"tools/list"'* ]]; then
      body='{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"findDepartment"}]}}'
    fi
    ;;
esac

[[ -z "$output_file" ]] || printf '%s' "$body" >"$output_file"
printf '%s' "$status"
SH

chmod +x "$FAKE_BIN/docker" "$FAKE_BIN/curl"

run_smoke() {
  PATH="$FAKE_BIN:$PATH" \
    DOCKER_BIN=docker \
    CURL_BIN=curl \
    SMOKE_TMP_PARENT="$TMP_DIR" \
    BACKEND_URL=http://backend.test \
    FRONTEND_URL=http://frontend.test \
    MINIO_URL=http://minio.test \
    MCP_URL=http://mcp.test/mcp \
    "$ROOT_DIR/scripts/smoke-a-stage.sh"
}

happy_output="$(run_smoke)"
grep -q 'A-stage smoke checks passed' <<<"$happy_output"

if FAKE_BACKEND_DOWN=true run_smoke >"$TMP_DIR/down.out" 2>"$TMP_DIR/down.err"; then
  printf 'Expected the smoke command to fail when backend health is DOWN.\n' >&2
  exit 1
fi
grep -q 'Backend health check failed' "$TMP_DIR/down.err"

if grep -q 'fixture-token' "$TMP_DIR/down.out" "$TMP_DIR/down.err"; then
  printf 'Smoke output leaked an authentication token.\n' >&2
  exit 1
fi

printf 'Smoke command contract tests passed.\n'
