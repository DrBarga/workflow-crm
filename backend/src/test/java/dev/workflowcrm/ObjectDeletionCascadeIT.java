package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class ObjectDeletionCascadeIT extends ObjectDeletionTestSupport {
    @Test
    void deletesDependentsPreservesSharedContactsAndArchivesFullSnapshot() throws Exception {
        UUID object = add("Complete snapshot", manager, region), other = add("Keep", manager, region);
        UUID task = task(object, "NEW");
        task(object, "DONE");
        task(object, "CANCELLED");
        task(other, "NEW");
        UUID contact = UUID.randomUUID();
        jdbc.update("insert into contacts(id,name,role,phone) values(?,'Shared','Owner','+380991234567')", contact);
        jdbc.update(
                "insert into object_contacts(object_id,contact_id) values(?,?),(?,?)", object, contact, other, contact);
        jdbc.update(
                "insert into object_comments(object_id,author_id,body) values(?,?,'object comment')", object, manager);
        jdbc.update("insert into task_comments(task_id,author_id,body) values(?,?,'task comment')", task, manager);
        jdbc.update(
                "insert into task_history(task_id,actor_id,actor_type,previous_status,new_status) values(?,?,'USER','NEW','IN_PROGRESS')",
                task,
                manager);
        jdbc.update(
                "insert into task_assignment_history(task_id,actor_id,previous_assignee_id,new_assignee_id,task_version) values(?,?,?,?,1)",
                task,
                lead,
                manager,
                colleague);
        jdbc.update(
                "insert into object_history(object_id,author_id,actor_type,field_name,previous_value,new_value,object_version) values(?,?,'USER','name','old','new',0)",
                object,
                manager);
        jdbc.update(
                "insert into entry_point_approval_requests(object_id,initiator_id,confirming_user_id,longitude,latitude,object_version) values(?,?,?,30,50,0)",
                object,
                manager,
                lead);
        jdbc.update(
                "insert into status_approval_requests(object_id,initiator_id,confirming_role_id,transition_id,object_version,command) "
                        + "select ?,?,r.id,t.id,0,'{}' from roles r cross join object_status_transitions t where r.code='REGIONAL_MANAGER' limit 1",
                object,
                manager);
        jdbc.update(
                "insert into user_notifications(recipient_id,event_key,event_type,object_id,task_id) values(?,'delete-me','TEST',?,?),(?,'keep-me','TEST',?,null)",
                manager,
                object,
                task,
                manager,
                other);
        jdbc.update(
                "insert into event_outbox(event_key,event_type,aggregate_id,payload) values('delete-me','TEST',?,'{}'),('keep-me','TEST',?,'{}')",
                object,
                other);
        String bucket = bucket();
        file(object, null, bucket, "object");
        file(null, task, bucket, "task");
        file(other, null, bucket, "keep");
        accepted(object);
        processor.processNext();
        assertThat(count("objects")).isEqualTo(1);
        assertThat(count("tasks")).isEqualTo(1);
        assertThat(count("files")).isEqualTo(1);
        assertThat(count("contacts")).isEqualTo(1);
        assertThat(count("object_contacts")).isEqualTo(1);
        for (String table : List.of(
                "object_comments",
                "task_comments",
                "object_history",
                "task_history",
                "task_assignment_history",
                "status_approval_requests",
                "entry_point_approval_requests"))
            assertThat(count(table)).as(table).isZero();
        assertThat(count("user_notifications")).isEqualTo(1);
        assertThat(count("event_outbox")).isEqualTo(1);
        assertThat(stored(bucket)).isEqualTo(1);
        var audit = jdbc.queryForMap("select * from object_deletion_audit where object_id=?", object);
        assertThat(audit.get("actor_id")).isEqualTo(admin);
        assertThat(audit.get("created_at")).isNotNull();
        var snapshot = json.readTree(audit.get("snapshot").toString());
        assertThat(snapshot.path("object").path("name").asText()).isEqualTo("Complete snapshot");
        assertThat(snapshot.path("object").path("commercial_terms").asText()).isEqualTo("Умови");
        assertThat(snapshot.path("deletedBy").path("id").asText()).isEqualTo(admin.toString());
        assertThat(snapshot.path("tasks").size()).isEqualTo(3);
        assertThat(snapshot.path("files").size()).isEqualTo(2);
        for (String field : List.of(
                "contacts",
                "comments",
                "taskComments",
                "taskHistory",
                "taskAssignments",
                "statusRequests",
                "entryPointRequests"))
            assertThat(snapshot.path(field).size()).as(field).isEqualTo(1);
        assertThat(snapshot.path("history").size()).isEqualTo(3);
        for (String sql : List.of(
                "delete from object_deletion_audit",
                "update object_deletion_audit set snapshot='{}'",
                "truncate object_deletion_audit"))
            assertThatThrownBy(() -> jdbc.execute(sql)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void acceptanceAndCompletionAuthorizationCannotBeForged() throws Exception {
        UUID object = add("Authorization", manager, region);
        assertThatThrownBy(() -> jdbc.update(
                        "insert into object_deletion_operations(object_id,actor_id,actor_name) values(?,?,'User')",
                        object,
                        manager))
                .isInstanceOf(DataAccessException.class);
        UUID operation = UUID.fromString(accepted(object).path("id").asText());
        assertThatThrownBy(() ->
                        jdbc.update("update object_deletion_operations set actor_id=? where id=?", manager, operation))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "update object_deletion_operations set state='COMPLETED',completed_at=clock_timestamp() where id=?",
                        operation))
                .isInstanceOf(DataAccessException.class);
        processor.processNext();
        assertThatThrownBy(() -> jdbc.update(
                        "update object_deletion_operations set state='PENDING',completed_at=null where id=?",
                        operation))
                .isInstanceOf(DataAccessException.class);
    }
}
