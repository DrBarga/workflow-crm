package dev.workflowcrm.tasks.api;

import dev.workflowcrm.shared.error.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = TaskExecutionController.class)
class TaskExecutionExceptionHandler {
    private record Error(
            Instant timestamp,
            int status,
            String error,
            String code,
            String message,
            String path,
            String correlationId,
            List<FieldViolation> violations,
            List<TaskStatusChanged.State> blockingTasks) {}

    @ExceptionHandler(TaskDependenciesBlockedException.class)
    ResponseEntity<Error> blocked(TaskDependenciesBlockedException failure, HttpServletRequest request) {
        String correlation = request.getHeader("X-Correlation-Id");
        return ResponseEntity.unprocessableEntity()
                .cacheControl(CacheControl.noStore())
                .body(new Error(
                        Instant.now(),
                        422,
                        "Unprocessable Entity",
                        "TASK_DEPENDENCIES_NOT_MET",
                        failure.getMessage(),
                        request.getRequestURI(),
                        correlation == null ? UUID.randomUUID().toString() : correlation,
                        List.of(new FieldViolation("taskId", "Task is waiting for its predecessors")),
                        failure.blockingTasks()));
    }
}
