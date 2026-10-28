package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.objects.domain.CreatedObjectView;
import dev.workflowcrm.objects.domain.ObjectCreationService;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/objects")
class ObjectCreationController {
    private final ObjectCreationService objects;
    private final ObjectRequestParser parser;

    ObjectCreationController(ObjectCreationService objects, ObjectRequestParser parser) {
        this.objects = objects;
        this.parser = parser;
    }

    @PostMapping
    ResponseEntity<CreatedObjectView> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody JsonNode body,
            JwtAuthenticationToken authentication) {
        UUID actor = actor(authentication, key);
        var request = parser.parse(body);
        var object = objects.create(
                actor, key, parser.hash(request), request.command(), Boolean.TRUE.equals(request.confirmDuplicates()));
        return ResponseEntity.created(URI.create("/api/objects/" + object.id())).body(object);
    }

    @PostMapping("/duplicate-cancellations")
    ResponseEntity<Void> cancel(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody JsonNode body,
            JwtAuthenticationToken authentication) {
        UUID actor = actor(authentication, key);
        var request = parser.parse(body);
        if (Boolean.TRUE.equals(request.confirmDuplicates()))
            throw new UnprocessableInputException("confirmDuplicates", "Cancellation cannot confirm creation");
        objects.cancel(actor, key, parser.hash(request), request.command());
        return ResponseEntity.noContent().build();
    }

    private UUID actor(JwtAuthenticationToken authentication, String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}"))
            throw new UnprocessableInputException(
                    "Idempotency-Key", "Provide a key of 1..128 ASCII letters, digits, . _ : or -");
        try {
            return UUID.fromString(authentication.getToken().getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
    }
}
