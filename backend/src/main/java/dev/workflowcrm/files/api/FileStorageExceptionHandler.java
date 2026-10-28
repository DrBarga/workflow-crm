package dev.workflowcrm.files.api;

import dev.workflowcrm.integrations.api.BinaryStorageException;
import dev.workflowcrm.shared.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class FileStorageExceptionHandler {
    @ExceptionHandler(BinaryStorageException.class)
    ResponseEntity<ApiError> unavailable(HttpServletRequest request) {
        String correlationId = request.getHeader("X-Correlation-Id");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(
                        java.time.Instant.now(),
                        503,
                        "Service Unavailable",
                        "STORAGE_UNAVAILABLE",
                        "File storage is unavailable",
                        request.getRequestURI(),
                        correlationId == null || correlationId.isBlank()
                                ? java.util.UUID.randomUUID().toString()
                                : correlationId,
                        java.util.List.of()));
    }
}
