package dev.workflowcrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

abstract class ObjectReassignmentTestSupport extends ObjectEditTestSupport {
    ResultActions reassign(UUID actor, UUID object, UUID target) throws Exception {
        return reassign(actor, object, Map.of("managerId", target, "version", version(object)));
    }

    ResultActions reassign(UUID actor, UUID object, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/objects/{id}/reassign", object)
                .cookie(cookie(actor), csrf)
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)));
    }

    UUID task(UUID object, UUID assignee, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into tasks(id,object_id,source_status_id,assignee_id,name,status,deadline,author_id)
                select ?,?,id,?,'Задача об’єкта',?,'2026-10-15',? from object_statuses where code='FOUND'
                """,
                id,
                object,
                assignee,
                status,
                manager);
        return id;
    }

    UUID owner(UUID object) {
        return jdbc.queryForObject("select manager_id from objects where id=?", UUID.class, object);
    }

    UUID assignee(UUID task) {
        return jdbc.queryForObject("select assignee_id from tasks where id=?", UUID.class, task);
    }

    boolean transferred(UUID task) {
        return jdbc.queryForObject("select transferred_from_predecessor from tasks where id=?", Boolean.class, task);
    }

    String taskSnapshot(UUID task) {
        return jdbc.queryForObject(
                "select (to_jsonb(t)-'assignee_id'-'version'-'updated_at'-'transferred_from_predecessor')::text from tasks t where id=?",
                String.class,
                task);
    }
}
