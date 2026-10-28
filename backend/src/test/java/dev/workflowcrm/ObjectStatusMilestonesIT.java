package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectStatusMilestonesIT extends ObjectStatusTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CONTRACT_MANAGER"})
    void signingThroughCardUsesTransitionAndRequiresType(String role) throws Exception {
        state("CONTRACT_APPROVAL");
        ObjectSigningTestData.ready(jdbc, object, manager);
        var body = new LinkedHashMap<String, Object>();
        body.put("version", version());
        body.put("contractSignedDate", "2026-10-04");
        var error = response(milestones(actor(role), body).andExpect(status().isOk()));
        assertThat(state()).isEqualTo("CONTRACT_APPROVAL");
        assertThat(version()).isEqualTo(1);
        assertThat(error.toString()).contains("contractTypeId");
        jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);
        body.put("version", version());
        body.put("contractTypeId", contractType());
        milestones(actor(role), body).andExpect(status().isOk());
        assertThat(state()).isEqualTo("CONTRACT_SIGNED");
        assertThat(jdbc.queryForObject(
                        "select contract_signed_date::text from objects where id=?", String.class, object))
                .isEqualTo("2026-10-04");
    }

    @Test
    void plannedOpeningDoesNotTriggerStatusButActualOpeningDoes() throws Exception {
        state("CONTRACT_SIGNED");
        jdbc.update("update objects set contract_signed_date='2026-10-04' where id=?", object);
        send(
                        lead,
                        "/api/objects/" + object,
                        Map.of(
                                "version",
                                version(),
                                "constructionAcceptancePlannedDate",
                                "2026-11-01",
                                "commercialTerms",
                                Map.of("constructionTermDays", 0)),
                        true)
                .andExpect(status().isOk());
        assertThat(state()).isEqualTo("CONTRACT_SIGNED");
        milestones(contract, Map.of("version", version(), "openingDate", "2026-10-05"))
                .andExpect(status().isForbidden());
        milestones(lead, Map.of("version", version(), "openingDate", "2026-10-03"))
                .andExpect(status().isUnprocessableEntity());
        milestones(director, Map.of("version", version(), "openingDate", "2026-10-05"))
                .andExpect(status().isOk());
        assertThat(state()).isEqualTo("OPENED");
    }

    @Test
    void rollbackClearsDatesAndPreservesPreviousValuesInHistory() throws Exception {
        state("OPENED");
        jdbc.update(
                "update objects set contract_signed_date='2026-10-04',opening_date='2026-10-05' where id=?", object);
        change(lead, command("CONTRACT_SIGNED")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select opening_date from objects where id=?", java.sql.Date.class, object))
                .isNull();
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_history where field_name='OPENING_DATE' and previous_value='2026-10-05'",
                        Integer.class))
                .isEqualTo(1);
        change(director, command("CONTRACT_APPROVAL")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                        "select contract_signed_date from objects where id=?", java.sql.Date.class, object))
                .isNull();
    }

    @Test
    void returningToCommitteeClearsDecisionDateAndConditions() throws Exception {
        state("COMMERCIAL_TERMS_APPROVAL");
        jdbc.update(
                "update objects set legacy_investment_committee_conditions=? where id=?",
                "Історичні умови договору понад поточний ліміт".repeat(3),
                object);
        jdbc.update(
                "update objects set investment_committee_decision='APPROVED_WITH_CONDITIONS',investment_committee_date='2026-10-04',investment_committee_conditions='Умови' where id=?",
                object);
        change(lead, command("IC_PREPARATION")).andExpect(status().isOk());
        assertThat(jdbc.queryForMap(
                                "select investment_committee_decision,investment_committee_date,investment_committee_conditions from objects where id=?",
                                object)
                        .values())
                .containsOnlyNulls();
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_history where field_name='IC_CONDITIONS' and previous_value='Умови'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select previous_value from object_history where field_name='IC_LEGACY_CONDITIONS'",
                        String.class))
                .isEqualTo("Історичні умови договору понад поточний ліміт".repeat(3));
    }

    @Test
    void manualSigningAndOpeningEnforceSameRequiredFields() throws Exception {
        state("CONTRACT_APPROVAL");
        ObjectSigningTestData.ready(jdbc, object, manager);
        jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);
        change(lead, command("CONTRACT_SIGNED")).andExpect(status().isUnprocessableEntity());
        var body = command("CONTRACT_SIGNED");
        body.put("contractSignedDate", "2026-10-04");
        body.put("contractTypeId", contractType());
        change(lead, body).andExpect(status().isOk());
        change(lead, command("OPENED")).andExpect(status().isUnprocessableEntity());
        body = command("OPENED");
        body.put("openingDate", "2026-10-05");
        change(lead, body).andExpect(status().isOk());
    }
}
