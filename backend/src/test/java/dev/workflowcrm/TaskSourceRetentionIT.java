package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import dev.workflowcrm.tasks.api.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class TaskSourceRetentionIT extends ObjectStatusTasksSupport {
    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    PlatformTransactionManager transactions;

    @Test
    void savingConfirmedPlanDoesNotIncludeNewUnconfirmedDraftItem() {
        var event = event();
        jdbc.update(
                "insert into task_template_items(template_id,name,assignee_id,deadline_offset_days,sort_order) values(?,'Не підтверджено',?,4,1)",
                event.plan().templateId(),
                manager);
        generate(event);
        assertThat(count("task_template_items")).isEqualTo(2);
        assertThat(count("template_items")).isEqualTo(1);
        assertThat(count("tasks")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select name from template_items", String.class))
                .isEqualTo("Підтверджена задача");
    }

    @Test
    void deletingDraftItemPreservesSavedSourceAndUsedTemplateCannotBeDeleted() {
        var event = event();
        generate(event);
        var source = jdbc.queryForList("select to_jsonb(i)::text from template_items i", String.class);
        jdbc.update("delete from task_template_items");
        assertThat(jdbc.queryForList("select to_jsonb(i)::text from template_items i", String.class))
                .isEqualTo(source);
        assertThat(jdbc.queryForObject(
                        "select count(*) from tasks where template_item_id is null and source_item_id is not null",
                        Integer.class))
                .isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("delete from task_templates"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("tasks")).isEqualTo(1);
    }

    private void generate(TaskGenerationRequested event) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> publisher.publishEvent(event));
    }

    private TaskGenerationRequested event() {
        UUID template = template("IN_PROGRESS", null);
        UUID item = jdbc.queryForObject("select id from task_template_items where template_id=?", UUID.class, template);
        var context = new ObjectTaskTransition.Context(
                object, manager, region, statusId("FOUND"), statusId("IN_PROGRESS"), null, LocalDate.now(), true);
        var task = new TaskTransitionPlan.Task(
                item, "Підтверджена задача", manager, LocalDate.now().plusDays(3));
        return new TaskGenerationRequested(
                context, new TaskTransitionPlan(template, "test", List.of(task), List.of()), manager);
    }
}
