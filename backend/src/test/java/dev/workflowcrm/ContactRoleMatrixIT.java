package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContactRoleMatrixIT extends ContactApiTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "SUPERADMIN"})
    void documentedWritersCanCreateAndEditAnAccessibleContactRegardlessOfAuthor(String role) throws Exception {
        var actor = UUID.randomUUID();
        user(actor, role, role.equals("DEVELOPMENT_DIRECTOR") || role.equals("SUPERADMIN") ? otherRegion : region);
        var id = contactId(createdContact());
        var object = objectId(manager);
        link(object, id);
        if (role.equals("MANAGER")) jdbc.update("update objects set manager_id=? where id=?", actor, object);
        jdbc.update("update contacts set author_id=null where id=?", id);
        getContact(actor, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.canDelete").value(role.equals("SUPERADMIN")));
        patchContact(actor, id, Map.of("version", 0, "name", "Уточнений контакт"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact.version").value(1));
        var body = contactBody();
        body.put("objectIds", java.util.List.of(object));
        postContact(actor, body).andExpect(status().isCreated());
        if (!role.equals("SUPERADMIN"))
            deleteContact(actor, id, 1, null)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.confirmationToken").doesNotExist());
    }

    @Test
    void unlinkedContactsAreReadOnlyForTheirAuthorsAndAdministrableBySuperadmin() throws Exception {
        var id = contactId(createdContact());
        getContact(manager, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(false));
        patchContact(manager, id, Map.of("version", 0, "name", "No")).andExpect(status().isForbidden());
        getContact(admin, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.canDelete").value(true));
        patchContact(admin, id, Map.of("version", 0, "name", "Updated")).andExpect(status().isOk());
        deleteContact(admin, id, 1, null).andExpect(status().isNoContent());
    }

    @Test
    void reassignmentRevokesAuthorRightsAndSharedContactsFollowVisibleObjectScope() throws Exception {
        var id = contactId(createdContact());
        var object = objectId(colleague);
        link(object, id);
        patchContact(manager, id, Map.of("version", 0, "name", "No")).andExpect(status().isNotFound());
        patchContact(colleague, id, Map.of("version", 0, "name", "Updated")).andExpect(status().isOk());
        jdbc.update("update objects set region_id=? where id=?", otherRegion, object);
        patchContact(lead, id, Map.of("version", 1, "name", "No")).andExpect(status().isNotFound());
        link(objectId(manager), id);
        patchContact(manager, id, Map.of("version", 1, "name", "Shared"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact.objects.length()").value(1));
        jdbc.update("update users set role_id=(select id from roles where code='CEO') where id=?", manager);
        patchContact(manager, id, Map.of("version", 2, "name", "No")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select version from contacts where id=?", Long.class, id))
                .isEqualTo(2);
    }

    @Test
    void regionalEditRecordsActorTimeAndOldAndNewValuesWithoutExposingHistoryInCard() throws Exception {
        var id = contactId(createdContact());
        link(objectId(manager), id);
        patchContact(lead, id, Map.of("version", 0, "phone", "050 123-45-67", "email", "new@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact.phone").value("***"))
                .andExpect(jsonPath("$.contact.changedValues").doesNotExist());
        var history = jdbc.queryForMap(
                "select actor_id,created_at,changed_values::text as changes "
                        + "from contact_history where contact_id=? and event_type='UPDATED'",
                id);
        assertThat(history.get("actor_id")).isEqualTo(lead);
        assertThat(history.get("created_at")).isNotNull();
        var changes = json.readTree((String) history.get("changes"));
        assertThat(changes.path("phone").path("before").asText()).isEqualTo("+380671234567");
        assertThat(changes.path("phone").path("after").asText()).isEqualTo("+380501234567");
        assertThat(changes.path("email").path("before").asText()).isEqualTo("owner@example.com");
        assertThat(changes.path("email").path("after").asText()).isEqualTo("new@example.com");
    }
}
