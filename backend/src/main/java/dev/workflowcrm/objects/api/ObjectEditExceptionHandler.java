package dev.workflowcrm.objects.api;

import dev.workflowcrm.objects.domain.ObjectEditConflictException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
        assignableTypes = {ObjectEditController.class, ObjectReassignmentController.class, ObjectCommentController.class
        })
class ObjectEditExceptionHandler {
    @ExceptionHandler(ObjectEditConflictException.class)
    ResponseEntity<Object> conflict(ObjectEditConflictException exception, HttpServletRequest request) {
        record Error(
                Instant timestamp,
                int status,
                String error,
                String code,
                String message,
                String path,
                String correlationId,
                List<Object> violations,
                long currentVersion) {}
        String correlation = request.getHeader("X-Correlation-Id");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .cacheControl(CacheControl.noStore())
                .body(new Error(
                        Instant.now(),
                        409,
                        "Conflict",
                        "CONCURRENT_UPDATE",
                        exception.getMessage(),
                        request.getRequestURI(),
                        correlation == null ? UUID.randomUUID().toString() : correlation,
                        List.of(),
                        exception.currentVersion()));
    }
}
