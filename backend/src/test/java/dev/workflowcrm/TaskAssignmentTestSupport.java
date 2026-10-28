package dev.workflowcrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

abstract class TaskAssignmentTestSupport extends ObjectStatusTasksSupport {
    UUID role(String code) {
        return jdbc.queryForObject("select id from roles where code=?", UUID.class, code);
    }

    UUID roleItem(String target, String rule, String role) {
        UUID template = template(target, null);
        UUID item = jdbc.queryForObject("select id from task_template_items where template_id=?", UUID.class, template);
        jdbc.update(
                "update task_template_items set assignment_rule=?,role_id=?,assignee_id=null where id=?",
                rule,
                role(role),
                item);
        return item;
    }

    JsonNode preview(String target) throws Exception {
        return response(mvc.perform(get("/api/objects/{id}/task-preview", object)
                        .cookie(cookie(manager))
                        .param("status", target))
                .andExpect(status().isOk()));
    }

    ResultActions modal(UUID actor, Map<String, Object> body) throws Exception {
        return send(actor, "/api/objects/" + object + "/task-sets/preview", body, false);
    }

    Map<String, Object> selected(String target, UUID item, UUID assignee) {
        var body = command(target);
        body.put("assignees", Map.of(item.toString(), assignee.toString()));
        return body;
    }

    Map<String, Object> confirm(UUID actor, Map<String, Object> body) throws Exception {
        body.put(
                "confirmationToken",
                response(modal(actor, body).andExpect(status().isOk()))
                        .path("confirmationToken")
                        .asText());
        return body;
    }

    ResultActions submit(UUID actor, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/objects/{id}/task-sets", object)
                .cookie(cookie(actor), csrf)
                .header("X-XSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)));
    }

    UUID generate(String target, UUID actor, Map<String, Object> body) throws Exception {
        change(actor, confirm(actor, body)).andExpect(status().isOk());
        return jdbc.queryForObject("select id from tasks where object_id=?", UUID.class, object);
    }
}
