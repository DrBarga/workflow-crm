package dev.workflowcrm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.workflowcrm.tasks.api.*;
import dev.workflowcrm.tasks.domain.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

abstract class TaskDependencyTestSupport extends ObjectStatusTasksSupport {
    @Autowired
    PlatformTransactionManager transactions;

    @Autowired
    TaskObjectContext taskObjects;

    @Autowired
    TaskRuntimeStore runtime;

    @Autowired
    TaskTransitionStore transitions;

    @Autowired
    TaskDependencies dependencies;

    @Autowired
    ApplicationEventPublisher publisher;

    UUID set() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into task_sets(id,object_id,trigger_status_id) values (?,?,?)", id, object, statusId("FOUND"));
        return id;
    }

    UUID node(UUID set, String status) {
        UUID id = task(status);
        jdbc.update("update tasks set task_set_id=?,needs_review=false where id=?", set, id);
        return id;
    }

    void depends(UUID task, UUID... predecessors) {
        for (UUID predecessor : predecessors)
            jdbc.update(
                    "insert into task_dependencies(object_id,task_id,predecessor_id) values (?,?,?)",
                    object,
                    task,
                    predecessor);
    }

    String taskStatus(UUID task) {
        return jdbc.queryForObject("select status from tasks where id=?", String.class, task);
    }

    long taskVersion(UUID task) {
        return jdbc.queryForObject("select version from tasks where id=?", Long.class, task);
    }

    String setState(UUID set) {
        return jdbc.queryForObject("select state from task_sets where id=?", String.class, set);
    }

    ResultActions complete(UUID actor, UUID task, long version) throws Exception {
        return mvc.perform(post("/api/objects/{id}/tasks/{task}/complete", object, task)
                .cookie(cookie(actor), csrf)
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("version", version))));
    }

    void cancelled(UUID task) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            taskObjects.lock(object);
            var node = runtime.lock(object).require(task);
            transitions.cancel(
                    List.of(new TaskTransitionPlan.ExistingTask(
                            node.id(), node.version(), node.name(), node.status(), node.assigneeId())),
                    manager);
        });
    }

    void finished(UUID task, UUID actor) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            taskObjects.lock(object);
            var node = runtime.lock(object).require(task);
            publisher.publishEvent(runtime.change(object, node, "DONE", actor, "USER"));
        });
    }

    void recheck() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> dependencies.synchronize(object, manager));
    }
}
