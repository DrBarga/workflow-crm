package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class TaskModelConstraintsIT extends ObjectStatusTasksSupport {
    @Test
    void normalizedContractReferenceSelectsTemplateAndIsPreservedInVersion() throws Exception {
        UUID template = template("IN_PROGRESS", null);
        jdbc.update("update task_templates set contract_type_id=? where id=?", contractType(), template);
        jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);
        enter();
        assertThat(count("tasks")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select contract_type_id from task_template_versions", UUID.class))
                .isEqualTo(contractType());
        assertThat(jdbc.queryForList("select name from task_statuses order by sort_order", String.class))
                .containsExactly("Не розпочата", "У роботі", "На паузі", "На погодженні", "Завершена", "Скасована");
    }

    @Test
    void taskEdgesRejectSelfReferencesDuplicatesAndOtherObjects() throws Exception {
        template("IN_PROGRESS", null);
        enter();
        UUID first = jdbc.queryForObject("select id from tasks", UUID.class);
        UUID second = task("NEW");
        jdbc.update(
                "insert into task_dependencies(object_id,task_id,predecessor_id) values(?,?,?)", object, second, first);
        assertThatThrownBy(() -> jdbc.update(
                        "insert into task_dependencies(object_id,task_id,predecessor_id) values(?,?,?)",
                        object,
                        second,
                        first))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "insert into task_dependencies(object_id,task_id,predecessor_id) values(?,?,?)",
                        object,
                        first,
                        first))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID foreign = UUID.randomUUID();
        jdbc.update(
                """
                insert into objects(id,name,address,manager_id,region_id,author_id,status_id,
                    entrance_longitude,entrance_latitude,object_area)
                select ?,'Інший об’єкт','Інша адреса',?,?,?,status_id,30.8,50.8,120 from objects where id=?
                """,
                foreign,
                colleague,
                region,
                colleague,
                object);
        assertThatThrownBy(() -> jdbc.update(
                        "insert into task_dependencies(object_id,task_id,predecessor_id) values(?,?,?)",
                        foreign,
                        first,
                        second))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("delete from tasks where id=?", second);
        assertThat(count("task_dependencies")).isZero();
    }

    @Test
    void templateEdgesCannotCrossVersionsAndAssignmentRuleRequiresMatchingParameters() throws Exception {
        UUID template = template("IN_PROGRESS", null);
        enter();
        UUID first = jdbc.queryForObject("select id from template_items", UUID.class);
        UUID version = jdbc.queryForObject("select id from task_template_versions", UUID.class);
        UUID otherVersion = UUID.randomUUID(), otherItem = UUID.randomUUID();
        jdbc.update(
                "insert into task_template_versions(id,template_id,revision,fingerprint,trigger_status_id) values(?,?,2,repeat('b',64),?)",
                otherVersion,
                template,
                statusId("IN_PROGRESS"));
        jdbc.update(
                "insert into template_items(id,template_version_id,source_item_id,name,deadline_rule,deadline_offset_days,assignment_rule,sort_order) values(?,?,?,'Інший елемент','FROM_START_BUSINESS',2,'OBJECT_MANAGER',0)",
                otherItem,
                otherVersion,
                UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update(
                        "insert into template_item_dependencies(template_version_id,item_id,predecessor_id) values(?,?,?)",
                        version,
                        first,
                        otherItem))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () -> jdbc.update("update template_items set assignment_rule='NETWORK_ROLE' where id=?", first))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update tasks set template_version_id=?", otherVersion))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update task_sets set trigger_status_id=?", statusId("FOUND")))
                .isInstanceOf(DataIntegrityViolationException.class);
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
