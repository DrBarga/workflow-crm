package dev.workflowcrm.objects.api;

import dev.workflowcrm.objects.domain.ObjectTaskPreviewService;
import dev.workflowcrm.tasks.api.TaskSetPreview;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/objects")
class ObjectTaskPreviewController {
    private final ObjectTaskPreviewService previews;

    ObjectTaskPreviewController(ObjectTaskPreviewService previews) {
        this.previews = previews;
    }

    @GetMapping("/{id}/task-preview")
    ResponseEntity<TaskSetPreview> preview(
            @PathVariable UUID id, @RequestParam String status, JwtAuthenticationToken authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(previews.preview(ObjectStatusController.actor(authentication), id, status));
    }
}
