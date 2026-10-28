package dev.workflowcrm.objects.domain;

import java.util.List;
import java.util.UUID;

public interface ObjectDuplicateSearch {

    Matches find(ObjectRegistration registration);

    record Candidate(
            UUID id,
            String name,
            String address,
            String status,
            String statusName,
            UUID managerId,
            UUID regionId,
            double distanceMetres) {}

    record Matches(long total, List<Candidate> items) {
        public Matches {
            items = List.copyOf(items);
        }

        public List<UUID> ids() {
            return items.stream().map(Candidate::id).toList();
        }
    }
}
