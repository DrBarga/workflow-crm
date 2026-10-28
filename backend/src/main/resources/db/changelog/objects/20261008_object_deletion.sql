CREATE TABLE IF NOT EXISTS object_deletion_operations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    object_id uuid NOT NULL UNIQUE,
    actor_id uuid NOT NULL,
    actor_name varchar(200) NOT NULL,
    state varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','COMPLETED')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error varchar(32),
    next_attempt_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at timestamptz,
    CHECK ((state='COMPLETED')=(completed_at IS NOT NULL))
);;
CREATE INDEX IF NOT EXISTS ix_object_deletion_pending ON object_deletion_operations(next_attempt_at,id) WHERE state='PENDING';;
CREATE TABLE IF NOT EXISTS object_deletion_files (
    operation_id uuid NOT NULL REFERENCES object_deletion_operations(id) ON DELETE CASCADE,
    file_id uuid NOT NULL,
    bucket varchar(63) NOT NULL,
    storage_key varchar(1024) NOT NULL,
    purged boolean NOT NULL DEFAULT false,
    PRIMARY KEY (operation_id,file_id)
);;

ALTER TABLE status_approval_requests DROP CONSTRAINT status_approval_requests_object_id_fkey;;
ALTER TABLE status_approval_requests ADD CONSTRAINT status_approval_requests_object_id_fkey FOREIGN KEY(object_id) REFERENCES objects(id) ON DELETE CASCADE;;
ALTER TABLE entry_point_approval_requests DROP CONSTRAINT entry_point_approval_requests_object_id_fkey;;
ALTER TABLE entry_point_approval_requests ADD CONSTRAINT entry_point_approval_requests_object_id_fkey FOREIGN KEY(object_id) REFERENCES objects(id) ON DELETE CASCADE;;
ALTER TABLE object_creation_requests DROP CONSTRAINT IF EXISTS object_creation_requests_object_id_fkey;;
ALTER TABLE object_duplicate_decisions DROP CONSTRAINT IF EXISTS object_duplicate_decisions_object_id_fkey;;

CREATE OR REPLACE FUNCTION protect_deleting_object() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE owner uuid; active_operation uuid; row_data jsonb; rows jsonb[];
BEGIN
    rows := CASE WHEN TG_OP='UPDATE' THEN ARRAY[to_jsonb(OLD),to_jsonb(NEW)]
                 WHEN TG_OP='DELETE' THEN ARRAY[to_jsonb(OLD)] ELSE ARRAY[to_jsonb(NEW)] END;
    FOREACH row_data IN ARRAY rows LOOP
    IF TG_TABLE_NAME='objects' THEN owner := (row_data->>'id')::uuid;
    ELSIF TG_TABLE_NAME IN ('task_comments','task_history','task_assignment_history') THEN
        SELECT object_id INTO owner FROM tasks WHERE id=(row_data->>'task_id')::uuid;
    ELSE
        owner := (row_data->>'object_id')::uuid;
        IF owner IS NULL AND row_data ? 'task_id' THEN
            SELECT object_id INTO owner FROM tasks WHERE id=(row_data->>'task_id')::uuid;
        END IF;
    END IF;
    PERFORM id FROM objects WHERE id=owner FOR KEY SHARE;
    SELECT id INTO active_operation FROM object_deletion_operations WHERE object_id=owner AND state='PENDING';
    IF active_operation IS NOT NULL
       AND COALESCE(current_setting('workflow.object_deletion_operation',true),'')<>active_operation::text
    THEN RAISE EXCEPTION 'Object deletion is in progress' USING ERRCODE='55000'; END IF;
    END LOOP;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;;

