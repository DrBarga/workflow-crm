package dev.workflowcrm.auth.domain;

import java.time.Instant;

public interface TokenRevocationStore {

    void revoke(String tokenId, Instant expiresAt);

    boolean isRevoked(String tokenId);
}
