#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT_DIR/deploy/compose/docker-compose.dev.yml}"
DOCKER_BIN="${DOCKER_BIN:-docker}"
CURL_BIN="${CURL_BIN:-curl}"
NODE_BIN="${NODE_BIN:-node}"
REQUEST_TIMEOUT_SECONDS="${SMOKE_TIMEOUT_SECONDS:-8}"
BACKEND_URL="${BACKEND_URL:-http://localhost:${BACKEND_PORT:-8080}}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:${FRONTEND_PORT:-3000}}"
MINIO_URL="${MINIO_URL:-http://localhost:${MINIO_PORT:-9000}}"
MCP_URL="${MCP_URL:-http://localhost:${HOSPITAL_DIRECTORY_MCP_PORT:-18080}/mcp}"
MCP_TOKEN="${HOSPITAL_DIRECTORY_MCP_TOKEN:-local-demo-hospital-token-change-me}"
MCP_ORIGIN="${MCP_CLIENT_ORIGIN:-https://medora.internal}"
DB_USER="${DB_USERNAME:-postgres}"
DB_DATABASE="${DB_NAME:-medora}"
SMOKE_EMAIL="${SMOKE_EMAIL:-smoke.m6@medora.local}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-MedoraSmoke!2026}"

for command_name in "$DOCKER_BIN" "$CURL_BIN" "$NODE_BIN"; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    printf 'Required command is unavailable: %s\n' "$command_name" >&2
    exit 1
  fi
done

TMP_DIR="$(mktemp -d "${SMOKE_TMP_PARENT:-${TMPDIR:-/tmp}}/medora-smoke.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT

compose() {
  "$DOCKER_BIN" compose -f "$COMPOSE_FILE" "$@"
}

expect_http() {
  local label="$1"
  local expected="$2"
  local status="$3"
  if [[ ! "$status" =~ ^($expected)$ ]]; then
    printf '%s failed (HTTP %s).\n' "$label" "$status" >&2
    exit 1
  fi
}

json_assert() {
  local file="$1"
  local expression="$2"
  "$NODE_BIN" -e '
    const fs = require("fs");
    const raw = fs.readFileSync(process.argv[1], "utf8").trim();
    const dataLines = raw.split(/\r?\n/).filter(line => line.startsWith("data:"));
    const json = JSON.parse(dataLines.length ? dataLines[0].slice(5).trim() : raw);
    const predicate = new Function("json", `return (${process.argv[2]});`);
    if (!predicate(json)) process.exit(1);
  ' "$file" "$expression"
}

printf '[1/7] PostgreSQL + pgvector... '
compose exec -T postgres pg_isready -U "$DB_USER" -d "$DB_DATABASE" >/dev/null
printf 'ok\n'

printf '[2/7] Redis... '
redis_reply="$(compose exec -T redis redis-cli ping | tr -d '\r')"
if [[ "$redis_reply" != "PONG" ]]; then
  printf 'Redis health check failed.\n' >&2
  exit 1
fi
printf 'ok\n'

printf '[3/7] MinIO... '
minio_status="$($CURL_BIN --silent --show-error --max-time "$REQUEST_TIMEOUT_SECONDS" --output /dev/null --write-out '%{http_code}' "$MINIO_URL/minio/health/live")"
expect_http 'MinIO health check' '2[0-9][0-9]' "$minio_status"
printf 'ok\n'

printf '[4/7] Backend health... '
backend_status="$($CURL_BIN --silent --show-error --max-time "$REQUEST_TIMEOUT_SECONDS" --output "$TMP_DIR/backend-health.json" --write-out '%{http_code}' "$BACKEND_URL/actuator/health")"
if [[ ! "$backend_status" =~ ^2[0-9][0-9]$ ]] || ! json_assert "$TMP_DIR/backend-health.json" 'json.status === "UP"'; then
  printf 'Backend health check failed (HTTP %s).\n' "$backend_status" >&2
  exit 1
fi
printf 'ok\n'

printf '[5/7] Frontend HTTP... '
frontend_status="$($CURL_BIN --silent --show-error --max-time "$REQUEST_TIMEOUT_SECONDS" --output /dev/null --write-out '%{http_code}' "$FRONTEND_URL/login")"
expect_http 'Frontend HTTP check' '2[0-9][0-9]|3[0-9][0-9]' "$frontend_status"
printf 'ok\n'

