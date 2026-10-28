package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.error.FieldViolation;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.util.ArrayList;
import java.util.TreeSet;

public class ObjectTransitionConditionsException extends UnprocessableInputException {
    private final ObjectTransitionConditions conditions;
    private final ObjectStatusValues values;

    ObjectTransitionConditionsException(ObjectTransitionConditions conditions, ObjectStatusValues values) {
        super(violations(conditions));
        this.conditions = conditions;
        this.values = values;
    }

    public ObjectTransitionConditions conditions() {
        return conditions;
    }

    ObjectStatusValues values() {
        return values;
    }

    private static java.util.List<FieldViolation> violations(ObjectTransitionConditions conditions) {
        var result = new ArrayList<FieldViolation>();
        conditions
                .missingFields()
                .forEach(field -> result.add(new FieldViolation(field, "Field is required for this transition")));
        var codes = new TreeSet<>(conditions.missingTaskCodes());
        conditions.incompleteTasks().forEach(task -> codes.add(task.code()));
        codes.forEach(
                code -> result.add(new FieldViolation("completedTasks", "Complete required transition task: " + code)));
        return result;
    }
}
