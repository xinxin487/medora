BEGIN;

-- The reset boundary is the fixture slot, not its historical user. Smoke users and
-- other local accounts can create DEMO_M6 records too, so clear every dependent row.
DELETE FROM a2ui_action
WHERE hold_id IN (
  SELECT id FROM appointment_hold
  WHERE slot_id IN (SELECT id FROM schedule_slot WHERE source_type = 'DEMO_M6')
);

DELETE FROM waitlist_entry
WHERE slot_id IN (SELECT id FROM schedule_slot WHERE source_type = 'DEMO_M6');

UPDATE appointment_hold
SET confirmed_appointment_id = NULL
WHERE slot_id IN (SELECT id FROM schedule_slot WHERE source_type = 'DEMO_M6');

DELETE FROM appointment_operation_log
WHERE appointment_id IN (
  SELECT id FROM appointment
  WHERE slot_id IN (SELECT id FROM schedule_slot WHERE source_type = 'DEMO_M6')
);

DELETE FROM appointment
WHERE slot_id IN (SELECT id FROM schedule_slot WHERE source_type = 'DEMO_M6');

DELETE FROM appointment_hold
WHERE slot_id IN (SELECT id FROM schedule_slot WHERE source_type = 'DEMO_M6');

UPDATE schedule_slot
SET available_quota = total_quota,
    reserved_quota = 0,
    status = 'OPEN',
    updated_at = NOW()
WHERE source_type = 'DEMO_M6';

COMMIT;
