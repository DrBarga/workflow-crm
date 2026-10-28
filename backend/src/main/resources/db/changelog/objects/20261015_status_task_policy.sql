CREATE TABLE object_status_task_policy_backup (
    transition_id uuid PRIMARY KEY REFERENCES object_status_transitions(id) ON DELETE CASCADE,
    previous_action varchar(16) NOT NULL,
    applied_version bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO object_status_task_policy_backup(transition_id, previous_action, applied_version)
SELECT id, unfinished_task_action, version + 1 FROM object_status_transitions
WHERE is_active AND code IN (
    'MANAGER_FOUND_TO_IN_PROGRESS','LEAD_FOUND_TO_IC','LEAD_IN_PROGRESS_TO_IC',
    'REQUEST_FOUND_TO_IC','REQUEST_IN_PROGRESS_TO_IC','REQUEST_CONTRACT_APPROVAL','SIGN_CONTRACT','OPEN_OBJECT',
    'MANAGER_IN_PROGRESS_TO_FOUND','LEAD_IN_PROGRESS_TO_FOUND',
    'MANAGER_PAUSED_TO_FOUND','LEAD_PAUSED_TO_FOUND','IC_REFUSED');

UPDATE object_status_transitions t
SET unfinished_task_action=CASE WHEN t.code IN ('MANAGER_IN_PROGRESS_TO_FOUND',
        'LEAD_IN_PROGRESS_TO_FOUND','MANAGER_PAUSED_TO_FOUND','LEAD_PAUSED_TO_FOUND','IC_REFUSED')
        THEN 'CANCEL_ALL' ELSE 'NONE' END,
    version=t.version+1, updated_at=CURRENT_TIMESTAMP
FROM object_status_task_policy_backup b WHERE t.id=b.transition_id;
