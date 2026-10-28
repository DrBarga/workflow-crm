package dev.workflowcrm.objects.api;

import dev.workflowcrm.objects.domain.ObjectHistoryPage;
import dev.workflowcrm.objects.domain.ObjectHistoryService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/objects")
class ObjectHistoryController {
    private final ObjectHistoryService service;

    ObjectHistoryController(ObjectHistoryService service) {
        this.service = service;
    }

    @GetMapping("/{id}/history")
    ResponseEntity<ObjectHistoryPage> get(
            @PathVariable UUID id,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            JwtAuthenticationToken authentication) {
        UUID actor;
        try {
            actor = UUID.fromString(authentication.getToken().getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(actor, id, cursor, limit));
    }
}
