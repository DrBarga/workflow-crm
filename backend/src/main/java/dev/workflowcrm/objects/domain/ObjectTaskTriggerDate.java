package dev.workflowcrm.objects.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
class ObjectTaskTriggerDate {
    private static final ZoneId ZONE = ZoneId.of("Europe/Kyiv");
    private final Clock clock;

    ObjectTaskTriggerDate(Clock clock) {
        this.clock = clock;
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }
}
