DROP TRIGGER immutable_object_history_truncate ON object_history;;
DROP TRIGGER immutable_object_history ON object_history;;
DROP FUNCTION protect_object_history();;
DROP TRIGGER archive_object_before_delete ON objects;;
DROP FUNCTION archive_deleted_object();;
-- Deletion snapshots and their protection survive rollback.
ALTER TABLE object_history DROP CONSTRAINT object_history_object_id_fkey;;
ALTER TABLE object_history ADD CONSTRAINT object_history_object_id_fkey
    FOREIGN KEY (object_id) REFERENCES objects(id) ON DELETE RESTRICT;;
DROP TRIGGER entry_point_approval_history ON entry_point_approval_requests;;
DROP TRIGGER status_approval_history ON status_approval_requests;;
DROP FUNCTION record_object_approval_history();;
DROP INDEX ix_object_history_page;;
DROP INDEX ux_object_history_sequence;;
ALTER TABLE object_history DROP COLUMN sequence;;
