package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.objects.domain.ObjectStatusResult;
import dev.workflowcrm.objects.domain.ObjectStatusService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/objects")
class ObjectStatusController {
    private final ObjectStatusService statuses;
    private final ObjectStatusParser parser;

    ObjectStatusController(ObjectStatusService statuses, ObjectStatusParser parser) {
        this.statuses = statuses;
        this.parser = parser;
    }

    @PostMapping("/{id}/status")
    ResponseEntity<ObjectStatusResult> change(
            @PathVariable UUID id, @RequestBody JsonNode body, JwtAuthenticationToken authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(statuses.change(actor(authentication), id, parser.parse(body, false), false));
    }

    @PatchMapping("/{id}/milestones")
    ResponseEntity<ObjectStatusResult> milestones(
            @PathVariable UUID id, @RequestBody JsonNode body, JwtAuthenticationToken authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(statuses.change(actor(authentication), id, parser.parse(body, true), true));
    }

    @GetMapping("/{id}/status")
    ResponseEntity<java.util.Map<String, Object>> get(@PathVariable UUID id, JwtAuthenticationToken authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(statuses.get(actor(authentication), id));
    }

    static UUID actor(JwtAuthenticationToken authentication) {
        try {
            return UUID.fromString(authentication.getToken().getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
    }

    @GetMapping("/{id}/refusal-reasons")
    ResponseEntity<java.util.List<ObjectRefusalTag>> refusalReasons(
            @PathVariable UUID id, JwtAuthenticationToken authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(statuses.refusalReasons(actor(authentication), id));
    }
}
