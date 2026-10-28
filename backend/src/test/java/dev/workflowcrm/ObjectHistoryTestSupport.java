package dev.workflowcrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.test.web.servlet.ResultActions;

abstract class ObjectHistoryTestSupport extends ObjectEditTestSupport {
    ResultActions historyRequest(UUID actor, UUID object, Map<String, String> parameters) throws Exception {
        var request = get("/api/objects/{id}/history", object).cookie(cookie(actor));
        parameters.forEach(request::param);
        return mvc.perform(request);
    }

    JsonNode history(UUID actor, UUID object, Map<String, String> parameters) throws Exception {
        return response(historyRequest(actor, object, parameters).andExpect(status().isOk()));
    }

    void entry(UUID object, UUID author, String field, String before, String after) {
        jdbc.update(
                """
            insert into object_history(object_id,author_id,actor_type,field_name,previous_value,new_value,object_version)
            values (?,?,?,?,?,?,0)
            """,
                object,
                author,
                author == null ? "SYSTEM" : "USER",
                field,
                before,
                after);
    }

    List<String> historyFields(UUID object) throws Exception {
        var fields = new ArrayList<String>();
        history(manager, object, Map.of())
                .path("items")
                .forEach(e -> fields.add(e.path("field").asText()));
        return fields;
    }
}
