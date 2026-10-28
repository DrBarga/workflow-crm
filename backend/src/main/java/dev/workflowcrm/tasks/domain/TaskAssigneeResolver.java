package dev.workflowcrm.tasks.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import dev.workflowcrm.tasks.api.*;
import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
class TaskAssigneeResolver {
    private final ObjectAssignmentQuery users;

    TaskAssigneeResolver(ObjectAssignmentQuery users) {
        this.users = users;
    }

    Lookup lookup(UUID manager, UUID region, UUID confirmer) {
        return new Lookup(manager, region, confirmer);
    }

    final class Lookup {
        private final UUID manager;
        private final UUID region;
        private final UUID confirmer;
        private final Map<UUID, Optional<ObjectAssignmentQuery.Member>> members = new HashMap<>();
        private final Map<UUID, List<ObjectAssignmentQuery.Member>> candidates = new HashMap<>();
        private final Map<UUID, Optional<ObjectAssignmentQuery.AssignmentRole>> roles = new HashMap<>();

        Lookup(UUID manager, UUID region, UUID confirmer) {
            this.manager = manager;
            this.region = region;
            this.confirmer = confirmer;
        }

        Selection resolve(TaskAssignment assignment, UUID explicit) {
            return switch (assignment.rule()) {
                case "EXPLICIT_USER" -> explicit(explicit, false);
                case "OBJECT_MANAGER" -> explicit(manager, true);
                case "REGIONAL_ROLE", "NETWORK_ROLE" ->
                    role(assignment.roleId(), "REGIONAL_ROLE".equals(assignment.rule()));
                default -> throw new TaskTemplateConfigurationException("Task assignment rule is invalid");
            };
        }

        Selection role(UUID id, boolean regional) {
            var role = role(id);
            if (role == null) return new Selection(null, TaskAssignment.Issue.NOT_FOUND, List.of());
            if ("MANAGER".equals(role.code())) return explicit(manager, true);
            var confirming = member(confirmer).orElse(null);
            if ("CONTRACT_MANAGER".equals(role.code())
                    && confirming != null
                    && confirming.active()
                    && id.equals(confirming.roleId())
                    && eligible(confirming)) return resolved(confirming);
            var available = available(id);
            if (regional) {
                var local = available.stream().filter(this::inRegion).toList();
                if (!local.isEmpty()) available = local;
                else if (!available.isEmpty()) return new Selection(null, TaskAssignment.Issue.NOT_FOUND, available);
            }
            if (available.size() == 1) return resolved(available.getFirst());
            return new Selection(
                    null,
                    available.isEmpty() ? TaskAssignment.Issue.NOT_FOUND : TaskAssignment.Issue.AMBIGUOUS,
                    available);
        }

        Selection selected(Selection automatic, UUID selected) {
            if (selected == null) return automatic;
            if (automatic.member() != null) {
                if (!selected.equals(automatic.member().id()))
                    throw invalid("An automatically assigned task cannot be reassigned in the preview");
                return automatic;
            }
            var member = automatic.candidates().stream()
                    .filter(value -> selected.equals(value.id()))
                    .findFirst();
            if (member.isEmpty()) throw invalid("Choose an active assignee from the available role and region");
            return resolved(member.get());
        }

        List<ObjectAssignmentQuery.Member> replacementOptions(TaskAssignment assignment) {
            var role = role(assignment.roleId());
            if ("OBJECT_MANAGER".equals(assignment.rule()) || (role != null && "MANAGER".equals(role.code()))) {
                var owner = explicit(manager, true).member();
                return owner == null ? List.of() : List.of(owner);
            }
            var available = available(assignment.roleId());
            if ("REGIONAL_ROLE".equals(assignment.rule())) {
                var local = available.stream().filter(this::inRegion).toList();
                if (!local.isEmpty()) return local;
            }
            return available;
        }

        TaskAssignment description(TaskAssignment assignment, Selection selection) {
            var role = role(assignment.roleId());
            String message =
                    selection.member() == null && selection.candidates().isEmpty()
                            ? role == null
                                    ? "Немає доступного активного виконавця. Зверніться до Адміністратора."
                                    : "Немає активних користувачів з роллю «" + role.name()
                                            + "» у доступному регіоні. Зверніться до Адміністратора."
                            : null;
            return new TaskAssignment(
                    assignment.rule(),
                    assignment.roleId(),
                    selection.issue(),
                    options(selection.candidates()),
                    message);
        }

        private Selection explicit(UUID id, boolean owner) {
            var member = member(id).orElse(null);
            var issue = member == null
                    ? TaskAssignment.Issue.NOT_FOUND
                    : !member.active()
                            ? TaskAssignment.Issue.INACTIVE
                            : !eligible(member) || (owner && !"MANAGER".equals(member.role()))
                                    ? TaskAssignment.Issue.OUTSIDE_REGION
                                    : null;
            if (issue == null) return resolved(member);
            return new Selection(null, issue, owner ? List.of() : available(null));
        }

        private List<ObjectAssignmentQuery.Member> available(UUID role) {
            return candidates.computeIfAbsent(role, users::assignmentCandidates).stream()
                    .filter(ObjectAssignmentQuery.Member::active)
                    .filter(value -> role == null || role.equals(value.roleId()))
                    .filter(this::eligible)
                    .distinct()
                    .sorted(Comparator.comparing(
                                    ObjectAssignmentQuery.Member::fullName,
                                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                            .thenComparing(ObjectAssignmentQuery.Member::id))
                    .toList();
        }

        private ObjectAssignmentQuery.AssignmentRole role(UUID id) {
            return id == null
                    ? null
                    : roles.computeIfAbsent(id, users::assignmentRole).orElse(null);
        }

        private Optional<ObjectAssignmentQuery.Member> member(UUID id) {
            return id == null ? Optional.empty() : members.computeIfAbsent(id, users::member);
        }

        private boolean eligible(ObjectAssignmentQuery.Member member) {
            return switch (member.role()) {
                case "MANAGER", "REGIONAL_MANAGER" -> inRegion(member);
                default -> true;
            };
        }

        private boolean inRegion(ObjectAssignmentQuery.Member member) {
            return member.regionIds().stream().anyMatch(assigned -> users.regionContains(assigned, region));
        }
    }

    static List<TaskAssignment.Candidate> options(List<ObjectAssignmentQuery.Member> members) {
        return members.stream()
                .map(member -> new TaskAssignment.Candidate(member.id(), member.fullName()))
                .toList();
    }

    private static Selection resolved(ObjectAssignmentQuery.Member member) {
        return new Selection(member, null, List.of());
    }

    private static UnprocessableInputException invalid(String message) {
        return new UnprocessableInputException("assignees", message);
    }

    record Selection(
            ObjectAssignmentQuery.Member member,
            TaskAssignment.Issue issue,
            List<ObjectAssignmentQuery.Member> candidates) {}
}
