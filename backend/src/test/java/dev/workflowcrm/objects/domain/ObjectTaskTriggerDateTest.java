package dev.workflowcrm.objects.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ObjectTaskTriggerDateTest {
    @ParameterizedTest
    @CsvSource({
        "2026-10-23T20:59:59Z, 2026-10-23",
        "2026-10-23T21:00:00Z, 2026-10-24",
        "2026-12-31T21:59:59Z, 2026-12-31",
        "2026-12-31T22:00:00Z, 2027-01-01"
    })
    void usesKyivDateRegardlessOfClockZone(String instant, String expected) {
        var date = new ObjectTaskTriggerDate(Clock.fixed(Instant.parse(instant), ZoneId.of("America/New_York")));
        assertThat(date.today()).isEqualTo(expected);
    }
}
