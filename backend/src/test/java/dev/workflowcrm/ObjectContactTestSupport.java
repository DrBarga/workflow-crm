package dev.workflowcrm;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

abstract class ObjectContactTestSupport extends ObjectApiTestSupport {
    UUID existing(UUID actor) throws Exception {
        var response = create(actor, key(), withContact())
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();
        return UUID.fromString(json.readTree(response.getContentAsByteArray())
                .path("contactId")
                .asText());
    }

    Map<String, Object> withContact() {
        var body = body();
        body.put("contact", Map.of("name", "Власник", "role", "Власник", "phone", "+380 (67) 123-45-67"));
        return body;
    }
}
