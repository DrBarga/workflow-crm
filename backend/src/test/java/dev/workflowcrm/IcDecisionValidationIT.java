package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class IcDecisionValidationIT extends IcDecisionTestSupport {
    @Test
    void conditionsRequiredForConditionalApproval() throws Exception {
        var body = decision("APPROVED_WITH_CONDITIONS");
        for (String value : List.of("", "  ", "\t\n")) {
            body.put("conditions", value);
            decide(lead, body).andExpect(status().isUnprocessableEntity());
        }
        body.remove("conditions");
        decide(lead, body).andExpect(status().isUnprocessableEntity());
        assertThat(state()).isEqualTo("IC_PREPARATION");
        assertThat(count("object_history")).isZero();
    }

    @Test
    void dateRequiredForEveryDecisionAndMustBeValid() throws Exception {
        for (String value : List.of("APPROVED", "APPROVED_WITH_CONDITIONS", "REFUSED")) {
            var body = decision(value);
            body.remove("date");
            decide(lead, body).andExpect(status().isUnprocessableEntity());
            body.put("date", "2026-02-30");
            decide(lead, body).andExpect(status().isUnprocessableEntity());
        }
        assertThat(count("object_history")).isZero();
    }

    @Test
    void otherStatusRejectedWithoutMutation() throws Exception {
        jdbc.update(
                "update objects set status_id=(select id from object_statuses where code='IN_PROGRESS') where id=?",
                object);
        decide(lead, decision("APPROVED")).andExpect(status().isUnprocessableEntity());
        assertThat(state()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void roleAndRegionRestrictionsUseCurrentDatabaseMembership() throws Exception {
        for (String role : List.of("MANAGER", "CEO", "SUPERADMIN", "CONTRACT_MANAGER")) {
            jdbc.update("update users set role_id=(select id from roles where code=?) where id=?", role, lead);
            decide(lead, decision("APPROVED")).andExpect(status().isForbidden());
        }
        jdbc.update("update users set role_id=(select id from roles where code='REGIONAL_MANAGER') where id=?", lead);
        UserRegionTestData.replace(jdbc, lead, otherRegion);
        decide(lead, decision("APPROVED")).andExpect(status().isForbidden());
        assertThat(count("object_history")).isZero();
    }

    @Test
    void regionalLeadCanDecideOnlyForAssignedRegions() throws Exception {
        UserRegionTestData.replace(jdbc, lead, region);
        decide(lead, decision("APPROVED")).andExpect(status().isOk());
        jdbc.update(
                "update objects set region_id=?,status_id=(select id from object_statuses where code='IC_PREPARATION'),version=0 where id=?",
                otherRegion,
                object);
        UserRegionTestData.replace(jdbc, lead, region);
        decide(lead, decision("APPROVED")).andExpect(status().isForbidden());
        assertThat(decisionHistoryCount()).isEqualTo(2);
    }

    @Test
    void versionUnknownFieldsTypesAndExtraConditionsRejected() throws Exception {
        var body = decision("APPROVED");
        body.put("version", 1);
        decide(lead, body).andExpect(status().isConflict());
        body.put("version", "0");
        decide(lead, body).andExpect(status().isUnprocessableEntity());
        body.put("version", 0);
        body.put("conditions", "irrelevant");
        decide(lead, body).andExpect(status().isUnprocessableEntity());
        body.remove("conditions");
        body.put("refusalReason", "IC_NOT_APPROVED");
        decide(lead, body).andExpect(status().isUnprocessableEntity());
        assertThat(count("object_history")).isZero();
    }
}
