#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT_DIR/deploy/compose/docker-compose.dev.yml}"

query_counts() {
  docker compose -f "$COMPOSE_FILE" exec -T postgres \
    psql -X -v ON_ERROR_STOP=1 -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-medora}" \
    -Atc "
      SELECT concat_ws(',',
        (SELECT count(*) FROM user_account WHERE lower(email) = 'demo.patient@medora.local'),
        (SELECT count(*) FROM department WHERE code IN ('RESPIRATORY', 'GENERAL_INTERNAL')),
        (SELECT count(*) FROM doctor WHERE code IN ('DEMO_RESP_01', 'DEMO_GEN_01')),
        (SELECT count(*) FROM schedule_slot WHERE source_type = 'DEMO_M6'),
        (SELECT count(*) FROM knowledge_document WHERE metadata_json->>'fixtureVersion' = 'm6-v1' AND biz_scope = 'GUIDE' AND publish_status = 'PUBLISHED' AND ingest_status = 'INDEXED'),
        (SELECT count(*) FROM knowledge_chunk c JOIN knowledge_document d ON d.id = c.document_id WHERE d.metadata_json->>'fixtureVersion' = 'm6-v1' AND (c.chunk_type = 'PARENT' OR c.parent_chunk_id IS NOT NULL)),
        (SELECT count(*) FROM appointment a JOIN user_account u ON u.id = a.user_id WHERE lower(u.email) = 'demo.patient@medora.local' AND a.status = 'CONFIRMED')
      );"
}

"$ROOT_DIR/scripts/seed-demo.sh"
first_counts="$(query_counts)"
"$ROOT_DIR/scripts/seed-demo.sh"
second_counts="$(query_counts)"

if [[ "$first_counts" != "$second_counts" ]]; then
  printf 'seed is not idempotent: first=%s second=%s\n' "$first_counts" "$second_counts" >&2
  exit 1
fi

expected="1,2,2,4,1,4,0"
if [[ "$second_counts" != "$expected" ]]; then
  printf 'unexpected fixture counts: expected=%s actual=%s\n' "$expected" "$second_counts" >&2
  exit 1
fi

printf 'seed counts unchanged after second run: %s\n' "$second_counts"
