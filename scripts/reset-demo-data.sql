BEGIN;

UPDATE appointment
SET hold_id = NULL
WHERE user_id = (SELECT id FROM user_account WHERE lower(email) = 'demo.patient@medora.local');

UPDATE appointment_hold
SET confirmed_appointment_id = NULL
WHERE user_id = (SELECT id FROM user_account WHERE lower(email) = 'demo.patient@medora.local');

DELETE FROM appointment_operation_log
WHERE appointment_id IN (
  SELECT id FROM appointment
  WHERE user_id = (SELECT id FROM user_account WHERE lower(email) = 'demo.patient@medora.local')
);
DELETE FROM a2ui_action
WHERE user_id = (SELECT id FROM user_account WHERE lower(email) = 'demo.patient@medora.local');
DELETE FROM waitlist_entry
WHERE user_id = (SELECT id FROM user_account WHERE lower(email) = 'demo.patient@medora.local');
DELETE FROM appointment
WHERE user_id = (SELECT id FROM user_account WHERE lower(email) = 'demo.patient@medora.local');
DELETE FROM appointment_hold
WHERE user_id = (SELECT id FROM user_account WHERE lower(email) = 'demo.patient@medora.local');

UPDATE schedule_slot
SET available_quota = total_quota,
    reserved_quota = 0,
    status = 'OPEN',
    updated_at = NOW()
WHERE source_type = 'DEMO_M6';

COMMIT;
