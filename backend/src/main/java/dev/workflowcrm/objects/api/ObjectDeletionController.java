package dev.workflowcrm.objects.api;

import dev.workflowcrm.objects.domain.ObjectDeletion;
import dev.workflowcrm.objects.domain.ObjectDeletionService;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
class ObjectDeletionController {
    private final ObjectDeletionService deletions;

    ObjectDeletionController(ObjectDeletionService deletions) {
        this.deletions = deletions;
    }

    @DeleteMapping("/api/objects/{id}")
    ResponseEntity<ObjectDeletion> delete(@PathVariable UUID id, JwtAuthenticationToken authentication) {
        var operation = deletions.request(ObjectStatusController.actor(authentication), id);
        return ResponseEntity.accepted()
                .location(URI.create("/api/object-deletions/" + operation.id()))
                .cacheControl(CacheControl.noStore())
                .body(operation);
    }

    @GetMapping("/api/object-deletions/{id}")
    ResponseEntity<ObjectDeletion> get(@PathVariable UUID id, JwtAuthenticationToken authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(deletions.get(ObjectStatusController.actor(authentication), id));
    }
}
