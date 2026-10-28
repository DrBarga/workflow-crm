package dev.workflowcrm.users.infrastructure;

import dev.workflowcrm.users.domain.CurrentUser;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
class SecurityCurrentUser implements CurrentUser {
    public UUID id() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        try {
            return UUID.fromString(token.getToken().getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
    }
}
