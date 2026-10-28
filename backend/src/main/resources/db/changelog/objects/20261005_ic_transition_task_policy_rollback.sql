UPDATE object_status_transitions
SET unfinished_task_action = 'CANCEL', version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE code IN ('IC_APPROVED', 'IC_APPROVED_WITH_CONDITIONS', 'IC_REFUSED')
  AND unfinished_task_action <> 'CANCEL';
