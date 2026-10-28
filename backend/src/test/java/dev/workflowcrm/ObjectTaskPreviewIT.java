package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;

class ObjectTaskPreviewIT extends ObjectStatusTasksSupport {
    @Test
    void previewsCompleteSetWithoutWritesAndMatchesConfirmedGeneration() throws Exception {
        UUID template = template("IN_PROGRESS", null);
        UUID first = firstItem(template);
        jdbc.update("update task_template_items set needs_review=false where id=?", first);
        UUID second = item(template, "Підготувати документи", "OBJECT_MANAGER", null, null, 6, 1);
        UUID third = item(template, "Перевірити документи", "REGIONAL_ROLE", null, role("REGIONAL_MANAGER"), 2, 2);
        jdbc.update(
                "update task_template_items set result_recipient_role_id=? where id=?",
                role("REGIONAL_MANAGER"),
                second);
        dependency(template, second, first);
        dependency(template, third, second);
        var before = snapshot();
        JsonNode preview = response(preview(manager, "IN_PROGRESS")
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")));
        preview(manager, "IN_PROGRESS").andExpect(status().isOk());
        assertThat(snapshot()).isEqualTo(before);
        assertThat(preview.path("templateFound").asBoolean()).isTrue();
        assertThat(preview.path("tasks")).hasSize(3);
        var date = LocalDate.parse(preview.path("triggerDate").asText());
        assertThat(preview.at("/tasks/0/needsReview").asBoolean()).isFalse();
        assertThat(preview.at("/tasks/1/deadline").asText())
                .isEqualTo(date.plusDays(6).toString());
        assertThat(preview.at("/tasks/1/assigneeId").asText()).isEqualTo(manager.toString());
        assertThat(preview.at("/tasks/1/predecessors/0").asText()).isEqualTo(first.toString());
        assertThat(preview.at("/tasks/1/resultRecipientRoleId").asText())
                .isEqualTo(role("REGIONAL_MANAGER").toString());
        assertThat(preview.at("/tasks/1/resultRecipientId").asText()).isEqualTo(lead.toString());
        assertThat(preview.at("/tasks/2/assigneeId").asText()).isEqualTo(lead.toString());
        var command = command("IN_PROGRESS");
        var confirmation = response(change(manager, command).andExpect(status().isOk()));
        assertThat(confirmation.path("tasks")).isEqualTo(preview.path("tasks"));
        command.put("confirmationToken", confirmation.path("confirmationToken").asText());
        change(manager, command)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(count("tasks")).isEqualTo(3);
        assertThat(count("task_dependencies")).isEqualTo(2);
        assertThat(count("template_item_dependencies")).isEqualTo(2);
        for (JsonNode planned : preview.path("tasks")) {
            UUID source = UUID.fromString(planned.path("templateItemId").asText());
            var saved = jdbc.queryForMap(
                    "select name,deadline,assignee_id,needs_review,result_recipient_id from tasks where template_item_id=?",
                    source);
            assertThat(saved.get("name")).isEqualTo(planned.path("name").asText());
            assertThat(saved.get("deadline").toString())
                    .isEqualTo(planned.path("deadline").asText());
            assertThat(saved.get("assignee_id").toString())
                    .isEqualTo(planned.path("assigneeId").asText());
            assertThat(saved.get("needs_review"))
                    .isEqualTo(planned.path("needsReview").asBoolean());
        }
        assertThat(jdbc.queryForObject(
                        "select result_recipient_id from tasks where template_item_id=?", UUID.class, second))
                .isEqualTo(lead);
        assertThat(jdbc.queryForList("select assignment_rule from template_items order by sort_order", String.class))
                .containsExactly("EXPLICIT_USER", "OBJECT_MANAGER", "REGIONAL_ROLE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABSENT", "EMPTY", "INACTIVE"})
    void missingTemplateReturnsExplicitFlagWithoutEvents(String variant) throws Exception {
        if (!"ABSENT".equals(variant)) {
            UUID template = template("IN_PROGRESS", null);
            if ("EMPTY".equals(variant)) jdbc.update("delete from task_template_items where template_id=?", template);
            if ("INACTIVE".equals(variant))
                jdbc.update("update task_templates set is_active=false where id=?", template);
            if ("UNMATCHED_TYPE".equals(variant))
                jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);
        }
        var before = snapshot();
        preview(manager, "IN_PROGRESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateFound").value(false))
                .andExpect(jsonPath("$.templateId").isEmpty())
                .andExpect(jsonPath("$.tasks").isEmpty());
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOT_FOUND", "INACTIVE", "OUTSIDE_REGION", "AMBIGUOUS"})
    void unresolvedAssigneeRemainsInPreviewAndCannotBeGenerated(String issue) throws Exception {
        UUID template = template("IN_PROGRESS", null);
        UUID item = firstItem(template);
        switch (issue) {
            case "INACTIVE" -> {
                jdbc.update("update task_template_items set assignee_id=? where id=?", colleague, item);
                jdbc.update("update users set status='DISABLED' where id=?", colleague);
            }
            case "OUTSIDE_REGION" ->
                jdbc.update("update task_template_items set assignee_id=? where id=?", outsider, item);
            case "NOT_FOUND" ->
                jdbc.update(
                        "update task_template_items set assignment_rule='REGIONAL_ROLE',role_id=?,assignee_id=null where id=?",
                        role("SUPERADMIN"),
                        item);
            case "AMBIGUOUS" -> {
                user(UUID.randomUUID(), "REGIONAL_MANAGER", region);
                jdbc.update(
                        "update task_template_items set assignment_rule='REGIONAL_ROLE',role_id=?,assignee_id=null where id=?",
                        role("REGIONAL_MANAGER"),
                        item);
            }
            default -> throw new IllegalArgumentException(issue);
        }
        if ("NOT_FOUND".equals(issue)) jdbc.update("update users set status='DISABLED' where id=?", admin);
        var before = snapshot();
        preview(manager, "IN_PROGRESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()").value(1))
                .andExpect(jsonPath("$.tasks[0].assignment.issue").value(issue))
                .andExpect(jsonPath("$.tasks[0].assigneeId").isEmpty());
        assertThat(snapshot()).isEqualTo(before);
        change(manager, command("IN_PROGRESS")).andExpect(status().isUnprocessableEntity());
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CEO", "SUPERADMIN"})
    void fullCardReadersCanPreview(String role) throws Exception {
        template("IN_PROGRESS", null);
        preview(actor(role), "IN_PROGRESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.length()").value(1));
    }

    @Test
    void enforcesObjectVisibilityAuthenticationAndInputValidation() throws Exception {
        template("IN_PROGRESS", null);
        var before = snapshot();
        for (UUID actor : List.of(outsider, colleague, foreignLead, contract))
            preview(actor, "IN_PROGRESS").andExpect(status().isForbidden());
        mvc.perform(get("/api/objects/{id}/task-preview", object).param("status", "IN_PROGRESS"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/objects/{id}/task-preview", object).cookie(cookie(manager)))
                .andExpect(status().isBadRequest());
        preview(manager, "UNKNOWN").andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/objects/{id}/task-preview", UUID.randomUUID())
                        .param("status", "IN_PROGRESS")
                        .cookie(cookie(manager)))
                .andExpect(status().isNotFound());
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void ambiguousTemplatesAndCyclicDependenciesReturnConfigurationError() throws Exception {
        UUID template = template("IN_PROGRESS", null);
        UUID first = firstItem(template);
        UUID second = item(template, "Друга задача", "OBJECT_MANAGER", null, null, 3, 1);
        dependency(template, first, second);
        dependency(template, second, first);
        var before = snapshot();
        preview(manager, "IN_PROGRESS")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_TEMPLATE_CONFIGURATION_ERROR"));
        assertThat(snapshot()).isEqualTo(before);
        jdbc.update("delete from task_template_item_dependencies");
        jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);
        jdbc.update("update task_templates set contract_type_id=? where id=?", contractType(), template);
        template("IN_PROGRESS", "PRIMARY");
        before = snapshot();
        preview(manager, "IN_PROGRESS")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_TEMPLATE_CONFIGURATION_ERROR"));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void roleAssignmentUsesExplicitRegionsAndNetworkScope() throws Exception {
        UUID template = template("IN_PROGRESS", null);
        jdbc.update("delete from task_template_items where template_id=?", template);
        UserRegionTestData.replace(jdbc, lead, otherRegion);
        item(template, "Провідний регіону", "REGIONAL_ROLE", null, role("REGIONAL_MANAGER"), 1, 0);
        item(template, "Директор мережі", "NETWORK_ROLE", null, role("DEVELOPMENT_DIRECTOR"), 2, 1);
        preview(manager, "IN_PROGRESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].assignment.issue").value("NOT_FOUND"))
                .andExpect(jsonPath("$.tasks[1].assigneeId").value(director.toString()));
        UserRegionTestData.assign(jdbc, lead, region);
        preview(manager, "IN_PROGRESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].assigneeId").value(lead.toString()))
                .andExpect(jsonPath("$.tasks[1].assigneeId").value(director.toString()));
    }

    @Test
    void dependencyChangeInvalidatesConfirmationAndAssignedRegionsResolveAssignee() throws Exception {
        UUID template = template("IN_PROGRESS", null);
        UUID first = firstItem(template);
        UUID second = item(template, "Регіональна перевірка", "REGIONAL_ROLE", null, role("REGIONAL_MANAGER"), 4, 1);
        UserRegionTestData.replace(jdbc, lead, otherRegion, region);
        preview(manager, "IN_PROGRESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[1].assigneeId").value(lead.toString()));
        var command = command("IN_PROGRESS");
        var confirmation = response(change(manager, command).andExpect(status().isOk()));
        command.put("confirmationToken", confirmation.path("confirmationToken").asText());
        dependency(template, second, first);
        var before = snapshot();
        change(manager, command).andExpect(status().isConflict());
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void draftDependenciesAndAssignmentRulesRejectInconsistentConfiguration() {
        UUID template = template("IN_PROGRESS", null);
        UUID first = firstItem(template);
        UUID otherTemplate = template("IC_PREPARATION", null);
        UUID other = firstItem(otherTemplate);
        assertThatThrownBy(() -> dependency(template, first, first))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> dependency(template, first, other))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "update task_template_items set assignment_rule='OBJECT_MANAGER' where id=?", first))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private ResultActions preview(UUID actor, String status) throws Exception {
        return mvc.perform(get("/api/objects/{id}/task-preview", object)
                .param("status", status)
                .cookie(cookie(actor)));
    }

    private UUID firstItem(UUID template) {
        return jdbc.queryForObject("select id from task_template_items where template_id=?", UUID.class, template);
    }

    private UUID role(String code) {
        return jdbc.queryForObject("select id from roles where code=?", UUID.class, code);
    }

    private UUID item(UUID template, String name, String rule, UUID assignee, UUID role, int days, int order) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into task_template_items(id,template_id,name,assignment_rule,assignee_id,role_id,deadline_offset_days,sort_order) values(?,?,?,?,?,?,?,?)",
                id,
                template,
                name,
                rule,
                assignee,
                role,
                days,
                order);
        return id;
    }

    private void dependency(UUID template, UUID item, UUID predecessor) {
        jdbc.update(
                "insert into task_template_item_dependencies(template_id,item_id,predecessor_id) values(?,?,?)",
                template,
                item,
                predecessor);
    }

    private Map<String, List<String>> snapshot() {
        var result = new LinkedHashMap<String, List<String>>();
        for (String table : List.of(
                "objects",
                "task_templates",
                "task_template_items",
                "task_template_item_dependencies",
                "task_template_versions",
                "template_items",
                "template_item_dependencies",
                "task_sets",
                "tasks",
                "task_dependencies",
                "task_history",
                "object_history",
                "object_status_audit",
                "status_approval_requests",
                "event_outbox",
                "user_notifications"))
            result.put(
                    table,
                    jdbc.queryForList(
                            "select to_jsonb(t)::text from " + table + " t order by to_jsonb(t)::text", String.class));
        return result;
    }
}
