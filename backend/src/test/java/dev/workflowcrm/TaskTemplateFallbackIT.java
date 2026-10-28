package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TaskTemplateFallbackIT extends ObjectStatusTasksSupport {
    @ParameterizedTest(name = "exact={0}, default={1}")
    @MethodSource("candidates")
    void previewAndConfirmedTransitionUseTheSameUsableCandidate(Candidate exact, Candidate general) throws Exception {
        UUID typed = candidate(exact, "PRIMARY");
        UUID fallback = candidate(general, null);
        UUID expected = exact == Candidate.POPULATED ? typed : general == Candidate.POPULATED ? fallback : null;
        jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);

        var preview = response(mvc.perform(get("/api/objects/{id}/task-preview", object)
                        .param("status", "IN_PROGRESS")
                        .cookie(cookie(manager)))
                .andExpect(status().isOk()));
        assertThat(preview.path("templateFound").asBoolean()).isEqualTo(expected != null);
        assertThat(count("tasks")).isZero();
        assertThat(count("task_sets")).isZero();
        assertThat(count("task_template_versions")).isZero();
        assertThat(events("OBJECT_TASK_TEMPLATE_MISSING")).isZero();

        var command = command("IN_PROGRESS");
        var transition = response(change(manager, command).andExpect(status().isOk()));
        assertThat(transition.path("tasks")).isEqualTo(preview.path("tasks"));
        if (expected == null) {
            assertThat(preview.path("templateId").isNull()).isTrue();
            assertThat(transition.path("saved").asBoolean()).isTrue();
            assertThat(transition.path("confirmationToken").isNull()).isTrue();
            assertThat(count("tasks")).isZero();
            assertThat(count("task_sets")).isZero();
            assertThat(count("task_template_versions")).isZero();
            assertThat(events("OBJECT_TASK_TEMPLATE_MISSING")).isEqualTo(1);
        } else {
            assertThat(preview.path("templateId").asText()).isEqualTo(expected.toString());
            assertThat(transition.path("saved").asBoolean()).isFalse();
            assertThat(preview.path("tasks")).hasSize(1);
            assertThat(preview.at("/tasks/0/name").asText())
                    .isEqualTo(expected.equals(typed) ? "Типовий набір" : "Загальний набір");
            command.put(
                    "confirmationToken", transition.path("confirmationToken").asText());
            change(manager, command)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.saved").value(true));
            assertThat(count("tasks")).isEqualTo(1);
            assertThat(count("task_sets")).isEqualTo(1);
            assertThat(count("task_template_versions")).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                            "select v.template_id from task_sets s join task_template_versions v on v.id=s.template_version_id where s.object_id=?",
                            UUID.class,
                            object))
                    .isEqualTo(expected);
            assertThat(jdbc.queryForObject("select template_item_id from tasks where object_id=?", UUID.class, object))
                    .isEqualTo(UUID.fromString(
                            preview.at("/tasks/0/templateItemId").asText()));
            assertThat(events("OBJECT_TASK_TEMPLATE_MISSING")).isZero();
        }
        assertThat(state()).isEqualTo("IN_PROGRESS");
        change(manager, command).andExpect(status().isConflict());
        assertThat(events("OBJECT_TASK_TEMPLATE_MISSING")).isEqualTo(expected == null ? 1 : 0);
    }

    @Test
    void newlyAvailableExactTemplateInvalidatesFallbackConfirmation() throws Exception {
        candidate(Candidate.POPULATED, null);
        jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);
        var command = command("IN_PROGRESS");
        var preview = response(change(manager, command).andExpect(status().isOk()));
        command.put("confirmationToken", preview.path("confirmationToken").asText());

        UUID typed = candidate(Candidate.POPULATED, "PRIMARY");
        change(manager, command).andExpect(status().isConflict());
        assertUnchangedTransition();
        command.remove("confirmationToken");
        var current = response(change(manager, command).andExpect(status().isOk()));
        assertThat(current.at("/tasks/0/templateItemId").asText())
                .isEqualTo(jdbc.queryForObject(
                        "select id::text from task_template_items where template_id=?", String.class, typed));
    }

    @Test
    void emptyExactTemplateInvalidatesTypedConfirmationAndOffersFallback() throws Exception {
        UUID typed = candidate(Candidate.POPULATED, "PRIMARY");
        candidate(Candidate.POPULATED, null);
        jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);
        var command = command("IN_PROGRESS");
        var preview = response(change(manager, command).andExpect(status().isOk()));
        command.put("confirmationToken", preview.path("confirmationToken").asText());

        jdbc.update("delete from task_template_items where template_id=?", typed);
        change(manager, command).andExpect(status().isConflict());
        assertUnchangedTransition();
        command.remove("confirmationToken");
        var current = response(change(manager, command).andExpect(status().isOk()));
        assertThat(current.at("/tasks/0/name").asText()).isEqualTo("Загальний набір");
    }

    @Test
    void emptyDuplicateExactPairCannotBeHiddenByPopulatedFallback() throws Exception {
        candidate(Candidate.EMPTY, "PRIMARY");
        UUID duplicate = candidate(Candidate.EMPTY, "Основний");
        jdbc.update("update task_templates set contract_type_id=? where id=?", contractType(), duplicate);
        candidate(Candidate.POPULATED, null);
        jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);

        change(manager, command("IN_PROGRESS"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_TEMPLATE_CONFIGURATION_ERROR"));
        assertUnchangedTransition();
    }

    private void assertUnchangedTransition() {
        assertThat(state()).isEqualTo("FOUND");
        assertThat(version()).isZero();
        assertThat(count("tasks")).isZero();
        assertThat(count("task_sets")).isZero();
        assertThat(count("task_template_versions")).isZero();
        assertThat(count("object_history")).isZero();
        assertThat(events("OBJECT_TASK_TEMPLATE_MISSING")).isZero();
        assertThat(events("OBJECT_STATUS_CHANGED")).isZero();
    }

    private UUID candidate(Candidate state, String type) {
        if (state == Candidate.ABSENT) return null;
        UUID id = template("IN_PROGRESS", type);
        jdbc.update(
                "update task_template_items set name=? where template_id=?",
                type == null ? "Загальний набір" : "Типовий набір",
                id);
        if (state == Candidate.INACTIVE) jdbc.update("update task_templates set is_active=false where id=?", id);
        if (state == Candidate.EMPTY) jdbc.update("delete from task_template_items where template_id=?", id);
        return id;
    }

    static Stream<Arguments> candidates() {
        return Stream.of(Candidate.values())
                .flatMap(exact -> Stream.of(Candidate.values()).map(general -> Arguments.of(exact, general)));
    }

    enum Candidate {
        ABSENT,
        INACTIVE,
        EMPTY,
        POPULATED
    }
}
