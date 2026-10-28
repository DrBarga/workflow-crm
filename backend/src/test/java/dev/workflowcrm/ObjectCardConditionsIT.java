package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectCardConditionsIT extends ObjectCardTestSupport {
    @Test
    void reportsAllMissingFieldsAndAbsentTemplateCodesWithoutHidingTransition() throws Exception {
        UUID object = add("Договір", manager, region);
        state(object, "CONTRACT_APPROVAL");
        var conditions = signing(contract, object).path("unmetConditions");
        assertThat(strings(conditions.path("missingFields")))
                .containsExactly(
                        "commissioningDate",
                        "constructionAcceptancePlannedDate",
                        "constructionTermDays",
                        "contractSignedDate",
                        "contractTypeId",
                        "openingPlannedDate");
        assertThat(strings(conditions.path("missingTaskCodes")))
                .containsExactly("APPROVE_FINANCIAL_MODEL", "APPROVE_LOTUS_CONTRACT", "APPROVE_QUESTIONNAIRE");
        assertThat(conditions.path("incompleteTasks")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEW", "IN_PROGRESS", "PAUSED", "WAITING_APPROVAL", "CANCELLED"})
    void returnsRealIdNameAndStatusForEveryUnfinishedRequiredTask(String status) throws Exception {
        UUID object = ready();
        jdbc.update(
                "update tasks set status=?,name='Погодити договір' where object_id=? and name='APPROVE_LOTUS_CONTRACT'",
                status,
                object);
        UUID task = jdbc.queryForObject(
                "select id from tasks where object_id=? and name='Погодити договір'", UUID.class, object);
        var conditions = signing(contract, object).path("unmetConditions");
        assertThat(conditions.path("missingFields")).isEmpty();
        assertThat(conditions.path("missingTaskCodes")).isEmpty();
        assertThat(conditions.path("incompleteTasks")).hasSize(1);
        var item = conditions.path("incompleteTasks").get(0);
        assertThat(item.path("id").asText()).isEqualTo(task.toString());
        assertThat(item.path("code").asText()).isEqualTo("APPROVE_LOTUS_CONTRACT");
        assertThat(item.path("name").asText()).isEqualTo("Погодити договір");
        assertThat(item.path("status").asText()).isEqualTo(status);
        assertThat(card(manager, object).path("availableTransitions").toString())
                .doesNotContain(task.toString());
    }

    @Test
    void satisfiedConditionsAreEmptyAndGetLeavesTasksObjectAndHistoryUnchanged() throws Exception {
        UUID object = ready();
        var before = jdbc.queryForMap("select version,status_id,updated_at from objects where id=?", object);
        var tasksBefore = jdbc.queryForList("select * from tasks where object_id=? order by id", object);
        var conditions = signing(contract, object).path("unmetConditions");
        assertThat(conditions.path("missingFields")).isEmpty();
        assertThat(conditions.path("incompleteTasks")).isEmpty();
        assertThat(conditions.path("missingTaskCodes")).isEmpty();
        assertThat(jdbc.queryForMap("select version,status_id,updated_at from objects where id=?", object))
                .isEqualTo(before);
        assertThat(jdbc.queryForList("select * from tasks where object_id=? order by id", object))
                .isEqualTo(tasksBefore);
        assertThat(count("object_history")).isZero();
        assertThat(count("event_outbox")).isZero();
        assertThat(count("status_approval_requests")).isZero();
    }

    @Test
    void manualTaskWithSameNameAndOtherObjectOrStageDoNotSatisfyRequirements() throws Exception {
        UUID object = ready();
        UUID other = add("Інше погодження", manager, region);
        state(other, "CONTRACT_APPROVAL");
        jdbc.update(
                """
                insert into tasks(object_id,source_status_id,template_item_id,name,assignee_id,deadline,status,author_id)
                select ?,source_status_id,template_item_id,name,assignee_id,deadline,status,author_id
                from tasks where object_id=?
                """,
                other,
                object);
        jdbc.update(
                "update tasks set template_item_id=null where object_id=? and name='APPROVE_QUESTIONNAIRE'", object);
        jdbc.update(
                "update tasks set source_status_id=(select id from object_statuses where code='FOUND') where object_id=? and name='APPROVE_FINANCIAL_MODEL'",
                object);
        var conditions = signing(contract, object).path("unmetConditions");
        assertThat(strings(conditions.path("missingTaskCodes")))
                .containsExactly("APPROVE_FINANCIAL_MODEL", "APPROVE_QUESTIONNAIRE");
        assertThat(conditions.path("incompleteTasks")).isEmpty();
        assertThat(signing(contract, other).path("unmetConditions").path("missingTaskCodes"))
                .isEmpty();
    }

    @Test
    void latestElementTaskRemainsUnmetWhenItsSetIsCancelled() throws Exception {
        UUID object = ready();
        UUID old = set(object, "COMPLETED", "2026-01-01");
        jdbc.update("update tasks set task_set_id=? where object_id=?", old, object);
        UUID current = set(object, "ACTIVE", "2026-02-01");
        jdbc.update(
                """
                insert into tasks(object_id,source_status_id,template_item_id,name,assignee_id,deadline,status,author_id,task_set_id)
                select object_id,source_status_id,template_item_id,name,assignee_id,deadline,'NEW',author_id,?
                from tasks where task_set_id=?
                """,
                current,
                old);
        var conditions = signing(contract, object).path("unmetConditions");
        assertThat(conditions.path("incompleteTasks")).hasSize(3);
        assertThat(conditions.path("missingTaskCodes")).isEmpty();
        jdbc.update("update task_sets set state='CANCELLED' where object_id=?", object);
        conditions = signing(contract, object).path("unmetConditions");
        assertThat(conditions.path("incompleteTasks")).hasSize(3);
        assertThat(conditions.path("missingTaskCodes")).isEmpty();
    }

    @Test
    void refusalRequiresNewReasonEvenWhenObjectRetainsHistoricalReason() throws Exception {
        UUID object = add("Відмова", manager, region);
        jdbc.update("update objects set refusal_reason_id=(select id from refusal_reasons limit 1) where id=?", object);
        var transitions = card(manager, object).path("availableTransitions");
        var refusal = java.util.stream.StreamSupport.stream(transitions.spliterator(), false)
                .filter(t -> t.path("status").asText().equals("REFUSED"))
                .findFirst()
                .orElseThrow();
        assertThat(strings(refusal.path("unmetConditions").path("missingFields")))
                .containsExactly("refusalReasonId");
    }

    @Test
    void duplicateTasksForSameCodeMustAllBeCompletedAndOtherCodesAreExcluded() throws Exception {
        UUID object = ready();
        jdbc.update(
                """
                insert into tasks(object_id,source_status_id,template_item_id,name,assignee_id,deadline,status,author_id)
                select object_id,source_status_id,template_item_id,'Повторне погодження',assignee_id,deadline,'NEW',author_id
                from tasks where object_id=? and name='APPROVE_LOTUS_CONTRACT'
                """,
                object);
        tasks(object);
        var conditions = signing(contract, object).path("unmetConditions");
        assertThat(conditions.path("incompleteTasks")).hasSize(1);
        assertThat(conditions.path("incompleteTasks").get(0).path("name").asText())
                .isEqualTo("Повторне погодження");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "contract_signed_date",
                "contract_type_id",
                "commissioning_date",
                "construction_acceptance_planned_date",
                "construction_term_days"
            })
    void reportsMissingSigningFieldsFromCurrentObjectValues(String column) throws Exception {
        UUID object = ready();
        jdbc.update("update objects set " + column + "=null where id=?", object);
        var expected =
                switch (column) {
                    case "contract_signed_date" -> List.of("contractSignedDate");
                    case "contract_type_id" -> List.of("contractTypeId");
                    case "commissioning_date" -> List.of("commissioningDate");
                    case "construction_acceptance_planned_date" ->
                        List.of("constructionAcceptancePlannedDate", "openingPlannedDate");
                    default -> List.of("constructionTermDays", "openingPlannedDate");
                };
        assertThat(strings(signing(contract, object).path("unmetConditions").path("missingFields")))
                .containsExactlyElementsOf(expected);
    }

    @Test
    void savedTemplateCodeSurvivesDraftRenamingAndRemoval() throws Exception {
        UUID object = ready();
        UUID template = jdbc.queryForObject(
                "select template_id from task_template_items where code='APPROVE_LOTUS_CONTRACT'", UUID.class);
        UUID version = UUID.randomUUID();
        jdbc.update(
                "insert into task_template_versions(id,template_id,revision,fingerprint,trigger_status_id) select ?,?,1,repeat('a',64),id from object_statuses where code='CONTRACT_APPROVAL'",
                version,
                template);
        jdbc.update(
                """
                insert into template_items(template_version_id,source_item_id,name,code,deadline_rule,deadline_offset_days,assignment_rule,assignee_id,sort_order)
                select ?,id,name,code,'FROM_START_CALENDAR',0,'EXPLICIT_USER',assignee_id,sort_order from task_template_items where template_id=?
                """,
                version,
                template);
        UUID current = set(object, "ACTIVE", "2026-10-01");
        jdbc.update("update task_sets set template_version_id=? where id=?", version, current);
        jdbc.update(
                "update tasks t set source_item_id=i.id,template_version_id=?,task_set_id=? from template_items i where t.template_item_id=i.source_item_id and t.object_id=?",
                version,
                current,
                object);
        assertThatThrownBy(() -> jdbc.update(
                        "update task_template_items set code='CHANGED_'||code where template_id=?", template))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("update task_template_items set name='Змінена назва' where template_id=?", template);
        assertThat(signing(contract, object).path("unmetConditions").path("missingTaskCodes"))
                .isEmpty();
        jdbc.update("delete from task_template_items where template_id=?", template);
        jdbc.update(
                "update tasks set status='NEW',name='Збережена задача' where object_id=? and source_item_id in(select id from template_items where code='APPROVE_LOTUS_CONTRACT')",
                object);
        var conditions = signing(contract, object).path("unmetConditions");
        assertThat(conditions.path("missingTaskCodes")).isEmpty();
        assertThat(conditions.path("incompleteTasks")).hasSize(1);
        assertThat(conditions.path("incompleteTasks").get(0).path("code").asText())
                .isEqualTo("APPROVE_LOTUS_CONTRACT");
    }

    private UUID ready() {
        UUID object = add("Погодження", manager, region);
        state(object, "CONTRACT_APPROVAL");
        contractType(object);
        ObjectSigningTestData.ready(jdbc, object, manager);
        return object;
    }

    private UUID set(UUID object, String state, String created) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into task_sets(id,object_id,trigger_status_id,state,created_at) select ?,?,id,?,?::timestamptz from object_statuses where code='CONTRACT_APPROVAL'",
                id,
                object,
                state,
                created);
        return id;
    }

    private JsonNode signing(UUID actor, UUID object) throws Exception {
        return java.util.stream.StreamSupport.stream(
                        card(actor, object).path("availableTransitions").spliterator(), false)
                .filter(t -> t.path("code").asText().equals("SIGN_CONTRACT"))
                .findFirst()
                .orElseThrow();
    }

    private List<String> strings(JsonNode values) {
        return java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }
}
