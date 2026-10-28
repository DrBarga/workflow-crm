package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ContactValidationIT extends ContactApiTestSupport {
    @Test
    void validatesFormatLengthsRequiredFieldsAndUnknownMetadata() throws Exception {
        postContact(manager, Map.of("name", "", "role", "", "phone", "abc", "email", "bad"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations").isArray());
        postContact(manager, Map.of("name", "Name", "role", "Role")).andExpect(status().isUnprocessableEntity());
        for (var field : List.of("authorId", "deletedAt", "normalizedPhone", "id", "version")) {
            var body = contactBody();
            body.put(field, UUID.randomUUID());
            postContact(manager, body).andExpect(status().isUnprocessableEntity());
        }
        var oversized = contactBody();
        oversized.put("name", "x".repeat(201));
        postContact(manager, oversized).andExpect(status().isUnprocessableEntity());
        oversized = contactBody();
        oversized.put("comment", "x".repeat(2001));
        postContact(manager, oversized).andExpect(status().isUnprocessableEntity());
        var invalidType = contactBody();
        invalidType.put("contactTypeId", UUID.randomUUID());
        postContact(manager, invalidType).andExpect(status().isUnprocessableEntity());
        invalidType.put("objectIds", List.of(UUID.randomUUID()));
        postContact(manager, invalidType).andExpect(status().isUnprocessableEntity());
        assertThat(count("contacts")).isZero();
        mvc.perform(post("/api/contacts")
                        .cookie(cookie(manager), csrf)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":123,\"role\":\"Role\",\"phone\":\"0671234567\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void requiresPatchVersionAndRejectsBlankNameForeignObjectAndInactiveType() throws Exception {
        var id = contactId(createdContact());
        link(objectId(manager), id);
        for (var body : List.of(
                Map.of("comment", "Missing version"),
                Map.of("version", -1, "name", "Name"),
                Map.of("version", 0),
                Map.of("version", 0, "name", " "),
                Map.of("version", "0", "name", "Name"),
                Map.of("version", 0, "objectIds", List.of(UUID.randomUUID())))) {
            patchContact(manager, id, body).andExpect(status().isUnprocessableEntity());
        }
        var type = jdbc.queryForObject("select id from contact_types where code='OWNER'", UUID.class);
        jdbc.update("update contact_types set is_active=false where id=?", type);
        try {
            patchContact(manager, id, Map.of("version", 0, "contactTypeId", type))
                    .andExpect(status().isUnprocessableEntity());
        } finally {
            jdbc.update("update contact_types set is_active=true where id=?", type);
        }
        var body = contactBody();
        body.put("objectIds", List.of(objectId(colleague)));
        postContact(manager, body).andExpect(status().isUnprocessableEntity());
        body.put("objectIds", List.of(UUID.randomUUID(), UUID.randomUUID()));
        postContact(manager, body).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void requiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(get("/api/contacts/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/contacts")
                        .cookie(cookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(contactBody())))
                .andExpect(status().isForbidden());
    }
}
