package dev.workflowcrm.objects.api;

import dev.workflowcrm.tasks.api.UnfinishedTasksException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
        assignableTypes = {
            ObjectStatusController.class,
            ObjectStatusApprovalController.class,
            ObjectEditController.class
        })
class ObjectStatusExceptionHandler {
    private record Error(
            Instant timestamp,
            int status,
            String error,
            String code,
            String message,
            String path,
            String correlationId,
            Object violations,
            Object tasks,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                    dev.workflowcrm.objects.domain.ObjectTransitionConditions unmetConditions) {}

    @ExceptionHandler(UnfinishedTasksException.class)
    ResponseEntity<Object> blocked(UnfinishedTasksException exception, HttpServletRequest request) {
        return error(
                "UNFINISHED_TASKS", exception.getMessage(), exception.violations(), exception.tasks(), null, request);
    }

    @ExceptionHandler(dev.workflowcrm.objects.domain.ObjectTransitionConditionsException.class)
    ResponseEntity<Object> conditions(
            dev.workflowcrm.objects.domain.ObjectTransitionConditionsException exception, HttpServletRequest request) {
        return error(
                "TRANSITION_CONDITIONS_NOT_MET",
                exception.getMessage(),
                exception.violations(),
                exception.conditions().incompleteTasks(),
                exception.conditions(),
                request);
    }

    private ResponseEntity<Object> error(
            String code,
            String message,
            Object violations,
            Object tasks,
            dev.workflowcrm.objects.domain.ObjectTransitionConditions conditions,
            HttpServletRequest request) {
        String correlation = request.getHeader("X-Correlation-Id");
        return ResponseEntity.unprocessableEntity()
                .cacheControl(CacheControl.noStore())
                .body(new Error(
                        Instant.now(),
                        422,
                        "Unprocessable Entity",
                        code,
                        message,
                        request.getRequestURI(),
                        correlation == null ? UUID.randomUUID().toString() : correlation,
                        violations,
                        tasks,
                        conditions));
    }
}
