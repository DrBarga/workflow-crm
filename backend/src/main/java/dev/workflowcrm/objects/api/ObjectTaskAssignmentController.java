package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.objects.domain.ObjectTaskAssignmentService;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import dev.workflowcrm.tasks.api.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/objects/{id}/tasks/{taskId}")
class ObjectTaskAssignmentController {
    private final ObjectTaskAssignmentService tasks;

    ObjectTaskAssignmentController(ObjectTaskAssignmentService tasks) {
        this.tasks = tasks;
    }

    @GetMapping("/assignees")
    ResponseEntity<List<TaskAssignment.Candidate>> options(
            @PathVariable UUID id, @PathVariable UUID taskId, JwtAuthenticationToken auth) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(tasks.options(ObjectStatusController.actor(auth), id, taskId));
    }

    @PatchMapping("/assignee")
    ResponseEntity<TaskAssigneeChange.Result> change(
            @PathVariable UUID id, @PathVariable UUID taskId, @RequestBody JsonNode body, JwtAuthenticationToken auth) {
        if (body == null || !body.isObject()) throw invalid("body", "Expected an object");
        body.fieldNames().forEachRemaining(field -> {
            if (!List.of("assigneeId", "version").contains(field)) throw invalid(field, "Unknown field");
        });
        var version = body.path("version");
        if (!version.isIntegralNumber() || !version.canConvertToLong() || version.longValue() < 0)
            throw invalid("version", "Provide a non-negative version");
        UUID assignee;
        try {
            String value = body.path("assigneeId").textValue();
            assignee = UUID.fromString(value);
            if (!assignee.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw invalid("assigneeId", "Provide a valid user ID");
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(tasks.change(ObjectStatusController.actor(auth), id, taskId, assignee, version.longValue()));
    }

    private static UnprocessableInputException invalid(String field, String message) {
        return new UnprocessableInputException(field, message);
    }
}
