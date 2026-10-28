package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.objects.domain.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/objects/{id}/task-sets")
class ObjectTaskSetController {
    private final ObjectTaskSetService sets;
    private final ObjectStatusParser parser;

    ObjectTaskSetController(ObjectTaskSetService sets, ObjectStatusParser parser) {
        this.sets = sets;
        this.parser = parser;
    }

    @PostMapping("/preview")
    ResponseEntity<ObjectStatusResult> preview(
            @PathVariable UUID id, @RequestBody JsonNode body, JwtAuthenticationToken auth) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(sets.preview(ObjectStatusController.actor(auth), id, parser.taskSet(body)));
    }

    @PostMapping
    ResponseEntity<ObjectTaskSetJob> submit(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody JsonNode body,
            JwtAuthenticationToken auth) {
        var job = sets.submit(ObjectStatusController.actor(auth), id, key, parser.taskSet(body));
        return ResponseEntity.status(job.state() == ObjectTaskSetJob.State.QUEUED ? 202 : 200)
                .location(URI.create("/api/objects/" + id + "/task-sets/jobs/" + job.id()))
                .cacheControl(CacheControl.noStore())
                .body(job);
    }

    @GetMapping("/jobs/{jobId}")
    ResponseEntity<ObjectTaskSetJob> get(@PathVariable UUID id, @PathVariable UUID jobId, JwtAuthenticationToken auth) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(sets.get(ObjectStatusController.actor(auth), id, jobId));
    }
}
