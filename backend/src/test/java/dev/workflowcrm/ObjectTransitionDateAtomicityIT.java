package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectTransitionDateAtomicityIT extends ObjectStatusTasksSupport {
    @ParameterizedTest
    @ValueSource(strings = {"card", "milestones"})
    void auditFailureRollsBackBlockedDateAndHistory(String route) throws Exception {
        state("CONTRACT_APPROVAL");
        var before = jdbc.queryForMap("select * from objects where id=?", object);
        jdbc.execute(
                "create function fail_transition_date_audit() returns trigger language plpgsql as $$ begin raise exception 'test audit outage'; end $$");
        jdbc.execute(
                "create trigger fail_transition_date_audit before insert on object_status_audit for each row execute function fail_transition_date_audit()");
        try {
            date(route, Map.of("version", version(), "contractSignedDate", "2026-10-20"))
                    .andExpect(status().isInternalServerError());
            assertThat(jdbc.queryForMap("select * from objects where id=?", object))
                    .isEqualTo(before);
            assertThat(count("object_history")).isZero();
            assertThat(count("event_outbox")).isZero();
            assertThat(count("task_sets")).isZero();
        } finally {
            jdbc.execute("drop trigger fail_transition_date_audit on object_status_audit");
            jdbc.execute("drop function fail_transition_date_audit()");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"card", "milestones"})
    void invalidDateRoleOrVersionNeverUsesFallback(String route) throws Exception {
        state("CONTRACT_APPROVAL");
        var invalid = Map.<String, Object>of("version", version(), "contractSignedDate", "not-a-date");
        date(route, invalid).andExpect(status().isUnprocessableEntity());
        date(route, Map.of("version", version() + 1, "contractSignedDate", "2026-10-20"))
                .andExpect(status().isConflict());
        send(
                        ceo,
                        route.equals("card") ? "/api/objects/" + object : "/api/objects/" + object + "/milestones",
                        Map.of("version", version(), "contractSignedDate", "2026-10-20"),
                        true)
                .andExpect(status().isForbidden());
        assertThat(version()).isZero();
        assertThat(count("object_history")).isZero();
        assertThat(count("event_outbox")).isZero();
    }

    @org.junit.jupiter.api.Test
    void blockedDateAndOrdinaryFieldsAreSavedAsOneEdit() throws Exception {
        state("CONTRACT_APPROVAL");
        date("card", Map.of("version", version(), "contractSignedDate", "2026-10-20", "name", "Updated property"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(version()).isEqualTo(1);
        assertThat(state()).isEqualTo("CONTRACT_APPROVAL");
        assertThat(jdbc.queryForObject("select name from objects where id=?", String.class, object))
                .isEqualTo("Updated property");
        assertThat(jdbc.queryForList("select field_name from object_history", String.class))
                .contains("name", "contractSignedDate");
        assertThat(events("OBJECT_FIELDS_CHANGED")).isEqualTo(1);
        assertThat(events("OBJECT_STATUS_CHANGED")).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions date(String route, Map<String, Object> fields)
            throws Exception {
        return route.equals("milestones")
                ? milestones(lead, fields)
                : send(lead, "/api/objects/" + object, fields, true);
    }
}
