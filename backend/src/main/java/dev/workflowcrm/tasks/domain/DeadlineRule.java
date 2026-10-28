package dev.workflowcrm.tasks.domain;

import java.time.LocalDate;
import java.util.Objects;

public record DeadlineRule(Basis basis, int offsetDays) {
    public enum Basis {
        FROM_START_CALENDAR,
        FROM_START_BUSINESS
    }

    public DeadlineRule {
        Objects.requireNonNull(basis);
        if (offsetDays < 0 || offsetDays > 3650) throw new IllegalArgumentException("Invalid deadline offset");
    }

    public LocalDate calculate(LocalDate triggerDate) {
        Objects.requireNonNull(triggerDate);
        if (basis != Basis.FROM_START_CALENDAR) {
            throw new IllegalArgumentException("Only calendar deadlines are supported");
        }
        return triggerDate.plusDays(offsetDays);
    }
}