CREATE TRIGGER deleting_object_guard BEFORE UPDATE OR DELETE ON objects FOR EACH ROW EXECUTE FUNCTION protect_deleting_object();;
CREATE TRIGGER deleting_tasks_guard BEFORE INSERT OR UPDATE OR DELETE ON tasks FOR EACH ROW EXECUTE FUNCTION protect_deleting_object();;
CREATE TRIGGER deleting_files_guard BEFORE INSERT OR UPDATE OR DELETE ON files FOR EACH ROW EXECUTE FUNCTION protect_deleting_object();;
CREATE TRIGGER deleting_comments_guard BEFORE INSERT OR UPDATE OR DELETE ON object_comments FOR EACH ROW EXECUTE FUNCTION protect_deleting_object();;
CREATE TRIGGER deleting_task_comments_guard BEFORE INSERT OR UPDATE OR DELETE ON task_comments FOR EACH ROW EXECUTE FUNCTION protect_deleting_object();;
CREATE TRIGGER deleting_contacts_guard BEFORE INSERT OR UPDATE OR DELETE ON object_contacts FOR EACH ROW EXECUTE FUNCTION protect_deleting_object();;
CREATE TRIGGER deleting_status_requests_guard BEFORE INSERT OR UPDATE OR DELETE ON status_approval_requests FOR EACH ROW EXECUTE FUNCTION protect_deleting_object();;
CREATE TRIGGER deleting_point_requests_guard BEFORE INSERT OR UPDATE OR DELETE ON entry_point_approval_requests FOR EACH ROW EXECUTE FUNCTION protect_deleting_object();;
CREATE TRIGGER deleting_history_guard BEFORE INSERT ON object_history FOR EACH ROW EXECUTE FUNCTION protect_deleting_object();;
CREATE TRIGGER deleting_task_history_guard BEFORE INSERT OR UPDATE OR DELETE ON task_history FOR EACH ROW EXECUTE FUNCTION protect_deleting_object();;
CREATE TRIGGER deleting_task_assignment_guard BEFORE INSERT OR UPDATE OR DELETE ON task_assignment_history FOR EACH ROW EXECUTE FUNCTION protect_deleting_object();;

CREATE OR REPLACE FUNCTION protect_deletion_operation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Deletion operations cannot be removed' USING ERRCODE='42501'; END IF;
    IF TG_OP='INSERT' THEN
        IF NOT EXISTS (SELECT 1 FROM users u JOIN roles r ON r.id=u.role_id WHERE u.id=NEW.actor_id AND u.status='ACTIVE' AND r.code='SUPERADMIN')
        THEN RAISE EXCEPTION 'Administrator access required' USING ERRCODE='42501'; END IF;
        IF NEW.state<>'PENDING' OR NEW.attempts<>0 OR NEW.last_error IS NOT NULL
           OR NOT EXISTS (SELECT 1 FROM objects WHERE id=NEW.object_id)
        THEN RAISE EXCEPTION 'Invalid deletion operation' USING ERRCODE='23514'; END IF;
    ELSIF (NEW.id,NEW.object_id,NEW.actor_id,NEW.actor_name,NEW.created_at) IS DISTINCT FROM (OLD.id,OLD.object_id,OLD.actor_id,OLD.actor_name,OLD.created_at)
        OR OLD.state='COMPLETED'
    THEN RAISE EXCEPTION 'Deletion authorization is immutable' USING ERRCODE='42501'; END IF;
    IF NEW.state='COMPLETED' AND (EXISTS (SELECT 1 FROM objects WHERE id=NEW.object_id)
       OR NOT EXISTS (SELECT 1 FROM object_deletion_audit WHERE object_id=NEW.object_id AND actor_id=NEW.actor_id)
       OR EXISTS (SELECT 1 FROM object_deletion_files WHERE operation_id=NEW.id AND NOT purged))
    THEN RAISE EXCEPTION 'Object deletion has not completed' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;;
DROP TRIGGER IF EXISTS deletion_operation_authorization ON object_deletion_operations;;
CREATE TRIGGER deletion_operation_authorization BEFORE INSERT OR UPDATE OR DELETE ON object_deletion_operations FOR EACH ROW EXECUTE FUNCTION protect_deletion_operation();;
DROP TRIGGER IF EXISTS deletion_operation_truncate ON object_deletion_operations;;
CREATE TRIGGER deletion_operation_truncate BEFORE TRUNCATE ON object_deletion_operations FOR EACH STATEMENT EXECUTE FUNCTION protect_object_audit();;

