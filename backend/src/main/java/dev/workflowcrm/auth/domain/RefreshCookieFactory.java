package dev.workflowcrm.auth.domain;

import java.time.Instant;

public interface RefreshCookieFactory {

    String create(String token);

    String create(String token, Instant expiresAt);

    String clear();
}
