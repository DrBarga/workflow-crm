package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class TaskElementCodesIT extends ObjectStatusTasksSupport {
    @Autowired
    PlatformTransactionManager transactions;

    @Test
    void renamingAndRevisingTemplatePreservesElementCodeAndCreatedTaskSources() throws Exception {
        UUID template = template("IN_PROGRESS", null);
        UUID item = jdbc.queryForObject("select id from task_template_items where template_id=?", UUID.class, template);
        String code = jdbc.queryForObject("select code from task_template_items where id=?", String.class, item);
        enter();
        change(lead, command("FOUND")).andExpect(status().isOk());
        jdbc.update("update task_template_items set name='Revised task',deadline_offset_days=5 where id=?", item);
        enter();
        assertThat(jdbc.queryForList("select code from template_items where source_item_id=?", String.class, item))
                .containsExactly(code, code);
        assertThat(count("task_template_versions")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "select count(distinct source_item_id) from tasks where object_id=?", Integer.class, object))
                .isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("update task_template_items set code='CHANGED' where id=?", item))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update template_items set code='CHANGED' where source_item_id=?", item))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletedDraftDoesNotAllowDifferentCodeInANewVersion() throws Exception {
        UUID template = template("IN_PROGRESS", null);
        UUID item = jdbc.queryForObject("select id from task_template_items where template_id=?", UUID.class, template);
        enter();
        String code = jdbc.queryForObject("select code from template_items where source_item_id=?", String.class, item);
        jdbc.update("delete from task_template_items where id=?", item);
        UUID next = version(template, 2);
        assertThatThrownBy(() -> snapshot(next, item, "CHANGED")).isInstanceOf(DataIntegrityViolationException.class);
        snapshot(next, item, null);
        assertThat(jdbc.queryForObject(
                        "select code from template_items where template_version_id=?", String.class, next))
                .isEqualTo(code);
        assertThat(jdbc.queryForObject("select count(*) from tasks where source_item_id is not null", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void rejectsDuplicateCodesWithinATemplateAndCrossTemplateIdentityReuse() throws Exception {
        UUID template = template("IN_PROGRESS", null);
        UUID item = jdbc.queryForObject("select id from task_template_items where template_id=?", UUID.class, template);
        String code = jdbc.queryForObject("select code from task_template_items where id=?", String.class, item);
        assertThatThrownBy(() -> jdbc.update(
                        "insert into task_template_items(template_id,name,assignee_id,deadline_offset_days,sort_order,code) values(?,'Duplicate',?,1,1,?)",
                        template,
                        manager,
                        code))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID another = template("IC_PREPARATION", null);
        assertThatThrownBy(() -> snapshot(version(another, 1), item, code))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentFirstSnapshotsCannotAssignDifferentCodesToSameElement() throws Exception {
        UUID template = template("IN_PROGRESS", null), source = UUID.randomUUID();
        var versions = List.of(version(template, 1), version(template, 2));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var attempts = new ArrayList<Future<Boolean>>();
            for (int index = 0; index < 2; index++) {
                UUID version = versions.get(index);
                String code = "CONCURRENT_" + index;
                attempts.add(executor.submit(() -> {
                    start.await();
                    try {
                        new TransactionTemplate(transactions)
                                .executeWithoutResult(tx -> snapshot(version, source, code));
                        return true;
                    } catch (DataIntegrityViolationException conflict) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int saved = 0;
            for (var attempt : attempts) if (attempt.get(20, TimeUnit.SECONDS)) saved++;
            assertThat(saved).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject(
                        "select count(*) from template_items where source_item_id=?", Integer.class, source))
                .isEqualTo(1);
    }

    private UUID version(UUID template, int revision) {
        UUID version = UUID.randomUUID();
        jdbc.update(
                "insert into task_template_versions(id,template_id,revision,fingerprint,trigger_status_id) select ?,?,?,?,trigger_status_id from task_templates where id=?",
                version,
                template,
                revision,
                version.toString().replace("-", "").repeat(2),
                template);
        return version;
    }

    private void snapshot(UUID version, UUID source, String code) {
        jdbc.update(
                "insert into template_items(template_version_id,source_item_id,name,code,deadline_rule,deadline_offset_days,assignment_rule,sort_order) values(?,?,'Snapshot',?,'FROM_START_CALENDAR',1,'OBJECT_MANAGER',0)",
                version,
                source,
                code);
    }

    private void enter() throws Exception {
        var body = command("IN_PROGRESS");
        body.put(
                "confirmationToken",
                response(change(manager, body).andExpect(status().isOk()))
                        .path("confirmationToken")
                        .asText());
        change(manager, body).andExpect(status().isOk());
    }
}
