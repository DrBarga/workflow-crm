package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IcDecisionTaskSetsIT extends IcDecisionTestSupport {
    @Test
    void refusalCancelsEveryUnfinishedTaskAndNotifiesAssignees() throws Exception {
        for (String state : java.util.List.of("NEW", "IN_PROGRESS", "PAUSED", "WAITING_APPROVAL", "DONE", "CANCELLED"))
            task(state);
        jdbc.update(
                "update tasks set source_status_id=(select id from object_statuses where code='FOUND') where status='PAUSED'");
        decide(lead, decision("REFUSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(jdbc.queryForObject("select count(*) from tasks where status='CANCELLED'", Integer.class))
                .isEqualTo(5);
        assertThat(count("task_history")).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                        "select count(*) from event_outbox where event_type='TASK_CANCELLED'", Integer.class))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject(
                        "select count(*) from user_notifications where event_type='TASK_CANCELLED' and recipient_id=?",
                        Integer.class,
                        manager))
                .isEqualTo(4);
    }

    @Test
    void approvalReusesActiveCommercialSetWithoutPreviewOrMissingTemplateAlert() throws Exception {
        UUID set = UUID.randomUUID();
        jdbc.update(
                "insert into task_sets(id,object_id,trigger_status_id) select ?,?,id from object_statuses where code='COMMERCIAL_TERMS_APPROVAL'",
                set,
                object);
        template();
        jdbc.update("update task_template_items set assignee_id=?", colleague);
        jdbc.update("update users set status='DISABLED' where id=?", colleague);
        decide(lead, decision("APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.tasks").isEmpty());
        assertThat(count("task_sets")).isEqualTo(1);
        assertThat(count("tasks")).isZero();
        assertThat(jdbc.queryForList("select event_type from event_outbox", String.class))
                .doesNotContain("TASK_GENERATION_REQUESTED", "OBJECT_TASK_TEMPLATE_MISSING");
    }
}
