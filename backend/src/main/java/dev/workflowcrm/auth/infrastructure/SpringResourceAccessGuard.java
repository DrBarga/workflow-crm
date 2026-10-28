package dev.workflowcrm.auth.infrastructure;

import dev.workflowcrm.auth.api.AccessScope;
import dev.workflowcrm.auth.api.ResourceAccessGuard;
import dev.workflowcrm.auth.api.ResourceScope;
import dev.workflowcrm.auth.domain.ResourceAccessPolicy;
import dev.workflowcrm.users.api.AuthenticationUserQuery;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
class SpringResourceAccessGuard implements ResourceAccessGuard {
    private final AuthenticationUserQuery users;
    private final ResourceAccessPolicy policy;

    SpringResourceAccessGuard(AuthenticationUserQuery users, ResourceAccessPolicy policy) {
        this.users = users;
        this.policy = policy;
    }

    @Override
    public void requireAccess(ResourceScope resource, Set<String> allowedRoles, AccessScope scope) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        if (jwt.getToken().getSubject() == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getToken().getSubject());
        } catch (IllegalArgumentException exception) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        var user = users.findById(userId).orElse(null);
        if (!policy.allows(user, resource, allowedRoles, scope)) {
            throw new AccessDeniedException("Access denied");
        }
    }
}
