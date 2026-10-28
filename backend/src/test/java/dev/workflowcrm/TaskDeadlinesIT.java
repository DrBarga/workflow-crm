package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class TaskDeadlinesIT extends ObjectStatusTasksSupport {
    @MockitoSpyBean
    Clock clock;

    @ParameterizedTest
    @CsvSource({
        "2026-10-23T21:00:00Z, 2026-10-24, 2, 2026-10-26",
        "2026-12-31T22:00:00Z, 2027-01-01, 0, 2027-01-01",
        "2028-02-28T12:00:00Z, 2028-02-28, 2, 2028-03-01"
    })
    void previewAndCreationUseTriggerDateForEveryTask(String instant, String trigger, int offset, String due)
            throws Exception {
        at(instant);
        UUID template = template("IN_PROGRESS", null);
        UUID root = firstItem(template);
        jdbc.update("update task_template_items set deadline_offset_days=99 where id=?", root);
        UUID dependent = UUID.randomUUID();
        jdbc.update(
                """
                insert into task_template_items(id,template_id,name,assignee_id,deadline_offset_days,sort_order)
                values(?,?,'Перевірити документи',?,?,1)
                """,
                dependent,
                template,
                manager,
                offset);
        jdbc.update(
                "insert into task_template_item_dependencies(template_id,item_id,predecessor_id) values(?,?,?)",
                template,
                dependent,
                root);
        JsonNode preview = preview();
        assertThat(preview.path("triggerDate").asText()).isEqualTo(trigger);
        assertThat(preview.at("/tasks/0/deadline").asText())
                .isEqualTo(LocalDate.parse(trigger).plusDays(99).toString());
        assertThat(preview.at("/tasks/1/deadline").asText()).isEqualTo(due);
        assertThat(preview.at("/tasks/1/predecessors/0").asText()).isEqualTo(root.toString());
        assertThat(count("tasks")).isZero();
        var command = command("IN_PROGRESS");
        var confirmation = response(change(manager, command).andExpect(status().isOk()));
        assertThat(confirmation.path("tasks")).isEqualTo(preview.path("tasks"));
        command.put("confirmationToken", confirmation.path("confirmationToken").asText());
        change(manager, command)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(jdbc.queryForObject(
                        "select deadline from tasks where template_item_id=?", LocalDate.class, dependent))
                .isEqualTo(due);
        assertThat(count("task_dependencies")).isEqualTo(1);
        assertThat(jdbc.queryForList("select deadline_rule from template_items", String.class))
                .containsOnly("FROM_START_CALENDAR");
        assertThat(jdbc.queryForObject(
                        "select deadline_offset_days from template_items where source_item_id=?",
                        Integer.class,
                        dependent))
                .isEqualTo(offset);
        jdbc.update("update task_template_items set deadline_offset_days=17,version=version+1 where id=?", dependent);
        assertThat(jdbc.queryForObject(
                        "select deadline from tasks where template_item_id=?", LocalDate.class, dependent))
                .isEqualTo(due);
        assertThat(jdbc.queryForObject(
                        "select deadline_offset_days from template_items where source_item_id=?",
                        Integer.class,
                        dependent))
                .isEqualTo(offset);
    }

    @Test
    void approvalUsesActualTransitionDayInsteadOfRequestDay() throws Exception {
        at("2026-10-23T12:00:00Z");
        template("IC_PREPARATION", null);
        var command = command("IC_PREPARATION");
        var request = response(change(manager, command).andExpect(status().isOk()));
        UUID requestId = UUID.fromString(request.path("approvalRequestId").asText());
        assertThat(count("tasks")).isZero();
        at("2026-10-25T12:00:00Z");
        var approvalPreview =
                response(approval(lead, requestId, "approve", Map.of()).andExpect(status().isOk()));
        assertThat(approvalPreview.at("/tasks/0/deadline").asText()).isEqualTo("2026-10-28");
        approval(
                        lead,
                        requestId,
                        "approve",
                        Map.of(
                                "confirmationToken",
                                approvalPreview.path("confirmationToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(jdbc.queryForObject("select deadline from tasks where object_id=?", LocalDate.class, object))
                .isEqualTo("2026-10-28");
    }

    @Test
    void sameTemplateRuleReusesVersionAcrossDifferentTransitionDates() throws Exception {
        at("2026-10-23T12:00:00Z");
        UUID template = template("IN_PROGRESS", null);
        confirmTransition();
        jdbc.update("update tasks set status='CANCELLED' where object_id=?", object);
        jdbc.update("update task_sets set state='CANCELLED' where object_id=?", object);
        state("FOUND");
        at("2026-10-25T12:00:00Z");
        confirmTransition();
        assertThat(jdbc.queryForList(
                        "select deadline from tasks where object_id=? order by deadline", LocalDate.class, object))
                .containsExactly(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 28));
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_template_versions where template_id=?", Integer.class, template))
                .isEqualTo(1);
    }

    private void confirmTransition() throws Exception {
        var command = command("IN_PROGRESS");
        var preview = response(change(manager, command).andExpect(status().isOk()));
        command.put("confirmationToken", preview.path("confirmationToken").asText());
        change(manager, command)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
    }

    @Test
    void crossingKyivMidnightRequiresNewConfirmation() throws Exception {
        at("2026-10-23T20:59:59Z");
        UUID template = template("IN_PROGRESS", null);
        jdbc.update("update task_template_items set deadline_offset_days=0 where template_id=?", template);
        var command = command("IN_PROGRESS");
        var confirmation = response(change(manager, command).andExpect(status().isOk()));
        assertThat(confirmation.at("/tasks/0/deadline").asText()).isEqualTo("2026-10-23");
        command.put("confirmationToken", confirmation.path("confirmationToken").asText());
        at("2026-10-23T21:00:00Z");
        change(manager, command).andExpect(status().isConflict());
        assertThat(state()).isEqualTo("FOUND");
        assertThat(count("tasks")).isZero();
        assertThat(count("task_sets")).isZero();
        assertThat(preview().at("/tasks/0/deadline").asText()).isEqualTo("2026-10-24");
    }

    @Test
    void templateOffsetChangeInvalidatesConfirmation() throws Exception {
        UUID template = template("IN_PROGRESS", null);
        var command = command("IN_PROGRESS");
        var confirmation = response(change(manager, command).andExpect(status().isOk()));
        command.put("confirmationToken", confirmation.path("confirmationToken").asText());
        jdbc.update("update task_template_items set deadline_offset_days=0 where template_id=?", template);
        change(manager, command).andExpect(status().isConflict());
        assertThat(count("tasks")).isZero();
    }

    private UUID firstItem(UUID template) {
        return jdbc.queryForObject("select id from task_template_items where template_id=?", UUID.class, template);
    }

    private JsonNode preview() throws Exception {
        return response(mvc.perform(get("/api/objects/{id}/task-preview", object)
                        .param("status", "IN_PROGRESS")
                        .cookie(cookie(manager)))
                .andExpect(status().isOk()));
    }

    private void at(String instant) {
        doReturn(Clock.fixed(Instant.parse(instant), ZoneId.of("Europe/Kyiv")))
                .when(clock)
                .withZone(ZoneId.of("Europe/Kyiv"));
    }
}
