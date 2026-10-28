package dev.workflowcrm.objects.domain;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ObjectHistoryService {
    private final ObjectCardAccess access;
    private final ObjectCardStore objects;
    private final ObjectHistoryQuery history;

    ObjectHistoryService(ObjectCardAccess access, ObjectCardStore objects, ObjectHistoryQuery history) {
        this.access = access;
        this.objects = objects;
        this.history = history;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ObjectHistoryPage get(UUID actor, UUID object, String cursor, int limit) {
        var user = access.require(actor);
        if (!access.full(user, objects.summary(object))) throw new AccessDeniedException("Full object access required");
        if (limit < 1 || limit > 100)
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "History limit must be between 1 and 100");
        var rows = history.page(object, ObjectHistoryCursor.decode(object, cursor), limit + 1);
        boolean more = rows.size() > limit;
        var items = rows.stream().limit(limit).toList();
        String next = more ? ObjectHistoryCursor.encode(object, items.getLast().sequence()) : null;
        return new ObjectHistoryPage(items, next, more);
    }
}
