package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.objects.domain.IcDecisionResult;
import dev.workflowcrm.objects.domain.IcDecisionService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/objects")
class IcDecisionController {
    private final IcDecisionService decisions;
    private final IcDecisionParser parser;

    IcDecisionController(IcDecisionService decisions, IcDecisionParser parser) {
        this.decisions = decisions;
        this.parser = parser;
    }

    @PatchMapping("/{id}/ic-decision")
    ResponseEntity<IcDecisionResult> decide(
            @PathVariable UUID id, @RequestBody JsonNode body, JwtAuthenticationToken authentication) {
        UUID actor;
        try {
            actor = UUID.fromString(authentication.getToken().getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(decisions.decide(actor, id, parser.parse(body)));
    }
}
