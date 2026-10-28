package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectHistoryAccessIT extends ObjectHistoryTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CEO", "SUPERADMIN"})
    void fullViewRolesCanReadHistory(String role) throws Exception {
        UUID object = add("Історія", manager, region);
        entry(object, manager, "name", "Старе", "Нове");
        var result = historyRequest(actor(role), object, Map.of());
        result.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        var row = response(result).path("items").get(0);
        assertThat(row.path("author").path("id").asText()).isEqualTo(manager.toString());
        assertThat(row.path("author").path("name").asText()).isEqualTo("Object Test User");
        assertThat(row.path("previousValue").asText()).isEqualTo("Старе");
        assertThat(row.path("newValue").asText()).isEqualTo("Нове");
        assertThat(row.path("occurredAt").asText()).isNotBlank();
    }

    @Test
    void reducedObjectViewsCannotReadHistory() throws Exception {
        UUID object = add("Приватна історія", manager, region);
        for (UUID actor : List.of(colleague, outsider, contract))
            historyRequest(actor, object, Map.of()).andExpect(status().isForbidden());
        UUID foreign = add("Інший регіон", outsider, otherRegion);
        historyRequest(lead, foreign, Map.of()).andExpect(status().isForbidden());
    }

    @Test
    void leadUsesExplicitAssignmentsAndManagerMustMatchCurrentRegion() throws Exception {
        UserRegionTestData.assign(jdbc, lead, otherRegion);
        UUID object = add("Дочірній регіон", outsider, otherRegion);
        historyRequest(lead, object, Map.of()).andExpect(status().isOk());
        UserRegionTestData.replace(jdbc, outsider, region);
        historyRequest(outsider, object, Map.of()).andExpect(status().isForbidden());
    }

    @Test
    void currentOwnershipAndActivityAreCheckedOnEveryPage() throws Exception {
        UUID object = add("Зміна відповідального", manager, region);
        entry(object, manager, "name", "1", "2");
        entry(object, manager, "name", "2", "3");
        String cursor = history(manager, object, Map.of("limit", "1"))
                .path("nextCursor")
                .asText();
        jdbc.update("update objects set manager_id=? where id=?", colleague, object);
        historyRequest(manager, object, Map.of("cursor", cursor)).andExpect(status().isForbidden());
        historyRequest(colleague, object, Map.of("cursor", cursor)).andExpect(status().isOk());
        jdbc.update("update users set status='DISABLED' where id=?", colleague);
        historyRequest(colleague, object, Map.of()).andExpect(status().isForbidden());
    }

    @Test
    void missingObjectIs404AndMissingSessionIs401() throws Exception {
        historyRequest(manager, UUID.randomUUID(), Map.of()).andExpect(status().isNotFound());
        mvc.perform(get("/api/objects/{id}/history", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void historyHasNoWriteEndpoint() throws Exception {
        UUID object = add("Без редагування історії", manager, region);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                "/api/objects/{id}/history", object)
                        .cookie(cookie(admin), csrf)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/objects/{id}/history", object)
                        .cookie(cookie(admin), csrf)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isMethodNotAllowed());
    }
}
