package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ObjectStatusMatrixIT extends ObjectStatusTestSupport {
    @ParameterizedTest
    @CsvSource({
        "FOUND,IN_PROGRESS,MANAGER",
        "FOUND,IN_PROGRESS,REGIONAL_MANAGER",
        "FOUND,IN_PROGRESS,DEVELOPMENT_DIRECTOR",
        "IN_PROGRESS,FOUND,MANAGER",
        "IN_PROGRESS,FOUND,REGIONAL_MANAGER",
        "PAUSED,FOUND,MANAGER",
        "PAUSED,FOUND,DEVELOPMENT_DIRECTOR",
        "FOUND,REFUSED,MANAGER",
        "IN_PROGRESS,REFUSED,REGIONAL_MANAGER",
        "FOUND,IC_PREPARATION,REGIONAL_MANAGER",
        "IN_PROGRESS,IC_PREPARATION,DEVELOPMENT_DIRECTOR",
        "IC_PREPARATION,REFUSED,REGIONAL_MANAGER",
        "CONTRACT_APPROVAL,REFUSED,DEVELOPMENT_DIRECTOR",
        "COMMERCIAL_TERMS_APPROVAL,IC_PREPARATION,REGIONAL_MANAGER",
        "CONTRACT_APPROVAL,COMMERCIAL_TERMS_APPROVAL,DEVELOPMENT_DIRECTOR",
        "REFUSED,FOUND,MANAGER",
        "REFUSED,IN_PROGRESS,MANAGER",
        "REFUSED,PAUSED,MANAGER",
        "REFUSED,IC_PREPARATION,DEVELOPMENT_DIRECTOR",
        "REFUSED,CONTRACT_APPROVAL,REGIONAL_MANAGER",
        "FOUND,PAUSED,MANAGER",
        "IN_PROGRESS,PAUSED,REGIONAL_MANAGER",
        "IC_PREPARATION,PAUSED,DEVELOPMENT_DIRECTOR",
        "PAUSED,IN_PROGRESS,MANAGER"
    })
    void appliesConfiguredManualTransition(String source, String target, String role) throws Exception {
        state(source);
        var command = command(target);
        if ("REFUSED".equals(target)) command.put("refusalReasonId", reason().toString());
        var result = response(change(actor(role), command).andExpect(status().isOk()));
        assertThat(result.path("saved").asBoolean()).isTrue();
        assertThat(state()).isEqualTo(target);
        assertThat(version()).isEqualTo(1);
        assertThat(count("object_status_audit")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void newStatusAndNewRuleWorkWithoutCodeChanges() throws Exception {
        UUID custom = UUID.randomUUID(), rule = UUID.randomUUID();
        jdbc.update(
                "insert into object_statuses(id,code,name,sort_order) values (?,'CUSTOM_STAGE','Додатковий етап',999)",
                custom);
        jdbc.update(
                """
                insert into object_status_transitions(id,code,from_status_id,to_status_id)
                values (?,'CUSTOM_RULE',?,?)
                """,
                rule,
                statusId("FOUND"),
                custom);
        jdbc.update(
                """
                insert into object_transition_roles(transition_id,role_id)
                select ?,id from roles where code='MANAGER'
                """,
                rule);
        try {
            change(manager, command("CUSTOM_STAGE")).andExpect(status().isOk());
            assertThat(state()).isEqualTo("CUSTOM_STAGE");
        } finally {
            ObjectTestCleanup.erase(jdbc, () -> jdbc.update("delete from object_history where object_id=?", object));
            jdbc.update("delete from object_creation_requests where object_id=?", object);
            ObjectTestCleanup.erase(jdbc, () -> jdbc.update("delete from objects where id=?", object));
            jdbc.update("delete from object_transition_roles where transition_id=?", rule);
            jdbc.update("delete from object_status_transitions where id=?", rule);
            jdbc.update("delete from object_statuses where id=?", custom);
        }
    }
}
