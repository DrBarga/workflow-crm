package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;

class ObjectStatusSecurityIT extends ObjectStatusTestSupport {
    @ParameterizedTest
    @CsvSource({
        "SUPERADMIN,403",
        "CEO,403",
        "CONTRACT_MANAGER,403",
        "MANAGER,200",
        "REGIONAL_MANAGER,200",
        "DEVELOPMENT_DIRECTOR,200"
    })
    void evaluatesRoleFromDatabase(String role, int expected) throws Exception {
        change(actor(role), command("IN_PROGRESS")).andExpect(status().is(expected));
        assertThat(state()).isEqualTo(expected == 200 ? "IN_PROGRESS" : "FOUND");
    }

    @Test
    void rejectsForeignOwnerAndRegion() throws Exception {
        for (var actor : java.util.List.of(colleague, outsider, foreignLead))
            change(actor, command("IN_PROGRESS")).andExpect(status().isForbidden());
        assertThat(state()).isEqualTo("FOUND");
    }

    @Test
    void disabledUserCannotTransitionEvenWithExistingJwt() throws Exception {
        jdbc.update("update users set status='DISABLED' where id=?", manager);
        change(manager, command("IN_PROGRESS")).andExpect(status().isForbidden());
    }

    @Test
    void systemOnlyAndUnconfiguredTransitionsAreRejected() throws Exception {
        state("IC_PREPARATION");
        change(lead, command("COMMERCIAL_TERMS_APPROVAL")).andExpect(status().isForbidden());
        change(lead, command("OPENED")).andExpect(status().isUnprocessableEntity());
        assertThat(version()).isZero();
    }

    @Test
    void contractRoleCannotManuallyRefuseAnObject() throws Exception {
        state("CONTRACT_APPROVAL");
        var body = command("REFUSED");
        body.put("refusalReasonId", reason().toString());
        change(contract, body).andExpect(status().isForbidden());
    }

    @Test
    void managerCannotRestoreLateRefusal() throws Exception {
        state("REFUSED");
        jdbc.update("update objects set status_before_refusal_id=? where id=?", statusId("IC_PREPARATION"), object);
        change(manager, command("FOUND")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void authenticationAndCsrfAreRequired() throws Exception {
        mvc.perform(post("/api/objects/" + object + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(command("IN_PROGRESS"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/objects/" + object + "/status")
                        .cookie(cookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(command("IN_PROGRESS"))))
                .andExpect(status().isForbidden());
    }
}
