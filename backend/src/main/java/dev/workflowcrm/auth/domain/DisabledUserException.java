package dev.workflowcrm.auth.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class DisabledUserException extends ResponseStatusException {

    public DisabledUserException() {
        super(HttpStatus.FORBIDDEN, "User account is disabled");
    }
}
