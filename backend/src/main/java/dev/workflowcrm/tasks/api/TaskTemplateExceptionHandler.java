package dev.workflowcrm.tasks.api;

import dev.workflowcrm.shared.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class TaskTemplateExceptionHandler {
    @ExceptionHandler(TaskTemplateConfigurationException.class)
    ResponseEntity<ApiError> configuration(TaskTemplateConfigurationException exception, HttpServletRequest request) {
        String correlation = request.getHeader("X-Correlation-Id");
        return ResponseEntity.status(409)
                .cacheControl(CacheControl.noStore())
                .body(new ApiError(
                        Instant.now(),
                        409,
                        "Conflict",
                        "TASK_TEMPLATE_CONFIGURATION_ERROR",
                        exception.getMessage(),
                        request.getRequestURI(),
                        correlation == null || correlation.isBlank()
                                ? UUID.randomUUID().toString()
                                : correlation,
                        List.of()));
    }
}
