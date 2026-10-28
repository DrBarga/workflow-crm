package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObjectCreationContactRegionsIT extends ObjectApiTestSupport {
    @Test
    void managerWithOnlyAdditionalRegionCreatesAndReusesContact() throws Exception {
        UserRegionTestData.replace(jdbc, manager, otherRegion);
        var source = body();
        source.put("regionId", otherRegion.toString());
        source.put("contact", contact());
        var response = create(manager, key(), source)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();
        String contact = json.readTree(response.getContentAsByteArray())
                .path("contactId")
                .asText();
        var target = body();
        target.put("regionId", otherRegion.toString());
        target.put("contactId", contact);
        target.put("confirmDuplicates", true);
        create(manager, key(), target)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contactId").value(contact));
        assertThat(count("contacts")).isEqualTo(1);
        assertThat(count("object_contacts")).isEqualTo(2);
    }

    @Test
    void directorCreatesWithNewContactAndCanReuseContactAcrossRegions() throws Exception {
        var director = UUID.randomUUID();
        user(director, "DEVELOPMENT_DIRECTOR", null);
        var source = body();
        source.put("regionId", otherRegion.toString());
        source.put("managerId", outsider.toString());
        source.put("contact", contact());
        var response = create(director, key(), source)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();
        String contact = json.readTree(response.getContentAsByteArray())
                .path("contactId")
                .asText();
        var target = body();
        target.put("managerId", manager.toString());
        target.put("contactId", contact);
        target.put("confirmDuplicates", true);
        create(director, key(), target)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contactId").value(contact));
        assertThat(count("contacts")).isEqualTo(1);
        assertThat(count("object_contacts")).isEqualTo(2);
    }

    @Test
    void managerCannotUseForeignContactInAdditionalRegionAndFailedAttachRollsBackCreation() throws Exception {
        var source = body();
        source.put("regionId", otherRegion.toString());
        source.put("contact", contact());
        var response = create(outsider, key(), source)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();
        String contact = json.readTree(response.getContentAsByteArray())
                .path("contactId")
                .asText();
        jdbc.update("insert into user_regions(user_id,region_id) values(?,?)", manager, otherRegion);
        var target = body();
        target.put("regionId", otherRegion.toString());
        target.put("contactId", contact);
        target.put("confirmDuplicates", true);
        create(manager, key(), target)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].field").value("contactId"));
        assertThat(count("objects")).isEqualTo(1);
        assertThat(count("object_contacts")).isEqualTo(1);
        assertThat(count("object_creation_requests")).isEqualTo(1);
    }

    private Map<String, String> contact() {
        return Map.of("name", "Власник", "role", "Власник", "phone", "+380671234567");
    }
}
