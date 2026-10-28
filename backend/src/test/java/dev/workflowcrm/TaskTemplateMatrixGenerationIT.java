package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import dev.workflowcrm.tasks.api.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class TaskTemplateMatrixGenerationIT extends ObjectStatusTasksSupport {
    @Autowired
    ObjectTaskTransition tasks;

    @Autowired
    PlatformTransactionManager transactions;

    @BeforeEach
    void loadSeedDataAfterFixtureCleanup() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            execute("20261021_ic_preparation_template.sql");
            jdbc.update("update task_template_items set escalation_days=1");
            execute("seed/template_matrix_stage.sql");
            execute("20261027_contract_template_matrix.sql");
            execute("seed/template_matrix_apply.sql");
        });
    }

    @ParameterizedTest
    @CsvSource({"IC_PREPARATION,", "IC_PREPARATION,PRIMARY", "CONTRACT_APPROVAL,", "CONTRACT_APPROVAL,PRIMARY"})
    void previewAndGeneratedSetUseSeededAssigneesDatesReviewAndDependencies(String status, String contractType) {
        boolean ic = status.equals("IC_PREPARATION");
        var context = new ObjectTaskTransition.Context(
                object,
                manager,
                region,
                statusId("FOUND"),
                statusId(status),
                contractType,
                LocalDate.of(2026, 10, 30),
                true);
        var plan = new TransactionTemplate(transactions).execute(tx -> tasks.preview(context));
        assertThat(plan).isNotNull();
        assertThat(plan.tasks()).hasSize(ic ? 4 : 3);
        assertThat(plan.tasks().stream().map(TaskTransitionPlan.Task::assigneeId))
                .containsExactlyElementsOf(
                        ic ? List.of(manager, manager, manager, lead) : List.of(lead, lead, contract));
        for (int index = 0; index < plan.tasks().size(); index++) {
            var task = plan.tasks().get(index);
            assertThat(task.deadline()).isEqualTo(context.startDate().plusDays(ic && index >= 2 ? 5 : 3));
            assertThat(task.escalationDays()).isEqualTo(1);
            assertThat(task.needsReview()).isEqualTo(ic && index < 3);
            assertThat(task.resultRecipientId()).isEqualTo(ic && index < 3 ? lead : director);
            if (ic && index == 3)
                assertThat(task.predecessors())
                        .containsExactly(plan.tasks().getFirst().templateItemId());
            else assertThat(task.predecessors()).isEmpty();
        }
        assertThat(count("task_sets")).isZero();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> tasks.apply(context, plan, "NONE", director));
        assertThat(count("tasks")).isEqualTo(ic ? 4 : 3);
        assertThat(count("task_dependencies")).isEqualTo(ic ? 1 : 0);
        assertThat(jdbc.queryForObject(
                        "select count(*) from tasks where needs_review=true and result_recipient_id=?",
                        Integer.class,
                        lead))
                .isEqualTo(ic ? 3 : 0);
        assertThat(jdbc.queryForList("select escalation_days from template_items", Integer.class))
                .hasSize(ic ? 4 : 3)
                .containsOnly(1);
    }

    private void execute(String file) {
        try (var input = getClass().getClassLoader().getResourceAsStream("db/changelog/tasks/" + file)) {
            jdbc.execute(new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
