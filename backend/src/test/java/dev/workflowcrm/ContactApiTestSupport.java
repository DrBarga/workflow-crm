package dev.workflowcrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

abstract class ContactApiTestSupport extends ObjectApiTestSupport {
    final UUID admin = UUID.fromString("00000000-0000-0000-0400-000000000007");

    @org.junit.jupiter.api.BeforeEach
    void setupContactAdmin() {
        user(admin, "SUPERADMIN", null);
    }

    Map<String, Object> contactBody() {
        return new java.util.LinkedHashMap<>(Map.of(
                "name", "Власник", "role", "Директор", "phone", "067 123-45-67", "email", " Owner@Example.com "));
    }

    ResultActions postContact(UUID actor, Map<String, ?> body) throws Exception {
        return mvc.perform(post("/api/contacts")
                .cookie(cookie(actor), csrf)
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)));
    }

    ResultActions patchContact(UUID actor, UUID id, Map<String, ?> body) throws Exception {
        return mvc.perform(patch("/api/contacts/{id}", id)
                .cookie(cookie(actor), csrf)
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)));
    }

    ResultActions getContact(UUID actor, UUID id) throws Exception {
        return mvc.perform(get("/api/contacts/{id}", id).cookie(cookie(actor)));
    }

    ResultActions deleteContact(UUID actor, UUID id, long version, String token) throws Exception {
        var request = delete("/api/contacts/{id}", id)
                .param("version", Long.toString(version))
                .cookie(cookie(actor), csrf)
                .header("X-XSRF-TOKEN", csrfToken);
        if (token != null) request.param("confirmationToken", token);
        return mvc.perform(request);
    }

    JsonNode createdContact() throws Exception {
        return json.readTree(postContact(manager, contactBody())
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray())
                .path("contact");
    }

    UUID contactId(JsonNode contact) {
        return UUID.fromString(contact.path("id").asText());
    }

    UUID objectId(UUID actor) throws Exception {
        var body = body();
        body.put("confirmDuplicates", true);
        return UUID.fromString(json.readTree(create(actor, key(), body)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray())
                .path("id")
                .asText());
    }

    void link(UUID object, UUID contact) {
        jdbc.update("insert into object_contacts(object_id,contact_id) values (?,?)", object, contact);
    }
}
