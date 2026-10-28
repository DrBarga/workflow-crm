ALTER TABLE tasks ADD COLUMN transferred_from_predecessor boolean NOT NULL DEFAULT false;

CREATE TABLE task_assignment_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id uuid NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    actor_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    previous_assignee_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    new_assignee_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    task_version bigint NOT NULL CHECK (task_version > 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (previous_assignee_id <> new_assignee_id),
    UNIQUE (task_id, task_version)
);
CREATE INDEX idx_task_assignment_history_actor ON task_assignment_history(actor_id);
CREATE INDEX idx_task_assignment_history_previous ON task_assignment_history(previous_assignee_id);
CREATE INDEX idx_task_assignment_history_new ON task_assignment_history(new_assignee_id);
CREATE INDEX idx_tasks_open_assignee_object ON tasks(object_id, assignee_id)
    WHERE status NOT IN ('DONE', 'CANCELLED');
