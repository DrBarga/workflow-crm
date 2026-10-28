package dev.workflowcrm.objects.api;

import dev.workflowcrm.objects.domain.ObjectMapResult;
import dev.workflowcrm.objects.domain.ObjectMapService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/objects/map")
class ObjectMapController {
    private final ObjectMapService service;
    private final ObjectMapParser parser;

    ObjectMapController(ObjectMapService service, ObjectMapParser parser) {
        this.service = service;
        this.parser = parser;
    }

    @GetMapping
    ResponseEntity<ObjectMapResult> find(
            @RequestParam MultiValueMap<String, String> parameters, JwtAuthenticationToken authentication) {
        UUID actor;
        try {
            actor = UUID.fromString(authentication.getToken().getSubject());
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        var request = parser.parse(parameters);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.find(actor, request.filter(), request.bounds()));
    }
}
