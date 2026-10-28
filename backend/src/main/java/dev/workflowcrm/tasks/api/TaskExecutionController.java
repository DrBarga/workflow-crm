package dev.workflowcrm.tasks.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.workflowcrm.shared.error.UnprocessableInputException;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/objects/{id}/tasks/{taskId}")
class TaskExecutionController {
    private final TaskExecution tasks;

    TaskExecutionController(TaskExecution tasks) {
        this.tasks = tasks;
    }

    @PostMapping("/complete")
    ResponseEntity<TaskExecution.Result> complete(
            @PathVariable UUID id, @PathVariable UUID taskId, @RequestBody JsonNode body, JwtAuthenticationToken auth) {
        if (body == null || !body.isObject() || body.size() != 1 || !body.has("version"))
            throw new UnprocessableInputException("body", "Provide only the task version");
        var version = body.path("version");
        if (!version.isIntegralNumber() || !version.canConvertToLong() || version.longValue() < 0)
            throw new UnprocessableInputException("version", "Provide a non-negative task version");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(tasks.complete(
                        id,
                        taskId,
                        version.longValue(),
                        UUID.fromString(auth.getToken().getSubject())));
    }
}
