CREATE TABLE object_refusal_task_policy_backup (
    transition_id uuid PRIMARY KEY REFERENCES object_status_transitions(id) ON DELETE CASCADE,
    previous_action varchar(16) NOT NULL CHECK (previous_action IN ('BLOCK', 'CANCEL', 'CANCEL_ALL', 'NONE')),
    applied_version bigint NOT NULL CHECK (applied_version >= 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO object_refusal_task_policy_backup(transition_id, previous_action, applied_version)
SELECT id, unfinished_task_action, version + 1
FROM object_status_transitions
WHERE is_active AND is_refusal_entry AND NOT is_system_only
  AND unfinished_task_action <> 'CANCEL_ALL';

UPDATE object_status_transitions t
SET unfinished_task_action = 'CANCEL_ALL', version = t.version + 1, updated_at = CURRENT_TIMESTAMP
FROM object_refusal_task_policy_backup b
WHERE t.id = b.transition_id;
