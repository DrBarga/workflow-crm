DROP INDEX idx_tasks_open_assignee_object;
DROP TABLE task_assignment_history;
ALTER TABLE tasks DROP COLUMN transferred_from_predecessor;
