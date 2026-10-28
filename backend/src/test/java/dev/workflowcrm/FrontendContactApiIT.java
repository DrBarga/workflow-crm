package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;

class FrontendContactApiIT extends ContactApiTestSupport {
    @Test
    void searchAndObjectLinksApplyVisibilityAndMaskPhones() throws Exception {
        UUID own = objectId(manager);
        var foreignObject = body();
        foreignObject.put("regionId", otherRegion.toString());
        foreignObject.put("confirmDuplicates", true);
        UUID foreign = UUID.fromString(json.readTree(create(outsider, key(), foreignObject)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray())
                .path("id")
                .asText());
        UUID visible = contactId(createdContact());
        link(own, visible);
        var foreignBody = contactBody();
        foreignBody.put("name", "Прихований");
        foreignBody.put("phone", "050 234-56-78");
        UUID hidden = UUID.fromString(json.readTree(postContact(outsider, foreignBody)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray())
                .path("contact")
                .path("id")
                .asText());
        link(foreign, hidden);
        var found = read(manager, "/api/contacts?q=123");
        assertThat(found.path("items")).hasSize(1);
        assertThat(found.path("items").get(0).path("phone").asText()).isEqualTo("***");
        assertThat(found.toString()).doesNotContain("671234567", hidden.toString());
        assertThat(read(manager, "/api/objects/" + own + "/contacts").path("items"))
                .hasSize(1);
        assertThat(read(lead, "/api/contacts").path("items")).hasSize(1);
        assertThat(read(admin, "/api/contacts").path("items")).hasSize(2);
        mvc.perform(get("/api/objects/" + foreign + "/contacts").cookie(cookie(manager)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/objects/" + UUID.randomUUID() + "/contacts").cookie(cookie(manager)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unlinkedContactsLiteralSearchSoftDeletionAndPagination() throws Exception {
        UUID first = contactId(createdContact());
        var other = contactBody();
        other.put("name", "100%_ Власник");
        other.put("phone", "050 234-56-78");
        UUID second = UUID.fromString(json.readTree(postContact(manager, other)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray())
                .path("contact")
                .path("id")
                .asText());
        var literal = json.readTree(
                mvc.perform(get("/api/contacts").param("q", "100%_").cookie(cookie(manager)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray());
        assertThat(literal.path("items")).hasSize(1);
        assertThat(read(colleague, "/api/contacts").path("items")).isEmpty();
        var page = read(manager, "/api/contacts?size=1");
        assertThat(page.path("totalElements").asInt()).isEqualTo(2);
        assertThat(page.path("hasMore").asBoolean()).isTrue();
        assertThat(read(manager, "/api/contacts?size=1&page=1").path("hasMore").asBoolean())
                .isFalse();
        jdbc.update("update contacts set deleted_at=now() where id=?", second);
        assertThat(read(manager, "/api/contacts").path("items")).hasSize(1);
        assertThat(read(manager, "/api/contacts")
                        .path("items")
                        .get(0)
                        .path("id")
                        .asText())
                .isEqualTo(first.toString());
        mvc.perform(get("/api/contacts").param("size", "0").cookie(cookie(manager)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/contacts")).andExpect(status().isUnauthorized());
    }

    @Test
    void contractContactsFollowExistingStatusPolicy() throws Exception {
        UUID contract = UUID.randomUUID();
        user(contract, "CONTRACT_MANAGER", null);
        UUID object = objectId(manager);
        link(object, contactId(createdContact()));
        assertThat(read(contract, "/api/contacts").path("items")).isEmpty();
        mvc.perform(get("/api/objects/" + object + "/contacts").cookie(cookie(contract)))
                .andExpect(status().isForbidden());
        jdbc.update(
                "update objects set status_id=(select id from object_statuses where code='CONTRACT_APPROVAL') where id=?",
                object);
        assertThat(read(contract, "/api/objects/" + object + "/contacts").path("items"))
                .hasSize(1);
    }

    private JsonNode read(UUID actor, String path) throws Exception {
        return json.readTree(mvc.perform(get(path).cookie(cookie(actor)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    }
}
