package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObjectCreationContactIT extends ObjectContactTestSupport {
    @Test
    void createsContactAndLinkWithObjectAndReplaysSameContact() throws Exception {
        var body = withContact();
        String key = key();
        var first = create(manager, key, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contactId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var second = create(manager, key, body)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(second).isEqualTo(first);
        assertThat(count("contacts")).isEqualTo(1);
        assertThat(count("object_contacts")).isEqualTo(1);
        assertThat(count("objects")).isEqualTo(1);
        assertThat(count("object_history")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select phone from contacts", String.class))
                .isEqualTo("+380671234567");
        assertThat(jdbc.queryForObject("select author_id from contacts", UUID.class))
                .isEqualTo(manager);
        assertThat(jdbc.queryForObject("select normalized_phone from contacts", String.class))
                .isEqualTo("380671234567");
    }

    @Test
    void linksExistingContactWithoutDuplicatingOrDisclosingItsChannels() throws Exception {
        UUID contact = existing(manager);
        var body = body();
        body.put("contactId", contact.toString());
        body.put("confirmDuplicates", true);
        String key = key();
        var first = create(manager, key, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contactId").value(contact.toString()))
                .andExpect(jsonPath("$.contact").isEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var second = create(manager, key, body)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(second).isEqualTo(first);
        assertThat(first).doesNotContain("380671234567");
        assertThat(count("contacts")).isEqualTo(1);
        assertThat(count("object_contacts")).isEqualTo(2);
        assertThat(count("objects")).isEqualTo(2);
    }

    @Test
    void regionalManagerCanReuseContactOfManagerInOwnRegion() throws Exception {
        UUID contact = existing(colleague);
        var body = body();
        body.put("contactId", contact.toString());
        body.put("managerId", manager.toString());
        body.put("confirmDuplicates", true);
        create(lead, key(), body).andExpect(status().isCreated());
        assertThat(count("contacts")).isEqualTo(1);
        assertThat(count("object_contacts")).isEqualTo(2);
    }
}
