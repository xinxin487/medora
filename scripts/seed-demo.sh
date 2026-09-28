#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT_DIR/deploy/compose/docker-compose.dev.yml}"
DB_USER="${DB_USERNAME:-postgres}"
DB_DATABASE="${DB_NAME:-medora}"

if ! docker compose -f "$COMPOSE_FILE" ps --status running postgres >/dev/null 2>&1; then
  printf 'PostgreSQL is not running. Start it with: docker compose -f %s up -d postgres\n' "$COMPOSE_FILE" >&2
  exit 1
fi

if [[ "$(docker compose -f "$COMPOSE_FILE" exec -T postgres psql -X -U "$DB_USER" -d "$DB_DATABASE" -Atc "select to_regclass('public.flyway_schema_history') is not null")" != "t" ]]; then
  printf 'Database schema is missing. Start the backend once so Flyway can apply migrations, then rerun this script.\n' >&2
  exit 1
fi

docker compose -f "$COMPOSE_FILE" exec -T postgres \
  psql -X -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_DATABASE" <<'SQL'
BEGIN;

INSERT INTO user_account (public_id, email, password_hash, status, register_source)
VALUES (
  '60000000-0000-0000-0000-000000000001',
  'demo.patient@medora.local',
  '$2y$10$uDLEbEcH4yAyptsYKMRTi.FqiJjxlD.zs8ycvae203YZktBLzdgzm',
  'ACTIVE',
  'DEMO'
)
ON CONFLICT (lower(email)) WHERE email IS NOT NULL DO UPDATE
SET status = EXCLUDED.status,
    updated_at = NOW();

INSERT INTO user_profile (user_id, display_name, preference_json)
SELECT id, '作品集演示患者', '{"fixtureVersion":"m6-v1","preferredPeriod":"AM"}'::jsonb
FROM user_account
WHERE lower(email) = 'demo.patient@medora.local'
ON CONFLICT (user_id) DO UPDATE
SET display_name = EXCLUDED.display_name,
    preference_json = EXCLUDED.preference_json,
    updated_at = NOW();

INSERT INTO user_account (public_id, email, password_hash, status, register_source)
VALUES (
  '60000000-0000-0000-0000-000000000002',
  'demo.admin@medora.local',
  '$2y$10$WUbiix2Yfe9HQZ97wxBYrOZSskl8VlQMmqJRVBbBvaDS/x2DOpK4K',
  'ACTIVE',
  'ADMIN'
)
ON CONFLICT (lower(email)) WHERE email IS NOT NULL DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    status = EXCLUDED.status,
    register_source = EXCLUDED.register_source,
    updated_at = NOW();

INSERT INTO user_profile (user_id, display_name, preference_json)
SELECT id, '本机受控管理员', '{"fixtureVersion":"m6-v1","role":"ADMIN"}'::jsonb
FROM user_account
WHERE lower(email) = 'demo.admin@medora.local'
ON CONFLICT (user_id) DO UPDATE
SET display_name = EXCLUDED.display_name,
    preference_json = EXCLUDED.preference_json,
    updated_at = NOW();

INSERT INTO department (public_id, code, name, category, description, tags, status, sort_order)
VALUES
  ('61000000-0000-0000-0000-000000000001', 'RESPIRATORY', '呼吸内科', 'OUTPATIENT', '处理咳嗽、气促等呼吸系统问题。', ARRAY['咳嗽','呼吸困难','哮喘'], 'ACTIVE', 10),
  ('61000000-0000-0000-0000-000000000002', 'GENERAL_INTERNAL', '普通内科', 'OUTPATIENT', '常见内科症状与基础健康咨询。', ARRAY['发热','乏力','全科'], 'ACTIVE', 20)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    tags = EXCLUDED.tags,
    status = EXCLUDED.status,
    updated_at = NOW();

INSERT INTO doctor (public_id, department_id, code, name, title, specialty, intro, tags, status, consultation_fee)
SELECT seed.public_id::uuid, d.id, seed.code, seed.name, seed.title, seed.specialty,
       seed.intro, seed.tags, 'ACTIVE', seed.fee
