package dev.workflowcrm.objects.api;

import dev.workflowcrm.objects.domain.ObjectMentionLookupService;
import dev.workflowcrm.shared.read.ReadPage;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
class ObjectMentionLookupController {
    private final ObjectMentionLookupService users;

    ObjectMentionLookupController(ObjectMentionLookupService users) {
        this.users = users;
    }

    @GetMapping("/api/objects/{id}/mentionable-users")
    ResponseEntity<ReadPage.Result<ObjectMentionLookupService.Option>> list(
            @PathVariable UUID id,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            JwtAuthenticationToken auth) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(users.list(ObjectStatusController.actor(auth), id, q, page, size));
    }
}
