package dev.workflowcrm.tasks.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DeadlineRuleTest {
    @ParameterizedTest
    @CsvSource({
        "2026-10-23, 2, 2026-10-25",
        "2026-12-31, 1, 2027-01-01",
        "2028-02-28, 1, 2028-02-29",
        "2028-02-28, 2, 2028-03-01",
        "2026-10-25, 0, 2026-10-25",
        "2026-03-28, 2, 2026-03-30",
        "2026-10-24, 2, 2026-10-26"
    })
    void countsCalendarDays(String trigger, int offset, String expected) {
        var rule = new DeadlineRule(DeadlineRule.Basis.FROM_START_CALENDAR, offset);
        assertThat(rule.calculate(LocalDate.parse(trigger))).isEqualTo(expected);
    }

    @Test
    void rejectsUnsupportedBusinessDayCalculation() {
        var rule = new DeadlineRule(DeadlineRule.Basis.FROM_START_BUSINESS, 3);
        assertThatThrownBy(() -> rule.calculate(LocalDate.of(2026, 10, 23)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
