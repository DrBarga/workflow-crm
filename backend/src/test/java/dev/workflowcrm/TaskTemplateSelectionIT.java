package dev.workflowcrm;

import static org.assertj.core.api.Assertions.*;

import dev.workflowcrm.tasks.api.*;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class TaskTemplateSelectionIT extends ObjectStatusTasksSupport {
    @Autowired
    ObjectTaskTransition selection;

    @Autowired
    PlatformTransactionManager transactions;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void unsetTypeSelectsOnlyDefaultTemplateForRequestedStatus(String type) {
        UUID expected = template("IN_PROGRESS", null);
        template("IN_PROGRESS", "PRIMARY");
        template("IC_PREPARATION", null);
        var result = lookup(type);
        assertThat(result.templateId()).isEqualTo(expected);
        assertThat(result.missingTemplate()).isFalse();
        assertThat(result.tasks()).hasSize(1);
        assertThat(count("tasks")).isZero();
        assertThat(count("task_sets")).isZero();
        assertThat(count("task_template_versions")).isZero();
    }

    @ParameterizedTest
    @CsvSource({
        "REFERENCE,PRIMARY",
        "CODE,PRIMARY",
        "NAME,PRIMARY",
        "REFERENCE,Основний",
        "CODE,Основний",
        "NAME,Основний"
    })
    void resolvesDictionaryReferenceAndLegacyCodeOrNameToSamePair(String storage, String requestedType) {
        template("IN_PROGRESS", null);
        String legacy =
                switch (storage) {
                    case "CODE" -> "PRIMARY";
                    case "NAME" -> "Основний";
                    default -> "PRIMARY";
                };
        UUID expected = template("IN_PROGRESS", legacy);
        if ("REFERENCE".equals(storage))
            jdbc.update("update task_templates set contract_type_id=? where id=?", contractType(), expected);
        assertThat(lookup(requestedType).templateId()).isEqualTo(expected);
    }

    @Test
    void setTypeWithoutExactMatchFallsBackToDefaultButNeverToAnotherType() {
        UUID fallback = template("IN_PROGRESS", null);
        template("IN_PROGRESS", "PRELIMINARY");
        for (String type : java.util.List.of("PRIMARY", "UNKNOWN")) {
            var result = lookup(type);
            assertThat(result.missingTemplate()).isFalse();
            assertThat(result.templateId()).isEqualTo(fallback);
            assertThat(result.tasks()).hasSize(1);
        }
        assertThat(events("OBJECT_TASK_TEMPLATE_MISSING")).isZero();
    }

    @Test
    void normalizedReferenceTakesPrecedenceOverConflictingLegacyText() {
        UUID template = template("IN_PROGRESS", "PRIMARY");
        UUID preliminary = jdbc.queryForObject("select id from contract_types where code='PRELIMINARY'", UUID.class);
        jdbc.update("update task_templates set contract_type_id=? where id=?", preliminary, template);
        assertThat(lookup("PRIMARY").missingTemplate()).isTrue();
        assertThat(lookup("PRELIMINARY").templateId()).isEqualTo(template);
    }

    @Test
    void emptyExactTemplateFallsBackToStatusDefaultAndAbsenceOfBothReturnsMissing() {
        UUID exact = template("IN_PROGRESS", "PRIMARY");
        jdbc.update("delete from task_template_items where template_id=?", exact);
        assertThat(lookup("PRIMARY").missingTemplate()).isTrue();
        UUID fallback = template("IN_PROGRESS", null);
        assertThat(lookup("PRIMARY").templateId()).isEqualTo(fallback);
        jdbc.update("update task_templates set is_active=false where id=?", fallback);
        assertThat(lookup("PRIMARY").missingTemplate()).isTrue();
    }

    @Test
    void ignoresInactiveCandidateButRejectsTwoActiveRepresentationsOfSamePair() {
        UUID normalized = template("IN_PROGRESS", null);
        jdbc.update("update task_templates set contract_type_id=? where id=?", contractType(), normalized);
        UUID legacy = template("IN_PROGRESS", "Основний");
        assertThatThrownBy(() -> lookup("PRIMARY")).isInstanceOf(TaskTemplateConfigurationException.class);
        jdbc.update("update task_templates set is_active=false where id=?", legacy);
        assertThat(lookup("PRIMARY").templateId()).isEqualTo(normalized);
    }

    @Test
    void selectsCurrentDefinitionAndKeepsPreviouslyReturnedPlanUnchanged() {
        UUID template = template("IN_PROGRESS", null);
        var previous = lookup(null);
        jdbc.update("update task_templates set version=version+1 where id=?", template);
        jdbc.update(
                "update task_template_items set name='Оновлена задача',deadline_offset_days=7,version=version+1 where template_id=?",
                template);
        var current = lookup(null);
        assertThat(current.revision()).isNotEqualTo(previous.revision());
        assertThat(current.tasks().getFirst().name()).isEqualTo("Оновлена задача");
        assertThat(current.tasks().getFirst().deadline()).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(previous.tasks().getFirst().name()).isEqualTo("Перевірити об’єкт");
        assertThat(previous.tasks().getFirst().deadline()).isEqualTo(LocalDate.of(2026, 10, 23));
    }

    @Test
    void emptyTemplateReturnsMissingIndicatorWithoutNotificationFromPreview() {
        UUID template = template("IN_PROGRESS", null);
        jdbc.update("delete from task_template_items where template_id=?", template);
        assertThat(lookup(null).missingTemplate()).isTrue();
        assertThat(events("OBJECT_TASK_TEMPLATE_MISSING")).isZero();
        assertThat(count("task_sets")).isZero();
    }

    private TaskTransitionPlan lookup(String type) {
        return new TransactionTemplate(transactions)
                .execute(tx -> selection.preview(new ObjectTaskTransition.Context(
                        object,
                        manager,
                        region,
                        statusId("FOUND"),
                        statusId("IN_PROGRESS"),
                        type,
                        LocalDate.of(2026, 10, 20),
                        true)));
    }
}
