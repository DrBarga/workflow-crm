package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

class ObjectDuplicateCancellationIT extends ObjectDuplicateTestSupport {
    @Test
    void cancellationIsRecordedOnceWithoutPhantomObjectAndPreventsKeyReuse() throws Exception {
        nearby(10);
        String key = key();
        create(manager, key, body()).andExpect(status().isConflict());
        cancel(manager, key, body()).andExpect(status().isNoContent());
        cancel(manager, key, body()).andExpect(status().isNoContent());
        assertThat(count("objects")).isEqualTo(1);
        assertThat(count("object_history")).isZero();
        assertThat(count("object_duplicate_decisions")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select decision from object_duplicate_decisions", String.class))
                .isEqualTo("CANCELLED");
        var confirmed = body();
        confirmed.put("confirmDuplicates", true);
        create(manager, key, confirmed).andExpect(status().isConflict());
        var changed = body();
        changed.put("name", "Інші дані");
        cancel(manager, key, changed).andExpect(status().isConflict());
        create(manager, key(), confirmed).andExpect(status().isCreated());
    }

    @Test
    void cannotCancelAnAlreadyCreatedRequestOrConfirmViaCancellation() throws Exception {
        String key = key();
        create(manager, key, body()).andExpect(status().isCreated());
        cancel(manager, key, body()).andExpect(status().isConflict());
        var body = body();
        body.put("confirmDuplicates", true);
        cancel(manager, key(), body).andExpect(status().isUnprocessableEntity());
        assertThat(count("object_duplicate_decisions")).isZero();
    }

    @Test
    void cancellationUsesCurrentPermissionsAndRequiresSessionAndCsrf() throws Exception {
        var body = body();
        body.put("regionId", otherRegion.toString());
        cancel(manager, key(), body).andExpect(status().isForbidden());
        mvc.perform(post("/api/objects/duplicate-cancellations")
                        .contentType("application/json")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .content(json.writeValueAsBytes(body())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/objects/duplicate-cancellations")
                        .cookie(cookie(manager))
                        .contentType("application/json")
                        .content(json.writeValueAsBytes(body())))
                .andExpect(status().isForbidden());
        assertThat(count("object_duplicate_decisions")).isZero();
    }
}