CREATE OR REPLACE FUNCTION archive_deleted_object() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE actor uuid; operation uuid;
BEGIN
    actor := NULLIF(current_setting('workflow.object_deletion_actor',true),'')::uuid;
    operation := NULLIF(current_setting('workflow.object_deletion_operation',true),'')::uuid;
    IF NOT EXISTS (SELECT 1 FROM object_deletion_operations WHERE id=operation AND object_id=OLD.id AND actor_id=actor AND state='PENDING')
       AND NOT EXISTS (SELECT 1 FROM users u JOIN roles r ON r.id=u.role_id WHERE u.id=actor AND u.status='ACTIVE' AND r.code='SUPERADMIN')
    THEN RAISE EXCEPTION 'Physical object deletion requires an authorized administrator' USING ERRCODE='42501'; END IF;
    IF EXISTS (SELECT 1 FROM files f
       WHERE (f.object_id=OLD.id OR f.task_id IN (SELECT id FROM tasks WHERE object_id=OLD.id))
         AND NOT EXISTS (SELECT 1 FROM object_deletion_files p WHERE p.operation_id=operation AND p.file_id=f.id
                         AND p.bucket=f.bucket AND p.storage_key=f.storage_key AND p.purged))
    THEN RAISE EXCEPTION 'Stored files must be purged first' USING ERRCODE='55000'; END IF;
    INSERT INTO object_deletion_audit(object_id,actor_id,object_version,snapshot)
    VALUES (OLD.id,actor,OLD.version,jsonb_build_object(
        'object',to_jsonb(OLD),
        'deletedBy',jsonb_build_object('id',actor,'name',COALESCE((SELECT actor_name FROM object_deletion_operations WHERE id=operation),(SELECT full_name FROM users WHERE id=actor))),
        'contacts',COALESCE((SELECT jsonb_agg(to_jsonb(c)) FROM object_contacts oc JOIN contacts c ON c.id=oc.contact_id WHERE oc.object_id=OLD.id),'[]'::jsonb),
        'tasks',COALESCE((SELECT jsonb_agg(to_jsonb(t)) FROM tasks t WHERE object_id=OLD.id),'[]'::jsonb),
        'files',COALESCE((SELECT jsonb_agg(to_jsonb(f)) FROM files f WHERE f.object_id=OLD.id OR f.task_id IN (SELECT id FROM tasks WHERE object_id=OLD.id)),'[]'::jsonb),
        'comments',COALESCE((SELECT jsonb_agg(to_jsonb(c)) FROM object_comments c WHERE object_id=OLD.id),'[]'::jsonb),
        'history',COALESCE((SELECT jsonb_agg(to_jsonb(h) ORDER BY sequence) FROM object_history h WHERE object_id=OLD.id),'[]'::jsonb),
        'taskComments',COALESCE((SELECT jsonb_agg(to_jsonb(c)) FROM task_comments c WHERE task_id IN (SELECT id FROM tasks WHERE object_id=OLD.id)),'[]'::jsonb),
        'taskHistory',COALESCE((SELECT jsonb_agg(to_jsonb(h)) FROM task_history h WHERE task_id IN (SELECT id FROM tasks WHERE object_id=OLD.id)),'[]'::jsonb),
        'taskAssignments',COALESCE((SELECT jsonb_agg(to_jsonb(h)) FROM task_assignment_history h WHERE task_id IN (SELECT id FROM tasks WHERE object_id=OLD.id)),'[]'::jsonb),
        'statusRequests',COALESCE((SELECT jsonb_agg(to_jsonb(r)) FROM status_approval_requests r WHERE object_id=OLD.id),'[]'::jsonb),
        'entryPointRequests',COALESCE((SELECT jsonb_agg(to_jsonb(r)) FROM entry_point_approval_requests r WHERE object_id=OLD.id),'[]'::jsonb)));
    DELETE FROM user_notifications WHERE object_id=OLD.id OR task_id IN (SELECT id FROM tasks WHERE object_id=OLD.id);
    DELETE FROM event_outbox WHERE aggregate_id=OLD.id OR aggregate_id IN (SELECT id FROM tasks WHERE object_id=OLD.id);
    RETURN OLD;
END $$;;