printf '[6/7] Authentication + deterministic knowledge fixture... '
register_status="$($CURL_BIN --silent --show-error --max-time "$REQUEST_TIMEOUT_SECONDS" \
  --header 'Content-Type: application/json' \
  --output "$TMP_DIR/register.json" --write-out '%{http_code}' \
  --data "{\"email\":\"$SMOKE_EMAIL\",\"password\":\"$SMOKE_PASSWORD\",\"displayName\":\"M6 Smoke User\"}" \
  "$BACKEND_URL/api/v1/auth/register")"
expect_http 'Registration check' '200|409' "$register_status"

login_status="$($CURL_BIN --silent --show-error --max-time "$REQUEST_TIMEOUT_SECONDS" \
  --header 'Content-Type: application/json' \
  --output "$TMP_DIR/login.json" --write-out '%{http_code}' \
  --data "{\"account\":\"$SMOKE_EMAIL\",\"password\":\"$SMOKE_PASSWORD\",\"loginType\":\"PASSWORD\"}" \
  "$BACKEND_URL/api/v1/auth/login")"
if [[ ! "$login_status" =~ ^2[0-9][0-9]$ ]] || ! json_assert "$TMP_DIR/login.json" 'json.code === "SUCCESS" && typeof json.data?.accessToken === "string" && json.data.accessToken.length > 0'; then
  printf 'Login check failed (HTTP %s).\n' "$login_status" >&2
  exit 1
fi

knowledge_hits="$(compose exec -T postgres psql -X -U "$DB_USER" -d "$DB_DATABASE" -Atc \
  "select count(*) from knowledge_chunk c join knowledge_document d on d.id=c.document_id where d.public_id='64000000-0000-0000-0000-000000000001' and d.ingest_status='INDEXED' and c.status='ACTIVE' and c.content like '%持续呼吸困难%'")"
if [[ ! "$knowledge_hits" =~ ^[1-9][0-9]*$ ]]; then
  printf 'Deterministic knowledge fixture query returned no results. Run ./scripts/seed-demo.sh.\n' >&2
  exit 1
fi
printf 'ok\n'

printf '[7/7] MCP initialize + tools/list... '
initialize_status="$($CURL_BIN --silent --show-error --max-time "$REQUEST_TIMEOUT_SECONDS" \
  --dump-header "$TMP_DIR/mcp-headers.txt" --output "$TMP_DIR/mcp-initialize.json" --write-out '%{http_code}' \
  --header "Authorization: Bearer $MCP_TOKEN" \
  --header "Origin: $MCP_ORIGIN" \
  --header 'Content-Type: application/json' \
  --header 'Accept: application/json, text/event-stream' \
  --data '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"medora-smoke","version":"1"}}}' \
  "$MCP_URL")"
expect_http 'MCP initialize' '2[0-9][0-9]' "$initialize_status"

session_id="$(awk 'BEGIN{IGNORECASE=1} /^Mcp-Session-Id:/ {gsub("\\r", "", $2); print $2}' "$TMP_DIR/mcp-headers.txt" | tail -1)"
if [[ -z "$session_id" ]]; then
  printf 'MCP initialize did not return a session id.\n' >&2
  exit 1
fi

initialized_status="$($CURL_BIN --silent --show-error --max-time "$REQUEST_TIMEOUT_SECONDS" \
  --output /dev/null --write-out '%{http_code}' \
  --header "Authorization: Bearer $MCP_TOKEN" \
  --header "Origin: $MCP_ORIGIN" \
  --header "Mcp-Session-Id: $session_id" \
  --header 'Content-Type: application/json' \
  --header 'Accept: application/json, text/event-stream' \
  --data '{"jsonrpc":"2.0","method":"notifications/initialized"}' \
  "$MCP_URL")"
expect_http 'MCP initialized notification' '200|202' "$initialized_status"

tools_status="$($CURL_BIN --silent --show-error --max-time "$REQUEST_TIMEOUT_SECONDS" \
  --output "$TMP_DIR/mcp-tools.json" --write-out '%{http_code}' \
  --header "Authorization: Bearer $MCP_TOKEN" \
  --header "Origin: $MCP_ORIGIN" \
  --header "Mcp-Session-Id: $session_id" \
  --header 'Content-Type: application/json' \
  --header 'Accept: application/json, text/event-stream' \
  --data '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}' \
  "$MCP_URL")"
if [[ ! "$tools_status" =~ ^2[0-9][0-9]$ ]] || ! json_assert "$TMP_DIR/mcp-tools.json" 'Array.isArray(json.result?.tools) && json.result.tools.some(tool => tool.name === "findDepartment")'; then
  printf 'MCP tools/list check failed (HTTP %s).\n' "$tools_status" >&2
  exit 1
fi
printf 'ok\n'

printf 'A-stage smoke checks passed. No response bodies or credentials were printed.\n'
