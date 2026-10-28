package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.objects.domain.*;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/objects/{id}/reassign")
class ObjectReassignmentController {
    private final ObjectReassignmentService service;
    private final ObjectReassignmentParser parser;

    ObjectReassignmentController(ObjectReassignmentService service, ObjectReassignmentParser parser) {
        this.service = service;
        this.parser = parser;
    }

    @PostMapping
    ResponseEntity<ObjectReassignmentResult> reassign(
            @PathVariable UUID id, @RequestBody JsonNode body, JwtAuthenticationToken authentication) {
        UUID actor = ObjectStatusController.actor(authentication);
        service.requireActor(actor);
        var command = parser.parse(body);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.reassign(actor, id, command.managerId(), command.version()));
    }
}
