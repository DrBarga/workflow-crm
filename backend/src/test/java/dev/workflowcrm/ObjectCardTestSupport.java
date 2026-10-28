package dev.workflowcrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.test.web.servlet.ResultActions;

abstract class ObjectCardTestSupport extends ObjectListTestSupport {
    ResultActions cardRequest(UUID actor, UUID object) throws Exception {
        return mvc.perform(get("/api/objects/{id}", object).cookie(cookie(actor)));
    }

    JsonNode card(UUID actor, UUID object) throws Exception {
        return json.readTree(cardRequest(actor, object)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    }

    UUID actor(String role) {
        return switch (role) {
            case "MANAGER" -> manager;
            case "REGIONAL_MANAGER" -> lead;
            case "DEVELOPMENT_DIRECTOR" -> director;
            case "CEO" -> ceo;
            case "CONTRACT_MANAGER" -> contract;
            default -> admin;
        };
    }

    void state(UUID object, String status) {
        jdbc.update(
                "update objects set status_id=(select id from object_statuses where code=?) where id=?",
                status,
                object);
    }

    void tasks(UUID object) {
        jdbc.update(
                """
            insert into tasks(id,object_id,source_status_id,assignee_id,name,status,deadline,author_id)
            select ?,?,s.id,?,'Завершена задача','DONE','2026-10-10',?
            from object_statuses s where s.code='FOUND'
            """,
                UUID.randomUUID(),
                object,
                manager,
                manager);
    }

    void contractType(UUID object) {
        jdbc.update(
                "update objects set contract_type_id=(select id from contract_types where code='PRIMARY'),contract_signed_date='2026-10-01' where id=?",
                object);
    }

    Set<String> transitionCodes(JsonNode card) {
        var result = new HashSet<String>();
        card.path("availableTransitions").forEach(r -> result.add(r.path("code").asText()));
        return result;
    }
}
