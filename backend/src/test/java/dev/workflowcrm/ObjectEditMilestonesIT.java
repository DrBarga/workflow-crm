package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ObjectEditMilestonesIT extends ObjectStatusTasksSupport {
    ResultActions patch(UUID actor, Map<String, Object> body) throws Exception {
        return mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/objects/{id}", object)
                        .cookie(cookie(actor), csrf)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(body)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"REGIONAL_MANAGER", "DEVELOPMENT_DIRECTOR", "CONTRACT_MANAGER"})
    void signingPatchUsesConfiguredTransition(String role) throws Exception {
        state("CONTRACT_APPROVAL");
        ObjectSigningTestData.ready(jdbc, object, manager);
        jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);
        var result = response(patch(actor(role), Map.of("version", version(), "contractSignedDate", "2026-10-07"))
                .andExpect(status().isOk()));
        assertThat(result.path("version").asLong()).isEqualTo(1);
        assertThat(state()).isEqualTo("CONTRACT_SIGNED");
    }

    @Test
    void signingNeedsContractTypeAndPreservesUnfinishedTasks() throws Exception {
        state("CONTRACT_APPROVAL");
        ObjectSigningTestData.ready(jdbc, object, manager);
        patch(lead, Map.of("version", version(), "contractSignedDate", "2026-10-07"))
                .andExpect(status().isOk());
        assertThat(state()).isEqualTo("CONTRACT_APPROVAL");
        assertThat(version()).isEqualTo(1);
        jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);
        task("IN_PROGRESS");
        var before = taskSnapshot();
        patch(lead, Map.of("version", version(), "contractSignedDate", "2026-10-07"))
                .andExpect(status().isOk());
        assertThat(taskSnapshot()).isEqualTo(before);
        assertThat(state()).isEqualTo("CONTRACT_SIGNED");
        assertThat(version()).isEqualTo(2);
    }

    @Test
    void previewHoldsEntirePatchAndBindsOrdinaryFields() throws Exception {
        state("CONTRACT_APPROVAL");
        ObjectSigningTestData.ready(jdbc, object, manager);
        jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);
        template("CONTRACT_SIGNED", "PRIMARY");
        var body = new LinkedHashMap<String, Object>(
                Map.of("version", version(), "contractSignedDate", "2026-10-07", "name", "Нова назва"));
        var preview = response(patch(lead, body).andExpect(status().isOk()));
        assertThat(preview.path("saved").asBoolean()).isFalse();
        assertThat(preview.path("tasks")).hasSize(1);
        assertThat(version()).isZero();
        assertThat(jdbc.queryForObject("select name from objects where id=?", String.class, object))
                .isNotEqualTo("Нова назва");
        body.put("confirmationToken", preview.path("confirmationToken").asText());
        body.put("name", "Підмінена назва");
        patch(lead, body).andExpect(status().isConflict());
        body.put("name", "Нова назва");
        patch(lead, body).andExpect(status().isOk());
        assertThat(state()).isEqualTo("CONTRACT_SIGNED");
        assertThat(version()).isEqualTo(1);
        assertThat(taskSnapshot()).hasSize(4);
        assertThat(jdbc.queryForObject("select name from objects where id=?", String.class, object))
                .isEqualTo("Нова назва");
    }

    @Test
    void removedTemplateInvalidatesPreviewWithoutSavingFields() throws Exception {
        state("CONTRACT_APPROVAL");
        ObjectSigningTestData.ready(jdbc, object, manager);
        jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);
        var template = template("CONTRACT_SIGNED", "PRIMARY");
        var body = new LinkedHashMap<String, Object>(Map.of("version", version(), "contractSignedDate", "2026-10-07"));
        body.put(
                "confirmationToken",
                response(patch(lead, body)).path("confirmationToken").asText());
        jdbc.update("update task_templates set is_active=false where id=?", template);
        patch(lead, body).andExpect(status().isConflict());
        assertThat(version()).isZero();
        assertThat(state()).isEqualTo("CONTRACT_APPROVAL");
    }

    @Test
    void actualOpeningUsesTransitionWhilePlannedDateDoesNot() throws Exception {
        state("CONTRACT_SIGNED");
        jdbc.update(
                "update objects set contract_signed_date='2026-10-01',contract_type_id=? where id=?",
                contractType(),
                object);
        patch(
                        lead,
                        Map.of(
                                "version",
                                version(),
                                "constructionAcceptancePlannedDate",
                                "2026-10-10",
                                "commercialTerms",
                                Map.of("constructionTermDays", 0)))
                .andExpect(status().isOk());
        assertThat(state()).isEqualTo("CONTRACT_SIGNED");
        patch(manager, Map.of("version", version(), "openingActualDate", "2026-10-07"))
                .andExpect(status().isForbidden());
        patch(director, Map.of("version", version(), "openingActualDate", "2026-10-07"))
                .andExpect(status().isOk());
        assertThat(state()).isEqualTo("OPENED");
    }
}
