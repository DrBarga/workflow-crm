package dev.workflowcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class ObjectTransitionTaskSetsIT extends ObjectStatusTasksSupport {
    private static final String LOTUS = "APPROVE_LOTUS_CONTRACT";

    @Test
    void latestSetContainingEachElementControlsItsOwnCompletion() throws Exception {
        ready();
        snapshot("COMPLETED", "2026-01-01", Map.of(LOTUS, "DONE", "APPROVE_FINANCIAL_MODEL", "DONE"));
        UUID latest = snapshot("CANCELLED", "2026-02-01", Map.of(LOTUS, "CANCELLED"));
        snapshot("ACTIVE", "2026-03-01", Map.of("UNRELATED", "NEW"));
        var error = response(change(lead, signing()).andExpect(status().isUnprocessableEntity()));
        var unmet = error.path("unmetConditions");
        assertThat(unmet.path("missingFields")).isEmpty();
        assertThat(unmet.path("missingTaskCodes")).isEmpty();
        assertThat(unmet.path("incompleteTasks")).hasSize(1);
        assertThat(unmet.path("incompleteTasks").get(0).path("code").asText()).isEqualTo(LOTUS);
        assertThat(unmet.path("incompleteTasks").get(0).path("status").asText()).isEqualTo("CANCELLED");
        var card = response(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/objects/{id}", object)
                        .cookie(cookie(lead)))
                .andExpect(status().isOk()));
        var cardConditions = java.util.stream.StreamSupport.stream(
                        card.path("availableTransitions").spliterator(), false)
                .filter(t -> t.path("code").asText().equals("SIGN_CONTRACT"))
                .findFirst()
                .orElseThrow()
                .path("unmetConditions");
        assertThat(cardConditions.path("incompleteTasks")).isEqualTo(unmet.path("incompleteTasks"));
        jdbc.update("update tasks set status='DONE' where task_set_id=?", latest);
        change(lead, signing())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(state()).isEqualTo("CONTRACT_SIGNED");
    }

    @Test
    void absentTaskInLatestSnapshotDoesNotFallBackToOlderDoneTask() throws Exception {
        ready();
        snapshot("COMPLETED", "2026-01-01", Map.of(LOTUS, "DONE"));
        snapshot("CANCELLED", "2026-02-01", Map.of(LOTUS, "ABSENT"));
        snapshot("ACTIVE", "2026-03-01", Map.of("UNRELATED", "DONE"));
        change(lead, signing())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.unmetConditions.incompleteTasks").isEmpty())
                .andExpect(jsonPath("$.unmetConditions.missingTaskCodes[0]").value(LOTUS));
        assertThat(version()).isZero();
    }

    @Test
    void completedTaskInCancelledSetStillCountsWhenItIsTheLatestForElement() throws Exception {
        ready();
        snapshot("CANCELLED", "2026-02-01", Map.of(LOTUS, "DONE"));
        snapshot("ACTIVE", "2026-03-01", Map.of("UNRELATED", "NEW"));
        change(lead, signing()).andExpect(status().isOk());
        assertThat(state()).isEqualTo("CONTRACT_SIGNED");
    }

    @Test
    void anotherDoneTaskCannotHideAnIncompleteTaskInTheLatestElementSet() throws Exception {
        ready();
        UUID current = snapshot("ACTIVE", "2026-03-01", Map.of(LOTUS, "NEW"));
        jdbc.update(
                "insert into tasks(object_id,source_status_id,source_item_id,template_version_id,task_set_id,name,assignee_id,deadline,status,author_id) select object_id,source_status_id,source_item_id,template_version_id,task_set_id,'Repeated approval',assignee_id,deadline,'DONE',author_id from tasks where task_set_id=?",
                current);
        change(lead, signing())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        jsonPath("$.unmetConditions.incompleteTasks[0].status").value("NEW"));
        assertThat(version()).isZero();
    }

    private void ready() {
        state("CONTRACT_APPROVAL");
        ObjectSigningTestData.ready(jdbc, object, manager);
        jdbc.update("update objects set contract_type_id=? where id=?", contractType(), object);
    }

    private Map<String, Object> signing() {
        var command = command("CONTRACT_SIGNED");
        command.put("contractSignedDate", "2026-10-20");
        return command;
    }

    private UUID snapshot(String state, String created, Map<String, String> items) {
        UUID template = jdbc.queryForObject(
                "select id from task_templates where trigger_status_id=?", UUID.class, statusId("CONTRACT_APPROVAL"));
        UUID version = UUID.randomUUID(), set = UUID.randomUUID();
        int revision = jdbc.queryForObject(
                "select coalesce(max(revision),0)+1 from task_template_versions where template_id=?",
                Integer.class,
                template);
        jdbc.update(
                "insert into task_template_versions(id,template_id,revision,fingerprint,trigger_status_id) values(?,?,?,?,?)",
                version,
                template,
                revision,
                version.toString().replace("-", "").repeat(2),
                statusId("CONTRACT_APPROVAL"));
        jdbc.update(
                "insert into task_sets(id,object_id,trigger_status_id,template_version_id,state,created_at) values(?,?,?,?,?,?::timestamptz)",
                set,
                object,
                statusId("CONTRACT_APPROVAL"),
                version,
                state,
                created);
        int order = 0;
        for (var item : items.entrySet()) {
            UUID id = UUID.randomUUID();
            jdbc.update(
                    "insert into template_items(id,source_item_id,template_version_id,name,code,deadline_rule,deadline_offset_days,assignment_rule,assignee_id,sort_order) values(?,?,?,?,?,'FROM_START_CALENDAR',0,'EXPLICIT_USER',?,?)",
                    id,
                    UUID.randomUUID(),
                    version,
                    item.getKey(),
                    item.getKey(),
                    manager,
                    order++);
            if (!item.getValue().equals("ABSENT"))
                jdbc.update(
                        "insert into tasks(object_id,source_status_id,source_item_id,template_version_id,task_set_id,name,assignee_id,deadline,status,author_id) values(?,?,?,?,?,?,?,current_date,?,?)",
                        object,
                        statusId("CONTRACT_APPROVAL"),
                        id,
                        version,
                        set,
                        item.getKey(),
                        manager,
                        item.getValue(),
                        manager);
        }
        return set;
    }
}
