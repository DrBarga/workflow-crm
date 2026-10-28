package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObjectContactSelectionValidationIT extends ObjectContactTestSupport {
    @Test
    void missingForeignAndColleaguesContactHaveSameErrorAndRollback() throws Exception {
        UUID colleaguesContact = existing(colleague);
        var foreign = withContact();
        foreign.put("regionId", otherRegion.toString());
        foreign.put("confirmDuplicates", true);
        var response = create(outsider, key(), foreign)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();
        String foreignContact = json.readTree(response.getContentAsByteArray())
                .path("contactId")
                .asText();
        String first = null;
        for (String contact :
                new String[] {UUID.randomUUID().toString(), colleaguesContact.toString(), foreignContact}) {
            var body = body();
            body.put("contactId", contact);
            body.put("confirmDuplicates", true);
            var rejected = create(manager, key(), body)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.violations[0].field").value("contactId"))
                    .andReturn()
                    .getResponse();
            String message = json.readTree(rejected.getContentAsByteArray())
                    .path("violations")
                    .get(0)
                    .path("message")
                    .asText();
            if (first == null) first = message;
            assertThat(message).isEqualTo(first);
        }
        assertThat(count("objects")).isEqualTo(2);
        assertThat(count("contacts")).isEqualTo(2);
        assertThat(count("object_contacts")).isEqualTo(2);
        assertThat(count("object_creation_requests")).isEqualTo(2);
    }

    @Test
    void rejectsBothSelectionModesMalformedIdAndUnknownNestedFields() throws Exception {
        var body = withContact();
        body.put("contactId", UUID.randomUUID().toString());
        create(manager, key(), body)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value("contactId"));
        body = body();
        body.put("contactId", "not-a-uuid");
        create(manager, key(), body).andExpect(status().isUnprocessableEntity());
        body = withContact();
        body.put(
                "contact",
                Map.of(
                        "name",
                        "Owner",
                        "role",
                        "Owner",
                        "phone",
                        "+380671234567",
                        "id",
                        UUID.randomUUID().toString()));
        create(manager, key(), body)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value("contact.id"));
        assertThat(count("objects")).isZero();
        assertThat(count("contacts")).isZero();
    }
}
