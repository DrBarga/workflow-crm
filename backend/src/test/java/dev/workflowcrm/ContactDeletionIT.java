package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ContactDeletionIT extends ContactApiTestSupport {
    @Test
    void confirmationExpiresWhenObjectOrContactChanges() throws Exception {
        var id = contactId(createdContact());
        var object = objectId(manager);
        link(object, id);
        var response = deleteContact(admin, id, 0, null)
                .andExpect(status().isConflict())
                .andReturn()
                .getResponse();
        var first = json.readTree(response.getContentAsByteArray())
                .path("confirmationToken")
                .asText();
        jdbc.update("update objects set version=version+1 where id=?", object);
        response = deleteContact(admin, id, 0, first)
                .andExpect(status().isConflict())
                .andReturn()
                .getResponse();
        var second = json.readTree(response.getContentAsByteArray())
                .path("confirmationToken")
                .asText();
        assertThat(second).isNotEqualTo(first);
        patchContact(manager, id, Map.of("version", 0, "comment", "Changed")).andExpect(status().isOk());
        deleteContact(admin, id, 0, second)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
        response = deleteContact(admin, id, 1, second)
                .andExpect(status().isConflict())
                .andReturn()
                .getResponse();
        var third = json.readTree(response.getContentAsByteArray())
                .path("confirmationToken")
                .asText();
        deleteContact(admin, id, 1, third).andExpect(status().isNoContent());
    }

    @Test
    void finalObjectDoesNotRequireConfirmationAndDeletedContactsCannotBeAttached() throws Exception {
        var id = contactId(createdContact());
        var object = objectId(manager);
        link(object, id);
        jdbc.update(
                "update objects set status_id=(select id from object_statuses where code='OPENED') where id=?", object);
        deleteContact(admin, id, 0, null).andExpect(status().isNoContent());
        var body = body();
        body.put("contactId", id);
        body.put("confirmDuplicates", true);
        create(manager, key(), body).andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("select count(*) from object_contacts where contact_id=?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    void auditFailureRollsBackContactCreation() throws Exception {
        jdbc.execute("alter table contact_history add constraint test_reject_history check (event_type <> 'CREATED')");
        try {
            postContact(manager, contactBody()).andExpect(status().isInternalServerError());
            assertThat(count("contacts")).isZero();
        } finally {
            jdbc.execute("alter table contact_history drop constraint test_reject_history");
        }
    }
}
