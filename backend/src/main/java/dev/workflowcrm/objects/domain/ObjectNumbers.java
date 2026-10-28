package dev.workflowcrm.objects.domain;

import java.math.BigDecimal;

final class ObjectNumbers {
    private ObjectNumbers() {}

    static BigDecimal nonnegative(BigDecimal value, int precision, int scale, String field) {
        if (value == null) return null;
        var normalized = value.stripTrailingZeros();
        if (value.signum() < 0
                || normalized.scale() > scale
                || normalized.precision() - normalized.scale() > precision - scale)
            throw new IllegalArgumentException(field + " must be nonnegative and fit its numeric precision");
        return value;
    }

    static Integer nonnegative(Integer value, String field) {
        if (value != null && value < 0) throw new IllegalArgumentException(field + " must not be negative");
        return value;
    }
}
