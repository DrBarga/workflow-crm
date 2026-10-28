package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;

class ObjectLifecycleRevisionIT extends ObjectStatusTasksSupport {
    @Test
    void persistsBothNewDatesAndShortCommentWithHistoryAndExplicitClearing() throws Exception {
        edit(Map.of(
                        "preliminaryContractSignedDate",
                        "2026-10-01",
                        "commissioningDate",
                        "2026-11-01",
                        "shortComment",
                        "Погоджено огляд приміщення"))
                .andExpect(status().isOk());
        assertThat(version()).isEqualTo(1);
        card().andExpect(jsonPath("$.shortComment").value("Погоджено огляд приміщення"))
                .andExpect(jsonPath("$.workflow.milestones.preliminaryContractSignedDate")
                        .value("2026-10-01"))
                .andExpect(jsonPath("$.workflow.milestones.commissioningDate").value("2026-11-01"));
        assertThat(jdbc.queryForList("select field_name from object_history", String.class))
                .contains("preliminaryContractSignedDate", "commissioningDate", "shortComment");
        var clear = new LinkedHashMap<String, Object>();
        for (String field : List.of("preliminaryContractSignedDate", "commissioningDate", "shortComment"))
            clear.put(field, null);
        edit(clear).andExpect(status().isOk());
        card().andExpect(jsonPath("$.shortComment").isEmpty())
                .andExpect(jsonPath("$.workflow.milestones").isEmpty());
        assertThat(state()).isEqualTo("FOUND");
    }

    @Test
    void calculatesCalendarOpeningAndRecalculatesOnEitherInputOrClearing() throws Exception {
        edit(Map.of(
                        "constructionAcceptancePlannedDate",
                        "2028-02-28",
                        "commercialTerms",
                        Map.of("constructionTermDays", 2)))
                .andExpect(status().isOk());
        assertOpening("2028-03-01");
        edit(Map.of("commercialTerms", Map.of("constructionTermDays", 0))).andExpect(status().isOk());
        assertOpening("2028-02-28");
        edit(Map.of("constructionAcceptancePlannedDate", "2026-12-31")).andExpect(status().isOk());
        assertOpening("2026-12-31");
        edit(Map.of("commercialTerms", Map.of("constructionTermDays", 1))).andExpect(status().isOk());
        assertOpening("2027-01-01");
        edit(Map.of("openingPlannedDate", "2030-01-01")).andExpect(status().isUnprocessableEntity());
        var clear = new LinkedHashMap<String, Object>();
        clear.put("constructionAcceptancePlannedDate", null);
        edit(clear).andExpect(status().isOk());
        assertOpening(null);
        edit(Map.of("constructionAcceptancePlannedDate", "2026-10-23")).andExpect(status().isOk());
        edit(Map.of("commercialTerms", Map.of())).andExpect(status().isOk());
        assertOpening(null);
        assertThat(state()).isEqualTo("FOUND");
    }

