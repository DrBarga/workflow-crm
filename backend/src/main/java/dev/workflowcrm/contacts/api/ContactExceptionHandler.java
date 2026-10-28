package dev.workflowcrm.contacts.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.workflowcrm.contacts.domain.ContactConflictException;
import dev.workflowcrm.contacts.domain.ContactNotFoundException;
import dev.workflowcrm.shared.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ContactController.class)
class ContactExceptionHandler {
    record Conflict(@JsonUnwrapped ApiError error, long currentVersion, String confirmationToken) {}

    @ExceptionHandler(ContactNotFoundException.class)
    ResponseEntity<ApiError> missing(ContactNotFoundException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .cacheControl(CacheControl.noStore())
                .body(error(request, HttpStatus.NOT_FOUND, "CONTACT_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(ContactConflictException.class)
    ResponseEntity<Conflict> conflict(ContactConflictException exception, HttpServletRequest request) {
        String code =
                exception.confirmationToken() == null ? "CONCURRENT_UPDATE" : "CONTACT_DELETION_CONFIRMATION_REQUIRED";
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .cacheControl(CacheControl.noStore())
                .body(new Conflict(
                        error(request, HttpStatus.CONFLICT, code, exception.getMessage()),
                        exception.currentVersion(),
                        exception.confirmationToken()));
    }

    private static ApiError error(HttpServletRequest request, HttpStatus status, String code, String message) {
        String correlation = request.getHeader("X-Correlation-Id");
        return new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                correlation == null ? UUID.randomUUID().toString() : correlation,
                List.of());
    }
}
