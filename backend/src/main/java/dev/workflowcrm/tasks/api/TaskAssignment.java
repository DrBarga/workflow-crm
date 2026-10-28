package dev.workflowcrm.tasks.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record TaskAssignment(String rule, UUID roleId, Issue issue, List<Candidate> candidates, String message) {
    public TaskAssignment {
        candidates = List.copyOf(candidates);
    }

    public TaskAssignment(String rule, UUID roleId, Issue issue) {
        this(rule, roleId, issue, List.of(), null);
    }

    public TaskAssignment(String rule, UUID roleId, Issue issue, List<Candidate> candidates) {
        this(rule, roleId, issue, candidates, null);
    }

    @JsonProperty
    public boolean selectionRequired() {
        return issue != null;
    }

    public record Candidate(UUID id, String fullName) {}

    public enum Issue {
        NOT_FOUND,
        INACTIVE,
        OUTSIDE_REGION,
        AMBIGUOUS
    }
}