    @Test
    void acceptsTwoHundredCharacterCommentOnCreationAndRejectsLongerValues() throws Exception {
        var body = body();
        body.put("shortComment", "а".repeat(200));
        body.put("name", "Інший об'єкт");
        body.put("entrance", Map.of("longitude", 30.6234, "latitude", 50.4501));
        body.put("address", "Інша адреса");
        create(manager, key(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortComment").value("а".repeat(200)));
        edit(Map.of("shortComment", "а".repeat(201))).andExpect(status().isUnprocessableEntity());
        assertThatThrownBy(() -> jdbc.update("update objects set short_comment=repeat('а',201) where id=?", object))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        mvc.perform(get("/api/objects/{id}", object).cookie(cookie(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortComment").doesNotExist());
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
    void signingRequiresEachConfiguredField(String column) throws Exception {
        ready();
        jdbc.update("update objects set contract_signed_date='2026-10-20' where id=?", object);
        jdbc.update("update objects set " + column + "=null where id=?", object);
        var before = version();
        change(lead, command("CONTRACT_SIGNED")).andExpect(status().isUnprocessableEntity());
        assertThat(state()).isEqualTo("CONTRACT_APPROVAL");
        assertThat(version()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEW", "IN_PROGRESS", "PAUSED", "WAITING_APPROVAL", "CANCELLED"})
    void onlyCompletedConfiguredTasksAllowSigning(String taskStatus) throws Exception {
        ready();
        jdbc.update(
                "update tasks set status=? where object_id=? and name='APPROVE_LOTUS_CONTRACT'", taskStatus, object);
        change(lead, signing()).andExpect(status().isUnprocessableEntity());
        assertThat(state()).isEqualTo("CONTRACT_APPROVAL");
    }

    @Test
    void signingDoesNotRequirePreliminaryContractAndZeroConstructionTermIsValid() throws Exception {
        ready();
        change(contract, signing())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(state()).isEqualTo("CONTRACT_SIGNED");
        assertOpening("2026-11-02");
        assertThat(jdbc.queryForObject(
                        "select preliminary_contract_signed_date from objects where id=?", LocalDate.class, object))
                .isNull();
    }

    @Test
    void fullPatchValidatesProposedDatesAndTermBeforeSavingTransition() throws Exception {
        ready();
        jdbc.update(
                "update objects set commissioning_date=null,construction_acceptance_planned_date=null,construction_term_days=null where id=?",
                object);
        edit(Map.of(
                        "contractSignedDate",
                        "2026-10-20",
                        "commissioningDate",
                        "2026-10-21",
                        "constructionAcceptancePlannedDate",
                        "2026-10-23",
                        "commercialTerms",
                        Map.of("constructionTermDays", 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(state()).isEqualTo("CONTRACT_SIGNED");
        assertOpening("2026-10-25");
    }

    @Test
    void manualTaskWithSameTitleCannotReplaceRequiredTemplateItem() throws Exception {
        ready();
        jdbc.update(
                "update tasks set template_item_id=null where object_id=? and name='APPROVE_QUESTIONNAIRE'", object);
        change(lead, signing()).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void revalidatesCompletionAfterPreviewAndExposesRequiredCodes() throws Exception {
        ready();
        template("CONTRACT_SIGNED", null);
        card().andExpect(jsonPath("$.availableTransitions[?(@.code=='SIGN_CONTRACT')].requiredCompletedTaskCodes")
                .exists());
        var body = signing();
        var preview = response(change(lead, body).andExpect(status().isOk()));
        assertThat(preview.path("saved").asBoolean()).isFalse();
        body.put("confirmationToken", preview.path("confirmationToken").asText());
        jdbc.update(
                "update tasks set status='WAITING_APPROVAL' where object_id=? and name='APPROVE_FINANCIAL_MODEL'",
                object);
        change(lead, body).andExpect(status().isUnprocessableEntity());
        assertThat(state()).isEqualTo("CONTRACT_APPROVAL");
        assertThat(count("task_sets")).isZero();
    }

    @Test
    void directStatusActionAcceptsRequiredDatesAndUsesSameCalculation() throws Exception {
        ready();
        jdbc.update(
                "update objects set commissioning_date=null,construction_acceptance_planned_date=null,construction_term_days=null where id=?",
                object);
        var body = signing();
        body.put("commissioningDate", "2026-10-21");
        body.put("constructionAcceptancePlannedDate", "2026-10-23");
        body.put("constructionTermDays", 2);
        change(lead, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertOpening("2026-10-25");
    }

    @Test
    void signingActionDoesNotGrantContractRoleCommercialFieldEditing() throws Exception {
        ready();
        var body = signing();
        body.put("constructionTermDays", 2);
        change(contract, body).andExpect(status().isUnprocessableEntity());
        assertThat(state()).isEqualTo("CONTRACT_APPROVAL");
        assertOpening("2026-11-02");
    }

    @Test
    void completedHistoryCannotReplaceCurrentOrCancelledTaskSets() throws Exception {
        ready();
        UUID old = UUID.randomUUID(), current = UUID.randomUUID();
        jdbc.update(
                "insert into task_sets(id,object_id,trigger_status_id,state,created_at) values(?,?,?,'COMPLETED','2026-01-01')",
                old,
                object,
                statusId("CONTRACT_APPROVAL"));
        jdbc.update("update tasks set task_set_id=? where object_id=?", old, object);
        jdbc.update(
                "insert into task_sets(id,object_id,trigger_status_id,state,created_at) values(?,?,?,'ACTIVE','2026-02-01')",
                current,
                object,
                statusId("CONTRACT_APPROVAL"));
        jdbc.update(
                """
                insert into tasks(object_id,source_status_id,template_item_id,name,assignee_id,deadline,status,author_id,task_set_id)
                select object_id,source_status_id,template_item_id,name,assignee_id,deadline,'NEW',author_id,?
                from tasks where task_set_id=?
                """,
                current,
                old);
        change(lead, signing()).andExpect(status().isUnprocessableEntity());
        jdbc.update("update task_sets set state='CANCELLED' where object_id=?", object);
        change(lead, signing()).andExpect(status().isUnprocessableEntity());
        assertThat(state()).isEqualTo("CONTRACT_APPROVAL");
    }

    @Test
    void requiredTaskCodesSurviveRenaming() throws Exception {
        ready();
        jdbc.update("update task_template_items set name='Renamed template item'");
        jdbc.update("update tasks set name='Renamed task' where object_id=?", object);
        change(lead, signing())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(state()).isEqualTo("CONTRACT_SIGNED");
    }

    private void ready() {
        state("CONTRACT_APPROVAL");
        jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);
        ObjectSigningTestData.ready(jdbc, object, manager);
    }

    private Map<String, Object> signing() {
        var body = command("CONTRACT_SIGNED");
        body.put("contractSignedDate", "2026-10-20");
        return body;
    }

    private ResultActions card() throws Exception {
        return mvc.perform(get("/api/objects/{id}", object).cookie(cookie(lead)))
                .andExpect(status().isOk());
    }

    private ResultActions edit(Map<String, Object> values) throws Exception {
        var body = new LinkedHashMap<String, Object>(values);
        body.put("version", version());
        return send(lead, "/api/objects/" + object, body, true);
    }

    private void assertOpening(String expected) {
        assertThat(jdbc.queryForObject(
                        "select opening_planned_date::text from objects where id=?", String.class, object))
                .isEqualTo(expected);
    }
}