FROM (VALUES
  ('62000000-0000-0000-0000-000000000001', 'RESPIRATORY', 'DEMO_RESP_01', '演示李医生', '主任医师', '慢性咳嗽、哮喘', '仅用于本地作品集演示。', ARRAY['咳嗽','哮喘']::text[], 20.00::numeric),
  ('62000000-0000-0000-0000-000000000002', 'GENERAL_INTERNAL', 'DEMO_GEN_01', '演示王医生', '副主任医师', '发热、常见内科问题', '仅用于本地作品集演示。', ARRAY['发热','全科']::text[], 15.00::numeric)
) AS seed(public_id, department_code, code, name, title, specialty, intro, tags, fee)
JOIN department d ON d.code = seed.department_code
ON CONFLICT (code) DO UPDATE
SET department_id = EXCLUDED.department_id,
    name = EXCLUDED.name,
    title = EXCLUDED.title,
    specialty = EXCLUDED.specialty,
    intro = EXCLUDED.intro,
    tags = EXCLUDED.tags,
    status = EXCLUDED.status,
    consultation_fee = EXCLUDED.consultation_fee,
    updated_at = NOW();

INSERT INTO schedule_slot (
  public_id, department_id, doctor_id, hospital_code, schedule_date, period_code,
  start_time, end_time, total_quota, available_quota, reserved_quota, fee, status, source_type
)
SELECT seed.public_id::uuid, doc.department_id, doc.id, 'DEMO_HOSPITAL', CURRENT_DATE + seed.day_offset,
       seed.period_code, seed.start_time, seed.end_time, seed.total_quota, seed.total_quota, 0,
       doc.consultation_fee, 'OPEN', 'DEMO_M6'
FROM (VALUES
  ('63000000-0000-0000-0000-000000000001', 'DEMO_RESP_01', 1, 'AM', TIME '09:00', TIME '12:00', 2),
  ('63000000-0000-0000-0000-000000000002', 'DEMO_RESP_01', 1, 'PM', TIME '14:00', TIME '17:00', 1),
  ('63000000-0000-0000-0000-000000000003', 'DEMO_GEN_01', 2, 'AM', TIME '09:00', TIME '12:00', 2),
  ('63000000-0000-0000-0000-000000000004', 'DEMO_GEN_01', 2, 'PM', TIME '14:00', TIME '17:00', 1)
) AS seed(public_id, doctor_code, day_offset, period_code, start_time, end_time, total_quota)
JOIN doctor doc ON doc.code = seed.doctor_code
ON CONFLICT (public_id) DO UPDATE
SET schedule_date = EXCLUDED.schedule_date,
    total_quota = CASE
      WHEN schedule_slot.reserved_quota = 0
       AND NOT EXISTS (SELECT 1 FROM appointment a WHERE a.slot_id = schedule_slot.id AND a.status = 'CONFIRMED')
      THEN EXCLUDED.total_quota ELSE schedule_slot.total_quota END,
    available_quota = CASE
      WHEN schedule_slot.reserved_quota = 0
       AND NOT EXISTS (SELECT 1 FROM appointment a WHERE a.slot_id = schedule_slot.id AND a.status = 'CONFIRMED')
      THEN EXCLUDED.available_quota ELSE schedule_slot.available_quota END,
    fee = EXCLUDED.fee,
    status = CASE WHEN schedule_slot.status = 'CLOSED' THEN schedule_slot.status ELSE 'OPEN' END,
    updated_at = NOW();

INSERT INTO knowledge_document (
  public_id, title, source_type, source_uri, biz_scope, department_tags, doctor_tags,
  version_no, publish_status, ingest_status, content_hash, metadata_json,
  effective_from, effective_to, access_scopes
)
VALUES (
  '64000000-0000-0000-0000-000000000001',
  '呼吸道症状分层就医演示指南',
  'DEMO',
  'fixture://m6/respiratory-guide',
  'GUIDE',
  ARRAY['RESPIRATORY'],
  ARRAY[]::text[],
  1,
  'PUBLISHED',
  'INDEXED',
  encode(digest('medora-m6-respiratory-guide-v1', 'sha256'), 'hex'),
  '{"fixtureVersion":"m6-v1","hospitalCode":"DEMO_HOSPITAL","documentType":"GUIDE","tags":["咳嗽","呼吸困难"]}'::jsonb,
  NOW() - INTERVAL '1 day',
  NOW() + INTERVAL '10 years',
  ARRAY['USER','ADMIN']
)
ON CONFLICT (public_id) DO UPDATE
SET title = EXCLUDED.title,
    biz_scope = EXCLUDED.biz_scope,
    publish_status = EXCLUDED.publish_status,
    ingest_status = EXCLUDED.ingest_status,
    metadata_json = EXCLUDED.metadata_json,
    effective_from = EXCLUDED.effective_from,
    effective_to = EXCLUDED.effective_to,
    access_scopes = EXCLUDED.access_scopes,
    updated_at = NOW();

