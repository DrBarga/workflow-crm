package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

class ObjectEditContractRoutesIT extends ObjectStatusTasksSupport {
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "REGIONAL_MANAGER,status",
        "REGIONAL_MANAGER,milestones",
        "DEVELOPMENT_DIRECTOR,status",
        "DEVELOPMENT_DIRECTOR,milestones"
    })
    void statusAndMilestoneActionsCanChooseTypeWithoutContractTemplateSet(String role, String route) throws Exception {
        ObjectContractTaskSetTestData.create(jdbc, object, null, null, "COMPLETED");
        state("CONTRACT_APPROVAL");
        ObjectSigningTestData.ready(jdbc, object, manager);
        var body = new LinkedHashMap<String, Object>(
                Map.of("version", version(), "contractSignedDate", "2026-10-07", "contractTypeId", contractType()));
        if (route.equals("status")) {
            body.put("status", "CONTRACT_SIGNED");
            change(actor(role), body).andExpect(status().isOk());
        } else milestones(actor(role), body).andExpect(status().isOk());
        assertThat(state()).isEqualTo("CONTRACT_SIGNED");
        assertThat(jdbc.queryForObject("select contract_type_id from objects where id=?", UUID.class, object))
                .isEqualTo(contractType());
    }

    @Test
    void statusActionCannotBypassManagerCommissioningRestriction() throws Exception {
        var body = command("IN_PROGRESS");
        body.put("commissioningDate", "2026-10-23");
        change(manager, body).andExpect(status().isForbidden());
        assertThat(version()).isZero();
        assertThat(state()).isEqualTo("FOUND");
    }

    @Test
    void preselectedTypeCanBeUsedByContractRoleWhenSigning() throws Exception {
        state("CONTRACT_APPROVAL");
        jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);
        ObjectSigningTestData.ready(jdbc, object, manager);
        milestones(
                        contract,
                        Map.of(
                                "version",
                                version(),
                                "contractSignedDate",
                                "2026-10-07",
                                "contractTypeId",
                                contractType()))
                .andExpect(status().isOk());
        assertThat(state()).isEqualTo("CONTRACT_SIGNED");
    }

    @Test
    void oldActionsCannotReplaceTypeAfterCompletedContractTemplateSet() throws Exception {
        ObjectContractTaskSetTestData.create(jdbc, object, contractType(), null, "COMPLETED");
        state("CONTRACT_APPROVAL");
        jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);
        UUID other = jdbc.queryForObject("select id from contract_types where code='PRELIMINARY'", UUID.class);
        ObjectSigningTestData.ready(jdbc, object, manager);
        for (UUID actor : List.of(lead, director, contract)) {
            var body = new LinkedHashMap<String, Object>(
                    Map.of("version", version(), "contractSignedDate", "2026-10-07", "contractTypeId", other));
            milestones(actor, body).andExpect(status().isUnprocessableEntity());
            body.put("status", "CONTRACT_SIGNED");
            change(actor, body).andExpect(status().isUnprocessableEntity());
        }
        assertThat(version()).isZero();
        milestones(
                        lead,
                        Map.of(
                                "version",
                                version(),
                                "contractSignedDate",
                                "2026-10-07",
                                "contractTypeId",
                                contractType()))
                .andExpect(status().isOk());
        assertThat(state()).isEqualTo("CONTRACT_SIGNED");
    }
}
