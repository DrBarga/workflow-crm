package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import dev.workflowcrm.tasks.api.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class TaskSnapshotTransactionIT extends ObjectStatusTasksSupport {
    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    PlatformTransactionManager transactions;

    @Test
    void failedTransactionLeavesNoSetVersionTasksOrGenerationEvent() {
        var event = event();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                    events.publishEvent(event);
                    throw new IllegalStateException("Simulated interruption");
                }))
                .isInstanceOf(IllegalStateException.class);
        for (String table : List.of("task_sets", "task_template_versions", "template_items", "tasks"))
            assertThat(jdbc.queryForObject("select count(*) from " + table, Integer.class))
                    .isZero();
        assertThat(events("TASK_GENERATION_REQUESTED")).isZero();
    }

    @Test
    void simultaneousCaptureReusesOneCompleteVersion() throws Exception {
        var event = event();
        var contexts = new ArrayList<ObjectTaskTransition.Context>();
        for (int index = 0; index < 4; index++) {
            UUID id = index == 0 ? object : UUID.randomUUID();
            if (index > 0)
                jdbc.update(
                        """
                    insert into objects(id,name,object_type_id,address,manager_id,region_id,status_id,
                        entrance_longitude,entrance_latitude,object_area,author_id)
                    select ?,'Об’єкт для перевірки версії',object_type_id,address,manager_id,region_id,status_id,
                        entrance_longitude,entrance_latitude,object_area,author_id from objects where id=?
                    """,
                        id,
                        object);
            var original = event.context();
            contexts.add(new ObjectTaskTransition.Context(
                    id,
                    manager,
                    region,
                    original.fromStatusId(),
                    original.toStatusId(),
                    original.contractType(),
                    original.startDate(),
                    true));
        }
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Future<?>> jobs = new ArrayList<>();
            for (var context : contexts)
                jobs.add(executor.submit(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    new TransactionTemplate(transactions)
                            .executeWithoutResult(tx ->
                                    events.publishEvent(new TaskGenerationRequested(context, event.plan(), manager)));
                }));
            start.countDown();
            for (var job : jobs) job.get(30, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("select count(*) from task_template_versions", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from template_items", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(distinct template_version_id) from tasks", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from task_sets", Integer.class))
                .isEqualTo(4);
    }

    private TaskGenerationRequested event() {
        UUID template = template("IN_PROGRESS", null);
        UUID item = jdbc.queryForObject("select id from task_template_items where template_id=?", UUID.class, template);
        var context = new ObjectTaskTransition.Context(
                object, manager, region, statusId("FOUND"), statusId("IN_PROGRESS"), null, LocalDate.now(), true);
        var task = new TaskTransitionPlan.Task(
                item, "Перевірити об’єкт", manager, LocalDate.now().plusDays(3));
        return new TaskGenerationRequested(
                context, new TaskTransitionPlan(template, "test", List.of(task), List.of()), manager);
    }
}
