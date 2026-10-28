package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.workflowcrm.objects.domain.ObjectTaskSetProcessor;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class TaskAssignmentRulesIT extends TaskAssignmentTestSupport {
    @Autowired
    ObjectTaskSetProcessor processor;

    @ParameterizedTest
    @ValueSource(strings = {"REGIONAL_ROLE", "NETWORK_ROLE"})
    void managerRoleAlwaysUsesObjectOwner(String rule) throws Exception {
        roleItem("IN_PROGRESS", rule, "MANAGER");
        assertThat(preview("IN_PROGRESS").at("/tasks/0/assigneeId").asText()).isEqualTo(manager.toString());
        generate("IN_PROGRESS", manager, command("IN_PROGRESS"));
        assertThat(jdbc.queryForObject("select assignee_id from tasks", UUID.class))
                .isEqualTo(manager);
    }

    @Test
    void singleRegionalRoleUsesAnyAssignedMatchingRegion() throws Exception {
        roleItem("IN_PROGRESS", "REGIONAL_ROLE", "REGIONAL_MANAGER");
        UserRegionTestData.replace(jdbc, lead, otherRegion, region);
        assertThat(preview("IN_PROGRESS").at("/tasks/0/assigneeId").asText()).isEqualTo(lead.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"REGIONAL_ROLE", "NETWORK_ROLE"})
    void regionalRoleNeverFallsBackOutsideItsRegions(String rule) throws Exception {
        roleItem("IN_PROGRESS", rule, "REGIONAL_MANAGER");
        jdbc.update("update users set status='DISABLED' where id=?", lead);
        var assignment = preview("IN_PROGRESS").at("/tasks/0/assignment");
        assertThat(assignment.path("candidates")).isEmpty();
        assertThat(assignment.path("selectionRequired").asBoolean()).isTrue();
        assertThat(assignment.path("message").asText()).contains("Адміністратора");
        change(manager, command("IN_PROGRESS")).andExpect(status().isUnprocessableEntity());
        assertThat(count("task_sets")).isZero();
    }

    @Test
    void multipleRegionalCandidatesAreCompleteSortedAndExcludeDisabledAndForeignUsers() throws Exception {
        UUID item = roleItem("IN_PROGRESS", "REGIONAL_ROLE", "REGIONAL_MANAGER");
        UUID second = UUID.randomUUID(), third = UUID.randomUUID(), disabled = UUID.randomUUID();
        user(second, "REGIONAL_MANAGER", region);
        user(third, "REGIONAL_MANAGER", region);
        user(disabled, "REGIONAL_MANAGER", region);
        jdbc.update("update users set full_name='Z' where id=?", lead);
        jdbc.update("update users set full_name='A' where id=?", second);
        jdbc.update("update users set full_name='M' where id=?", third);
        jdbc.update("update users set status='DISABLED' where id=?", disabled);
        var candidates = preview("IN_PROGRESS").at("/tasks/0/assignment/candidates");
        assertThat(candidates.findValuesAsText("id"))
                .containsExactly(second.toString(), third.toString(), lead.toString());
        var body = selected("IN_PROGRESS", item, third);
        generate("IN_PROGRESS", manager, body);
        assertThat(jdbc.queryForObject("select assignee_id from tasks", UUID.class))
                .isEqualTo(third);
        assertThat(jdbc.queryForObject("select manager_id from objects where id=?", UUID.class, object))
                .isEqualTo(manager);
    }

    @Test
    void networkRoleIsAssignedWithoutRegionalRestriction() throws Exception {
        roleItem("IN_PROGRESS", "NETWORK_ROLE", "CONTRACT_MANAGER");
        UserRegionTestData.replace(jdbc, contract, otherRegion);
        assertThat(preview("IN_PROGRESS").at("/tasks/0/assigneeId").asText()).isEqualTo(contract.toString());
    }

    @Test
    void networkRoleWithRegionalPreferenceOffersNetworkFallbackForManualChoice() throws Exception {
        UUID item = roleItem("IN_PROGRESS", "REGIONAL_ROLE", "CONTRACT_MANAGER");
        var task = preview("IN_PROGRESS").at("/tasks/0");
        assertThat(task.path("assigneeId").isNull()).isTrue();
        assertThat(task.at("/assignment/candidates").findValuesAsText("id")).containsExactly(contract.toString());
        generate("IN_PROGRESS", manager, selected("IN_PROGRESS", item, contract));
        assertThat(jdbc.queryForObject("select assignee_id from tasks", UUID.class))
                .isEqualTo(contract);
    }

    @Test
    void contractApprovalAssignsActualConfirmerAndInitiatorHasInformationalPreviewOnly() throws Exception {
        state("COMMERCIAL_TERMS_APPROVAL");
        UUID item = roleItem("CONTRACT_APPROVAL", "NETWORK_ROLE", "CONTRACT_MANAGER");
        UUID another = UUID.randomUUID();
        user(another, "CONTRACT_MANAGER", null);
        var info = response(modal(lead, command("CONTRACT_APPROVAL")).andExpect(status().isOk()));
        assertThat(info.path("confirmationToken").isNull()).isTrue();
        var request = response(change(lead, command("CONTRACT_APPROVAL")).andExpect(status().isOk()))
                .path("approvalRequestId")
                .asText();
        var body = new LinkedHashMap<String, Object>();
        body.put("approvalRequestId", request);
        var preview = response(modal(another, body).andExpect(status().isOk()));
        assertThat(preview.at("/tasks/0/assigneeId").asText()).isEqualTo(another.toString());
        body.put("confirmationToken", preview.path("confirmationToken").asText());
        submit(another, body).andExpect(status().isAccepted());
        processor.processNext();
        assertThat(jdbc.queryForObject("select assignee_id from tasks where template_item_id=?", UUID.class, item))
                .isEqualTo(another);
        assertThat(state()).isEqualTo("CONTRACT_APPROVAL");
    }

    @Test
    void absentConfirmationUsesGeneralContractRoleSelection() throws Exception {
        roleItem("IN_PROGRESS", "NETWORK_ROLE", "CONTRACT_MANAGER");
        UUID another = UUID.randomUUID();
        user(another, "CONTRACT_MANAGER", null);
        var task = preview("IN_PROGRESS").at("/tasks/0");
        assertThat(task.path("assigneeId").isNull()).isTrue();
        assertThat(task.at("/assignment/candidates").findValuesAsText("id"))
                .containsExactlyInAnyOrder(contract.toString(), another.toString());
    }

    @Test
    void inactiveOwnerDoesNotSilentlyAssignAnotherManager() throws Exception {
        roleItem("IN_PROGRESS", "NETWORK_ROLE", "MANAGER");
        jdbc.update("update users set status='DISABLED' where id=?", manager);
        var body = response(modal(director, command("IN_PROGRESS")).andExpect(status().isOk()));
        assertThat(body.at("/tasks/0/assignment/candidates")).isEmpty();
        assertThat(body.path("confirmationToken").isNull()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "FOREIGN", "WRONG_ROLE", "DISABLED", "UNKNOWN_ITEM"})
    void invalidSelectionReturns422WithoutChanges(String variant) throws Exception {
        UUID item = roleItem("IN_PROGRESS", "REGIONAL_ROLE", "REGIONAL_MANAGER");
        UUID second = UUID.randomUUID();
        user(second, "REGIONAL_MANAGER", region);
        var body = selected("IN_PROGRESS", item, second);
        switch (variant) {
            case "MISSING" -> body.remove("assignees");
            case "FOREIGN" -> body.put("assignees", Map.of(item.toString(), foreignLead.toString()));
            case "WRONG_ROLE" -> body.put("assignees", Map.of(item.toString(), colleague.toString()));
            case "DISABLED" -> jdbc.update("update users set status='DISABLED' where id=?", second);
            case "UNKNOWN_ITEM" ->
                body.put("assignees", Map.of(UUID.randomUUID().toString(), lead.toString()));
        }
        change(manager, body).andExpect(status().isUnprocessableEntity());
        assertThat(count("task_sets")).isZero();
        assertThat(count("tasks")).isZero();
        assertThat(state()).isEqualTo("FOUND");
    }

    @Test
    void selectionIsRevalidatedByQueueWorker() throws Exception {
        UUID item = roleItem("IN_PROGRESS", "REGIONAL_ROLE", "REGIONAL_MANAGER");
        UUID second = UUID.randomUUID();
        user(second, "REGIONAL_MANAGER", region);
        var body = confirm(manager, selected("IN_PROGRESS", item, second));
        submit(manager, body).andExpect(status().isAccepted());
        jdbc.update("update users set status='DISABLED' where id=?", second);
        processor.processNext();
        assertThat(count("tasks")).isZero();
        assertThat(state()).isEqualTo("FOUND");
        assertThat(jdbc.queryForObject("select error from object_task_set_jobs", String.class))
                .isEqualTo("INVALID_TRANSITION");
    }

    @Test
    void selectedCandidateSurvivesQueueAndDuplicateConfirmationCreatesOneSet() throws Exception {
        UUID item = roleItem("IN_PROGRESS", "REGIONAL_ROLE", "REGIONAL_MANAGER");
        UUID second = UUID.randomUUID();
        user(second, "REGIONAL_MANAGER", region);
        var body = confirm(manager, selected("IN_PROGRESS", item, second));
        var first = response(submit(manager, body).andExpect(status().isAccepted()));
        assertThat(response(submit(manager, body).andExpect(status().isAccepted()))
                        .path("id"))
                .isEqualTo(first.path("id"));
        assertThat(processor.processNext()).isTrue();
        assertThat(processor.processNext()).isFalse();
        assertThat(jdbc.queryForObject("select assignee_id from tasks where template_item_id=?", UUID.class, item))
                .isEqualTo(second);
        assertThat(count("task_sets")).isEqualTo(1);
        submit(manager, body).andExpect(status().isOk());
    }

    @Test
    void changingSelectedCandidateRequiresFreshConfirmation() throws Exception {
        UUID item = roleItem("IN_PROGRESS", "REGIONAL_ROLE", "REGIONAL_MANAGER");
        UUID second = UUID.randomUUID();
        user(second, "REGIONAL_MANAGER", region);
        var body = confirm(manager, selected("IN_PROGRESS", item, second));
        body.put("assignees", Map.of(item.toString(), lead.toString()));
        submit(manager, body).andExpect(status().isConflict());
        assertThat(count("object_task_set_jobs")).isZero();
        assertThat(count("tasks")).isZero();
    }
}
