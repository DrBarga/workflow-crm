package dev.workflowcrm.shared.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class RequestRateLimitException extends ResponseStatusException {
    public RequestRateLimitException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
