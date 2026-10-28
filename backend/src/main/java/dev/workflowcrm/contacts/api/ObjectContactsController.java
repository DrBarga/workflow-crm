package dev.workflowcrm.contacts.api;

import dev.workflowcrm.contacts.domain.*;
import dev.workflowcrm.shared.read.ReadPage;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
class ObjectContactsController {
    private final ContactSearchService contacts;

    ObjectContactsController(ContactSearchService contacts) {
        this.contacts = contacts;
    }

    @GetMapping("/api/objects/{id}/contacts")
    ResponseEntity<ReadPage.Result<ContactSummary>> list(
            @PathVariable UUID id,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            JwtAuthenticationToken auth) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(contacts.list(UUID.fromString(auth.getToken().getSubject()), id, q, page, size));
    }
}
