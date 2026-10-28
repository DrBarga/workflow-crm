package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectRefusalReasonsIT extends ObjectStatusTasksSupport {
    @ParameterizedTest
    @ValueSource(strings = {"missing", "unknown", "inactive", "system"})
    void reasonMustBeActiveManualDictionaryValue(String variant) throws Exception {
        task("NEW");
        var tasks = taskSnapshot();
        var body = command("REFUSED");
        UUID reason = reason();
        if ("unknown".equals(variant)) reason = UUID.randomUUID();
        if ("system".equals(variant))
            reason = jdbc.queryForObject("select id from refusal_reasons where code='IC_NOT_APPROVED'", UUID.class);
        if ("inactive".equals(variant)) jdbc.update("update refusal_reasons set is_active=false where id=?", reason);
        if (!"missing".equals(variant)) body.put("refusalReasonId", reason);
        try {
            change(manager, body)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.violations[0].field").value("refusalReasonId"));
            assertThat(state()).isEqualTo("FOUND");
            assertThat(taskSnapshot()).isEqualTo(tasks);
            assertThat(count("object_history")).isZero();
            assertThat(events("TASK_CANCELLED")).isZero();
        } finally {
            if ("inactive".equals(variant)) jdbc.update("update refusal_reasons set is_active=true where id=?", reason);
        }
    }

    @Test
    void dictionaryExcludesSystemAndInactiveReasonsAndRespectsObjectScope() throws Exception {
        UUID inactive = reason();
        jdbc.update("update refusal_reasons set is_active=false where id=?", inactive);
        try {
            var values = response(mvc.perform(
                            get("/api/objects/" + object + "/refusal-reasons").cookie(cookie(manager)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store")));
            assertThat(values).hasSize(4);
            assertThat(values.toString()).doesNotContain("IC_NOT_APPROVED", inactive.toString());
            mvc.perform(get("/api/objects/" + object + "/refusal-reasons").cookie(cookie(colleague)))
                    .andExpect(status().isForbidden());
        } finally {
            jdbc.update("update refusal_reasons set is_active=true where id=?", inactive);
        }
    }

    @Test
    void persistsTypedTagForApiAndAnalyticsAndKeepsRefusedObjectInDuplicateSearch() throws Exception {
        UUID reason = reason();
        var row = jdbc.queryForMap("select code,name from refusal_reasons where id=?", reason);
        var body = command("REFUSED");
        body.put("refusalReasonId", reason);
        change(manager, body).andExpect(status().isOk());
        mvc.perform(get("/api/objects/" + object + "/status").cookie(cookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refusalTag.id").value(reason.toString()))
                .andExpect(jsonPath("$.refusalTag.code").value(row.get("code")))
                .andExpect(jsonPath("$.refusalTag.name").value(row.get("name")));
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_refusal_tags where reason_id=? and region_id=? and manager_id=?",
                        Integer.class,
                        reason,
                        region,
                        manager))
                .isEqualTo(1);
        create(manager, key(), body())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.duplicates.items[0].id").value(object.toString()))
                .andExpect(jsonPath("$.duplicates.items[0].status").value("REFUSED"))
                .andExpect(jsonPath("$.duplicates.items[0].refused").value(true));
        assertThat(count("objects")).isEqualTo(1);
        change(manager, command("FOUND")).andExpect(status().isOk());
        assertThat(count("object_refusal_tags")).isZero();
        assertThat(jdbc.queryForList(
                        "select previous_value from object_history where object_id=? and field_name='REFUSAL_REASON'",
                        String.class,
                        object))
                .contains(row.get("code").toString());
    }
}
