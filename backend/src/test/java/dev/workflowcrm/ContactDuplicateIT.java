package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContactDuplicateIT extends ContactApiTestSupport {
    @Test
    void duplicatePhoneWarnsWithoutBlockingCreationAndIgnoresDeletedContacts() throws Exception {
        var first = contactId(createdContact());
        var body = contactBody();
        body.put("phone", "00 380 (67) 123-45-67");
        var second = postContact(manager, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warningCode").value("CONTACT_PHONE_DUPLICATE"))
                .andExpect(jsonPath("$.duplicateContacts[0].id").value(first.toString()))
                .andReturn()
                .getResponse();
        var secondId = contactId(json.readTree(second.getContentAsByteArray()).path("contact"));
        assertThat(count("contacts")).isEqualTo(2);
        deleteContact(admin, first, 0, null).andExpect(status().isNoContent());
        deleteContact(admin, secondId, 0, null).andExpect(status().isNoContent());
        postContact(manager, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duplicateContacts").isEmpty());
    }

    @Test
    void findsLegacyLocalPhoneAndCapsWarningsAtTwenty() throws Exception {
        for (int i = 0; i < 23; i++) {
            jdbc.update(
                    "insert into contacts(id,name,role,phone,author_id) values (?,'Legacy','Owner','0671234567',?)",
                    UUID.randomUUID(),
                    manager);
        }
        postContact(manager, contactBody())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duplicateContacts.length()").value(20));
        assertThat(count("contacts")).isEqualTo(24);
    }
}
