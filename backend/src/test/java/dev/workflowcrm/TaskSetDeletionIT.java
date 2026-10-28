package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import dev.workflowcrm.tasks.api.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class TaskSetDeletionIT extends ObjectDeletionTestSupport {
    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    PlatformTransactionManager transactions;

    @Test
    void objectDeletionRemovesTaskSetsAndEdgesButPreservesGlobalTemplateVersion() throws Exception {
        UUID object = add("Набір задач", manager, region), template = UUID.randomUUID();
        UUID status = jdbc.queryForObject("select id from object_statuses where code='FOUND'", UUID.class);
        jdbc.update("insert into task_templates(id,trigger_status_id) values(?,?)", template, status);
        var planned = new ArrayList<TaskTransitionPlan.Task>();
        for (int i = 0; i < 2; i++) {
            UUID item = UUID.randomUUID();
            String name = "Задача " + i;
            jdbc.update(
                    "insert into task_template_items(id,template_id,name,assignee_id,deadline_offset_days,sort_order) values(?,?,?,?,2,?)",
                    item,
                    template,
                    name,
                    manager,
                    i);
            planned.add(new TaskTransitionPlan.Task(
                    item, name, manager, LocalDate.now().plusDays(2)));
        }
        var context =
                new ObjectTaskTransition.Context(object, manager, region, status, status, null, LocalDate.now(), true);
        new TransactionTemplate(transactions)
                .executeWithoutResult(tx -> publisher.publishEvent(new TaskGenerationRequested(
                        context, new TaskTransitionPlan(template, "test", planned, List.of()), manager)));
        var tasks = jdbc.queryForList("select id from tasks order by name", UUID.class);
        jdbc.update(
                "insert into task_dependencies(object_id,task_id,predecessor_id) values(?,?,?)",
                object,
                tasks.getLast(),
                tasks.getFirst());
        accepted(object);
        assertThat(processor.processNext()).isTrue();
        for (String table : List.of("objects", "tasks", "task_sets", "task_dependencies"))
            assertThat(count(table)).as(table).isZero();
        assertThat(count("task_templates")).isEqualTo(1);
        assertThat(count("task_template_versions")).isEqualTo(1);
        assertThat(count("template_items")).isEqualTo(2);
        var audit = json.readTree(jdbc.queryForObject(
                "select snapshot::text from object_deletion_audit where object_id=?", String.class, object));
        assertThat(audit.path("tasks")).hasSize(2);
        assertThat(audit.path("tasks").get(0).path("task_set_id").asText()).isNotBlank();
    }
}
