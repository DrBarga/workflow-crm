package dev.workflowcrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

abstract class ObjectEditTestSupport extends ObjectCardTestSupport {
    ResultActions patch(UUID actor, UUID object, Map<String, Object> fields) throws Exception {
        return mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/objects/{id}", object)
                        .cookie(cookie(actor), csrf)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(fields)));
    }

    ResultActions point(UUID actor, UUID object, Map<String, Object> fields) throws Exception {
        return mvc.perform(post("/api/objects/{id}/entry-point", object)
                .cookie(cookie(actor), csrf)
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(fields)));
    }

    ResultActions decide(UUID actor, UUID object, UUID request, String decision) throws Exception {
        return mvc.perform(
                post("/api/objects/{id}/entry-point-requests/{request}/{decision}", object, request, decision)
                        .cookie(cookie(actor), csrf)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"));
    }

    Map<String, Object> change(UUID object, String field, Object value) {
        var body = new LinkedHashMap<String, Object>();
        body.put("version", version(object));
        body.put(field, value);
        return body;
    }

    long version(UUID object) {
        return jdbc.queryForObject("select version from objects where id=?", Long.class, object);
    }

    String state(UUID object) {
        return jdbc.queryForObject(
                "select s.code from objects o join object_statuses s on s.id=o.status_id where o.id=?",
                String.class,
                object);
    }

    JsonNode response(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    Map<String, Object> coordinates() {
        return Map.of("longitude", 30.5234, "latitude", 50.4501);
    }

    UUID request(UUID actor, UUID object) throws Exception {
        return UUID.fromString(response(point(actor, object, coordinates()).andExpect(status().isOk()))
                .path("approvalRequestId")
                .asText());
    }

    UUID type() {
        return jdbc.queryForObject("select id from contract_types where code='PRIMARY'", UUID.class);
    }

    String requestStatus(UUID request) {
        return jdbc.queryForObject(
                "select status from entry_point_approval_requests where id=?", String.class, request);
    }
}
