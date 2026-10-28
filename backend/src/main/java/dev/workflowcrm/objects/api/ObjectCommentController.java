package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.objects.domain.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/objects/{id}/comments")
class ObjectCommentController {
    private final ObjectCommentService comments;
    private final ObjectCommentParser parser;

    ObjectCommentController(ObjectCommentService comments, ObjectCommentParser parser) {
        this.comments = comments;
        this.parser = parser;
    }

    @GetMapping
    ResponseEntity<ObjectCommentPage> list(
            @PathVariable UUID id,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            JwtAuthenticationToken authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(comments.get(ObjectStatusController.actor(authentication), id, cursor, limit));
    }

    @PostMapping
    ResponseEntity<ObjectComment> create(
            @PathVariable UUID id, @RequestBody JsonNode body, JwtAuthenticationToken authentication) {
        UUID actor = ObjectStatusController.actor(authentication);
        comments.requireWriter(actor, id);
        var created = comments.create(actor, id, parser.create(body));
        return ResponseEntity.created(URI.create("/api/objects/" + id + "/comments/" + created.id()))
                .cacheControl(CacheControl.noStore())
                .body(created);
    }

    @PatchMapping("/{commentId}")
    ResponseEntity<ObjectComment> edit(
            @PathVariable UUID id,
            @PathVariable UUID commentId,
            @RequestBody JsonNode body,
            JwtAuthenticationToken authentication) {
        UUID actor = ObjectStatusController.actor(authentication);
        comments.requireWriter(actor, id);
        var command = parser.edit(body);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(comments.edit(actor, id, commentId, command.input(), command.version()));
    }

    @DeleteMapping("/{commentId}")
    ResponseEntity<Void> delete(
            @PathVariable UUID id, @PathVariable UUID commentId, JwtAuthenticationToken authentication) {
        comments.delete(ObjectStatusController.actor(authentication), id, commentId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
