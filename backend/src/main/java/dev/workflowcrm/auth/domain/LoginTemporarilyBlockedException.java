package dev.workflowcrm.auth.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class LoginTemporarilyBlockedException extends ResponseStatusException {

    public LoginTemporarilyBlockedException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "Too many failed login attempts");
    }
}
