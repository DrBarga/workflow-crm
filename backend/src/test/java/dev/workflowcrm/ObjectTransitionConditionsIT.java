package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectTransitionConditionsIT extends ObjectStatusTasksSupport {
    @Test
    void manualTransitionReportsAllFieldsAndTasksWithoutWritingAnything() throws Exception {
        state("CONTRACT_APPROVAL");
        ObjectSigningTestData.ready(jdbc, object, manager);
        jdbc.update(
                "update objects set commissioning_date=null,construction_acceptance_planned_date=null,construction_term_days=null where id=?",
                object);
        jdbc.update("update tasks set status='CANCELLED' where object_id=? and name='APPROVE_LOTUS_CONTRACT'", object);
        jdbc.update("delete from tasks where object_id=? and name='APPROVE_QUESTIONNAIRE'", object);
        var before = jdbc.queryForMap("select * from objects where id=?", object);
        var tasks = taskSnapshot();
        var error = response(change(lead, command("CONTRACT_SIGNED"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TRANSITION_CONDITIONS_NOT_MET")));
        var conditions = error.path("unmetConditions");
        assertThat(conditions.path("missingFields").toString())
                .contains(
                        "contractSignedDate",
                        "contractTypeId",
                        "commissioningDate",
                        "constructionAcceptancePlannedDate",
                        "constructionTermDays",
                        "openingPlannedDate");
        assertThat(conditions.path("incompleteTasks")).hasSize(1);
        assertThat(conditions.path("incompleteTasks").get(0).path("status").asText())
                .isEqualTo("CANCELLED");
        assertThat(conditions.path("missingTaskCodes").get(0).asText()).isEqualTo("APPROVE_QUESTIONNAIRE");
        assertThat(error.path("violations")).hasSize(8);
        assertThat(jdbc.queryForMap("select * from objects where id=?", object)).isEqualTo(before);
        assertThat(taskSnapshot()).isEqualTo(tasks);
        for (String table : List.of("object_history", "event_outbox", "status_approval_requests", "task_sets"))
            assertThat(count(table)).as(table).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEW", "IN_PROGRESS", "PAUSED", "WAITING_APPROVAL", "CANCELLED"})
    void everyNonDoneTaskRemainsAnExplicitCondition(String taskStatus) throws Exception {
        ready();
        jdbc.update(
                "update tasks set status=? where object_id=? and name='APPROVE_LOTUS_CONTRACT'", taskStatus, object);
        var body = command("CONTRACT_SIGNED");
        body.put("contractSignedDate", "2026-10-20");
        change(lead, body)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.unmetConditions.missingFields").isEmpty())
                .andExpect(
                        jsonPath("$.unmetConditions.incompleteTasks[0].status").value(taskStatus))
                .andExpect(jsonPath("$.unmetConditions.incompleteTasks[0].id").isNotEmpty())
                .andExpect(jsonPath("$.unmetConditions.incompleteTasks[0].name").value("APPROVE_LOTUS_CONTRACT"));
        assertThat(version()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"card", "milestones"})
    void validDateIsSavedWhileBlockedAndSameDateCanBeRetriedWithPreview(String route) throws Exception {
        state("CONTRACT_APPROVAL");
        var fields =
                new LinkedHashMap<String, Object>(Map.of("version", version(), "contractSignedDate", "2026-10-20"));
        var result = response(date(route, fields).andExpect(status().isOk()));
        assertThat(result.path("saved").asBoolean()).isTrue();
        assertThat(result.path("version").asLong()).isEqualTo(1);
        assertThat(result.path("unmetConditions").path("missingFields").toString())
                .contains("contractTypeId")
                .doesNotContain("contractSignedDate");
        assertThat(result.path("unmetConditions").path("missingTaskCodes")).hasSize(3);
        assertThat(state()).isEqualTo("CONTRACT_APPROVAL");
        assertThat(jdbc.queryForObject(
                        "select contract_signed_date::text from objects where id=?", String.class, object))
                .isEqualTo("2026-10-20");
        assertThat(events("OBJECT_FIELDS_CHANGED")).isEqualTo(1);
        for (String event :
                List.of("OBJECT_STATUS_CHANGED", "TASK_GENERATION_REQUESTED", "OBJECT_TASK_TEMPLATE_MISSING"))
            assertThat(events(event)).as(event).isZero();
        assertThat(jdbc.queryForList("select field_name from object_history", String.class))
                .contains("contractSignedDate")
                .doesNotContain("status");
        assertThat(count("task_sets")).isZero();
        assertThat(count("status_approval_requests")).isZero();
        ObjectSigningTestData.ready(jdbc, object, manager);
        jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);
        template("CONTRACT_SIGNED", "PRIMARY");
        fields.put("version", version());
        var preview = response(date(route, fields).andExpect(status().isOk()));
        assertThat(preview.path("saved").asBoolean()).isFalse();
        assertThat(preview.path("tasks")).hasSize(1);
        assertThat(version()).isEqualTo(1);
        fields.put("confirmationToken", preview.path("confirmationToken").asText());
        date(route, fields)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(state()).isEqualTo("CONTRACT_SIGNED");
        assertThat(version()).isEqualTo(2);
        date(route, fields).andExpect(status().isConflict());
    }

    @Test
    void stalePreviewDoesNotTurnIntoAnUnconfirmedDateSave() throws Exception {
        ready();
        template("CONTRACT_SIGNED", "PRIMARY");
        var fields =
                new LinkedHashMap<String, Object>(Map.of("version", version(), "contractSignedDate", "2026-10-20"));
        fields.put(
                "confirmationToken",
                response(date("card", fields)).path("confirmationToken").asText());
        jdbc.update("update tasks set status='CANCELLED' where object_id=?", object);
        date("card", fields).andExpect(status().isConflict());
        assertThat(version()).isZero();
        assertThat(jdbc.queryForObject(
                        "select contract_signed_date from objects where id=?", java.time.LocalDate.class, object))
                .isNull();
        assertThat(count("object_history")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"card", "milestones"})
    void actualOpeningUsesConfiguredConditionsAndKeepsTheValidDate(String route) throws Exception {
        state("CONTRACT_SIGNED");
        jdbc.update(
                "update objects set contract_signed_date='2026-10-01',contract_type_id=? where id=?",
                contractType(),
                object);
        UUID rule =
                jdbc.queryForObject("select id from object_status_transitions where code='OPEN_OBJECT'", UUID.class);
        jdbc.update(
                "insert into object_transition_required_fields(transition_id,field_code) values(?,'COMMISSIONING_DATE')",
                rule);
        try {
            String dateField = route.equals("card") ? "openingActualDate" : "openingDate";
            var fields = new LinkedHashMap<String, Object>(Map.of("version", version(), dateField, "2026-10-20"));
            date(route, fields)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.saved").value(true))
                    .andExpect(jsonPath("$.unmetConditions.missingFields[0]").value("commissioningDate"));
            assertThat(state()).isEqualTo("CONTRACT_SIGNED");
            assertThat(jdbc.queryForObject("select opening_date::text from objects where id=?", String.class, object))
                    .isEqualTo("2026-10-20");
            jdbc.update("update objects set commissioning_date='2026-10-02' where id=?", object);
            fields.put("version", version());
            date(route, fields).andExpect(status().isOk());
            assertThat(state()).isEqualTo("OPENED");
        } finally {
            jdbc.update(
                    "delete from object_transition_required_fields where transition_id=? and field_code='COMMISSIONING_DATE'",
                    rule);
        }
    }

    private void ready() {
        state("CONTRACT_APPROVAL");
        ObjectSigningTestData.ready(jdbc, object, manager);
        jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);
    }

    private org.springframework.test.web.servlet.ResultActions date(String route, Map<String, Object> fields)
            throws Exception {
        return route.equals("milestones")
                ? milestones(lead, fields)
                : send(lead, "/api/objects/" + object, fields, true);
    }
}
