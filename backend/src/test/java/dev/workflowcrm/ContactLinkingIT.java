package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ContactLinkingIT extends ContactApiTestSupport {
    @Test
    void contactCreatedWithoutObjectsCanBeAttachedThroughObjectCreation() throws Exception {
        var id = contactId(createdContact());
        var body = body();
        body.put("contactId", id);
        create(manager, key(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contactId").value(id.toString()));
        assertThat(count("contacts")).isEqualTo(1);
        assertThat(count("contact_history")).isEqualTo(1);
        getContact(manager, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objects.length()").value(1));
    }

    @Test
    void contactCanBeSelectedThroughObjectEditAndInlineCreationIsAudited() throws Exception {
        var id = contactId(createdContact());
        var object = objectId(manager);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                "/api/objects/{id}", object)
                        .cookie(cookie(manager), csrf)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("version", 0, "contactId", id))))
                .andExpect(status().isOk());
        var body = body();
        body.put("name", "Інший об’єкт");
        body.put("contact", Map.of("name", "Юрист", "role", "Юрист", "email", "legal@example.com"));
        body.put("confirmDuplicates", true);
        create(manager, key(), body).andExpect(status().isCreated());
        assertThat(count("contacts")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "select count(*) from contact_history where event_type='CREATED'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void auditFailureRollsBackUpdateAndDeletionWithoutLoggingContactValues(CapturedOutput output) throws Exception {
        var id = contactId(createdContact());
        jdbc.execute("alter table contact_history add constraint test_reject_changes check (event_type = 'CREATED')");
        try {
            patchContact(admin, id, Map.of("version", 0, "name", "Sensitive audit failure marker"))
                    .andExpect(status().isInternalServerError());
            deleteContact(admin, id, 0, null).andExpect(status().isInternalServerError());
            assertThat(jdbc.queryForObject("select version from contacts where id=?", Long.class, id))
                    .isZero();
            assertThat(jdbc.queryForObject("select deleted_at is null from contacts where id=?", Boolean.class, id))
                    .isTrue();
            assertThat(output.getAll())
                    .doesNotContain("Sensitive audit failure marker", "+380671234567", "owner@example.com");
        } finally {
            jdbc.execute("alter table contact_history drop constraint test_reject_changes");
        }
    }
}
