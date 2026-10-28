package dev.workflowcrm.objects.domain;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

public record ObjectTaskSetCommand(
        ObjectStatusCommand transition,
        UUID approvalRequestId,
        String confirmationToken,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
                Map<UUID, UUID> assignees) {
    public ObjectTaskSetCommand {
        assignees = assignees == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(Map.copyOf(assignees)));
        if ((transition == null) == (approvalRequestId == null))
            throw new IllegalArgumentException("Provide either a status transition or an approval request");
        if (transition != null && !Objects.equals(transition.confirmationToken(), confirmationToken))
            throw new IllegalArgumentException("Transition confirmation does not match");
        if (transition != null && !assignees.isEmpty())
            throw new IllegalArgumentException("Transition choices belong to its status command");
    }

    public ObjectTaskSetCommand(ObjectStatusCommand transition, UUID approvalRequestId, String confirmationToken) {
        this(transition, approvalRequestId, confirmationToken, Map.of());
    }
}
