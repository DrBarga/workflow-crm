package dev.workflowcrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

abstract class ObjectStatusTestSupport extends ObjectApiTestSupport {
    UUID object;
    final UUID director = UUID.fromString("00000000-0000-0000-0671-200000000001"),
            contract = UUID.fromString("00000000-0000-0000-0671-200000000002"),
            admin = UUID.fromString("00000000-0000-0000-0671-200000000003"),
            ceo = UUID.fromString("00000000-0000-0000-0671-200000000004"),
            foreignLead = UUID.fromString("00000000-0000-0000-0671-200000000005");

    @BeforeEach
    void prepareStatus() throws Exception {
        user(director, "DEVELOPMENT_DIRECTOR", null);
        user(contract, "CONTRACT_MANAGER", null);
        user(admin, "SUPERADMIN", null);
        user(ceo, "CEO", null);
        user(foreignLead, "REGIONAL_MANAGER", otherRegion);
        object = UUID.fromString(response(create(manager, key(), body()).andExpect(status().isCreated()))
                .path("id")
                .asText());
        ObjectTestCleanup.erase(jdbc, () -> jdbc.update("delete from object_history"));
    }

    @AfterEach
    void cleanStatusDependencies() {
        for (String table : List.of(
                "status_approval_requests",
                "task_history",
                "tasks",
                "task_sets",
                "task_template_items",
                "task_templates")) jdbc.update("delete from " + table);
    }

    UUID statusId(String code) {
        return jdbc.queryForObject("select id from object_statuses where code=?", UUID.class, code);
    }

    void state(String source) {
        jdbc.update(
                "update objects set status_id=?,status_before_pause_id=?,status_before_refusal_id=? where id=?",
                statusId(source),
                statusId("IN_PROGRESS"),
                statusId("FOUND"),
                object);
    }

    String state() {
        return jdbc.queryForObject(
                "select s.code from objects o join object_statuses s on s.id=o.status_id where o.id=?",
                String.class,
                object);
    }

    long version() {
        return jdbc.queryForObject("select version from objects where id=?", Long.class, object);
    }

    Map<String, Object> command(String target) {
        var body = new LinkedHashMap<String, Object>();
        body.put("status", target);
        body.put("version", version());
        return body;
    }

    ResultActions change(UUID actor, Map<String, Object> body) throws Exception {
        return send(actor, "/api/objects/" + object + "/status", body, false);
    }

    ResultActions milestones(UUID actor, Map<String, Object> body) throws Exception {
        return send(actor, "/api/objects/" + object + "/milestones", body, true);
    }

    ResultActions approval(UUID actor, UUID id, String decision, Map<String, Object> body) throws Exception {
        return send(actor, "/api/objects/" + object + "/status-requests/" + id + "/" + decision, body, false);
    }

    ResultActions send(UUID actor, String path, Map<String, Object> body, boolean partial) throws Exception {
        var request = partial ? patch(path) : post(path);
        return mvc.perform(request.cookie(cookie(actor), csrf)
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)));
    }

    JsonNode response(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    UUID reason() {
        return jdbc.queryForObject("select id from refusal_reasons where not is_system_only limit 1", UUID.class);
    }

    UUID contractType() {
        return jdbc.queryForObject("select id from contract_types where code='PRIMARY'", UUID.class);
    }

    UUID actor(String role) {
        return switch (role) {
            case "MANAGER" -> manager;
            case "REGIONAL_MANAGER" -> lead;
            case "DEVELOPMENT_DIRECTOR" -> director;
            case "CONTRACT_MANAGER" -> contract;
            case "SUPERADMIN" -> admin;
            case "CEO" -> ceo;
            default -> throw new IllegalArgumentException(role);
        };
    }
}
