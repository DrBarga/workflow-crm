package dev.workflowcrm.contacts.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.contacts.domain.ContactCard;
import dev.workflowcrm.contacts.domain.ContactDeletionService;
import dev.workflowcrm.contacts.domain.ContactMutation;
import dev.workflowcrm.contacts.domain.ContactSearchService;
import dev.workflowcrm.contacts.domain.ContactService;
import dev.workflowcrm.contacts.domain.ContactSummary;
import dev.workflowcrm.shared.read.ReadPage;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contacts")
class ContactController {
    private final ContactService contacts;
    private final ContactDeletionService deletion;
    private final ContactRequestParser parser;
    private final ContactSearchService search;

    ContactController(
            ContactService contacts,
            ContactDeletionService deletion,
            ContactRequestParser parser,
            ContactSearchService search) {
        this.contacts = contacts;
        this.deletion = deletion;
        this.parser = parser;
        this.search = search;
    }

    @GetMapping
    ResponseEntity<ReadPage.Result<ContactSummary>> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            JwtAuthenticationToken authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(search.list(actor(authentication), null, q, page, size));
    }

    @PostMapping
    ResponseEntity<ContactMutation> create(@RequestBody JsonNode body, JwtAuthenticationToken authentication) {
        var created = contacts.create(actor(authentication), parser.create(body));
        return ResponseEntity.created(
                        URI.create("/api/contacts/" + created.contact().id()))
                .cacheControl(CacheControl.noStore())
                .body(created);
    }

    @GetMapping("/{id}")
    ResponseEntity<ContactCard> get(@PathVariable UUID id, JwtAuthenticationToken authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(contacts.get(actor(authentication), id));
    }

    @PatchMapping("/{id}")
    ResponseEntity<ContactMutation> patch(
            @PathVariable UUID id, @RequestBody JsonNode body, JwtAuthenticationToken authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(contacts.update(actor(authentication), id, parser.patch(body)));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestParam long version,
            @RequestParam(required = false) String confirmationToken,
            JwtAuthenticationToken authentication) {
        parser.deletion(version, confirmationToken);
        deletion.delete(actor(authentication), id, version, confirmationToken);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static UUID actor(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getName());
    }
}
