package dev.workflowcrm.objects.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import dev.workflowcrm.shared.read.ReadPage;
import dev.workflowcrm.users.api.ObjectAssignmentQuery;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObjectMentionLookupService {
    private final ObjectCommentAccess access;
    private final ObjectCardStore objects;
    private final ObjectAssignmentQuery users;

    ObjectMentionLookupService(ObjectCommentAccess access, ObjectCardStore objects, ObjectAssignmentQuery users) {
        this.access = access;
        this.objects = objects;
        this.users = users;
    }

    public record Option(UUID id, String fullName, String role) {}

    @Transactional(readOnly = true)
    public ReadPage.Result<Option> list(UUID actor, UUID objectId, String query, int page, int size) {
        var member = access.writer(actor);
        var object = objects.summary(objectId);
        access.requireObject(member, object);
        var paging = new ReadPage(page, size);
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (q.length() > 160 || q.codePoints().anyMatch(Character::isISOControl))
            throw new UnprocessableInputException(
                    "q", "Search must be at most 160 characters without control characters");
        var eligible = users.assignmentCandidates(null).stream()
                .filter(ObjectAssignmentQuery.Member::active)
                .filter(user -> q.isEmpty()
                        || user.fullName() != null
                                && user.fullName().toLowerCase(Locale.ROOT).contains(q))
                .filter(user -> {
                    try {
                        access.requireObject(user, object);
                        return true;
                    } catch (AccessDeniedException denied) {
                        return false;
                    }
                })
                .sorted(Comparator.comparing(
                                ObjectAssignmentQuery.Member::fullName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(ObjectAssignmentQuery.Member::id))
                .map(user -> new Option(user.id(), user.fullName(), user.role()))
                .toList();
        return paging.result(
                eligible.stream().skip(paging.offset()).limit(paging.size()).toList(), eligible.size());
    }
}
