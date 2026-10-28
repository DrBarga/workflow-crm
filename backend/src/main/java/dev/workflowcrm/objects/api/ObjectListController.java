package dev.workflowcrm.objects.api;

import dev.workflowcrm.objects.domain.ObjectListPage;
import dev.workflowcrm.objects.domain.ObjectListService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/objects")
class ObjectListController {
    private final ObjectListService service;
    private final ObjectListParser parser;

    ObjectListController(ObjectListService service, ObjectListParser parser) {
        this.service = service;
        this.parser = parser;
    }

    @GetMapping
    ResponseEntity<ObjectListPage> list(
            @RequestParam MultiValueMap<String, String> parameters, JwtAuthenticationToken authentication) {
        UUID actor;
        try {
            actor = UUID.fromString(authentication.getToken().getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        var request = parser.parse(parameters);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.list(actor, request.filter(), request.cursor()));
    }
}
