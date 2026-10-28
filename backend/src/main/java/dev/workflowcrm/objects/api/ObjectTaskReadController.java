package dev.workflowcrm.objects.api;

import dev.workflowcrm.objects.domain.ObjectTaskReadService;
import dev.workflowcrm.tasks.api.ObjectTaskRead;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
class ObjectTaskReadController {
    private final ObjectTaskReadService tasks;

    ObjectTaskReadController(ObjectTaskReadService tasks) {
        this.tasks = tasks;
    }

    @GetMapping("/api/objects/{id}/tasks")
    ResponseEntity<ObjectTaskRead.Page> list(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID taskSetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            JwtAuthenticationToken auth) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(tasks.list(ObjectStatusController.actor(auth), id, taskSetId, page, size));
    }
}
