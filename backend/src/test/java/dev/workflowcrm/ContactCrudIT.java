package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContactCrudIT extends ContactApiTestSupport {
    @Test
    void createsNormalizedContactAndPatchesOnlySuppliedFieldsWithVersionedAudit() throws Exception {
        var contact = createdContact();
        link(objectId(manager), contactId(contact));
        var id = contactId(contact);
        assertThat(contact.path("phone").asText()).isEqualTo("***");
        assertThat(contact.path("email").asText()).isEqualTo("owner@example.com");
        assertThat(contact.path("authorId").asText()).isEqualTo(manager.toString());
        assertThat(jdbc.queryForObject("select phone from contacts where id=?", String.class, id))
                .isEqualTo("+380671234567");
        patchContact(manager, id, Map.of("version", 0, "comment", "Уточнено"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact.version").value(1))
                .andExpect(jsonPath("$.contact.name").value("Власник"));
        patchContact(manager, id, Map.of("version", 0, "name", "Changed"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"))
                .andExpect(jsonPath("$.currentVersion").value(1));
        patchContact(manager, id, Map.of("version", 1, "comment", "Уточнено"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact.version").value(1));
        var clear = new LinkedHashMap<String, Object>();
        clear.put("version", 1);
        clear.put("comment", null);
        clear.put("phone", null);
        patchContact(manager, id, clear)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact.phone").isEmpty())
                .andExpect(jsonPath("$.contact.version").value(2));
        assertThat(jdbc.queryForList(
                        "select event_type from contact_history where contact_id=? order by contact_version",
                        String.class,
                        id))
                .containsExactly("CREATED", "UPDATED", "UPDATED");
        assertThat(jdbc.queryForList(
                        "select changed_fields::text from contact_history where contact_id=?", String.class, id))
                .noneMatch(s -> s.contains("Уточнено") || s.contains("380671"));
        getContact(manager, id).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void createsWithObjectsAndSoftDeletesWithoutRemovingLinksOrHistory() throws Exception {
        var object = objectId(manager);
        var body = contactBody();
        body.put("objectIds", List.of(object));
        var response = postContact(manager, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contact.objects[0].id").value(object.toString()))
                .andReturn()
                .getResponse();
        var id = contactId(json.readTree(response.getContentAsByteArray()).path("contact"));
        var conflict = deleteContact(admin, id, 0, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTACT_DELETION_CONFIRMATION_REQUIRED"))
                .andReturn()
                .getResponse();
        var token = json.readTree(conflict.getContentAsByteArray())
                .path("confirmationToken")
                .asText();
        assertThat(token).matches("[0-9a-f]{64}");
        assertThat(count("contact_history")).isEqualTo(1);
        deleteContact(admin, id, 0, token).andExpect(status().isNoContent());
        getContact(manager, id).andExpect(status().isNotFound());
        patchContact(manager, id, Map.of("version", 1, "name", "Other")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select deleted_at is not null from contacts where id=?", Boolean.class, id))
                .isTrue();
        assertThat(count("object_contacts")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select linked_object_ids::text from contact_history where event_type='DELETED'", String.class))
                .contains(object.toString());
    }

    @Test
    void unlinkedContactDoesNotNeedConfirmationAndCannotLoseBothChannels() throws Exception {
        var id = contactId(createdContact());
        var clear = new LinkedHashMap<String, Object>();
        clear.put("version", 0);
        clear.put("phone", null);
        clear.put("email", null);
        patchContact(admin, id, clear).andExpect(status().isUnprocessableEntity());
        deleteContact(admin, id, 0, null).andExpect(status().isNoContent());
        deleteContact(admin, id, 0, null).andExpect(status().isNotFound());
    }
}
