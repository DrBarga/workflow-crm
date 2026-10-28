package dev.workflowcrm.objects.domain;

import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.List;
import java.util.UUID;

public record ObjectDuplicateReport(long total, List<Match> items) {
    public static ObjectDuplicateReport visible(
            ObjectDuplicateSearch.Matches matches, ObjectAssignmentQuery.Member actor) {
        return new ObjectDuplicateReport(
                matches.total(),
                matches.items().stream()
                        .map(candidate -> visible(candidate, actor))
                        .toList());
    }

    private static Match visible(ObjectDuplicateSearch.Candidate candidate, ObjectAssignmentQuery.Member actor) {
        boolean visible = "DEVELOPMENT_DIRECTOR".equals(actor.role())
                || "MANAGER".equals(actor.role()) && actor.id().equals(candidate.managerId())
                || "REGIONAL_MANAGER".equals(actor.role()) && actor.belongsTo(candidate.regionId());
        if (!visible) return new RestrictedMatch(candidate.name(), candidate.distanceMetres(), true);
        return new AccessibleMatch(
                candidate.name(),
                candidate.distanceMetres(),
                false,
                candidate.id(),
                candidate.address(),
                candidate.status(),
                candidate.statusName(),
                "REFUSED".equals(candidate.status()));
    }

    public sealed interface Match permits AccessibleMatch, RestrictedMatch {}

    public record RestrictedMatch(String name, double distanceMetres, boolean restricted) implements Match {}

    public record AccessibleMatch(
            String name,
            double distanceMetres,
            boolean restricted,
            UUID id,
            String address,
            String status,
            String statusName,
            boolean refused)
            implements Match {}
}
