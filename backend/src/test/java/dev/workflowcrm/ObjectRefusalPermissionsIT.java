package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectRefusalPermissionsIT extends ObjectStatusTasksSupport {
    @ParameterizedTest
    @CsvSource({
        "FOUND,MANAGER",
        "IN_PROGRESS,MANAGER",
        "PAUSED,MANAGER",
        "FOUND,REGIONAL_MANAGER",
        "IN_PROGRESS,DEVELOPMENT_DIRECTOR",
        "IC_PREPARATION,REGIONAL_MANAGER",
        "IC_PREPARATION,DEVELOPMENT_DIRECTOR",
        "COMMERCIAL_TERMS_APPROVAL,REGIONAL_MANAGER",
        "COMMERCIAL_TERMS_APPROVAL,DEVELOPMENT_DIRECTOR",
        "CONTRACT_APPROVAL,REGIONAL_MANAGER",
        "CONTRACT_APPROVAL,DEVELOPMENT_DIRECTOR",
        "CONTRACT_SIGNED,REGIONAL_MANAGER",
        "CONTRACT_SIGNED,DEVELOPMENT_DIRECTOR",
        "OPENED,REGIONAL_MANAGER",
        "OPENED,DEVELOPMENT_DIRECTOR",
        "PAUSED,REGIONAL_MANAGER"
    })
    void configuredRolesCanRefuseAtTheirStage(String source, String role) throws Exception {
        state(source);
        var body = command("REFUSED");
        body.put("refusalReasonId", reason());
        change(actor(role), body).andExpect(status().isOk());
        assertThat(state()).isEqualTo("REFUSED");
        assertThat(version()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "IC_PREPARATION",
                "COMMERCIAL_TERMS_APPROVAL",
                "CONTRACT_APPROVAL",
                "CONTRACT_SIGNED",
                "OPENED",
                "PAUSED"
            })
    void managerReceives422AfterCommitteeStageWithoutSideEffects(String source) throws Exception {
        state(source);
        jdbc.update("update objects set status_before_pause_id=? where id=?", statusId("IC_PREPARATION"), object);
        task("IN_PROGRESS");
        var tasks = taskSnapshot();
        var body = command("REFUSED");
        body.put("refusalReasonId", reason());
        change(manager, body).andExpect(status().isUnprocessableEntity());
        assertThat(state()).isEqualTo(source);
        assertThat(version()).isZero();
        assertThat(taskSnapshot()).isEqualTo(tasks);
        assertThat(count("object_history")).isZero();
        assertThat(events("OBJECT_STATUS_CHANGED")).isZero();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "FOUND",
                "IN_PROGRESS",
                "IC_PREPARATION",
                "COMMERCIAL_TERMS_APPROVAL",
                "CONTRACT_APPROVAL",
                "CONTRACT_SIGNED",
                "OPENED",
                "PAUSED",
                "REFUSED"
            })
    void contractRoleReceives403AtEveryStage(String source) throws Exception {
        state(source);
        var body = command("REFUSED");
        body.put("refusalReasonId", reason());
        change(contract, body).andExpect(status().isForbidden());
        assertThat(state()).isEqualTo(source);
        assertThat(version()).isZero();
    }
}
