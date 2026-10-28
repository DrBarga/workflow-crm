package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;

class FrontendMentionApiIT extends ObjectCommentMentionsTestSupport {
    @Test
    void suggestionsMatchMentionValidationAndExcludeForeignInactiveUsers() throws Exception {
        UUID object = add("Об'єкт", manager, region);
        jdbc.update("update users set full_name='Отримувач' where id=?", lead);
        jdbc.update("update users set status='DISABLED' where id=?", colleague);
        var response = json.readTree(
                mvc.perform(get("/api/objects/" + object + "/mentionable-users").cookie(cookie(manager)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray());
        var ids = new HashSet<String>();
        for (var item : response.path("items")) {
            ids.add(item.path("id").asText());
            assertThat(item.has("email") || item.has("regionIds")).isFalse();
        }
        assertThat(ids)
                .contains(
                        manager.toString(),
                        lead.toString(),
                        director.toString(),
                        contract.toString(),
                        admin.toString(),
                        ceo.toString());
        assertThat(ids).doesNotContain(colleague.toString(), outsider.toString());
        postComment(manager, object, body(label(lead), List.of(mention(lead, 0))))
                .andExpect(status().isCreated());
        var filtered = json.readTree(mvc.perform(get("/api/objects/" + object + "/mentionable-users")
                        .param("q", "Отри")
                        .cookie(cookie(manager)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        assertThat(filtered.path("items")).hasSize(1);
        mvc.perform(get("/api/objects/" + object + "/mentionable-users").cookie(cookie(outsider)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/objects/" + object + "/mentionable-users").cookie(cookie(ceo)))
                .andExpect(status().isForbidden());
    }
}
