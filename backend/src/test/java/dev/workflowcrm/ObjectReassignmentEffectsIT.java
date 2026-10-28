package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;

class ObjectReassignmentEffectsIT extends ObjectReassignmentTestSupport {
    @Test
    void recordsOwnerHistoryJournalAndTwoNotificationsWithOneVersion() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        UUID task = task(object, manager, "NEW");
        var result = response(reassign(lead, object, colleague).andExpect(status().isOk()));
        assertThat(result.path("version").asLong()).isEqualTo(1);
        assertThat(version(object)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select previous_value from object_history where field_name='managerId'", String.class))
                .isEqualTo(manager.toString());
        assertThat(jdbc.queryForObject(
                        "select new_value from object_history where field_name='managerId'", String.class))
                .isEqualTo(colleague.toString());
        assertThat(jdbc.queryForObject("select author_id from object_history", UUID.class))
                .isEqualTo(lead);
        assertThat(jdbc.queryForObject("select object_version from object_history", Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForList(
                        "select recipient_id from user_notifications where event_type='OBJECT_MANAGER_REASSIGNED'",
                        UUID.class))
                .containsExactlyInAnyOrder(manager, colleague);
        assertThat(count("object_status_audit")).isEqualTo(1);
        assertThat(count("event_outbox")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select task_id from task_assignment_history", UUID.class))
                .isEqualTo(task);
        assertThat(jdbc.queryForObject("select author_id from objects where id=?", UUID.class, object))
                .isEqualTo(manager);
        assertThat(jdbc.queryForObject("select region_id from objects where id=?", UUID.class, object))
                .isEqualTo(region);
    }

    @Test
    void ownershipChangesVisibilityAndInvalidatesPointRequest() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        state(object, "IC_PREPARATION");
        UUID pending = request(manager, object);
        reassign(lead, object, colleague).andExpect(status().isOk());
        assertThat(requestStatus(pending)).isEqualTo("STALE");
        assertThat(card(manager, object).has("address")).isFalse();
        assertThat(card(colleague, object).has("address")).isTrue();
        patch(manager, object, change(object, "name", "Чужа зміна")).andExpect(status().isForbidden());
        patch(colleague, object, change(object, "name", "Нова назва")).andExpect(status().isOk());
    }

    @Test
    void failureOfNotificationRollsBackOwnerTasksHistoryAndOutbox() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        UUID task = task(object, manager, "NEW");
        jdbc.execute(
                "alter table user_notifications add constraint test_reassignment_notify check(event_type<>'OBJECT_MANAGER_REASSIGNED')");
        try {
            reassign(lead, object, colleague).andExpect(status().isInternalServerError());
            assertThat(owner(object)).isEqualTo(manager);
            assertThat(version(object)).isZero();
            assertThat(assignee(task)).isEqualTo(manager);
            assertThat(transferred(task)).isFalse();
            for (String table : List.of(
                    "object_history",
                    "object_status_audit",
                    "task_assignment_history",
                    "event_outbox",
                    "user_notifications")) assertThat(count(table)).isZero();
        } finally {
            jdbc.execute("alter table user_notifications drop constraint test_reassignment_notify");
        }
    }

    @Test
    void failureOnSecondTaskRollsBackFirstTaskAsWell() throws Exception {
        UUID object = add("Об’єкт", manager, region);
        UUID first = task(object, manager, "NEW"), second = task(object, manager, "IN_PROGRESS");
        UUID last = jdbc.queryForObject(
                "select id from tasks where object_id=? order by id desc limit 1", UUID.class, object);
        jdbc.execute("alter table task_assignment_history add constraint test_reassignment_task check(task_id<>'" + last
                + "')");
        try {
            reassign(lead, object, colleague).andExpect(status().isInternalServerError());
            assertThat(owner(object)).isEqualTo(manager);
            assertThat(assignee(first)).isEqualTo(manager);
            assertThat(assignee(second)).isEqualTo(manager);
            assertThat(count("task_assignment_history")).isZero();
            assertThat(count("event_outbox")).isZero();
        } finally {
            jdbc.execute("alter table task_assignment_history drop constraint test_reassignment_task");
        }
    }
}
