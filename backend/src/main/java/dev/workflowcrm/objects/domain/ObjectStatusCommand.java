package dev.workflowcrm.objects.domain;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public record ObjectStatusCommand(
        String status,
        long version,
        UUID refusalReasonId,
        LocalDate contractSignedDate,
        UUID contractTypeId,
        LocalDate openingDate,
        LocalDate openingPlannedDate,
        boolean plannedDateProvided,
        String confirmationToken,
        ObjectSigningDetails signing,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
                Map<UUID, UUID> assignees) {
    public ObjectStatusCommand {
        assignees = assignees == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(Map.copyOf(assignees)));
    }

    public ObjectStatusCommand(
            String status,
            long version,
            UUID refusalReasonId,
            LocalDate contractSignedDate,
            UUID contractTypeId,
            LocalDate openingDate,
            LocalDate openingPlannedDate,
            boolean plannedDateProvided,
            String confirmationToken) {
        this(
                status,
                version,
                refusalReasonId,
                contractSignedDate,
                contractTypeId,
                openingDate,
                openingPlannedDate,
                plannedDateProvided,
                confirmationToken,
                null,
                Map.of());
    }
}
