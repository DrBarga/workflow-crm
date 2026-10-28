package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class IcDecisionAssigneeChoicesIT extends IcDecisionTestSupport {
    @Test
    void committeeDecisionOffersCandidatesAndCreatesExactlyTheSelectedAssignment() throws Exception {
        UUID second = UUID.randomUUID();
        user(second, "REGIONAL_MANAGER", region);
        UUID template = template();
        UUID item = jdbc.queryForObject("select id from task_template_items where template_id=?", UUID.class, template);
        jdbc.update(
                "update task_template_items set assignment_rule='REGIONAL_ROLE',assignee_id=null,role_id=(select id from roles where code='REGIONAL_MANAGER') where id=?",
                item);
        var body = decision("APPROVED");
        var unresolved = response(decide(lead, body).andExpect(status().isOk()));
        assertThat(unresolved.path("saved").asBoolean()).isFalse();
        assertThat(unresolved.path("confirmationToken").isNull()).isTrue();
        assertThat(unresolved.at("/tasks/0/assignment/candidates").findValuesAsText("id"))
                .containsExactlyInAnyOrder(lead.toString(), second.toString());
        body.put("assignees", Map.of(item.toString(), second.toString()));
        var planned = response(decide(lead, body).andExpect(status().isOk()));
        body.put("confirmationToken", planned.path("confirmationToken").asText());
        var tampered = new LinkedHashMap<>(body);
        tampered.put("assignees", Map.of(item.toString(), lead.toString()));
        decide(lead, tampered).andExpect(status().isConflict());
        decide(lead, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(jdbc.queryForObject("select assignee_id from tasks where template_item_id=?", UUID.class, item))
                .isEqualTo(second);
        assertThat(state()).isEqualTo("COMMERCIAL_TERMS_APPROVAL");
    }

    @Test
    void unresolvedConfirmationCannotChangeCommitteeDecisionOrCreatePartialSet() throws Exception {
        UUID template = template();
        jdbc.update(
                "update task_template_items set assignment_rule='REGIONAL_ROLE',assignee_id=null,role_id=(select id from roles where code='REGIONAL_MANAGER') where template_id=?",
                template);
        user(UUID.randomUUID(), "REGIONAL_MANAGER", region);
        var body = decision("APPROVED");
        body.put("confirmationToken", "0".repeat(64));
        decide(lead, body).andExpect(status().isUnprocessableEntity());
        assertThat(state()).isEqualTo("IC_PREPARATION");
        assertThat(count("tasks")).isZero();
        assertThat(count("object_history")).isZero();
    }
}
