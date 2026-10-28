package dev.workflowcrm.auth.domain;

import dev.workflowcrm.users.api.AuthenticationUser;

public interface TokenIssuer {

    String issue(AuthenticationUser user, String sessionId);
}
