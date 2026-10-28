package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectRefusalCancelAllIT extends ObjectStatusTasksSupport {
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
                "PAUSED"
            })
    void configuredRefusalCancelsEveryUnfinishedStageAndPreservesTerminalAndForeignTasks(String source)
            throws Exception {
        state(source);
        assertThat(jdbc.queryForList(
                        "select unfinished_task_action from object_status_transitions where is_active and is_refusal_entry and not is_system_only",
                        String.class))
                .isNotEmpty()
                .containsOnly("CANCEL_ALL");
        var unfinished = new ArrayList<UUID>();
        for (String value : List.of("NEW", "IN_PROGRESS", "PAUSED", "WAITING_APPROVAL")) unfinished.add(task(value));
        jdbc.update(
                "update tasks set source_status_id=? where id in (?,?)",
                statusId("FOUND"),
                unfinished.get(0),
                unfinished.get(1));
        jdbc.update(
                "update tasks set source_status_id=? where id in (?,?)",
                statusId("CONTRACT_APPROVAL"),
                unfinished.get(2),
                unfinished.get(3));
        var done = task("DONE");
        var cancelled = task("CANCELLED");
        var terminal = snapshots(done, cancelled);
        UUID foreignObject = UUID.randomUUID();
        jdbc.update(
                "insert into objects(id,name,region_id,manager_id,author_id,status_id,entrance_longitude,entrance_latitude,object_area) select ?,'Інший об’єкт',region_id,manager_id,author_id,status_id,30.6,50.5,200 from objects where id=?",
                foreignObject,
                object);
        UUID foreignTask = task("IN_PROGRESS");
        jdbc.update("update tasks set object_id=? where id=?", foreignObject, foreignTask);
        var foreign = snapshots(foreignTask);
        var command = command("REFUSED");
        command.put("refusalReasonId", reason());
        change(lead, command).andExpect(status().isOk());
        assertThat(state()).isEqualTo("REFUSED");
        assertThat(jdbc.queryForObject(
                        "select count(*) from tasks where object_id=? and status not in ('DONE','CANCELLED')",
                        Integer.class,
                        object))
                .isZero();
        assertThat(snapshots(done, cancelled)).isEqualTo(terminal);
        assertThat(snapshots(foreignTask)).isEqualTo(foreign);
        assertThat(jdbc.queryForObject("select count(*) from task_history where new_status='CANCELLED'", Integer.class))
                .isEqualTo(4);
        assertThat(events("TASK_CANCELLED")).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                        "select count(*) from user_notifications where event_type='TASK_CANCELLED' and recipient_id=?",
                        Integer.class,
                        manager))
                .isEqualTo(4);
        change(lead, command).andExpect(status().isConflict());
        assertThat(events("TASK_CANCELLED")).isEqualTo(4);
    }

    @Test
    void refusalWithoutUnfinishedTasksDoesNotProduceCancellationEvents() throws Exception {
        task("DONE");
        task("CANCELLED");
        var before = taskSnapshot();
        var command = command("REFUSED");
        command.put("refusalReasonId", reason());
        change(manager, command).andExpect(status().isOk());
        assertThat(taskSnapshot()).isEqualTo(before);
        assertThat(events("TASK_CANCELLED")).isZero();
    }

    private List<String> snapshots(UUID... ids) {
        return new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc)
                .queryForList(
                        "select to_jsonb(t)::text from tasks t where id in (:ids) order by id",
                        Map.of("ids", List.of(ids)),
                        String.class);
    }
}