INSERT INTO knowledge_chunk (
  document_id, chunk_index, section_path, content, embedding_model, metadata_json,
  status, chunk_type, parent_chunk_id, token_count, content_hash, page_number
)
SELECT d.id, seed.chunk_index, seed.section_path, seed.content, 'local-hash-1024',
       jsonb_build_object('fixtureVersion','m6-v1','departmentCode','RESPIRATORY'),
       'ACTIVE', 'PARENT', NULL, seed.token_count,
       encode(digest(seed.content, 'sha256'), 'hex'), seed.page_number
FROM knowledge_document d
CROSS JOIN (VALUES
  (0, '症状判断 > 需要及时就医', '若出现持续呼吸困难、口唇发紫、意识异常或胸痛，应立即寻求急诊医疗帮助。普通咳嗽伴发热可先记录病程并到呼吸内科评估。', 52, 1),
  (2, '就诊准备 > 信息清单', '就诊前记录咳嗽持续时间、体温、诱因、既往哮喘史与当前用药。不要根据本演示资料自行诊断或调整处方药。', 45, 2)
) AS seed(chunk_index, section_path, content, token_count, page_number)
WHERE d.public_id = '64000000-0000-0000-0000-000000000001'
ON CONFLICT (document_id, chunk_index) DO UPDATE
SET section_path = EXCLUDED.section_path,
    content = EXCLUDED.content,
    metadata_json = EXCLUDED.metadata_json,
    status = EXCLUDED.status,
    chunk_type = EXCLUDED.chunk_type,
    parent_chunk_id = NULL,
    token_count = EXCLUDED.token_count,
    content_hash = EXCLUDED.content_hash,
    page_number = EXCLUDED.page_number;

INSERT INTO knowledge_chunk (
  document_id, chunk_index, section_path, content, embedding_model, metadata_json,
  status, chunk_type, parent_chunk_id, token_count, content_hash, page_number
)
SELECT d.id, seed.chunk_index, seed.section_path, seed.content, 'local-hash-1024',
       jsonb_build_object('fixtureVersion','m6-v1','departmentCode','RESPIRATORY'),
       'ACTIVE', 'CHILD', parent.id, seed.token_count,
       encode(digest(seed.content, 'sha256'), 'hex'), seed.page_number
FROM knowledge_document d
JOIN (VALUES
  (1, 0, '症状判断 > 危险信号', '持续呼吸困难并伴口唇发紫或意识异常属于危险信号，应立即就医。', 28, 1),
  (3, 2, '就诊准备 > 病程记录', '记录咳嗽持续时间、体温、诱因、哮喘史和当前用药，有助于医生判断。', 30, 2)
) AS seed(chunk_index, parent_index, section_path, content, token_count, page_number) ON TRUE
JOIN knowledge_chunk parent
  ON parent.document_id = d.id AND parent.chunk_index = seed.parent_index AND parent.chunk_type = 'PARENT'
WHERE d.public_id = '64000000-0000-0000-0000-000000000001'
ON CONFLICT (document_id, chunk_index) DO UPDATE
SET section_path = EXCLUDED.section_path,
    content = EXCLUDED.content,
    metadata_json = EXCLUDED.metadata_json,
    status = EXCLUDED.status,
    chunk_type = EXCLUDED.chunk_type,
    parent_chunk_id = EXCLUDED.parent_chunk_id,
    token_count = EXCLUDED.token_count,
    content_hash = EXCLUDED.content_hash,
    page_number = EXCLUDED.page_number;

COMMIT;
SQL

printf 'Seeded synthetic fixture m6-v1 (demo patient, local admin, 2 doctors, 4 slots, 1 parent-child document).\n'
printf 'Disposable local login: demo.patient@medora.local / MedoraDemo!2026\n'
printf 'Controlled local admin: demo.admin@medora.local / MedoraAdmin!2026\n'
