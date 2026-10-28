package dev.workflowcrm.auth.domain;

import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class LogoutService {

    private final TokenRevocationStore revocationStore;
    private final RefreshSessionStore sessionStore;

    public LogoutService(TokenRevocationStore revocationStore, RefreshSessionStore sessionStore) {
        this.revocationStore = revocationStore;
        this.sessionStore = sessionStore;
    }

    public void logout(String tokenId, Instant expiresAt, String sessionId) {
        sessionStore.revoke(sessionId);
        revocationStore.revoke(tokenId, expiresAt);
    }
}
