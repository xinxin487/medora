# Demo 3: A2UI confirmation, idempotency, and waitlist promotion

## Prepare

Set `FEATURE_A2UI_APPOINTMENT=true`, configure a real `AI_CHAT_API_KEY`, and start the backend with a fast local-only waitlist rebuild schedule for the drill:

```bash
MEDORA_APPOINTMENT_WAITLIST_REBUILD_CRON='*/10 * * * * *' \
  mvn -f backend/pom.xml spring-boot:run
```

In another terminal:

```bash
./scripts/seed-demo.sh
docker compose -f deploy/compose/docker-compose.dev.yml exec -T postgres \
  psql -U postgres -d medora < scripts/reset-demo-data.sql
```

The one-quota slot used below has stable public ID `63000000-0000-0000-0000-000000000002`.

## A2UI hold, countdown, confirmation, and duplicate click

1. Sign in at `http://localhost:3000/login` as `demo.patient@medora.local` using the disposable password in `deploy/README.md`.
2. Open `http://localhost:3000/app/assistant` and send: `请先查询明天下午演示李医生的号源，然后为我准备预约。`
3. Expected before confirmation: an A2UI card shows `确认预约`, fee, and a countdown close to 120 seconds. PostgreSQL has `PENDING_CONFIRMATION`; the slot counters are `available=0, reserved=1, confirmed=0`.
4. Double-click `确认预约` quickly. The browser suppresses an in-flight second request; replaying the same action and `Idempotency-Key` at the API also returns the first appointment.
5. Expected after confirmation: one appointment is `CONFIRMED`, the action is `CONSUMED`, and counters are `available=0, reserved=0, confirmed=1`. No second appointment is created.

Inspect the truth state without exposing the confirmation token:

```bash
docker compose -f deploy/compose/docker-compose.dev.yml exec -T postgres \
  psql -U postgres -d medora -P pager=off -c "
    select slot.available_quota, slot.reserved_quota,
           count(appointment.id) filter (where appointment.status='CONFIRMED') as confirmed,
           count(distinct appointment.id) as appointment_rows
    from schedule_slot slot
    left join appointment on appointment.slot_id=slot.id
    where slot.public_id='63000000-0000-0000-0000-000000000002'
    group by slot.id;"
```

## Join the full-slot waitlist

Create a second disposable local user through the registration form, then log in and copy its access token from browser storage (`medora.access_token`) into `WAITLIST_TOKEN`. Join the full slot:

```bash
curl --fail-with-body -sS http://localhost:8080/api/v1/waitlist \
  -H "Authorization: Bearer $WAITLIST_TOKEN" \
  -H 'Content-Type: application/json' \
  --data '{"slotId":"63000000-0000-0000-0000-000000000002","priority":0}'
```

Expected: response status is `WAITING`; PostgreSQL has one active waitlist row and Redis has a member in `waitlist:{internalSlotId}`.

## Cancel and promote

1. Sign back in as the first demo patient, open `http://localhost:3000/app/services`, and cancel the confirmed appointment.
2. `AppointmentCommandService` commits `CANCELLED` and then publishes `SlotReleased`.
3. `WaitlistPromotionHandler` pops the first valid ZSet member, creates a new hold, and moves the candidate to `OFFERED`.
4. Expected counters after promotion: `available=0, reserved=1, confirmed=0`; candidate state is `OFFERED`; hold is `PENDING_CONFIRMATION`.
5. Sign in as the waitlist user and open any conversation in `/app/assistant`. `GET /api/v1/a2ui/surfaces?conversationId={conversationId}` restores the candidate's confirmation card. Confirming moves waitlist and appointment to `CONFIRMED`.

## Redis loss and rebuild drill

Use only the local demo Redis database. Delete waitlist acceleration keys, not PostgreSQL truth:

```bash
docker compose -f deploy/compose/docker-compose.dev.yml exec -T redis sh -c \
  'for key in $(redis-cli --raw --scan --pattern "waitlist:*"); do redis-cli DEL "$key"; done'
```

Before the next rebuild tick, PostgreSQL still shows `WAITING` rows and Redis has no matching keys. Within 10 seconds, `WaitlistRebuildJob` recreates ZSet entries from `WAITING` rows. Verify both sides:

```bash
docker compose -f deploy/compose/docker-compose.dev.yml exec -T postgres \
  psql -U postgres -d medora -Atc "select count(*) from waitlist_entry where status='WAITING';"
docker compose -f deploy/compose/docker-compose.dev.yml exec -T redis \
  redis-cli --raw --scan --pattern 'waitlist:*'
```

This drill proves Redis is rebuildable acceleration; it never changes appointment or inventory truth.
