package dev.workflowcrm.users.api;

import java.util.Optional;
import java.util.UUID;

public interface AuthenticationUserQuery {

    Optional<AuthenticationUser> findByEmail(String email);

    Optional<AuthenticationUser> findById(UUID id);

    Optional<String> email(UUID id);
}
