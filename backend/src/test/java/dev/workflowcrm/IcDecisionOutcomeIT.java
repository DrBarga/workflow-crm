package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IcDecisionOutcomeIT extends IcDecisionTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "APPROVED_WITH_CONDITIONS"})
    void savesDecisionAndAutomaticallyApprovesCommercialTerms(String value) throws Exception {
        decide(lead, decision(value))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.status").value("COMMERCIAL_TERMS_APPROVAL"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(state()).isEqualTo("COMMERCIAL_TERMS_APPROVAL");
        assertThat(jdbc.queryForObject(
                        "select investment_committee_decision from objects where id=?", String.class, object))
                .isEqualTo(value);
        assertThat(jdbc.queryForObject(
                        "select investment_committee_date::text from objects where id=?", String.class, object))
                .isEqualTo("2026-10-05");
        assertThat(decisionHistoryCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select actor_type from object_history where field_name='STATUS'", String.class))
                .isEqualTo("SYSTEM");
        assertThat(jdbc.queryForObject(
                        "select author_id from object_history where field_name='STATUS'", java.util.UUID.class))
                .isNull();
        assertThat(jdbc.queryForObject(
                        "select author_id from object_history where field_name='IC_DECISION'", java.util.UUID.class))
                .isEqualTo(lead);
        if (value.contains("CONDITIONS"))
            assertThat(jdbc.queryForObject(
                            "select investment_committee_conditions from objects where id=?", String.class, object))
                    .isEqualTo("Погодити орендну ставку");
    }

    @Test
    void refusalUsesSystemReasonAndRemembersPreviousStatus() throws Exception {
        decide(director, decision("REFUSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUSED"));
        assertThat(jdbc.queryForObject(
                        "select r.code from objects o join refusal_reasons r on r.id=o.refusal_reason_id where o.id=?",
                        String.class,
                        object))
                .isEqualTo("IC_NOT_APPROVED");
        assertThat(jdbc.queryForObject(
                        "select s.code from objects o join object_statuses s on s.id=o.status_before_refusal_id where o.id=?",
                        String.class,
                        object))
                .isEqualTo("IC_PREPARATION");
        assertThat(decisionHistoryCount()).isEqualTo(2);
    }

    @Test
    void directorCanDecideAcrossRegionsAndDisabledLeadCannot() throws Exception {
        jdbc.update("update users set status='DISABLED' where id=?", lead);
        decide(lead, decision("APPROVED")).andExpect(status().isForbidden());
        jdbc.update("update objects set region_id=? where id=?", otherRegion, object);
        decide(director, decision("APPROVED")).andExpect(status().isOk());
    }

    @Test
    void unknownObjectIsNotFound() throws Exception {
        object = java.util.UUID.randomUUID();
        decide(lead, decision("APPROVED")).andExpect(status().isNotFound());
    }
}
