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
        (SELECT count(*) FROM user_account WHERE lower(email) = 'demo.admin@medora.local' AND status = 'ACTIVE' AND register_source = 'ADMIN'),
        (SELECT count(*) FROM user_profile p JOIN user_account u ON u.id = p.user_id WHERE lower(u.email) = 'demo.admin@medora.local'),
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

expected="1,1,1,2,2,4,1,4,0"
if [[ "$second_counts" != "$expected" ]]; then
  printf 'unexpected fixture counts: expected=%s actual=%s\n' "$expected" "$second_counts" >&2
  exit 1
fi

printf 'seed counts unchanged after second run: %s\n' "$second_counts"

docker compose -f "$COMPOSE_FILE" exec -T postgres \
  psql -X -v ON_ERROR_STOP=1 -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-medora}" <<'SQL'
INSERT INTO user_account (email, password_hash, status, register_source)
VALUES ('reset.scope@medora.local', 'fixture', 'ACTIVE', 'TEST')
ON CONFLICT (lower(email)) WHERE email IS NOT NULL DO UPDATE SET status = EXCLUDED.status;

INSERT INTO appointment (appointment_no, user_id, slot_id, department_id, doctor_id, schedule_date, period_code, status, source_channel, booked_at)
SELECT 'RESET_SCOPE_APPOINTMENT', u.id, s.id, s.department_id, s.doctor_id, s.schedule_date, s.period_code, 'CONFIRMED', 'TEST', NOW()
FROM user_account u CROSS JOIN LATERAL (SELECT * FROM schedule_slot WHERE source_type = 'DEMO_M6' ORDER BY id LIMIT 1) s
WHERE lower(u.email) = 'reset.scope@medora.local'
ON CONFLICT (appointment_no) DO NOTHING;

INSERT INTO appointment_operation_log (appointment_id, operation_type, operator_type, request_id)
SELECT id, 'CREATE', 'SYSTEM', 'reset-scope'
FROM appointment WHERE appointment_no = 'RESET_SCOPE_APPOINTMENT'
ON CONFLICT DO NOTHING;

INSERT INTO appointment_hold (user_id, slot_id, idempotency_key, status, expires_at, confirmed_appointment_id)
SELECT u.id, s.id, 'RESET_SCOPE_HOLD', 'PENDING_CONFIRMATION', NOW() + INTERVAL '10 minutes',
       (SELECT id FROM appointment WHERE appointment_no = 'RESET_SCOPE_APPOINTMENT')
FROM user_account u CROSS JOIN LATERAL (SELECT * FROM schedule_slot WHERE source_type = 'DEMO_M6' ORDER BY id LIMIT 1) s
WHERE lower(u.email) = 'reset.scope@medora.local'
ON CONFLICT (user_id, idempotency_key) DO NOTHING;

INSERT INTO a2ui_action (hold_id, user_id, surface_id, action_type, token_hash, status, expires_at)
SELECT h.id, h.user_id, 'reset-scope', 'CONFIRM', 'reset-scope-token', 'PENDING', NOW() + INTERVAL '10 minutes'
FROM appointment_hold h WHERE h.idempotency_key = 'RESET_SCOPE_HOLD'
ON CONFLICT (token_hash) DO NOTHING;

INSERT INTO waitlist_entry (user_id, slot_id, status, priority, offered_hold_id, joined_at)
SELECT h.user_id, h.slot_id, 'OFFERED', 0, h.id, NOW()
FROM appointment_hold h WHERE h.idempotency_key = 'RESET_SCOPE_HOLD'
ON CONFLICT DO NOTHING;
SQL

docker compose -f "$COMPOSE_FILE" exec -T postgres \
  psql -X -v ON_ERROR_STOP=1 -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-medora}" \
  < "$ROOT_DIR/scripts/reset-demo-data.sql"

reset_counts="$(docker compose -f "$COMPOSE_FILE" exec -T postgres \
  psql -X -v ON_ERROR_STOP=1 -U "${DB_USERNAME:-postgres}" -d "${DB_NAME:-medora}" -Atc "
    SELECT concat_ws(',',
      (SELECT count(*) FROM appointment WHERE slot_id IN (SELECT id FROM schedule_slot WHERE source_type = 'DEMO_M6')),
      (SELECT count(*) FROM appointment_hold WHERE slot_id IN (SELECT id FROM schedule_slot WHERE source_type = 'DEMO_M6')),
      (SELECT count(*) FROM waitlist_entry WHERE slot_id IN (SELECT id FROM schedule_slot WHERE source_type = 'DEMO_M6')),
      (SELECT count(*) FROM a2ui_action WHERE user_id = (SELECT id FROM user_account WHERE lower(email) = 'reset.scope@medora.local')),
      (SELECT count(*) FROM appointment_operation_log WHERE request_id = 'reset-scope'),
      (SELECT count(*) FROM schedule_slot WHERE source_type = 'DEMO_M6' AND (available_quota <> total_quota OR reserved_quota <> 0 OR status <> 'OPEN'))
    );")"

if [[ "$reset_counts" != "0,0,0,0,0,0" ]]; then
  printf 'demo reset is inconsistent: actual=%s\n' "$reset_counts" >&2
  exit 1
fi

printf 'demo reset cleared all DEMO_M6 associations and restored inventory.\n'
