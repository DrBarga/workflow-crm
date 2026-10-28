package dev.workflowcrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.web.servlet.ResultActions;

abstract class ObjectMapTestSupport extends ObjectListTestSupport {
    static final String BOX = "30,50,31,51";

    ResultActions mapRequest(UUID actor, String bbox, Map<String, String> filters) throws Exception {
        var request = get("/api/objects/map").cookie(cookie(actor));
        if (bbox != null) request.param("bbox", bbox);
        filters.forEach(request::param);
        return mvc.perform(request);
    }

    JsonNode pins(UUID actor, String bbox, Map<String, String> filters) throws Exception {
        return json.readTree(mapRequest(actor, bbox, filters)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    }

    JsonNode pins(UUID actor) throws Exception {
        return pins(actor, BOX, Map.of());
    }
}
