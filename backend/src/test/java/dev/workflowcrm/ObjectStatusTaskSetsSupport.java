package dev.workflowcrm;

import java.util.UUID;

abstract class ObjectStatusTaskSetsSupport extends ObjectStatusTasksSupport {
    UUID set(String source, String state) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into task_sets(id,object_id,trigger_status_id,state) values(?,?,?,?)",
                id,
                object,
                statusId(source),
                state);
        return id;
    }

    UUID assignedTask(UUID set, String source, String state) {
        UUID id = task(state);
        jdbc.update("update tasks set task_set_id=?,source_status_id=? where id=?", set, statusId(source), id);
        return id;
    }

    String taskState(UUID task) {
        return jdbc.queryForObject("select status from tasks where id=?", String.class, task);
    }
}
