package dev.workflowcrm.objects.domain;

import dev.workflowcrm.tasks.api.TransitionTaskRequirements;
import java.util.List;

public record ObjectTransitionConditions(
        List<String> missingFields,
        List<TransitionTaskRequirements.Task> incompleteTasks,
        List<String> missingTaskCodes) {
    public ObjectTransitionConditions {
        missingFields = List.copyOf(missingFields);
        incompleteTasks = List.copyOf(incompleteTasks);
        missingTaskCodes = List.copyOf(missingTaskCodes);
    }

    static ObjectTransitionConditions of(
            List<String> fields, java.util.Set<String> codes, TransitionTaskRequirements.Assessment assessment) {
        var incomplete = assessment.incompleteTasks().stream()
                .filter(t -> codes.contains(t.code()))
                .toList();
        var absent = codes.stream()
                .filter(assessment.missingCodes()::contains)
                .filter(code -> incomplete.stream().noneMatch(t -> code.equals(t.code())))
                .sorted()
                .toList();
        return new ObjectTransitionConditions(fields, incomplete, absent);
    }

    boolean fulfilled() {
        return missingFields.isEmpty() && incompleteTasks.isEmpty() && missingTaskCodes.isEmpty();
    }
}
