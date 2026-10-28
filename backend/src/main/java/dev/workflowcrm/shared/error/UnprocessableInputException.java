package dev.workflowcrm.shared.error;

import java.util.List;

public class UnprocessableInputException extends RuntimeException {
    private final List<FieldViolation> violations;

    public UnprocessableInputException(List<FieldViolation> violations) {
        super("Request validation failed");
        this.violations = List.copyOf(violations);
    }

    public UnprocessableInputException(String field, String message) {
        this(List.of(new FieldViolation(field, message)));
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}
