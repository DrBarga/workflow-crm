package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.objects.domain.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/objects/{id}")
class ObjectEditController {
    private final ObjectEditService edits;
    private final ObjectEntryPointService points;
    private final ObjectEditParser parser;

    ObjectEditController(ObjectEditService edits, ObjectEntryPointService points, ObjectEditParser parser) {
        this.edits = edits;
        this.points = points;
        this.parser = parser;
    }

    @PatchMapping
    ResponseEntity<ObjectStatusResult> patch(
            @PathVariable UUID id, @RequestBody JsonNode body, JwtAuthenticationToken authentication) {
        UUID actor = ObjectStatusController.actor(authentication);
        edits.requireEditor(actor, fields(body));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(edits.patch(actor, id, parser.patch(body)));
    }

    @PostMapping("/entry-point")
    ResponseEntity<ObjectEntryPointResult> point(
            @PathVariable UUID id, @RequestBody JsonNode body, JwtAuthenticationToken authentication) {
        UUID actor = ObjectStatusController.actor(authentication);
        edits.requireEditor(actor, fields(body));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(points.change(actor, id, parser.point(body)));
    }

    @PostMapping("/entry-point-requests/{requestId}/{action:approve|reject|cancel}")
    ResponseEntity<ObjectEntryPointResult> decide(
            @PathVariable UUID id,
            @PathVariable UUID requestId,
            @PathVariable String action,
            @RequestBody JsonNode body,
            JwtAuthenticationToken authentication) {
        UUID actor = ObjectStatusController.actor(authentication);
        edits.requireEditor(actor, fields(body));
        parser.empty(body);
        String decision =
                switch (action) {
                    case "approve" -> "APPROVED";
                    case "reject" -> "REJECTED";
                    case "cancel" -> "CANCELLED";
                    default -> throw new IllegalArgumentException("Invalid decision");
                };
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(points.decide(actor, id, requestId, decision));
    }

    private Set<String> fields(JsonNode body) {
        var fields = new HashSet<String>();
        if (body != null && body.isObject()) body.fieldNames().forEachRemaining(fields::add);
        return fields;
    }
}
