package dev.workflowcrm.objects.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.workflowcrm.objects.domain.ObjectDuplicateReport;
import dev.workflowcrm.objects.domain.ObjectDuplicatesFoundException;
import dev.workflowcrm.shared.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(assignableTypes = ObjectCreationController.class)
class ObjectDuplicateExceptionHandler {
    @ExceptionHandler(ObjectDuplicatesFoundException.class)
    ResponseEntity<Warning> duplicates(ObjectDuplicatesFoundException exception, HttpServletRequest request) {
        String correlation = request.getHeader("X-Correlation-Id");
        if (correlation == null || correlation.isBlank())
            correlation = UUID.randomUUID().toString();
        var error = new ApiError(
                Instant.now(),
                409,
                "Conflict",
                "DUPLICATES_FOUND",
                exception.getMessage(),
                request.getRequestURI(),
                correlation,
                List.of());
        return ResponseEntity.status(409).body(new Warning(error, exception.duplicates()));
    }

    record Warning(@JsonUnwrapped ApiError error, ObjectDuplicateReport duplicates) {}
}
