package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObjectCardAccessIT extends ObjectCardTestSupport {
    @Test
    void missingAndAdministratorDeletedObjectReturn404() throws Exception {
        cardRequest(manager, UUID.randomUUID()).andExpect(status().isNotFound());
        UUID object = add("Видалений", manager, region);
        ObjectTestCleanup.erase(jdbc, () -> jdbc.update("delete from objects where id=?", object));
        cardRequest(manager, object).andExpect(status().isNotFound());
    }

    @Test
    void missingUserOrDisabledUserCannotReadEvenPinOrProbeMissingObject() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        cardRequest(UUID.randomUUID(), object).andExpect(status().isForbidden());
        jdbc.update("update users set status='DISABLED' where id=?", manager);
        cardRequest(manager, object).andExpect(status().isForbidden());
        cardRequest(manager, UUID.randomUUID()).andExpect(status().isForbidden());
    }

    @Test
    void missingRegionNeverGivesFullAccess() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        UserRegionTestData.replace(jdbc, manager);
        assertThat(fields(card(manager, object))).containsExactlyInAnyOrder("name", "entrance");
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/objects/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void malformedIdReturns400() throws Exception {
        mvc.perform(get("/api/objects/not-a-uuid").cookie(cookie(manager))).andExpect(status().isBadRequest());
    }
}
