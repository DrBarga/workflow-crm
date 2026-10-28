UPDATE object_status_transitions t
SET unfinished_task_action = b.previous_action, version = t.version + 1, updated_at = CURRENT_TIMESTAMP
FROM object_refusal_task_policy_backup b
WHERE t.id = b.transition_id AND t.unfinished_task_action = 'CANCEL_ALL'
  AND t.version = b.applied_version;

DROP TABLE object_refusal_task_policy_backup;
