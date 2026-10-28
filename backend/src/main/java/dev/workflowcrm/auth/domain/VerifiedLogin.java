package dev.workflowcrm.auth.domain;

import dev.workflowcrm.users.api.AuthenticationUser;

public record VerifiedLogin(AuthenticationUser user, SessionMetadata metadata, String email) {}
