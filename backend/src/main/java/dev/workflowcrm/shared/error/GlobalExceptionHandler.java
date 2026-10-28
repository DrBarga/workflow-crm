package dev.workflowcrm.shared.error;

import dev.workflowcrm.shared.security.InvalidPasswordException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleMalformedRequest(HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request body", request, List.of());
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleUploadLimit(HttpServletRequest request) {
        return buildResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "FILE_TOO_LARGE",
                "File limit is 20971520 bytes",
                request,
                List.of(new FieldViolation("file", "File limit is 20971520 bytes")));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> handleUnsupportedMedia(HttpServletRequest request) {
        return buildResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "Unsupported content type",
                request,
                List.of());
    }

    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    ResponseEntity<ApiError> handleMalformedMultipart(HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid multipart body", request, List.of());
    }

    @ExceptionHandler({
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.bind.MissingRequestHeaderException.class
    })
    ResponseEntity<ApiError> handleInvalidParameter(HttpServletRequest request) {
        return buildResponse(
                HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request parameter", request, List.of());
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleUnsupportedMethod(HttpServletRequest request) {
        return buildResponse(
                HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed", request, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleMissingResource(HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", request, List.of());
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleConcurrentUpdate(HttpServletRequest request) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "CONCURRENT_UPDATE",
                "User has changed; reload before editing",
                request,
                List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();

        return buildResponse(
                HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, violations);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        String code =
                switch (status) {
                    case UNAUTHORIZED -> "AUTHENTICATION_FAILED";
                    case FORBIDDEN -> "ACCESS_DENIED";
                    case TOO_MANY_REQUESTS ->
                        exception instanceof RequestRateLimitException
                                ? "REQUEST_RATE_LIMITED"
                                : "LOGIN_TEMPORARILY_BLOCKED";
                    default -> "REQUEST_REJECTED";
                };
        return buildResponse(status, code, message, request, List.of());
    }

    @ExceptionHandler(UnprocessableInputException.class)
    ResponseEntity<ApiError> handleUnprocessableInput(
            UnprocessableInputException exception, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_FAILED",
                exception.getMessage(),
                request,
                exception.violations());
    }

    @ExceptionHandler(InvalidPasswordException.class)
    ResponseEntity<ApiError> handleInvalidPassword(InvalidPasswordException exception, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Password does not meet the security requirements",
                request,
                List.of(new FieldViolation("password", exception.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception while processing {}", safePath(request), exception);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied", request, List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleAuthentication(HttpServletRequest request) {
        return buildResponse(
                HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Authentication required", request, List.of());
    }

    private ResponseEntity<ApiError> buildResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<FieldViolation> violations) {
        ApiError error = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                safePath(request),
                correlationId(request),
                violations);
        return ResponseEntity.status(status).body(error);
    }

    private String correlationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        return correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString()
                : correlationId;
    }

    private String safePath(HttpServletRequest request) {
        return request.getRequestURI().replaceFirst("/api/auth/invite/[^/]+", "/api/auth/invite/[redacted]");
    }
}
