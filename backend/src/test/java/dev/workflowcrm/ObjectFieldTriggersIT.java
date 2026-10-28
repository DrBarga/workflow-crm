package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.workflowcrm.tasks.api.StatusTaskTriggerQuery;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ObjectFieldTriggersIT extends IcDecisionTestSupport {
    @Autowired
    StatusTaskTriggerQuery triggers;

    @Test
    void determinesTriggersFromAnyActiveTemplateAndReflectsDeactivationImmediately() {
        UUID status = jdbc.queryForObject("select id from object_statuses where code='FOUND'", UUID.class);
        assertThat(triggers.isTrigger(status)).isFalse();
        UUID template = UUID.randomUUID();
        jdbc.update("insert into task_templates(id,trigger_status_id) values (?,?)", template, status);
        assertThat(triggers.isTrigger(status)).isTrue();
        jdbc.update("update task_templates set is_active=false where id=?", template);
        assertThat(triggers.isTrigger(status)).isFalse();
        jdbc.update("update task_templates set is_active=true where id=?", template);
        assertThat(triggers.isTrigger(status)).isTrue();
    }

    @Test
    void inactiveTemplateDoesNotRequireConfirmationOrCreateTasks() throws Exception {
        var template = template();
        jdbc.update("update task_templates set is_active=false where id=?", template);
        decide(lead, decision("APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(count("tasks")).isZero();
    }

    @Test
    void dictionaryContractTypeSelectsMatchingTemplateAndRequiresConfirmation() throws Exception {
        var template = template();
        jdbc.update(
                "update objects set contract_type_id=(select id from contract_types where code='PRIMARY') where id=?",
                object);
        jdbc.update("update task_templates set contract_type='PRIMARY' where id=?", template);
        var body = decision("APPROVED");
        var preview = response(decide(lead, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false)));
        body.put("confirmationToken", preview.path("confirmationToken").asText());
        decide(lead, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(count("tasks")).isEqualTo(1);
    }

    @Test
    void retainsHistoricalObjectTypeWithoutOfferingItForNewObjects() {
        assertThat(jdbc.queryForList("select name from object_types where is_active", String.class))
                .containsExactlyInAnyOrder("Земельна ділянка", "Вбудоване приміщення", "ТЦ", "Ангар");
        assertThat(jdbc.queryForObject(
                        "select is_active from object_types where code='RESIDENTIAL_GROUND_FLOOR'", Boolean.class))
                .isFalse();
        assertThat(jdbc.queryForList("select name from contract_types where is_active", String.class))
                .containsExactlyInAnyOrder("Основний", "Попередній");
    }
}
