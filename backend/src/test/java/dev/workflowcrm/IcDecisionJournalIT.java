package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IcDecisionJournalIT extends IcDecisionTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "APPROVED_WITH_CONDITIONS", "REFUSED"})
    void automaticStatusChangeWritesAuditAndOutboxWithConfiguredTaskAction(String result) throws Exception {
        var task = task("IN_PROGRESS");
        var before = taskSnapshot();
        decide(lead, decision(result)).andExpect(status().isOk());
        if ("REFUSED".equals(result)) {
            assertThat(jdbc.queryForObject("select status from tasks where id=?", String.class, task))
                    .isEqualTo("CANCELLED");
            assertThat(count("task_history")).isEqualTo(1);
        } else assertThat(taskSnapshot()).isEqualTo(before);
        assertThat(decisionHistoryCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_status_audit where event_type='STATUS_CHANGED'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from event_outbox where event_type='OBJECT_STATUS_CHANGED'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_history where field_name='TASK_TEMPLATE_MISSING'", Integer.class))
                .isEqualTo("REFUSED".equals(result) ? 0 : 1);
    }
}
