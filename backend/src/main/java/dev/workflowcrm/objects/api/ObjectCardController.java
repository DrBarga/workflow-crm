package dev.workflowcrm.objects.api;

import dev.workflowcrm.objects.domain.ObjectCard;
import dev.workflowcrm.objects.domain.ObjectCardService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/objects")
class ObjectCardController {
    private final ObjectCardService service;

    ObjectCardController(ObjectCardService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    ResponseEntity<ObjectCard> get(@PathVariable UUID id, JwtAuthenticationToken authentication) {
        UUID actor;
        try {
            actor = UUID.fromString(authentication.getToken().getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(actor, id));
    }
}
