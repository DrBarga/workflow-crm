package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.error.FieldViolation;
import java.time.Instant;
import java.util.*;

public final class ObjectImport {
    private ObjectImport() {}

    public record Input(int row, Map<String, String> values) {
        public Input {
            values = Map.copyOf(values);
        }
    }

    public record Command(CreateObjectCommand object, ObjectMilestones dates) {}

    public record Job(UUID id, UUID actor, String hash) {}

    public record View(
            UUID id,
            UUID author,
            String state,
            int total,
            int created,
            int rejected,
            int duplicates,
            Instant requestedAt,
            Instant completedAt,
            String error) {}

    public record Row(
            int row, String state, UUID objectId, boolean possibleDuplicate, String note, List<FieldViolation> errors) {
        public Row {
            errors = List.copyOf(errors);
        }
    }

    public record Report(List<Row> content, int offset, int limit, int total) {
        public Report {
            content = List.copyOf(content);
        }
    }

    record Prepared(
            Input input, ObjectRegistration registration, ObjectMilestones dates, List<FieldViolation> errors) {}
}
