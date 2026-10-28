package dev.workflowcrm.contacts.domain;

import dev.workflowcrm.shared.error.UnprocessableInputException;
import dev.workflowcrm.shared.read.ReadPage;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ContactSearchService {
    private final ContactAccess access;
    private final ContactObjectStore objects;
    private final ContactSearchStore store;

    ContactSearchService(ContactAccess access, ContactObjectStore objects, ContactSearchStore store) {
        this.access = access;
        this.objects = objects;
        this.store = store;
    }

    public ReadPage.Result<ContactSummary> list(UUID actor, UUID object, String query, int page, int size) {
        var scope = access.require(actor);
        if (object != null) {
            var linked = objects.find(object)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Object not found"));
            if (!scope.sees(linked)) throw new AccessDeniedException("Object contacts are unavailable");
        }
        String q = query == null ? "" : query.strip();
        if (q.length() > 200 || q.codePoints().anyMatch(Character::isISOControl))
            throw new UnprocessableInputException(
                    "q", "Search must be at most 200 characters without control characters");
        return store.search(scope, object, q, new ReadPage(page, size));
    }
}
