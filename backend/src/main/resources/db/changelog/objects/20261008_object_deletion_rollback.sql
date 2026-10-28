-- Pending operations must finish before downgrading the deletion worker.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM object_deletion_operations WHERE state='PENDING')
    THEN RAISE EXCEPTION 'Finish pending object deletions before rollback'; END IF;
END $$;;
DROP TRIGGER deleting_object_guard ON objects;;
DROP TRIGGER deleting_tasks_guard ON tasks;;
DROP TRIGGER deleting_files_guard ON files;;
DROP TRIGGER deleting_comments_guard ON object_comments;;
DROP TRIGGER deleting_task_comments_guard ON task_comments;;
DROP TRIGGER deleting_contacts_guard ON object_contacts;;
DROP TRIGGER deleting_status_requests_guard ON status_approval_requests;;
DROP TRIGGER deleting_point_requests_guard ON entry_point_approval_requests;;
DROP TRIGGER deleting_history_guard ON object_history;;
DROP TRIGGER deleting_task_history_guard ON task_history;;
DROP TRIGGER deleting_task_assignment_guard ON task_assignment_history;;
DROP FUNCTION protect_deleting_object();;
ALTER TABLE status_approval_requests DROP CONSTRAINT status_approval_requests_object_id_fkey;;
ALTER TABLE status_approval_requests ADD CONSTRAINT status_approval_requests_object_id_fkey FOREIGN KEY(object_id) REFERENCES objects(id) ON DELETE RESTRICT;;
ALTER TABLE entry_point_approval_requests DROP CONSTRAINT entry_point_approval_requests_object_id_fkey;;
ALTER TABLE entry_point_approval_requests ADD CONSTRAINT entry_point_approval_requests_object_id_fkey FOREIGN KEY(object_id) REFERENCES objects(id) ON DELETE RESTRICT;;
-- Creation tombstones, completed operations and audit snapshots survive rollback.
CREATE OR REPLACE FUNCTION archive_deleted_object() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE actor uuid;
BEGIN
    actor := NULLIF(current_setting('workflow.object_deletion_actor',true),'')::uuid;
    IF actor IS NULL OR NOT EXISTS (SELECT 1 FROM users u JOIN roles r ON r.id=u.role_id
        WHERE u.id=actor AND u.status='ACTIVE' AND r.code='SUPERADMIN')
    THEN RAISE EXCEPTION 'Physical object deletion requires an active administrator' USING ERRCODE='42501'; END IF;
    IF EXISTS (SELECT 1 FROM files WHERE object_id=OLD.id OR task_id IN (SELECT id FROM tasks WHERE object_id=OLD.id))
    THEN RAISE EXCEPTION 'Stored files require the object deletion worker' USING ERRCODE='55000'; END IF;
    INSERT INTO object_deletion_audit(object_id,actor_id,object_version,snapshot)
    VALUES(OLD.id,actor,OLD.version,jsonb_build_object('object',to_jsonb(OLD),'contacts',
        COALESCE((SELECT jsonb_agg(to_jsonb(c)) FROM object_contacts oc JOIN contacts c ON c.id=oc.contact_id
                  WHERE oc.object_id=OLD.id),'[]'::jsonb)));
    RETURN OLD;
END $$;;
