package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.approvals.api.StatusApprovalRequests;
import dev.workflowcrm.objects.domain.ObjectStatusApprovalService;
import dev.workflowcrm.objects.domain.ObjectStatusResult;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/objects/{objectId}/status-requests/{id}")
class ObjectStatusApprovalController {
    private final ObjectStatusApprovalService approvals;
    private final ObjectStatusParser parser;

    ObjectStatusApprovalController(ObjectStatusApprovalService approvals, ObjectStatusParser parser) {
        this.approvals = approvals;
        this.parser = parser;
    }

    @GetMapping
    ResponseEntity<StatusApprovalRequests.Request> get(
            @PathVariable UUID objectId, @PathVariable UUID id, JwtAuthenticationToken authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(approvals.get(ObjectStatusController.actor(authentication), objectId, id));
    }

    @PostMapping("/{action:approve|reject|cancel}")
    ResponseEntity<ObjectStatusResult> decide(
            @PathVariable UUID objectId,
            @PathVariable UUID id,
            @PathVariable String action,
            @RequestBody JsonNode body,
            JwtAuthenticationToken authentication) {
        String decision =
                switch (action) {
                    case "approve" -> "APPROVED";
                    case "reject" -> "REJECTED";
                    case "cancel" -> "CANCELLED";
                    default -> throw new IllegalArgumentException("Invalid decision");
                };
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(approvals.decide(
                        ObjectStatusController.actor(authentication),
                        objectId,
                        id,
                        decision,
                        parser.approval(body),
                        parser.assignees(body)));
    }
}
