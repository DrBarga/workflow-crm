package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.objects.domain.ObjectTaskSetProcessor;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ObjectTaskSetQueueIT extends ObjectStatusTaskSetsSupport {
    @Autowired
    ObjectTaskSetProcessor processor;

    @Test
    void confirmsOneHundredTasksUnderThreeSecondsAndProcessesThemAfterResponse() throws Exception {
        UUID template = template("IN_PROGRESS", null);
        List<Object[]> items = new ArrayList<>();
        for (int i = 1; i < 100; i++) items.add(new Object[] {template, "Задача " + i, manager, i, i});
        jdbc.batchUpdate(
                "insert into task_template_items(template_id,name,assignee_id,deadline_offset_days,sort_order) values(?,?,?,?,?)",
                items);
        var body = confirmed(manager, "IN_PROGRESS");
        long start = System.nanoTime();
        var receipt = response(submit(manager, key(), body)
                .andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store")));
        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        assertThat(millis).as("HTTP acknowledgement for 100 tasks").isLessThan(3000);
        System.out.println("T5808 acknowledgement for 100 tasks: " + millis + " ms");
        assertThat(state()).isEqualTo("FOUND");
        assertThat(count("tasks")).isZero();
        assertThat(count("task_sets")).isZero();
        assertThat(processor.processNext()).isTrue();
        assertThat(state()).isEqualTo("IN_PROGRESS");
        assertThat(count("tasks")).isEqualTo(100);
        assertThat(jdbc.queryForObject("select count(*) from tasks where status='IN_PROGRESS'", Integer.class))
                .isEqualTo(100);
        assertThat(jdbc.queryForObject(
                        "select count(*) from user_notifications where event_type='TASK_STARTED'", Integer.class))
                .isEqualTo(100);
        poll(manager, receipt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.taskSetId").isNotEmpty());
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_history where field_name='TASK_SET_CREATED'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from object_status_audit where event_type='TASK_SET_CREATED'", Integer.class))
                .isEqualTo(1);
        assertThat(processor.processNext()).isFalse();
    }

    @Test
    void repeatKeysAndDoubleConfirmationReturnSameSetBeforeAndAfterCompletion() throws Exception {
        template("IN_PROGRESS", null);
        var body = confirmed(manager, "IN_PROGRESS");
        String key = key();
        var first = response(submit(manager, key, body).andExpect(status().isAccepted()));
        var repeated = response(submit(manager, key, body).andExpect(status().isAccepted()));
        var doubleClick = response(submit(manager, key(), body).andExpect(status().isAccepted()));
        assertThat(repeated.path("id")).isEqualTo(first.path("id"));
        assertThat(doubleClick.path("id")).isEqualTo(first.path("id"));
        assertThat(count("object_task_set_jobs")).isEqualTo(1);
        processor.processNext();
        var replay = response(submit(manager, key, body).andExpect(status().isOk()));
        assertThat(replay.path("id")).isEqualTo(first.path("id"));
        assertThat(replay.path("taskSetId").asText())
                .isEqualTo(jdbc.queryForObject("select id from task_sets", UUID.class)
                        .toString());
        assertThat(count("task_sets")).isEqualTo(1);
        assertThat(processor.process(UUID.fromString(first.path("id").asText())))
                .isFalse();
    }

    @Test
    void concurrentConfirmationsCreateOneDurableJob() throws Exception {
        template("IN_PROGRESS", null);
        var body = confirmed(manager, "IN_PROGRESS");
        try (var threads = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<String> request = () -> {
                gate.await();
                return response(submit(manager, key(), body).andExpect(status().isAccepted()))
                        .path("id")
                        .asText();
            };
            var first = threads.submit(request);
            var second = threads.submit(request);
            gate.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
        }
        assertThat(count("object_task_set_jobs")).isEqualTo(1);
        processor.processNext();
        assertThat(count("task_sets")).isEqualTo(1);
    }

    @Test
    void changedPayloadCannotReuseKeyOrConfirmedToken() throws Exception {
        template("IN_PROGRESS", null);
        var body = confirmed(manager, "IN_PROGRESS");
        String key = key();
        submit(manager, key, body).andExpect(status().isAccepted());
        body.put("status", "IC_PREPARATION");
        submit(manager, key, body).andExpect(status().isConflict());
        submit(manager, key(), body).andExpect(status().isConflict());
        assertThat(count("object_task_set_jobs")).isEqualTo(1);
    }

    @Test
    void missingKeyMissingConfirmationAndForgedPreviewDoNotQueueWork() throws Exception {
        template("IN_PROGRESS", null);
        var body = confirmed(manager, "IN_PROGRESS");
        mvc.perform(post(path())
                        .cookie(cookie(manager), csrf)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest());
        submit(manager, "bad key", body).andExpect(status().isUnprocessableEntity());
        body.remove("confirmationToken");
        submit(manager, key(), body).andExpect(status().isUnprocessableEntity());
        body.put("confirmationToken", "a".repeat(64));
        submit(manager, key(), body).andExpect(status().isConflict());
        assertThat(count("object_task_set_jobs")).isZero();
        assertThat(state()).isEqualTo("FOUND");
    }

    @Test
    void failureAfterFirstTaskRollsBackTasksVersionNotificationsHistoryAndObjectStatus() throws Exception {
        UUID template = template("IN_PROGRESS", null);
        jdbc.update(
                "insert into task_template_items(template_id,name,assignee_id,deadline_offset_days,sort_order) values(?,'Rejected task',?,1,1)",
                template,
                manager);
        var body = confirmed(manager, "IN_PROGRESS");
        String submissionKey = key();
        var receipt = response(submit(manager, submissionKey, body).andExpect(status().isAccepted()));
        long version = version();
        jdbc.execute("alter table tasks add constraint ck_generation_test check(name<>'Rejected task')");
        try {
            processor.processNext();
        } finally {
            jdbc.execute("alter table tasks drop constraint ck_generation_test");
        }
        assertThat(state()).isEqualTo("FOUND");
        assertThat(version()).isEqualTo(version);
        for (String table : List.of(
                "tasks",
                "task_sets",
                "task_template_versions",
                "user_notifications",
                "object_history",
                "object_status_audit")) assertThat(count(table)).as(table).isZero();
        assertThat(events("TASK_GENERATION_REQUESTED")).isZero();
        poll(manager, receipt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILED"))
                .andExpect(jsonPath("$.error").value("GENERATION_FAILED"));
        submit(manager, submissionKey, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILED"));
        submit(manager, key(), body).andExpect(status().isAccepted());
        processor.processNext();
        assertThat(count("tasks")).isEqualTo(2);
        assertThat(count("task_sets")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OBJECT", "TEMPLATE", "ASSIGNEE"})
    void workerRejectsChangedPreviewWithoutApplyingTransition(String mutation) throws Exception {
        UUID template = template("IN_PROGRESS", null);
        var receipt = response(
                submit(manager, key(), confirmed(manager, "IN_PROGRESS")).andExpect(status().isAccepted()));
        switch (mutation) {
            case "OBJECT" -> jdbc.update("update objects set version=version+1 where id=?", object);
            case "TEMPLATE" ->
                jdbc.update("update task_template_items set name='Changed task' where template_id=?", template);
            case "ASSIGNEE" ->
                jdbc.update("update task_template_items set assignee_id=? where template_id=?", colleague, template);
        }
        processor.processNext();
        poll(manager, receipt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILED"));
        assertThat(state()).isEqualTo("FOUND");
        assertThat(count("tasks")).isZero();
        assertThat(count("task_sets")).isZero();
    }

    @Test
    void accessIsCheckedAtSubmissionPollingAndProcessing() throws Exception {
        template("IN_PROGRESS", null);
        var body = confirmed(manager, "IN_PROGRESS");
        submit(outsider, key(), body).andExpect(status().isForbidden());
        var receipt = response(submit(manager, key(), body).andExpect(status().isAccepted()));
        poll(colleague, receipt).andExpect(status().isForbidden());
        poll(lead, receipt).andExpect(status().isNotFound());
        jdbc.update("update users set status='DISABLED' where id=?", manager);
        processor.processNext();
        assertThat(jdbc.queryForObject("select state from object_task_set_jobs", String.class))
                .isEqualTo("FAILED");
        assertThat(state()).isEqualTo("FOUND");
        assertThat(count("tasks")).isZero();
    }

    @Test
    void preservesDependenciesRecipientsAndAllPreviewFields() throws Exception {
        UUID template = template("IN_PROGRESS", null);
        UUID first =
                jdbc.queryForObject("select id from task_template_items where template_id=?", UUID.class, template);
        UUID second = UUID.randomUUID();
        jdbc.update(
                "insert into task_template_items(id,template_id,name,assignee_id,deadline_offset_days,sort_order,needs_review,result_recipient_role_id) values(?,?,'Dependent',?,7,1,false,?)",
                second,
                template,
                manager,
                jdbc.queryForObject("select id from roles where code='REGIONAL_MANAGER'", UUID.class));
        jdbc.update(
                "insert into task_template_item_dependencies(template_id,item_id,predecessor_id) values(?,?,?)",
                template,
                second,
                first);
        var command = command("IN_PROGRESS");
        var preview =
                response(send(manager, path() + "/preview", command, false).andExpect(status().isOk()));
        command.put("confirmationToken", preview.path("confirmationToken").asText());
        submit(manager, key(), command).andExpect(status().isAccepted());
        processor.processNext();
        for (var planned : preview.path("tasks")) {
            var saved = jdbc.queryForMap(
                    "select name,deadline,assignee_id,needs_review,result_recipient_id,status from tasks where template_item_id=?",
                    UUID.fromString(planned.path("templateItemId").asText()));
            assertThat(saved.get("name")).isEqualTo(planned.path("name").asText());
            assertThat(saved.get("deadline").toString())
                    .isEqualTo(planned.path("deadline").asText());
            assertThat(saved.get("assignee_id").toString())
                    .isEqualTo(planned.path("assigneeId").asText());
            assertThat(saved.get("needs_review"))
                    .isEqualTo(planned.path("needsReview").asBoolean());
            assertThat(saved.get("status"))
                    .isEqualTo(planned.path("predecessors").isEmpty() ? "IN_PROGRESS" : "NEW");
        }
        assertThat(jdbc.queryForObject(
                        "select result_recipient_id from tasks where template_item_id=?", UUID.class, second))
                .isEqualTo(lead);
        assertThat(count("task_dependencies")).isEqualTo(1);
    }

    @Test
    void returnToActiveSetReusesItAndDatabaseRejectsSecondActiveSet() throws Exception {
        template("IN_PROGRESS", null);
        UUID existing = set("IN_PROGRESS", "ACTIVE");
        var receipt = response(
                submit(manager, key(), confirmed(manager, "IN_PROGRESS")).andExpect(status().isAccepted()));
        processor.processNext();
        poll(manager, receipt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskSetId").value(existing.toString()));
        assertThat(count("task_sets")).isEqualTo(1);
        assertThat(count("tasks")).isZero();
        assertThatThrownBy(() -> set("IN_PROGRESS", "ACTIVE"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void cancelledSetAllowsNewSetAndMissingTemplateAllowsTransitionWithoutTasks() throws Exception {
        UUID existing = set("IN_PROGRESS", "CANCELLED");
        var missing = response(
                submit(manager, key(), confirmed(manager, "IN_PROGRESS")).andExpect(status().isAccepted()));
        processor.processNext();
        assertThat(state()).isEqualTo("IN_PROGRESS");
        poll(manager, missing)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskSetId").isEmpty());
        assertThat(events("OBJECT_TASK_TEMPLATE_MISSING")).isEqualTo(1);
        state("FOUND");
        template("IN_PROGRESS", null);
        submit(manager, key(), confirmed(manager, "IN_PROGRESS")).andExpect(status().isAccepted());
        processor.processNext();
        assertThat(count("task_sets")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select state from task_sets where id=?", String.class, existing))
                .isEqualTo("CANCELLED");
    }

    @Test
    void initiatorRequestsApprovalWithoutConfirmingTheTaskSet() throws Exception {
        state("IN_PROGRESS");
        template("IC_PREPARATION", null);
        var informational = response(send(manager, path() + "/preview", command("IC_PREPARATION"), false)
                .andExpect(status().isOk()));
        assertThat(informational.path("confirmationToken").isNull()).isTrue();
        var waiting = response(change(manager, command("IC_PREPARATION")).andExpect(status().isOk()));
        assertThat(state()).isEqualTo("IN_PROGRESS");
        assertThat(count("tasks")).isZero();
        UUID request = UUID.fromString(waiting.path("approvalRequestId").asText());
        var preview = response(approval(lead, request, "approve", Map.of()).andExpect(status().isOk()));
        approval(
                        lead,
                        request,
                        "approve",
                        Map.of(
                                "confirmationToken",
                                preview.path("confirmationToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(count("tasks")).isEqualTo(1);
        assertThat(count("object_task_set_jobs")).isZero();
    }

    @Test
    void transientDatabaseFailureRetriesTheWholeTransaction() throws Exception {
        template("IN_PROGRESS", null);
        var receipt = response(
                submit(manager, key(), confirmed(manager, "IN_PROGRESS")).andExpect(status().isAccepted()));
        jdbc.execute(
                "create function fail_generation_test() returns trigger language plpgsql as $$ begin raise exception 'retry' using errcode='40001'; end $$");
        jdbc.execute(
                "create trigger fail_generation_test before insert on tasks for each row execute function fail_generation_test()");
        try {
            processor.processNext();
        } finally {
            jdbc.execute("drop trigger fail_generation_test on tasks");
            jdbc.execute("drop function fail_generation_test()");
        }
        assertThat(state()).isEqualTo("FOUND");
        assertThat(count("task_sets")).isZero();
        poll(manager, receipt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("QUEUED"))
                .andExpect(jsonPath("$.attempts").value(1));
        jdbc.update("update object_task_set_jobs set available_at=current_timestamp");
        assertThat(processor.processNext()).isTrue();
        poll(manager, receipt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.attempts").value(2));
        assertThat(count("task_sets")).isEqualTo(1);
    }

    @Test
    void reviewerConfirmsThroughQueueWithoutApplyingTheTransitionInHttpRequest() throws Exception {
        state("IN_PROGRESS");
        template("IC_PREPARATION", null);
        var waiting = response(change(manager, command("IC_PREPARATION")).andExpect(status().isOk()));
        var approval = new LinkedHashMap<String, Object>();
        approval.put("approvalRequestId", waiting.path("approvalRequestId").asText());
        send(manager, path() + "/preview", approval, false).andExpect(status().isForbidden());
        var preview = response(send(lead, path() + "/preview", approval, false).andExpect(status().isOk()));
        approval.put("confirmationToken", preview.path("confirmationToken").asText());
        var confirmed = response(submit(lead, key(), approval).andExpect(status().isAccepted()));
        assertThat(state()).isEqualTo("IN_PROGRESS");
        assertThat(count("tasks")).isZero();
        assertThat(jdbc.queryForObject("select status from status_approval_requests", String.class))
                .isEqualTo("PENDING");
        processor.processNext();
        assertThat(state()).isEqualTo("IC_PREPARATION");
        assertThat(count("task_sets")).isEqualTo(1);
        assertThat(count("tasks")).isEqualTo(1);
        poll(lead, confirmed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"));
    }

    @Test
    void approvedIcMatrixGeneratesCorrectRecipientsReviewFlagsAndDependency() throws Exception {
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                        new org.springframework.core.io.ClassPathResource(
                                "db/changelog/tasks/20261021_ic_preparation_template.sql"))
                .execute(jdbc.getDataSource());
        state("IN_PROGRESS");
        var receipt =
                response(submit(lead, key(), confirmed(lead, "IC_PREPARATION")).andExpect(status().isAccepted()));
        processor.processNext();
        poll(lead, receipt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"));
        assertThat(jdbc.queryForObject(
                        "select count(*) from tasks where needs_review and assignee_id=? and result_recipient_id=? and status='IN_PROGRESS'",
                        Integer.class,
                        manager,
                        lead))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                        "select count(*) from tasks where not needs_review and assignee_id=? and result_recipient_id=? and status='NEW'",
                        Integer.class,
                        lead,
                        director))
                .isEqualTo(1);
        assertThat(count("task_dependencies")).isEqualTo(1);
        assertThat(count("tasks")).isEqualTo(4);
    }

    Map<String, Object> confirmed(UUID actor, String target) throws Exception {
        var body = command(target);
        var preview = response(send(actor, path() + "/preview", body, false).andExpect(status().isOk()));
        body.put("confirmationToken", preview.path("confirmationToken").asText());
        return body;
    }

    String path() {
        return "/api/objects/" + object + "/task-sets";
    }

    ResultActions submit(UUID actor, String key, Map<String, Object> body) throws Exception {
        return mvc.perform(post(path())
                .cookie(cookie(actor), csrf)
                .header("X-XSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)));
    }

    ResultActions poll(UUID actor, JsonNode receipt) throws Exception {
        return mvc.perform(get(path() + "/jobs/" + receipt.path("id").asText()).cookie(cookie(actor)));
    }
}
