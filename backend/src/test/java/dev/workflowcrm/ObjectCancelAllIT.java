package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ObjectCancelAllIT extends IcDecisionTestSupport {
    @AfterEach
    void restorePolicy() {
        policy("NONE");
    }

    @Test
    void cancelsUnfinishedTasksFromEveryStageAndPreservesTerminalTasksAndOtherObjects() throws Exception {
        policy("CANCEL_ALL");
        UUID current = task("NEW"), earlier = task("PAUSED"), done = task("DONE"), cancelled = task("CANCELLED");
        jdbc.update(
                "update tasks set source_status_id=(select id from object_statuses where code='FOUND') where id=?",
                earlier);
        var terminal = jdbc.queryForList(
                "select to_jsonb(t)::text from tasks t where id in (?,?) order by id", String.class, done, cancelled);
        UUID other = UUID.randomUUID();
        jdbc.update(
                "insert into objects(id,name,region_id,manager_id,author_id,status_id,entrance_longitude,entrance_latitude,object_area) select ?,'Інший об’єкт',region_id,manager_id,author_id,status_id,30.6,50.5,200 from objects where id=?",
                other,
                object);
        UUID foreignTask = task("IN_PROGRESS");
        jdbc.update("update tasks set object_id=? where id=?", other, foreignTask);
        decide(director, decision("APPROVED")).andExpect(status().isOk());
        assertThat(jdbc.queryForList("select status from tasks where id in (?,?)", String.class, current, earlier))
                .containsOnly("CANCELLED")
                .hasSize(2);
        assertThat(jdbc.queryForList(
                        "select to_jsonb(t)::text from tasks t where id in (?,?) order by id",
                        String.class,
                        done,
                        cancelled))
                .isEqualTo(terminal);
        assertThat(jdbc.queryForObject("select status from tasks where id=?", String.class, foreignTask))
                .isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("select count(*) from task_history where new_status='CANCELLED'", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "select count(*) from event_outbox where event_type='TASK_CANCELLED'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void existingCancelKeepsEarlierStageTaskWhileCancellingCurrentStage() throws Exception {
        policy("CANCEL");
        UUID current = task("NEW"), earlier = task("PAUSED");
        jdbc.update(
                "update tasks set source_status_id=(select id from object_statuses where code='FOUND') where id=?",
                earlier);
        UUID set = UUID.randomUUID();
        jdbc.update(
                "insert into task_sets(id,object_id,trigger_status_id) select ?,?,status_id from objects where id=?",
                set,
                object,
                object);
        jdbc.update("update tasks set task_set_id=? where id=?", set, current);
        decide(director, decision("APPROVED")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select status from tasks where id=?", String.class, current))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select status from tasks where id=?", String.class, earlier))
                .isEqualTo("PAUSED");
    }

    private void policy(String action) {
        jdbc.update("update object_status_transitions set unfinished_task_action=? where code='IC_APPROVED'", action);
    }
}
